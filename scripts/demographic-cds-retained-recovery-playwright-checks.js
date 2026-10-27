#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Disposable-reference-database gate. Default mode is read-only; execution is explicit.
const fs = require('node:fs');
const {createHash} = require('node:crypto');
const h = require('./lib/playwright-harness');
const {importApi, awaitRunReceipt, uploadAndAwaitRun} = require('./demographic-cds-import-playwright-checks');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function assertRetainedLogin(recorder, baseUrl, maintenanceExpected = true) {
  const schedule = new URL(baseUrl).pathname.replace(/\/$/, '') + '/provider/providercontrol';
  const knownResponse = entry => entry.status === 503 && entry.resourceType === 'document'
    && new URL(entry.url).pathname === schedule;
  const knownConsole = entry => {
    const pathname = new URL(entry.location?.url || '/', baseUrl).pathname;
    return (maintenanceExpected && pathname === schedule && /server responded with a status of 503/.test(entry.text))
      || (pathname === '/favicon.ico' && /server responded with a status of 404/.test(entry.text));
  };
  h.assert(recorder.badResponses.every(entry => maintenanceExpected && knownResponse(entry)), 'Native login returned an unexpected error response');
  h.assert(recorder.consoleIssues.every(knownConsole), 'Native login produced an unexpected console error');
  h.assert(!recorder.pageErrors.length && !recorder.requestFailures.length && !recorder.unexpectedDialogs.length,
    'Native login produced a script, network or dialog failure');
  if (maintenanceExpected) h.assert(recorder.badResponses.some(knownResponse), 'The retained clinical schedule was not closed503');
}

function sourceFixture(env) {
  h.assert(env.CDS_RECOVERY_SOURCE_FILE && /^[0-9a-f]{64}$/.test(env.CDS_RECOVERY_SOURCE_SHA256 || ''),
    'Execution requires the original local source file and its independently verified SHA256');
  const stat = fs.statSync(env.CDS_RECOVERY_SOURCE_FILE);
  h.assert(stat.isFile() && stat.size > 0 && stat.size <= 16 * 1024 * 1024,
    'This synthetic recovery gate accepts one source fixture of at most16MiB');
  const name = env.CDS_RECOVERY_SOURCE_NAME;
  h.assert(/^[A-Za-z0-9_.-]+\.xml$/.test(name), 'An explicit safe original XML file name is required');
  const buffer = fs.readFileSync(env.CDS_RECOVERY_SOURCE_FILE);
  h.assert(createHash('sha256').update(buffer).digest('hex') === env.CDS_RECOVERY_SOURCE_SHA256,
    'The replacement fixture differs from the retained original source bytes');
  return {name, mimeType:'application/xml', buffer};
}

