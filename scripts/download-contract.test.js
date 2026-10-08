/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/download-contract.js: the helpers behind direct-response-contract.
 *
 * The check runs against a live install; these tests pin what the helpers decide, without one: which
 * answers count as "served exactly", which as "the application refused" and which as "the front door
 * blocked it" (never the same thing), that seeded marker files are removed again and only while they are
 * still the bytes the run wrote, how a .properties text is read, and what a correctly encoded log page
 * looks like.
 */

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const D = require('./lib/download-contract');
const { APPLICATION_HEADER } = require('./lib/playwright-harness');

const MARKER = 'FAKE-PW0123456789abcdef';
const APP = { [APPLICATION_HEADER]: 'none' };

function scratch() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'download-contract-test-'));
}

function download(bytes, name, overrides = {}) {
  return D.describeResponse({
    status: 200,
    headers: {
      ...APP,
      'Content-Type': 'application/octet-stream;charset=UTF-8',
      'Content-Disposition': `attachment;filename="${name}"`,
      'X-Content-Type-Options': 'nosniff',
      'Content-Length': String(bytes.length),
      ...overrides.headers,
    },
    body: overrides.body || bytes,
  });
}

test('shouldReadTheLastAssignment_whenAPropertyIsSetTwice', () => {
  const text = '# backup_path = /commented\nbackup_path = /first/\nother: x\nbackup_path=/second/  \n';
  assert.equal(D.propertyValue(text, 'backup_path'), '/second/');
  assert.equal(D.propertyValue(text, 'other'), 'x');
});

test('shouldReturnUndefined_whenThePropertyIsOnlyCommentedOut', () => {
  assert.equal(D.propertyValue('#LOGGING_PATH = /var/log\n! LOGGING_PATH = /x\n', 'LOGGING_PATH'), undefined);
  assert.equal(D.propertyValue('LOGGING_PATH =\n', 'LOGGING_PATH'), '');
  assert.equal(D.propertyValue(null, 'x'), undefined);
});

test('shouldNotMatchAKeyPrefix_whenAnotherKeyStartsWithIt', () => {
  assert.equal(D.propertyValue('HOME_DIR_OLD = /a\n', 'HOME_DIR'), undefined);
});

test('shouldExplainWhyADirectoryIsNotUsable_whenItIsMissingOrUnset', () => {
  assert.match(D.usableDirectory(undefined, 'LOGGING_PATH').reason, /LOGGING_PATH is not set/);
  assert.match(D.usableDirectory('/definitely/not/here', 'backup_path').reason, /does not exist on this host/);
  const dir = scratch();
  try {
    assert.equal(D.usableDirectory(dir, 'x').ok, true);
    const file = path.join(dir, 'f');
    fs.writeFileSync(file, 'x');
    assert.match(D.usableDirectory(file, 'x').reason, /not a directory/);
  } finally {
    fs.rmSync(dir, { recursive: true });
  }
});

test('shouldSeedAndRemoveMarkerFiles_whenTheyAreStillTheBytesTheRunWrote', () => {
  const dir = scratch();
  try {
    const files = D.markerFiles(MARKER);
    const seeded = files.seed(dir, `${MARKER}-a.txt`, 'a');
    const nested = files.seedNested(dir, `${MARKER}-sub`, `${MARKER}-n.txt`, 'n');
    assert.equal(nested.relative, `${MARKER}-sub/${MARKER}-n.txt`);
    assert.deepEqual(fs.readFileSync(seeded.file), seeded.bytes);
    assert.ok(seeded.bytes.includes(0x00) && seeded.bytes.includes(0xff), 'the payload is not valid UTF-8 text');
    assert.equal(files.paths.length, 3);
    files.remove();
    assert.deepEqual(fs.readdirSync(dir), []);
    files.remove();
  } finally {
    fs.rmSync(dir, { recursive: true });
  }
});

test('shouldNeverOverwrite_whenAFileAlreadyHoldsTheName', () => {
  const dir = scratch();
  try {
    fs.writeFileSync(path.join(dir, 'report19991231.html'), 'a real report');
    const files = D.markerFiles(MARKER);
    assert.throws(() => files.seed(dir, 'report19991231.html', 'log'), /EEXIST/);
    // Registered before the write, but removal sees content that is not the run's and leaves it.
    assert.throws(() => files.remove(), /left in place/);
    assert.equal(fs.readFileSync(path.join(dir, 'report19991231.html'), 'utf8'), 'a real report');
  } finally {
    fs.rmSync(dir, { recursive: true });
  }
});

test('shouldRefuseAnUnsafeName_whenSeedingAMarkerFile', () => {
  const dir = scratch();
  try {
    const files = D.markerFiles(MARKER);
    assert.throws(() => files.seed(dir, '../escape.txt', 'x'), /not a plain file name/);
    assert.throws(() => files.seed(dir, '.hidden', 'x'), /not a plain file name/);
    assert.throws(() => D.markerFiles('FAKE-PW1'), /run marker/);
  } finally {
    fs.rmSync(dir, { recursive: true });
  }
});

test('shouldAcceptTheExactDownload_whenEverythingMatches', () => {
  const bytes = Buffer.concat([Buffer.from(`${MARKER} x\n`), Buffer.from([0, 255, 254, 13, 10, 7])]);
  assert.doesNotThrow(() => D.assertServedExactly(download(bytes, 'f.txt'), { bytes, name: 'f.txt', label: 'x' }));
});

