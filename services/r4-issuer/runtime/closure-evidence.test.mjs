import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync, sign} from 'node:crypto';
import {fork} from 'node:child_process';
import {once} from 'node:events';
import {mkdtempSync, readFileSync, readdirSync, unlinkSync, rmdirSync} from 'node:fs';
import {createConnection} from 'node:net';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertClosureEvidenceActive, createClosureEvidenceReader} from './closure-evidence.mjs';
import {createIssuerGate} from '../gate.mjs';
import {decision, decisionKeys, digest} from '../records.mjs';
import {fixtureBody, fixturePath, fixtureRequest} from '../test-support/receiver.mjs';

const encode = value => Buffer.from(JSON.stringify(value));
const copy = value => structuredClone(value);
const hash = value => digest(String(value));
const signerPairs = Object.fromEntries(['ready', 'coverage', 'outcome'].map(role => {
  const pair = generateKeyPairSync('ed25519');
  const der = pair.publicKey.export({format: 'der', type: 'spki'});
  return [role, {...pair, pin: {publicKeySpkiBase64: der.toString('base64'), publicKeySha256: digest(der)}}];
}));

function fixture({zero = false, change = () => {}, observed} = {}) {
  const actors = Object.fromEntries(['writer', 'stopper', 'closer', 'gate', 'transport'].map(role => [role, `${role}@fixture.invalid`]));
  const manifest = {purpose: 'R4_ISSUER_SYNTHETIC_ONLY', packageSha256: hash('package'), approvalSha256: hash('approval'),
    dispatchId: 'dispatch-1', runId: 'run-1', claimSha256: hash('claim'), challengeSha256: hash('challenge'),
    bodySha256: hash('body'), receiver: 'synthetic-receiver', notBeforeMs: 1000, expiresAtMs: 5000,
    actors, gateRevision: 'gate-1', transportRevision: 'transport-1'};
  if (observed) {
    manifest.notBeforeMs = Date.parse(observed[0].utc);
    manifest.expiresAtMs = manifest.notBeforeMs + 5000;
    manifest.receiver = fixturePath; manifest.bodySha256 = digest(fixtureBody);
  }
  manifest.keys = decisionKeys(manifest.packageSha256, manifest.approvalSha256);
  const manifestBytes = encode(manifest);
  const authority = {...manifest, manifestSha256: digest(manifestBytes), pairKey: manifest.keys.pairKey};
  const state = {requestGeneration: '4', reservationGeneration: zero ? null : '1', admissionGeneration: '2',
    egressGeneration: '3', admissionKind: zero ? 'STOP_BEFORE_SEND' : 'STARTED', egressKind: zero ? 'EGRESS_BLOCKED' : 'EGRESS_GRANTED'};
  const binding = decision(authority, 'STOP_EVIDENCE_BINDING', state);
  const observer = {id: 'independent-fixture', bootSha256: hash('boot'), routeSha256: hash('one-frame-route')};
  const events = [{kind: 'COVERAGE_BEGIN', atMs: 900}];
  if (!zero) events.push({kind: 'CONNECTION_OPEN', atMs: 1100, connectionId: 'c1'},
    {kind: 'FIRST_BYTE', atMs: 1200, connectionId: 'c1', byteLength: 1, claimSha256: authority.claimSha256});
  events.push({kind: 'FENCE_CLOSED', atMs: 1300, stopRequestGeneration: '4'});
  if (!zero) events.push({kind: 'CONNECTION_CLOSED', atMs: 1400, connectionId: 'c1'});
  events.push({kind: 'COVERAGE_END', atMs: 1500});
  const coverage = {schema: 'POINCARE_ISSUER_COVERAGE_V1', binding: copy(binding), observer,
    receiver: authority.receiver, bodySha256: authority.bodySha256, coverageComplete: true, events};
  const outcome = {schema: 'POINCARE_ISSUER_TRANSPORT_OUTCOME_V1', binding: copy(binding), receiver: authority.receiver,
    bodySha256: authority.bodySha256, requestCount: zero ? 0 : 1, outcome: zero ? 'ZERO_REQUESTS' : 'RECEIVER_ACCEPTED',
    receiverGeneration: zero ? null : '9', connectionId: zero ? null : 'c1', final: true, finalAtMs: 1350};
  const ready = {schema: 'POINCARE_ISSUER_CLOSURE_READY_V1', stage: 'READY_BEFORE_TERMINAL', binding: copy(binding),
    readyAtMs: 1550, expiresAtMs: 2200};
  if (observed) {
    // Test-only custodian translates the existing local observer's originals into the proposed
    // signed source schema. This is not an operational observer adapter or custody acceptance.
    let previous = null;
    for (const [i, {sha256, ...entry}] of observed.entries()) {
      assert.equal(entry.sequence, i + 1); assert.equal(entry.previousSha256, previous);
      assert.equal(digest(encode(entry)), sha256); previous = sha256;
    }
    coverage.events = observed.flatMap(entry => {
      const atMs = Date.parse(entry.utc), connectionId = `c${entry.connection}`;
      switch (entry.kind) {
        case 'STARTED': return [{kind: 'COVERAGE_BEGIN', atMs}];
        case 'CONNECTION_OPEN': return [{kind: entry.kind, atMs, connectionId}];
        case 'REQUEST_BYTES': return [{kind: 'FIRST_BYTE', atMs, connectionId, byteLength: entry.firstChunkBytes, claimSha256: authority.claimSha256}];
        case 'FENCE_CLOSED': return [{kind: entry.kind, atMs, stopRequestGeneration: '4'}];
        case 'CONNECTION_CLOSED': return [{kind: entry.kind, atMs, connectionId}];
        case 'DRAINED': return [{kind: 'COVERAGE_END', atMs}];
        default: return [];
      }
    });
    const accepted = observed.find(entry => entry.kind === 'RECEIVER_ACCEPTED');
    outcome.receiverGeneration = accepted.generation; outcome.finalAtMs = Date.parse(accepted.utc);
    ready.readyAtMs = Date.parse(observed.at(-1).utc) + 1; ready.expiresAtMs = ready.readyAtMs + 1000;
  }
  change({coverage, outcome, ready, binding});
  let previous = null;
  coverage.events = coverage.events.map((event, i) => {
    const payload = {sequence: i + 1, previousSha256: previous, ...event};
    previous = digest(encode(payload));
    return {...payload, sha256: previous};
  });
  const records = new Map(), order = [];
  function signed(role, body) {
    const bytes = encode({body, signature: sign(null, encode(body), signerPairs[role].privateKey).toString('base64')});
    const pin = {bucket: 'fixture-custody', object: `${role}.json`, generation: '11', metageneration: '1', sha256: digest(bytes)};
    records.set(pin.object, {bytes, generation: pin.generation, metageneration: pin.metageneration, sha256: pin.sha256});
    return pin;
  }
  ready.coverage = signed('coverage', coverage);
  ready.outcome = signed('outcome', outcome);
  const config = {readyReference: signed('ready', ready), signers: Object.fromEntries(Object.entries(signerPairs).map(([role, pair]) => [role, pair.pin])),
    observer: copy(observer), stopDeadlineMs: 6000, maxAgeMs: 1000};
  let clock = 1600;
  if (observed) { clock = ready.readyAtMs + 1; config.stopDeadlineMs = authority.expiresAtMs + 1000; }
  const now = () => clock;
  const controller = new AbortController();
  const context = {deadlineMs: 5500, signal: controller.signal, assertActive() {
    controller.signal.throwIfAborted(); assert.ok(clock < this.deadlineMs);
  }};
  if (observed) context.deadlineMs = config.stopDeadlineMs;
  const readControlObject = async (pin, inner) => {
    inner.assertActive(); order.push(pin.object); return records.get(pin.object) ?? null;
  };
  const reader = (read = readControlObject) => createClosureEvidenceReader({authority, config, readControlObject: read, now});
  return {authority, manifestBytes, state, binding, config, coverage, outcome, ready, records, order,
    context, controller, reader, now, clock: value => { clock = value; }, readControlObject};
}

