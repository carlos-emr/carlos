/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {test, before, after} = require('node:test');
const {chromium} = require('playwright');

// Render the production fragment's JSTL/EL with owned fixture values, then let
// Chromium parse the actual form/table structure and execute its Disable handler.
// This fixture does not claim server-side JSP rendering or database persistence.
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/PMmodule/Admin/Facility/ListFacilities.jsp'), 'utf8');
const facilities = [
  {id: '20959291', name: "FAKE First 'facility' & clinic", description: 'Owned first row', contactName: 'FAKE Contact', hic: false},
  {id: '20959292', name: 'FAKE Second facility', description: 'Owned second row', contactName: 'FAKE Contact', hic: true},
];
const htmlAttribute = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const javascriptValue = value => String(value).replace(/[\\'"<>&\r\n\u2028\u2029]/g,
  c => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0'));
function expressions(source, facility, index = 0) {
  return source.replace(/\$\{([^}]+)\}/g, (_, expression) => {
    if (expression === 'pageContext.request.contextPath') return '/carlos';
    if (expression.startsWith('tableRowStatus.count')) return index % 2 ? 'even' : 'odd';
    const field = expression.match(/\bfacility\.(id|name|description|contactName|hic)\b/);
    assert.ok(field && facility, 'Unknown fixture expression: ' + expression);
    const value = facility[field[1]];
    if (expression.includes('forJavaScript')) return javascriptValue(value);
    if (expression.includes('forUriComponent')) return encodeURIComponent(value);
    if (expression.includes('tableSortType')) return 'text';
    return htmlAttribute(value);
  });
}
function fixtureHtml() {
  let body = jsp.replace(/<%--[\s\S]*?--%>/g, '').replace(/<%@[^%]*%>/g, '')
    .replace(/<%=request.getContextPath\(\)\s*%>/g, '/carlos');
  body = body.replace(/<c:if test="\$\{empty facilities\}">[\s\S]*?<\/c:if>/g, '')
    .replace(/<c:if test="\$\{not empty facilities\}">([\s\S]*?)<\/c:if>/g, '$1');
  body = body.replace(/<c:forEach\b[^>]*>([\s\S]*?)<\/c:forEach>/g,
    (_, row) => facilities.map((facility, index) => expressions(row, facility, index + 1)).join(''));
  body = expressions(body);
  assert.ok(!/<%|\$\{|<\/?c:/.test(body), 'Unrendered JSP/JSTL in fixture');
  return body;
}
let browser;
before(async () => { browser = await chromium.launch({headless: true,
  ...(process.env.PLAYWRIGHT_BROWSER_PATH ? {executablePath: process.env.PLAYWRIGHT_BROWSER_PATH} : {})}); });
after(async () => { if (browser) await browser.close(); });

async function withPage(body) {
  const posts = [], errors = [];
  const server = http.createServer((req, res) => {
    if (req.method === 'POST') {
      let data = ''; req.on('data', chunk => data += chunk);
      req.on('end', () => {posts.push({url: req.url, fields: Object.fromEntries(new URLSearchParams(data))});res.writeHead(204);res.end();});
      return;
    }
    res.writeHead(200, {'Content-Type': 'text/html;charset=UTF-8'});res.end(fixtureHtml());
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const context = await browser.newContext(), page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  try {
    await page.goto(`http://127.0.0.1:${server.address().port}/carlos/list`); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- this test owns the server bound to literal 127.0.0.1; the OS assigns its port and the path is fixed; no user input enters the destination
    await body(page, posts);
    assert.deepEqual(errors, [], 'The production Disable handler must not throw');
  } finally {await context.close();server.closeAllConnections();await new Promise(resolve => server.close(resolve));}
}

test('every parsed row retains its own POST form, including the first facility', async () => withPage(async page => {
  for (const facility of facilities) {
    const form = page.locator('#deleteForm_' + facility.id);
    assert.equal(await form.count(), 1, 'Missing form for ' + facility.id);
    assert.equal(await form.getAttribute('method'), 'post');
    assert.equal(await form.getAttribute('action'), '/carlos/PMmodule/FacilityManager');
    assert.equal(await form.locator('input[name=id]').inputValue(), facility.id);
  }
}));
for (const [index, facility] of facilities.entries()) {
  test('Disable submits only the selected facility: ' + facility.id, async () => withPage(async (page, posts) => {
    page.on('dialog', dialog => dialog.accept());
    const submitted = page.waitForRequest(request => request.method() === 'POST', {timeout: 5000});
    await page.getByRole('link', {name: 'Disable', exact: true}).nth(index).click();
    await submitted;
    await page.waitForTimeout(50);
    assert.deepEqual(posts, [{url: '/carlos/PMmodule/FacilityManager',
      fields: {method: 'delete', id: facility.id, name: facility.name}}]);
  }));
}
test('Cancel leaves every facility unsubmitted', async () => withPage(async (page, posts) => {
  page.on('dialog', dialog => dialog.dismiss());
  await page.getByRole('link', {name: 'Disable', exact: true}).first().click();
  assert.deepEqual(posts, []);
}));
