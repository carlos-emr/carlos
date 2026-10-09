/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const { PRESET, TOP_MAX, normalizeLayout, partition } =
  require('../src/main/webapp/js/chartspace/chartspace-layout.js');

test('normalizeLayout drops unknown ids and duplicates, first region wins', () => {
  const out = normalizeLayout({ top: ['a', 'x'], right: ['a', 'b'], hidden: ['b'] }, ['a', 'b']);
  assert.deepEqual(out, { top: ['a'], right: ['b'], hidden: [] });
});

test('normalizeLayout caps top at 4 and moves the overflow to the start of right', () => {
  const out = normalizeLayout(
    { top: ['a', 'b', 'c', 'd', 'e'], right: ['f'], hidden: [] },
    ['a', 'b', 'c', 'd', 'e', 'f']);
  assert.equal(TOP_MAX, 4);
  assert.deepEqual(out.top, ['a', 'b', 'c', 'd']);
  assert.deepEqual(out.right, ['e', 'f']);
});

test('normalizeLayout tolerates missing or non-array regions', () => {
  assert.deepEqual(normalizeLayout(null, ['a']), { top: [], right: [], hidden: [] });
  assert.deepEqual(normalizeLayout({ top: 'a', right: 7 }, ['a']), { top: [], right: [], hidden: [] });
});

test('normalizeLayout returns a fresh object and never mutates PRESET', () => {
  const out = normalizeLayout(PRESET, ['allergies']);
  assert.notEqual(out, PRESET);
  assert.notEqual(out.right, PRESET.right);
  assert.ok(Object.isFrozen(PRESET));
  assert.deepEqual(out, { top: [], right: ['allergies'], hidden: [] });
  out.right.push('zzz');
  assert.deepEqual(PRESET.right, ['allergies']);
});

test('partition auto-hides EMPTY blocks but never NO_ACCESS, ERROR or LOADING', () => {
  const out = partition(
    { top: [], right: ['a', 'b', 'c', 'd'], hidden: [] },
    { a: 'EMPTY', b: 'NO_ACCESS', c: 'ERROR', d: 'LOADING' });
  assert.deepEqual(out.right, ['b', 'c', 'd']);
  assert.deepEqual(out.hidden, ['a']);
  assert.equal(out.hiddenHasData, false);
});

test('partition flags hiddenHasData only when a hidden block has data', () => {
  const layout = { top: [], right: [], hidden: ['a'] };
  assert.equal(partition(layout, { a: 'OK' }).hiddenHasData, true);
  assert.equal(partition(layout, { a: 'EMPTY' }).hiddenHasData, false);
});

test('normalizeLayout keeps ids that collide with Object.prototype members', () => {
  const out = normalizeLayout(
    { top: ['constructor', 'toString'], right: ['a'], hidden: [] },
    ['constructor', 'toString', 'a']);
  assert.deepEqual(out, { top: ['constructor', 'toString'], right: ['a'], hidden: [] });
});

test('chartSpace.jsp keeps the presentation contract', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const jsp = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/chartspace/chartSpace.jsp'), 'utf8');
  assert.equal(jsp.split('<fmt:setBundle basename="oscarResources"/>').length - 1, 1);
  assert.ok(jsp.includes('<%@ taglib uri="carlos" prefix="carlos" %>'));
  ['<e:', 'Encode.', '<c:out'].forEach((s) => assert.ok(!jsp.includes(s), s));
  assert.ok(!/(src|href)="[^"]*\.jsp"/.test(jsp));
  const ctx = '${pageContext.request.contextPath}';
  assert.ok(jsp.includes(ctx + '/library/bootstrap/5.3.8/css/bootstrap.min.css'));
  assert.ok(jsp.includes(ctx + '/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js'));
  const order = ['chartspace-layout.js', 'chartspace-allergies.js', 'chartspace.js']
    .map((f) => jsp.indexOf('/js/chartspace/' + f));
  assert.ok(order[0] > -1 && order[0] < order[1] && order[1] < order[2], String(order));
  ['cs-top', 'cs-right', 'cs-hidden-toggle', 'cs-hidden-panel', 'cs-announcer']
    .forEach((id) => assert.ok(jsp.includes('id="' + id + '"'), id));
  assert.ok(jsp.includes('<html lang="${pageContext.request.locale.language}">'));
});

function readJsp() {
  const fs = require('node:fs');
  const path = require('node:path');
  return fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/chartspace/chartSpace.jsp'), 'utf8');
}

test('JSP renders the hidden toggle disabled in static HTML', () => {
  const m = readJsp().match(/<button[^>]*id="cs-hidden-toggle"[^>]*>/);
  assert.ok(m, 'toggle tag present');
  assert.match(m[0], /\sdisabled(\s|>|=)/);
});

test('JSP drops the unused core taglib and the redundant setLocale', () => {
  const jsp = readJsp();
  assert.ok(!jsp.includes('prefix="c"'), 'no core taglib');
  assert.ok(!jsp.includes('fmt:setLocale'), 'no fmt:setLocale');
});

test('JSP has one page-level polite status announcer outside the hidden panel', () => {
  const jsp = readJsp();
  const tags = jsp.match(/<div[^>]*id="cs-announcer"[^>]*>/g) || [];
  assert.equal(tags.length, 1);
  assert.match(tags[0], /role="status"/);
  assert.match(tags[0], /aria-live="polite"/);
  assert.match(tags[0], /class="visually-hidden"/);
  const panel = jsp.slice(jsp.indexOf('id="cs-hidden-panel"'), jsp.indexOf('</section>', jsp.indexOf('id="cs-hidden-panel"')));
  assert.ok(!panel.includes('cs-announcer'), 'announcer is not inside the hidden panel');
});

test('JSP makes the hidden panel title programmatically focusable', () => {
  const m = readJsp().match(/<h2[^>]*id="cs-hidden-title"[^>]*>/);
  assert.ok(m, 'panel title present');
  assert.match(m[0], /tabindex="-1"/);
});

test('JSP passes the announce messages to the shell, JavaScript-encoded', () => {
  const jsp = readJsp();
  ['announceLoaded', 'announceEmpty', 'announceNoAccess', 'announceError'].forEach((k) => {
    const cap = 'csA' + k.slice(1);
    assert.ok(jsp.includes('<fmt:message key="chartspace.chartSpace.' + k + '" var="' + cap + '"/>'), k);
    assert.ok(jsp.includes(k + ": '${carlos:forJavaScript(" + cap + ")}'"), k);
  });
});
