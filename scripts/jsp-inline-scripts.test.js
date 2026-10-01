/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Static guards for JSP inline scripts (issue #4131).
 *
 * A single broken token in a page's inline <script> (a literal newline inside a string, an
 * identifier split across two lines) is a SyntaxError for the WHOLE block: every function in
 * it is undefined and every control on the page goes dead, with nothing reported but a console
 * message. Document Browser, Note Browser and the waiting-list confirm in
 * demographicupdatearecord.jsp all shipped that way. This test compiles every inline script of
 * every JSP after neutralising the JSP/EL markup, so the defect class fails CI instead.
 *
 * The neutralisation is a heuristic: scriptlet expressions become 0, scriptlets and <c:set>
 * vanish, EL and custom tags become an identifier. Pages whose scripts are assembled by
 * scriptlet branches (or that embed "%>" in Java strings) cannot be modelled that way; they are
 * listed in UNMODELLED and skipped. The list may only shrink: an entry that now compiles, or
 * names a file that no longer exists, fails the test so it is removed.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '..');
const WEBAPP = path.join(ROOT, 'src/main/webapp');

// Files whose inline scripts the neutraliser cannot turn into valid JavaScript. These are not
// known defects; each needs a human (or a smarter model of its scriptlets) to judge.
const UNMODELLED = new Set([
  'WEB-INF/jsp/billing/CA/BC/billingCodeNewUpdate.jsp',
  'WEB-INF/jsp/billing/CA/BC/billingDigNewUpdate.jsp',
  'WEB-INF/jsp/billing/CA/BC/billingDigUpdate.jsp',
  'WEB-INF/jsp/billing/CA/BC/billingReferCodeUpdate.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingCodeUpdate.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingON.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingONCorrection.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingONNewReport.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingONStatus.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingONfavourite.jsp',
  'WEB-INF/jsp/billing/CA/ON/billingShortcutPg1.jsp',
  'WEB-INF/jsp/casemgmt/newEncounterLayout.jsp',
  'WEB-INF/jsp/documentManager/addedithtmldocument.jsp',
  'WEB-INF/jsp/documentManager/annotateDocument.jsp',
  'WEB-INF/jsp/documentManager/documentReport.jsp',
  'WEB-INF/jsp/documentManager/editDocument.jsp',
  'WEB-INF/jsp/documentManager/incomingDocs.jsp',
  'WEB-INF/jsp/encounter/includes/encounter-head.jspf',
  'WEB-INF/jsp/form/formConsultant.jsp',
  'WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp',
  'WEB-INF/jsp/oscarMDS/Index.jsp',
  'WEB-INF/jsp/schedule/scheduletemplateapplying.jsp',
  'WEB-INF/jspf/csrf-token.jspf',
]);

function listJsps(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) listJsps(full, out);
    else if (/\.jspf?$/.test(entry.name)) out.push(full);
  }
  return out;
}

/** JSP comments are blanked (not removed) so reported line numbers still match the file. */
function blankJspComments(source) {
  return source.replace(/<%--[\s\S]*?--%>/g, comment => comment.replace(/[^\n]/g, ' '));
}

/** Inline, executable script blocks with the line each starts on. */
function inlineScripts(source) {
  const scripts = [];
  // HTML ends a script at "</script" followed by whitespace, "/" or ">", so "</script >" and
  // "</script\n>" close it too; matching only "</script>" would fold the following markup in.
  const pattern = /<script\b([^>]*)>([\s\S]*?)<\/script\b[^>]*>/gi;
  let match;
  while ((match = pattern.exec(source))) {
    const attributes = match[1];
    if (/\bsrc\s*=/i.test(attributes)) continue;
    const type = /\btype\s*=\s*["']([^"']*)["']/i.exec(attributes);
    if (type && !/^(text|application)\/javascript$|^module$/i.test(type[1].trim())) continue;
    scripts.push({ code: match[2], line: source.slice(0, match.index).split('\n').length });
  }
  return scripts;
}

