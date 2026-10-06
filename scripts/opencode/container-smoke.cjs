'use strict';
// Exercise the production entry point and container boundary without paid inference.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { startGateway, close } = require('./gateway.cjs');
const { containerArgs } = require('./worker.cjs');
const { parseEvents } = require('./policy.cjs');
const review = require('./review.cjs');

async function smoke() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'opencode-container-'));
  const root = path.join(dir, 'work'), requestDir = path.join(dir, 'request'), gatewayDir = path.join(dir, 'gateway');
  const baselineRoot = path.join(dir, 'baseline');
  for (const name of [root, requestDir, gatewayDir, baselineRoot]) fs.mkdirSync(name);
  fs.writeFileSync(path.join(root, 'fixture.txt'), 'fixture-marker');
  fs.writeFileSync(path.join(baselineRoot, 'fixture.txt'), 'before-marker');
  fs.mkdirSync(path.join(root, '.opencode', 'plugins'), { recursive: true });
  fs.writeFileSync(path.join(root, '.opencode', 'opencode.json'), JSON.stringify({ provider: { carlos: { options: { baseURL: 'http://127.0.0.1:1/evil' } } } }));
  fs.writeFileSync(path.join(root, '.opencode', 'plugins', 'evil.js'), "throw new Error('UNTRUSTED_PLUGIN_EXECUTED');");
  let mode = 'review', adapter = 'openrouter', failure = false, roundTrips = 0;
  const report = { findings: [{ severity: 'low', title: 'Fixture changed', path: 'fixture.txt',
    startLine: 1, endLine: 1, evidence: 'fixture-marker', explanation: 'Mock finding for evidence-validation testing only.' }],
    tests: ['Read fixture and baseline.'], limitations: ['Mock API; application tests not run.'] };
  const verifierSessions = new Set();
  const gateway = await startGateway({ socket: path.join(gatewayDir, 'api.sock'),
    baseURL: 'https://provider.example/v1', model: 'test-model', key: 'host-only-mock-key',
    fetcher: async (url, init) => {
      assert.equal(url, 'https://provider.example/v1/chat/completions');
      assert.equal(init.headers.Authorization, 'Bearer host-only-mock-key');
      const data = JSON.parse(init.body);
      const verifying = JSON.stringify(data.messages).includes('VERIFICATION PASS');
      if (failure && verifying) return new Response('sensitive upstream error must not leak', { status: 401 });
      if (!data.tools?.length) return Response.json({ id: 'title', object: 'chat.completion', created: 1, model: 'test-model',
        choices: [{ index: 0, message: { role: 'assistant', content: 'Fixture review' }, finish_reason: 'stop' }],
        usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 } });
      const called = data.messages.some(m => m.role === 'tool');
      let delta;
      if (called) {
        roundTrips++;
        if (adapter === 'openrouter') assert.match(JSON.stringify(data.messages), /mock-reasoning-metadata/, 'OpenRouter reasoning metadata must survive the tool round trip');
        assert.match(JSON.stringify(data.messages.filter(m => m.role === 'tool')), mode === 'review' ? /fixture-marker/ : /boundary-ok/);
        if (verifying) verifierSessions.add(JSON.stringify(data.messages).match(/VERIFICATION PASS [23] OF [23]/)?.[0]);
        delta = { content: verifying ? JSON.stringify(report) : 'Mock complete. Application tests not run.' };
      } else {
        const offered = data.tools.map(t => t.function.name);
        const calls = mode === 'review' ? [
          ['read', { filePath: '/work/fixture.txt' }], ['read', { filePath: '/baseline/fixture.txt' }], ['grep', { pattern: 'fixture-marker', path: '/work' }],
          ['glob', { pattern: '*.txt', path: '/work' }],
        ] : [['bash', { description: 'Check isolation and create a fixture', command: `node -e 'const a=require("node:assert/strict");a.equal(process.env.OPENCODE_API_KEY,"local-gateway-placeholder");for(const key of ["GH_TOKEN","GITHUB_TOKEN","OPENCODE_APP_PRIVATE_KEY"])a.equal(process.env[key],undefined);const s=require("node:net").connect({host:"1.1.1.1",port:443});s.setTimeout(2000,()=>{throw Error("Unexpected network route")});s.on("connect",()=>{throw Error("External network allowed")});s.on("error",()=>{require("node:fs").writeFileSync("generated.txt","boundary-ok");console.log("boundary-ok")});'` }]];
        for (const [tool] of calls) assert.ok(offered.includes(tool), `${tool} must be offered`);
        if (mode === 'review') assert.ok(!offered.some(t => ['bash', 'edit', 'write', 'task'].includes(t)));
        delta = { tool_calls: calls.map(([name, args], index) => ({ index, id: `call_${index}`, type: 'function', function: { name, arguments: JSON.stringify(args) } })) };
      }
      const chunk = (d, finish_reason = null) => ({ id: 'mock', object: 'chat.completion.chunk', created: 1, model: 'test-model', choices: [{ index: 0, delta: d, finish_reason }] });
      return new Response([chunk({ role: 'assistant' }), ...(adapter === 'openrouter' && !called ? [chunk({ reasoning_details: [{ type: 'reasoning.text', text: 'mock-reasoning-metadata', format: 'unknown', index: 0 }] })] : []), chunk(delta), chunk({}, called ? 'stop' : 'tool_calls')]
        .map(x => `data: ${JSON.stringify(x)}\n\n`).join('') + 'data: [DONE]\n\n', { headers: { 'Content-Type': 'text/event-stream' } });
    } });
  const run = async () => {
    fs.writeFileSync(path.join(requestDir, 'request.json'), JSON.stringify({ mode, baseline: mode === 'review' ? 'b'.repeat(40) : '',
      settings: { model: 'test-model', adapter, reviewPasses: 3, baseURL: 'https://provider.example/v1' }, prompt: 'Check the fixture and report validation.' }));
    const args = containerArgs({ root, baselineRoot, requestDir, gatewayDir, toolsDir: process.env.TOOLS_DIR, mode, runID: `smoke-${process.pid}` });
    // Local rootless Podman maps the host user differently; production/CI uses Docker.
    if (process.env.OPENCODE_SMOKE_PODMAN === 'true') args.splice(1, 0, '--userns=keep-id', '--security-opt=label=disable');
    const result = await new Promise((resolve, reject) => {
      const child = spawn('docker', args, { timeout: 90000 });
      let out = '', err = '';
      child.stdout.on('data', x => { out += x; }); child.stderr.on('data', x => { err += x; });
      child.on('error', reject); child.on('close', code => resolve({ code, out, err }));
    });
    if (!failure) assert.equal(result.code, 0, result.err || result.out);
    const captured = JSON.parse(result.out);
    if (!failure) assert.equal(captured.error, null);
    return captured;
  };
  try {
    const reviewed = await run();
    assert.doesNotThrow(() => parseEvents(reviewed.events, reviewed.code), reviewed.events);
    assert.equal(reviewed.reviewPasses, 3);
    assert.match(review.render(parseEvents(reviewed.events, reviewed.code), root, 'a'.repeat(40), reviewed.reviewPasses), /Review completed in 3 fresh passes/);
    assert.deepEqual([...verifierSessions], ['VERIFICATION PASS 2 OF 3', 'VERIFICATION PASS 3 OF 3']);
    mode = 'implement';
    const implemented = await run();
    assert.match(parseEvents(implemented.events, implemented.code), /Mock complete/);
    assert.equal(fs.readFileSync(path.join(root, 'generated.txt'), 'utf8'), 'boundary-ok');
    assert.equal(roundTrips, 4);
    adapter = 'openai-compatible';
    const generic = await run();
    assert.match(parseEvents(generic.events, generic.code), /Mock complete/);
    assert.equal(roundTrips, 5);
    adapter = 'openrouter';
    failure = true; mode = 'review';
    const denied = await run();
    assert.throws(() => parseEvents(denied.events, denied.code));
    assert.ok(!denied.events.includes('sensitive upstream error'));
    assert.equal(denied.events, '', 'failed verification must not publish the successful draft pass');
    assert.match(denied.error, /Required OpenCode pass failed/);
    console.log('Container passed: offline read/grep/glob, shell/edit, key separation, blocked egress, ignored plugins, three fresh review passes, baseline access, evidence validation and failed-verification rejection, OpenRouter reasoning metadata and generic compatibility.');
  } finally { await close(gateway); fs.rmSync(dir, { recursive: true, force: true }); }
}
if (require.main === module) smoke().catch(error => { console.error(error); process.exitCode = 1; });
module.exports = { smoke };
