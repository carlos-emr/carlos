#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Inbox "Forward" and "File" for HL7 labs and documents, end to end (coverage plan: inbox-file-forward).
 *
 * WHY THIS CHECK EXISTS. Forwarding a result to a colleague and filing it away are the two things a
 * clinician does to a result besides reading it. Both are an Inbox button that posts a list of
 * "<id>:<type>" tokens (oscarMDS/ReportReassign, oscarMDS/FileLabs) and both write providerLabRouting.
 * Each has a specific promise: Forward puts the result in the RECIPIENT's Inbox as New and leaves the
 * sender's own copy alone; File takes it out of the sender's Inbox only (status F) without recording that
 * anyone reviewed it, and for a document also closes its queue link. Until now only document forwarding
 * (detached-delete-forward-favourites) and rule-driven lab forwarding (lab-forwarding-rules) were driven:
 * nothing clicked Forward on a lab, nothing clicked File, and nothing logged in as the person who was sent
 * the result to see that it arrived.
 *
 * User path (as the shared test login unless stated):
 *   Inbox (Schedule > Inbox) > list mode, filtered to the owned patient > a lab's own link (the lab popup,
 *   lab/CA/ALL/ViewLabDisplay) > Forward > provider search > Forward.
 *   Inbox > preview mode > the lab's card (an iframe of the same lab page) > Forward > provider search >
 *   Forward.
 *   A throwaway second provider logs in > Inbox: the forwarded labs are listed.
 *   Inbox > list mode > tick the lab > File (#topFileBtn).
 *   Inbox > preview mode > the document's card > File (the document viewer's File button, which posts
 *   oscarMDS/FileLabs method=fileLabAjax).
 *
 * Asserted, in order, on owned rows only:
 *   1. The owned items reach the test provider's Inbox as New: three synthetic HL7 labs uploaded through
 *      Inbox > HL7 Lab Upload (built like lab-upload's; the patient's MRP is the test provider, so the
 *      upload routes each to it) and two owned PDF documents with a New routing row for the provider and
 *      an ACTIVE queue_document_link. The recipient has no row for any of them.
 *   2. The Inbox, filtered to the owned patient, lists exactly those five items.
 *   3. Lab popup > Forward: the dialog's provider search offers the recipient; Forward posts ReportReassign
 *      for exactly that lab and provider (200) and the recipient gets one providerLabRouting row, status N.
 *      The sender's row (id, status, comment, timestamp) and every other provider's row for the lab (the
 *      demo clinic forwards the test provider's labs to a second provider by a rule) are unchanged.
 *   4. The recipient, logged in, finds the lab in their own Inbox, and only that lab.
 *   5. Preview card > Forward: the same, for the second lab, from inside the card's iframe.
 *   6. The recipient's Inbox now lists both forwarded labs and still none of the unforwarded items.
 *   7. List > File on the first lab (already forwarded): the sender's row is status F and its comment is
 *      untouched (no acknowledgement: the status is not A and no comment was written), the lab leaves the
 *      sender's list, the RECIPIENT's row stays New and still shows in their Inbox, every other provider's
 *      row is unchanged, and the unticked labs are untouched.
 *   8. Document viewer > File on the first document: the sender's DOC row is status F (not A, comment
 *      untouched), every queue_document_link of the document is closed (status I), the document row itself
 *      stays, and the control document keeps its New row and its active queue link.
 *   9. A GET (and HEAD) replay of the real FileLabs (list form and fileLabAjax) and ReportReassign (lab
 *      popup and preview card) POSTs, aimed at the untouched control items, is refused by the application
 *      (a 403/405 it wrote, via h.assertRefused: its own header or error page, not the front door's) and
 *      writes nothing: no status moved, no routing row created, the queue link still active. The
 *      ReportReassign replay carries the sender's own favourites list, because Forward replaces the
 *      favourites with the posted list and an accepted GET with an empty one would delete them.
 *  10. The sender's favourites and the control items end exactly as they began.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow; its NULL HIN set to the empty HIN the demographic form
 * stores, which the Inbox name search needs, after the labs are matched), a throwaway provider login that is
 * the recipient (lib/throwaway-login-fixture.js), three synthetic CML labs uploaded through the UI (each with
 * a unique accession; the archived upload files under LAB_UPLOAD_DOCUMENT_STORE are removed by prefix), two
 * owned PDFs in DOCUMENT_DIR with document / ctl_document / patientLabRouting / providerLabRouting rows and
 * a queue_document_link row each. Cleanup removes exactly those rows and files, the throwaway login and its
 * audit rows, and asserts them gone. The test login's own favourites are snapshotted and asserted unchanged.
 * The lab-upload archive is retained (and the run says so) when LAB_UPLOAD_DOCUMENT_STORE is unset.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openSecondSession } = require('./lib/concurrency-support');
