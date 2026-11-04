/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Finding 177 / #4434: the Rich Text Letter editor used doctor_contact_* AP
 * keys that the shipped apconfig.xml does not define, leaving contact details
 * blank. Execute the editor's actual letterhead functions and mapping with
 * nonempty synthetic AP results. This checks the JavaScript/configuration
 * contract; it does not replace a packaged lookup/render check against SQL.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const acorn = require('acorn');

const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/eform-assets/editControl2.js'), 'utf8');
const config = fs.readFileSync(path.join(root, 'src/main/resources/oscar/eform/apconfig.xml'), 'utf8');
const configuredKeys = new Set([...config.matchAll(/<ap-name>\s*([^<]+?)\s*<\/ap-name>/g)].map(match => match[1]));
const nodes = [];
function walk(node) {
  if (!node || typeof node.type !== 'string') return;
  nodes.push(node);
  for (const value of Object.values(node)) {
    if (Array.isArray(value)) value.forEach(walk);
    else if (value && typeof value === 'object') walk(value);
  }
}
walk(acorn.parse(source, { ecmaVersion: 'latest' }));
function one(predicate, label) {
  const matches = nodes.filter(predicate);
  assert.equal(matches.length, 1, `Expected one real ${label}`);
  return source.slice(matches[0].start, matches[0].end);
}
const functions = ['genericLetterhead', 'fhtLetterhead'].map(name =>
  one(node => node.type === 'FunctionDeclaration' && node.id?.name === name, name)).join('\n');
const mapping = one(node => node.type === 'CallExpression' && node.callee.type === 'MemberExpression'
  && node.callee.object.name === 'cache' && node.callee.property.name === 'addMapping'
  && node.arguments[0]?.type === 'ObjectExpression' && node.arguments[0].properties.some(property =>
    (property.key.name || property.key.value) === 'name' && property.value.value === 'letterhead'), 'letterhead mapping');

const values = {
  clinic_name: 'FAKE Cedar Clinic',
  clinic_addressLineFull: '8 FAKE Clinic Road',
  clinic_phone: '416-555-0101',
  clinic_fax: '416-555-0102',
  doctor: 'FAKE Physician, Alex',
  doctor_address_label: '17 FAKE Physician Avenue',
  doctor_work_phone: '416-555-0111',
  doctor_fax: '416-555-0112',
  current_user: 'FAKE Staff, Robin',
};

function editor(overrides = {}) {
  const entries = new Map(Object.entries({ ...values, ...overrides }).filter(([, value]) => value !== null));
  let letterhead;
  const cache = {
    addMapping(value) { letterhead = value; },
    contains(key) { return entries.has(key); },
    get(key) {
      assert(entries.has(key), `The editor read an unavailable AP key: ${key}`);
      if (key !== 'letterhead') {
        assert(configuredKeys.has(key), `The editor read an undefined AP key: ${key}`);
        assert(letterhead.values.includes(key), `The mapping never requests ${key}`);
      }
      return entries.get(key);
    },
    put(key, value) { entries.set(key, value); },
  };
  vm.runInNewContext(`${functions}\n${mapping};`, { cache, ImgArray: [], console: { log() {} } });
  return {
    keys: Array.from(letterhead.values),
    render() {
      // Run the registered handler after its AP values have arrived, as the
      // cache does, rather than calling a hand-written replacement renderer.
      letterhead.storeInCacheHandler('doctor_work_phone', entries.get('doctor_work_phone'));
      return cache.get('letterhead');
    },
  };
}

test('letterhead requests defined AP keys, including the physician contact fields', () => {
  const { keys } = editor();
  assert.equal(new Set(keys).size, keys.length, 'The letterhead repeats an AP request');
  for (const key of keys) assert(configuredKeys.has(key), `No shipped AP definition for ${key}`);
  for (const key of ['doctor_address_label', 'doctor_work_phone', 'doctor_fax']) assert(keys.includes(key), `Missing ${key}`);
});

test('the generic letterhead renders nonempty physician address, phone and fax from the registered AP values', () => {
  const html = editor().render();
  for (const key of ['clinic_name', 'doctor_address_label', 'doctor_work_phone', 'doctor_fax']) {
    assert(html.includes(values[key]), `The rendered letterhead lost ${key}`);
  }
  assert(!html.includes('undefined'), 'The letterhead rendered an unavailable value');
});

for (const clinicName of ['FAKE Cedar Family Health Team', 'FAKE Cedar FHT']) {
  test(`${clinicName} renders its clinic contact details and physician name`, () => {
    const html = editor({ clinic_name: clinicName }).render();
    for (const key of ['clinic_addressLineFull', 'clinic_phone', 'clinic_fax']) {
      assert(html.includes(values[key]), `The FHT letterhead lost ${key}`);
    }
    assert(html.includes(clinicName));
    assert(html.includes('Alex FAKE Physician'), 'The FHT letterhead lost the physician name');
    assert(!html.includes('undefined'), 'The FHT letterhead rendered an unavailable value');
  });
}
