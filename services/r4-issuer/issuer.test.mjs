import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createIssuerGate} from './gate.mjs';
import {runStopJob, runWriterJob} from './jobs.mjs';
import {decisionKeys, digest} from './records.mjs';
import {createIssuerTransport} from './transport.mjs';

const ackLost = () => { throw Error('FIXTURE_ACK_LOST'); };
const body = Buffer.from('Synthetic CR-3a receiver body');
const actors = {writer:'fixture-writer', stopper:'fixture-stop', closer:'fixture-closer',
  gate:'fixture-gate', transport:'fixture-transport'};

function durableFixture() {
  const records = new Map();
  const controls = {};
  let sequence = 0;
  const store = {
    async read(key, context) {
      await controls.beforeRead?.(key, context);
      if (!controls.allowLateReadReply) context?.assertActive();
      return structuredClone(records.get(key) ?? null);
    },
    async create({key, ifGenerationMatch, value}, context) {
      assert.equal(ifGenerationMatch, 0);
      await controls.beforeCreate?.(value, context);
      context.assertActive();
      if (records.has(key)) return {status:'EXISTS', key};
      const record = {key, generation:String(++sequence), value:structuredClone(value)};
      records.set(key, record);
      await controls.afterCreate?.(value, context);
      return {status:'CREATED', ...structuredClone(record)};
    },
  };
  return {store, records, controls};
}

function receiverFixture() {
  const requests = [];
  const accepted = [];
  return {requests, accepted};
}

function fixtureClock() {
  let now = 125;
  const pending = new Map();
  return {
    now: () => now,
    scheduleTimeout(fn, wait) {
      const timer = Symbol();
      pending.set(timer, {at:now + wait, fn});
      return timer;
    },
    cancelTimeout: timer => pending.delete(timer),
    advance(ms) {
      assert(ms >= now);
      now = ms;
      for (const [timer, {at, fn}] of pending) {
        if (at <= now) {
          pending.delete(timer);
          fn();
        }
      }
    },
  };
}

function boot({durable = durableFixture(), receiver = receiverFixture(), changes = {}, policyChanges = {}} = {}) {
  const packageSha256 = digest('fixture-package');
  const approvalSha256 = digest('fixture-approval');
  const keys = decisionKeys(packageSha256, approvalSha256);
  const manifest = {purpose:'R4_ISSUER_SYNTHETIC_ONLY', packageSha256, approvalSha256, keys, actors,
    runId:'original-run', dispatchId:'original-dispatch', claimSha256:digest('claim'),
    challengeSha256:digest('challenge'), bodySha256:digest(body), receiver:'offline-fixture/receiver.json',
    gateRevision:'fixture-gate-v1', transportRevision:'fixture-transport-v1', notBeforeMs:100, expiresAtMs:200,
    ...changes};
  const manifestBytes = Buffer.from(JSON.stringify(manifest));
  const policy = {actors, manifestSha256:digest(manifestBytes), receiver:'offline-fixture/receiver.json',
    gateRevision:'fixture-gate-v1', transportRevision:'fixture-transport-v1', deadlineMs:200, stopDeadlineMs:300, ...policyChanges};
  const controls = {};
  const clock = fixtureClock();
  const credentials = new Map(Object.values(actors).map(actor => [Symbol(actor), actor]));
  const credential = role => [...credentials].find(([, actor]) => actor === actors[role])[0];
  const ports = {
    async authenticate(token, actor) { return credentials.get(token) === actor; },
    async readVerifiedManifest(context) { return controls.readManifest ? controls.readManifest(context) : manifestBytes; },
    async dispatchAllowed(authority, context) {
      return controls.dispatchAllowed ? controls.dispatchAllowed(authority, context) : true;
    },
    ...clock,
    store: durable.store,
  };
  const transport = createIssuerTransport(policy, {...ports, async sendOnce(request, context) {
    assert.equal(request.receiver, 'offline-fixture/receiver.json');
    assert.equal(request.ifGenerationMatch, 0);
    assert.equal(digest(request.body), manifest.bodySha256);
    await controls.beforeSend?.(context);
    context.assertActive();
    receiver.requests.push(request);
    await controls.beforeReceiverAccept?.();
    assert.equal(receiver.accepted.length, 0);
    const ack = {status:'CREATED', receiver:request.receiver, bodySha256:request.bodySha256, generation:'9001'};
    receiver.accepted.push(ack);
    await controls.afterReceiverAccept?.();
    return controls.returnReceipt ? controls.returnReceipt(ack) : ack;
  }});
  const gate = createIssuerGate(policy, {...ports,
    invokeTransport(handoff, bytes, context) {
      if (controls.transportHook) return controls.transportHook(transport, credential('gate'), handoff, bytes, context);
      return transport(credential('gate'), handoff, bytes, context);
    },
    async readIndependentClosure(binding, context) { return controls.closure?.(binding, context); },
  });
  return {durable, receiver, controls, clock, keys, gate, credential,
    start: (bytes, signal) => runWriterJob({start: (payload, context) => gate.start(credential('writer'), payload, context.signal),
      sealedBody:bytes ?? body, clock, deadlineMs:policy.deadlineMs, signal}),
    stop: () => runStopJob({stop: context => gate.stop(credential('stopper'), context.signal), clock, deadlineMs:policy.stopDeadlineMs}),
    confirm: () => gate.confirmStop(credential('closer'))};
}

