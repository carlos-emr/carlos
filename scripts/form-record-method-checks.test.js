/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { assertNativeFormPost, assertFormRefusal, requestFormRefusal, assertFormSaveDelta, assertOwnedFormRows }
  = require('./lib/form-record-method-checks');
const baseUrl = new URL('https://127.0.0.1/carlos');
const headers = { [h.APPLICATION_HEADER]: 'none', location: '/carlos/form/SetupForm?formName=VTForm&formId=17&demographic_no=101' };
const payload = () => new URLSearchParams({ demographic_no: '101', 'value(formName)': 'VTForm',
  'value(formId)': '0', 'value(ExerValue)': '150', 'value(ExerComments)': 'FAKE clinical sentinel',
  'CSRF-TOKEN': 'owned-form-token', submit: 'save' });
const native = () => ({ url: 'https://127.0.0.1/carlos/form/SubmitForm', method: 'POST',
  data: payload().toString(), status: 302, headers: { ...headers } });

test('the actual native VT POST retains its canonical patient, new-form identity and saved redirect', () => {
  const result = assertNativeFormPost(native(), { patient: '101', previousId: '0', baseUrl });
  assert.equal(result.id, '17');
  assert.equal(result.parameters.get('value(ExerComments)'), 'FAKE clinical sentinel');
});

for (const change of ['method', 'origin', 'route', 'patient', 'repeated-patient', 'form-name', 'form-id', 'submit', 'csrf',
  'status', 'application', 'redirect-origin', 'redirect-patient', 'redirect-route', 'redirect-id']) {
  test(`native VT proof rejects ${change}`, () => {
    const result = native();
    const values = new URLSearchParams(result.data);
    if (change === 'method') result.method = 'GET';
    if (change === 'origin') result.url = 'https://foreign.invalid/carlos/form/SubmitForm';
    if (change === 'route') result.url = 'https://127.0.0.1/carlos/form/Other';
    if (change === 'patient') values.set('demographic_no', '202');
    if (change === 'repeated-patient') values.append('demographic_no', '101');
    if (change === 'form-name') values.set('value(formName)', 'Other');
    if (change === 'form-id') values.set('value(formId)', '9');
    if (change === 'submit') values.set('submit', 'exit');
    if (change === 'csrf') values.delete('CSRF-TOKEN');
    if (change === 'status') result.status = 403;
    if (change === 'application') delete result.headers[h.APPLICATION_HEADER];
    if (change === 'redirect-origin') result.headers.location = 'https://foreign.invalid/carlos/form/SetupForm?formName=VTForm&formId=17&demographic_no=101';
    if (change === 'redirect-patient') result.headers.location = '/carlos/form/SetupForm?formName=VTForm&formId=17&demographic_no=202';
    if (change === 'redirect-route') result.headers.location = '/carlos/login?formName=VTForm&formId=17&demographic_no=101';
    if (change === 'redirect-id') result.headers.location = '/carlos/form/SetupForm?formName=VTForm&formId=0&demographic_no=101';
    result.data = values.toString();
    assert.throws(() => assertNativeFormPost(result, { patient: '101', previousId: '0', baseUrl }));
  });
}

test('a record denial and POST-only refusal require exact application responses and contain no form', () => {
  assert.doesNotThrow(() => assertFormRefusal({ status: 403, headers, body: '<p>Security Exception</p>' }, { status: 403 }));
  assert.doesNotThrow(() => assertFormRefusal({ status: 405, headers: { ...headers, allow: 'POST' }, body: '' }, { status: 405 }));
});

for (const change of ['status', 'application', 'allow', 'clinical', 'form', 'waf']) {
  test(`form refusal rejects ${change} evidence`, () => {
    const result = { status: 405, headers: { ...headers, allow: 'POST' }, body: '<p>Not allowed</p>' };
    if (change === 'status') result.status = 500;
    if (change === 'application') delete result.headers[h.APPLICATION_HEADER];
    if (change === 'allow') result.headers.allow = 'GET';
    if (change === 'clinical') result.body += 'FAKE clinical sentinel';
    if (change === 'form') result.body += '<input name="value(ExerValue)">';
    if (change === 'waf') { result.status = 403; result.body = 'ModSecurity: Access denied'; }
    assert.throws(() => assertFormRefusal(result, { status: change === 'waf' ? 403 : 405,
      tokens: ['FAKE clinical sentinel'] }));
  });
}

for (const method of ['GET', 'HEAD', 'PUT', 'POST']) {
  test(`the actual ${method} refusal boundary preserves authority, transport and response disposal`, async () => {
    let disposed = 0;
    const expectedStatus = method === 'POST' ? 403 : 405;
    const context = { request: { async fetch(address, options) {
      const sent = method === 'GET' || method === 'HEAD' ? new URL(address).searchParams : new URLSearchParams(options.form);
      assert.equal(options.method, method);
      assert.equal(options.maxRedirects, 0);
      assert.equal(sent.get('demographic_no'), '101');
      assert.equal(sent.get('CSRF-TOKEN'), 'owned-form-token');
      assert.equal(sent.get('value(formId)'), '0');
      assert.equal(sent.get('value(ExerComments)'), 'FAKE clinical sentinel');
      if (method === 'GET' || method === 'HEAD') assert.equal(options.form, undefined);
      return { status: () => expectedStatus, headers: () => ({ ...headers, allow: 'POST' }),
        text: async () => '', dispose: async () => { disposed++; } };
    } } };
    await requestFormRefusal(context, baseUrl, payload(), { method, status: expectedStatus });
    assert.equal(disposed, 1);
  });
}

