/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { randomBytes } = require('node:crypto');
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const {providerNavigationFixture}=require('./provider-navigation-fixture');
const { openMasterRecord } = require('../master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('../echart-navbar-modules-playwright-checks');

// Each scenario owns its patient and child rows. No reset of a shared demo chart.
// REST workflows may retain the patient fixture without opening its master record.
async function runWorkflow(name, workflow, { openPatient = true, openMaster = true, preflightConfig, preflight, patientFixtureFactory, contextOptions = {}, cancellationGraceMs } = {}) {
  let browser;
  let sql;
  let patient;
  let patientFixture;
  const cleanups = [];
  let navigation;
  const marker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  const recorder = h.createRecorder();
  const result = await h.runCheck({
    name,cancellationGraceMs,
    async run({ cancellation }) {
      const config = h.readConfig();
      // Environment-only prerequisites must be able to SKIP without database
      // credentials. Keep provider/schema-dependent preflight after SQL setup.
      if (preflightConfig) await preflightConfig({config});
      sql = h.createSqlRunner(config.mysql);
      navigation=providerNavigationFixture(sql,config.testUser,name);
      const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
      h.assert(provider, 'The configured test login has no provider');
      if (preflight) await preflight({config, sql, provider});
      if (openPatient) {
        if (patientFixtureFactory) {
          // Register the lifecycle before its first INSERT. A failed create keeps
          // its durable intent and cannot fall back to generic patient deletion.
          patientFixture = patientFixtureFactory({sql, marker, provider});
          h.assert(patientFixture && typeof patientFixture.create === 'function'
            && typeof patientFixture.verifyOwner === 'function' && typeof patientFixture.cleanup === 'function',
          'Patient fixture lifecycle is incomplete');
          patient = await patientFixture.create();
        } else patient = sql.value(`INSERT INTO demographic
        (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
         provider_no,hc_type,province,roster_status,lastUpdateDate)
        VALUES (${h.sqlString(marker)},'Workflow','1980','01','02','F','AC',
          ${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
        h.assert(/^[1-9]\d*$/.test(patient), 'The synthetic patient fixture was not created');
      }
      browser = await h.launchBrowser(config);
      const context = await h.newContext(browser, config, contextOptions);
      context.setDefaultTimeout(20000);
      // Install synchronously on the event, before a popup's first script runs.
      context.on('page', page => h.wireStrictPage(page, name, recorder));
      const schedule = await h.login(context, config, recorder);
      const scheduleUrl=schedule.url();
      let master, search;
      if (openPatient && openMaster) {
        ({ masterPage: master, searchPage: search } = await openMasterRecord(context, schedule, recorder, {
          searchTerm: marker, preferredDemographicNo: patient, timeout: 20000,
        }));
        h.assert(new URL(master.url()).searchParams.get('demographic_no') === patient,
          'The workflow opened a patient other than its owned fixture');
      }
      let chart;
      const session = {
        config, sql, patient, provider, marker, context, recorder, master, schedule, scheduleUrl, search,
        cleanup(fn) { cleanups.push(fn); },
        async chart({navbarTimeout = 20000} = {}) {
          h.assert(master, 'This workflow did not request a patient fixture');
          if (!chart || chart.isClosed()) {
            chart = await openChart(context, master, recorder, 20000);
            await waitForNavbars(chart, navbarTimeout);
          }
          return chart;
        },
        async popup(page, locator, label) {
          return ui.clickOpensPopup(page, locator, { context, recorder, label, timeout: 20000 });
        },
        async step(label, body) {
          // Both inside the try: a script error or console error that the step's own
          // actions provoked is reported by assertStrictPage, and belongs to this step.
          // markFailedStep tags the error so the runner can compare the failing step with the
          // manifest's expectedFailure.step (and tell a known failure from a new one).
          try {
            await cancellation.run(body);
            h.assertStrictPage(recorder);
          } catch (error) {
            throw h.markFailedStep(error, label);
          }
          console.log(`  PASS ${name}: ${label}`);
        },
      };
      await workflow(session);
      h.assertStrictPage(recorder);
    },
    async cleanup() {
      await cleanupOwnedWorkflow({ browser, sql, patient, marker, cleanups, patientFixture, independentCleanups:navigation?[()=>navigation.cleanup()]:[] });
    },
  });
  if (result.outcome === 'FAIL') console.error(JSON.stringify(h.buildFailureDetails(recorder), null, 2));
  return result;
}

async function cleanupOwnedWorkflow({ browser, sql, patient, marker, cleanups, patientFixture, independentCleanups=[] }) {
  const failures = [];
  await require('./uncertain-server-writes').drainWrites(browser);
  let browserClosed = !browser;
  try { if (browser) await browser.close(); browserClosed = true; } catch (error) { failures.push(error); }
  const serverSettled=!require('./uncertain-server-writes').hasUncertainWrites(browser);
  if(!serverSettled)failures.push(new Error('Server write outcome is unknown; retain fixtures for reconciliation'));
  if(browserClosed&&serverSettled)for(const cleanup of independentCleanups) {try{await cleanup();}catch(error){failures.push(error);}}
  else if(independentCleanups.length)failures.push(new Error('Provider navigation retained: browser close is unconfirmed'));
  // Check ownership before touching children. A replaced fixture must never cause
  // cleanup to erase another patient's records.
  let owned = !patient && !patientFixture;
  try {
    if (patientFixture) {
      // Strict fixtures cannot release any child while a browser might still
      // submit writes. Gate before ownership/child callbacks, not just the parent.
      h.assert(browserClosed, 'Strict patient fixture and children retained: browser close is unconfirmed');
      await patientFixture.verifyOwner(); owned = true;
    } else if (patient) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${h.sqlString(marker)}`) === '1', 'Patient fixture ownership changed');
      owned = true;
    }
  } catch (error) { failures.push(error); }
  if (owned&&serverSettled) {
    let childrenRemoved = true;
    for (const cleanup of [...cleanups].reverse()) {
      try { await cleanup(); } catch (error) {
        childrenRemoved = false;
        failures.push(error);
      }
    }
    // Retain the parent when children need recovery. Legacy workflows retain
    // their verified-cleanup behavior after a browser teardown error; strict
    // patient fixtures already refused every child callback above.
    if ((patient || patientFixture) && childrenRemoved) {
      try {
        if (patientFixture) await patientFixture.cleanup({browserClosed});
        else {
          // An older upgrade baseline (deb-docker-validation.sh upgrade <tag>) has no rx_submission_receipt
          // table yet; it is the only table that may be absent (see presentTables in owned-patient.js). A
          // catalogue that cannot be read decides nothing, and the database then answers for the table.
          const lacksReceipts = () => {
            try {
              return sql.value('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() '
                + "AND table_name='rx_submission_receipt'") === '0';
            } catch (unreadable) { return false; }
          };
          const supportRows = [
            ...(lacksReceipts() ? [] : [['rx_submission_receipt', 'demographic_no']]),
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
        }
      } catch (error) { failures.push(error); }
    }
  }
  try { if (sql) sql.dispose(); } catch (error) { failures.push(error); }
  h.assert(!failures.length, failures.map(error => error.message).join('; '));
}

async function expectValue(sql, query, expected, message, parameters) {
  // Database reads are synchronous; bounded polling covers asynchronous UI saves.
  // `parameters`, when given, are bound to the query's placeholders (createSqlRunner).
  const deadline = Date.now() + 15000;
  do {
    if (sql.value(query, parameters) === expected) return;
    await new Promise(resolve => setTimeout(resolve, 150));
  } while (Date.now() < deadline);
  h.assert(false, message);
}

module.exports = { runWorkflow, expectValue, cleanupOwnedWorkflow };
