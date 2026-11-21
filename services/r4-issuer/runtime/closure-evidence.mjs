import {createPublicKey, verify} from 'node:crypto';
import {isDeepStrictEqual} from 'node:util';
import {decision, decisionKeys, digest, isDigest, isGeneration} from '../records.mjs';
import {duringOperation} from '../deadline.mjs';

const referenceFields = ['bucket', 'object', 'generation', 'metageneration', 'sha256'];
const stateFields = ['requestGeneration', 'reservationGeneration', 'admissionGeneration',
  'egressGeneration', 'admissionKind', 'egressKind'];
const encode = value => Buffer.from(JSON.stringify(value));
const copy = value => structuredClone(value);
const exact = (value, fields) => value !== null && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === fields.length && fields.every(field => Object.hasOwn(value, field));
const time = value => Number.isSafeInteger(value) && value >= 0;
const verifiedClosures = new WeakMap();
function need(condition) { if (!condition) throw Error('ISSUER_CLOSURE_EVIDENCE_REFUSED'); }

/** Recheck the original, operation-bound evidence at every use, especially after credential I/O.
 * Clones and caller-supplied summaries carry no authority. The return value is an exclusive cutoff.
 */
export function assertClosureEvidenceActive(evidence, context) {
  const lease = verifiedClosures.get(evidence);
  need(lease && typeof context?.assertActive === 'function' && context.signal === lease.signal &&
    !context.signal.aborted && time(context.deadlineMs));
  context.assertActive();
  const current = lease.now();
  const cutoff = Math.min(lease.validUntilMs, context.deadlineMs);
  need(time(current) && current < cutoff);
  return cutoff;
}
function reference(value) {
  need(exact(value, referenceFields) && typeof value.bucket === 'string' &&
    /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/u.test(value.bucket) &&
    typeof value.object === 'string' && value.object.length > 0 && value.object.length <= 1024 &&
    !/[\u0000-\u001f\u007f]/u.test(value.object) && isGeneration(value.generation) &&
    value.metageneration === '1' && isDigest(value.sha256));
  return Object.freeze(copy(value));
}
function publicKey(pin) {
  need(exact(pin, ['publicKeySpkiBase64', 'publicKeySha256']) && isDigest(pin.publicKeySha256) &&
    typeof pin.publicKeySpkiBase64 === 'string' && pin.publicKeySpkiBase64.length <= 1024);
  const bytes = Buffer.from(pin.publicKeySpkiBase64, 'base64');
  need(bytes.toString('base64') === pin.publicKeySpkiBase64 && digest(bytes) === pin.publicKeySha256);
  const key = createPublicKey({key: bytes, format: 'der', type: 'spki'});
  need(key.asymmetricKeyType === 'ed25519');
  return key;
}

