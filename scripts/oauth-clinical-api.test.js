/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const { SkipCheck, appUrl } = require('./lib/playwright-harness');
const oauth1 = require('./lib/oauth1-signing');
const {
  cleanup, removeOwnedLabFile, requestFailure, setUpRun, shouldRun,
  createAuthorizer, createEndpointRecorder, createSignedRequest, selectBpInstruction, checkMeasurementWrite,
} = require('./oauth-clinical-api-playwright-checks');

test('shouldRegisterOAuthClinicalApiCheck_inManifestAndPackageJson', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8'));
  const entry = manifest.checks.find((c) => c.name === 'oauth-clinical-api');
  assert.ok(entry, 'playwright-suite.json must register oauth-clinical-api');
  assert.equal(entry.script, 'scripts/oauth-clinical-api-playwright-checks.js');
  assert.equal(entry.manual, true);
  assert.equal(entry.assertsDatabase, true);
  assert.deepEqual(entry.envSet, { EXPECT_OAUTH_MODE: 'scoped' });
  assert.ok(entry.mutates.includes('eform') && entry.mutates.includes('ServiceClient'));
  assert.match(entry.fixtures, /must not run concurrently with other checks \(live wrapper: EXCLUSIVE=1\)/);

  const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
  assert.equal(
    pkg.scripts['test:oauth-clinical-api-playwright'],
    'node scripts/oauth-clinical-api-playwright-checks.js',
  );
});

