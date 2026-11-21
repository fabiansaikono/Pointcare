import {createHash} from 'node:crypto';
import {constants} from 'node:fs';
import {open} from 'node:fs/promises';
import {request as httpRequest} from 'node:http';
import {request as httpsRequest} from 'node:https';
import {digest, isGeneration} from '../records.mjs';

export const managedBindingPath = '/run/poincare/issuer/service-ports.json';
const bindingLimit = 98_304;
const metadata = 'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/';
const storageOrigin = 'https://storage.googleapis.com';
const utf8 = new TextDecoder('utf-8', {fatal: true});
const jsonType = 'application/json';
const octets = 'application/octet-stream';
const objectFields = 'bucket,name,generation,metageneration,size,contentType,md5Hash';
const md5 = body => createHash('md5').update(body).digest('base64');
const receiverPattern = /^gs:\/\/([a-z0-9][a-z0-9-]{1,61}[a-z0-9])\/((?:[a-zA-Z0-9_-]+\/)*receiver\.json)$/u;

function demand(condition, reason) {
  if (!condition) throw Error(`ISSUER_PROVIDER_${reason}`);
}

export async function readManagedServiceBinding() {
  const file = await open(managedBindingPath, constants.O_RDONLY | constants.O_NONBLOCK);
  try {
    const stat = await file.stat();
    const {size} = stat;
    demand(stat.isFile() && size > 0 && size <= bindingLimit, 'BINDING_BYTES');
    const raw = Buffer.alloc(size + 1);
    let offset = 0;
    while (offset < raw.length) {
      const {bytesRead} = await file.read(raw, offset, raw.length - offset, offset);
      if (!bytesRead) break;
      offset += bytesRead;
    }
    demand(offset === size, 'BINDING_BYTES');
    return raw.subarray(0, offset);
  } finally {
    await file.close();
  }
}

function once(request, url, method, headers, body, limit, context) {
  return new Promise((resolve, reject) => {
    context.assertActive();
    const call = request(url, {method, headers, signal: context.signal, agent: false, maxHeaderSize: 16_384},
      async response => {
        try {
          const status = response.statusCode;
          const encoding = response.headersDistinct['content-encoding'];
          demand(Number.isInteger(status) && !(status >= 300 && status < 400) &&
            (!encoding || (encoding.length === 1 && encoding[0] === 'identity')), 'RESPONSE');
          const result = Buffer.alloc(limit);
          let length = 0;
          for await (const chunk of response) {
            context.assertActive();
            demand(Buffer.isBuffer(chunk) && chunk.length <= limit - length, 'RESPONSE_LIMIT');
            chunk.copy(result, length);
            length += chunk.length;
          }
          context.assertActive();
          demand(response.complete, 'RESPONSE');
          const headers = new Headers();
          for (const [key, values] of Object.entries(response.headersDistinct)) {
            for (const value of values) headers.append(key, value);
          }
          resolve({status, headers, body: result.subarray(0, length)});
        } catch (error) {
          response.destroy();
          reject(error);
        }
      });
    call.once('error', reject);
    try {
      context.assertActive();
      call.end(body);
    } catch (error) {
      call.destroy();
      reject(error);
    }
  });
}

function readJson({headers, body}) {
  demand(headers.get('content-type')?.split(';', 1)[0] === jsonType, 'JSON_RESPONSE');
  return JSON.parse(utf8.decode(body));
}

