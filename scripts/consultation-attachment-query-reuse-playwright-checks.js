#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// #5668: exercise the real ViewRequest/JSP, including its attachment loaders and omission notice.
// Requires an exclusive disposable local install and a database administrator: temporarily enables
// MariaDB's general query log to TABLE, then restores its original settings in finally. Existing log
// rows are retained. Never run against patient data. Fixtures belong to runWorkflow/ownedReferrals.
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {ownedReferrals} = require('./lib/consultation-referral-fixture');

function preflightConfig({config}) {
  if (process.env.CARLOS_DISPOSABLE_VM !== 'true' || process.env.EXCLUSIVE !== '1'
      || !h.isLocalTlsTarget(config.baseUrl)) {
    throw new h.SkipCheck('Requires CARLOS_DISPOSABLE_VM=true, EXCLUSIVE=1 and a local target');
  }
}

function preflight({sql}) {
  if (sql.value('SELECT @@GLOBAL.general_log') !== '0') {
    throw new h.SkipCheck('The query log is already enabled; refusing to alter an existing capture');
  }
}

async function workflow(s) {
  const {sql, context, config, patient, provider, marker} = s;
  const owned = ownedReferrals(s);
  const requestId = owned.seed({reason: `${marker} attachment query regression`});
  const page = await context.newPage();
  const url = h.appUrl(config.baseUrl, `/encounter/ViewRequest?requestId=${requestId}&de=${patient}`);
  for (const omitted of [false, true]) {
    await s.step(`${omitted ? 'omitted' : 'empty'} attachments query each relationship once in the request view`, async () => {
      if (omitted) sql.execute(`INSERT INTO consultdocs(requestId,document_no,doctype,deleted,attach_date,provider_no)
        VALUES(?,2147483640,'F',NULL,CURDATE(),?),
        (?,2147483641,'H',NULL,CURDATE(),?)`, [requestId, provider, requestId, provider]);
      const originalOutput = sql.value('SELECT @@GLOBAL.log_output');
      const started = sql.value('SELECT NOW(6)');
      let counts;
      try {
        sql.execute("SET GLOBAL log_output='TABLE'");
        sql.execute('SET GLOBAL general_log=ON');
        await page.goto(url, {waitUntil: 'networkidle', timeout: 120000});
        await page.locator('#EctConsultationFormRequest2Form').waitFor();
        await h.assertNotErrorPage(page, 'consultation request render');
      } finally {
        sql.execute('SET GLOBAL general_log=OFF');
        sql.execute('SET GLOBAL log_output=?', [originalOutput]);
      }
      counts = sql.rows(`SELECT
        COALESCE(SUM(LOWER(argument) REGEXP "doctype[[:space:]]*=[[:space:]]*'f'"),0),
        COALESCE(SUM(LOWER(argument) REGEXP "doctype[[:space:]]*=[[:space:]]*'h'"),0)
        FROM mysql.general_log WHERE event_time>=? AND command_type IN ('Query','Execute')
        AND LOWER(argument) LIKE '%from consultdocs%'
        AND LOWER(argument) REGEXP ?`, [started, `requestid[[:space:]]*=[[:space:]]*${requestId}([^0-9]|$)`]);
      h.assert(counts.length === 1 && counts[0][0] === '1' && counts[0][1] === '1',
        `Expected one form and one HRM relationship SELECT; got ${JSON.stringify(counts)}`);
      const notice = page.locator('#consultAttachmentsNotIncluded');
      if (omitted) {
        const text = await notice.innerText();
        h.assert(text.includes('An attached clinical form is unavailable and has not been included.'), 'Missing form omission warning');
        h.assert(text.includes('An attachment is unavailable and has not been included.'), 'Missing generic HRM omission warning');
        h.assert(await notice.locator('li').count() === 2, 'Expected exactly two generic omission warnings');
        h.assert(!text.includes('2147483640') && !text.includes('2147483641'), 'An unavailable attachment identifier was disclosed');
      } else h.assert(await notice.count() === 0, 'Unexpected omission warning');
      h.assert(sql.value('SELECT COUNT(*) FROM consultdocs WHERE requestId=?', [requestId]) === (omitted ? '2' : '0'),
        'Viewing the referral changed its attachment relationships');
      console.log('PASS actual request view: form relationship queries=1, HRM relationship queries=1; warning output correct');
    });
  }
}

if (require.main === module) runWorkflow('consultation-attachment-query-reuse', workflow,
  {openMaster: false, preflightConfig, preflight});
module.exports = {preflightConfig, preflight, workflow};
