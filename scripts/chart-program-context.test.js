/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const chartSource = chartActionUrlSource(source, { includeInitialization: true });

function chart(token) {
    const sandbox = {window: {carlosChartContext: token}, encodeURIComponent};
    vm.createContext(sandbox);
    vm.runInContext(chartSource, sandbox);
    sandbox.ctx = '/carlos';
    return sandbox;
}

test('chart URLs keep the window program across another chart opening and fragment globals changing', () => {
    const first = chart('patient101.program11.signature');
    const second = chart('patient101.program22.signature');
    // The first page's captured token is independent of later fragments and the second page.
    first.window.carlosChartContext = second.window.carlosChartContext;
    first.case_program_id = '22';
    assert.equal(first.chartActionUrl('/CaseManagementEntry'),
        '/carlos/CaseManagementEntry?chartContext=patient101.program11.signature');
    assert.equal(second.chartActionUrl('/CaseManagementEntry'),
        '/carlos/CaseManagementEntry?chartContext=patient101.program22.signature');
});

test('chart context precedes partial query values without corrupting the completed request', () => {
    const page = chart('signed.context');
    const url = new URL(page.chartActionUrl('/CaseManagementView?method=listNotes&providerNo=')
        + '4249&demographicNo=101&issue_code=MedHistory', 'https://clinic.test');
    assert.equal(url.searchParams.get('chartContext'), 'signed.context');
    assert.equal(url.searchParams.get('method'), 'listNotes');
    assert.equal(url.searchParams.get('providerNo'), '4249');
    assert.equal(url.searchParams.get('demographicNo'), '101');
    assert.equal(url.searchParams.get('issue_code'), 'MedHistory');
});

test('legacy standalone chart requests retain their URL when no window context was issued', () => {
    const page = chart(null);
    assert.equal(page.chartActionUrl('/CaseManagementView?method=view'),
        '/carlos/CaseManagementView?method=view');
    assert.equal(page.chartActionUrl('/CaseManagementEntry'), '/carlos/CaseManagementEntry');
});

test('standalone chart notes initialize the captured program before loading editor behavior', () => {
    const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp'), 'utf8');
    const bootstrap = jsp.match(/if \(typeof window\.carlosChartContext === 'undefined'\) \{[\s\S]*?\n    \}/);
    assert.ok(bootstrap, 'standalone notes supply an initial context');
    assert.ok(bootstrap.index < jsp.indexOf('/js/newCaseManagementView.js.jsp'),
        'context exists before the editor captures it');
    const code = bootstrap[0].replace(/<%=[\s\S]*?%>/, 'patient101.program11.signature');
    const initial = {window: {}};
    vm.runInNewContext(code, initial);
    assert.equal(initial.window.carlosChartContext, 'patient101.program11.signature');
    const fragment = {window: {carlosChartContext: 'patient101.program22.signature'}};
    vm.runInNewContext(code, fragment);
    assert.equal(fragment.window.carlosChartContext, 'patient101.program22.signature');
});

function legacyChart(token) {
    const jsp = fs.readFileSync(path.join(__dirname,
        '../src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementView.jsp'), 'utf8');
    const start = jsp.indexOf('            function chartPopupUrl(');
    const end = jsp.indexOf('            function popupNotePage(', start);
    assert.ok(start >= 0 && end > start, 'legacy chart popup context helper exists');
    const code = jsp.slice(start, end).replace(/var token = '<%=[\s\S]*?%>';/,
        () => `var token = ${JSON.stringify(token)};`);
    assert.doesNotMatch(code, /<%|%>/, 'all server-rendered popup values were supplied');
    const sandbox = {URL, document: {baseURI: 'https://clinic.test/carlos/CaseManagementView?demographicNo=101'},
        window: {location: {origin: 'https://clinic.test'}}};
    vm.createContext(sandbox);
    vm.runInContext(code, sandbox);
    return sandbox;
}

