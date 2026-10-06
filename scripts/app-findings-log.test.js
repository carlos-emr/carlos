/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * Keeps docs/ui-tests/app-findings-log.md honest and current.
 *
 * WHY THIS IS A TEST AND NOT A CONVENTION. The findings log only has value if it
 * is complete: the moment a defect is tolerated somewhere in the suite without
 * being written down, the suite is quietly blind to a class of regression and
 * nobody can tell which. The console baseline is exactly that tolerance
 * mechanism, so every baseline entry has to cite either a GitHub issue or a
 * finding that actually exists in the log. Left to discipline this lapses; as a
 * test it cannot.
 */

const LOG_PATH = path.join(__dirname, '..', 'docs', 'ui-tests', 'app-findings-log.md');
const LOG = fs.readFileSync(LOG_PATH, 'utf8');
const BASELINE = JSON.parse(fs.readFileSync(path.join(__dirname, 'lib', 'console-baseline.json'), 'utf8'));

const VALID_STATUSES = ['open', 'issue-filed', 'fixed', 'needs-live-check'];

/** Finding rows are table rows whose first cell is a number. */
function findingRows() {
  return LOG.split('\n')
    .filter((line) => /^\|\s*\d+\s*\|/.test(line))
    .map((line) => {
      const cells = line.split('|').slice(1, -1).map((cell) => cell.trim());
      return { id: Number(cells[0]), cells, line };
    });
}

test('the findings log exists and records at least one finding', () => {
  const rows = findingRows();
  assert.ok(rows.length > 0, 'the log must record the findings turned up while building coverage');
});

test('finding ids are unique and consecutive, so a finding can be cited by number', () => {
  const ids = findingRows().map((row) => row.id);
  assert.deepEqual(ids, [...ids].sort((a, b) => a - b), 'findings must be listed in id order');
  assert.equal(new Set(ids).size, ids.length, 'two findings share an id, so a citation would be ambiguous');
  ids.forEach((id, index) => {
    assert.equal(id, index + 1, `finding ids must be consecutive from 1; found ${id} at position ${index + 1}`);
  });
});

test('every finding carries evidence and a status from the allowed set', () => {
  for (const row of findingRows()) {
    const status = row.cells[row.cells.length - 1].replace(/`/g, '').trim();
    assert.ok(VALID_STATUSES.includes(status),
      `finding ${row.id} has status "${status}"; expected one of ${VALID_STATUSES.join(', ')}`);
    // Second-to-last cell is the evidence / where column. An unevidenced claim
    // on this list is exactly what section 3 of the log exists to prevent.
    const evidence = row.cells[row.cells.length - 2];
    assert.ok(evidence && evidence.length > 20,
      `finding ${row.id} must record how it was verified, so a reader can re-check it`);
  }
});

test('a console-baseline entry can only cite a finding that is actually recorded', () => {
  // This is the point of the whole file: the suite may not tolerate a defect
  // that nobody wrote down.
  const ids = new Set(findingRows().map((row) => row.id));
  for (const entry of BASELINE.allow) {
    const citesLog = entry.issue.includes('app-findings-log.md');
    const citesIssue = /#\d+/.test(entry.issue);
    const citesDoc = /docs\/[\w/-]+\.md/.test(entry.issue);
    assert.ok(citesLog || citesIssue || citesDoc,
      `console-baseline entry "${entry.match}" must cite a GitHub issue or a findings-log entry`);
    if (citesLog) {
      const found = entry.issue.match(/finding (\d+)/);
      assert.ok(found, `"${entry.match}" cites the findings log but names no finding number`);
      assert.ok(ids.has(Number(found[1])),
        `"${entry.match}" cites findings-log finding ${found[1]}, which is not recorded there`);
    }
  }
});

test('any docs path a baseline entry cites actually exists', () => {
  for (const entry of BASELINE.allow) {
    const docPath = entry.issue.match(/(docs\/[\w/-]+\.md)/);
    if (!docPath) {
      continue;
    }
    assert.ok(fs.existsSync(path.join(__dirname, '..', docPath[1])),
      `console-baseline entry "${entry.match}" cites ${docPath[1]}, which does not exist`);
  }
});

test('the log keeps its record of candidates that were NOT defects', () => {
  // Recording the cleared candidates is what stops the same false lead being
  // re-investigated -- one of them was a bug in my own search, not the app's.
  assert.match(LOG, /Investigated and \*\*not\*\* defects/,
    'the log must keep the section recording what was checked and cleared');
});

test('the log states the rule that a finding never weakens a check', () => {
  assert.match(LOG, /report, don't encode/i,
    'the log must carry the rule: a check that pins current broken behaviour as expected makes the bug permanent');
});
