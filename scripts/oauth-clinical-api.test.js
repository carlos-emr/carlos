/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const {
  createAuthorizer,
  createEndpointRecorder,
  createInstanceProbes,
  createProviderPageOpener,
  createSignedRequest,
  handshakeStep,
  removeOwnedLabFile,
  requestFailure,
} = require('./oauth-clinical-api-playwright-checks');

test('requestFailure redacts OAuth headers, tokens, and verifiers while classifying transport errors', () => {
  const secretToken = 'tok-secret-999';
  const authHeader = 'OAuth oauth_consumer_key="ck", oauth_signature="sig-secret-123"';
  const cases = [
    [new Error(`Timeout 30000ms exceeded. Call log: Authorization: ${authHeader}`), 'timed out'],
    [new Error(`net::ERR_CERT_AUTHORITY_INVALID for https://host/ws/oauth/authorize?oauth_token=${secretToken}`), 'TLS certificate verification failed'],
    [new Error(`connect ECONNREFUSED 127.0.0.1:8080 header=${authHeader}`), 'connection failed'],
    [new Error(`Target page, context or browser has been closed oauth_verifier=${secretToken}`), 'browser closed'],
    [new Error(`unexpected failure carrying ${authHeader} and ${secretToken}`), 'request failed'],
  ];
  for (const [raw, expectedCategory] of cases) {
    const sanitized = requestFailure('POST /ws/services/eform/instance', raw);
    assert.equal(sanitized.message, `POST /ws/services/eform/instance: ${expectedCategory}`);
    assert.ok(!sanitized.message.includes(secretToken));
    assert.ok(!sanitized.message.includes('sig-secret-123'));
    assert.ok(!sanitized.message.includes('OAuth '));
  }
});

test('handshakeStep and createSignedRequest replace raw Playwright errors with bounded messages', async () => {
  const leaked = 'OAuth oauth_signature="super-secret-sig"';
  await assert.rejects(
    handshakeStep('OAuth access-token exchange', async () => {
      throw new Error(`request.post: Timeout 10000ms exceeded ${leaked}`);
    }),
    (error) => {
      assert.equal(error.message, 'OAuth access-token exchange: timed out');
      assert.ok(!error.message.includes('super-secret-sig'));
      return true;
    },
  );

  const endpointResults = [];
  const { endpointRecords } = createEndpointRecorder(endpointResults);
  const signedRequest = createSignedRequest({
    state: {},
    app: (p) => `https://127.0.0.1/carlos${p}`,
    anon: {
      fetch: async () => {
        throw new Error(`fetch failed: ECONNRESET ${leaked}`);
      },
    },
    consumerKey: 'ck',
    consumerSecret: 'cs',
    endpointResults,
    endpointRecords,
  });

  await assert.rejects(
    signedRequest('POST', '/ws/services/measurements/1/new', {
      oauth_token: 'tok', oauth_token_secret: 'tsec',
    }, { type: 'BP', dataField: '120/80' }),
    (error) => {
      assert.equal(error.message, 'POST /carlos/ws/services/measurements/1/new: connection failed');
      assert.ok(!error.message.includes('super-secret-sig'));
      assert.equal(endpointResults.length, 1);
      assert.equal(endpointResults[0].outcome, 'FAIL');
      assert.equal(endpointResults[0].verification, error.message);
      return true;
    },
  );
});

