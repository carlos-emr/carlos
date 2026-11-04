/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/dco-check.yml'), 'utf8');
const step = workflow.split('      - name: Verify DCO sign-off on all commits\n')[1]
  .split('      - name: Set success status\n')[0];
const verifier = step.split('        run: |\n')[1].split('\n')
  .map(line => line.startsWith('          ') ? line.slice(10) : line).join('\n');
const base = 'f'.repeat(40);
const sha = number => number.toString(16).padStart(40, '0');
function commit(number, changes = {}) {
  return { sha: sha(number), parents: [{ sha: base }],
    author: { login: 'contributor', type: 'User' }, committer: { login: 'contributor' },
    commit: { message: 'fix: owned change ' + number + '\n\nSigned-off-by: Contributor <contributor@example.test>',
      verification: { verified: false, reason: 'unsigned' } }, ...changes };
}

// The real workflow shell runs against a read-only fake REST transport. Tests
// exercise pagination and account/signature metadata, not a second verifier.
function fakeCurl() {
  const fs = require('node:fs');
  const data = JSON.parse(fs.readFileSync(process.env.DCO_FIXTURE, 'utf8'));
  const args = process.argv.slice(2);
  if (args.includes('-X') || args.includes('--request')) throw new Error('Unexpected mutation');
  const url = new URL(args.at(-1));
  if (url.origin !== 'https://api.github.com') throw new Error('Unexpected origin');
  fs.appendFileSync(process.env.DCO_REQUESTS, url.href + '\n');
  if (url.pathname === '/repos/owner/repo/pulls/5291') {
    process.stdout.write(JSON.stringify({ commits: data.commits.length,
      base: { sha: data.base }, head: { sha: data.head } }));
  } else if (url.pathname === '/repos/owner/repo/compare/' + data.base + '...' + data.head) {
    const page = Number(url.searchParams.get('page'));
    if (url.searchParams.get('per_page') !== '100' || !Number.isInteger(page) || page < 1) {
      throw new Error('Unexpected pagination');
    }
    const visible = data.visible || data.commits;
    process.stdout.write(JSON.stringify({ total_commits: data.total === undefined ? data.commits.length : data.total,
      base_commit: { sha: data.comparisonBase || data.base },
      commits: visible.slice((page - 1) * 100, page * 100) }));
  } else throw new Error('Unexpected endpoint: ' + url.pathname);
}

function exercise(t, commits, options = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-dco-workflow-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const output = path.join(directory, 'output');
  const missing = path.join(directory, 'missing');
  const truncated = path.join(directory, 'truncated');
  const requests = path.join(directory, 'requests');
  const fixture = path.join(directory, 'fixture.json');
  const head = options.head || commits.at(-1).sha;
  fs.writeFileSync(fixture, JSON.stringify({ base, head, commits, ...options }));
  fs.writeFileSync(path.join(directory, 'curl'),
    '#!' + process.execPath + '\n(' + fakeCurl.toString() + ')();\n', { mode: 0o700 });
  fs.writeFileSync(output, '');
  const script = verifier.replaceAll('/tmp/missing_commits.txt', missing)
    .replaceAll('/tmp/dco_truncated.txt', truncated);
  const result = spawnSync('bash', ['-c', script], { encoding: 'utf8', timeout: 180000,
    env: { ...process.env, PATH: directory + path.delimiter + process.env.PATH,
      GH_TOKEN: 'unused-test-token', REPO: 'owner/repo', PR_NUMBER: '5291',
      HEAD_SHA: options.expectedHead || head, GITHUB_OUTPUT: output,
      DCO_FIXTURE: fixture, DCO_REQUESTS: requests } });
  assert.ifError(result.error);
  return { ...result, output: fs.readFileSync(output, 'utf8'),
    missing: fs.existsSync(missing) ? fs.readFileSync(missing, 'utf8') : '',
    truncated: fs.existsSync(truncated) ? fs.readFileSync(truncated, 'utf8') : '',
    requests: fs.existsSync(requests) ? fs.readFileSync(requests, 'utf8').trim().split('\n') : [] };
}

