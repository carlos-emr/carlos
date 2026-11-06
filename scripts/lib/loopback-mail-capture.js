/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const net = require('node:net');

// Test-only SMTP sink. It never relays, binds only loopback, and accepts one
// explicitly named synthetic recipient. Payloads remain in memory and are erased on close.
async function captureMail(recipient) {
  if (!/^[A-Za-z0-9._-]+@example\.com$/.test(recipient)) throw new Error('Mail capture requires a synthetic recipient');
  const sockets = new Set();
  const messages = [];
  const server = net.createServer(socket => {
    sockets.add(socket);
    socket.setTimeout(15000, () => socket.destroy());
    socket.on('close', () => sockets.delete(socket));
    socket.on('error', () => {});
    let pending = ''; let data = null; let sender = false; let accepted = false;
    socket.write('220 localhost coverage sink\r\n');
    socket.on('data', chunk => {
      pending += chunk.toString('utf8');
      if (pending.length + (data ? data.length : 0) > 2 * 1024 * 1024) return socket.destroy();
      while (pending.includes('\r\n')) {
        const end = pending.indexOf('\r\n'); const line = pending.slice(0, end); pending = pending.slice(end + 2);
        if (data !== null) {
          if (line === '.') {
            messages.push(data); data = null; sender = false; accepted = false;
            socket.write('250 captured\r\n');
          } else data += `${line.replace(/^\.\./, '.')}\r\n`;
        } else if (/^(EHLO|HELO) /i.test(line)) socket.write('250 localhost\r\n');
        else if (/^MAIL FROM:/i.test(line)) { sender = true; accepted = false; socket.write('250 sender accepted\r\n'); }
        else if (/^RCPT TO:/i.test(line)) {
          const valid = sender && line.toLowerCase() === `rcpt to:<${recipient}>`.toLowerCase();
          accepted = valid; socket.write(valid ? '250 recipient accepted\r\n' : '550 test recipient only\r\n');
        } else if (/^DATA$/i.test(line)) {
          if (sender && accepted) { data = ''; socket.write('354 end with dot\r\n'); }
          else socket.write('503 recipient required\r\n');
        } else if (/^RSET$/i.test(line)) { sender = false; accepted = false; socket.write('250 reset\r\n'); }
        else if (/^QUIT$/i.test(line)) socket.end('221 goodbye\r\n');
        else socket.write('502 unsupported command\r\n');
      }
    });
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  return {
    port: server.address().port, messages,
    async close() {
      for (const socket of sockets) socket.destroy();
      await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
      messages.length = 0;
    },
  };
}
module.exports = { captureMail };
