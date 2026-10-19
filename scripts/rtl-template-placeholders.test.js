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
 * Rich Text Letter template placeholders (##letterhead##, ##today##, ...).
 *
 * parseTemplate() asks efmformapconfig_lookup only for the placeholders that are not cached yet.
 * The letter's cache is created with APCache's default flushCache=true, so every lookup EMPTIES the
 * cache before it runs: the keys it skipped as "already cached" were discarded by that same lookup,
 * and populateTemplate() then prompted the clinician for each of them. Choosing a second template
 * (Consultation Letter after Clinic Letter) prompted for the letterhead, the date, the patient label
 * and the closing salutation.
 *
 * Runs the real APCache.js cache and the real parseTemplate() and inspects the keys requested.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const WEBAPP = path.join(__dirname, '../src/main/webapp');
const AP_CACHE_JS = path.join(WEBAPP, 'library/eforms/APCache.js');
const EDIT_CONTROL_2_JS = path.join(WEBAPP, 'WEB-INF/eform-assets/editControl2.js');

function parseTemplateSource() {
  const source = fs.readFileSync(EDIT_CONTROL_2_JS, 'utf8');
  const start = source.indexOf('function parseTemplate(){');
  const end = source.indexOf('function populateTemplate(){');
  assert.ok(start >= 0 && end > start, 'parseTemplate() was not found in editControl2.js');
  return source.slice(start, end);
}

/**
 * A letter page holding the given hidden inputs (id -> value) and template body, with the cache
 * holding the given values (as a previous lookup would have left it).
 */
function loadLetter({ template, cached = {}, hiddenInputs = {}, mappings = [] }) {
  const requests = [];
  const populated = [];
  const elements = { edit: { contentDocument: {} } };
  for (const [id, value] of Object.entries(hiddenInputs)) {
    elements[id] = { id, tagName: 'INPUT', type: 'hidden', value };
  }
  const context = {
    console,
    demographicNo: '17',
    window: { location: { search: '?fid=1&demographic_no=17' } },
    document: { getElementById: (id) => elements[id] || null },
    jQuery: { ajax: (settings) => requests.push(settings) },
    alert(message) { throw new Error(`unexpected alert: ${message}`); },
    cfg_editorname: 'edit',
    measureTemplateRevision: 0,
    measureTemplateLookupsPending: 0,
    editControlContents: () => template,
    populateTemplate: () => populated.push(template),
    finishTemplateLookup() {},
    showLetterStatus() {},
  };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(AP_CACHE_JS, 'utf8'), context);
  vm.runInContext('var cache = createCache({});', context);
  vm.runInContext('cache.addMapping({name: "template", cacheResponseHandler: populateTemplate});', context);
  for (const mapping of mappings) {
    context.cache.addMapping(mapping);
  }
  for (const [key, value] of Object.entries(cached)) {
    context.cache.put(key, value);
  }
  vm.runInContext(parseTemplateSource(), context);
  return {
    context,
    populated,
    requests,
    requestedKeys: () => requests.map((settings) => new URLSearchParams(settings.data).getAll('key')),
  };
}

test('a second template re-requests the placeholders an earlier lookup cached', () => {
  const letter = loadLetter({
    template: '<p>##letterhead##</p><p>##today##</p><p>Dear ##first_last_name##,</p>',
    cached: { letterhead: 'CARLOS Demo Clinic', today: '2026-10-16' },
  });

  letter.context.parseTemplate();

  assert.equal(letter.requests.length, 1);
  assert.equal(new URLSearchParams(letter.requests[0].data).get('oscarAPCacheLookupType'), 'template');
  const [keys] = letter.requestedKeys();
  for (const key of ['letterhead', 'today', 'first_last_name']) {
    assert.ok(keys.includes(key), `${key} must be requested, got ${keys.join(', ')}`);
  }
});

test('a placeholder a hidden input supplies is filled from the page, not requested', () => {
  // The letter's measurement inputs (oscarDB=m$WT#value) are filled by the server when the page is
  // rendered; populateTemplate() reads them from the page. Asking efmformapconfig_lookup for them
  // only reports them as unconfigured keys in the "could not be filled in" banner.
  const letter = loadLetter({
    template: '<p>Weight ##WT## kg on ##today##</p>',
    hiddenInputs: { WT: '70' },
  });

  letter.context.parseTemplate();

  const [keys] = letter.requestedKeys();
  assert.ok(keys.includes('today'), keys.join(', '));
  assert.ok(!keys.includes('WT'), `WT must not be requested, got ${keys.join(', ')}`);
});

test('a template whose placeholders are all cached is populated without a lookup', () => {
  const letter = loadLetter({ template: '<p>##today##</p>', cached: { today: '2026-10-16' } });

  letter.context.parseTemplate();

  assert.equal(letter.requests.length, 0);
  assert.equal(letter.populated.length, 1);
});

test('a mapped placeholder requests the keys its mapping is built from', () => {
  const letter = loadLetter({
    template: '<p>a ##ageGender##</p>',
    mappings: [{ name: 'ageGender', values: ['_age', 'gender', 'age', 'sex', 'ageComplex'] }],
  });

  letter.context.parseTemplate();

  const [keys] = letter.requestedKeys();
  for (const key of ['_age', 'gender', 'age', 'sex', 'ageComplex', 'ageGender']) {
    assert.ok(keys.includes(key), `${key} must be requested, got ${keys.join(', ')}`);
  }
});
