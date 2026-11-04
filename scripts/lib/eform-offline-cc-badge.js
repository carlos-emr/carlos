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

const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

const SOURCE_COMMIT = '2ae879e0d7a4cd9474c0fc5773abc26b0ba45189';
const SOURCE_PATH = 'www/l/by-sa/3.0/80x15.png';
const SOURCE_URL = `https://raw.githubusercontent.com/creativecommons/licensebuttons/${SOURCE_COMMIT}/${SOURCE_PATH}`;
const SHA256 = '5283893486a4fafa05b9cfecd1181b7b41f0367a5b1cfdd693bd423a1cccf5f3';
const GIT_BLOB_SHA = 'c67509f1264e4b845b643e340cb39970e432c711';
const BYTE_LENGTH = 672;
const URLS = Object.freeze([
  'http://i.creativecommons.org/l/by-sa/3.0/80x15.png',
  'https://i.creativecommons.org/l/by-sa/3.0/80x15.png',
]);
const ROUTE = /^https?:\/\/i\.creativecommons\.org\/l\/by-sa\/3\.0\/80x15\.png$/;

function requireFixture(condition, message) {
  if (!condition) throw new Error(`Offline CC badge fixture: ${message}`);
}

function isExactBadgeImageRequest(request) {
  return request.method() === 'GET' && request.resourceType() === 'image' && URLS.includes(request.url());
}

function loadFixture(fixturePath, readFile = fs.readFileSync) {
  requireFixture(typeof fixturePath === 'string' && fixturePath.length > 0 && path.extname(fixturePath) === '.png',
    'an explicit PNG file path is required');
  const provenancePath = path.join(path.dirname(fixturePath), `${path.basename(fixturePath, '.png')}.provenance.json`);
  let provenance;
  let body;
  try {
    provenance = JSON.parse(readFile(provenancePath, 'utf8'));
    body = readFile(fixturePath);
  } catch {
    throw new Error('Offline CC badge fixture: both the image and its readable JSON provenance are required');
  }
  const expected = {
    kind: 'official-public-asset-offline-cache', sourceRepository: 'creativecommons/licensebuttons',
    sourceCommit: SOURCE_COMMIT, sourcePath: SOURCE_PATH, sourceUrl: SOURCE_URL,
    sha256: SHA256, gitBlobSha: GIT_BLOB_SHA, bytes: BYTE_LENGTH,
  };
  for (const [key, value] of Object.entries(expected)) {
    requireFixture(provenance?.[key] === value, `provenance ${key} does not match the pinned official source`);
  }
  requireFixture(JSON.stringify(provenance.dimensions) === '[80,15]', 'provenance dimensions must be 80x15');
  requireFixture(JSON.stringify(provenance.allowedOfflineCacheUrls) === JSON.stringify(URLS),
    'provenance URL set must match only the two exact original badge URLs');
  requireFixture(Buffer.isBuffer(body) && body.length === BYTE_LENGTH, 'image has an unexpected byte length');
  requireFixture(createHash('sha256').update(body).digest('hex') === SHA256, 'image SHA256 does not match the pinned official bytes');
  requireFixture(createHash('sha1').update(`blob ${body.length}\0`).update(body).digest('hex') === GIT_BLOB_SHA,
    'image Git blob identity does not match the pinned official source');
  requireFixture(body.subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex'))
      && body.readUInt32BE(16) === 80 && body.readUInt32BE(20) === 15, 'image is not the expected 80x15 PNG');
  return { body };
}

/** Explicit test-only asset cache; it makes no change to stored HTML or application egress policy. */
async function installOfflineCcBadge(context, fixturePath, { readFile = fs.readFileSync, log = console.log } = {}) {
  if (!fixturePath) return null;
  const { body } = loadFixture(fixturePath, readFile);
  let hits = 0;
  const urls = new Set();
  await context.route(ROUTE, async (route) => {
    const request = route.request();
    if (!isExactBadgeImageRequest(request)) {
      await route.continue();
      return;
    }
    await route.fulfill({ status: 200, contentType: 'image/png', body });
    hits++;
    urls.add(request.url());
  });
  log(`Offline CC badge fixture enabled: official commit=${SOURCE_COMMIT} SHA256=${SHA256}; this proves sourced offline rendering, not external internet availability`);
  return {
    assertUsed() { requireFixture(hits > 0, 'the enabled asset cache was not exercised by an exact GET image request'); },
    receipt() {
      return { kind: 'official-public-asset-offline-cache', sourceCommit: SOURCE_COMMIT,
        sourcePath: SOURCE_PATH, sha256: SHA256, bytes: BYTE_LENGTH, hits, urls: [...urls],
        proves: 'sourced offline rendering; external internet availability is not tested' };
    },
  };
}

module.exports = { isExactBadgeImageRequest, loadFixture, installOfflineCcBadge, URLS, SHA256, GIT_BLOB_SHA,
  SOURCE_COMMIT, SOURCE_PATH, SOURCE_URL, BYTE_LENGTH };
