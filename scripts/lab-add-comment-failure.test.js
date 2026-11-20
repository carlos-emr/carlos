/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Add Comment on the lab viewer: fetch() resolves on an HTTP error, so a refused or failed save
// must not reload the page and discard the clinician's text (#1408 follow-up). The handlers are
// read from labDisplay.jsp itself and run against stubbed browser objects.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp', 'utf8');
function slice(from, to) {
  const start = jsp.indexOf(from);
  const end = jsp.indexOf(to, start);
  assert.ok(start >= 0 && end > start, `${from.trim()} markers missing or out of order in labDisplay.jsp`);
  return jsp.slice(start, end);
}
// The JSP tag that renders the context path is not JavaScript; the test serves it as '/carlos'.
const source = (slice('        function getComment(', '        function printPDF(')
  + slice('        function addComment(', '        function submitLabel('))
  .replace(/'<carlos:encode value='<%= request\.getContextPath\(\) %>' context="javaScriptBlock"\/>'/g, "'/carlos'");

function fixture(outcome) {
  const prompts = [], answers = [], alerts = [], posts = [];
  let reloads = 0;
  const form = {comment: {value: ''}};
  const elements = {
    'acknowledgeForm_7': form,
    'labStatus_7': {value: ''},
    '101_7commentText': {textContent: 'SAVED SYNTHETIC COMMENT'}
  };
  const context = {
    providerNo: '101',
    URLSearchParams,
    FormData: function (target) { return [['comment', target.comment.value]]; },
    document: {forms: {'acknowledgeForm_7': form}, getElementById: id => elements[id] || null},
    prompt(message, initial) { prompts.push(initial); return answers.shift(); },
    alert(message) { alerts.push(message); },
    console: {log() {}, error() {}},
    window: {location: {reload() { reloads++; }}},
    carlosEnsureCsrfParam(params) { params.set('CSRF-TOKEN', 'synthetic token'); return Promise.resolve(); },
    // The link check: an addComment action on a lab linked to a patient goes straight to addComment.
    handleLab(formid, labid, action) { if (action === 'addComment') context.addComment(formid, labid); },
    fetch(url, options) {
      posts.push(new URLSearchParams(options.body));
      if (outcome instanceof Error) return Promise.reject(outcome);
      return Promise.resolve(outcome);
    }
  };
  vm.runInNewContext(source, context);
  return {
    context, prompts, alerts, posts, form,
    reloads: () => reloads,
    async comment(answer) { answers.push(answer); context.getComment('addComment', 7); await new Promise(setImmediate); }
  };
}

test('an HTTP error keeps the page and the unsaved comment, and the retry offers it again', async () => {
  for (const status of [500, 403]) {
    const f = fixture({ok: false, status});
    await f.comment('SYNTHETIC DRAFT COMMENT');

    assert.equal(f.reloads(), 0, `HTTP ${status} must not reload the viewer`);
    assert.equal(f.alerts.length, 1);
    assert.match(f.alerts[0], /not saved/);
    assert.match(f.alerts[0], /Refreshing will clear it/);
    assert.equal(f.posts[0].get('comment'), 'SYNTHETIC DRAFT COMMENT');

    await f.comment(null);
    assert.deepEqual(f.prompts, ['SAVED SYNTHETIC COMMENT', 'SYNTHETIC DRAFT COMMENT'],
      'the second prompt offers the unsaved text, not the saved comment');
  }
});

test('a transport failure keeps the unsaved comment as well', async () => {
  const f = fixture(new Error('synthetic transport failure'));
  await f.comment('SYNTHETIC DRAFT COMMENT');

  assert.equal(f.reloads(), 0);
  assert.equal(f.alerts.length, 1);
  assert.equal(f.context.addComment.unsaved['7'], 'SYNTHETIC DRAFT COMMENT');
});

test('a saved comment reloads the viewer once and forgets the draft', async () => {
  const f = fixture({ok: true, status: 200});
  f.context.addComment.unsaved['7'] = 'EARLIER SYNTHETIC DRAFT';
  await f.comment('SYNTHETIC FINAL COMMENT');

  assert.deepEqual(f.prompts, ['EARLIER SYNTHETIC DRAFT']);
  assert.equal(f.reloads(), 1);
  assert.deepEqual(f.alerts, []);
  assert.equal(f.context.addComment.unsaved['7'], undefined);
  assert.equal(f.posts[0].get('comment'), 'SYNTHETIC FINAL COMMENT');
  assert.equal(f.posts[0].get('CSRF-TOKEN'), 'synthetic token');
});
