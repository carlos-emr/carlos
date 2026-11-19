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
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const {formMenuUrl} = require('./form-print-pdf-playwright-checks');

function chart(anchors) {
  return {evaluate: (callback, args) => vm.runInNewContext(`(${callback.toString()})(args)`, {
    args, document: {querySelectorAll: () => anchors.map(({text, ...attributes}) => ({
      textContent: text, getAttribute: name => attributes[name] || null,
    }))},
  })};
}

test('form print reads the guarded menu URL without interpreting its click handler', async () => {
  const url = '/carlos/form/formlabreq07?demographic_no=2&formId=0';
  const page = chart([{text: 'Lab Req 2007', 'data-encounter-form-name': 'Lab Req 2007',
    'data-encounter-form-url': url, onclick: 'return openEncounterForm(this);'}]);
  assert.equal(await formMenuUrl(page, 'Lab Req 2007'), url);
  assert.equal(await formMenuUrl(page, 'Lab Req 2007', 'started'), '');
});

test('form print distinguishes started and blank legacy popup entries', async () => {
  const page = chart([
    {text: '*Lab Req 2007*', onclick: "popupPage(700,960,'started','/carlos/form/forwardshortcutname?formname=labreq07\\x26formId=latest');"},
    {text: 'Lab Req 2007', onclick: "popupPage(700,960,'new','/carlos/form/formlabreq07?demographic_no=2\\x26formId=0');"},
  ]);
  assert.equal(await formMenuUrl(page, 'Lab Req 2007', 'started'), '/carlos/form/forwardshortcutname?formname=labreq07&formId=latest');
  assert.equal(await formMenuUrl(page, 'Lab Req 2007'), '/carlos/form/formlabreq07?demographic_no=2&formId=0');
});