async function run(env = process.env) {
  let browser; let sql;
  const recorder = h.createRecorder();
  return h.runCheck({name:'demographic-cds-retained-recovery', async run() {
    const oldId = env.CDS_RETAINED_RUN_ID;
    h.assert(UUID.test(oldId || ''), 'CDS_RETAINED_RUN_ID must identify the retained reference-database run');
    const config = h.readConfig(); sql = h.createSqlRunner(config.mysql);
    const zeroProof = () => sql.value(`SELECT
      (SELECT COUNT(*) FROM cds_import_checkpoint WHERE run_id=${h.sqlString(oldId)}) +
      (SELECT COUNT(*) FROM cds_import_patient WHERE run_id=${h.sqlString(oldId)}
        AND (held=1 OR target_demographic_no IS NOT NULL OR state='RECONCILIATION_REQUIRED')) +
      (SELECT COUNT(*) FROM cds_import_unit WHERE run_id=${h.sqlString(oldId)} AND state<>'EXPECTED') +
      (SELECT COUNT(*) FROM cds_import_diagnostic WHERE run_id=${h.sqlString(oldId)} AND resolved=0
        AND code IN ('UNIT_COMMIT_OUTCOME_UNKNOWN','CONTROL_COMMIT_OUTCOME_UNKNOWN'))`);
    h.assert(zeroProof() === '0', 'A committed, held or uncertain prior run cannot use zero-write replacement');
    const retained = sql.value("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'") === '1';
    h.assert(retained || UUID.test(env.CDS_RECOVERY_RUN_ID || ''), 'The retained window is not closed; supply an exact accepted recovery handle to inspect it');
    browser = await h.launchBrowser(config);
    const context = await h.newContext(browser, config);
    const loginRecorder = h.createRecorder(); const schedule = await h.login(context, config, loginRecorder);
    assertRetainedLogin(loginRecorder, config.baseUrl, retained);
    const page = await context.newPage(); h.wireStrictPage(page, 'retained-recovery', recorder);
    const response = await page.goto(h.appUrl(config.baseUrl, '/form/importUpload?runId=' + oldId));
    h.assert(response.status() === 200, 'The authenticated recovery form is unavailable under retained maintenance');
    await page.locator('#cdsImport').waitFor();
    await page.waitForFunction(id => {
      try { return JSON.parse(document.getElementById('runSummary').textContent).job.id === id; } catch (_) { return false; }
    }, oldId);
    let prior = JSON.parse(await page.locator('#runSummary').innerText());
    h.assert(prior.job.id === oldId && typeof prior.summary.resumeRequested === 'boolean',
      'The visible durable run contract is incomplete');
    h.assertStrictPage(recorder);
    console.log('PASS retained maintenance: native authentication, recovery form and owner status render');
    if (env.CDS_RECOVERY_EXECUTE !== 'true') {
      h.assert(zeroProof() === '0', 'The read-only restart gate changed prior native-write proof');
      console.log('PASS read-only gate: no cancellation, review, replacement or native import requested');
      return;
    }
    const file = sourceFixture(env);
    h.assert(/^[A-Za-z0-9_-]{1,16}$/.test(env.CDS_RECOVERY_PROVIDER_NO || ''), 'The reviewed provider context is required');
    for (const name of ['CDS_RECOVERY_PROGRAM_ID','CDS_RECOVERY_FACILITY_ID']) {
      h.assert(/^[1-9][0-9]*$/.test(env[name] || '') && Number.isSafeInteger(Number(env[name])),
        name + ' must freeze the reviewed positive numeric context');
    }
    if (!['CANCELLED_BEFORE_WRITE','CANCELLED'].includes(prior.job.state)) {
      h.assert(prior.job.state === 'REVIEW_REQUIRED' || prior.job.state === 'FAILED_BEFORE_WRITE',
        'Prior run must be stopped and proven unwritten before this recovery gate cancels it');
      await page.locator('#cancelRun').click();
      await page.waitForFunction(id => {
        try { const row=JSON.parse(document.getElementById('runSummary').textContent);
          return row.job.id===id && ['CANCELLED_BEFORE_WRITE','CANCELLED'].includes(row.job.state)
            && row.summary.reportsDelivered===true && row.summary.operationTerminal===true; } catch (_) { return false; }
      }, oldId, {timeout:180000});
      prior = JSON.parse(await page.locator('#runSummary').innerText());
    }
    h.assert(zeroProof() === '0', 'Cancellation changed the prior zero-write proof');
    h.assert(prior.summary.clinicalComplete === false, 'Cancellation was misreported as clinical completion');
    h.assertStrictPage(recorder);
    console.log('PASS prior cancellation retained its source/history; cancellation does not prove maintenance release');
    let id = env.CDS_RECOVERY_RUN_ID;
    if (id) {
      h.assert(UUID.test(id), 'CDS_RECOVERY_RUN_ID is invalid');
      await page.locator('#runId').fill(id); await page.locator('#openRun').click();
    } else {
      h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_run WHERE id<>${h.sqlString(oldId)} AND created_at>=
        (SELECT created_at FROM cds_import_run WHERE id=${h.sqlString(oldId)})`) === '0',
      'A newer accepted attempt already exists: retain it and supply its exact CDS_RECOVERY_RUN_ID');
      await page.locator('#cdsSource').selectOption(prior.job.sourceNamespace + '|' + prior.job.profile);
      await page.locator('#providerNo').fill(env.CDS_RECOVERY_PROVIDER_NO);
      await page.locator('#programId').fill(env.CDS_RECOVERY_PROGRAM_ID);
      await page.locator('#facilityId').fill(env.CDS_RECOVERY_FACILITY_ID);
      await page.locator('#mergeExisting').uncheck();
      if (await page.locator('#synthetic').count()) await page.locator('#synthetic').uncheck();
      await page.locator('#matchProviderNames').setChecked(env.CDS_RECOVERY_MATCH_PROVIDER_NAMES === 'true');
      const accepted = await uploadAndAwaitRun({config}, page, page, file);
      id = accepted.id;
      console.log('RECOVERY_RUN_ID', id);
    }
    let current = await awaitRunReceipt(page, id);
    h.assert(current.job.sourceNamespace === prior.job.sourceNamespace && current.job.profile === prior.job.profile,
      'Recovery changed the source namespace/profile');
    if (current.summary.cleanComplete !== true) {
      h.assert(current.summary.unwrittenRevisionApprovalAllowed === true,
        'The backend does not prove this exact zero-write revision is eligible; inspect retained diagnostics');
      const diagnostic = page.locator('#diagnosticRows tr').filter({hasText:'SOURCE_REVISION_REVIEW_REQUIRED'}).first();
      await diagnostic.getByRole('button', {name:/Review|Réviser/}).click();
      h.assert(await page.locator('#reviewKind').inputValue() === 'APPROVE_UNWRITTEN_REVISION',
        'The UI did not explicitly offer zero-write revision review');
      await page.locator('#reviewReason').fill('Synthetic release gate: reviewed identical retained source bytes, same namespace and frozen command context; authorize current-code replacement only after server zero-write and historical provenance proof.');
      await page.locator('#cdsReviewForm button[type=submit]').click();
      await page.waitForFunction(version => {
        try { return JSON.parse(document.getElementById('runSummary').textContent).summary.reviewVersion > version; }
        catch (_) { return false; }
      }, current.summary.reviewVersion, {timeout:120000});
      current = await awaitRunReceipt(page, id, 300000);
    }
    h.assert(current.summary.cleanComplete === true && current.summary.clinicalComplete === true
      && current.summary.reportsDelivered === true && current.summary.cleanupComplete === true
      && current.summary.resumeRequested === false, 'Recovery did not prove clinical/report/cleanup completion');
    h.assert(sql.value("SELECT maintenance_active FROM cds_import_admission WHERE id='clinical-maintenance'") === '0',
      'The durable clinical barrier remains closed');
    h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE run_id=${h.sqlString(id)}
      AND target_demographic_no IS NOT NULL AND held=0`) === '1', 'Recovery has no one released native patient');
    const report = await importApi(page, 'cdsImportReport', {runId:id,type:'receipt'});
    h.assert(report.status === 200 && report.body?.manifestDigest === current.job.manifestDigest
      && report.body.cleanComplete === true, 'Protected completion receipt differs from visible durable status');
    h.assert(zeroProof() === '0', 'Replacement rewrote the prior run instead of retaining its zero-write archive');
    h.assertStrictPage(recorder);
    // An externally released barrier may leave this web node's initializer pending.
    // Use authenticated HTTP reads to start it, without suppressing browser errors.
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    let clinicalReady = false;
    for (let attempt = 0; attempt < 60; attempt++) {
      const ordinary = await context.request.get(schedule.url(), {headers:{'CSRF-TOKEN':token}, failOnStatusCode:false});
      if (ordinary.status() === 200) {
        const body = await ordinary.text();
        h.assert(!h.isApplicationErrorPage(body) && !h.isSecurityErrorBody(body)
          && /id=["'](?:admin-panel|admin2)["']/.test(body), 'Ordinary workflow returned an error or login document');
        clinicalReady = true; break;
      }
      h.assert(ordinary.status() === 503, 'Ordinary workflow failed after maintenance release');
      await page.waitForTimeout(1000);
    }
    h.assert(clinicalReady, 'Post-release clinical initialization did not complete');
    console.log('PASS post-release ordinary clinical workflow resumed after real deferred initialization');
    console.log('PASS current-code replacement: same source/namespace, explicit review, native commits, reports and released maintenance');
  }, async cleanup() {
    if (browser) await browser.close();
    if (sql) sql.dispose();
    // Accepted jobs, immutable source, review archives and the synthetic chart remain for reconciliation.
  }});
}
if (require.main === module) run();
module.exports = {run, assertRetainedLogin, sourceFixture};
