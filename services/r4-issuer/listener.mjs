import {once} from 'node:events';
import {createServer} from 'node:http';
import {createIssuerServiceEntrypoint} from './service-entrypoint.mjs';
import {createIssuerServiceAuthenticator} from './service-auth.mjs';
import {runStopJob, runWriterJob} from './jobs.mjs';
import {duringOperation} from './deadline.mjs';
import {digest, isDigest, isGeneration} from './records.mjs';

const routes = Object.freeze({
  gate: Object.freeze({'/v1/start': 'start', '/v1/stop': 'stop', '/v1/confirm-stop': 'confirmStop'}),
  transport: Object.freeze({'/v1/dispatch': 'dispatch'}),
});
const octets = 'application/octet-stream';
const validToken = token => typeof token === 'string' && token.length <= 8192 &&
  /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u.test(token);
const validLimit = value => Number.isSafeInteger(value) && value > 0 && value <= 65536;
const refused = Object.freeze({status: 'REFUSED'});
const unknown = Object.freeze({status: 'OPEN_UNKNOWN'});
const generationFields = ['reservationGeneration', 'admissionGeneration', 'egressGeneration', 'receiverGeneration'];
const handoffFields = ['kind', 'pairKey', 'manifestSha256', 'dispatchId', 'runId', 'claimSha256', 'challengeSha256',
  'reservationGeneration', 'admissionGeneration'];
const handoffDigest = handoff => digest(JSON.stringify(handoffFields.map(field => handoff[field])));

function isCompleteIssuerAcceptance(value) {
  return value?.status === 'RECEIVER_ACCEPTED' && Object.keys(value).length === 6 &&
    typeof value.dispatchId === 'string' && /^[A-Za-z0-9_.-]{1,160}$/u.test(value.dispatchId) &&
    generationFields.every(field => isGeneration(value[field]));
}

function acceptedForHandoff(result, handoff) {
  return isCompleteIssuerAcceptance(result) && ['dispatchId', 'reservationGeneration', 'admissionGeneration']
    .every(field => result[field] === handoff[field]);
}

function gateTransportPort(ports, scope) {
  return async (handoff, body, context) => {
    const original = structuredClone(handoff);
    const reply = await ports.invokeTransport(handoff, body, context);
    context.assertActive();
    if (reply?.status === 'CONSUMED_NO_SEND') {
      const {reservationGeneration, admissionGeneration, egressGeneration} = reply;
      if (reservationGeneration !== original.reservationGeneration || admissionGeneration !== original.admissionGeneration ||
          !isGeneration(egressGeneration)) return unknown;
      return {status: 'CONSUMED_NO_SEND', reservationGeneration, admissionGeneration, egressGeneration};
    }
    if (reply?.status !== 'RECEIVER_ACCEPTED') {
      return reply?.status === 'REFUSED' ? refused : unknown;
    }
    const {receiverAck, ...result} = reply;
    if (!acceptedForHandoff(result, original) || receiverAck?.status !== 'CREATED' ||
        receiverAck.handoffSha256 !== handoffDigest(original) || receiverAck.receiver !== scope.receiver ||
        receiverAck.bodySha256 !== scope.bodySha256 || receiverAck.egressGeneration !== result.egressGeneration ||
        receiverAck.generation !== result.receiverGeneration) return unknown;
    return result;
  };
}

async function handleTransportDispatch({createHandle, ports, scope, frame, context}) {
  let observed;
  const handle = createHandle({...ports, async sendOnce(request, context) {
    if (observed) throw Error('ISSUER_HTTP_DUPLICATE_SEND');
    const {dispatchId, reservationGeneration, admissionGeneration, egressGeneration} = request;
    const current = {dispatchId, reservationGeneration, admissionGeneration, egressGeneration, receipt: null};
    observed = current;
    const receipt = await ports.sendOnce(request, context);
    context.assertActive();
    if (receipt?.status === 'CREATED' && receipt.receiver === scope.receiver &&
        receipt.bodySha256 === scope.bodySha256 && isGeneration(receipt.generation)) {
      const {status, receiver, bodySha256, generation} = receipt;
      current.receipt = {status, receiver, bodySha256, generation};
    }
    return receipt;
  }});
  const result = await handle(frame, context);
  if (result.status !== 'RECEIVER_ACCEPTED') return result;
  if (!acceptedForHandoff(result, frame.handoff) || !observed?.receipt ||
      ['dispatchId', 'reservationGeneration', 'admissionGeneration', 'egressGeneration']
        .some(field => result[field] !== observed[field]) ||
      result.receiverGeneration !== observed.receipt.generation) return unknown;
  return {...result, receiverAck: {...observed.receipt, egressGeneration: observed.egressGeneration,
    handoffSha256: handoffDigest(frame.handoff)}};
}

