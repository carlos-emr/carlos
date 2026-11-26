#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the document screens and the Inbox.
// User path: Schedule > Inbox (list); Schedule > eDoc (provider document list); Master Record > Documents,
// then the document's Edit popup (typing in its sub-class field to open the suggestions); the document
// information fragment the Document Browser and Browse Notes load (ManageDocument?method=viewDocumentInfo);
// the two document upload pages (Inbox > Upload: the modern ViewDocumentUploader, and the multi-file
// ViewHtml5AddDocuments the Inbox offers when legacy_document_upload_enabled is on).
// Fixtures: an owned FAKE patient and one document (description, source, facility, type) linked to the
// patient and routed to the test provider's inbox with a routing comment; a document class whose report
// class and sub-class feed every document page's class select and sub-class autocomplete; a document queue.
// Every text column carries inert markup (INSERTed, bypassing the WAF). The class and the queue are
// GLOBALLY VISIBLE while the check runs. The document has no file behind it, so its preview answers 500
// (fixture limit; that one resource response is ignored). Cleanup removes exactly these rows by key and
// asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error (a stored
// sub-class printed raw into the autocomplete array breaks that inline script); findings are collected for
// the whole walk and the check fails once at the end. Issue #3057 added the sub-class, queue, routing
// comment, upload-page and document-information coverage.
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
    // The routing comment is printed by the document-information fragment's acknowledgement line.
    seed.insert('providerLabRouting', { provider_no: me, lab_no: Number(doc), status: 'N', lab_type: 'DOC', comment: P('document acknowledgement comment') }, { key: 'id' });
    seed.insert('patientLabRouting', { demographic_no: Number(demo), lab_no: Number(doc), lab_type: 'DOC', created: { raw: 'NOW()' }, dateModified: { raw: 'NOW()' } }, { key: 'id' });
    // A second document filed under the logged-in provider, so the eDoc provider list (Schedule > eDoc) has one to show.
    const own = seed.insert('document', { doctype: P('provider document type', 60), docdesc: P('provider document description'), docfilename: 'xp.pdf', doccreator: me, responsible: me,
      source: P('provider document source', 60), sourceFacility: '', updatedatetime: { raw: 'NOW()' }, status: 'A', contenttype: 'application/pdf',
      contentdatetime: { raw: 'NOW()' }, public1: 0, observationdate: { raw: 'CURDATE()' }, number_of_pages: 1, restrictToProgram: 0, abnormal: 0, reviewer: '' }, { key: 'document_no' });
    seed.insert('ctl_document', { module: 'provider', module_id: Number(me), document_no: Number(own), status: 'A' }, { where: `module='provider' AND module_id=${Number(me)} AND document_no=${own}` });
    // Issue #3057: report classes fill the class selects; sub-classes are printed into a JavaScript array
    // literal for the sub-class autocomplete on the document list and the Edit popup; queue names fill the
    // queue selects of both upload pages.
    seed.insert('ctl_doc_class', { reportclass: P('document report class', 60), subclass: P('document subclass', 60) }, { key: 'id' });
    seed.insert('queue', { name: P('document queue name', 40) }, { key: 'id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  f.ignoredPaths.push('/documentManager/ManageDocument');
  // What each surface is known to show (live run on the packaged install); a surface that stops showing them is
  // a MISSING finding, so an empty or misrouted list cannot pass as an encoded one.
  const patientName = fieldIds(fields, 'patient last name', 'patient first name');
  // The report class is an option of the class select on both document lists (the Add Document form they
  // include) and the Edit popup; the sub-class is shown by the Edit popup's suggestions once typed for.
  const expected = {
    Inbox: patientName,
    'eDoc list': fieldIds(fields, 'provider document type', 'provider document description', 'document report class'),
    list: [...patientName, ...fieldIds(fields, 'document description', 'document report class')],
    edit: fieldIds(fields, 'document description', 'document source', 'document source facility', 'document report class', 'document subclass'),
    info: fieldIds(fields, 'document description', 'document source', 'document acknowledgement comment'),
    upload: fieldIds(fields, 'document queue name'),
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
      // Open the sub-class suggestions. 'data-xp' occurs in every payload tier but names no field, so the
      // typed value itself never counts as showing one: only the suggestion decoded from the page's array
      // literal can show the seeded sub-class.
      const subClass = popup.locator('#docSubClass');
      if (await subClass.count()) {
        // editDocument.jsp attaches the suggestion listener in window.onload.
        await popup.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
        await subClass.fill('data-xp');
        await popup.locator('#docSubClass_list li').first().waitFor({ state: 'visible', timeout: 5000 })
          .catch(() => f.missing('document edit popup', 'typing in the sub-class field offered no suggestion'));
      } else f.missing('document edit popup', 'the Edit popup has no sub-class field');
      await inspect(f, 'document edit popup', popup, fields, since, { expect: expected.edit });
      await popup.close();
    } else f.missing('document edit popup', 'the seeded document offers no Edit control');
    if (isPopup) await page.close();
    // Teardown proof, not best effort: a lock this run took must be released before the browser closes.
    await releaseChartLocks(s.context, s.config.baseUrl, [master]);
  });
  // Pages opened by route: each is what a real control loads (see the header), and none depends on opener state.
  const openRoute = async (route, surface, expect) => {
    const page = await s.context.newPage();
    await page.goto(h.appUrl(s.config.baseUrl, route), { waitUntil: 'load', timeout: 30000 });
    await page.waitForLoadState('networkidle').catch(() => {});
    const since = f.mark();
    await inspect(f, surface, page, fields, since, { expect });
    await page.close();
  };
  await step('the document information fragment shows stored metadata and the routing comment as text', async () => {
    // The Document Browser (url4) and Browse Notes load this fragment into the page as HTML (issue #3057).
    await openRoute(`/documentManager/ManageDocument?method=viewDocumentInfo&doc_no=${doc}`, 'document information fragment', expected.info);
  });
  await step('both document upload pages show stored queue names as text', async () => {
    // A packaged install's Inbox > Upload opens ViewDocumentUploader; ViewHtml5AddDocuments is the multi-file
    // page it opens instead when legacy_document_upload_enabled is on, and printed queue names raw (issue #3057).
    for (const [route, surface] of [['/documentManager/ViewDocumentUploader', 'document uploader'],
      ['/documentManager/ViewHtml5AddDocuments', 'multi-file document upload']]) {
      await openRoute(route, surface, expected.upload);
    }
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Documents and Inbox walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-documents-inbox', workflow, { openPatient: false });
