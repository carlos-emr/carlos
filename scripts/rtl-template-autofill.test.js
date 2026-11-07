/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const editor = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/eform-assets/editControl2.js'), 'utf8');
const apcache = fs.readFileSync(path.join(__dirname, '../src/main/webapp/library/eforms/APCache.js'), 'utf8');

// Run the real parser, cache transport and lookup completion logic together.
// HTTP/jQuery parsing, editor DOM, simplified composites and notice rendering are fixtures;
// the packaged Playwright check exercises the shipped forms and their actual mappings.
function setup({flushCache = true, text = '##letterhead## / ##ageGender## / ##_ClosingSalutation##'} = {}) {
  const requests = [], failures = [], statuses = [], composed = [];
  const frame = {contentDocument: {}};
  const jquery = response => {
    if (!Array.isArray(response)) return {attr: name => response[name], val: () => response.value};
    response.filter = selector => {
      const name = selector.match(/name="([^"]+)"/)[1];
      const matches = Array.prototype.filter.call(response, item => item.name === name);
      matches.val = () => matches[0]?.value;
      return matches;
    };
    return response;
  };
  jquery.ajax = request => requests.push(request);
  const context = vm.createContext({
    console, window: {location: {search: '?fid=1&demographic_no=17'}},
    stampImagesIn: () => [], bindStampFallbacks: () => {}, // DOM stamp behavior is outside the parser/cache fixture.
    document: {getElementById: id => id === 'edit' ? frame : null},
    jQuery: jquery, cfg_editorname: 'edit', measureTemplateRevision: 0, measureTemplateLookupsPending: 0,
    editControlContents: () => text, seteditControlContents: (_name, value) => {text = value;},
    showLetterStatus: (...args) => statuses.push(args), finishPendingSourceView() {}, prompt: key => { throw new Error('Unexpected prompt: ' + key); },
  });
  vm.runInContext(apcache, context);
  context.showLookupFailureNotice = keys => failures.push(keys);
  context.cache = context.createCache({flushCache});
  const mapping = (name, values, compose) => context.cache.addMapping({name, values,
    storeInCacheHandler() {composed.push(name);if (compose) context.cache.put(name, compose());}});
  const get = key => context.cache.get(key);
  mapping('template', []);
  mapping('letterhead', ['clinic_name', 'doctor'], () => get('clinic_name') + ' / ' + get('doctor'));
  mapping('_age', ['age', 'ageComplex'], () => get('ageComplex') || get('age') + ' year old');
  context.cache.addMapping({name: 'sex', values: ['sex'], storeInCacheHandler(_key, value) {
    context.cache.put('sex', value);context.cache.put('gender', value === 'F' ? 'female' : 'male');
  }});
  mapping('gender', ['sex']);
  mapping('ageGender', ['_age', 'gender', 'age', 'sex', 'ageComplex'], () => get('_age') + ' ' + get('gender'));
  mapping('_ClosingSalutation', ['doctor'], () => 'Yours Sincerely ' + get('doctor'));
  vm.runInContext(editor.slice(editor.indexOf('function finishTemplateLookup('), editor.indexOf('// add RTL specific key values')), context);
  vm.runInContext(editor.slice(editor.indexOf('function parseTemplate()'), editor.indexOf('function parseText(')), context);
  const values = {clinic_name: 'FAKE Clinic', doctor: 'FAKE Doctor', age: '46', ageComplex: '', sex: 'F'};
  return {context, frame, requests, failures, composed, statuses, text: () => text,
    keys: () => new URLSearchParams(requests.at(-1).data).getAll('key'),
    respond(overrides = {}, missing = '') {
      Object.assign(values, overrides);
      const response = [{name: 'oscarAPCacheLookupType', value: 'template'}];
      for (const key of this.keys()) response.push({name: key, value: values[key] || ''});
      if (missing) response.push({name: 'oscarAPCacheLookupFailures', value: missing});
      requests.at(-1).success(response);
    },
  };
}

