/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const PROFILES = Object.freeze({
  'address-lock': {login_lock: 'false'},
  'no-pin': {'mfa.legacy.pin.enable': 'false'},
  multisite: {multisites: 'on', rma_enabled: 'true', moh_file_management_enabled: 'true'},
  'caisi-admin': {caisi: 'on', 'caisi.search.workflow': 'false'},
  'legacy-contacts': {NEW_CONTACTS_UI: 'false'},
  'private-consent': {privateConsentEnabled: 'true'},
  'appointment-options': {mc_number: 'true', emailApptReminder: 'yes'},
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
function restartAndWait(env = process.env) {
  execFileSync('systemctl', ['restart', 'carlos-emr'], {stdio: 'inherit', timeout: 120000});
  // /status is public; never include a credential-bearing URL in subprocess args.
  const base = new URL(env.BASE_URL || 'https://127.0.0.1/carlos');
  if (base.username || base.password || !['http:', 'https:'].includes(base.protocol)) throw new Error('Invalid profile readiness target');
  const url = `${base.origin}${base.pathname.replace(/\/$/, '')}/status`;
  const curl = ['--fail', '--silent', '--show-error', '--retry', '30', '--retry-delay', '2', '--retry-connrefused', '--max-time', '5', url];
  if (base.protocol === 'https:' && ['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname)
      && fs.existsSync('/etc/carlos-emr/tls/fullchain.pem')) curl.unshift('--cacert', '/etc/carlos-emr/tls/fullchain.pem');
  execFileSync('curl', curl, {stdio: 'inherit', timeout: 180000, env});
}
async function withPropertyProfile(name, operation, {env = process.env, file = '/etc/carlos-emr/carlos.properties', restart = () => restartAndWait(env), readRestarts = () => execFileSync('systemctl', ['show', 'carlos-emr', '-p', 'NRestarts', '--value'], {encoding: 'utf8', timeout: 10000}).trim()} = {}) {
  const properties = propertiesFor(name, env);
  if (env.CARLOS_DISPOSABLE_HOST !== 'true') throw new Error('Property profiles require CARLOS_DISPOSABLE_HOST=true');
  if (!fs.lstatSync(file).isFile()) throw new Error('Property profile target must be a regular file');
  const snapshot = fs.readFileSync(file);
  let result, failure;
  try {
    fs.writeFileSync(file, renderProperties(snapshot, properties));
    await restart();
    const before = readRestarts();
    result = await operation();
    if (readRestarts() !== before) throw new Error('carlos-emr restarted during the profile checks');
  } catch (error) {failure = error;}
  try {fs.writeFileSync(file, snapshot); await restart();}
  catch (error) {throw failure ? new AggregateError([failure, error], 'Profile run and restoration failed') : error;}
  if (failure) throw failure;
  return result;
}
module.exports = {PROFILES, propertiesFor, renderProperties, withPropertyProfile};
