import assert from 'node:assert/strict';
import {generateKeyPairSync, sign} from 'node:crypto';
import {test} from 'node:test';
import {decisionKeys, digest} from './records.mjs';
import {createIssuerServiceEntrypoint} from './service-entrypoint.mjs';

const nowMs = 1_760_000_000_000;
const seconds = nowMs / 1000;
const projectId = 'issuer-fixture-project';
const audiences = {gate:'https://gate.issuer-fixture.example', transport:'https://transport.issuer-fixture.example'};
const roleNames = ['writer','stopper','closer','gate','transport'];
const principals = Object.fromEntries(roleNames.map((role, index) => [role,
  {subject:String(200000 + index), email:`fixture-${role}@${projectId}.iam.gserviceaccount.com`}]));
const {publicKey, privateKey} = generateKeyPairSync('rsa', {modulusLength:2048});
const config = {projectId, issuer:'https://accounts.google.com', audiences, principals, maxLifetimeSeconds:120,
  keyset:{validFromMs:nowMs - 1000, validUntilMs:nowMs + 120_000,
    keys:{'fixture-key':publicKey.export({type:'spki', format:'pem'})}}};
const rawConfig = () => Buffer.from(JSON.stringify(config));
const receiver = 'offline-auth-fixture/receiver.json';
const body = Buffer.from('Synthetic protected issuer payload');
const packageSha256 = digest('service-auth-package');
const approvalSha256 = digest('service-auth-approval');
const keys = decisionKeys(packageSha256, approvalSha256);
const actorEmails = Object.fromEntries(roleNames.map(role => [role, principals[role].email]));
const manifest = {purpose:'R4_ISSUER_SYNTHETIC_ONLY', actors:actorEmails, keys, packageSha256, approvalSha256,
  runId:'auth-fixture-run', dispatchId:'auth-fixture-dispatch', claimSha256:digest('claim'), challengeSha256:digest('challenge'),
  bodySha256:digest(body), receiver, gateRevision:'auth-gate-v1', transportRevision:'auth-transport-v1',
  notBeforeMs:nowMs - 1000, expiresAtMs:nowMs + 15_000};
const manifestBytes = Buffer.from(JSON.stringify(manifest));
const policy = {actors:actorEmails, manifestSha256:digest(manifestBytes), receiver, gateRevision:manifest.gateRevision,
  transportRevision:manifest.transportRevision, deadlineMs:manifest.expiresAtMs, stopDeadlineMs:nowMs + 20_000};

function credential(role = 'writer', service = 'gate', changes = {}, headerChanges = {}) {
  const header = {alg:'RS256', typ:'JWT', kid:'fixture-key', ...headerChanges};
  const claims = {iss:config.issuer, aud:audiences[service], sub:principals[role].subject,
    email:principals[role].email, email_verified:true, iat:seconds - 1, exp:seconds + 60, ...changes};
  const input = [header, claims].map(value => Buffer.from(JSON.stringify(value)).toString('base64url')).join('.');
  return `${input}.${sign('RSA-SHA256', Buffer.from(input, 'ascii'), privateKey).toString('base64url')}`;
}

function testClock() {
  let time = nowMs;
  const timers = new Map();
  return {now:() => time,
    scheduleTimeout(fn, wait) {
      const timer = Symbol();
      timers.set(timer, {at:time + wait, fn});
      return timer;
    },
    cancelTimeout:timer => timers.delete(timer),
    advance(to, {deliverTimers = true} = {}) {
      time = to;
      if (!deliverTimers) return;
      for (const [timer, {at, fn}] of timers) if (at <= time) {
        timers.delete(timer);
        fn();
      }
    }};
}

