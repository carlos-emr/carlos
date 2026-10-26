/* Copyright (c) 2026 CARLOS Contributors. GPL v2 or later. */
'use strict';

const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

// Execute the actual preview handler: no second, test-only implementation of its decision flow.
const jsp = readFileSync(resolve(__dirname,
  '../src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp'), 'utf8');
const handler = jsp.slice(jsp.indexOf('function getConsultFormPrintPreview('), jsp.indexOf('function showPreview('))
  .replace('${carlos:forJavaScript(previewAttachmentsUnavailableMessage)}', 'Omitted: {0}. Proceed?')
  .replace('${carlos:forJavaScript(previewRequestFailedMessage)}', 'Failed: {0}, {1}');

function browser(decisions = []) {
  const requests = [], downloads = [], prompts = [], alerts = [];
  const signature = { value: '' }, newSignature = { value: 'true' };
  const form = { submission: { value: '' } };
  const jquery = () => ({ serialize: () => 'submission=And+Print+Preview&newSignature=' + newSignature.value });
  jquery.ajax = options => requests.push(options);
  const context = vm.createContext({
    jQuery: jquery, HideSpin() {}, isSignatureSaved: false,
    isStoredSignatureId: value => /^\d+$/.test(value),
    document: { getElementById: id => ({ signatureImg: signature, newSignature })[id] },
    window: { confirm: message => { prompts.push(message); return decisions.shift() ?? false; } },
    alert: message => alerts.push(message),
    showPreview: (pdf, name) => downloads.push({ pdf, name }),
  });
  vm.runInContext(handler, context);
  context.getConsultFormPrintPreview(form);
  return { requests, downloads, prompts, alerts, form, newSignature };
}
const review = token => ({ attachmentReviewRequired: true, consultAttachmentApproval: token,
  attachmentWarnings: ['Document 80 could not be read'] });
const ready = { consultPDF: 'synthetic-pdf', consultPDFName: 'consult.pdf', attachmentReviewRequired: false };

test('clean preview downloads directly', () => {
  const b = browser();
  b.requests[0].success(ready);
  assert.equal(b.downloads.length, 1);
  assert.equal(b.prompts.length, 0);
});

test('cancel keeps edits and never requests or downloads the incomplete PDF', () => {
  const b = browser([false]);
  b.requests[0].success(review('ticket'));
  assert.equal(b.prompts.length, 1);
  assert.equal(b.requests.length, 1);
  assert.equal(b.downloads.length, 0);
});

test('approval retries with the ticket and releases only the approved response', () => {
  const b = browser([true]);
  b.requests[0].success({ ...review('ticket/+'), signatureImg: '42' });
  assert.equal(b.downloads.length, 0);
  assert.equal(b.newSignature.value, 'false');
  assert.match(b.requests[1].data, /newSignature=false/);
  assert.match(b.requests[1].data, /confirmAttachmentOmissions=true&consultAttachmentApproval=ticket%2F%2B/);
  b.requests[1].success(ready);
  assert.equal(b.downloads.length, 1);
});

test('changed warnings need a second decision; cancelling never downloads', () => {
  const b = browser([true, false]);
  b.requests[0].success(review('old-ticket'));
  b.requests[1].success({ ...review('new-ticket'), attachmentWarnings: ['eForm 12 could not be read'] });
  assert.equal(b.prompts.length, 2);
  assert.match(b.prompts[1], /eForm 12/);
  assert.equal(b.downloads.length, 0);
  assert.equal(b.requests.length, 2);
});

test('render failure alerts without issuing an approval request or download', () => {
  const b = browser();
  b.requests[0].success({ errorMessage: 'Renderer busy' });
  assert.deepEqual(b.alerts, ['Renderer busy']);
  assert.equal(b.downloads.length, 0);
  assert.equal(b.prompts.length, 0);
});
