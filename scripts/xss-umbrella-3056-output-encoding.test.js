/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Output-encoding contract for the application XSS umbrella (#3056) and the follow-ups it consolidates
// (#2426, #4185, #5833, #5837, #5838, #5855, #5962, #5985).
//
// Every sink the umbrella names was re-validated against release/2026.08. A sink is pinned here in two
// directions: the raw output the review found must be gone, and the context-correct CARLOS encoder must be
// in its place. JSP comments are stripped first so a note describing the old code can never satisfy or
// violate an assertion. The one sink built in script (the referral list) is executed instead of matched.
//
// Sinks that were already encoded on the release branch when the umbrella was audited are listed as well
// ("already encoded"), so they cannot quietly regress.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '..');
const JSP_ROOT = path.join(ROOT, 'src/main/webapp/WEB-INF/jsp');
const JAVA_ROOT = path.join(ROOT, 'src/main/java/io/github/carlos_emr/carlos');
const stripComments = text => text.replace(/<%--[\s\S]*?--%>/g, '');
const jsp = rel => stripComments(fs.readFileSync(path.join(JSP_ROOT, rel), 'utf8'));
const java = rel => fs.readFileSync(path.join(JAVA_ROOT, rel), 'utf8');

const esc = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const spaced = text => esc(text).replace(/\s+/g, '\\s*');

/**
 * A raw scriptlet output: <%= expr %>. The same text is the value of a correctly encoded tag
 * (<carlos:encode value='<%= expr %>'/>), so that position is excluded; a scriptlet inside a plain
 * attribute (value="<%= expr %>") is still raw.
 */
const raw = expr => new RegExp(`(?<!<carlos:encode\\s+value=['"])<%=\\s*${spaced(expr)}\\s*%>`);

/** <carlos:encode value='<%= expr %>' [context="ctx"]/>, with either quote style around the value. */
function tag(expr, context) {
  const value = `(?:'<%=\\s*${spaced(expr)}\\s*%>'|"<%=\\s*${spaced(expr)}\\s*%>")`;
  const ctx = context && context !== 'html' ? `\\s+context="${context}"` : '(?:\\s+context="html")?';
  return new RegExp(`<carlos:encode\\s+value=${value}${ctx}\\s*/>`);
}

/** ${carlos:fn(expr)} */
const el = (fn, expr) => new RegExp(`\\$\\{\\s*carlos:${fn}\\(\\s*${spaced(expr)}\\s*\\)\\s*\\}`);

const count = (re, text) => (text.match(new RegExp(re.source, 'g')) || []).length;

/**
 * Each surface: forbid = patterns that must not appear at all; require = [pattern, minimum count].
 * `issue` ties the entry to the ticket that reported it.
 */
const SURFACES = [
  // ---- #3056 original scope -------------------------------------------------------------------------
  { issue: '#2766', file: 'provider/providerSignature.jsp',
    forbid: [raw('sig.getSignature(curUser_no)')],
    require: [[tag('sig.getSignature(curUser_no)'), 1]] },
  { issue: '#2775', file: 'provider/providerAddress.jsp',
    forbid: [raw('address'), raw('city'), raw('province'), raw('postal')],
    require: [[tag('address', 'htmlAttribute'), 1], [tag('city', 'htmlAttribute'), 1],
      [tag('province', 'htmlAttribute'), 1], [tag('postal', 'htmlAttribute'), 1],
      [tag('address'), 1], [tag('city'), 1], [tag('province'), 1], [tag('postal'), 1]] },
  { issue: '#2773', file: 'provider/mainMenu.jsp',
    forbid: [/resourcehelpHtml\s*=\s*rbuHtml\.getValue\(\)/],
    require: [[/SafeHtml\.forFormattedText\(\s*rbuHtml\.getValue\(\)\s*\)/, 1],
      [/<%@\s*page\s+import="io\.github\.carlos_emr\.carlos\.utility\.SafeHtml"\s*%>/, 1]] },
  { issue: '#2773', file: 'provider/appointmentprovideradminmonth.jsp',
    forbid: [/resourcehelpHtml\s*=\s*rbuHtml\.getValue\(\)/],
    require: [[/SafeHtml\.forFormattedText\(\s*rbuHtml\.getValue\(\)\s*\)/, 1],
      [/<%@\s*page\s+import="io\.github\.carlos_emr\.carlos\.utility\.SafeHtml"\s*%>/, 1]] },
  { issue: '#2771', file: 'provider/providerPreferenceQuickLinksAction.jsp',
    forbid: [/"Missing action case\. action="\s*\+\s*action/],
    require: [[/LogSafe\.sanitize\(\s*action\s*\)/, 1],
      [/<%@\s*page\s+import="io\.github\.carlos_emr\.carlos\.utility\.LogSafe"\s*%>/, 1]] },
  { issue: '#2776', file: 'provider/preference_action.jsp',
    forbid: [/parameters\.get\("provider_no"\)\[0\]/],
    require: [[/preference\.getProviderNo\(\)/, 1]] },

  // ---- #2426 ------------------------------------------------------------------------------------------
  { issue: '#2426', file: 'demographic/demographicswipe.jsp',
    forbid: ['responseDescription', 'responseCode', 'responseAction', 'lastName', 'firstName',
      'hcMagneticStripe.getHealthNumber()', 'hcMagneticStripe.getCardVersion().toUpperCase()', 'gender',
      'dobyear', 'dobmonth', 'dobdate', 'effyear', 'effmonth', 'effdate', 'endyear', 'endmonth', 'enddate']
      .map(raw),
    require: [
      ...['responseDescription', 'responseCode', 'responseAction'].map(expr => [tag(expr), 1]),
      // each card field is printed twice: in the editable input (HTML attribute) and in the Attach() call
      // of the onclick handler (JavaScript inside an HTML attribute)
      ...['lastName', 'firstName', 'hcMagneticStripe.getHealthNumber()', 'hcMagneticStripe.getCardVersion().toUpperCase()',
        'gender', 'dobyear', 'dobmonth', 'dobdate', 'effyear', 'effmonth', 'effdate', 'endyear', 'endmonth', 'enddate']
        .flatMap(expr => [[tag(expr, 'htmlAttribute'), 1], [tag(expr, 'javaScriptAttribute'), 1]])] },

  // ---- #4185 ------------------------------------------------------------------------------------------
  { issue: '#4185', file: 'oscarReport/oscarReportAgeSex.jsp',
    forbid: [raw('proOHIP'), raw('proLast'), raw('proFirst')],
    require: [[tag('proOHIP', 'htmlAttribute'), 1], [tag('proLast'), 1], [tag('proFirst'), 1]] },
  { issue: '#4185', file: 'oscarReport/oscarReportDxReg.jsp',
    forbid: [raw('p.getProviderNo()'), raw('p.getFormattedName()'), raw('g.getId().getMyGroupNo()'),
      raw('"_grp_"+g.getId().getMyGroupNo()'), /<option value="\$\{codingSys\}">\$\{codingSys\}<\/option>/],
    require: [[tag('p.getProviderNo()', 'htmlAttribute'), 2], [tag('p.getFormattedName()'), 2],
      [tag('"_grp_" + g.getId().getMyGroupNo()', 'htmlAttribute'), 1], [tag('g.getId().getMyGroupNo()'), 1],
      [el('forHtmlAttribute', 'codingSys'), 1], [el('forHtml', 'codingSys'), 1]] },
  { issue: '#4185', file: 'admin/groupnoacl.jsp',
    forbid: [raw('group'), raw('provider.getProviderNo()'), raw('provider.getFormattedName()'),
      raw('provider.getLastName()'), raw('provider.getFirstName()')],
    require: [[tag('group', 'htmlAttribute'), 1], [tag('group'), 1],
      [tag('provider.getProviderNo()', 'htmlAttribute'), 2], [tag('provider.getFormattedName()'), 1],
      [tag('provider.getLastName()'), 1], [tag('provider.getFirstName()'), 1]] },
  { issue: '#4185', file: 'admin/manageFaxes.jsp',
    forbid: [raw('providerData.getId()'), raw('providerData.getLastName() + ", " + providerData.getFirstName()')],
    require: [[tag('providerData.getId()', 'htmlAttribute'), 1],
      [tag('providerData.getLastName() + ", " + providerData.getFirstName()'), 1]] },
  { issue: '#4185', file: 'admin/fixRolesOnNotes.jsp',
    forbid: [raw('role.getName()')],
    require: [[tag('role.getName()'), 1]] },
  // Backport of the two Demographic Export hunks #2452 made on develop (same text, so a forward merge is clean).
  { issue: '#4185/#2452', file: 'demographic/demographicExport.jsp',
    forbid: [raw('setName'), raw('p.getProviderNo()'), raw('p.getFormattedName()')],
    require: [[/SafeEncode\.forHtmlAttribute\(\s*setName\s*\)/, 1], [tag('setName', 'html'), 1],
      [/SafeEncode\.forHtmlAttribute\(\s*p\.getProviderNo\(\)\s*\)/, 1], [tag('p.getFormattedName()', 'html'), 1],
      [/<%@\s*page\s+import="io\.github\.carlos_emr\.carlos\.utility\.SafeEncode"\s*%>/, 1]] },

  // ---- #5838 ------------------------------------------------------------------------------------------
  { issue: '#5838', file: 'casemgmt/noteIssueList.jsp',
    forbid: [/id="summary\$\{param\.newNoteIdx\}"/, /id="observation\$\{param\.newNoteIdx\}"/,
      /id="encType\$\{noteIndex\}"/, /id="\$\{encSelect\}"/],
    require: [[el('forHtmlAttribute', 'param.newNoteIdx'), 2], [el('forHtmlAttribute', 'noteIndex'), 1],
      [el('forHtmlAttribute', 'encSelect'), 1]] },
  { issue: '#5838', file: 'form/formselect.jsp',
    forbid: [/value="\$\{param\.savedAddSelection\}"/, /value="\$\{param\.savedDeleteSelection\}"/],
    require: [[el('forHtmlAttribute', 'param.savedAddSelection'), 1],
      [el('forHtmlAttribute', 'param.savedDeleteSelection'), 1]] },
  { issue: '#5838', file: 'oscarMDS/Page.jsp',
    forbid: [/'\$\{\s*searchProviderNo\s*\}'/, /'\$\{\s*param\.status\s*\}'/],
    // Forward and File each put both values into a JavaScript string inside an onclick attribute
    require: [[el('forJavaScriptAttribute', 'searchProviderNo'), 2], [el('forJavaScriptAttribute', 'param.status'), 2]] },
  { issue: '#5838', file: 'demographic/manageHealthCareTeam.jsp',
    forbid: [/(?:class|id)="\$\{\s*param\.view\s*\}View"/],
    require: [[el('forHtmlAttribute', 'param.view'), 3]] },
  { issue: '#5838', file: 'casemgmt/search.jsp',
    forbid: [/note_view\.value='\$\{param\.note_view\}'/],
    require: [[el('forJavaScriptAttribute', 'param.note_view'), 1]] },
  { issue: '#5838', file: 'tickler/ticklerAddWidget.jsp',
    forbid: [/value="\$\{\s*param\.demographicNo\s*\}"/],
    require: [[el('forHtmlAttribute', 'param.demographicNo'), 1]] },

  // ---- #5833, #5985 -----------------------------------------------------------------------------------
  { issue: '#5833', file: 'rx/prescribe.jsp',
    forbid: [/getRenalDosingInformation\('renalDosing_<%=rand%>','<%=rx\.getAtcCode\(\)%>'\)/],
    // the allergy check on the same card already used this context; the renal call now does too
    require: [[tag('rx.getAtcCode()', 'javaScriptBlock'), 2]] },
  { issue: '#5985', file: 'rx/WriteScript.jsp',
    forbid: [raw('limitedUseCode.getTxt()')],
    require: [[tag('limitedUseCode.getTxt()'), 1], [tag('limitedUseCode.getUseId()', 'html'), 1],
      [tag('limitedUseCode.getUseId()', 'javaScriptAttribute'), 1]] },

  // ---- #5837 ------------------------------------------------------------------------------------------
  { issue: '#5837', file: 'form/formGrowth0_36.jsp',
    // every stored/prefilled property goes through an encoder: none may be printed raw
    forbid: [/(?<!<carlos:encode\s+value=['"])<%=\s*props\.getProperty\(/],
    require: [[/<carlos:encode\s+value='<%=\s*props\.getProperty\(/, 22],
      [/str_date = "<carlos:encode\s+value='<%=\s*props\.getProperty\("dateOfBirth"\)\s*%>'\s+context="javaScriptBlock"\s*\/>";/, 1]] },

  // ---- #5962 ------------------------------------------------------------------------------------------
  { issue: '#5962', file: 'decision/antenatal/antenatalplanner.jsp',
    forbid: [raw('risk_content'), raw('checklist_content')],
    // two tags, not one concatenation: a missing half must stay empty instead of becoming the text "null"
    require: [[tag('risk_content', 'javaScriptBlock'), 1], [tag('checklist_content', 'javaScriptBlock'), 1]] },

  // ---- already encoded when the umbrella was audited: keep them that way -------------------------------
  { issue: '#2768 (already encoded)', file: 'provider/receptionistfindprovider.jsp',
    forbid: [raw('sp'), raw('spnl'), raw('spnf'), raw('providername')],
    require: [[tag('sp', 'javaScriptAttribute'), 3], [tag('sp'), 3], [tag('spnl'), 1], [tag('spnf'), 1]] },
  { issue: '#4185 (already encoded)', file: 'admin/admindisplaymygroup.jsp',
    forbid: [raw('myGroup.getLastName() + "," + myGroup.getFirstName()')],
    require: [[tag('myGroup.getLastName() + "," + myGroup.getFirstName()'), 1]] },
  { issue: '#4185 (already encoded)', file: 'oscarReport/reportByTemplate/homePage.jsp',
    forbid: [raw('curReport.getTitle()'), raw('curReport.getDescription()')],
    require: [[tag('curReport.getTitle()'), 1], [tag('curReport.getDescription()'), 1]] },
  { issue: '#4185 (already encoded)', file: 'admin/adminnewgroup.jsp',
    forbid: [raw('provider.getLastName()'), raw('provider.getFirstName()')],
    require: [[tag('provider.getLastName()'), 1], [tag('provider.getFirstName()'), 1]] },
];

for (const surface of SURFACES) {
  test(`shouldEncodeTheReportedOutput_in ${surface.file} ${surface.issue}`, () => {
    const text = jsp(surface.file);
    for (const re of surface.forbid) {
      assert.equal(count(re, text), 0, `${surface.file} still prints ${re} unencoded`);
    }
    for (const [re, minimum] of surface.require) {
      const found = count(re, text);
      assert.ok(found >= minimum, `${surface.file}: expected at least ${minimum} of ${re}, found ${found}`);
    }
  });
}

test('shouldDeclareTheCarlosTaglibExactlyOnce_inEveryJspThatUsesIt', () => {
  const declaration = /<%@\s*taglib\s+uri="carlos"\s+prefix="carlos"\s*%>/g;
  const problems = [];
  for (const file of new Set(SURFACES.map(s => s.file))) {
    const text = jsp(file);
    const used = /\bcarlos:[A-Za-z]+/.test(text);
    const declared = (text.match(declaration) || []).length;
    if (used && declared !== 1) problems.push(`${file}: uses carlos: and declares the taglib ${declared} time(s)`);
  }
  assert.deepEqual(problems, [], 'A JSP that uses <carlos:...> or ${carlos:...} without the directive does not translate');
});

test('shouldNotUseTheRetiredOwaspTaglib_inAnyEditedJsp', () => {
  const retired = /owasp\.encoder\.jakarta|prefix="e"|<e:for|\$\{e:for|\bEncode\.for[A-Z]/;
  const offenders = [...new Set(SURFACES.map(s => s.file))].filter(file => retired.test(jsp(file)));
  assert.deepEqual(offenders, [], 'new output must use the null-safe carlos wrappers (the OWASP JSP taglib is not shipped)');
});

// ---- #5855: the referral list is built in script, so run the production function ------------------------
test('shouldInsertSpecialistNamesAsText_inTheCheckedReferralList', () => {
  const page = jsp('admin/billingreferralAdmin.jsp');
  const match = page.match(/function updateCheckedList\(data\) \{[\s\S]*?\n {12}\}/);
  assert.ok(match, 'updateCheckedList must still exist on the Referral Doctors page');

  function run(data) {
    const htmlHandedToTheDom = [];
    const table = { rows: [], append(child) {
      if (typeof child === 'string') htmlHandedToTheDom.push(child); else this.rows.push(child);
      return this;
    } };
    const element = tagName => ({ tagName, textValue: null, children: [],
      text(value) { this.textValue = value; return this; },
      append(child) { if (typeof child === 'string') htmlHandedToTheDom.push(child); else this.children.push(child); return this; } });
    const $ = selector => {
      if (typeof selector === 'string' && selector.startsWith('<') && !selector.includes('</')) return element(selector);
      if (selector === '#checked_items_tbl tbody tr') return { remove() { table.rows = []; } };
      if (selector === '#checked_items_tbl tbody') return table;
      throw new Error(`unexpected selector ${selector}`);
    };
    vm.runInNewContext(`${match[0]}; updateCheckedList(data);`, { $, data });
    return { table, htmlHandedToTheDom };
  }

  const markup = '<img src=x onerror="window.xp=1">';
  const poisoned = run([{ formattedName: `Doe, ${markup}` }, { formattedName: 'Roe, Ada' }]);
  assert.deepEqual(poisoned.htmlHandedToTheDom.filter(html => html.includes('onerror') || html.includes('Doe')), [],
    'a specialist name was handed to the DOM as HTML');
  const names = poisoned.table.rows.map(row => row.children[0].textValue);
  assert.deepEqual(names, [`Doe, ${markup}`, 'Roe, Ada'], 'the stored markup must stay literal text, unchanged');

  const empty = run([]);
  assert.equal(empty.table.rows.length + empty.htmlHandedToTheDom.length, 1, 'an empty list shows one -None- row');
  assert.match(JSON.stringify(empty), /-None-/);
  assert.equal(run(null).htmlHandedToTheDom.join(''), '<tr><td>-None-</td></tr>');
});

// ---- #5985: the existing limited-use anchor contract is extended to the text cell -----------------------
test('shouldEncodeTheLimitedUseTextCell_nextToTheEncodedCode', () => {
  const page = jsp('rx/WriteScript.jsp');
  const row = page.match(/<%for \(LimitedUseCode limitedUseCode : luList\) \{%>[\s\S]*?<%\}%>/);
  assert.ok(row, 'the limited-use table loop must exist');
  assert.equal(count(raw('limitedUseCode.getTxt()'), row[0]), 0);
  assert.equal(count(tag('limitedUseCode.getTxt()', 'html'), row[0]), 1);
});

// ---- #2769: verified done, locked ------------------------------------------------------------------------
test('shouldEncodeFormNames_inUserPreference2Action', () => {
  const source = java('provider/web/UserPreference2Action.java');
  assert.ok(count(/SafeEncode\.forHtmlAttribute\(/, source) >= 1, 'form names in attributes must be encoded');
  assert.ok(count(/SafeEncode\.forHtmlContent\(/, source) >= 1, 'form names in element text must be encoded');
});

// ---- #2778: saveWorkView is an allowlist, not a copy of the request ---------------------------------------
test('shouldStoreOnlyAllowlistedSettings_inTheSaveWorkViewAction', () => {
  const source = java('provider/web/ProviderView2Action.java');
  assert.equal(count(/Map<String, String\[\]>\s+parameterMap\s*=\s*new HashMap/, source), 0,
    'the action must not copy the whole request parameter map into the view table');
  assert.equal(count(/return\s+null\s*;/, source), 0, 'a Struts result of null is an error page: return NONE');
  assert.ok(count(/WorkViewSettings\.accept\(\s*view_name\s*,\s*request\.getParameterMap\(\)\s*\)/, source) === 1,
    'settings must come from WorkViewSettings.accept');
  assert.ok(count(/response\.sendError\(\s*HttpServletResponse\.SC_BAD_REQUEST\s*\)/, source) === 1,
    'a rejected save must answer HTTP 400');
  assert.equal(count(/return\s+NONE\s*;/, source), 2, 'both the rejected and the saved path end with NONE');
  assert.ok(count(/LogSafe\.sanitize\(\s*view_name\s*\)/, source) === 1, 'the rejected view name is logged sanitized');
});

test('shouldKeepTheFourTicklerFiltersTheTicklerPagePosts_inTheAllowlist', () => {
  const allowlist = java('provider/web/WorkViewSettings.java');
  const page = jsp('tickler/ticklerMain.jsp');
  const posted = [...page.slice(page.indexOf('function saveView()')).match(/let params = \{[\s\S]*?\};/)[0]
    .matchAll(/^\s+(\w+):/gm)].map(match => match[1]);
  for (const setting of ['ticklerview', 'providerview', 'assignedTo', 'mrpview']) {
    assert.ok(posted.includes(setting), `the Tickler page no longer posts ${setting}`);
    assert.ok(allowlist.includes(`"${setting}"`), `${setting} is posted by the Tickler page but missing from WorkViewSettings`);
  }
});
