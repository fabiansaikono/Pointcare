import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {test} from 'node:test';
import {decision, decisionKeys, digest} from './records.mjs';
import {createIssuerStorageStore} from './storage-store.mjs';

const bucket = 'issuer-evidence-fixture';
const packageSha256 = digest('storage-store-test-package');
const approvalSha256 = digest('storage-store-test-approval');
const keys = decisionKeys(packageSha256, approvalSha256);
const seal = {bucket, packageSha256, approvalSha256, keys, dispatchId:'offline-dispatch', runId:'offline-run',
  manifestSha256:digest('manifest'), claimSha256:digest('claim'), challengeSha256:digest('challenge')};
const authority = {...seal, pairKey:keys.pairKey};
const reservation = decision(authority, 'ADMISSION_RESERVED');
const admission = decision(authority, 'STARTED', {reservationGeneration:'9007199254740993'});
const args = {key:keys.reservation, ifGenerationMatch:0, value:reservation};
const bytesOf = value => Buffer.from(JSON.stringify(value));
const md5 = bytes => createHash('md5').update(bytes).digest('base64');
const generation = '9007199254740993';
const metaFields = 'bucket,name,generation,metageneration,size,contentType,contentEncoding,md5Hash';

function response(request, {status = 200, value, data = value === undefined ? Buffer.alloc(0) : bytesOf(value),
  url = request.url, redirected = false, headers = {}} = {}) {
  return {url, redirected, status, body:data,
    headers:new Headers({'content-type':'application/json; charset=utf-8', ...headers})};
}

function objectMetadata(key, bytes, overrides) {
  return {bucket, name:key, generation, metageneration:'1', size:String(bytes.length),
    contentType:'application/json', md5Hash:md5(bytes), ...overrides};
}

function rig(overrides) {
  const calls = [];
  const pins = [];
  const steps = [];
  const controller = new AbortController();
  let now = 100;
  const context = {deadlineMs:200, signal:controller.signal, assertActive() {
    controller.signal.throwIfAborted();
    if (now >= context.deadlineMs) throw Error('FAKE_DEADLINE');
  }};
  const pinLookup = new Map([
    [`CREATE ${keys.reservation} ADMISSION_RESERVED`, digest(bytesOf(reservation))],
    [`CREATE ${keys.admission} STARTED`, digest(bytesOf(admission))],
    [`READ ${keys.reservation} ${generation}`, digest(bytesOf(reservation))],
  ]);
  const store = createIssuerStorageStore({seal, evidenceBucket:bucket, readSlots:['reservation','admission'],
    createKinds:{reservation:['ADMISSION_RESERVED'], admission:['STARTED']},
    async expectedDigest(ref, scope) {
      assert.equal(scope, context);
      pins.push(ref);
      return pinLookup.get(`${ref.operation} ${ref.key} ${ref.kind ?? ref.generation}`);
    },
    async send(request) {
      calls.push(request);
      assert.equal(request.signal, context.signal);
      assert.equal(request.deadlineMs, 200);
      assert.equal(request.redirect, 'error');
      assert.equal(request.retries, 0);
      assert.equal(new URL(request.url).origin, 'https://storage.googleapis.com');
      request.assertActive();
      const next = steps.shift();
      assert.ok(next, 'no unscripted provider request');
      return next(request);
    }, ...overrides});
  return {store, context, calls, steps, pins, pinLookup, controller, expire() { now = 200; }};
}

test('missing or mismatched protected bucket refuses during construction', () => {
  for (const evidenceBucket of [undefined, 'other-evidence-fixture', 'invalid/path']) {
    assert.throws(() => createIssuerStorageStore({seal, evidenceBucket}), /ISSUER_STORAGE_SEAL/u);
  }
});

