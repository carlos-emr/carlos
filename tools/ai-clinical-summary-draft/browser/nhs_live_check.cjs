/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Uses a real isolated CARLOS deployment and database. The configured gateway may
// be a model or nhs_fixture_gateway.py; record which one in CHART_TEST_AGENT.
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const assert = require('node:assert/strict');
const { readConfig, createRecorder, launchBrowser, newContext, login, createSqlRunner, wireStrictPage } =
  require('../../../scripts/lib/playwright-harness');
const { openMasterRecord } = require('../../../scripts/master-record-tabs-playwright-checks');
const { openChart } = require('../../../scripts/echart-navbar-modules-playwright-checks');

(async () => {
  const config = readConfig({ require: ['CHART_TEST_FIXTURES', 'CHART_TEST_OUTPUT', 'CHART_TEST_AGENT', 'MYSQL_PASSWORD'] });
  assert.match(config.mysql.database, /^carlos_chartupdates_[a-zA-Z0-9_]+$/, 'Use an isolated chart-update database copy');
  const fixtures = JSON.parse(fs.readFileSync(process.env.CHART_TEST_FIXTURES, 'utf8'));
  assert.equal(fixtures.length, 3);
  assert.deepEqual(fixtures.map(f => f.fixture), ['NHSSYN001', 'NHSSYN002', 'NHSSYN003']);
  const output = path.resolve(process.env.CHART_TEST_OUTPUT);
  fs.mkdirSync(output, { recursive: true });
  const sql = createSqlRunner(config.mysql);
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const results = { agent: process.env.CHART_TEST_AGENT, patients: [], passed: false };
  let page;
  try {
    for (const fixture of fixtures) {
      assert(Number.isSafeInteger(fixture.demographicId) && fixture.demographicId > 0);
      assert(Number.isSafeInteger(fixture.documentId) && fixture.documentId > 0);
      assert.equal(sql.value(`SELECT chart_no FROM demographic WHERE demographic_no=${fixture.demographicId}`), fixture.fixture);
    }
    const context = await newContext(browser, config);
    // A previous browser run may leave this same test user's eChart lock.
    // Accept only CARLOS's explicit same-user takeover prompt on this isolated copy.
    context.on('page', candidate => wireStrictPage(candidate, 'nhs-test', recorder, {
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
    console.log('Authenticated through CARLOS login');
    for (const fixture of fixtures) {
      const { demographicId: patient, documentId: doc } = fixture;
      const count = () => Number(sql.value(`SELECT COUNT(*) FROM clinical_chart_update_receipt WHERE demographic_no=${patient} AND document_no=${doc}`));
      const auditCount = () => Number(sql.value(`SELECT COUNT(*) FROM log WHERE action='ChartUpdates.read' AND data='documentId=${doc},demographicNo=${patient}'`));
      const auditsBefore = auditCount();
      const receiptsBefore = count();
      assert(receiptsBefore >= 0 && receiptsBefore <= 2, 'Unexpected prior approvals in the isolated test fixture');
      const { masterPage } = await openMasterRecord(context, schedule, recorder,
        { searchTerm: sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`), preferredDemographicNo: patient, timeout: 60000 });
      assert.equal(new URL(masterPage.url()).searchParams.get('demographic_no'), String(patient));
      const chart = await openChart(context, masterPage, recorder, 60000);
      await chart.waitForLoadState('domcontentloaded');
      page = await context.newPage();
      const preview = `${config.baseUrl}/documentManager/AiDocumentSummary?documentId=${doc}`;
      assert.equal((await page.goto(preview)).status(), 200);
      const reviewResponse = page.waitForNavigation({ waitUntil: 'domcontentloaded' });
      await page.getByRole('link', { name: 'Review chart updates', exact: true }).click();
      assert.equal((await reviewResponse).status(), 200, 'Review route must return success');
      await page.waitForLoadState('domcontentloaded');
      assert.equal(await page.locator('.alert-danger').count(), 0, 'Review preview must load');
      assert.equal(await page.locator('form[action$="GenerateAiChartUpdates"]').count(), 1, 'Review must offer proposal generation');
      assert(auditCount() > auditsBefore, 'Opening the review must persist its access audit');
      const details = { fixture: fixture.fixture, documentId: doc, checks: ['login', 'search', 'eChart', 'summary review link', 'access audit persisted'] };
      const missingCsrf = await page.request.post(`${config.baseUrl}/documentManager/GenerateAiChartUpdates`,
        { form: { documentId: String(doc) } });
      assert.equal(missingCsrf.status(), 403, 'Missing CSRF token must reject generation');
      const getMutation = await page.request.get(`${config.baseUrl}/documentManager/ApplyAiChartUpdate?documentId=${doc}`);
      assert.equal(getMutation.status(), 405, 'GET must never apply an update');
      // An inaccessible document exercises the real Struts SecurityException mapping.
      const forbidden = await page.request.get(`${config.baseUrl}/documentManager/AiChartUpdates?documentId=99999999`);
      assert.equal(forbidden.status(), 403, 'Document access failures must use the controlled forbidden response');
      assert(!(await forbidden.text()).includes('class="proposal-form"'), 'Forbidden response must not render the review');
      assert.equal(count(), receiptsBefore);
      details.checks.push('real CSRF rejection', 'GET mutation rejection', 'controlled 403 access refusal');
      const generate = page.getByRole('button', { name: /Generate/ });
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 650000 }), generate.click()]);
      assert.equal(await page.locator('.alert-danger').count(), 0, 'Proposal generation must succeed');
      assert.equal(new URL(page.url()).pathname, new URL(config.baseUrl).pathname.replace(/\/$/, '') + '/documentManager/AiChartUpdates', 'Generation must redirect to GET');
      const proposals = page.locator('article.proposal');
      assert(await proposals.count() >= 3, 'This walkthrough expects at least three source-backed proposals');
      assert.equal(count(), receiptsBefore, 'Generation must not write chart updates');
      details.proposals = await proposals.count();
      details.existingReceipts = receiptsBefore;
      const source = fs.readFileSync(fixture.sourceFile, 'utf8');
      assert.equal(createHash('sha256').update(source).digest('hex'), fixture.sourceSha256);
      for (const evidence of await proposals.locator('blockquote').allTextContents()) assert(source.includes(evidence));
      details.checks.push('generation without writes', 'exact source evidence');
      const csrf = page.locator('input[name="CSRF-TOKEN"]').first();
      const firstKey = await proposals.locator('input[name="proposalKey"]').first().inputValue();
      const forgedReview = await page.request.post(`${config.baseUrl}/documentManager/DismissAiChartUpdate`,
        { form: { documentId: String(doc), 'CSRF-TOKEN': await csrf.inputValue(), reviewToken: 'forged', proposalKey: firstKey } });
      assert((await forgedReview.text()).includes('This review expired or was replaced'), 'Forged review token must be rejected');
      assert.equal(count(), receiptsBefore);
      details.checks.push('review token rejection');
      await page.screenshot({ path: path.join(output, `${fixture.fixture}-review.png`), fullPage: true });
      const reminder = proposals.filter({ has: page.locator('input[name="dueDate"]') }).first();
      assert.equal(await reminder.count(), 1);
      const historyDraft = proposals.filter({ has: page.locator('select[name="destination"]') }).first();
      const editedHistory = 'Synthetic workflow check: ' + await historyDraft.locator('textarea').inputValue();
      await historyDraft.locator('textarea').fill(editedHistory);
      await historyDraft.locator('select[name="destination"]').selectOption('MedHistory');
      await historyDraft.locator('input[name="confirmed"]').check();
      await reminder.locator('input[name="dueDate"]').fill('2026-10-05');
      await reminder.locator('select[name="assignee"]').selectOption('999998');
      const edited = 'Synthetic workflow check: ' + await reminder.locator('textarea').inputValue();
      await reminder.locator('textarea').fill(edited);
      await reminder.locator('input[name="confirmed"]').check();
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), reminder.getByRole('button', { name: /Accept/ }).click()]);
      assert.equal(await page.locator('.alert-danger').count(), 0, 'Reminder save must succeed');
      assert.equal(count(), Math.max(1, receiptsBefore));
      const tickler = sql.rows(`SELECT t.message,t.task_assigned_to,t.service_date FROM tickler t JOIN clinical_chart_update_receipt r ON r.target_id=t.tickler_no AND r.kind='tickler' WHERE r.demographic_no=${patient} AND r.document_no=${doc}`);
      assert.equal(tickler.length, 1);
      assert(tickler[0][0].includes(edited));
      assert(tickler[0][0].includes('Reviewed source passage:'));
      assert.equal(tickler[0][1], '999998');
      assert(tickler[0][2].startsWith('2026-10-05'));
      assert.equal(Number(sql.value(`SELECT COUNT(*) FROM tickler_link l JOIN clinical_chart_update_receipt r ON r.target_id=l.tickler_no AND r.kind='tickler' WHERE r.demographic_no=${patient} AND r.document_no=${doc} AND l.table_name='DOC' AND l.table_id=${doc}`)), 1);
      details.checks.push('edited reminder persisted', 'reminder source link');
      const history = proposals.filter({ has: page.locator('select[name="destination"]') }).first();
      assert.equal(await history.locator('textarea').inputValue(), editedHistory, 'Saving a reminder must preserve other edits');
      assert.equal(await history.locator('select[name="destination"]').inputValue(), 'MedHistory');
      assert.equal(await history.locator('input[name="confirmed"]').isChecked(), false, 'Approval must be renewed after refresh');
      await page.reload();
      assert.equal(await history.locator('textarea').inputValue(), editedHistory);
      assert.equal(count(), Math.max(1, receiptsBefore), 'Refreshing must not repeat a save');
      details.checks.push('other edits retained', 'approval reset', 'refresh-safe GET');
      await history.locator('select[name="destination"]').selectOption('MedHistory');
      await history.locator('input[name="confirmed"]').check();
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), history.getByRole('button', { name: /Accept/ }).click()]);
      assert.equal(await page.locator('.alert-danger').count(), 0, 'History save must succeed');
      assert.equal(count(), 2);
      const note = sql.rows(`SELECT n.signed,n.signing_provider_no,n.note FROM casemgmt_note n JOIN clinical_chart_update_receipt r ON r.target_id=n.note_id AND r.kind='history' WHERE r.demographic_no=${patient} AND r.document_no=${doc}`);
      assert.equal(note.length, 1);
      assert.equal(note[0][0], '1');
      assert.equal(note[0][1], '999998');
      assert(note[0][2].includes('Reviewed source passage:'));
      assert.equal(Number(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link l JOIN clinical_chart_update_receipt r ON r.target_id=l.note_id AND r.kind='history' WHERE r.demographic_no=${patient} AND r.document_no=${doc} AND l.table_name=5 AND l.table_id=${doc}`)), 1);
      details.checks.push('signed history persisted', 'history source link');
      await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), proposals.getByRole('button', { name: /Dismiss/ }).first().click()]);
      assert.equal(count(), 2, 'Dismissal must not write');
      details.checks.push('dismissal without writes');
      if (receiptsBefore === 2) details.checks.push('durable replay without duplicates');
      await page.setViewportSize({ width: 390, height: 844 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: path.join(output, `${fixture.fixture}-saved-mobile.png`), fullPage: true });
      details.checks.push('mobile layout');
      results.patients.push(details);
      console.log(`${fixture.fixture}: ${details.checks.length} checks passed`);
      // CARLOS reuses named Search/E-Chart windows. Close the search popup too,
      // so the next patient's opener produces a new, observable window.
      for (const candidate of context.pages()) {
        if (candidate !== schedule) await candidate.close();
      }
    }
    results.passed = true;
  } catch (error) {
    results.error = error.message;
    if (page && !page.isClosed()) {
      await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
      fs.writeFileSync(path.join(output, 'failure.html'), await page.content());
    }
    throw error;
  } finally {
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(results, null, 2) + '\n');
    await browser.close();
    sql.dispose();
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
