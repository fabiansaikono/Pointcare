import {createHash} from 'node:crypto';
import {appendFileSync, writeFileSync} from 'node:fs';
import {createServer} from 'node:net';
import {join} from 'node:path';

export const fixturePath = '/r4-synthetic-fixture';
export const fixtureBody = Buffer.from('Poincare R4 offline recovery fixture\n');
export const hash = bytes => createHash('sha256').update(bytes).digest('hex');

export function fixtureRequest({pairKey, dispatchId, attemptId, reservationGeneration, admissionGeneration, egressGeneration}) {
  return Buffer.concat([Buffer.from([
    `PUT ${fixturePath} HTTP/1.1`, 'Host: 127.0.0.1', 'Connection: close',
    'If-None-Match: *', `Content-Length: ${fixtureBody.byteLength}`,
    `X-Pair: ${pairKey}`, `X-Dispatch: ${dispatchId}`, `X-Attempt: ${attemptId}`,
    `X-Reservation: ${reservationGeneration}`, `X-Admission: ${admissionGeneration}`, `X-Egress: ${egressGeneration}`, '', '',
  ].join('\r\n')), fixtureBody]);
}

function serveFixture({directory, binding, loseAckAndCoverage}) {
  const expectedRequest = fixtureRequest(binding);
  let sequence = 0;
  let previousSha256 = null;
  function record(kind, fields = {}, publish = true) {
    const entry = {sequence: ++sequence, previousSha256, utc: new Date().toISOString(), kind, ...fields};
    const sha256 = hash(JSON.stringify(entry));
    previousSha256 = sha256;
    if (publish) appendFileSync(join(directory, 'observer.ndjson'), JSON.stringify({...entry, sha256}) + '\n',
      {mode: 0o600, flush: true});
  }

  let fenceOpen = true;
  let drainPending = false;
  let acceptedSocket;
  let receipt;
  let connectionCount = 0;
  const connections = new Set();
  function publishDrain() {
    if (!drainPending || connections.size !== 0) return;
    drainPending = false;
    record('DRAINED');
    process.send({kind: 'DRAINED'});
  }

  const server = createServer({allowHalfOpen: true}, socket => {
    const connection = ++connectionCount;
    connections.add(socket);
    record('CONNECTION_OPEN', {connection});
    let frame = Buffer.alloc(0);
    let observedBytes = false;
    socket.setTimeout(2_500, () => socket.destroy());
    socket.on('error', () => socket.destroy());
    socket.on('data', chunk => {
      if (!observedBytes) {
        observedBytes = true;
        record('REQUEST_BYTES', {connection, attemptId: binding.attemptId, firstChunkBytes: chunk.length});
      }
      if (!fenceOpen || frame.length + chunk.length > expectedRequest.length) {
        socket.destroy();
        return;
      }
      frame = Buffer.concat([frame, chunk]);
    });
    socket.on('end', () => {
      if (!fenceOpen || receipt || !frame.equals(expectedRequest)) {
        socket.destroy();
        return;
      }
      receipt = {status: 'CREATED', receiver: fixturePath, bodySha256: hash(fixtureBody),
        generation: '1', connection, ...binding};
      writeFileSync(join(directory, 'generation-1.fixture'), frame.subarray(-fixtureBody.length),
        {flag: 'wx', mode: 0o600, flush: true});
      writeFileSync(join(directory, 'accepted.json'), JSON.stringify(receipt), {flag: 'wx', mode: 0o600, flush: true});
      record('RECEIVER_ACCEPTED', {connection, generation: receipt.generation, bodySha256: receipt.bodySha256});
      // Hold the response so Control's stop request must account for a real open connection.
      acceptedSocket = socket;
      process.send({kind: 'ACCEPTED'});
    });
    socket.on('close', () => {
      connections.delete(socket);
      // The negative case simulates a missing original closure record with a real sequence gap.
      record('CONNECTION_CLOSED', {connection}, !(loseAckAndCoverage && socket === acceptedSocket));
      publishDrain();
    });
  });

  process.on('message', message => {
    if (message.kind === 'STOP' && fenceOpen) {
      fenceOpen = false;
      drainPending = true;
      record('FENCE_CLOSED', {stop: message.stop, openConnections: connections.size});
      if (acceptedSocket && !acceptedSocket.destroyed) {
        if (loseAckAndCoverage) acceptedSocket.destroy();
        else {
          const body = JSON.stringify(receipt);
          acceptedSocket.end(`HTTP/1.1 201 Created\r\nContent-Length: ${Buffer.byteLength(body)}\r\nConnection: close\r\n\r\n${body}`);
        }
      }
      publishDrain();
    } else if (message.kind === 'FINISH' && !fenceOpen && !drainPending && connections.size === 0) {
      server.close(() => {
        record('FINISHED');
        process.send({kind: 'FINISHED'}, () => process.disconnect());
      });
    }
  });
  server.listen(0, '127.0.0.1', () => {
    const {port} = server.address();
    record('STARTED', {binding, host: '127.0.0.1', port});
    process.send({kind: 'READY', port});
  });
}

if (process.argv[2] === '--receiver' && typeof process.send === 'function') {
  process.once('message', serveFixture);
}