/** Inputs come only from the consumed, hash-verified service binding and manifest. */
export function createManagedProviderPorts({service, bucket, receiver, handoffObject, transportUrl, audience},
  {http = httpRequest, https = httpsRequest} = {}) {
  const receiverTarget = service === 'transport' && receiverPattern.exec(receiver);
  demand(service === 'gate' || (receiverTarget && receiverTarget[1] !== bucket), 'RECEIVER_SCOPE');
  const readPrefix = `/storage/v1/b/${bucket}/o/`;
  const writePath = `/upload/storage/v1/b/${bucket}/o`;

  async function metadataResponse(url, context) {
    const reply = await once(http, url, 'GET', {'Metadata-Flavor': 'Google', 'Accept-Encoding': 'identity'},
      undefined, 8_192, context);
    demand(reply.status === 200 && reply.headers.get('metadata-flavor') === 'Google' && reply.body.length > 0,
      'METADATA_RESPONSE');
    return reply;
  }

  async function storageCredential(context) {
    const credential = readJson(await metadataResponse(new URL('token', metadata), context));
    const token = credential?.access_token;
    demand(typeof token === 'string' && /^[a-zA-Z0-9._~+\/-]{1,8192}={0,2}$/u.test(token) &&
      credential.token_type === 'Bearer' && Number.isSafeInteger(credential.expires_in) &&
      credential.expires_in > 0, 'STORAGE_CREDENTIAL');
    return `Bearer ${token}`;
  }

  async function authorizedStorage(url, method, headers, body, limit, context) {
    const authorization = await storageCredential(context);
    return once(https, url, method, {...headers, Authorization: authorization}, body, limit, context);
  }

  async function createObject(targetBucket, object, contentType, body, context) {
    const url = new URL(`/upload/storage/v1/b/${targetBucket}/o`, storageOrigin);
    url.search = new URLSearchParams({uploadType: 'media', name: object, ifGenerationMatch: '0', fields: objectFields});
    const response = await authorizedStorage(url, 'POST', {'Content-Type': contentType, 'Content-Length': String(body.length),
      'Accept-Encoding': 'identity'}, body, 8_192, context);
    demand(response.status === 200, 'CREATE_UNCERTAIN');
    const record = readJson(response);
    demand(record?.bucket === targetBucket && record.name === object && isGeneration(record.generation) &&
      record.metageneration === '1' && record.size === String(body.length) && record.contentType === contentType &&
      record.md5Hash === md5(body), 'CREATE_ACK');
    return record.generation;
  }

  async function readJsonObject(reference, mode, limit, context) {
    const {bucket, object, generation: pinnedGeneration, metageneration: pinnedMetageneration, sha256} = reference;
    const url = new URL(`/storage/v1/b/${bucket}/o/${encodeURIComponent(object)}`, storageOrigin);
    const headers = {'Accept-Encoding': 'identity', 'Cache-Control': 'no-store',
      Authorization: await storageCredential(context)};
    const metadataQuery = {fields: objectFields};
    if (mode === 'PINNED') Object.assign(metadataQuery, {generation: pinnedGeneration,
      ifGenerationMatch: pinnedGeneration, ifMetagenerationMatch: pinnedMetageneration});
    url.search = new URLSearchParams(metadataQuery);
    const objectResponse = await once(https, url, 'GET', headers, undefined, 8_192, context);
    demand(objectResponse.status === 200, 'EVIDENCE_METADATA');
    const record = readJson(objectResponse);
    demand(record?.bucket === bucket && record.name === object && isGeneration(record.generation) &&
      isGeneration(record.metageneration) && (mode !== 'ORIGINAL' || record.metageneration === '1') &&
      (mode !== 'PINNED' || (record.generation === pinnedGeneration && record.metageneration === pinnedMetageneration)) &&
      record.contentType === jsonType && typeof record.size === 'string' && /^[1-9][0-9]{0,4}$/u.test(record.size) &&
      Number(record.size) <= limit && typeof record.md5Hash === 'string' && /^[A-Za-z0-9+/]{22}==$/u.test(record.md5Hash),
    'EVIDENCE_METADATA');
    const {generation, metageneration} = record;
    const mediaUrl = new URL(url);
    // A live read must fail if the live generation changed, even when Storage retains the old object.
    const params = {alt: 'media', ifGenerationMatch: generation, ifMetagenerationMatch: metageneration};
    if (mode !== 'LIVE') params.generation = generation;
    mediaUrl.search = new URLSearchParams(params);
    const media = await once(https, mediaUrl, 'GET', headers, undefined, Number(record.size), context);
    demand(media.status === 200 && (media.headers.get('x-goog-generation') ?? generation) === generation &&
      (media.headers.get('x-goog-metageneration') ?? metageneration) === metageneration &&
      media.body.length === Number(record.size) && md5(media.body) === record.md5Hash, 'EVIDENCE_MEDIA');
    const actual = digest(media.body);
    demand(mode !== 'PINNED' || actual === sha256, 'EVIDENCE_DIGEST');
    return Object.freeze({generation, metageneration, sha256: actual, bytes: media.body});
  }

  const ports = {
    // The Control factory supplies only exact references admitted by its role-bound source lists.
    readControlObject(reference, context) {
      return readJsonObject(reference, reference.generation === undefined ? 'LIVE' : 'PINNED', 65_536, context);
    },
    async sendStorage(req) {
      const url = new URL(req.url);
      demand(url.origin === storageOrigin && !url.username && !url.password && !url.hash &&
        ((req.method === 'GET' && url.pathname.startsWith(readPrefix) && !req.body) ||
          (req.method === 'POST' && url.pathname === writePath && url.searchParams.get('ifGenerationMatch') === '0' &&
            req.body instanceof Uint8Array && digest(req.body) === req.bodySha256)) &&
        req.retries === 0 && req.redirect === 'error' && Number.isSafeInteger(req.maxResponseBytes) &&
        req.maxResponseBytes > 0 && req.maxResponseBytes <= 32_768, 'STORAGE_SCOPE');
      const {status, headers, body} = await authorizedStorage(url, req.method, req.headers, req.body,
        req.maxResponseBytes, req);
      return {url: url.href, redirected: false, status, headers, body};
    },
  };
  if (service === 'gate') {
    return Object.freeze({...ports, async readGateIdentityToken(scope, context) {
      demand(scope.audience === audience, 'AUDIENCE');
      const url = new URL('identity', metadata);
      url.search = new URLSearchParams({audience, format: 'full'});
      return utf8.decode((await metadataResponse(url, context)).body);
    }, async postTransport({url, credential, bytes}, context) {
      demand(url === transportUrl && typeof credential === 'string' && bytes instanceof Uint8Array &&
        bytes.length > 0 && bytes.length <= 65_536, 'TRANSPORT_SCOPE');
      const reply = await once(https, new URL(transportUrl), 'POST', {Authorization: `Bearer ${credential}`,
        'Content-Type': octets, 'Content-Length': String(bytes.length), 'Accept-Encoding': 'identity'}, bytes, 16_384, context);
      return {code: reply.status, value: readJson(reply)};
    }, async createHandoffEvidence(raw, context) {
      demand(raw instanceof Uint8Array && raw.length > 0 && raw.length <= 8_192, 'HANDOFF_EVIDENCE');
      return createObject(bucket, handoffObject, jsonType, raw, context);
    }});
  }

  const [, sinkBucket, object] = receiverTarget;
  return Object.freeze({...ports, async sendReceiver(req, context) {
    demand(req.receiver === receiver && req.ifGenerationMatch === 0 && req.body instanceof Uint8Array &&
      req.body.length > 0 && req.body.length <= 65_536 && digest(req.body) === req.bodySha256, 'RECEIVER_SCOPE');
    const generation = await createObject(sinkBucket, object, octets, req.body, context);
    return {status: 'CREATED', receiver, bodySha256: req.bodySha256, generation};
  }, async readHandoffEvidence(context) {
    return readJsonObject({bucket, object: handoffObject}, 'ORIGINAL', 8_192, context);
  }});
}
