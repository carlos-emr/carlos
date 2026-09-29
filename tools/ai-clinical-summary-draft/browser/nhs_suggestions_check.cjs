/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Verify suggested fields against the fixed three-patient gateway without approving chart entries.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { readConfig, createRecorder, launchBrowser, newContext, login, createSqlRunner } =
  require('../../../scripts/lib/playwright-harness');
const { openMasterRecord } = require('../../../scripts/master-record-tabs-playwright-checks');
const { openChart } = require('../../../scripts/echart-navbar-modules-playwright-checks');

(async () => {
  const config = readConfig({ require: ['CHART_TEST_FIXTURES', 'CHART_TEST_OUTPUT', 'MYSQL_PASSWORD'] });
  assert.match(config.mysql.database, /^carlos_chartupdates_[a-zA-Z0-9_]+$/);
  const fixtures = JSON.parse(fs.readFileSync(process.env.CHART_TEST_FIXTURES, 'utf8'));
  assert.deepEqual(fixtures.map(f => f.fixture), ['NHSSYN001', 'NHSSYN002', 'NHSSYN003']);
  const output = path.resolve(process.env.CHART_TEST_OUTPUT);
  fs.mkdirSync(output, { recursive: true });
  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const results = [];
  try {
    const context = await newContext(browser, config);
    const schedule = await login(context, config, recorder);
    for (const [index, fixture] of fixtures.entries()) {
      const { demographicId: patient, documentId: doc } = fixture;
      assert(Number.isSafeInteger(patient) && patient > 0 && Number.isSafeInteger(doc) && doc > 0);
      assert.equal(sql.value(`SELECT chart_no FROM demographic WHERE demographic_no=${patient}`), fixture.fixture);
      const count = () => Number(sql.value(`SELECT COUNT(*) FROM clinical_chart_update_receipt WHERE demographic_no=${patient} AND document_no=${doc}`));
      const before = count();
      const { masterPage } = await openMasterRecord(context, schedule, recorder, {
        searchTerm: sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`),
        preferredDemographicNo: patient, timeout: 60000,
      });
      const chart = await openChart(context, masterPage, recorder, 60000);
      await chart.waitForLoadState('domcontentloaded');
      const page = await context.newPage();
      assert.equal((await page.goto(`${config.baseUrl}/documentManager/AiChartUpdates?documentId=${doc}`)).status(), 200);
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        page.getByRole('button', { name: 'Generate new proposals', exact: true }).click()]);
      assert.equal(await page.locator('article.proposal').count(), 3);
      assert.match(await page.locator('main').innerText(), /Fixed NHS proposals - no model/);
      const reminder = page.locator('article').filter({ has: page.locator('[name="dueDate"]') });
      const date = sql.value(`SELECT DATE(observationdate) FROM document WHERE document_no=${doc}`);
      let expected = '';
      if (index < 2) {
        const due = new Date(`${date}T00:00:00Z`);
        due.setUTCDate(due.getUTCDate() + (index === 0 ? 28 : 1));
        expected = due.toISOString().slice(0, 10);
        assert.match(await reminder.locator('.field-help').first().innerText(), new RegExp(date));
      }
      assert.equal(await reminder.locator('[name="dueDate"]').inputValue(), expected);
      assert.equal(await reminder.locator('[name="assignee"]').inputValue(), '999998');
      for (const field of await page.locator('[name="destination"]').all()) assert.equal(await field.inputValue(), 'Concerns');
      assert.equal(await page.locator('[name="confirmed"]:checked').count(), 0);
      assert.equal(count(), before);
      await page.screenshot({ path: path.join(output, `${fixture.fixture}-suggestions.png`), fullPage: true });
      await page.setViewportSize({ width: 390, height: 844 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      const csrf = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const noteId = sql.value(`SELECT note_id FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no='999998' ORDER BY id DESC LIMIT 1`);
      assert.match(noteId, /^\d+$/);
      const release = await page.request.post(`${config.baseUrl}/CaseManagementEntry`, {
        form: { method: 'releaseNoteLock', demographicNo: String(patient), noteId, 'CSRF-TOKEN': csrf },
      });
      assert.equal(release.status(), 200);
      assert.equal(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no='999998'`), '0');
      results.push({ fixture: fixture.fixture, documentDate: date, suggestedDate: expected, approvalsUnchanged: true });
      console.log(`${fixture.fixture}: suggested date, assignee, destinations, explicit approval and mobile layout passed`);
      for (const candidate of context.pages()) if (candidate !== schedule) await candidate.close();
    }
    fs.writeFileSync(path.join(output, 'suggestions-result.json'), JSON.stringify(results, null, 2) + '\n');
  } finally { await browser.close(); sql.dispose(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