/**
 * Replaces JSP/EL markup with JavaScript-neutral stand-ins. Scriptlets are removed outright, not
 * replaced by their newlines: one inside a string literal would otherwise split the string.
 */
function neutralise(code) {
  return code
    .replace(/<%=[\s\S]*?%>/g, '0')
    .replace(/<%[\s\S]*?%>/g, '')
    .replace(/<c:set\b[^>]*\/>/g, '')
    .replace(/<c:set\b[^>]*>[\s\S]*?<\/c:set>/g, '')
    .replace(/\$\{[^}]*\}/g, 'X')
    .replace(/<\/?[A-Za-z][\w-]*:[\w-]+\b[^>]*>/g, 'X');
}

function compileErrors(file) {
  const source = blankJspComments(fs.readFileSync(file, 'utf8'));
  const errors = [];
  for (const script of inlineScripts(source)) {
    try {
      new vm.Script(neutralise(script.code), { filename: file });
    } catch (error) {
      errors.push(`script at line ${script.line}: ${error.message}`);
    }
  }
  return errors;
}

const relative = file => path.relative(WEBAPP, file).split(path.sep).join('/');

test('every JSP inline script compiles once JSP markup is neutralised', () => {
  const failures = [];
  for (const file of listJsps(WEBAPP)) {
    if (UNMODELLED.has(relative(file))) continue;
    for (const error of compileErrors(file)) failures.push(`${relative(file)} ${error}`);
  }
  assert.deepEqual(failures, [], 'Inline scripts that no longer parse (the whole block is dead in the browser)');
});

test('the unmodelled list only names files that exist and still need it', () => {
  for (const entry of UNMODELLED) {
    const file = path.join(WEBAPP, entry);
    assert.ok(fs.existsSync(file), `${entry} no longer exists: remove it from UNMODELLED`);
    assert.notDeepEqual(compileErrors(file), [], `${entry} now compiles: remove it from UNMODELLED`);
  }
});

test('the four pages from issue #4131 are checked, not skipped', () => {
  for (const page of ['documentManager/documentBrowser.jsp', 'casemgmt/noteBrowser.jsp',
    'admin/labforwardingrules.jsp', 'admin/jobs.jsp', 'demographic/demographicupdatearecord.jsp']) {
    const entry = `WEB-INF/jsp/${page}`;
    assert.ok(!UNMODELLED.has(entry), `${entry} must not be skipped`);
    assert.ok(inlineScripts(fs.readFileSync(path.join(WEBAPP, entry), 'utf8')).length > 0, `${entry} has no inline script`);
    assert.deepEqual(compileErrors(path.join(WEBAPP, entry)), []);
  }
});

test('the neutraliser still detects a newline inside a string literal', () => {
  // Guards the guard: the exact Document Browser defect must be reported.
  const broken = "popup(1, 2, '<%= request.getContextPath() %>/docume\nntManager/ViewEditDocument', 'EditDoc');";
  assert.throws(() => new vm.Script(neutralise(broken)), SyntaxError);
  const fixed = "popup(1, 2, '<%= request.getContextPath() %>/documentManager/ViewEditDocument?x=<carlos:encode value='${v}' context=\"javaScript\"/>', 'EditDoc');";
  assert.doesNotThrow(() => new vm.Script(neutralise(fixed)));
});

