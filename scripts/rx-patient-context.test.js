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

test('isRxUrl never classifies an off-origin or non-http URL as an Rx route', () => {
    for (const url of [
        '//other.example/rx/WriteScript',
        'ht\ttps://other.example/rx/WriteScript',
        'htt\nps://other.example/rx/WriteScript',
        '/\r/other.example/rx/WriteScript',
        '  //other.example/rx/WriteScript',
        '/\\other.example/rx/WriteScript',
        '\\\\other.example/rx/WriteScript',
        'https://other.example/rx/WriteScript',
        'http://other.example/carlos/rx/viewScript?scriptId=1',
        'javascript:alert(1)//rx/x',
        'data:text/html,/rx/x',
        'mailto:rx/x',
    ]) {
        assert.equal(context.isRxUrl(url), false, url);
    }
});

test('isRxUrl accepts relative Rx routes and absolute http(s) Rx URLs on this origin only', () => {
    const previous = Object.getOwnPropertyDescriptor(globalThis, 'location');
    Object.defineProperty(globalThis, 'location', {
        value: { origin: 'https://emr.example', href: 'https://emr.example/carlos/rx/searchDrug' },
        configurable: true,
    });
    try {
        assert.equal(context.isRxUrl('/carlos/rx/WriteScript'), true);
        assert.equal(context.isRxUrl('rx/WriteScript'), true);
        assert.equal(context.isRxUrl('https://emr.example/carlos/rx/WriteScript'), true);
        assert.equal(context.isRxUrl('https://emr.example.evil.test/carlos/rx/WriteScript'), false);
        assert.equal(context.isRxUrl('http://emr.example/carlos/rx/WriteScript'), false);
        assert.equal(context.isRxUrl('//emr.example/carlos/rx/WriteScript'), false);
        assert.equal(context.isRxUrl('/carlos/encounter/x'), false);

        const rx = context.create('1001');
        assert.equal(rx.withPatient('https://emr.example/carlos/rx/WriteScript'),
            'https://emr.example/carlos/rx/WriteScript?demographicNo=1001');
        assert.equal(rx.withPatient('//other.example/rx/WriteScript'), '//other.example/rx/WriteScript');
        assert.equal(rx.withPatient('javascript:void(0)//rx/'), 'javascript:void(0)//rx/');
    } finally {
        if (previous) {
            Object.defineProperty(globalThis, 'location', previous);
        } else {
            delete globalThis.location;
        }
    }
});

test('withPatient never adds a patient to a URL with embedded control characters', () => {
    const rx = context.create('1001');
    for (const url of ['ht\ttps://other.example/rx/x', '/\n/other.example/rx/x']) {
        // Browsers normalize these URLs into cross-origin navigations.
        assert.equal(new URL(url, 'https://emr.example/carlos/').origin, 'https://other.example');
        assert.equal(rx.withPatient(url), url);
    }
});