const { captureRequest, replayParams } = require('./lib/get-reject-probe');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');
const { syntheticCmlLab, openUploader } = require('./lab-upload-playwright-checks');
const { removeOwnedHl7Labs, removeArchiveFiles, archivesNamed } = require('./lab-forwarding-rules-playwright-checks');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

const NAME = 'inbox-file-forward';
const TIMEOUT = 30000;
const q = h.sqlString;

/** Upload one synthetic CML lab through Inbox > HL7 Lab Upload and return the status the page reports. */
async function uploadLab(s, filePath, fileName) {
  const { inbox, popup } = await openUploader(s);
  try {
    await popup.locator('#importFiles').setInputFiles(filePath);
    await popup.locator('#type').selectOption('CML');
    const [response] = await Promise.all([
      popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'), { timeout: 60000 }),
      popup.locator('#uploadForm button[type="submit"]').click(),
    ]);
    h.assert(response.status() < 400, `HL7 lab upload answered HTTP ${response.status()}`);
    const item = popup.locator('#file-list .file-item').filter({ hasText: fileName }).first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    return (await item.locator('.upload-text').innerText()).trim();
  } finally {
    await popup.close().catch(() => {});
    await inbox.close().catch(() => {});
  }
}

/** Open Schedule > Inbox (popup or same tab, by deployment), the way a clinician does. */
async function openInbox(schedule, context, recorder, label) {
  h.assert(await schedule.locator('#inboxLink').count() > 0,
    'The schedule offers no Inbox control, so a clinician cannot reach their results from the day sheet at all');
  const { page } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#inboxLink').first(),
    { context, recorder, label, timeout: TIMEOUT });
  await page.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
  return page;
}

/** The search sidebar is collapsible; open it before touching a filter. */
async function openSidebar(inbox) {
  if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
}

/** Switch the Inbox into list mode (the table with the File / Forward toolbar) or preview mode (cards). */
async function setMode(inbox, mode) {
  const toggle = inbox.locator('#btnViewMode2');
  await toggle.waitFor({ state: 'attached', timeout: TIMEOUT });
  // A CHECKED #btnViewMode2 is preview mode: its label then offers "List mode".
  if (await toggle.isChecked() !== (mode === 'preview')) await inbox.locator('#btnViewModeLabel').click();
  if (mode === 'preview') await inbox.locator('#inboxViewItems').waitFor({ state: 'attached', timeout: TIMEOUT });
  else await inbox.locator('#inboxhubListModeTableBody').waitFor({ state: 'attached', timeout: TIMEOUT });
  await settle(inbox, TIMEOUT);
}

/** Press the search button (sidebar opened first) and wait for the list to stop changing. */
async function search(inbox) {
  await openSidebar(inbox);
  await inbox.locator('#inboxhubFormSearchBtn').click();
  await settle(inbox, TIMEOUT);
}

/** Preview mode's cards, as "TYPE:segmentID" in rendered order. */
async function shownCards(inbox) {
  return (await inbox.$$eval('#inboxViewItems .document-card', (cards) => cards.map((card) => (
    `${card.getAttribute('data-lab-type') || '?'}:${card.getAttribute('data-segment-id') || '?'}`)))).sort();
}

/** Wait until preview mode shows exactly `expected` cards (sorted identities) and return them. */
async function expectCards(inbox, expected, message) {
  const deadline = Date.now() + TIMEOUT;
  let seen = [];
  do {
    seen = await shownCards(inbox);
    if (JSON.stringify(seen) === JSON.stringify(expected)) return seen;
    await inbox.waitForTimeout(400);
  } while (Date.now() < deadline);
  h.assert(false, `${message}: expected ${JSON.stringify(expected)}, the cards were ${JSON.stringify(seen)}`);
  return seen;
}