function authorization(headers) {
  if (headers['x-serverless-authorization'] || headers.authorization?.length !== 1) return null;
  const value = headers.authorization[0];
  const token = value.startsWith('Bearer ') ? value.slice(7) : null;
  return validToken(token) ? token : null;
}

async function readBody(req, limit, signal) {
  const headers = req.headersDistinct;
  const sizes = headers['content-length'];
  if (headers['transfer-encoding'] || headers['content-encoding'] ||
      (sizes && (sizes.length !== 1 || !/^(?:0|[1-9][0-9]{0,4})$/u.test(sizes[0])))) return null;
  const declared = sizes ? Number(sizes[0]) : 0;
  if (limit === 0) return declared === 0 ? Buffer.alloc(0) : null;
  if (declared <= 0 || declared > limit || headers['content-type']?.length !== 1 ||
      headers['content-type'][0] !== octets) return null;
  const result = Buffer.alloc(declared);
  let length = 0;
  for await (const chunk of req) {
    signal.throwIfAborted();
    if (length + chunk.length > declared) return null;
    chunk.copy(result, length);
    length += chunk.length;
  }
  return length === declared ? result : null;
}

function respond(res, code, result = refused) {
  if (res.destroyed) return;
  res.writeHead(code, {'content-type': 'application/json', 'cache-control': 'no-store', connection: 'close'});
  res.end(JSON.stringify(result));
}

