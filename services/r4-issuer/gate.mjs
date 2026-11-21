import {checkedBody, decision, isDigest, isGeneration, issuerBoundary, requireDecision} from './records.mjs';
import {duringOperation} from './deadline.mjs';

const refused = () => ({status: 'REFUSED'});
const unknown = () => ({status: 'OPEN_UNKNOWN'});

export function createIssuerGate(policy, ports) {
  const boundary = issuerBoundary(policy, ports);

  function start(credential, body, signal) {
    return duringOperation(ports, boundary.deadlineMs, operation => startIn(operation, credential, body), signal);
  }

  async function startIn(operation, credential, body) {
    let authority, bytes;
    try {
      authority = await boundary.authority(operation, credential, 'writer');
      bytes = checkedBody(body, authority.bodySha256);
      if (!await boundary.mayDispatch(operation, authority) || await boundary.read(operation, authority.keys.stop)) return refused();
    } catch {
      return refused();
    }
    try {
      const {keys} = authority;
      const attempt = await boundary.create(operation, keys.reservation, decision(authority, 'ADMISSION_RESERVED'));
      if (!attempt) return refused();
      const reservation = {reservationGeneration: attempt.generation};
      if (!await boundary.mayDispatch(operation, authority) || await boundary.read(operation, keys.stop)) {
        return {status: 'CONSUMED_NO_HANDOFF', ...reservation};
      }
      const admission = await boundary.create(operation, keys.admission, decision(authority, 'STARTED', reservation));
      if (!admission) return refused();
      if (!await boundary.mayDispatch(operation, authority) || await boundary.read(operation, keys.stop)) {
        return {status: 'CONSUMED_NO_HANDOFF', ...reservation, admissionGeneration: admission.generation};
      }
      const handoff = decision(authority, 'STARTED_HANDOFF', {...reservation, admissionGeneration: admission.generation});
      return await operation.run(context => ports.invokeTransport(handoff, bytes, context));
    } catch {
      return unknown();
    }
  }

  async function stopState(operation, authority, request, admission, egress) {
    requireDecision(request, authority, 'REQUESTED');
    const admissionKind = admission?.value.kind;
    const egressKind = egress?.value.kind;
    if (admissionKind !== 'STARTED' && admissionKind !== 'STOP_BEFORE_SEND') throw Error('ADMISSION_STATE');
    let reservationGeneration = null;
    if (admissionKind === 'STARTED') {
      const reservation = await boundary.read(operation, authority.keys.reservation);
      requireDecision(reservation, authority, 'ADMISSION_RESERVED');
      reservationGeneration = reservation.generation;
      requireDecision(admission, authority, 'STARTED', {reservationGeneration});
    } else {
      requireDecision(admission, authority, 'STOP_BEFORE_SEND');
    }
    if (egressKind === 'EGRESS_BLOCKED') {
      requireDecision(egress, authority, 'EGRESS_BLOCKED');
    } else if (egressKind === 'EGRESS_GRANTED' && admissionKind === 'STARTED') {
      requireDecision(egress, authority, 'EGRESS_GRANTED', {reservationGeneration, admissionGeneration: admission.generation});
    } else {
      throw Error('EGRESS_STATE');
    }
    return {requestGeneration: request.generation, reservationGeneration, admissionGeneration: admission.generation,
      egressGeneration: egress.generation, admissionKind, egressKind};
  }

  function stop(credential, signal) {
    return duringOperation(ports, boundary.stopDeadlineMs, operation => stopIn(operation, credential), signal);
  }

  async function stopIn(operation, credential) {
    let authority;
    try {
      authority = await boundary.authority(operation, credential, 'stopper');
    } catch {
      return refused();
    }
    const {keys} = authority;
    try {
      const request = await boundary.createOrRead(operation, keys.stop, decision(authority, 'REQUESTED'));
      requireDecision(request, authority, 'REQUESTED');
      const egress = await boundary.createOrRead(operation, keys.egress, decision(authority, 'EGRESS_BLOCKED'));
      if (!egress || !['EGRESS_BLOCKED','EGRESS_GRANTED'].includes(egress.value.kind)) return unknown();
      if (egress.value.kind === 'EGRESS_BLOCKED') requireDecision(egress, authority, 'EGRESS_BLOCKED');
      const admission = egress.value.kind === 'EGRESS_GRANTED' ? await boundary.read(operation, keys.admission) :
        await boundary.createOrRead(operation, keys.admission, decision(authority, 'STOP_BEFORE_SEND'));
      const state = await stopState(operation, authority, request, admission, egress);
      return {status: 'STOP_REQUESTED', ...state};
    } catch {
      return unknown();
    }
  }

  function confirmStop(credential, signal) {
    return duringOperation(ports, boundary.stopDeadlineMs, operation => confirmStopIn(operation, credential), signal);
  }

  async function confirmStopIn(operation, credential) {
    let authority;
    try {
      authority = await boundary.authority(operation, credential, 'closer');
    } catch {
      return refused();
    }
    const {keys} = authority;
    try {
      const request = await boundary.read(operation, keys.stop);
      const egress = await boundary.read(operation, keys.egress);
      const admission = await boundary.read(operation, keys.admission);
      const state = await stopState(operation, authority, request, admission, egress);
      const binding = decision(authority, 'STOP_EVIDENCE_BINDING', state);
      const evidence = await operation.run(context => ports.readIndependentClosure(binding, context));
      if (!evidence || evidence.routeClosed !== true || evidence.connectionsClosed !== true ||
          evidence.coverageComplete !== true || !isDigest(evidence.evidenceSha256)) return unknown();
      requireDecision({value: evidence.binding}, authority, 'STOP_EVIDENCE_BINDING', state);
      const {requestCount, outcome, receiverGeneration, evidenceSha256} = evidence;
      const definitive = (outcome === 'ZERO_REQUESTS' && requestCount === 0 && receiverGeneration === null) ||
        (state.egressKind === 'EGRESS_GRANTED' && requestCount === 1 &&
          ((outcome === 'REQUEST_NOT_ACCEPTED' && receiverGeneration === null) ||
            (outcome === 'RECEIVER_ACCEPTED' && isGeneration(receiverGeneration))));
      if (!definitive) return unknown();
      const fields = {...state, requestCount, outcome, receiverGeneration, evidenceSha256};
      const record = await boundary.createOrRead(operation, keys.terminal, decision(authority, 'STOPPED_CONFIRMED', fields));
      requireDecision(record, authority, 'STOPPED_CONFIRMED', fields);
      return {status: 'STOPPED_CONFIRMED', terminalGeneration: record.generation, ...fields};
    } catch {
      return unknown();
    }
  }

  return {start, stop, confirmStop};
}
