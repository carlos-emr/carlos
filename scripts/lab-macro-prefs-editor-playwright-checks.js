#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Lab Macros editor's Delete, its delete-all confirmation and its raw JSON editor. lab-macro-tickler defines one
 * macro through the editor; document-viewer-macro(s) seed the macros by SQL; nothing deleted one or used Show macro JSON.
 *
 * User path (as the shared test login): Schedule ▸ Preferences (provider/ViewProviderPreference popup) ▸ the Lab section ▸
 * Lab Recall Macros ▸ Lab Macros (setProviderStaleDate?method=viewLabMacroPrefs, a new window) ▸ Delete ▸ Save; Show macro JSON ▸
 * edit ▸ the Save under it (POST setProviderStaleDate, method=saveLabMacroPrefs). Then the E-Chart's document viewer's Macros menu.
 *
 * Asserted, on owned macros (named with the run marker) only:
 *   1. The editor lists the two owned macros with their comment, tickler message, assignee and date.
 *   2. Delete on the first and Save stores exactly the second, as the editor writes it, and lists only it.
 *   3. The document viewer's Macros menu offers the macro left and not the deleted one.
 *   4. Delete on the last macro and Save asks before storing none: dismissed, nothing is sent and the macro stays;
 *      accepted, no macro is stored and none is listed.
 *   5. LAST: Show macro JSON saves a hand-edited list exactly as typed and lists it; JSON that does not parse is refused
 *      with a message, kept in the editor, and stores nothing.
 *
 * Fixtures: viewerDocuments (document-viewer-review-actions-playwright-checks.js): the owned FAKE- patient and one owned
 * PDF routed New to the test provider and another active provider. The test login's labMacroJSON preference is recorded
 * and holds the two owned macros while the run lasts; afterwards it is put back byte for byte (the row removed if there
 * was none), and the check asserts it. lab-macro-tickler and document-viewer-macro(s) use the same row, so the check must
 * not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { viewerDocuments } = require('./document-viewer-review-actions-playwright-checks');

const TIMEOUT = 60000;
const q = h.sqlString;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Preferences ▸ Lab Macros lists the two owned macros with their fields',
  delete: 'Delete on the first macro and Save stores exactly the second and lists only it',
  viewer: 'the document viewer\'s Macros menu offers the macro left and not the deleted one',
  deleteAll: 'Delete on the last macro asks before storing none: dismissed nothing is sent, accepted no macro is stored',
  raw: 'Show macro JSON saves a hand-edited list as typed; JSON that does not parse is refused, kept and stores nothing',
};

