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

function sourceTree(source, sha) {
  if (!/^[a-f0-9]{40}$/.test(sha)) throw new Error('Invalid source commit.');
  const raw = git(source, ['ls-tree', '-r', '-l', '-z', sha]);
  const text = raw.toString('utf8');
  if (!Buffer.from(text).equals(raw)) throw new Error('Source paths must use UTF-8.');
  const entries = new Map();
  for (const row of text.split('\0').filter(Boolean)) {
    const match = /^(\d{6}) (blob|commit) ([a-f0-9]{40}) +([0-9]+|-)\t([\s\S]+)$/.exec(row);
    if (!match) throw new Error('Invalid source tree metadata.');
    const [, mode, type, hash, size, name] = match;
    if (type === 'commit') throw new Error('Submodules are not supported; refusing an incomplete source snapshot.');
    if (!['100644', '100755', '120000'].includes(mode) || /[\\\x00-\x1f\x7f]/.test(name) ||
        name.startsWith('/') || name.split('/').some(x => !x || x === '.' || x === '..' || x.toLowerCase() === '.git') ||
        entries.has(name)) throw new Error('Unsafe source tree entry.');
    entries.set(name, { mode, type, hash, size: Number(size) });
  }
  return entries;
}

function snapshot(source, root, sha) {
  const entries = sourceTree(source, sha);
  if ([...entries.values()].reduce((sum, entry) => sum + entry.size, 0) > 2 * 1024 ** 3) {
    throw new Error('Source snapshot exceeds the 2 GiB limit.');
  }
  fs.mkdirSync(root, { recursive: true });
  if (!fs.lstatSync(root).isDirectory() || fs.readdirSync(root).length) throw new Error('Snapshot destination must be an empty directory.');
  // Raw Git objects bypass export-ignore/export-subst, checkout filters and hooks.
  // Spool outside the agent mount so large repositories do not fill host memory.
  const spool = fs.mkdtempSync(path.join(path.dirname(root), 'opencode-blobs-'));
  const fd = fs.openSync(path.join(spool, 'objects'), 'wx+', 0o600);
  try {
    execFileSync('git', ['-c', 'core.hooksPath=/dev/null', '-c', 'core.fsmonitor=false', '-C', source, 'cat-file', '--batch'], {
      input: [...entries.values()].map(entry => entry.hash + '\n').join(''), stdio: ['pipe', fd, 'pipe'], timeout: 120000,
    });
    let position = 0;
    const read = bytes => {
      let offset = 0;
      while (offset < bytes.length) {
        const count = fs.readSync(fd, bytes, offset, bytes.length - offset, position);
        if (!count) throw new Error('Incomplete source object stream.');
        position += count; offset += count;
      }
      return bytes;
    };
    const byte = Buffer.alloc(1), block = Buffer.alloc(1024 * 1024);
    for (const [name, entry] of entries) {
      let header = '';
      while (read(byte)[0] !== 10) {
        header += byte.toString();
        if (header.length > 100) throw new Error('Invalid source object header.');
      }
      if (header !== `${entry.hash} blob ${entry.size}`) throw new Error('Source object metadata mismatch.');
      const target = path.join(root, name);
      let parent = root;
      for (const segment of name.split('/').slice(0, -1)) {
        parent = path.join(parent, segment);
        try { fs.mkdirSync(parent); } catch (error) { if (error.code !== 'EEXIST') throw error; }
        if (!fs.lstatSync(parent).isDirectory()) throw new Error('Unsafe source parent path.');
      }
      if (entry.mode === '120000') {
        if (entry.size > 4096) throw new Error('Source symlink is too large.');
        fs.symlinkSync(read(Buffer.alloc(entry.size)), target);
      } else {
        const out = fs.openSync(target, 'wx', entry.mode === '100755' ? 0o755 : 0o644);
        try {
          for (let left = entry.size; left > 0;) {
            const chunk = read(block.subarray(0, Math.min(left, block.length)));
            fs.writeFileSync(out, chunk); left -= chunk.length;
          }
        } finally { fs.closeSync(out); }
      }
      if (read(byte)[0] !== 10) throw new Error('Invalid source object boundary.');
    }
    if (position !== fs.fstatSync(fd).size) throw new Error('Unexpected extra source object data.');
  } finally { fs.closeSync(fd); fs.rmSync(spool, { recursive: true, force: true }); }
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
  const baseline = sourceTree(source, sha);
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
  snapshot(env.SOURCE_DIR, root, request.source);
  let baselineRoot;
  if (request.baseline) {
    if (request.baseline !== env.EXPECTED_BASELINE) throw new Error('Review baseline does not match authorized checkout.');
    baselineRoot = env.BASELINE_WORK_DIR;
    snapshot(env.BASELINE_SOURCE_DIR, baselineRoot, request.baseline);
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
  let response = p.parseEvents(captured.events, captured.code, request.mode === 'review');
  if (request.mode === 'review') {
    if (captured.reviewPasses !== request.settings.reviewPasses) throw new Error('Not all required review passes completed.');
    response = review.render(response, root, request.source, captured.reviewPasses);
  }
  response = `Model: \`${request.settings.alias}\` (\`${request.settings.model}\`).\n\n${response}`;
  const files = collect(env.SOURCE_DIR, root, request.source);
  if (request.mode !== 'implement' && files.length) throw new Error('Read-only operation changed the source tree.');
  const bundle = p.validateBundle({ version: 1, source: request.source, response, files }, request.source);
  redactCheck(response, env.OPENCODE_API_KEY);
  for (const file of files) if (file.content !== null) {
    const text = Buffer.from(file.content, 'base64').toString('utf8');
    redactCheck(text, env.OPENCODE_API_KEY);
  }
  fs.mkdirSync(env.RESULT_DIR, { recursive: true });
  fs.writeFileSync(`${env.RESULT_DIR}/result.json`, JSON.stringify(bundle));
  fs.writeFileSync(`${env.RESULT_DIR}/review.txt`, reviewText(bundle, request.base));
  console.log(`Validated generation: ${files.length} changed files. Raw provider output was not logged.`);
}

module.exports = { collect, snapshot, redactCheck, install, containerArgs, reviewText };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
