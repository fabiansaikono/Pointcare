import assert from 'node:assert/strict';
import {createHash, generateKeyPairSync, sign} from 'node:crypto';
import {EventEmitter, once} from 'node:events';
import {request} from 'node:http';
import {Readable} from 'node:stream';
import {test} from 'node:test';
import {decision, decisionKeys, digest} from '../records.mjs';
import {managedBindingPath} from './managed-provider-ports.mjs';
import {bindIssuerServicePorts} from './protected-service-ports.mjs';
import {prepareManagedIssuer} from './startup.mjs';

const bytes = value => Buffer.from(JSON.stringify(value));
const roles = ['writer', 'stopper', 'closer', 'gate', 'transport'];
const projectId = 'issuer-local-fixture';
const issuer = 'https://accounts.google.com';
const principals = Object.fromEntries(roles.map((role, i) => [role,
  {subject: String(400000 + i), email: `fixture-${role}@${projectId}.iam.gserviceaccount.com`}]));
const actors = Object.fromEntries(roles.map(role => [role, principals[role].email]));
const audiences = {gate: 'https://gate.fixture.example', transport: 'https://transport.fixture.example'};
const {publicKey, privateKey} = generateKeyPairSync('rsa', {modulusLength: 2048});
const seconds = Math.floor(Date.now() / 1000);
const writerDeadline = (seconds + 25) * 1000;
const deadlineMs = (seconds + 45) * 1000;
const configBytes = bytes({projectId, issuer, audiences, principals, maxLifetimeSeconds: 120,
  keyset: {validFromMs: (seconds - 2) * 1000, validUntilMs: (seconds + 75) * 1000,
    keys: {fixture: publicKey.export({type: 'spki', format: 'pem'})}}});
const packageSha256 = digest('package');
const approvalSha256 = digest('approval');
const keys = decisionKeys(packageSha256, approvalSha256);
const body = Buffer.from('synthetic sealed body');
const bodySha256 = digest(body);
const receiver = 'gs://synthetic-issuer-sink/trials/synthetic/receiver.json';
const manifest = {purpose: 'R4_ISSUER_SYNTHETIC_ONLY', packageSha256, approvalSha256, keys, actors,
  runId: 'fixture-run', dispatchId: 'fixture-dispatch', claimSha256: digest('claim'), challengeSha256: digest('challenge'),
  receiver, bodySha256, gateRevision: 'fixture-gate', transportRevision: 'fixture-transport',
  notBeforeMs: (seconds - 2) * 1000, expiresAtMs: deadlineMs};
const manifestBytes = bytes(manifest);
const policy = {actors, receiver, gateRevision: manifest.gateRevision, transportRevision: manifest.transportRevision,
  manifestSha256: digest(manifestBytes), deadlineMs, stopDeadlineMs: deadlineMs + 5_000};
const authority = {...manifest, manifestSha256: policy.manifestSha256, pairKey: keys.pairKey};
const config = {configBytes, configSha256: digest(configBytes), policy, bodySha256, startBytes: body.length};
const envelopeBytes = 2_048;
const handoffObject = `decisions/sha256/${keys.pairKey}/post-ack-handoff.json`;
const evidenceBucket = 'issuer-evidence-fixture';
const trialId = 'synthetic';
const routeEvidenceSha256 = digest('synthetic route evidence');
const observerBoundary = 'synthetic-egress';
const controlScopes = ['runId', 'dispatchId', 'claimSha256', 'challengeSha256', 'pairKey', 'manifestSha256',
  'gateRevision', 'transportRevision', 'receiver', 'bodySha256', 'routeEvidenceSha256'];
const signingKeys = Object.fromEntries(['release', 'observer', 'index', 'closure-anchor'].map(role =>
  [role, generateKeyPairSync('rsa', {modulusLength: 2048})]));
const signer = role => signingKeys[role].publicKey.export({type: 'spki', format: 'pem'});
const authorizationScope = Object.fromEntries(controlScopes.map(key =>
  [key, key === 'routeEvidenceSha256' ? routeEvidenceSha256 : authority[key]]));
const {kind: ignored, ...decisionScope} = decision(authority, 'DECISION_ORIGINALS');
function signed(value, role) {
  const payload = bytes(value);
  return bytes({payloadBase64: payload.toString('base64'),
    signatureBase64: sign('RSA-SHA256', payload, signingKeys[role].privateKey).toString('base64')});
}
const source = name => ({bucket: evidenceBucket, object: `trials/${trialId}/${name}.json`});
const releaseData = {schema: 'POINCARE_R4_ISSUER_AUTHORIZATION_V1', scope: authorizationScope,
  notBeforeMs: manifest.notBeforeMs, expiresAtMs: deadlineMs, observerBoundary};
const releaseBytes = signed(releaseData, 'release');
const releaseSource = {...source('authorization'), generation: '31', metageneration: '1', sha256: digest(releaseBytes)};
const statusSource = source('dispatch-status');
const coverageSource = source('coverage');
const originalsSource = source('decision-originals');
const dispatch = {trialId, authorization: releaseSource, status: statusSource, coverage: coverageSource,
  releasePublicKey: signer('release'), observerPublicKey: signer('observer'), observerBoundary,
  statusMaxAgeMs: 50_000, coverageMaxAgeMs: 50_000};
