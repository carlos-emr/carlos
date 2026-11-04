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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

const assert = require('node:assert/strict');

// A fresh package may compile the provider JSPs on the first reset/login.
// Bound that work, but do not wait for load/networkidle: ancillary requests
// can remain active after the document and its login/schedule controls exist.
const LOGIN_NAVIGATION_TIMEOUT_MS = 120000;

async function submitLoginDocument(page, button, { label, timeout = LOGIN_NAVIGATION_TIMEOUT_MS } = {}) {
  // Arm before the POST. Waiting only for a URL/load state can accidentally
  // accept the old login document, including after a same-URL failed login.
  const navigation = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout });
  const [response] = await Promise.all([
    navigation,
    button.click({ noWaitAfter: true, timeout }),
  ]);
  assert(response, `${label || 'Login submission'} did not produce a new document`);
  assert(response.status() < 500, `${label || 'Login submission'} returned HTTP ${response.status()}`);
}

function isProviderScheduleUrl(candidate, appBase) {
  const url = new URL(candidate);
  const base = new URL(appBase);
  const context = base.pathname.replace(/\/+$/, '');
  return url.origin === base.origin && [
    `${context}/provider/providercontrol`,
    `${context}/provider/ViewAppointmentAdminDay`,
  ].includes(url.pathname);
}

async function waitForProviderSchedule(page, appBase, { timeout = LOGIN_NAVIGATION_TIMEOUT_MS } = {}) {
  await page.waitForURL(url => isProviderScheduleUrl(url, appBase), { waitUntil: 'domcontentloaded', timeout });
}

module.exports = { LOGIN_NAVIGATION_TIMEOUT_MS, submitLoginDocument, isProviderScheduleUrl, waitForProviderSchedule };
