import {isDeepStrictEqual} from 'node:util';
import {duringOperation} from '../deadline.mjs';
import {createIssuerStorageStore} from '../storage-store.mjs';
import {decision, decisionKeys, digest, isDigest, isGeneration} from '../records.mjs';
import {createIssuerServiceAuthenticator} from '../service-auth.mjs';
import {createManagedProviderPorts, managedBindingPath, readManagedServiceBinding} from './managed-provider-ports.mjs';
import {createControlEvidence} from './control-evidence.mjs';

const utf8 = new TextDecoder('utf-8', {fatal: true, ignoreBOM: true});
const schema = 'POINCARE_R4_ORIGINAL_HANDOFF_V1';
const postAckSchema = 'POINCARE_R4_POST_ACK_EVIDENCE_V1';
const storageScopes = {
  gate: {readSlots: ['stop', 'reservation', 'admission', 'egress', 'terminal'],
    createKinds: {stop: ['REQUESTED'], reservation: ['ADMISSION_RESERVED'], admission: ['STARTED', 'STOP_BEFORE_SEND'],
      egress: ['EGRESS_BLOCKED'], terminal: ['STOPPED_CONFIRMED']}},
  transport: {readSlots: ['stop', 'reservation', 'admission'], createKinds: {egress: ['EGRESS_GRANTED']}},
};
const exact = (value, keys) => value && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
const encode = value => Buffer.from(JSON.stringify(value));

function requireBinding(condition, reason) {
  if (!condition) throw Error(`ISSUER_PROTECTED_${reason}`);
}

function active(context, now) {
  context.assertActive();
  requireBinding(Number.isSafeInteger(context.deadlineMs) && now() < context.deadlineMs, 'CUTOFF');
}

function decodeBase64(value) {
  requireBinding(typeof value === 'string' && value.length <= 87_384, 'BASE64');
  const bytes = Buffer.from(value, 'base64');
  requireBinding(bytes.length > 0 && bytes.toString('base64') === value, 'BASE64');
  return bytes;
}

