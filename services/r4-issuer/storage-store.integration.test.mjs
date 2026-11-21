import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {test} from 'node:test';
import {createIssuerGate} from './gate.mjs';
import {decision, decisionKeys, digest} from './records.mjs';
import {createIssuerStorageStore} from './storage-store.mjs';
import {createIssuerTransport} from './transport.mjs';

const bucket = 'issuer-evidence-fixture';
const receiver = 'offline-issuer-fixture/receiver.json';
const receiverBody = Buffer.from('Synthetic end-to-end issuer payload');
const packageSha256 = digest('offline-storage-integration-package');
const approvalSha256 = digest('offline-storage-integration-approval');
const keys = decisionKeys(packageSha256, approvalSha256);
const actors = {writer:'fixture-writer', stopper:'fixture-stop', closer:'fixture-closer',
  gate:'fixture-gate', transport:'fixture-transport'};
const manifest = {purpose:'R4_ISSUER_SYNTHETIC_ONLY', packageSha256, approvalSha256, keys, actors,
  runId:'offline-run', dispatchId:'offline-dispatch', claimSha256:digest('claim'), challengeSha256:digest('challenge'),
  bodySha256:digest(receiverBody), receiver, gateRevision:'offline-gate', transportRevision:'offline-transport',
  notBeforeMs:100, expiresAtMs:2000};
const bytes = value => Buffer.from(JSON.stringify(value));
const manifestBytes = bytes(manifest);
const manifestSha256 = digest(manifestBytes);
const seal = {...manifest, bucket, manifestSha256, pairKey:keys.pairKey};
const policy = {actors, receiver, manifestSha256, gateRevision:manifest.gateRevision,
  transportRevision:manifest.transportRevision, deadlineMs:manifest.expiresAtMs, stopDeadlineMs:3000};
const admissionBinding = {reservationGeneration:'1'};
const grantBinding = {...admissionBinding, admissionGeneration:'2'};

// These pins are calculated from the fixed fixture plan before any provider calls.
const planned = new Map([
  [keys.reservation, {generation:'1', value:decision(seal, 'ADMISSION_RESERVED')}],
  [keys.admission, {generation:'2', value:decision(seal, 'STARTED', admissionBinding)}],
  [keys.egress, {generation:'3', value:decision(seal, 'EGRESS_GRANTED', grantBinding)}],
]);

function providerFixture(dropAckKey) {
  const objects = new Map();
  const createRequests = [];
  const droppedAcks = [];
  const handoffs = [];
  const sends = [];
  const acceptances = [];

  function response(request, status, body, headers = {}) {
    return {url:request.url, redirected:false, status, body,
      headers:new Headers({'content-type':'application/json', ...headers})};
  }

  function metadata(key, {generation, raw}) {
    return {bucket, name:key, generation, metageneration:'1', size:String(raw.length), contentType:'application/json',
      md5Hash:createHash('md5').update(raw).digest('base64')};
  }

  async function send(request) {
    request.assertActive();
    const url = new URL(request.url);
    assert.equal(url.origin, 'https://storage.googleapis.com');
    if (request.method === 'POST') {
      assert.equal(url.pathname, `/upload/storage/v1/b/${bucket}/o`);
      assert.equal(url.searchParams.get('uploadType'), 'media');
      assert.equal(url.searchParams.get('ifGenerationMatch'), '0');
      const key = url.searchParams.get('name');
      createRequests.push(key);
      const pin = planned.get(key);
      assert.ok(pin);
      assert.deepEqual(request.body, bytes(pin.value));
      if (objects.has(key)) return response(request, 412, bytes({error:{code:412}}));
      const committed = {generation:String(objects.size + 1), raw:Buffer.from(request.body)};
      assert.equal(committed.generation, pin.generation);
      objects.set(key, committed);
      if (key === dropAckKey) {
        droppedAcks.push(key);
        throw Error('FIXTURE_COMMITTED_ACK_LOST');
      }
      return response(request, 200, bytes(metadata(key, committed)));
    }

    assert.equal(request.method, 'GET');
    const prefix = `/storage/v1/b/${bucket}/o/`;
    assert.ok(url.pathname.startsWith(prefix));
    const key = decodeURIComponent(url.pathname.slice(prefix.length));
    const object = objects.get(key);
    if (!object) {
      assert.equal(key, keys.stop);
      const message = `No such object: ${bucket}/${key}`;
      return response(request, 404, bytes({error:{code:404, message, errors:[{reason:'notFound', message}]}}));
    }
    if (url.searchParams.get('alt') !== 'media') return response(request, 200, bytes(metadata(key, object)));
    assert.equal(url.searchParams.get('generation'), object.generation);
    assert.equal(url.searchParams.get('ifGenerationMatch'), object.generation);
    assert.equal(url.searchParams.get('ifMetagenerationMatch'), '1');
    return response(request, 200, Buffer.from(object.raw), {'x-goog-generation':object.generation});
  }

  function boot() {
    const common = {now:() => 125, async authenticate(credential, principal) { return credential === principal; },
      async readVerifiedManifest() { return manifestBytes; }, async dispatchAllowed() { return true; }};
    const expectedDigest = async ({operation, key, kind, generation}) => {
      const pin = planned.get(key);
      if (!pin || (operation === 'CREATE' ? pin.value.kind !== kind : pin.generation !== generation)) return null;
      return digest(bytes(pin.value));
    };
    const transport = createIssuerTransport(policy, {...common,
      store:createIssuerStorageStore({seal, evidenceBucket:bucket, readSlots:['stop','reservation','admission'],
        createKinds:{egress:['EGRESS_GRANTED']}, expectedDigest, send}),
      async sendOnce(request, context) {
        context.assertActive();
        sends.push(request);
        assert.equal(request.receiver, receiver);
        assert.equal(request.ifGenerationMatch, 0);
        assert.deepEqual(request.body, receiverBody);
        assert.equal(request.bodySha256, manifest.bodySha256);
        assert.equal(request.reservationGeneration, '1');
        assert.equal(request.admissionGeneration, '2');
        assert.equal(request.egressGeneration, '3');
        assert.equal(acceptances.length, 0, 'fixture receiver is create-only');
        const receipt = {status:'CREATED', receiver, bodySha256:manifest.bodySha256, generation:'4'};
        acceptances.push(receipt);
        return receipt;
      }});
    return createIssuerGate(policy, {...common,
      store:createIssuerStorageStore({seal, evidenceBucket:bucket, readSlots:['stop'],
        createKinds:{reservation:['ADMISSION_RESERVED'], admission:['STARTED']}, expectedDigest, send}),
      invokeTransport(handoff, body, context) {
        handoffs.push(handoff);
        return transport(actors.gate, handoff, body, context);
      }});
  }

  return {boot, objects, createRequests, droppedAcks, handoffs, sends, acceptances};
}

