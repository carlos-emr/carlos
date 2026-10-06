'use strict';
// Runs inside the container. Only the provider key is passed into this process.
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const request = JSON.parse(fs.readFileSync('/config/request.json', 'utf8'));
const result = spawnSync('/tools/opencode', ['run', '--pure', '--format', 'json', '--agent', 'carlos',
  '--model', `carlos/${request.settings.model}`], {
  cwd: '/work', input: request.prompt, encoding: 'utf8', timeout: 25 * 60 * 1000,
  maxBuffer: 20 * 1024 * 1024,
  env: { PATH: process.env.PATH, HOME: '/tmp/home', XDG_CONFIG_HOME: '/tmp/home/config',
    XDG_DATA_HOME: '/tmp/home/data', XDG_CACHE_HOME: '/tmp/home/cache',
    OPENCODE_API_KEY: process.env.OPENCODE_API_KEY, OPENCODE_CONFIG: '/config/opencode.json',
    OPENCODE_DISABLE_PROJECT_CONFIG: 'true', OPENCODE_DISABLE_CLAUDE_CODE: 'true',
    OPENCODE_DISABLE_EXTERNAL_SKILLS: 'true', OPENCODE_DISABLE_DEFAULT_PLUGINS: 'true',
    OPENCODE_DISABLE_LSP_DOWNLOAD: 'true', OPENCODE_DISABLE_AUTOUPDATE: 'true',
    OPENCODE_DISABLE_MODELS_FETCH: 'true', OPENCODE_PURE: 'true', CI: 'true' },
});
// Do not forward raw stderr (it may contain provider responses or a credential).
process.stdout.write(JSON.stringify({ code: result.status, events: result.stdout || '',
  error: result.error ? 'CLI startup, timeout, or output limit failure.' : null }));
process.exitCode = result.status === 0 && !result.error ? 0 : 1;
