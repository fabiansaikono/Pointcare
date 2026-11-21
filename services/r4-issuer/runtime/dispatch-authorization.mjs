import {constants, createPublicKey, verify} from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';
import {digest, isDigest, isGeneration} from '../records.mjs';

const utf8 = new TextDecoder('utf-8', {fatal: true, ignoreBOM: true});
const scopes = ['runId', 'dispatchId', 'claimSha256', 'challengeSha256', 'pairKey', 'manifestSha256',
  'gateRevision', 'transportRevision', 'receiver', 'bodySha256', 'routeEvidenceSha256'];
const manifestScopes = scopes.filter(field => field !== 'routeEvidenceSha256');
const exact = (value, fields) => value && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === fields.length && fields.every(field => Object.hasOwn(value, field));
const time = value => Number.isSafeInteger(value) && value >= 0;

function need(condition, reason) {
  if (!condition) throw Error(`ISSUER_DISPATCH_${reason}`);
}

function decode(value) {
  need(typeof value === 'string' && value.length > 0 && value.length <= 65_536, 'ENCODING');
  const bytes = Buffer.from(value, 'base64');
  need(bytes.toString('base64') === value, 'ENCODING');
  return bytes;
}

export function pinControlSigner(pem) {
  need(typeof pem === 'string' && pem.length <= 8_192 && /^-----BEGIN PUBLIC KEY-----\r?\n/u.test(pem) &&
    !pem.includes('PRIVATE KEY'), 'SIGNER_PIN');
  const key = createPublicKey(pem);
  const bits = key.asymmetricKeyDetails?.modulusLength;
  need(key.asymmetricKeyType === 'rsa' && bits >= 2048 && bits <= 4096, 'SIGNER_PIN');
  return {key, fingerprint: digest(key.export({type: 'spki', format: 'der'}))};
}

export function readSignedControlRecord(original, key) {
  const signed = JSON.parse(utf8.decode(original.bytes));
  need(exact(signed, ['payloadBase64', 'signatureBase64']), 'SIGNED_RECORD');
  const payload = decode(signed.payloadBase64);
  const signature = decode(signed.signatureBase64);
  need(verify('RSA-SHA256', payload, {key, padding: constants.RSA_PKCS1_PADDING}, signature), 'SIGNATURE');
  return JSON.parse(utf8.decode(payload));
}

function reference(input, prefix, live) {
  const fields = live ? ['bucket', 'object'] : ['bucket', 'object', 'generation', 'metageneration', 'sha256'];
  need(exact(input, fields) && typeof input.object === 'string' && input.object.startsWith(prefix) &&
    /^[a-zA-Z0-9_-]+\.json$/u.test(input.object.slice(prefix.length)) &&
    (live || (isGeneration(input.generation) && isGeneration(input.metageneration) && isDigest(input.sha256))), 'SOURCE_REF');
  return Object.freeze({...input});
}

