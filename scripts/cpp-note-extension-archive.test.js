/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The CPP browser check edits a clinical summary column during cleanup, so the
 * string surgery that decides what it removes is worth asserting on its own.
 *
 * copyNote2cpp() appends "\n-----[[<date>]]-----\n<note text>" to
 * casemgmt_cpp.socialHistory on every save, so deleting the note alone leaves
 * the check's text in the patient's summary for good. Removing too much instead
 * would delete a clinician's entry, which is worse.
 */

const test = require('node:test');
const assert = require('node:assert');

const { withoutStampedBlocks } = require('./cpp-note-extension-archive-playwright-checks');

const STAMP = 'PW_CPP_EXT_1790000000000';
const BEFORE = '\n-----[[Thu Sep 24 2026]]-----\nclinician entry before';
const MINE = `\n-----[[Fri Sep 25 2026]]-----\n${STAMP} CPP extension regression item`;
const AFTER = '\n-----[[Sat Sep 26 2026]]-----\nclinician entry after';

test('removes only the block carrying the stamp', () => {
  assert.equal(withoutStampedBlocks(BEFORE + MINE + AFTER, STAMP), BEFORE + AFTER);
});

test('removes every block carrying the stamp, not just the first', () => {
  const twice = BEFORE + MINE + MINE + AFTER;
  assert.equal(withoutStampedBlocks(twice, STAMP), BEFORE + AFTER);
});

test('leaves a summary that never held the stamp exactly as it was', () => {
  assert.equal(withoutStampedBlocks(BEFORE + AFTER, STAMP), BEFORE + AFTER);
});

test('leaves another run\'s stamp alone', () => {
  const other = `\n-----[[Fri Sep 25 2026]]-----\nPW_CPP_EXT_9999999999999 someone else`;
  assert.equal(withoutStampedBlocks(BEFORE + other, STAMP), BEFORE + other);
});

test('handles an absent summary and an absent stamp without throwing', () => {
  assert.equal(withoutStampedBlocks('', STAMP), '');
  assert.equal(withoutStampedBlocks(null, STAMP), '');
  assert.equal(withoutStampedBlocks(BEFORE, ''), BEFORE);
});
