/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const badge = require('./lib/eform-offline-cc-badge');

const request = (url = badge.URLS[1], method = 'GET', type = 'image') => ({
  url: () => url, method: () => method, resourceType: () => type,
});
const provenance = () => ({
  kind: 'official-public-asset-offline-cache', sourceRepository: 'creativecommons/licensebuttons',
  sourceCommit: badge.SOURCE_COMMIT, sourcePath: badge.SOURCE_PATH, sourceUrl: badge.SOURCE_URL,
  sha256: badge.SHA256, gitBlobSha: badge.GIT_BLOB_SHA, bytes: badge.BYTE_LENGTH,
  dimensions: [80, 15], allowedOfflineCacheUrls: [...badge.URLS],
});
const readFixture = (record = provenance(), bytes = Buffer.alloc(badge.BYTE_LENGTH)) =>
  (file) => file.endsWith('.json') ? JSON.stringify(record) : bytes;

// Official same-path badge, pinned to the source commit/SHA above. These 672 public
// bytes keep the success-path test reproducible without network access or a skip.
const officialBadge = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAFAAAAAPCAIAAAD8q9/YAAAABGdBTUEAANbY1E9YMgAAABl0RVh0U29mdHdhcmUAQWRvYmUg' +
  'SW1hZ2VSZWFkeXHJZTwAAAIySURBVHjaYmAYYYARiP///z9SfMvIyAJhrdm0Glliz669M6fO7OjoKC8vx9Tm6up66tSptu5W' +
  'cQlxIFdeVsHVyfXjh4+D3LeQeGXClHj54uXyxctx+RYIdu/ebWZmNrl/CoT74eOHwoLCoRLJWDy8fMkKYWFhXL6FAGBw3Lxx' +
  'c8PajUD2x08fPHw8vH28h6qHL128lJ6eDmScPXtWWVkZmO6Bafj9+/dAEsgGigDFjY2NXVxcrl29BjWFiQnIRUs/EICcnDDZ' +
  '+LXQonBhwvDt5c+fPgP9A2SHhYUBGUBbgWRnZyfQn+/evQPG/L1794CyQME7t+/AUzUwkWOWEEAAcTScASSBbKy+ZYQBZC7V' +
  '/Yzu4a9fvgBJQUFBYJQCPQbxOTABA9lKSkpA8bS0tNDQUFgRD3X6z58/GFkYscYY3HsQ18O5aPEJkUVLCPSIYW4eHiAJ9C3Q' +
  'b0AfAmMVUiwDuUA/A8UrKipmzZqFljjZ2Tn+//mPJ4ax1hDw+MSqHk2WVh7W09fl5eWF+BMYsXv27AHaCvQ5kA2MbSEhIaAI' +
  'JLueOXNGUVkRokuAXwBYUeGPYYJ1BknqKW14INfDfV39z58+v3//Ph5twBAxMTGJjosOCPYHcmWl5Zobmrdu2TrI62FgaGIp' +
  'paPjot6+fQOMUjyay8rKVFSVIb7l5xPYsWXHIPctvmoJ2H5KzUytrKxsb2/HqsfJ2en69esFJQXw9Nw/oX+0LT1I29IjrbPE' +
  'ABBgAEdwNeO7yfOaAAAAAElFTkSuQmCC', 'base64');

test('verified official bytes fulfill only image GETs, preserve other requests and report exact cache use', async () => {
  let handler; let pattern;
  const announcements = [];
  const cache = await badge.installOfflineCcBadge({ route(matcher, callback) { pattern = matcher; handler = callback; } },
    '/fixture/cc.png', { readFile: readFixture(provenance(), officialBadge), log: text => announcements.push(text) });
  assert.throws(() => cache.assertUsed(), /not exercised/);
  assert.equal(cache.receipt().hits, 0);
  assert.equal(announcements.length, 1);
  assert.match(announcements[0], /sourced offline rendering, not external internet availability/);
  assert.ok(announcements[0].includes(badge.SHA256));
  let fulfilled = 0; let continued = 0;
  const route = req => ({ request: () => req, continue() { continued++; }, fulfill(response) {
    fulfilled++;
    assert.equal(response.status, 200);
    assert.equal(response.contentType, 'image/png');
    assert.deepEqual(response.body, officialBadge);
  } });
  for (const url of badge.URLS) {
    assert.equal(pattern.test(url), true);
    await handler(route(request(url)));
  }
  for (const req of [request(undefined, 'POST'), request(undefined, 'HEAD'), request(undefined, 'GET', 'script'),
    request(`${badge.URLS[1]}?other=1`), request('https://other.invalid/icon.png')]) await handler(route(req));
  assert.equal(fulfilled, 2);
  assert.equal(continued, 5);
  for (const url of [`${badge.URLS[1]}?other=1`, `${badge.URLS[1]}#fragment`, 'https://other.invalid/icon.png']) {
    assert.equal(pattern.test(url), false);
  }
  cache.assertUsed();
  assert.deepEqual(cache.receipt(), {
    kind: 'official-public-asset-offline-cache', sourceCommit: badge.SOURCE_COMMIT, sourcePath: badge.SOURCE_PATH,
    sha256: badge.SHA256, bytes: badge.BYTE_LENGTH, hits: 2, urls: [...badge.URLS],
    proves: 'sourced offline rendering; external internet availability is not tested',
  });
});