function counts(env, sent, accepted) {
  assert.equal(env.receiver.requests.length, sent, 'requests to the receiver');
  assert.equal(env.receiver.accepted.length, accepted, 'receiver accepts');
}

function independentClosure(binding, requestCount, receiverGeneration) {
  return {binding, routeClosed:true, connectionsClosed:true, coverageComplete:true,
    requestCount, outcome:requestCount === 0 ? 'ZERO_REQUESTS' : 'RECEIVER_ACCEPTED',
    receiverGeneration, evidenceSha256:digest('synthetic closure proof')};
}

function pause() {
  const reached = Promise.withResolvers();
  const release = Promise.withResolvers();
  return {reached:reached.promise, release:release.resolve, async hold() {
    reached.resolve();
    await release.promise;
  }};
}

const options = {timeout: 4000};

test('initial claim ACK, grant ACK and receiver acceptance are separate events', options, async () => {
  const env = boot();
  const admission = pause();
  const egress = pause();
  const provider = pause();
  env.durable.controls.afterCreate = value => {
    if (value.kind === 'STARTED') return admission.hold();
    if (value.kind === 'EGRESS_GRANTED') return egress.hold();
  };
  env.controls.beforeReceiverAccept = provider.hold;
  const result = env.start();
  await admission.reached;
  assert.equal(env.durable.records.size, 2);
  counts(env, 0, 0);
  admission.release();
  await egress.reached;
  assert.equal(env.durable.records.size, 3);
  counts(env, 0, 0);
  egress.release();
  await provider.reached;
  counts(env, 1, 0);
  provider.release();
  assert.deepEqual(await result, {status:'RECEIVER_ACCEPTED', dispatchId:'original-dispatch',
    reservationGeneration:'1', admissionGeneration:'2', egressGeneration:'3', receiverGeneration:'9001'});
  assert.equal((await env.start()).status, 'REFUSED');
  counts(env, 1, 1);
});

test('stop wins even with a concurrent STARTED, and closure needs independent evidence', options, async () => {
  for (const stage of ['beforeCreate','afterCreate']) {
    const env = boot();
    const admission = pause();
    env.durable.controls[stage] = value => value.kind === 'STARTED' ? admission.hold() : undefined;
    const writer = env.start();
    await admission.reached;
    const stop = await env.stop();
    assert.equal(stop.status, 'STOP_REQUESTED');
    assert.equal(stop.egressKind, 'EGRESS_BLOCKED');
    assert.equal(stop.admissionKind, stage === 'beforeCreate' ? 'STOP_BEFORE_SEND' : 'STARTED');
    assert.equal((await env.confirm()).status, 'OPEN_UNKNOWN');
    assert.equal(env.durable.records.has(env.keys.terminal), false);
    assert.equal((await env.gate.confirmStop(env.credential('stopper'))).status, 'REFUSED');
    admission.release();
    assert.equal((await writer).status, stage === 'beforeCreate' ? 'REFUSED' : 'CONSUMED_NO_HANDOFF');
    counts(env, 0, 0);
    env.controls.closure = binding => independentClosure(binding, 0, null);
    assert.equal((await env.confirm()).status, 'STOPPED_CONFIRMED');
    assert.equal(env.durable.records.get(env.keys.terminal).value.requestCount, 0);
  }
});

