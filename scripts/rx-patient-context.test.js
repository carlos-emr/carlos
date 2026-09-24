/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Per-patient Rx state (#3875): every Rx request from an Rx page must name the page's patient.

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const context = require(path.join(__dirname, '../src/main/webapp/share/javascript/rx-patient-context.js'));

test('withPatient appends demographicNo to Rx routes', () => {
    const rx = context.create('1001');
    assert.equal(rx.withPatient('/carlos/rx/WriteScript'), '/carlos/rx/WriteScript?demographicNo=1001');
    assert.equal(rx.withPatient('/carlos/rx/viewScript?scriptId=5'), '/carlos/rx/viewScript?scriptId=5&demographicNo=1001');
    assert.equal(rx.withPatient('/carlos/rx/x?a=1#top'), '/carlos/rx/x?a=1&demographicNo=1001#top');
});

test('withPatient leaves a URL that already names a patient alone', () => {
    const rx = context.create('1001');
    assert.equal(rx.withPatient('/carlos/rx/showAllergy?demographicNo=2002'), '/carlos/rx/showAllergy?demographicNo=2002');
    assert.equal(rx.withPatient('/carlos/rx/ViewChartDrugProfile?demographic_no=2002'),
        '/carlos/rx/ViewChartDrugProfile?demographic_no=2002');
});

test('withPatient does not touch non-Rx or cross-origin URLs', () => {
    const rx = context.create('1001');
    assert.equal(rx.withPatient('/carlos/encounter/x'), '/carlos/encounter/x');
    assert.equal(rx.withPatient('https://evil.example/rx/x'), 'https://evil.example/rx/x');
});

test('without a patient nothing is rewritten', () => {
    const rx = context.create(null);
    assert.equal(rx.withPatient('/carlos/rx/WriteScript'), '/carlos/rx/WriteScript');
});

test('install tags CarlosAjax, jQuery and popupWindow calls once', () => {
    const seen = [];
    const win = {
        CarlosAjax: {
            request(url) { seen.push(['request', url]); },
            updater(container, url) { seen.push(['updater', container, url]); },
        },
        jQuery: { ajaxPrefilter(fn) { this.filter = fn; } },
        popupWindow(h, w, url) { seen.push(['popup', url]); },
    };
    const rx = context.create('1001');
    rx.install(win);
    rx.install(win);

    win.CarlosAjax.request('/carlos/rx/WriteScript', {});
    win.CarlosAjax.updater('rxText', '/carlos/rx/rePrescribe2', {});
    win.popupWindow(1, 1, '/carlos/rx/ViewShowPreviousPrints?scriptNo=3', 'x');
    const options = { url: '/carlos/rx/drugInfo' };
    win.jQuery.filter(options);

    assert.deepEqual(seen, [
        ['request', '/carlos/rx/WriteScript?demographicNo=1001'],
        ['updater', 'rxText', '/carlos/rx/rePrescribe2?demographicNo=1001'],
        ['popup', '/carlos/rx/ViewShowPreviousPrints?scriptNo=3&demographicNo=1001'],
    ]);
    assert.equal(options.url, '/carlos/rx/drugInfo?demographicNo=1001');
});

test('install tags followed Rx links and form submissions with the page patient', () => {
    const listeners = {};
    const makeLink = (href) => {
        const attrs = { href };
        return {
            getAttribute: (name) => attrs[name],
            setAttribute: (name, value) => { attrs[name] = value; },
            closest() { return this; },
        };
    };
    const appended = [];
    const win = {
        document: {
            addEventListener(type, fn) { listeners[type] = fn; },
            createElement: () => ({}),
        },
    };
    context.create('1001').install(win);

    const staticScript = makeLink('/carlos/rx/ViewStaticScript2?regionalIdentifier=1');
    listeners.click({ target: staticScript });
    assert.equal(staticScript.getAttribute('href'),
        '/carlos/rx/ViewStaticScript2?regionalIdentifier=1&demographicNo=1001');

    const named = makeLink('/carlos/rx/ViewStaticScript2?demographicNo=2002');
    listeners.click({ target: named });
    assert.equal(named.getAttribute('href'), '/carlos/rx/ViewStaticScript2?demographicNo=2002');

    const other = makeLink('javascript:void(0)');
    listeners.click({ target: other });
    assert.equal(other.getAttribute('href'), 'javascript:void(0)');

    const form = {
        getAttribute: () => '/carlos/rx/deleteRx',
        querySelector: () => null,
        appendChild: (el) => appended.push(el),
    };
    listeners.submit({ target: form });
    assert.equal(appended.length, 1);
    assert.equal(appended[0].name, 'demographicNo');
    assert.equal(appended[0].value, '1001');
});