const closureIdentity = {id: observerBoundary, bootSha256: digest('observer boot'), routeSha256: routeEvidenceSha256};
const closureKeys = Object.fromEntries(['ready', 'coverage', 'outcome'].map(role => [role, generateKeyPairSync('ed25519')]));
const closureSigners = Object.fromEntries(Object.entries(closureKeys).map(([role, key]) => {
  const raw = key.publicKey.export({type: 'spki', format: 'der'});
  return [role, {publicKeySpkiBase64: raw.toString('base64'), publicKeySha256: digest(raw)}];
}));
const closureAnchor = source('closure-anchor');
const closureSlots = {ready: source('closure-ready'), coverage: source('stop-coverage'), outcome: source('transport-outcome')};
const closureConfig = {anchor: {source: closureAnchor, publicKey: signer('closure-anchor')},
  originals: Object.values(closureSlots),
  config: {stopDeadlineMs: policy.stopDeadlineMs, maxAgeMs: 55_000, observer: closureIdentity, signers: closureSigners}};
const stoppedState = {requestGeneration: '41', reservationGeneration: null, admissionGeneration: '42',
  egressGeneration: '43', admissionKind: 'STOP_BEFORE_SEND', egressKind: 'EGRESS_BLOCKED'};
const stopEvidence = {requestCount: 0, outcome: 'ZERO_REQUESTS', receiverGeneration: null};
const terminalExpiryModes = ['terminal-anchor-expiry', 'terminal-ready-expiry', 'terminal-coverage-expiry'];
const revokedStopModes = ['revoked', 'revoked-closed', 'closure-gap', 'closure-anchor-read-expiry', ...terminalExpiryModes];

function closureFixture(mode, shortDeadline) {
  const binding = decision(authority, 'STOP_EVIDENCE_BINDING', stoppedState);
  const at = seconds * 1000;
  const events = [];
  function append(kind, atMs, fields = {}) {
    const event = {sequence: events.length + 1, previousSha256: events.at(-1)?.sha256 ?? null, kind, atMs, ...fields};
    events.push({...event, sha256: digest(bytes(event))});
  }
  append('COVERAGE_BEGIN', manifest.notBeforeMs);
  append('FENCE_CLOSED', at, {stopRequestGeneration: '41'});
  append('COVERAGE_END', at);
  const originals = new Map();
  function original(role, value, generation) {
    const raw = bytes({body: value, signature: sign(null, bytes(value), closureKeys[role].privateKey).toString('base64')});
    const reference = {...closureSlots[role], generation, metageneration: '1', sha256: digest(raw)};
    originals.set(reference.object, {reference, raw});
    return reference;
  }
  const coverage = original('coverage', {schema: 'POINCARE_ISSUER_COVERAGE_V1', binding,
    observer: closureIdentity, receiver, bodySha256, coverageComplete: mode !== 'closure-gap', events}, '52');
  const outcome = original('outcome', {schema: 'POINCARE_ISSUER_TRANSPORT_OUTCOME_V1', binding,
    receiver, bodySha256, ...stopEvidence, connectionId: null, final: true, finalAtMs: at}, '53');
  const ready = original('ready', {schema: 'POINCARE_ISSUER_CLOSURE_READY_V1', stage: 'READY_BEFORE_TERMINAL',
    binding, coverage, outcome, readyAtMs: mode === 'terminal-coverage-expiry' ? at + 25 : at,
    expiresAtMs: mode === 'terminal-ready-expiry' ? shortDeadline : policy.stopDeadlineMs}, '51');
  const anchor = signed({schema: 'POINCARE_R4_ISSUER_CLOSURE_ANCHOR_V1', binding, readyReference: ready,
    issuedAtMs: at,
    expiresAtMs: ['terminal-anchor-expiry', 'closure-anchor-read-expiry'].includes(mode) ?
      shortDeadline : policy.stopDeadlineMs}, 'closure-anchor');
  return {originals, anchor, ready};
}

function token(role, service) {
  const {email, subject} = principals[role];
  const content = [{alg: 'RS256', typ: 'JWT', kid: 'fixture'},
    {iss: issuer, aud: audiences[service], sub: subject, email, email_verified: true,
      iat: seconds - 1, exp: role === 'writer' ? writerDeadline / 1000 : seconds + 65}];
  const input = content.map(field => bytes(field).toString('base64url')).join('.');
  return `${input}.${sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`;
}

function post(port, path, credential, body, signal) {
  return new Promise((resolve, reject) => {
    const call = request({hostname: '127.0.0.1', port, path, method: 'POST', agent: false, signal,
      headers: {authorization: `Bearer ${credential}`, 'content-type': 'application/octet-stream',
        'content-length': body.length, 'x-deadline-ms': String(deadlineMs)}}, async response => {
      try {
        const chunks = [];
        for await (const chunk of response) chunks.push(chunk);
        resolve({code: response.statusCode, value: JSON.parse(Buffer.concat(chunks).toString())});
      } catch (error) { reject(error); }
    });
    call.setTimeout(2_500, () => call.destroy(Error('OFFLINE_HTTP_TIMEOUT')));
    call.once('error', reject);
    call.end(body);
  });
}