/** The frame of one preview card, loaded (cards are lazy iframes, so the card is scrolled into view first). */
async function cardFrame(inbox, type, segment) {
  h.assert(/^[A-Za-z0-9_-]+$/.test(type) && /^[1-9]\d*$/.test(String(segment)), 'Preview card identity has an unexpected shape');
  const card = inbox.locator(`#inboxViewItems .document-card[data-lab-type="${type}"][data-segment-id="${segment}"]`);
  await card.waitFor({ state: 'attached', timeout: TIMEOUT });
  await card.scrollIntoViewIfNeeded({ timeout: TIMEOUT });
  const handle = await card.locator('iframe').first().elementHandle({ timeout: TIMEOUT });
  const frame = await handle.contentFrame();
  h.assert(frame, `The ${type}:${segment} preview card has no document frame`);
  await frame.waitForLoadState('load', { timeout: TIMEOUT });
  return frame;
}

/**
 * In an open "Forward Documents" dialog (inside `scope`, the page or the card's frame): find the recipient
 * through the dialog's provider search, add them to the Forward List, and press Forward. Returns the
 * ReportReassign request the page really sent (path, params, status), captured on `page`.
 */
async function forwardTo(page, scope, recipientNo, searchText) {
  const dialog = scope.getByRole('dialog', { name: 'Forward Documents', exact: true });
  await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
  await dialog.locator('#autocompleteprov').fill(searchText);
  const offered = scope.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: searchText.split(',')[0] }).first();
  await offered.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
  h.assert(await offered.isVisible(),
    'The Forward dialog\'s provider search offered nothing for the recipient (no provider can be added by name)');
  await offered.click();
  const listed = await dialog.locator('#fwdProviders option').evaluateAll((options) => options.map((option) => option.value));
  h.assert(JSON.stringify(listed) === JSON.stringify([recipientNo]),
    'Selecting the search result did not put exactly the intended provider in the Forward List');
  return captureRequest(page, (url) => url.pathname.endsWith('/oscarMDS/ReportReassign'),
    () => dialog.getByRole('button', { name: 'Forward', exact: true }).click());
}

