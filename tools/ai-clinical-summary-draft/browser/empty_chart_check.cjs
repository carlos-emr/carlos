/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Measure proposals on an isolated empty synthetic chart. Never approve entries.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { readConfig, createRecorder, launchBrowser, newContext, login, createSqlRunner, withExpectedDialogs } =
  require('../../../scripts/lib/playwright-harness');
const { openMasterRecord } = require('../../../scripts/master-record-tabs-playwright-checks');
const { openChart } = require('../../../scripts/echart-navbar-modules-playwright-checks');

(async () => {
  const config = readConfig({ require: ['CHART_EMPTY_FIXTURE', 'CHART_TEST_OUTPUT', 'MYSQL_PASSWORD'] });
  assert.match(config.mysql.database, /^carlos_chartupdates_[a-zA-Z0-9_]+$/);
  const fixture = JSON.parse(fs.readFileSync(process.env.CHART_EMPTY_FIXTURE, 'utf8'));
  const patient = fixture.demographicId;
  assert(Number.isSafeInteger(patient) && patient > 0);
  assert.equal(fixture.chartNumber, 'AIFACT005');
  assert.equal(fixture.documents?.length, 2, 'Both empty-chart attachments are required');
  const output = path.resolve(process.env.CHART_TEST_OUTPUT);
  fs.mkdirSync(output, { recursive: true });
  const sql = createSqlRunner(config.mysql);
  try {
  const counts = () => Object.fromEntries(
    ['casemgmt_note', 'tickler', 'drugs', 'allergies', 'clinical_chart_update_receipt'].map(table =>
      [table, Number(sql.value(`SELECT COUNT(*) FROM ${table} WHERE demographic_no=${patient}`))]));
  assert.equal(sql.value(`SELECT chart_no FROM demographic WHERE demographic_no=${patient}`), fixture.chartNumber);
  const before = counts();
  assert(Object.values(before).every(count => count === 0), 'Requires an empty test chart');
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const results = [];
  let chart, csrf;
  try {
    const context = await newContext(browser, config);
    const schedule = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: 'FAKE-EMPTY-CHART', preferredDemographicNo: patient, timeout: 60000,
    });
    chart = await openChart(context, masterPage, recorder, 60000);
    const chartUrl = chart.url();
    const tabs = context.pages().length;
    await chart.getByRole('link', { name: 'Review chart updates', exact: true }).click();
    const frame = await (await chart.locator('#chart-update-workflow-frame').elementHandle()).contentFrame();
    await frame.locator('#chart-update-picker-title').waitFor();
    for (const document of fixture.documents) {
      assert(Number.isSafeInteger(document.documentId) && document.documentId > 0);
      const source = fs.readFileSync(document.sourceFile, 'utf8');
      assert.equal(crypto.createHash('sha256').update(source).digest('hex'), document.sha256);
      const link = frame.locator(`a.chart-update-document-link[href$="documentId=${document.documentId}"]`);
      const original = await chart.request.get(new URL(await link.getAttribute('data-original-url'), frame.url()).href);
      assert.equal(original.status(), 200);
      assert.equal(await original.text(), source);
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }), link.click()]);
      assert.equal(await frame.locator('#chart-update-source').textContent(), source);
      assert.equal(await frame.locator('.chart-entry').count(), 0);
      csrf = await frame.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const started = Date.now();
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 1800000 }),
        frame.getByRole('button', { name: 'Generate new proposals', exact: true }).click({ timeout: 1800000 })]);
      const error = await frame.locator('.alert-danger').count()
        ? await frame.locator('.alert-danger').innerText() : null;
      const cards = frame.locator('article.proposal');
      const count = await cards.count();
      // A rejected generation is not a valid zero-proposal model result.
      const row = { documentId: document.documentId, words: document.words,
        sha256: document.sha256, elapsedMs: Date.now() - started,
        status: error ? 'generation-unavailable' : 'generated',
        proposals: error ? null : count, error };
      if (!error) {
        await frame.locator('.review-progress').waitFor();
        row.history = await cards.filter({ has: frame.locator('[name="destination"]') }).count();
        row.reminders = await cards.filter({ has: frame.locator('[name="dueDate"]') }).count();
        row.nativeReviews = await cards.locator('[name="entryText"][readonly]').count();
        row.agentLabel = await frame.locator('main > p.small').innerText();
        row.defaults = [];
        let checkedNativeForm = false;
        assert.equal(row.history + row.reminders + row.nativeReviews, count);
        for (let index = 0; index < count; index++) {
          const quote = await frame.locator('article.proposal:visible .proposal-evidence blockquote').textContent();
          assert(source.includes(quote));
          assert((await frame.locator('.source-highlight').allTextContents()).includes(quote));
          assert.equal(await frame.locator('#chart-update-source').textContent(), source);
          const card = frame.locator('article.proposal:visible');
          const related = card.locator('.related-proposal-notice');
          const hasRelated = await related.count() ? await related.isVisible() : false;
          if (await card.locator('[name="dueDate"]').count()) {
            const dueDate = await card.locator('[name="dueDate"]').inputValue();
            row.defaults.push({ kind: 'tickler', dueDate, relatedSuggestion: hasRelated,
              assignee: await card.locator('[name="assignee"]').inputValue() });
            const days = /in 2 weeks/i.test(quote) ? 14 : /in 7 days/i.test(quote) ? 7 : null;
            if (days !== null) {
              const expected = new Date(`${document.date}T00:00:00Z`);
              expected.setUTCDate(expected.getUTCDate() + days);
              assert.equal(dueDate, expected.toISOString().slice(0, 10));
            }
          } else if (await card.locator('[name="destination"]').count()) {
            row.defaults.push({ kind: 'history', relatedSuggestion: hasRelated,
              destination: await card.locator('[name="destination"]').inputValue() });
          } else {
            const destination = await card.getAttribute('data-destination');
            row.defaults.push({ kind: 'review', destination, relatedSuggestion: hasRelated });
            assert.equal(await card.locator('[name="confirmed"]').count(), 0);
            assert.equal(await card.getByRole('button', { name: 'Accept and save', exact: true }).count(), 0);
            if (destination === 'Demographics' && !checkedNativeForm) {
              assert.equal(await card.locator('.native-review-open').count(), 1, 'Demographics review must offer its normal form');
              await card.locator('.native-review-open').click();
              const dialog = frame.locator('#native-chart-review');
              const native = await (await dialog.locator('iframe').elementHandle()).contentFrame();
              await native.waitForURL(url => url.pathname.includes('/demographic/'), { waitUntil: 'domcontentloaded' });
              assert.equal(new URL(native.url()).searchParams.get('demographic_no'), String(patient));
              await native.locator('input[name="demographic_no"]').first().waitFor({ state: 'attached' });
              assert.equal(await native.locator('input[name="demographic_no"]').first().inputValue(), String(patient));
              assert.equal(await dialog.locator('.native-review-source').textContent(), quote);
              const closed = await withExpectedDialogs(chart, () => dialog.getByRole('button', { name: 'Close', exact: true }).click());
              assert.equal(closed.length, 1);
              assert.equal(closed[0].type, 'confirm');
              await dialog.waitFor({ state: 'hidden' });
              checkedNativeForm = true;
              row.demographicFormPatientChecked = true;
            }
            if (['Medications', 'Allergies'].includes(destination)) {
              assert.equal(await card.locator('.native-review-open').count(), 0);
              assert.match(await card.innerText(), /Closing this suggestion does not save a record/);
            }
          }
          if (index + 1 < count) await frame.getByRole('button', { name: 'Next', exact: true }).click();
        }
      }
      assert.equal(await frame.locator('[name="confirmed"]:checked').count(), 0);
      assert.deepEqual(counts(), before);
      await chart.screenshot({ path: path.join(output, `empty-chart-document-${document.documentId}.png`) });
      results.push(row);
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        frame.getByRole('link', { name: 'Back', exact: true }).click()]);
      await frame.locator('#chart-update-picker-title').waitFor();
    }
    await chart.getByRole('dialog', { name: 'Review chart updates', exact: true })
      .getByRole('button', { name: 'Close', exact: true }).click();
    assert.equal(context.pages().length, tabs);
    assert.equal(chart.url(), chartUrl);
  } finally {
    try {
      if (chart && csrf) {
        const noteId = sql.value(`SELECT note_id FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no='999998' ORDER BY id DESC LIMIT 1`);
        if (noteId) {
          assert.match(noteId, /^\d+$/);
          const response = await chart.request.post(`${config.baseUrl}/CaseManagementEntry`, {
            form: { method: 'releaseNoteLock', demographicNo: String(patient), noteId, 'CSRF-TOKEN': csrf },
          });
          assert.equal(response.status(), 200);
        }
        assert.equal(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}`), '0');
      }
      assert.deepEqual(counts(), before);
      const report = { chartNumber: fixture.chartNumber, demographicId: patient,
        baselineCounts: before, afterCounts: counts(), results };
      fs.writeFileSync(path.join(output, 'empty-chart-result.json'), JSON.stringify(report, null, 2) + '\n');
      console.log(JSON.stringify(report, null, 2));
      if (results.some(row => row.status !== 'generated')) process.exitCode = 2;
    } finally { await browser.close(); }
  }
  } finally { sql.dispose(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
