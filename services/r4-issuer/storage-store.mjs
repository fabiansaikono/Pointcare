import {createHash} from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';
import {decision, decisionKeys, digest, isDigest, isGeneration} from './records.mjs';

const origin = 'https://storage.googleapis.com';
const slots = Object.freeze({reservation:['ADMISSION_RESERVED'], admission:['STARTED','STOP_BEFORE_SEND'],
  egress:['EGRESS_GRANTED','EGRESS_BLOCKED'], stop:['REQUESTED'], terminal:['STOPPED_CONFIRMED']});
const jsonType = 'application/json';
const objectLimit = 32 * 1024;
const metadataLimit = 8 * 1024;
const fields = 'bucket,name,generation,metageneration,size,contentType,contentEncoding,md5Hash';
const nonEmptyId = value => typeof value === 'string' && /^[a-zA-Z0-9_.-]{1,160}$/u.test(value);
const md5 = bytes => createHash('md5').update(bytes).digest('base64');

function demand(ok, code) {
  if (!ok) throw Error(`ISSUER_STORAGE_${code}`);
}

function active(context) {
  demand(Number.isSafeInteger(context?.deadlineMs) && context.signal instanceof AbortSignal &&
    typeof context.assertActive === 'function', 'CONTEXT');
  context.assertActive();
  context.signal.throwIfAborted();
}

function mediaType(value) {
  return typeof value === 'string' ? value.split(';', 1)[0].trim().toLowerCase() : '';
}

function json(bytes) {
  try {
    return JSON.parse(new TextDecoder('utf-8', {fatal:true, ignoreBOM:true}).decode(bytes));
  } catch {
    throw Error('ISSUER_STORAGE_JSON');
  }
}

function responseJson(response) {
  demand(mediaType(response.headers.get('content-type')) === jsonType, 'JSON_CONTENT_TYPE');
  return json(response.body);
}

function metadata(response, key, evidenceBucket) {
  const record = responseJson(response);
  const {generation, metageneration, size, contentType, contentEncoding, md5Hash} = record ?? {};
  demand(record?.bucket === evidenceBucket && record.name === key && isGeneration(generation) &&
    isGeneration(metageneration) && typeof size === 'string' && /^[1-9][0-9]{0,4}$/u.test(size) &&
    Number(size) <= objectLimit && mediaType(contentType) === jsonType &&
    (contentEncoding === undefined || contentEncoding === 'identity') && typeof md5Hash === 'string' &&
    /^[A-Za-z0-9+/]{22}==$/u.test(md5Hash) && Buffer.from(md5Hash, 'base64').toString('base64') === md5Hash,
  'METADATA_BINDING');
  return {generation, metageneration, byteLength:Number(size), md5Hash};
}

