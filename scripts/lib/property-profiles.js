/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const crypto = require('node:crypto');
const { execFileSync, spawn } = require('node:child_process');
const PROFILES = Object.freeze({
  'address-lock': {login_lock: 'false',login_max_failed_times:'3',login_max_duration:'10',login_local_ip:'127.0.0.1'},
  'native-pathnet': {PATHNET_LABS: 'yes'},
  'first-nations': {FIRST_NATIONS_MODULE:'true',showBandNumberOnly:'false'},
  'first-nations-encoding': {FIRST_NATIONS_MODULE:'true',showBandNumberOnly:'false'},
  'no-pin': {'mfa.legacy.pin.enable': 'false'},
  multisite: {multisites: 'on', rma_enabled: 'true', moh_file_management_enabled: 'true'},
  'caisi-admin': {caisi: 'on', 'caisi.search.workflow': 'false',ModuleNames:'Caisi'},
  'legacy-contacts': {NEW_CONTACTS_UI: 'false'},
  'private-consent': {privateConsentEnabled: 'true',STUDENT_PARTICIPATION_CONSENT:'false'},
  'appointment-options': {mc_number: 'true', emailApptReminder: 'yes'},
  'appointment-lock-expiry': {appointment_locking_timeout: '20', allowMultipleSameDayGroupAppt: 'yes'},
});
function propertiesFor(name, env = process.env) {
  if (!Object.hasOwn(PROFILES, name)) throw new Error(`Unknown property profile ${name}`);
  const properties = {...PROFILES[name]};
  if (name === 'private-consent') {
    if (!/^[1-9]\d*$/.test(env.CONSENT_PROGRAM_ID || '')) throw new Error('private-consent needs CONSENT_PROGRAM_ID for an owned fixture program');
    properties.privateConsentPrograms = env.CONSENT_PROGRAM_ID;
  }
  return properties;
}
function renderProperties(snapshot, properties) {
  // Append overrides and restore the original bytes afterwards. Existing duplicate
  // keys, comments, encoding and final-newline choices are never reconstructed.
  return Buffer.concat([snapshot, Buffer.from('\n# CARLOS disposable validation profile\n'
    + Object.entries(properties).map(([key, value]) => `${key}=${value}`).join('\n') + '\n')]);
}
function readinessSeconds(env=process.env) {
  const seconds=Number(env.CARLOS_PROFILE_READINESS_SECONDS||300);
  if(!Number.isInteger(seconds)||seconds<10||seconds>1800)throw new Error('CARLOS_PROFILE_READINESS_SECONDS must be an integer from 10 to 1800');
  return seconds;
}
function profileBytes(file) {
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  try {if(!fs.fstatSync(fd).isFile())throw new Error('Profile configuration must be a regular file');return fs.readFileSync(fd);}
  finally {fs.closeSync(fd);}
}
function handoffRecord(name,env,file,parentPid=process.pid) {
  if(env.CARLOS_DISPOSABLE_HOST!=='true')throw new Error('Property profiles require CARLOS_DISPOSABLE_HOST=true');
  const bytes=profileBytes(file),suffix=renderProperties(Buffer.alloc(0),propertiesFor(name,env));
  if(bytes.length<suffix.length||!bytes.subarray(-suffix.length).equals(suffix))throw new Error('Active profile properties do not match the requested configuration');
  return {profile:name,parentPid,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
}
async function verifyActiveProfile(name,{env=process.env,file='/etc/carlos-emr/carlos.properties',parentPid=process.ppid,fd=3}={}) {
  if(env.CARLOS_PROPERTY_PROFILE_FD!=='3')throw new Error('Active profile has no private parent handoff');
  const stat=fs.fstatSync(fd);if(!stat.isFIFO()&&!stat.isSocket())throw new Error('Active profile handoff must be an inherited pipe');
  const expected=handoffRecord(name,env,file,parentPid);
  const record=await new Promise((resolve,reject)=>{
    const stream=fs.createReadStream(null,{fd,autoClose:true,highWaterMark:4096});let bytes=Buffer.alloc(0),settled=false;
    const done=(error,value)=>{if(settled)return;settled=true;clearTimeout(timer);stream.destroy();error?reject(error):resolve(value);};
    const timer=setTimeout(()=>done(new Error('Active profile handoff timed out')),3000);
    stream.on('data',chunk=>{bytes=Buffer.concat([bytes,chunk]);if(bytes.length>4096)done(new Error('Active profile handoff is oversized'));});
    stream.on('error',error=>done(error));stream.on('end',()=>{try{done(null,JSON.parse(bytes.toString('utf8')));}catch(error){done(new Error('Active profile handoff is invalid'));}});
  });
  if(record.profile!==expected.profile||record.parentPid!==expected.parentPid||record.sha256!==expected.sha256)throw new Error('Active profile handoff does not match its parent and installed configuration');
}
function restartAndWait(env = process.env) {
  const seconds=readinessSeconds(env);
  // A profile deliberately restarts twice. Release systemd start-rate limits
  // count these operator starts too; reset that budget only at this explicit
  // boundary. The child-run NRestarts check still catches an unexpected crash.
  execFileSync('systemctl', ['reset-failed', 'carlos-emr'], {stdio: 'inherit', timeout: 10000});
  execFileSync('systemctl', ['restart', 'carlos-emr'], {stdio: 'inherit', timeout: 120000});
  // /status is public; never include a credential-bearing URL in subprocess args.
  const base = new URL(env.BASE_URL || 'https://127.0.0.1/carlos');
  if (base.username || base.password || !['http:', 'https:'].includes(base.protocol)) throw new Error('Invalid profile readiness target');
  const url = `${base.origin}${base.pathname.replace(/\/$/, '')}/status`;
  const curl = ['--fail', '--silent', '--show-error', '--retry', String(seconds), '--retry-max-time', String(seconds), '--retry-delay', '2', '--retry-connrefused', '--max-time', '5', url];
  if (base.protocol === 'https:' && ['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname)
      && fs.existsSync('/etc/carlos-emr/tls/fullchain.pem')) curl.unshift('--cacert', '/etc/carlos-emr/tls/fullchain.pem');
  execFileSync('curl', curl, {stdio: 'inherit', timeout: (seconds+30)*1000, env});
}
async function withPropertyProfile(name, operation, {env = process.env, file = '/etc/carlos-emr/carlos.properties', restart = () => restartAndWait(env), readRestarts = () => execFileSync('systemctl', ['show', 'carlos-emr', '-p', 'NRestarts', '--value'], {encoding: 'utf8', timeout: 10000}).trim()} = {}) {
  const properties = propertiesFor(name, env);
  readinessSeconds(env);
  if (env.CARLOS_DISPOSABLE_HOST !== 'true') throw new Error('Property profiles require CARLOS_DISPOSABLE_HOST=true');
  if (!fs.lstatSync(file).isFile()) throw new Error('Property profile target must be a regular file');
  const snapshot = fs.readFileSync(file);
  let result, failure, interrupted;
  const cancellation=new AbortController();
  const handlers=Object.fromEntries(['SIGINT','SIGTERM'].map(signal=>[signal,()=>{
    interrupted=signal;cancellation.abort(signal);
  }]));
  for(const [signal,handler] of Object.entries(handlers))process.on(signal,handler);
  try {
    fs.writeFileSync(file, renderProperties(snapshot, properties));
    await restart();
    if(cancellation.signal.aborted)throw new Error(`Profile interrupted by ${interrupted}`);
    const before = readRestarts();
    result = await operation({signal:cancellation.signal});
    if(cancellation.signal.aborted)throw new Error(`Profile interrupted by ${interrupted}`);
    if (readRestarts() !== before) throw new Error('carlos-emr restarted during the profile checks');
  } catch (error) {failure = error;}
  try {fs.writeFileSync(file, snapshot); await restart();}
  catch (error) {throw failure ? new AggregateError([failure, error], 'Profile run and restoration failed') : error;}
  finally {for(const [signal,handler] of Object.entries(handlers))process.removeListener(signal,handler);}
  if (failure) throw failure;
  if (interrupted) throw new Error(`Profile interrupted by ${interrupted} during restoration`);
  return result;
}

// Keep the profile owner asynchronous while the suite performs synchronous
// per-check subprocess work. Signals can then reach the whole check process
// group and allow its cleanup before the owner restores/restarts the service.
function runProfileChild(args,profile,signal,{file='/etc/carlos-emr/carlos.properties',env=process.env}={}) {
  if(signal.aborted)return Promise.reject(new Error('Profile interrupted before suite start'));
  const handoff=handoffRecord(profile,env,file);
  return new Promise((resolve,reject)=>{
    const child=spawn(process.execPath,args,{stdio:['inherit','inherit','inherit','pipe'],detached:process.platform!=='win32',
      env:{...env,CARLOS_PROPERTY_PROFILE_ACTIVE:profile,CARLOS_PROPERTY_PROFILE_FD:'3'}});
    let handoffFailure;child.stdio[3].on('error',error=>{handoffFailure=error;});child.stdio[3].end(JSON.stringify(handoff));
    let timer;
    const stop=()=>{
      const kill=name=>{try{if(process.platform==='win32')child.kill(name);else process.kill(-child.pid,name);}catch(error){if(error.code!=='ESRCH')reject(error);}};
      kill('SIGTERM');timer=setTimeout(()=>kill('SIGKILL'),30000);timer.unref();
    };
    signal.addEventListener('abort',stop,{once:true});
    const clear=()=>{clearTimeout(timer);signal.removeEventListener('abort',stop);};
    child.once('error',error=>{clear();reject(error);});
    child.once('close',(code,killed)=>{clear();
      if(signal.aborted||killed)reject(new Error('Profile suite interrupted; configuration restored after child shutdown'));
      else if(handoffFailure)reject(new Error('Profile parent handoff could not be delivered'));
      else resolve(code);
    });
    if(signal.aborted)stop();
  });
}
module.exports = {PROFILES, propertiesFor, renderProperties, withPropertyProfile, runProfileChild,verifyActiveProfile,handoffRecord,readinessSeconds,restartAndWait};
