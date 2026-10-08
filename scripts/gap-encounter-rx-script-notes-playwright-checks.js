#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx "Additional Rx Notes" on a saved prescription: typed on the print window, stored on the script,
 * shown on the printable preview, and still there when the script is reprinted.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ Prescriptions "+" (the Rx page) ▸ Custom Drug ▸
 * Save And Print (the print window, rx/viewScript, opens over the Rx page) ▸ the Additional Rx Notes
 * box (change event, then its button: POST rx/ViewAddRxComment) ▸ later: Rx page ▸ Reprint ▸ the
 * saved script's row.
 * Asserts against MariaDB: Save And Print stores one prescription for the owned patient with the
 * staged drug; a note with punctuation, a quote pair, an ampersand, a percent sign and a line break is
 * stored exactly in prescription.rx_comments; the printable preview (the nested ViewPreview2 frame)
 * shows that note; a second edit through the button replaces it and clearing it stores an empty note;
 * and the Reprint panel's copy of the script comes back with the stored note on its preview. rx-fax-record-binding covers the note only through the fax path.
 * Fixtures: the owned synthetic patient and one custom drug named with the run marker (the drug and
 * its script are removed by marker in cleanup, including any signature rows the print stamped).
 * Implements gap-encounter "write a note on a prescription before printing".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

const q = h.sqlString;

/** The print window is an iframe over the Rx page; poll Playwright's own frame list for it. */
async function viewScriptFrame(page, timeout = 60000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const frame = page.frames().find(f => /\/rx\/viewScript\?/.test(f.url()));
    if (frame) return frame;
    await page.waitForTimeout(250);
  }
  throw new Error('The print window (rx/viewScript) did not open');
}

/**
 * The note box's fetch() never reads the action's empty 204 reply, so Chromium cancels the unread body
 * (net::ERR_ABORTED) a few milliseconds after the page has handled the response (the same artefact
 * lab-forwarding-rules consumes). Consume exactly one such entry per POST the page sent, waiting for
 * the late event, and only after the database proved the save; any other failure stays strict.
 */
