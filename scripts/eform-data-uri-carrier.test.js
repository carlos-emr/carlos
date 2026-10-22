/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
/*
 * The page half of the eForm signature data-URI carrier (#3552), run from the shipped toolbar source.
 *
 * eform_floating_toolbar.js moves signature-pad data URIs out of an eForm's author-named fields into
 * the one argument the WAF exempts (carlos-data-uri) and leaves carlos-data-uri:<n> behind;
 * AddEForm2Action restores them through EFormDataUriCarrier. The shared case file makes both sides
 * decide every value the same way (EFormDataUriCarrierUnitTest reads it too). The real browser paths
 * (formdata event, legacy temporary inputs) are driven in Chromium by eform-toolbar-browser-check.js.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
const start = source.indexOf('// <eform-data-uri-carrier>');
const end = source.indexOf('// </eform-data-uri-carrier>', start);
assert.ok(start >= 0 && end > start, 'carrier section markers are present');
const carrierSource = source.slice(start, end);
const shared = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/eform/data-uri-carrier-cases.json'), 'utf8'));

const PNG = shared.cases.find(c => c.name === 'canvas PNG').value;
const JSIGNATURE = shared.cases.find(c => c.name === 'jSignature strokes').value;
const ORIGIN = 'https://emr.example.test';

class FakeForm {
  constructor({ action = '../eform/addEForm?efmfid=1', method = 'post' } = {}) {
    this.attributes = { action, method };
    // What a template field named "action" or "method" does to the properties.
    this.action = { name: 'action', value: 'clobbered' };
    this.method = { name: 'method', value: 'clobbered' };
  }
  getAttribute(name) { return Object.hasOwn(this.attributes, name) ? this.attributes[name] : null; }
}

/** Loads the carrier section with stub browser globals; returns its API and what it registered. */
function load({ formDataEvent = true } = {}) {
  const listeners = [];
  const timers = [];
  let nativeSubmits = 0;
  FakeForm.prototype.submit = function () { nativeSubmits++; };
  const nativeSubmit = FakeForm.prototype.submit;
  const context = {
    eformFormDataEventSupported: formDataEvent,
    HTMLFormElement: FakeForm,
    document: {
      baseURI: ORIGIN + '/carlos/eform/efmformadd_data?fid=1',
      addEventListener: (type, listener, capture) => listeners.push({ type, listener, capture }),
    },
    window: { location: { origin: ORIGIN } },
    atob, URL, Map, Set, Array,
    setTimeout: callback => timers.push(callback),
  };
  vm.runInNewContext(carrierSource + '\nthis.carrier = eformDataUriCarrier;', context);
  return { carrier: context.carrier, listeners, timers, nativeSubmit, nativeSubmits: () => nativeSubmits };
}

test('decides every shared case exactly as EFormDataUriCarrier does', () => {
  const { carrier } = load();
  assert.ok(shared.cases.length > 20);
  for (const c of shared.cases) {
    assert.equal(carrier.isCarriable(c.value), c.accepted, c.name);
  }
});

test('refuses non-strings and payloads past the shared size limit', () => {
  const { carrier } = load();
  assert.equal(carrier.isCarriable(undefined), false);
  assert.equal(carrier.isCarriable({ value: PNG }), false);
  // MAX_PAYLOAD_CHARS = EFormDataUriCarrier.MAX_BASE64_CHARS; one more 4-character group is refused.
  const header = PNG.slice('data:image/png;base64,'.length, 'data:image/png;base64,'.length + 12);
  const at = 'data:image/png;base64,' + header + 'A'.repeat(6990504 - 12);
  assert.equal(carrier.isCarriable(at), true);
  assert.equal(carrier.isCarriable(at + 'AAAA'), false);
  assert.equal(carrier.isCarriable('data:image/jsignature;base30,' + 'a'.repeat(6990505)), false);
});

test('rewrites the request: references in place, carried values appended once each', () => {
  const { carrier } = load();
  const data = new FormData();
  data.append('efmfid', '1');
  data.append('SignatureBox', PNG);
  data.append('clinicalNote', 'Routine follow up.');
  data.append('StoreSignature1', JSIGNATURE);
  data.append('MirrorSignature', PNG);
  data.append('carlos-image-link', JSON.stringify({ id: 'sig', value: PNG }));
  data.append('svgDrawing', 'data:image/svg+xml;base64,PHN2Zz4=');

  assert.equal(carrier.carryFormData(data), 2);

  assert.deepEqual(Array.from(data.entries()), [
    ['efmfid', '1'],
    ['SignatureBox', 'carlos-data-uri:0'],
    ['clinicalNote', 'Routine follow up.'],
    ['StoreSignature1', 'carlos-data-uri:1'],
    ['MirrorSignature', 'carlos-data-uri:0'],
    // The image placeholder has its own exemption (1030) and is JSON, never carried.
    ['carlos-image-link', JSON.stringify({ id: 'sig', value: PNG })],
    // SVG can hold script: posted where it is, so the WAF still scores it.
    ['svgDrawing', 'data:image/svg+xml;base64,PHN2Zz4='],
    ['carlos-data-uri', PNG],
    ['carlos-data-uri', JSIGNATURE],
  ]);
});

