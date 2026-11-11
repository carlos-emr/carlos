/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Issue #6090: an issue change or delete must reach the patient issue the clinician clicked.
// These tests run the production issue-row handlers and the editor's issue-refresh path from
// newCaseManagementView.js.jsp. Only the transport, the DOM and Prototype's Form.serialize are
// synthetic; each test asserts the request body the server would receive.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');

function block(from, to) {
  const start = source.indexOf(from);
  const end = source.indexOf(to, start);
  assert.ok(start >= 0 && end > start, `Missing or misordered source markers: ${from} -> ${to}`);
  return source.slice(start, end);
}

function control(props) {
  const attributes = { ...props.attributes };
  return Object.assign({
    disabled: false,
    getAttribute: name => (name in attributes ? attributes[name] : null),
    setAttribute: (name, value) => { attributes[name] = String(value); },
    removeAttribute: name => { delete attributes[name]; },
    attributes,
  }, props);
}

/**
 * One rendered issue row: its checkbox, Delete/Change links and the acute radio pair, named as
 * noteIssueList.jsp names them (position in the list) and carrying the patient issue id.
 */
function row(form, index, caseIssueId, { acute = false, readOnly = false } = {}) {
  const name = `issueCheckList[${index}].issue.acute`;
  const radio = value => control({
    tagName: 'INPUT', type: 'radio', name, value, form,
    checked: (value === 'true') === acute, defaultChecked: (value === 'true') === acute, disabled: readOnly,
    attributes: { 'data-case-issue-id': String(caseIssueId), 'data-issue-field': 'acute' },
  });
  const link = action => control({ tagName: 'A', attributes: { 'data-issue-action': action, 'data-case-issue-id': String(caseIssueId) } });
  return {
    caseIssueId: String(caseIssueId),
    checkbox: control({ tagName: 'INPUT', type: 'checkbox', name: `issueCheckList[${index}].checked`, checked: true,
      attributes: { 'data-issue-id': String(caseIssueId) } }),
    acuteTrue: radio('true'), acuteFalse: radio('false'), deleteLink: link('delete'), changeLink: link('change'),
  };
}

function harness() {
  const requests = []; const reloads = []; const alerts = []; const removed = [];
  const form = { method: { value: 'save' }, ajax: { value: 'false' }, elements: [] };
  let rows = [];
  const container = { id: 'sig31' };
  const noteIssues = {
    parentNode: container,
    querySelectorAll: selector => {
      assert.equal(selector, 'input[type="radio"][data-issue-field], a[data-issue-action]');
      return rows.flatMap(r => [r.acuteTrue, r.acuteFalse, r.deleteLink, r.changeLink]);
    },
  };
  form.querySelectorAll = selector => {
    assert.equal(selector, 'input[type="checkbox"][data-issue-id]');
    return rows.map(r => r.checkbox);
  };
  // Render (or re-render, as the updater does) the checklist in the given order.
  function render(issues) {
    rows = issues.map((issue, index) => row(form, index, issue.id, issue));
    form.elements = rows.flatMap(r => [r.checkbox, r.acuteTrue, r.acuteFalse]);
    return rows;
  }
  const fields = { noteIssues, issueAutocomplete: { value: '', disabled: false }, asgnIssues: { disabled: false } };
  const context = {
    ctx: '/carlos', chartContext: '', demographicNo: '101', caseNote: 'caseNote_note01', savingNoteError: 'save failed',
    document: { forms: { caseManagementEntryForm: form } },
    // Prototype's Form.serialize: enabled controls only, checked radios and checkboxes only.
    Form: { serialize: frm => new URLSearchParams([['method', frm.method.value], ['ajax', String(frm.ajax.value)],
      ...frm.elements.filter(el => !el.disabled && el.checked).map(el => [el.name, el.value])]).toString() },
    CarlosAjax: { updater: (target, url, options) => { requests.push({ target, url, options, body: new URLSearchParams(options.postBody) }); } },
    loadDiv: (id, url, delay, done) => reloads.push(done),
    removeIssue: id => removed.push(id),
    $: id => fields[id], alert: message => alerts.push(message), URLSearchParams,
  };
  vm.createContext(context);
  vm.runInContext(chartActionUrlSource(source), context);
  vm.runInContext(block('    var ajaxRequest;', '    function submitIssue('), context);
  context.updateIssueError = 'issue update failed';
  context.noteSavingMessage = 'saving note';

  // Answer the oldest pending request: 2xx installs `rendered` (when given) between onSuccess and
  // onComplete, as CarlosAjax.updater does, and the Unresolved Issues reload then completes.
  function respond(status, rendered) {
    const request = requests.find(r => !r.answered);
    assert.ok(request, 'no request is waiting for a response');
    request.answered = true;
    const ok = status >= 200 && status < 300;
    if (ok && request.options.onSuccess) request.options.onSuccess({ status });
    if (ok && rendered) render(rendered);
    if (!ok) request.options.onFailure({ status });
    request.options.onComplete({ status });
    if (ok) reloads.shift()(true);
  }
  // A click on a radio is a change of its checked state followed by its onchange handler.
  function click(radio) {
    if (radio.disabled) return 'disabled';
    form.elements.filter(el => el.name === radio.name).forEach(el => { el.checked = el === radio; });
    return context.changeNoteIssue(radio);
  }
  const mutations = () => requests.filter(r => r.body.get('method') !== 'edit');
  return { context, form, render, respond, click, requests, mutations, alerts, removed, fields, rows: () => rows };
}

