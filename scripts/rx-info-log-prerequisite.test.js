/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { SkipCheck } = require('./lib/playwright-harness');
const { workflow } = require('./gap-encounter-rx-drug-info-on-host-playwright-checks');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { requiredRxServerLog, verifyActiveRxServerLog } = require('./lib/rx-server-log-prerequisite');

const warning = '2026-10-19 04:50:00,001 WARN pageUtil.RxWriteScript2Action (RxWriteScript2Action.java:651) - listPreviousInstructions: invalid randomId\n';

function fixture(t, contents = '') {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-log-prerequisite-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const file = path.join(dir, 'console.log');
  fs.writeFileSync(file, contents);
  return file;
}

function probeSession(write) {
  return {
    config: { baseUrl: 'https://127.0.0.1:8443/carlos' }, patient: '123',
    context: { request: { async post(url, options) {
      assert.equal(url, 'https://127.0.0.1:8443/carlos/rx/WriteScript');
      assert.equal(options.form.demographicNo, '123');
      assert.equal(options.form.parameterValue, 'listPreviousInstructions');
      assert.equal(options.form.randomId, 'log-source-probe');
      assert.equal(options.headers['CSRF-TOKEN'], 'test-token');
      assert.equal(options.maxRedirects, 0);
      write();
      return { status: () => 200, async dispose() {} };
    } } },
  };
}
const rx = { locator: () => ({ first: () => ({ inputValue: async () => 'test-token' }) }) };

test('Rx Info cannot pass or start its UI work without the required server-log source', async () => {
  const keys = ['LAB_UPLOAD_JOURNAL_UNIT', 'LAB_UPLOAD_SERVER_LOG'];
  const before = keys.map(key => [key, process.env[key]]);
  for (const key of keys) delete process.env[key];
  try {
    const untouched = new Proxy({}, { get() { throw new Error('UI or fixture work started before checking its prerequisite'); } });
    await assert.rejects(workflow(untouched), error => error instanceof SkipCheck && /server log/.test(error.message));
  } finally {
    for (const [key, value] of before) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
});

test('an unreadable configured log is a prerequisite skip before an expected-failure step', t => {
  const file = fixture(t);
  fs.unlinkSync(file);
  assert.throws(() => requiredRxServerLog({ LAB_UPLOAD_SERVER_LOG: file }), error => error instanceof SkipCheck);
});

for (const [name, contents] of [['empty', ''], ['unrelated', 'another application\n'], ['historical matching', warning]]) {
  test(`a readable ${name} file cannot verify an active application log`, async t => {
    const file = fixture(t, contents);
    const reader = requiredRxServerLog({ LAB_UPLOAD_SERVER_LOG: file });
    await assert.rejects(verifyActiveRxServerLog(reader, probeSession(() => {}), rx, { timeout: 0 }),
      error => error instanceof SkipCheck && /live probe/.test(error.message));
  });
}

test('a fresh response-correlated application warning verifies the active source', async t => {
  const file = fixture(t, warning);
  const reader = requiredRxServerLog({ LAB_UPLOAD_SERVER_LOG: file });
  await verifyActiveRxServerLog(reader, probeSession(() => fs.appendFileSync(file, warning)), rx, { timeout: 0 });
});

test('fresh output from another logger cannot satisfy the application control', async t => {
  const file = fixture(t);
  const reader = requiredRxServerLog({ LAB_UPLOAD_SERVER_LOG: file });
  await assert.rejects(verifyActiveRxServerLog(reader,
    probeSession(() => fs.appendFileSync(file, warning.replace('pageUtil.RxWriteScript2Action', 'other.Application'))),
    rx, { timeout: 0 }), error => error instanceof SkipCheck);
});

test('a source becoming unreadable after preflight cannot become a known application failure', async t => {
  const file = fixture(t);
  const reader = requiredRxServerLog({ LAB_UPLOAD_SERVER_LOG: file });
  const mark = reader.mark();
  fs.unlinkSync(file);
  assert.throws(() => reader.since(mark), error => error instanceof SkipCheck);
});