// The synthetic provider gets exact precomputed decision pins before it receives any request.
function decisions(mode, closure) {
  const bucket = evidenceBucket;
  const planned = new Map([
    [keys.reservation, {generation: '1', value: decision(authority, 'ADMISSION_RESERVED')}],
    [keys.admission, {generation: '2', value: decision(authority, 'STARTED', {reservationGeneration: '1'})}],
    [keys.egress, {generation: '3', value: decision(authority, 'EGRESS_GRANTED',
      {reservationGeneration: '1', admissionGeneration: '2'})}],
  ]);
  if (revokedStopModes.includes(mode)) {
    planned.set(keys.stop, {generation: '41', value: decision(authority, 'REQUESTED')});
    planned.set(keys.admission, {generation: '42', value: decision(authority, 'STOP_BEFORE_SEND')});
    planned.set(keys.egress, {generation: '43', value: decision(authority, 'EGRESS_BLOCKED')});
    planned.set(keys.terminal, {generation: '44', value: decision(authority, 'STOPPED_CONFIRMED',
      {...stoppedState, ...stopEvidence, evidenceSha256: closure.ready.sha256})});
  }
  const committed = new Map();
  const response = (req, status, body) => ({url: req.url, redirected: false, status,
    headers: new Headers({'content-type': 'application/json'}), body});
  return {committed, async sendStorage(req) {
    req.assertActive();
    const url = new URL(req.url);
    assert.equal(url.origin, 'https://storage.googleapis.com');
    const creating = req.method === 'POST';
    const key = creating ? url.searchParams.get('name') : decodeURIComponent(url.pathname.split('/').at(-1));
    if (creating) {
      assert.equal(url.pathname, `/upload/storage/v1/b/${bucket}/o`);
      assert.equal(url.searchParams.get('ifGenerationMatch'), '0');
      if (committed.has(key)) return response(req, 412, bytes({}));
      const pin = planned.get(key);
      assert.deepEqual(req.body, bytes(pin.value));
      committed.set(key, pin);
      if (mode === 'lost' && key === keys.admission) throw Error('SYNTHETIC_ACK_LOST');
    }
    const object = committed.get(key);
    if (!object) {
      const message = `No such object: ${bucket}/${key}`;
      return response(req, 404, bytes({error: {code: 404, message, errors: [{reason: 'notFound', message}]}}));
    }
    const raw = bytes(object.value);
    if (url.searchParams.get('alt') === 'media') {
      assert.equal(url.searchParams.get('generation'), object.generation);
      assert.equal(url.searchParams.get('ifGenerationMatch'), object.generation);
      return response(req, 200, raw);
    }
    return response(req, 200, bytes({bucket, name: key, generation: object.generation, metageneration: '1',
      size: String(raw.length), contentType: 'application/json', md5Hash: createHash('md5').update(raw).digest('base64')}));
  }};
}

const md5 = data => createHash('md5').update(data).digest('base64');

function requestFixture(handle, prepare = () => {}) {
  return (url, options, callback) => {
    const req = new EventEmitter();
    req.destroy = () => {};
    prepare(url);
    req.end = body => Promise.resolve().then(async () => {
      options.signal.throwIfAborted();
      const reply = await handle(url, options, body);
      const response = Readable.from([reply.body]);
      const headers = reply.headers ?? new Headers({'content-type': 'application/json'});
      Object.assign(response, {complete: true, statusCode: reply.status,
        headersDistinct: Object.fromEntries(Array.from(headers, ([key, value]) => [key, [value]]))});
      callback(response);
    }).catch(error => req.emit('error', error));
    return req;
  };
}

function startupOptions(service, servicePorts, object = handoffObject, control = controlConfig(service)) {
  const bindingBytes = bytes({schema: 'POINCARE_R4_SERVICE_PORTS_V2', service, sourceRef: managedBindingPath,
    manifestBase64: manifestBytes.toString('base64'), transportOrigin: audiences.transport, envelopeBytes,
    evidenceBucket, handoffObject: object, routeEvidenceSha256, controlEvidence: control});
  const raw = bytes({schema: 'POINCARE_R4_ISSUER_STARTUP_V1', service,
    credentialPresentation: 'AUTHORIZATION_COMPACT_RS256', identityBase64: configBytes.toString('base64'),
    identitySha256: config.configSha256, policy, startBytes: body.length, bodySha256,
    ...(service === 'transport' ? {envelopeBytes} : {}),
    portsBinding: {sourceRef: managedBindingPath, sha256: digest(bindingBytes)}});
  return {env: {PORT: '8099', ISSUER_STARTUP_SHA256: digest(raw)}, readConfig: async () => raw,
    servicePorts: {readBinding: async () => bindingBytes, ...servicePorts}};
}

function controlConfig(service) {
  return {dispatch, decisionOriginals: {source: originalsSource, publicKey: signer('index')},
    ...(service === 'gate' ? {closure: closureConfig} : {})};
}

async function listen(t, {server}) {
  assert.equal(server.listening, false);
  const ready = once(server, 'listening');
  server.listen(0, '127.0.0.1');
  await ready;
  t.after(() => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())));
  return server.address().port;
}

