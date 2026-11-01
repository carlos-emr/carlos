/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');

// Older checks already own fixture cleanup in finally, but hand-written signal
// handlers could delete their rows while an accepted browser write was running.
// Await that write, checkpoint in the caller, and unwind through its finally.
function runLegacyCheck(name,operation) {
  return h.runCheck({name,async run(context) {
    const requested=process.env.CARLOS_INTERRUPT_AFTER_CHECKPOINT;
    if(requested!==undefined) {
      h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true'&&/^[1-9]\d{0,3}$/.test(requested),
        'Signal audit requires a disposable install and a positive checkpoint number');
      let reached=0;
      const original=context.cancellation.throwIfCancelled;
      context.cancellation.throwIfCancelled=()=>{
        if(++reached===Number(requested))process.kill(process.pid,'SIGINT');
        original();
      };
    }
    await operation(context);
    context.throwIfCancelled();
    // Preserve legacy checks which collect failures instead of throwing them.
    if(process.exitCode===2)throw new h.SkipCheck('Prerequisite unavailable; see the check diagnostics');
    if(process.exitCode)throw new Error('Legacy check reported a failure; see its diagnostics');
  }});
}
module.exports={runLegacyCheck,NO_PLAYWRIGHT_SIGNAL_HANDLING:h.NO_PLAYWRIGHT_SIGNAL_HANDLING};