test('nested composites request only unique server keys and compose after all inputs arrive', () => {
  const f = setup();f.context.parseTemplate();
  assert.deepEqual(f.keys(), ['clinic_name', 'doctor', 'age', 'ageComplex', 'sex']);
  assert.equal(f.context.measureTemplateLookupsPending, 1);
  f.respond();
  assert.equal(f.text(), 'FAKE Clinic / FAKE Doctor / 46 year old female / Yours Sincerely FAKE Doctor');
  assert(f.composed.indexOf('_age') < f.composed.indexOf('ageGender'));
  assert.deepEqual(f.failures, []);
  assert(f.statuses.every(([, message]) => message === ''));
  assert.equal(f.context.measureTemplateLookupsPending, 0);
});

test('shared and repeated placeholders do not duplicate requested keys or composition', () => {
  const f = setup({text: '##ageGender##, ##_age##, ##ageGender##, ##sex##'});f.context.parseTemplate();f.respond();
  assert.equal(f.keys().filter(key => key === 'sex').length, 1);
  assert.equal(f.composed.filter(key => key === 'ageGender').length, 1);
  assert.equal(f.text(), '46 year old female, 46 year old, 46 year old female, F');
});

test('a flushing lookup includes placeholders cached by an earlier template', () => {
  const f = setup();f.context.cache.put('letterhead', 'old clinic');f.context.parseTemplate();f.respond();
  assert(f.keys().includes('clinic_name'));
  assert.match(f.text(), /^FAKE Clinic/);
});

test('a non-flushing cache preserves an existing composite while resolving another', () => {
  const f = setup({flushCache: false});f.context.cache.put('letterhead', 'cached clinic');f.context.parseTemplate();f.respond();
  assert(!f.keys().includes('clinic_name'));
  assert.match(f.text(), /^cached clinic/);
});

test('a template wholly in a non-flushing cache needs no request', () => {
  const f = setup({flushCache: false, text: '##letterhead##'});f.context.cache.put('letterhead', 'cached clinic');f.context.parseTemplate();
  assert.equal(f.requests.length, 0);assert.equal(f.text(), 'cached clinic');
});

test('a wholly cached template also avoids flushing the default cache', () => {
  const f = setup({text: '##letterhead##'});f.context.cache.put('letterhead', 'cached clinic');f.context.parseTemplate();
  assert.equal(f.requests.length, 0);assert.equal(f.text(), 'cached clinic');
});

test('genuinely unknown keys still reach the server and report lookup failures', () => {
  const f = setup({text: '##renderer_missing_ap_123##'});
  f.context.prompt = () => '';
  f.context.parseTemplate();assert.deepEqual(f.keys(), ['renderer_missing_ap_123']);
  f.respond({}, 'renderer_missing_ap_123');assert.deepEqual(f.failures, ['renderer_missing_ap_123']);
});

test('a replaced template rejects a late response before caching or composing', () => {
  const f = setup();f.context.parseTemplate();f.frame.contentDocument = {};f.respond();
  assert.equal(f.context.cache.contains('clinic_name'), false);
  assert.deepEqual(f.composed, []);assert.deepEqual(f.statuses, []);
  assert.equal(f.context.measureTemplateLookupsPending, 0);
});

test('a failed request leaves the template unexpanded and settles its pending count', () => {
  const f = setup();f.context.parseTemplate();f.requests[0].error(null, 'timeout');
  assert.match(f.text(), /##letterhead##/);assert.deepEqual(f.composed, []);
  assert.match(f.statuses.at(-1)[1], /Template fields could not be loaded/);assert.equal(f.context.measureTemplateLookupsPending, 0);
});


test('a replaced letter receives no status warning from a late lookup failure', () => {
  const f = setup();f.context.parseTemplate();f.frame.contentDocument = {};
  f.requests[0].error(null, 'timeout');
  assert.deepEqual(f.statuses, []);assert.deepEqual(f.composed, []);
  assert.equal(f.context.measureTemplateLookupsPending, 0);
});