test('keeps repeated names and file entries in their places', () => {
  const { carrier } = load();
  const data = new FormData();
  const file = new File(['scan'], 'scan.pdf', { type: 'application/pdf' });
  data.append('pads', PNG);
  data.append('upload', file);
  data.append('pads', 'second value');
  carrier.carryFormData(data);
  const entries = Array.from(data.entries());
  assert.deepEqual(entries.map(([name]) => name), ['pads', 'upload', 'pads', 'carlos-data-uri']);
  assert.deepEqual(data.getAll('pads'), ['carlos-data-uri:0', 'second value']);
  assert.equal(entries[1][1].name, 'scan.pdf');
});

test('leaves a request with nothing to carry, or already carried, untouched', () => {
  const { carrier } = load();
  const plain = new FormData();
  plain.append('clinicalNote', 'carlos-data-uri:0');
  assert.equal(carrier.carryFormData(plain), 0);
  assert.deepEqual(Array.from(plain.entries()), [['clinicalNote', 'carlos-data-uri:0']]);

  const carried = new FormData();
  carried.append('SignatureBox', PNG);
  carried.append('carlos-data-uri', PNG);
  assert.equal(carrier.carryFormData(carried), 0);
  assert.equal(carried.get('SignatureBox'), PNG);
});

test('carries at most EFormDataUriCarrier.MAX_VALUES distinct values', () => {
  const { carrier } = load();
  const pads = [];
  for (let i = 0; i < 258; i++) {
    // Distinct, valid PNG data URIs: the PNG signature then a varying tail.
    const tail = Buffer.from(String(i).padStart(3, '0') + '!').toString('base64');
    pads.push(['pad' + i, PNG.slice(0, 'data:image/png;base64,'.length + 12) + tail]);
  }
  assert.ok(pads.every(([, value]) => carrier.isCarriable(value)));
  const plan = carrier.plan(pads);
  assert.equal(plan.carried.length, 256);
  assert.equal(plan.replacements.size, 256);
  assert.equal(plan.replacements.has(256), false);
});

test('only an eForm save form is rewritten', () => {
  const { carrier } = load();
  // The server writes action="../eform/addEForm?..."; fields named action/method do not hide it.
  assert.equal(carrier.postsToEFormSave(new FakeForm()), true);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: ORIGIN + '/eform/addEForm' })), true);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ method: null })), false);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: null })), false);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: ORIGIN + '/carlos/eform/addEForm;jsessionid=x?efmfid=1' })), true);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ method: 'POST' })), true);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ method: 'get' })), false);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: ORIGIN + '/carlos/eform/addEFormEvil' })), false);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: ORIGIN + '/carlos/eform/editForm' })), false);
  assert.equal(carrier.postsToEFormSave(new FakeForm({ action: 'https://elsewhere.test/carlos/eform/addEForm' })), false);
  assert.equal(carrier.postsToEFormSave({ getAttribute: () => 'post' }), false);
});

test('with the formdata event, rewrites each eForm save request as it is built', () => {
  const { listeners } = load();
  const formdata = listeners.filter(l => l.type === 'formdata');
  assert.equal(formdata.length, 1);
  assert.equal(formdata[0].capture, true, 'capture phase: formdata does not bubble');
  assert.equal(listeners.some(l => l.type === 'submit'), false);

  const save = new FormData();
  save.append('SignatureBox', PNG);
  formdata[0].listener({ target: new FakeForm(), formData: save });
  assert.equal(save.get('SignatureBox'), 'carlos-data-uri:0');

  const other = new FormData();
  other.append('SignatureBox', PNG);
  formdata[0].listener({ target: new FakeForm({ action: ORIGIN + '/carlos/somewhere' }), formData: other });
  assert.equal(other.get('SignatureBox'), PNG);
});

test('without the formdata event, wraps form.submit() and the submit event instead', () => {
  const { listeners, nativeSubmit, nativeSubmits } = load({ formDataEvent: false });
  assert.equal(listeners.some(l => l.type === 'formdata'), false);
  const submit = listeners.filter(l => l.type === 'submit');
  assert.equal(submit.length, 1);
  assert.notEqual(submit[0].capture, true, 'bubble phase, after the template\'s own submit handlers');
  assert.notEqual(FakeForm.prototype.submit, nativeSubmit);
  // A form that is not an eForm save passes straight through.
  new FakeForm({ action: ORIGIN + '/carlos/somewhere' }).submit();
  assert.equal(nativeSubmits(), 1);
});
