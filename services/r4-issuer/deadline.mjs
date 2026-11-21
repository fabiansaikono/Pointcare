export async function duringOperation(ports, deadlineMs, work, parentSignal) {
  const controller = new AbortController();
  const reason = Error('OPERATION_DEADLINE_OR_CANCELLED');
  const cancel = () => controller.abort(reason);
  const waitMs = deadlineMs - ports.now();
  const schedule = ports.scheduleTimeout ?? setTimeout;
  const unschedule = ports.cancelTimeout ?? clearTimeout;
  const timer = Number.isSafeInteger(waitMs) && waitMs > 0 && waitMs <= 2_147_483_647 ?
    schedule(cancel, waitMs) : null;
  if (timer === null || parentSignal?.aborted) cancel();
  parentSignal?.addEventListener('abort', cancel, {once:true});

  let rejectCancellation;
  const cancelled = new Promise((_, reject) => { rejectCancellation = reject; });
  // A scope can expire before its first port or just after its last one.
  cancelled.catch(() => {});
  const signal = controller.signal;
  const reject = () => rejectCancellation(reason);
  signal.addEventListener('abort', reject, {once:true});
  if (signal.aborted) reject();

  function assertActive() {
    const now = ports.now();
    if (!Number.isSafeInteger(now) || now >= deadlineMs) cancel();
    signal.throwIfAborted();
  }

  const context = Object.freeze({deadlineMs, signal, assertActive});
  const operation = {context, async run(invoke) {
    assertActive();
    const pending = Promise.resolve().then(() => {
      assertActive();
      return invoke(context);
    });
    const result = await Promise.race([pending, cancelled]);
    assertActive();
    return result;
  }};

  try {
    return await work(operation);
  } finally {
    if (timer !== null) unschedule(timer);
    parentSignal?.removeEventListener('abort', cancel);
    signal.removeEventListener('abort', reject);
    cancel();
  }
}
