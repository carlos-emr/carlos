/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// The patient's navigation script opens the record's usual popup windows, with the names and
// features popupPage, popupEChart and popupOscarRx have always used.
const test = require('node:test');
const assert = require('node:assert/strict');
const {features, popupFor} = require('../src/main/webapp/share/javascript/demographic/patient-nav');

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
