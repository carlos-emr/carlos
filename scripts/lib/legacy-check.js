/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');

// Older checks already own fixture cleanup in finally, but hand-written signal
// handlers could delete their rows while an accepted browser write was running.
// Await that write, checkpoint in the caller, and unwind through its finally.
function armInterruption(context,{realSignalCheckpoints=false}={}) {
    const requested=process.env.CARLOS_INTERRUPT_AFTER_CHECKPOINT;
    if(requested!==undefined) {
      h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true'&&/^[1-9]\d{0,3}$/.test(requested),
        'Signal audit requires a disposable install and a positive checkpoint number');
      const signal=process.env.CARLOS_INTERRUPT_SIGNAL||'SIGINT';
      h.assert(['SIGINT','SIGTERM'].includes(signal),'Signal audit requires SIGINT or SIGTERM');
      let reached=0;
      const original=context.cancellation.throwIfCancelled;
      const checkpoint=realSignalCheckpoints?async()=>{
        if(++reached===Number(requested)) {
          let deadline;
          try {
            // A pending Promise and Node's signal watcher do not keep the event
            // loop alive. Keep a bounded handle until the OS handler confirms delivery.
            const missing=new Promise((_,reject)=>{
              deadline=setTimeout(()=>reject(new Error('OS interruption was not observed')),5000);
            });
            process.kill(process.pid,signal);
            await Promise.race([context.cancellation.waitForCancellation(),missing]);
          } finally {clearTimeout(deadline);}
        }
        original();
      }:()=>{
        // Dispatch the installed handler synchronously: the last checkpoint must
        // observe cancellation before runCheck can dispose its listeners.
        if(++reached===Number(requested))process.emit(signal);
        original();
      };
      context.cancellation.throwIfCancelled=checkpoint;context.throwIfCancelled=checkpoint;
    }
}
// Some retained clients already install their own cooperative cancellation or
// cleanup handlers. Observe their signal without adding a competing deadline or
// running their cleanup twice; record completion only after their finally ends.
function observeLegacySignals({signalProcess=process}={}) {
  let interruption;
  const handlers=new Map();
  for(const [signal,code] of [['SIGINT',130],['SIGTERM',143]]) {
    const handler=()=>{if(!interruption){interruption=new Error('interrupted');interruption.exitCode=code;}};
    handlers.set(signal,handler);signalProcess.on(signal,handler);
  }
  return {
    throwIfCancelled(){if(interruption)throw interruption;},
    isCancellation:error=>error===interruption,
    get exitCode(){return interruption?.exitCode;},
    dispose(){for(const [signal,handler] of handlers)signalProcess.removeListener(signal,handler);},
  };
}
function runLegacyCheck(name,operation,{cancellationGraceMs,cleanup,realSignalCheckpoints=false,existingSignalHandlers=false,processRef=process}={}) {
  return h.runCheck({name,cancellationGraceMs,cleanup,processRef,
    ...(existingSignalHandlers?{createCancellation:observeLegacySignals}:{}),async run(context) {
    armInterruption(context,{realSignalCheckpoints});
    await operation(context);
    await context.throwIfCancelled();
    // Preserve legacy checks which collect failures instead of throwing them.
    if(processRef.exitCode===2)throw new h.SkipCheck('Prerequisite unavailable; see the check diagnostics');
    if(processRef.exitCode)throw new Error('Legacy check reported a failure; see its diagnostics');
  }});
}
async function finishLegacyCleanup(actions) {
  const failures=[];
  for(const action of actions) {
    try { await action(); } catch(error) { failures.push(error); }
  }
  if(failures.length)throw new AggregateError(failures,'Legacy fixture cleanup failed');
}
function sanitizedError(error, isCancellation) {
  if(typeof isCancellation==='function'&&isCancellation(error))return error;
  const safe=new Error(h.withoutQueryStrings(error?.message || 'Legacy check failed'));
  if(error?.name==='SkipCheck')safe.name='SkipCheck';
  if(typeof error?.failedStep==='string')safe.failedStep=h.withoutQueryStrings(error.failedStep);
  return safe;
}
module.exports={runLegacyCheck,observeLegacySignals,finishLegacyCleanup,armInterruption,sanitizedError,NO_PLAYWRIGHT_SIGNAL_HANDLING:h.NO_PLAYWRIGHT_SIGNAL_HANDLING};
