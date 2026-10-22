#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Forms/eForms ▸ Manage eForms: the Edit pencil opens the Visual eForm Editor for a
 * form the visual editor owns, pre-loaded and inside the Administration panel, and keeps the HTML
 * source editor for every other form (issue #3166).
 *
 * User path: Visual eForm Editor ▸ build and "Save As New Eform" (the template now carries the
 * editor's marker) ▸ Administration ▸ Forms/eForms ▸ Manage eForms ▸ that row's pencil ▸ the editor in
 * the panel's frame, showing the saved field ▸ edit it ▸ "Update Eform" ▸ the same row is updated.
 * The same library shows an eForm Generator-style form with the HTML pencil plus an explicit "Open
 * in Visual eForm Editor" conversion link that warns its scripts are not kept, and a hand-written
 * form with the HTML pencil only. Once the visual form is changed in the HTML source editor its
 * marker no longer matches, and it too falls back to the HTML pencil plus the explicit conversion.
 * Opened outside the panel, the pencil navigates the same tab.
 * Asserts against MariaDB: the visual save stores the fingerprinted marker; "Update Eform" from the pre-loaded
 * editor rewrites the same fid (one row for the name, new text) instead of adding a copy; opening a
 * convertible or unconvertible form in the editor changes nothing stored; the gate answers 400 for a
 * malformed fid and 404 for a missing or deleted form.
 * Fixtures: the eForm built in the editor and three SQL-seeded templates, all named with the run
 * marker; cleanup deletes them by fid and marker and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;
// The marker the editor stamps; its fingerprint covers the rest of the stored markup.
const MARKER_TAG = /<meta name='carlos-eform-editor' content='visual' data-fingerprint='[0-9a-f]{8}'>/;
// A 1x1 PNG, so the converted form's background needs no request to the image library.
const PIXEL = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

function generatorHtml(name) {
  return `<html><head><title>${name}</title><script>function clinicCustomCode(){ return 'kept only in HTML'; }</script></head>
<body><div id="page1" style="position:relative;" >
<img id="BGImage1" src="${PIXEL}" style="position: relative; left: 0px; top: 0px; width:750px">
<input name="generatorField" id="generatorField" type="text" class="noborder" style="position:absolute; left:100px; top:100px; width:200px; height:20px;">
</div></body></html>`;
}

function handWrittenHtml(name) {
  return `<html><head><title>${name}</title><script>function init(){}</script></head>
<body onload="init()"><form method="post" action=""><textarea name="letterBody"></textarea></form></body></html>`;
}

async function workflow(s) {
  const { sql, marker, provider, context, config } = s;
  const visualName = `${marker} Visual`;
  const generatorName = `${marker} Generator`;
  const handName = `${marker} Handwritten`;
  const deletedName = `${marker} Deleted`;
  const firstDefault = `First${marker.slice(-8)}`;
  const secondDefault = `Second${marker.slice(-8)}`;
  const fids = [];
  s.cleanup(() => {
    const owned = sql.rows(`SELECT fid FROM eform WHERE form_name LIKE ${q(`${marker}%`)}`).map(r => r[0]);
    for (const fid of new Set([...fids, ...owned])) {
      sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name LIKE ${q(`${marker}%`)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name LIKE ${q(`${marker}%`)}`) === '0',
      'The owned eForm templates were not removed');
  });
  const seed = (name, html, status = 1) => {
    const fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
      status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(name)},'','visual editor routing fixture',CURDATE(),CURTIME(),${q(provider)},
      ${status},${q(html) /* nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- fixed eForm fixture HTML; text fields are SQL-quoted. */},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid), `The ${name} fixture was not created`);
    fids.push(fid);
    return fid;
  };
  const html = fid => sql.value(`SELECT form_html FROM eform WHERE fid=${fid}`);
  const dialogText = async page => (await page.locator('.ui-dialog:visible').first().innerText()).replace(/\s+/g, ' ');
  const closeDialog = async page => {
    await page.locator('.ui-dialog:visible button, .ui-dialog:visible .ui-dialog-titlebar-close').first().click();
    await page.locator('.ui-dialog:visible').waitFor({ state: 'hidden' }).catch(() => {});
  };
  const shot = async (page, name) => {
    if (config.screenshotDir) await page.screenshot({ path: `${config.screenshotDir}/${name}.png`, fullPage: true }).catch(() => {});
  };

  let visualFid;
  await s.step('a form built in the Visual eForm Editor is saved with the editor\'s marker', async () => {
    const editor = await context.newPage();
    await h.gotoApp(editor, config.baseUrl, '/eform/visualEformEditor');
    await editor.locator('#page_1').waitFor();
    h.assert((await editor.locator('#saveToOscarButton').innerText()).trim() === 'Save As New Eform',
      'The editor opened without an fid is attached to a stored form');
    h.assert(await editor.locator('#gen-editingBanner').isHidden(), 'A blank editor shows the editing banner');
    await editor.locator('h3:has-text("Form Building")').click();
    await editor.locator('a:has-text("Text Box")').first().click();
    await editor.locator('#gen-textBoxDefaultTextId').fill(firstDefault);
    await editor.locator('#gen-textBoxDefaultTextId').blur();
    await editor.locator('#textBoxTemplate').dragTo(editor.locator('#page_1'), { targetPosition: { x: 120, y: 100 } });
    await editor.locator('#page_1 .gen-widget').first().waitFor();
    await editor.locator('h3:has-text("Finalize")').click();
    await editor.locator('#eformNameInput').fill(visualName);
    await editor.locator('#eformNameInput').blur();
    await editor.locator('#saveToOscarButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE form_name=${q(visualName)}`, '1', 'Save As New Eform stored no eForm');
    visualFid = sql.value(`SELECT fid FROM eform WHERE form_name=${q(visualName)}`);
    fids.push(visualFid);
    h.assert(MARKER_TAG.test(html(visualFid)), 'The visual editor did not stamp its marker into the saved template');
    h.assert(/Save Successful/i.test(await dialogText(editor)), 'The editor did not confirm the save');
    await closeDialog(editor);
    h.assert((await editor.locator('#gen-editingFid').innerText()).trim() === visualFid,
      'After saving, the editor does not say which stored form it is attached to');
    await editor.close();
  });

  const generatorFid = seed(generatorName, generatorHtml(generatorName));
  const handFid = seed(handName, handWrittenHtml(handName));
  const deletedFid = seed(deletedName, '<html><head><title>d</title></head><body></body></html>', 0);
  const generatorBefore = html(generatorFid);
  const handBefore = html(handFid);

  await s.step('the editor route validates its fid before rendering', async () => {
    const status = async query => {
      const response = await context.request.get(h.appUrl(config.baseUrl, `/eform/visualEformEditor${query}`), { maxRedirects: 0 });
      try { return response.status(); } finally { await response.dispose(); }
    };
    h.assert(await status('') === 200, 'The editor does not open without an fid');
    h.assert(await status(`?fid=${visualFid}`) === 200, 'The editor does not open for a stored form');
    for (const bad of ['abc', '0', '-1', `${visualFid}x`]) {
      h.assert(await status(`?fid=${encodeURIComponent(bad)}`) === 400, `A malformed fid (${bad}) was not refused with 400`);
    }
    h.assert(await status('?fid=999999999') === 404, 'A missing form did not answer 404');
    h.assert(await status(`?fid=${deletedFid}`) === 404, 'A deleted form did not answer 404');
  });

  const admin = await context.newPage();
  const openLibrary = async () => {
    if (!/\/administration/.test(admin.url())) {
      await h.gotoApp(admin, config.baseUrl, '/administration');
    }
    if (await admin.locator('#collapseForms').isHidden()) await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await admin.locator('a.defaultForms').first().click();
    await admin.locator('#dynamic-content #eformTbl').waitFor({ state: 'visible', timeout: 30000 });
  };
  const rowOf = name => admin.locator('#dynamic-content #eformTbl tbody tr', { hasText: name }).first();
  const frame = async fid => {
    const deadline = Date.now() + 30000;
    let found;
    while (!(found = admin.frames().find(f => f.parentFrame() && /\/eform\/visualEformEditor\?fid=/.test(f.url())
      && new URL(f.url()).searchParams.get('fid') === fid))) {
      h.assert(Date.now() < deadline, `The panel did not frame the editor for fid ${fid}`);
      await admin.waitForTimeout(100);
    }
    await found.waitForLoadState('domcontentloaded');
    return found;
  };

  await s.step('the library routes each row\'s Edit to the editor that can change it safely', async () => {
    await openLibrary();
    const visualRow = rowOf(visualName);
    await visualRow.waitFor();
    h.assert(await visualRow.locator(`a.eformVisualEditorLink[data-editor="visual"][href$="/eform/visualEformEditor?fid=${visualFid}"] i.fa-pencil`).count() === 1,
      'The visual editor\'s own form does not have the Visual eForm Editor as its Edit pencil');
    h.assert(await visualRow.locator(`a.contentLink[data-editor="html"][href$="/eform/efmformmanageredit?fid=${visualFid}"] i.fa-code`).count() === 1,
      'The visual editor\'s form lost its HTML source (Advanced) link');
    const generatorRow = rowOf(generatorName);
    h.assert(await generatorRow.locator(`a.contentLink[data-editor="html"][href$="/eform/efmformmanageredit?fid=${generatorFid}"] i.fa-pencil`).count() === 1,
      'The eForm Generator form does not keep the HTML editor as its Edit pencil');
    h.assert(await generatorRow.locator(`a.eformVisualEditorLink[data-editor="visual-convert"][href$="/eform/visualEformEditor?fid=${generatorFid}"]`).count() === 1,
      'The eForm Generator form is not offered the explicit visual conversion');
    const handRow = rowOf(handName);
    h.assert(await handRow.locator(`a.contentLink[data-editor="html"][href$="/eform/efmformmanageredit?fid=${handFid}"] i.fa-pencil`).count() === 1,
      'The hand-written form does not keep the HTML editor as its Edit pencil');
    h.assert(await handRow.locator('a.eformVisualEditorLink').count() === 0,
      'The hand-written form is offered the Visual eForm Editor, which would drop its scripts');
    await shot(admin, 'eform-library-edit-routing');
  });

  await s.step('the pencil opens the selected form pre-loaded in the editor inside the Administration panel', async () => {
    const pagesBefore = context.pages().length;
    await rowOf(visualName).locator('a.eformVisualEditorLink[data-editor="visual"]').click();
    const editor = await frame(visualFid);
    await editor.locator('#gen-editingBanner').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(context.pages().length === pagesBefore, 'The pencil opened a new tab instead of the panel');
    h.assert(await admin.locator('#dynamic-content.dynamic-iframe-content iframe#myFrame').count() === 1,
      'The editor is not framed inside the Administration panel');
    h.assert((await editor.locator('#gen-editingFid').innerText()).trim() === visualFid, 'The banner names another form');
    h.assert(await editor.locator('#gen-convertedNotice').isHidden(), 'The editor\'s own form is reported as converted');
    h.assert(new URL(await editor.locator('#gen-editHtmlSourceLink').getAttribute('href'), editor.url()).searchParams.get('fid') === visualFid,
      'The banner\'s HTML source link does not point at this form');
    h.assert(await editor.locator('#eformNameInput').inputValue() === visualName, 'The editor did not load the form\'s name');
    h.assert(await editor.locator('#page_1 .gen-widget input[type="text"]').first().inputValue() === firstDefault,
      'The editor did not load the form\'s saved field');
    h.assert((await editor.locator('#saveToOscarButton').innerText()).trim() === 'Update Eform',
      'The pre-loaded editor would save a new copy instead of updating the form');
    await shot(admin, 'visual-editor-in-panel');
  });

  await s.step('Update Eform from the pre-loaded editor rewrites the same form instead of adding a copy', async () => {
    const editor = await frame(visualFid);
    await editor.locator('h3:has-text("Form Building")').click();
    await editor.locator('#page_1 .gen-widget .inputOverride').first().dblclick();
    await editor.locator('#page_1 .gen-widget input[type="text"]').first().fill(secondDefault);
    await editor.locator('h3:has-text("Finalize")').click();
    await editor.locator('#saveToOscarButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE fid=${visualFid} AND form_html LIKE ${q(`%${secondDefault}%`)}`, '1',
      'Update Eform did not store the edited text on the same form');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(visualName)}`) === '1', 'Update Eform created a second eForm');
    h.assert(MARKER_TAG.test(html(visualFid)), 'The updated template lost the editor\'s marker');
    h.assert(/Save Successful/i.test(await dialogText(editor)), 'The editor did not confirm the update');
    await closeDialog(editor);
  });

  await s.step('outside the Administration panel the pencil opens the editor in the same tab', async () => {
    const library = await context.newPage();
    await h.gotoApp(library, config.baseUrl, '/eform/efmformmanager');
    const pencil = library.locator('#eformTbl tbody tr', { hasText: visualName }).first().locator('a.eformVisualEditorLink[data-editor="visual"]');
    await pencil.waitFor();
    const pagesBefore = context.pages().length;
    await Promise.all([library.waitForURL(url => new URL(url).pathname.endsWith('/eform/visualEformEditor')
      && new URL(url).searchParams.get('fid') === visualFid, { timeout: 30000 }), pencil.click()]);
    await library.locator('#gen-editingBanner').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(context.pages().length === pagesBefore, 'The standalone pencil opened a new tab');
    h.assert(await library.locator('#page_1 .gen-widget input[type="text"]').first().inputValue() === secondDefault,
      'The standalone editor did not load the form\'s current field');
    await library.close();
  });
  await s.step('a change made in the HTML source (Advanced) editor demotes the form to explicit conversion', async () => {
    await openLibrary();
    await rowOf(visualName).locator('a.contentLink[data-editor="html"]').click();
    const source = admin.locator('#dynamic-content textarea[name="formHtml"]');
    await source.waitFor({ state: 'visible', timeout: 30000 });
    const before = await source.inputValue();
    h.assert(before.includes(secondDefault), 'The HTML editor did not open the visual form\'s current source');
    await source.fill(before.replace('</head>', '<script>function clinicCustomCode(){ return 1; }</script>\n</head>'));
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.url().includes('/eform/editForm') && r.request().method() === 'POST', { timeout: 60000 }),
      admin.locator('#dynamic-content #savebtn').click(),
    ]);
    h.assert(response.status() < 400, `Saving in the HTML editor answered ${response.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE fid=${visualFid} AND form_html LIKE '%clinicCustomCode%'`, '1',
      'The HTML editor did not store the added script');
    await openLibrary();
    const row = rowOf(visualName);
    h.assert(await row.locator(`a.contentLink[data-editor="html"][href$="/eform/efmformmanageredit?fid=${visualFid}"] i.fa-pencil`).count() === 1,
      'A visual form changed in the HTML editor still defaults to the Visual eForm Editor, which would drop the new script');
    h.assert(await row.locator('a.eformVisualEditorLink[data-editor="visual-convert"]').count() === 1,
      'The changed form is no longer offered the explicit visual conversion');
  });

  await s.step('a convertible form opens visually only by explicit choice, warns that its scripts go, and saves nothing', async () => {
    await openLibrary();
    await rowOf(generatorName).locator('a.eformVisualEditorLink[data-editor="visual-convert"]').click();
    const editor = await frame(generatorFid);
    await editor.locator('.ui-dialog:visible').first().waitFor({ timeout: 30000 });
    h.assert(/not created in the Visual eForm Editor/.test(await dialogText(editor)),
      'Converting a form the editor did not write gave no warning that its scripts are replaced');
    await closeDialog(editor);
    h.assert(await editor.locator('#gen-convertedNotice').isVisible(), 'The banner does not say the form was converted');
    h.assert(await editor.locator('#page_1 input[name="generatorField"]').count() === 1, 'The converted form\'s field is not on the canvas');
    h.assert(html(generatorFid) === generatorBefore, 'Opening a form for conversion changed it before any save');
  });

  await s.step('a form the editor cannot convert leaves the editor detached so nothing can overwrite it', async () => {
    const editor = await context.newPage();
    await h.gotoApp(editor, config.baseUrl, `/eform/visualEformEditor?fid=${handFid}`);
    await editor.locator('.ui-dialog:visible').first().waitFor({ timeout: 30000 });
    h.assert(/unable to convert/i.test(await dialogText(editor)), 'The editor did not say it cannot convert the form');
    await closeDialog(editor);
    h.assert((await editor.locator('#saveToOscarButton').innerText()).trim() === 'Save As New Eform',
      'A refused conversion still attached the editor to the stored form, so a save would overwrite it');
    h.assert(await editor.locator('#gen-editingBanner').isHidden(), 'A refused conversion shows the editing banner');
    h.assert(html(handFid) === handBefore, 'Opening an unconvertible form changed it');
    await editor.close();
  });

}

if (require.main === module) runWorkflow('eform-visual-editor-default-edit', workflow, { openPatient: false });
module.exports = { workflow };
