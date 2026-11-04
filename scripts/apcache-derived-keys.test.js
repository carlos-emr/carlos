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
 * APCache's "Some fields could not be filled in automatically" notice and the page's own mappings.
 *
 * A mapping may list another mapping's name among its values (the Rich Text Letter's ageGender
 * lists "_age" and "gender"), and a template lookup sends each placeholder's name ("letterhead",
 * "_ClosingSalutation"). Those names are derived in the browser: they are sent only so
 * efmformapconfig_lookup echoes them back and the named mapping's storeInCacheHandler runs. The
 * server cannot know that, so it also lists them as unconfigured, and every template load and
 * every "Age & Gender" click raised the notice for fields that were filled in.
 *
 * Runs the real APCache.js against a minimal DOM and a response in the lookup JSP's format.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const AP_CACHE_JS = path.join(__dirname, '../src/main/webapp/library/eforms/APCache.js');

/** The lookup JSP's response: one hidden input per key, then the reserved failure list. */
function lookupResponse(type, values, failures) {
  const inputs = Object.entries(values)
    .map(([name, value]) => `<input type="hidden" name="${name}" value="${value}"/>`);
  if (failures.length) {
    inputs.push(`<input type="hidden" name="oscarAPCacheLookupFailures" value="${failures.join(',')}"/>`);
  }
  return `<input type="hidden" name="oscarAPCacheLookupType" value="${type}"/>${inputs.join('')}`;
}

function fakeJquery(responseHtml, requests) {
  const element = (name, value) => ({ fakeInput: true, name, value });
  const collection = (items) => Object.assign(items.slice(), {
    filter(selector) {
      const name = /\[name="([^"]+)"\]/.exec(selector)[1];
      return collection(items.filter((item) => item.name === name));
    },
    val() { return items.length ? items[0].value : undefined; },
    attr(attribute) { return attribute === 'name' && items.length ? items[0].name : undefined; },
  });
  const jQuery = (argument) => {
    if (argument && argument.fakeInput) { return collection([argument]); }
    const items = Array.from(String(argument).matchAll(/<input[^>]*name="([^"]*)"[^>]*value="([^"]*)"/g),
      (match) => element(match[1], match[2]));
    return collection(items);
  };
  jQuery.ajax = (settings) => {
    requests.push(settings);
    settings.success(responseHtml);
  };
  return jQuery;
}

/** A page with only what showLookupFailureNotice() touches. */
function fakeDocument() {
  const byId = {};
  const node = (tag) => ({
    tagName: tag,
    style: {},
    children: [],
    textContent: '',
    setAttribute(name, value) { if (name === 'id') { byId[value] = this; } this[name] = value; },
    appendChild(child) { this.children.push(child); return child; },
    get firstChild() { return this.children[0]; },
    set id(value) { byId[value] = this; },
  });
  const body = node('body');
  body.insertBefore = (child) => { body.children.unshift(child); return child; };
  return { byId, body, getElementById: (id) => byId[id] || null, createElement: node };
}

function lookUp({ key, mappings, values, failures }) {
  const requests = [];
  const document = fakeDocument();
  const context = {
    console,
    demographicNo: '17',
    window: { location: { search: '?fid=1&demographic_no=17' } },
    document,
    jQuery: fakeJquery(lookupResponse(key, values, failures), requests),
    alert(message) { throw new Error(`unexpected alert: ${message}`); },
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(AP_CACHE_JS, 'utf8'), context);
  const cache = vm.runInContext('createCache({ defaultCacheResponseHandler: function () {} })', context);
  for (const mapping of mappings) { cache.addMapping(mapping); }
  cache.lookup(key);
  const notice = document.getElementById('carlos-apcache-lookup-failure');
  return { cache, requests, notice: notice ? notice.firstChild.textContent : null };
}

// The Rich Text Letter's age-and-gender mappings, as its stored form and editControl2.js register them.
const ageGenderMappings = (calls) => [
  {
    name: 'ageGender',
    values: ['_age', 'gender', 'age', 'sex', 'ageComplex'],
    storeInCacheHandler() { calls.push('ageGender'); },
  },
  { name: '_age', values: ['age', 'ageComplex'], storeInCacheHandler() { calls.push('_age'); } },
  { name: 'gender', values: ['sex'] },
  { name: 'sex', values: ['sex'], storeInCacheHandler() { calls.push('sex'); } },
];

test('a name derived in the browser is not reported as a field that could not be filled in', () => {
  const calls = [];
  const result = lookUp({
    key: 'ageGender',
    mappings: ageGenderMappings(calls),
    values: { _age: '', gender: '', age: '41', sex: 'M', ageComplex: '' },
    failures: ['_age', 'gender'],
  });

  assert.equal(result.notice, null, `no notice expected, got: ${result.notice}`);
  assert.ok(calls.includes('ageGender'), 'the requested mapping still runs');
});

test('a server key that failed is still reported beside derived names', () => {
  const result = lookUp({
    key: 'ageGender',
    mappings: ageGenderMappings([]),
    values: { _age: '', gender: '', age: '', sex: 'M', ageComplex: '' },
    failures: ['_age', 'gender', 'age'],
  });

  assert.equal(result.notice,
    'Some fields could not be filled in automatically and are blank: age. Check them before saving.');
});

test('a mapping that reads its own name from the server still reports that name', () => {
  // "sex" is both a mapping and a server key: its handler derives he/she and his/her from the
  // value the server returns, so a failure there is a real blank.
  const result = lookUp({
    key: 'ageGender',
    mappings: ageGenderMappings([]),
    values: { _age: '', gender: '', age: '41', sex: '', ageComplex: '' },
    failures: ['_age', 'gender', 'sex'],
  });

  assert.equal(result.notice,
    'Some fields could not be filled in automatically and are blank: sex. Check them before saving.');
});

test('a template lookup does not report its own placeholder names', () => {
  // parseTemplate() sends each placeholder's name (letterhead, _ClosingSalutation) so the lookup
  // echoes it and the placeholder's mapping builds the text.
  const result = lookUp({
    key: 'template',
    mappings: [
      { name: 'template', values: ['clinic_name', 'letterhead', 'current_user', '_ClosingSalutation'] },
      { name: 'letterhead', values: ['clinic_name'], storeInCacheHandler() {} },
      { name: '_ClosingSalutation', values: ['current_user'], storeInCacheHandler() {} },
    ],
    values: { clinic_name: 'CARLOS Demo Clinic', letterhead: '', current_user: 'Doc, Carlos', _ClosingSalutation: '' },
    failures: ['letterhead', '_ClosingSalutation'],
  });

  assert.equal(result.notice, null, `no notice expected, got: ${result.notice}`);
});