async function fixture(t, mode) {
  let currentMs = seconds * 1000 + 50;
  const now = () => currentMs;
  const shortDeadline = (seconds + 10) * 1000;
  const cutoffCase = /^(gate|receiver)-(expiry|active)-(grant|status|coverage)$/u.exec(mode);
  const [, cutoffPath, cutoffMode, shortSource] = cutoffCase ?? [];
  const handoffDeadline = shortSource === 'grant' || cutoffPath === 'gate' ? shortDeadline : writerDeadline;
  const pinnedRelease = shortSource === 'grant' ? signed({...releaseData, expiresAtMs: shortDeadline}, 'release') : releaseBytes;
  const pinnedReleaseSource = {...releaseSource, sha256: digest(pinnedRelease)};
  const fixtureConfig = service => ({...controlConfig(service),
    dispatch: {...dispatch, authorization: pinnedReleaseSource},
    ...(service === 'gate' && mode === 'terminal-coverage-expiry' ? {closure: {...closureConfig,
      config: {...closureConfig.config, maxAgeMs: 9_999}}} : {})});
  const closure = closureFixture(mode, shortDeadline);
  const provider = decisions(mode, closure);
  const handoffs = [];
  const sends = [];
  let evidence;
  let evidenceRef;
  let evidenceReads = 0;
  let reviewerReads = 0;
  let evidenceCreates = 0;
  let transport;
  let dispatches = 0;
  let credentialCalls = 0;
  let statusAfterEgress = 0;
  let delayedPreparations = 0;
  let closureOutcomeRead = false;
  let terminalCredential = false;
  let terminalPosts = 0;
  const controlReads = [];
  const evidencePath = `/storage/v1/b/${evidenceBucket}/o/${encodeURIComponent(handoffObject)}`;
  const evidenceMetadata = (raw, changes) => bytes({bucket: evidenceBucket, name: handoffObject,
    metageneration: '1', generation: '81', size: String(raw.length), contentType: 'application/json',
    md5Hash: md5(raw), ...changes});

  function readControlFixture(url) {
    const object = decodeURIComponent(url.pathname.split('/').at(-1));
    const exactClosure = closure.originals.get(object);
    const match = [releaseSource, statusSource, coverageSource, originalsSource, closureAnchor].find(ref => ref.object === object)
      ?? exactClosure?.reference;
    if (!match) return null;
    const media = url.searchParams.get('alt') === 'media';
    controlReads.push({object, media});
    const records = Array.from(provider.committed, ([key, record]) => ({bucket: evidenceBucket, object: key,
      generation: record.generation, metageneration: '1', sha256: digest(bytes(record.value))}));
    const enabled = !revokedStopModes.includes(mode) &&
      !(mode === 'revoked-after-reservation' && provider.committed.has(keys.reservation)) &&
      !(mode === 'revoked-after-post-ack' && evidenceCreates > 0) &&
      !(mode === 'revoked-before-send' && provider.committed.has(keys.egress) && statusAfterEgress >= 1);
    const currentBase = {scope: authorizationScope, authorizationSha256: pinnedReleaseSource.sha256,
      observedAtMs: seconds * 1000, validUntilMs: deadlineMs};
    const shortEvidence = cutoffPath === 'gate' || (cutoffPath === 'receiver' && provider.committed.has(keys.egress));
    let raw;
    let generation;
    if (object === releaseSource.object) {
      generation = '31';
      raw = mode === 'authorization-substituted' ? signed({...releaseData, scope: {...authorizationScope, runId: 'other-run'}},
        'release') : pinnedRelease;
    } else if (object === statusSource.object) {
      const shorterReceiverStatus = shortEvidence && cutoffPath === 'receiver' && shortSource === 'status';
      generation = shorterReceiverStatus || (provider.committed.has(keys.egress) && statusAfterEgress > 0) ? '38' : '32';
      const shortened = mode === 'gate-shortened-status' && evidenceCreates > 0;
      raw = signed({schema: 'POINCARE_R4_ISSUER_DISPATCH_STATUS_V1', ...currentBase, state: enabled ? 'ENABLED' : 'REVOKED',
        scope: mode === 'wrong-status-scope' ? {...authorizationScope, runId: 'other-run'} : authorizationScope,
        observedAtMs: shortened || shorterReceiverStatus || (!enabled && generation === '38') ?
          seconds * 1000 + 1 : currentBase.observedAtMs,
        validUntilMs: mode === 'stale-status' ? seconds * 1000 :
          (shortened || (shortEvidence && shortSource === 'status')) ? shortDeadline : currentBase.validUntilMs}, 'release');
    } else if (object === coverageSource.object) {
      const shorterReceiverCoverage = shortEvidence && cutoffPath === 'receiver' && shortSource === 'coverage';
      generation = shorterReceiverCoverage ? '39' : '33';
      const gap = mode === 'coverage-gap' || (mode === 'coverage-gap-before-send' && statusAfterEgress >= 1);
      raw = signed({schema: 'POINCARE_R4_ISSUER_EGRESS_COVERAGE_V1', ...currentBase, observerBoundary,
        state: gap ? 'GAP' : 'ARMED', coverageFromMs: manifest.notBeforeMs,
        observedAtMs: shorterReceiverCoverage ? seconds * 1000 + 1 : currentBase.observedAtMs,
        validUntilMs: mode === 'coverage-stale' ? seconds * 1000 :
          (shortEvidence && shortSource === 'coverage') ? shortDeadline : currentBase.validUntilMs},
      mode === 'coverage-wrong-signer' ? 'release' : 'observer');
    } else if (object === originalsSource.object) {
      generation = String(340 + records.length);
      raw = signed({schema: 'POINCARE_R4_ISSUER_DECISION_ORIGINALS_V1', scope: decisionScope,
        issuedAtMs: seconds * 1000 + records.length,
        records: mode === 'missing-decision-pin' ? records.filter(row => row.object !== keys.admission) : records}, 'index');
    } else if (object === closureAnchor.object) {
      if (mode === 'revoked') return {status: 404, body: bytes({})};
      generation = '50';
      raw = closure.anchor;
    } else {
      generation = exactClosure.reference.generation;
      raw = exactClosure.raw;
    }
    if (mode === 'status-missing' && object === statusSource.object) return {status: 404, body: bytes({})};
    const pinned = object === releaseSource.object || Boolean(exactClosure);
    assert.equal(url.searchParams.get('generation'), pinned ? generation : null);
    assert.equal(url.searchParams.get('ifGenerationMatch'), media || pinned ? generation : null);
    assert.equal(url.searchParams.get('ifMetagenerationMatch'), media || pinned ? '1' : null);
    if (media) {
      if (object === closureSlots.outcome.object) closureOutcomeRead = true;
      if (mode === 'closure-anchor-read-expiry' && object === closureSlots.ready.object) {
        delayedPreparations++;
        currentMs = shortDeadline;
      }
      if (mode === 'status-drift' && object === statusSource.object) return {status: 412, body: bytes({})};
      if (object === statusSource.object && provider.committed.has(keys.egress)) statusAfterEgress++;
      return {status: 200, body: raw};
    }
    return {status: 200, body: bytes({bucket: evidenceBucket, name: object, generation, metageneration: '1',
      size: String(raw.length), contentType: 'application/json', md5Hash: md5(raw)})};
  }

  function readEvidence(url, options) {
    assert.equal(url.href.startsWith('https://storage.googleapis.com' + evidencePath + '?'), true);
    assert.equal(options.method, 'GET');
    const reviewer = options.headers.Authorization === 'Bearer synthetic-reviewer';
    if (reviewer) reviewerReads++;
    else {
      assert.equal(options.headers.Authorization, 'Bearer synthetic-oauth');
      evidenceReads++;
    }
    const media = url.searchParams.get('alt') === 'media';
    if (media || reviewer) {
      assert.equal(url.searchParams.get('generation'), '81');
      assert.equal(url.searchParams.get('ifGenerationMatch'), '81');
      assert.equal(url.searchParams.get('ifMetagenerationMatch'), '1');
    } else {
      assert.deepEqual([...url.searchParams.keys()], ['fields']);
    }
    if (!evidence || (!reviewer && mode === 'missing-evidence')) return {status: 404, body: bytes({})};
    if (reviewer) return {status: 200, body: media ? evidence : evidenceMetadata(evidence)};

    const row = JSON.parse(evidence);
    const stored = mode === 'stale-record' ? bytes({...row, admissionGeneration: '7'}) :
      mode === 'wrong-record-cutoff' ? bytes({...row, effectiveDeadlineMs: writerDeadline - 1000}) : evidence;
    if (!media) {
      const changes = mode === 'wrong-slot' ? {name: `decisions/sha256/${digest('other-pair')}/post-ack-handoff.json`} :
        mode === 'invalid-generation' ? {generation: '0'} :
        mode === 'wrong-metageneration' ? {metageneration: '2'} : {};
      return {status: 200, body: evidenceMetadata(stored, changes)};
    }
    if (mode === 'generation-drift') return {status: 412, body: bytes({})};
    if (mode === 'missing-media') return {status: 404, body: bytes({})};
    if (mode === 'wrong-media-generation') {
      return {status: 200, body: stored, headers: new Headers({'x-goog-generation': '82'})};
    }
    const reply = Buffer.from(stored);
    if (mode === 'substituted-media') reply[0] ^= 1;
    return {status: 200, body: reply};
  }

  const providerRequests = {
    http: requestFixture(async (url, options) => {
      credentialCalls++;
      assert.equal(url.origin, 'http://metadata.google.internal');
      assert.equal(options.method, 'GET');
      assert.equal(options.headers['Metadata-Flavor'], 'Google');
      const identity = url.pathname.endsWith('/identity');
      assert.equal(url.pathname, '/computeMetadata/v1/instance/service-accounts/default/' + (identity ? 'identity' : 'token'));
      if (identity) {
        assert.equal(url.searchParams.get('audience'), audiences.transport);
        assert.equal(url.searchParams.get('format'), 'full');
        assert.equal(provider.committed.get(keys.admission).generation, '2');
      }
      if (!identity && cutoffPath === 'receiver' && cutoffMode === 'expiry' && statusAfterEgress === 2) {
        // The last signed check has completed; this is the receiver write's OAuth request.
        await Promise.resolve();
        delayedPreparations++;
        currentMs = shortDeadline;
      }
      if (!identity && terminalCredential) {
        terminalCredential = false;
        await Promise.resolve();
        delayedPreparations++;
        currentMs = shortDeadline;
      }
      return {status: 200, headers: new Headers({'metadata-flavor': 'Google', 'content-type': 'application/json'}),
        body: identity ? Buffer.from(token('gate', 'transport')) :
          bytes({access_token: 'synthetic-oauth', token_type: 'Bearer', expires_in: 60})};
    }),
    https: requestFixture(async (url, options, raw) => {
      assert.equal(options.agent, false);
      if (url.origin === audiences.transport) {
        dispatches++;
        assert.equal(url.href, audiences.transport + '/v1/dispatch');
        assert.equal(options.method, 'POST');
        assert.equal(options.headers['Content-Type'], 'application/octet-stream');
        assert.equal(options.headers.Authorization, 'Bearer ' + token('gate', 'transport'));
        assert.equal(JSON.parse(evidence).envelopeSha256, digest(raw));
        handoffs.push(Buffer.from(raw));
        const envelope = JSON.parse(raw);
        let payload = raw;
        if (mode === 'altered') payload = bytes({...envelope, deadlineMs: writerDeadline + 1000});
        if (mode === 'expired') payload = bytes({...envelope, deadlineMs: now() - 1});
        if (mode === 'caller-reference') payload = bytes({...envelope, generation: '81', sha256: digest(evidence)});
        const credential = token(mode === 'unauthorized' ? 'writer' : 'gate', 'transport');
        const {code, value} = await post(transport, '/v1/dispatch', credential, payload, options.signal);
        return {status: code, body: bytes(value)};
      }
      assert.equal(url.origin, 'https://storage.googleapis.com');
      assert.equal(options.headers.Authorization, 'Bearer synthetic-oauth');
      if (options.method === 'GET') {
        const original = readControlFixture(url);
        if (original) return original;
      }
      if (url.pathname === `/upload/storage/v1/b/${evidenceBucket}/o` && url.searchParams.get('name') === handoffObject) {
        evidenceCreates++;
        assert.equal(options.method, 'POST');
        assert.equal(url.searchParams.get('ifGenerationMatch'), '0');
        assert.equal(provider.committed.get(keys.admission).generation, '2');
        const row = JSON.parse(raw);
        assert.equal(row.reservationGeneration, '1');
        assert.equal(row.admissionGeneration, '2');
        assert.equal(row.effectiveDeadlineMs, handoffDeadline);
        assert.equal(row.transportUrl, audiences.transport + '/v1/dispatch');
        assert.equal(row.actorRole, 'gate');
        assert.equal(row.bodySha256, bodySha256);
        if (mode === 'evidence-conflict') return {status: 412, body: bytes({})};
        evidence = Buffer.from(raw);
        evidenceRef = Object.freeze({bucket: evidenceBucket, object: handoffObject, generation: '81',
          sha256: digest(evidence)});
        if (mode === 'evidence-ack-lost') throw Error('SYNTHETIC_POST_ACK_CREATE_RESPONSE_LOST');
        return {status: 200, body: evidenceMetadata(evidence)};
      }
      if (url.pathname === evidencePath) return readEvidence(url, options);
      if (url.pathname === '/upload/storage/v1/b/synthetic-issuer-sink/o') {
        const object = 'trials/synthetic/receiver.json';
        assert.equal(options.method, 'POST');
        assert.equal(url.searchParams.get('name'), object);
        assert.equal(url.searchParams.get('ifGenerationMatch'), '0');
        assert.equal(url.searchParams.get('uploadType'), 'media');
        assert.deepEqual(raw, body);
        sends.push(raw);
        if (mode === 'receiver-redirect') return {status: 302, body: bytes({})};
        return {status: 200, body: bytes({bucket: 'synthetic-issuer-sink', name: object,
          metageneration: '1', generation: '99', size: String(body.length), contentType: 'application/octet-stream',
          md5Hash: md5(mode === 'receiver-bad-ack' ? Buffer.from('wrong') : raw)})};
      }
      if (options.method === 'POST' && url.searchParams.get('name') === keys.terminal) terminalPosts++;
      if (terminalExpiryModes.includes(mode) && closureOutcomeRead && delayedPreparations === 0 &&
          options.method === 'GET' && url.pathname.endsWith('/' + encodeURIComponent(keys.egress)) &&
          url.searchParams.get('alt') === 'media') terminalCredential = true;
      return provider.sendStorage({method: options.method, url: url.href, body: raw,
        assertActive() { options.signal.throwIfAborted(); }});
    }, url => {
      if (url.origin === audiences.transport && cutoffPath === 'gate' && cutoffMode === 'expiry') {
        // The final authorization just passed; expire it during ClientRequest preparation.
        delayedPreparations++;
        currentMs = shortDeadline;
      }
    }),
  };
  const servicePorts = {providerRequests};
  const gateReady = await prepareManagedIssuer('gate',
    {...startupOptions('gate', servicePorts, handoffObject, fixtureConfig('gate')), now});
  const transportReady = await prepareManagedIssuer('transport',
    {...startupOptions('transport', servicePorts, handoffObject, fixtureConfig('transport')), now});
  assert.equal(credentialCalls, 0);
  transport = await listen(t, transportReady);
  const gate = await listen(t, gateReady);
  return {gate, transport, handoffs, sends, committed: provider.committed, controlReads,
    get dispatches() { return dispatches; }, get evidenceCreates() { return evidenceCreates; },
    get evidenceReads() { return evidenceReads; }, get evidenceRef() { return evidenceRef; },
    get reviewerReads() { return reviewerReads; }, get delayedPreparations() { return delayedPreparations; },
    get terminalPosts() { return terminalPosts; },
    shortDeadline, handoffDeadline,
    async envelopeProof(raw) {
      const options = startupOptions('transport', servicePorts, handoffObject, fixtureConfig('transport')).servicePorts;
      const {openTransportEnvelope} = await bindIssuerServicePorts({...config, service: 'transport', envelopeBytes,
        sourceRef: managedBindingPath, expectedSha256: digest(await options.readBinding()), now}, options);
      return openTransportEnvelope(raw, {actor: {role: 'gate', subject: principals.gate.subject},
        deadlineMs, policyDeadlineMs: deadlineMs, signal: new AbortController().signal, assertActive() {}});
    },
    reopenForReviewer(reference) {
      const {bucket, object, generation, sha256} = reference;
      const url = new URL(`/storage/v1/b/${bucket}/o/${encodeURIComponent(object)}`, 'https://storage.googleapis.com');
      const where = {generation, ifGenerationMatch: generation, ifMetagenerationMatch: '1'};
      const options = {method: 'GET', headers: {Authorization: 'Bearer synthetic-reviewer'}};
      url.search = new URLSearchParams(where);
      const metadata = readEvidence(url, options);
      assert.equal(metadata.status, 200);
      const record = JSON.parse(metadata.body);
      assert.deepEqual([record.bucket, record.name, record.generation, record.metageneration], [bucket, object, generation, '1']);
      url.search = new URLSearchParams({...where, alt: 'media'});
      const media = readEvidence(url, options);
      assert.equal(media.status, 200);
      assert.equal(md5(media.body), record.md5Hash);
      assert.equal(digest(media.body), sha256);
      return JSON.parse(media.body);
    }};
}

