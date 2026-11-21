import {duringOperation} from './deadline.mjs';

// Each job has a separate authenticated client and one absolute cutoff.
export function runWriterJob({start, sealedBody, clock, deadlineMs, signal}) {
  return runJob(clock, deadlineMs, context => start(sealedBody, context), signal);
}

export function runStopJob({stop, clock, deadlineMs, signal}) {
  return runJob(clock, deadlineMs, stop, signal);
}

function runJob(clock, deadlineMs, invoke, signal) {
  return duringOperation(clock, deadlineMs, async operation => {
    try {
      return await operation.run(invoke);
    } catch {
      return {status: 'OPEN_UNKNOWN'};
    }
  }, signal);
}
