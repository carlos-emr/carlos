/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// An owned coordination row is held by a dedicated physical MariaDB connection.
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{spawn}=require('node:child_process');
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos',h=require(root+'/scripts/lib/playwright-harness');
async function fence(config,key){
 h.assert(Number.isInteger(key)&&key<0,'Expected a negative source coordination key');
 const host=h.validateMysqlHost(config.host),directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-row-fence-')),file=path.join(directory,'client.cnf');
 h.assert(config.password!=null&&!/[\r\n]/.test(config.password),'Invalid database credentials');
 fs.writeFileSync(file,'[client]\npassword="'+String(config.password).replace(/\\/g,'\\\\').replace(/"/g,'\\"')+'"\n',{mode:0o600});
 const child=spawn('mysql',[`--defaults-extra-file=${file}`,'-h',host,'-u',config.user||'root',config.database||'carlos','-N','-B','--unbuffered'],{stdio:['pipe','pipe','pipe']});
 child.stderr.resume();let ended=false,failed=false;
 const closed=new Promise(resolve=>{child.once('error',()=>{failed=true;ended=true;resolve();});child.once('close',code=>{failed||=code!==0;ended=true;resolve();});});
 child.stdin.on('error',()=>{failed=true;});
 async function release(){if(!ended)child.stdin.end('COMMIT;\n');const timer=setTimeout(()=>{failed=true;child.kill('SIGKILL');},5000);
  try{await closed;h.assert(!failed,'Physical row fence failed');}finally{clearTimeout(timer);fs.rmSync(directory,{recursive:true,force:true});}}
 try{
  const connection=await new Promise((resolve,reject)=>{let text='';const timer=setTimeout(()=>finish(new Error('Physical row fence timed out')),5000);
   function finish(error,value){clearTimeout(timer);child.stdout.off('data',receive);error?reject(error):resolve(value);}
   function receive(chunk){text+=chunk.toString();if(text.includes('\n'))finish(/^[1-9]\d*\n$/.test(text)?null:new Error('Unexpected row fence reply'),Number(text.trim()));}
   child.stdout.on('data',receive);closed.then(()=>finish(new Error('Physical row fence closed')));
   child.stdin.write(`START TRANSACTION; UPDATE providerLabRoutingLock SET lab_no=lab_no WHERE lab_no=${key}; SELECT CONNECTION_ID();\n`);
  });
  fs.rmSync(directory,{recursive:true,force:true});return{connection,release};
 }catch(error){try{await release();}catch(cleanup){throw new AggregateError([error,cleanup],'Physical row fence and cleanup failed');}throw error;}
}
async function settleWrites(prior,operations){
 const results=await Promise.allSettled(operations),failures=prior?[prior]:[];
 for(const result of results)if(result.status==='rejected')failures.push(result.reason);
 if(failures.length)throw new AggregateError(failures,'Concurrent native operations failed after all responses settled');
 return results.map(result=>result.value);
}
module.exports={fence,settleWrites};
