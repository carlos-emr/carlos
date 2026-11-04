/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// The footer editor window's server-save mode (issue #3981, Ben's demo of 15 Oct 2026): opened from
// Preferences or the email screen's "Edit my saved personal footer", it reads the saved personal footer
// from the server and saves it there itself, with the page's CSRF token, as one step.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.join(__dirname, '..',
  'src/main/webapp/share/javascript/email/footerEditor.js'), 'utf8');

// Just enough of an element for the window script.
function element(attributes = {}) {
  const listeners = {};
  const classes = new Set();
  const attrs = {...attributes};
  return {
    listeners, attrs, value: '', disabled: false, textContent: '', innerHTML: '',
    classList: {
      toggle(name, on) { if (on) classes.add(name); else classes.delete(name); },
      add(name) { classes.add(name); }, remove(...names) { names.forEach(n => classes.delete(n)); },
      contains: name => classes.has(name)
    },
    getAttribute: name => (name in attrs ? attrs[name] : null),
    setAttribute(name, value) { attrs[name] = String(value); },
    removeAttribute(name) { delete attrs[name]; },
    addEventListener(type, listener) { listeners[type] = listener; },
    focus() {}, after() {}, appendChild() {}, querySelector: () => null,
    get hidden() { return classes.has('d-none'); }
  };
}

const settle = () => new Promise(resolve => setImmediate(resolve));

function page({token = 'FAKE-TOKEN', replies = []} = {}) {
  const ids = {
    footerEditorText: element(), footerEditorCount: element(), footerEditorTooLong: element(),
    footerEditorTooMuchFormatting: element(), footerEditorApply: element(), footerEditorPreviewFooter: element(),
    footerEditorPreviewText: element(), footerEditorPreviewHtml: element(), footerEditorLogoNote: element(),
    footerEditorLinkRow: element(), footerEditorLinkAddress: element(), footerEditorLinkApply: element(),
    footerEditorLinkError: element(), footerEditorScope: element(), footerEditorMessage: element()
  };
  ids.footerEditorScope.textContent = 'This email only';
  ids.footerEditorApply.textContent = 'Use for this email';
  const modal = element({
    'data-max-length': '2000', 'data-max-html-length': '10000',
    'data-remote-saved-text': 'Your personal footer was saved.',
    'data-remote-stale-text': 'Changed in another window.',
    'data-remote-too-long-text': 'Too long.',
    'data-remote-conflict-text': 'Another save was in progress.',
    'data-remote-failed-text': 'Could not be saved.',
    'data-remote-load-failed-text': 'Could not be loaded.'
  });
  modal.querySelector = selector => ids[selector.replace('#', '')] || null;
  modal.querySelectorAll = () => [];
  const status = element();
  const revisionField = element();
  revisionField.value = 'rev-old';
  const footerField = element();
  footerField.value = 'This email footer';
  const elements = {footerEditorModal: modal, myFooterSavedStatus: status, footerEmail: footerField,
    footerEmailPreview: element()};
  const calls = [];
  const pending = [];
  let hidden = false;
  let ready;
  const context = {
    Node: {TEXT_NODE: 3},
    URLSearchParams, Promise, setImmediate,
    DOMPurify: {sanitize: html => html, addHook() {}, removeHook() {}},
    bootstrap: {Modal: {getOrCreateInstance: () => ({hide() { hidden = true; }})}},
    getSelection: () => null,
    fetch(url, options = {}) {
      calls.push({url, options});
      const reply = replies.shift() || {status: 500, body: {}};
      if (reply.network) return Promise.reject(new Error('offline'));
      if (reply.defer) {
        return new Promise(resolve => pending.push(answer => resolve({ok: answer.status >= 200 && answer.status < 300,
          status: answer.status, json: () => Promise.resolve(answer.body)})));
      }
      return Promise.resolve({ok: reply.status >= 200 && reply.status < 300, status: reply.status,
        json: () => Promise.resolve(reply.body)});
    },
    document: {
      addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
      getElementById: id => elements[id] || null,
      querySelector: selector => (selector === 'input[name="CSRF-TOKEN"]' && token ? {value: token} : null),
      querySelectorAll: selector => (selector === 'input[name="personalFooterRevision"]' ? [revisionField] : []),
      createElement() {
        const holder = {childNodes: []};
        Object.defineProperty(holder, 'innerHTML', {set(html) {
          holder.childNodes = [{nodeType: 3, textContent: String(html)}];
        }});
        return holder;
      },
      execCommand() {}
    }
  };
  context.window = context;
  vm.runInNewContext(SOURCE, context);
  ready();
  const opener = element({
    'data-footer-editor-load': '/carlos/email/myEmailFooter',
    'data-footer-editor-remote-save': '/carlos/email/saveMyEmailFooter',
    'data-footer-editor-scope-text': 'My personal footer',
    'data-footer-editor-apply-text': 'Save footer',
    'data-footer-editor-status': 'myFooterSavedStatus',
    'data-footer-editor-revision-field': 'personalFooterRevision'
  });
  const fieldOpener = element({'data-footer-editor-target': 'footerEmail', 'data-footer-editor-preview': 'footerEmailPreview'});
  return {
    ids, status, revisionField, calls, opener, fieldOpener, pending,
    get hidden() { return hidden; },
    open(from) { modal.listeners['show.bs.modal']({relatedTarget: from}); },
    save() { ids.footerEditorApply.listeners.click(); }
  };
}