test('signed originals yield the existing eight-field result, with exactly three pinned reads', async () => {
  for (const zero of [false, true]) {
    const f = fixture({zero});
    const evidence = await f.reader()(f.binding, f.context);
    assert.deepEqual(evidence, {binding: f.binding, routeClosed: true, connectionsClosed: true, coverageComplete: true,
      requestCount: zero ? 0 : 1, outcome: zero ? 'ZERO_REQUESTS' : 'RECEIVER_ACCEPTED',
      receiverGeneration: zero ? null : '9', evidenceSha256: f.config.readyReference.sha256});
    assert.deepEqual(f.order, ['ready.json', 'coverage.json', 'outcome.json']);
  }
  const f = fixture({change: ({outcome}) => { outcome.outcome = 'REQUEST_NOT_ACCEPTED'; outcome.receiverGeneration = null; }});
  assert.equal((await f.reader()(f.binding, f.context)).outcome, 'REQUEST_NOT_ACCEPTED');
});

const semanticCases = {
  'second physical first-byte observation': ({coverage}) => coverage.events.splice(3, 0, {...coverage.events[2]}),
  'coverage began after dispatch permission': ({coverage}) => { coverage.events[0].atMs = 1001; },
  'missing connection closure': ({coverage}) => coverage.events.splice(4, 1),
  'missing route fence': ({coverage}) => coverage.events.splice(3, 1),
  'incomplete coverage': ({coverage}) => { coverage.coverageComplete = false; },
  'sequence gap': ({coverage}) => { coverage.events[2].sequence = 9; },
  'broken event hash chain': ({coverage}) => { coverage.events[2].previousSha256 = hash('not-the-previous-event'); },
  'missing coverage end': ({coverage}) => { coverage.events.pop(); },
  'wrong stop generation': ({coverage}) => { coverage.events[3].stopRequestGeneration = '99'; },
  'wrong attempt claim': ({coverage}) => { coverage.events[2].claimSha256 = hash('other'); },
  'bytes after fence': ({coverage}) => { [coverage.events[2], coverage.events[3]] = [coverage.events[3], coverage.events[2]]; coverage.events[2].atMs = 1200; coverage.events[3].atMs = 1300; },
  'lost ambiguous ACK': ({outcome}) => { outcome.outcome = 'OPEN_UNKNOWN'; outcome.receiverGeneration = null; outcome.final = false; },
  'missing receiver generation': ({outcome}) => { outcome.receiverGeneration = null; },
  'noncanonical receiver generation': ({outcome}) => { outcome.receiverGeneration = '09'; },
  'wrong request count': ({outcome}) => { outcome.requestCount = 0; },
  'wrong connection': ({outcome}) => { outcome.connectionId = 'c2'; },
  'wrong receiver': ({outcome}) => { outcome.receiver = 'elsewhere'; },
  'wrong body': ({outcome}) => { outcome.bodySha256 = hash('other'); },
  'stale correlated record': ({outcome}) => { outcome.binding.dispatchId = 'old-dispatch'; },
  'outcome after closure': ({outcome}) => { outcome.finalAtMs = 1501; },
  'ready before closure': ({ready}) => { ready.readyAtMs = 1499; },
  'expired ready': ({ready}) => { ready.expiresAtMs = 1599; },
  'future ready': ({ready}) => { ready.readyAtMs = 1700; },
  'circular terminal dependency': ({ready}) => { ready.terminalGeneration = '99'; },
};
for (const [name, change] of Object.entries(semanticCases)) test(`refuses signed ${name}`, async () => {
  const f = fixture({change});
  assert.equal(await f.reader()(f.binding, f.context), null);
});

