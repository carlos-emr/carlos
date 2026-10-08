#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Print-output regression check for the Administration reports (issue #3342):
 * Visit Report, PCN, PHCP Encounter Report and Ontario billing reconciliation.
 *
 * Each report is opened the way an administrator reaches it (the Administration
 * panel's own menu), then inspected twice:
 *
 *   screen media  the filters, Print control, pagination and side navigation are
 *                 all still there and the Print control calls window.print();
 *   print media   the same controls and the application chrome are gone, the
 *                 report title, criteria and result table remain, the content
 *                 pane spans the page (no blank strip where the side column
 *                 was), and the result table's header row is a repeating
 *                 table-header-group with every data row in <tbody>.
 *
 * It also renders each report to PDF with the browser's print pipeline and, when
 * pdftotext is installed, asserts the PDF text carries the report content and
 * none of the control labels -- the artifact a user actually gets.
 *
 * READ-ONLY. Reports are read-only queries; nothing is seeded or cleaned up, so
 * no database access is needed.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:report-print-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   REPORT_PRINT_TIMEOUT_MS=20000     per-step allowance
 *   REPORT_PRINT_ARTIFACT_DIR=/tmp    keep the rendered PDFs here (default: discarded)
 *   REPORT_PRINT_KEEP_GOING=true      report every failing report, not only the first
 */

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const {
  appUrl, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');

// Result tables are found by markup that predates the print work, so this check
// can also be run against a build without it and fail on the print assertions
// rather than on a missing class.
const RESULT_TABLE = {
  visit: '#dynamic-content table.table',
  pcn: '#dynamic-content table.table',
  phcp: 'table[border="1"]',
  reconciliation: 'table.table',
};
const TIMEOUT = Number(process.env.REPORT_PRINT_TIMEOUT_MS || '20000');
const artifactDir = process.env.REPORT_PRINT_ARTIFACT_DIR || '';

/** Click a left-navigation entry, first opening the accordion section that holds it. */
async function clickMenu(admin, selector) {
  const link = admin.locator(selector).first();
  assert(await link.count() > 0, `The Administration menu has no entry matching ${selector}`);
  const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (await panel.count()) {
    const id = await panel.getAttribute('id');
    assert(id, 'Administration accordion panel has no id');
    // A section that is still collapsing hides its links; wait it out first.
    await admin.waitForFunction(panelId => {
      const element = document.getElementById(panelId);
      return element && !element.classList.contains('collapsing');
    }, id);
    if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${id}"]`).click();
  }
  await link.click();
}

/** Every control must be in the given visibility state; all violations are reported together. */
async function assertVisibility(scope, expectations, state, label) {
  const wrong = [];
  for (const [name, locator] of Object.entries(expectations)) {
    const visible = await scope(locator).first().isVisible().catch(() => false);
    if (visible !== state) wrong.push(name);
  }
  assert(!wrong.length, `${label}: should be ${state ? 'visible' : 'hidden'}: ${wrong.join(', ')}`);
}

/**
 * The content pane (#dynamic-content) must start at the left edge and span the
 * page; if the side column is only hidden, the pane keeps its 9/12 width and
 * leaves a blank strip down the left of every printed page.
 */
async function assertContentPaneSpansPage(admin, label) {
  const geometry = await admin.evaluate(() => {
    const pane = document.getElementById('dynamic-content');
    const box = pane.getBoundingClientRect();
    return { left: box.left, width: box.width, viewport: document.documentElement.clientWidth };
  });
  assert(geometry.left < 40, `${label}: content pane starts ${geometry.left}px from the edge, leaving a blank strip`);
  assert(geometry.width >= geometry.viewport * 0.9,
    `${label}: content pane is ${geometry.width}px of ${geometry.viewport}px, so the page is not used fully`);
}

/** The result table prints its header row on every page and splits no row. */
async function assertPrintableTable(scope, selector, label, { requireRows = true } = {}) {
  const info = await scope(selector).first().evaluate(table => {
    const head = table.tHead;
    const bodies = [...table.tBodies];
    return {
      headDisplay: head ? getComputedStyle(head).display : null,
      headRows: head ? head.rows.length : 0,
      bodyRows: bodies.reduce((sum, body) => sum + body.rows.length, 0),
      rowBreak: table.rows.length ? getComputedStyle(table.rows[table.rows.length - 1]).breakInside : null,
    };
  });
  assert(info.headDisplay === 'table-header-group', `${label}: header does not repeat across pages (display=${info.headDisplay})`);
  assert(info.headRows >= 1, `${label}: table has no header row`);
  // A repeating header is a title row or two; a whole report in <thead> would
  // be reprinted on every page (the Visit Report's markup did exactly that).
  assert(info.headRows <= 2, `${label}: ${info.headRows} rows are inside <thead> and would repeat on every page`);
  assert(!requireRows || info.bodyRows >= 1, `${label}: table has no <tbody> data rows`);
  assert(info.rowBreak === 'avoid', `${label}: a table row may split across a page break (break-inside=${info.rowBreak})`);
  return info;
}

/** window.print() must be what the on-page Print control calls. */
async function assertPrintControlPrints(frameOrPage, locator, label) {
  await frameOrPage.evaluate(() => { window.__printCalls = 0; window.print = () => { window.__printCalls += 1; }; });
  await locator.click();
  const calls = await frameOrPage.evaluate(() => window.__printCalls);
  assert(calls === 1, `${label}: the Print control called window.print() ${calls} time(s)`);
}

function pdfText(pdf, name) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'report-print-'));
  const file = path.join(dir, `${name}.pdf`);
  fs.writeFileSync(file, pdf);
  assert(pdf.subarray(0, 5).toString('latin1') === '%PDF-', `${name}: print output is not a PDF`);
  if (artifactDir) {
    fs.mkdirSync(artifactDir, { recursive: true });
    fs.copyFileSync(file, path.join(artifactDir, `${name}.pdf`));
  }
  const probe = spawnSync('pdftotext', ['-layout', file, '-'], { encoding: 'utf8' });
  fs.rmSync(dir, { recursive: true, force: true });
  if (probe.error || probe.status !== 0) {
    console.log(`  NOTE ${name}: pdftotext unavailable; PDF text assertions skipped`);
    return null;
  }
  return probe.stdout;
}

