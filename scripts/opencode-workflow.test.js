'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const p = require('./opencode/policy.cjs');
const c = require('./opencode/controller.cjs');
const w = require('./opencode/worker.cjs');

const sha = 'a'.repeat(40);
const repo = 'carlos-emr/carlos';
function fixture() {
  const comment = { id: 12, user: { id: 1, login: 'Alice', type: 'User' }, body: '/oc implement fix', updated_at: 'now',
    issue_url: `https://api.github.com/repos/${repo}/issues/3` };
  const event = { repository: { full_name: repo }, issue: { number: 3 }, comment };
  const env = { GITHUB_REPOSITORY: repo, GITHUB_RUN_ID: '42', GITHUB_ACTOR: 'Alice',
    OPENCODE_ENABLED: 'true', OPENCODE_ALLOWED_USERS: '["alice"]', OPENCODE_API_BASE_URL: 'https://example.com/v1',
    OPENCODE_MODEL_ID: 'vendor/model', OPENCODE_APP_ID: '123' };
  const ctx = c.context(event, env);
  const data = {
    [`${ctx.root}/issues/comments/12`]: structuredClone(comment),
    [`${ctx.root}/collaborators/Alice/permission`]: { permission: 'write' },
    [`${ctx.root}/issues/3`]: { state: 'open', title: 'Issue', body: 'Context' },
    [`${ctx.root}/branches/release%2F2026.08`]: { commit: { sha } },
  };
  const calls = [];
  const api = { request: async (route, method = 'GET', body) => {
    calls.push({ route, method, body });
    if (!(route in data)) throw new Error(`Unmocked request: ${route}`);
    if (data[route] instanceof Error) throw data[route];
    return data[route];
  }, optional: async route => data[route] || null, pages: async route => data[route] || [] };
  return { env, ctx, data, api, calls };
}
function makePR(f) {
  f.data[`${f.ctx.root}/issues/3`].pull_request = {};
  f.data[`${f.ctx.root}/pulls/3`] = { state: 'open', head: { repo: { full_name: repo }, ref: 'topic', sha },
    base: { repo: { full_name: repo }, ref: 'release/2026.08' } };
  f.data[`${f.ctx.root}/branches/topic`] = { protected: false };
}

test('only exact slash commands parse, including aliases and hostile text as data', () => {
  assert.deepEqual(p.command('/OC fix $(echo nope)\n`code`'), { mode: 'implement', prompt: '$(echo nope)\n`code`' });
  for (const text of ['hello /oc fix it', '/octopus fix it', '```\n/oc fix it', '> /oc fix it']) assert.equal(p.command(text), null);
  for (const text of ['/oc', '/oc deploy foo', '/opencode review ']) assert.throws(() => p.command(text), /Use/);
});

test('allowlist is exact, case-insensitive, deny-by-default and validates malformed input', () => {
  assert.equal(p.permitted(p.allowlist('["Alice"]'), 'ALICE', { permission: 'write' }), true);
  assert.equal(p.permitted(p.allowlist('["alice2"]'), 'alice', { permission: 'admin' }), false);
  assert.equal(p.permitted(p.allowlist('[]'), 'alice', { permission: 'admin' }), false);
  for (const value of ['alice', '{}', '[12]', '["alice,bob"]', '["alice[bot]"]']) assert.throws(() => p.allowlist(value));
  assert.equal(p.permitted(p.allowlist('["alice"]'), 'alice', { permission: 'custom', user: { permissions: { push: true } } }), true);
  assert.equal(p.permitted(p.allowlist('["alice"]'), 'alice', { permission: 'triage' }), false);
});

