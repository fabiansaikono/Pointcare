import {closeSync, constants, fstatSync, openSync, readSync} from 'node:fs';
import {request as httpRequest} from 'node:http';
import {digest, isDigest} from '../records.mjs';
import {createIssuerServiceAuthenticator} from '../service-auth.mjs';

const startupPath = '/run/poincare/issuer/startup.json';
const metadataEndpoint = 'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity';
const recordLimit = 65_536;
const startupLimit = 264_192;
const tokenLimit = 8_192;
const utf8 = new TextDecoder('utf-8', {fatal: true});
const actions = Object.freeze({writer: 'start', stop: 'stop'});

function need(condition, reason) {
  if (!condition) throw Error(`ISSUER_JOB_BINDING_${reason}`);
}

function exact(value, fields) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === fields.length && fields.every(field => Object.hasOwn(value, field));
}

function readStartup() {
  const fd = openSync(startupPath, constants.O_RDONLY | constants.O_NONBLOCK);
  try {
    const info = fstatSync(fd);
    need(info.isFile() && info.size > 0 && info.size <= startupLimit, 'STARTUP_BYTES');
    const bytes = Buffer.alloc(info.size + 1);
    let length = 0;
    while (length < bytes.length) {
      const received = readSync(fd, bytes, length, bytes.length - length, length);
      if (received === 0) break;
      length += received;
    }
    need(length === info.size, 'STARTUP_BYTES');
    return bytes.subarray(0, length);
  } finally {
    closeSync(fd);
  }
}

function sealedRecord(encoded, expectedHash, reason) {
  need(typeof encoded === 'string' && encoded.length <= 4 * Math.ceil(recordLimit / 3) &&
    typeof expectedHash === 'string' && isDigest(expectedHash), reason);
  const bytes = Buffer.from(encoded, 'base64');
  need(bytes.length > 0 && bytes.length <= recordLimit && bytes.toString('base64') === encoded &&
    digest(bytes) === expectedHash, reason);
  return bytes;
}

function openTokenResponse(url, signal, request) {
  return new Promise((resolve, reject) => {
    const call = request(url, {
      method: 'GET', agent: false, maxHeaderSize: 8_192, signal,
      headers: {'Metadata-Flavor': 'Google'},
    }, resolve);
    call.once('error', reject);
    call.end();
  });
}

async function readTokenResponse(response, checkActive) {
  const flavor = response.headersDistinct?.['metadata-flavor'];
  need(response.statusCode === 200 && flavor?.length === 1 && flavor[0] === 'Google' &&
    !response.headersDistinct['content-encoding'], 'METADATA_RESPONSE');
  const bytes = Buffer.alloc(tokenLimit);
  let length = 0;
  try {
    for await (const chunk of response) {
      checkActive();
      need(Buffer.isBuffer(chunk) && chunk.length <= bytes.length - length, 'TOKEN_BYTES');
      chunk.copy(bytes, length);
      length += chunk.length;
    }
    need(response.complete && length > 0, 'TOKEN_BYTES');
    return utf8.decode(bytes.subarray(0, length));
  } finally {
    bytes.fill(0);
  }
}

