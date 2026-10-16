/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// Configure Email > Clinic Email Logo (issue #3981, Ben's demo of 15 Oct 2026): the page refuses a
// picture CARLOS would refuse before sending it, so typed footer text is never lost to a reload, and
// any footer text not saved yet travels with the logo form.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.join(__dirname, '..',
  'src/main/webapp/share/javascript/email/clinicLogoForm.js'), 'utf8');

function classList() {
  const names = new Set(['d-none']);
  return {add: name => names.add(name), remove: name => names.delete(name), contains: name => names.has(name)};
}

// The page as clinicLogoForm.js sees it, with one chosen file and a picture size for the decoder.
function page({file, picture, decoder = true, requestSubmit = true}) {
  const listeners = {};
  const sent = [];
  const message = {textContent: '', classList: classList()};
  const hidden = {clinicFooterDraft: {value: ''}, clinicFooterDraftFingerprint: {value: ''}};
  const attributes = {
    'data-max-bytes': '102400', 'data-max-width': '600', 'data-max-height': '200',
    'data-too-big': 'This picture is {0} KB. The limit is 100 KB and 600 x 200 pixels.',
    'data-too-large': 'This picture is {0} x {1} pixels. The limit is 600 x 200 pixels and 100 KB.',
    'data-not-image': 'Not a PNG or JPEG picture.'
  };
  const form = {
    getAttribute: name => attributes[name],
    querySelector: () => ({files: file ? [file] : []}),
    elements: {namedItem: name => hidden[name]},
    addEventListener: (type, listener) => { listeners[type] = listener; },
    appendChild: child => sent.push({appended: child}),
    submit: () => sent.push({submit: true})
  };
  if (requestSubmit) {
    form.requestSubmit = submitter => {
      sent.push({requestSubmit: submitter.value});
      // The browser fires submit again for requestSubmit.
      form.lastEvent = submitEvent(submitter);
      listeners.submit(form.lastEvent);
    };
  }
  const elements = {
    clinicLogoForm: form,
    clinicLogoCheck: message,
    clinicFooter: {value: '<b>FAKE typed footer</b>'}
  };
  let ready;
  const window = {};
  if (decoder) {
    window.createImageBitmap = () => picture
      ? Promise.resolve({width: picture[0], height: picture[1], close() {}})
      : Promise.reject(new Error('cannot decode'));
  }
  const document = {
    addEventListener: (type, listener) => { if (type === 'DOMContentLoaded') ready = listener; },
    getElementById: id => elements[id] || null,
    querySelector: selector => selector === '#clinicFooterForm input[name="clinicFooterFingerprint"]'
      ? {value: 'f'.repeat(64)} : null,
    createElement: () => ({})
  };
  vm.runInNewContext(SOURCE, {window, document});
  ready();
  return {form, message, hidden, sent, submit: submitter => {
    const event = submitEvent(submitter);
    listeners.submit(event);
    return event;
  }};
}

function submitEvent(submitter) {
  return {submitter, prevented: false, preventDefault() { this.prevented = true; }};
}

const upload = {name: 'logoAction', value: 'upload'};
const remove = {name: 'logoAction', value: 'remove'};
const settle = () => new Promise(resolve => setImmediate(resolve));

test('refuses a file over the byte limit on the page, naming both limits', () => {
  const p = page({file: {type: 'image/png', size: 240 * 1024}});
  const event = p.submit(upload);
  assert.equal(event.prevented, true);
  assert.equal(p.message.textContent, 'This picture is 240 KB. The limit is 100 KB and 600 x 200 pixels.');
  assert.equal(p.message.classList.contains('d-none'), false);
  assert.deepEqual(p.sent, []);
});

test('refuses a file that is not a PNG or JPEG', () => {
  const p = page({file: {type: 'image/gif', size: 1000}});
  assert.equal(p.submit(upload).prevented, true);
  assert.equal(p.message.textContent, 'Not a PNG or JPEG picture.');
});

test('refuses a picture over the pixel limit once it is read, and sends nothing', async () => {
  const p = page({file: {type: 'image/jpeg', size: 1000}, picture: [1200, 300]});
  assert.equal(p.submit(upload).prevented, true);
  await settle();
  assert.equal(p.message.textContent, 'This picture is 1200 x 300 pixels. The limit is 600 x 200 pixels and 100 KB.');
  assert.deepEqual(p.sent, []);
});

test('sends a picture that fits, with the button pressed, and lets that submit through', async () => {
  const p = page({file: {type: 'image/png', size: 1000}, picture: [300, 100]});
  assert.equal(p.submit(upload).prevented, true);
  await settle();
  assert.deepEqual(p.sent, [{requestSubmit: 'upload'}]);
  assert.equal(p.form.lastEvent.prevented, false);
});

test('leaves a picture the browser cannot read to the server, which says why', async () => {
  const p = page({file: {type: 'image/png', size: 1000}, picture: null});
  p.submit(upload);
  await settle();
  assert.deepEqual(p.sent, [{requestSubmit: 'upload'}]);
});

test('without a picture decoder, sends at once and leaves the pixel check to the server', () => {
  const p = page({file: {type: 'image/png', size: 1000}, decoder: false});
  assert.equal(p.submit(upload).prevented, false);
});

test('without requestSubmit, still sends which button was pressed', async () => {
  const p = page({file: {type: 'image/png', size: 1000}, picture: [300, 100], requestSubmit: false});
  p.submit(upload);
  await settle();
  assert.deepEqual(p.sent, [{appended: {type: 'hidden', name: 'logoAction', value: 'upload'}}, {submit: true}]);
});

test('carries footer text not saved yet with any logo submit, Remove included', () => {
  const p = page({file: null});
  assert.equal(p.submit(remove).prevented, false);
  assert.equal(p.hidden.clinicFooterDraft.value, '<b>FAKE typed footer</b>');
  assert.equal(p.hidden.clinicFooterDraftFingerprint.value, 'f'.repeat(64));
});
