#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4370, the consultant letter (Letterhead, form/formConsultant): Print shows the print view, prints, then asks
// "Do you wish to make changes?". OK used to send the form with submit=save, so going back to editing saved the letter
// although the clinician never pressed Save. Print now never saves: OK returns to the editor, Cancel stays on the print
// view with the buttons shown, and only Save (or Save & Exit) stores the letter.
//
// User path: a new letter opened for the owned patient (form/formConsultant?demographic_no=&formId=0), Comments typed,
// Print pressed twice (answered OK, then Cancel), then Save pressed and confirmed. window.print is stubbed and counted,
// so the browser's own print dialog never opens. Every POST to form/formname is recorded: there must be none until Save.
//
// Fixtures: the owned synthetic patient; the letter Save stores. Nothing is added to the Forms menu, so this check does
// not need EXCLUSIVE=1. Cleanup deletes the patient's formConsult rows and asserts them gone.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const NAME = 'form-consultant-print-no-save';

function letterRows(sql, patient) {
  return Number(sql.value(`SELECT COUNT(*) FROM formConsult WHERE demographic_no=${patient}`));
}

async function shown(page, id) {
  return page.evaluate(elementId => getComputedStyle(document.getElementById(elementId)).visibility, id);
}

async function workflow(s) {
  const { sql, patient, context, marker } = s;
  h.assert(/^[1-9]\d*$/.test(patient), 'The owned synthetic patient was not created');
  s.cleanup(() => {
    sql.execute(`DELETE FROM formConsult WHERE demographic_no=${patient}`);
    h.assert(letterRows(sql, patient) === 0, "The owned patient's formConsult rows were not removed");
  });
  h.assert(letterRows(sql, patient) === 0, 'The owned patient already has a consultant letter');

  const comments = `${marker} consultant letter comments`;
  const page = await context.newPage();
  const posts = [];
  page.on('request', request => {
    if (request.method() === 'POST' && /\/form\/formname$/.test(new URL(request.url()).pathname)) posts.push(request.url());
  });

  try {
    await s.step('Consultant letter: a new letter opens for the owned patient', async () => {
      const response = await h.gotoApp(page, s.config.baseUrl, `/form/formConsultant?demographic_no=${patient}&formId=0`);
      h.assert(response && response.status() === 200, `The new letter answered HTTP ${response && response.status()}`);
      await h.assertNotErrorPage(page, 'consultant letter');
      await page.locator('#comments').fill(comments);
      await page.evaluate(() => {
        window.__prints = 0;
        window.print = () => { window.__prints++; };
      });
    });

    await s.step('Consultant letter: Print answered OK returns to editing and saves nothing', async () => {
      const dialogs = await h.withExpectedDialogs(page, () => page.locator('#printbut').click(), { accept: true });
      h.assert(dialogs.length === 1, `Print raised ${dialogs.length} dialogs, not the one "make changes?" question`);
      await page.waitForTimeout(1500);
      h.assert(await page.evaluate(() => window.__prints) === 1, 'Print did not call the browser print exactly once');
      h.assert(posts.length === 0, `OK after Print sent the form (${posts.length} POST to form/formname)`);
      h.assert(letterRows(sql, patient) === 0, 'OK after Print stored a consultant letter');
      h.assert(await shown(page, 'buttons') === 'visible', 'the buttons are not shown again after OK');
      h.assert(await shown(page, 'textareaDiv') === 'visible', 'the Comments editor is not shown again after OK');
      h.assert(await page.locator('#comments').inputValue() === comments, 'the typed Comments changed after OK');
    });

    await s.step('Consultant letter: Print answered Cancel stays on the print view and saves nothing', async () => {
      await h.withExpectedDialogs(page, () => page.locator('#printbut').click(), { accept: false });
      await page.waitForTimeout(1500);
      h.assert(await page.evaluate(() => window.__prints) === 2, 'the second Print did not call the browser print');
      h.assert(posts.length === 0, `Cancel after Print sent the form (${posts.length} POST to form/formname)`);
      h.assert(letterRows(sql, patient) === 0, 'Cancel after Print stored a consultant letter');
      h.assert(await shown(page, 'buttons') === 'visible', 'the buttons are not shown again after Cancel');
      h.assert(await shown(page, 'textDiv') === 'visible', 'Cancel left the print view');
    });

    await s.step('Consultant letter: Save still stores the letter once with the typed Comments', async () => {
      await page.evaluate(() => {
        // Back to the editor before saving, as a clinician would after the Cancel above.
        document.getElementById('textareaDiv').style.visibility = 'visible';
      });
      // Save posts form/formname, which redirects to form/forwardname to redisplay the stored letter.
      await Promise.all([
        page.waitForURL(url => /\/form\/forwardname$/.test(url.pathname), { waitUntil: 'load' }),
        h.withExpectedDialogs(page, () => page.locator('#savebut').click(), { accept: true }),
      ]);
      await h.assertNotErrorPage(page, 'saved consultant letter');
      h.assert(posts.length === 1, `Save sent the form ${posts.length} times, not once`);
      h.assert(letterRows(sql, patient) === 1, `Save stored ${letterRows(sql, patient)} consultant letters, not one`);
      const stored = sql.value(`SELECT comments FROM formConsult WHERE demographic_no=${patient}`);
      h.assert(stored === comments, 'the stored letter does not hold the typed Comments');
    });
  } finally {
    await page.close();
  }
}

if (require.main === module) runWorkflow(NAME, workflow, { openMaster: false });
module.exports = { workflow };
