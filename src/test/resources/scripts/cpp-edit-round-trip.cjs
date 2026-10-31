/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync(process.argv[2], 'utf8');
const definitions = source.indexOf('var exFields =');
const prepare = source.indexOf('function prepareExtraFields(');
const update = source.indexOf('function updateCPPNote(');
const next = source.indexOf('function loadDiv(', update);
assert(definitions >= 0 && prepare > definitions && update > prepare && next > update);

const fields = Object.create(null);
const element = (id) => fields[id] ||= {value: '', style: {}, hide() {}};
let posted;
const context = {
  console: {log() {}, error(error) {throw error;}},
  cppNames: [],
  $: element,
  caseNote: 'synthetic-case-note',
  CONTROL_CHAR_PATTERN_2: /[\u0000-\u0008]/g,
  sanitizeElementByPattern() {},
  CarlosAjax: {request(url, options) {posted = options.postBody;}},
};
const form = element('frmIssueNotes');
form.action = '/synthetic-cpp-save';
form.elements = {noteEditTxt: {value: 'synthetic note'}};
form.querySelectorAll = () => [];
form.serialize = () => new URLSearchParams(context.exFields.map(name => [name, element(name).value])).toString();
context.document = {forms: {frmIssueNotes: form}};

vm.createContext(context);
vm.runInContext(source.slice(definitions, next), context, {timeout: 1000});
const encodedArgument = JSON.parse(fs.readFileSync(0, 'utf8'));
vm.runInContext(`prepareExtraFields('synthetic', '${encodedArgument}');updateCPPNote();`, context, {timeout: 1000});
assert.equal(typeof posted, 'string', 'the real updateCPPNote must submit the form');
process.stdout.write(JSON.stringify(Object.fromEntries(new URLSearchParams(posted))));
