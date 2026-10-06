'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync, execFileSync } = require('node:child_process');
const p = require('./policy.cjs');
const runtime = require('./runtime.json');
const env = process.env;

function git(source, args) {
  return execFileSync('git', ['-c', 'core.hooksPath=/dev/null', '-c', 'core.fsmonitor=false', '-C', source, ...args],
    { maxBuffer: 32 * 1024 * 1024 });
}

async function install(dir) {
  fs.mkdirSync(dir, { recursive: true });
  const response = await fetch(runtime.url, { signal: AbortSignal.timeout(120000) });
  if (!response.ok) throw new Error(`Pinned CLI download failed (HTTP ${response.status}).`);
  const data = Buffer.from(await response.arrayBuffer());
  if (crypto.createHash('sha256').update(data).digest('hex') !== runtime.sha256) throw new Error('OpenCode archive integrity check failed.');
  const archive = path.join(dir, 'opencode.tar.gz');
  fs.writeFileSync(archive, data);
  execFileSync('tar', ['-xzf', archive, '-C', dir, 'opencode']);
  fs.chmodSync(path.join(dir, 'opencode'), 0o755);
  const version = execFileSync(path.join(dir, 'opencode'), ['--version'], { encoding: 'utf8' }).trim();
  if (version !== runtime.version) throw new Error('Installed CLI version does not match the pinned release.');
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

async function main() {
  const root = env.WORK_DIR;
  if (process.argv[2] === 'install') return install(env.TOOLS_DIR);
  if (!env.OPENCODE_API_KEY) throw new Error('Missing OPENCODE_API_KEY secret.');
  const request = JSON.parse(fs.readFileSync(`${env.REQUEST_DIR}/request.json`, 'utf8'));
  if (request.source !== env.EXPECTED_SOURCE) throw new Error('Request source does not match authorized checkout.');
  fs.mkdirSync(root, { recursive: true });
  const archive = path.join(path.dirname(root), 'opencode-source.tar');
  git(env.SOURCE_DIR, ['archive', '--format=tar', `--output=${archive}`, request.source]);
  execFileSync('tar', ['-xf', archive, '-C', root]);
  fs.unlinkSync(archive);
  // A separate trusted checkout owns the Git index; the container never mounts its .git directory.
  fs.writeFileSync(`${env.REQUEST_DIR}/opencode.json`, JSON.stringify(p.config(request.settings, request.mode)));
  const uid = process.getuid(), gid = process.getgid();
  const args = ['run', '--rm', '--name', `carlos-opencode-${env.GITHUB_RUN_ID}`, '--read-only',
    '--cap-drop=ALL', '--security-opt=no-new-privileges', '--pids-limit=256', '--memory=6g', '--cpus=2',
    '--user', `${uid}:${gid}`, '--tmpfs', '/tmp:rw,exec,size=2g,mode=1777',
    '-v', `${root}:/work${request.mode === 'implement' ? '' : ':ro'}`,
    '-v', `${env.REQUEST_DIR}:/config:ro`, '-v', `${env.TOOLS_DIR}:/tools:ro`,
    '-v', `${__dirname}:/runner:ro`, '-e', 'OPENCODE_API_KEY',
    runtime.image, 'node', '/runner/entry.cjs'];
  const result = spawnSync('docker', args, { encoding: 'utf8', maxBuffer: 24 * 1024 * 1024,
    timeout: 27 * 60 * 1000, env: { PATH: env.PATH, HOME: env.HOME, OPENCODE_API_KEY: env.OPENCODE_API_KEY } });
  if (result.error || result.status !== 0) throw new Error('Isolated OpenCode run failed or timed out; check model credentials, endpoint, and container availability.');
  let captured;
  try { captured = JSON.parse(result.stdout); } catch { throw new Error('Invalid isolated runner output; refusing to publish.'); }
  if (captured.error) throw new Error(captured.error);
  const response = p.parseEvents(captured.events, captured.code);
  const files = collect(env.SOURCE_DIR, root, request.source);
  if (request.mode !== 'implement' && files.length) throw new Error('Read-only operation changed the source tree.');
  const bundle = p.validateBundle({ version: 1, source: request.source, response, files }, request.source);
  redactCheck(response, env.OPENCODE_API_KEY);
  for (const file of files) if (file.content !== null) redactCheck(Buffer.from(file.content, 'base64').toString('utf8'), env.OPENCODE_API_KEY);
  fs.mkdirSync(env.RESULT_DIR, { recursive: true });
  fs.writeFileSync(`${env.RESULT_DIR}/result.json`, JSON.stringify(bundle));
  console.log(`Validated generation: ${files.length} changed files. Raw provider output was not logged.`);
}

module.exports = { collect, redactCheck, install };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
