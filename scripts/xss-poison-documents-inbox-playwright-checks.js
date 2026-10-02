#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the document screens and the Inbox.
// User path: Schedule > Inbox (list); Schedule > eDoc (provider document list); Master Record > Documents,
// then the document's Edit popup.
// Fixtures: an owned FAKE patient and one document (description, source, facility, type) linked to the
// patient and routed to the test provider's inbox; every text column carries inert markup (INSERTed,
// bypassing the WAF). The document has no file behind it, so its preview answers 500 (fixture limit; that
// one resource response is ignored). Cleanup removes exactly these rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, openMasterByChartNo } = require('./lib/xss-poison-helpers');
const { seedPatient, purgeSupport } = require('./lib/xss-poison-patient');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

async function workflow(s) {
  const fields = {};
  let n = 0;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup);
  const hex = s.marker.slice(-8);
  const chartNo = `XD${hex.slice(0, 6)}`;
  let doc;
  await s.step('seed the poisoned patient and document', async () => {
    const demo = seedPatient(seed, P, '999998', chartNo);
    s.cleanup(() => purgeSupport(s.sql, demo));
    doc = seed.insert('document', { doctype: 'consult', docdesc: P('document description'), docfilename: 'xp.pdf', doccreator: '999998', responsible: '999998',
      source: P('document source', 60), sourceFacility: P('document source facility', 120), updatedatetime: { raw: 'NOW()' }, status: 'A', contenttype: 'application/pdf',
      contentdatetime: { raw: 'NOW()' }, public1: 0, observationdate: { raw: 'CURDATE()' }, number_of_pages: 1, restrictToProgram: 0, abnormal: 0, reviewer: '' }, { key: 'document_no' });
    seed.insert('ctl_document', { module: 'demographic', module_id: Number(demo), document_no: Number(doc), status: 'A' }, { where: `module='demographic' AND module_id=${demo} AND document_no=${doc}` });
    seed.insert('providerLabRouting', { provider_no: '999998', lab_no: Number(doc), status: 'N', lab_type: 'DOC' }, { key: 'id' });
    seed.insert('patientLabRouting', { demographic_no: Number(demo), lab_no: Number(doc), lab_type: 'DOC', created: { raw: 'NOW()' }, dateModified: { raw: 'NOW()' } }, { key: 'id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  f.ignoredPaths.push('/documentManager/ManageDocument');
  const topBar = async (selector, label) => ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator(selector).first(),
    { context: s.context, label, recorder: s.recorder, timeout: 20000 });
  const dayUrl = s.schedule.url();
  await step('Inbox and the eDoc provider list show stored values as text', async () => {
    for (const [selector, label] of [['a[onclick*="Inboxhub"]', 'Inbox'], ['a[onclick*="ViewDocumentReport"]', 'eDoc list']]) {
      const { page, isPopup } = await topBar(selector, label);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(800);
      const since = f.mark();
      await inspect(f, label, page, fields, since);
      if (isPopup) await page.close(); else await s.schedule.goto(dayUrl);
    }
  });
  await step('Master Record > Documents and the document Edit popup show stored values as text', async () => {
    const { master } = await openMasterByChartNo(s, chartNo);
    const link = master.locator('a').filter({ hasText: /^\s*Documents\s*$/i }).first();
    const { page, isPopup } = await ui.clickOpensPopupOrNavigates(master, link, { context: s.context, label: 'master-documents', recorder: s.recorder, timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'patient document list', page, fields, since);
    const edit = page.locator(`a[onclick*="ViewEditDocument?editDocumentNo=${doc}"]`).first();
    if (await edit.count()) {
      const popup = await ui.clickOpensPopup(page, edit, { context: s.context, label: 'document-edit', recorder: s.recorder, timeout: 20000 });
      since = f.mark();
      await inspect(f, 'document edit popup', popup, fields, since);
      await popup.close();
    } else f.note('document edit popup', 'edit control not offered');
    if (isPopup) await page.close();
    await releaseChartLocks(s.context, s.config.baseUrl, [master]).catch(() => {});
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Documents and Inbox walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-documents-inbox', workflow, { openPatient: false });
