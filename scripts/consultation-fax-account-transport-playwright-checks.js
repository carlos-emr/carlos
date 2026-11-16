#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
/*
 * Issue #5278, the transport half: a consultation faxed from a chosen sender reaches the relay as that sender, with
 * the referral's PDF, and a relay failure is reported on the job without falling back to another sender.
 * User path: Schedule ▸ Consultations ▸ the owned referral ▸ Letterhead ▸ Fax Account ▸ Update And Fax ▸ the cover page
 * ▸ Send; Administration ▸ Faxes ▸ Configure Fax ▸ Restart fax scheduler when the scheduler is not running; the fax
 * scheduler's send cycle (FaxSender) then calls the relay.
 * The relay is a stub this check runs on 127.0.0.1 (FAX_TRANSPORT_STUB_PORT, default 18096): it answers a send for
 * sender A with a provider job id and a send for sender B with HTTP 500. Nothing leaves the host.
 * Asserted:
 *   1. the stub receives exactly one send, for sender A only: POST /fax/send/<A's fax user> with A's site credentials
 *      (Basic), A's fax user and fax password headers, the consultant as destination, A's fax line, and a document whose
 *      bytes are exactly the PDF the job names (this referral); the job becomes SENT with the stub's job id;
 *   2. a send from B that the relay refuses leaves B's job in ERROR with "Problem communicating with fax service", and
 *      the relay is never called for it as A (no fallback to another sender).
 * OPERATOR PREREQUISITE (so this check is manual, run only by name): the middleware client refuses loopback relays
 * unless the JVM allowlists them. Add -Dcarlos.fax.middleware.allowedHosts=127.0.0.1 to CARLOS_JAVA_OPTS in
 * /etc/carlos-emr/carlos-emr.env, run carlos-ctl restart, export FAX_TRANSPORT_STUB=on, run the check, then remove the
 * option and restart again. Without FAX_TRANSPORT_STUB=on the check SKIPs. It also SKIPs where another fax sender
 * account is active, or consultation fax is unavailable to the login.
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patient, service, consultant with a fax number, a referral);
 * lib/consultation-fax-fixture.js (two owned MIDDLEWARE senders pointed at the stub, the jobs, their FaxClientLog rows
 * and PDFs). The accounts are clinic-wide while the run lasts, so the check must not run concurrently with other
 * checks (live wrapper: EXCLUSIVE=1). If the fax scheduler was not running the check starts it, as an administrator
 * would; once the owned accounts are removed its cycles have nothing to send. Cleanup removes every owned row and file
 * and asserts it gone.
 */
const crypto = require('node:crypto');
const http = require('node:http');
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { expand, openConsultationsList, openFromList, ownedReferrals } = require('./lib/consultation-referral-fixture');
const { ownedJobs, ownedSenders } = require('./lib/consultation-fax-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const CONSULTANT_FAX = '905-555-0163';
const PORT = Number(process.env.FAX_TRANSPORT_STUB_PORT || 18096);
/** The scheduler polls every faxPollInterval (default 60 s); allow a missed cycle and a restart. */
const CYCLE_WAIT = 200000;

const STEP = {
  sent: 'the relay receives the referral\'s PDF from exactly the chosen sender, and the job is sent',
  failed: 'a relay refusal leaves the job in error and is never retried through another sender',
};

/** Polls a query until it answers `expected`, for up to a scheduler cycle and a half. */
async function awaitValue(sql, query, expected, message) {
  const deadline = Date.now() + CYCLE_WAIT;
  let last;
  while (Date.now() < deadline) {
    last = sql.value(query);
    if (last === expected) return;
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  h.assert(false, `${message} (last: ${JSON.stringify(last)})`);
}

/** A middleware relay stub: records every call; A's sends succeed, B's are refused. */
function startStub(refusedUser) {
  const calls = [];
  let nextJob = 7100000 + crypto.randomInt(0, 99999);
  const server = http.createServer((request, response) => {
    const chunks = [];
    request.on('data', chunk => chunks.push(chunk));
    request.on('end', () => {
      const call = { method: request.method, path: request.url, headers: request.headers, body: Buffer.concat(chunks).toString('utf8') };
      calls.push(call);
      const send = /^\/fax\/send\/([^/]+)$/.exec(request.url);
      if (request.method === 'POST' && send) {
        if (decodeURIComponent(send[1]) === refusedUser) {
          response.writeHead(500, { 'content-type': 'text/plain' });
          response.end('refused by the stub');
          return;
        }
        call.jobId = String(nextJob++);
        response.writeHead(200, { 'content-type': 'application/xml' });
        response.end(`<FaxJob><jobId>${call.jobId}</jobId><status>SENT</status><statusString>Accepted by the stub</statusString></FaxJob>`);
        return;
      }
      const status = /^\/fax\/(\d+)$/.exec(request.url);
      if (request.method === 'GET' && status) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ jobId: Number(status[1]), status: 'SENT', statusString: 'Accepted by the stub' }));
        return;
      }
      response.writeHead(404);
      response.end();
    });
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(PORT, '127.0.0.1', () => resolve({ calls, close: () => new Promise(done => server.close(done)) }));
  });
}