test('settings require explicit enablement and HTTPS; model IDs may include a provider slash', () => {
  const { env } = fixture();
  assert.equal(p.settings(env).model, 'vendor/model');
  for (const changes of [{ OPENCODE_ENABLED: '' }, { OPENCODE_MODEL_ID: '' }, { OPENCODE_APP_ID: '' },
    { OPENCODE_API_BASE_URL: 'http://example.com' }, { OPENCODE_API_BASE_URL: 'https://user:pass@example.com' },
    { OPENCODE_API_BASE_URL: 'https://example.com?key=secret' }]) assert.throws(() => p.settings({ ...env, ...changes }));
});

test('authorization resolves issues explicitly from release/2026.08', async () => {
  const f = fixture();
  const task = await c.authorize(f.api, f.ctx, f.env);
  assert.equal(task.base, 'release/2026.08'); assert.equal(task.source, sha);
  assert.equal(task.branch, 'opencode/comment-12');
});

test('authorization rejects missing grants, revoked access, lookup failures and rerunning actors', async () => {
  for (const change of [f => { f.env.OPENCODE_ALLOWED_USERS = '[]'; },
    f => { f.env.GITHUB_TRIGGERING_ACTOR = 'Mallory'; },
    f => { f.data[`${f.ctx.root}/collaborators/Alice/permission`] = { permission: 'read' }; },
    f => { f.data[`${f.ctx.root}/collaborators/Alice/permission`] = new Error('lookup failed'); }]) {
    const f = fixture(); change(f); await assert.rejects(c.authorize(f.api, f.ctx, f.env));
    assert.equal(f.calls.some(x => x.method !== 'GET'), false);
  }
});

test('authorization rejects changed or deleted comments and bots', async () => {
  for (const change of [f => { f.data[`${f.ctx.root}/issues/comments/12`].body += ' changed'; },
    f => { f.data[`${f.ctx.root}/issues/comments/12`].user.type = 'Bot'; },
    f => { f.data[`${f.ctx.root}/issues/comments/12`] = new Error('404'); }]) {
    const f = fixture(); change(f); await assert.rejects(c.authorize(f.api, f.ctx, f.env));
  }
});

test('existing PR retains its base; forks, closed PRs and protected heads are rejected', async () => {
  const f = fixture(); makePR(f);
  f.data[`${f.ctx.root}/pulls/3`].base.ref = 'develop';
  assert.equal((await c.authorize(f.api, f.ctx, f.env)).base, 'develop');
  for (const change of [f => { f.data[`${f.ctx.root}/pulls/3`].head.repo.full_name = 'alice/fork'; },
    f => { f.data[`${f.ctx.root}/pulls/3`].state = 'closed'; },
    f => { f.data[`${f.ctx.root}/branches/topic`].protected = true; }]) {
    const next = fixture(); makePR(next); change(next); await assert.rejects(c.authorize(next.api, next.ctx, next.env));
  }
  for (const name of ['main', 'develop', 'release/2026.08', 'community/a/develop']) assert.equal(p.protectedHead(name), true);
});

test('publication recovery uses the request marker and never overwrites a colliding branch', async () => {
  const f = fixture(); const task = await c.authorize(f.api, f.ctx, f.env);
  f.data[`${f.ctx.root}/git/ref/heads/${task.branch}`] = { object: { sha } };
  f.data[`${f.ctx.root}/commits?sha=${sha}&per_page=100`] = [{ sha, commit: { message: `fix: task\n\n${p.marker(repo, 12)}` } }];
  assert.equal(await c.existing(f.api, f.ctx, task), sha);
  f.data[`${f.ctx.root}/commits?sha=${sha}&per_page=100`] = [];
  await assert.rejects(c.existing(f.api, f.ctx, task), /already exists/);
});

test('branch URL metacharacters cannot redirect recovery to a different ref', async () => {
  const f = fixture();
  const task = { mode: 'implement', pr: {}, branch: 'topic/a#b%25' };
  let requested;
  f.api.optional = async route => { requested = route; return null; };
  await c.existing(f.api, f.ctx, task);
  assert.equal(requested, `${f.ctx.root}/git/ref/heads/topic/a%23b%2525`);
});