async function assertPdf(admin, name, { present, absent, repeatsOnEveryPage = null }) {
  const text = pdfText(await admin.pdf({ format: 'A4', printBackground: true, preferCSSPageSize: true }), name);
  if (text === null) return;
  if (repeatsOnEveryPage) {
    // pdftotext separates pages with a form feed.
    const pages = text.split('\f').filter(page => page.trim());
    console.log(`  NOTE ${name}: printed over ${pages.length} page(s)`);
    pages.forEach((page, index) => assert(page.includes(repeatsOnEveryPage),
      `${name}: page ${index + 1} of ${pages.length} lacks the repeated header "${repeatsOnEveryPage}"`));
  }
  for (const needle of present) assert(text.includes(needle), `${name}: printed PDF lacks "${needle}"`);
  for (const needle of absent) assert(!text.includes(needle), `${name}: printed PDF still carries "${needle}"`);
}

/**
 * The Administration shell's report frame. contentFrame() is null while the
 * frame is navigating (after a form submit), so poll until it is available.
 */
async function reportFrame(admin) {
  const deadline = Date.now() + TIMEOUT;
  for (;;) {
    const handle = await admin.locator('#myFrame').elementHandle();
    const frame = handle && await handle.contentFrame();
    if (frame) return frame;
    assert(Date.now() < deadline, 'The report frame (#myFrame) never became available');
    await new Promise(resolve => setTimeout(resolve, 200));
  }
}

/** Run `body` with print media emulated; screen media is always restored. */
async function inPrintMedia(admin, body) {
  await admin.emulateMedia({ media: 'print' });
  try { return await body(); } finally { await admin.emulateMedia({ media: null }); }
}

const KEEP_GOING = process.env.REPORT_PRINT_KEEP_GOING === 'true';
const failures = [];