for (const service of ['gate', 'transport']) {
  test(`${service} normal adapter refuses missing protected Control references`, async () => {
    const options = startupOptions(service, {providerRequests: {http() { assert.fail('unexpected metadata'); },
      https() { assert.fail('unexpected provider request'); }}});
    await assert.rejects(prepareManagedIssuer(service, startupOptions(service,
      {providerRequests: options.servicePorts.providerRequests}, handoffObject, {})),
      /ISSUER_CONTROL_CONFIG/u);
    const changed = await options.servicePorts.readBinding();
    changed[0] ^= 1;
    options.servicePorts.readBinding = async () => changed;
    await assert.rejects(prepareManagedIssuer(service, options), /SOURCE_PIN/u);
    const otherObject = handoffObject.replace(keys.pairKey, digest('other-pair'));
    await assert.rejects(prepareManagedIssuer(service, startupOptions(service, {}, otherObject)), /HANDOFF_OBJECT/u);
  });
}

const refusedReads = {altered: 2, expired: 0, 'caller-reference': 0, unauthorized: 0, 'missing-decision-pin': 2,
  'missing-evidence': 1, 'wrong-slot': 1, 'invalid-generation': 1, 'wrong-metageneration': 1,
  'generation-drift': 2, 'missing-media': 2, 'wrong-media-generation': 2, 'substituted-media': 2,
  'stale-record': 2, 'wrong-record-cutoff': 2};

