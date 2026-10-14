/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createRequire} = require('node:module');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const from = (name) => createRequire(require.resolve(name));

function isolated(source, extraEnv = {}) {
  const result = spawnSync(process.execPath, ['--max-old-space-size=256', '-e', source], {
    cwd: root,
    encoding: 'utf8',
    timeout: 15000,
    env: {...process.env, ...extraEnv},
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr || result.stdout);
}

test('unpatched sprintf and HTTP caching implementations are absent from the locked tree', () => {
  const {packages} = require('../package-lock.json');
  for (const name of ['sprintf-js', 'http-cache-semantics', 'cacheable-request', 'got']) {
    assert.ok(!Object.keys(packages).some((key) => key.endsWith(`/node_modules/${name}`)
      || key === `node_modules/${name}`), `${name} must remain removed`);
  }
});

test('brace consumers retain expansion, compilation, AST and glob behavior', () => {
  // This is a reviewed MIT derivative, not an upstream patched release:
  // https://github.com/dieub/braces-depth-guard/commit/305a2e4bfe324bb53c336c1b03387ee1251c926f
  // Resolve through both real consumers so a second vulnerable copy cannot hide.
  for (const consumer of ['micromatch', 'chokidar']) {
    const consumerRequire = from(consumer);
    const braces = consumerRequire('braces');
    const bridgeRequire = createRequire(consumerRequire.resolve('braces'));
    assert.equal(consumerRequire('braces/package.json').name, '@carlos/braces-compat');
    assert.equal(braces, bridgeRequire('@dieub/braces-depth-guard'));
    assert.deepEqual(braces.expand('docs/{api,guide}/{01..02}.md'), [
      'docs/api/01.md', 'docs/api/02.md', 'docs/guide/01.md', 'docs/guide/02.md',
    ]);
    assert.deepEqual(braces(['{a,b}', '{b,c}'], {expand: true, nodupes: true}), ['a', 'b', 'c']);
    assert.equal(braces.compile('docs/{api,guide}.md'), 'docs/(api|guide).md');
    const ast = braces.parse('docs/{api,{guide,faq}}.md');
    assert.equal(braces.stringify(ast), 'docs/{api,{guide,faq}}.md');
    assert.deepEqual(braces.expand(ast), ['docs/api.md', 'docs/guide.md', 'docs/faq.md']);
    assert.deepEqual(braces.expand('literal/\\{a,b\\}.md'), ['literal/{a,b}.md']);
  }
  const micromatch = require('micromatch');
  assert.deepEqual(micromatch(['docs/a.md', 'docs/b.mdx', 'docs/skip.md', 'src/a.js'],
    ['docs/*.{md,mdx}', '!**/skip.md']), ['docs/a.md', 'docs/b.mdx']);
  assert.deepEqual(micromatch.braceExpand('docs/{api,guide}.md'), ['docs/api.md', 'docs/guide.md']);
});

test('braces rejects advisory payloads and direct AST depth before stack exhaustion', () => {
  // GHSA-vfj7-8cjw-p6xm: short inputs with thousands of containers overflow
  // upstream's recursive walkers. Isolate the test to bound regressions in CI.
  isolated(`
    const assert = require('node:assert/strict');
    const {createRequire} = require('node:module');
    for (const consumer of ['micromatch', 'chokidar']) {
      const braces = createRequire(require.resolve(consumer))('braces');
      // The earlier GHSA-grv7-fg5c-xmjg uses imbalanced input, not a valid pair.
      assert.throws(() => braces.parse('{'.repeat(4000)), /exceeds max depth/);
      for (const [open, close] of [['{', '}'], ['(', ')']]) {
        const input = open.repeat(4000) + 'a,b' + close.repeat(4000);
        for (const operation of [braces, braces.parse, braces.compile, braces.expand, braces.stringify]) {
          assert.throws(() => operation(input), (error) =>
            error instanceof SyntaxError && /depth/i.test(error.message)
            && !/call stack/i.test(error.message));
        }
      }
      const ast = {type: 'root', nodes: []};
      let parent = ast;
      for (let i = 0; i < 4000; i++) {
        const child = {type: 'brace', nodes: [], parent};
        parent.nodes.push(child);
        parent = child;
      }
      for (const operation of [braces.compile, braces.expand, braces.stringify]) {
        assert.throws(() => operation(ast), /exceeds max depth/);
      }
      assert.throws(() => braces.parse('{'.repeat(101) + 'x' + '}'.repeat(101),
        {maxDepth: Infinity}), /exceeds max depth/);
      assert.throws(() => braces.parse('x'.repeat(10001), {maxLength: NaN}), /maxLength/);
    }
  `);
});

test('chokidar still watches brace globs after the braces replacement', async (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-dependency-watch-'));
  fs.writeFileSync(path.join(dir, 'existing.md'), '# Existing');
  fs.writeFileSync(path.join(dir, 'ignored.txt'), 'Not Markdown');
  const seen = [];
  const watcher = require('chokidar').watch(path.join(dir, '*.{md,mdx}'));
  watcher.on('add', (file) => seen.push(path.basename(file)));
  t.after(async () => {
    await watcher.close();
    fs.rmSync(dir, {recursive: true, force: true});
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('watcher did not become ready')), 5000);
    watcher.once('ready', () => { clearTimeout(timer); resolve(); });
    watcher.once('error', (error) => { clearTimeout(timer); reject(error); });
  });
  // ready follows the complete initial scan, so exclusion needs no timed sleep.
  assert.deepEqual(seen, ['existing.md']);
  const added = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('brace glob did not detect Markdown')), 5000);
    watcher.once('add', (file) => { clearTimeout(timer); resolve(path.basename(file)); });
  });
  fs.writeFileSync(path.join(dir, 'article.mdx'), '# A fixture');
  assert.equal(await added, 'article.mdx');
});

