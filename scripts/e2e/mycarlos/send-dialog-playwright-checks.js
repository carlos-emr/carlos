#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// "Send to myCarlos" as a dialog on the document page (mycarlosSendDialog.jspf,
// mycarlos-send-dialog.js): it opens in place, holds Close while a send runs, retries a lost
// reply with the same operation id, and works in the pending-documents view, which inserts
// documents without running their scripts.
//
// IT SENDS DOCUMENTS. It sends two FAKE documents to a FAKE approved device through the portal,
// so it runs only on a throwaway CARLOS + portal, never a live demo, and only with
// MYCARLOS_SEND_DIALOG_THROWAWAY=yes. It needs:
//   MYCARLOS_PATIENT    a FAKE patient whose myCarlos device is approved, with delivery on;
//   MYCARLOS_DOCUMENTS  two of that patient's documents, comma-separated (e.g. 55,56);
// plus the usual BASE_URL, TEST_USER/TEST_PASSWORD/TEST_PIN and MYSQL_* for the harness. The
// deliveries it makes stay in the throwaway portal; nothing else is written.
const h = require('../../lib/playwright-harness');
const { runWorkflow } = require('../../lib/workflow-session');

const TIMEOUT = 30000;
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function fixture() {
  if (process.env.MYCARLOS_SEND_DIALOG_THROWAWAY !== 'yes') {
    throw new h.SkipCheck('MYCARLOS_SEND_DIALOG_THROWAWAY is not yes: this check sends documents through the portal');
  }
  const patient = process.env.MYCARLOS_PATIENT || '';
  const documents = (process.env.MYCARLOS_DOCUMENTS || '').split(',').map(id => id.trim()).filter(Boolean);
  if (!/^[1-9]\d*$/.test(patient) || documents.length !== 2 || !documents.every(id => /^[1-9]\d*$/.test(id))) {
    throw new h.SkipCheck('MYCARLOS_PATIENT and two MYCARLOS_DOCUMENTS are needed');
  }
  return { patient, documents };
}

// Every PortalDelivery call a page makes, with its parsed operation and body.
function watchDeliveries(page, origins) {
  const calls = [];
  page.on('request', request => {
    origins.push(new URL(request.url()).origin);
    const url = new URL(request.url());
    if (!url.pathname.endsWith('/demographic/PortalDelivery')) return;
    const body = new URLSearchParams(request.postData() || '');
    calls.push({ method: request.method(), operation: url.searchParams.get('method') || body.get('method'), url, body });
  });
  return calls;
}

const dialogOf = (page, documentId) => page.locator(`#mycarlos-send-dialog-${documentId}`);
const openerOf = (page, documentId) => page.locator(`[data-mycarlos-dialog="mycarlos-send-dialog-${documentId}"]`);
const message = dialog => dialog.locator('[data-mycarlos-part="message"]');
const button = (dialog, name) => dialog.getByRole('button', { name, exact: true });
const rowsFor = (dialog, deliveryId) => dialog.locator(`[data-mycarlos-part="history"] tr[data-delivery="${deliveryId}"]`);

async function openDialog(page, documentId) {
  await openerOf(page, documentId).click();
  const dialog = dialogOf(page, documentId);
  await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
  // The read has answered once the device list or the "no device" note is there.
  await page.waitForFunction(id => {
    const root = document.getElementById('mycarlos-send-dialog-' + id);
    const select = root && root.querySelector('[data-mycarlos-part="device"]');
    return select && (select.options.length > 0 || !root.querySelector('[data-mycarlos-part="no-device"]').hidden);
  }, documentId, { timeout: TIMEOUT });
  return dialog;
}

