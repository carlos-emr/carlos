/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const { SkipCheck } = require('./lib/playwright-harness');
const oauth1 = require('./lib/oauth1-signing');
const {
  cleanup, removeOwnedLabFile, requestFailure, setUpRun, shouldRun,
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
    rows(query, params = []) {
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
    labAccession: 'FAKE-PW-abcdef1234',
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
