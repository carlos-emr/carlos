#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: manual lab entry "Submit to EMR" (a duplicate lab result is clinically significant).
 *
 * User path: Schedule > Inbox > Create Lab (oscarMDS/ViewCreateLab) > fill the CML lab with one test > Submit to
 * EMR (confirm()). For each rapid activation (dblclick(), two back-to-back clicks, double Enter in the
 * accession field, slow-response re-click) the check files ONE lab under its own accession number and asserts
 * EXACTLY ONE hl7TextInfo row for that accession.
 *
 * Fixtures: the owned FAKE- patient and the labs filed (unique accessions); cleanup removes every owned lab's
 * routing, measurement, message and checksum rows and archived file (removeOwnedHl7Labs) and asserts it.
 * Wave-6 pattern sweep "double-submit".
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { removeOwnedHl7Labs } = require('./lab-forwarding-rules-playwright-checks');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, marker } = s;
  const accessions = [];
  s.cleanup(() => {
    const labs = [];
    for (const accession of accessions) {
      labs.push(...sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`).map(([id]) => id));
    }
    removeOwnedHl7Labs(sql, labs);
    for (const accession of accessions) {
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '0', 'An owned lab was not removed');
    }
  });
  const v = verdicts('lab-manual-entry');

  for (const mode of MODES) {
    await s.step(`Submit to EMR via ${mode.label} files at most one lab`, async () => {
      const accession = `DS${crypto.randomBytes(4).toString('hex').toUpperCase()}`;
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '0', 'The accession is already in use');
      accessions.push(accession);
      const { page: inbox, opened } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
        { context: s.context, recorder: s.recorder, label: 'manual-lab-inbox', timeout: 30000 });
      const form = await s.popup(inbox, inbox.locator('a[href*="oscarMDS/ViewCreateLab"]').first(), 'manual-lab-create');
      await form.locator('#labname').selectOption('CML');
      await form.locator('#accession').fill(accession);
      await form.locator('#lab_req_date').fill('2026-09-30 08:30');
      await form.locator('#lastname').fill(marker);
      await form.locator('#firstname').fill('Workflow');
      await form.locator('#sex').selectOption('F');
      await form.locator('#dob').fill('1980-01-02');
      await form.getByRole('link', { name: 'Add Test' }).click();
      const field = (name) => form.locator(`[id="test_1.${name}"]`);
      await field('valDate').waitFor({ state: 'visible', timeout: 30000 });
      await field('valDate').fill('2026-09-30 09:00');
      await field('code').fill('2010');
      await field('lab_test_name').fill('HEMOGLOBIN');
      await field('codeVal').fill('137');
      await field('codeUnit').fill('g/L');
      await field('refRangeLow').fill('120');
      await field('refRangeHigh').fill('160');
      await field('flag').selectOption('N');
      const route = /\/oscarMDS\/SubmitLab$/;
      const posts = watchPosts(form.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
      const dialogs = await h.withExpectedDialogs(form, async () => {
        await rapid(mode.key, form.locator('form[name="testForm"] button[type="submit"]'), { textField: form.locator('#accession') });
        await sleep(1500);
      });
      const count = await settledCount(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`,
        { min: mode.key === 'doubleEnter' ? 0 : 1, quietMs: 3500 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${dialogs.length} confirm dialog(s); ${posts.seen.length} SubmitLab POST(s))`);
      v.record(mode.label, count, mode.key === 'doubleEnter' ? { atMost: 1 } : { exactly: 1 });
      if (!form.isClosed()) await form.close().catch(() => {});
      if (opened && !inbox.isClosed()) await inbox.close().catch(() => {});
      if (!opened) await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-lab-manual', workflow, { openPatient: true, openMaster: false });
