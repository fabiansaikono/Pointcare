import assert from 'node:assert/strict';
import {request as httpRequest} from 'node:http';
import {request as httpsRequest} from 'node:https';
import {runWriterJob, runStopJob} from '../jobs.mjs';
import {createIssuerServiceAuthenticator} from '../service-auth.mjs';
import {digest, isDigest, isGeneration} from '../records.mjs';

const paths = Object.freeze({writer:'/v1/start', stop:'/v1/stop'});
const known = Object.freeze({writer:new Set(['RECEIVER_ACCEPTED','CONSUMED_NO_HANDOFF','CONSUMED_NO_SEND']),
  stop:new Set(['STOP_REQUESTED'])});
const configurationRefused = Object.freeze({status:'CONFIGURATION_REFUSED'});
const unknown = Object.freeze({status:'OPEN_UNKNOWN'});
const epoch = value => Number.isSafeInteger(value) && value >= 0;
const requestLimit = 65536;
const responseLimit = 16384;
const exact = (value, keys) => value && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));

function completeAck(job, ack, manifestSha256, dispatchId) {
  if (ack.manifestSha256 !== manifestSha256) return false;
  if (job === 'writer' && ack.status === 'RECEIVER_ACCEPTED') {
    return exact(ack, ['status','manifestSha256','dispatchId','reservationGeneration','admissionGeneration',
      'egressGeneration','receiverGeneration']) && ack.dispatchId === dispatchId &&
      ['reservationGeneration','admissionGeneration','egressGeneration','receiverGeneration'].every(key => isGeneration(ack[key]));
  }
  if (job === 'writer' && ack.status === 'CONSUMED_NO_HANDOFF') {
    const keys = ['status','manifestSha256','reservationGeneration'];
    if (Object.hasOwn(ack,'admissionGeneration')) keys.push('admissionGeneration');
    return exact(ack, keys) && isGeneration(ack.reservationGeneration) &&
      (!Object.hasOwn(ack,'admissionGeneration') || isGeneration(ack.admissionGeneration));
  }
  if (job === 'writer' && ack.status === 'CONSUMED_NO_SEND') {
    return exact(ack, ['status','manifestSha256','reservationGeneration','admissionGeneration','egressGeneration']) &&
      ['reservationGeneration','admissionGeneration','egressGeneration'].every(key => isGeneration(ack[key]));
  }
  if (job === 'stop' && ack.status === 'STOP_REQUESTED') {
    return exact(ack, ['status','manifestSha256','requestGeneration','reservationGeneration','admissionGeneration',
      'egressGeneration','admissionKind','egressKind']) &&
      ['requestGeneration','admissionGeneration','egressGeneration'].every(key => isGeneration(ack[key])) &&
      (ack.admissionKind === 'STARTED' ? isGeneration(ack.reservationGeneration) &&
        ['EGRESS_BLOCKED','EGRESS_GRANTED'].includes(ack.egressKind) :
        ack.admissionKind === 'STOP_BEFORE_SEND' && ack.reservationGeneration === null && ack.egressKind === 'EGRESS_BLOCKED');
  }
  return false;
}

function selectOrigin(value, admittedLoopback) {
  const url = new URL(value);
  assert(value === url.origin && !url.username && !url.password);
  // Only the protected test composition may admit a literal loopback origin.
  // There is no command-line or environment switch to select this branch.
  if (admittedLoopback === true) {
    assert(url.protocol === 'http:' && url.hostname === '127.0.0.1' && url.port);
  } else {
    assert(admittedLoopback === undefined && url.protocol === 'https:' && !url.port);
  }
  return url.origin;
}

async function postOnce(url, body, token, context, cutoff, writer) {
  context.assertActive();
  const remaining = cutoff - Date.now();
  assert(epoch(remaining) && remaining > 0 && remaining <= 2147483647);
  const controller = new AbortController();
  const cancel = () => controller.abort();
  context.signal.addEventListener('abort', cancel, {once:true});
  const timer = setTimeout(cancel, remaining);
  if (context.signal.aborted) cancel();
  try {
    return await new Promise((resolve, reject) => {
      context.assertActive();
      const send = url.protocol === 'https:' ? httpsRequest : httpRequest;
      const req = send(url, {method:'POST', agent:false, signal:controller.signal, maxHeaderSize:16384,
        headers:{authorization:`Bearer ${token}`, accept:'application/json', connection:'close',
          'content-length':body.length, ...(writer ? {'content-type':'application/octet-stream'} : {})}}, res => {
        const type = res.headersDistinct['content-type'];
        if (!type || type.length !== 1 || !/^application\/json(?:\s*;[^\r\n]*)?$/iu.test(type[0]) ||
            res.headers['content-encoding'] || ![200,400,403,404,405,409].includes(res.statusCode)) {
          res.destroy(); reject(Error('ISSUER_JOB_RESPONSE')); return;
        }
        const chunks = [];
        let size = 0;
        res.on('data', chunk => {
          size += chunk.length;
          if (size > responseLimit) { res.destroy(); reject(Error('ISSUER_JOB_RESPONSE_SIZE')); }
          else chunks.push(chunk);
        });
        res.once('error', reject);
        res.once('aborted', () => reject(Error('ISSUER_JOB_RESPONSE_ABORTED')));
        res.once('end', () => {
          try {
            context.assertActive();
            assert(!controller.signal.aborted && Date.now() < cutoff && res.complete);
            const result = JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(Buffer.concat(chunks)));
            assert(result && typeof result.status === 'string');
            resolve({code:res.statusCode, value:result});
          } catch { reject(Error('ISSUER_JOB_RESPONSE')); }
          finally { for (const chunk of chunks) chunk.fill(0); }
        });
      });
      req.once('error', reject);
      context.assertActive();
      if (Date.now() >= cutoff) { req.destroy(); reject(Error('ISSUER_JOB_CUTOFF')); return; }
      req.end(body);
    });
  } finally {
    clearTimeout(timer);
    context.signal.removeEventListener('abort', cancel);
    cancel();
  }
}

