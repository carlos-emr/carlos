/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Verify suggested fields against the fixed three-patient gateway without approving chart entries.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { readConfig, createRecorder, launchBrowser, newContext, login, createSqlRunner, wireStrictPage, assertStrictPage } =
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
  try {
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const results = [];
  try {
    const context = await newContext(browser, config);
    // A failed earlier browser run can leave this test account's lock in the
    // isolated copy. Use CARLOS's explicit same-user takeover flow only.
    context.on('page', candidate => wireStrictPage(candidate, 'nhs-suggestions', recorder, {
      dialogHandler: async dialog => {
        if (dialog.type() === 'confirm' && dialog.message().startsWith('You have started to edit this note in another window at ')) {
          await dialog.accept();
        } else {
          recorder.unexpectedDialogs.push({ type: dialog.type(), text: dialog.message() });
          await dialog.dismiss();
        }
      },
    }));
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
      await chart.getByRole('link', { name: 'Review chart updates', exact: true }).waitFor();
      await chart.screenshot({ path: path.join(output, `${fixture.fixture}-echart-entry.png`) });
      const chartUrl = chart.url();
      const pageCount = context.pages().length;
      await chart.getByRole('link', { name: 'Review chart updates', exact: true }).click();
      await chart.frameLocator('#chart-update-workflow-frame').locator('#chart-update-picker-title').waitFor();
      const page = await (await chart.locator('#chart-update-workflow-frame').elementHandle()).contentFrame();
      assert.equal(context.pages().length, pageCount);
      assert.equal(new URL(page.url()).searchParams.get('functionid'), String(patient));
      await page.locator('#chart-update-picker-title').waitFor();
      // When a fixture list contains an unavailable older document, verify the actual
      // failure path too. The isolated browser harness always covers this case.
      let unavailableModalChecked = false;
      if (index === 0) {
        for (const candidate of await page.locator('a.chart-update-document-link').all()) {
          if ((await candidate.getAttribute('href')).endsWith(`documentId=${doc}`)) continue;
          const availability = await chart.request.get(new URL(await candidate.getAttribute('href'), page.url()).href, { headers: { Accept: 'application/json' } });
          if (!availability.ok() || !availability.headers()['content-type']?.includes('application/json')) continue;
          const state = await availability.json();
          if (state.available !== false) continue;
          const listUrl = page.url();
          await candidate.click();
          const modal = page.getByRole('dialog', { name: 'Document review unavailable' });
          await modal.waitFor();
          assert.equal(page.url(), listUrl);
          assert.equal(await modal.locator('.chart-update-error-document').innerText(), await candidate.getAttribute('data-document-title'));
          assert.equal(await modal.locator('.chart-update-original').isVisible(), state.originalAvailable === true);
          if (state.originalAvailable === false) {
            assert.match(await modal.innerText(), /original document file is missing/);
            const original = await chart.request.get(new URL(await candidate.getAttribute('data-original-url'), page.url()).href);
            assert.equal(original.status(), 404);
          }
          await chart.screenshot({ path: path.join(output, 'unavailable-document-modal.png'), fullPage: true });
          await modal.getByRole('button', { name: 'Close', exact: true }).click();
          assert.equal(await candidate.evaluate(el => el === document.activeElement), true);
          unavailableModalChecked = true;
          console.log('Unavailable document: modal retained the patient document list and restored focus');
          break;
        }
      }
      const reviewLink = page.locator(`a.chart-update-document-link[href$="documentId=${doc}"]`);
      assert.equal(await reviewLink.count(), 1);
      const original = await chart.request.get(new URL(await reviewLink.getAttribute('data-original-url'), page.url()).href);
      assert.equal(original.status(), 200);
      assert.equal(await original.text(), fs.readFileSync(fixture.sourceFile, 'utf8'));
      assert.equal(original.headers()['content-type'].split(';')[0], 'text/plain');
      await chart.screenshot({ path: path.join(output, `${fixture.fixture}-document-picker.png`), fullPage: true });
      const [reviewResponse] = await Promise.all([
        page.waitForNavigation({ waitUntil: 'domcontentloaded' }), reviewLink.click(),
      ]);
      assert.equal(reviewResponse.status(), 200);
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        page.getByRole('button', { name: 'Generate new proposals', exact: true }).click()]);
      assert.equal(await page.locator('article.proposal').count(), 3);
      assert.equal(await page.locator('article.proposal:visible').count(), 1);
      const fullSource = await page.locator('#chart-update-source').textContent();
      assert.equal(fullSource, fs.readFileSync(fixture.sourceFile, 'utf8'));
      for (let step = 0; step < 3; step++) {
        const passage = await page.locator('article.proposal:visible .proposal-evidence blockquote').textContent();
        assert((await page.locator('.source-highlight').allTextContents()).includes(passage));
        assert.equal(await page.locator('#chart-update-source').textContent(), fullSource);
        if (step < 2) await page.getByRole('button', { name: 'Next', exact: true }).click();
      }
      await page.getByRole('button', { name: 'Previous', exact: true }).click();
      await page.getByRole('button', { name: 'Previous', exact: true }).click();
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
      await chart.screenshot({ path: path.join(output, `${fixture.fixture}-suggestions.png`), fullPage: true });
      await chart.setViewportSize({ width: 390, height: 844 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      const csrf = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const [backResponse] = await Promise.all([
        page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        page.getByRole('link', { name: 'Back', exact: true }).click(),
      ]);
      assert.equal(backResponse.status(), 200);
      assert.equal(new URL(page.url()).searchParams.get('functionid'), String(patient));
      await page.locator('#chart-update-picker-title').waitFor();
      assert.equal(await page.locator(`a.chart-update-document-link[href$="documentId=${doc}"]`).count(), 1);
      await chart.getByRole('dialog', { name: 'Review chart updates', exact: true }).getByRole('button', { name: 'Close', exact: true }).click();
      assert.equal(chart.url(), chartUrl);
      assert.equal(context.pages().length, pageCount);
      assert.equal(await chart.getByRole('link', { name: 'Review chart updates', exact: true }).evaluate(el => el === document.activeElement), true);
      const noteId = sql.value(`SELECT note_id FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no='999998' ORDER BY id DESC LIMIT 1`);
      assert.match(noteId, /^\d+$/);
      const release = await chart.request.post(`${config.baseUrl}/CaseManagementEntry`, {
        form: { method: 'releaseNoteLock', demographicNo: String(patient), noteId, 'CSRF-TOKEN': csrf },
      });
      assert.equal(release.status(), 200);
      assert.equal(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no='999998'`), '0');
      assertStrictPage(recorder);
      results.push({ fixture: fixture.fixture, documentDate: date, suggestedDate: expected, approvalsUnchanged: true, unavailableModalChecked });
      console.log(`${fixture.fixture}: eChart navigation, document selection, suggested fields, explicit approval, mobile layout, source highlighting, modal steps, Back and Close navigation passed`);
      for (const candidate of context.pages()) if (candidate !== schedule) await candidate.close();
    }
    fs.writeFileSync(path.join(output, 'suggestions-result.json'), JSON.stringify(results, null, 2) + '\n');
  } finally { await browser.close(); }
  } finally { sql.dispose(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
