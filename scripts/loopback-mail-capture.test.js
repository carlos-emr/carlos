/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const net = require('node:net');
const { once } = require('node:events');
const { captureMail } = require('./lib/loopback-mail-capture');
test('mail capture rejects non-synthetic recipients before binding', async () => {
  await assert.rejects(captureMail('real@example.org'), /synthetic/);
});
test('SMTP capture rejects foreign recipients and records only complete accepted data', async () => {
  const sink = await captureMail('fixture@example.com');
  const socket = net.connect(sink.port, '127.0.0.1');
  let received = '';
  socket.on('data', chunk => { received += chunk.toString(); });
  async function waitForResponse(pattern) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      while (!pattern.test(received)) await once(socket, 'data', { signal: controller.signal });
    } finally { clearTimeout(timeout); }
  }
  try {
    await once(socket, 'connect');
    socket.write('EHLO localhost\r\nMAIL FROM:<sender@example.com>\r\nRCPT TO:<foreign@example.com>\r\nDATA\r\n');
    await waitForResponse(/503 recipient required/);
    assert.match(received, /550 test recipient only/); assert.match(received, /503 recipient required/);
    assert.equal(sink.messages.length, 0);
    socket.write('MAIL FROM:<sender@example.com>\r\nRCPT TO:<fixture@example.com>\r\nDATA\r\nSubject: fixture\r\n\r\n..literal dot\r\n');
    await waitForResponse(/354 end with dot/);
    assert.equal(sink.messages.length, 0, 'incomplete DATA must not count as delivery');
    socket.write('.\r\n'); await waitForResponse(/250 captured/);
    assert.equal(sink.messages.length, 1); assert.match(sink.messages[0], /\r\n\.literal dot\r\n$/);
  } finally { socket.destroy(); await sink.close(); }
  assert.equal(sink.messages.length, 0, 'cleanup erases payloads');
});
