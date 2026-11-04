#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Codemod for issue #5090: encode every saved value a legacy encounter-form page prints back into its
// boxes, for the context it is printed in (see scripts/lib/legacy-form-encoding.js for the rules).
//
// Usage:
//   node scripts/encode-legacy-form-values.js --check [page.jsp ...]   report; exit 1 if anything is left
//   node scripts/encode-legacy-form-values.js --write [page.jsp ...]   rewrite the pages in place
// With no pages it works on the BC form pages the issue names (BC_LEGACY_FORM_PAGES). Pages are file
// names under src/main/webapp/WEB-INF/jsp/form/ or paths. --write is idempotent: a second run changes
// nothing. Expressions it cannot place (a saved value printed as bare JavaScript code, say) are listed
// as unhandled and must be fixed by hand.
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const lib = require('./lib/legacy-form-encoding');

function main(argv) {
  const write = argv.includes('--write');
  const check = argv.includes('--check');
  if (write === check) {
    console.error('usage: encode-legacy-form-values.js (--check | --write) [page.jsp ...]');
    return 2;
  }
  const named = argv.filter((a) => !a.startsWith('--'));
  const pages = (named.length ? named : lib.BC_LEGACY_FORM_PAGES)
    .map((p) => (fs.existsSync(p) ? p : path.join(lib.FORM_DIR, p)));
  let pending = 0;
  let unhandled = 0;
  for (const file of pages) {
    const src = fs.readFileSync(file, 'utf8');
    const result = lib.classify(src);
    const rel = path.relative(process.cwd(), file);
    if (write && result.encode.length) {
      const { src: out, changed } = lib.rewrite(src);
      fs.writeFileSync(file, out);
      console.log(`${rel}: encoded ${changed} saved value(s)`);
    } else {
      pending += result.encode.length;
      console.log(`${rel}: ${result.encode.length} to encode, ${result.kept.length} kept, ${result.unhandled.length} unhandled`);
    }
    for (const o of result.unhandled) {
      unhandled++;
      console.log(`  UNHANDLED ${rel}:${lib.lineOf(src, o.start)} ${o.reason}: <%= ${o.expr.slice(0, 120)} %>`);
    }
  }
  if (check && (pending || unhandled)) return 1;
  return 0;
}

if (require.main === module) process.exitCode = main(process.argv.slice(2));
module.exports = { main };
