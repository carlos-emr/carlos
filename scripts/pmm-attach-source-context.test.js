/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');

const chart = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const popup = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/casemgmt/attachClient.jsp'), 'utf8');

function assignHarness(token) {
    const begin = chart.indexOf('    function assign(programId, demographicNo) {');
    const end = chart.indexOf('    function setIssueCheckbox(', begin);
    assert.ok(begin >= 0 && end > begin, 'the actual native Assign handler exists');
    const script = chart.slice(begin, end).replace(/<%=request\.getContextPath\(\)%>/g, '/carlos');
    assert.doesNotMatch(script, /<%|%>/);
    const opened = [];
    const sandbox = {
        window: { carlosChartContext: token },
        document: { forms: { caseManagementEntryForm: { noteId: { value: '5' } } } },
        origCaseNote: 'FAKE saved text',
        origObservationDate: '2026-10-25',
        caseNote: 'note',
        $F: () => 'FAKE saved text',
        $: () => ({ value: '2026-10-25' }),
        popupPage: (...args) => opened.push(args),
        encodeURIComponent,
    };
    vm.createContext(sandbox);
    vm.runInContext(chartActionUrlSource(chart, { includeInitialization: true }) + script, sandbox);
    return { sandbox, opened };
}

test('native Assign keeps the source window signed program after another program opens', () => {
    const token = 'source202.program8.signature +&?#';
    const { sandbox, opened } = assignHarness(token);
    sandbox.window.carlosChartContext = 'source202.program7.later';
    sandbox.case_program_id = '7';
    sandbox.assign('8', '202');
    assert.equal(opened.length, 1);
    const url = new URL(opened[0][3], 'https://clinic.test');
    assert.equal(url.pathname, '/carlos/PMmodule/ClientSearch2');
    assert.equal(url.searchParams.get('demographicNo'), '202');
    assert.equal(url.searchParams.get('noteId'), '5');
    assert.equal(url.searchParams.get('method'), 'attachForm');
    assert.deepEqual(url.searchParams.getAll('sourceChartContext'), [token]);
    assert.equal(url.searchParams.has('chartContext'), false,
        'a source token must never masquerade as the future target patient context');
});

test('native tokenless Assign leaves the source token absent', () => {
    const { sandbox, opened } = assignHarness('');
    sandbox.assign('0', '202');
    const url = new URL(opened[0][3], 'https://clinic.test');
    assert.equal(url.searchParams.has('sourceChartContext'), false);
});

function selectClient(token) {
    const begin = popup.indexOf("    document.addEventListener('click', function (event) {");
    const end = popup.indexOf('\n</script>', begin);
    assert.ok(begin >= 0 && end > begin, 'the actual native client-selection handler exists');
    const script = popup.slice(begin, end)
        .replace(/<%= SafeEncode\.forJavaScript\(request\.getContextPath\(\)\) %>/g, '/carlos');
    assert.doesNotMatch(script, /<%|%>/);
    let handler;
    let sent;
    let prevented = false;
    const document = {
        clientSearchForm2: { elements: token == null ? {} : { sourceChartContext: { value: token } } },
        addEventListener: (name, callback) => { assert.equal(name, 'click'); handler = callback; },
    };
    vm.runInNewContext(script, {
        document,
        carlosPostForm: (url, fields) => { sent = { url, fields: Array.from(fields, pair => Array.from(pair)) }; },
    });
    handler({
        preventDefault: () => { prevented = true; },
        target: { closest: () => ({ getAttribute: name => ({ 'data-client': '101', 'data-note': '5' })[name] }) },
    });
    assert.equal(prevented, true);
    return sent;
}

test('native client selection POST keeps source context separate from the target patient', () => {
    const token = 'source202.program8.signature';
    const sent = selectClient(token);
    assert.equal(sent.url, '/carlos/encounter/IncomingEncounter');
    assert.deepEqual(sent.fields, [
        ['PEAttach', 'yes'], ['noteId', '5'], ['selectId', '101'],
        ['demographicNo', '101'], ['appointmentNo', '0'], ['sourceChartContext', token],
    ]);
});

test('native legacy client selection never submits a blank source token', () => {
    assert.equal(selectClient('').fields.some(([name]) => name === 'sourceChartContext'), false);
    assert.equal(selectClient(null).fields.some(([name]) => name === 'sourceChartContext'), false);
});

test('the popup redisplays only the action-validated signed source token', () => {
    assert.match(popup,
        /name="sourceChartContext" value="\$\{carlos:forHtmlAttribute\(requestScope\.sourceChartContext\)\}"/);
    assert.doesNotMatch(popup, /name="sourceChartContext"[^>]+param\./);
    assert.match(popup, /<c:if test="\$\{not empty requestScope\.sourceChartContext\}">\s*<input type="hidden" name="sourceChartContext"[^>]+\/?>\s*<\/c:if>/,
        'a tokenless search omits the control instead of submitting an explicitly blank token');
});