for (const mode of [...revokedStopModes, 'revoked-after-reservation', 'revoked-after-post-ack',
  'gate-shortened-status',
  'revoked-before-send', 'stale-status', 'status-missing', 'status-drift', 'wrong-status-scope', 'coverage-gap',
  'coverage-gap-before-send', 'coverage-stale', 'coverage-wrong-signer', 'authorization-substituted']) {
  test(`normal services enforce Control evidence for ${mode}`, {timeout: 10_000}, async t => {
    const state = await fixture(t, mode);
    const response = await post(state.gate, '/v1/start', token('writer', 'gate'), body);
    assert.equal(state.sends.length, 0);
    if (mode === 'revoked-before-send' || mode === 'coverage-gap-before-send') {
      assert.equal(state.dispatches, 1);
      assert.equal(state.committed.get(keys.egress).value.kind, 'EGRESS_GRANTED');
      assert.deepEqual(response, {code: 409, value: {status: 'OPEN_UNKNOWN'}});
      return;
    }
    assert.equal(state.dispatches, 0);
    if (mode === 'revoked-after-post-ack' || mode === 'gate-shortened-status') {
      assert.equal(state.evidenceCreates, 1);
      assert.equal(state.committed.get(keys.admission).value.kind, 'STARTED');
      assert.deepEqual(response, {code: 409, value: {status: 'OPEN_UNKNOWN'}});
      return;
    }
    assert.equal(state.evidenceCreates, 0);
    assert.equal(state.committed.has(keys.admission), false);
    if (mode === 'revoked-after-reservation') {
      assert.equal(response.value.status, 'CONSUMED_NO_HANDOFF');
      assert.equal(state.committed.get(keys.reservation).value.kind, 'ADMISSION_RESERVED');
      return;
    }
    assert.equal(response.code, 403);
    assert.equal(state.committed.has(keys.reservation), false);
    if (revokedStopModes.includes(mode)) {
      const readsBeforeStop = state.controlReads.length;
      const stop = await post(state.gate, '/v1/stop', token('stopper', 'gate'), Buffer.alloc(0));
      assert.equal(stop.value.status, 'STOP_REQUESTED');
      assert.equal(state.controlReads.length, readsBeforeStop);
      const close = await post(state.gate, '/v1/confirm-stop', token('closer', 'gate'), Buffer.alloc(0));
      assert.equal(close.value.status, mode === 'revoked-closed' ? 'STOPPED_CONFIRMED' : 'OPEN_UNKNOWN');
      assert.equal(state.committed.has(keys.terminal), mode === 'revoked-closed');
      assert.equal(state.terminalPosts, mode === 'revoked-closed' ? 1 : 0);
      if (mode.includes('expiry')) assert.equal(state.delayedPreparations, 1);
      if (mode === 'terminal-coverage-expiry') {
        assert(policy.stopDeadlineMs > state.shortDeadline);
        assert.equal(seconds * 1000 + 9_999 + 1, state.shortDeadline);
        assert(seconds * 1000 + 25 + 9_999 + 1 > state.shortDeadline);
        assert.equal(state.sends.length, 0);
      }
      const closureReads = state.controlReads.slice(readsBeforeStop);
      assert.ok(closureReads.every(read => ![releaseSource.object, statusSource.object, coverageSource.object].includes(read.object)));
    }
  });
}