for (const target of ['CaseManagementEntry?method=edit&demographicNo=101#note',
    '/carlos/CaseManagementEntry?method=notehistory&demographicNo=101',
    'https://clinic.test/carlos/CaseManagementEntry?method=edit&chartContext=other-window&demographicNo=101']) {
    test(`legacy popup retains its owner context and patient while opening ${target}`, () => {
        const token = 'patient101.program11.signature +&?#';
        const page = legacyChart(token);
        const actual = new URL(page.chartPopupUrl(target));
        const expected = new URL(target, page.document.baseURI);
        assert.equal(actual.origin, expected.origin);
        assert.equal(actual.pathname, expected.pathname);
        assert.equal(actual.hash, expected.hash);
        assert.equal(actual.searchParams.get('demographicNo'), '101');
        assert.equal(actual.searchParams.get('method'), expected.searchParams.get('method'));
        assert.deepEqual(actual.searchParams.getAll('chartContext'), [token],
            'the originating chart replaces another window token without duplicating it');
    });
}

for (const target of ['https://other-clinic.test/carlos/CaseManagementEntry?demographicNo=101',
    '//other-clinic.test/carlos/CaseManagementEntry', 'mailto:clinic@example.test',
    'https://clinic.test:8443/carlos/CaseManagementEntry']) {
    test(`legacy popup never discloses its signed chart context to ${target}`, () => {
        assert.equal(legacyChart('patient101.program11.signature').chartPopupUrl(target), target);
    });
}

test('a legacy popup without a signed context preserves the caller URL byte for byte', () => {
    const target = 'CaseManagementEntry?demographicNo=101&method=edit#note';
    assert.equal(legacyChart('').chartPopupUrl(target), target);
});

for (const [description, brokenSource, expectedMarker] of [
    ['missing URL helper', '    var caisiEnabled', 'function chartActionUrl('],
    ['missing helper boundary', '    function chartActionUrl() {}', 'var caisiEnabled'],
    ['boundary before the URL helper', '    var caisiEnabled\n    function chartActionUrl() {}', 'var caisiEnabled'],
]) {
    test(`chart helper extraction reports its source marker when ${description}`, () => {
        assert.throws(() => chartActionUrlSource(brokenSource), error =>
            error instanceof assert.AssertionError && error.message.includes(expectedMarker));
    });
}

test('chart initialization extraction reports a missing initialization marker', () => {
    assert.throws(() => chartActionUrlSource(source.replace('    var ctx;', ''), { includeInitialization: true }),
        /var ctx;/);
});

test('the native Browse Notes button carries its containing chart context after another program opens', () => {
    const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp'), 'utf8');
    const target = jsp.indexOf('ViewNoteBrowser?');
    const button = jsp.slice(jsp.lastIndexOf('<button', target), jsp.indexOf('>', target) + 1);
    const handler = /onclick="([^"]+)"/.exec(button)?.[1];
    assert.ok(handler, 'the native Browse Notes button exists');
    for (const token of ['patient101.program7.signature +&?#', 'patient101.program8.signature']) {
        const page = chart(token);
        page.window.carlosChartContext = 'another-program-opened-later';
        let opened;
        page.popupPage = (...args) => { opened = args[3]; };
        vm.runInContext(handler.replace(/\$\{[^}]+\}/g, '101'), page);
        const url = new URL(opened, 'https://clinic.test');
        assert.equal(url.pathname, '/carlos/casemgmt/ViewNoteBrowser');
        assert.equal(url.searchParams.get('demographic_no'), '101');
        assert.equal(url.searchParams.get('FirstTime'), '1');
        assert.deepEqual(url.searchParams.getAll('chartContext'), [token]);
    }
});