test('js-yaml 3 CLI and gray-matter retain their argparse and YAML contracts', () => {
  const grayRequire = from('gray-matter');
  const yamlRequire = createRequire(grayRequire.resolve('js-yaml'));
  assert.equal(yamlRequire('argparse/package.json').name, '@carlos/argparse-compat');
  const cli = path.join(path.dirname(grayRequire.resolve('js-yaml/package.json')), 'bin/js-yaml.js');
  const run = (args, input) => spawnSync(process.execPath, [cli, ...args], {
    encoding: 'utf8', input, timeout: 5000,
  });
  const help = run(['--help']);
  assert.equal(help.status, 0, help.stderr);
  assert.match(help.stdout, /--compact/);
  for (const option of ['--version', '-v']) {
    const version = run([option]);
    assert.equal(version.status, 0, version.stderr);
    assert.equal(version.stdout.trim(), grayRequire('js-yaml/package.json').version);
  }
  const yaml = run([], 'title: CARLOS\ntags: [security, docs]\n');
  assert.equal(yaml.status, 0, yaml.stderr);
  assert.deepEqual(JSON.parse(yaml.stdout), {title: 'CARLOS', tags: ['security', 'docs']});
  const json = run([], '{"title":"CARLOS","enabled":true}');
  assert.equal(json.status, 0, json.stderr);
  assert.deepEqual(grayRequire('js-yaml').safeLoad(json.stdout), {title: 'CARLOS', enabled: true});
  const invalid = run(['--compact'], 'title: [unterminated');
  assert.equal(invalid.status, 1);
  assert.match(invalid.stderr, /YAMLException/);
  const matter = require('gray-matter')('---\ntitle: CARLOS\nsidebar_position: 2\n---\n# Guide\n');
  assert.deepEqual(matter.data, {title: 'CARLOS', sidebar_position: 2});
  assert.equal(matter.content, '# Guide\n');
  // js-yaml 4 uses the modern argparse API and shares the override.
  const modernCli = path.join(path.dirname(require.resolve('js-yaml/package.json')), 'bin/js-yaml.js');
  const modernVersion = spawnSync(process.execPath, [modernCli, '--version'], {encoding: 'utf8', timeout: 5000});
  assert.equal(modernVersion.status, 0, modernVersion.stderr);
  assert.equal(modernVersion.stdout.trim(), require('js-yaml/package.json').version);
  const modernYaml = spawnSync(process.execPath, [modernCli], {encoding: 'utf8', input: 'enabled: true\n', timeout: 5000});
  assert.equal(modernYaml.status, 0, modernYaml.stderr);
  assert.deepEqual(JSON.parse(modernYaml.stdout), {enabled: true});
});

