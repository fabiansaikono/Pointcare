import {checkedBody, decision, isGeneration, issuerBoundary, requireDecision} from './records.mjs';
import {duringOperation} from './deadline.mjs';

export function createIssuerTransport(policy, ports) {
  const boundary = issuerBoundary(policy, ports);

  return async function accept(credential, handoff, body, parent) {
    if (parent?.deadlineMs !== boundary.deadlineMs || !(parent.signal instanceof AbortSignal)) {
      return {status: 'REFUSED'};
    }
    return duringOperation(ports, boundary.deadlineMs,
      operation => acceptIn(operation, credential, handoff, body), parent.signal);
  };

  async function acceptIn(operation, credential, handoff, body) {
    let authority, bytes, binding;
    try {
      authority = await boundary.authority(operation, credential, 'gate');
      bytes = checkedBody(body, authority.bodySha256);
      const {keys} = authority;
      if (!await boundary.mayDispatch(operation, authority) || await boundary.read(operation, keys.stop)) {
        return {status: 'REFUSED'};
      }
      const reservation = await boundary.read(operation, keys.reservation);
      requireDecision(reservation, authority, 'ADMISSION_RESERVED');
      const admission = await boundary.read(operation, keys.admission);
      requireDecision(admission, authority, 'STARTED', {reservationGeneration: reservation.generation});
      binding = {reservationGeneration: reservation.generation, admissionGeneration: admission.generation};
      requireDecision({value: handoff}, authority, 'STARTED_HANDOFF', binding);
    } catch {
      return {status: 'REFUSED'};
    }

    try {
      const {keys} = authority;
      const egress = await boundary.create(operation, keys.egress, decision(authority, 'EGRESS_GRANTED', binding));
      if (!egress) return {status: 'REFUSED'};
      if (!await boundary.mayDispatch(operation, authority) || await boundary.read(operation, keys.stop)) {
        return {status: 'CONSUMED_NO_SEND', ...binding, egressGeneration: egress.generation};
      }
      // The adapter checks context.assertActive immediately before issuing the one physical request.
      const request = {receiver: authority.receiver, ifGenerationMatch: 0, body: bytes,
        bodySha256: authority.bodySha256, dispatchId: authority.dispatchId, ...binding, egressGeneration: egress.generation};
      const receipt = await operation.run(context => ports.sendOnce(request, context));
      if (receipt?.status !== 'CREATED' || receipt.receiver !== authority.receiver ||
          receipt.bodySha256 !== authority.bodySha256 || !isGeneration(receipt.generation)) {
        return {status: 'OPEN_UNKNOWN'};
      }
      return {status: 'RECEIVER_ACCEPTED', dispatchId: authority.dispatchId, ...binding,
        egressGeneration: egress.generation, receiverGeneration: receipt.generation};
    } catch {
      return {status: 'OPEN_UNKNOWN'};
    }
  }
}