function noteBrowser(token) {
    const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/noteBrowser.jsp'), 'utf8');
    const block = (from, to) => {
        const start = jsp.indexOf(from);
        const end = jsp.indexOf(to, start);
        assert.ok(start >= 0 && end > start, `note browser source markers exist: ${from} -> ${to}`);
        return jsp.slice(start, end);
    };
    let code = block('        var noteBrowserPatient =', '        function popup(')
        + block('        function reloadNoteBrowser(', '        function ReLoadDoc(')
        + block('        function showEncounter(', '        function getSelected(')
        + block('        function PrintEncounter(', '        function AddTickler(');
    assert.match(code, /SafeEncode\.forJavaScript\(demographicID\)/);
    assert.match(code, /SafeEncode\.forJavaScript\(io\.github\.carlos_emr\.carlos\.casemgmt\.common\.ChartProgramContext\.token\(request\)\)/);
    code = code.replace(/var noteBrowserPatient = '[^\n]+';/,
        () => `var noteBrowserPatient = ${JSON.stringify('101')};`)
        .replace(/var noteBrowserChartContext = '[^\n]+';/,
            () => `var noteBrowserChartContext = ${JSON.stringify(token)};`)
        .replaceAll('<%= SafeEncode.forJavaScript(request.getContextPath()) %>', '/carlos')
        .replaceAll('<%= request.getContextPath() %>', '/carlos');
    assert.doesNotMatch(code, /<%|\$\{/, 'all server values are supplied before actual browser functions execute');
    const fields = {
        encounterlist: [{value: '501'}, {value: '502'}], doclist: {selectedIndex: 0},
        docdisp: {replaceChildren(iframe) { this.iframe = iframe; }}, docinfo: {}, docextrainfo: {},
        printnotesbutton: {style: {}},
    };
    const form = Object.fromEntries(Object.entries({demographic_no: '101', view: 'type + & /',
        viewstatus: 'active', sortorder: 'Observation', 'CSRF-TOKEN': 'must-not-enter-a-GET'})
        .map(([name, value]) => [name, {value}]));
    const page = {
        URLSearchParams, encodeURIComponent, window: {location: {href: ''}}, getWidth: () => 1024, getHeight: () => 768,
        document: {DisplayDoc: form, getElementById: id => fields[id],
            createElement: () => ({addEventListener() {}})},
        getSelected: options => options, getDoc() {},
    };
    page.popup = (...args) => { page.printed = args[2]; };
    vm.createContext(page);
    vm.runInContext(code, page);
    return {page, fields};
}

for (const token of ['', 'patient101.program7.signature +&?#', 'patient101.program8.signature']) {
    test(`native note browser preview print and filter reload retain the originating context ${JSON.stringify(token)}`, () => {
        const {page, fields} = noteBrowser(token);
        // A later chart may replace compatibility state; this popup owns its captured token.
        page.case_program_id = 'another-program-opened-later';
        page.showEncounter('&notes2print=501,502');
        page.PrintEncounter();
        page.reloadNoteBrowser();
        for (const [address, route] of [[fields.docdisp.iframe.src, 'CaseManagementEntry'],
            [page.printed, 'CaseManagementEntry'], [page.window.location.href, 'casemgmt/ViewNoteBrowser']]) {
            const url = new URL(address, 'https://clinic.test');
            assert.equal(url.pathname, `/carlos/${route}`);
            assert.deepEqual(url.searchParams.getAll('chartContext'), token ? [token] : []);
            assert.equal(url.searchParams.get(route === 'CaseManagementEntry' ? 'demographicNo' : 'demographic_no'), '101');
            assert.equal(url.searchParams.has('CSRF-TOKEN'), false);
            if (route === 'CaseManagementEntry') assert.equal(url.searchParams.get('notes2print'), '501,502');
            else assert.equal(url.searchParams.get('view'), 'type + & /');
        }
        assert.equal(new URL(fields.docdisp.iframe.src, 'https://clinic.test').searchParams.get('method'), 'displayNotes');
        assert.equal(new URL(page.printed, 'https://clinic.test').searchParams.get('method'), 'print');
    });
}