test('parallel writers and restored boots preserve the original admission and grant generations', options, async () => {
  const env = boot();
  const contenders = pause();
  let starts = 0;
  env.durable.controls.beforeCreate = value => {
    if (value.kind === 'ADMISSION_RESERVED') {
      if (++starts === 2) contenders.release();
      return contenders.hold();
    }
  };
  let original;
  env.controls.transportHook = async (transport, actor, handoff, bytes, context) => {
    original = handoff;
    const both = await Promise.all([transport(actor, handoff, bytes, context), transport(actor, handoff, bytes, context)]);
    assert.deepEqual(both.map(item => item.status).sort(), ['RECEIVER_ACCEPTED', 'REFUSED']);
    return both.find(item => item.status === 'RECEIVER_ACCEPTED');
  };
  const results = await Promise.all([env.start(), env.start()]);
  assert.deepEqual(results.map(item => item.status).sort(), ['RECEIVER_ACCEPTED', 'REFUSED']);
  assert.equal(original.reservationGeneration, '1');
  assert.equal(original.admissionGeneration, '2');
  const originals = structuredClone(env.durable.records);
  const {durable, receiver} = env;
  assert.equal((await boot({durable, receiver}).start()).status, 'REFUSED');
  const restored = boot({durable, receiver, changes:{runId:'new-run', dispatchId:'new-dispatch',
    claimSha256:digest('new-claim'), challengeSha256:digest('new-challenge'), gateRevision:'v2'},
    policyChanges:{gateRevision:'v2'}});
  assert.deepEqual(restored.keys, env.keys);
  assert.equal((await restored.start()).status, 'REFUSED');
  assert.deepEqual(durable.records, originals);
  counts(env, 1, 1);
});

test('ambiguous or late admission and grant ACKs cannot authorize a send', options, async () => {
  for (const [kind, slot] of [['ADMISSION_RESERVED','reservation'], ['STARTED','admission'], ['EGRESS_GRANTED','egress']]) {
    const env = boot();
    env.durable.controls.afterCreate = value => { if (value.kind === kind) ackLost(); };
    assert.equal((await env.start()).status, 'OPEN_UNKNOWN');
    const recordKey = env.keys[slot];
    const record = await env.durable.store.read(recordKey);
    assert.equal(record.value.kind, kind);
    const restored = boot({durable:env.durable, receiver:env.receiver});
    assert.equal((await restored.start()).status, 'REFUSED');
    if (kind === 'ADMISSION_RESERVED') assert.equal(env.durable.records.has(env.keys.admission), false);
    counts(env, 0, 0);
    env.durable.controls.afterCreate = value => { if (value.kind === 'REQUESTED') ackLost(); };
    assert.equal((await env.stop()).status, 'STOP_REQUESTED');
    assert.deepEqual(await env.durable.store.read(recordKey), record);
    assert.equal((await env.confirm()).status, 'OPEN_UNKNOWN');

    const late = boot();
    late.durable.controls.afterCreate = value => { if (value.kind === kind) late.clock.advance(200); };
    const outcome = await late.start();
    assert.equal(outcome.status, 'OPEN_UNKNOWN');
    assert.equal(late.durable.records.get(recordKey).value.kind, kind);
    assert.equal((await boot({durable:late.durable, receiver:late.receiver}).start()).status, 'REFUSED');
    counts(late, 0, 0);
  }

});

