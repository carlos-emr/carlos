/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
// Issue #3944: the eForm renderers injected the full Bootstrap stylesheet into every eForm, and its
// reboot rules (body margin and font, box-sizing on every element, table and heading margins) moved
// legacy, absolutely positioned eForm fields on screen and in print. Only the CARLOS chrome (the
// floating toolbar, the save alert and the attachment picker) uses Bootstrap, so it now receives a
// copy whose every rule is confined to elements inside the .carlos-eform-ui scope.
//
// These tests pin the scoping transform, keep the committed stylesheet in step with the vendored
// Bootstrap it is generated from, and pin the renderer contracts that keep the unscoped copy out.
// The browser-level proof (eForm geometry unchanged, chrome still styled) is
// scripts/eform-bootstrap-isolation-playwright-checks.js.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const { scopeStylesheet, splitSelectorList, styleRuleSelectors } = require('./lib/css-scope');
const generator = require('./build-eform-scoped-bootstrap');

const ROOT = path.join(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(ROOT, relative), 'utf8');
const SCOPE = '.carlos-eform-ui';

test('the scope class and the output path are the ones the renderers and scripts use', () => {
  assert.equal(generator.SCOPE_CLASS, 'carlos-eform-ui');
  assert.equal(generator.OUTPUT, 'src/main/webapp/eform/eformFloatingToolbar/bootstrap-eform-scoped.min.css');
  assert.equal(generator.SOURCE, 'src/main/webapp/library/bootstrap/5.3.8/css/bootstrap.min.css');
});

test('splitSelectorList splits only top-level commas', () => {
  assert.deepEqual(splitSelectorList('a,b'), ['a', 'b']);
  assert.deepEqual(
    splitSelectorList('.x:not(.a,.b) > p, [title="a,b"],:is(i, b)'),
    ['.x:not(.a,.b) > p', '[title="a,b"]', ':is(i, b)'],
  );
  assert.deepEqual(splitSelectorList('.a\\,b,.c'), ['.a\\,b', '.c']);
});

test('ordinary selectors become descendants of the scope', () => {
  assert.equal(scopeStylesheet('.btn{color:red}', 'scope'), '.scope .btn{color:red}');
  assert.equal(
    scopeStylesheet('*,::after,::before{box-sizing:border-box}', 'scope'),
    '.scope *,.scope ::after,.scope ::before{box-sizing:border-box}',
  );
  assert.equal(
    scopeStylesheet('.input-group>:not(:first-child):not(.dropdown-menu){margin-left:0}', 'scope'),
    '.scope .input-group>:not(:first-child):not(.dropdown-menu){margin-left:0}',
  );
});

test('document-root selectors are mapped onto the scope element itself', () => {
  // The custom properties Bootstrap defines on :root must exist on the scope, or every component
  // that reads var(--bs-*) loses its colours; the body reset must style the scope, not the eForm.
  assert.equal(
    scopeStylesheet(':root,[data-bs-theme=light]{--bs-blue:#0d6efd}', 'scope'),
    '.scope,.scope [data-bs-theme=light]{--bs-blue:#0d6efd}',
  );
  assert.equal(scopeStylesheet('body{margin:0}', 'scope'), '.scope{margin:0}');
  assert.equal(scopeStylesheet('html{font-size:16px}', 'scope'), '.scope{font-size:16px}');
  assert.equal(scopeStylesheet(':root[data-x] .a{color:red}', 'scope'), '.scope[data-x] .a{color:red}');
  // A selector that merely starts with the same letters is not a root selector.
  assert.equal(scopeStylesheet('bodyish{color:red}', 'scope'), '.scope bodyish{color:red}');
  assert.equal(scopeStylesheet('.table>tbody{color:red}', 'scope'), '.scope .table>tbody{color:red}');
});

