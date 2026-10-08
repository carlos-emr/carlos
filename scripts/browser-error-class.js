/* SPDX-License-Identifier: GPL-2.0-or-later */
const fs = require('node:fs');
const path = require('node:path');

const SCRIPTS_DIR = __dirname;

// Browser exception messages/stacks and even custom names may contain clinical
// text. Persist only a standard error class, never arbitrary page-controlled data.
// TimeoutError is Playwright's own class for a wait that ran out.
function browserErrorClass(error) {
  const name = error?.name;
  return /^(?:Eval|Range|Reference|Syntax|Type|URI|Aggregate|Timeout)?Error$/.test(name || '')
    ? name : 'Error';
}

// The location is the last parenthesised run without spaces or parentheses, so a
// function name with spaces ("new Foo", "Object.x [as y]") cannot leak into it.
const FRAME = /^\s*at (?:async )?(?:(.+?) \()?([^\s()]+):(\d+):(\d+)\)?\s*$/;
const SAFE_FUNCTION = /^[A-Za-z_$][\w$.]*$/;
const SAFE_LOCATION = /^[A-Za-z0-9._/-]+$/;
// A path segment that is a record id (all digits, or a long hex/uuid token) is
// a PHI-correlating identifier (CLAUDE.md), not part of a script's name.
const ID_SEGMENT = /^(?:\d+|[0-9a-f-]{16,})$/i;

/**
 * One stack frame reduced to what cannot carry page content: a URL keeps its
 * path only -- no query string, fragment or ;jsessionid, and with id segments
 * replaced by ":id" -- a file path keeps its basename, and anything outside a
 * plain path alphabet is dropped. Null when the frame is not a usable location.
 */
function parseFrame(line) {
  const match = FRAME.exec(line);
  if (!match) return null;
  const [, fn, where, row, column] = match;
  let location;
  let kind;
  if (/^https?:\/\//.test(where)) {
    try {
      location = new URL(where).pathname.split(';')[0]
        .split('/').map((segment) => (ID_SEGMENT.test(segment) ? ':id' : segment)).join('/');
    } catch (error) {
      return null;
    }
    kind = 'page';
  } else if (where.startsWith('node:') || where.includes('/node_modules/')) {
    return { kind: 'internal' };
  } else if (/^[a-z][a-z0-9+.-]*:/i.test(where)) {
    // blob:, data:, chrome-extension: ... nothing a finding should repeat.
    return null;
  } else {
    // A Node frame counts only if it names a real file under scripts/: text in an
    // error message that merely looks like a frame names no such file.
    const resolved = path.resolve(where);
    if (!resolved.startsWith(`${SCRIPTS_DIR}${path.sep}`) || !fs.existsSync(resolved)) return null;
    location = path.basename(resolved);
    kind = location.endsWith('-playwright-checks.js') ? 'check' : 'helper';
  }
  if (!SAFE_LOCATION.test(location.replace(/:id/g, 'id'))) return null;
  const name = fn && SAFE_FUNCTION.test(fn) ? `${fn} ` : '';
  return { kind, text: `${name}(${location}:${row}:${column})` };
}

/**
 * Where an error was thrown, as " at fn (path:line:col)", or '' when no frame
 * qualifies. Issue #4412: a finding reading only "Error" could not be told
 * apart from any other failure. The message is never reported. Only lines
 * shaped like frames are parsed, and Playwright repeats a page error's extra
 * message lines inside its stack, so a frame is trusted only when it is an
 * http(s) script location (path only, ids redacted) or a real file under
 * scripts/. Preference: the page script that threw (a page error), else the
 * browser check's own line (a Playwright wait or assertion), else the first
 * shared helper.
 */
function errorSourceLocation(error) {
  const stack = typeof error?.stack === 'string' ? error.stack : '';
  const frames = stack.split('\n').map(parseFrame).filter((frame) => frame && frame.kind !== 'internal');
  const chosen = frames.find((frame) => frame.kind === 'page')
    || frames.find((frame) => frame.kind === 'check')
    || frames[0];
  return chosen ? ` at ${chosen.text}` : '';
}

module.exports = { browserErrorClass, errorSourceLocation };
