/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Issue #4715: opening a saved flowsheet reading shows it read-only with a Delete button. The
// page used to disable EVERY control of #measurementForm, including the hidden CSRF-TOKEN field
// CSRFGuard had put there, and a disabled control is not submitted, so Delete was refused with
// HTTP 403. This runs the production script block of AddMeasurementData.jsp against a stub form
// and pins which controls end up disabled. The browser-level proof is the
// `flowsheet-measurement-delete` Playwright check.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const JSP = process.env.ADD_MEASUREMENT_DATA_JSP || path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/encounter/oscarMeasurements/AddMeasurementData.jsp');
const source = fs.readFileSync(JSP, 'utf8');

/**
 * The script that runs only for a saved reading: the JavaScript between the
 * `<% if ( id != null ) { %>` scriptlet that opens the page's closing <script> block and the
 * scriptlet that closes it. It is the only such guarded block that starts with an IIFE.
 */
function readOnlyScript() {
  // The lazy match ends at the FIRST `<% } %>` after the IIFE, which is the scriptlet that closes the
  // `if ( id != null )` guard only while the IIFE itself holds no scriptlet. Fail loudly if one is
  // added, rather than run a truncated script or one with raw JSP tags in it.
  const match = /<%\s*if\s*\(\s*id\s*!=\s*null\s*\)\s*\{\s*%>\s*(\(function[\s\S]*?)<%\s*\}\s*%>/.exec(source);
  assert.ok(match, 'AddMeasurementData.jsp has no read-only script for a saved reading');
  assert.ok(!match[1].includes('<%'),
    'the read-only script must stay free of JSP scriptlets, or this test can no longer extract it whole');
  return match[1];
}

function control(tagName, type, name, id = '') {
  return { tagName: tagName.toUpperCase(), type, name, id, disabled: false };
}

/**
 * A form shaped like the rendered saved-reading page. `elements` is the live collection a
 * browser would give: listed controls in document order, fieldsets included.
 */
function renderedForm(extra = []) {
  const elements = [
    control('input', 'hidden', 'numType'),
    control('input', 'hidden', 'demographicNo'),
    control('input', 'hidden', 'template'),
    control('input', 'hidden', 'id'),
    control('input', 'hidden', 'deleteCheckbox', 'deleteCheck'),
    control('fieldset', 'fieldset', ''),
    control('input', 'radio', 'inputMInstrc-0'),
    control('input', 'text', 'date-0', 'prevDate0'),
    control('select', 'select-one', 'inputValue-0', 'inputValue-0'),
    control('fieldset', 'fieldset', ''),
    control('textarea', 'textarea', 'comments-0'),
    control('input', 'submit', 'delete', 'deleteButton'),
    ...extra,
  ];
  return { elements, byName: (name) => elements.filter((el) => el.name === name) };
}

function run(form, { withForm = true } = {}) {
  const document = {
    getElementById(id) {
      if (id === 'measurementForm') return withForm ? form : null;
      return form.elements.find((el) => el.id === id) || null;
    },
  };
  vm.runInNewContext(readOnlyScript(), { document, Array });
}

test('hidden inputs stay enabled so the CSRFGuard token and the delete identity are submitted', () => {
  // CSRFGuard adds CSRF-TOKEN to the form as soon as a script after the form has run, so it is
  // present when this block executes; it may also arrive later, which needs no handling.
  const form = renderedForm([control('input', 'hidden', 'CSRF-TOKEN')]);
  run(form);
  assert.equal(form.byName('CSRF-TOKEN')[0].disabled, false, 'CSRF-TOKEN must be submitted with Delete');
  for (const el of form.elements.filter((e) => e.type === 'hidden')) {
    assert.equal(el.disabled, false, `hidden input ${el.name} must stay enabled: a disabled control is not submitted`);
  }
});

test('the visible data controls are read-only', () => {
  const form = renderedForm();
  run(form);
  for (const name of ['inputMInstrc-0', 'date-0', 'inputValue-0', 'comments-0']) {
    assert.equal(form.byName(name)[0].disabled, true, `${name} must be disabled in the read-only view`);
  }
});

test('the Delete button stays enabled', () => {
  const form = renderedForm();
  run(form);
  assert.equal(form.byName('delete')[0].disabled, false);
});

test('fieldsets are not disabled, because that would disable every control nested in them', () => {
  const form = renderedForm();
  run(form);
  for (const el of form.elements.filter((e) => e.tagName === 'FIELDSET')) {
    assert.equal(el.disabled, false);
  }
});

test('a page without the form does not throw', () => {
  assert.doesNotThrow(() => run(renderedForm(), { withForm: false }));
});

test('the script is emitted only for a saved reading, and the page still posts the identity Delete needs', () => {
  assert.match(source, /<%\s*if\s*\(\s*id\s*!=\s*null\s*\)\s*\{\s*%>\s*\(function/);
  assert.match(source, /<form action="[^"]*"\s+id="measurementForm"\s+method="post">/);
  assert.match(source, /<input type="hidden" name="deleteCheckbox" id="deleteCheck"/);
  assert.match(source, /<input type="hidden" name="demographicNo"/);
});