test('createAuthorizer checks cancellation before initiating and closes consent page on cancellation', async () => {
  const cancelError = new Error('cancelled by SIGINT');
  let initiateCalls = 0;
  const cancelledAuth = createAuthorizer({
    state: { throwIfCancelled: () => { throw cancelError; } },
    app: (p) => `https://127.0.0.1/carlos${p}`,
    anon: { post: async () => { initiateCalls += 1; } },
    sessionContext: { newPage: async () => { throw new Error('must not open page'); } },
    recorder: { pageErrors: [], consoleErrors: [], failedRequests: [] },
    consumerKey: 'ck',
    consumerSecret: 'cs',
    recordEndpoint: () => {},
  });
  await assert.rejects(cancelledAuth('demographic.read'), /cancelled by SIGINT/);
  assert.equal(initiateCalls, 0);

  let cancelOnSecondCheck = 0;
  let closed = false;
  const midFlowAuth = createAuthorizer({
    state: {
      throwIfCancelled: () => {
        cancelOnSecondCheck += 1;
        if (cancelOnSecondCheck >= 3) throw cancelError;
      },
    },
    app: (p) => `https://127.0.0.1/carlos${p}`,
    anon: {
      post: async () => ({
        status: () => 200,
        text: async () => 'oauth_token=reqtok&oauth_token_secret=reqsec&oauth_callback_confirmed=true',
      }),
    },
    sessionContext: {
      newPage: async () => ({
        on: () => {},
        url: () => 'https://127.0.0.1/carlos/ws/oauth/authorize',
        goto: async () => ({ status: () => 200 }),
        title: async () => 'Authorize',
        locator: (sel) => ({
          innerText: async () => (sel === 'body' ? 'Authorize demographic.read' : ''),
          allTextContents: async () => ['Authorize'],
          count: async () => 0,
        }),
        close: async () => { closed = true; },
      }),
    },
    recorder: { pageErrors: [], consoleErrors: [], failedRequests: [] },
    consumerKey: 'ck',
    consumerSecret: 'cs',
    recordEndpoint: () => {},
  });
  await assert.rejects(midFlowAuth('demographic.read'), /cancelled by SIGINT/);
  assert.equal(closed, true, 'consent page must be closed in finally when cancelled before approval');
});

test('createProviderPageOpener and createInstanceProbes stop on cancellation before opening pages', async () => {
  let newPageCalls = 0;
  const opener = createProviderPageOpener({
    state: { throwIfCancelled: () => { throw new Error('stopped by SIGTERM'); } },
    config: { baseUrl: 'https://127.0.0.1/carlos' },
    recorder: { pageErrors: [], consoleErrors: [], failedRequests: [] },
    sessionContext: {
      newPage: async () => {
        newPageCalls += 1;
        throw new Error('should not open page after cancellation');
      },
    },
  });
  await assert.rejects(opener('test-page', '/eform/efmpatientformlist?demographic_no=1'), /stopped by SIGTERM/);
  assert.equal(newPageCalls, 0);

  const probes = createInstanceProbes({
    state: { throwIfCancelled: () => { throw new Error('stopped by SIGINT'); } },
    sql: { rows: () => [], value: () => '0' },
    config: { baseUrl: 'https://127.0.0.1/carlos' },
    recorder: { pageErrors: [], consoleErrors: [], failedRequests: [] },
    sessionContext: {
      newPage: async () => {
        newPageCalls += 1;
        throw new Error('should not open page after cancellation');
      },
    },
    recordEndpoint: () => {},
    patientNo: '1',
  });
  await assert.rejects(probes.openProviderPage('list', '/eform/efmpatientformlist?demographic_no=1'), /stopped by SIGINT/);
  assert.equal(newPageCalls, 0);
});

test('removeOwnedLabFile removes only matching regular files and refuses symlinks', (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-oauth-lab-cleanup-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));

  const runMarker = 'deadbeef0123456789abcdef01234567';
  const prefix = `LabUpload.oauth-clinical-${runMarker}.xml.`;
  const owned = path.join(dir, `${prefix}1700000000`);
  const unrelated = path.join(dir, 'LabUpload.other-clinic.xml.1700000000');
  fs.writeFileSync(owned, '<messages/>');
  fs.writeFileSync(unrelated, '<messages/>');

  removeOwnedLabFile({ documentStore: dir, labFilePrefix: prefix });
  assert.equal(fs.existsSync(owned), false);
  assert.equal(fs.existsSync(unrelated), true);

  const symlinkTarget = path.join(dir, 'target.txt');
  const symlinkPath = path.join(dir, `${prefix}1700000001`);
  fs.writeFileSync(symlinkTarget, 'keep');
  fs.symlinkSync(symlinkTarget, symlinkPath);
  assert.throws(
    () => removeOwnedLabFile({ documentStore: dir, labFilePrefix: prefix }),
    /Refusing to remove non-file lab artifact/,
  );
  assert.equal(fs.existsSync(symlinkTarget), true);
});
