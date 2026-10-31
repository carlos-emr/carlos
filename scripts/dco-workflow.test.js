/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/dco-check.yml'), 'utf8');
// The text of one workflow step's `run: |` block, de-indented as the runner would.
function stepScript(name, nextName) {
  const step = workflow.split('      - name: ' + name + '\n')[1];
  const body = (nextName ? step.split('      - name: ' + nextName + '\n')[0] : step);
  return body.split('        run: |\n')[1].split('\n')
    .map(line => line.startsWith('          ') ? line.slice(10) : line).join('\n');
}
const verifier = stepScript('Verify DCO sign-off on all commits', 'Set success status');
const successStatus = stepScript('Set success status', 'Check for manual DCO confirmation comment');
const reporter = stepScript('Report result');
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
  const exempt = path.join(directory, 'exempt');
  const summary = path.join(directory, 'summary');
  const fixture = path.join(directory, 'fixture.json');
  const head = options.head || commits.at(-1).sha;
  fs.writeFileSync(fixture, JSON.stringify({ base, head, commits, ...options }));
  fs.writeFileSync(path.join(directory, 'curl'),
    '#!' + process.execPath + '\n(' + fakeCurl.toString() + ')();\n', { mode: 0o700 });
  fs.writeFileSync(output, '');
  const script = verifier.replaceAll('/tmp/missing_commits.txt', missing)
    .replaceAll('/tmp/dco_truncated.txt', truncated)
    .replaceAll('/tmp/exempt_commits.txt', exempt);
  const result = spawnSync('bash', ['-c', script], { encoding: 'utf8', timeout: 180000,
    env: { ...process.env, PATH: directory + path.delimiter + process.env.PATH,
      GH_TOKEN: 'unused-test-token', REPO: 'owner/repo', PR_NUMBER: '5291',
      HEAD_SHA: options.expectedHead || head, GITHUB_OUTPUT: output, GITHUB_STEP_SUMMARY: summary,
      DCO_FIXTURE: fixture, DCO_REQUESTS: requests } });
  assert.ifError(result.error);
  return { ...result, output: fs.readFileSync(output, 'utf8'),
    exempt: fs.existsSync(exempt) ? fs.readFileSync(exempt, 'utf8') : '',
    summary: fs.existsSync(summary) ? fs.readFileSync(summary, 'utf8') : '',
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

// ---------------------------------------------------------------------------
// Verified GitHub merge exemptions (issue 5283). The eight merge commits that
// were once mistaken for DCO failures are replayed as sanitized API fixtures
// through the real workflow shell.
// ---------------------------------------------------------------------------
const mergeFixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, 'fixtures/dco/github-merge-commits.json'), 'utf8')).commits;
const githubMerge = (number, subject, changes = {}) => commit(number, {
  committer: { login: 'web-flow' }, parents: [{ sha: base }, { sha: sha(1) }],
  commit: { message: subject, verification: { verified: true, reason: 'valid' } }, ...changes });
const unsignedCommit = number => commit(number, {
  commit: { message: 'fix: forgot to sign ' + number,
    verification: { verified: false, reason: 'unsigned' } } });

// The reporting steps run against a recording transport that serves an empty
// comment history and captures every write the step would make.
function fakeRecordingCurl() {
  const fs = require('node:fs');
  const args = process.argv.slice(2);
  const url = args.at(-1);
  const method = args.includes('-X') ? args[args.indexOf('-X') + 1] : 'GET';
  const body = args.includes('-d') ? fs.readFileSync(0, 'utf8') : '';
  fs.appendFileSync(process.env.DCO_WRITES, JSON.stringify({ method, url, body }) + '\n');
  if (method === 'GET') process.stdout.write('[]');
}