async function workflow(s) {
  const { patient, documents: [first, second] } = fixture();
  const origin = new URL(s.config.baseUrl).origin;
  const origins = [];
  const documentPage = async documentId => {
    const page = await s.context.newPage();
    const calls = watchDeliveries(page, origins);
    await h.gotoApp(page, s.config.baseUrl, `/documentManager/ViewShowDocument?segmentID=${documentId}&inWindow=true`);
    await h.assertNotErrorPage(page, `document ${documentId}`);
    return { page, calls };
  };

  let page;
  let calls;
  let minted;
  await s.step('the Send button opens a modal dialog in place, with one read and focus on the device', async () => {
    ({ page, calls } = await documentPage(first));
    const pagesBefore = s.context.pages().length;
    const address = page.url();
    const dialog = await openDialog(page, first);
    h.assert(s.context.pages().length === pagesBefore, 'Send opened a new page');
    h.assert(page.url() === address, 'the document page URL changed');
    h.assert(await dialog.evaluate(element => element.matches(':modal')), 'the dialog is not modal');
    h.assert(await page.getByRole('dialog', { name: 'Send to myCarlos' }).count() === 1, 'the dialog is not named by its heading');
    const reads = calls.filter(call => call.operation === 'read');
    h.assert(reads.length === 1 && reads[0].method === 'GET', `expected one GET read, saw ${reads.length}`);
    h.assert(!reads[0].url.searchParams.has('deliveryId'), 'the dialog named its own operation id');
    h.assert(reads[0].url.searchParams.get('documentId') === first && reads[0].url.searchParams.get('demographicNo') === patient,
      'the read is not for this document and patient');
    minted = await dialog.getAttribute('data-delivery');
    h.assert(UUID_V4.test(minted || ''), 'the read did not give the dialog a v4 operation id');
    const options = await dialog.locator('[data-mycarlos-part="device"] option').count();
    h.assert(options > 0, `patient ${patient} has no approved myCarlos device on this stack`);
    await page.waitForFunction(id => document.activeElement && document.activeElement.id === 'mycarlos-send-device-' + id, first);
  });

  await s.step('Esc closes it and focus returns to the Send button', async () => {
    await page.keyboard.press('Escape');
    await dialogOf(page, first).waitFor({ state: 'hidden', timeout: TIMEOUT });
    await page.waitForFunction(id => document.activeElement
      && document.activeElement.getAttribute('data-mycarlos-dialog') === 'mycarlos-send-dialog-' + id, first);
  });

  await s.step('a send holds Close and Esc until its result shows, then lists its copy', async () => {
    let release;
    const held = new Promise(resolve => { release = resolve; });
    await page.route('**/demographic/PortalDelivery', async route => {
      const request = route.request();
      if (request.method() !== 'POST' || new URLSearchParams(request.postData() || '').get('method') !== 'send') return route.fallback();
      const response = await route.fetch();
      await held;
      return route.fulfill({ response });
    });
    const dialog = await openDialog(page, first);
    h.assert(await dialog.getAttribute('data-delivery') === minted, 'reopening changed the operation id');
    await button(dialog, 'Send to myCarlos').click();
    await message(dialog).filter({ hasText: 'Sending' }).waitFor({ timeout: TIMEOUT });
    h.assert(await button(dialog, 'Close').isDisabled(), 'Close is enabled while sending');
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);
    h.assert(await dialog.isVisible(), 'Esc closed the dialog while sending');
    release();
    await message(dialog).filter({ hasText: 'Sent.' }).waitFor({ timeout: TIMEOUT });
    h.assert(await button(dialog, 'Close').isEnabled(), 'Close stayed disabled after the result');
    h.assert(await rowsFor(dialog, minted).count() === 1, 'the sent copy is not listed once');
    const sends = calls.filter(call => call.operation === 'send');
    h.assert(sends.length === 1 && sends[0].method === 'POST', `expected one POST send, saw ${sends.length}`);
    h.assert(sends[0].body.get('deliveryId') === minted, 'the send did not use the dialog operation id');
    h.assert((sends[0].body.get('CSRF-TOKEN') || '').length > 0, 'the send carried no CSRF token');
    h.assert(await button(dialog, 'Send to myCarlos').isDisabled(), 'the same copy can be sent twice');
    await page.unroute('**/demographic/PortalDelivery');
  });

  await s.step('Check delivery status refreshes the table by POST', async () => {
    const dialog = dialogOf(page, first);
    const before = calls.length;
    await button(dialog, 'Check delivery status').click();
    await page.waitForFunction(id => {
      const root = document.getElementById('mycarlos-send-dialog-' + id);
      return root && !root.querySelector('[data-mycarlos-part="check"]').disabled;
    }, first);
    const refresh = calls.slice(before).filter(call => call.operation === 'refresh');
    h.assert(refresh.length === 1 && refresh[0].method === 'POST', 'Check delivery status did not POST one refresh');
    h.assert(await rowsFor(dialog, minted).count() === 1, 'the refreshed table lost the sent copy');
    await button(dialog, 'Close').click();
  });

  await s.step('a lost reply, then Send again, sends the same operation id once', async () => {
    const other = await documentPage(second);
    await other.page.route('**/demographic/PortalDelivery', async route => {
      const request = route.request();
      if (request.method() !== 'POST' || new URLSearchParams(request.postData() || '').get('method') !== 'send') return route.fallback();
      await route.fetch();
      return route.abort('failed');
    }, { times: 1 });
    const dialog = await openDialog(other.page, second);
    const id = await dialog.getAttribute('data-delivery');
    h.assert(UUID_V4.test(id || '') && id !== minted, 'the second document has no operation id of its own');
    await button(dialog, 'Send to myCarlos').click();
    await message(dialog).filter({ hasText: 'could not be completed' }).waitFor({ timeout: TIMEOUT });
    h.assert(await button(dialog, 'Close').isEnabled(), 'Close stayed disabled after a failed send');
    await button(dialog, 'Send to myCarlos').click();
    await message(dialog).filter({ hasText: 'Sent.' }).waitFor({ timeout: TIMEOUT });
    const sends = other.calls.filter(call => call.operation === 'send');
    h.assert(sends.length === 2, `expected two sends, saw ${sends.length}`);
    h.assert(sends.every(call => call.body.get('deliveryId') === id && call.body.get('documentId') === second),
      'the retry did not repeat the same operation for the same document');
    h.assert(sends[0].body.get('deviceKeyId') === sends[1].body.get('deviceKeyId'), 'the retry changed device');
    h.assert(await rowsFor(dialog, id).count() === 1, 'the retried copy is listed more than once');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM mycarlos_delivery_outbox WHERE delivery_id=${h.sqlString(id)}`) === '1',
      'the retry queued a second copy');
    // The reply this step dropped on purpose leaves one failed request, and the console line the
    // browser writes for it. They are this step's own doing, not a page fault: take out exactly
    // those (and require the failed request, so the drop is known to have happened). Anything else
    // stays for the strict check after the step.
    const dropped = entry => /\/demographic\/PortalDelivery$/.test(entry.url || (entry.location && entry.location.url) || '')
      && /ERR_FAILED/.test(entry.errorText || entry.text || '');
    const failure = s.recorder.requestFailures.findIndex(dropped);
    h.assert(failure >= 0, 'the dropped reply left no failed request');
    s.recorder.requestFailures.splice(failure, 1);
    const line = s.recorder.consoleIssues.findIndex(dropped);
    if (line >= 0) s.recorder.consoleIssues.splice(line, 1);
    await button(dialog, 'Close').click();
    await other.page.close();
  });

  await s.step('the pending-documents view, which runs no inserted scripts, opens each document its own dialog', async () => {
    const queue = await s.context.newPage();
    const queueCalls = watchDeliveries(queue, origins);
    await h.gotoApp(queue, s.config.baseUrl, '/documentManager/inboxManage?method=getDocumentsInQueues');
    await h.assertNotErrorPage(queue, 'pending documents');
    // Insert both documents the way this view does (showDocLab, scripts not run), without
    // changing any queue membership.
    await queue.evaluate(({ ids, provider }) => {
      for (const id of ids) {
        window.docType[id] = 'DOC';
        const panel = document.createElement('div');
        panel.id = 'sendDialogPanel' + id;
        document.getElementById('docs').appendChild(panel);
        window.showDocLab(panel.id, id, provider, provider, 'N', '', 0);
      }
    }, { ids: [first, second], provider: s.provider });
    await openerOf(queue, first).waitFor({ timeout: TIMEOUT });
    await openerOf(queue, second).waitFor({ timeout: TIMEOUT });
    const pagesBefore = s.context.pages().length;
    for (const id of [second, first]) {
      const dialog = await openDialog(queue, id);
      const read = queueCalls.filter(call => call.operation === 'read').at(-1);
      h.assert(read && read.url.searchParams.get('documentId') === id, `the dialog for ${id} read another document`);
      await button(dialog, 'Close').click();
      await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    }
    h.assert(s.context.pages().length === pagesBefore, 'a dialog opened a new page in the pending-documents view');
    await queue.close();
  });

  await s.step('a send without a CSRF token is refused and queues nothing', async () => {
    const before = s.sql.value(`SELECT COUNT(*) FROM mycarlos_delivery_outbox WHERE demographic_no=${patient}`);
    const response = await page.request.post(h.appUrl(s.config.baseUrl, '/demographic/PortalDelivery'), {
      form: { method: 'send', demographicNo: patient, documentId: first,
        deliveryId: '00000000-0000-4000-8000-00000000c5f1', deviceKeyId: 'A'.repeat(43) },
      maxRedirects: 0,
    });
    await h.assertRefused(s, { response, table: 'mycarlos_delivery_outbox', where: `demographic_no=${patient}`,
      before, label: 'a token-less send' });
  });

  await s.step('every request stayed on this origin', async () => {
    const elsewhere = origins.filter(value => value !== origin && !value.startsWith('data:') && value !== 'null');
    h.assert(!elsewhere.length, `requests left the origin: ${[...new Set(elsewhere)].join(', ')}`);
  });
}

if (require.main === module) runWorkflow('mycarlos-send-dialog', workflow, { openPatient: false });

module.exports = { workflow, fixture };