/** Verifies original custody bytes. It neither observes traffic nor establishes live custody. */
export function createClosureEvidenceReader({authority, config, readControlObject, now = Date.now}) {
  need(typeof now === 'function' && typeof readControlObject === 'function');
  const a = copy(authority);
  const c = copy(config);
  const keys = decisionKeys(a.packageSha256, a.approvalSha256);
  need(a.purpose === 'R4_ISSUER_SYNTHETIC_ONLY' && isDeepStrictEqual(a.keys, keys) &&
    a.pairKey === keys.pairKey && [a.manifestSha256, a.claimSha256, a.challengeSha256, a.bodySha256].every(isDigest) &&
    [a.dispatchId, a.runId].every(value => typeof value === 'string' && /^[a-zA-Z0-9_.-]{1,160}$/u.test(value)) &&
    typeof a.receiver === 'string' && a.receiver.length > 0 && time(a.notBeforeMs) &&
    time(a.expiresAtMs) && a.notBeforeMs < a.expiresAtMs &&
    time(c.stopDeadlineMs) && c.stopDeadlineMs >= a.expiresAtMs &&
    Number.isSafeInteger(c.maxAgeMs) && c.maxAgeMs > 0 &&
    exact(c.observer, ['id', 'bootSha256', 'routeSha256']) &&
    typeof c.observer.id === 'string' && c.observer.id.length > 0 &&
    isDigest(c.observer.bootSha256) && isDigest(c.observer.routeSha256));
  const readyReference = reference(c.readyReference);
  const signers = Object.fromEntries(['ready', 'coverage', 'outcome'].map(role => [role, publicKey(c.signers?.[role])]));
  // The fixed factory supplies the verified outer anchor's expiry. Direct pinned-reader use
  // has no outer anchor; its original context and stop deadline still bound every result.
  const anchorExpiresAtMs = Object.hasOwn(c, 'anchorExpiresAtMs') ? c.anchorExpiresAtMs : c.stopDeadlineMs;
  need(time(anchorExpiresAtMs) && anchorExpiresAtMs <= c.stopDeadlineMs);

  return async function readIndependentClosure(candidate, context) {
    try {
      // Copy before any await: neither callers nor I/O may mutate the compared binding/pins.
      const binding = copy(candidate);
      const state = Object.fromEntries(stateFields.map(field => [field, binding?.[field]]));
      need(isDeepStrictEqual(binding, decision(a, 'STOP_EVIDENCE_BINDING', state)) &&
        [state.requestGeneration, state.admissionGeneration, state.egressGeneration].every(isGeneration) &&
        ((state.admissionKind === 'STARTED' && isGeneration(state.reservationGeneration) &&
          ['EGRESS_GRANTED', 'EGRESS_BLOCKED'].includes(state.egressKind)) ||
         (state.admissionKind === 'STOP_BEFORE_SEND' && state.reservationGeneration === null &&
          state.egressKind === 'EGRESS_BLOCKED')) &&
        typeof context?.assertActive === 'function' && context.signal instanceof AbortSignal &&
        time(context.deadlineMs));
      const deadlineMs = Math.min(context.deadlineMs, c.stopDeadlineMs, anchorExpiresAtMs);
      return await duringOperation({now}, deadlineMs, async operation => {
        let validUntilMs = deadlineMs;
        function active() {
          context.assertActive();
          operation.context.assertActive();
          const current = now();
          need(time(current) && current < validUntilMs);
        }
        function fresh(atMs) {
          const current = now();
          need(time(atMs) && atMs <= current && current - atMs <= c.maxAgeMs && atMs < c.stopDeadlineMs);
        }
        async function original(pin, role) {
          active();
          // A separate copy is given to I/O; the independent comparison pin remains private.
          const readPin = Object.freeze({...pin});
          const raw = await operation.run(inner => readControlObject(readPin, inner));
          active();
          need(raw?.bytes instanceof Uint8Array && raw.bytes.byteLength > 0 && raw.bytes.byteLength <= 65_536);
          const bytes = Buffer.from(raw.bytes);
          need(raw.generation === pin.generation && raw.metageneration === pin.metageneration &&
            raw.sha256 === pin.sha256 && digest(bytes) === pin.sha256);
          const text = new TextDecoder('utf-8', {fatal: true, ignoreBOM: true}).decode(bytes);
          const frame = JSON.parse(text);
          need(exact(frame, ['body', 'signature']) && JSON.stringify(frame) === text &&
            typeof frame.signature === 'string' && /^[A-Za-z0-9+/]{86}==$/u.test(frame.signature));
          const signature = Buffer.from(frame.signature, 'base64');
          need(signature.toString('base64') === frame.signature &&
            verify(null, encode(frame.body), signers[role], signature));
          need(isDeepStrictEqual(frame.body?.binding, binding));
          return frame.body;
        }

        active();
        const ready = await original(readyReference, 'ready');
        need(exact(ready, ['schema', 'stage', 'binding', 'coverage', 'outcome', 'readyAtMs', 'expiresAtMs']) &&
          ready.schema === 'POINCARE_ISSUER_CLOSURE_READY_V1' && ready.stage === 'READY_BEFORE_TERMINAL');
        fresh(ready.readyAtMs);
        need(time(ready.expiresAtMs) && ready.readyAtMs < ready.expiresAtMs && now() < ready.expiresAtMs &&
          ready.expiresAtMs <= c.stopDeadlineMs);
        // maxAge is inclusive at integer milliseconds; preserve that boundary as an exclusive cutoff.
        validUntilMs = Math.min(validUntilMs, ready.expiresAtMs, ready.readyAtMs + c.maxAgeMs + 1);
        active();
        const coveragePin = reference(ready.coverage);
        const outcomePin = reference(ready.outcome);
        need(new Set([readyReference, coveragePin, outcomePin].map(pin => `${pin.bucket}/${pin.object}`)).size === 3);
        const coverage = await original(coveragePin, 'coverage');
        need(exact(coverage, ['schema', 'binding', 'observer', 'receiver', 'bodySha256', 'coverageComplete', 'events']) &&
          coverage.schema === 'POINCARE_ISSUER_COVERAGE_V1' && isDeepStrictEqual(coverage.observer, c.observer) &&
          coverage.receiver === a.receiver && coverage.bodySha256 === a.bodySha256 && coverage.coverageComplete === true &&
          Array.isArray(coverage.events) && coverage.events.length >= 3 && coverage.events.length <= 128);
        let previous = null, lastAt = 0, fenced = false, ended = false;
        let firstByte = null;
        const connections = new Set(), seen = new Set();
        for (let i = 0; i < coverage.events.length; i++) {
          const event = coverage.events[i];
          const additions = {COVERAGE_BEGIN: [], CONNECTION_OPEN: ['connectionId'],
            FIRST_BYTE: ['connectionId', 'byteLength', 'claimSha256'], FENCE_CLOSED: ['stopRequestGeneration'],
            CONNECTION_CLOSED: ['connectionId'], COVERAGE_END: []}[event?.kind];
          need(additions && exact(event, ['sequence', 'previousSha256', 'kind', 'atMs', ...additions, 'sha256']));
          const {sha256, ...payload} = event;
          need(event.sequence === i + 1 && event.previousSha256 === previous && digest(encode(payload)) === sha256 &&
            time(event.atMs) && event.atMs >= lastAt && event.atMs <= ready.readyAtMs && !ended);
          if (i === 0) need(event.kind === 'COVERAGE_BEGIN' && event.atMs <= a.notBeforeMs);
          else need(event.kind !== 'COVERAGE_BEGIN');
          if (Object.hasOwn(event, 'connectionId')) need(typeof event.connectionId === 'string' &&
            /^[a-zA-Z0-9_.-]{1,160}$/u.test(event.connectionId));
          if (event.kind === 'CONNECTION_OPEN') {
            need(!fenced && !seen.has(event.connectionId));
            connections.add(event.connectionId); seen.add(event.connectionId);
          } else if (event.kind === 'FIRST_BYTE') {
            need(!fenced && !firstByte && connections.has(event.connectionId) &&
              event.claimSha256 === a.claimSha256 && Number.isSafeInteger(event.byteLength) && event.byteLength > 0 &&
              event.atMs >= a.notBeforeMs && event.atMs < a.expiresAtMs);
            firstByte = event;
          } else if (event.kind === 'FENCE_CLOSED') {
            need(!fenced && event.stopRequestGeneration === state.requestGeneration); fenced = true;
          } else if (event.kind === 'CONNECTION_CLOSED') {
            need(connections.delete(event.connectionId));
          } else if (event.kind === 'COVERAGE_END') {
            need(i === coverage.events.length - 1 && fenced && connections.size === 0); ended = true;
          }
          previous = sha256; lastAt = event.atMs;
        }
        need(ended); fresh(lastAt);
        validUntilMs = Math.min(validUntilMs, lastAt + c.maxAgeMs + 1);
        active();
        const outcome = await original(outcomePin, 'outcome');
        need(exact(outcome, ['schema', 'binding', 'receiver', 'bodySha256', 'requestCount', 'outcome',
          'receiverGeneration', 'connectionId', 'final', 'finalAtMs']) &&
          outcome.schema === 'POINCARE_ISSUER_TRANSPORT_OUTCOME_V1' && outcome.final === true &&
          outcome.receiver === a.receiver && outcome.bodySha256 === a.bodySha256 && time(outcome.finalAtMs) &&
          outcome.finalAtMs >= (firstByte?.atMs ?? coverage.events[0].atMs) && outcome.finalAtMs <= lastAt &&
          outcome.finalAtMs <= ready.readyAtMs);
        if (firstByte === null) need(outcome.requestCount === 0 && outcome.outcome === 'ZERO_REQUESTS' &&
          outcome.receiverGeneration === null && outcome.connectionId === null);
        else need(state.egressKind === 'EGRESS_GRANTED' && outcome.requestCount === 1 &&
          outcome.connectionId === firstByte.connectionId &&
          ((outcome.outcome === 'RECEIVER_ACCEPTED' && isGeneration(outcome.receiverGeneration)) ||
           (outcome.outcome === 'REQUEST_NOT_ACCEPTED' && outcome.receiverGeneration === null)));
        active();
        const evidence = Object.freeze({binding: Object.freeze(binding), routeClosed: true, connectionsClosed: true, coverageComplete: true,
          requestCount: outcome.requestCount, outcome: outcome.outcome,
          receiverGeneration: outcome.receiverGeneration, evidenceSha256: readyReference.sha256});
        verifiedClosures.set(evidence, Object.freeze({validUntilMs, signal: context.signal, now}));
        assertClosureEvidenceActive(evidence, context);
        return evidence;
      }, context.signal);
    } catch {
      // The existing Gate maps absent evidence to OPEN_UNKNOWN; this port never infers success.
      return null;
    }
  };
}