test('grouping at-rules are scoped inside; keyframes, font faces and charset are kept as written', () => {
  assert.equal(
    scopeStylesheet('@media print{.d-print-none{display:none!important}}', 'scope'),
    '@media print{.scope .d-print-none{display:none!important}}',
  );
  assert.equal(
    scopeStylesheet('@supports (position:sticky){.sticky-top{top:0}}', 'scope'),
    '@supports (position:sticky){.scope .sticky-top{top:0}}',
  );
  assert.equal(
    scopeStylesheet('@media (min-width:576px){@supports (x:y){:root{--a:1}}}', 'scope'),
    '@media (min-width:576px){@supports (x:y){.scope{--a:1}}}',
  );
  const keyframes = '@keyframes spinner-border{to{transform:rotate(360deg)}}';
  assert.equal(scopeStylesheet(keyframes, 'scope'), keyframes);
  assert.equal(scopeStylesheet('@charset "UTF-8";.a{b:c}', 'scope'), '@charset "UTF-8";.scope .a{b:c}');
});

test('comments, strings and url() values pass through untouched', () => {
  const css = '/*! licence { } */.a::before{content:"} {";background:url("data:x,%3csvg{}")}';
  assert.equal(scopeStylesheet(css, 'scope'),
    '/*! licence { } */.scope .a::before{content:"} {";background:url("data:x,%3csvg{}")}');
});

test('constructs the transform cannot scope safely are refused rather than emitted unscoped', () => {
  assert.throws(() => scopeStylesheet('.a{.b{color:red}}', 'scope'), /nested/i);
  assert.throws(() => scopeStylesheet('>.a{color:red}', 'scope'), /combinator/i);
  assert.throws(() => scopeStylesheet('.a{color:red', 'scope'), /unterminated/i);
  assert.throws(() => scopeStylesheet('.a{}', 'not a class'), /scope/i);
  // An imported stylesheet would arrive unscoped, and @page sets the whole document's print margins.
  assert.throws(() => scopeStylesheet('@import url(other.css);.a{b:c}', 'scope'), /@import .*cannot be scoped/);
  assert.throws(() => scopeStylesheet('@media print{@page{margin:0}}', 'scope'), /@page .*cannot be scoped/);
});

test('the committed scoped stylesheet is exactly what the generator produces', () => {
  assert.equal(read(generator.OUTPUT), generator.build(read(generator.SOURCE)),
    'regenerate with: node scripts/build-eform-scoped-bootstrap.js');
});

test('every style rule in the committed stylesheet is confined to the scope', () => {
  const selectors = styleRuleSelectors(read(generator.OUTPUT));
  assert.ok(selectors.length > 1000, `expected the whole of Bootstrap, found ${selectors.length} selectors`);
  const escaped = selectors.filter((selector) => selector !== SCOPE
    && !selector.startsWith(SCOPE + ' ')
    && !selector.startsWith(SCOPE + '['));
  assert.deepEqual(escaped, [], 'these selectors can match eForm content');
});

test('the scoped stylesheet keeps Bootstrap\'s licence notice and leads with its charset', () => {
  const css = read(generator.OUTPUT);
  assert.ok(css.startsWith('@charset "UTF-8";'), 'a @charset that is not first is ignored by browsers');
  assert.match(css, /Bootstrap {2}v5\.3\.8/);
  assert.match(css, /Licensed under MIT/);
  assert.match(css, /generated by scripts\/build-eform-scoped-bootstrap\.js/i);
});

/** The asset paths a renderer adds, in call order, for one EForm method. */
function injected(source, method) {
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- method is one of this file's EForm method-name literals
  const pattern = new RegExp(`\\.${method}\\(\\s*request\\.getContextPath\\(\\)\\s*\\+\\s*"([^"]+)"`, 'g');
  return [...source.matchAll(pattern)].map((match) => match[1]);
}

