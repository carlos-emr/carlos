/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');
const { serverLogFromEnvironment, splitLogEvents } = require('./server-log');

function requiredRxServerLog(env = process.env) {
  const reader = serverLogFromEnvironment(env);
  if (!reader.configured) throw new h.SkipCheck('A readable LAB_UPLOAD_JOURNAL_UNIT or LAB_UPLOAD_SERVER_LOG is required to verify the Rx server log');
  const checked = { source: reader.source };
  for (const method of ['mark', 'since']) {
    checked[method] = (...args) => {
      try { return reader[method](...args); }
      catch { throw new h.SkipCheck(`Cannot read the Rx server log from ${reader.source}`); }
    };
  }
  // Read now, outside every expected-failure step. A later read failure is also
  // a prerequisite skip, never evidence of the application defect.
  checked.since(checked.mark());
  return checked;
}

async function verifyActiveRxServerLog(reader, session, rx, { timeout = 5000, interval = 200 } = {}) {
  const mark = reader.mark();
  const token = await rx.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  h.assert(token, 'The Rx log-source probe has no CSRF token');
  // This read-only history lookup rejects a nonnumeric card key before any
  // prescription lookup. Its fresh WARN proves that the configured stream is
  // this application's live log, even when the defect under test is fixed.
  // It only clears this owned session's transient history list.
  const response = await session.context.request.post(`${session.config.baseUrl}/rx/WriteScript`, {
    headers: { 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
    form: { parameterValue: 'listPreviousInstructions', demographicNo: session.patient, randomId: 'log-source-probe' },
    maxRedirects: 0,
  });
  const status = response.status();
  await response.dispose();
  h.assert(status === 200, `The read-only Rx log-source probe answered HTTP ${status}`);
  const deadline = Date.now() + timeout;
  do {
    const events = splitLogEvents(reader.since(mark));
    if (events.some(event => event.level === 'WARN' && /RxWriteScript2Action/.test(event.logger)
      && / - listPreviousInstructions: invalid randomId\s*$/.test(event.text))) return;
    if (Date.now() >= deadline) break;
    await new Promise(resolve => setTimeout(resolve, interval));
  } while (true);
  throw new h.SkipCheck(`The Rx server log source did not record this application's live probe: ${reader.source}`);
}

module.exports = { requiredRxServerLog, verifyActiveRxServerLog };