test('only the exact original HTTP and upgraded HTTPS image GET are cache candidates', () => {
  for (const url of badge.URLS) assert.equal(badge.isExactBadgeImageRequest(request(url)), true);
});

test('queries, fragments, credentials, ports, encoded paths and other origins never become cache candidates', () => {
  for (const url of [
    `${badge.URLS[1]}?cache=1`, `${badge.URLS[1]}?`, `${badge.URLS[1]}#badge`, `${badge.URLS[1]}#`,
    'https://user:secret@i.creativecommons.org/l/by-sa/3.0/80x15.png',
    'https://i.creativecommons.org:443/l/by-sa/3.0/80x15.png',
    'https://i.creativecommons.org.evil.invalid/l/by-sa/3.0/80x15.png',
    'https://i.creativecommons.org/l/by-sa/4.0/80x15.png',
    'https://i.creativecommons.org/l/by-sa/3.0/%38%30x15.png',
    'https://i.creativecommons.org/l/by-sa/3.0/80x15.png/other',
    'http://127.0.0.1/carlos/eform/displayImage?imagefile=80x15.png',
    'file:///l/by-sa/3.0/80x15.png', '', null,
  ]) assert.equal(badge.isExactBadgeImageRequest(request(url)), false);
});

test('POST, HEAD and non-image subresources stay on the normal network path', () => {
  for (const method of ['POST', 'HEAD', 'PUT', 'DELETE', 'get']) {
    assert.equal(badge.isExactBadgeImageRequest(request(undefined, method)), false);
  }
  for (const type of ['document', 'script', 'fetch', 'xhr', 'stylesheet', 'other']) {
    assert.equal(badge.isExactBadgeImageRequest(request(undefined, 'GET', type)), false);
  }
});

test('the optional cache is entirely inactive without an explicit fixture path', async () => {
  await assert.doesNotReject(async () => {
    const result = await badge.installOfflineCcBadge({ route() { throw new Error('must not register'); } }, '', {
      readFile() { throw new Error('must not read'); }, log() { throw new Error('must not announce'); },
    });
    assert.equal(result, null);
  });
});

test('a same-length modified image is rejected by the pinned SHA before any browser route is registered', async () => {
  let registered = false;
  await assert.rejects(() => badge.installOfflineCcBadge({ route() { registered = true; } }, '/fixture/cc.png', {
    readFile: readFixture(), log() {},
  }), /image SHA256 does not match/);
  assert.equal(registered, false);
});

test('a caller cannot authorize different bytes by changing the provenance hash or source', () => {
  for (const [key, value] of Object.entries({ sha256: '0'.repeat(64), gitBlobSha: '0'.repeat(40),
    sourceCommit: 'main', sourceRepository: 'other/licensebuttons', sourcePath: 'other.png',
    sourceUrl: 'https://elsewhere.invalid/80x15.png', bytes: 1, kind: 'substitute-image' })) {
    const record = provenance(); record[key] = value;
    assert.throws(() => badge.loadFixture('/fixture/cc.png', readFixture(record)), /provenance .* does not match/);
  }
});

test('widened URL sets, wrong dimensions and wrong image lengths fail closed', () => {
  const widened = provenance(); widened.allowedOfflineCacheUrls.push('https://other.invalid/icon.png');
  assert.throws(() => badge.loadFixture('/fixture/cc.png', readFixture(widened)), /URL set/);
  const wrongSize = provenance(); wrongSize.dimensions = [88, 31];
  assert.throws(() => badge.loadFixture('/fixture/cc.png', readFixture(wrongSize)), /dimensions/);
  assert.throws(() => badge.loadFixture('/fixture/cc.png', readFixture(provenance(), Buffer.alloc(1))), /byte length/);
});

test('missing or malformed provenance and implicit filenames are errors, without exposing file contents', () => {
  assert.throws(() => badge.loadFixture('/fixture/cc.png', () => { throw new Error('private contents'); }),
    /^Error: Offline CC badge fixture: both the image and its readable JSON provenance are required$/);
  assert.throws(() => badge.loadFixture('/fixture/cc.png', () => '{private malformed'), /readable JSON provenance/);
  assert.throws(() => badge.loadFixture('/fixture/cc.dat', readFixture()), /explicit PNG file path/);
});