async function step(label, recorder, body) {
  try {
    await body();
    assertStrictPage(recorder);
  } catch (error) {
    // REPORT_PRINT_KEEP_GOING lists every failing report in one run, which is
    // how a build without the print work is compared with one that has it.
    if (!KEEP_GOING) throw error;
    failures.push(`${label}: ${error.message}`);
    console.log(`  FAIL report-print: ${label}: ${error.message}`);
    return;
  }
  console.log(`  PASS report-print: ${label}`);
}

async function main() {
  const config = readConfig();
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    context.setDefaultTimeout(TIMEOUT);
    const schedule = await login(context, config, recorder);
    const opener = schedule.locator('#admin-panel, #admin2').first();
    assert(await opener.count() > 0, 'The schedule offers no Administration control');
    const { page: admin } = await clickOpensPopupOrNavigates(schedule, opener, {
      context, label: 'administration', recorder, timeout: TIMEOUT,
    });
    await admin.setViewportSize({ width: 1280, height: 900 });
    const sideNav = l => l('#side');

    await step('Visit Report prints only the report', recorder, async () => {
      await clickMenu(admin, 'a.defaultvisitreport');
      await admin.locator('#visitForm').waitFor({ state: 'visible' });
      await admin.locator('#xml_vdate').fill('2020-01-01');
      await admin.locator('#xml_appointment_date').fill('2026-12-31');
      // The date pickers open a calendar over the form; dismiss it before submitting.
      await admin.keyboard.press('Escape');
      await admin.locator('h3').first().click();
      await admin.locator('#visitForm button[type="submit"]').click();
      await admin.locator(RESULT_TABLE.visit).waitFor({ state: 'visible' });

      const controls = {
        'Print button': 'button[name="print"]',
        'Manage Visit Report Providers': 'a:has-text("Manage Visit Report")',
        'report-type radios': '#reportActionVr',
        'provider selector': '#providerview',
        'date inputs': '#xml_vdate',
        'Create Report': '#visitForm button[type="submit"]',
        'side navigation': '#side',
        'schedule menu': '#firstTable',
      };
      await assertVisibility(l => admin.locator(l), controls, true, 'Visit Report on screen');
      await assertPrintControlPrints(admin, admin.locator('button[name="print"]'), 'Visit Report');

      await inPrintMedia(admin, async () => {
        await assertVisibility(l => admin.locator(l), controls, false, 'Visit Report in print');
        await assertContentPaneSpansPage(admin, 'Visit Report in print');
        await assertVisibility(l => admin.locator(l), {
          'report title': 'h3',
          'printed criteria': '#visitReportPrintCriteria',
          'result table': RESULT_TABLE.visit,
        }, true, 'Visit Report in print');
        const criteria = await admin.locator('#visitReportPrintCriteria').innerText();
        assert(criteria.includes('2020-01-01') && criteria.includes('2026-12-31'),
          `Visit Report print criteria lack the selected dates: ${criteria}`);
        await assertPrintableTable(l => admin.locator(l), RESULT_TABLE.visit, 'Visit Report');
        await assertPdf(admin, 'visit-report', {
          present: ['2020-01-01', '2026-12-31', 'TOTAL'],
          absent: ['Create Report', 'Manage Visit Report', 'Select Report'],
        });
      });
      // Screen is restored and the criteria summary is screen-hidden.
      assert(!(await admin.locator('#visitReportPrintCriteria').isVisible()), 'Visit Report print criteria leaked onto the screen');
      await assertVisibility(l => admin.locator(l), controls, true, 'Visit Report back on screen');
    });

    await step('PCN prints only the report', recorder, async () => {
      await clickMenu(admin, 'a[href$="/oscarReport/ViewOscarReportCatchment"]');
      // Wait on the pagination, which only PCN has: the previous panel's table would satisfy RESULT_TABLE at once.
      await admin.locator('ul.pagination').waitFor({ state: 'visible' });
      const controls = {
        'Print button': 'button[name="print"]',
        'Previous Page': 'text=Previous Page',
        'Next Page': 'ul.pagination li:nth-child(2) a',
        'side navigation': '#side',
        'schedule menu': '#firstTable',
      };
      await assertVisibility(l => admin.locator(l), controls, true, 'PCN on screen');
      await assertPrintControlPrints(admin, admin.locator('button[name="print"]'), 'PCN');
      await inPrintMedia(admin, async () => {
        await assertVisibility(l => admin.locator(l), controls, false, 'PCN in print');
        await assertContentPaneSpansPage(admin, 'PCN in print');
        await assertVisibility(l => admin.locator(l), {
          'report title': 'h4',
          'result table': RESULT_TABLE.pcn,
        }, true, 'PCN in print');
        // The heading used to be the Visit Report's (shared bundle key).
        const heading = (await admin.locator('#dynamic-content h4').first().innerText()).trim();
        assert(heading === 'PCN', `PCN printed under the title "${heading}"`);
        await assertPrintableTable(l => admin.locator(l), RESULT_TABLE.pcn, 'PCN');
        await assertPdf(admin, 'pcn', {
          present: ['Sex'],
          absent: ['Previous Page', 'Next Page'],
          repeatsOnEveryPage: 'Demographic',
        });
      });
      await assertVisibility(l => admin.locator(l), controls, true, 'PCN back on screen');
    });

    await step('PHCP Encounter Report prints only the report', recorder, async () => {
      await clickMenu(admin, '.xlink[rel$="/report/ViewReportonbilledphcp"]');
      const frame = await reportFrame(admin);
      await frame.locator('form[name="myform"]').waitFor({ state: 'visible' });
      // The calendar widgets own the date inputs; set them directly.
      await frame.evaluate(() => {
        const form = document.forms.myform;
        form.codeType.selectedIndex = [...form.codeType.options].findIndex(o => o.value !== '');
        form.startDate.value = '2020-01-01';
        form.endDate.value = '2026-12-31';
        const doctor = form.providerNoDoctor;
        doctor.selectedIndex = [...doctor.options].findIndex(o => o.value !== '');
      });
      const doctorChosen = await frame.evaluate(() => document.forms.myform.providerNoDoctor.value !== '');
      assert(doctorChosen, 'The PHCP provider list offers no doctor to report on');
      await frame.locator('input[name="submit"]').click();
      const resultFrame = await reportFrame(admin);
      await resultFrame.locator(RESULT_TABLE.phcp).waitFor({ state: 'visible' });

      const controls = {
        'filter form': 'form[name="myform"]',
        'code type': 'select[name="codeType"]',
        'Go': 'input[name="submit"]',
        'Print button': 'input[type="button"][name="Button"]',
      };
      const scope = l => resultFrame.locator(l);
      await assertVisibility(scope, controls, true, 'PHCP on screen');
      await inPrintMedia(admin, async () => {
        await assertVisibility(scope, controls, false, 'PHCP in print');
        await assertVisibility(scope, { 'result table': RESULT_TABLE.phcp }, true, 'PHCP in print');
        await assertPrintableTable(scope, RESULT_TABLE.phcp, 'PHCP');
        const margin = await resultFrame.evaluate(() => getComputedStyle(document.body).marginLeft);
        assert(margin === '0px', `PHCP body margin should be 0 so @page controls the page edge, got ${margin}`);
        await assertPdf(admin, 'phcp', { present: ['2020-01-01'], absent: ['Exit'] });
      });
      await assertVisibility(scope, controls, true, 'PHCP back on screen');
    });

    // The same two routes opened on their own, outside the shell: no Bootstrap
    // (so d-print-none is inert) and no shell stylesheet. The report's own
    // stylesheet has to hide its controls without help.
    await step('Visit Report and PCN print cleanly when opened outside the shell', recorder, async () => {
      for (const [name, route, controls] of [
        ['Visit Report', '/oscarReport/ViewOscarReportVisitControl', {
          'Print button': 'button[name="print"]',
          'Manage Visit Report Providers': 'a:has-text("Manage Visit Report")',
          'report-type radios': '#reportActionVr',
          'provider selector': '#providerview',
          'Create Report': '#visitForm button[type="submit"]',
        }],
        ['PCN', '/oscarReport/ViewOscarReportCatchment', {
          'Print button': 'button[name="print"]',
          'Previous Page': 'text=Previous Page',
          'Next Page': 'ul.pagination li:nth-child(2) a',
        }],
      ]) {
        const standalone = await context.newPage();
        // These fragments take jQuery from the shell, so opened alone they throw
        // "$ is not defined" from their own inline script. That predates this
        // work and is not what is under test, so it goes to a recorder nothing
        // asserts on rather than failing the check.
        wireStrictPage(standalone, `standalone-${name}`, createRecorder());
        try {
          await standalone.goto(appUrl(config.baseUrl, route), { waitUntil: 'domcontentloaded' });
          await standalone.locator('h3, h4').first().waitFor({ state: 'visible' });
          await assertVisibility(l => standalone.locator(l), controls, true, `${name} standalone on screen`);
          await inPrintMedia(standalone, () =>
            assertVisibility(l => standalone.locator(l), controls, false, `${name} standalone in print`));
        } finally {
          await standalone.close();
        }
      }
    });

    const reconciliation = admin.locator('.xlink[rel*="GenRA"]').first();
    if (await reconciliation.count() === 0) {
      console.log('  NOTE report-print: no Ontario billing reconciliation entry (non-ON build); step skipped');
    } else {
      await step('Billing reconciliation prints without its Print button', recorder, async () => {
        await clickMenu(admin, '.xlink[rel*="GenRA"]');
        const frame = await reportFrame(admin);
        await frame.locator('button[name="print"]').waitFor({ state: 'visible' });
        const scope = l => frame.locator(l);
        await assertVisibility(scope, { 'Print button': 'button[name="print"]' }, true, 'Reconciliation on screen');
        await assertPrintControlPrints(frame, frame.locator('button[name="print"]'), 'Billing reconciliation');
        await inPrintMedia(admin, async () => {
          await assertVisibility(scope, { 'Print button': 'button[name="print"]' }, false, 'Reconciliation in print');
          await assertVisibility(scope, { 'result table': RESULT_TABLE.reconciliation }, true, 'Reconciliation in print');
          // Row actions (Error/Summary/Report, Settle/S35) are workflow controls, not
          // report content. With no RA rows this is vacuously true; seed an RA row
          // in raheader to exercise it.
          const rowControls = await scope('table.table tbody a').evaluateAll(links =>
            links.filter(link => link.getClientRects().length > 0).map(link => link.textContent.trim()));
          assert(!rowControls.length, `Reconciliation prints row controls: ${rowControls.join(', ')}`);
          // The demo dataset has no RA files, so the table may legitimately be empty.
          await assertPrintableTable(scope, RESULT_TABLE.reconciliation, 'Billing reconciliation', { requireRows: false });
          await assertPdf(admin, 'billing-reconciliation', { present: ['Payment Date'], absent: ['Print'] });
        });
        await assertVisibility(scope, { 'Print button': 'button[name="print"]' }, true, 'Reconciliation back on screen');

        // The page's own Print button prints the framed document by itself, not the
        // shell around it, so its table header repeats on every page it spans. (A
        // browser print of the whole shell lays the frame out as one tall box, so
        // a long reconciliation prints completely there but without repeated
        // headers.) Vacuous on a one-page list; verified with 150 seeded RA rows.
        const alone = await context.newPage();
        wireStrictPage(alone, 'standalone-reconciliation', createRecorder());
        try {
          await alone.goto(appUrl(config.baseUrl, '/billing/CA/ON/ViewOnGenRA'), { waitUntil: 'domcontentloaded' });
          await alone.locator('button[name="print"]').waitFor({ state: 'visible' });
          await assertPdf(alone, 'billing-reconciliation-own-page', {
            present: ['Payment Date'], absent: ['Print'], repeatsOnEveryPage: 'Payment Date',
          });
        } finally {
          await alone.close();
        }
      });
    }
    assert(await sideNav(l => admin.locator(l)).first().isVisible(), 'Side navigation did not return to the screen layout');
    assert(!failures.length, `${failures.length} report(s) print incorrectly:\n${failures.join('\n')}`);
    assertStrictPage(recorder);
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'report-print', run: main });
}

module.exports = { main };