test('the actual DCO verifier audits all 471 commits through immutable paged comparisons', t => {
  const result = exercise(t, Array.from({ length: 471 }, (_, i) => commit(i + 1)));
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Checked 471 commit\(s\): 471 passed, 0 missing sign-off/);
  assert.match(result.output, /commits_missing=false/);
  assert.equal(result.missing, '');
  assert.equal(result.requests.length, 7); // PR snapshot, five populated pages, empty final page.
  assert(result.requests.slice(1).every(url => url.includes('/compare/' + base + '...' + sha(471) + '?')));
  assert(!result.requests.some(url => url.includes('/5291/commits')));
});

test('bot and merge exemptions still require verified account type and merge metadata', t => {
  const unsigned = message => ({ message, verification: { verified: false, reason: 'unsigned' } });
  const verifiedMerge = message => ({ message, verification: { verified: true, reason: 'valid' } });
  const commits = [commit(1),
    commit(2, { author: { login: 'github-actions[bot]', type: 'Bot' }, commit: unsigned('bot change') }),
    commit(3, { committer: { login: 'web-flow' }, parents: [{ sha: base }, { sha: sha(1) }],
      commit: verifiedMerge('Merge pull request #1 from owner/topic') }),
    commit(4, { author: { login: 'github-actions[bot]', type: 'User' }, commit: unsigned('spoofed bot') }),
    commit(5, { committer: { login: 'web-flow' }, parents: [{ sha: base }, { sha: sha(1) }],
      commit: unsigned('Merge pull request #2 from owner/unsigned') }),
    commit(6, { committer: { login: 'web-flow' },
      commit: verifiedMerge('Merge pull request #3 from owner/single-parent') }),
    commit(7, { author: { login: 'unknown[bot]', type: 'Bot' }, commit: unsigned('unapproved bot') })];
  const result = exercise(t, commits);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Checked 7 commit\(s\): 3 passed, 4 missing sign-off/);
  assert.match(result.output, /commits_missing=true/);
  for (const subject of ['spoofed bot', 'owner/unsigned', 'owner/single-parent', 'unapproved bot']) {
    assert(result.missing.includes(subject), subject);
  }
});

test('truncated pages cannot pass even when every returned commit is signed', t => {
  const commits = Array.from({ length: 251 }, (_, i) => commit(i + 1));
  const result = exercise(t, commits, { visible: commits.slice(0, 100) });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.output, /commits_missing=true/);
  assert.match(result.output, /dco_truncated=true/);
  assert.match(result.truncated, /251 commits; the comparison returned 100/);
  assert(!result.stdout.includes('All commits have valid DCO sign-off.'));
});

test('a repeated commit on another page cannot inflate complete coverage', t => {
  const commits = Array.from({ length: 102 }, (_, i) => commit(i + 1));
  const visible = [...commits]; visible[100] = commits[0];
  const result = exercise(t, commits, { visible });
  assert.notEqual(result.status, 0);
  assert.match(result.stdout, /invalid or repeated commit identity/);
  assert(!result.output.includes('commits_missing=false'));
});

test('a changed live PR head cannot certify the event revision', t => {
  const result = exercise(t, [commit(1)], { expectedHead: sha(999) });
  assert.notEqual(result.status, 0);
  assert.match(result.stdout, /PR head changed/);
  assert.equal(result.requests.length, 1);
  assert(!result.output.includes('commits_missing=false'));
});

test('the verifier refuses comparison total or base identity disagreement', t => {
  for (const options of [{ total: 2 }, { comparisonBase: sha(888) }]) {
    const result = exercise(t, [commit(1)], options);
    assert.notEqual(result.status, 0);
    assert.match(result.stdout, /comparison does not match/);
    assert(!result.output.includes('commits_missing=false'));
  }
});

test('matching totals without the expected head still cannot pass', t => {
  const result = exercise(t, [commit(1)], { head: sha(999) });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.output, /dco_truncated=true/);
  assert.match(result.truncated, /head coverage was false/);
  assert(!result.output.includes('commits_missing=false'));
});
