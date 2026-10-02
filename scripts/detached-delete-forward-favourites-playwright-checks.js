#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Forward dialog: removing a forwarding favourite (detached-delete risk sweep, issue #4129).
 * User path: Schedule > Search > Master Record > E-Chart > Documents entry (showDocument popup)
 *   > Forward (#fwdBtn_<doc>, "Forward Documents" dialog from oscarMDS/ViewSelectProvider) >
 *   Favorites: select the first owned favourite and "<<" it into the Forward List, double-click
 *   the second owned favourite to drop it > Forward (oscarMDS/ReportReassign). Then Schedule >
 *   Inbox (#inboxLink) > list mode > filter to the owned patient > tick the document > Forward
 *   (#topFBtn) > "<<" > provider search > Cancel.
 * Asserts: the dialog lists both owned favourites; Forward closes it, routes the document to the
 *   chosen provider (one new providerLabRouting row) and deletes exactly the dropped favourite's
 *   providerLabRoutingFavorites row while the kept one keeps its id. ReportReassign finds the
 *   favourites in one DAO call and deletes each by id (remove-by-id, the safe shape of the
 *   detached-delete pattern). Last: the Inbox list's Forward dialog opens without a script error,
 *   "<<" works and its provider search offers a provider; on 2026.08 this fails (Inboxhub.jsp
 *   never loads carlosAutocomplete.js and the sanitised dialog drops SelectProvider.jsp's own
 *   <script src>, so initProviderAutocomplete is undefined: uncaught ReferenceError and a dead
 *   provider search). Cancel writes nothing.
 * Fixtures: the owned FAKE- patient (runWorkflow; its NULL HIN set to the empty HIN the
 *   demographic form stores, which the inbox name search needs), one owned PDF in DOCUMENT_DIR
 *   with its document, ctl_document, patientLabRouting and providerLabRouting rows, and two
 *   favourites rows for the test login. EXCLUSIVE=1: the login's forwarding favourites are
 *   per-user state that any other check's Forward re-posts. Pre-existing favourites are
 *   snapshotted and must be unchanged. Cleanup deletes the owned rows and file and asserts them gone.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

const TIMEOUT = 60000;

/** One page of PDF text, enough for the document viewer to render a preview. */
function onePagePdf(text) {
  const content = `BT /F1 12 Tf 40 700 Td (${text}) Tj ET\n`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [4 0 R] /Count 1 >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const configured = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!configured) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const owner = fs.statSync(store);
  const targets = sql.rows(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
      AND provider_no<>${h.sqlString(provider)} AND COALESCE(last_name,'')<>'' ORDER BY provider_no LIMIT 2`).map(row => row[0]);
  if (targets.length < 2) throw new h.SkipCheck('The database has fewer than two other active providers to use as favourites');
  const [keep, drop] = targets;
  const favourites = () => sql.rows(`SELECT id, route_to_provider_no FROM providerLabRoutingFavorites
    WHERE provider_no=${h.sqlString(provider)} ORDER BY id`);
  const preexisting = favourites();
  h.assert(!preexisting.some(([, to]) => targets.includes(to)), 'The test login already has one of the fixture favourites');
  const ownedFavourites = [];
  const file = path.join(store, `${marker}.pdf`);
  let documentNo = null;
  const routes = () => sql.rows(`SELECT provider_no FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo} ORDER BY provider_no`)
    .map(row => row[0]);
  const listed = (dialog, select) => dialog.locator(`#${select} option`).evaluateAll(options => options.map(option => option.value));

  s.cleanup(() => {
    if (ownedFavourites.length) sql.execute(`DELETE FROM providerLabRoutingFavorites WHERE id IN (${ownedFavourites.join(',')})`);
    if (documentNo) {
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo} AND docdesc=${h.sqlString(marker)}`) === '1',
        'Document fixture ownership changed');
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${documentNo} AND lab_type='DOC';
        DELETE FROM patientLabRouting WHERE lab_no=${documentNo} AND lab_type='DOC' AND demographic_no=${patient};
        DELETE FROM ctl_document WHERE document_no=${documentNo} AND module='demographic' AND module_id=${patient};
        DELETE FROM document WHERE document_no=${documentNo} AND docdesc=${h.sqlString(marker)}`);
    }
    if (fs.existsSync(file)) fs.unlinkSync(file);
    const left = sql.value(`SELECT CONCAT_WS(',',
      (SELECT COUNT(*) FROM providerLabRoutingFavorites WHERE id IN (${ownedFavourites.length ? ownedFavourites.join(',') : 0})),
      (SELECT COUNT(*) FROM document WHERE document_no=${documentNo || 0}),
      (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo || 0}),
      (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo || 0} AND demographic_no=${patient}))`);
    h.assert(left === '0,0,0,0' && !fs.existsSync(file), `Owned favourites / document rows or file were not removed (${left})`);
    h.assert(JSON.stringify(favourites()) === JSON.stringify(preexisting), 'The test login\'s own favourites were changed');
  });

  // The harness patient has a NULL HIN; the inbox name search matches `hin LIKE '%%'`, which NULL
  // never satisfies, so give the owned patient the empty HIN the demographic form itself stores.
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)} AND hin IS NULL`);
  for (const to of targets) {
    const id = sql.value(`INSERT INTO providerLabRoutingFavorites (provider_no,route_to_provider_no)
      VALUES (${h.sqlString(provider)},${h.sqlString(to)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A favourite fixture was not created');
    ownedFavourites.push(id);
  }
  fs.writeFileSync(file, onePagePdf(`${marker} forwarded document`), { flag: 'wx', mode: 0o640 });
  fs.chownSync(file, owner.uid, owner.gid);
  documentNo = sql.value(`INSERT INTO document
    (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES ('lab',${h.sqlString(marker)},${h.sqlString(path.basename(file))},${h.sqlString(provider)},${h.sqlString(provider)},
      'A','application/pdf',0,1,0,CURDATE(),NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(documentNo), 'Document fixture was not inserted');
  // Demo data carries routing rows for long-deleted document numbers; a reused number would
  // link the owned document to other patients and providers, so refuse it rather than share it.
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ctl_document WHERE document_no=${documentNo})
    + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo})
    + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${documentNo})`) === '0',
  'The new document number collides with orphaned routing rows; rerun once the auto-increment has passed them');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A');
    INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${documentNo},'DOC',NOW());
    INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${h.sqlString(provider)},${documentNo},'DOC','N')`);

  let viewer;
  let dialog;
  await s.step('the E-Chart document viewer\'s Forward dialog lists both owned favourites', async () => {
    const chart = await s.chart();
    const link = chart.locator('#leftNavBar a, #rightNavBar a').filter({ hasText: marker }).first();
    // A long title sits under the right-floated date suffix; click its visible left edge.
    viewer = await clickOpensPopup(chart, link, { context: s.context, recorder: s.recorder, label: 'forward-favourites-viewer',
      timeout: TIMEOUT, position: { x: 8, y: 9 } });
    await viewer.locator(`[id="fwdBtn_${documentNo}"]`).click();
    dialog = viewer.getByRole('dialog', { name: 'Forward Documents', exact: true });
    await dialog.waitFor({ state: 'visible', timeout: 15000 });
    const offered = await listed(dialog, 'favorites');
    h.assert(targets.every(to => offered.includes(to)), 'The Favorites list does not offer both owned favourites');
  });

  await s.step('Forward with one favourite dropped routes the document and deletes exactly that favourite', async () => {
    await dialog.locator('#favorites').selectOption(keep);
    await dialog.getByRole('button', { name: '<<', exact: true }).click();
    h.assert(JSON.stringify(await listed(dialog, 'fwdProviders')) === JSON.stringify([keep]),
      '"<<" did not copy the selected favourite into the Forward List');
    await dialog.locator(`#favorites option[value="${drop}"]`).dblclick();
    const remaining = await listed(dialog, 'favorites');
    h.assert(!remaining.includes(drop) && remaining.includes(keep), 'Double-click did not drop exactly the second favourite from the list');
    const [response] = await Promise.all([
      viewer.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/oscarMDS/ReportReassign')),
      viewer.waitForEvent('domcontentloaded', { timeout: 30000 }),
      dialog.getByRole('button', { name: 'Forward', exact: true }).click(),
    ]);
    h.assert(response.status() === 200, `Forward answered HTTP ${response.status()}`);
    await h.assertNotErrorPage(viewer, 'document viewer after forwarding');
    h.assert(JSON.stringify(routes()) === JSON.stringify([provider, keep].sort()),
      'Forward did not route the owned document to exactly the chosen provider');
    const mine = favourites().filter(([id]) => ownedFavourites.includes(id));
    h.assert(JSON.stringify(mine) === JSON.stringify([[ownedFavourites[0], keep]]),
      'Forward did not delete exactly the dropped favourite (or replaced the kept one)');
    h.assert(JSON.stringify(favourites().filter(([id]) => !ownedFavourites.includes(id))) === JSON.stringify(preexisting),
      'Forward changed a favourite this check does not own');
    await viewer.close();
  });

  await s.step('the Inbox list Forward dialog opens without a script error, "<<" and provider search work, Cancel writes nothing', async () => {
    const { page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'), {
      context: s.context, label: 'forward-favourites-inbox', recorder: s.recorder, timeout: TIMEOUT,
    });
    await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
    if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
    await settle(inbox, TIMEOUT);
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await inbox.locator('#anyProvider').check();
    await inbox.locator('#statusNew').check();
    await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(marker);
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, TIMEOUT);
    h.assert(JSON.stringify(await shownRows(inbox)) === JSON.stringify([`DOC:${documentNo}`]),
      'The filtered inbox list does not show exactly the owned document');
    const before = { routes: routes(), favourites: favourites() };
    await inbox.locator(`input[name="flaggedLabs"][value="${documentNo}:DOC"]`).check();
    await inbox.locator('#topFBtn').click();
    const inboxDialog = inbox.getByRole('dialog', { name: 'Forward Documents', exact: true });
    await inboxDialog.waitFor({ state: 'visible', timeout: 15000 });
    await inboxDialog.locator('#favorites').selectOption(keep);
    await inboxDialog.getByRole('button', { name: '<<', exact: true }).click();
    const copied = await listed(inboxDialog, 'fwdProviders');
    // The provider search is the only way to add a recipient who is not a favourite.
    const [last, first] = sql.rows(`SELECT last_name, first_name FROM provider WHERE provider_no=${h.sqlString(drop)}`)[0];
    await inboxDialog.locator('#autocompleteprov').fill(`${last}, ${first}`);
    const offeredBySearch = await inbox.locator('.ui-autocomplete:visible .ui-menu-item').first()
      .waitFor({ state: 'visible', timeout: 10000 }).then(() => true, () => false);
    await inboxDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
    await inboxDialog.waitFor({ state: 'hidden' });
    h.assert(JSON.stringify({ routes: routes(), favourites: favourites() }) === JSON.stringify(before), 'Cancel changed routing or favourites');
    h.assert(JSON.stringify(copied) === JSON.stringify([keep]), 'The Inbox list Forward dialog\'s "<<" did not copy the favourite');
    h.assert(offeredBySearch, 'The Inbox list Forward dialog\'s provider search offered nothing (no recipient can be added by name)');
  });
}

if (require.main === module) runWorkflow('detached-delete-forward-favourites', workflow, { openPatient: true });
module.exports = { workflow };
