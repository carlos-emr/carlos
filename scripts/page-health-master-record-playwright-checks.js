#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the patient Master Record, one click deep.
 *
 * User path: login > Schedule > Search > FAKE- > patient row (demo patient, read-only) >
 * every link on the Master Record (Appt. History, Billing History, Invoice List,
 * Consultations, Prescriptions, E-Chart, Prevention, Tickler, Documents, eForms, Manage
 * Contacts, Enrollment History, the print/label menu, ...).
 *
 * master-record-tabs proves each one opens cleanly. This check judges the destinations
 * with lib/page-health-engine.js: no literal "null" / "undefined" / "NaN" / "???key???" /
 * "[object Object]" / unresolved ${...} / double-encoded entity in visible text or
 * pre-filled fields, no off-host or mixed-content request (aborted and recorded), the
 * front door's security headers once each on every document, and the click does not end
 * on the login page.
 *
 * READ-ONLY, no fixtures: it uses an existing demo patient and submits nothing. Chart
 * locks taken by the E-Chart link are released as master-record-tabs does.
 *
 * Implements: wave-7 `page-health` sweep (Master Record).
 */

const h = require('./lib/playwright-harness');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { closeBrowserWithChartCleanup, releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { assertHealthy, beginEntry, crawl, judgePage, startSession } = require('./lib/page-health-engine');
const { SKIP_ITEMS, openMasterRecord } = require('./master-record-tabs-playwright-checks');

// Master Record links demographic/edit.jsp renders for the test login on every province (Billing History and Invoice
// List are province-specific, so they are left to the opened-count floor). Each must be opened and judged: a floor
// alone lets a named link disappear or be skipped while eight others pass.
const EXPECTED_LINKS = ['Appointment History', 'Consultations', 'Prescriptions', 'E-Chart', 'Preventions', 'Tickler'];

// Every clinical/billing sidebar popup must keep the displayed patient's scope.
const PATIENT_PARAMETERS = [
  ['/billing/CA/ON/ViewBillingONHistory', 'demographic_no'],
  ['/billing/CA/ON/billingShortcutPg1View', 'demographic_no'],
  ['/billing/CA/BC/reprocessBill', 'demographicNo'],
  ['/billing', 'demographic_no'],
  ['/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests', 'de'],
  ['/rx/choosePatient', 'demographicNo'],
  ['/encounter/IncomingEncounter', 'demographicNo'],
  ['/prevention/ViewPreventionIndex', 'demographic_no'],
  ['/tickler/ViewTicklerMain', 'demoview'],
  ['/form/forwardshortcutname', 'demographic_no'],
  ['/mod/docmgmtComp/DocList', 'demographic_no'],
  ['/documentManager/ViewDocumentReport', 'functionid'],
  ['/documentManager/ViewDocumentBrowser', 'functionid'],
];

function assertPopupScope(links, hostUrl) {
  const host = new URL(hostUrl);
  const patient = host.searchParams.get('demographic_no');
  h.assert(patient && /^[1-9][0-9]*$/.test(patient), 'The Master Record has no valid patient scope');
  h.assert(links.length >= 8, 'The Master Record lost its clinical and billing popup links');
  for (const link of links) {
    h.assert(link.href && !/^(?:#|javascript:)/i.test(link.href),
      `The Master Record popup link has no usable destination: ${link.label}`);
    const target = new URL(link.url);
    h.assert(target.origin === host.origin, `The Master Record popup link points off host: ${link.label}`);
    const route = PATIENT_PARAMETERS.find(([suffix]) => target.pathname.endsWith(suffix));
    h.assert(route, `The Master Record popup route needs a patient-scope assertion: ${link.label}`);
    const values = target.searchParams.getAll(route[1]);
    h.assert(values.length === 1 && values[0] === patient,
      `The Master Record popup link lost or changed patient scope: ${link.label}`);
  }
}

async function assertPopupDestinations(page) {
  const links = await page.locator('.MainTableLeftColumn a[onclick]').evaluateAll(elements => elements.map(link => ({
    href: link.getAttribute('href'), url: link.href, label: link.textContent.trim(),
  })));
  assertPopupScope(links, page.url());
}

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    // Search and the Master Record landing are judged with the crawl (recorder and off-host requests).
    const entry = beginEntry(session);
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: process.env.MASTER_RECORD_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.MASTER_RECORD_DEMOGRAPHIC_NO || '2',
      timeout,
    });
    await assertPopupDestinations(masterPage);
    await judgePage(session.ledger, session.probe, 'master-record (landing)', masterPage, {});
    const items = dedupe(await catalogueLinks(masterPage));
    h.assert(items.length > 0, 'The Master Record offered no navigable links at all');
    const result = await crawl({
      context: session.context, hostPage: masterPage, items, recorder: session.recorder, probe: session.probe,
      labelPrefix: 'master-record', timeout, entry: { window: entry, label: 'master-record (entry)' }, skipRules: SKIP_ITEMS, ledger: session.ledger,
      beforePopupClose: page => releaseChartLocks(session.context, config.baseUrl, [page]),
    });
    console.log(`  opened ${result.opened.length} Master Record link(s), skipped ${result.skipped}`);
    for (const name of EXPECTED_LINKS) {
      if (!result.opened.includes(name)) result.failures.push(`master-record:${name}: NOT-OPENED (missing from the Master Record or skipped by the crawl)`);
    }
    assertHealthy(result, { surface: 'the Master Record', minimumOpened: 8 });
  } finally {
    await closeBrowserWithChartCleanup(session.browser, config.baseUrl);
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-master-record', run: main });
}

module.exports = { main, assertPopupScope, assertPopupDestinations };
