import {createHash} from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';

const sha256 = /^[a-f0-9]{64}$/u;
const generation = /^[1-9][0-9]{0,19}$/u;
const identifier = /^[a-zA-Z0-9_.-]{1,160}$/u;
const roles = ['writer', 'stopper', 'closer', 'gate', 'transport'];

export const digest = bytes => createHash('sha256').update(bytes).digest('hex');
export const isGeneration = value => typeof value === 'string' && generation.test(value);
export const isDigest = value => typeof value === 'string' && sha256.test(value);

export function decisionKeys(packageSha256, approvalSha256) {
  if (!isDigest(packageSha256) || !isDigest(approvalSha256)) throw Error('INVALID_PAIR');
  const pairKey = digest(`poincare-r4-dispatch-pair-v1\n${packageSha256}\n${approvalSha256}\n`);
  const prefix = `decisions/sha256/${pairKey}`;
  return {pairKey, reservation: `${prefix}/admission-attempt.json`, admission: `${prefix}/decision.json`, egress: `${prefix}/egress-decision.json`,
    stop: `${prefix}/stop-request.json`, terminal: `${prefix}/terminal-stop.json`};
}

export function checkedBody(body, expectedHash) {
  if (!(body instanceof Uint8Array) || body.length === 0) throw Error('INVALID_BODY');
  const bytes = Buffer.from(body);
  if (digest(bytes) !== expectedHash) throw Error('INVALID_BODY_HASH');
  return bytes;
}

export function decision(authority, kind, fields = {}) {
  const {pairKey, manifestSha256, dispatchId, runId, claimSha256, challengeSha256} = authority;
  return {kind, pairKey, manifestSha256, dispatchId, runId, claimSha256, challengeSha256, ...fields};
}

export function requireDecision(record, authority, kind, fields) {
  if (!record || !isDeepStrictEqual(record.value, decision(authority, kind, fields))) {
    throw Error('DECISION_BINDING');
  }
}

export function issuerBoundary(policy, ports) {
  const actors = Object.freeze({...policy.actors});
  const {manifestSha256, gateRevision, transportRevision, receiver, deadlineMs, stopDeadlineMs} = policy;
  if (!isDigest(manifestSha256) || !receiver || !gateRevision || !transportRevision ||
      !Number.isSafeInteger(deadlineMs) || !Number.isSafeInteger(stopDeadlineMs) || stopDeadlineMs < deadlineMs ||
      roles.some(role => typeof actors[role] !== 'string' || actors[role].length === 0) ||
      new Set(roles.map(role => actors[role])).size !== roles.length) throw Error('INVALID_POLICY');

  async function authority(operation, credential, role) {
    if (await operation.run(context => ports.authenticate(credential, actors[role], context)) !== true) {
      throw Error('UNAUTHENTICATED');
    }
    const bytes = await operation.run(context => ports.readVerifiedManifest(context));
    if (!(bytes instanceof Uint8Array) || digest(bytes) !== manifestSha256) throw Error('MANIFEST_PIN');
    const manifest = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(bytes));
    const keys = Object.freeze(decisionKeys(manifest.packageSha256, manifest.approvalSha256));
    if (manifest.purpose !== 'R4_ISSUER_SYNTHETIC_ONLY' || !isDeepStrictEqual(manifest.keys, keys) ||
        !isDeepStrictEqual(manifest.actors, actors) || manifest.receiver !== receiver ||
        manifest.gateRevision !== gateRevision || manifest.transportRevision !== transportRevision ||
        ![manifest.claimSha256, manifest.challengeSha256, manifest.bodySha256].every(isDigest) ||
        ![manifest.dispatchId, manifest.runId].every(value => typeof value === 'string' && identifier.test(value)) ||
        !Number.isSafeInteger(manifest.notBeforeMs) || !Number.isSafeInteger(manifest.expiresAtMs) ||
        manifest.notBeforeMs < 0 || manifest.notBeforeMs >= manifest.expiresAtMs ||
        manifest.expiresAtMs !== deadlineMs) {
      throw Error('MANIFEST_BINDING');
    }
    return Object.freeze({...manifest, keys, actors, manifestSha256, pairKey: keys.pairKey});
  }

  function inWindow(authority) {
    const now = ports.now();
    return Number.isSafeInteger(now) && authority.notBeforeMs <= now && now < authority.expiresAtMs;
  }

  async function mayDispatch(operation, authority) {
    return inWindow(authority) && await operation.run(context => ports.dispatchAllowed(authority, context)) === true;
  }

  function checkedRecord(key, record) {
    if (record?.key !== key || !isGeneration(record.generation) || !record.value) throw Error('RECORD_READBACK');
    return record;
  }

  async function read(operation, key) {
    const record = await operation.run(context => ports.store.read(key, context));
    return record === null ? null : checkedRecord(key, record);
  }

  async function create(operation, key, value) {
    const ack = await operation.run(context => ports.store.create({key, ifGenerationMatch: 0, value}, context));
    if (ack?.status === 'EXISTS' && ack.key === key) return null;
    if (ack?.status !== 'CREATED') throw Error('CREATE_ACK_UNCERTAIN');
    const record = checkedRecord(key, ack);
    if (!isDeepStrictEqual(record.value, value)) throw Error('CREATE_ACK_BINDING');
    return record;
  }

  // Only stop processing may use a readback to resume a fixed create intent.
  // A readback never conveys the original execution's permission to dispatch.
  async function createOrRead(operation, key, value) {
    try {
      const created = await create(operation, key, value);
      if (created) return created;
    } catch {
      // An uncertain stop write is reconciled against its exact immutable slot.
    }
    return read(operation, key);
  }

  return {authority, deadlineMs, stopDeadlineMs, mayDispatch, read, create, createOrRead};
}
