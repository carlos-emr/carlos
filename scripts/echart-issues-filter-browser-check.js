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
  assert.ok(start >= 0 && end > start, `Missing or misordered source markers: ${from} -> ${to}`);
  return source.slice(start, end);
}
const noteControls = `<div class="sig" id="sig0"><div id="noteIssues">
  <label for="issueAutocomplete">Assign Issues</label><input id="issueAutocomplete" class="issueAutocomplete">
  <input id="asgnIssues" type="button" value="Assign" onclick="return assignNoteIssue(event)">
  </div></div>`;
// Serve the production CPP markup as fixture HTML so its labels still exercise the browser parser.
const renderIssues = new Function('noteIssues', 'getCPP',
  block('    function escapeNoteText(', '    function completeChangeToView(') +
  block('        var noteIssueUl =', '        var noteInfo =') + '; return noteIssueUl;');
const cppIssues = renderIssues('12;A;Safety & support;13;B;Patient "quoted" issue;14;C;Third issue', () => '');
const html = `<!doctype html><html><body>
<form name="caseManagementEntryForm" method="post">
<input name="method"><input name="note_edit"><input name="noteId"><input name="ajax"><input name="chain">
<input name="newIssueId" id="newIssueId"><input name="newIssueName" id="newIssueName">${noteControls}</form>
<form id="frmIssueNotes"><input name="issueChange"><input id="issueAutocompleteCPP" class="issueAutocomplete">
<ul id="issueIdList"></ul></form>
<form name="caseManagementViewForm"><input name="method"><input name="resetFilter"><input id="check_issue" name="check_issue" value="99">
<button type="button" id="notesFilterToggle" aria-expanded="false" onclick="return showFilter()">Filter</button>
<div id="filter" style="display:none"><input type="checkbox" name="issues" value="12"><input type="checkbox" name="issues" value="13">
<input type="checkbox" name="filter_providers" value="200"><input type="checkbox" name="filter_roles" value="7">
<input type="radio" name="note_sort" value="date"><input type="radio" name="note_sort" value="name">
<input type="button" value="Hide" onclick="return filter(false)"><input type="button" value="Reset" onclick="return filter(true)"></div></form>
<button type="button" id="unresolvedHeading" onclick="return clearIssueFilter()">Unresolved Issues</button>
<button type="button" id="resolvedHeading" onclick="return clearIssueFilter()">Resolved Issues</button><div id="notCPP"></div>
<section id="accessibilityChecks">${cppIssues}</section>
</body></html>`;
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}), args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage(); const errors = []; const queries = [];
    page.on('pageerror', error => errors.push(error.message));
    const issueName = 'A & B <img src=x onerror="window.issueInjected=true">';
    const validIssueResponse = {contentType: 'application/json',
      body: JSON.stringify([{id: 12, description: issueName, code: 'SAFE'}])};
    let issueResponse = validIssueResponse;
    await page.route('http://echart.test/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/CaseManagementEntry') {
        queries.push(url.searchParams.get('term'));
        await route.fulfill(issueResponse);
      } else await route.fulfill({ contentType: 'text/html', body: html });
    });
    await page.goto('http://echart.test/fixture');
    for (const file of ['jquery-3.7.1.min.js', 'jquery-ui-1.14.2.min.js']) {
      await page.addScriptTag({ path: path.join(root, 'src/main/webapp/library/jquery', file) });
    }
    await page.addScriptTag({ content: `
      var ctx='', demographicNo='1', providerNo='2', fullChart='true', issueUpdatesPending=0, submitIssues=false;
      var caseNote=''; function deferNoteSaveUntilIssues() {return false;} function changeToView() {return true;}
      var replaceFiltersOnResponse=true;
      var sent=[], assignments=[]; var pickIssueMsg='Pick an issue';
      var $=function(id) { var el=typeof id==='string'?document.getElementById(id):id;
        if(el) el.update=function(text) {
          this.textContent=text;
          if (window.replaceFiltersOnResponse) {
            var previous=document.forms.caseManagementViewForm;
            var replacement=previous.cloneNode(true);
            replacement.querySelectorAll('input').forEach(function(control) {control.checked=false;});
            replacement.querySelector('#check_issue').value='';
            previous.replaceWith(replacement);
          }
        }; return el; };
      var Event={stop:function(event) {event.preventDefault();event.stopPropagation();}};
      var Form={serialize:function(form) {return jQuery(form).serialize();}};
      var CarlosAjax={request:function(url, options) {sent.push(options.postBody);
        options.onSuccess({responseText: 'listing refreshed'});}};
      function ajaxUpdateIssues(method, div) {assignments.push({method:method, div:div,
        id:document.forms.caseManagementEntryForm.elements.newIssueId.value});}
      ${block('    var disposeNoteIssueSearch =', '    var ajaxRequest;')}
      ${block('    function resetInputElements(', '// find index of month')}
      ${block('    function showFilter(', '    function filterCheckBox(')}
      ${block('    function setIssueCheckbox(', '    function assignNoteAjax(')}
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
      await page.evaluate(id => {
        const signature = document.createElement('div');
        signature.className = 'sig'; signature.id = `sig${id}`;
        const issues = document.createElement('div'); issues.id = 'noteIssues';
        const label = document.createElement('label');
        label.htmlFor = 'issueAutocomplete'; label.textContent = 'Assign Issues';
        const search = document.createElement('input');
        search.id = 'issueAutocomplete'; search.className = 'issueAutocomplete';
        const assign = document.createElement('input');
        assign.id = 'asgnIssues'; assign.type = 'button'; assign.value = 'Assign';
        assign.addEventListener('click', event => assignNoteIssue(event));
        issues.append(label, search, assign); signature.appendChild(issues);
        document.querySelector('.sig').replaceWith(signature);
        initializeIssueAutocomplete(document);
      }, id);
      await pick('#issueAutocomplete');
      await page.locator('#asgnIssues').click();
      assert.equal(await page.evaluate(() => assignments.at(-1).div), `sig${id}`);
      assert.equal(await page.locator('.ui-autocomplete').count(), 2, 'One note and one CPP menu must remain');
      assert.equal(await page.locator('.ui-helper-hidden-accessible').count(), 2, 'Detached note live regions must be removed');
    }
    console.log('PASS replacement editor binds once, follows latest id, and destroys detached widget');

    const malformedItems = [null, false, 42, 'invalid', {}, {description: null}, {description: 17},
      {description: {}}, {description: []}];
    const searchCases = [
      {name: 'HTML error page', contentType: 'text/html', body: '<html><body>Login required</body></html>'},
      {name: 'malformed JSON', contentType: 'application/json', body: '[{"description":'},
      {name: 'null JSON', contentType: 'application/json', body: 'null'},
      {name: 'object JSON', contentType: 'application/json', body: '{"description":"not an array"}'},
      {name: 'invalid items', contentType: 'application/json', body: JSON.stringify(malformedItems)},
      {name: 'HTTP failure', status: 503, contentType: 'text/plain', body: 'Unavailable'},
      {name: 'script response', contentType: 'text/javascript', body: 'window.issueJsonInjected=true;'},
      {name: 'mixed items with plain content type', contentType: 'text/plain', body: JSON.stringify([
        ...malformedItems, {id: 12, description: ' Safe & Sound ', code: 'SAFE'},
      ]), expected: [{id: 12, label: 'Safe & Sound (SAFE)', value: 'Safe & Sound'}]},
    ];
    for (const selector of ['#issueAutocomplete', '#issueAutocompleteCPP']) {
      const search = page.locator(selector);
      for (const {name, expected = [], ...response} of searchCases) {
        issueResponse = response;
        await search.evaluate(input => {
          window.issueSearchResponses = [];
          jQuery(input).one('autocompleteresponse', (event, ui) => window.issueSearchResponses.push(ui.content));
        });
        await search.fill(name);
        await page.waitForFunction(() => window.issueSearchResponses.length > 0, null, {timeout: 5000});
        assert.deepEqual(await page.evaluate(() => window.issueSearchResponses), [expected], name);
        assert.equal(await search.evaluate(input => jQuery(input).autocomplete('instance').pending), 0, name);
        assert.equal(await search.evaluate(input => input.classList.contains('ui-autocomplete-loading')), false, name);
      }
      issueResponse = validIssueResponse;
      await pick(selector);
    }
    assert.equal(await page.evaluate(() => window.issueJsonInjected), undefined);
    assert.equal(await page.locator('#newIssueId').inputValue(), '12');
    assert.equal(await choice.count(), 1);
    assert.equal(await choice.isChecked(), true);
    console.log('PASS JSON-only autocomplete handles HTML/malformed/failed responses and invalid items, clears pending state, and recovers in both forms');

    await page.locator('#notesFilterToggle').click();
    assert.equal(await page.locator('#filter').isVisible(), true);
    assert.equal(await page.locator('#notesFilterToggle').getAttribute('aria-expanded'), 'true');
    await page.locator('#notesFilterToggle').click();
    assert.equal(await page.locator('#filter').isVisible(), false);
    await page.locator('#notesFilterToggle').click();
    await page.locator('input[name="issues"][value="12"]').check();
    await page.locator('input[name="filter_providers"]').check();
    await page.locator('input[name="filter_roles"]').check();
    await page.locator('input[name="note_sort"][value="date"]').check();
    await page.locator('#filter input[value="Hide"]').click();
    let post = new URLSearchParams(await page.evaluate(() => sent.at(-1)));
    assert.equal(post.get('issues'), '12'); assert.equal(post.get('resetFilter'), 'false');
    assert.equal(await page.locator('#check_issue').inputValue(), '99');
    for (const name of ['issues', 'filter_providers', 'filter_roles', 'note_sort']) {
      assert.equal(await page.locator(`input[name="${name}"]:checked`).count(), 1, 'Apply lost ' + name);
    }
    for (const control of ['#filter input[value="Reset"]', '#unresolvedHeading', '#resolvedHeading']) {
      await page.locator('input[name="issues"][value="12"]').check();
      await page.locator('input[name="note_sort"][value="date"]').check();
      await page.locator('input[name="filter_providers"]').check();
      await page.locator('input[name="filter_roles"]').check();
      await page.locator('#check_issue').fill('99');
      if (control === '#unresolvedHeading') await page.locator(control).press('Enter');
      else if (control === '#resolvedHeading') await page.locator(control).press('Space');
      else await page.locator(control).click();
      post = new URLSearchParams(await page.evaluate(() => sent.at(-1)));
      const reset = control.includes('Reset');
      assert.equal(post.get('resetFilter'), String(reset));
      assert.equal(post.has('issues'), false);
      assert.equal(post.get('check_issue'), '');
      for (const [name, value] of [['filter_providers', '200'], ['filter_roles', '7'], ['note_sort', 'date']]) {
        assert.equal(post.get(name), reset ? null : value);
        assert.equal(await page.locator(`input[name="${name}"]:checked`).count(), reset ? 0 : 1);
      }
      assert.equal(await page.locator('#frmIssueNotes input:checked').count(), 1);
      assert.equal(await page.locator('#notCPP').innerText(), 'listing refreshed');
    }
    await page.evaluate(() => setIssueCheckbox('13'));
    assert.equal(await page.locator('input[name="issues"][value="13"]').isChecked(), true,
      'A sidebar issue must remain selectable after checked states were restored');
    await page.locator('#filter input[value="Hide"]').click();
    post = new URLSearchParams(await page.evaluate(() => sent.at(-1)));
    assert.deepEqual(post.getAll('issues'), ['13']);
    assert.equal(post.get('filter_providers'), '200');
    assert.equal(post.get('filter_roles'), '7');
    assert.equal(post.get('note_sort'), 'date');
    console.log('PASS filter open/hide/apply/reset; both headings retain provider/role/sort after form replacement and clear only issues');
    await page.addScriptTag({ content: `
      var noteSwitchSavePending=false, issueUpdateFailed=false, queuedNoteSave=null, noteIssuesChanged=true;
      var updateIssueError='Issue update failed', noteSavingMessage='Saving', unsavedNoteWarning='Save draft?';
      var origCaseNote='', origObservationDate='date';
      var $F=function(id) {return $(id).value;};
      ${block('    function changeToView(', '    function finishChangeToView(')}
      ${block('    function deferNoteSaveUntilIssues(', '    function ajaxUpdateIssues(')}
    ` });
    await page.evaluate(() => {
      const form = document.forms.caseManagementEntryForm;
      const host = document.createElement('div'); host.id = 'n999'; form.appendChild(host);
      const date = document.createElement('input'); date.id = 'observationDate'; date.value = 'date'; form.appendChild(date);
      const view = document.forms.caseManagementViewForm;
      const snapshot = () => new URLSearchParams(new FormData(view)).toString();
      window.alert = () => {};
      for (const outcome of ['pending issues', 'issue failure', 'save pending', 'cancel', 'save failure', 'success']) {
        const editor = document.createElement('textarea');
        editor.id = 'guardEditor'; editor.value = 'dirty draft'; host.replaceChildren(editor);
        caseNote = 'guardEditor'; noteIssuesChanged = true;
        issueUpdatesPending = ['pending issues', 'success'].includes(outcome) ? 1 : 0;
        issueUpdateFailed = outcome === 'issue failure'; noteSwitchSavePending = outcome === 'save pending'; queuedNoteSave = null;
        view.querySelectorAll('input[type="checkbox"]').forEach(input => {input.checked = true;});
        view.querySelector('input[name="note_sort"][value="date"]').checked = true;
        document.getElementById('check_issue').value = '99';
        const before = snapshot(); const count = sent.length; const saves = [];
        window.confirm = () => outcome !== 'cancel';
        window.ajaxSaveNote = (sig, id, text, done) => saves.push(done);
        window.finishChangeToView = id => document.getElementById(id).remove();
        document.getElementById('unresolvedHeading').click();
        if (snapshot() !== before || sent.length !== count) throw new Error(outcome + ' changed filters before the save');
        if (outcome === 'save failure') saves[0](false);
        if (outcome !== 'success') {
          if (snapshot() !== before || document.getElementById('guardEditor').value !== 'dirty draft') throw new Error(outcome + ' lost draft/filter state');
          continue;
        }
        finishIssueUpdate(true);
        if (snapshot() !== before || saves.length !== 1) throw new Error('Pending issue completion changed filters before saving');
        saves[0](true);
        const post = new URLSearchParams(sent.at(-1));
        if (sent.length !== count + 1 || post.get('resetFilter') !== 'false' || post.get('check_issue') !== '' || post.has('issues')) {
          throw new Error('Successful save did not clear exactly the issue constraints');
        }
        for (const [name, value] of [['filter_providers', '200'], ['filter_roles', '7'], ['note_sort', 'date']]) {
          if (post.get(name) !== value) throw new Error('Successful save lost ' + name);
        }
      }
      host.remove(); date.remove(); caseNote = '';
    });
    console.log('PASS heading guards preserve filters/draft on pending/cancel/failure and retain non-issue filters after save');
    const markupChecks = {
      textarea: block('    function createNoteTextarea(', '    function editNote('),
      editInsertion: block('        var input = createNoteTextarea(caseNote', '        if ($F(isFull) == "true") {'),
      newMarkup: block('        ++newNoteCounter;', '        if (changeToView(caseNote, function () { newNote(null); }))'),
      newInsertion: block('            document.getElementById("encMainDiv").insertAdjacentHTML', '            $(safeSigId).addClassName'),
    };
    await page.evaluate(({textarea, editInsertion, newMarkup, newInsertion}) => {
      const section = document.getElementById('accessibilityChecks');
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
      const signature = document.createElement('div');
      signature.className = 'sig'; signature.id = 'sig31'; signature.textContent = 'existing signature';
      existing.appendChild(signature);
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
