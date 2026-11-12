/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const { pressPrint } = require('./lib/gap-clinical-print');
const { textPdf } = require('./lib/stored-pdf-documents');

const ENDPOINT = 'https://127.0.0.1/carlos/CaseManagementEntry?chartContext=owned';
const response = (status = 200, method = 'print') => ({
  url: () => ENDPOINT,
  status: () => status,
  request: () => ({ method: () => 'POST', postData: () => `method=${method}&demographicNo=101` }),
});

function setup(t, trigger) {
  const context = new EventEmitter();
  const chart = new EventEmitter();
  const popup = new EventEmitter();
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'gap-print-test-'));
  t.after(() => fs.rmSync(scratch, { recursive: true, force: true }));
  context.pages = () => [chart];
  chart.context = () => context;
  chart.url = () => 'https://127.0.0.1/carlos/CaseManagementView?demographicNo=101';
  chart.locator = selector => {
    if (selector === '#caseManagementEntryForm') return { evaluate: async read => read({ action: ENDPOINT }) };
    assert.equal(selector, '#printOp');
    return { click: async () => trigger({ context, chart, popup }) };
  };
  chart.waitForTimeout = async timeout => assert.equal(timeout, 3200);
  return { context, chart, popup, scratch };
}

function download(bytes, { url = ENDPOINT, failure = null } = {}) {
  return { url: () => url, failure: async () => failure, saveAs: async file => fs.writeFileSync(file, bytes) };
}

function clean({ context, chart, popup, scratch }) {
  assert.equal(context.listenerCount('response'), 0);
  assert.equal(context.listenerCount('page'), 0);
  for (const page of [chart, popup]) {
    assert.equal(page.listenerCount('download'), 0);
    assert.equal(page.listenerCount('popup'), 0);
  }
  assert.deepEqual(fs.readdirSync(scratch), []);
}

for (const target of ['opener', 'popup']) {
  test(`chart print retains the real ${target} PDF when only the context observes the POST response`, async t => {
    const bytes = textPdf('owned chart print');
    const printed = response();
    const box = setup(t, ({ context, chart, popup }) => {
      context.emit('response', response(200, 'autosave'));
      if (target === 'popup') {
        // The first popup event can already carry the download, before an awaited
        // page event or opener-only response observer would be installed.
        chart.emit('popup', popup);
        popup.emit('download', download(bytes));
        context.emit('page', popup);
      } else chart.emit('download', download(bytes));
      context.emit('response', printed);
    });
    let verified = 0;
    const result = await pressPrint(box.chart, box.scratch, undefined, file => {
      assert.deepEqual(fs.readFileSync(file), bytes);
      verified++;
    });
    assert.equal(result.response, printed);
    assert.equal(result.bytes, bytes.length);
    assert.match(result.text, /owned chart print page 1/);
    assert.equal(verified, 1);
    clean(box);
  });
}

test('a failed native print trigger cancels both observers', async t => {
  const failure = new Error('native print button unavailable');
  const box = setup(t, () => { throw failure; });
  await assert.rejects(pressPrint(box.chart, box.scratch), error => error === failure);
  clean(box);
});

test('a refused print response cancels the pending download observer', async t => {
  const box = setup(t, ({ context }) => context.emit('response', response(500)));
  await assert.rejects(pressPrint(box.chart, box.scratch), /Print answered HTTP 500/);
  clean(box);
});

for (const [label, bytes, reason] of [
  ['HTML masquerading as a PDF', Buffer.from('<html>login</html>'), /not a PDF/],
  ['a truncated PDF', Buffer.from('%PDF-1.4\ntruncated'), /truncated PDF/],
]) {
  test(`chart print refuses ${label} and removes its temporary file`, async t => {
    const box = setup(t, ({ context, chart }) => {
      context.emit('response', response());
      chart.emit('download', download(bytes));
    });
    let verified = false;
    await assert.rejects(pressPrint(box.chart, box.scratch, undefined, () => { verified = true; }), reason);
    assert.equal(verified, false);
    clean(box);
  });
}

test('a failed native download cannot pass the PDF assertions', async t => {
  const box = setup(t, ({ context, chart }) => {
    context.emit('response', response());
    chart.emit('download', download(textPdf('failed download'), { failure: 'net::ERR_FAILED' }));
  });
  await assert.rejects(pressPrint(box.chart, box.scratch), /native chart PDF download failed/);
  clean(box);
});

test('a download from another print response cannot satisfy the current print', async t => {
  const box = setup(t, ({ context, chart }) => {
    context.emit('response', response());
    chart.emit('download', download(textPdf('another request'), { url: `${ENDPOINT}&other=request` }));
  });
  await assert.rejects(pressPrint(box.chart, box.scratch), /belongs to another print response/);
  clean(box);
});

test('caller PDF verification still runs and its failure cleans the downloaded file', async t => {
  const box = setup(t, ({ context, chart }) => {
    context.emit('response', response());
    chart.emit('download', download(textPdf('verify callback')));
  });
  const failure = new Error('embedded font verification failed');
  await assert.rejects(pressPrint(box.chart, box.scratch, undefined, file => {
    assert.ok(fs.existsSync(file));
    throw failure;
  }), error => error === failure);
  clean(box);
});
