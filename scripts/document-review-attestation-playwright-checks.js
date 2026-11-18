#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Reviewing a stored document from its Edit popup when the page is out of date (issue #6081).
 * The popup renders the reviewer it read when it opened; the server decides whether a click on
 * Reviewed is the first review or an additional one from the stored document, under its row lock.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Documents (eDoc report popup) ▸ Edit
 * (documentManager/ViewEditDocument popup) ▸ Reviewed / Update (POST documentManager/addEditDocument).
 * Asserted, each time after the popup was rendered and before the click:
 *   - another user's Update cleared the review (the popup still shows it): Reviewed makes the
 *     signed-in provider the document's reviewer, with no extra-reviewer row;
 *   - another provider reviewed it (the popup still shows it unreviewed): Reviewed keeps that first
 *     review and adds the signed-in provider as an additional reviewer;
 *   - the document row is deleted while the request is between its access check and the save (a
 *     second database session holds the row lock, deletes the row and commits): Reviewed re-renders
 *     the popup with "This document no longer exists" and stores nothing. A row deleted before the
 *     click is refused earlier, by the access check (HTTP 403), on any build.
 * Env: SCREENSHOT_DIR (optional) receives the evidence screenshots.
 * Fixtures: the owned patient and three owned 1-page PDF documents (rows + files, names carry the
 * marker); a second provider from the install is used only as a stored reviewer value. Cleanup
 * removes the extra-reviewer rows, the documents, their links and files, and asserts they are gone.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  directory, ownedPdfDocuments, seedOwnedPdfDocuments, removeOwnedPdfDocuments, assertOwnedPdfDocumentsRemoved,
} = require('./lib/stored-pdf-documents');
const { masterRecordLink } = require('./lib/master-record-link');

/**
 * A second database session that locks the document row, waits, deletes it and commits. Started
 * just before the click, it lets the request pass its access check and reach the locked save while
 * the row still exists, then removes it: the window issue #6081 describes, made deterministic.
 * Resolves `locked` once the lock is held and `done` once the delete has committed.
 */
