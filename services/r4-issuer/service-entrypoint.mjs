import {duringOperation} from './deadline.mjs';
import {createIssuerGate} from './gate.mjs';
import {issuerBoundary} from './records.mjs';
import {createIssuerServiceAuthenticator, issuerActions} from './service-auth.mjs';
import {createIssuerTransport} from './transport.mjs';

const refused = () => ({status:'REFUSED'});

function bindActionPorts(ports, credential, email, policyDeadlineMs, cutoff) {
  function scoped(context) {
    const assertActive = () => {
      cutoff.assertActive();
      context.assertActive();
    };
    assertActive();
    return Object.freeze({deadlineMs:cutoff.deadlineMs, policyDeadlineMs, signal:context.signal, assertActive});
  }

  return {...ports,
    authenticate(ticket, expected, context) {
      scoped(context);
      return ticket === credential && expected === email;
    },
    readVerifiedManifest: context => ports.readVerifiedManifest(scoped(context)),
    dispatchAllowed: (authority, context) => ports.dispatchAllowed(authority, scoped(context)),
    store: {
      read: (key, context) => ports.store.read(key, scoped(context)),
      create: (request, context) => ports.store.create(request, scoped(context)),
    },
    invokeTransport: (handoff, body, context) => ports.invokeTransport(handoff, body, scoped(context)),
    sendOnce: (request, context) => ports.sendOnce(request, scoped(context)),
    readIndependentClosure: (binding, context) => ports.readIndependentClosure(binding, scoped(context)),
  };
}

// The future listener maps its exact route to action and supplies its own abort context.
// The frame carries a compact signed token, never an asserted principal or an operation context.
export function createIssuerServiceEntrypoint({service, configBytes, configSha256, policy, ports}) {
  if (service !== 'gate' && service !== 'transport') throw Error('ISSUER_ENTRYPOINT_SERVICE');
  const bound = structuredClone(policy);
  const {now} = ports;
  const {verifyAction, emails} = createIssuerServiceAuthenticator({configBytes, configSha256, now});
  if (!Object.entries(emails).every(([role, email]) => bound?.actors?.[role] === email)) {
    throw Error('ISSUER_ENTRYPOINT_ACTORS');
  }
  issuerBoundary(bound, ports); // Validate the same policy pins at boot, before accepting actions.

  return async function handle(frame, trustedContext) {
    const action = frame?.action;
    if (typeof action !== 'string' || !Object.hasOwn(issuerActions, action) ||
        issuerActions[action].service !== service) return refused();
    const deadlineMs = action === 'stop' || action === 'confirmStop' ? bound.stopDeadlineMs : bound.deadlineMs;
    if (!(trustedContext?.signal instanceof AbortSignal) || trustedContext.signal.aborted) return refused();
    if (action === 'dispatch') {
      if (trustedContext.policyDeadlineMs !== deadlineMs || !Number.isSafeInteger(trustedContext.deadlineMs) ||
          trustedContext.deadlineMs > deadlineMs || typeof trustedContext.assertActive !== 'function') return refused();
    } else if (trustedContext.deadlineMs !== deadlineMs) return refused();
    const proof = verifyAction(action, frame.token);
    if (!proof) return refused();
    const cutoff = Math.min(trustedContext.deadlineMs, proof.deadlineMs);
    if (now() >= cutoff) return refused();

    const ticket = {};
    try {
      if (action === 'dispatch') trustedContext.assertActive();
      return await duringOperation(ports, cutoff, operation => operation.run(context => {
        const authenticatedPorts = bindActionPorts(ports, ticket, proof.email, deadlineMs, context);
        if (action === 'dispatch') {
          const transport = createIssuerTransport(bound, authenticatedPorts);
          return transport(ticket, frame.handoff, frame.body, {deadlineMs, signal:context.signal});
        }
        const gate = createIssuerGate(bound, authenticatedPorts);
        const {signal} = context;
        if (action === 'start') return gate.start(ticket, frame.body, signal);
        return gate[action](ticket, signal);
      }), trustedContext.signal);
    } catch {
      return {status:'OPEN_UNKNOWN'};
    }
  };
}
