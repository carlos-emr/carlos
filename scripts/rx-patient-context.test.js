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