async function workflow(s) {
  const { sql, marker, provider } = s;
  // In the shape the editor writes a macro (assembleJSON), so a save of the form can be compared byte for byte.
  const ackOnly = { name: `${marker} ack`, acknowledge: { comment: 'FAKE macro ack only' }, closeOnSuccess: true };
  const withTickler = { name: `${marker} tickler`, acknowledge: { comment: 'FAKE macro with tickler' }, closeOnSuccess: true,
    tickler: { taskAssignedTo: provider, message: `${marker} macro tickler`, quantity: 2, timeUnits: 7 } };
  // One line: a form sends a textarea's line breaks as CRLF. The spaces, which the editor's own save never writes, show
  // the text is stored as typed rather than rebuilt.
  const typed = `[ { "name": "${marker} typed", "acknowledge": { "comment": "FAKE typed macro" }, "closeOnSuccess": false } ]`;
  const broken = `[{ "name": "${marker} broken", "acknowledge": `;

  const rows = () => sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE provider_no=${q(provider)}
    AND name='labMacroJSON' ORDER BY id`);
  const stored = () => {
    const list = rows();
    h.assert(list.length === 1, `The test login has ${list.length} labMacroJSON rows, not one`);
    return list[0][1];
  };
  const before = rows();
  h.assert(before.length <= 1, 'The test login has more than one labMacroJSON preference row; refusing to change it');
  const floor = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM property'));

  const { docs, openViewer } = viewerDocuments(s, ['macro']);
  s.cleanup(() => {
    h.assert(before.every(([id]) => /^[1-9]\d*$/.test(id)), 'Invalid macro preference identity');
    sql.execute(`DELETE FROM property WHERE provider_no=${q(provider)} AND name='labMacroJSON' AND id > ${floor}`);
    for (const [id, value, isNull] of before) {
      sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : q(value)} WHERE id=${id}
        AND provider_no=${q(provider)} AND name='labMacroJSON'`);
    }
    h.assert(JSON.stringify(rows()) === JSON.stringify(before), 'The test login\'s labMacroJSON preference was not restored');
  });
  const seeded = JSON.stringify([ackOnly, withTickler]);
  if (before.length) sql.execute(`UPDATE property SET value=${q(seeded)} WHERE id=${before[0][0]}`);
  else sql.execute(`INSERT INTO property (name,value,provider_no) VALUES ('labMacroJSON',${q(seeded)},${q(provider)})`);
  h.assert(stored() === seeded, 'The owned macros were not stored');

  let editor;
  /** The macros the editor lists: every macro_<n> row but the blank new one, with its fields. */
  const listed = () => editor.locator('[id^="macro_"]:not(#macro_new)').evaluateAll(divs => divs.map(div => {
    const suffix = div.id.slice('macro_'.length);
    const value = id => document.getElementById(`${id}_${suffix}`)?.value;
    return { name: value('name'), comment: value('comment'), message: value('message'), ticklerTo: value('ticklerTo'),
      quantity: value('quantity'), timeUnits: value('timeUnits'), shown: getComputedStyle(div).display !== 'none' };
  }));
  // The form has two Save buttons: the editor's own, and one under the raw JSON box (#raw). Each step presses the one
  // beside what it edited.
  const saveButton = () => editor.locator('form[name="labMacroPrefsForm"] input[type="submit"]:not(#raw input)');
  const rawSaveButton = () => editor.locator('#raw input[type="submit"]');
  /** Save with `button`, and the editor once it has loaded the save's answer. */
  async function save(button = saveButton()) {
    const [response] = await Promise.all([
      editor.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/setProviderStaleDate'),
        { timeout: TIMEOUT }),
      editor.waitForEvent('load', { timeout: TIMEOUT }),
      button.click(),
    ]);
    h.assert(response.status() === 200, `Saving the macros answered HTTP ${response.status()}`);
  }
  const saidSaved = async () => h.assert(await editor.locator('.alert-success').count() === 1, 'The save did not say it saved');

  await s.step(STEP.open, async () => {
    const prefs = await s.popup(s.schedule, s.schedule.locator('a[onclick*="/provider/ViewProviderPreference?provider_no="]').first(),
      'provider-preferences');
    // The link sits in the Lab section, which the page opens collapsed.
    await prefs.locator('[data-bs-target="#secLab"]').click();
    await prefs.locator('#secLab.show').waitFor({ state: 'visible', timeout: TIMEOUT });
    editor = await s.popup(prefs, prefs.locator('#secLab a[href*="setProviderStaleDate?method=viewLabMacroPrefs"]').first(), 'lab-macro-prefs');
    await editor.locator('#macro_new').waitFor({ state: 'attached', timeout: TIMEOUT });
    const macros = await listed();
    h.assert(JSON.stringify(macros) === JSON.stringify([
      { name: ackOnly.name, comment: ackOnly.acknowledge.comment, message: '', ticklerTo: '', quantity: '0', timeUnits: '1', shown: true },
      { name: withTickler.name, comment: withTickler.acknowledge.comment, message: withTickler.tickler.message,
        ticklerTo: provider, quantity: '2', timeUnits: '7', shown: true },
    ]), `The editor lists ${JSON.stringify(macros)}`);
  });

  await s.step(STEP.delete, async () => {
    await editor.locator('#delete_0').click();
    h.assert(!(await listed())[0].shown, 'Delete did not take the first macro off the editor');
    await save();
    await saidSaved();
    h.assert(stored() === JSON.stringify([withTickler]), `Delete and Save stored ${stored()}, not the second macro alone`);
    const macros = await listed();
    h.assert(macros.length === 1 && macros[0].name === withTickler.name, `After the save the editor lists ${JSON.stringify(macros)}`);
  });

  await s.step(STEP.viewer, async () => {
    const viewer = await openViewer(docs.macro);
    await viewer.locator('.macro-dropdown button.dropdown-toggle').click();
    const offered = (await viewer.locator('.macro-dropdown .dropdown-item').allInnerTexts()).map(text => text.trim())
      .filter(text => text.includes(marker));
    h.assert(JSON.stringify(offered) === JSON.stringify([withTickler.name]),
      `The Macros menu offers ${JSON.stringify(offered)}; the deleted "${ackOnly.name}" must not be one of them`);
    await viewer.close();
  });

  await s.step(STEP.deleteAll, async () => {
    await editor.locator('#delete_0').click();
    const posts = [];
    const onRequest = request => { if (request.method() === 'POST') posts.push(request.url()); };
    editor.on('request', onRequest);
    const dismissed = await h.withExpectedDialogs(editor, () => saveButton().click(), { accept: false });
    await editor.waitForTimeout(500);
    editor.off('request', onRequest);
    h.assert(dismissed.length === 1 && dismissed[0].type === 'confirm', `Save with no macro left did not ask once: ${JSON.stringify(dismissed)}`);
    h.assert(posts.length === 0, `Dismissing the confirmation still sent ${JSON.stringify(posts)}`);
    h.assert(stored() === JSON.stringify([withTickler]), 'Dismissing the confirmation changed the stored macros');

    const accepted = await h.withExpectedDialogs(editor, () => save());
    h.assert(accepted.length === 1 && accepted[0].type === 'confirm', `Save with no macro left did not ask once: ${JSON.stringify(accepted)}`);
    await saidSaved();
    h.assert(stored() === '', `Accepting stored ${JSON.stringify(stored())}, not an empty macro list`);
    h.assert((await listed()).length === 0, 'The editor still lists a macro after all were deleted');
  });

  await s.step(STEP.raw, async () => {
    await editor.locator('a[onclick*="toggleMe"]').click();
    await editor.locator('#macroJSON').waitFor({ state: 'visible' });
    await editor.locator('#macroJSON').fill(typed);
    await save(rawSaveButton());
    await saidSaved();
    h.assert(stored() === typed, `Show macro JSON stored ${JSON.stringify(stored())}, not the list as typed`);
    const macros = await listed();
    h.assert(macros.length === 1 && macros[0].name === `${marker} typed` && macros[0].comment === 'FAKE typed macro',
      `After the typed save the editor lists ${JSON.stringify(macros)}`);

    await editor.locator('a[onclick*="toggleMe"]').click();
    await editor.locator('#macroJSON').fill(broken);
    await save(rawSaveButton());
    h.assert(await editor.locator('.alert-danger').count() === 1 && await editor.locator('.alert-success').count() === 0,
      'JSON that does not parse was not refused with a message');
    h.assert(await editor.locator('#macroJSON').isVisible() && await editor.locator('#macroJSON').inputValue() === broken,
      'The refused JSON is not kept in the editor');
    h.assert(stored() === typed, `The refused JSON changed the stored macros to ${JSON.stringify(stored())}`);
  });
}

if (require.main === module) runWorkflow('lab-macro-prefs-editor', workflow);
module.exports = { workflow, STEP };
