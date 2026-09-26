/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const { HANDLER, decodeJsString, recordNeedle } = require('./echart-prevention-row-links-playwright-checks');

// Rendered by LeftNavBarDisplay.jsp: trackWindowString's reloadWindows prefix, then the
// handler EctDisplayPrevention2Action built with SafeEncode.forJavaScript (issue #3975).
const RENDERED = "reloadWindows['addPreventionData42'] = '\\/carlos\\/encounter\\/displayPrevention?hC=009999\\x26numToDisplay=6';"
  + "reloadWindows['addPreventionData42div'] = 'preventions';"
  + "popupPage(600,900,'addPreventionData42','\\/carlos\\/prevention\\/ViewAddPreventionData?prevention=HPV\\-CERVIX"
  + "\\x26demographic_no=42\\x26prevResultDesc=');return false;";

test('the handler pattern reads the popup call, not the reloadWindows prefix', () => {
  const match = HANDLER.exec(RENDERED);
  assert.ok(match);
  assert.deepEqual(match.slice(1, 4), ['600', '900', 'addPreventionData42']);
  const url = new URL(decodeJsString(match[4]), 'https://127.0.0.1');
  assert.equal(url.pathname, '/carlos/prevention/ViewAddPreventionData');
  assert.equal(url.searchParams.get('prevention'), 'HPV-CERVIX');
  assert.equal(url.searchParams.get('demographic_no'), '42');
  assert.equal(url.searchParams.get('prevResultDesc'), '');
});

test('JavaScript escapes decode to the characters the browser evaluates', () => {
  assert.equal(decodeJsString("O\\x27Brien \\x26 \\\"Co\\\" \\u00e9\\/"), "O'Brien & \"Co\" é/");
});

test('the record needle matches the encoded edit handler the action emits for an id', () => {
  const edit = "popupPage(600,900,'addPreventionData42','\\/carlos\\/prevention\\/ViewAddPreventionData?id=917\\x26demographic_no=42');return false;";
  assert.ok(edit.includes(recordNeedle('917')));
  assert.ok(!edit.includes(recordNeedle('91')));
});