for (const path of ['gate', 'receiver']) {
  for (const evidence of ['grant', 'status', 'coverage']) {
    test(`${path} refuses a POST when ${evidence} expires during final preparation`, {timeout: 10_000}, async t => {
      const state = await fixture(t, `${path}-expiry-${evidence}`);
      const reply = await post(state.gate, '/v1/start', token('writer', 'gate'), body);
      assert.deepEqual(reply, {code: 409, value: {status: 'OPEN_UNKNOWN'}});
      assert.equal(state.delayedPreparations, 1);
      assert.equal(state.evidenceCreates, 1);
      assert.equal(state.sends.length, 0);
      assert.equal(state.dispatches, path === 'gate' ? 0 : 1);
      assert.equal(state.committed.has(keys.egress), path === 'receiver');
      if (path === 'receiver') assert.equal(JSON.parse(state.handoffs[0]).deadlineMs, state.handoffDeadline);
    });
  }
}

test('a still-valid short signed status is sealed into the Gate envelope', {timeout: 10_000}, async t => {
  const state = await fixture(t, 'gate-active-status');
  const reply = await post(state.gate, '/v1/start', token('writer', 'gate'), body);
  assert.equal(reply.code, 200);
  assert.equal(reply.value.status, 'RECEIVER_ACCEPTED');
  assert.equal(JSON.parse(state.handoffs[0]).deadlineMs, state.shortDeadline);
  assert.equal(state.reopenForReviewer(state.evidenceRef).effectiveDeadlineMs, state.shortDeadline);
  assert.equal(state.dispatches, 1);
  assert.equal(state.sends.length, 1);
});

