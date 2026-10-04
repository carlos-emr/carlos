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
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/demographic');
const source = fs.readFileSync(path.join(root, 'add.jsp'), 'utf8');
const dateFields = ['roster_date', 'date_joined', 'end_date', 'hc_renew_date', 'eff_date'];
function functionSource(name) {
  const functions = [...source.matchAll(/^            function (\w+)\([^)]*\) \{[\s\S]*?^            \}/gm)];
  const match = functions.find(candidate => candidate[1] === name);
  assert.ok(match, `Missing production function ${name}`);
  return match[0];
}
function fieldMarkup(name) {
  const page = name === 'eff_date' || name === 'hc_renew_date' ? 'add-form-personal.jsp' : 'add-form-clinical.jsp';
  const jsp = fs.readFileSync(path.join(root, page), 'utf8');
  const start = jsp.indexOf('name="' + name + '"');
  assert.ok(start >= 0, `Missing visible input ${name}`);
  return jsp.slice(start, jsp.indexOf('</div>', start));
}
function harness(names = dateFields, functions = ['parseDateField']) {
  // Read the real backing controls rather than assuming their names match the parser.
  const fields = Object.fromEntries(names.flatMap(name => {
    const block = fieldMarkup(name);
    const hiddenNames = [...block.matchAll(/<input type="hidden" name="([^"]+)"/g)].map(match => match[1]);
    return [name, ...hiddenNames].map(key => [key, { value: '' }]);
  }));
  const context = {
    document: {
      getElementById: id => fields[id] || null,
      querySelector: selector => fields[/^input\[name="([^"]+)"\]$/.exec(selector)?.[1]] || null,
      adddemographic: fields,
    },
    window: {},
  };
  vm.createContext(context);
  for (const name of functions) vm.runInContext(functionSource(name), context);
  return { fields, context };
}
function parts(fields, name) {
  return ['_year', '_month', '_date'].map(suffix => fields[name + suffix]?.value);
}

test('date selectors contain no JSP EL expressions that consume the JavaScript field name', () => {
  assert.doesNotMatch(functionSource('parseDateField'), /\$\{/);
});

for (const name of dateFields) {
  test(`${name} copies typed date parts without losing leading zeroes`, () => {
    const { fields, context } = harness();
    fields[name].value = '2024-02-09';
    context.parseDateField(name);
    assert.deepEqual(parts(fields, name), ['2024', '02', '09']);
    for (const other of dateFields.filter(field => field !== name)) assert.deepEqual(parts(fields, other), ['', '', '']);
  });
  test(`${name} clears stale hidden parts when the visible date is cleared`, () => {
    const { fields, context } = harness();
    fields[name].value = '2024-02-29';
    context.parseDateField(name);
    fields[name].value = '';
    context.parseDateField(name);
    assert.deepEqual(parts(fields, name), ['', '', '']);
  });
  test(`${name} starts blank instead of silently assuming today's date`, () => {
    const input = fieldMarkup(name).match(/value="([^"]*)"/);
    assert.ok(input, `Missing ${name} input`);
    assert.equal(input[1], '');
  });
}

test('an optional date field absent from the rendered form is harmless', () => {
  const { context } = harness([]);
  assert.doesNotThrow(() => context.parseDateField('eff_date'));
});

test('submission synchronizes current date values before validating or checking roster requirements', () => {
  const { fields, context } = harness(dateFields, ['parseDateField', 'syncDateFields']);
  for (const name of dateFields) fields[name].value = '2024-03-05';
  // A stale previous selection must not survive a cleared visible field.
  fields.end_date.value = '';
  for (const suffix of ['_year', '_month', '_date']) fields['end_date' + suffix].value = 'stale';
  fields.roster_status = { value: 'RO' };
  fields.roster_enrolled_to = { value: '999998' };
  Object.assign(context, {
    parseHINforVC() {}, formatPhoneNum() {}, syncInputDobParts() {},
    checkFormTypeIn() {
      for (const name of dateFields) assert.deepEqual(parts(fields, name), name === 'end_date' ? ['', '', ''] : ['2024', '03', '05']);
      return true;
    },
    ignoreDuplicates: () => true, isPostalCode: () => true,
    alert: message => assert.fail(`Unexpected validation alert: ${message}`),
    i18n: { msgEnrolledToRequired: 'Provider required', msgRosterDateRequired: 'Date required' },
  });
  // The only JSP directives in aSubmit conditionally enable postal validation;
  // keep that validation branch, as on the tested Ontario installation.
  vm.runInContext(functionSource('aSubmit').replace(/<%[\s\S]*?%>/g, ''), context);
  assert.equal(context.aSubmit(), true);
});
