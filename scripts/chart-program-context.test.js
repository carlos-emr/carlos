/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const start = source.indexOf('    var ctx;');
const end = source.indexOf('    var caisiEnabled', start);
assert.ok(start >= 0 && end > start, 'chart context initialization and URL helper exist');

function chart(token) {
    const sandbox = {window: {carlosChartContext: token}, encodeURIComponent};
    vm.createContext(sandbox);
    vm.runInContext(source.slice(start, end), sandbox);
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