const saved = {status: 200, body: {footer: 'FAKE own footer', revision: 'rev-1'}};

test('opens on the saved footer, read as JSON, with its own badge and button, and cannot save before it arrives', async () => {
  const p = page({replies: [saved]});
  p.open(p.opener);
  assert.equal(p.ids.footerEditorApply.disabled, true);
  assert.equal(p.ids.footerEditorScope.textContent, 'My personal footer');
  assert.equal(p.ids.footerEditorApply.textContent, 'Save footer');
  await settle();
  assert.equal(p.calls[0].url, '/carlos/email/myEmailFooter');
  assert.equal(p.calls[0].options.headers.Accept, 'application/json');
  assert.equal(p.calls[0].options.headers['X-Requested-With'], 'XMLHttpRequest');
  assert.equal(p.ids.footerEditorText.innerHTML, 'FAKE own footer');
  assert.equal(p.ids.footerEditorApply.disabled, false);
});

test('Save footer posts the text and revision with the CSRF token, then closes and says so', async () => {
  const p = page({replies: [saved, {status: 200, body: {saved: true, revision: 'rev-2'}}]});
  p.open(p.opener);
  await settle();
  p.ids.footerEditorText.innerHTML = 'FAKE changed footer';
  p.save();
  await settle();
  const post = p.calls[1];
  assert.equal(post.url, '/carlos/email/saveMyEmailFooter');
  assert.equal(post.options.method, 'POST');
  assert.equal(post.options.headers['CSRF-TOKEN'], 'FAKE-TOKEN');
  assert.equal(post.options.headers['X-Requested-With'], 'XMLHttpRequest');
  const body = new URLSearchParams(post.options.body);
  assert.equal(body.get('footerAction'), 'save');
  assert.equal(body.get('myFooter'), 'FAKE changed footer');
  assert.equal(body.get('personalFooterRevision'), 'rev-1');
  assert.equal(p.hidden, true);
  assert.equal(p.status.textContent, 'Your personal footer was saved.');
  assert.equal(p.status.hidden, false);
  assert.equal(p.revisionField.value, 'rev-2', 'The page keeps the new revision for its own later save');
});

