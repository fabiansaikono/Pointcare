import {constants, createPublicKey, verify} from 'node:crypto';
import {digest, isDigest} from './records.mjs';

export const issuerActions = Object.freeze({
  start: Object.freeze({service:'gate', role:'writer'}),
  stop: Object.freeze({service:'gate', role:'stopper'}),
  confirmStop: Object.freeze({service:'gate', role:'closer'}),
  dispatch: Object.freeze({service:'transport', role:'gate'}),
});
const roles = ['writer','stopper','closer','gate','transport'];
const issuers = new Set(['https://accounts.google.com','accounts.google.com']);
const kidPattern = /^[A-Za-z0-9._-]{1,128}$/u;
const googleSubject = /^[0-9]{6,32}$/u;
const accountName = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const utf8 = new TextDecoder('utf-8', {fatal:true, ignoreBOM:true});
const exact = (value, names) => value !== null && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === names.length && names.every(name => Object.hasOwn(value, name));
const epochMs = value => Number.isSafeInteger(value) && value >= 0 && value <= 8_640_000_000_000_000;

function requireConfig(condition) {
  if (!condition) throw Error('ISSUER_SERVICE_IDENTITY_CONFIG');
}

function audience(value) {
  if (typeof value !== 'string' || value.length > 512) return false;
  const url = URL.parse(value);
  return url?.protocol === 'https:' && !url.port && (value === url.origin || value === `${url.origin}/`);
}

function jwtPart(part) {
  if (!/^[A-Za-z0-9_-]+$/u.test(part)) throw Error('ISSUER_TOKEN_ENCODING');
  const bytes = Buffer.from(part, 'base64url');
  if (bytes.toString('base64url') !== part) throw Error('ISSUER_TOKEN_ENCODING');
  return bytes;
}

// Portable service-only subset of Poincaré's pinned audio token verifier.
// The expected digest and keyset come from protected operator inputs, never from the token.
export function createIssuerServiceAuthenticator({configBytes, configSha256, now}) {
  requireConfig(configBytes instanceof Uint8Array && configBytes.length <= 65536 &&
    isDigest(configSha256) && typeof now === 'function');
  const raw = Buffer.from(configBytes);
  requireConfig(digest(raw) === configSha256);
  const config = JSON.parse(utf8.decode(raw));
  requireConfig(exact(config, ['projectId','issuer','audiences','principals','keyset','maxLifetimeSeconds']));
  const {projectId, issuer, audiences, principals, keyset, maxLifetimeSeconds} = config;
  requireConfig(typeof projectId === 'string' && /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u.test(projectId) &&
    issuers.has(issuer) && exact(audiences, ['gate','transport']) && Object.values(audiences).every(audience) &&
    new URL(audiences.gate).origin !== new URL(audiences.transport).origin && exact(principals, roles) &&
    Number.isSafeInteger(maxLifetimeSeconds) && maxLifetimeSeconds > 0 && maxLifetimeSeconds <= 3600 &&
    exact(keyset, ['validFromMs','validUntilMs','keys']) && epochMs(keyset.validFromMs) && epochMs(keyset.validUntilMs) &&
    keyset.validFromMs < keyset.validUntilMs && keyset.keys && typeof keyset.keys === 'object' && !Array.isArray(keyset.keys));

  const suffix = `@${projectId}.iam.gserviceaccount.com`;
  const entries = roles.map(role => {
    const principal = principals[role];
    requireConfig(exact(principal, ['subject','email']) && typeof principal.subject === 'string' &&
      googleSubject.test(principal.subject) && typeof principal.email === 'string' && principal.email.endsWith(suffix) &&
      accountName.test(principal.email.slice(0, -suffix.length)));
    return principal;
  });
  requireConfig(new Set(entries.map(value => value.subject)).size === roles.length &&
    new Set(entries.map(value => value.email)).size === roles.length);

  const published = Object.entries(keyset.keys);
  requireConfig(published.length > 0 && published.length <= 8);
  const keys = new Map(published.map(([kid, pem]) => {
    requireConfig(kidPattern.test(kid) && typeof pem === 'string' && pem.length <= 8192 &&
      /^-----BEGIN (?:PUBLIC KEY|CERTIFICATE)-----\r?\n/u.test(pem) && !pem.includes('PRIVATE KEY'));
    const key = createPublicKey(pem);
    const bits = key.asymmetricKeyDetails?.modulusLength;
    requireConfig(key.type === 'public' && key.asymmetricKeyType === 'rsa' && bits >= 2048 && bits <= 4096);
    return [kid, key];
  }));

  function verifyAction(action, token) {
    if (typeof action !== 'string' || !Object.hasOwn(issuerActions, action) ||
        typeof token !== 'string' || token.length > 8192) return null;
    const {service, role} = issuerActions[action];
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const [head, body, signature] = parts.map(jwtPart);
      const header = JSON.parse(utf8.decode(head));
      if (!exact(header, ['alg','typ','kid']) || header.alg !== 'RS256' || header.typ !== 'JWT') return null;
      const key = keys.get(header.kid);
      if (!key || !verify('RSA-SHA256', Buffer.from(`${parts[0]}.${parts[1]}`, 'ascii'),
        {key, padding:constants.RSA_PKCS1_PADDING}, signature)) return null;

      const claims = JSON.parse(utf8.decode(body));
      const principal = principals[role];
      const nowMs = now();
      const issuedAtMs = claims?.iat * 1000;
      const expiresAtMs = claims?.exp * 1000;
      if (!epochMs(nowMs) || nowMs < keyset.validFromMs || nowMs >= keyset.validUntilMs ||
          claims?.iss !== issuer || claims.aud !== audiences[service] || claims.sub !== principal.subject ||
          claims.email !== principal.email || claims.email_verified !== true || !Number.isSafeInteger(claims.iat) ||
          !Number.isSafeInteger(claims.exp) || !epochMs(issuedAtMs) || !epochMs(expiresAtMs) || issuedAtMs > nowMs ||
          nowMs >= expiresAtMs || expiresAtMs - issuedAtMs > maxLifetimeSeconds * 1000 ||
          (Object.hasOwn(claims, 'nbf') && (!Number.isSafeInteger(claims.nbf) || !epochMs(claims.nbf * 1000) ||
            claims.nbf * 1000 > nowMs))) return null;
      return Object.freeze({action, role, subject:principal.subject, email:principal.email,
        deadlineMs:Math.min(expiresAtMs, keyset.validUntilMs)});
    } catch {
      return null;
    }
  }

  return Object.freeze({verifyAction, emails:Object.freeze(Object.fromEntries(roles.map(role => [role, principals[role].email])))});
}