function runStep(t, script, files, env) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-dco-step-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const writes = path.join(directory, 'writes');
  fs.writeFileSync(path.join(directory, 'curl'),
    '#!' + process.execPath + '\n(' + fakeRecordingCurl.toString() + ')();\n', { mode: 0o700 });
  fs.writeFileSync(writes, '');
  let rewritten = script;
  for (const [name, content] of Object.entries(files)) {
    const local = path.join(directory, path.basename(name));
    fs.writeFileSync(local, content);
    rewritten = rewritten.replaceAll(name, local);
  }
  for (const name of ['/tmp/dco_failure_comment.md', '/tmp/dco_success_comment.md']) {
    rewritten = rewritten.replaceAll(name, path.join(directory, path.basename(name)));
  }
  const result = spawnSync('bash', ['-c', rewritten], { encoding: 'utf8', timeout: 60000,
    env: { ...process.env, PATH: directory + path.delimiter + process.env.PATH,
      GH_TOKEN: 'unused-test-token', REPO: 'owner/repo', PR_NUMBER: '5291',
      GITHUB_SERVER_URL: 'https://github.test', GITHUB_RUN_ID: '1',
      DCO_WRITES: writes, ...env } });
  assert.ifError(result.error);
  const recorded = fs.readFileSync(writes, 'utf8').trim().split('\n').filter(Boolean).map(line => JSON.parse(line));
  return { ...result, writes: recorded.filter(entry => entry.method !== 'GET') };
}

test('the workflow stays API-only: no checkout and no action runs PR code', () => {
  assert(!/^\s*-?\s*uses:/m.test(workflow), 'dco-check.yml must not use any action, including checkout');
  assert(!workflow.includes('actions/checkout'));
  assert(workflow.includes('pull_request_target'));
});

for (const [pr, label] of [[2721, '#2721'], [2938, '#2938'], [2976, '#2976'], [3179, '#3179']]) {
  test('verified GitHub merge commits of ' + label + ' pass without a manual confirmation', t => {
    const commits = mergeFixture.filter(entry => entry.pr === pr).map(({ pr: _pr, ...entry }) => entry);
    assert(commits.length > 0);
    const result = exercise(t, commits);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.output, /commits_missing=false/);
    assert.match(result.stdout, new RegExp('DCO-exempt verified GitHub-generated merge commits \\(' + commits.length + '\\)'));
    assert.match(result.stdout, new RegExp('Checked ' + commits.length + ' commit\\(s\\): ' + commits.length + ' passed, 0 missing'));
    assert.equal(result.missing, '');
    assert(!result.stdout.includes('All commits have valid DCO sign-off.'),
      'an exemption must not be reported as a sign-off');
  });
}

test('all eight reported merge commits together are reported as exemptions, not sign-offs', t => {
  assert.equal(mergeFixture.length, 8);
  const commits = mergeFixture.map(({ pr: _pr, ...entry }) => entry);
  const result = exercise(t, commits);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Breakdown: 0 signed off, 8 verified GitHub merge exemption\(s\), 0 bot exemption\(s\), 0 missing sign-off/);
  assert.match(result.stdout, /All commits satisfy DCO \(0 signed off, 8 verified GitHub merge exemption/);
  assert.match(result.output, /success_description=DCO satisfied: 0 signed off, 8 verified GitHub merge\(s\) exempt/);
  assert.match(result.summary, /Exempt: verified GitHub-generated merge \| 8 \|/);
  assert.match(result.summary, /Missing sign-off \| 0 \|/);
});

test('both GitHub merge subject forms are exempt', t => {
  const result = exercise(t, [commit(1),
    githubMerge(2, 'Merge pull request #4242 from some-owner/some-topic\n\nTitle of the pull request'),
    githubMerge(3, "Merge branch 'develop' into some-topic")]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Breakdown: 1 signed off, 2 verified GitHub merge exemption\(s\), 0 bot exemption\(s\), 0 missing/);
  assert.match(result.output, /commits_missing=false/);
});

test('the summary separates sign-offs, GitHub merge exemptions, bot exemptions and missing sign-offs', t => {
  const result = exercise(t, [commit(1), commit(2),
    commit(3, { author: { login: 'dependabot[bot]', type: 'Bot' },
      commit: { message: 'chore(deps): bump', verification: { verified: false, reason: 'unsigned' } } }),
    githubMerge(4, "Merge branch 'develop' into topic"), unsignedCommit(5)]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Breakdown: 2 signed off, 1 verified GitHub merge exemption\(s\), 1 bot exemption\(s\), 1 missing sign-off/);
  assert.match(result.summary, /Signed off .* \| 2 \|/);
  assert.match(result.summary, /Exempt: verified GitHub-generated merge \| 1 \|/);
  assert.match(result.summary, /Exempt: permitted bot account \| 1 \|/);
  assert.match(result.summary, /Missing sign-off \| 1 \|/);
});

