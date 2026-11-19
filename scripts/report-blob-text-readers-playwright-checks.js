#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
'use strict';
// #5922: actual MariaDB binary-column codecs and the installed report reader classes.
// Named manual check: run locally on the disposable packaged host with JDK25+.
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

const webapp = () => process.env.CARLOS_INSTALLED_WEBAPP_DIR || '/usr/share/carlos-emr/webapp/carlos';
const databaseIdentity = "SELECT CONCAT_WS(':', HEX(DATABASE()), HEX(@@hostname), @@port, @@server_id, HEX(@@datadir))";

function runProbe(args, env) {
  const classpath = [path.join(webapp(), 'WEB-INF/classes'), path.join(webapp(), 'WEB-INF/lib/*')].join(':');
  return spawnSync('java', ['--source', '25', '-cp', classpath,
    path.join(__dirname, 'fixtures/carlostest5922/ReportBlobReaderProbe.java'), ...args],
  {stdio: 'inherit', timeout:60000, killSignal:'SIGKILL', env: {...process.env, ...env}});
}

async function preflight({config}) {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('The local packaged reader check requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');
  }
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(new URL(config.baseUrl).hostname)) {
    throw new h.SkipCheck('The reader probe must run on the local host serving the installed application');
  }
  try {
    fs.accessSync('/etc/carlos-emr/carlos.properties', fs.constants.R_OK);
    fs.accessSync(path.join(webapp(), 'WEB-INF/classes/io/github/carlos_emr/carlos/report/data/ConfiguredReportQuery.class'));
  } catch {
    throw new h.SkipCheck('The reader probe needs the local packaged webapp and access to its private properties');
  }
  const java = spawnSync('java', ['--list-modules'], {encoding: 'utf8', timeout:10000, killSignal:'SIGKILL'});
  const compiler = /^jdk\.compiler@(\d+)/m.exec(java.stdout || '');
  if (java.error || java.status !== 0 || !compiler || Number(compiler[1]) < 25) {
    throw new h.SkipCheck('The installed reader probe requires a local JDK25 or newer');
  }
}

async function workflow(s) {
  const {sql, marker} = s;
  const identity = sql.value(databaseIdentity);
  h.assert(typeof identity === 'string' && /^[0-9A-F]+:[0-9A-F]+:\d+:\d+:[0-9A-F]+$/.test(identity),
    'Could not identify the report BLOB fixture database');
  const targetEnv = {PROBE_BLOB_DB_ID: identity};
  const target = runProbe(['--verify-target'], targetEnv);
  h.assert(!target.error && target.status === 0,
    'Report BLOB probe setup refused the fixture database before insertion');
  const html = '<html><body>FAKE unpublished BLOB reader fixture</body></html>';
  const owned = [marker, marker, marker, html];
  const present = () => sql.rows('SELECT fid FROM eform WHERE form_name=? AND subject=? AND form_creator=? AND form_html=?', owned);
  h.assert(present().length === 0, 'The owned BLOB fixture already exists');
  s.cleanup(() => {
    const rows = present();
    h.assert(rows.length <= 1, 'The BLOB fixture identity is ambiguous');
    for (const [id] of rows) {
      h.assert(sql.value('SELECT COUNT(*) FROM eform_data WHERE fid=?', [id]) === '0',
        'The inactive BLOB fixture acquired clinical data and is left in place');
      sql.execute('DELETE FROM eform WHERE fid=? AND form_name=? AND subject=? AND form_creator=? AND form_html=?', [id, ...owned]);
    }
    h.assert(present().length === 0, 'The owned BLOB fixture remains');
  });
  await s.step('an owned inactive form supplies a real binary BLOB column', async () => {
    const id = sql.insertId('INSERT INTO eform (form_name,subject,form_creator,status,form_html,showLatestFormOnly,patient_independent,errorLog) VALUES (?,?,?,0,?,0,0,?)',
      [marker, marker, marker, html, 'FAKE-report-\u03a9']);
    h.assert(/^[1-9]\d*$/.test(id), 'The BLOB fixture has no valid generated identifier');
    const result = runProbe([], {...targetEnv, PROBE_BLOB_FORM_ID: id, PROBE_BLOB_FORM_MARKER: marker});
    if (result.error) throw result.error;
    h.assert(result.status !== 2, 'Report BLOB probe setup lost the fixture database after insertion');
    h.assert(result.status === 0, 'An installed binary report reader failed the JDBC decoding probe');
  });
}

if (require.main === module) runWorkflow('report-blob-text-readers', workflow, {openPatient: false, preflightConfig: preflight});
module.exports = {workflow, preflight};