test('the issue probe: two clicks behind a held editor refresh never send the other issue', () => {
  const h = harness();
  h.render([{ id: 40 }, { id: 41 }, { id: 42 }]);
  h.context.ajaxUpdateIssues('edit', 'sig31', true);
  const [, first, second] = h.rows();
  // The reviewer's sequence: click index 1, then index 2, while the edit response is held.
  assert.equal(h.click(first.acuteTrue), 'disabled');
  assert.equal(h.click(second.acuteTrue), 'disabled');
  assert.equal(h.mutations().length, 0, 'refused clicks are not queued for later serialization');
  assert.equal(first.acuteTrue.checked, false, 'a refused click shows the stored state again');
  assert.equal(first.acuteFalse.checked, true);

  // The refresh rebuilds and reorders the list; the clinician's next clicks use the new rows.
  h.respond(200, [{ id: 42 }, { id: 41 }, { id: 40 }]);
  assert.equal(h.context.issueUpdatesPending, 0);
  const byId = id => h.rows().find(r => r.caseIssueId === id);
  assert.equal(h.click(byId('41').acuteTrue), false);
  assert.equal(h.click(byId('42').acuteTrue), 'disabled', 'a second click waits for the first to finish');
  h.respond(200, [{ id: 42 }, { id: 41, acute: true }, { id: 40 }]);
  assert.equal(h.click(byId('42').acuteTrue), false);
  h.respond(200, [{ id: 42, acute: true }, { id: 41, acute: true }, { id: 40 }]);

  const posted = h.mutations().map(r => ({ id: r.body.get('caseIssueId'), field: r.body.get('issueField'), value: r.body.get('issueValue') }));
  assert.deepEqual(posted, [{ id: '41', field: 'acute', value: 'true' }, { id: '42', field: 'acute', value: 'true' }]);
  for (const request of h.mutations()) {
    assert.equal(request.body.get('method'), 'issueChange');
    assert.equal(request.body.has('lineId'), false, 'no list position is sent');
    assert.equal(request.target.success, 'sig31');
  }
  assert.deepEqual(h.alerts, []);
});

test('the target and value are fixed in the request body at the click', () => {
  const h = harness();
  const [issue] = h.render([{ id: 77 }]);
  h.click(issue.acuteTrue);
  assert.equal(h.requests.length, 1);
  const body = h.requests[0].body;
  // Changing the form afterwards cannot change what was sent for this click.
  issue.acuteFalse.checked = true; issue.acuteTrue.checked = false;
  h.form.method.value = 'save';
  assert.equal(body.get('method'), 'issueChange');
  assert.equal(body.get('caseIssueId'), '77');
  assert.equal(body.get('issueField'), 'acute');
  assert.equal(body.get('issueValue'), 'true');
  assert.equal(body.getAll('caseIssueId').length, 1);
});

test('row controls are disabled while an update is in flight and read-only rows stay disabled', () => {
  const h = harness();
  const [writable, readOnly] = h.render([{ id: 5 }, { id: 6, readOnly: true }]);
  h.click(writable.acuteTrue);
  for (const r of h.rows()) {
    assert.equal(r.acuteTrue.disabled, true);
    assert.equal(r.deleteLink.getAttribute('aria-disabled'), 'true');
  }
  assert.equal(h.fields.asgnIssues.disabled, true);
  h.respond(200);
  assert.equal(writable.acuteTrue.disabled, false);
  assert.equal(writable.deleteLink.getAttribute('aria-disabled'), null);
  assert.equal(readOnly.acuteTrue.disabled, true, 'a control the server rendered disabled stays disabled');
});

