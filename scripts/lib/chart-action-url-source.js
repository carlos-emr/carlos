/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');

/** Extract the production chart URL helper, optionally including its captured window context. */
function chartActionUrlSource(source, { includeInitialization = false } = {}) {
    const helperMarker = '    function chartActionUrl(';
    const startMarker = includeInitialization ? '    var ctx;' : helperMarker;
    const endMarker = '    var caisiEnabled';
    const helperStart = source.indexOf(helperMarker);
    assert.ok(helperStart >= 0, `newCaseManagementView.js.jsp contains ${helperMarker.trim()}`);
    const start = includeInitialization ? source.indexOf(startMarker) : helperStart;
    assert.ok(start >= 0 && start <= helperStart,
        `newCaseManagementView.js.jsp contains ${startMarker.trim()} before its URL helper`);
    const end = source.indexOf(endMarker, helperStart);
    assert.ok(end > helperStart,
        `newCaseManagementView.js.jsp contains ${endMarker.trim()} after ${helperMarker.trim()}`);
    return source.slice(start, end);
}

module.exports = { chartActionUrlSource };