test('update-notifier fetches version metadata through the replacement without HTTP cache reuse', () => {
  // Mock the public fetch boundary, not latest-version or notifier internals.
  // Two responses at the same URL must produce two independent results.
  isolated(`
    const assert = require('node:assert/strict');
    let requests = 0;
    globalThis.fetch = async (request) => {
      const url = new URL(typeof request === 'string' ? request : request.url);
      assert.equal(url.hostname, 'registry.npmjs.org');
      assert.equal(url.pathname, '/carlos-dependency-fixture');
      const version = ++requests === 1 ? '2.4.0' : '3.0.0';
      return new Response(JSON.stringify({
        'dist-tags': {next: version},
        versions: {[version]: {name: 'carlos-dependency-fixture', version}}
      }), {headers: {'content-type': 'application/json', 'set-cookie': 'fixture=private'}});
    };
    (async () => {
      const {default: updateNotifier} = await import('update-notifier');
      const notifier = updateNotifier({pkg: {name: 'CARLOS-dependency-fixture', version: '1.0.0'}, distTag: 'next'});
      assert.equal(typeof notifier.notify, 'function');
      const first = await notifier.fetchInfo();
      const second = await notifier.fetchInfo();
      assert.deepEqual(first, {latest: '2.4.0', current: '1.0.0', type: 'major', name: 'CARLOS-dependency-fixture'});
      assert.equal(second.latest, '3.0.0');
      assert.equal(requests, 2);
    })().catch((error) => { console.error(error); process.exitCode = 1; });
  `, {NODE_ENV: 'test', npm_config_registry: 'https://registry.npmjs.org/'});
});

test('Tinypool keeps Docusaurus worker options and ignores polluted execution options', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-dependency-pool-'));
  try {
    const legitimate = path.join(dir, 'worker.cjs');
    const malicious = path.join(dir, 'unselected.cjs');
    const preload = path.join(dir, 'preload.cjs');
    const marker = path.join(dir, 'unexpected-preload');
    fs.writeFileSync(legitimate, "const {workerData} = require('node:worker_threads'); module.exports = (n) => n * workerData[1].factor;");
    fs.writeFileSync(malicious, 'module.exports = () => -1;');
    fs.writeFileSync(preload, `require('node:fs').writeFileSync(${JSON.stringify(marker)}, 'executed');`);
    for (const pollution of ['execArgv', 'env', 'filename']) {
      isolated(`
        const assert = require('node:assert/strict');
        const fs = require('node:fs');
        (async () => {
          const {default: Tinypool} = await import('tinypool');
          const key = ${JSON.stringify(pollution)};
          const values = {execArgv: ['--require', ${JSON.stringify(preload)}],
            env: {NODE_OPTIONS: '--require=' + ${JSON.stringify(preload)}},
            filename: ${JSON.stringify(malicious)}};
          Object.prototype[key] = values[key];
          let pool;
          try {
            pool = new Tinypool({filename: ${JSON.stringify(legitimate)}, minThreads: 1, maxThreads: 1,
              concurrentTasksPerWorker: 1, runtime: 'worker_threads', isolateWorkers: false,
              workerData: {factor: 2}});
            assert.equal(await pool.run(21, {signal: new AbortController().signal}), 42);
            assert.equal(await pool.run(3), 6);
            assert.ok(!fs.existsSync(${JSON.stringify(marker)}), 'inherited options must not execute a preload');
          } finally {
            delete Object.prototype[key];
            if (pool) await pool.destroy();
          }
        })().catch((error) => { console.error(error); process.exitCode = 1; });
      `);
    }
  } finally {
    fs.rmSync(dir, {recursive: true, force: true});
  }
});

test('selector-parser 7 retains CSS transformations and bounds the flat-selector advisory', () => {
  isolated(`
    const assert = require('node:assert/strict');
    const parser = require('postcss-selector-parser');
    const selector = 'svg|a[href^="https"]:not(.hidden), .card > #content';
    assert.equal(parser().processSync(selector), selector);
    let count = 0;
    parser((root) => root.walkClasses(() => count++)).processSync('.a'.repeat(50000));
    assert.equal(count, 50000);
    (async () => {
      const result = await require('postcss')([require('cssnano')({preset: 'default'})])
        .process('.a, .b { color: #ffffff; margin: 0px 0px 0px 0px; }', {from: undefined});
      assert.equal(result.css, '.a,.b{color:#fff;margin:0}');
    })().catch((error) => { console.error(error); process.exitCode = 1; });
  `);
});
