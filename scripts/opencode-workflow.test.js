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
    GITHUB_TRIGGERING_ACTOR: 'Alice', GITHUB_RUN_ATTEMPT: '1',
    OPENCODE_ENABLED: 'true', OPENCODE_ALLOWED_USERS: '["alice"]', OPENCODE_PROVIDER_1_CONFIGURED: 'true',
    OPENCODE_MODELS: JSON.stringify({ deepseek41flash: { model: 'vendor/model', provider: 1, adapter: 'openai-compatible' } }), OPENCODE_APP_ID: '123' };
  const ctx = c.context(event, env);
  const data = {
    [`${ctx.root}/issues/comments/12`]: structuredClone(comment),
    [`${ctx.root}/collaborators/Alice/permission`]: { permission: 'write' },
    [`${ctx.root}/issues/3`]: { state: 'open', title: 'Issue', body: 'Context' },
    [`${ctx.root}/branches/release%2F2026.08`]: { commit: { sha } },
    [`${ctx.root}/environments/opencode-publish`]: { protection_rules: [{ type: 'required_reviewers', reviewers: [{ type: 'User', reviewer: { login: 'Alice' } }] }] },
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
    base: { repo: { full_name: repo }, ref: 'release/2026.08', sha: 'c'.repeat(40) } };
  f.data[`${f.ctx.root}/branches/topic`] = { protected: false };
  f.data[`${f.ctx.root}/compare/${'c'.repeat(40)}...${sha}?per_page=1`] = { merge_base_commit: { sha: 'd'.repeat(40) } };
}

test('only exact slash commands parse, including aliases and hostile text as data', () => {
  assert.deepEqual(p.command('/OC fix $(echo nope)\n`code`'), { mode: 'implement', prompt: '$(echo nope)\n`code`' });
  for (const text of ['hello /oc fix it', '/octopus fix it', '```\n/oc fix it', '> /oc fix it']) assert.equal(p.command(text), null);
  for (const text of ['/oc', '/oc deploy foo', '/opencode implement ']) assert.throws(() => p.command(text), /Use/);
});

test('allowlist is exact, case-insensitive, deny-by-default and validates malformed input', () => {
  assert.equal(p.permitted(p.allowlist('["Alice"]'), 'ALICE', { permission: 'write' }), true);
  assert.equal(p.permitted(p.allowlist('["alice2"]'), 'alice', { permission: 'admin' }), false);
  assert.equal(p.permitted(p.allowlist('[]'), 'alice', { permission: 'admin' }), false);
  for (const value of ['alice', '{}', '[12]', '["alice,bob"]', '["alice[bot]"]']) assert.throws(() => p.allowlist(value));
  assert.equal(p.permitted(p.allowlist('["alice"]'), 'alice', { permission: 'custom', user: { permissions: { push: true } } }), true);
  assert.equal(p.permitted(p.allowlist('["alice"]'), 'alice', { permission: 'triage' }), false);
});

test('settings require enablement and configured credentials; endpoints require HTTPS', () => {
  const { env } = fixture();
  assert.equal(p.settings(env).model, 'vendor/model');
  for (const changes of [{ OPENCODE_ENABLED: '' }, { OPENCODE_MODELS: '{}' }, { OPENCODE_APP_ID: '' },
    { OPENCODE_PROVIDER_1_CONFIGURED: 'false' }]) assert.throws(() => p.settings({ ...env, ...changes }));
  for (const url of ['http://example.com', 'https://user:pass@example.com', 'https://example.com?key=secret', '']) assert.throws(() => p.endpoint(url));
  assert.equal(p.endpoint('https://example.com/v1/'), 'https://example.com/v1');
});

test('authorization resolves issues explicitly from release/2026.08', async () => {
  const f = fixture();
  const task = await c.authorize(f.api, f.ctx, f.env);
  assert.equal(task.base, 'release/2026.08'); assert.equal(task.source, sha);
  assert.equal(task.branch, 'opencode/comment-12');
});