async function workflow(s) {
  if (process.env.FAX_TRANSPORT_STUB !== 'on') {
    throw new h.SkipCheck('The relay stub needs the operator prerequisite in this script\'s header (CARLOS_JAVA_OPTS allowlist, then FAX_TRANSPORT_STUB=on)');
  }
  const { sql, marker, patient } = s;
  if (sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1') !== '0') {
    throw new h.SkipCheck('A fax sender account is active on this install; the check runs only where its own are the only ones');
  }
  const referrals = ownedReferrals(s, { consultantFax: CONSULTANT_FAX });
  const senders = ownedSenders(s, { url: `http://127.0.0.1:${PORT}/`, labels: [['A', 1], ['B', 1]] });
  const stub = await startStub(senders.B.faxUser);
  s.cleanup(() => stub.close());
  const jobs = ownedJobs(s, [patient]);
  const reason = `${marker} relay referral`;
  const requestId = referrals.seed({ reason });
  const sendsFor = user => stub.calls.filter(call => call.method === 'POST' && call.path === `/fax/send/${encodeURIComponent(user)}`);

  let list;
  /** Update And Fax from the chosen sender, then Send; returns the job it queued. */
  async function faxFrom(sender, label) {
    const before = jobs.rows().length;
    const form = await openFromList(s, list, requestId, reason, label);
    await expand(form, 'collapseLetterhead');
    const select = form.locator('select#faxAccount');
    if (await form.locator('#fax_button').count() !== 1 || await select.count() !== 1) {
      throw new h.SkipCheck('This install does not offer consultation fax (consultation_fax_enabled or the _fax right)');
    }
    await select.selectOption(sender.number);
    await form.waitForFunction(() => !document.getElementById('fax_button').disabled, null, { timeout: 20000 });
    await clickAndAwaitReload(form, form.locator('#fax_button'), { label: 'Update And Fax' });
    h.assert(await form.locator('#senderFaxAccount').inputValue() === sender.number, 'The cover page did not carry the chosen sender');
    await clickAndAwaitReload(form, form.locator('#btnSend'), { label: 'Send' });
    await form.locator('#closeButton').waitFor({ state: 'attached', timeout: TIMEOUT });
    await form.close();
    const queued = jobs.rows().slice(before);
    h.assert(queued.length === 1 && queued[0].faxline === sender.number && queued[0].requestId === requestId,
      `Send queued ${JSON.stringify(queued)}, not one job for the referral from the chosen sender`);
    return queued[0];
  }

  /** Configure Fax ▸ Restart fax scheduler, when the scheduler is not running. */
  async function ensureScheduler() {
    const { page: admin } = await clickOpensPopupOrNavigates(s.schedule,
      s.schedule.locator('#admin-panel,#admin2').first(), { context: s.context, recorder: s.recorder, label: 'relay-admin', timeout: TIMEOUT });
    const faxes = admin.locator('button[data-bs-target="#collapseFifteen"]').first();
    await faxes.waitFor({ state: 'visible', timeout: TIMEOUT });
    await faxes.click();
    const configure = admin.locator('a.xlink[rel$="/admin/ViewConfigureFax"]').first();
    await configure.waitFor({ state: 'visible', timeout: TIMEOUT });
    // The page asks the scheduler for its state once it loads, and Restart stays disabled until that answer says the
    // scheduler is stopped. Read the answer itself: a fixed pause misses a slow one and would skip a needed restart.
    const status = admin.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/admin/ManageFax')
      && /(^|&)method=getFaxSchedularStatus(&|$)/.test(response.request().postData() || ''), { timeout: TIMEOUT });
    await configure.click();
    await admin.locator('#dynamic-content iframe#myFrame').waitFor({ state: 'attached', timeout: TIMEOUT });
    const frame = admin.frameLocator('#myFrame');
    const restart = frame.locator('#restartFaxSchedulerBtn');
    await restart.waitFor({ state: 'attached', timeout: TIMEOUT });
    const answer = await status;
    h.assert(answer.ok(), `Configure Fax could not read the scheduler state (HTTP ${answer.status()})`);
    const { isRunning } = await answer.json();
    h.assert(typeof isRunning === 'boolean', 'Configure Fax answered the scheduler state without isRunning');
    if (!isRunning) {
      await frame.locator('#restartFaxSchedulerBtn:enabled').waitFor({ state: 'attached', timeout: TIMEOUT });
      await restart.click();
      await frame.locator('#restartFaxSchedulerBtn:disabled').waitFor({ state: 'attached', timeout: TIMEOUT });
    }
    if (admin !== s.schedule) await admin.close();
  }


  await s.step(STEP.sent, async () => {
    // Administration opens in the schedule's section window, which Consultations shares: start the scheduler first.
    await ensureScheduler();
    list = await openConsultationsList(s);
    const job = await faxFrom(senders.A, 'relay-a');
    await awaitValue(sql, `SELECT status FROM faxes WHERE id=${job.id}`, 'SENT',
      `The scheduler did not send the job to the relay stub within its cycle (stub calls: ${JSON.stringify(stub.calls.map(call => `${call.method} ${call.path}`))})`);
    const sends = sendsFor(senders.A.faxUser);
    h.assert(sends.length === 1, `The relay received ${sends.length} sends for sender A, not one`);
    h.assert(sendsFor(senders.B.faxUser).length === 0, 'The relay received a send as sender B');
    const [call] = sends;
    const basic = Buffer.from(`${senders.A.siteUser}:${senders.A.passwd}`).toString('base64');
    h.assert(call.headers.authorization === `Basic ${basic}` && call.headers.user === senders.A.faxUser
      && call.headers.passwd === senders.A.faxPasswd, 'The relay call does not carry exactly sender A\'s credentials');
    h.assert(call.body.includes(`<destination>${CONSULTANT_FAX.replace(/\D/g, '')}</destination>`)
      && call.body.includes(`<fax_line>${senders.A.number}</fax_line>`), 'The relay call is not addressed to the consultant from A\'s line');
    const document = /<document>([^<]*)<\/document>/.exec(call.body);
    h.assert(document, 'The relay call carries no document');
    const sent = Buffer.from(document[1], 'base64');
    const filed = jobs.pdf(job);
    h.assert(crypto.createHash('sha256').update(sent).digest('hex') === crypto.createHash('sha256').update(filed).digest('hex'),
      'The document the relay received is not the PDF the job names');
    h.assert(pdfTextBuffer(sent).replace(/\s+/g, ' ').includes(reason), 'The document the relay received is not this referral');
    const [stored] = jobs.rows().filter(row => row.id === job.id);
    h.assert(stored.jobId === call.jobId, `The job stored the provider id ${stored.jobId}, not the relay's ${call.jobId}`);
  });

  await s.step(STEP.failed, async () => {
    const aSends = sendsFor(senders.A.faxUser).length;
    const job = await faxFrom(senders.B, 'relay-b');
    await awaitValue(sql, `SELECT status FROM faxes WHERE id=${job.id}`, 'ERROR',
      'The refused send did not leave the job in error within the scheduler cycle');
    const [stored] = jobs.rows().filter(row => row.id === job.id);
    h.assert(/problem communicating with fax service/i.test(stored.statusString),
      `The failed job reports ${JSON.stringify(stored.statusString)}, not the provider failure`);
    h.assert(sendsFor(senders.B.faxUser).length >= 1, 'The relay was never called as sender B');
    h.assert(sendsFor(senders.A.faxUser).length === aSends, 'The refused send was retried through sender A');
  });
}

if (require.main === module) {
  runWorkflow('consultation-fax-account-transport', workflow, { openPatient: true, openMaster: false, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow, STEP };