test('a writer already past preflight cannot replace an uncertain uncommitted STARTED', options, async () => {
  const first = boot();
  const second = boot({durable:first.durable, receiver:first.receiver});
  const firstCreate = pause();
  const secondReserve = pause();
  let attempts = 0;
  let reservations = 0;
  first.durable.controls.beforeCreate = async value => {
    if (value.kind === 'ADMISSION_RESERVED' && ++reservations === 2) await secondReserve.hold();
    if (value.kind === 'STARTED') {
      attempts++;
      await firstCreate.hold();
      ackLost();
    }
  };
  const starting = first.start();
  await firstCreate.reached;
  const conflict = second.start();
  await secondReserve.reached;
  firstCreate.release();
  assert.equal((await starting).status, 'OPEN_UNKNOWN');
  assert.equal(await first.durable.store.read(first.keys.admission), null);
  const reservation = await first.durable.store.read(first.keys.reservation);
  assert.equal(reservation.value.kind, 'ADMISSION_RESERVED');
  counts(first, 0, 0);
  secondReserve.release();
  assert.equal((await conflict).status, 'REFUSED');
  const restored = boot({durable:first.durable, receiver:first.receiver});
  assert.equal((await restored.start()).status, 'REFUSED');
  assert.equal(attempts, 1);
  assert.equal(await first.durable.store.read(first.keys.admission), null);
  assert.equal(first.durable.records.has(first.keys.egress), false);
  assert.deepEqual(await first.durable.store.read(first.keys.reservation), reservation);
  counts(first, 0, 0);
  assert.equal((await restored.stop()).admissionKind, 'STOP_BEFORE_SEND');
  assert.equal((await restored.confirm()).status, 'OPEN_UNKNOWN');
  restored.controls.closure = binding => independentClosure(binding, 0, null);
  assert.equal((await restored.confirm()).status, 'STOPPED_CONFIRMED');
  assert.equal((await restored.start()).status, 'REFUSED');
  counts(first, 0, 0);
});

test('a deadline bounds unresolved reads and checks every dispatch write and send after reads', options, async () => {
  for (const read of ['first-stop','reserved-stop','admission','grant-stop']) {
    const env = boot();
    const paused = pause();
    let operationContext;
    env.durable.controls.allowLateReadReply = true;
    env.durable.controls.beforeRead = async (key, context) => {
      const {records} = env.durable;
      const matches = (read === 'first-stop' && key === env.keys.stop && records.size === 0) ||
        (read === 'reserved-stop' && key === env.keys.stop && records.size === 1) ||
        (read === 'admission' && key === env.keys.admission) ||
        (read === 'grant-stop' && key === env.keys.stop && records.has(env.keys.egress));
      if (!matches) return;
      operationContext = context;
      await paused.hold();
    };
    const result = env.start();
    await paused.reached;
    assert.equal(operationContext.deadlineMs, 200);
    env.clock.advance(200);
    assert.equal((await result).status, 'OPEN_UNKNOWN');
    assert.equal(operationContext.signal.aborted, true);
    assert.equal(env.durable.records.has(env.keys.reservation), read !== 'first-stop');
    assert.equal(env.durable.records.has(env.keys.admission), ['admission','grant-stop'].includes(read));
    assert.equal(env.durable.records.has(env.keys.egress), read === 'grant-stop');
    counts(env, 0, 0);
    if (read !== 'first-stop') paused.release(); // The first read remains unresolved.
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(env.durable.records.has(env.keys.egress), read === 'grant-stop');
    counts(env, 0, 0);
  }
});

test('cancellation aborts deferred physical send and preserves uncertainty after issuance', options, async () => {
  for (const issued of [false, true]) {
    const env = boot();
    const paused = pause();
    let operationContext;
    const caller = new AbortController();
    env.controls.beforeSend = context => {
      operationContext = context;
      return issued ? undefined : paused.hold();
    };
    if (issued) env.controls.beforeReceiverAccept = paused.hold;
    const writer = env.start(undefined, caller.signal);
    await paused.reached;
    assert.equal(operationContext.deadlineMs, 200);
    counts(env, Number(issued), 0);
    if (issued) env.clock.advance(200);
    else caller.abort();
    assert.equal((await writer).status, 'OPEN_UNKNOWN');
    assert.equal(operationContext.signal.aborted, true);
    paused.release();
    await new Promise(resolve => setImmediate(resolve));
    const restored = boot({durable:env.durable, receiver:env.receiver});
    assert.equal((await restored.start()).status, 'REFUSED');
    counts(env, Number(issued), Number(issued));
    assert.equal((await env.confirm()).status, 'OPEN_UNKNOWN');
  }
});