test('provider and tool errors cannot pass even with exit status zero', () => {
  const good = [{ type: 'text', part: { text: 'Done' } }, { type: 'step_finish', part: { reason: 'stop' } }];
  const json = events => events.map(e => JSON.stringify(e)).join('\n');
  assert.equal(p.parseEvents(json(good), 0), 'Done');
  for (const events of [[], [good[0]], [...good, { type: 'error', error: {} }],
    [...good, { type: 'tool_use', part: { state: { status: 'error' } } }],
    [...good, { type: 'tool_use', part: { tool: 'bash', state: { status: 'completed', metadata: { exit: 1 } } } }],
    [good[0], { type: 'step_finish', part: { reason: 'length' } }]]) assert.throws(() => p.parseEvents(json(events), 0));
  assert.throws(() => p.parseEvents(json(good), 1));
  assert.throws(() => p.parseEvents('not JSON', 0));
});

test('publisher accepts only bounded regular-file results and rejects control/path injection', () => {
  const bundle = file => ({ version: 1, source: sha, response: 'Done', files: [file] });
  const file = { path: 'src/example.java', mode: '100644', content: Buffer.from('hello').toString('base64') };
  assert.equal(p.validateBundle(bundle(file), sha).files.length, 1);
  for (const name of ['../escape', '/absolute', 'a\\b', 'a/.git/config', '.github/workflows/evil.yml',
    '.opencode/plugins/evil.js', 'scripts/opencode/controller.cjs', '.gitmodules', 'a/.gitattributes']) {
    assert.throws(() => p.validateBundle(bundle({ ...file, path: name }), sha));
  }
  assert.throws(() => p.validateBundle(bundle({ ...file, mode: '120000' }), sha));
  assert.throws(() => p.validateBundle(bundle({ ...file, content: '$bad' }), sha));
  assert.throws(() => p.validateBundle(bundle(file), 'b'.repeat(40)));
  assert.throws(() => p.validateBundle({ ...bundle(file), files: [file, file] }, sha));
});

test('read-only configuration disables edits, bash, questions, subagents and external services', () => {
  const config = p.config(p.settings(fixture().env), 'review');
  assert.equal(config.agent.carlos.permission['*'], 'deny');
  assert.equal(config.agent.carlos.permission.bash, undefined);
  assert.equal(config.share, 'disabled'); assert.equal(config.autoupdate, false);
  assert.deepEqual(config.enabled_providers, ['carlos']);
  assert.equal(config.model, 'carlos/vendor/model');
});

test('notifications only update authentic Actions-bot comments; failures propagate', async () => {
  const f = fixture();
  f.data[`${f.ctx.root}/issues/3/comments`] = [
    { id: 99, body: '<!-- carlos-opencode:12 -->', user: { login: 'alice', type: 'User' } },
    { id: 100, body: '<!-- carlos-opencode:12 -->', user: { login: 'github-actions[bot]', type: 'Bot' } },
  ];
  f.data[`${f.ctx.root}/issues/comments/100`] = {};
  await c.status(f.api, f.ctx, 'Failed');
  assert.equal(f.calls[0].route, `${f.ctx.root}/issues/comments/100`);
  f.data[`${f.ctx.root}/issues/comments/100`] = new Error('posting failed');
  await assert.rejects(c.status(f.api, f.ctx, 'Failed'), /posting failed/);
});

test('pagination never silently truncates or retries a mutation', async () => {
  let count = 0;
  const api = new c.GitHub('test', async () => ({ ok: true, status: 200, json: async () => { count++; return Array(100).fill({}); } }));
  await assert.rejects(api.pages('/test', 2), /pagination limit/); assert.equal(count, 2);
  count = 0;
  const fail = new c.GitHub('test', async () => { count++; return { ok: false, status: 429 }; });
  await assert.rejects(fail.request('/test', 'POST', {}), /HTTP 429/); assert.equal(count, 1);
});