// The caller supplies a verified seal, role-specific permissions, expected raw-byte
// SHA-256 values from protected inputs, and an authenticated one-request transport.
export function createIssuerStorageStore({seal, evidenceBucket, readSlots, createKinds, expectedDigest, send}) {
  const keys = decisionKeys(seal?.packageSha256, seal?.approvalSha256);
  const {manifestSha256, dispatchId, runId, claimSha256, challengeSha256} = seal ?? {};
  demand(typeof evidenceBucket === 'string' && /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/u.test(evidenceBucket) &&
    seal.bucket === evidenceBucket && isDeepStrictEqual(seal.keys, keys) &&
    [manifestSha256, claimSha256, challengeSha256].every(isDigest) && [dispatchId, runId].every(nonEmptyId), 'SEAL');
  const authority = {pairKey:keys.pairKey, manifestSha256, dispatchId, runId, claimSha256, challengeSha256};
  demand(Array.isArray(readSlots) && readSlots.every(slot => Object.hasOwn(slots, slot)) &&
    new Set(readSlots).size === readSlots.length && createKinds && typeof createKinds === 'object' &&
    !Array.isArray(createKinds) && typeof expectedDigest === 'function' && typeof send === 'function', 'PORT_SCOPE');
  const readKeys = new Map(readSlots.map(slot => [keys[slot], new Set(slots[slot])]));
  const writeKeys = new Map(Object.entries(createKinds).map(([slot, kinds]) => {
    demand(Object.hasOwn(slots, slot) && Array.isArray(kinds) && kinds.length > 0 &&
      new Set(kinds).size === kinds.length && kinds.every(kind => slots[slot].includes(kind)), 'PORT_SCOPE');
    return [keys[slot], new Set(kinds)];
  }));

  async function request(url, method, context, maxResponseBytes, bytes) {
    const headers = {accept: jsonType, 'accept-encoding':'identity', 'cache-control':'no-store'};
    if (bytes) Object.assign(headers, {'content-type':jsonType, 'content-length':String(bytes.length)});
    active(context);
    const response = await send(Object.freeze({url:url.href, method, headers:Object.freeze(headers),
      redirect:'error', retries:0, maxResponseBytes, body:bytes && Buffer.from(bytes),
      bodySha256:bytes && digest(bytes), deadlineMs:context.deadlineMs,
      signal:context.signal, assertActive:() => active(context)}));
    active(context);
    demand(response?.url === url.href && response.redirected === false && Number.isInteger(response.status) &&
      !(response.status >= 300 && response.status < 400) && response.headers instanceof Headers &&
      (response.headers.get('content-encoding') ?? 'identity') === 'identity' && response.body instanceof Uint8Array &&
      response.body.length <= maxResponseBytes, 'RESPONSE_BOUNDARY');
    return response;
  }

  async function verifyDigest(reference, bytes, context) {
    active(context);
    const expected = await expectedDigest(Object.freeze(reference), context);
    active(context);
    demand(isDigest(expected) && digest(bytes) === expected, 'BODY_DIGEST');
  }

  function checkRecord(value, kinds) {
    demand(value && typeof value === 'object' && !Array.isArray(value) && kinds.has(value.kind) &&
      Object.entries(decision(authority, value.kind)).every(([field, expected]) => value[field] === expected),
    'RECORD_SCOPE');
  }

  async function create({key, ifGenerationMatch, value}, context) {
    const kinds = writeKeys.get(key);
    demand(kinds && ifGenerationMatch === 0, 'CREATE_SCOPE');
    const bytes = Buffer.from(JSON.stringify(value));
    demand(bytes.length > 0 && bytes.length <= objectLimit, 'BODY_LIMIT');
    const record = json(bytes);
    checkRecord(record, kinds);
    await verifyDigest({operation:'CREATE', key, kind:record.kind}, bytes, context);
    const url = new URL(`/upload/storage/v1/b/${evidenceBucket}/o`, origin);
    url.search = new URLSearchParams({uploadType:'media', name:key, ifGenerationMatch:'0', fields});
    const response = await request(url, 'POST', context, metadataLimit, bytes);
    if (response.status === 412) return {status:'EXISTS', key};
    demand(response.status === 200, 'CREATE_UNCERTAIN');
    const result = metadata(response, key, evidenceBucket);
    demand(result.metageneration === '1' && result.byteLength === bytes.length && result.md5Hash === md5(bytes),
      'CREATE_ACK_BINDING');
    return {status:'CREATED', key, generation:result.generation, value:record};
  }

  async function read(key, context) {
    const kinds = readKeys.get(key);
    demand(kinds, 'READ_SCOPE');
    const url = new URL(`/storage/v1/b/${evidenceBucket}/o/${encodeURIComponent(key)}`, origin);
    url.search = new URLSearchParams({fields});
    const response = await request(url, 'GET', context, metadataLimit);
    if (response.status === 404) {
      const {error} = responseJson(response) ?? {};
      const missing = `No such object: ${evidenceBucket}/${key}`;
      demand(error?.code === 404 && error.message === missing && Array.isArray(error.errors) &&
        error.errors.some(item => item?.reason === 'notFound' && item.message === missing), 'ABSENCE_UNCONFIRMED');
      return null;
    }
    demand(response.status === 200, 'READ_UNCERTAIN');
    const object = metadata(response, key, evidenceBucket);
    const {generation, metageneration, byteLength, md5Hash} = object;
    url.search = new URLSearchParams({alt:'media', generation, ifGenerationMatch:generation, ifMetagenerationMatch:metageneration});
    const media = await request(url, 'GET', context, byteLength);
    demand(media.status === 200 && (media.headers.get('x-goog-generation') ?? generation) === generation &&
      (media.headers.get('x-goog-metageneration') ?? metageneration) === metageneration &&
      mediaType(media.headers.get('content-type')) === jsonType && media.body.length === byteLength &&
      md5(media.body) === md5Hash, 'MEDIA_BINDING');
    const bytes = Buffer.from(media.body);
    await verifyDigest({operation:'READ', key, generation}, bytes, context);
    const value = json(bytes);
    checkRecord(value, kinds);
    return {key, generation, value};
  }

  return Object.freeze({create, read});
}