test('a fragment installed mid-update stays disabled until the Unresolved Issues reload finishes', () => {
  const h = harness();
  const [issue] = h.render([{ id: 5 }]);
  h.click(issue.acuteTrue);
  const request = h.requests[0];
  request.answered = true;
  request.options.onSuccess({ status: 200 });
  const [fresh] = h.render([{ id: 5, acute: true }]);
  request.options.onComplete({ status: 200 });
  assert.equal(fresh.acuteFalse.disabled, true, 'new rows render enabled; the busy state must cover them');
  assert.equal(h.click(fresh.acuteFalse), 'disabled');
  // The Unresolved Issues reload has not answered yet, so the update is still in flight.
  assert.equal(h.context.issueUpdatesPending, 1);
});

test('a refused or failed change puts the radio back on the stored state', () => {
  for (const status of [409, 400, 403, 500]) {
    const h = harness();
    const [issue] = h.render([{ id: 9, acute: true }]);
    h.click(issue.acuteFalse);
    assert.equal(issue.acuteFalse.checked, true);
    h.respond(status);
    assert.equal(issue.acuteTrue.checked, true, `HTTP ${status} must not leave an unsaved choice on screen`);
    assert.equal(issue.acuteFalse.checked, false);
    assert.equal(issue.acuteTrue.disabled, false);
    assert.deepEqual(h.alerts, [`${status} issue update failed`]);
    assert.equal(h.context.issueUpdateFailed, true, 'a pending note save is still held back after a failure');
  }
});

test('Delete sends the clicked patient issue and refuses a repeat while the first is pending', () => {
  const h = harness();
  const [first, second] = h.render([{ id: 30 }, { id: 31 }]);
  assert.equal(h.context.deleteNoteIssue(first.deleteLink, 'noteIssueDetails30'), false);
  assert.equal(h.context.deleteNoteIssue(second.deleteLink, 'noteIssueDetails31'), false);
  assert.equal(h.mutations().length, 1, 'a second delete is refused while the first is pending, not queued');
  h.respond(200, [{ id: 31 }]);
  // The remaining issue now sits at position 0; its own id still names it.
  h.context.deleteNoteIssue(h.rows()[0].deleteLink, 'noteIssueDetails31');
  h.respond(200, []);
  assert.deepEqual(h.mutations().map(r => [r.body.get('method'), r.body.get('caseIssueId')]),
    [['issueDelete', '30'], ['issueDelete', '31']]);
  assert.deepEqual(h.removed, ['noteIssueDetails30', 'noteIssueDetails31']);
});

test('a control without a usable patient issue id sends nothing', () => {
  for (const bad of [null, '', '0', '-4', '1e3', '12abc', '99999999999999999999']) {
    const h = harness();
    const [issue] = h.render([{ id: 3 }]);
    issue.acuteTrue.setAttribute('data-case-issue-id', bad);
    if (bad === null) issue.acuteTrue.removeAttribute('data-case-issue-id');
    issue.deleteLink.setAttribute('data-case-issue-id', bad);
    if (bad === null) issue.deleteLink.removeAttribute('data-case-issue-id');
    h.click(issue.acuteTrue);
    h.context.deleteNoteIssue(issue.deleteLink, 'noteIssueDetails3');
    assert.equal(h.requests.length, 0, `id ${bad} must not be sent`);
    assert.equal(issue.acuteTrue.checked, false);
    assert.deepEqual(h.alerts, ['issue update failed', 'issue update failed']);
  }
});

test('an unknown field or value sends nothing', () => {
  const h = harness();
  const [issue] = h.render([{ id: 3 }]);
  issue.acuteTrue.setAttribute('data-issue-field', 'role');
  h.click(issue.acuteTrue);
  issue.acuteTrue.setAttribute('data-issue-field', 'acute');
  issue.acuteTrue.value = 'yes';
  h.click(issue.acuteTrue);
  assert.equal(h.requests.length, 0);
  assert.equal(h.alerts.length, 2);
});

test('a change during a note-switch save is refused with the saving message and restored', () => {
  const h = harness();
  const [issue] = h.render([{ id: 3 }]);
  h.context.noteSwitchSavePending = true;
  h.click(issue.acuteTrue);
  assert.equal(h.requests.length, 0);
  assert.equal(issue.acuteTrue.checked, false);
  assert.deepEqual(h.alerts, ['saving note']);
});
