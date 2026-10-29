/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// The pure helpers of el-jstl-pages-playwright-checks.js (issue #5416): the detector that
// fails a page still carrying EL source, and the decoder that reads a carlos:forJavaScript
// string the way the browser does.

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  unevaluatedSource, unevaluatedMarkup, jsLiteral, KEYWORD, CONTACT_TYPE,
} = require('./el-jstl-pages-playwright-checks');

test('JSP EL source that reached the browser is reported with its context', () => {
  for (const body of [
    '<link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>',
    '<a class="xlink" rel="${ctx}/report/DxresearchReport">',
    '<a rel="${carlos:forHtmlAttribute(ctx)}/admin/LabDisplaySettings">',
    '<option ${ empty param.searchType ? "selected" : "" }>',
    '<input value="${sessionScope.user}">',
  ]) {
    assert.ok(unevaluatedSource(body), `not reported: ${body}`);
  }
  assert.match(unevaluatedSource(`${'x'.repeat(100)} href="\${pageContext.request.contextPath}/a"`),
    /href="\$\{pageContext\.request\.contextPath\}\/a"/);
});

test('JavaScript template literals and evaluated pages are not reported', () => {
  for (const body of [
    '<script>const url = `${base}/report?id=${id}`;</script>',
    '<script>el.textContent = `${count} waiting`;</script>',
    '<a href="/carlos/report/DxresearchReport">Disease Registry</a>',
  ]) {
    assert.equal(unevaluatedSource(body), null, body);
  }
});

test('any expression left in markup is reported, whatever it names and wherever it hides', () => {
  for (const body of [
    '<a href="/carlos/FacilityManager?method=edit&id=1" title="${facility.name}">Edit</a>',
    '<input type="hidden" name="contactType" value="${carlos:forHtmlAttribute(param.contactType)}">',
    '<td style="display:none">${program.numOfMembers}</td>',
    '<option value="${codingSys}">icd9</option>',
    '<span data-value="#{bean.deferred}"></span>',
    '<textarea name="note">${note.text}</textarea>',
  ]) {
    assert.ok(unevaluatedMarkup(body), `not reported: ${body}`);
  }
  assert.match(unevaluatedMarkup('<p>ok</p><a title="${facility.name}">x</a>'), /title="\$\{facility\.name\}"/);
});

test('template literals in scripts, styles, comments and inline handlers are not markup', () => {
  const body = [
    '<script>const url = `${base}/x`;</script>',
    '<SCRIPT type="module">render(`#{id}`)</SCRIPT>',
    '<style>.a::after { content: "${x}"; }</style>',
    '<!-- legacy: <a href="${facility.id}"> -->',
    '<button onclick="go(`${ctx}/a`)" ONMOUSEOVER=\'tip(`${n}`)\'>Go</button>',
    '<a href="/carlos/report/DxresearchReport" title="Disease Registry">Disease Registry</a>',
  ].join('\n');
  assert.equal(unevaluatedMarkup(body), null);
});

test('a carlos:forJavaScript literal decodes to the exact value the check sent', () => {
  // What OWASP Encode.forJavaScript writes for the check's keyword.
  assert.equal(jsLiteral('EL \\x226\\x22 O\\x27Brien \\x26 Sons <Ltd> A\\/B, Given'), `${KEYWORD}, Given`);
  assert.equal(jsLiteral('type \\x221\\x22 \\x26 \\x27x\\x27 <y>'), CONTACT_TYPE);
});

test('the probe values hold every character the encoders must neutralise, and no ${...} the WAF refuses', () => {
  for (const value of [KEYWORD, CONTACT_TYPE]) {
    for (const character of ['"', '\'', '&', '<', '>']) {
      assert.ok(value.includes(character), `${JSON.stringify(value)} lacks ${character}`);
    }
    assert.ok(!value.includes('${') && !value.includes(','), `${JSON.stringify(value)} would be refused or split`);
  }
  assert.ok(KEYWORD.includes('/'), 'the keyword must include a slash, which forJavaScript escapes');
});
