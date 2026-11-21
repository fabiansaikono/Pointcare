import {isDeepStrictEqual} from 'node:util';
import {decision, decisionKeys, digest, isDigest, isGeneration} from '../records.mjs';

const slotKinds = Object.freeze({
  reservation: ['ADMISSION_RESERVED'], admission: ['STARTED', 'STOP_BEFORE_SEND'],
  egress: ['EGRESS_GRANTED', 'EGRESS_BLOCKED'], stop: ['REQUESTED'], terminal: ['STOPPED_CONFIRMED'],
});
const scopes = Object.freeze({
  gate: {read: ['reservation', 'admission', 'egress', 'stop', 'terminal'],
    create: ['ADMISSION_RESERVED', 'STARTED', 'REQUESTED', 'EGRESS_BLOCKED', 'STOP_BEFORE_SEND', 'STOPPED_CONFIRMED']},
  transport: {read: ['reservation', 'admission', 'stop'], create: ['EGRESS_GRANTED']},
});
const utf8 = new TextDecoder('utf-8', {fatal: true, ignoreBOM: true});
const identifier = /^[a-zA-Z0-9_.-]{1,160}$/u;
const referenceFields = ['bucket', 'object', 'generation', 'metageneration', 'sha256'];
const encoded = value => Buffer.from(JSON.stringify(value));

function need(condition, reason) {
  if (!condition) throw Error(`ISSUER_DECISION_DIGEST_${reason}`);
}

function exact(value, fields) {
  return value !== null && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === fields.length && fields.every(field => Object.hasOwn(value, field));
}

