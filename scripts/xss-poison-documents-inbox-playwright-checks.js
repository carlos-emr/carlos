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
const { payload, inspect, Findings, Seeder, openMasterByChartNo, fieldIds } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 400: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 400;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  const chartNo = `XD${hex}`;
  let doc;
  // Every provider-owned row belongs to the provider the session logged in as, so the Inbox and the eDoc list
  // it opens are the ones that list them.
  const me = s.provider;
  await s.step('seed the poisoned patient and document', async () => {
    const demo = seedPatient(seed, P, me, chartNo);
    doc = seed.insert('document', { doctype: 'consult', docdesc: P('document description'), docfilename: 'xp.pdf', doccreator: me, responsible: me,
      source: P('document source', 60), sourceFacility: P('document source facility', 120), updatedatetime: { raw: 'NOW()' }, status: 'A', contenttype: 'application/pdf',
      contentdatetime: { raw: 'NOW()' }, public1: 0, observationdate: { raw: 'CURDATE()' }, number_of_pages: 1, restrictToProgram: 0, abnormal: 0, reviewer: '' }, { key: 'document_no' });
    seed.insert('ctl_document', { module: 'demographic', module_id: Number(demo), document_no: Number(doc), status: 'A' }, { where: `module='demographic' AND module_id=${demo} AND document_no=${doc}` });
    seed.insert('providerLabRouting', { provider_no: me, lab_no: Number(doc), status: 'N', lab_type: 'DOC' }, { key: 'id' });
    seed.insert('patientLabRouting', { demographic_no: Number(demo), lab_no: Number(doc), lab_type: 'DOC', created: { raw: 'NOW()' }, dateModified: { raw: 'NOW()' } }, { key: 'id' });
    // A second document filed under the logged-in provider, so the eDoc provider list (Schedule > eDoc) has one to show.
    const own = seed.insert('document', { doctype: P('provider document type', 60), docdesc: P('provider document description'), docfilename: 'xp.pdf', doccreator: me, responsible: me,
      source: P('provider document source', 60), sourceFacility: '', updatedatetime: { raw: 'NOW()' }, status: 'A', contenttype: 'application/pdf',
      contentdatetime: { raw: 'NOW()' }, public1: 0, observationdate: { raw: 'CURDATE()' }, number_of_pages: 1, restrictToProgram: 0, abnormal: 0, reviewer: '' }, { key: 'document_no' });
    seed.insert('ctl_document', { module: 'provider', module_id: Number(me), document_no: Number(own), status: 'A' }, { where: `module='provider' AND module_id=${Number(me)} AND document_no=${own}` });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  f.ignoredPaths.push('/documentManager/ManageDocument');
  // What each surface is known to show (live run on the packaged install); a surface that stops showing them is
  // a MISSING finding, so an empty or misrouted list cannot pass as an encoded one.
  const patientName = fieldIds(fields, 'patient last name', 'patient first name');
  const expected = {
    Inbox: patientName,
    'eDoc list': fieldIds(fields, 'provider document type', 'provider document description'),
    list: [...patientName, ...fieldIds(fields, 'document description')],
    edit: fieldIds(fields, 'document description', 'document source', 'document source facility'),
  };
  const topBar = async (selector, label) => ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator(selector).first(),
    { context: s.context, label, recorder: s.recorder, timeout: 20000 });
  const dayUrl = s.schedule.url();
  await step('Inbox and the eDoc provider list show stored values as text', async () => {
    for (const [selector, label] of [['a[onclick*="Inboxhub"]', 'Inbox'], ['a[onclick*="ViewDocumentReport"]', 'eDoc list']]) {
      const { page, isPopup } = await topBar(selector, label);
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(800);
      const since = f.mark();
      await inspect(f, label, page, fields, since, { expect: expected[label] });
      if (isPopup) await page.close(); else await s.schedule.goto(dayUrl);
    }
  });
  await step('Master Record > Documents and the document Edit popup show stored values as text', async () => {
    const { master } = await openMasterByChartNo(s, chartNo);
    const link = master.locator('a').filter({ hasText: /^\s*Documents\s*$/i }).first();
    const { page, isPopup } = await ui.clickOpensPopupOrNavigates(master, link, { context: s.context, label: 'master-documents', recorder: s.recorder, timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'patient document list', page, fields, since, { expect: expected.list });
    const edit = page.locator(`a[onclick*="ViewEditDocument?editDocumentNo=${doc}"]`).first();
    if (await edit.count()) {
      const popup = await ui.clickOpensPopup(page, edit, { context: s.context, label: 'document-edit', recorder: s.recorder, timeout: 20000 });
      since = f.mark();
      await inspect(f, 'document edit popup', popup, fields, since, { expect: expected.edit });
      await popup.close();
    } else f.missing('document edit popup', 'the seeded document offers no Edit control');
    if (isPopup) await page.close();
    // Teardown proof, not best effort: a lock this run took must be released before the browser closes.
    await releaseChartLocks(s.context, s.config.baseUrl, [master]);
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Documents and Inbox walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-documents-inbox', workflow, { openPatient: false });