test('refuses missing sources, signature corruption, altered generations and original bytes', async () => {
  for (const mutate of [r => null, r => ({...r, generation: '12'}), r => ({...r, metageneration: '2'}),
    r => ({...r, sha256: hash('wrong')}), r => ({...r, bytes: Buffer.concat([r.bytes, Buffer.from(' ')])}),
    r => { const frame = JSON.parse(r.bytes); frame.signature = Buffer.alloc(64).toString('base64');
      const bytes = encode(frame); return {...r, bytes, sha256: digest(bytes)}; }]) {
    const f = fixture();
    const read = async (pin, context) => pin.object === 'coverage.json' ? mutate(f.records.get(pin.object)) : f.readControlObject(pin, context);
    assert.equal(await f.reader(read)(f.binding, f.context), null);
  }
  const f = fixture();
  f.config.signers.coverage = signerPairs.outcome.pin;
  assert.equal(await f.reader()(f.binding, f.context), null); // hash-valid source, wrong trusted signer
  const forged = fixture();
  const raw = forged.records.get('ready.json');
  const frame = JSON.parse(raw.bytes); frame.signature = Buffer.alloc(64).toString('base64');
  raw.bytes = encode(frame); raw.sha256 = digest(raw.bytes);
  forged.config.readyReference.sha256 = raw.sha256;
  assert.equal(await forged.reader()(forged.binding, forged.context), null); // Valid raw hash, invalid signature
});

