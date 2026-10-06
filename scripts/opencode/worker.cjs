'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn, execFileSync } = require('node:child_process');
const { startGateway, close } = require('./gateway.cjs');
const p = require('./policy.cjs');
const runtime = require('./runtime.json');
const review = require('./review.cjs');
const env = process.env;

function git(source, args) {
  return execFileSync('git', ['-c', 'core.hooksPath=/dev/null', '-c', 'core.fsmonitor=false', '-C', source, ...args],
    { maxBuffer: 32 * 1024 * 1024 });
}

async function install(dir) {
  fs.mkdirSync(dir, { recursive: true });
  for (const [name, pin, member] of [['opencode', runtime, 'opencode'], ['rg', runtime.ripgrep, runtime.ripgrep.member]]) {
    const response = await fetch(pin.url, { signal: AbortSignal.timeout(120000) });
    if (!response.ok) throw new Error(`Pinned ${name} download failed (HTTP ${response.status}).`);
    const data = Buffer.from(await response.arrayBuffer());
    if (crypto.createHash('sha256').update(data).digest('hex') !== pin.sha256) throw new Error(`${name} archive integrity check failed.`);
    const archive = path.join(dir, `${name}.tar.gz`);
    fs.writeFileSync(archive, data);
    execFileSync('tar', ['-xzf', archive, '-C', dir, '--strip-components=' + (member.split('/').length - 1), member]);
    fs.chmodSync(path.join(dir, name), 0o755);
    // Version execution happens only inside the isolated container, never on the host.
  }
}