/** Protected composition supplies a token port, never a caller-supplied identity.
 * This client does not mint credentials, use metadata, follow redirects or retry. */
export async function runIssuerJob(job, input) {
  let body;
  try {
    assert(Object.hasOwn(paths, job) && input && typeof input.readIdentityToken === 'function');
    const origin = selectOrigin(input.gateBaseUrl, input.admittedLoopback);
    const audience = input.gateAudience;
    const expectedManifestSha256 = input.expectedManifestSha256;
    const expectedDispatchId = input.expectedDispatchId;
    assert(isDigest(expectedManifestSha256) && typeof expectedDispatchId === 'string' &&
      /^[A-Za-z0-9_.-]{1,160}$/.test(expectedDispatchId));
    const configBytes = Buffer.from(input.identityConfigBytes);
    const configSha256 = input.identityConfigSha256;
    const deadlineMs = job === 'writer' ? input.deadlineMs : input.stopDeadlineMs;
    assert(epoch(input.deadlineMs) && epoch(input.stopDeadlineMs) && input.stopDeadlineMs >= input.deadlineMs);
    const {verifyAction} = createIssuerServiceAuthenticator({configBytes, configSha256, now:Date.now});
    assert(JSON.parse(configBytes.toString('utf8')).audiences.gate === audience);
    const action = job === 'writer' ? 'start' : 'stop';
    const readIdentityToken = input.readIdentityToken;
    body = job === 'writer' ? Buffer.from(input.sealedBody) : Buffer.alloc(0);
    assert(job !== 'writer' || body.length > 0 && body.length <= requestLimit &&
      isDigest(input.sealedBodySha256) && digest(body) === input.sealedBodySha256);
    const url = new URL(paths[job], origin);
    let called = false;
    const invoke = async context => {
      assert(!called); called = true;
      context.assertActive();
      // The original job context bounds token acquisition as well as the POST.
      // Late port completion cannot dispatch after this context is cancelled.
      const token = await readIdentityToken(Object.freeze({audience, deadlineMs, signal:context.signal}));
      context.assertActive();
      const proof = verifyAction(action, token);
      if (!proof) return {status:'REFUSED'};
      const result = await postOnce(url, body, token, context, Math.min(deadlineMs, proof.deadlineMs), job === 'writer');
      const ack = result.value;
      if (ack.manifestSha256 !== expectedManifestSha256) return unknown;
      if ([400,403,404,405].includes(result.code) && exact(ack,['status','manifestSha256']) &&
          ack.status === 'REFUSED') return {status:'REFUSED'};
      if (result.code === 409 && ack.status === 'OPEN_UNKNOWN') return unknown;
      if (result.code !== 200 || !known[job].has(ack.status) ||
          !completeAck(job, ack, expectedManifestSha256, expectedDispatchId)) return unknown;
      return {status:ack.status};
    };
    try {
      const clock = {now:Date.now};
      return job === 'writer'
        ? await runWriterJob({sealedBody:body, clock, deadlineMs, start:(_body, context) => invoke(context)})
        : await runStopJob({clock, deadlineMs, stop:invoke});
    } catch { return unknown; }
  } catch { return configurationRefused; }
  finally { body?.fill(0); }
}

export function exitCode(result) {
  if (['RECEIVER_ACCEPTED','STOP_REQUESTED'].includes(result?.status)) return 0;
  if (['REFUSED','CONSUMED_NO_HANDOFF','CONSUMED_NO_SEND'].includes(result?.status)) return 20;
  if (result?.status === 'ARGUMENTS_REFUSED') return 64;
  if (result?.status === 'CONFIGURATION_REFUSED') return 78;
  return 75; // Any uncertain/malformed/lost outcome requires reconciliation.
}

/** One process, one static role, one fixed local composition module. */
export async function main(job) {
  let result;
  if (process.argv.length !== 2) result = {status:'ARGUMENTS_REFUSED'};
  else {
    try {
      // This custodian-owned module is deliberately absent from the candidate.
      // It must export a synchronous openJobBinding(role), with no top-level I/O.
      const composition = await import('./job-bindings.mjs');
      const input = composition.openJobBinding(job);
      assert(!(input instanceof Promise));
      result = await runIssuerJob(job, input);
    } catch { result = configurationRefused; }
  }
  // Never print token, body, origin, actor, generation or raw response/error.
  process.stdout.write(JSON.stringify({status:result.status}) + '\n');
  process.exitCode = exitCode(result);
}