test('collection uses trusted Git metadata and detects edits, additions, deletion and symlinks', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-files-')); t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const source = path.join(dir, 'source'), work = path.join(dir, 'work'); fs.mkdirSync(source); fs.mkdirSync(work);
  const git = args => execFileSync('git', ['-C', source, ...args], { stdio: ['ignore', 'pipe', 'pipe'] }).toString().trim();
  git(['init']); git(['config', 'user.name', 'Fixture']); git(['config', 'user.email', 'fixture@example.com']);
  fs.writeFileSync(path.join(source, 'keep'), 'keep'); fs.writeFileSync(path.join(source, 'remove'), 'remove');
  git(['add', '.']); git(['commit', '-m', 'fixture']); const head = git(['rev-parse', 'HEAD']);
  fs.writeFileSync(path.join(work, 'keep'), 'edit'); fs.writeFileSync(path.join(work, 'add'), 'new');
  assert.deepEqual(w.collect(source, work, head).map(x => x.path).sort(), ['add', 'keep', 'remove']);
  fs.symlinkSync('/etc/passwd', path.join(work, 'link'));
  assert.throws(() => w.collect(source, work, head), /symlink/);
});

test('credential checks reject cleartext, URL-encoded and base64 values', () => {
  const key = 'secret/key-value';
  for (const value of [key, encodeURIComponent(key), Buffer.from(key).toString('base64')]) assert.throws(() => w.redactCheck(value, key));
  w.redactCheck('Ordinary output', key);
});

function publication(t, pr = false) {
  const f = fixture(); if (pr) makePR(f);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-publish-'));
  const old = { ...process.env };
  Object.assign(process.env, f.env, { EXPECTED_SOURCE: sha, EXPECTED_BASE: 'release/2026.08',
    EXPECTED_BRANCH: pr ? 'topic' : 'opencode/comment-12', RESULT_DIR: dir });
  t.after(() => {
    for (const key of Object.keys(process.env)) if (!(key in old)) delete process.env[key];
    Object.assign(process.env, old); fs.rmSync(dir, { recursive: true, force: true });
  });
  fs.writeFileSync(path.join(dir, 'result.json'), JSON.stringify({ version: 1, source: sha, response: 'Tests not run.',
    files: [{ path: 'docs/example.md', mode: '100644', content: Buffer.from('example').toString('base64') }] }));
  f.data[`${f.ctx.root}/issues/3/comments`] = [];
  const writes = [];
  const app = { slug: 'carlos-test', api: { pages: async () => [], request: async (route, method = 'GET', body) => {
    writes.push({ route, method, body });
    if (route.endsWith('/git/commits/' + sha)) return { tree: { sha: 'tree-old' } };
    if (route.startsWith('/users/')) return { login: 'carlos-test[bot]', id: 123 };
    if (route.endsWith('/git/blobs')) return { sha: 'blob' };
    if (route.endsWith('/git/trees')) return { sha: 'tree-new' };
    if (route.endsWith('/git/commits')) return { sha: 'b'.repeat(40) };
    if (route.endsWith('/pulls')) return { html_url: 'https://github.com/carlos-emr/carlos/pull/4' };
    return {};
  } } };
  return { ...f, app, writes, dir };
}

test('publisher creates a draft release PR using regular-file Git APIs and revokes its token', async t => {
  const f = publication(t);
  await c.publish(f.api, f.ctx, async () => f.app);
  const create = f.writes.find(x => x.route.endsWith('/pulls'));
  assert.equal(create.body.base, 'release/2026.08'); assert.equal(create.body.draft, true);
  assert.equal(create.body.head, 'opencode/comment-12');
  const commit = f.writes.find(x => x.route.endsWith('/git/commits'));
  assert.deepEqual(commit.body.parents, [sha]);
  assert.equal(commit.body.author.name, 'carlos-test[bot]');
  assert.ok(!commit.body.message.includes('Signed-off-by:'));
  assert.equal(f.writes.at(-1).route, '/installation/token');
});