/** Send the captured request again as GET and as HEAD with `overrides`, and assert it was refused and wrote nothing. */
async function assertReplayRefused(s, label, sent, overrides, target) {
  const url = new URL(`${sent.path}?${replayParams(sent.params, overrides).toString()}`, s.config.baseUrl.origin).toString();
  h.assert(url.length < 7000, `${label}: the replayed request is too long for a request line`);
  for (const method of ['GET', 'HEAD']) {
    const before = s.sql.value(`SELECT COUNT(*) FROM ${target.table} WHERE ${target.where}`);
    h.assert(before === String(target.expected), `${label}: the rows the ${method} replay is aimed at were not in the expected state (${before})`);
    const response = await s.context.request.fetch(url, { method, maxRedirects: 0, failOnStatusCode: false });
    const verdict = await h.assertRefused(s, { response, table: target.table, where: target.where, before, label: `${label} as ${method}` });
    console.log(`  probe ${NAME}: ${label} as ${method} -> ${verdict.evidence}; ${target.table} rows unchanged (${verdict.rows})`);
  }
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient, provider } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const recipient = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-inbox-ff-'));
  const labs = ['popup', 'card', 'control'].map((key, index) => ({
    key, labNo: null, accession: `FF${stamp}${index}`, fileName: `inbox-file-forward-${stamp}-${index}.hl7`,
  }));
  const [labPopup, labCard, labControl] = labs;
  const docs = ownedPdfDocuments(store, marker, [{ key: 'file' }, { key: 'control' }]);
  const [docFile, docControl] = docs;
  const queueOf = (doc) => `SELECT GROUP_CONCAT(status ORDER BY id) FROM queue_document_link WHERE document_id=${doc.id}`;
  const favourites = () => JSON.stringify(sql.rows(`SELECT id, route_to_provider_no FROM providerLabRoutingFavorites
    WHERE provider_no=${q(provider)} ORDER BY id`));
  const favouritesBefore = favourites();
  const ownFavouriteProviders = sql.rows(`SELECT route_to_provider_no FROM providerLabRoutingFavorites
    WHERE provider_no=${q(provider)} ORDER BY id`).map(([to]) => to);

  // Routing rows of one item, by who holds them. A row is identified by id, status, comment and timestamp,
  // so "unchanged" means no write touched it at all.
  const rowsOf = (type, no, scope) => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'),
    IFNULL(timestamp,'<null>') FROM providerLabRouting WHERE lab_type=${q(type)} AND lab_no=${no} AND ${scope} ORDER BY id`));
  const mine = (type, no) => rowsOf(type, no, `provider_no=${q(provider)}`);
  const theirs = (type, no) => rowsOf(type, no, `provider_no=${q(recipient.providerNo)}`);
  const others = (type, no) => rowsOf(type, no, `provider_no NOT IN (${q(provider)},${q(recipient.providerNo)})`);
  const statusOf = (type, no, who) => sql.value(`SELECT IFNULL(GROUP_CONCAT(status ORDER BY id),'') FROM providerLabRouting
    WHERE lab_type=${q(type)} AND lab_no=${no} AND provider_no=${q(who)}`);

  // Cleanup runs last-registered first. Everything below is registered before its first write.
  s.cleanup(() => recipient.cleanup());
  s.cleanup(() => {
    h.assert(favourites() === favouritesBefore, 'The test login\'s own forwarding favourites were changed');
  });
  s.cleanup(() => {
    const ids = docs.map((doc) => doc.id).filter(Boolean);
    if (ids.length) {
      const list = ids.join(',');
      h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no IN (${list}) AND docdesc LIKE ${q(`${marker}%`)}`) === String(ids.length),
        'Document fixture ownership changed; refusing to delete routing rows keyed by document number');
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}) AND demographic_no=${patient};
        DELETE FROM queue_document_link WHERE document_id IN (${list})`);
    }
    removeOwnedPdfDocuments({ sql, marker, patient, files: docs.map((doc) => doc.file), docs });
    assertOwnedPdfDocumentsRemoved({ sql, marker, patient, files: docs.map((doc) => doc.file), docs });
    if (ids.length) {
      const list = ids.join(',');
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))
        + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no IN (${list}))
        + (SELECT COUNT(*) FROM queue_document_link WHERE document_id IN (${list}))`) === '0',
      'The owned document routing or queue rows were not removed');
    }
  });
  s.cleanup(() => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const found = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum IN (${labs.map((lab) => q(lab.accession)).join(',')})`)
      .map(([id]) => id);
    removeOwnedHl7Labs(sql, [...found, ...labs.map((lab) => lab.labNo).filter(Boolean)]);
    // A rejected upload leaves its checksum row and archive without any lab row; the unique name finds both.
    for (const lab of labs) {
      sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${lab.fileName}.%`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${lab.fileName}.%`)}`) === '0',
        'A synthetic lab upload checksum row was not removed');
      removeArchiveFiles(archivesNamed(lab.fileName));
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum IN (${labs.map((lab) => q(lab.accession)).join(',')})`) === '0',
      'The synthetic labs were not removed');
  });

  recipient.create();
  const recipientNo = recipient.providerNo;
  const searchText = `${marker}, Throwaway`;
  let sender;          // the test provider's Inbox page
  let second;          // the recipient's session: { context, schedule }
  let recipientInbox;  // the recipient's Inbox page
  let forwarded;       // the ReportReassign POST the lab popup really sent
  let filedList;       // the FileLabs POST the list's File button really sent
  let filedDocument;   // the FileLabs (fileLabAjax) POST the document viewer's File button really sent
  let forwardedByCard; // the ReportReassign POST the preview card really sent
  const control = { labRows: null, docRows: null };

  await s.step('the owned labs and documents reach the test provider\'s Inbox as New, unknown to the recipient', async () => {
    for (const lab of labs) {
      const file = path.join(workDir, lab.fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
      fs.writeFileSync(file, Buffer.from(syntheticCmlLab(lab.accession, marker), 'latin1'));
      const status = await uploadLab(s, file, lab.fileName);
      h.assert(status === 'Uploaded successfully', `The ${lab.key} lab upload reported "${status}"`);
      await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`, '1',
        `The ${lab.key} lab did not reach hl7TextInfo`);
      lab.labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(lab.accession)}`);
      h.assert(/^[1-9]\d*$/.test(lab.labNo), `The ${lab.key} lab has no lab number`);
      h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab.labNo}`) === patient,
        `The ${lab.key} lab was not matched to the owned patient`);
      h.assert(statusOf('HL7', lab.labNo, provider) === 'N',
        `The upload did not route the ${lab.key} lab to the test provider as New (the patient's MRP)`);
      h.assert(theirs('HL7', lab.labNo) === '[]', `The recipient already holds the ${lab.key} lab`);
    }
    // The harness patient has a NULL HIN; the Inbox name search matches `hin LIKE '%%'`, which NULL never
    // satisfies, so give the owned patient the empty HIN the demographic form itself stores (after matching).
    sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${q(marker)} AND hin IS NULL`);

    for (const doc of docs) {
      // Demo data carries routing rows for long-deleted document numbers; a reused number would link the
      // owned document to other patients and providers. Take the next number instead of sharing them.
      for (let attempt = 1; ; attempt += 1) {
        seedOwnedPdfDocuments({ sql, store, patient, provider, docs: [doc] });
        const held = sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id})
          + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${doc.id})
          + (SELECT COUNT(*) FROM queue_document_link WHERE document_id=${doc.id})
          + (SELECT COUNT(*) FROM ctl_document WHERE document_no=${doc.id} AND NOT (module='demographic' AND module_id=${patient}))`);
        if (held === '0') break;
        h.assert(attempt < 6, 'Six consecutive new document numbers collide with orphaned routing rows');
        sql.execute(`DELETE FROM ctl_document WHERE document_no=${doc.id} AND module='demographic' AND module_id=${patient};
          DELETE FROM document WHERE document_no=${doc.id} AND docdesc=${q(doc.label)}`);
        fs.unlinkSync(doc.file);
        delete doc.id;
      }
      const queue = sql.value('SELECT MIN(id) FROM queue');
      h.assert(/^[1-9]\d*$/.test(queue), 'The install has no document queue to link the owned document to');
      sql.execute(`INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${doc.id},'DOC',NOW());
        INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${doc.id},'DOC','N');
        INSERT INTO queue_document_link (queue_id,document_id,status) VALUES (${queue},${doc.id},'A')`);
      h.assert(queueOf(doc) && sql.value(queueOf(doc)) === 'A', 'The document\'s queue link fixture is not active');
    }
  });

  await s.step('the Inbox lists exactly the owned items for the test provider', async () => {
    sender = await openInbox(s.schedule, s.context, recorder, 'inbox-sender');
    await setMode(sender, 'list');
    await openSidebar(sender);
    h.assert(await sender.locator('#specificProvider').isChecked(),
      'The Inbox does not default to the logged-in provider\'s own results');
    await sender.locator('#statusNew').check();
    await sender.locator('#specificPatients').check();
    await sender.locator('#inputLastName').fill(marker);
    await search(sender);
    const expected = [`DOC:${docFile.id}`, `DOC:${docControl.id}`, ...labs.map((lab) => `HL7:${lab.labNo}`)].sort();
    h.assert(JSON.stringify(await shownRows(sender)) === JSON.stringify(expected),
      `The filtered Inbox list does not show exactly the owned items (${JSON.stringify(await shownRows(sender))})`);
    control.labRows = JSON.stringify([mine('HL7', labControl.labNo), theirs('HL7', labControl.labNo), others('HL7', labControl.labNo)]);
    control.docRows = JSON.stringify([mine('DOC', docControl.id), sql.value(queueOf(docControl))]);
  });

  await s.step('lab popup > Forward routes the lab to the chosen provider as New and leaves every other row alone', async () => {
    const beforeMine = mine('HL7', labPopup.labNo);
    const beforeOthers = others('HL7', labPopup.labNo);
    const row = sender.locator(`tr[data-lab-type="HL7"][data-segment-id="${labPopup.labNo}"]`);
    await row.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const popup = await clickOpensPopup(sender, row.locator('a[onclick*="reportWindow"]').first(),
      { context: s.context, recorder, label: 'inbox-ff-lab-popup', timeout: TIMEOUT });
    try {
      const button = popup.getByRole('button', { name: 'Forward', exact: true }).first();
      await button.waitFor({ state: 'visible', timeout: TIMEOUT });
      await button.click();
      const reloaded = popup.waitForEvent('framenavigated', { predicate: (f) => f === popup.mainFrame(), timeout: TIMEOUT });
      reloaded.catch(() => {});
      forwarded = await forwardTo(popup, popup, recipientNo, searchText);
      h.assert(forwarded.status === 200, `ReportReassign answered HTTP ${forwarded.status}`);
      h.assert(forwarded.params.get('flaggedLabs') === JSON.stringify({ files: [`${labPopup.labNo}:HL7`] }),
        'Forward from the lab popup did not post exactly this lab');
      h.assert(forwarded.params.get('selectedProviders') === JSON.stringify({ providers: [recipientNo] }),
        'Forward from the lab popup did not post exactly the chosen provider');
      await expectValue(sql, `SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labPopup.labNo}
        AND provider_no=${q(recipientNo)}`, '1', 'Forwarding the lab created no routing row for the recipient');
      // On success the page reloads itself; a lab page that stays put after a 200 hides a failure from the clinician.
      await reloaded;
      await popup.waitForLoadState('load', { timeout: TIMEOUT });
    } finally {
      await popup.close().catch(() => {});
    }
    h.assert(statusOf('HL7', labPopup.labNo, recipientNo) === 'N', 'The recipient\'s routing row is not New');
    h.assert(mine('HL7', labPopup.labNo) === beforeMine, 'Forwarding changed the sender\'s own routing row');
    h.assert(others('HL7', labPopup.labNo) === beforeOthers, 'Forwarding changed another provider\'s routing row');
    h.assert(statusOf('HL7', labPopup.labNo, provider) === 'N', 'The sender no longer holds the forwarded lab as New');
  });

  await s.step('the recipient finds the forwarded lab in their own Inbox', async () => {
    second = await openSecondSession(s, { label: 'inbox-ff-recipient', openMaster: false,
      config: { ...config, testUser: recipient.username } });
    recipientInbox = await openInbox(second.schedule, second.context, recorder, 'inbox-recipient');
    await setMode(recipientInbox, 'list');
    await openSidebar(recipientInbox);
    h.assert(await recipientInbox.locator('#specificProvider').isChecked(),
      'The recipient\'s Inbox does not default to their own results');
    await search(recipientInbox);
    h.assert(JSON.stringify(await shownRows(recipientInbox)) === JSON.stringify([`HL7:${labPopup.labNo}`]),
      `The recipient's Inbox does not list exactly the forwarded lab (${JSON.stringify(await shownRows(recipientInbox))})`);
  });

  await s.step('preview card > Forward routes the second lab to the chosen provider as New and leaves every other row alone', async () => {
    const beforeMine = mine('HL7', labCard.labNo);
    const beforeOthers = others('HL7', labCard.labNo);
    await setMode(sender, 'preview');
    await expectCards(sender, [`DOC:${docFile.id}`, `DOC:${docControl.id}`, ...labs.map((lab) => `HL7:${lab.labNo}`)].sort(),
      'Preview mode does not show the same owned items as the list');
    const frame = await cardFrame(sender, 'HL7', labCard.labNo);
    const button = frame.getByRole('button', { name: 'Forward', exact: true }).first();
    await button.waitFor({ state: 'visible', timeout: TIMEOUT });
    await button.click();
    const reloaded = sender.waitForEvent('framenavigated', { predicate: (f) => f === frame, timeout: TIMEOUT });
    reloaded.catch(() => {});
    forwardedByCard = await forwardTo(sender, frame, recipientNo, searchText);
    h.assert(forwardedByCard.status === 200, `ReportReassign answered HTTP ${forwardedByCard.status}`);
    h.assert(forwardedByCard.params.get('flaggedLabs') === JSON.stringify({ files: [`${labCard.labNo}:HL7`] }),
      'Forward from the preview card did not post exactly this lab');
    h.assert(forwardedByCard.params.get('selectedProviders') === JSON.stringify({ providers: [recipientNo] }),
      'Forward from the preview card did not post exactly the chosen provider');
    await expectValue(sql, `SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labCard.labNo}
      AND provider_no=${q(recipientNo)}`, '1', 'Forwarding from the card created no routing row for the recipient');
    await reloaded;
    await frame.waitForLoadState('load', { timeout: TIMEOUT });
    h.assert(statusOf('HL7', labCard.labNo, recipientNo) === 'N', 'The recipient\'s routing row is not New');
    h.assert(mine('HL7', labCard.labNo) === beforeMine, 'Forwarding changed the sender\'s own routing row');
    h.assert(others('HL7', labCard.labNo) === beforeOthers, 'Forwarding changed another provider\'s routing row');
  });

  await s.step('the recipient\'s Inbox lists both forwarded labs and nothing that was not forwarded', async () => {
    await search(recipientInbox);
    h.assert(JSON.stringify(await shownRows(recipientInbox)) === JSON.stringify([`HL7:${labPopup.labNo}`, `HL7:${labCard.labNo}`].sort()),
      `The recipient's Inbox does not list exactly the two forwarded labs (${JSON.stringify(await shownRows(recipientInbox))})`);
    h.assert(theirs('HL7', labControl.labNo) === '[]' && theirs('DOC', docFile.id) === '[]',
      'The recipient holds an item that was never forwarded');
  });

  await s.step('Inbox > File files the lab for this provider only and does not acknowledge it', async () => {
    await setMode(sender, 'list');
    await search(sender);
    const row = sender.locator(`tr[data-lab-type="HL7"][data-segment-id="${labPopup.labNo}"]`);
    await row.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const before = { mine: mine('HL7', labPopup.labNo), theirs: theirs('HL7', labPopup.labNo), others: others('HL7', labPopup.labNo) };
    const myComment = sql.value(`SELECT IFNULL(comment,'<null>') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${labPopup.labNo} AND provider_no=${q(provider)}`);
    await sender.locator(`input[name="flaggedLabs"][value="${labPopup.labNo}:HL7"]`).check();
    h.assert(await sender.locator('#topFileBtn').isVisible(), 'The Inbox list offers no File button');
    filedList = await captureRequest(sender, (url) => url.pathname.endsWith('/oscarMDS/FileLabs'),
      () => sender.locator('#topFileBtn').click());
    h.assert(filedList.status === 200, `FileLabs answered HTTP ${filedList.status}`);
    // The File button builds its JSON by hand ({"files" : [...]}), so compare the parsed list.
    h.assert(JSON.stringify(JSON.parse(filedList.params.get('flaggedLabs') || '{}').files) === JSON.stringify([`${labPopup.labNo}:HL7`]),
      'File did not post exactly the ticked lab');
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${labPopup.labNo} AND provider_no=${q(provider)}`, 'F', 'File did not set the sender\'s routing row to F');
    // Filing is not reviewing: no acknowledgement status, and no comment written.
    const [status, comment] = sql.rows(`SELECT status, IFNULL(comment,'<null>') FROM providerLabRouting WHERE lab_type='HL7'
      AND lab_no=${labPopup.labNo} AND provider_no=${q(provider)}`)[0];
    h.assert(status === 'F' && comment === myComment, `File left the sender's row status=${status}, comment ${comment === myComment ? 'unchanged' : 'changed'}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labPopup.labNo} AND status='A'`) === '0',
      'File acknowledged the lab for someone');
    h.assert(theirs('HL7', labPopup.labNo) === before.theirs, 'File changed the recipient\'s routing row');
    h.assert(others('HL7', labPopup.labNo) === before.others, 'File changed another provider\'s routing row');
    await row.first().waitFor({ state: 'detached', timeout: TIMEOUT });
    await search(recipientInbox);
    h.assert((await shownRows(recipientInbox)).includes(`HL7:${labPopup.labNo}`),
      'Filing the lab removed it from the recipient\'s Inbox');
    h.assert(statusOf('HL7', labCard.labNo, provider) === 'N' && mine('HL7', labControl.labNo) === JSON.parse(control.labRows)[0],
      'File touched a lab that was not ticked');
  });

  await s.step('document viewer > File files the document for this provider and closes its queue link', async () => {
    await setMode(sender, 'preview');
    await expectCards(sender, [`DOC:${docFile.id}`, `DOC:${docControl.id}`, `HL7:${labCard.labNo}`, `HL7:${labControl.labNo}`].sort(),
      'Preview mode does not show the owned items still New for the test provider');
    const before = { mine: mine('DOC', docFile.id), others: others('DOC', docFile.id), documentStatus:
      sql.value(`SELECT status FROM document WHERE document_no=${docFile.id}`) };
    const myComment = sql.value(`SELECT IFNULL(comment,'<null>') FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${docFile.id}
      AND provider_no=${q(provider)}`);
    const frame = await cardFrame(sender, 'DOC', docFile.id);
    const file = frame.locator(`[id="fileBtn_${docFile.id}"]`);
    await file.waitFor({ state: 'visible', timeout: TIMEOUT });
    filedDocument = await captureRequest(sender, (url) => url.pathname.endsWith('/oscarMDS/FileLabs'), () => file.click());
    h.assert(filedDocument.status === 200, `FileLabs answered HTTP ${filedDocument.status}`);
    h.assert(filedDocument.params.get('method') === 'fileLabAjax' && filedDocument.params.get('flaggedLabId') === docFile.id
      && filedDocument.params.get('labType') === 'DOC', 'The document viewer\'s File did not post fileLabAjax for exactly this document');
    await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(status),'') FROM providerLabRouting WHERE lab_type='DOC'
      AND lab_no=${docFile.id} AND provider_no=${q(provider)}`, 'F', 'File did not set the sender\'s document routing row to F');
    const comment = sql.value(`SELECT IFNULL(comment,'<null>') FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${docFile.id}
      AND provider_no=${q(provider)}`);
    h.assert(comment === myComment, 'File wrote a comment on the document routing row');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${docFile.id} AND status='A'`) === '0',
      'File acknowledged the document');
    h.assert(sql.value(queueOf(docFile)) === 'I', `The document's queue link reads "${sql.value(queueOf(docFile))}", not closed (I)`);
    h.assert(others('DOC', docFile.id) === before.others, 'File changed another provider\'s routing row');
    h.assert(sql.value(`SELECT status FROM document WHERE document_no=${docFile.id}`) === before.documentStatus,
      'File changed the document row itself');
    h.assert(JSON.stringify([mine('DOC', docControl.id), sql.value(queueOf(docControl))]) === control.docRows,
      'File touched the control document\'s routing row or queue link');
  });

  await s.step('a GET (and HEAD) replay of FileLabs and ReportReassign is refused and writes nothing', async () => {
    const heldLab = `provider_no=${q(provider)} AND lab_type='HL7' AND lab_no=${labControl.labNo} AND status='N'`;
    await assertReplayRefused(s, 'FileLabs flaggedLabs', filedList,
      { flaggedLabs: JSON.stringify({ files: [`${labControl.labNo}:HL7`] }) },
      { table: 'providerLabRouting', where: heldLab, expected: 1 });
    await assertReplayRefused(s, 'FileLabs fileLabAjax', filedDocument,
      { flaggedLabId: docControl.id },
      { table: 'providerLabRouting', where: `provider_no=${q(provider)} AND lab_type='DOC' AND lab_no=${docControl.id} AND status='N'`, expected: 1 });
    h.assert(sql.value(queueOf(docControl)) === 'A', 'The replayed fileLabAjax closed the control document\'s queue link');
    // The replay carries the sender's real favourites: Forward replaces the favourites with the posted
    // list, so an empty list on an accepted GET would delete them.
    for (const [label, sent] of [['ReportReassign from the lab popup', forwarded], ['ReportReassign from the preview card', forwardedByCard]]) {
      await assertReplayRefused(s, label, sent,
        {
          flaggedLabs: JSON.stringify({ files: [`${labControl.labNo}:HL7`] }),
          selectedProviders: JSON.stringify({ providers: [recipientNo] }),
          selectedFavorites: JSON.stringify({ favorites: ownFavouriteProviders }),
        },
        { table: 'providerLabRouting', where: `provider_no=${q(recipientNo)} AND lab_type='HL7' AND lab_no=${labControl.labNo}`, expected: 0 });
    }
  });

  await s.step('the control items and the sender\'s favourites end exactly as they began', async () => {
    h.assert(JSON.stringify([mine('HL7', labControl.labNo), theirs('HL7', labControl.labNo), others('HL7', labControl.labNo)]) === control.labRows,
      'The control lab\'s routing rows changed');
    h.assert(JSON.stringify([mine('DOC', docControl.id), sql.value(queueOf(docControl))]) === control.docRows,
      'The control document\'s routing row or queue link changed');
    h.assert(favourites() === favouritesBefore, 'The test login\'s forwarding favourites changed');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
