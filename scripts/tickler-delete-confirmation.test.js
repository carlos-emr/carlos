/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Task-list Delete asks before it submits (#5683, finding 337). Runs the real confirmDeleteSelected()
// from both task-list JSPs and pins the markup the browser checks select on.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '..');
const PAGES = {
  'ticklerMain.jsp': 'tickler.ticklerMain.msgConfirmDelete',
  'ticklerDemoMain.jsp': 'tickler.ticklerDemoMain.msgConfirmDelete',
};
const LOCALES = ['en', 'es', 'fr', 'pl', 'pt_BR'];
const jsp = name => fs.readFileSync(path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/tickler', name), 'utf8');

/** The page's own confirmDeleteSelected(), run against a fake form holding the given checkbox states. */
function confirmFunction(source, boxes, answer) {
  const start = source.indexOf('function confirmDeleteSelected()');
  assert.ok(start >= 0, 'the page must define confirmDeleteSelected()');
  // The function is brace-balanced and has no nested functions, so the first "\n            }" closes it.
  const end = source.indexOf('\n            }', start);
  assert.ok(end > start, 'confirmDeleteSelected() must end at its closing brace');
  const prompts = [];
  const elements = [{ name: 'submit_form', checked: false }, ...boxes.map(checked => ({ name: 'checkbox', checked }))];
  const context = {
    document: { ticklerform: { elements } },
    i18nConfirmDelete: 'Delete the selected tasks?',
    confirm: message => { prompts.push(message); return answer; },
  };
  vm.runInNewContext(`${source.slice(start, end + '\n            }'.length)}\nresult = confirmDeleteSelected();`, context);
  return { result: context.result, prompts };
}

for (const [page, key] of Object.entries(PAGES)) {
  test(`${page} Delete asks once when a task is ticked and a dismissed prompt blocks the submit`, () => {
    const dismissed = confirmFunction(jsp(page), [false, true, false], false);
    assert.deepEqual(dismissed.prompts, ['Delete the selected tasks?']);
    assert.equal(dismissed.result, false, 'a dismissed confirmation must stop the submit');
  });

  test(`${page} Delete proceeds once the confirmation is accepted`, () => {
    const accepted = confirmFunction(jsp(page), [true], true);
    assert.equal(accepted.prompts.length, 1);
    assert.equal(accepted.result, true);
  });

  test(`${page} Delete with nothing ticked raises no prompt, because the server only redirects back`, () => {
    const none = confirmFunction(jsp(page), [false, false], false);
    assert.deepEqual(none.prompts, []);
    assert.equal(none.result, true);
  });

  test(`${page} wires the guard into Delete only and keeps the selectors the browser checks use`, () => {
    const source = jsp(page);
    // Match the onclick attribute alone: the tags' value="<fmt:message .../>" contains ">" characters.
    const handlers = source.match(/onclick="[^"]*submit_form\.value='(?:Delete|Complete)'[^"]*"/g) || [];
    const del = handlers.filter(handler => handler.includes("submit_form.value='Delete'"));
    const complete = handlers.filter(handler => handler.includes("submit_form.value='Complete'"));
    assert.equal(del.length, 1, 'exactly one Delete control');
    assert.equal(complete.length, 1, 'exactly one Complete control');
    const onclick = del[0].slice('onclick="'.length, -1);
    assert.ok(onclick.startsWith('if (!confirmDeleteSelected()) return false;'), 'the guard must run before anything is submitted');
    assert.ok(onclick.indexOf('confirmDeleteSelected()') < onclick.indexOf("submit_form.value='Delete'"),
      'the confirmation must precede setting submit_form');
    assert.ok(onclick.endsWith("document.forms['ticklerform'].submit();"), 'Delete must still submit the form');
    assert.ok(!complete[0].includes('confirm'), 'Complete must not ask');
  });

  test(`${page} reads its prompt text from ${key} through the JavaScript-block encoder`, () => {
    const source = jsp(page);
    assert.ok(source.includes(`<fmt:message key="${key}" var="msgConfirmDelete"/>`));
    assert.match(source, /i18nConfirmDelete = '<carlos:encode value='<%= \(String\) pageContext\.getAttribute\("msgConfirmDelete"\) %>' context="javaScriptBlock"\/>';/);
  });

  for (const locale of LOCALES) {
    test(`oscarResources_${locale} defines ${key} as non-empty ASCII with unicode escapes`, () => {
      const text = fs.readFileSync(path.join(ROOT, `src/main/resources/oscarResources_${locale}.properties`), 'utf8');
      const line = text.split(/\r?\n/).find(row => row.startsWith(`${key}=`));
      assert.ok(line, `${locale} lacks ${key}`);
      const value = line.slice(key.length + 1);
      assert.ok(value.trim().length > 10, `${locale} has an empty ${key}`);
      assert.ok(!/[^\x00-\x7f]/.test(value), `${locale} ${key} has a raw non-ASCII byte; use a \\u escape`);
      assert.ok(!/['"{}]/.test(value), `${locale} ${key} has a quote or brace that MessageFormat or the JS string would mishandle`);
    });
  }
}

// A browser check that clicks the task list's Delete without answering its prompt would have the strict harness
// dismiss the dialog, so the click changes nothing and the step times out (this is how tickler-crud was first missed).
// Any check that drives the task-list form and selects a Delete control must go through the shared helper.
test('every browser check that clicks the task-list Delete control answers the prompt through the shared helper', () => {
  const scriptsDir = __dirname;
  const DRIVES_LIST = /tickler/i;
  const SELECTS_DELETE = /input\.btn-danger|submit_form\.value='(?:Delete|\$\{)|\[onclick\*="Delete"\]/;
  const offenders = [];
  const covered = [];
  for (const name of fs.readdirSync(scriptsDir).filter(file => /-playwright-checks\.js$/.test(file))) {
    const source = fs.readFileSync(path.join(scriptsDir, name), 'utf8');
    if (!DRIVES_LIST.test(source) || !SELECTS_DELETE.test(source)) continue;
    (source.includes('withTicklerConfirmations') ? covered : offenders).push(name);
  }
  assert.deepEqual(offenders, [], `these checks click task-list Delete without withTicklerConfirmations: ${offenders.join(', ')}`);
  assert.deepEqual(covered.sort(), [
    'concurrency-tickler-list-status-playwright-checks.js',
    'tickler-bulk-actions-playwright-checks.js',
    'tickler-crud-playwright-checks.js',
    'tickler-demo-main-status-playwright-checks.js',
    'tickler-main-list-lifecycle-playwright-checks.js',
    'tickler-paged-selection-playwright-checks.js',
  ].sort(), 'the set of checks driving the task-list Delete changed; confirm each one answers the prompt, then update this list');
});
