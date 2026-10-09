'use strict';

// The host owns the real credential. The network-isolated agent sees only a Unix
// socket accepting bounded chat-completion requests for one configured model.
const fs = require('node:fs');
const http = require('node:http');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');

function reject(res, status, message) {
  if (res.headersSent) { res.destroy(); return; }
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: { message, type: 'gateway_error' } }));
}

async function startGateway({ socket, baseURL, model, key, fetcher = fetch }) {
  let calls = 0;
  const upstream = new URL(`${baseURL.replace(/\/$/, '')}/chat/completions`);
  if (upstream.protocol !== 'https:' || upstream.username || upstream.password || upstream.search || upstream.hash) {
    throw new Error('Gateway requires an HTTPS upstream without URL credentials, query, or fragment.');
  }
  const server = http.createServer(async (req, res) => {
    try {
      if (req.method !== 'POST' || req.url !== '/v1/chat/completions') return reject(res, 403, 'Only chat completions are permitted.');
      if (++calls > 100) return reject(res, 429, 'Per-run provider request limit exceeded.');
      const chunks = []; let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 2 * 1024 * 1024) { reject(res, 413, 'Request exceeds 2 MiB.'); return; }
        chunks.push(chunk);
      }
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { return reject(res, 400, 'Invalid JSON request.'); }
      if (!body || body.model !== model || !Array.isArray(body.messages) ||
          (body.stream !== undefined && typeof body.stream !== 'boolean')) return reject(res, 400, 'Invalid model or messages.');
      // Copy supported inference fields only; no alternate hosts, callback URLs,
      // upload APIs, request headers, or provider credentials come from the agent.
      const payload = { model, messages: body.messages, max_tokens: 8192 };
      for (const name of ['stream', 'stream_options', 'tools', 'tool_choice', 'parallel_tool_calls',
        'temperature', 'top_p', 'stop', 'frequency_penalty', 'presence_penalty', 'response_format', 'seed']) {
        if (body[name] !== undefined) payload[name] = body[name];
      }
      const response = await fetcher(upstream.href, {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(120000),
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
        body: JSON.stringify(payload),
      });
      if (!response.ok || !response.body) {
        await response.body?.cancel();
        return reject(res, response.status >= 400 && response.status < 600 ? response.status : 502,
          `Configured provider rejected the request (HTTP ${response.status}).`);
      }
      res.writeHead(200, { 'Content-Type': body.stream ? 'text/event-stream' : 'application/json' });
      await pipeline(Readable.fromWeb(response.body), res);
    } catch { reject(res, 502, 'Configured provider connection failed or timed out.'); }
  });
  server.requestTimeout = 150000;
  server.headersTimeout = 10000;
  server.maxConnections = 4;
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(socket, resolve); });
  fs.chmodSync(socket, 0o666);
  return server;
}

async function startForwarder(socket) {
  // This runs inside --network=none: only loopback and the mounted socket exist.
  const server = http.createServer((req, res) => {
    const upstream = http.request({ socketPath: socket, method: req.method, path: req.url,
      headers: { 'Content-Type': 'application/json' }, timeout: 150000 }, response => {
      res.writeHead(response.statusCode, { 'Content-Type': response.headers['content-type'] || 'application/json' });
      response.pipe(res);
      response.on('error', () => res.destroy());
    });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => reject(res, 502, 'Inference gateway unavailable.'));
    req.on('aborted', () => upstream.destroy());
    req.pipe(upstream);
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  return server;
}

async function close(server) {
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
}

module.exports = { startGateway, startForwarder, close };
