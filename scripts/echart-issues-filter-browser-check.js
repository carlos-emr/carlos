#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Fast browser coverage of the production functions with real DOM/form serialization and
// jQuery UI. The companion -playwright-checks.js verifies persistence against the application.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
function block(from, to) {
  const start = source.indexOf(from); const end = source.indexOf(to, start);
  assert.ok(start >= 0 && end > start); return source.slice(start, end);
}
const noteControls = `<div class="sig" id="sig0"><div id="noteIssues">
  <label for="issueAutocomplete">Assign Issues</label><input id="issueAutocomplete" class="issueAutocomplete">
  <input id="asgnIssues" type="button" value="Assign" onclick="return assignNoteIssue(event)">
  </div></div>`;
const html = `<!doctype html><html><body>
<form name="caseManagementEntryForm" method="post">
<input name="method"><input name="note_edit"><input name="noteId"><input name="ajax"><input name="chain">
<input name="newIssueId" id="newIssueId"><input name="newIssueName" id="newIssueName">${noteControls}</form>
<form id="frmIssueNotes"><input name="issueChange"><input id="issueAutocompleteCPP" class="issueAutocomplete">
<ul id="issueIdList"></ul></form>
<form name="caseManagementViewForm"><input name="method"><input name="resetFilter"><input id="check_issue" value="99">
<button type="button" id="notesFilterToggle" aria-expanded="false" onclick="return showFilter()">Filter</button>
<div id="filter" style="display:none"><input type="checkbox" name="issues" value="12"><input type="checkbox" name="issues" value="13">
<input type="radio" name="note_sort" value="date"><input type="radio" name="note_sort" value="name">
<input type="button" value="Hide" onclick="return filter(false)"><input type="button" value="Reset" onclick="return filter(true)"></div></form>
<button type="button" id="unresolvedHeading" onclick="return clearIssueFilter()">Unresolved Issues</button>
<button type="button" id="resolvedHeading" onclick="return clearIssueFilter()">Resolved Issues</button><div id="notCPP"></div>
</body></html>`;
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}), args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage(); const errors = []; const queries = [];
    page.on('pageerror', error => errors.push(error.message));
    const issueName = 'A & B <img src=x onerror="window.issueInjected=true">';
    await page.route('http://echart.test/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/CaseManagementEntry') {
        queries.push(url.searchParams.get('term'));
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify([{ id: 12, description: issueName, code: 'SAFE' }]) });
      } else await route.fulfill({ contentType: 'text/html', body: html });
    });
    await page.goto('http://echart.test/fixture');
    for (const file of ['jquery-3.7.1.min.js', 'jquery-ui-1.14.2.min.js']) {
      await page.addScriptTag({ path: path.join(root, 'src/main/webapp/library/jquery', file) });
    }
    await page.addScriptTag({ content: `
      var ctx='', demographicNo='1', providerNo='2', fullChart='true', issueUpdatesPending=0, submitIssues=false;
      var caseNote=''; function deferNoteSaveUntilIssues() {return false;} function changeToView() {return true;}
      var sent=[], assignments=[]; var pickIssueMsg='Pick an issue';
      var $=function(id) { var el=typeof id==='string'?document.getElementById(id):id;
        if(el) el.update=function(html) { this.innerHTML=html; }; return el; };
      var Event={stop:function(event) {event.preventDefault();event.stopPropagation();}};
      var Form={serialize:function(form) {return jQuery(form).serialize();}};
      var CarlosAjax={request:function(url, options) {sent.push(options.postBody);
        options.onSuccess({responseText: '<span>listing refreshed</span>'});}};
      function ajaxUpdateIssues(method, div) {assignments.push({method:method, div:div,
        id:document.forms.caseManagementEntryForm.elements.newIssueId.value});}
      ${block('    var disposeNoteIssueSearch =', '    var ajaxRequest;')}
      ${block('    function resetInputElements(', '// find index of month')}
      ${block('    function showFilter(', '    function filterCheckBox(')}
      initializeIssueAutocomplete(document);
    ` });
    async function pick(selector) {
      await page.locator(selector).fill('A&B');
      await page.locator('.ui-autocomplete:visible .ui-menu-item').first().click();
    }
    await pick('#issueAutocomplete');
    assert.equal(await page.locator('#newIssueId').inputValue(), '12');
    assert.equal(await page.locator('#issueAutocomplete').inputValue(), issueName);
    assert.equal(await page.locator('#frmIssueNotes input[name="issue_id"]').count(), 0);
    await page.locator('#asgnIssues').click();
    assert.deepEqual(await page.evaluate(() => assignments), [{method:'makeIssue',div:'sig0',id:'12'}]);
    await pick('#issueAutocompleteCPP');
    await pick('#issueAutocompleteCPP');
    const choice = page.locator('#frmIssueNotes input[name="issue_id"]');
    assert.equal(await choice.count(), 1); assert.equal(await choice.isChecked(), true);
    assert.equal(await choice.locator('..').innerText(), issueName);
    assert.equal(await page.locator('#newIssueName').inputValue(), issueName);
    assert.equal(await page.locator('#issueIdList img').count(), 0);
    assert.equal(await page.evaluate(() => window.issueInjected), undefined);
    const data = await page.locator('#frmIssueNotes').evaluate(form => new URLSearchParams(new FormData(form)).toString());
    assert.equal(new URLSearchParams(data).get('issue_id'), '12');
    assert.ok(queries.every(term => term === 'A&B'));
    console.log('PASS real autocomplete, encoded query, literal label, CPP deduplication, form serialization/isolation');

    for (const id of ['31', '92']) {
      await page.evaluate(({noteControls, id}) => {
        document.querySelector('.sig').outerHTML = noteControls.replace('sig0', `sig${id}`);
        initializeIssueAutocomplete(document);
      }, {noteControls, id});
      await pick('#issueAutocomplete');
      await page.locator('#asgnIssues').click();
      assert.equal(await page.evaluate(() => assignments.at(-1).div), `sig${id}`);
      assert.equal(await page.locator('.ui-autocomplete').count(), 2, 'One note and one CPP menu must remain');
      assert.equal(await page.locator('.ui-helper-hidden-accessible').count(), 2, 'Detached note live regions must be removed');
    }
    console.log('PASS replacement editor binds once, follows latest id, and destroys detached widget');

    await page.locator('#notesFilterToggle').click();
    assert.equal(await page.locator('#filter').isVisible(), true);
    assert.equal(await page.locator('#notesFilterToggle').getAttribute('aria-expanded'), 'true');
    await page.locator('#notesFilterToggle').click();
    assert.equal(await page.locator('#filter').isVisible(), false);
    await page.locator('#notesFilterToggle').click();
    await page.locator('input[name="issues"][value="12"]').check();
    await page.locator('#filter input[value="Hide"]').click();
    let post = new URLSearchParams(await page.evaluate(() => sent.at(-1)));
    assert.equal(post.get('issues'), '12'); assert.equal(post.get('resetFilter'), 'false');
    for (const control of ['#filter input[value="Reset"]', '#unresolvedHeading', '#resolvedHeading']) {
      await page.locator('input[name="issues"][value="12"]').check();
      await page.locator('input[name="note_sort"][value="date"]').check();
      if (control === '#unresolvedHeading') await page.locator(control).press('Enter');
      else if (control === '#resolvedHeading') await page.locator(control).press('Space');
      else await page.locator(control).click();
      post = new URLSearchParams(await page.evaluate(() => sent.at(-1)));
      assert.equal(post.get('resetFilter'), 'true');
      assert.equal(post.has('issues'), false); assert.equal(post.has('note_sort'), false);
      assert.equal(await page.locator('input:checked').count(), 1, 'Only the unrelated CPP selection stays checked');
      assert.equal(await page.locator('#notCPP').innerText(), 'listing refreshed');
    }
    console.log('PASS filter open/hide/apply/reset and both heading clicks send cleared filters and refresh notes');
    const markupChecks = {
      cpp: block("        var noteIssueUl =", '        var noteInfo ='),
      textarea: block('    function createNoteTextarea(', '    function editNote('),
      editInsertion: block('        var input = createNoteTextarea(caseNote', '        if ($F(isFull) == "true") {'),
      newMarkup: block('        ++newNoteCounter;', '        if (changeToView(caseNote, function () { newNote(null); }))'),
      newInsertion: block('            document.getElementById("encMainDiv").insertAdjacentHTML', '            $(safeSigId).addClassName'),
      escape: block('    function escapeNoteText(', '    function completeChangeToView('),
    };
    await page.evaluate(({cpp, textarea, editInsertion, newMarkup, newInsertion, escape}) => {
      const escapeText = new Function(escape + '; return escapeNoteText;')();
      const section = document.createElement('section');
      section.id = 'accessibilityChecks';
      document.body.appendChild(section);
      const renderIssues = new Function('noteIssues', 'getCPP', 'escapeNoteText', cpp + '; return noteIssueUl;');
      section.innerHTML = renderIssues('12;A;Safety & support;13;B;Patient "quoted" issue;14;C;Third issue', () => '', escapeText);
      const createTextarea = new Function('noteEditorLabel', textarea + '; return createNoteTextarea;')(`Encounter "note's" <literal>`);
      const form = document.createElement('form');
      section.appendChild(form);
      form.id = 'encMainDiv';
      const text = '\n\nleading &amp; <literal> "quoted" </textarea><img src=x> 🩺\n';
      const byId = id => document.getElementById(id);
      const renderNew = new Function('createNoteTextarea', '$', 'reason', 'var newNoteCounter = 0;' + newMarkup + newInsertion + '; return input;');
      const newInput = renderNew(createTextarea, byId, text);
      if (newInput.id !== 'caseNote_note01' || newInput.parentNode.id !== 'n01' || newInput.nextSibling.id !== 'sig01') {
        throw new Error('New note editor/signature placement changed');
      }
      if (newInput.previousSibling.id !== 'bgColour01' || form.querySelector('#full01').value !== 'true') {
        throw new Error('New note hidden controls changed');
      }
      const existing = document.createElement('div');
      existing.id = 'n31';
      existing.innerHTML = '<div class="sig" id="sig31">existing signature</div>';
      form.appendChild(existing);
      const renderEdit = new Function('createNoteTextarea', '$', 'payload', 'caseNote', 'txt', 'nId', 'ctx', editInsertion + '; return input;');
      const editInput = renderEdit(createTextarea, byId, text, 'caseNote_note31', 'n31', '31', '');
      if (editInput.nextSibling.id !== 'sig31' || editInput.previousSibling.className !== 'tool-button print-button') {
        throw new Error('Existing note print/editor/signature placement changed');
      }
      if (newInput.value !== text || editInput.value !== text) throw new Error('DOM insertion changed the raw note value');
      const submitted = new FormData(form).getAll('caseNote_note');
      if (submitted.length !== 2 || submitted.some(value => value !== text)) throw new Error('Form serialization changed a note');
    }, markupChecks);
    const accessibility = page.locator('#accessibilityChecks');
    const checkboxes = accessibility.getByRole('checkbox');
    assert.equal(await checkboxes.count(), 3);
    assert.equal(await accessibility.getByRole('checkbox', {name: 'Patient "quoted" issue', exact: true}).isChecked(), true);
    await accessibility.locator('label').first().click();
    assert.equal(await accessibility.getByRole('checkbox', {name: 'Safety & support', exact: true}).isChecked(), false);
    assert.equal(await accessibility.locator('[id="issueId"]').count(), 0);
    assert.equal(await accessibility.locator('li').count(), 2);
    const editors = accessibility.getByRole('textbox', {name: `Encounter "note's" <literal>`, exact: true});
    assert.equal(await editors.count(), 2);
    for (const editor of await editors.all()) {
      assert.equal(await editor.getAttribute('tabindex'), null);
      assert.equal(await editor.getAttribute('wrap'), 'soft');
      assert.equal(await editor.inputValue(), '\n\nleading &amp; <literal> "quoted" </textarea><img src=x> 🩺\n');
    }
    assert.equal(await accessibility.locator('img').count(), 1, 'Only the existing print icon is allowed');
    assert.equal(await accessibility.locator('img').getAttribute('id'), 'print31');
    const master = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/edit.jsp'), 'utf8');
    const spinner = master.match(/<output id="search_spinner">[\s\S]*?<\/output>/);
    assert.ok(spinner);
    await accessibility.evaluate((section, html) => section.insertAdjacentHTML('beforeend', html),
      spinner[0].replace(/<fmt:message[^>]*\/>/, 'Loading fixture'));
    assert.equal(await accessibility.getByRole('status').innerText(), 'Loading fixture');
    console.log('PASS native CPP labels, raw note values/leading newlines, safe editor names/natural tab order, keyboard heading activation, output status');
    assert.deepEqual(errors, []);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
