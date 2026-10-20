#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Appends the Flyway migrations a release tag ships to scripts/flyway-released-migrations.txt.
 *
 *   node scripts/flyway-record-release.js 2026.08.0-alpha20
 *
 * Run it once per published tag, on the tag's own line and on every newer line (develop at its next
 * forward merge). It refuses a tag that is not in the current branch's history, and only adds
 * migrations not already listed; running it twice changes nothing.
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const { contentHash, readLedger, versionOf } = require('./lib/flyway-ledger');

const LEDGER = path.join(__dirname, 'flyway-released-migrations.txt');
const PREFIX = 'database/mysql/migration/';

function git(args, options = {}) {
  return execFileSync('git', args, { cwd: path.join(__dirname, '..'), maxBuffer: 1 << 28, ...options });
}

function main(tag) {
  if (!tag) throw new Error('usage: node scripts/flyway-record-release.js <tag>');
  git(['rev-parse', '--verify', '--quiet', `refs/tags/${tag}`]);
  try {
    git(['merge-base', '--is-ancestor', tag, 'HEAD']);
  } catch {
    // A newer line's tag recorded on an older branch would fail there as "missing" for good.
    throw new Error(`${tag} is not in this branch's history; record it on its own line and on newer lines only`);
  }
  const listed = new Set(readLedger(LEDGER).filter(([first]) => first !== 'floor').map(([, file]) => file));
  const added = [];
  for (const line of git(['ls-tree', '-r', tag, '--', PREFIX]).toString().trim().split('\n')) {
    const [meta, full] = line.split('\t');
    const file = full.slice(PREFIX.length);
    if (!/^(common|on|bc)\//.test(file) || !versionOf(file) || listed.has(file)) continue;
    added.push(`${tag} ${file} ${contentHash(git(['cat-file', 'blob', meta.split(' ')[2]]))}`);
  }
  if (added.length) fs.appendFileSync(LEDGER, added.join('\n') + '\n');
  console.log(`${tag}: ${added.length} migration(s) added`);
}

main(process.argv[2]);