function deleteUnderLock(mysql, documentNo, holdSeconds) {
  h.assert(/^[1-9]\d*$/.test(String(documentNo)), 'Invalid document identity');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-review-race-'));
  const options = path.join(dir, 'client.cnf');
  fs.writeFileSync(options, `[client]\npassword="${String(mysql.password).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"\n`, { mode: 0o600 });
  const child = spawn('mysql', [`--defaults-extra-file=${options}`, '-h', mysql.host || 'localhost', '-u', mysql.user || 'root',
    mysql.database || 'carlos', '-N', '-B', '-n', '-e',
    `START TRANSACTION; SELECT document_no FROM document WHERE document_no=${documentNo} FOR UPDATE; SELECT 'locked';
     DO SLEEP(${holdSeconds}); DELETE FROM document WHERE document_no=${documentNo}; COMMIT; SELECT 'deleted';`],
  { stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
  let lockedResolve;
  const locked = new Promise(resolve => { lockedResolve = resolve; });
  child.stdout.on('data', chunk => {
    output += chunk;
    if (/^locked$/m.test(output)) lockedResolve();
  });
  const done = new Promise((resolve, reject) => {
    child.on('error', reject);
    child.on('close', code => {
      fs.rmSync(dir, { recursive: true, force: true });
      lockedResolve();
      if (code === 0 && /^deleted$/m.test(output)) resolve();
      else reject(new Error(`The locking session ended with exit ${code} before deleting the row`));
    });
  });
  return { locked, done };
}

const MISSING_MESSAGE = 'This document no longer exists. It may have been deleted by another user, so nothing was saved.';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
  const docs = ownedPdfDocuments(store, marker, [{ key: 'C' }, { key: 'R' }, { key: 'V' }]);
  const [cleared, raced, vanished] = docs;
  const extraReviewers = id => sql.rows(`SELECT reviewerProviderNo FROM DocumentExtraReviewer WHERE documentNo=${id} ORDER BY id`)
    .map(row => row[0]);
  const owned = () => ({ sql, marker, patient, docs, files: docs.map(doc => doc.file) });
  s.cleanup(() => {
    const ids = docs.map(doc => doc.id).filter(id => /^[1-9]\d*$/.test(id || ''));
    if (ids.length) sql.execute(`DELETE FROM DocumentExtraReviewer WHERE documentNo IN (${ids.join(',')})`);
    removeOwnedPdfDocuments(owned());
    assertOwnedPdfDocumentsRemoved(owned());
    h.assert(!ids.length || sql.value(`SELECT COUNT(*) FROM DocumentExtraReviewer WHERE documentNo IN (${ids.join(',')})`) === '0',
      'Owned extra-reviewer rows were not removed');
  });
  seedOwnedPdfDocuments({ sql, store, patient, provider, docs });
  // Only a stored reviewer value: no login, message or record of this provider is touched.
  const other = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${h.sqlString(provider)}
    AND provider_no NOT IN ('-1','') AND status='1' ORDER BY provider_no LIMIT 1`);
  h.assert(other, 'The install has no second active provider to stand for another reviewer');
  const setReview = (doc, reviewer) => sql.execute(reviewer
    ? `UPDATE document SET reviewer=${h.sqlString(reviewer)}, reviewdatetime=NOW() WHERE document_no=${doc.id}`
    : `UPDATE document SET reviewer=NULL, reviewdatetime=NULL WHERE document_no=${doc.id}`);
  const reviewState = doc => sql.value(`SELECT CONCAT_WS('|',COALESCE(reviewer,''),IF(reviewdatetime IS NULL,'no time','time'))
    FROM document WHERE document_no=${doc.id}`);

  let report;
  const openReport = async () => {
    if (report && !report.isClosed()) return report;
    report = await s.popup(s.master, masterRecordLink(s.master, '/documentManager/ViewDocumentReport').first(), 'edoc-report');
    return report;
  };
  const openEditor = async doc => {
    const list = await openReport();
    await list.locator(`#docNo${doc.id}`).waitFor({ state: 'attached' });
    const row = list.locator('tr', { has: list.locator(`#docNo${doc.id}`) });
    const editor = await s.popup(list, row.locator('a[onclick*="/documentManager/ViewEditDocument"]'), 'edoc-edit');
    h.assert(await editor.locator('input[name="mode"]').inputValue() === doc.id, 'The edit popup opened another document');
    return editor;
  };
  // The page's Reviewed button posts the edit form; a saved edit closes the popup and reloads the report.
  const clickReviewed = async (editor, { expectSaved }) => {
    const button = editor.locator('input[type="button"][value="Reviewed"]');
    h.assert(await button.count() === 1 && await button.isVisible(), 'The edit popup does not offer Reviewed');
    const waits = [editor.waitForResponse(response => new URL(response.url()).pathname.endsWith('/documentManager/addEditDocument')
      && response.request().method() === 'POST')];
    if (expectSaved) waits.push(report.waitForEvent('load'));
    const [post] = await Promise.all([...waits, button.click()]);
    h.assert(post.status() < 400, `The Reviewed POST answered HTTP ${post.status()}`);
    if (expectSaved && !editor.isClosed()) await editor.waitForEvent('close');
    return post;
  };
  // Screenshots pair the popup the clinician sees with the stored state, which the popup cannot show.
  const evidence = async (page, name, lines) => {
    await page.evaluate(text => {
      const note = document.createElement('pre');
      note.id = 'carlos-playwright-evidence';
      note.textContent = text;
      note.style.cssText = 'margin:8px;padding:8px;border:2px dashed #555;background:#ffffe0;font:12px monospace;white-space:pre-wrap';
      document.body.prepend(note);
    }, ['Playwright evidence: stored state read from the database after the click', ...lines].join('\n'));
    await h.screenshot(page, s.config.screenshotDir, name);
    await page.evaluate(() => document.getElementById('carlos-playwright-evidence')?.remove());
  };

  await s.step('Reviewed on a popup that still shows a review another user cleared makes the provider the reviewer', async () => {
    setReview(cleared, other);
    const editor = await openEditor(cleared);
    h.assert((await editor.locator('body').innerText()).includes('Reviewed:'), 'The popup does not show the stored review');
    // Another user's Update clears the review after the popup was rendered.
    setReview(cleared, null);
    await clickReviewed(editor, { expectSaved: true });
    await expectValue(sql, `SELECT COALESCE(reviewer,'') FROM document WHERE document_no=${cleared.id}`, provider,
      'The document is not reviewed by the provider who clicked Reviewed');
    h.assert(reviewState(cleared) === `${provider}|time`, `The review was stored without its time: ${reviewState(cleared)}`);
    const extras = extraReviewers(cleared.id);
    h.assert(extras.length === 0, `The review was stored as ${extras.length} extra-reviewer row(s) instead of the document's review`);
    const reopened = await openEditor(cleared);
    await evidence(reopened, 'document-review-cleared', [
      `document.reviewer = ${reviewState(cleared)}`, `DocumentExtraReviewer rows = [${extras.join(', ')}]`]);
    await reopened.close();
  });

  await s.step('Reviewed on a popup that still shows the document unreviewed keeps the other provider\'s first review', async () => {
    const editor = await openEditor(raced);
    // Another provider reviews it after the popup was rendered.
    setReview(raced, other);
    await clickReviewed(editor, { expectSaved: true });
    h.assert(reviewState(raced) === `${other}|time`, `The first review was overwritten: ${reviewState(raced)}`);
    const extras = extraReviewers(raced.id);
    h.assert(extras.length === 1 && extras[0] === provider,
      `The provider's review was not added as one additional review: [${extras.join(', ')}]`);
    const reopened = await openEditor(raced);
    const text = await reopened.locator('body').innerText();
    h.assert((text.match(/Reviewed:/g) || []).length === 2, 'The popup does not list both reviews');
    h.assert(await reopened.locator('input[type="button"][value="Reviewed"]').count() === 0,
      'The popup still offers Reviewed to a provider who has reviewed the document');
    await evidence(reopened, 'document-review-additional', [
      `document.reviewer = ${reviewState(raced)}`, `DocumentExtraReviewer rows = [${extras.join(', ')}]`]);
    await reopened.close();
  });

  await s.step('Reviewed on a document deleted while its save waits for the row says so and stores nothing', async () => {
    setReview(vanished, other);
    const editor = await openEditor(vanished);
    const race = deleteUnderLock(s.config.mysql, vanished.id, 4);
    await race.locked;
    const started = Date.now();
    await Promise.all([editor.waitForEvent('load'), clickReviewed(editor, { expectSaved: false })]);
    await race.done;
    console.log(`    the save waited ${Date.now() - started} ms for the deleting session`);
    h.assert(!editor.isClosed(), 'The popup closed as if the review had been saved');
    const text = await editor.locator('body').innerText();
    h.assert(text.includes(`Error: ${MISSING_MESSAGE}`), 'The popup does not say that the document no longer exists');
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${vanished.id}`) === '0', 'The deleted document came back');
    const extras = extraReviewers(vanished.id);
    h.assert(extras.length === 0, `A review was stored for a deleted document: [${extras.join(', ')}]`);
    await evidence(editor, 'document-review-vanished', [
      `document row present = ${sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${vanished.id}`)}`,
      `DocumentExtraReviewer rows = [${extras.join(', ')}]`]);
    await editor.close();
  });
}

if (require.main === module) runWorkflow('document-review-attestation', workflow);
module.exports = { workflow };