test('a mixed PR exempts the GitHub merges but still fails the unsigned contributor commit', t => {
  const commits = [...mergeFixture.slice(0, 2).map(({ pr: _pr, ...entry }) => entry),
    unsignedCommit(50), commit(51)];
  const result = exercise(t, commits);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.output, /commits_missing=true/);
  assert.equal(result.missing.trim().split('\n').length, 1);
  assert.match(result.missing, /forgot to sign 50/);
  assert(!result.missing.includes('Merge branch'), 'exempt merges must not be listed as missing');
  assert.match(result.exempt, /github-merge .*Merge branch 'develop' into fix-pdf-action-response/);
  assert(!result.output.includes('success_description'));
});

test('the failure comment lists only the real gap and names exemptions as needing no action', t => {
  const commits = [githubMerge(1, "Merge branch 'develop' into topic"), unsignedCommit(2)];
  const verify = exercise(t, commits);
  const report = runStep(t, reporter, {
    '/tmp/missing_commits.txt': verify.missing, '/tmp/exempt_commits.txt': verify.exempt },
    { HEAD_SHA: sha(2), EVENT_NAME: 'pull_request_target', MANUAL_CONFIRMED: 'false',
      CONFIRMING_USER: '', CONFIRMING_PERMISSION: '', CONFIRMING_ROLE: '', CONFIRMING_AT: '',
      STALE_CONFIRMATION_FOUND: 'false', DCO_TRUNCATED: 'false', CONFIRMATION_PHRASE: 'Confirming DCO sign off for all commits' });
  assert.equal(report.status, 1, report.stderr);
  const bodies = report.writes.map(entry => entry.body);
  const text = JSON.parse(bodies.find(body => body.includes('dco-check-failed'))).body;
  const missingSection = text.split('These commits were checked and are exempt')[0];
  assert(missingSection.includes('forgot to sign 2'));
  assert(!missingSection.includes('Merge branch'), 'exempt merge listed as missing');
  assert.match(text, /exempt; they need no sign-off and no manual confirmation[^\n]*\n- `github-merge [0-9a-f]{7} - Merge branch 'develop' into topic`/);
  assert.equal(JSON.parse(bodies.find(body => body.includes('"context"'))).state, 'failure');
});