test('shouldRejectTheDownload_whenAnyPartIsWrong', () => {
  const bytes = Buffer.from(`${MARKER} payload`);
  const judge = res => () => D.assertServedExactly(res, { bytes, name: 'f.txt', label: 'x' });
  assert.throws(judge(download(bytes, 'f.txt', { body: Buffer.from('<html><body>Error</body></html>') })), /starts like an HTML page/);
  assert.throws(judge(download(bytes, 'f.txt', { body: Buffer.from(`${MARKER} paylaod`) })), /not the \d+ seeded bytes/);
  assert.throws(judge(download(bytes, 'other.txt')), /names another file/);
  assert.throws(judge(download(bytes, 'f.txt', { headers: { 'Content-Disposition': 'inline' } })), /no attachment/);
  assert.throws(judge(download(bytes, 'f.txt', { headers: { 'Content-Type': 'text/html' } })), /not application\/octet-stream/);
  assert.throws(judge(download(bytes, 'f.txt', { headers: { 'X-Content-Type-Options': '' } })), /nosniff/);
  assert.throws(judge(download(bytes, 'f.txt', { headers: { 'Content-Length': '3' } })), /Content-Length/);
  const noApp = D.describeResponse({ status: 200, headers: { 'Content-Type': 'application/octet-stream' }, body: bytes });
  assert.throws(judge(noApp), /not shown to be the application/);
});

test('shouldFindServedBytes_whenARefusalCarriesASeededFile', () => {
  const bytes = Buffer.from(`${MARKER} payload with enough bytes to cover the leading window of the file`);
  const refusal = D.describeResponse({ status: 403, headers: { ...APP, 'Content-Type': 'text/html' }, body: '<html>no</html>' });
  assert.doesNotThrow(() => D.assertNothingServed(refusal, { label: 'x', forbidden: [bytes] }));
  const leaking = D.describeResponse({ status: 403, headers: APP, body: Buffer.concat([Buffer.from('<p>'), bytes]) });
  assert.throws(() => D.assertNothingServed(leaking, { label: 'x', forbidden: [bytes] }), /leading bytes/);
  const attachment = D.describeResponse({ status: 500, headers: { ...APP, 'Content-Disposition': 'attachment;filename="a"' }, body: 'x' });
  assert.throws(() => D.assertNothingServed(attachment, { label: 'x' }), /attachment/);
});

test('shouldTellTheApplicationFromTheFrontDoor_whenJudgingABlockedRequest', () => {
  const app400 = D.describeResponse({ status: 400, headers: { ...APP, 'Content-Type': 'text/html' }, body: '<html>Error</html>' });
  assert.equal(D.judgeBlocked(app400, { label: 'x' }), 'app-400');
  const waf = D.describeResponse({ status: 403, headers: { 'Content-Type': 'text/html', Server: 'nginx' }, body: '<html><head><title>403 Forbidden</title></head><body><center>nginx</center></body></html>' });
  assert.equal(waf.waf, true);
  assert.equal(waf.nginx, true);
  assert.equal(D.judgeBlocked(waf, { label: 'x' }), 'waf');
});

test('shouldNotCountABareStatusAsARefusal_whenTheApplicationDidNotAnswer', () => {
  const bare403 = D.describeResponse({ status: 403, headers: { 'Content-Type': 'text/html' }, body: '<html>Forbidden</html>' });
  assert.throws(() => D.judgeBlocked(bare403, { label: 'x' }), /from outside the application/);
  const notFound = D.describeResponse({ status: 404, headers: APP, body: '<html>nope</html>' });
  assert.throws(() => D.judgeBlocked(notFound, { label: 'x' }), /neither the application/);
  const crash = D.describeResponse({ status: 500, headers: APP, body: '<html>Error 500</html>' });
  assert.throws(() => D.judgeBlocked(crash, { label: 'x' }), /neither the application/);
  const bytes = Buffer.from(`${MARKER} file`);
  assert.throws(() => D.judgeBlocked(download(bytes, 'f.txt'), { label: 'x' }), /served a file/);
});

test('shouldRecogniseAnHtmlLead_whenABodyIsAnErrorPage', () => {
  assert.equal(D.startsLikeHtml(Buffer.from('\n\n<!DOCTYPE html><html>')), true);
  assert.equal(D.startsLikeHtml(Buffer.from('<HTML lang="en">')), true);
  assert.equal(D.startsLikeHtml(Buffer.from('OBEC01 1234567890AB\r')), false);
  assert.equal(D.startsLikeHtml(Buffer.from([0x00, 0xff, 0x3c, 0x68])), false);
});

test('shouldAcceptAnEncodedLogPage_andRejectAnUnencodedOne', () => {
  const encoded = `<pre id="log-results">&lt;script&gt;window.__logXss=1&lt;/script&gt;${MARKER} log\n</pre>`;
  assert.deepEqual(D.logViewerProblems(encoded, MARKER), []);
  const raw = `<pre id="log-results">${D.LOG_SCRIPT}${MARKER} log\n</pre>`;
  assert.match(D.logViewerProblems(raw, MARKER).join(';'), /rendered unencoded/);
  assert.deepEqual(D.logViewerProblems('<p>Logging path is not configured.</p>', MARKER), ['no <pre id="log-results"> in the page']);
  assert.match(D.logViewerProblems('<pre id="log-results">&lt;script&gt;</pre>', MARKER).join(';'), /marker text is not/);
});
