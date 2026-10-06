'use strict';
// Runs inside the network-isolated container with no real provider/GitHub keys.
const fs = require('node:fs');
const { spawn, execFileSync } = require('node:child_process');
const runtime = require('./runtime.json');
const { startForwarder, close } = require('./gateway.cjs');
const { config, parseEvents } = require('./policy.cjs');
const { verificationPrompt, assertInspection } = require('./review.cjs');
async function main() {
  const request = JSON.parse(fs.readFileSync('/config/request.json', 'utf8'));
  for (const [name, expected] of [['opencode', runtime.version], ['rg', runtime.ripgrep.version]]) {
    const version = execFileSync(`/tools/${name}`, ['--version'], { encoding: 'utf8', timeout: 10000,
      env: { PATH: process.env.PATH, HOME: '/tmp' } }).trim();
    if ((name === 'opencode' ? version : version.split('\n')[0].split(' ')[1]) !== expected) throw new Error('Pinned tool version mismatch.');
  }
  const passes = request.mode === 'review' ? request.settings.reviewPasses : 1;
  if (request.mode === 'review' && ![2, 3].includes(passes)) throw new Error('Invalid pass count.');
  const proxy = await startForwarder('/gateway/api.sock');
  const settings = { ...request.settings, baseURL: `http://127.0.0.1:${proxy.address().port}/v1` };
  fs.writeFileSync('/tmp/opencode.json', JSON.stringify(config(settings, request.mode)));
  let prompt = request.prompt, captured;
  try {
    for (let pass = 1; pass <= passes; pass++) {
      const child = spawn('/tools/opencode', ['run', '--pure', '--format', 'json', '--agent', 'carlos',
        '--model', `carlos/${request.settings.model}`], {
      cwd: '/work', timeout: Math.floor(25 * 60 * 1000 / passes),
      env: { PATH: `/tools:${process.env.PATH}`, HOME: `/tmp/home-${pass}`, XDG_CONFIG_HOME: `/tmp/home-${pass}/config`,
        XDG_DATA_HOME: `/tmp/home-${pass}/data`, XDG_CACHE_HOME: `/tmp/home-${pass}/cache`,
        OPENCODE_API_KEY: 'local-gateway-placeholder', OPENCODE_CONFIG: '/tmp/opencode.json',
        OPENCODE_DISABLE_PROJECT_CONFIG: 'true', OPENCODE_DISABLE_CLAUDE_CODE: 'true',
        OPENCODE_DISABLE_EXTERNAL_SKILLS: 'true', OPENCODE_DISABLE_DEFAULT_PLUGINS: 'true',
        OPENCODE_DISABLE_LSP_DOWNLOAD: 'true', OPENCODE_DISABLE_AUTOUPDATE: 'true',
        OPENCODE_DISABLE_MODELS_FETCH: 'true', OPENCODE_DISABLE_EMBEDDED_WEB_UI: 'true', OPENCODE_PURE: 'true', CI: 'true' },
      });
      let out = '', size = 0, error = null;
      child.stdout.on('data', data => { size += data.length; if (size > 20 * 1024 * 1024) {
        error = 'CLI output limit exceeded.'; child.kill('SIGKILL');
      } else out += data; });
      child.stderr.resume(); // never log raw provider stderr
      child.on('error', () => { error = 'CLI startup failed.'; });
      const code = await new Promise(resolve => { child.on('close', resolve); child.stdin.end(prompt); });
      if (error) throw new Error(error);
      const response = parseEvents(out, code);
      if (request.mode === 'review') assertInspection(out, Boolean(request.baseline));
      captured = { code, events: out, error: null, reviewPasses: passes };
      if (pass < passes) prompt = verificationPrompt(request, response, pass + 1, passes);
    }
  } finally { await close(proxy); }
  process.stdout.write(JSON.stringify(captured));
}
main().catch(() => { process.stdout.write(JSON.stringify({ code: 1, events: '', error: 'Required OpenCode pass failed; no verified result is available.' })); process.exitCode = 1; });