function rig(options = {}) {
  const observed = {reads:0, writes:0, handoffs:0, sends:0, authorityReads:0, closeoutReads:0};
  const irreversible = [];
  const clock = testClock();
  const hooks = {};
  const objects = new Map();
  const store = {
    async read(key, context) {
      context.assertActive();
      observed.reads++;
      const result = structuredClone(objects.get(key) ?? null);
      await hooks.afterRead?.(key);
      return result;
    },
    async create({key, ifGenerationMatch, value}, context) {
      await hooks.beforeCreate?.(value.kind, context);
      context.assertActive();
      observed.writes++;
      record(value.kind, context);
      assert.equal(ifGenerationMatch, 0);
      if (objects.has(key)) return {status:'EXISTS', key};
      const stored = {key, generation:String(objects.size + 1), value:structuredClone(value)};
      objects.set(key, stored);
      await hooks.afterCreate?.(value.kind);
      return {status:'CREATED', ...stored};
    },
  };
  const ports = {...clock, store, async dispatchAllowed() { return true; },
    async readVerifiedManifest(context) {
      observed.authorityReads++;
      await hooks.beforeManifest?.(context);
      return manifestBytes;
    },
    // An old or permissive adapter must never be able to authorize the entrypoint.
    async authenticate() { throw Error('UNVERIFIED_PORT_MUST_NOT_BE_CALLED'); },
  };
  const configBytes = options.configBytes ?? rawConfig();
  const configSha256 = options.configSha256 ?? digest(configBytes);
  const pinned = {configBytes, configSha256, policy:options.policy ?? policy};
  function record(action, {deadlineMs, policyDeadlineMs}) {
    irreversible.push({action, time:clock.now(), deadlineMs, policyDeadlineMs});
  }
  const transport = createIssuerServiceEntrypoint({...pinned, service:'transport', ports:{...ports,
    async sendOnce(request, context) {
      await hooks.beforeSend?.(context);
      context.assertActive();
      record('SEND', context);
      observed.sends++;
      assert.equal(request.receiver, receiver);
      return {status:'CREATED', receiver, bodySha256:manifest.bodySha256, generation:'4'};
    }}});
  const gate = createIssuerServiceEntrypoint({...pinned, service:'gate', ports:{...ports,
    invokeTransport(handoff, payload, context) {
      context.assertActive();
      record('HANDOFF', context);
      observed.handoffs++;
      const token = options.gateToken ?? credential('gate','transport');
      return transport({action:'dispatch', token, handoff, body:payload}, context);
    },
    async readIndependentClosure(binding, context) {
      context.assertActive();
      observed.closeoutReads++;
      await hooks.beforeClosure?.(context);
      return {binding, routeClosed:true, connectionsClosed:true, coverageComplete:true, requestCount:0,
        outcome:'ZERO_REQUESTS', receiverGeneration:null, evidenceSha256:digest('synthetic closure')};
    }}});
  const context = stop => ({deadlineMs:stop ? policy.stopDeadlineMs : policy.deadlineMs,
    signal:new AbortController().signal});
  const dispatchContext = { ...context(), policyDeadlineMs:policy.deadlineMs, assertActive() {} };
  return {observed, irreversible, objects, clock, hooks, gate, transport, context, dispatchContext};
}

function untouched(env) {
  assert.deepEqual(env.observed, {reads:0, writes:0, handoffs:0, sends:0, authorityReads:0, closeoutReads:0});
}

test('signed writer and gate identities reach the real gate and transport exactly once', async () => {
  const configBytes = rawConfig();
  const env = rig({configBytes});
  configBytes.fill(0); // The operator input is snapshotted and cannot be rebound after boot.
  assert.equal((await env.gate({action:'start', token:credential(), body}, env.context())).status, 'RECEIVER_ACCEPTED');
  assert.equal(env.observed.handoffs, 1);
  assert.equal(env.observed.writes, 3);
  assert.equal(env.observed.sends, 1);
});

