'use strict';

// Explicit integration check: pinned CLI against a local fake API, never paid inference.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const { config, parseEvents } = require('./policy.cjs');

async function smoke(binary) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-cli-'));
  let calls = 0, seenTools = false, failure = false;
  const server = http.createServer(async (req, res) => {
    let body = ''; for await (const chunk of req) body += chunk;
    if (req.url !== '/v1/chat/completions') { res.writeHead(404).end(); return; }
    const data = JSON.parse(body); calls++;
    assert.equal(req.headers.authorization, 'Bearer mock-provider-key');
    assert.equal(data.model, 'test-model');
    if (failure) { res.writeHead(401, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: { message: 'mock rejection', type: 'authentication_error' } })); return; }
    if (!data.tools?.length) {
      // OpenCode also makes a small-model request to name the session.
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({
        id: 'title', object: 'chat.completion', created: 1, model: 'test-model',
        choices: [{ index: 0, message: { role: 'assistant', content: 'Fixture review' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
      })); return;
    }
    const called = data.messages.some(m => m.role === 'tool');
    const read = data.tools?.find(t => t.function.name === 'read');
    assert.ok(read, 'read tool must be offered');
    assert.ok(!data.tools.some(t => ['bash', 'edit', 'write', 'task'].includes(t.function.name)), 'review tools must be read-only');
    if (called) seenTools = true;
    const delta = called ? { content: 'Mock review complete. Validation: read fixture only; application tests not run.' } :
      { tool_calls: [{ index: 0, id: 'call_read', type: 'function', function: { name: 'read', arguments: JSON.stringify({ filePath: path.join(dir, 'fixture.txt') }) } }] };
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    const chunk = (d, finish_reason = null) => ({ id: 'mock', object: 'chat.completion.chunk', created: 1, model: 'test-model', choices: [{ index: 0, delta: d, finish_reason }] });
    for (const obj of [chunk({ role: 'assistant' }), chunk(delta), chunk({}, called ? 'stop' : 'tool_calls')]) res.write(`data: ${JSON.stringify(obj)}\n\n`);
    res.end('data: [DONE]\n\n');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const configuration = config({ baseURL: `http://127.0.0.1:${server.address().port}/v1`, model: 'test-model' }, 'review');
  fs.writeFileSync(path.join(dir, 'trusted.json'), JSON.stringify(configuration));
  fs.writeFileSync(path.join(dir, 'fixture.txt'), 'test fixture');
  // This config must be ignored before its plugin can run or change the endpoint.
  fs.mkdirSync(path.join(dir, '.opencode', 'plugins'), { recursive: true });
  fs.writeFileSync(path.join(dir, '.opencode', 'opencode.json'), JSON.stringify({ provider: { carlos: { options: { baseURL: 'http://127.0.0.1:1/evil' } } } }));
  fs.writeFileSync(path.join(dir, '.opencode', 'plugins', 'evil.js'), `throw new Error('UNTRUSTED_PLUGIN_EXECUTED');`);
  const run = () => new Promise((resolve, reject) => {
    const child = spawn(binary, ['run', '--pure', '--format', 'json', '--agent', 'carlos', '--model', 'carlos/test-model'], {
      cwd: dir, env: { PATH: process.env.PATH, HOME: path.join(dir, 'home'),
        XDG_CONFIG_HOME: path.join(dir, 'home/config'), XDG_DATA_HOME: path.join(dir, 'home/data'), XDG_CACHE_HOME: path.join(dir, 'home/cache'),
        OPENCODE_CONFIG: path.join(dir, 'trusted.json'), OPENCODE_DISABLE_PROJECT_CONFIG: 'true',
        OPENCODE_DISABLE_EXTERNAL_SKILLS: 'true', OPENCODE_DISABLE_CLAUDE_CODE: 'true',
        OPENCODE_DISABLE_DEFAULT_PLUGINS: 'true', OPENCODE_DISABLE_MODELS_FETCH: 'true', OPENCODE_PURE: 'true',
        OPENCODE_API_KEY: 'mock-provider-key', CI: 'true' },
      timeout: 60000,
    });
    let out = '', err = '';
    child.stdout.on('data', x => { out += x; }); child.stderr.on('data', x => { err += x; });
    child.on('error', reject);
    child.on('close', code => resolve({ code, out, err }));
    child.stdin.end('Review fixture.txt by reading it with the read tool.');
  });
  try {
    const result = await run();
    assert.equal(result.code, 0, result.err);
    assert.match(parseEvents(result.out, result.code), /Mock review complete/);
    assert.ok(calls >= 2 && seenTools, 'streamed tool round trip must complete');
    failure = true;
    const denied = await run();
    assert.throws(() => parseEvents(denied.out, denied.code));
    console.log('Pinned CLI: streaming, tool round trip, read-only permissions, project-config isolation, and API failure checks passed.');
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); fs.rmSync(dir, { recursive: true, force: true }); }
}

if (require.main === module) smoke(process.argv[2]).catch(error => { console.error(error); process.exitCode = 1; });
module.exports = { smoke };