/** Construct the shared HTTP boundary without opening a socket. */
export function createIssuerHttpServer({service, configBytes, configSha256, policy, ports,
  startBytes, bodySha256, envelopeBytes, openTransportEnvelope} = {}) {
  if (!Object.hasOwn(routes, service) || !validLimit(startBytes) || !isDigest(bodySha256) ||
    ![ports?.now, ports?.readVerifiedManifest, ports?.dispatchAllowed, ports?.store?.read, ports?.store?.create]
      .every(port => typeof port === 'function') ||
    (service === 'gate' && [ports.invokeTransport, ports.readIndependentClosure].some(port => typeof port !== 'function')) ||
    (service === 'transport' && (!validLimit(envelopeBytes) || typeof openTransportEnvelope !== 'function' ||
      typeof ports.sendOnce !== 'function'))) throw Error('ISSUER_HTTP_SERVICE_BINDING');
  const bound = structuredClone(policy);
  const config = Buffer.from(configBytes ?? []);
  const createHandle = scopedPorts => createIssuerServiceEntrypoint({
    service, configBytes: config, configSha256, policy: bound, ports: scopedPorts,
  });
  const scope = Object.freeze({receiver: bound.receiver, bodySha256});
  const handle = createHandle(service === 'gate' ? {...ports, invokeTransport: gateTransportPort(ports, scope)} : ports);
  const authenticator = service === 'transport'
    ? createIssuerServiceAuthenticator({configBytes: config, configSha256, now: ports.now}) : null;

  return createServer({maxHeaderSize: 16384, headersTimeout: 5000, requestTimeout: 5000}, async (req, res) => {
    const action = Object.hasOwn(routes[service], req.url) && routes[service][req.url];
    if (!action) return respond(res, 404);
    if (req.method !== 'POST') return respond(res, 405);
    const token = authorization(req.headersDistinct);
    if (!token) return respond(res, 403);
    const lifecycle = new AbortController();
    res.once('close', () => lifecycle.abort());
    req.once('aborted', () => lifecycle.abort());
    const signal = lifecycle.signal;
    let body;
    try {
      body = await readBody(req, action === 'start' ? startBytes : action === 'dispatch' ? envelopeBytes : 0, signal);
    } catch {
      return respond(res, 400);
    }
    if (!body || (action === 'start' && (body.length !== startBytes || digest(body) !== bodySha256))) {
      return respond(res, 400);
    }

    let frame = {action, token, body};
    let actionPorts = ports;
    const policyDeadlineMs = action === 'stop' || action === 'confirmStop' ? bound.stopDeadlineMs : bound.deadlineMs;
    let context = {deadlineMs: policyDeadlineMs, signal};
    if (action === 'dispatch') {
      // Reuse the sealed token verifier before opening the protected envelope. The sealed entrypoint rechecks it.
      const actor = authenticator.verifyAction(action, token);
      if (!actor) return respond(res, 403);
      try {
        const proof = await duringOperation(ports, Math.min(policyDeadlineMs, actor.deadlineMs), operation =>
          operation.run(context => openTransportEnvelope(body, Object.freeze({...context, actor, policyDeadlineMs}))), signal);
        if (!proof || proof.policyDeadlineMs !== policyDeadlineMs || !Number.isSafeInteger(proof.deadlineMs) ||
          proof.deadlineMs > policyDeadlineMs || proof.deadlineMs <= ports.now() ||
          proof.actorSubject !== actor.subject || !proof.handoff ||
          !(proof.body instanceof Uint8Array) || proof.body.byteLength !== startBytes ||
          digest(proof.body) !== bodySha256) return respond(res, 403);
        const {deadlineMs} = proof;
        context = {policyDeadlineMs, deadlineMs, signal, assertActive() {
          signal.throwIfAborted();
          if (ports.now() >= deadlineMs) throw Error('ISSUER_HTTP_CUTOFF');
        }};
        context.assertActive();
        frame = {action, token, handoff: proof.handoff, body: Buffer.from(proof.body)};
        if (ports.forVerifiedHandoff) actionPorts = ports.forVerifiedHandoff(proof);
      } catch {
        return respond(res, 403);
      }
    }
    try {
      const result = action === 'dispatch' ? await handleTransportDispatch({createHandle, ports: actionPorts, scope, frame, context})
        : await handle(frame, context);
      if (result.status === 'OPEN_UNKNOWN') return respond(res, 409, unknown);
      // Successful gate actions have already verified this policy's exact manifest and decision records.
      const reply = service === 'gate' ? {...result, manifestSha256: bound.manifestSha256} : result;
      return respond(res, result.status === 'REFUSED' ? 403 : 200, reply);
    } catch {
      return respond(res, 409, unknown);
    }
  });
}

/** Explicit offline composition only. No fallback to credentials, environment or proxy headers. */
export async function listenOfflineIssuer(binding = {}) {
  if (binding.offline !== true) throw Error('ISSUER_HTTP_OFFLINE_BINDING');
  const server = createIssuerHttpServer(binding);
  const started = once(server, 'listening');
  server.listen(0, '127.0.0.1');
  await started;
  return Object.freeze({port: server.address().port, close: () => new Promise((resolve, reject) =>
    server.close(error => error ? reject(error) : resolve()))});
}

/** Reuse fixed writer/stop jobs. The injected client owns its destination and credential source. */
export function runOfflineIssuerJob({offline, job, sealedBody, token, clock, deadlineMs, signal, callGate}) {
  if (offline !== true || !['writer', 'stop'].includes(job) || !validToken(token) || typeof callGate !== 'function' ||
      (job === 'writer' && (!(sealedBody instanceof Uint8Array) || !validLimit(sealedBody.byteLength)))) {
    throw Error('ISSUER_HTTP_JOB_BINDING');
  }
  const headers = Object.freeze({authorization: `Bearer ${token}`});
  if (job === 'writer') return runWriterJob({sealedBody: Buffer.from(sealedBody), clock, deadlineMs, signal,
    start: (body, context) => callGate({path: '/v1/start', headers: {...headers, 'content-type': octets}, body}, context)});
  return runStopJob({clock, deadlineMs, signal,
    stop: context => callGate({path: '/v1/stop', headers, body: Buffer.alloc(0)}, context)});
}
