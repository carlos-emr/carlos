/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const h = require('./lib/playwright-harness');
const { withTicklerConfirmations, dismissTicklerDelete } = require('./lib/tickler-confirmation');

/** A form and event source let cancellation be checked even when the server would refuse the POST. */
function taskList(t, dialogs = [{ type: 'confirm', text: 'Delete selected tasks?' }]) {
  const page = new EventEmitter();
  const action = { name: 'submit_form', value: 'unchanged' };
  const selected = { name: 'checkbox', value: '42', checked: true };
  const elements = [action, selected];
  elements.namedItem = name => elements.find(e => e.name === name);
  const frame = { url: () => 'https://example.test/carlos/tickler/ViewTicklerMain' };
  page.mainFrame = () => frame;
  page.locator = selector => {
    assert.equal(selector, 'form[name="ticklerform"]');
    return { evaluate: async fn => fn({ elements }) };
  };
  page.waitForLoadState = async state => assert.equal(state, 'networkidle');
  t.mock.method(h, 'withExpectedDialogs', async (target, body, options) => {
    assert.equal(target, page);
    assert.equal(options.accept, false);
    await body();
    return dialogs;
  });
  const request = (endpoint, method = 'POST') => page.emit('request', {
    method: () => method, url: () => `https://example.test/carlos/tickler/${endpoint}`,
  });
  return { page, action, selected, frame, request };
}

test('Cancel preserves the selected tasks and action and releases its observers', async t => {
  const { page } = taskList(t);
  await dismissTicklerDelete(page, async () => {});
  assert.equal(page.listenerCount('request'), 0);
  assert.equal(page.listenerCount('framenavigated'), 0);
});

for (const endpoint of ['DbTicklerMain', 'DbTicklerDemoMain']) {
  test(`Cancel fails if ${endpoint} receives a POST even when no task row changes`, async t => {
    const { page, request } = taskList(t);
    await assert.rejects(dismissTicklerDelete(page, async () => request(endpoint)), /submitted a mutation request/);
    assert.equal(page.listenerCount('request'), 0);
    assert.equal(page.listenerCount('framenavigated'), 0);
  });
}

test('Cancel permits unrelated background reads and iframe navigation', async t => {
  const { page, request } = taskList(t);
  await dismissTicklerDelete(page, async () => {
    request('ListTicklers', 'GET');
    page.emit('framenavigated', {});
  });
});

test('Cancel fails on a main-frame reload even when it returns to the same URL', async t => {
  const { page, frame } = taskList(t);
  await assert.rejects(dismissTicklerDelete(page, async () => page.emit('framenavigated', frame)), /navigated the list/);
});

for (const field of ['selection', 'action']) {
  test(`Cancel fails if the ${field} changes`, async t => {
    const { page, action, selected } = taskList(t);
    await assert.rejects(dismissTicklerDelete(page, async () => {
      if (field === 'selection') selected.checked = false;
      else action.value = 'Delete';
    }), /changed the selection or submit action/);
  });
}

test('Cancel requires a selected task so an empty selection cannot pass the cancellation check', async t => {
  const { page, selected } = taskList(t);
  selected.checked = false;
  await assert.rejects(dismissTicklerDelete(page, async () => {}), /needs a selected task/);
});

test('Cancel releases its observers when clicking Delete fails', async t => {
  const { page } = taskList(t);
  await assert.rejects(dismissTicklerDelete(page, async () => { throw new Error('click failed'); }), /click failed/);
  assert.equal(page.listenerCount('request'), 0);
  assert.equal(page.listenerCount('framenavigated'), 0);
});

for (const dialogs of [[], [{ type: 'alert', text: 'Delete?' }], [{ type: 'confirm', text: '' }],
  [{ type: 'confirm', text: '???missing???' }], [{ type: 'confirm', text: 'Delete?' }, { type: 'confirm', text: 'Again?' }]]) {
  test(`missing, wrong or repeated confirmations fail: ${JSON.stringify(dialogs)}`, async t => {
    const { page } = taskList(t, dialogs);
    await assert.rejects(withTicklerConfirmations(page, 1, async () => {}, { accept: false }));
  });
}
