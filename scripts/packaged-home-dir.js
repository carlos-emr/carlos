#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { propertyValue } = require('./lib/download-contract');

/** Match Startup.checkAndSetProperty for the packaged /carlos context, including an explicit override. */
function homeDirFromProperties(properties) {
  const explicit = propertyValue(properties, 'HOME_DIR');
  const base = propertyValue(properties, 'BASE_DOCUMENT_DIR');
  const directory = explicit === undefined && base
    ? path.join(base, 'carlos', 'billing', 'download') : explicit;
  if (!directory || !path.isAbsolute(directory)) {
    throw new Error('The installed HOME_DIR or BASE_DOCUMENT_DIR must name an absolute directory');
  }
  return path.resolve(directory);
}

if (require.main === module) {
  try {
    const directory = homeDirFromProperties(fs.readFileSync(process.argv[2] || '/etc/carlos-emr/carlos.properties', 'utf8'));
    if (!fs.statSync(directory).isDirectory()) throw new Error('The installed Teleplan output directory is not a folder');
    process.stdout.write(directory);
  } catch (error) {
    console.error(`packaged-home-dir: ${error.message}`);
    process.exitCode = 1;
  }
}
module.exports = { homeDirFromProperties };