const cases = [
  {name:'definitive reservation, admission and egress', status:'RECEIVER_ACCEPTED', handoffs:1, deliveries:1},
  {name:'persisted reservation with a lost ACK', dropAckKey:keys.reservation, status:'OPEN_UNKNOWN', handoffs:0, deliveries:0},
  {name:'persisted egress grant with a lost ACK', dropAckKey:keys.egress, status:'OPEN_UNKNOWN', handoffs:1, deliveries:0},
];

for (const scenario of cases) {
  test(`gate and Storage adapter: ${scenario.name}`, {timeout:3000}, async () => {
    const fixture = providerFixture(scenario.dropAckKey);
    const stored = scenario.dropAckKey === keys.reservation ? [keys.reservation] : [...planned.keys()];
    const result = await fixture.boot().start(actors.writer, receiverBody);
    assert.equal(result.status, scenario.status);
    if (scenario.deliveries) assert.equal(result.receiverGeneration, '4');
    assert.deepEqual([...fixture.objects.keys()], stored);
    for (const key of stored) {
      const object = fixture.objects.get(key);
      assert.equal(object.generation, planned.get(key).generation);
      assert.deepEqual(object.raw, bytes(planned.get(key).value));
    }
    assert.deepEqual(fixture.droppedAcks, scenario.dropAckKey ? [scenario.dropAckKey] : []);
    assert.deepEqual(fixture.createRequests, stored);
    assert.equal(fixture.handoffs.length, scenario.handoffs, 'gate handoffs');
    assert.equal(fixture.sends.length, scenario.deliveries, 'physical synthetic sends');
    assert.equal(fixture.acceptances.length, scenario.deliveries, 'receiver acceptances');

    const original = structuredClone(fixture.objects);
    assert.equal((await fixture.boot().start(actors.writer, receiverBody)).status, 'REFUSED');
    assert.deepEqual(structuredClone(fixture.objects), original, 'fresh gate preserves the original generations and bytes');
    assert.deepEqual(fixture.createRequests, [...stored, keys.reservation]);
    assert.equal(fixture.handoffs.length, scenario.handoffs, 'fresh gate handoffs');
    assert.equal(fixture.sends.length, scenario.deliveries, 'fresh gate synthetic sends');
    assert.equal(fixture.acceptances.length, scenario.deliveries, 'fresh gate receiver acceptances');
  });
}
