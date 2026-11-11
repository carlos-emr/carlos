#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Expression Language, JSTL and displaytag on deployed pages (issue #5416).
 *
 * #5416 moved the Jakarta Expression Language API to 6.0 and out of the WAR: Tomcat 11
 * supplies it, and loads it ahead of any copy in WEB-INF/lib (docs/container-provided-apis.md).
 * This check proves, on a running deployment, that the container still evaluates the
 * expressions, tags and EL functions CARLOS pages rely on. Administration is entered through
 * the schedule's control; the other pages are requested with the GET their links send, which
 * renders and saves nothing:
 *
 *   1. Administration (from the schedule's Administration control): every menu link's
 *      `${ctx}` resolved to the context path, and every <fmt:message> label resolved.
 *   2. Disease Registry (Administration ▸ Disease Registry, in the panel's frame): <c:forEach>
 *      over a bean property lists the coding systems, <c:if test="${radiostatus == '...'}">
 *      checks exactly the status the scriptlet-built search form names, and displaytag
 *      renders the code table or its empty-list message.
 *   3. Facility management (FacilityManager): displaytag column headers from <fmt:message>
 *      keys, and either facility rows with Edit links or the EL-supplied empty message.
 *   4. Add professional contact (demographic/Contact?method=addProContact): the
 *      ${carlos:forJavaScript(param.keyword)} and ${carlos:forHtmlAttribute(param.contactType)}
 *      EL functions carry quotes, an apostrophe, an ampersand and angle brackets into the page
 *      exactly, and inject no markup. (No literal ${...} is sent: the packaged WAF rightly
 *      refuses one in a parameter, CRS rule 932130.)
 *
 * Every page must answer 200, render no error page, leave no `${...}` or `#{...}` in its markup
 * (text and attributes, hidden ones included) or EL source in its scripts, show no `???key???`
 * from a missing <fmt:message> key, and raise no strict-page browser problem.
 *
 * READ-ONLY: nothing is submitted, seeded or cleaned up.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/el-jstl-pages-playwright-checks.js
 * The common environment contract (BASE_URL, TEST_USER, ...) is in lib/playwright-harness.js.
 */
const vm = require('node:vm');
const {
  appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, launchBrowser, login,
  newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');

const TIMEOUT = 20000;
// Source text that only survives into a response when the container did not evaluate it,
// immediate (${...}) or deferred (#{...}) syntax alike.
const UNEVALUATED_SOURCE = /[$#]\{\s*(?:pageContext|param|paramValues|header|cookie|initParam|(?:page|request|session|application)Scope|ctx\b|carlos:|fn:|empty\b|not\b)/;
// Visible text never shows an expression, and <fmt:message> marks a missing key ???key???.
const UNEVALUATED_TEXT = /\$\{|#\{|\?\?\?[\w.-]+\?\?\?/;
// Characters the encoders must neutralise: the quotes that end a JavaScript string or an
// attribute, the ampersand that starts an entity, the brackets that start markup and the slash
// of a closing </script>.
const KEYWORD = 'EL "6" O\'Brien & Sons <Ltd> A/B';
const CONTACT_TYPE = 'type "1" & \'x\' <y>';

/** The text around a match, for a failure message. */
function excerpt(text, index) {
  return text.slice(Math.max(0, index - 40), index + 60).replace(/\s+/g, ' ');
}

/** EL source anywhere in a response body, scripts included, by the names CARLOS pages use. */
function unevaluatedSource(body) {
  const match = UNEVALUATED_SOURCE.exec(body);
  return match ? excerpt(body, match.index) : null;
}

/**
 * Any `${` or `#{` left in the markup: text and attribute values, hidden ones included, whatever
 * the expression names. Comments, <script> and <style> blocks and inline on* handlers are set
 * aside first, because JavaScript template literals legitimately use `${`; EL left in those is
 * what unevaluatedSource looks for.
 */
function unevaluatedMarkup(body) {
  const markup = body
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, ' ')
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, ' ');
  const match = /\$\{|#\{/.exec(markup);
  return match ? excerpt(markup, match.index) : null;
}

/** The checks every page gets: 200, no error page, no EL or fmt source left in it. */
async function assertRendered(page, response, label) {
  assert(response, `${label} did not load`);
  assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
  const text = await assertNotErrorPage(page, label);
  const body = await response.text();
  const leaked = unevaluatedSource(body) || unevaluatedMarkup(body);
  assert(!leaked, `${label} contains unevaluated EL source: "${leaked}"`);
  const visible = UNEVALUATED_TEXT.exec(text);
  assert(!visible, `${label} shows unevaluated text "${visible && visible[0]}"`);
  return text;
}

/** Open an application path in its own strictly wired tab. */
async function openPage(context, recorder, config, label, appPath) {
  const page = await context.newPage();
  wireStrictPage(page, label, recorder);
  const response = await page.goto(appUrl(config.baseUrl, appPath), { waitUntil: 'load', timeout: TIMEOUT });
  return { page, response };
}

/** Decode a single-quoted JavaScript string literal the way the browser does, without a browser. */
function jsLiteral(literal) {
  return vm.runInNewContext(`'${literal}'`, Object.create(null), { timeout: 1000 });
}

async function checkAdministration(schedule, context, recorder, contextPath) {
  const opener = schedule.locator('#admin-panel, #admin2').first();
  assert(await opener.count() > 0, 'The schedule offers no Administration control');
  // The panel may open as a popup or in place, so keep every top-level document response and
  // take the one that loaded the panel once the click has settled.
  const documents = [];
  const keep = candidate => {
    const request = candidate.request();
    if (request.resourceType() === 'document' && request.isNavigationRequest()) documents.push(candidate);
  };
  context.on('response', keep);
  let opened;
  try {
    opened = await clickOpensPopupOrNavigates(schedule, opener,
      { context, label: 'administration', recorder, timeout: TIMEOUT });
    await opened.page.waitForLoadState('load', { timeout: TIMEOUT });
  } finally {
    context.off('response', keep);
  }
  const { page } = opened;
  const response = documents.filter(candidate => {
    try {
      return candidate.frame() === page.mainFrame() && (candidate.status() < 300 || candidate.status() >= 400);
    } catch {
      return false;
    }
  }).pop();
  await assertRendered(page, response, 'Administration');
  const targets = await page.locator('a.xlink[rel]').evaluateAll(links => links.map(link => link.getAttribute('rel')));
  assert(targets.length >= 20, `Administration lists only ${targets.length} menu links`);
  const unresolved = targets.filter(target => !target.startsWith(`${contextPath}/`) || target.includes('${'));
  assert(unresolved.length === 0,
    `Administration menu links whose \${ctx} was not resolved to ${contextPath}: ${unresolved.slice(0, 5).join(', ')}`);
  console.log(`  Administration: ${targets.length} menu links resolve under ${contextPath}`);
  return opened;
}

async function checkDiseaseRegistry(admin) {
  const label = 'Disease Registry';
  const route = '/report/DxresearchReport';
  const entry = admin.locator(`a.xlink[rel$="${route}"]`).first();
  assert(await entry.count() === 1, 'Administration offers no Disease Registry entry');
  await revealAuditLink(admin, entry, TIMEOUT);
  const isReport = url => { try { return new URL(url).pathname.endsWith(route); } catch { return false; } };
  const responded = admin.waitForResponse(candidate => isReport(candidate.url())
    && candidate.request().resourceType() === 'document', { timeout: TIMEOUT });
  const loaded = admin.waitForEvent('framenavigated',
    { predicate: candidate => candidate !== admin.mainFrame() && isReport(candidate.url()), timeout: TIMEOUT });
  responded.catch(() => {});
  loaded.catch(() => {});
  await entry.click({ timeout: TIMEOUT });
  const [response, frame] = await Promise.all([responded, loaded]);
  await frame.waitForLoadState('load', { timeout: TIMEOUT });
  await assertRendered(frame, response, label);

  // <c:forEach items="${codingSystem.codingSystems}"> lists the configured coding systems.
  const codingSystems = await frame.locator('select#codingSystem option').evaluateAll(
    options => options.map(option => option.value).filter(Boolean));
  assert(codingSystems.length > 0, '<c:forEach> over ${codingSystem.codingSystems} listed no coding system');
  // <c:if test="${radiostatus == '...'}">checked</c:if> must check exactly the status the scriptlet
  // put in the search form's action, both read from the same session value.
  const checked = await frame.locator('input[name="SearchBy"]').evaluateAll(
    radios => radios.filter(radio => radio.checked).map(radio => radio.value));
  const formAction = await frame.locator('form', { has: frame.locator('input[name="SearchBy"]') }).getAttribute('action');
  const status = new URL(formAction, response.url()).searchParams.get('method');
  assert(checked.length === 1 && checked[0] === status,
    `<c:if test="\${radiostatus == ...}"> checked ${JSON.stringify(checked)}; the search form says ${status}`);
  // displaytag: the code table, or its empty-list message when no code has been added.
  const codeTable = await frame.locator('table#codeSearch').count();
  const text = await frame.locator('body').innerText();
  assert(codeTable === 1 || /Nothing found to display/i.test(text),
    'displaytag rendered neither the code table nor its empty-list message');
  console.log(`  ${label}: ${codingSystems.length} coding system(s), "${status}" checked by <c:if>, displaytag rendered`);
}

async function checkFacilityManager(context, recorder, config, contextPath) {
  const label = 'Facility management';
  const { page, response } = await openPage(context, recorder, config, label, '/FacilityManager');
  await assertRendered(page, response, label);
  const table = page.locator('table#facility');
  assert(await table.count() === 1, 'displaytag rendered no facility table (table#facility)');
  const headers = await table.locator('thead th').evaluateAll(ths => ths.map(th => th.innerText.trim()));
  assert(headers.length >= 2 && headers[0] && headers[1],
    `displaytag column headers from <fmt:message> keys are blank: ${JSON.stringify(headers)}`);
  const rows = await table.locator('tbody tr').evaluateAll(trs => trs.map(tr => ({
    cells: tr.cells.length,
    text: tr.innerText.trim(),
    edit: (tr.querySelector('a[href*="method=edit"]') || {}).getAttribute?.('href') || '',
  })));
  assert(rows.length > 0, 'The facility table has no row and no empty-list message');
  if (rows.length === 1 && rows[0].cells === 1) {
    // The only row is displaytag's basic.msg.empty_list, set from ${noFacilitiesMessage}.
    assert(rows[0].text.length > 0, 'The facility list is empty and shows no empty-list message');
    console.log(`  ${label}: no facility; empty-list message "${rows[0].text}"`);
  } else {
    for (const row of rows) {
      assert(/\/FacilityManager\?method=edit&id=\d+$/.test(row.edit) && row.edit.startsWith(`${contextPath}/`),
        `Facility row "${row.text}" has Edit link "${row.edit}", not ${contextPath}/FacilityManager?method=edit&id=<id>`);
    }
    console.log(`  ${label}: ${rows.length} facility row(s) under headers ${JSON.stringify(headers.slice(0, 2))}`);
  }
  await page.close();
}

async function checkElFunctions(context, recorder, config) {
  const label = 'Add professional contact';
  const query = new URLSearchParams({ method: 'addProContact', keyword: `${KEYWORD}, Given`, contactType: CONTACT_TYPE });
  const { page, response } = await openPage(context, recorder, config, label, `/demographic/Contact?${query}`);
  const body = await response.text();
  await assertRendered(page, response, label);

  // forJavaScript: the literal in the page decodes to the exact keyword, and cannot end its string early.
  const literal = /var keyword = '((?:[^'\\]|\\.)*)';/.exec(body);
  assert(literal, 'The page has no var keyword = \'...\' literal from ${carlos:forJavaScript(param.keyword)}');
  // OWASP forJavaScript escapes quotes, '&', '\\' and '/' (so no </script> can form) and leaves '<' '>'.
  assert(!/['"&]|(^|[^\\])\//.test(literal[1]),
    `carlos:forJavaScript left a quote, '&' or an unescaped '/' in the script string: ${literal[1]}`);
  assert(jsLiteral(literal[1]) === `${KEYWORD}, Given`,
    `carlos:forJavaScript does not round-trip the keyword: ${JSON.stringify(jsLiteral(literal[1]))}`);
  // The page's onload splits that string into the name fields, so the browser saw the same value.
  const lastName = await page.locator('[id="pcontact.lastName"]').inputValue();
  const firstName = await page.locator('[id="pcontact.firstName"]').inputValue();
  assert(lastName === KEYWORD && firstName === 'Given',
    `The keyword reached the name fields as ${JSON.stringify([lastName, firstName])}`);

  // forHtmlAttribute: the hidden field carries the exact value and no markup escaped the attribute.
  const contactType = await page.locator('input#contactType').inputValue();
  assert(contactType === CONTACT_TYPE,
    `carlos:forHtmlAttribute does not round-trip contactType: ${JSON.stringify(contactType)}`);
  assert(await page.locator('Ltd, y').count() === 0, 'A parameter value was rendered as markup');
  console.log(`  ${label}: carlos:forJavaScript and carlos:forHtmlAttribute round-trip ${JSON.stringify(KEYWORD)}`);
  await page.close();
}

async function main() {
  const config = readConfig();
  const contextPath = new URL(config.baseUrl).pathname.replace(/\/$/, '');
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedule = await login(context, config, recorder);
    const { page: admin, isPopup } = await checkAdministration(schedule, context, recorder, contextPath);
    await checkDiseaseRegistry(admin);
    if (isPopup) await admin.close();
    await checkFacilityManager(context, recorder, config, contextPath);
    await checkElFunctions(context, recorder, config);
    assertStrictPage(recorder);
    return { pages: 4 };
  } finally {
    await browser.close();
  }
}

if (require.main === module) runCheck({ name: 'el-jstl-pages', run: main });
module.exports = { main, unevaluatedSource, unevaluatedMarkup, jsLiteral, KEYWORD, CONTACT_TYPE };
