/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {spawn}=require('node:child_process');
function runOwnedInstaller(command,args,{timeoutMs=900000,killGraceMs=10000}={}) {
  return new Promise((resolve,reject)=>{
    const child=spawn(command,args,{stdio:'inherit',detached:process.platform!=='win32'});
    let timedOut=false,killTimer,settled=false;
    const kill=signal=>{try{if(process.platform==='win32')child.kill(signal);else process.kill(-child.pid,signal);}catch(error){if(error.code!=='ESRCH')throw error;}};
    const stop=signal=>{try{kill(signal);}catch(error){error.uncertain=true;finish(error);}};
    const timer=setTimeout(()=>{timedOut=true;stop('SIGTERM');if(!settled)killTimer=setTimeout(()=>stop('SIGKILL'),killGraceMs);},timeoutMs);
    const finish=(error)=>{if(settled)return;settled=true;clearTimeout(timer);clearTimeout(killTimer);error?reject(error):resolve();};
    child.once('error',finish);
    child.once('close',async(code,signal)=>{
      try {
      if(timedOut) {
        kill('SIGKILL');
        const deadline=Date.now()+killGraceMs;
        while(process.platform!=='win32'&&Date.now()<deadline) {
          try{process.kill(-child.pid,0);}catch(error){if(error.code==='ESRCH')break;finish(error);return;}
          await new Promise(resolve=>setTimeout(resolve,25));
        }
      }
      if(code===0&&!timedOut){finish();return;}
      const error=new Error(`Upgrade installer failed (${timedOut?'timeout':signal||code}); retain canaries for reconciliation`);error.uncertain=true;finish(error);
      }catch(error){error.uncertain=true;finish(error);}
    });
  });
}
module.exports={runOwnedInstaller};