test('only the signed stop and closer principals can invoke their distinct gate actions', async () => {
  const env = rig();
  assert.equal((await env.gate({action:'stop', token:credential()}, env.context(true))).status, 'REFUSED');
  assert.equal((await env.gate({action:'confirmStop', token:credential('stopper')}, env.context(true))).status, 'REFUSED');
  assert.equal((await env.gate({action:'start', token:credential('closer'), body}, env.context())).status, 'REFUSED');
  untouched(env);
  assert.equal((await env.gate({action:'stop', token:credential('stopper')}, env.context(true))).status, 'STOP_REQUESTED');
  assert.equal((await env.gate({action:'confirmStop', token:credential('closer')}, env.context(true))).status, 'STOPPED_CONFIRMED');
  assert.equal(env.observed.closeoutReads, 1);
  assert.equal(env.observed.handoffs, 0);
  assert.equal(env.observed.sends, 0);
});

test('wrong service, action, audience or caller cannot start authority, durable store or transport', async () => {
  const env = rig();
  const cases = [
    [env.gate, 'dispatch', credential('gate', 'transport')],
    [env.gate, 'unknown-action', credential()],
    [env.gate, 'start', credential('writer', 'transport')],
    [env.gate, 'start', credential('writer','gate',{aud:[audiences.gate]})],
    [env.transport, 'start', credential()],
    [env.transport, 'dispatch', credential('gate','gate')],
    [env.transport, 'dispatch', credential('writer','transport')],
  ];
  for (const [handle, action, token] of cases) {
    const context = handle === env.transport && action === 'dispatch' ? env.dispatchContext : env.context();
    assert.equal((await handle({action, token, body}, context)).status, 'REFUSED');
  }
  const dispatch = {action:'dispatch', token:credential('gate','transport'), body};
  assert.equal((await env.transport(dispatch, env.context())).status, 'REFUSED');
  assert.equal((await env.transport(dispatch, {...env.dispatchContext, deadlineMs:policy.deadlineMs + 1})).status, 'REFUSED');
  untouched(env);
});

test('missing, distinct or cross-project signed identity and issuer are refused', async () => {
  const env = rig();
  const changes = [{sub:undefined}, {sub:200000}, {sub:principals.stopper.subject}, {email:undefined},
    {email:principals.stopper.email}, {email:'fixture-writer@other-project.iam.gserviceaccount.com'},
    {email_verified:false}, {email_verified:'true'}, {email_verified:undefined}, {iss:undefined},
    {iss:'accounts.google.com'}, {iss:'https://other-issuer.example'}, {aud:undefined}];
  for (const claims of changes) {
    assert.equal((await env.gate({action:'start', token:credential('writer','gate',claims), body}, env.context())).status, 'REFUSED');
  }
  const headers = {authorization:credential(), 'x-forwarded-email':principals.writer.email};
  assert.equal((await env.gate({action:'start', headers, subject:principals.writer.subject, body}, env.context())).status, 'REFUSED');
  assert.equal((await env.gate({action:'start', token:credential(), body, trustedContext:env.context()})).status, 'REFUSED');
  untouched(env);
});

test('expired or premature claims, unknown key and altered signature fail closed', async () => {
  const env = rig();
  const good = credential();
  const [head, payload, signature] = good.split('.');
  const flipped = Buffer.from(signature, 'base64url');
  flipped[0] ^= 1;
  const malformed = [credential('writer','gate',{exp:seconds}), credential('writer','gate',{iat:seconds + 1}),
    credential('writer','gate',{exp:undefined}), credential('writer','gate',{nbf:seconds + 1}),
    credential('writer','gate',{exp:seconds + 121}), `${head}.${payload}.${flipped.toString('base64url')}`,
    credential('writer','gate',{}, {kid:'untrusted'}), credential('writer','gate',{}, {alg:'none'}),
    credential('writer','gate',{}, {jku:'https://untrusted.example/jwks'})];
  for (const token of malformed) {
    assert.equal((await env.gate({action:'start', token, body}, env.context())).status, 'REFUSED');
  }
  untouched(env);
  const staleConfig = Buffer.from(JSON.stringify({...config, keyset:{...config.keyset, validUntilMs:nowMs}}));
  const stale = rig({configBytes:staleConfig});
  assert.equal((await stale.gate({action:'start', token:good, body}, stale.context())).status, 'REFUSED');
  untouched(stale);
});

