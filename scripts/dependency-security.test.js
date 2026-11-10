/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createRequire} = require('node:module');
const {spawnSync} = require('node:child_process');
const {randomUUID} = require('node:crypto');

const root = path.resolve(__dirname, '..');
const from = (name) => createRequire(require.resolve(name));

/** Run a bounded child check and require its awaited assertions to finish. */
function isolated(source, extraEnv = {}) {
  const completion = `dependency-check-${randomUUID()}`;
  const program = `(async () => {\n${source}\n})().then(() => {
    console.log(${JSON.stringify(completion)});
  }, (error) => { console.error(error); process.exitCode = 1; });`;
  const result = spawnSync(process.execPath, ['--max-old-space-size=256', '-e', program], {
    cwd: root,
    encoding: 'utf8',
    timeout: 15000,
    env: {...process.env, ...extraEnv},
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.ok(result.stdout.split(/\r?\n/).includes(completion), 'isolated check did not report completion');
}

test('isolated checks cannot pass before asynchronous assertions finish', () => {
  isolated('await Promise.resolve();');
  assert.throws(() => isolated('await new Promise(() => {});'), /did not report completion/);
  assert.throws(() => isolated('await Promise.resolve(); throw new Error("async assertion failed");'),
    /async assertion failed/);
});

/** Reject removed npm identities, following links and inferring names when npm omits them. */
function assertRemovedPackagesAbsent(packages) {
  const removed = ['sprintf-js', 'http-cache-semantics', 'cacheable-request', 'got', 'deepmerge'];
  const packageName = (location) => {
    const seen = new Set();
    while (true) {
      assert.ok(!seen.has(location), `Cyclic npm lockfile link target (${location})`);
      seen.add(location);
      assert.ok(Object.hasOwn(packages, location), `Missing npm lockfile link target (${location})`);
      const entry = packages[location];
      if (!entry.link) {
        // Local targets can live outside node_modules; preserve any scope in the final path.
        const name = entry.name ?? location.match(/(?:^|\/)((?:@[^/]+\/)?[^/]+)$/)?.[1];
        assert.ok(typeof name === 'string' && name.length > 0, `Missing npm package identity (${location})`);
        return name;
      }
      assert.equal(typeof entry.resolved, 'string', `Missing npm lockfile link target (${location})`);
      location = entry.resolved; // Link targets are relative to the lockfile root.
    }
  };
  for (const location of Object.keys(packages)) {
    if (!location) continue; // The root project is not an installed dependency.
    const name = packageName(location);
    assert.ok(!removed.includes(name), `${name} must remain removed (${location})`);
  }
}

test('unpatched sprintf and HTTP caching implementations are absent from the locked tree', () => {
  assertRemovedPackagesAbsent(require('../package-lock.json').packages);
});

test('removed implementations are rejected under direct, nested and aliased install names', () => {
  // npm lockfile v3 alias entries retain name/version even when resolved is omitted.
  const aliases = {
    'node_modules/sprintf-alias': {name: 'sprintf-js', version: '1.0.3'},
    'node_modules/@fixture/policy-alias': {name: 'http-cache-semantics', version: '4.2.0'},
    'node_modules/consumer/node_modules/request-alias': {name: 'cacheable-request', version: '10.2.14'},
    'node_modules/consumer/node_modules/@fixture/client-alias': {name: 'got', version: '12.6.1'},
    'node_modules/merge-alias': {name: 'deepmerge', version: '4.3.1'},
  };
  for (const [location, entry] of Object.entries(aliases)) {
    const expected = {name: 'AssertionError', message: `${entry.name} must remain removed (${location})`};
    assert.throws(() => assertRemovedPackagesAbsent({[location]: entry}), expected);
    for (const prefix of ['node_modules/', 'node_modules/consumer/node_modules/']) {
      const direct = `${prefix}${entry.name}`;
      assert.throws(() => assertRemovedPackagesAbsent({[direct]: {version: entry.version}}), {
        name: 'AssertionError', message: `${entry.name} must remain removed (${direct})`,
      });
    }
  }
});

test('SVGR retains SVG optimization and JSX generation without unused deepmerge', () => {
  const pluginRequire = from('@svgr/webpack');
  const optimize = pluginRequire('@svgr/plugin-svgo');
  assert.equal(pluginRequire('@svgr/plugin-svgo/package.json').name, '@carlos/svgr-plugin-svgo');
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">'
    + '<defs><linearGradient id="paint"><stop stop-color="red"/></linearGradient></defs>'
    + '<path data-fixture="remove" fill="url(#paint)" d="M0 0h24v24H0z"/></svg>';
  const state = {componentName: 'SecurityIcon', filePath: '/fixtures/security.svg'};
  const optimized = optimize(svg, {svgo: true, icon: true}, state);
  assert.match(optimized, /viewBox="0 0 24 24"/);
  assert.match(optimized, /id="security_svg__/);
  assert.match(optimized, /fill="url\(#security_svg__/);
  assert.equal(optimize(svg, {svgo: false}, state), svg);
  const custom = optimize(svg, {svgo: true, svgoConfig: {
    plugins: [{name: 'removeAttrs', params: {attrs: 'data-fixture'}}],
  }}, state);
  assert.doesNotMatch(custom, /data-fixture/);
  const {transform} = pluginRequire('@svgr/core');
  const jsx = transform.sync(svg, {
    icon: true, plugins: [optimize, pluginRequire('@svgr/plugin-jsx')],
  }, state);
  assert.match(jsx, /const SecurityIcon =/);
  assert.match(jsx, /viewBox="0 0 24 24"/);
  assert.match(jsx, /export default SecurityIcon/);
});

test('serialization escapes a script terminator at the end of a function body', () => {
  const serialize = require('serialize-javascript');
  function f() {}
  function g() {}
  f.toString = () => 'function f(){ return 1 }//</script';
  g.toString = () => 'function g(x){ return x } > <img src=x onerror=alert(1)>';
  const output = serialize({a: {f}, g}, {space: 2});
  assert.doesNotMatch(output, /<\/script(?:\s|\/|>)/i);
  const ordinary = serialize({fn: function double(n) { return n * 2; }});
  assert.equal(Function(`return (${ordinary});`)().fn(3), 6);
});

test('allowed packages and aliases are not confused with removed implementations', () => {
  const {packages} = require('../package-lock.json');
  assert.doesNotThrow(() => assertRemovedPackagesAbsent({
    '': {name: 'got', version: '1.0.0'},
    'node_modules/argparse-upstream': packages['node_modules/argparse-upstream'],
    'node_modules/gray-matter': packages['node_modules/gray-matter'],
    'node_modules/@fixture/got': {version: '1.0.0'},
    'node_modules/consumer/node_modules/got-helper': {version: '1.0.0'},
    'node_modules/got': {name: '@fixture/allowed-client', version: '1.0.0'},
    'node_modules/cache-helper': {name: 'http-cache-semantics-helper', version: '1.0.0'},
    'scripts/npm/local-helper': {name: '@fixture/local-helper', version: '1.0.0'},
  }));
});

test('removed implementations are rejected behind local npm links', () => {
  // npm 10.9.9 Arborist serialization omits name when a local target's folder matches it.
  for (const [name, version] of Object.entries({
    'sprintf-js': '1.0.3', 'http-cache-semantics': '4.2.0', 'cacheable-request': '10.2.14', got: '12.6.1',
  })) {
    const target = `vendor/${name}`;
    assert.throws(() => assertRemovedPackagesAbsent({
      '': {name: 'fixture', version: '1.0.0', dependencies: {'client-alias': `file:${target}`}},
      'node_modules/client-alias': {resolved: target, link: true},
      [target]: {version},
    }), {name: 'AssertionError', message: `${name} must remain removed (node_modules/client-alias)`});
  }
});

test('local npm links use allowed target identities instead of removed install names', () => {
  // Both target shapes were verified with npm 10.9.9's lockfile serializer.
  assert.doesNotThrow(() => assertRemovedPackagesAbsent({
    '': {name: 'fixture', version: '1.0.0', dependencies: {
      got: 'file:vendor/allowed-client', 'sprintf-js': 'file:vendor/@fixture/got',
    }},
    'node_modules/got': {resolved: 'vendor/allowed-client', link: true},
    'vendor/allowed-client': {name: '@fixture/allowed-client', version: '1.0.0'},
    'node_modules/sprintf-js': {resolved: 'vendor/@fixture/got', link: true},
    'vendor/@fixture/got': {version: '1.0.0'},
  }));
});

test('npm links with missing targets fail closed', () => {
  for (const entry of [{link: true}, {link: true, resolved: 'vendor/missing'}, {link: true, resolved: 'toString'}]) {
    assert.throws(() => assertRemovedPackagesAbsent({'node_modules/client-alias': entry}),
      {name: 'AssertionError', message: /Missing npm lockfile link target/});
  }
});

test('npm links with cyclic targets fail closed', () => {
  for (const target of ['node_modules/client-alias', 'vendor/cycle']) {
    assert.throws(() => assertRemovedPackagesAbsent({
      'node_modules/client-alias': {link: true, resolved: target},
      'vendor/cycle': {link: true, resolved: 'node_modules/client-alias'},
    }), {name: 'AssertionError', message: /Cyclic npm lockfile link target/});
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
    await (async () => {
      const {default: updateNotifier} = await import('update-notifier');
      const notifier = updateNotifier({pkg: {name: 'CARLOS-dependency-fixture', version: '1.0.0'}, distTag: 'next'});
      assert.equal(typeof notifier.notify, 'function');
      const first = await notifier.fetchInfo();
      const second = await notifier.fetchInfo();
      assert.deepEqual(first, {latest: '2.4.0', current: '1.0.0', type: 'major', name: 'CARLOS-dependency-fixture'});
      assert.equal(second.latest, '3.0.0');
      assert.equal(requests, 2);
    })();
  `, {NODE_ENV: 'test', npm_config_registry: 'https://registry.npmjs.org/'});
});

test('Tinypool keeps Docusaurus worker options and ignores polluted execution options', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-dependency-pool-'));
  try {
    const legitimate = path.join(dir, 'worker.cjs');
    const malicious = path.join(dir, 'unselected.cjs');
    const preload = path.join(dir, 'preload.cjs');
    const marker = path.join(dir, 'unexpected-preload');
    fs.writeFileSync(legitimate, `
      const {workerData} = require('node:worker_threads');
      const assert = require('node:assert/strict');
      module.exports = (n) => {
        assert.equal(process.env.CARLOS_DEPENDENCY_POLLUTED_ENV, undefined);
        return n * workerData[1].factor;
      };
    `);
    fs.writeFileSync(malicious, 'module.exports = () => -1;');
    fs.writeFileSync(preload, `require('node:fs').writeFileSync(${JSON.stringify(marker)}, 'executed');`);
    for (const pollution of ['execArgv', 'env', 'filename']) {
      isolated(`
        const assert = require('node:assert/strict');
        const fs = require('node:fs');
        await (async () => {
          const {default: Tinypool} = await import('tinypool');
          const key = ${JSON.stringify(pollution)};
          const values = {execArgv: ['--require', ${JSON.stringify(preload)}],
            env: {NODE_OPTIONS: '--require=' + ${JSON.stringify(preload)}, CARLOS_DEPENDENCY_POLLUTED_ENV: 'polluted'},
            filename: ${JSON.stringify(malicious)}};
          delete process.env.CARLOS_DEPENDENCY_POLLUTED_ENV;
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
        })();
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
    await (async () => {
      const result = await require('postcss')([require('cssnano')({preset: 'default'})])
        .process('.a, .b { color: #ffffff; margin: 0px 0px 0px 0px; }', {from: undefined});
      assert.equal(result.css, '.a,.b{color:#fff;margin:0}');
    })();
  `);
});
