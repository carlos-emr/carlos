/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/eform/efmimagemanager.jsp'), 'utf8');
const start = jsp.indexOf('            async function csrfToken()');
const end = jsp.indexOf('        </script>', start);
assert(start >= 0 && end > start);
const script = jsp.slice(start, end)
  .replaceAll('${carlos:forJavaScript(imageDeleteConfirm)}', 'Confirm deletion')
  .replaceAll('${carlos:forJavaScript(imageDeleteTokenUnavailable)}', 'Token unavailable')
  .replace(/<%=[\s\S]*?%>/g, value => value.includes('getContextPath') ? '/carlos' : '?scheduleNav=1');
function setup({confirm = true, failedBootstrap = false, failedRetry = false, transientFailure = false} = {}) {
  const state = {fetches: 0, posts: [], alerts: []};
  const token = {value: ''};
  const context = vm.createContext({
    window: {csrfTokenReady: failedBootstrap ? Promise.resolve().then(() => { throw new Error('bootstrap'); }) : null},
    document: {
      querySelector() { return token; },
      createElement() {
        return {children: [], appendChild(input) { this.children.push(input); },
          submit() { state.posts.push(this); }};
      },
      body: {appendChild() {}},
    },
    confirm() { return confirm; },
    alert(message) { state.alerts.push(message); },
    async fetchCsrfToken() {
      state.fetches++;
      if (failedRetry || (transientFailure && state.fetches === 1)) throw new Error('retry');
      token.value = 'test-token';
    },
  });
  vm.runInContext(script, context);
  return {state, context};
}
test('canceling deletion does not request a token or post', async () => {
  const {state, context} = setup({confirm: false});
  await context.deleteImg('fixture.png');
  assert.equal(state.fetches, 0);
  assert.equal(state.posts.length, 0);
});
test('failed bootstrap retries and submits the filename and token once', async () => {
  const {state, context} = setup({failedBootstrap: true});
  await context.deleteImg('fixture.png');
  assert.equal(state.fetches, 1);
  assert.equal(state.posts.length, 1);
  const form = state.posts[0];
  assert.equal(form.method, 'post');
  assert.equal(form.action, '/carlos/eform/deleteImage?scheduleNav=1');
  assert.deepEqual(form.children.map(input => [input.name, input.value]),
    [['filename', 'fixture.png'], ['CSRF-TOKEN', 'test-token']]);
});
test('unavailable token reports failure and never submits the delete', async () => {
  const {state, context} = setup({failedBootstrap: true, failedRetry: true});
  await context.deleteImg('fixture.png');
  assert.equal(state.posts.length, 0);
  assert.deepEqual(state.alerts, ['Token unavailable']);
  assert.equal(state.fetches, 1);
});
test('fragment without a bootstrap retries a transient failure on the same click', async () => {
  const {state, context} = setup({transientFailure: true});
  await context.deleteImg('fixture.png');
  assert.equal(state.fetches, 2);
  assert.equal(state.posts.length, 1);
  assert.deepEqual(state.alerts, []);
});
test('fragment token failure stops after two attempts without submitting', async () => {
  const {state, context} = setup({failedRetry: true});
  await context.deleteImg('fixture.png');
  assert.equal(state.fetches, 2);
  assert.equal(state.posts.length, 0);
  assert.deepEqual(state.alerts, ['Token unavailable']);
});
test('localized delete messages use the JavaScript encoder before entering literals', () => {
  assert.match(jsp, /confirm\("\$\{carlos:forJavaScript\(imageDeleteConfirm\)\}"\)/);
  assert.match(jsp, /alert\("\$\{carlos:forJavaScript\(imageDeleteTokenUnavailable\)\}"\)/);
});