for (const mode of ['valid', 'lost', 'evidence-conflict', 'evidence-ack-lost', 'receiver-redirect', 'receiver-bad-ack',
  ...Object.keys(refusedReads)]) {
  test(`protected service composition with ${mode} original handoff`, {timeout: 10_000}, async t => {
    const state = await fixture(t, mode);
    const {gate, transport, handoffs, sends, committed} = state;
    const response = await post(gate, '/v1/start', token('writer', 'gate'), body);
    const [original] = handoffs;
    assert.equal(committed.has(keys.admission), true);
    if (mode === 'lost' || mode === 'evidence-conflict' || mode === 'evidence-ack-lost') {
      assert.deepEqual(response, {code: 409, value: {status: 'OPEN_UNKNOWN'}});
      assert.equal(handoffs.length, 0);
      assert.equal(sends.length, 0);
      assert.equal(state.dispatches, 0);
      assert.equal(state.evidenceCreates, mode === 'lost' ? 0 : 1);
      assert.equal(state.evidenceReads, 0);
      if (mode === 'lost') {
        const readback = bytes({schema: 'POINCARE_R4_ORIGINAL_HANDOFF_V1',
          handoff: decision(authority, 'STARTED_HANDOFF', {reservationGeneration: '1', admissionGeneration: '2'}),
          bodyBase64: body.toString('base64'), deadlineMs: writerDeadline, policyDeadlineMs: deadlineMs});
        assert.equal((await post(transport, '/v1/dispatch', token('gate', 'transport'), readback)).code, 403);
        assert.equal(state.evidenceReads, 1);
      }
      if (mode === 'evidence-ack-lost') {
        assert.equal(state.reopenForReviewer(state.evidenceRef).effectiveDeadlineMs, writerDeadline);
        assert.equal(state.reviewerReads, 2);
        assert.equal(state.evidenceReads, 0);
        assert.equal(state.dispatches, 0);
      }
      assert.equal(committed.has(keys.egress), false);
      return;
    }
    assert.equal(handoffs.length, 1);
    assert.equal(state.dispatches, 1);
    assert.equal(state.evidenceCreates, 1);
    assert.equal(state.evidenceReads, refusedReads[mode] ?? 2);
    if (Object.hasOwn(refusedReads, mode)) {
      assert.equal(response.code, 403);
      assert.equal(committed.has(keys.egress), false);
      assert.equal(sends.length, 0);
      return;
    }
    if (mode.startsWith('receiver-')) {
      assert.deepEqual(response, {code: 409, value: {status: 'OPEN_UNKNOWN'}});
      assert.equal(sends.length, 1);
      return;
    }
    assert.equal(response.code, 200);
    assert.equal(response.value.receiverGeneration, '99');
    assert.equal(response.value.manifestSha256, policy.manifestSha256);
    assert.equal(JSON.parse(original).deadlineMs, writerDeadline);
    assert.equal(sends.length, 1);
    assert.equal((await post(transport, '/v1/dispatch', token('gate', 'transport'), original)).code, 403);
    assert.equal(state.evidenceReads, 4);
    const reference = (await state.envelopeProof(original)).handoffEvidence;
    assert.deepEqual(reference, state.evidenceRef);
    assert.equal(state.evidenceReads, 6);
    assert.equal(state.reopenForReviewer(reference).envelopeSha256, digest(original));
    assert.equal(state.reviewerReads, 2);
    assert.equal(state.dispatches, 1);
    assert.equal(sends.length, 1);
  });
}