test('a bad config pin, missing keyset or mismatched issuer role fails at startup', () => {
  assert.throws(() => rig({configSha256:digest('different config')}), /IDENTITY_CONFIG/);
  const withoutKeyset = Buffer.from(JSON.stringify({...config, keyset:undefined}));
  assert.throws(() => rig({configBytes:withoutKeyset}), /IDENTITY_CONFIG/);
  const badEmail = {...principals, writer:{...principals.writer, email:'fixture-writer@other-project.iam.gserviceaccount.com'}};
  assert.throws(() => rig({configBytes:Buffer.from(JSON.stringify({...config, principals:badEmail}))}), /IDENTITY_CONFIG/);
  assert.throws(() => rig({policy:{...policy, actors:{...actorEmails, writer:actorEmails.stopper}}}), /ENTRYPOINT_ACTORS/);
});

test('credential expiry cancels pending verification before any durable operation', {timeout:3000}, async () => {
  const env = rig();
  const entered = Promise.withResolvers();
  const complete = Promise.withResolvers();
  env.hooks.beforeManifest = () => {
    entered.resolve();
    return complete.promise;
  };
  const token = credential('writer','gate',{exp:seconds + 1});
  const result = env.gate({action:'start', token, body}, env.context());
  await entered.promise;
  env.clock.advance(nowMs + 1000);
  assert.equal((await result).status, 'OPEN_UNKNOWN');
  complete.resolve();
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(env.observed, {reads:0, writes:0, handoffs:0, sends:0, authorityReads:1, closeoutReads:0});
});

const delayedExpiry = env => env.clock.advance(nowMs + 1000, {deliverTimers:false});
const operations = env => env.irreversible.map(item => item.action);

test('clock-only expiry fences each dispatch write, handoff and physical send', async () => {
  const reserved = ['ADMISSION_RESERVED'];
  const admitted = [...reserved, 'STARTED'];
  const handedOff = [...admitted, 'HANDOFF'];
  const granted = [...handedOff, 'EGRESS_GRANTED'];
  const cases = [
    {stopBefore:'reservation', keep:[]},
    {stopBefore:'physical reservation', keep:[]},
    {stopBefore:'admission', keep:reserved},
    {stopBefore:'handoff', keep:admitted},
    {stopBefore:'egress', keep:handedOff, shortGate:true},
    {stopBefore:'send port', keep:granted},
    {stopBefore:'send port', keep:granted, shortKeyset:true},
    {stopBefore:'physical send', keep:granted, shortGate:true},
  ];
  for (const {stopBefore, keep, shortGate, shortKeyset} of cases) {
    const expiry = nowMs + 1000;
    const configBytes = shortKeyset ? Buffer.from(JSON.stringify({...config, keyset:{...config.keyset, validUntilMs:expiry}})) : rawConfig();
    const env = rig({configBytes, gateToken:credential('gate','transport', shortGate ? {exp:seconds + 1} : {})});
    env.hooks.afterRead = key => {
      if ((stopBefore === 'reservation' && key === keys.stop) ||
          (stopBefore === 'handoff' && key === keys.stop && env.objects.has(keys.admission)) ||
          (stopBefore === 'egress' && key === keys.admission) ||
          (stopBefore === 'send port' && key === keys.stop && env.objects.has(keys.egress))) delayedExpiry(env);
    };
    env.hooks.afterCreate = kind => {
      if (stopBefore === 'admission' && kind === 'ADMISSION_RESERVED') delayedExpiry(env);
    };
    if (stopBefore === 'physical reservation') env.hooks.beforeCreate = (kind, context) => {
      assert.equal(kind, 'ADMISSION_RESERVED');
      assert.equal(context.deadlineMs, expiry);
      delayedExpiry(env);
      assert.equal(context.signal.aborted, false);
    };
    let physicalPortEntered = false;
    if (stopBefore === 'physical send') env.hooks.beforeSend = context => {
      physicalPortEntered = true;
      assert.equal(context.deadlineMs, expiry);
      delayedExpiry(env);
      assert.equal(context.signal.aborted, false, 'fixture did not deliver the timer');
    };
    const token = credential('writer','gate', shortGate || shortKeyset ? {} : {exp:seconds + 1});
    assert.equal((await env.gate({action:'start', token, body}, env.context())).status, 'OPEN_UNKNOWN', stopBefore);
    assert.equal(env.clock.now(), expiry, stopBefore);
    assert.equal(physicalPortEntered, stopBefore === 'physical send');
    assert.deepEqual(operations(env), keep, stopBefore);
    assert.equal(env.observed.sends, 0, stopBefore);
    assert.ok(env.irreversible.every(event => event.time < expiry), stopBefore);
    for (const event of env.irreversible) {
      assert.equal(event.policyDeadlineMs, policy.deadlineMs, stopBefore);
      assert.equal(event.deadlineMs, !shortGate || event.action === 'EGRESS_GRANTED' ? expiry : policy.deadlineMs, stopBefore);
    }
  }
});