test('stale source prevents token creation and publication', async t => {
  const f = publication(t, true);
  f.data[`${f.ctx.root}/pulls/3`].head.sha = 'c'.repeat(40);
  let minted = false;
  await assert.rejects(c.publish(f.api, f.ctx, async () => { minted = true; return f.app; }), /Source branch changed/);
  assert.equal(minted, false);
});

test('concurrent push failures are never force-pushed or automatically retried', async t => {
  const f = publication(t, true);
  const request = f.app.api.request;
  f.app.api.request = async (route, method, body) => {
    const result = await request(route, method, body);
    if (route.includes('/git/refs/heads/')) { assert.equal(body.force, false); throw new Error('non-fast-forward'); }
    return result;
  };
  await assert.rejects(c.publish(f.api, f.ctx, async () => f.app), /non-fast-forward/);
  assert.equal(f.writes.filter(x => x.route.includes('/git/refs/heads/')).length, 1);
  assert.equal(f.writes.at(-1).route, '/installation/token');
});

test('partial publication recovery creates the missing PR without reading a result or committing again', async t => {
  const f = publication(t);
  fs.unlinkSync(path.join(f.dir, 'result.json'));
  f.data[`${f.ctx.root}/git/ref/heads/opencode/comment-12`] = { object: { sha } };
  f.data[`${f.ctx.root}/commits?sha=${sha}&per_page=100`] = [{ sha, commit: { message: `fix: result\n\n${p.marker(repo, 12)}` } }];
  await c.publish(f.api, f.ctx, async () => f.app);
  assert.ok(f.writes.some(x => x.route.endsWith('/pulls')));
  assert.ok(!f.writes.some(x => /\/git\/(commits|trees|blobs|refs)/.test(x.route)));
});

test('no-change and read-only results never mint a write token; changed read-only output fails', async t => {
  const f = publication(t);
  const result = { version: 1, source: sha, response: 'No changes necessary. Tests not run.', files: [] };
  fs.writeFileSync(path.join(f.dir, 'result.json'), JSON.stringify(result));
  const issueToken = async () => { throw new Error('must not mint'); };
  await c.publish(f.api, f.ctx, issueToken);
  assert.match(f.calls.at(-1).body.body, /Completed without changes/);
  f.ctx.event.comment.body = '/oc review changes';
  f.data[`${f.ctx.root}/issues/comments/12`].body = '/oc review changes';
  await c.publish(f.api, f.ctx, issueToken);
  result.files = [{ path: 'example', content: 'YQ==', mode: '100644' }];
  fs.writeFileSync(path.join(f.dir, 'result.json'), JSON.stringify(result));
  await assert.rejects(c.publish(f.api, f.ctx, issueToken), /Read-only request/);
});

test('completion reporter handles cancellation and overflow using metadata, ignoring unrelated workflows', async t => {
  const f = publication(t);
  const run = { event: 'issue_comment', repository: { full_name: repo }, conclusion: 'cancelled', workflow_id: 9,
    path: '.github/workflows/opencode.yml', display_title: 'OpenCode #3 comment 12', id: 42, run_attempt: 1, html_url: f.ctx.url };
  f.data[`${f.ctx.root}/actions/workflows/opencode.yml`] = { id: 9 };
  f.data[`${f.ctx.root}/actions/runs/42`] = { run_attempt: 1 };
  await c.report(f.api, { workflow_run: run });
  assert.match(f.calls.at(-1).body.body, /Workflow cancelled/);
  await assert.rejects(c.report(f.api, { workflow_run: { ...run, workflow_id: 10 } }), /Unexpected originating/);
  f.data[`${f.ctx.root}/actions/runs/42`].run_attempt = 2;
  const before = f.calls.filter(x => x.method === 'POST').length;
  await c.report(f.api, { workflow_run: run });
  assert.equal(f.calls.filter(x => x.method === 'POST').length, before);
});