test('create serializes one exact conditional media POST and uses its own generation ACK', async () => {
  const env = rig();
  for (const [key, value] of [[keys.reservation, reservation], [keys.admission, admission]]) {
    const bytes = bytesOf(value);
    env.steps.push(request => {
      const url = new URL(request.url);
      assert.equal(request.method, 'POST');
      assert.equal(url.pathname, `/upload/storage/v1/b/${bucket}/o`);
      assert.deepEqual(Object.fromEntries(url.searchParams), {uploadType:'media', name:key, ifGenerationMatch:'0', fields:metaFields});
      assert.deepEqual(request.body, bytes);
      assert.equal(request.bodySha256, digest(bytes));
      assert.equal(request.headers['content-type'], 'application/json');
      assert.equal(request.headers['content-length'], String(bytes.length));
      assert.equal(request.maxResponseBytes, 8192);
      return response(request, {value:objectMetadata(key, bytes)});
    });
    assert.deepEqual(await env.store.create({key, ifGenerationMatch:0, value}, env.context),
      {status:'CREATED', key, generation, value});
  }
  assert.equal(env.calls.length, 2);
  assert.deepEqual(env.pins, [{operation:'CREATE', key:keys.reservation, kind:'ADMISSION_RESERVED'},
    {operation:'CREATE', key:keys.admission, kind:'STARTED'}]);
});

test('sealed authority, exact keys, role and raw body SHA are enforced before any request', async () => {
  assert.throws(() => rig({seal:{...seal, bucket:'different-bucket'}}), /ISSUER_STORAGE_SEAL/);
  assert.throws(() => rig({seal:{...seal, keys:{...keys, reservation:'unsealed'}}}), /ISSUER_STORAGE_SEAL/);
  assert.throws(() => rig({createKinds:{reservation:['STARTED']}}), /ISSUER_STORAGE_PORT_SCOPE/);
  const env = rig();
  await assert.rejects(env.store.create({...args, key:`${keys.reservation}/../other`}, env.context), /CREATE_SCOPE/);
  await assert.rejects(env.store.create({...args, ifGenerationMatch:1}, env.context), /CREATE_SCOPE/);
  await assert.rejects(env.store.create({...args, value:{...reservation, manifestSha256:digest('other')}}, env.context), /RECORD_SCOPE/);
  await assert.rejects(env.store.create({...args, value:{...reservation, payload:'unsigned'}}, env.context), /BODY_DIGEST/);
  await assert.rejects(env.store.create({key:keys.admission, ifGenerationMatch:0,
    value:decision(authority, 'STOP_BEFORE_SEND')}, env.context), /RECORD_SCOPE/);
  await assert.rejects(env.store.read(keys.terminal, env.context), /READ_SCOPE/);
  assert.equal(env.calls.length, 0);
});

test('412 only reports EXISTS and neither redirects nor retryable or ambiguous errors repeat a request', async () => {
  const exists = rig();
  exists.steps.push(request => response(request, {status:412, value:{generation, fakeSuccess:true}}));
  assert.deepEqual(await exists.store.create(args, exists.context), {status:'EXISTS', key:keys.reservation});
  assert.equal(exists.calls.length, 1);

  const failures = [
    ...[301, 307, 421, 429, 503].map(status => request => response(request, {status, headers:{location:request.url}})),
    request => response(request, {redirected:true, value:objectMetadata(keys.reservation, request.body)}),
    request => response(request, {url:request.url.replace('storage.googleapis.com','unverified.example'), status:412}),
    () => { throw Error('FAKE_TRANSPORT_TIMEOUT'); },
  ];
  for (const failure of failures) {
    const env = rig();
    env.steps.push(failure);
    await assert.rejects(env.store.create(args, env.context));
    assert.equal(env.calls.length, 1);
    assert.equal(env.calls[0].method, 'POST');
  }
});

test('malformed creation success never yields a generation or begins reconciliation', async () => {
  for (const bad of [{generation:9}, {generation:'090'}, {metageneration:'2'}, {bucket:'other'}, {name:keys.admission},
    {md5Hash:md5(Buffer.from('different'))}, {size:'32769'}, {contentEncoding:'gzip'}, {contentType:'text/plain'}]) {
    const env = rig();
    env.steps.push(request => response(request, {value:objectMetadata(keys.reservation, request.body, bad)}));
    await assert.rejects(env.store.create(args, env.context));
    assert.equal(env.calls.length, 1);
  }
  const corrupted = rig();
  corrupted.steps.push(request => {
    request.body.fill(0);
    return response(request, {value:objectMetadata(keys.reservation, request.body)});
  });
  await assert.rejects(corrupted.store.create(args, corrupted.context), /CREATE_ACK_BINDING/);
  assert.equal(corrupted.calls.length, 1);

  const notJson = rig();
  notJson.steps.push(request => response(request, {data:Buffer.from('{broken')}));
  await assert.rejects(notJson.store.create(args, notJson.context), /ISSUER_STORAGE_JSON/);
  assert.equal(notJson.calls.length, 1);
});