test('the failure comment caps a long exempt list so the guidance still fits in a comment', t => {
  const commits = [...Array.from({ length: 300 }, (_, i) =>
    githubMerge(i + 1, "Merge branch 'develop' into " + 'long-topic-name-'.repeat(15) + i)), unsignedCommit(400)];
  const verify = exercise(t, commits);
  assert.match(verify.output, /commits_missing=true/);
  const report = runStep(t, reporter, {
    '/tmp/missing_commits.txt': verify.missing, '/tmp/exempt_commits.txt': verify.exempt },
    { HEAD_SHA: sha(400), EVENT_NAME: 'pull_request_target', MANUAL_CONFIRMED: 'false',
      CONFIRMING_USER: '', CONFIRMING_PERMISSION: '', CONFIRMING_ROLE: '', CONFIRMING_AT: '',
      STALE_CONFIRMATION_FOUND: 'false', DCO_TRUNCATED: 'false', CONFIRMATION_PHRASE: 'Confirming DCO sign off for all commits' });
  assert.equal(report.status, 1, report.stderr);
  const text = JSON.parse(report.writes.map(entry => entry.body).find(body => body.includes('dco-check-failed'))).body;
  assert(text.length < 65536, 'comment body exceeds GitHub limit: ' + text.length);
  assert.equal((text.match(/^- `github-merge /gm) || []).length, 20);
  assert.match(text, /and 280 more exempt commit\(s\)/);
  assert.match(text, /forgot to sign 400/);
  assert.match(text, /Manual confirmation fallback/);
});

test('an all-exempt PR never produces a failure comment or a manual-confirmation request', t => {
  const verify = exercise(t, [githubMerge(1, "Merge branch 'develop' into topic")]);
  assert.match(verify.output, /commits_missing=false/);
  const description = verify.output.match(/success_description=(.*)/)[1];
  const status = runStep(t, successStatus, {}, { HEAD_SHA: sha(1), DESCRIPTION: description });
  assert.equal(status.status, 0, status.stderr);
  assert.equal(status.writes.length, 1);
  const posted = JSON.parse(status.writes[0].body);
  assert.equal(posted.state, 'success');
  assert.match(posted.description, /1 verified GitHub merge\(s\) exempt/);
  assert(posted.description.length <= 140, 'commit status descriptions are capped at 140 characters');
});

test('a plain signed-off PR keeps the original success wording', t => {
  const verify = exercise(t, [commit(1), commit(2)]);
  assert.match(verify.stdout, /All commits have valid DCO sign-off\./);
  assert.match(verify.output, /success_description=All commits have valid DCO sign-off/);
});

// Lookalikes: each of these presents some merge-like trait but lacks another,
// so none may acquire the GitHub merge exemption.
const lookalikes = {
  'a local merge with a merge subject and a valid user signature':
    githubMerge(1, "Merge branch 'develop' into topic", { committer: { login: 'contributor' } }),
  'git committer text spoofing GitHub on a contributor account':
    githubMerge(2, "Merge branch 'develop' into topic", { committer: { login: 'contributor' },
      commit: { message: "Merge branch 'develop' into topic",
        committer: { name: 'GitHub', email: 'noreply@github.com' },
        verification: { verified: true, reason: 'valid' } } }),
  'git author text claiming web-flow on a local commit':
    commit(3, { commit: { message: 'fix: spoof\n\nCo-authored-by: web-flow <noreply@github.com>',
      author: { name: 'web-flow', email: 'noreply@github.com' },
      verification: { verified: true, reason: 'valid' } } }),
  'an unsigned squash commit by web-flow (single parent)':
    commit(4, { committer: { login: 'web-flow' }, commit: { message: 'fix: squashed change (#4242)',
      verification: { verified: true, reason: 'valid' } } }),
  'a single-parent commit with a merge subject':
    githubMerge(5, 'Merge pull request #1 from owner/topic', { parents: [{ sha: base }] }),
  'a web-flow merge that is not signature-verified':
    githubMerge(6, "Merge branch 'develop' into topic", { commit: {
      message: "Merge branch 'develop' into topic", verification: { verified: false, reason: 'unsigned' } } }),
  'a web-flow merge whose verified flag is false even with a valid reason':
    githubMerge(15, "Merge branch 'develop' into topic", { commit: {
      message: "Merge branch 'develop' into topic", verification: { verified: false, reason: 'valid' } } }),
  'a web-flow merge with an invalid signature reason':
    githubMerge(7, "Merge branch 'develop' into topic", { commit: {
      message: "Merge branch 'develop' into topic", verification: { verified: true, reason: 'bad_email' } } }),
  'a verified web-flow merge with a non-merge subject':
    githubMerge(8, 'Revert "feat: something"'),
  'a merge subject only on the second line':
    githubMerge(9, 'fix: harmless\n\nMerge pull request #1 from owner/topic'),
  'a lower-case merge subject':
    githubMerge(10, "merge branch 'develop' into topic"),
  'a branch merge subject without a target':
    githubMerge(11, "Merge branch 'develop'"),
  'a pull request merge subject without a source':
    githubMerge(12, 'Merge pull request #1'),
  'a pull request merge subject with a non-numeric number':
    githubMerge(13, 'Merge pull request #abc from owner/topic'),
  'a web-flow merge with no committer account':
    githubMerge(14, "Merge branch 'develop' into topic", { committer: null }),
};
for (const [name, lookalike] of Object.entries(lookalikes)) {
  test('lookalike is not exempt: ' + name, t => {
    const message = lookalike.commit.message;
    const stripped = { ...lookalike, commit: { ...lookalike.commit, message: message.replace(/\n\nSigned-off-by:.*/s, '') } };
    const result = exercise(t, [stripped]);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.output, /commits_missing=true/);
    assert.match(result.stdout, /Checked 1 commit\(s\): 0 passed, 1 missing sign-off/);
    assert.match(result.stdout, /Breakdown: 0 signed off, 0 verified GitHub merge exemption\(s\)/);
    assert(!result.stdout.includes('DCO-exempt verified GitHub-generated merge'));
    assert.equal(result.exempt.trim(), '');
  });
}
