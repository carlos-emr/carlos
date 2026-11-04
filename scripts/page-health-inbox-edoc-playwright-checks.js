#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl: Inbox and eDoc.
 *
 * User path: login > Schedule > Inbox / eDoc > every link that surface offers (one click deep).
 * Asserts per destination (lib/page-health-engine.js): HTTP < 400, not the error page, no
 * page error / console error / failed same-origin request, no off-host or mixed-content
 * request (aborted and recorded), the front door's security headers once each, and no
 * literal "null" / "undefined" / "NaN" / "???key???" / "[object Object]" / unresolved
 * ${...} / double-encoded entity in visible text or pre-filled fields.
 *
 * READ-ONLY, no fixtures, nothing to clean up. Entry controls and per-surface skips come
 * from lib/playwright-surfaces.js (shared with surface-audit).
 *
 * Implements: wave-7 `page-health` sweep (Inbox and eDoc).
 */

const h = require('./lib/playwright-harness');
const { runSurfaceHealth } = require('./lib/page-health-surfaces');

if (require.main === module) {
  h.runCheck({ name: 'page-health-inbox-edoc', run: () => runSurfaceHealth(['inbox-surface', 'edoc-surface'], { minimumOpened: 4 }) });
}

module.exports = { run: () => runSurfaceHealth(['inbox-surface', 'edoc-surface'], { minimumOpened: 4 }) };
