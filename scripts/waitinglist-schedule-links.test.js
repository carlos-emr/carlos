/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/waitinglist/DisplayWaitingList.jsp'), 'utf8');

for (const patients of [1, 2]) {
  test(`waiting-list scheduling keeps patient, date and window arguments aligned for ${patients} patient rows`, () => {
    const functionStart = jsp.indexOf('function popupPage(');
    assert.notEqual(functionStart, -1);
    const functionEnd = jsp.indexOf('\n        }', functionStart);
    assert.notEqual(functionEnd, -1);
    const source = jsp.slice(functionStart, functionEnd + '\n        }'.length)
      .replaceAll('${carlos:forJavaScript(nbPatients)}', String(patients));
    const call = /onClick="(popupPage\([^\n]*?\)); return false;"/.exec(jsp)?.[1];
    assert.ok(call, 'the actual appointment link must invoke the tested function');
    assert.ok(!call.includes('patientName'), 'the appointment handler must omit the unused patient name');
    const renderedCall = call.replaceAll('${ctr.index}', '0')
      .replaceAll('${carlos:forJavaScript(waitingListBean.demographicNo)}', '17')
      .replaceAll('${carlos:forJavaScript(today)}', '2026-11-09')
      .replaceAll('<%= request.getContextPath() %>', '/carlos');
    const provider = { selectedIndex: 0, options: [{ value: 'provider&42' }] };
    let opened;
    vm.runInNewContext(source + '\n' + renderedCall, {
      document: { forms: [{ selectedProvider: patients > 1 ? [provider] : provider }] },
      window: { open: (url, title, properties) => { opened = { url, properties }; return null; } },
    });
    const url = new URL(opened.url, 'http://localhost');
    assert.equal(url.pathname, '/carlos/schedule/FlipView');
    assert.equal(url.searchParams.get('provider_no'), 'provider&42');
    assert.equal(url.searchParams.get('demographic_no'), '17');
    assert.equal(url.searchParams.get('startDate'), '2026-11-09');
    assert.equal(url.searchParams.get('originalpage'), '/carlos/waitinglist/SetupDisplayWaitingList');
    assert.match(opened.properties, /^height=400,width=780,/);
  });
}
