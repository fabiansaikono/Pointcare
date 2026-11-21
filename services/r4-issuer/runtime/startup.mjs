import {once} from 'node:events';
import {open} from 'node:fs/promises';
import {createIssuerHttpServer} from '../listener.mjs';
import {createIssuerServiceAuthenticator} from '../service-auth.mjs';
import {digest, isDigest, issuerBoundary} from '../records.mjs';
import {bindIssuerServicePorts} from './protected-service-ports.mjs';

const startupFile = '/run/poincare/issuer/startup.json';
const startupLimit = 98_304;
const identityLimit = 65_536;
const exact = (value, fields) => value && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === fields.length && fields.every(field => Object.hasOwn(value, field));
const readLimit = value => Number.isSafeInteger(value) && value > 0 && value <= 65_536;

function demand(ok, reason) {
  if (!ok) throw Error(`ISSUER_MANAGED_${reason}`);
}

async function readStartup() {
  const file = await open(startupFile, 'r');
  try {
    const info = await file.stat();
    demand(info.isFile() && info.size > 0 && info.size <= startupLimit, 'STARTUP_BYTES');
    const bytes = Buffer.alloc(info.size + 1);
    let length = 0;
    while (length < bytes.length) {
      const {bytesRead} = await file.read(bytes, length, bytes.length - length, length);
      if (bytesRead === 0) break;
      length += bytesRead;
    }
    demand(length === info.size, 'STARTUP_BYTES');
    return bytes.subarray(0, length);
  } finally {
    await file.close();
  }
}

/** Prepare without listening. The approved adapter must compose lazy ports without provider I/O. */
export async function prepareManagedIssuer(service, {env = process.env, readConfig = readStartup,
  servicePorts, now = Date.now} = {}) {
  demand(service === 'gate' || service === 'transport', 'SERVICE');
  const {PORT, ISSUER_STARTUP_SHA256} = env;
  demand(typeof PORT === 'string' && /^[1-9][0-9]{0,4}$/u.test(PORT) && Number(PORT) <= 65_535, 'PORT');
  demand(typeof ISSUER_STARTUP_SHA256 === 'string' && isDigest(ISSUER_STARTUP_SHA256), 'STARTUP_PIN');
  const input = await readConfig();
  demand(input instanceof Uint8Array && input.byteLength > 0 && input.byteLength <= startupLimit, 'STARTUP_BYTES');
  const raw = Buffer.from(input);
  demand(digest(raw) === ISSUER_STARTUP_SHA256, 'STARTUP_PIN');
  const seal = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(raw));
  const fields = ['schema','service','credentialPresentation','identityBase64','identitySha256','policy',
    'startBytes','bodySha256','portsBinding'];
  demand(exact(seal, service === 'transport' ? [...fields, 'envelopeBytes'] : fields) &&
    seal.schema === 'POINCARE_R4_ISSUER_STARTUP_V1' && seal.service === service &&
    seal.credentialPresentation === 'AUTHORIZATION_COMPACT_RS256' &&
    readLimit(seal.startBytes) && typeof seal.bodySha256 === 'string' && isDigest(seal.bodySha256) &&
    (service !== 'transport' || readLimit(seal.envelopeBytes)) &&
    exact(seal.portsBinding, ['sourceRef', 'sha256']) &&
    typeof seal.portsBinding.sourceRef === 'string' && seal.portsBinding.sourceRef.length > 0 &&
    seal.portsBinding.sourceRef.length <= 1024 && !/\s/u.test(seal.portsBinding.sourceRef) &&
    typeof seal.portsBinding.sha256 === 'string' && isDigest(seal.portsBinding.sha256), 'STARTUP_SCOPE');
  demand(typeof seal.identityBase64 === 'string' && seal.identityBase64.length <= Math.ceil(identityLimit / 3) * 4,
    'IDENTITY_ENCODING');
  const configBytes = Buffer.from(seal.identityBase64, 'base64');
  demand(configBytes.toString('base64') === seal.identityBase64 && configBytes.length <= identityLimit, 'IDENTITY_ENCODING');
  const {emails} = createIssuerServiceAuthenticator({configBytes, configSha256: seal.identitySha256, now});
  issuerBoundary(seal.policy, {now}); // Reuse the reviewed policy validation without calling any effect port.
  demand(Object.entries(emails).every(([role, email]) => seal.policy.actors[role] === email), 'ACTORS');
  const {keyset} = JSON.parse(configBytes.toString('utf8'));
  const deadline = service === 'gate' ? seal.policy.stopDeadlineMs : seal.policy.deadlineMs;
  function assertCurrent() {
    const current = now();
    demand(Number.isSafeInteger(current) && current >= keyset.validFromMs && current < keyset.validUntilMs &&
      current < deadline, 'STARTUP_WINDOW');
  }

  assertCurrent();
  const composition = await bindIssuerServicePorts(Object.freeze({service,
    sourceRef: seal.portsBinding.sourceRef, expectedSha256: seal.portsBinding.sha256,
    policy: structuredClone(seal.policy), bodySha256: seal.bodySha256,
    configBytes: Buffer.from(configBytes), configSha256: seal.identitySha256, startBytes: seal.startBytes,
    envelopeBytes: seal.envelopeBytes, now}), servicePorts);
  demand(composition?.bindingSha256 === seal.portsBinding.sha256 && composition.ports, 'PORTS_PIN');
  assertCurrent();
  const server = createIssuerHttpServer({service, configBytes, configSha256: seal.identitySha256,
    policy: seal.policy, ports: {...composition.ports, now}, startBytes: seal.startBytes, bodySha256: seal.bodySha256,
    envelopeBytes: seal.envelopeBytes, openTransportEnvelope: composition.openTransportEnvelope});
  return {server, port: Number(PORT), assertCurrent};
}

export async function runManagedIssuer(service) {
  try {
    const {server, port, assertCurrent} = await prepareManagedIssuer(service);
    assertCurrent();
    const ready = once(server, 'listening');
    server.listen(port, '0.0.0.0');
    await ready;
  } catch {
    process.stderr.write('ISSUER_MANAGED_START_REFUSED\n');
    process.exitCode = 2;
  }
}
