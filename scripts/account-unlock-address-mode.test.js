/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

// Drive the workflow against a small UI/SQL boundary. In particular, its initial list
// already contains the runner's address: a below-threshold failure is listed too.
function scenario({ hideAddress = false, initialOutcome = 'failed', releaseStatus = 200, releaseConfirmed = true, exclusive = '1' } = {}) {
  const address = '192.0.2.208';
  const otherAddress = '192.0.2.209';
  const username = 'FAKEPW0123456789abcdef';
  const entries = new Set([address, otherAddress]);
  const audits = [{ id: 40, address }, { id: 41, address: otherAddress }];
  const cleanups = [];
  const releases = [];
  const steps = [];
  let attempts = 0;
  let selected;
  let closed = false;
  let created = false;
  let message = '';
  const frame = {
    async waitForURL() {}, async waitForLoadState() {},
    locator(selector) {
      return {
        async waitFor() {},
        async evaluateAll(callback) {
          return callback([...entries].filter(key => !hideAddress || key !== address).map(value => ({ value })));
        },
        async selectOption(value) { assert.equal(value, address); selected = value; },
        async click() {
          assert.match(selector, /submit/);
          assert(entries.delete(selected));
          audits.push({ id: 42, address: selected });
          message = `Account unlocked: ${selected}`;
        },
        async innerText() { return message; },
      };
    },
  };
  const admin = {
    locator() {
      return { first() { return this; }, async waitFor() {}, async click() {},
        async elementHandle() { return { async contentFrame() { return frame; } }; } };
    },
    async waitForEvent() {},
    async evaluate(_callback, { key }) {
      releases.push(key);
      if (releaseStatus === 200 && releaseConfirmed && entries.delete(key)) audits.push({ id: 42, address: key });
      return { status: releaseStatus, message: releaseConfirmed ? `Account unlocked: ${key}` : '' };
    },
  };
  const probes = { on() {}, async close() { closed = true; } };
  const sql = {
    rows(query) { return /SELECT role_name/.test(query) ? [['admin']] : [[address]]; },
    value(query) {
      if (/MAX\(id\)/.test(query)) return '41';
      if (/FROM log/.test(query)) return String(audits.filter(row => row.id > 41 && row.address === address).length);
      return '1';
    },
    execute(query) {
      assert.match(query, /^DELETE FROM log WHERE id>41/);
      assert(query.includes(`contentId='${address}'`));
      for (let i = audits.length - 1; i >= 0; i--) {
        if (audits[i].id > 41 && audits[i].address === address) audits.splice(i, 1);
      }
    },
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'account-unlock-address-mode-playwright-checks.js'), 'utf8'), {
    module, process: { env: { EXCLUSIVE: exclusive } },
    require(name) {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');
      if (name.startsWith('node:')) return require(name);
      if (name === './lib/playwright-harness') return {
        ...h, assertStrictPage() {}, newContext: async () => probes,
        async login() { assert(!entries.has(address), 'The client is still blocked'); return { url: () => '/providercontrol' }; },
      };
      if (name === './lib/playwright-ui') return { clickOpensPopupOrNavigates: async () => ({ page: admin }) };
      if (name === './lib/playwright-link-audit') return { revealAuditLink: async () => {} };
      if (name === './lib/workflow-session') return {
        async expectValue(runner, query, expected) { assert.equal(runner.value(query), expected); },
      };
      if (name === './lib/throwaway-login-fixture') return {
        throwawayLoginFixture: () => ({ username, create() { created = true; }, cleanup() {} }),
        async submitLoginForm() {
          attempts++;
          return { page: { async close() {} }, outcome: attempts === 1 ? initialOutcome : 'locked' };
        },
      };
      throw new Error(`Unexpected dependency ${name}`);
    },
  });
  const session = {
    sql, provider: '900001', config: { baseUrl: new URL('https://example.invalid/carlos'), testPassword: 'synthetic' },
    recorder: {}, schedule: admin, context: { browser() {} },
    cleanup(fn) { cleanups.push(fn); },
    async step(label, body) { steps.push(label); await body(); },
  };
  return { run: () => module.exports.workflow(session), async cleanup() { for (const fn of cleanups.reverse()) await fn(); },
    entries, audits, releases, steps, address, otherAddress, isClosed: () => closed, isCreated: () => created };
}

test('shouldUnlockTheAuditedAddress_whenItWasAlreadyListedBeforeLockout', async () => {
  const s = scenario();
  await s.run();
  assert.equal(s.steps.length, 5);
  assert.deepEqual([...s.entries], [s.otherAddress]);
  assert.equal(s.isClosed(), true);
  await s.cleanup();
  assert.deepEqual(s.audits, [{ id: 40, address: s.address }, { id: 41, address: s.otherAddress }]);
});

test('shouldFailAndReleaseOnlyTheOwnedAddress_whenTheScopedListHidesIt', async () => {
  const s = scenario({ hideAddress: true });
  await assert.rejects(s.run(), /does not contain the tracked client address/);
  assert.deepEqual(s.releases, [s.address]);
  assert.deepEqual([...s.entries], [s.otherAddress]);
  assert.equal(s.isClosed(), true);
  await s.cleanup();
  assert.equal(s.audits.length, 2);
});

test('shouldRefuseToReleaseAForeignLock_whenTheFirstAttemptIsAlreadyBlocked', async () => {
  const s = scenario({ initialOutcome: 'locked' });
  await assert.rejects(s.run(), /already locked/);
  assert.deepEqual(s.releases, []);
  assert.equal(s.entries.size, 2);
  assert.equal(s.isClosed(), true);
});

test('shouldReportTheReleaseFailure_whenTheServerRefusesCleanup', async () => {
  const s = scenario({ hideAddress: true, releaseStatus: 403 });
  await assert.rejects(s.run(), /does not contain.*failure-safe release FAILED:.*HTTP 403/);
  assert.equal(s.entries.size, 2);
  assert.equal(s.isClosed(), true);
});

test('shouldRequireExclusiveUse_beforeCreatingAnyFixture', async () => {
  const s = scenario({ exclusive: '' });
  await assert.rejects(s.run(), /EXCLUSIVE=1/);
  assert.equal(s.isCreated(), false);
});

test('shouldRejectAnUnconfirmedRelease_whenTheRefusalPageAnswers200', async () => {
  const s = scenario({ hideAddress: true, releaseConfirmed: false });
  await assert.rejects(s.run(), /does not contain.*failure-safe release FAILED:.*did not confirm an unlock/);
  assert.equal(s.entries.size, 2);
  assert.equal(s.isClosed(), true);
});