test('explicit issue base targets an existing repository branch and is rejected on PRs', async () => {
  const f = fixture();
  f.ctx.event.comment.body = '/oc implement --base develop requested fix';
  f.data[`${f.ctx.root}/issues/comments/12`].body = f.ctx.event.comment.body;
  f.data[`${f.ctx.root}/branches/develop`] = { commit: { sha: 'd'.repeat(40) } };
  const task = await c.authorize(f.api, f.ctx, f.env);
  assert.equal(task.base, 'develop'); assert.equal(task.source, 'd'.repeat(40));
  assert.equal(task.prompt, 'requested fix');
  makePR(f);
  await assert.rejects(c.authorize(f.api, f.ctx, f.env), /only on issues/);
  assert.throws(() => p.command('/oc review --base main request'), /Use/);
  assert.throws(() => p.command('/oc implement --base develop'), /Use/);
  assert.equal(p.command('/oc fix --base=release/2026.08 issue').requestedBase, 'release/2026.08');
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
  Object.assign(process.env, f.env, { EXPECTED_MODEL: 'vendor/model', EXPECTED_PROVIDER: '1',
    EXPECTED_ALIAS: 'deepseek41flash', EXPECTED_ADAPTER: 'openai-compatible', EXPECTED_REVIEW_PASSES: '2', EXPECTED_SOURCE: sha, EXPECTED_BASE: 'release/2026.08',
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

test('publisher preserves an explicit alternate issue base', async t => {
  const f = publication(t);
  f.ctx.event.comment.body = '/oc implement --base develop fix';
  f.data[`${f.ctx.root}/issues/comments/12`].body = f.ctx.event.comment.body;
  f.data[`${f.ctx.root}/branches/develop`] = { commit: { sha } };
  process.env.EXPECTED_BASE = 'develop';
  await c.publish(f.api, f.ctx, async () => f.app);
  assert.equal(f.writes.find(x => x.route.endsWith('/pulls')).body.base, 'develop');
});

test('provider gateway enforces endpoint/model, hides credentials/errors, and bounds requests', async t => {
  const { startGateway, startForwarder, close } = require('./opencode/gateway.cjs');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-gateway-'));
  const calls = [];
  let failure = false;
  const gateway = await startGateway({ socket: path.join(dir, 'api.sock'), baseURL: 'https://provider.example/v1',
    model: 'chosen-model', key: 'real-host-only-key', fetcher: async (url, init) => {
      calls.push({ url, init });
      return failure ? new Response('real-host-only-key secret upstream diagnostics', { status: 401 }) : Response.json({ choices: [] });
    } });
  const forwarder = await startForwarder(path.join(dir, 'api.sock'));
  t.after(async () => { await close(forwarder); await close(gateway); fs.rmSync(dir, { recursive: true, force: true }); });
  const root = `http://127.0.0.1:${forwarder.address().port}`;
  const send = (body, url = '/v1/chat/completions') => fetch(root + url, { method: 'POST',
    headers: { Authorization: 'Bearer attacker', 'X-Api-Key': 'attacker' }, body: JSON.stringify(body) });
  const body = { model: 'chosen-model', messages: [{ role: 'user', content: 'hello' }], stream: false,
    max_tokens: 999999, baseURL: 'https://evil.example', headers: { Authorization: 'attacker' } };
  assert.equal((await send(body, '/v1/files')).status, 403);
  assert.equal((await fetch(root + '/v1/chat/completions')).status, 403);
  assert.equal((await send({ ...body, model: 'other-model' })).status, 400);
  assert.equal((await send(body)).status, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://provider.example/v1/chat/completions');
  assert.equal(calls[0].init.redirect, 'error');
  assert.deepEqual(calls[0].init.headers, { 'Content-Type': 'application/json', Authorization: 'Bearer real-host-only-key' });
  assert.deepEqual(JSON.parse(calls[0].init.body), { model: body.model, messages: body.messages, stream: false, max_tokens: 8192 });
  failure = true;
  const rejected = await send(body);
  assert.equal(rejected.status, 401); assert.doesNotMatch(await rejected.text(), /real-host-only-key|diagnostics/);
  const oversized = await send({ ...body, messages: ['x'.repeat(2 * 1024 * 1024)] });
  assert.equal(oversized.status, 413);
  for (let i = 0; i < 98; i++) await (await send({ ...body, model: 'denied' })).text();
  assert.equal((await send(body)).status, 429);
  assert.equal(calls.length, 2);
});

test('publication fails closed before creating a token if required reviewers are removed', async t => {
  const f = publication(t);
  let minted = false;
  for (const protection of [null, { protection_rules: [] }, { protection_rules: [{ type: 'required_reviewers', reviewers: [] }] }]) {
    f.data[`${f.ctx.root}/environments/opencode-publish`] = protection;
    await assert.rejects(c.publish(f.api, f.ctx, async () => { minted = true; return f.app; }), /required reviewers/);
    assert.equal(minted, false);
  }
});

test('publication preview includes exact source, target, text and deletion/binary notices', () => {
  const preview = w.reviewText({ source: sha, response: 'Tests not run.', files: [
    { path: 'text', mode: '100644', content: Buffer.from('review this').toString('base64') },
    { path: 'removed', content: null }, { path: 'binary', mode: '100644', content: 'AA==' },
  ] }, 'develop');
  for (const expected of [sha, 'Target base: develop', 'review this', 'DELETE removed', 'Binary:', 'Tests not run.']) assert.ok(preview.includes(expected));
});

test('review requires configurable verification passes and resolves the PR merge base exactly', async () => {
  const f = fixture(); makePR(f);
  f.ctx.event.comment.body = '/oc review potential regressions';
  f.data[`${f.ctx.root}/issues/comments/12`].body = f.ctx.event.comment.body;
  const task = await c.authorize(f.api, f.ctx, f.env);
  assert.equal(task.baseline, 'd'.repeat(40));
  assert.equal(task.settings.reviewPasses, 2);
  assert.equal(p.settings({ ...f.env, OPENCODE_REVIEW_PASSES: '3' }).reviewPasses, 3);
  for (const value of ['1', '0', '4', 'banana']) assert.throws(() => p.settings({ ...f.env, OPENCODE_REVIEW_PASSES: value }), /REVIEW_PASSES/);
  f.data[`${f.ctx.root}/compare/${'c'.repeat(40)}...${sha}?per_page=1`] = {};
  await assert.rejects(c.authorize(f.api, f.ctx, f.env), /merge base/);
});

test('verified review rejects invented paths, quotations, line ranges, duplicate findings and malformed output', t => {
  const review = require('./opencode/review.cjs');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-evidence-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  fs.writeFileSync(path.join(dir, 'sample.js'), 'const value = null;\nvalue.run();\n');
  const finding = { severity: 'high', title: 'Null dereference', path: 'sample.js', startLine: 1, endLine: 2,
    evidence: 'const value = null;\nvalue.run();', explanation: 'Calling run always throws for this null value.' };
  const report = { findings: [finding], tests: ['Read source.'], limitations: ['No executable tests run.'] };
  assert.match(review.render(JSON.stringify(report), dir, sha, 2), /sample.js#L1-L2/);
  for (const changes of [{ path: '../secret' }, { path: '/etc/passwd' }, { path: 'missing.js' },
    { startLine: 2, endLine: 3 }, { evidence: 'hallucinated' }, { severity: 'critical' }]) {
    assert.throws(() => review.render(JSON.stringify({ ...report, findings: [{ ...finding, ...changes }] }), dir, sha, 2));
  }
  assert.throws(() => review.render('Unstructured confident answer', dir, sha, 2), /required evidence report/);
  assert.throws(() => review.render(JSON.stringify({ ...report, findings: [finding, finding] }), dir, sha, 2), /duplicate/);
  assert.match(review.render(JSON.stringify({ ...report, findings: [] }), dir, sha, 2), /No sufficiently supported defects/);
  fs.symlinkSync(path.join(dir, 'sample.js'), path.join(dir, 'link'));
  assert.throws(() => review.render(JSON.stringify({ ...report, findings: [{ ...finding, path: 'link' }] }), dir, sha, 2), /symlink/);
});

test('each review pass must read real files from every required snapshot', () => {
  const { assertInspection, verificationPrompt } = require('./opencode/review.cjs');
  const read = filePath => JSON.stringify({ type: 'tool_use', part: { tool: 'read', state: { status: 'completed',
    input: { filePath }, metadata: { display: { type: 'file' } } } } });
  assert.throws(() => assertInspection('', false), /did not inspect/);
  assertInspection(read('/work/source.js'), false);
  assert.throws(() => assertInspection(read('/work/source.js'), true), /did not inspect/);
  assertInspection(read('/work/source.js') + '\n' + read('/baseline/source.js'), true);
  const prompt = verificationPrompt({ prompt: 'Original request' }, 'Candidate finding', 2, 2);
  assert.match(prompt, /Original request/); assert.match(prompt, /Candidate finding/);
  assert.match(prompt, /VERIFICATION PASS 2 OF 2/); assert.match(prompt, /Drop unsupported/);
});

test('review accepts extra direction and configured model options in either order with issue bases', async () => {
  assert.deepEqual(p.command('/oc review --model kimi focus on authorization\nand tenant boundaries'), {
    mode: 'review', modelAlias: 'kimi', prompt: 'focus on authorization\nand tenant boundaries',
  });
  assert.match(p.command('/oc review').prompt, /correctness/);
  assert.equal(p.command('/oc review --model sonnet').modelAlias, 'sonnet');
  for (const command of ['/oc implement --model glm53 --base release/2026.08 fix it', '/oc implement --base release/2026.08 --model=glm53 fix it']) {
    assert.deepEqual(p.command(command), { mode: 'implement', requestedBase: 'release/2026.08', modelAlias: 'glm53', prompt: 'fix it' });
  }
  for (const command of ['/oc review --model', '/oc review --model unknown check', '/oc review --model kimi --model sonnet check', '/oc review --bad-option x']) assert.throws(() => p.command(command));
  const f = fixture();
  f.env.OPENCODE_MODELS = JSON.stringify(Object.fromEntries(p.MODEL_ALIASES.map(alias => [alias, { provider: 2, model: `vendor/${alias}`, adapter: 'openrouter' }])));
  f.env.OPENCODE_PROVIDER_2_CONFIGURED = 'true';
  for (const alias of p.MODEL_ALIASES) {
    const settings = p.settings(f.env, alias);
    assert.equal(settings.alias, alias); assert.equal(settings.provider, 2);
    assert.equal(settings.model, `vendor/${alias}`); assert.equal(settings.adapter, 'openrouter');
  }
  f.env.OPENCODE_PROVIDER_2_CONFIGURED = 'false';
  assert.throws(() => p.settings(f.env, 'kimi'), /Declined.*both the API key and base URL/);
  assert.throws(() => p.settings(fixture().env, 'sonnet'), /not configured/);
});

test('a model configuration change before publication cannot silently change the requested model', async t => {
  const f = publication(t);
  process.env.OPENCODE_MODELS = JSON.stringify({ deepseek41flash: { provider: 1, model: 'changed-model', adapter: 'openrouter' } });
  let minted = false;
  await assert.rejects(c.publish(f.api, f.ctx, async () => { minted = true; return f.app; }), /configuration changed/);
  assert.equal(minted, false);
});

test('availability notice is brief, grant-aware, idempotent and treats fork metadata as data', async () => {
  const f = fixture(); makePR(f);
  f.data[`${f.ctx.root}/issues/3/comments`] = [];
  const pr = f.data[`${f.ctx.root}/pulls/3`]; pr.number = 3; pr.user = { login: 'Alice', type: 'User' };
  const event = { repository: { full_name: repo }, pull_request: { number: 3 } };
  await c.notice(f.api, event, f.env);
  const posted = f.calls.find(x => x.method === 'POST');
  assert.match(posted.body.body, /\/oc review --model kimi focus/);
  assert.ok(posted.body.body.length < 1200);
  f.data[`${f.ctx.root}/issues/3/comments`] = [{ user: { type: 'Bot', login: 'github-actions[bot]' }, body: posted.body.body }];
  await c.notice(f.api, event, f.env);
  assert.equal(f.calls.filter(x => x.method === 'POST').length, 1);
  f.data[`${f.ctx.root}/issues/3/comments`][0].user = { type: 'User', login: 'attacker' };
  pr.head.repo.full_name = 'someone/fork';
  await c.notice(f.api, event, f.env);
  assert.match(f.calls.at(-1).body.body, /fork will be declined/);
});

test('availability notice skips disabled, unlisted, read-only and bot authors; API errors remain visible', async () => {
  const event = { repository: { full_name: repo }, pull_request: { number: 3 } };
  for (const change of ['disabled', 'unlisted', 'read-only', 'bot']) {
    const f = fixture(); makePR(f);
    const pr = f.data[`${f.ctx.root}/pulls/3`]; pr.user = { login: 'Alice', type: 'User' };
    if (change === 'disabled') f.env.OPENCODE_ENABLED = 'false';
    if (change === 'unlisted') f.env.OPENCODE_ALLOWED_USERS = '[]';
    if (change === 'read-only') f.data[`${f.ctx.root}/collaborators/Alice/permission`] = { permission: 'read' };
    if (change === 'bot') pr.user.type = 'Bot';
    await c.notice(f.api, event, f.env);
    assert.equal(f.calls.filter(x => x.method === 'POST').length, 0);
  }
  const f = fixture(); makePR(f); f.data[`${f.ctx.root}/pulls/3`].user = { login: 'Alice', type: 'User' };
  f.data[`${f.ctx.root}/collaborators/Alice/permission`] = new Error('lookup failed');
  await assert.rejects(c.notice(f.api, event, f.env), /lookup failed/);
});
