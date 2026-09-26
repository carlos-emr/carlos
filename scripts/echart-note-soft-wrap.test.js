/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
// Pins the verdicts of the eChart soft-wrap browser check (#3955): what it accepts as a
// soft-wrapped editor, what it rejects, and that it refuses to pass vacuously in a browser
// whose hard-wrapped control shows no inserted breaks.
const assert = require('node:assert/strict');
const test = require('node:test');

const { noteText, assertSoftWrap, assertStoredAsTyped, submittedBreaks } = require('./echart-note-soft-wrap-playwright-checks');

const TYPED = 'one long paragraph\nsecond paragraph';

function probe(overrides = {}) {
  return {
    wrapAttribute: 'soft',
    wrapProperty: 'soft',
    value: TYPED,
    submitted: TYPED,
    valueBreaks: 1,
    submittedBreaks: 1,
    controlBreaks: 7,
    ...overrides,
  };
}

function stored(overrides = {}) {
  return { prefixMatches: true, rest: '', signed: false, carriageReturns: 0, lineFeeds: 1, ...overrides };
}

const STAMP = '\n[Signed on 26-Sep-2026 1:38 by FAKE-Provider]\n';

test('noteText types one long paragraph and exactly one line break', () => {
  const text = noteText('TAG', 'label');
  const [first, second, ...rest] = text.split('\n');
  assert.equal(rest.length, 0);
  // Several times the 84-column editor, so the paragraph wraps at any chart width.
  assert.ok(first.length > 84 * 4, `first paragraph is only ${first.length} characters`);
  assert.ok(first.startsWith('TAG label ') && second.startsWith('TAG label '));
});

test('assertSoftWrap accepts a soft editor whose submission equals what was typed', () => {
  assert.doesNotThrow(() => assertSoftWrap(probe(), 'editor'));
  // FormData normalizes line breaks to CRLF on the wire; the value may carry either.
  assert.doesNotThrow(() => assertSoftWrap(probe({ submitted: TYPED.replace('\n', '\r\n') }), 'editor'));
});

test('assertSoftWrap rejects a hard-wrapped editor', () => {
  assert.throws(() => assertSoftWrap(probe({ wrapAttribute: 'hard', wrapProperty: 'hard' }), 'editor'),
    /not soft-wrapped/);
  assert.throws(() => assertSoftWrap(probe({ wrapAttribute: null }), 'editor'), /not soft-wrapped/);
});

test('assertSoftWrap rejects a submission that gained line breaks', () => {
  assert.throws(() => assertSoftWrap(probe({
    submitted: 'one long\r\nparagraph\r\nsecond paragraph',
    submittedBreaks: 2,
  }), 'editor'), /submits 2 line break\(s\) for 1 typed/);
});

test('assertSoftWrap refuses to pass when the hard-wrapped control shows no inserted breaks', () => {
  assert.throws(() => assertSoftWrap(probe({ controlBreaks: 1 }), 'editor'), /cannot show the defect/);
});

test('assertSoftWrap reports a probe that could not reach the form', () => {
  assert.throws(() => assertSoftWrap({ error: 'the note editor is not inside a form' }, 'editor'), /not inside a form/);
});

test('assertStoredAsTyped accepts only the typed text with its typed line breaks', () => {
  assert.doesNotThrow(() => assertStoredAsTyped(stored(), TYPED, 'note'));
  assert.throws(() => assertStoredAsTyped(stored({ carriageReturns: 3 }), TYPED, 'note'), /carriage return/);
  assert.throws(() => assertStoredAsTyped(stored({ lineFeeds: 4 }), TYPED, 'note'), /4 line break\(s\); 1 were typed/);
  assert.throws(() => assertStoredAsTyped(stored({ prefixMatches: false }), TYPED, 'note'), /not stored exactly as typed/);
  assert.throws(() => assertStoredAsTyped(stored({ rest: ' trailing' }), TYPED, 'note'), /not the signature stamp/);
});

test('assertStoredAsTyped allows the server signature stamp after a signed note, and nothing else', () => {
  assert.doesNotThrow(() => assertStoredAsTyped(stored({ signed: true, rest: STAMP, lineFeeds: 3 }), TYPED, 'note'));
  // The stamp is only legitimate on a signed note.
  assert.throws(() => assertStoredAsTyped(stored({ rest: STAMP, lineFeeds: 3 }), TYPED, 'note'), /not the signature stamp/);
  // A wrap break inside the typed text still counts against the note, stamp or not.
  assert.throws(() => assertStoredAsTyped(stored({ signed: true, rest: STAMP, lineFeeds: 5 }), TYPED, 'note'),
    /3 line break\(s\); 1 were typed/);
  assert.throws(() => assertStoredAsTyped(stored({ signed: true, rest: `${STAMP}extra`, lineFeeds: 3 }), TYPED, 'note'),
    /not the signature stamp/);
});

test('submittedBreaks counts a CRLF from the form encoding once', () => {
  assert.equal(submittedBreaks('a\r\nb'), 1);
  assert.equal(submittedBreaks('a\nb\rc'), 2);
  assert.equal(submittedBreaks('no breaks'), 0);
});
