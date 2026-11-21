import {isDeepStrictEqual} from 'node:util';
import {decision, digest, isDigest, isGeneration} from '../records.mjs';
import {duringOperation} from '../deadline.mjs';
import {createDispatchAuthorization, pinControlSigner, readSignedControlRecord} from './dispatch-authorization.mjs';
import {createDecisionDigestReader} from './decision-digests.mjs';
import {assertClosureEvidenceActive, createClosureEvidenceReader} from './closure-evidence.mjs';

const bucketName = /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/u;
const objectName = /^(?:[a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_-]+\.json$/u;
const referenceKeys = ['bucket', 'object', 'generation', 'metageneration', 'sha256'];
const exact = (v, k) => v && typeof v === 'object' && !Array.isArray(v) &&
  Object.keys(v).length === k.length && k.every(key => Object.hasOwn(v, key));
const id = reference => JSON.stringify(referenceKeys.map(key => reference[key] ?? null));

function need(condition, reason) {
  if (!condition) throw Error(`ISSUER_CONTROL_${reason}`);
}

function checkReference(reference) {
  need(reference && typeof reference.bucket === 'string' && bucketName.test(reference.bucket) &&
    typeof reference.object === 'string' && reference.object.length <= 512 && objectName.test(reference.object) &&
    (exact(reference, ['bucket', 'object']) || (exact(reference, referenceKeys) &&
      isGeneration(reference.generation) && isGeneration(reference.metageneration) && isDigest(reference.sha256))), 'REFERENCE');
  return Object.freeze({...reference});
}

function bindReads(references, readControlObject) {
  need(Array.isArray(references) && references.length > 0 && references.length <= 32, 'SOURCE_LIST');
  const admitted = new Map(references.map(checkReference).map(ref => [id(ref), ref]));
  need(admitted.size === references.length, 'DUPLICATE_SOURCE');
  return (reference, context) => {
    const exactOriginal = admitted.get(id(checkReference(reference)));
    need(exactOriginal, 'UNADMITTED_SOURCE');
    return readControlObject(exactOriginal, context);
  };
}

export function createControlEvidence({service, authority, config, readControlObject, now}) {
  const gate = service === 'gate';
  need((gate || service === 'transport') && typeof readControlObject === 'function' && typeof now === 'function' &&
    exact(config, ['dispatch', 'decisionOriginals', ...(gate ? ['closure'] : [])]), 'CONFIG');
  const {dispatch, decisionOriginals, closure} = config;
  const readDispatch = bindReads([dispatch.authorization, dispatch.status, dispatch.coverage], readControlObject);
  const dispatchPermission = createDispatchAuthorization({authority, config: dispatch, readControlObject: readDispatch, now});
  const dispatchAllowed = async (request, context) => Boolean(await dispatchPermission(request, context));

  need(exact(decisionOriginals, ['source', 'publicKey']) && exact(decisionOriginals.source, ['bucket', 'object']),
    'DECISION_SOURCE');
  const prefix = `trials/${dispatch.trialId}/`;
  need(decisionOriginals.source.object.startsWith(prefix) &&
    ![dispatch.authorization, dispatch.status, dispatch.coverage].some(ref => ref.object === decisionOriginals.source.object),
    'DECISION_SCOPE');
  const readIndex = bindReads([decisionOriginals.source], readControlObject);
  const decisionSigner = pinControlSigner(decisionOriginals.publicKey);
  need([dispatch.releasePublicKey, dispatch.observerPublicKey].every(pem =>
    pinControlSigner(pem).fingerprint !== decisionSigner.fingerprint), 'DECISION_SIGNER_SEPARATION');
  const decisionKeys = new Set(['reservation', 'admission', 'egress', 'stop', 'terminal'].map(slot => authority.keys[slot]));
  const originalsScope = decision(authority, 'DECISION_ORIGINALS');
  delete originalsScope.kind;
  const ownReservations = new WeakMap();
  const closureResults = new WeakMap();
  const selected = new WeakMap();
  let latestIndex;

  let readIndependentClosure;
  if (gate) {
    need(exact(closure, ['config', 'anchor', 'originals']) &&
      exact(closure.config, ['stopDeadlineMs', 'maxAgeMs', 'observer', 'signers']) &&
      closure.config.stopDeadlineMs === authority.stopDeadlineMs &&
      closure.config.observer?.id === dispatch.observerBoundary &&
      exact(closure.anchor, ['source', 'publicKey']) && exact(closure.anchor.source, ['bucket', 'object']) &&
      Array.isArray(closure.originals) && closure.originals.length === 3, 'CLOSURE_CONFIG');
    const readAnchor = bindReads([closure.anchor.source], readControlObject);
    const anchorSigner = pinControlSigner(closure.anchor.publicKey);
    need([dispatch.releasePublicKey, dispatch.observerPublicKey, decisionOriginals.publicKey].every(pem =>
      pinControlSigner(pem).fingerprint !== anchorSigner.fingerprint), 'CLOSURE_SIGNER_SEPARATION');
    const originalSlots = closure.originals.map(checkReference);
    const slotId = ({bucket, object}) => `${bucket}/${object}`;
    const admittedSlots = new Set(originalSlots.map(slotId));
    need(admittedSlots.size === 3 && originalSlots.some(ref => ref.object === prefix + 'transport-outcome.json') &&
      originalSlots.every(ref => exact(ref, ['bucket', 'object']) &&
      ref.object.startsWith(prefix)) && closure.anchor.source.object.startsWith(prefix) &&
      !admittedSlots.has(slotId(closure.anchor.source)), 'CLOSURE_SOURCE_SCOPE');
    function readClosureOriginal(reference, context) {
      const pin = checkReference(reference);
      need(exact(pin, referenceKeys) && admittedSlots.has(slotId(pin)), 'CLOSURE_REFERENCE_UNSELECTED');
      return readControlObject(pin, context);
    }
    readIndependentClosure = async (binding, context) => {
      try {
        context.assertActive();
        const previous = closureResults.get(context.signal);
        if (previous && isDeepStrictEqual(previous.evidence.binding, binding)) {
          assertClosureEvidenceActive(previous.evidence, previous.context);
          return previous.evidence;
        }
        const original = await readAnchor(closure.anchor.source, context);
        const anchor = readSignedControlRecord(original, anchorSigner.key);
        context.assertActive();
        const current = now();
        need(exact(anchor, ['schema', 'binding', 'readyReference', 'issuedAtMs', 'expiresAtMs']) &&
          anchor.schema === 'POINCARE_R4_ISSUER_CLOSURE_ANCHOR_V1' && isDeepStrictEqual(anchor.binding, binding) &&
          Number.isSafeInteger(anchor.issuedAtMs) && Number.isSafeInteger(anchor.expiresAtMs) &&
          authority.notBeforeMs <= anchor.issuedAtMs && anchor.issuedAtMs <= current && current < anchor.expiresAtMs &&
          anchor.expiresAtMs <= authority.stopDeadlineMs, 'CLOSURE_ANCHOR');
        const ready = checkReference(anchor.readyReference);
        need(exact(ready, referenceKeys) && admittedSlots.has(slotId(ready)), 'CLOSURE_READY_REFERENCE');
        const read = createClosureEvidenceReader({authority,
          config: {...closure.config, readyReference: ready, anchorExpiresAtMs: anchor.expiresAtMs},
          readControlObject: readClosureOriginal, now});
        const evidence = await read(binding, context);
        context.assertActive();
        if (evidence) {
          assertClosureEvidenceActive(evidence, context);
          closureResults.set(context.signal, Object.freeze({evidence, context}));
        }
        return evidence;
      } catch {
        return null;
      }
    };
  }

  function withVerifiedClosure(request, context, create) {
    const closed = closureResults.get(context.signal);
    need(closed && request.key === authority.keys.terminal && request.value.evidenceSha256 === closed.evidence.evidenceSha256,
      'TERMINAL_CLOSURE');
    const deadlineMs = assertClosureEvidenceActive(closed.evidence, closed.context);
    return duringOperation({now}, Math.min(context.deadlineMs, deadlineMs), operation =>
      operation.run(bounded => {
        // The digest reader uses the child scope; reuse only the original action's verified object.
        closureResults.set(bounded.signal, closed);
        return create(request, Object.freeze({...bounded, assertActive() {
          bounded.assertActive();
          assertClosureEvidenceActive(closed.evidence, closed.context);
        }}));
      }), context.signal);
  }

  function rememberReservation(record, context) {
    if (record?.status !== 'CREATED' || record.key !== authority.keys.reservation ||
        !isGeneration(record.generation) || !isDeepStrictEqual(record.value, decision(authority, 'ADMISSION_RESERVED'))) return;
    context.assertActive();
    ownReservations.set(context.signal, Object.freeze({bucket: authority.bucket, object: record.key,
      generation: record.generation, metageneration: '1', sha256: digest(JSON.stringify(record.value))}));
  }

  async function signedWinner(query, context) {
    context.assertActive();
    const source = await readIndex(decisionOriginals.source, context);
    const value = readSignedControlRecord(source, decisionSigner.key);
    context.assertActive();
    const current = now();
    need(exact(value, ['schema', 'scope', 'issuedAtMs', 'records']) &&
      value.schema === 'POINCARE_R4_ISSUER_DECISION_ORIGINALS_V1' && isDeepStrictEqual(value.scope, originalsScope) &&
      Number.isSafeInteger(value.issuedAtMs) && value.issuedAtMs >= authority.notBeforeMs && value.issuedAtMs <= current &&
      current < authority.stopDeadlineMs && Array.isArray(value.records) && value.records.length <= decisionKeys.size,
    'DECISION_INDEX');
    need(!latestIndex || value.issuedAtMs > latestIndex.issuedAtMs ||
      (value.issuedAtMs === latestIndex.issuedAtMs && source.sha256 === latestIndex.sha256), 'DECISION_INDEX_ROLLBACK');
    const pins = new Map();
    for (const item of value.records) {
      const pin = checkReference(item);
      need(exact(pin, referenceKeys) && pin.bucket === authority.bucket && decisionKeys.has(pin.object) &&
        pin.metageneration === '1' && !pins.has(pin.object), 'DECISION_PIN');
      pins.set(pin.object, pin);
    }
    latestIndex = {issuedAtMs: value.issuedAtMs, sha256: source.sha256};
    const pin = pins.get(query.key);
    need(pin, 'DECISION_WINNER');
    return pin;
  }

  function bindDecisionDigests(verifiedHandoff) {
    async function readOriginalReference(query, context) {
      let reference;
      let extra;
      if (query.purpose === 'DIRECT_CREATE_ACK') {
        need(gate && query.key === authority.keys.reservation, 'DECISION_DIRECT_ACK');
        reference = ownReservations.get(context.signal);
        need(reference, 'DECISION_DIRECT_ACK');
        extra = {ackSignal: context.signal};
      } else {
        need(['WINNER', 'PREDECESSOR', 'HANDOFF_PREDECESSOR'].includes(query.purpose), 'DECISION_PURPOSE');
        reference = await signedWinner(query, context);
        if (query.purpose === 'HANDOFF_PREDECESSOR') {
          const field = query.key === authority.keys.reservation ? 'reservationGeneration' :
            query.key === authority.keys.admission ? 'admissionGeneration' : null;
          need(!gate && field && verifiedHandoff?.handoff[field] === reference.generation &&
            isDigest(verifiedHandoff.handoffEvidence?.sha256), 'DECISION_HANDOFF');
          extra = {handoffSha256: verifiedHandoff.handoffEvidence.sha256};
        }
      }
      let refs = selected.get(context.signal);
      if (!refs) selected.set(context.signal, refs = new Map());
      refs.set(id(reference), reference);
      return {reference, ...extra};
    }

    return createDecisionDigestReader({service, authority, now,
      config: {bucket: authority.bucket, readOriginalReference, readIndependentClosure},
      readControlObject(reference, context) {
        const pin = selected.get(context.signal)?.get(id(checkReference(reference)));
        need(pin, 'DECISION_REFERENCE_UNSELECTED');
        return readControlObject(pin, context);
      }});
  }

  return Object.freeze({dispatchAllowed, dispatchPermission, readIndependentClosure, withVerifiedClosure,
    bindDecisionDigests, rememberReservation});
}