test('a refusal keeps the window open with the text and says why', async () => {
  for (const [reply, said] of [[{status: 409, body: {error: 'stale'}}, 'Changed in another window.'],
    [{status: 400, body: {error: 'tooLong'}}, 'Too long.'],
    [{status: 409, body: {error: 'conflict'}}, 'Another save was in progress.'],
    [{network: true}, 'Could not be saved.']]) {
    const p = page({replies: [saved, reply]});
    p.open(p.opener);
    await settle();
    p.ids.footerEditorText.innerHTML = 'FAKE kept text';
    p.save();
    await settle();
    assert.equal(p.hidden, false);
    assert.equal(p.ids.footerEditorMessage.textContent, said);
    assert.equal(p.ids.footerEditorMessage.hidden, false);
    assert.equal(p.ids.footerEditorText.innerHTML, 'FAKE kept text');
    assert.equal(p.ids.footerEditorApply.disabled, false, 'Save works again after a refusal');
    assert.equal(p.revisionField.value, 'rev-old');
  }
});

test('without a CSRF token on the page it sends nothing and says it could not save', async () => {
  const p = page({token: null, replies: [saved]});
  p.open(p.opener);
  await settle();
  p.save();
  await settle();
  assert.equal(p.calls.length, 1, 'Only the read was sent');
  assert.equal(p.ids.footerEditorMessage.textContent, 'Could not be saved.');
  assert.equal(p.hidden, false);
});

test('a footer that cannot be read leaves Save off and says so', async () => {
  const p = page({replies: [{status: 403, body: {}}]});
  p.open(p.opener);
  await settle();
  assert.equal(p.ids.footerEditorMessage.textContent, 'Could not be loaded.');
  assert.equal(p.ids.footerEditorApply.disabled, true);
});

test('a field opener on the same page keeps the window\'s own badge and button, and reads nothing', async () => {
  const p = page({replies: [saved]});
  p.open(p.opener);
  await settle();
  p.open(p.fieldOpener);
  assert.equal(p.ids.footerEditorScope.textContent, 'This email only');
  assert.equal(p.ids.footerEditorApply.textContent, 'Use for this email');
  assert.equal(p.ids.footerEditorText.innerHTML, 'This email footer');
  assert.equal(p.calls.length, 1);
});

test('a read answered after the window was opened again is ignored', async () => {
  const p = page({replies: [{defer: true}, {status: 200, body: {footer: 'FAKE newer footer', revision: 'rev-2'}}]});
  p.open(p.opener);
  p.open(p.opener);
  await settle();
  assert.equal(p.ids.footerEditorText.innerHTML, 'FAKE newer footer');
  p.pending[0]({status: 200, body: {footer: 'FAKE older footer', revision: 'rev-1'}});
  await settle();
  assert.equal(p.ids.footerEditorText.innerHTML, 'FAKE newer footer', 'The earlier read overwrote the window');
});

test('a save answered after the window was opened again neither closes nor messages it, and it reads the footer again', async () => {
  const p = page({replies: [saved, {defer: true}, {status: 200, body: {footer: 'FAKE own footer', revision: 'rev-1'}},
    {status: 200, body: {footer: 'FAKE saved footer', revision: 'rev-2'}}]});
  p.open(p.opener);
  await settle();
  p.ids.footerEditorText.innerHTML = 'FAKE saved footer';
  p.save();
  // Closed with the window's own close button and opened again before the save answers.
  p.open(p.opener);
  await settle();
  p.pending[0]({status: 200, body: {saved: true, revision: 'rev-2'}});
  await settle();
  assert.equal(p.hidden, false, 'The earlier save closed the window opened since');
  assert.equal(p.status.textContent, 'Your personal footer was saved.');
  assert.equal(p.revisionField.value, 'rev-2');
  assert.equal(p.calls.length, 4, 'The reopened window did not read the saved footer again');
  assert.equal(p.ids.footerEditorText.innerHTML, 'FAKE saved footer');
  assert.equal(p.ids.footerEditorMessage.hidden, true);
});

test('a refusal answered after the window was opened again does not message it', async () => {
  const p = page({replies: [saved, {defer: true}, saved]});
  p.open(p.opener);
  await settle();
  p.save();
  p.open(p.opener);
  await settle();
  p.pending[0]({status: 409, body: {error: 'stale'}});
  await settle();
  assert.equal(p.ids.footerEditorMessage.hidden, true, 'The earlier refusal showed in the window opened since');
  assert.equal(p.ids.footerEditorApply.disabled, false);
});