test('lab forwarding rules binds its provider handler to the select that exists', () => {
  const source = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/admin/labforwardingrules.jsp'), 'utf8');
  const ids = new Set([...source.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]));
  const script = inlineScripts(source).map(s => s.code).join('\n');
  const bound = [...script.matchAll(/\$\(\s*["']#([\w-]+)["']\s*\)\s*\.\s*(?:on|change)\s*\(/g)].map(match => match[1]);
  assert.ok(bound.includes('provider-selection'), 'The provider change handler is not bound to #provider-selection');
  for (const id of bound) assert.ok(ids.has(id), `The handler is bound to #${id}, which the page does not render`);
});

/** The full source of `function name(...) { ... }`, braces balanced. */
function functionSource(source, name, file) {
  const start = source.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `${file} no longer defines ${name}()`);
  let depth = 0;
  for (let i = source.indexOf('{', start); i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error(`Unbalanced ${name}() in ${file}`);
}

/** Runs the Jobs Management schedule helpers against a stub jQuery that knows each select's options. */
function jobsHelpers() {
  const source = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/admin/jobs.jsp'), 'utf8');
  const take = name => functionSource(source, name, 'jobs.jsp');
  const range = (from, to) => Array.from({ length: to - from + 1 }, (_, i) => String(from + i));
  const options = { minute: range(0, 59), hour: range(0, 23), day: range(1, 31), month: range(1, 12), weekday: range(0, 6) };
  const $ = selector => {
    const type = /^#(\w+) option$/.exec(selector)[1];
    return { map: fn => ({ get: () => options[type].map(value => fn.call({ value })) }) };
  };
  const context = { $ };
  vm.runInNewContext(`${take('firstJob')}\n${take('expandCronPart')}\n${take('parseCronExpression')}\n`
    + 'this.firstJob = firstJob; this.expandCronPart = expandCronPart; this.parseCronExpression = parseCronExpression;', context);
  return { context, options };
}

test('Jobs Management reads the stored job from the jobs array', () => {
  const { context } = jobsHelpers();
  assert.equal(context.firstJob({ jobs: [{ id: 5, cronExpression: '0 15,45 3 * * *' }] }).cronExpression, '0 15,45 3 * * *');
  assert.equal(context.firstJob({ jobs: { id: 5, cronExpression: '0 1 * * * *' } }).cronExpression, '0 1 * * * *');
  assert.equal(context.firstJob({}), null);
});

test('Jobs Management expands stored cron fields into exactly the values its selects show', () => {
  const { context } = jobsHelpers();
  // Copy out of the VM realm so deepEqual compares plain arrays.
  const expand = (value, type) => {
    const result = context.expandCronPart(value, type);
    return Array.isArray(result) ? [...result] : result;
  };
  assert.equal(expand('*', 'minute'), null);
  assert.equal(expand('?', 'day'), null);
  assert.deepEqual(expand('15,45', 'minute'), ['15', '45']);
  assert.deepEqual(expand('1-5', 'weekday'), ['1', '2', '3', '4', '5']);
  assert.deepEqual(expand('*/15', 'minute'), ['0', '15', '30', '45']);
  assert.deepEqual(expand('0-30/10', 'minute'), ['0', '10', '20', '30']);
  assert.deepEqual(expand('5/20', 'minute'), ['5', '25', '45']);
  // Spring's 7 is Sunday: 1-7 must keep Sunday, as option 0.
  assert.deepEqual(expand('1-7', 'weekday'), ['0', '1', '2', '3', '4', '5', '6']);
  assert.deepEqual(expand('7', 'weekday'), ['0']);
});

test('Jobs Management refuses cron fields it cannot represent exactly', () => {
  const { context } = jobsHelpers();
  // Each of these would be silently changed by a lossy parse (parseInt('1W') is 1).
  for (const [value, type] of [['MON-FRI', 'weekday'], ['JAN', 'month'], ['1W', 'day'], ['L', 'day'],
    ['5#2', 'weekday'], ['5L', 'weekday'], ['60', 'minute'], ['0', 'day'], ['13', 'month'], ['5-1', 'hour'],
    ['*/0', 'minute'], ['', 'minute'], ['1,', 'minute']]) {
    assert.equal(context.expandCronPart(value, type), undefined, `${type} "${value}" must be refused`);
  }
});

test('Jobs Management only restores six-field expressions it can show completely', () => {
  const { context } = jobsHelpers();
  const parsed = context.parseCronExpression('0 15,45 3 * * *');
  assert.deepEqual([...parsed.minute], ['15', '45']);
  assert.deepEqual([...parsed.hour], ['3']);
  assert.equal(parsed.day, null);
  assert.equal(parsed.month, null);
  assert.equal(parsed.weekday, null);
  for (const expression of ['0 5 * * *', '30 15 3 * * *', '0 0 9 * * MON-FRI', '0 0 9 1W * *', '0 0 9 * * * 2027']) {
    assert.equal(context.parseCronExpression(expression), null, `"${expression}" must not be restored`);
  }
});

test('Jobs Management refuses to save a schedule it could not restore', () => {
  const source = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/admin/jobs.jsp'), 'utf8');
  assert.match(source, /click: function \(\) \{\s*if \(!scheduleEditable\) \{\s*return;\s*\}/,
    'The schedule dialog Save must return early when the stored schedule is not editable');
  const schedule = functionSource(source, 'scheduleJob', 'jobs.jsp');
  assert.match(schedule, /if \(request !== scheduleRequestSeq\) \{\s*return;/, 'A superseded schedule response must be ignored');
  assert.match(schedule, /\$\('#scheduleJobId'\)\.val\(job\.id\)/, 'The dialog must be bound to the loaded job, not the click');
});

test('Jobs Management offers every cron minute and day of the month and no javascript: links', () => {
  const source = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/admin/jobs.jsp'), 'utf8');
  assert.match(source, /for \(int x = 0; x <= 59; x\+\+\)/, 'The minute picker must offer 0-59');
  assert.match(source, /for \(int x = 1; x <= 31; x\+\+\)/, 'The day picker must offer 1-31');
  assert.doesNotMatch(source, /javascript:void\(\)/, 'javascript:void() is a SyntaxError on every click');
  assert.doesNotMatch(source, /'\s*\+\s*job\.name\s*\+\s*'/, 'The job name must not be concatenated into HTML');
});

test('the document browser link carries a scope token, never the patient-named heading', () => {
  const report = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/documentManager/documentReport.jsp'), 'utf8');
  const browserLinks = report.match(/ViewDocumentBrowser\?.*/g) || [];
  assert.equal(browserLinks.length, 1);
  assert.match(browserLinks[0], /&categorykey=<%= currentScope %>/);
  assert.doesNotMatch(browserLinks[0], /currentkey/);
  const edit = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/demographic/edit.jsp'), 'utf8');
  assert.match(edit, /ViewDocumentBrowser\?[^']*&categorykey=private'/);
});

test('the note browser re-opens its GET-only gate with GET and keeps Print from submitting', () => {
  const source = fs.readFileSync(path.join(WEBAPP, 'WEB-INF/jsp/casemgmt/noteBrowser.jsp'), 'utf8');
  const take = name => functionSource(source, name, 'noteBrowser.jsp');
  const reload = take('reloadNoteBrowser');
  assert.match(reload, /window\.location\.href\s*=\s*'[^']*\/casemgmt\/ViewNoteBrowser\?'\s*\+\s*params\.toString\(\)/,
    'reloadNoteBrowser must navigate (GET) to ViewNoteBrowser with the built query');
  assert.deepEqual([...reload.matchAll(/params\.set\('([^']+)'/g)].map(match => match[1]),
    ['demographic_no', 'view', 'viewstatus', 'sortorder'], 'reloadNoteBrowser must carry only the filter fields');
  assert.doesNotMatch(reload, /submit\(|CSRF|method\s*=|\.post\(|fetch\(|XMLHttpRequest/,
    'reloadNoteBrowser must not POST or copy the CSRF token');
  for (const name of ['ReLoadDoc', 'LoadView']) {
    assert.doesNotMatch(take(name), /DisplayDoc\.submit\(\)/, `${name} must not POST the form to ViewNoteBrowser`);
    assert.match(take(name), /reloadNoteBrowser\(\)/);
  }
  assert.match(source, /onclick="PrintEncounter\(\); return false;"/, 'Print must not submit the DisplayDoc form');
});
