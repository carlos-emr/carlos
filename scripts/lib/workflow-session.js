/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { randomBytes } = require('node:crypto');
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { openMasterRecord } = require('../master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('../echart-navbar-modules-playwright-checks');

// Each scenario owns its patient and child rows. No reset of a shared demo chart.
async function runWorkflow(name, workflow, { openPatient = true } = {}) {
  let browser;
  let sql;
  let patient;
  const cleanups = [];
  const marker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  const recorder = h.createRecorder();
  const result = await h.runCheck({
    name,
    async run({ cancellation }) {
      const config = h.readConfig();
      sql = h.createSqlRunner(config.mysql);
      const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
      h.assert(provider, 'The configured test login has no provider');
      if (openPatient) {
        patient = sql.value(`INSERT INTO demographic
        (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
         provider_no,hc_type,province,roster_status,lastUpdateDate)
        VALUES (${h.sqlString(marker)},'Workflow','1980','01','02','F','AC',
          ${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
        h.assert(/^[1-9]\d*$/.test(patient), 'The synthetic patient fixture was not created');
      }
      browser = await h.launchBrowser(config);
      const context = await h.newContext(browser, config);
      context.setDefaultTimeout(20000);
      // Install synchronously on the event, before a popup's first script runs.
      context.on('page', page => h.wireStrictPage(page, name, recorder));
      const schedule = await h.login(context, config, recorder);
      let master;
      if (openPatient) {
        ({ masterPage: master } = await openMasterRecord(context, schedule, recorder, {
          searchTerm: marker, preferredDemographicNo: patient, timeout: 20000,
        }));
        h.assert(new URL(master.url()).searchParams.get('demographic_no') === patient,
          'The workflow opened a patient other than its owned fixture');
      }
      let chart;
      const session = {
        config, sql, patient, provider, marker, context, recorder, master, schedule,
        cleanup(fn) { cleanups.push(fn); },
        async chart() {
          h.assert(master, 'This workflow did not request a patient fixture');
          if (!chart || chart.isClosed()) {
            chart = await openChart(context, master, recorder, 20000);
            await waitForNavbars(chart, 20000);
          }
          return chart;
        },
        async popup(page, locator, label) {
          return ui.clickOpensPopup(page, locator, { context, recorder, label, timeout: 20000 });
        },
        async step(label, body) {
          await cancellation.run(body);
          h.assertStrictPage(recorder);
          console.log(`  PASS ${name}: ${label}`);
        },
      };
      await workflow(session);
      h.assertStrictPage(recorder);
    },
    async cleanup() {
      await cleanupOwnedWorkflow({ browser, sql, patient, marker, cleanups });
    },
  });
  if (result.outcome === 'FAIL') console.error(JSON.stringify(h.buildFailureDetails(recorder), null, 2));
  return result;
}

async function cleanupOwnedWorkflow({ browser, sql, patient, marker, cleanups }) {
  const failures = [];
  try { if (browser) await browser.close(); } catch (error) { failures.push(error); }
  // Check ownership before touching children. A replaced fixture must never cause
  // cleanup to erase another patient's records.
  let owned = !patient;
  try {
    if (patient) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${h.sqlString(marker)}`) === '1', 'Patient fixture ownership changed');
      owned = true;
    }
  } catch (error) { failures.push(error); }
  if (owned) {
    for (const cleanup of [...cleanups].reverse()) {
      try { await cleanup(); } catch (error) { failures.push(error); }
    }
    // Keep the parent available for recovery if any child cleanup failed.
    if (patient && failures.length === 0) {
      try {
        const supportRows = [
          ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
          ['measurementsDeleted', 'demographicNo'], ['demographicExt', 'demographic_no'],
          ['demographicArchive', 'demographic_no'],
        ];
        sql.execute(supportRows.map(([table, column]) =>
          `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';'));
        const remaining = supportRows.map(([table, column]) =>
          `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`).join('+');
        h.assert(sql.value(`SELECT ${remaining}`) === '0', 'Owned chart support rows were not removed');
        sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
          'The owned patient was not removed');
      } catch (error) { failures.push(error); }
    }
  }
  try { if (sql) sql.dispose(); } catch (error) { failures.push(error); }
  h.assert(!failures.length, failures.map(error => error.message).join('; '));
}

async function expectValue(sql, query, expected, message) {
  // Database reads are synchronous; bounded polling covers asynchronous UI saves.
  const deadline = Date.now() + 15000;
  do {
    if (sql.value(query) === expected) return;
    await new Promise(resolve => setTimeout(resolve, 150));
  } while (Date.now() < deadline);
  h.assert(false, message);
}

module.exports = { runWorkflow, expectValue, cleanupOwnedWorkflow };
