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
  // Patient identity: title and heading come from csPatientName/csPatientAge, always via carlos:encode.
  const title = /<title>([\s\S]*?)<\/title>/.exec(jsp)[1];
  assert.ok(title.includes('<carlos:encode value="${csPatientName}"/>'), 'title uses csPatientName');
  const sepEl = "${empty csPatientAge ? '' : ' &middot; '}";
  assert.ok(title.includes(sepEl + '<carlos:encode value="${csPatientAge}"/> &middot; <carlos:encode value="${csTitle}"/>'),
    'title separates name, age and ChartSpace with a constant separator outside carlos:encode');
  assert.ok(!title.includes('<c:') && !title.includes('.concat('), 'title uses no core taglib or concat');
  const h1 = /<h1 class="cs-title">([\s\S]*?)<\/h1>/.exec(jsp)[1];
  assert.ok(h1.includes('<carlos:encode value="${csPatientName}"/>'), 'h1 uses csPatientName');
  assert.ok(h1.includes('<span class="cs-patient-age">' +
    "${empty csPatientAge ? '' : '<span aria-hidden=" + '"true"' + ">&middot;</span> '}" +
    '<carlos:encode value="${csPatientAge}"/></span>'),
    'h1 renders an aria-hidden separator only with an age, then the encoded age, in its own span');
  assert.ok(jsp.includes('<%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>'));
  assert.ok(jsp.includes('${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/css/global.css'));
  assert.ok(!jsp.includes('/library/bootstrap/'), 'Bootstrap asset versions belong in the shared include');
  // Every external script of the page, in order: each is loaded through the encoded context path,
  // like the stylesheet links, and nothing else is loaded (the inline init block has no src).
  const scriptSrcs = Array.from(jsp.matchAll(/<script\s+src="([^"]*)"/g), (m) => m[1]);
  assert.deepEqual(scriptSrcs, ['chartspace-layout.js', 'chartspace-allergies.js', 'chartspace.js']
    .map((f) => '${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/chartspace/' + f));
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

test('CSS styles the patient age as normal-weight, name-coloured text', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const css = fs.readFileSync(path.join(__dirname, '../src/main/webapp/css/chartspace.css'), 'utf8');
  const m = /\.cs-patient-age\s*\{([^}]*)\}/.exec(css);
  assert.ok(m, '.cs-patient-age rule present');
  ['font-weight: 400', 'color: inherit', 'margin-left: .25rem'].forEach((d) => assert.ok(m[1].includes(d), d));
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