for (const failure of ['wrong-status', 'body-read']) {
  test(`a ${failure} failure still disposes its exact API response`, async () => {
    let disposed = 0;
    const context = { request: { async fetch() { return { status: () => failure === 'wrong-status' ? 500 : 403,
      headers: () => headers, text: async () => { if (failure === 'body-read') throw new Error('FAKE response read failure'); return ''; },
      dispose: async () => { disposed++; } }; } } };
    await assert.rejects(requestFormRefusal(context, baseUrl, payload(), { method: 'POST', status: 403 }));
    assert.equal(disposed, 1);
  });
}

const before = () => ({ demographic: [['101', 'FAKE patient']], forms: [['1', 'FAKE existing form']],
  measurements: [['2', 'FAKE existing reading']], notes: [], drafts: [], locks: [], admissions: [] });
const after = () => ({ ...before(), forms: [...before().forms, ['17', 'FAKE new form']],
  measurements: [...before().measurements, ['18', 'FAKE new reading']] });
test('native save permits only one exact form and reading while preserving all historical state', () => {
  assert.doesNotThrow(() => assertFormSaveDelta(before(), after(), { formId: '17', measurementId: '18' }));
});

for (const change of ['old-form', 'old-reading', 'draft', 'lock', 'note', 'admission', 'missing-domain', 'duplicate-form', 'missing-reading']) {
  test(`native save delta rejects ${change}`, () => {
    const current = after();
    if (change === 'old-form') current.forms[0][1] = 'changed';
    if (change === 'old-reading') current.measurements[0][1] = 'changed';
    if (change === 'draft') current.drafts.push(['19', 'typed draft']);
    if (change === 'lock') current.locks.push(['20', 'lock']);
    if (change === 'note') current.notes.push(['21', 'note']);
    if (change === 'admission') current.admissions.push(['22', 'program']);
    if (change === 'missing-domain') delete current.drafts;
    if (change === 'duplicate-form') current.forms.push(['17', 'duplicate']);
    if (change === 'missing-reading') current.measurements.pop();
    assert.throws(() => assertFormSaveDelta(before(), current, { formId: '17', measurementId: '18' }));
  });
}

const owned = () => ({ state: { demographic: [['101', 'owned']], demographicExtensions: [],
  forms: [['17', 'full form']], measurements: [['18', 'full measurement']], notes: [], drafts: [], locks: [],
  deletedMeasurements: [], measurementExtensions: [], documents: [], groupLinks: [] },
forms: [['17', '899991', '150', 'FAKE owned form']], measurements: [['18', '899991', 'Exer', '150', 'FAKE owned form']],
patient: '101', provider: '899991', intents: [{ patient: '101', comment: 'FAKE owned form', value: '150' }] });
test('owned cleanup accepts only explicitly intended patient/provider/form and reading relationships', () => {
  assert.doesNotThrow(() => assertOwnedFormRows(owned()));
});
for (const change of ['form-author', 'form-body', 'form-value', 'reading-author', 'reading-type', 'reading-body', 'patient',
  'extra-draft', 'extra-lock', 'extra-note', 'extra-document', 'extra-group-link', 'deleted-reading', 'extension', 'missing-summary']) {
  test(`cleanup refuses ${change} before reaching deletion`, () => {
    const fixture = owned();
    if (change === 'form-author') fixture.forms[0][1] = '899992';
    if (change === 'form-body') fixture.forms[0][3] = 'foreign form';
    if (change === 'form-value') fixture.forms[0][2] = '151';
    if (change === 'reading-author') fixture.measurements[0][1] = '899992';
    if (change === 'reading-type') fixture.measurements[0][2] = 'WT';
    if (change === 'reading-body') fixture.measurements[0][4] = 'foreign reading';
    if (change === 'patient') fixture.intents[0].patient = '202';
    if (change === 'extra-draft') fixture.state.drafts.push(['1']);
    if (change === 'extra-lock') fixture.state.locks.push(['1']);
    if (change === 'extra-note') fixture.state.notes.push(['1']);
    if (change === 'extra-document') fixture.state.documents.push(['1']);
    if (change === 'extra-group-link') fixture.state.groupLinks.push(['1']);
    if (change === 'deleted-reading') fixture.state.deletedMeasurements.push(['1']);
    if (change === 'extension') fixture.state.measurementExtensions.push(['1']);
    if (change === 'missing-summary') fixture.forms.pop();
    let reachedDelete = false;
    assert.throws(() => { assertOwnedFormRows(fixture); reachedDelete = true; });
    assert.equal(reachedDelete, false);
  });
}
