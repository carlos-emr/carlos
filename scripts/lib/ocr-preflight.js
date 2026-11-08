/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {execFileSync}=require('node:child_process');

/** Prove this invocation's configured OCR data includes the English model. */
function requireEnglishOcr(run=execFileSync) {
  const output=run('tesseract',['--list-langs'],{encoding:'utf8',timeout:10000,stdio:'pipe'});
  if(!output.split(/\r?\n/).some(line=>line.trim()==='eng'))throw new Error('Tesseract English language data (eng) is required');
}
module.exports={requireEnglishOcr};