/** The fixed job entrypoint calls without overrides. Explicit ports support offline tests. */
export function openJobBinding(role, {env = process.env, readConfig = readStartup,
  request = httpRequest, now = Date.now} = {}) {
  need(Object.hasOwn(actions, role), 'ROLE');
  const startupHash = env.ISSUER_STARTUP_SHA256;
  need(typeof startupHash === 'string' && isDigest(startupHash), 'STARTUP_PIN');
  const raw = readConfig();
  need(raw instanceof Uint8Array && raw.byteLength > 0 && raw.byteLength <= startupLimit &&
    digest(raw) === startupHash, 'STARTUP_PIN');
  const startup = JSON.parse(utf8.decode(raw));
  const fields = ['schema', 'role', 'gateBaseUrl', 'identityBase64', 'identitySha256',
    'manifestBase64', 'manifestSha256', 'stopDeadlineMs'];
  need(exact(startup, role === 'writer' ? [...fields, 'sealedBodyBase64'] : fields) &&
    startup.schema === 'POINCARE_R4_ISSUER_JOB_STARTUP_V1' && startup.role === role, 'STARTUP_SCOPE');

  const gateOrigin = typeof startup.gateBaseUrl === 'string' && URL.parse(startup.gateBaseUrl);
  need(gateOrigin?.protocol === 'https:' && !gateOrigin.port && startup.gateBaseUrl === gateOrigin.origin,
    'GATE_ORIGIN');
  const identityConfigBytes = sealedRecord(startup.identityBase64, startup.identitySha256, 'IDENTITY_PIN');
  const {verifyAction, emails} = createIssuerServiceAuthenticator({
    configBytes: identityConfigBytes, configSha256: startup.identitySha256, now,
  });
  const {audiences, keyset} = JSON.parse(utf8.decode(identityConfigBytes));
  const manifest = JSON.parse(utf8.decode(sealedRecord(startup.manifestBase64, startup.manifestSha256,
    'MANIFEST_PIN')));
  need(manifest?.purpose === 'R4_ISSUER_SYNTHETIC_ONLY' && exact(manifest.actors, Object.keys(emails)) &&
    Object.entries(emails).every(([actor, email]) => manifest.actors[actor] === email) &&
    typeof manifest.dispatchId === 'string' && /^[A-Za-z0-9_.-]{1,160}$/u.test(manifest.dispatchId) &&
    typeof manifest.bodySha256 === 'string' && isDigest(manifest.bodySha256), 'MANIFEST_SCOPE');
  const deadlineMs = manifest.expiresAtMs;
  const {stopDeadlineMs} = startup;
  const {notBeforeMs} = manifest;
  need([notBeforeMs, deadlineMs, stopDeadlineMs].every(Number.isSafeInteger) &&
    notBeforeMs >= 0 && notBeforeMs < deadlineMs && stopDeadlineMs >= deadlineMs, 'DEADLINE');
  const roleDeadline = role === 'writer' ? deadlineMs : stopDeadlineMs;
  const cutoff = Math.min(roleDeadline, keyset.validUntilMs);
  const earliest = role === 'writer' ? Math.max(notBeforeMs, keyset.validFromMs) : keyset.validFromMs;
  const sealedBody = role === 'writer' ? sealedRecord(startup.sealedBodyBase64, manifest.bodySha256, 'BODY_PIN')
    : undefined;

  function remaining(signal) {
    const current = now();
    const left = cutoff - current;
    need(!signal?.aborted && Number.isSafeInteger(current) && current >= earliest &&
      Number.isSafeInteger(left) && left > 0 && left <= 2_147_483_647, 'DEADLINE');
    return left;
  }

  remaining();
  let opened = false;
  return Object.freeze({
    gateBaseUrl: startup.gateBaseUrl,
    gateAudience: audiences.gate,
    identityConfigBytes,
    identityConfigSha256: startup.identitySha256,
    expectedDispatchId: manifest.dispatchId,
    expectedManifestSha256: startup.manifestSha256,
    deadlineMs,
    stopDeadlineMs,
    ...(role === 'writer' ? {sealedBody, sealedBodySha256: manifest.bodySha256} : {}),
    async readIdentityToken({audience, deadlineMs: requestedDeadline, signal}) {
      need(audience === audiences.gate && requestedDeadline === roleDeadline && signal instanceof AbortSignal,
        'TOKEN_SCOPE');
      need(!opened, 'TOKEN_ALREADY_REQUESTED');
      const controller = new AbortController();
      const active = AbortSignal.any([controller.signal, signal]);
      const timer = setTimeout(() => controller.abort(), remaining(active));
      const url = new URL(metadataEndpoint);
      url.searchParams.set('format', 'full');
      url.searchParams.set('audience', audiences.gate);
      let response;
      try {
        remaining(active);
        opened = true;
        try {
          response = await openTokenResponse(url, active, request);
        } catch {
          throw Error('ISSUER_JOB_BINDING_METADATA_UNAVAILABLE');
        }
        remaining(active);
        const token = await readTokenResponse(response, () => remaining(active));
        remaining(active);
        need(verifyAction(actions[role], token), 'TOKEN_IDENTITY');
        remaining(active);
        return token;
      } finally {
        clearTimeout(timer);
        response?.destroy();
      }
    },
  });
}
