/*
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

// The patient's navigation script opens the record's usual popup windows, with the names and
// features popupPage, popupEChart and popupOscarRx have always used.
const test = require('node:test');
const assert = require('node:assert/strict');
const {features, popupFor, checkEligibility} = require('../src/main/webapp/share/javascript/demographic/patient-nav');

test('popup features match the record helpers', () => {
  assert.equal(features('page', 700, 960),
    'height=700,width=960,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=20,left=20');
  assert.equal(features('echart', 710, 1024),
    'height=710,width=1024,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=20,left=20');
  assert.equal(features('rx', 700, 1027),
    'height=700,width=1027,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0');
});

test('each kind of link reuses the window it always did', () => {
  assert.equal(popupFor('page', 700, 960, '/a', false).name, 'demodetail');
  assert.equal(popupFor('echart', 710, 1024, '/b', false).name, 'encounter');
  assert.equal(popupFor('rx', 700, 1027, '/c', false).name, 'rx');
  assert.deepEqual(popupFor('window', null, null, '/d', false),
    {url: '/d', name: '_blank', features: 'resizable=yes,status=yes,scrollbars=yes'});
});

test('the open-encounter-in-tab preference opens a tab instead', () => {
  assert.deepEqual(popupFor('page', 700, 960, '/a', true), {url: '/a', tab: true});
  assert.deepEqual(popupFor('rx', 700, 1027, '/c', true), {url: '/c', tab: true});
});

test('an unknown kind is left to the browser', () => {
  assert.equal(popupFor('__proto__', 1, 1, '/x', false), null);
  assert.equal(popupFor('other', 1, 1, '/x', false), null);
});

test('the eligibility check posts checkElig for the patient and reports the answer', () => {
  const calls = [];
  const ajax = {request: (url, options) => { calls.push({url, options}); options.onSuccess({responseText: '<b>ok</b>'}); }};
  const seen = [];
  checkEligibility(ajax, '/ctx/billing/CA/BC/ManageTeleplan', '123',
    {answered: (html) => seen.push(['answered', html]), failed: () => seen.push(['failed'])});
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/ctx/billing/CA/BC/ManageTeleplan');
  assert.equal(calls[0].options.method, 'POST');
  assert.deepEqual(calls[0].options.parameters, {demographic: '123', method: 'checkElig'});
  assert.deepEqual(seen, [['answered', '<b>ok</b>']]);
});

test('a refused or failed eligibility check is reported as failed', () => {
  const ajax = {request: (url, options) => options.onFailure({status: 405, responseText: '<html>error page</html>'})};
  const seen = [];
  checkEligibility(ajax, '/x', '1', {answered: () => seen.push('answered'), failed: () => seen.push('failed')});
  assert.deepEqual(seen, ['failed']);
});
