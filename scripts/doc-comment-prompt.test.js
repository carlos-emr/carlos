/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The document viewer's comment prompt (oscarMDSIndex.js getDocComment for Acknowledge, addDocComment for Comment)
 * starts from the reviewing provider's own saved comment. For a document with no comment the viewer shows the
 * translated "No comment" placeholder (showDocument.noComment); the prompt used to start with that text, and OK then
 * saved it as the provider's comment.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const index = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/oscarMDSIndex.js'), 'utf8');
// The two prompt functions and the helper they share, as the page ships them.
const start = Math.min(...['function storedDocComment(', 'function addDocComment(']
  .map((anchor) => index.indexOf(anchor)).filter((at) => at >= 0));
const end = index.indexOf('\n}\n', index.indexOf('function getDocComment(')) + 3;
assert.ok(start >= 0 && end > start, 'the comment prompt functions were not found in oscarMDSIndex.js');

const DOC = '12';
const PROVIDER = '999998';

/**
 * A document viewer for one document: the visible comment (`shown`), the acknowledge form's hidden comment field
 * (`saved`), and a prompt that answers `answer(defaultText)`. Returns the context and what the page did.
 */
function viewer({ shown, saved, answer, post = () => Promise.resolve({ ok: true, json: () => Promise.resolve({ date: '2026-10-18 12:00' }) }) }) {
  const elements = {
    [`comment_${DOC}_${PROVIDER}`]: { textContent: shown, value: undefined },
    [`comment_${DOC}`]: { textContent: '', value: saved },
    [`status_${DOC}`]: { value: 'A' },
    [`timestamp_${DOC}_${PROVIDER}`]: { textContent: '' },
  };
  const seen = { offered: [], acknowledged: [], posted: [], alerts: [] };
  const jQuery = (selector) => {
    const element = elements[selector.replace(/^#/, '')];
    return {
      length: element ? 1 : 0,
      text: () => (element ? element.textContent : ''),
      val: (value) => {
        if (value === undefined) return element ? element.value : undefined;
        if (element) element.value = value;
        return undefined;
      },
    };
  };
  const context = vm.createContext({
    jQuery,
    document: { getElementById: (id) => elements[id] || null },
    prompt: (message, offered) => { seen.offered.push(offered); return answer(offered); },
    alert: (message) => seen.alerts.push(message),
    URLSearchParams,
    updateStatus: (formId) => seen.acknowledged.push({ formId, comment: elements[`comment_${DOC}`].value }),
    serializeForm: () => `comment=${encodeURIComponent(elements[`comment_${DOC}`].value)}`,
    postForm: (url, data) => {
      seen.posted.push(data);
      return post();
    },
    ctx: '',
    console: { log() {}, error() {} },
  });
  vm.runInContext(index.slice(start, end), context);
  return { context, seen, elements };
}

for (const placeholder of ['No comment', 'Aucun commentaire']) {
  test(`Acknowledge on a document with no comment offers no text, not "${placeholder}"`, () => {
    const page = viewer({ shown: placeholder, saved: '', answer: (offered) => offered });
    page.context.getDocComment(DOC, PROVIDER, false);
    assert.deepEqual(page.seen.offered, ['']);
    assert.deepEqual(page.seen.acknowledged, [{ formId: `acknowledgeForm_${DOC}`, comment: '' }]);
  });
}

test('Acknowledge offers the provider\'s saved comment and keeps it on OK', () => {
  const page = viewer({ shown: 'Seen, booked follow-up', saved: 'Seen, booked follow-up', answer: (offered) => offered });
  page.context.getDocComment(DOC, PROVIDER, false);
  assert.deepEqual(page.seen.offered, ['Seen, booked follow-up']);
  assert.equal(page.seen.acknowledged[0].comment, 'Seen, booked follow-up');
});

test('Cancel on the Acknowledge prompt acknowledges nothing', () => {
  const page = viewer({ shown: 'No comment', saved: '', answer: () => null });
  page.context.getDocComment(DOC, PROVIDER, false);
  assert.deepEqual(page.seen.acknowledged, []);
});

test('Comment on a document with no comment offers no text, and the saved comment is offered next time', async () => {
  const typed = 'Called the patient';
  const page = viewer({ shown: 'No comment', saved: '', answer: (offered) => (offered === '' ? typed : offered) });
  const saving = page.context.addDocComment(DOC, PROVIDER);
  assert.deepEqual(page.seen.offered, ['']);
  assert.equal(page.seen.posted.length, 1);
  assert.equal(new URLSearchParams(page.seen.posted[0]).get('comment'), typed);
  assert.equal(new URLSearchParams(page.seen.posted[0]).get('status'), 'N');
  assert.equal(page.elements[`comment_${DOC}`].value, '', 'unconfirmed text is not the saved comment');
  await saving;
  assert.equal(page.elements[`comment_${DOC}_${PROVIDER}`].textContent, typed);
  // A later Acknowledge in the same viewer starts from the comment just saved.
  page.context.getDocComment(DOC, PROVIDER, false);
  assert.deepEqual(page.seen.offered, ['', typed]);
  assert.equal(page.seen.acknowledged[0].comment, typed);
});

for (const [failure, post] of [
  ['a refused HTTP response with JSON', () => Promise.resolve({ ok: false, json: () => Promise.resolve({ date: 'not saved' }) })],
  ['a network failure', () => Promise.reject(new Error('offline'))],
  ['an invalid success response', () => Promise.resolve({ ok: true, json: () => Promise.resolve({ success: false }) })],
]) {
  test(`Comment reports ${failure} and preserves the saved comment for retry or Acknowledge`, async () => {
    const saved = 'Previously saved';
    const page = viewer({ shown: saved, saved, answer: () => 'Unsaved edit', post });
    await page.context.addDocComment(DOC, PROVIDER);
    assert.equal(page.seen.posted.length, 1);
    assert.equal(new URLSearchParams(page.seen.posted[0]).get('comment'), 'Unsaved edit');
    assert.equal(page.elements[`comment_${DOC}`].value, saved);
    assert.equal(page.elements[`comment_${DOC}_${PROVIDER}`].textContent, saved);
    assert.equal(page.elements[`status_${DOC}`].value, 'A');
    assert.equal(page.elements[`timestamp_${DOC}_${PROVIDER}`].textContent, '');
    assert.deepEqual(page.seen.alerts, ['The comment could not be saved. Please try again.']);
    page.context.getDocComment(DOC, PROVIDER, false);
    assert.deepEqual(page.seen.offered, [saved, saved]);
  });
}