test('refuses stale evidence, changed bindings, cancellation and deadline crossing without retry', async () => {
  const f = fixture();
  f.clock(2600);
  assert.equal(await f.reader()(f.binding, f.context), null);
  f.clock(1600);
  assert.equal(await f.reader()({...f.binding, requestGeneration: '8'}, f.context), null);
  assert.equal(f.order.length, 2); // One ready read per attempted binding; neither attempt retries.
  f.controller.abort();
  assert.equal(await f.reader()(f.binding, f.context), null);
  const late = fixture();
  const reader = late.reader(async (pin, context) => {
    const result = await late.readControlObject(pin, context); late.clock(5500); return result;
  });
  assert.equal(await reader(late.binding, late.context), null);
  assert.equal(late.order.length, 1);
  const blocked = fixture();
  const pending = blocked.reader(async () => new Promise(() => {}))(blocked.binding, blocked.context);
  blocked.controller.abort();
  assert.equal(await pending, null);
});

test('snapshots authority, config, candidate and reference pins before asynchronous I/O', async () => {
  const f = fixture();
  const reader = f.reader(async (pin, context) => {
    f.binding.requestGeneration = '99';
    assert.ok(Object.isFrozen(pin));
    return f.readControlObject(pin, context);
  });
  f.config.readyReference.sha256 = hash('changed-after-construction');
  f.authority.dispatchId = 'changed-after-construction';
  const evidence = await reader(f.binding, f.context);
  assert.equal(evidence.binding.requestGeneration, '4');
  assert.equal(evidence.binding.dispatchId, 'dispatch-1');
});

test('remaining signed-source I/O cannot outlive anchor, ready, coverage freshness or operation validity', async () => {
  const cases = [
    {name: 'outer anchor', cutoff: 1700, configure: f => { f.config.anchorExpiresAtMs = 1700; }},
    {name: 'ready expiry', cutoff: 2200, configure: () => {}},
    {name: 'coverage freshness', cutoff: 2501, configure: () => {},
      change: ({ready}) => { ready.expiresAtMs = 4000; }},
    {name: 'operation', cutoff: 1700, configure: f => { f.context.deadlineMs = 1700; }},
  ];
  for (const scenario of cases) {
    const f = fixture({change: scenario.change}); scenario.configure(f);
    const reader = f.reader(async (pin, context) => {
      const bytes = await f.readControlObject(pin, context);
      if (pin.object === 'outcome.json') {
        await Promise.resolve(); // Controlled delayed I/O, with no network or physical sends.
        f.clock(scenario.cutoff);
      }
      return bytes;
    });
    assert.equal(await reader(f.binding, f.context), null, scenario.name);
    assert.deepEqual(f.order, ['ready.json', 'coverage.json', 'outcome.json']);
  }
});

test('cached original evidence preserves minimum validity; cloning, extension, other operations and cancellation cannot grant it', async () => {
  const scenarios = [
    {cutoff: 1700, configure: f => { f.config.anchorExpiresAtMs = 1700; }},
    {cutoff: 2200, configure: () => {}},
    {cutoff: 2501, configure: () => {}, change: ({ready}) => { ready.expiresAtMs = 4000; }},
    {cutoff: 1700, configure: f => { f.context.deadlineMs = 1700; }},
  ];
  for (const scenario of scenarios) {
    const f = fixture({change: scenario.change}); scenario.configure(f);
    const evidence = await f.reader()(f.binding, f.context);
    assert.equal(assertClosureEvidenceActive(evidence, f.context), scenario.cutoff);
    assert.ok(Object.isFrozen(evidence)); assert.ok(Object.isFrozen(evidence.binding));
    assert.throws(() => assertClosureEvidenceActive(copy(evidence), f.context));
    assert.throws(() => assertClosureEvidenceActive({...evidence, validUntilMs: 9000}, f.context));
    assert.throws(() => assertClosureEvidenceActive(evidence, {...f.context, signal: new AbortController().signal}));
    f.config.anchorExpiresAtMs = 6000; f.context.deadlineMs = 6000;
    f.clock(scenario.cutoff - 1);
    assert.equal(assertClosureEvidenceActive(evidence, f.context), scenario.cutoff);
    f.clock(scenario.cutoff);
    assert.throws(() => assertClosureEvidenceActive(evidence, f.context));
    assert.equal(f.order.length, 3); // Cached checks neither reread sources nor issue a send.
  }
  const f = fixture();
  const evidence = await f.reader()(f.binding, f.context);
  f.controller.abort();
  assert.throws(() => assertClosureEvidenceActive(evidence, f.context));
});