/** Provider code is fixed in the image. Control evidence requires a separately reviewed original source. */
export async function bindIssuerServicePorts({service, sourceRef, expectedSha256, policy, bodySha256,
  configBytes, configSha256, startBytes, envelopeBytes, now = Date.now},
  {readBinding = readManagedServiceBinding, providerRequests} = {}) {
  requireBinding(Object.hasOwn(storageScopes, service) && sourceRef === managedBindingPath && isDigest(expectedSha256),
    'SOURCE_PIN');
  policy = structuredClone(policy);
  const {verifyAction, emails} = createIssuerServiceAuthenticator({configBytes, configSha256, now});
  const {principals, audiences} = JSON.parse(utf8.decode(configBytes));
  const input = await readBinding();
  requireBinding(input instanceof Uint8Array && input.length > 0 && input.length <= 98_304, 'SOURCE_PIN');
  const raw = Buffer.from(input);
  requireBinding(digest(raw) === expectedSha256, 'SOURCE_PIN');
  const binding = JSON.parse(utf8.decode(raw));
  requireBinding(exact(binding, ['schema', 'service', 'sourceRef', 'manifestBase64', 'transportOrigin', 'envelopeBytes', 'evidenceBucket',
    'routeEvidenceSha256', 'handoffObject', 'controlEvidence']) && binding.schema === 'POINCARE_R4_SERVICE_PORTS_V2' &&
    binding.service === service &&
    binding.sourceRef === sourceRef && isDigest(binding.routeEvidenceSha256), 'SOURCE_SCOPE');
  const origin = typeof binding.transportOrigin === 'string' && URL.parse(binding.transportOrigin);
  requireBinding(origin?.protocol === 'https:' && !origin.port && binding.transportOrigin === origin.origin, 'ORIGIN');
  const url = new URL('/v1/dispatch', origin).href;
  const manifestBytes = decodeBase64(binding.manifestBase64);
  requireBinding(digest(manifestBytes) === policy.manifestSha256, 'MANIFEST_PIN');
  const manifest = JSON.parse(utf8.decode(manifestBytes));
  const keys = Object.freeze(decisionKeys(manifest.packageSha256, manifest.approvalSha256));
  const {manifestSha256, receiver, gateRevision, transportRevision, deadlineMs} = policy;
  requireBinding(manifest.purpose === 'R4_ISSUER_SYNTHETIC_ONLY' && isDeepStrictEqual(manifest.keys, keys) &&
    isDeepStrictEqual(manifest.actors, emails) && isDeepStrictEqual(policy.actors, emails) && manifest.receiver === receiver &&
    manifest.gateRevision === gateRevision && manifest.transportRevision === transportRevision &&
    manifest.bodySha256 === bodySha256 && isDigest(bodySha256) && manifest.expiresAtMs === deadlineMs &&
    Number.isSafeInteger(manifest.notBeforeMs) && manifest.notBeforeMs >= 0 &&
    manifest.notBeforeMs < deadlineMs, 'MANIFEST_SCOPE');
  const {evidenceBucket: bucket, envelopeBytes: envelopeLimit, handoffObject} = binding;
  requireBinding(typeof bucket === 'string' && /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/u.test(bucket), 'BUCKET');
  requireBinding(handoffObject === `decisions/sha256/${keys.pairKey}/post-ack-handoff.json`,
    'HANDOFF_OBJECT');
  requireBinding(Number.isSafeInteger(envelopeLimit) && envelopeLimit > 0 && envelopeLimit <= 65_536 &&
    Number.isSafeInteger(startBytes) && startBytes > 0 && startBytes <= 65_536 &&
    (service === 'gate' || envelopeBytes === envelopeLimit), 'LIMIT');

  const scope = Object.freeze({sourceRef, routeEvidenceSha256: binding.routeEvidenceSha256, gateRevision,
    transportRevision, actorSubject: principals.gate.subject, audience: audiences.transport, url, manifestSha256,
    bucket, handoffObject});
  const seal = Object.freeze({...manifest, bucket, keys, pairKey: keys.pairKey, manifestSha256,
    routeEvidenceSha256: binding.routeEvidenceSha256, stopDeadlineMs: policy.stopDeadlineMs});
  const maxGeneration = '9'.repeat(20);
  const longestHandoff = decision(seal, 'STARTED_HANDOFF',
    {reservationGeneration: maxGeneration, admissionGeneration: maxGeneration});
  const envelopeOverhead = encode({schema, handoff: longestHandoff, bodyBase64: '',
    deadlineMs, policyDeadlineMs: deadlineMs}).length;
  requireBinding(envelopeOverhead + 4 * Math.ceil(startBytes / 3) <= envelopeLimit, 'LIMIT');
  const {sendStorage, readGateIdentityToken, postTransport, sendReceiver, createHandoffEvidence,
    readHandoffEvidence, readControlObject} = createManagedProviderPorts({
    service, bucket, receiver, handoffObject, transportUrl: url, audience: audiences.transport,
  }, providerRequests);
  const {dispatchAllowed, dispatchPermission, readIndependentClosure, withVerifiedClosure,
    bindDecisionDigests, rememberReservation} =
    createControlEvidence({service, authority: seal, config: binding.controlEvidence, readControlObject, now});
  function store(verifiedHandoff) {
    return createIssuerStorageStore({seal, evidenceBucket: bucket, ...storageScopes[service],
      expectedDigest: bindDecisionDigests(verifiedHandoff), send: sendStorage});
  }
  const ports = {now, async readVerifiedManifest() { return Buffer.from(manifestBytes); }, dispatchAllowed, store: store()};

  function checkHandoff(handoff) {
    const {reservationGeneration, admissionGeneration} = handoff ?? {};
    requireBinding(isGeneration(reservationGeneration) && isGeneration(admissionGeneration) &&
      isDeepStrictEqual(handoff, decision(seal, 'STARTED_HANDOFF', {reservationGeneration, admissionGeneration})), 'HANDOFF');
  }

  function postAckRecord(handoff, effectiveDeadlineMs, raw) {
    return encode({schema: postAckSchema, actorRole: 'gate', actorSubject: scope.actorSubject, gateRevision,
      transportRevision, transportUrl: url, transportAudience: scope.audience, receiver,
      routeEvidenceSha256: scope.routeEvidenceSha256, manifestSha256, bodySha256, pairKey: handoff.pairKey,
      dispatchId: handoff.dispatchId, reservationGeneration: handoff.reservationGeneration,
      admissionGeneration: handoff.admissionGeneration, effectiveDeadlineMs, policyDeadlineMs: deadlineMs,
      envelopeSha256: digest(raw)});
  }

  function guard(context, permission) {
    return Object.freeze({...context, assertActive() {
      context.assertActive();
      permission.assertActive();
    }});
  }

  async function withDispatchPermission(context, run) {
    const permission = await dispatchPermission(seal, context);
    requireBinding(permission, 'DISPATCH_PERMISSION');
    return duringOperation({now}, permission.deadlineMs, operation => operation.run(bounded =>
      run(guard(bounded, permission))), context.signal);
  }

  if (service === 'gate') {
    let invoked = false;
    const originalStore = ports.store;
    ports.store = {...originalStore, async create(request, context) {
      if (request.value.kind === 'STOPPED_CONFIRMED') return withVerifiedClosure(request, context, originalStore.create);
      const result = await originalStore.create(request, context);
      rememberReservation(result, context);
      return result;
    }};
    ports.readIndependentClosure = readIndependentClosure;
    ports.invokeTransport = async (handoff, body, context) => {
      active(context, now);
      requireBinding(!invoked && context.policyDeadlineMs === deadlineMs && context.deadlineMs <= deadlineMs, 'HANDOFF_SCOPE');
      invoked = true; // Only the retained Gate's direct create ACK reaches this port.
      checkHandoff(handoff);
      requireBinding(body instanceof Uint8Array && body.byteLength === startBytes && digest(body) === bodySha256, 'BODY');
      const credential = await readGateIdentityToken(scope, context);
      active(context, now);
      const claim = verifyAction('dispatch', credential);
      requireBinding(claim?.subject === scope.actorSubject, 'GATE_IDENTITY');
      const result = await duringOperation({now}, Math.min(context.deadlineMs, claim.deadlineMs), operation =>
        operation.run(identityContext => withDispatchPermission(identityContext, async postContext => {
          const raw = encode({schema, handoff, bodyBase64: Buffer.from(body).toString('base64'),
            deadlineMs: postContext.deadlineMs, policyDeadlineMs: deadlineMs});
          requireBinding(raw.length <= envelopeLimit, 'LIMIT');
          const original = postAckRecord(handoff, postContext.deadlineMs, raw);
          const generation = await createHandoffEvidence(original, postContext);
          requireBinding(isGeneration(generation), 'ORIGINAL_PUBLICATION');
          const refreshed = await dispatchPermission(seal, postContext);
          // An earlier cutoff would no longer match the immutable evidence envelope.
          requireBinding(refreshed?.deadlineMs === postContext.deadlineMs, 'DISPATCH_PERMISSION');
          return postTransport(Object.freeze({url, credential, bytes: raw}), guard(postContext, refreshed));
        })), context.signal);
      active(context, now);
      requireBinding((result?.code === 200 && ['RECEIVER_ACCEPTED', 'CONSUMED_NO_SEND'].includes(result.value?.status)) ||
        (result?.code === 403 && result.value?.status === 'REFUSED') ||
        (result?.code === 409 && result.value?.status === 'OPEN_UNKNOWN'), 'TRANSPORT_RESPONSE');
      return result.value;
    };
    return {bindingSha256: expectedSha256, ports: Object.freeze(ports)};
  }

  ports.sendOnce = async (request, context) => {
    active(context, now);
    requireBinding(request.receiver === receiver && request.ifGenerationMatch === 0 &&
      request.bodySha256 === bodySha256 && digest(request.body) === bodySha256, 'RECEIVER_SCOPE');
    return withDispatchPermission(context, bounded => sendReceiver(request, bounded));
  };
  ports.forVerifiedHandoff = proof => ({...ports, store: store(proof)});
  return {bindingSha256: expectedSha256, ports: Object.freeze(ports), async openTransportEnvelope(input, context) {
    active(context, now);
    requireBinding(context.actor?.subject === scope.actorSubject && context.actor.role === 'gate' &&
      context.policyDeadlineMs === deadlineMs && input instanceof Uint8Array && input.byteLength > 0 &&
      input.byteLength <= envelopeLimit, 'ENVELOPE_SCOPE');
    const raw = Buffer.from(input);
    const value = JSON.parse(utf8.decode(raw));
    requireBinding(exact(value, ['schema', 'handoff', 'bodyBase64', 'deadlineMs', 'policyDeadlineMs']) &&
      value.schema === schema && value.policyDeadlineMs === deadlineMs && Number.isSafeInteger(value.deadlineMs) &&
      value.deadlineMs > now() && value.deadlineMs <= deadlineMs, 'ENVELOPE_CUTOFF');
    checkHandoff(value.handoff);
    const body = decodeBase64(value.bodyBase64);
    requireBinding(body.byteLength === startBytes && digest(body) === bodySha256, 'BODY');
    // The submitted cutoff can shorten this read; only the verified record grants its provenance.
    const original = await duringOperation({now}, Math.min(value.deadlineMs, context.deadlineMs),
      operation => operation.run(readHandoffEvidence), context.signal);
    requireBinding(original.bytes.equals(postAckRecord(value.handoff, value.deadlineMs, raw)), 'ORIGINAL_HANDOFF');
    active(context, now);
    requireBinding(value.deadlineMs > now(), 'ENVELOPE_CUTOFF');
    const handoffEvidence = Object.freeze({bucket, object: handoffObject, generation: original.generation,
      sha256: original.sha256});
    return {actorSubject: scope.actorSubject, handoff: value.handoff, body, handoffEvidence,
      deadlineMs: value.deadlineMs, policyDeadlineMs: deadlineMs};
  }};
}