/** The protected role binding controls the exact data sources, keys and permitted freshness. */
export function createDispatchAuthorization({authority, config, readControlObject, now = Date.now}) {
  need(exact(config, ['trialId', 'authorization', 'status', 'coverage', 'releasePublicKey',
    'observerPublicKey', 'statusMaxAgeMs', 'coverageMaxAgeMs', 'observerBoundary']) &&
    typeof config.trialId === 'string' && /^[a-zA-Z0-9_-]{1,128}$/u.test(config.trialId) &&
    typeof config.observerBoundary === 'string' && /^[a-zA-Z0-9_.-]{1,160}$/u.test(config.observerBoundary) &&
    [config.statusMaxAgeMs, config.coverageMaxAgeMs].every(value => Number.isSafeInteger(value) && value > 0), 'CONFIG');
  const prefix = `trials/${config.trialId}/`;
  const authorization = reference(config.authorization, prefix, false);
  const status = reference(config.status, prefix, true);
  const coverage = reference(config.coverage, prefix, true);
  need(authorization.object === prefix + 'authorization.json' &&
    new Set([authorization.object, status.object, coverage.object]).size === 3, 'SOURCE_SCOPE');
  const release = pinControlSigner(config.releasePublicKey);
  const observer = pinControlSigner(config.observerPublicKey);
  need(release.fingerprint !== observer.fingerprint, 'SIGNER_SEPARATION');
  const scope = Object.freeze(Object.fromEntries(scopes.map(field => [field, authority[field]])));
  need(scopes.every(field => typeof scope[field] === 'string' && scope[field].length > 0) &&
    time(authority.notBeforeMs) && time(authority.expiresAtMs) && authority.notBeforeMs < authority.expiresAtMs, 'SCOPE');
  const {observerBoundary, statusMaxAgeMs, coverageMaxAgeMs} = config;
  let released;
  const currentMarks = new Map();
  let disabled = false;

  function verifyRelease(original) {
    const value = readSignedControlRecord(original, release.key);
    need(exact(value, ['schema', 'scope', 'notBeforeMs', 'expiresAtMs', 'observerBoundary']) &&
      value.schema === 'POINCARE_R4_ISSUER_AUTHORIZATION_V1' && isDeepStrictEqual(value.scope, scope) &&
      value.observerBoundary === observerBoundary && time(value.notBeforeMs) && time(value.expiresAtMs) &&
      authority.notBeforeMs <= value.notBeforeMs && value.notBeforeMs < value.expiresAtMs &&
      value.expiresAtMs <= authority.expiresAtMs, 'AUTHORIZATION');
    return Object.freeze(value);
  }

  function isFresh(value, age, current) {
    return time(value.observedAtMs) && time(value.validUntilMs) && value.observedAtMs <= current &&
      current < value.validUntilMs && value.validUntilMs - value.observedAtMs <= age;
  }

  function checkFreshSample(kind, value, original, age, current) {
    const previous = currentMarks.get(kind);
    need(isFresh(value, age, current) && (!previous || value.observedAtMs > previous.observedAtMs ||
      (value.observedAtMs === previous.observedAtMs && original.sha256 === previous.sha256)), 'STALE');
    currentMarks.set(kind, {observedAtMs: value.observedAtMs, sha256: original.sha256});
  }

  async function dispatchPermission(request, context) {
    try {
      context.assertActive();
      if (disabled || !manifestScopes.every(field => request[field] === scope[field])) return null;
      const grant = released ?? verifyRelease(await readControlObject(authorization, context));
      context.assertActive();
      released = grant;
      if (now() < grant.notBeforeMs || now() >= grant.expiresAtMs) return null;
      const coverageSource = await readControlObject(coverage, context);
      const observed = readSignedControlRecord(coverageSource, observer.key);
      const beforeStatus = now();
      need(exact(observed, ['schema', 'scope', 'authorizationSha256', 'observerBoundary', 'state', 'coverageFromMs',
        'observedAtMs', 'validUntilMs']) && observed.schema === 'POINCARE_R4_ISSUER_EGRESS_COVERAGE_V1' &&
        isDeepStrictEqual(observed.scope, scope) && observed.authorizationSha256 === authorization.sha256 &&
        observed.observerBoundary === observerBoundary && ['ARMED', 'GAP'].includes(observed.state) &&
        time(observed.observedAtMs) && observed.observedAtMs <= beforeStatus, 'OBSERVER_COVERAGE');
      if (observed.state === 'GAP') {
        disabled = true;
        return null;
      }
      need(time(observed.coverageFromMs) && observed.coverageFromMs <= grant.notBeforeMs &&
        observed.coverageFromMs <= observed.observedAtMs, 'OBSERVER_COVERAGE');
      checkFreshSample('coverage', observed, coverageSource, coverageMaxAgeMs, beforeStatus);
      const currentSource = await readControlObject(status, context);
      const releaseState = readSignedControlRecord(currentSource, release.key);
      context.assertActive();
      const current = now();
      need(exact(releaseState, ['schema', 'scope', 'authorizationSha256', 'state', 'observedAtMs', 'validUntilMs']) &&
        releaseState.schema === 'POINCARE_R4_ISSUER_DISPATCH_STATUS_V1' && isDeepStrictEqual(releaseState.scope, scope) &&
        releaseState.authorizationSha256 === authorization.sha256 &&
        ['ENABLED', 'REVOKED'].includes(releaseState.state) && time(releaseState.observedAtMs) &&
        releaseState.observedAtMs <= current, 'DISPATCH_STATUS');
      if (releaseState.state === 'REVOKED') {
        disabled = true;
        return null;
      }
      checkFreshSample('status', releaseState, currentSource, statusMaxAgeMs, current);
      const deadlineMs = Math.min(authority.expiresAtMs, context.deadlineMs, grant.expiresAtMs,
        releaseState.validUntilMs, observed.validUntilMs);
      if (disabled || current < grant.notBeforeMs || !time(deadlineMs) || current >= deadlineMs) return null;
      return Object.freeze({deadlineMs, assertActive() {
        context.assertActive();
        const nowMs = now();
        need(!disabled && time(nowMs) && nowMs >= grant.notBeforeMs && nowMs < deadlineMs, 'CUTOFF');
      }});
    } catch {
      return null;
    }
  }

  return dispatchPermission;
}
