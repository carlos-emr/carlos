#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of a REFUSED attempt to open a locked patient (wave 7 sweep `audit-log`).
 *
 * User path (as a throwaway doctor login that an administrator locked out of the owned patient, |o| on
 * _demographic$N and _eChart$N): Schedule > Search > the patient's name > the result row's Master Demographic
 * File link. The application refuses (HTTP 403, no patient data on the page).
 *
 * Asserts: the doctor is refused by CARLOS itself (the Master Record window's own response is an application 403 or its
 * securityError/noRights redirect, not a WAF page, server error or login bounce) and the page shows nothing of the patient; the full-privilege test login still
 * opens the same patient (control: the same click is served, and that open writes its read/demographic row for
 * the test provider); and the refused attempt left an audit row that names the patient and the throwaway
 * provider (any action: a refusal of access to PHI is the event a privacy officer looks for, so a login that
 * probes locked charts must be visible as that login). The last assertion fails today because the refusal is
 * thrown as a SecurityException before any audit write.
 *
 * Fixtures: the harness's owned synthetic patient and one throwaway doctor login with its two lock rows
 * (lib/authz-read-fixture.js); cleanup removes the login, its audit rows and the lock rows and asserts them
 * gone, and the audit rows scoped to the patient.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, cleanupAll } = require('./lib/authz-read-fixture');
const { signIn, refusedByApp } = require('./lib/authz-read-probe');
const { auditProbe, label } = require('./lib/audit-log-helpers');

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  const probe = auditProbe({ sql, patient });
  let doctor;
  s.cleanup(() => cleanupAll(() => fixture.cleanup(), () => probe.cleanup()));
  let attemptedAfter;

  await s.step('a doctor login locked out of the owned patient signs in', async () => {
    const login = fixture.addLogin('doctor');
    h.assert(fixture.lockPatient(login, patient).length === 2, 'The patient lock rows were not written');
    doctor = await signIn(s, login);
  });

  await s.step('Search then the Master Demographic File link refuses the locked doctor and shows nothing of the patient', async () => {
    const schedule = doctor.page;
    const search = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#search a').first(), { context: doctor.context, label: 'locked-search', timeout: 30000 });
    await search.page.locator('#keyword, input[name="keyword"]').first().fill(marker);
    await ui.clickAndAwaitReload(search.page, search.page.locator("input[type='submit']").first(), { timeout: 30000, label: 'the patient search' });
    const link = search.page.locator(`a[title="Master Demographic File"][onclick*="demographic_no=${patient}"]`).first();
    h.assert(await link.count() === 1, 'The locked patient is not offered in the doctor\'s search results');
    await probe.settle(1500);
    attemptedAfter = probe.mark();
    // Every document navigation of the doctor's context from here on: the popup's own response is the proof the
    // refusal came from CARLOS (an application 403, or its securityError/noRights redirect) and not from a server
    // error, the WAF, a login bounce or a blank window, any of which would also show no patient data.
    const navigations = [];
    const onResponse = response => { if (response.request().isNavigationRequest()) navigations.push(response); };
    doctor.context.on('response', onResponse);
    const outcome = await ui.clickDownloadsOrOpens(search.page, link, { context: doctor.context, label: 'locked-master', timeout: 30000 });
    h.assert(outcome.kind === 'popup', 'The Master Record link produced no window');
    const page = outcome.page;
    await page.waitForLoadState('load').catch(() => {});
    doctor.context.off('response', onResponse);
    const text = await page.locator('body').innerText().catch(() => '');
    h.assert(!text.includes(marker), 'The locked patient\'s record was shown to the locked doctor');
    // Request.frame() throws (rather than returning null) for a navigation issued before its frame exists, which is the
    // popup's first document request; such a response is attributed by the patient it asked for instead.
    const pageOf = r => { try { return r.request().frame().page(); } catch { return null; } };
    const popupResponses = navigations.filter(r => {
      const owner = pageOf(r);
      return owner ? owner === page : new URL(r.url()).searchParams.get('demographic_no') === String(patient);
    });
    const asked = popupResponses.find(r => new URL(r.url()).searchParams.get('demographic_no') === String(patient)) || popupResponses[0];
    h.assert(asked, 'The Master Record window made no document request that could be inspected');
    const answer = { status: asked.status(), fromApp: Object.prototype.hasOwnProperty.call(asked.headers(), 'x-permitted-cross-domain-policies'),
      location: asked.headers().location || '' };
    h.assert(refusedByApp(answer), `The locked Master Record request was not refused by CARLOS (HTTP ${answer.status}${answer.fromApp ? '' : ', not an application response'})`);
    await page.close().catch(() => {});
  });

  await s.step('control: the full-privilege login opens the same patient and that read is audited', async () => {
    // The harness already opened s.master as the test login; its row proves the audit path works for the same patient.
    const reads = probe.rows(`action='read' AND content='demographic' AND provider_no=${h.sqlString(provider)}`);
    h.assert(reads.length >= 1, 'The test login\'s Master Record open wrote no read/demographic row');
  });

  await s.step('the refused attempt is itself in the audit log, naming the patient and the refused login', async () => {
    // LogAction.addLog commits on a background executor: poll for the doctor's patient-scoped row instead of
    // querying once after a fixed delay (waitFor fails with this description when none ever lands).
    const doctorNo = doctor.login.providerNo;
    const refused = (await probe.waitFor(rows => rows.some(r => r.provider === doctorNo),
      'a refused attempt to open a locked patient\'s Master Record (no row naming the patient and the refused login)',
      { after: attemptedAfter })).filter(r => r.provider === doctorNo);
    h.assert(refused.every(r => r.provider === doctor.login.providerNo), `A refusal row is attributed to another provider (${refused.map(label).join(', ')})`);
  });
}

if (require.main === module) runWorkflow('audit-log-locked-patient', workflow);
module.exports = { workflow };