test('an explicitly malformed outer expiry cannot fall back to the later stop deadline', () => {
  for (const expiry of [null, undefined, '1700', 6001, Number.NaN]) {
    const f = fixture(); f.config.anchorExpiresAtMs = expiry;
    assert.throws(() => f.reader());
  }
});

test('Gate plus cached-evidence guard issues zero terminal creates after delayed credentials cross any source cutoff', async () => {
  // This contract fixture replaces only storage/credential I/O. Audio owns the normal factory
  // and managed-store attachment and must separately replay this case through that composition.
  for (const scenario of [
    {name: 'live', cutoff: 1699, anchor: 1700, expected: 1},
    {name: 'outer anchor', cutoff: 1700, anchor: 1700, expected: 0},
    {name: 'ready', cutoff: 2200, expected: 0},
    {name: 'coverage', cutoff: 2501, expected: 0, change: ({ready}) => { ready.expiresAtMs = 4000; }},
  ]) {
    const f = fixture({change: scenario.change});
    if (scenario.anchor) f.config.anchorExpiresAtMs = scenario.anchor;
    const a = f.authority, s = f.state, store = new Map();
    const put = (slot, generation, kind, fields = {}) => store.set(a.keys[slot],
      {key: a.keys[slot], generation, value: decision(a, kind, fields)});
    put('reservation', '1', 'ADMISSION_RESERVED'); put('admission', '2', 'STARTED', {reservationGeneration: '1'});
    put('egress', '3', 'EGRESS_GRANTED', {reservationGeneration: '1', admissionGeneration: '2'});
    put('stop', '4', 'REQUESTED');
    const read = f.reader();
    let cached, cacheHits = 0, terminalCreates = 0, sends = 0;
    async function closure(binding, context) {
      if (!cached) cached = await read(binding, context);
      else cacheHits++;
      assertClosureEvidenceActive(cached, context);
      return copy(cached); // Data-only return; retain the exact original separately for the guard.
    }
    const gate = createIssuerGate({actors: a.actors, manifestSha256: a.manifestSha256, receiver: a.receiver,
      gateRevision: a.gateRevision, transportRevision: a.transportRevision, deadlineMs: a.expiresAtMs, stopDeadlineMs: 6000},
    {now: f.now, authenticate: async () => true, readVerifiedManifest: async () => f.manifestBytes,
      readIndependentClosure: closure, invokeTransport: async () => { sends++; }, store: {
        read: async key => store.get(key) ?? null,
        create: async ({key, value}, context) => {
          assert.equal(key, a.keys.terminal);
          await closure(cached.binding, context); // Exercise cache hit before credential preparation.
          await Promise.resolve(); f.clock(scenario.cutoff); // Delayed synthetic credential.
          assertClosureEvidenceActive(cached, context); // Must run AFTER await, immediately before CREATE.
          terminalCreates++;
          const record = {status: 'CREATED', key, generation: '12', value}; store.set(key, record); return record;
        },
      }});
    const result = await gate.confirmStop('synthetic-closer', f.context.signal);
    assert.equal(result.status, scenario.expected ? 'STOPPED_CONFIRMED' : 'OPEN_UNKNOWN', scenario.name);
    assert.equal(terminalCreates, scenario.expected, scenario.name);
    assert.equal(cacheHits, 1); assert.equal(sends, 0);
    assert.deepEqual(f.order, ['ready.json', 'coverage.json', 'outcome.json']);
    assert.equal(store.has(a.keys.terminal), Boolean(scenario.expected));
  }
});

