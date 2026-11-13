#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl: the Report index.
 *
 * User path: login > Schedule > Report > every link that surface offers (one click deep).
 * Asserts per destination (lib/page-health-engine.js): HTTP < 400, not the error page, no
 * page error / console error / failed same-origin request, no off-host or mixed-content
 * request (aborted and recorded), the front door's security headers once each, and no
 * literal "null" / "undefined" / "NaN" / "???key???" / "[object Object]" / unresolved
 * ${...} / double-encoded entity in visible text or pre-filled fields.
 *
 * READ-ONLY, no fixtures, nothing to clean up. Entry controls and per-surface skips come
 * from lib/playwright-surfaces.js (shared with surface-audit).
 *
 * Implements: wave-7 `page-health` sweep (the Report index).
 */

const h = require('./lib/playwright-harness');
const { runSurfaceHealth } = require('./lib/page-health-surfaces');

if (require.main === module) {
  h.runCheck({ name: 'page-health-report-index', run: () => runSurfaceHealth(['report-index'], { minimumOpened: 3 }) });
}

module.exports = { run: () => runSurfaceHealth(['report-index'], { minimumOpened: 3 }) };