test('delayed stopper expiry fences each of its three create slots', async () => {
  const sequence = ['REQUESTED','EGRESS_BLOCKED','STOP_BEFORE_SEND'];
  for (const [index, next] of sequence.entries()) {
    const env = rig();
    if (index === 0) env.hooks.beforeManifest = () => delayedExpiry(env);
    else env.hooks.afterCreate = kind => { if (kind === sequence[index - 1]) delayedExpiry(env); };
    const token = credential('stopper','gate',{exp:seconds + 1});
    assert.equal((await env.gate({action:'stop', token}, env.context(true))).status, 'OPEN_UNKNOWN', next);
    assert.equal(env.clock.now(), nowMs + 1000);
    assert.deepEqual(operations(env), sequence.slice(0, index));
    assert.ok(env.irreversible.every(event => event.time < nowMs + 1000 &&
      event.deadlineMs === nowMs + 1000 && event.policyDeadlineMs === policy.stopDeadlineMs));
  }
});

test('independent cleanup keeps its own authority and refuses an expired terminal create', async () => {
  const env = rig();
  env.hooks.afterRead = () => delayedExpiry(env);
  const writer = credential('writer','gate',{exp:seconds + 1});
  assert.equal((await env.gate({action:'start', token:writer, body}, env.context())).status, 'OPEN_UNKNOWN');
  assert.deepEqual(operations(env), []);
  delete env.hooks.afterRead;
  env.clock.advance(nowMs + 16_000, {deliverTimers:false});
  const stopper = credential('stopper','gate',{exp:seconds + 18});
  assert.equal((await env.gate({action:'stop', token:stopper}, env.context(true))).status, 'STOP_REQUESTED');
  assert.deepEqual(operations(env), ['REQUESTED','EGRESS_BLOCKED','STOP_BEFORE_SEND']);
  assert.ok(env.irreversible.every(event => event.deadlineMs === nowMs + 18_000 &&
    event.policyDeadlineMs === policy.stopDeadlineMs));

  env.hooks.beforeClosure = context => {
    assert.equal(context.deadlineMs, nowMs + 17_000);
    env.clock.advance(nowMs + 17_000, {deliverTimers:false});
    assert.equal(context.signal.aborted, false);
  };
  const expiredCloser = credential('closer','gate',{exp:seconds + 17});
  assert.equal((await env.gate({action:'confirmStop', token:expiredCloser}, env.context(true))).status, 'OPEN_UNKNOWN');
  assert.equal(env.objects.has(keys.terminal), false);
  delete env.hooks.beforeClosure;
  const currentCloser = credential('closer','gate',{exp:seconds + 19});
  assert.equal((await env.gate({action:'confirmStop', token:currentCloser}, env.context(true))).status, 'STOPPED_CONFIRMED');
  assert.deepEqual(env.irreversible.at(-1), {action:'STOPPED_CONFIRMED', time:nowMs + 17_000,
    deadlineMs:nowMs + 19_000, policyDeadlineMs:policy.stopDeadlineMs});
});