test('real Gate creates terminal only after verified ready; uncertainty creates none and never dispatches', async () => {
  for (const ambiguous of [false, true]) {
    const f = fixture({change: ({outcome}) => { if (ambiguous) outcome.final = false; }});
    const a = f.authority, s = f.state;
    const store = new Map();
    const put = (slot, generation, kind, fields = {}) => store.set(a.keys[slot],
      {key: a.keys[slot], generation, value: decision(a, kind, fields)});
    put('reservation', '1', 'ADMISSION_RESERVED'); put('admission', '2', 'STARTED', {reservationGeneration: '1'});
    put('egress', '3', 'EGRESS_GRANTED', {reservationGeneration: '1', admissionGeneration: '2'}); put('stop', '4', 'REQUESTED');
    let creates = 0, sends = 0;
    const policy = {actors: a.actors, manifestSha256: a.manifestSha256, receiver: a.receiver,
      gateRevision: a.gateRevision, transportRevision: a.transportRevision, deadlineMs: a.expiresAtMs, stopDeadlineMs: 6000};
    const gate = createIssuerGate(policy, {now: f.now, authenticate: async () => true, readVerifiedManifest: async () => f.manifestBytes,
      readIndependentClosure: f.reader(), invokeTransport: async () => { sends++; }, store: {
        read: async key => store.get(key) ?? null,
        create: async ({key, value}) => {
          creates++; assert.equal(key, a.keys.terminal); assert.deepEqual(f.order, ['ready.json', 'coverage.json', 'outcome.json']);
          assert.deepEqual(value, decision(a, 'STOPPED_CONFIRMED', {...s, requestCount: 1, outcome: 'RECEIVER_ACCEPTED',
            receiverGeneration: '9', evidenceSha256: f.config.readyReference.sha256}));
          const record = {status: 'CREATED', key, generation: '12', value}; store.set(key, record); return record;
        },
      }});
    const result = await gate.confirmStop('synthetic-closer', f.context.signal);
    assert.equal(result.status, ambiguous ? 'OPEN_UNKNOWN' : 'STOPPED_CONFIRMED');
    assert.equal(creates, ambiguous ? 0 : 1); assert.equal(sends, 0);
  }
});

test('existing physical loopback first-byte originals pass the production parser; a second observation cannot pass', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'poincare-closure-'));
  const child = fork(fileURLToPath(new URL('../test-support/receiver.mjs', import.meta.url)),
    ['--receiver'], {stdio: ['ignore', 'ignore', 'ignore', 'ipc'], windowsHide: true});
  let socket;
  const signal = AbortSignal.timeout(5000);
  async function message(kind, command) {
    const pending = once(child, 'message', {signal}); child.send(command);
    const [result] = await pending; assert.equal(result.kind, kind); return result;
  }
  try {
    const a = fixture().authority;
    const binding = {pairKey: a.pairKey, dispatchId: a.dispatchId, attemptId: a.claimSha256,
      reservationGeneration: '1', admissionGeneration: '2', egressGeneration: '3'};
    const {port} = await message('READY', {directory, binding, loseAckAndCoverage: false});
    const accepted = once(child, 'message', {signal});
    socket = createConnection({host: '127.0.0.1', port, family: 4, signal});
    socket.on('error', () => {});
    await once(socket, 'connect', {signal});
    const closed = once(socket, 'close', {signal});
    socket.resume();
    socket.end(fixtureRequest(binding));
    assert.equal((await accepted)[0].kind, 'ACCEPTED');
    // Deliberately lose the caller ACK. Only the independent original receipt can reconcile it.
    socket.destroy();
    await message('DRAINED', {kind: 'STOP', stop: {requestGeneration: '4'}});
    await closed;
    const exited = once(child, 'exit', {signal});
    await message('FINISHED', {kind: 'FINISH'}); await exited;
    const receipt = JSON.parse(readFileSync(join(directory, 'accepted.json'), 'utf8'));
    assert.deepEqual(receipt, {status: 'CREATED', receiver: fixturePath, bodySha256: digest(fixtureBody),
      generation: '1', connection: 1, ...binding});
    const originals = readFileSync(join(directory, 'observer.ndjson'), 'utf8').trim().split('\n').map(JSON.parse);
    assert.equal(originals.filter(entry => entry.kind === 'REQUEST_BYTES').length, 1);
    assert.equal(readdirSync(directory).filter(name => name.startsWith('generation-')).length, 1);
    const f = fixture({observed: originals});
    const evidence = await f.reader()(f.binding, f.context);
    assert.equal(evidence.requestCount, 1); assert.equal(evidence.receiverGeneration, '1');
    const doubled = fixture({observed: originals, change: ({coverage}) => coverage.events.splice(3, 0, {...coverage.events[2]})});
    assert.equal(await doubled.reader()(doubled.binding, doubled.context), null);
    assert.equal(originals.filter(entry => entry.kind === 'REQUEST_BYTES').length, 1); // Verification never sends.
  } finally {
    socket?.destroy();
    if (child.exitCode === null && child.signalCode === null) {
      const stopped = once(child, 'exit'); child.kill(); await stopped;
    }
    for (const name of readdirSync(directory)) unlinkSync(join(directory, name));
    rmdirSync(directory);
  }
});