for (const jsp of ['efmshowform_data.jsp', 'efmformadd_data.jsp']) {
  test(`${jsp} gives the eForm the scoped Bootstrap, never the global stylesheet`, () => {
    const source = read(`src/main/webapp/WEB-INF/jsp/eform/${jsp}`);
    const css = injected(source, 'addCSS');
    assert.ok(!css.some((href) => href.includes('/library/bootstrap/')),
      `${jsp} still injects a global Bootstrap stylesheet: ${css.join(', ')}`);
    assert.ok(css.includes('/eform/eformFloatingToolbar/bootstrap-eform-scoped.min.css'), css.join(', '));
  });

  test(`${jsp} loads the Bootstrap script bundle exactly once`, () => {
    // Two copies register Bootstrap's data-api click handlers twice, so a collapse or dropdown a
    // form wires with data-bs-toggle opens and immediately closes again.
    const scripts = injected(read(`src/main/webapp/WEB-INF/jsp/eform/${jsp}`), 'addHeadJavascript')
      .filter((src) => src.endsWith('/bootstrap.bundle.min.js'));
    assert.equal(scripts.length, 1, scripts.join(', '));
  });
}

test('the PDF renderer does not restyle the eForm with Bootstrap either', () => {
  // The saved PDF must match the screen. The render surface has no toolbar or alert, so it needs
  // no Bootstrap CSS at all; the script bundle stays for form code that calls it.
  const composer = read('src/main/java/io/github/carlos_emr/carlos/eform/util/EFormRenderPdfHtmlComposer.java');
  assert.doesNotMatch(composer, /bootstrap[^"]*\.css/);
  assert.match(composer, /bootstrap\.bundle\.min\.js/);
});

test('every Bootstrap consumer on the eForm page sits inside the scope', () => {
  const toolbar = read('src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js');
  // The toolbar fragment is loaded into this wrapper.
  assert.match(toolbar, /toolbarWrapper\.setAttribute\("class", "[^"]*\bcarlos-eform-ui\b[^"]*"\)/);
  // jQuery UI would move the attachment picker out of the toolbar to <body>; it is mounted in a
  // scoped host instead, because its show/hide logic depends on Bootstrap's .d-none.
  assert.match(toolbar, /\.dialog\(\{[^}]*appendTo: eformDialogHost\(\)/);
  assert.match(toolbar, /host\.className = "carlos-eform-ui carlos-eform-dialog-host";/);
  const toolbarCss = read('src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar_custom.css');
  assert.match(toolbarCss, /\.carlos-eform-dialog-host \{ display: contents; \}/);
});

/** Loads oscar-alert.js into a bare context, with the DOM-dependent entry point replaced. */
function loadAlertScript() {
  const calls = [];
  const context = vm.createContext({});
  vm.runInContext(read('src/main/webapp/js/oscar-alert.js'), context, { filename: 'oscar-alert.js' });
  context.createAndShowAlert = (...args) => calls.push(args);
  return { context, calls };
}

test('the alert helpers work whatever this is bound to', () => {
  // They used to call this.createAndShowAlert, which works only for a bare call in sloppy mode,
  // where this is window. Used as an event handler or method it threw instead of showing the alert.
  const { context, calls } = loadAlertScript();
  const receiver = { unrelated: true };
  const dismiss = () => {};
  context.showErrorAlert.call(receiver);
  context.showSuccessAlert.call(receiver, dismiss);
  context.showWarningAlert.call(receiver, 'Check the fax number', dismiss);
  context.showWarningAlert.call(undefined, 'Strict-mode caller', undefined);
  assert.deepEqual(calls.map((args) => [args[0], args[2]]), [
    ['submit-error-alert', 'danger'],
    ['submit-success-alert', 'success'],
    ['submit-warning-alert', 'warning'],
    ['submit-warning-alert', 'warning'],
  ]);
  assert.equal(calls[1][4], dismiss);
  assert.equal(calls[2][1], 'Check the fax number');
});

test('the alert is mounted inside its own scope host', () => {
  const source = read('src/main/webapp/js/oscar-alert.js');
  assert.doesNotMatch(source, /this\.createAndShowAlert\s*\(/);
  assert.match(source, /carlos-eform-ui/);
});
