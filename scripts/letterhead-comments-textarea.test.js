// Static regression for finding 233 (#4592): the Letterhead Comments textarea must not wrap its value in
// whitespace, because the browser drops only the first newline after <textarea> and posts everything else back,
// so each redisplay + Save would lengthen the letter. The live round-trip is pinned by the Playwright entry
// form-catalog-smoke-letterhead-restore.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const jsp = fs.readFileSync(path.join(__dirname, '..', 'src/main/webapp/WEB-INF/jsp/form/formConsultant.jsp'), 'utf8');

test('shouldKeepCommentsFlushAgainstTextareaTags_whenLetterheadRedisplaysSavedValue', () => {
  const m = jsp.match(/<textarea\b[^>]*\bname="comments"[^>]*>([\s\S]*?)<\/textarea>/);
  assert.ok(m, 'comments textarea not found');
  // The browser drops exactly one newline after <textarea>; the markup supplies it so a value that itself starts
  // with a line break is kept, and nothing else may wrap the value.
  const body = m[1].replace(/^\r?\n/, '');
  assert.equal(body, body.trim(), 'whitespace between the textarea tags and the encoded value is saved with the letter');
  assert.match(body, /^<carlos:encode\b[\s\S]*\/>$/, 'the textarea body must be exactly the encoded comments');
  assert.match(body, /props\.getProperty\("comments",\s*""\)/, 'the textarea must redisplay the saved comments property');
});

test('shouldEmitOneNewlineAfterTextareaOpen_soALeadingLineBreakInCommentsSurvives', () => {
  assert.match(jsp, /<textarea\b[^>]*\bname="comments"[^>]*>\r?\n<carlos:encode\b/);
});