/** readOriginalReference is a custody capability, never a reader of the caller's candidate bytes. */
export function createDecisionDigestReader({service, authority, config, readControlObject, now = Date.now}) {
  need(Object.hasOwn(scopes, service) && typeof now === 'function' &&
    typeof readControlObject === 'function', 'CONFIG');
  const {bucket, readOriginalReference, readIndependentClosure} = config ?? {};
  need(typeof bucket === 'string' && /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/u.test(bucket) &&
    typeof readOriginalReference === 'function' &&
    (service !== 'gate' || typeof readIndependentClosure === 'function'), 'CUSTODY');
  const keys = decisionKeys(authority?.packageSha256, authority?.approvalSha256);
  const {manifestSha256, dispatchId, runId, claimSha256, challengeSha256, notBeforeMs, expiresAtMs} = authority;
  need(authority.purpose === 'R4_ISSUER_SYNTHETIC_ONLY' && isDeepStrictEqual(authority.keys, keys) &&
    authority.pairKey === keys.pairKey && [manifestSha256, claimSha256, challengeSha256].every(isDigest) &&
    [dispatchId, runId].every(value => typeof value === 'string' && identifier.test(value)) &&
    Number.isSafeInteger(notBeforeMs) && notBeforeMs >= 0 &&
    Number.isSafeInteger(expiresAtMs) && notBeforeMs < expiresAtMs, 'AUTHORITY');
  const sealed = Object.freeze({pairKey: keys.pairKey, manifestSha256, dispatchId, runId, claimSha256,
    challengeSha256});
  const slots = new Map(Object.keys(slotKinds).map(slot => [keys[slot], slot]));
  const scope = scopes[service];

  return async function expectedDigest(reference, context) {
    const {operation, key, kind, generation} = reference ?? {};
    const slot = slots.get(key);
    const writing = operation === 'CREATE';
    need(slot && (writing
      ? exact(reference, ['operation', 'key', 'kind']) && scope.create.includes(kind) && slotKinds[slot].includes(kind)
      : operation === 'READ' && exact(reference, ['operation', 'key', 'generation']) &&
        scope.read.includes(slot) && isGeneration(generation)), 'REFERENCE');
    function active() {
      need(typeof context?.assertActive === 'function', 'CUTOFF');
      context.assertActive();
      const time = now();
      need(context?.signal instanceof AbortSignal && !context.signal.aborted &&
        Number.isSafeInteger(context.deadlineMs) && Number.isSafeInteger(time) && time >= 0 &&
        time < context.deadlineMs, 'CUTOFF');
      if (writing && ['ADMISSION_RESERVED', 'STARTED', 'EGRESS_GRANTED'].includes(kind)) {
        need(notBeforeMs <= time && time < expiresAtMs, 'CUTOFF');
      }
    }
    active();
    const originals = new Map();

    async function original(slotName, reason = 'PREDECESSOR', generationConstraint) {
      const cache = `${slotName}:${reason}:${generationConstraint ?? ''}`;
      if (originals.has(cache)) return originals.get(cache);
      active();
      const originalKey = keys[slotName];
      const query = Object.freeze({service, bucket, key: originalKey, purpose: reason, ...sealed});
      const selection = await readOriginalReference(query, context);
      active();
      const extras = reason === 'DIRECT_CREATE_ACK' ? ['ackSignal']
        : reason === 'HANDOFF_PREDECESSOR' ? ['handoffSha256'] : [];
      need(exact(selection, ['reference', ...extras]) &&
        (reason !== 'DIRECT_CREATE_ACK' || selection.ackSignal === context.signal) &&
        (reason !== 'HANDOFF_PREDECESSOR' || isDigest(selection.handoffSha256)), 'ORIGINAL_SELECTION');
      const pin = selection.reference;
      need(exact(pin, referenceFields) && pin.bucket === bucket && pin.object === originalKey &&
        isGeneration(pin.generation) && pin.metageneration === '1' && isDigest(pin.sha256) &&
        (generationConstraint === undefined || generationConstraint === pin.generation), 'ORIGINAL_PIN');
      const checked = Object.freeze({...pin});
      const observed = await readControlObject(checked, context);
      active();
      need(observed?.bytes instanceof Uint8Array && observed.bytes.length > 0 && observed.bytes.length <= 32_768 &&
        observed.generation === checked.generation && observed.metageneration === checked.metageneration &&
        observed.sha256 === checked.sha256, 'ORIGINAL_READ');
      const bytes = Buffer.from(observed.bytes);
      need(digest(bytes) === checked.sha256, 'ORIGINAL_READ');
      const value = JSON.parse(utf8.decode(bytes));
      need(slotKinds[slotName].includes(value?.kind), 'ORIGINAL_KIND');
      const proven = await canonical(value.kind, false);
      active();
      need(bytes.equals(proven), 'ORIGINAL_BYTES');
      const item = {kind: value.kind, generation: checked.generation, bytes, handoffSha256: selection.handoffSha256};
      originals.set(cache, item);
      return item;
    }

    async function predecessor(slotName, expectedKind, reason) {
      const value = await original(slotName, reason);
      need(value.kind === expectedKind, 'PREDECESSOR_KIND');
      return value;
    }

    async function stopEvidence() {
      const request = await predecessor('stop', 'REQUESTED');
      const admission = await original('admission');
      const egress = await original('egress');
      const reservation = admission.kind === 'STARTED' ? await predecessor('reservation', 'ADMISSION_RESERVED') : null;
      need(egress.kind === 'EGRESS_BLOCKED' || reservation !== null, 'STOP_STATE');
      const state = {requestGeneration: request.generation, reservationGeneration: reservation?.generation ?? null,
        admissionGeneration: admission.generation, egressGeneration: egress.generation,
        admissionKind: admission.kind, egressKind: egress.kind};
      const binding = decision(sealed, 'STOP_EVIDENCE_BINDING', state);
      const evidence = await readIndependentClosure(structuredClone(binding), context);
      active();
      need(evidence?.routeClosed === true && evidence.connectionsClosed === true && evidence.coverageComplete === true &&
        isDeepStrictEqual(evidence.binding, binding) && isDigest(evidence.evidenceSha256), 'STOP_EVIDENCE');
      const {requestCount, outcome, receiverGeneration, evidenceSha256} = evidence;
      need((requestCount === 0 && outcome === 'ZERO_REQUESTS' && receiverGeneration === null) ||
        (egress.kind === 'EGRESS_GRANTED' && requestCount === 1 &&
          ((outcome === 'REQUEST_NOT_ACCEPTED' && receiverGeneration === null) ||
            (outcome === 'RECEIVER_ACCEPTED' && isGeneration(receiverGeneration)))), 'STOP_OUTCOME');
      return {...state, requestCount, outcome, receiverGeneration, evidenceSha256};
    }

    async function canonical(row, creating) {
      let fields;
      if (row === 'STARTED') {
        const reservation = await predecessor('reservation', 'ADMISSION_RESERVED',
          creating ? 'DIRECT_CREATE_ACK' : 'PREDECESSOR');
        fields = {reservationGeneration: reservation.generation};
      } else if (row === 'EGRESS_GRANTED') {
        const reason = creating ? 'HANDOFF_PREDECESSOR' : 'PREDECESSOR';
        const reservation = await predecessor('reservation', 'ADMISSION_RESERVED', reason);
        const admission = await predecessor('admission', 'STARTED', reason);
        if (creating) {
          const originalReservation = await predecessor('reservation', 'ADMISSION_RESERVED');
          const originalAdmission = await predecessor('admission', 'STARTED');
          need(reservation.handoffSha256 === admission.handoffSha256 &&
            reservation.generation === originalReservation.generation &&
            admission.generation === originalAdmission.generation, 'HANDOFF');
        }
        fields = {reservationGeneration: reservation.generation, admissionGeneration: admission.generation};
      } else if (row === 'STOPPED_CONFIRMED') {
        fields = await stopEvidence();
      }
      return encoded(decision(sealed, row, fields));
    }

    const bytes = writing ? await canonical(kind, true) : (await original(slot, 'WINNER', generation)).bytes;
    active();
    return digest(bytes);
  };
}