test('install does not tag a protocol-relative link to another host', () => {
    const listeners = {};
    const attrs = { href: '//other.example/rx/WriteScript' };
    const link = {
        getAttribute: (name) => attrs[name],
        setAttribute: (name, value) => { attrs[name] = value; },
        closest() { return this; },
    };
    const win = {
        document: {
            addEventListener(type, fn) { listeners[type] = fn; },
            createElement: () => ({}),
        },
    };
    context.create('1001').install(win);
    listeners.click({ target: link });
    assert.equal(attrs.href, '//other.example/rx/WriteScript');
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

test('install tags Oscar.js popup2 popups, keeping the six-argument signature', () => {
    const seen = [];
    const win = {
        popup2(height, width, top, left, url, name) {
            seen.push([height, width, top, left, url, name, this === win]);
            return 'window';
        },
    };
    const rx = context.create('1001');
    rx.install(win);
    rx.install(win);

    assert.equal(win.popup2(600, 800, 70, 110, '/carlos/rx/searchDrug?rx2=true&searchString=x', 'n'), 'window');
    win.popup2(575, 650, 70, 110, '/carlos/rx/RxReason?demographicNo=2002&drugId=5', 'r');
    win.popup2(1, 1, 0, 0, '/carlos/encounter/x', 'e');
    win.popup2(1, 1, 0, 0, '//other.example/rx/searchDrug', 'o');

    assert.deepEqual(seen, [
        [600, 800, 70, 110, '/carlos/rx/searchDrug?rx2=true&searchString=x&demographicNo=1001', 'n', true],
        [575, 650, 70, 110, '/carlos/rx/RxReason?demographicNo=2002&drugId=5', 'r', true],
        [1, 1, 0, 0, '/carlos/encounter/x', 'e', true],
        [1, 1, 0, 0, '//other.example/rx/searchDrug', 'o', true],
    ]);
});

test('wrappers installed for one patient follow the context when a later page installs another', () => {
    const seen = [];
    const listeners = {};
    const appended = [];
    const win = {
        CarlosAjax: {
            request(url) { seen.push(['request', url]); },
            updater(container, url) { seen.push(['updater', url]); },
        },
        jQuery: { ajaxPrefilter(fn) { this.filter = fn; } },
        popupWindow(h, w, url) { seen.push(['popupWindow', url]); },
        popup2(h, w, t, l, url) { seen.push(['popup2', url]); },
        document: {
            addEventListener(type, fn) { listeners[type] = fn; },
            createElement: () => ({}),
        },
    };
    context.create('1001').install(win);
    // Same window, next Rx page for another patient; the wrapped globals were not recreated.
    context.create('2002').install(win);

    win.CarlosAjax.request('/carlos/rx/WriteScript', {});
    win.CarlosAjax.updater('rxText', '/carlos/rx/rePrescribe2', {});
    win.popupWindow(1, 1, '/carlos/rx/ViewShowPreviousPrints?scriptNo=3', 'x');
    win.popup2(1, 1, 0, 0, '/carlos/rx/searchDrug?rx2=true', 'n');
    const options = { url: '/carlos/rx/drugInfo' };
    win.jQuery.filter(options);
    listeners.submit({ target: {
        getAttribute: () => '/carlos/rx/deleteRx',
        querySelector: () => null,
        appendChild: (el) => appended.push(el),
    } });

    assert.deepEqual(seen, [
        ['request', '/carlos/rx/WriteScript?demographicNo=2002'],
        ['updater', '/carlos/rx/rePrescribe2?demographicNo=2002'],
        ['popupWindow', '/carlos/rx/ViewShowPreviousPrints?scriptNo=3&demographicNo=2002'],
        ['popup2', '/carlos/rx/searchDrug?rx2=true&demographicNo=2002'],
    ]);
    assert.equal(options.url, '/carlos/rx/drugInfo?demographicNo=2002');
    assert.equal(appended[0].value, '2002');

    // A later page with no patient must not keep tagging with the previous one.
    context.create(null).install(win);
    seen.length = 0;
    win.CarlosAjax.request('/carlos/rx/WriteScript', {});
    assert.deepEqual(seen, [['request', '/carlos/rx/WriteScript']]);
});

test('a request whose body already names its patient is not tagged again on the URL', () => {
    // SearchDrug3's Save posts the drug form, which carries demographicNo, through CarlosAjax.
    // Tagging the URL too sent demographicNo twice; Struts bound both values into the action's
    // int demographicNo, failed the conversion and answered 404 (the save was refused).
    const seen = [];
    const win = {
        CarlosAjax: {
            request(url, options) { seen.push(['request', url, options]); },
            updater(container, url, options) { seen.push(['updater', url, options]); },
        },
        jQuery: { ajaxPrefilter(fn) { this.filter = fn; } },
    };
    context.create('1001').install(win);

    win.CarlosAjax.request('/carlos/rx/WriteScript?parameterValue=updateSaveAllDrugs',
        { method: 'post', postBody: 'drugName_1=X&demographicNo=1001&repeats_1=0' });
    win.CarlosAjax.request('/carlos/rx/WriteScript', { parameters: 'demographic_no=1001&x=1' });
    win.CarlosAjax.request('/carlos/rx/WriteScript', { parameters: { demographicNo: '1001' } });
    win.CarlosAjax.request('/carlos/rx/WriteScript',
        { parameters: new URLSearchParams('demographicNo=1001') });
    win.CarlosAjax.updater('rxText', '/carlos/rx/rePrescribe2', { parameters: 'demographicNo=1001' });
    // A body that does not name the patient still gets the URL tag; "xdemographicNo" is not one.
    win.CarlosAjax.request('/carlos/rx/WriteScript', { postBody: 'xdemographicNo=5&a=1' });
    win.CarlosAjax.request('/carlos/rx/WriteScript');

    assert.deepEqual(seen.map((entry) => entry[1]), [
        '/carlos/rx/WriteScript?parameterValue=updateSaveAllDrugs',
        '/carlos/rx/WriteScript',
        '/carlos/rx/WriteScript',
        '/carlos/rx/WriteScript',
        '/carlos/rx/rePrescribe2',
        '/carlos/rx/WriteScript?demographicNo=1001',
        '/carlos/rx/WriteScript?demographicNo=1001',
    ]);

    const withBody = { url: '/carlos/rx/drugInfo', data: 'a=1&demographicNo=1001' };
    win.jQuery.filter(withBody);
    assert.equal(withBody.url, '/carlos/rx/drugInfo');
    const withoutBody = { url: '/carlos/rx/drugInfo', data: 'a=1' };
    win.jQuery.filter(withoutBody);
    assert.equal(withoutBody.url, '/carlos/rx/drugInfo?demographicNo=1001');
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