test('stop after grant prevents a pending send or reconciles the one in-flight request', options, async () => {
  const pending = boot();
  const grant = pause();
  pending.durable.controls.afterCreate = value => value.kind === 'EGRESS_GRANTED' ? grant.hold() : undefined;
  const pendingWriter = pending.start();
  await grant.reached;
  assert.equal((await pending.stop()).egressKind, 'EGRESS_GRANTED');
  assert.equal((await pending.confirm()).status, 'OPEN_UNKNOWN');
  grant.release();
  assert.equal((await pendingWriter).status, 'CONSUMED_NO_SEND');
  counts(pending, 0, 0);
  pending.controls.closure = binding => independentClosure(binding, 0, null);
  assert.equal((await pending.confirm()).status, 'STOPPED_CONFIRMED');

  const inFlight = boot();
  const receiving = pause();
  inFlight.controls.beforeReceiverAccept = receiving.hold;
  inFlight.controls.afterReceiverAccept = ackLost;
  const sentWriter = inFlight.start();
  await receiving.reached;
  assert.equal((await inFlight.stop()).egressKind, 'EGRESS_GRANTED');
  counts(inFlight, 1, 0);
  assert.equal((await inFlight.confirm()).status, 'OPEN_UNKNOWN');
  receiving.release();
  assert.equal((await sentWriter).status, 'OPEN_UNKNOWN');
  assert.equal((await boot({durable:inFlight.durable, receiver:inFlight.receiver}).start()).status, 'REFUSED');
  counts(inFlight, 1, 1);
  const observed = inFlight.receiver.accepted[0];
  inFlight.controls.closure = binding => independentClosure({...binding, egressGeneration:'9999'}, 1, observed.generation);
  assert.equal((await inFlight.confirm()).status, 'OPEN_UNKNOWN');
  inFlight.controls.closure = binding => independentClosure(binding, 1, observed.generation);
  const final = await inFlight.confirm();
  assert.equal(final.status, 'STOPPED_CONFIRMED');
  assert.equal(final.receiverGeneration, '9001');
  assert.equal(final.requestCount, 1);
});

test('scope, receipt and uncertain control status fail closed', options, async () => {
  const variants = [
    [boot(), env => env.gate.start(env.credential('stopper'), body)],
    [boot(), env => env.start(Buffer.from('wrong body'))],
    [boot({changes:{receiver:'other-receiver'}}), env => env.start()],
    [boot({changes:{keys:{...decisionKeys(digest('fixture-package'), digest('fixture-approval')), egress:'other'}}}), env => env.start()],
    [boot({changes:{gateRevision:'unapproved-revision'}}), env => env.start()],
    [boot(), env => { env.clock.advance(200); return env.gate.start(env.credential('writer'), body); }],
    [boot(), env => { env.controls.readManifest = () => null; return env.start(); }],
    [boot(), env => { env.controls.readManifest = () => Buffer.from('{}'); return env.start(); }],
    [boot(), env => { env.controls.dispatchAllowed = () => undefined; return env.start(); }],
    [boot(), env => { env.durable.controls.beforeRead = ackLost; return env.start(); }],
  ];
  for (const [env, invoke] of variants) {
    assert.equal((await invoke(env)).status, 'REFUSED');
    assert.equal(env.durable.records.size, 0);
    counts(env, 0, 0);
  }
  const badHandoff = boot();
  badHandoff.controls.transportHook = async (transport, actor, handoff, bytes, context) => {
    assert.equal((await transport(badHandoff.credential('writer'), handoff, bytes, context)).status, 'REFUSED');
    assert.equal((await transport(actor, handoff, bytes, {...context, deadlineMs:201})).status, 'REFUSED');
    return transport(actor, {...handoff, reservationGeneration:'9999'}, bytes, context);
  };
  assert.equal((await badHandoff.start()).status, 'REFUSED');
  assert.equal(badHandoff.durable.records.has(badHandoff.keys.egress), false);
  counts(badHandoff, 0, 0);

  const badReceipt = boot();
  badReceipt.controls.returnReceipt = ack => ({...ack, receiver:'other-receiver'});
  assert.equal((await badReceipt.start()).status, 'OPEN_UNKNOWN');
  assert.equal((await badReceipt.start()).status, 'REFUSED');
  counts(badReceipt, 1, 1);
});
