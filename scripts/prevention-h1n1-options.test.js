/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The H1N1 option checks (issue #5279 sections 1-5) name the form's categories and boxes. These tests hold those
 * names to AddPreventionData.jsp's h1n1 layout and to AddPrevention2Action, so an option added, renamed or no longer
 * stored fails here in CI instead of only on a packaged install. They also cover the pure helpers the checks share.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const {
  CHRONIC_CONDITIONS, FLAG_FIELDS, HEALTHCARE_WORKER_CATEGORIES, describeOptions, unrelatedChanges,
} = require('./lib/prevention-h1n1-options');
const { printedIncludes, requireId } = require('./lib/prevention-coverage');

const root = path.join(__dirname, '..');
const jsp = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/prevention/AddPreventionData.jsp'), 'utf8');
const action = fs.readFileSync(path.join(root, 'src/main/java/io/github/carlos_emr/carlos/prevention/pageUtil/AddPrevention2Action.java'), 'utf8');

/** The h1n1 layout's markup: from its branch to the next layout's. */
const h1n1 = jsp.slice(jsp.indexOf('layoutType.equals("h1n1")'), jsp.indexOf('layoutType.equals("PAPMAM")'));

/**
 * The value and visible text of each option of a select in the h1n1 layout. An option's selected test is a JSP
 * scriptlet whose `%>` must not be read as the end of the tag, so the tag is scanned rather than matched by a regex.
 */
function selectOptions(name) {
  const start = h1n1.indexOf(`<select name="${name}"`);
  assert.notEqual(start, -1, `the h1n1 layout has no ${name} select`);
  const body = h1n1.slice(start, h1n1.indexOf('</select>', start));
  const options = [];
  const open = '<option value="';
  for (let at = body.indexOf(open); at !== -1; at = body.indexOf(open, at)) {
    const valueEnd = body.indexOf('"', at + open.length);
    const value = body.slice(at + open.length, valueEnd);
    let tagEnd = valueEnd + 1;
    while (body[tagEnd] !== '>') {
      tagEnd = body.startsWith('<%', tagEnd) ? body.indexOf('%>', tagEnd) + 2 : tagEnd + 1;
      assert.ok(tagEnd > 0 && tagEnd < body.length, `an option of ${name} has no closing >`);
    }
    const close = body.indexOf('</option>', tagEnd);
    const text = body.slice(tagEnd + 1, close);
    options.push({
      value,
      label: text.includes('oscarprevention.addpreventiondata.yes') ? 'Yes' : text.replace(/\s+/g, ' ').trim(),
    });
    at = close;
  }
  return options;
}

test('the h1n1 layout exists and is the only one with the pandemic-flu Info box', () => {
  assert.ok(h1n1.length > 1000, 'the h1n1 layout was not found');
  assert.equal(jsp.split('select name="chronic"').length - 1, 1, 'the chronic select is no longer unique to the h1n1 layout');
});

for (const [name, expected] of [['chronic', CHRONIC_CONDITIONS], ['healthcareworker', HEALTHCARE_WORKER_CATEGORIES]]) {
  test(`the ${name} select offers exactly the categories its check walks through`, () => {
    assert.equal(describeOptions(selectOptions(name)), describeOptions(expected));
  });
}

for (const flag of FLAG_FIELDS) {
  test(`${flag} is a checkbox posting "true" and AddPrevention2Action stores it under its own name`, () => {
    const name = h1n1.indexOf(`name="${flag}"`);
    assert.notEqual(name, -1, `the h1n1 layout has no ${flag} input`);
    const tag = h1n1.slice(h1n1.lastIndexOf('<input', name), h1n1.indexOf('>', name));
    assert.ok(tag.includes('type="checkbox"'), `${flag} is not a checkbox`);
    assert.equal(h1n1.slice(name + `name="${flag}"`.length).trimStart().slice(0, 'value="true"'.length), 'value="true"',
      `${flag} does not post "true"`);
    assert.ok(action.includes(`addHashtoArray(extraData, request.getParameter("${flag}"), "${flag}");`), `${flag} is not stored`);
  });
}

for (const name of ['chronic', 'healthcareworker']) {
  test(`AddPrevention2Action stores the ${name} select under its own name`, () => {
    assert.ok(action.includes(`addHashtoArray(extraData, request.getParameter("${name}"), "${name}");`));
  });
}

test('printed text matches a phrase wherever the PDF wrapped it', () => {
  assert.ok(printedIncludes('Result: other (FAKE-\nPW12 sample\nunsuitable)', 'Result: other (FAKE-PW12 sample unsuitable)'));
  assert.ok(!printedIncludes('Result: other (no reason submitted)', 'Result: other (sample unsuitable)'));
});

test('record ids are positive integers', () => {
  assert.equal(requireId(42, 'x'), '42');
  for (const bad of ['0', '-1', '1 OR 1=1', '', null]) assert.throws(() => requireId(bad, 'The record'), /not a stored record id/);
});

test('unrelated details report every change and nothing when unchanged', () => {
  const forms = (ext) => ({ exts: () => ext });
  const details = { lot: 'L1', comments: 'C' };
  assert.deepEqual(unrelatedChanges(forms({ lot: 'L1', comments: 'C', pregnant: 'true' }), '7', details, { pregnant: 'true' }), []);
  const [change] = unrelatedChanges(forms({ lot: 'L2', comments: 'C' }), '7', details, { pregnant: 'true' });
  assert.match(change, /record 7: lot became L2, pregnant became \(none\)/);
});
