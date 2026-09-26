/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const check = require('./tickler-validation-messages-playwright-checks');

const JSP_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'tickler');
const PAGES = ['ticklerAdd.jsp', 'ticklerEdit.jsp'].map((name) => ({
  name, source: fs.readFileSync(path.join(JSP_DIR, name), 'utf8'),
}));
const BUNDLE = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties',
), 'utf8');

/** The JSP's JavaScript with its comments removed, so prose about the old pattern is not the pattern. */
function code(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

/*
 * Issue #3957: the tickler add and edit pages appended every validation message
 * to #error and never cleared it, so messages piled up on each submit and stayed
 * after the field was fixed. These tests pin the source-level half of the fix
 * on every pull request, where no browser runs; the Playwright check proves the
 * rendered behaviour against a deployment.
 */

for (const page of PAGES) {
  test(`${page.name} no longer appends validation text to the #error alert`, () => {
    // insertAdjacentText("beforeend") on #error is the defect itself: each
    // failed submit added another copy of the same message.
    assert.doesNotMatch(code(page.source), /getElementById\("error"\)\.insertAdjacentText/,
      `${page.name} still appends to #error instead of replacing its content`);
  });

  test(`${page.name} empties the alert before every validation pass`, () => {
    const js = code(page.source);
    assert.match(js, /function resetValidationMessages\(\)[\s\S]*?errorDiv\.textContent = "";[\s\S]*?errorDiv\.style\.display = "none";/,
      `${page.name} has no reset that empties and hides #error`);
    // validate() is the only entry point the Save/Update buttons call, and the
    // reset has to be its first act so a retry starts from an empty alert.
    const validateBody = js.match(/function validate\([^)]*\)\s*\{([\s\S]*?)\n\s*if \(/);
    assert.ok(validateBody, `${page.name} validate() was not found`);
    assert.match(validateBody[1], /resetValidationMessages\(\);/,
      `${page.name} validate() does not reset the alert before validating`);
  });

  test(`${page.name} renders each message as its own line through textContent`, () => {
    const js = code(page.source);
    // One element per message, filled through textContent: the bundle text is
    // never parsed as markup, and the browser check can count lines.
    assert.match(js, /function showValidationMessage\(message\)[\s\S]*?createElement\("div"\)[\s\S]*?className = "tickler-validation-message"[\s\S]*?line\.textContent = message;[\s\S]*?appendChild\(line\)/,
      `${page.name} does not render one textContent line per message`);
    // Every validator reports through the shared helper, and every message it
    // reports is bundle text passed through the null-safe CARLOS encoder for a
    // JavaScript string context, never a raw literal.
    const calls = [...js.matchAll(/showValidationMessage\(([^\n]*)\);/g)];
    assert.ok(calls.length >= 2, `${page.name} reports fewer validation messages than expected`);
    for (const [, argument] of calls) {
      assert.match(argument,
        /^'<carlos:encode value='<%= oscarBundle\.getString\("tickler\.ticklerAdd\.[A-Za-z]+"\) %>' context="javaScriptBlock"\/>'$/,
        `${page.name} passes a validation message that is not encoded bundle text: ${argument}`);
    }
  });

  test(`${page.name} evaluates every validator instead of stopping at the first failure`, () => {
    // A short-circuit && revealed the problems one retry at a time; with the
    // alert reset per pass, reporting them together is what "each message on
    // its own line" is for.
    assert.doesNotMatch(code(page.source), /if \(validate(?:DemoNo|Date)\([^)]*\)\s*<%=?\s*caisiEnabled/,
      `${page.name} still short-circuits the program validator behind the first one`);
  });
}

test('the browser check asserts the bundle text the pages actually render', () => {
  // The message the check compares each line against must be a real bundle
  // value, and the one the two pages emit for a missing service date.
  const line = BUNDLE.split('\n').find((candidate) => candidate.startsWith(`${check.MISSING_DATE_KEY}=`));
  assert.ok(line, `${check.MISSING_DATE_KEY} is not in oscarResources_en.properties`);
  assert.equal(check.bundleMessage(check.MISSING_DATE_KEY, 'fallback'), line.slice(check.MISSING_DATE_KEY.length + 1).trim());
  for (const page of PAGES) {
    assert.ok(page.source.includes(`oscarBundle.getString("${check.MISSING_DATE_KEY}")`),
      `${page.name} does not render ${check.MISSING_DATE_KEY}`);
  }
});

test('the browser check counts the line elements the fix renders, not substrings', () => {
  // If the class name in the JSP and the selector in the check drift apart, the
  // check would see zero lines on a page that is showing a message.
  assert.equal(check.SELECTORS.messageLine, '#error .tickler-validation-message');
  for (const page of PAGES) {
    assert.ok(page.source.includes('className = "tickler-validation-message"'),
      `${page.name} does not render the line class the check selects`);
    assert.ok(page.source.includes('id="error"'), `${page.name} has no #error alert`);
  }
});

test('the browser check drives the controls the pages render', () => {
  const [add, edit] = PAGES;
  assert.ok(add.source.includes('name="xml_appointment_date"') && edit.source.includes('name="xml_appointment_date"'));
  assert.ok(add.source.includes('name="Button" class="btn btn-primary"'), 'ticklerAdd.jsp Save button selector drifted');
  assert.ok(edit.source.includes('name="updateTickler"'), 'ticklerEdit.jsp Update button selector drifted');
  assert.equal(check.SELECTORS.addSave, 'input.btn-primary[name="Button"]');
  assert.equal(check.SELECTORS.editSave, 'input[name="updateTickler"]');
  // The success sentinels the hidden iframes render, which the check waits on
  // before asserting the alert cleared.
  const dbAdd = fs.readFileSync(path.join(JSP_DIR, 'dbTicklerAdd.jsp'), 'utf8');
  const editOk = fs.readFileSync(path.join(JSP_DIR, 'ticklerEditSuccess.jsp'), 'utf8');
  assert.ok(dbAdd.includes('id="tickler-save-ok"'));
  assert.ok(editOk.includes('tickler-edit-ok'));
  assert.equal(check.SELECTORS.addSavedMarker, 'ticklerSubmitFrame');
  assert.equal(check.SELECTORS.editSavedMarker, 'ticklerEditFrame');
});