function noteBodyTracker(page, recorder) {
  let sent = 0;
  let consumed = 0;
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/rx/ViewAddRxComment')) sent++;
  });
  const entries = () => recorder.requestFailures.filter(entry => entry.resourceType === 'fetch'
    && entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/rx/ViewAddRxComment'));
  return async () => {
    const deadline = Date.now() + 5000;
    while (entries().length < sent - consumed && Date.now() < deadline) await page.waitForTimeout(100);
    for (const entry of entries()) {
      recorder.requestFailures.splice(recorder.requestFailures.indexOf(entry), 1);
      consumed++;
    }
  };
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  const drug = `${marker}-notes`;
  s.cleanup(() => {
    clearOwnedPrescriptionRows(sql, patient, marker);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName LIKE ${q(`${marker}%`)}`) === '0',
      'Owned prescription drugs were not removed');
  });
  const note = `Take with food, "twice" & after 50% of meals\nNo alcohol ${marker}`;
  let consumeNoteBodies;

  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
  consumeNoteBodies = noteBodyTracker(rx, s.recorder);
  let scriptNo;
  let script;

  await s.step('Custom Drug ▸ Save And Print stores a prescription and opens the print window', async () => {
    const key = await stageCustomDrug(rx, drug);
    await rx.locator(`#instructions_${key}`).fill('Take one tablet daily');
    await rx.locator(`#instructions_${key}`).blur();
    await rx.locator('#saveButton').click();
    script = await viewScriptFrame(rx);
    scriptNo = new URL(script.url()).searchParams.get('scriptId');
    h.assert(/^[1-9]\d*$/.test(scriptNo), 'Save And Print opened no saved script');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE script_no=${scriptNo} AND demographic_no=${patient}`) === '1',
      'Save And Print did not store a prescription for the owned patient');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE script_no=${scriptNo} AND demographic_no=${patient} AND customName=${q(drug)}`) === '1',
      'The saved script does not carry the staged drug');
    await script.locator('#additionalNotes').waitFor({ state: 'visible', timeout: 30000 });
    // Allow the initial pharmacy selection to finish before testing an explicit preview reload.
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  });

  const previewNote = async frame => {
    const preview = frame.childFrames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    h.assert(preview, 'The print window has no preview frame');
    return (await preview.locator('#additNotes').textContent()) || '';
  };
  // The preview is rewritten by the page's own script after the save request returns.
  const previewShows = async (frame, expected, message) => {
    const deadline = Date.now() + 10000;
    let shown;
    do {
      shown = (await previewNote(frame)).replace(/\r/g, '').trim();
      if (shown === expected.trim()) return;
      await frame.waitForTimeout(150);
    } while (Date.now() < deadline);
    h.assert(false, message);
  };

  await s.step('typing a note stores it exactly on the script and shows it on the preview', async () => {
    const saved = rx.waitForResponse(r => /\/rx\/ViewAddRxComment/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 });
    await script.locator('#additionalNotes').fill(note);
    await script.locator('#additionalNotes').blur();
    h.assert((await saved).ok(), 'rx/ViewAddRxComment was refused');
    await expectValue(sql, `SELECT COUNT(*) FROM prescription WHERE script_no=${scriptNo} AND rx_comments=${q(note)}`, '1',
      'The note typed in the print window was not stored exactly on the prescription');
    await previewShows(script, note, 'The printable preview does not show the typed note');
    // A pharmacy change reloads this frame. It must restore the current editor text,
    // including an edit made since the outer print window was rendered.
    const preview = script.childFrames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    await Promise.all([
      preview.waitForNavigation({ waitUntil: 'load' }),
      preview.evaluate(() => window.location.reload())
    ]);
    await previewShows(script, note, 'Reloading the preview restored the old note');
    // The editable Save And Print view requires POST; GET opens only a read-only reprint.
    await Promise.all([
      script.waitForNavigation({ waitUntil: 'load' }),
      script.evaluate(() => {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = window.location.href;
        const token = document.querySelector('input[name="CSRF-TOKEN"]');
        if (!token) throw new Error('The editable script has no CSRF token');
        form.appendChild(token.cloneNode(true));
        document.body.appendChild(form);
        form.submit();
      })
    ]);
    h.assert(await script.locator('#additionalNotes').inputValue() === note,
      'Reopening the saved script did not initialize the editor with its stored note');
    await previewShows(script, note, 'Reopening the saved script erased its note from the preview');
    await consumeNoteBodies();
  });

  await s.step('the note button replaces the note, and clearing it stores an empty note', async () => {
    const second = `Second note ${marker}`;
    let saved = rx.waitForResponse(r => /\/rx\/ViewAddRxComment/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 });
    await script.locator('#additionalNotes').fill(second);
    await script.locator('#saveAdditionalNotes').click();
    h.assert((await saved).ok(), 'The note button request was refused');
    await expectValue(sql, `SELECT rx_comments FROM prescription WHERE script_no=${scriptNo}`, second, 'The second note did not replace the first');
    await previewShows(script, second, 'The preview kept the first note');
    await consumeNoteBodies();
    saved = rx.waitForResponse(r => /\/rx\/ViewAddRxComment/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 });
    await script.locator('#additionalNotes').fill('');
    await script.locator('#saveAdditionalNotes').click();
    h.assert((await saved).ok(), 'Clearing the note was refused');
    await expectValue(sql, `SELECT COALESCE(rx_comments,'') FROM prescription WHERE script_no=${scriptNo}`, '', 'Clearing the note left the old text stored');
    await consumeNoteBodies();
    // The final value stays stored for the reprint step.
    saved = rx.waitForResponse(r => /\/rx\/ViewAddRxComment/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 });
    await script.locator('#additionalNotes').fill(note);
    await script.locator('#saveAdditionalNotes').click();
    h.assert((await saved).ok(), 'Restoring the note was refused');
    await expectValue(sql, `SELECT COUNT(*) FROM prescription WHERE script_no=${scriptNo} AND rx_comments=${q(note)}`, '1',
      'The restored note was not stored');
    await consumeNoteBodies();
  });

  await s.step('the Rx page Reprint panel brings the script back with its stored note', async () => {
    // The chart's Prescriptions link reuses the open Rx window; a reload shows the saved script in its Reprint panel.
    await rx.reload({ waitUntil: 'networkidle' });
    const again = rx;
    await again.locator('a').filter({ hasText: /^Reprint$/ }).first().click();
    const row = again.locator(`#reprint [onclick*="reprint2('${scriptNo}')"]`).first();
    await row.waitFor({ state: 'visible', timeout: 20000 });
    await row.click();
    const reprint = await viewScriptFrame(again);
    await reprint.locator('#preview').waitFor({ state: 'attached', timeout: 30000 });
    await again.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    h.assert(new URL(reprint.url()).searchParams.get('scriptId') === scriptNo, 'The reprint opened another script');
    await previewShows(reprint, note, 'The reprinted preview lost the stored note');
    // A reprint is read-only: the Additional Rx Notes box is not offered, the stored note is only shown.
    h.assert(await reprint.locator('#additionalNotes').count() === 0, 'A reprint offers the Additional Rx Notes box');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-script-notes', workflow, { openPatient: true });
module.exports = { workflow };