test('shouldSkip_whenDisposableHostOrExclusiveOptInIsMissing', async () => {
  const saved = {
    CARLOS_DISPOSABLE_HOST: process.env.CARLOS_DISPOSABLE_HOST,
    EXCLUSIVE: process.env.EXCLUSIVE,
  };
  try {
    delete process.env.CARLOS_DISPOSABLE_HOST;
    delete process.env.EXCLUSIVE;
    await assert.rejects(() => setUpRun({}), SkipCheck);

    process.env.CARLOS_DISPOSABLE_HOST = 'true';
    delete process.env.EXCLUSIVE;
    await assert.rejects(() => setUpRun({}), SkipCheck);

    delete process.env.CARLOS_DISPOSABLE_HOST;
    process.env.EXCLUSIVE = '1';
    await assert.rejects(() => setUpRun({}), SkipCheck);
  } finally {
    for (const [k, v] of Object.entries(saved)) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
});

test('shouldFilterGroups_byApiOnlySelector', () => {
  assert.equal(shouldRun('measurements', ''), true);
  assert.equal(shouldRun('measurements', 'all'), true);
  assert.equal(shouldRun('measurements', 'measurements'), true);
  assert.equal(shouldRun('labs', 'measurements'), false);
  assert.equal(shouldRun('measurements.write', 'measurements.write'), true);
  assert.equal(shouldRun('measurements.read', 'measurements.write'), false);
});

test('shouldRedactSignedCredentials_inRequestFailureMessages', () => {
  const secretErr = new Error(
    'apiRequestContext.fetch: Request timed out\n'
    + 'Call log:\n'
    + '  - Authorization: OAuth oauth_consumer_key="ck123", oauth_token="tok456", oauth_signature="sig789"',
  );
  const redacted = requestFailure('POST /ws/services/measurements/1/new', secretErr);
  assert.equal(redacted.message, 'POST /ws/services/measurements/1/new: timed out');
  assert.ok(!redacted.message.includes('ck123'));
  assert.ok(!redacted.message.includes('tok456'));
  assert.ok(!redacted.message.includes('sig789'));

  assert.equal(
    requestFailure('GET /x', new Error('ERR_CERT_AUTHORITY_INVALID')).message,
    'GET /x: TLS certificate verification failed',
  );
  assert.equal(
    requestFailure('GET /x', new Error('connect ECONNREFUSED 127.0.0.1:443')).message,
    'GET /x: connection failed',
  );
  assert.equal(
    requestFailure('GET /x', new Error('Target page, context or browser has been closed')).message,
    'GET /x: browser closed',
  );
  assert.equal(
    requestFailure('GET /x', new Error('unexpected internal detail')).message,
    'GET /x: request failed',
  );
});

test('shouldDeleteOnlyIntroducedRoutingLocks_andRunDbDeletesEvenWhenLabFileRemovalFails', async () => {
  const executed = [];
  let disposed = false;
  const fakeSql = {
    rows(query) {
      if (query.includes('FROM fileUploadCheck')) return [['101']];
      if (query.includes('FROM hl7TextMessage')) return [['55'], ['77']];
      if (query.includes('FROM hl7TextInfo WHERE accessionNum')) return [['55'], ['77']];
      if (query.includes('FROM measurementsExt')) return [];
      if (query.includes('FROM ServiceClient')) return [['9']];
      return [];
    },
    value(query) {
      if (query.includes('FROM demographic WHERE demographic_no=?') && query.includes('last_name=?')) return '1';
      if (query.includes('SELECT COUNT(*) FROM demographic WHERE demographic_no=?')) {
        return executed.some((e) => e.query.includes('DELETE FROM demographic WHERE')) ? '0' : '1';
      }
      return '0';
    },
    execute(query, params = []) {
      executed.push({ query: query.replace(/\s+/g, ' ').trim(), params });
    },
    dispose() {
      disposed = true;
    },
  };

  // Lab 55 existed in providerLabRoutingLock before the run; only lab 77 was introduced.
  // Also point documentStore at a non-existent path so removeOwnedLabFile throws.
  const state = {
    sql: fakeSql,
    marker: 'FAKE-PW0123456789ab',
    providerNo: '999998',
    patientNo: '42',
    formName: 'FAKE-PW OAuth clinical API run1',
    consumerKey: 'ck-test',
    labRunMarker: 'abcdef1234',
    labAccession: 'FAKEPW-abcdef1234',
    labFilePrefix: 'LabUpload.oauth-clinical-abcdef1234.xml.',
    initialLabLocks: new Set(['55']),
    documentStore: path.join(os.tmpdir(), 'carlos-nonexistent-docstore-for-test-99999'),
  };

  await assert.rejects(() => cleanup(state), /ENOENT/);

  // Patient, eForm, and ServiceClient DB deletes must all have run despite lab file removal error.
  assert.ok(executed.some((e) => e.query.startsWith('DELETE FROM demographic WHERE')));
  assert.ok(executed.some((e) => e.query.startsWith('DELETE FROM eform WHERE')));
  assert.ok(executed.some((e) => e.query.startsWith('DELETE FROM ServiceClient WHERE')));
  assert.equal(disposed, true);

  // providerLabRoutingLock delete must target ONLY lab 77, never pre-existing lab 55.
  const lockDeletes = executed.filter((e) => e.query.includes('DELETE FROM providerLabRoutingLock'));
  assert.equal(lockDeletes.length, 1);
  assert.deepEqual(lockDeletes[0].params, ['77']);
});

test('shouldRetainLabArchive_whenLabRowDbCleanupFails', async (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-retain-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const owned = 'LabUpload.oauth-clinical-abcd1234.xml.1700000000';
  fs.writeFileSync(path.join(dir, owned), 'synthetic');

  const fakeSql = {
    rows(query) {
      if (query.includes('FROM fileUploadCheck')) throw new Error('database unavailable');
      return [];
    },
    value(query) {
      if (query.includes('FROM demographic WHERE demographic_no=?') && query.includes('last_name=?')) return '1';
      return '0';
    },
    execute() {},
    dispose() {},
  };

  const state = {
    sql: fakeSql,
    marker: 'FAKE-PW0123456789ab',
    providerNo: '999998',
    patientNo: '42',
    labRunMarker: 'abcd1234',
    labAccession: 'FAKEPW-abcd1234',
    labFilePrefix: 'LabUpload.oauth-clinical-abcd1234.xml.',
    documentStore: dir,
  };

  await assert.rejects(() => cleanup(state), /database unavailable/);
  assert.equal(fs.existsSync(path.join(dir, owned)), true,
    'Archive file must be retained when lab DB cleanup failed');
});

test('shouldCollectSqlDisposeError_andStillRemoveArchiveWhenLabRowsWereCleaned', async (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-dispose-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const owned = 'LabUpload.oauth-clinical-abcd1234.xml.1700000000';
  fs.writeFileSync(path.join(dir, owned), 'synthetic');

  const fakeSql = {
    rows() { return []; },
    value(query) {
      if (query.includes('FROM demographic WHERE demographic_no=?') && query.includes('last_name=?')) return '1';
      return '0';
    },
    execute() {},
    dispose() { throw new Error('mysql client dispose failed'); },
  };

  const state = {
    browser: { close: async () => { throw new Error('browser close failed'); } },
    sql: fakeSql,
    marker: 'FAKE-PW0123456789ab',
    providerNo: '999998',
    patientNo: '42',
    labRunMarker: 'abcd1234',
    labAccession: 'FAKEPW-abcd1234',
    labFilePrefix: 'LabUpload.oauth-clinical-abcd1234.xml.',
    documentStore: dir,
  };

  await assert.rejects(() => cleanup(state), (err) => {
    assert.ok(err instanceof AggregateError);
    assert.equal(err.errors.length, 2);
    assert.match(err.message, /browser close failed/);
    assert.match(err.message, /mysql client dispose failed/);
    return true;
  });
  assert.equal(fs.existsSync(path.join(dir, owned)), false,
    'Archive file must still be removed after lab DB rows were cleaned even if sql.dispose() throws');
});

test('shouldRefusePatientDependentDeletes_whenDemographicIdNoLongerMatchesRunMarker', async () => {
  const executed = [];
  const fakeSql = {
    rows() { return []; },
    value(query) {
      if (query.includes('last_name=?')) return '0';
      if (query.includes('SELECT COUNT(*) FROM demographic WHERE demographic_no=?')) return '1';
      return '0';
    },
    execute(query, params = []) { executed.push({ query, params }); },
    dispose() {},
  };

  const state = {
    sql: fakeSql,
    marker: 'FAKE-PW0123456789ab',
    providerNo: '999998',
    patientNo: '42',
    consumerKey: 'ck-test',
  };

  await assert.rejects(() => cleanup(state), /without this run's marker; cleanup stopped/);
  assert.ok(!executed.some((e) => e.query.includes('DELETE FROM measurements')));
  assert.ok(!executed.some((e) => e.query.includes('DELETE FROM demographic')));
  // OAuth client cleanup still runs in finally.
  assert.ok(executed.some((e) => e.query.includes('DELETE FROM ServiceClient')));
});

test('shouldRemoveOnlyOwnedLabFiles_inDocumentStore', (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-cleanup-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const owned = 'LabUpload.oauth-clinical-abcd1234.xml.1700000000';
  const unrelated = 'LabUpload.other-clinic.xml.1700000000';
  fs.writeFileSync(path.join(dir, owned), 'synthetic');
  fs.writeFileSync(path.join(dir, unrelated), 'keep');

  removeOwnedLabFile({ documentStore: dir, labFilePrefix: 'LabUpload.oauth-clinical-abcd1234.xml.' });

  assert.equal(fs.existsSync(path.join(dir, owned)), false);
  assert.equal(fs.existsSync(path.join(dir, unrelated)), true);
});

test('shouldRecordFailedOAuthHandshakeStatus_andDisableRedirects', async () => {
  const endpointResults = [];
  const { endpointRecords, recordEndpoint } = createEndpointRecorder(endpointResults);
  const postedCalls = [];
  const anon = {
    async post(url, options) {
      postedCalls.push({ url, options });
      return {
        status: () => 401,
        text: async () => 'oauth_problem=consumer_key_unknown',
      };
    },
  };
  const rootBase = new URL('http://127.0.0.1:8080/');
  const authorize = createAuthorizer({
    state: {},
    app: (appPath) => appUrl(rootBase, appPath),
    anon,
    sessionContext: {},
    recorder: {},
    consumerKey: 'ck-test',
    consumerSecret: 'cs-test',
    endpointResults,
    endpointRecords,
    recordEndpoint,
  });

  await assert.rejects(() => authorize('measurement.read'), /expected HTTP 200, got 401/);

  assert.equal(postedCalls.length, 1);
  assert.equal(postedCalls[0].url, 'http://127.0.0.1:8080/ws/oauth/initiate?scope=measurement.read');
  assert.equal(postedCalls[0].options.maxRedirects, 0);
  assert.equal(endpointResults.length, 1);
  assert.equal(endpointResults[0].method, 'POST');
  assert.equal(endpointResults[0].path, '/ws/oauth/initiate');
  assert.equal(endpointResults[0].status, 401);
  assert.equal(endpointResults[0].outcome, 'PENDING');
});

test('shouldDisableRedirects_andJoinRootUrlCleanlyInSignedRequest', async () => {
  const endpointResults = [];
  const { endpointRecords } = createEndpointRecorder(endpointResults);
  const fetched = [];
  const anon = {
    async fetch(url, options) {
      fetched.push({ url, options });
      return { status: () => 200 };
    },
  };
  const rootBase = new URL('http://127.0.0.1:8080/');
  const signedRequest = createSignedRequest({
    state: {},
    app: (appPath) => appUrl(rootBase, appPath),
    anon,
    consumerKey: 'ck-test',
    consumerSecret: 'cs-test',
    endpointResults,
    endpointRecords,
  });

  const response = await signedRequest(
    'GET', '/ws/services/schedule/statuses',
    { oauth_token: 'tok', oauth_token_secret: 'sec' },
  );
  assert.equal(response.status(), 200);
  assert.equal(fetched.length, 1);
  assert.equal(fetched[0].url, 'http://127.0.0.1:8080/ws/services/schedule/statuses');
  assert.equal(fetched[0].options.maxRedirects, 0);
  assert.equal(endpointResults[0].path, '/ws/services/schedule/statuses');
  assert.equal(endpointResults[0].status, 200);
});

test('shouldSelectValidBpInstruction_whenMultipleInstructionsAreConfigured', () => {
  const multiSql = {
    rows() {
      return [
        ['lying position', '6'],
        ['sitting position', '6'],
        ['standing position', '6'],
      ];
    },
  };
  assert.equal(selectBpInstruction(multiSql, ''), 'sitting position');
  assert.equal(selectBpInstruction(multiSql, 'STANDING POSITION'), 'standing position');
  assert.throws(() => selectBpInstruction(multiSql, 'unknown position'), /not an unambiguous configured BP instruction/);

  const customOnlySql = {
    rows() {
      return [
        ['ambiguous', '6'],
        ['ambiguous', '11'],
        ['left arm', '6'],
        ['right arm', '6'],
      ];
    },
  };
  assert.equal(selectBpInstruction(customOnlySql, ''), 'left arm');
  assert.throws(() => selectBpInstruction(customOnlySql, 'ambiguous'), /not an unambiguous configured BP instruction/);
});

test('shouldFailImmediately_whenScopeRefusedMeasurementCreateStillWritesToDatabase', async () => {
  const requests = [];
  const sql = {
    rows() {
      return [['sitting position', '6']];
    },
    value(query) {
      // Simulate a regression where the refused 403 POST still inserted a second BP row.
      if (query.includes('COUNT(*)') && query.includes('comments=?')) return '1';
      if (query.includes('COUNT(*)')) return '2';
      return '0';
    },
  };
  const signedRequest = async (method, requestPath) => {
    requests.push({ method, requestPath });
    return {
      status: () => 403,
      text: async () => 'insufficient_scope',
    };
  };

  await assert.rejects(
    () => checkMeasurementWrite({
      sql,
      signedRequest,
      fullToken: { oauth_token: 'full', oauth_token_secret: 'fsec' },
      limitedToken: { oauth_token: 'lim', oauth_token_secret: 'lsec' },
      recordEndpoint: () => {},
      runMarker: 'abc123',
      patientNo: '42',
      measurementComment: 'fixture comment',
      measurementId: '100',
    }),
    /scope-refused measurement write still persisted a row/,
  );
  // Must stop right after the limited request, never sending the authorized request.
  assert.equal(requests.length, 1);
});

test('shouldSignOAuth1RequestsIndependently_viaLibOAuth1Signing', () => {
  const sig = oauth1.oauthSignature({
    method: 'GET',
    url: 'http://photos.example.net/photos?file=vacation.jpg&size=original',
    oauthParams: {
      oauth_consumer_key: 'dpf43f3p2l4k3l03', // ggignore - RFC 5849 published test vector
      oauth_token: 'nnch734d00sl2jdk', // ggignore - RFC 5849 published test vector
      oauth_signature_method: 'HMAC-SHA1',
      oauth_timestamp: '1191242096',
      oauth_nonce: 'kllo9940pd9333jh', // ggignore - RFC 5849 published test vector
      oauth_version: '1.0',
    },
    consumerSecret: 'kd94hf93k423kf44', // ggignore - RFC 5849 published test vector
    tokenSecret: 'pfkkdhi9sl3r4s00', // ggignore - RFC 5849 published test vector
  });
  assert.equal(sig, 'tR3+Ty81lMeYAr/Fid0kMTYa/WM='); // ggignore - RFC 5849 published vector signature
});
