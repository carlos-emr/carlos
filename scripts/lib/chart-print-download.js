/**
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
 */
'use strict';

/** Native form downloads may belong to their opener or a transient print popup. */
function watchChartPrintDownload(context, expectedUrl, { timeout = 40000 } = {}) {
  const expected = new URL(expectedUrl);
  const pages = new Set();
  let resolve;
  let timer;
  let settled = false;
  const promise = new Promise(answer => { resolve = answer; });

  function finish(download) {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    context.off('page', attach);
    for (const page of pages) {
      page.off('download', received);
      page.off('popup', attach);
    }
    resolve(download);
  }

  function received(download) {
    const actual = new URL(download.url());
    if (actual.origin === expected.origin && actual.pathname === expected.pathname) finish(download);
  }

  function attach(page) {
    if (settled || pages.has(page)) return;
    pages.add(page);
    page.on('download', received);
    page.on('popup', attach);
  }

  // Both registrations precede the native click. A popup's first response can
  // already be its download, so attaching after an awaited popup loses it.
  context.on('page', attach);
  for (const page of context.pages()) attach(page);
  timer = setTimeout(() => finish(null), timeout);
  return { promise, cancel: () => finish(null) };
}

module.exports = { watchChartPrintDownload };