test('read binds metadata to exact-generation media with independent SHA and returns no create ACK', async () => {
  const bytes = bytesOf(reservation);
  for (const headers of [{}, {'x-goog-generation':generation, 'x-goog-metageneration':'7'}]) {
    const env = rig();
    env.steps.push(request => {
      const url = new URL(request.url);
      assert.equal(request.method, 'GET');
      assert.equal(url.pathname, `/storage/v1/b/${bucket}/o/${encodeURIComponent(keys.reservation)}`);
      assert.deepEqual(Object.fromEntries(url.searchParams), {fields:metaFields});
      return response(request, {value:objectMetadata(keys.reservation, bytes, {metageneration:'7'})});
    }, request => {
      const url = new URL(request.url);
      assert.equal(request.method, 'GET');
      assert.equal(url.pathname, new URL(env.calls[0].url).pathname);
      assert.deepEqual(Object.fromEntries(url.searchParams), {alt:'media', generation, ifGenerationMatch:generation, ifMetagenerationMatch:'7'});
      assert.equal(request.maxResponseBytes, bytes.length);
      return response(request, {data:bytes, headers});
    });
    assert.deepEqual(await env.store.read(keys.reservation, env.context), {key:keys.reservation, generation, value:reservation});
    assert.deepEqual(env.pins, [{operation:'READ', key:keys.reservation, generation}]);
    assert.equal(env.calls.length, 2);
  }
});

test('read refuses changed generation, unpinned bytes and ambiguous absence', async () => {
  const expected = bytesOf(reservation);
  const other = bytesOf({...reservation, added:true});
  for (const [bytes, status, mediaGeneration, metadataBytes, badMetageneration] of [
    [expected, 200, '9007199254740994', expected], [expected, 200, generation, expected, '2'],
    [expected, 404, generation, expected], [other, 200, generation, expected], [other, 200, generation, other],
  ]) {
    const env = rig();
    env.steps.push(request => response(request, {value:objectMetadata(keys.reservation, metadataBytes)}),
      request => response(request, {status, data:bytes,
        headers:{'x-goog-generation':mediaGeneration, ...(badMetageneration ? {'x-goog-metageneration':badMetageneration} : {})}}));
    await assert.rejects(env.store.read(keys.reservation, env.context));
    assert.equal(env.calls.length, 2);
  }
  const absent = `No such object: ${bucket}/${keys.reservation}`;
  for (const message of [absent, 'The specified bucket does not exist.', 'No such object: another-object']) {
    const env = rig();
    const error = {code:404, message, errors:[{reason:'notFound', message}]};
    env.steps.push(request => response(request, {status:404, value:{error}}));
    if (message === absent) assert.equal(await env.store.read(keys.reservation, env.context), null);
    else await assert.rejects(env.store.read(keys.reservation, env.context), /ABSENCE_UNCONFIRMED/);
    assert.equal(env.calls.length, 1);
  }
});

test('a cancelled or expired context cannot start a write, continue a read or accept a late ACK', async () => {
  const cancelled = rig();
  cancelled.controller.abort();
  await assert.rejects(cancelled.store.create(args, cancelled.context));
  assert.equal(cancelled.calls.length, 0);

  const beforeSend = rig({async expectedDigest() {
    beforeSend.expire();
    return digest(bytesOf(reservation));
  }});
  await assert.rejects(beforeSend.store.create(args, beforeSend.context), /FAKE_DEADLINE/);
  assert.equal(beforeSend.calls.length, 0);

  const metadataRead = rig();
  metadataRead.steps.push(request => {
    metadataRead.expire();
    return response(request, {value:objectMetadata(keys.reservation, bytesOf(reservation))});
  });
  await assert.rejects(metadataRead.store.read(keys.reservation, metadataRead.context), /FAKE_DEADLINE/);
  assert.equal(metadataRead.calls.length, 1);

  const late = rig();
  const issued = Promise.withResolvers();
  const release = Promise.withResolvers();
  late.steps.push(async request => {
    issued.resolve();
    await release.promise;
    return response(request, {value:objectMetadata(keys.reservation, request.body)});
  });
  const operation = late.store.create(args, late.context);
  await issued.promise;
  late.controller.abort();
  release.resolve();
  await assert.rejects(operation);
  assert.equal(late.calls.length, 1);
});
