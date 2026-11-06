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
const source = fs.readFileSync(path.join(__dirname, 'lab-acknowledge-playwright-checks.js'), 'utf8');
const functionSource = source.slice(source.indexOf('async function checkCumulativeValues(context) {'), source.indexOf('\n(async () => {'));
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/lab/CumulativeLabValues.jsp'), 'utf8');
const jspParameter = jsp.match(/String demographic_no = request\.getParameter\("([^"]+)"\)/)[1];

async function check({header = "O'Brien & Example, Jane F 45", names = [["O'Brien & Example", 'Jane']], status = 200} = {}) {
  let closed = false;
  let request;
  const page = {
    waitForLoadState: async () => {},
    locator: selector => selector === 'body' ? {innerText: async () => 'Generic cumulative labs shell'}
      : {first: () => ({innerText: async () => header})},
    close: async () => {closed = true;},
  };
  const context = vm.createContext({
    demographicNo: '42', config: {baseUrl: 'https://127.0.0.1/carlos'}, recorder: {},
    assert: (condition, message) => assert.ok(condition, message), wirePage() {},
    sqlRows: sql => {assert.match(sql, /demographic_no=42$/); return names;},
    gotoApp: async (_page, base, route) => {request = new URL(base + route); return {status: () => status};},
    assertNotErrorPage: async () => {},
  });
  vm.runInContext(functionSource, context);
  try {
    const result = await context.checkCumulativeValues({newPage: async () => page});
    return {result, request, closed};
  } catch (error) {
    error.pageClosed = closed;
    throw error;
  }
}

test('actual cumulative navigation uses the parameter read by its installed JSP and shows its selected patient', async () => {
  const result = await check();
  assert.equal(result.request.searchParams.get(jspParameter), '42');
  assert.equal(result.request.searchParams.has('demographicNo'), false);
  assert.equal(result.result, 'Generic cumulative labs shell');
  assert.equal(result.closed, true);
});

for (const header of ['', 'Cumulative Lab Values', 'Foreign, Patient F 45', "O'Brien & Example, Janet F 45"]) {
  test(`a generic or different-patient header cannot pass: ${JSON.stringify(header)}`, async () => {
    await assert.rejects(check({header}), error => error.pageClosed && /selected patient/.test(error.message));
  });
}

test('missing patient fixture and HTTP500 both fail while closing the page', async () => {
  await assert.rejects(check({names: []}), error => error.pageClosed && /complete name/.test(error.message));
  await assert.rejects(check({status: 500}), error => error.pageClosed && /HTTP 500/.test(error.message));
});