function collect(source, work, sha) {
  const baseline = new Map(git(source, ['ls-tree', '-r', '-z', sha]).toString('utf8').split('\0').filter(Boolean).map(row => {
    const [meta, name] = row.split('\t');
    const [mode, type, hash] = meta.split(' ');
    return [name, { mode, type, hash }];
  }));
  const other = git(source, ['--work-tree=' + work, 'ls-files', '--others', '--exclude-standard', '-z']).toString('utf8').split('\0').filter(Boolean);
  const names = new Set([...baseline.keys(), ...other]);
  const files = [];
  let total = 0;
  for (const name of names) {
    const before = baseline.get(name);
    // Never follow a symlink in any parent, including one introduced by the agent.
    let parent = work;
    for (const segment of name.split('/').slice(0, -1)) {
      parent = path.join(parent, segment);
      if (fs.existsSync(parent) && !fs.lstatSync(parent).isDirectory()) throw new Error('Generated tree contains an unsafe parent path.');
    }
    const absolute = path.join(work, name);
    let stat;
    try { stat = fs.lstatSync(absolute); } catch (error) { if (error.code !== 'ENOENT') throw error; }
    if (!stat) { if (before) files.push({ path: p.safePath(name), content: null }); continue; }
    if (stat.isSymbolicLink()) {
      if (before?.mode === '120000' && p.blobHash(Buffer.from(fs.readlinkSync(absolute))) === before.hash) continue;
      throw new Error('Generated symlinks are not supported.');
    }
    if (stat.isDirectory() && before?.type === 'commit') continue;
    if (!stat.isFile()) throw new Error('Generated special files are not supported.');
    // Read unchanged large repository blobs without treating them as generated output.
    const mode = stat.mode & 0o111 ? '100755' : '100644';
    if (stat.nlink !== 1) throw new Error('Generated hard links are not supported.');
    // Match the repository's Git blob-ID format solely to omit unchanged files.
    // A collision here omits a proposed edit; it cannot authorize new content.
    // nosemgrep: javascript.node-stdlib.cryptography.crypto-weak-algorithm.crypto-weak-algorithm
    const hash = crypto.createHash('sha1').update(`blob ${stat.size}\0`);
    const fd = fs.openSync(absolute, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
    const block = Buffer.alloc(1024 * 1024);
    try { let n; while ((n = fs.readSync(fd, block, 0, block.length, null))) hash.update(block.subarray(0, n)); }
    finally { fs.closeSync(fd); }
    if (before?.hash === hash.digest('hex') && before.mode === mode) continue;
    if (stat.size > p.MAX_BYTES) throw new Error('Generated file exceeds the 4 MiB limit.');
    const bytes = fs.readFileSync(absolute);
    total += bytes.length;
    if (total > p.MAX_BYTES || files.length >= p.MAX_FILES) throw new Error('Generated changes exceed 100 files or 4 MiB.');
    files.push({ path: p.safePath(name), mode, content: bytes.toString('base64') });
  }
  return files;
}

function redactCheck(text, key) {
  if ([key, Buffer.from(key).toString('base64'), encodeURIComponent(key)].some(value => value && text.includes(value))) {
    throw new Error('Provider credential detected in generated output; refusing to upload it.');
  }
}

function reviewText(bundle, base) {
  const sections = [`OPENCODE GENERATED CHANGES — UNTRUSTED CONTENT
Source: ${bundle.source}
Target base: ${base}
Review every changed file before approving publication. This triggers existing CI with its configured secrets.
Compare originals at https://github.com/carlos-emr/carlos/tree/${bundle.source}
Do not execute downloaded content. Binary changes require separate inspection.

Agent report (untrusted):
${bundle.response}`];
  for (const file of bundle.files) {
    sections.push(`\n===== ${file.content === null ? 'DELETE' : 'WRITE'} ${file.path} (${file.mode || 'original mode'}) =====`);
    if (file.content !== null) {
      const bytes = Buffer.from(file.content, 'base64');
      const value = bytes.toString('utf8');
      sections.push(value.includes('\0') || !Buffer.from(value).equals(bytes) ? '[Binary: inspect base64 content in result.json before approval.]' : value);
    }
  }
  return sections.join('\n');
}

function containerArgs({ root, mode, requestDir, toolsDir, gatewayDir, runID, baselineRoot }) {
  const uid = process.getuid(), gid = process.getgid();
  return ['run', '--rm', '--name', `carlos-opencode-${runID}`, '--read-only',
    '--network=none',
    '--cap-drop=ALL', '--security-opt=no-new-privileges', '--pids-limit=256', '--memory=6g', '--cpus=2',
    '--user', `${uid}:${gid}`, '--tmpfs', '/tmp:rw,exec,size=2g,mode=1777',
    '-v', `${root}:/work${mode === 'implement' ? '' : ':ro'}`,
    '-v', `${requestDir}:/config:ro`, '-v', `${toolsDir}:/tools:ro`,
    '-v', `${__dirname}:/runner:ro`, '-v', `${gatewayDir}:/gateway:ro`,
    ...(baselineRoot ? ['-v', `${baselineRoot}:/baseline:ro`] : []),
    runtime.image, 'node', '/runner/entry.cjs'];
}

async function main() {
  const root = env.WORK_DIR;
  if (process.argv[2] === 'install') return install(env.TOOLS_DIR);
  if (!env.OPENCODE_API_KEY) throw new Error('Missing OPENCODE_API_KEY secret.');
  const request = JSON.parse(fs.readFileSync(`${env.REQUEST_DIR}/request.json`, 'utf8'));
  if (JSON.stringify(request.settings) !== JSON.stringify(p.settings(env, env.EXPECTED_ALIAS))) throw new Error('Request model configuration does not match authorization.');
  if (request.source !== env.EXPECTED_SOURCE) throw new Error('Request source does not match authorized checkout.');
  fs.mkdirSync(root, { recursive: true });
  const archive = path.join(path.dirname(root), 'opencode-source.tar');
  git(env.SOURCE_DIR, ['archive', '--format=tar', `--output=${archive}`, request.source]);
  execFileSync('tar', ['-xf', archive, '-C', root]);
  fs.unlinkSync(archive);
  let baselineRoot;
  if (request.baseline) {
    if (request.baseline !== env.EXPECTED_BASELINE) throw new Error('Review baseline does not match authorized checkout.');
    baselineRoot = env.BASELINE_WORK_DIR;
    fs.mkdirSync(baselineRoot, { recursive: true });
    git(env.BASELINE_SOURCE_DIR, ['archive', '--format=tar', `--output=${archive}`, request.baseline]);
    execFileSync('tar', ['-xf', archive, '-C', baselineRoot]);
    fs.unlinkSync(archive);
  }
  // A separate trusted checkout owns the Git index; the container never mounts its .git directory.
  const gatewayDir = path.join(path.dirname(root), 'opencode-gateway');
  fs.mkdirSync(gatewayDir, { recursive: true });
  const gateway = await startGateway({ socket: path.join(gatewayDir, 'api.sock'),
    baseURL: p.endpoint(env.OPENCODE_API_BASE_URL), model: request.settings.model, key: env.OPENCODE_API_KEY });
  const args = containerArgs({ root, mode: request.mode, requestDir: env.REQUEST_DIR,
    toolsDir: env.TOOLS_DIR, gatewayDir, runID: env.GITHUB_RUN_ID, baselineRoot });
  let result;
  try {
    result = await new Promise(resolve => {
      const child = spawn('docker', args, { timeout: 27 * 60 * 1000, env: { PATH: env.PATH, HOME: env.HOME } });
      let stdout = '', size = 0, error = null;
      child.stdout.on('data', data => { size += data.length; if (size > 24 * 1024 * 1024) {
        error = 'Output limit exceeded.'; child.kill('SIGKILL');
      } else stdout += data; });
      child.stderr.resume();
      child.on('error', () => { error = 'Container startup failed.'; });
      child.on('close', status => resolve({ status, stdout, error }));
    });
  } finally { await close(gateway); }
  if (result.error || result.status !== 0) throw new Error('Isolated OpenCode run failed or timed out; check model credentials, endpoint, and container availability.');
  let captured;
  try { captured = JSON.parse(result.stdout); } catch { throw new Error('Invalid isolated runner output; refusing to publish.'); }
  if (captured.error) throw new Error(captured.error);
  let response = p.parseEvents(captured.events, captured.code);
  if (request.mode === 'review') {
    if (captured.reviewPasses !== request.settings.reviewPasses) throw new Error('Not all required review passes completed.');
    response = review.render(response, root, request.source, captured.reviewPasses);
  }
  response = `Model: \`${request.settings.alias}\` (\`${request.settings.model}\`).\n\n${response}`;
  const files = collect(env.SOURCE_DIR, root, request.source);
  if (request.mode !== 'implement' && files.length) throw new Error('Read-only operation changed the source tree.');
  const bundle = p.validateBundle({ version: 1, source: request.source, response, files }, request.source);
  redactCheck(response, env.OPENCODE_API_KEY);
  redactCheck(response, env.OPENCODE_API_BASE_URL);
  for (const file of files) if (file.content !== null) {
    const text = Buffer.from(file.content, 'base64').toString('utf8');
    redactCheck(text, env.OPENCODE_API_KEY); redactCheck(text, env.OPENCODE_API_BASE_URL);
  }
  fs.mkdirSync(env.RESULT_DIR, { recursive: true });
  fs.writeFileSync(`${env.RESULT_DIR}/result.json`, JSON.stringify(bundle));
  fs.writeFileSync(`${env.RESULT_DIR}/review.txt`, reviewText(bundle, request.base));
  console.log(`Validated generation: ${files.length} changed files. Raw provider output was not logged.`);
}

module.exports = { collect, redactCheck, install, containerArgs, reviewText };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
