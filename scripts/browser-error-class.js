/* SPDX-License-Identifier: GPL-2.0-or-later */

// Browser exception messages/stacks and even custom names may contain clinical
// text. Persist only a standard error class, never arbitrary page-controlled data.
// TimeoutError is Playwright's own class for a wait that ran out.
function browserErrorClass(error) {
  const name = error?.name;
  return /^(?:Eval|Range|Reference|Syntax|Type|URI|Aggregate|Timeout)?Error$/.test(name || '')
    ? name : 'Error';
}

const FRAME = /^\s*at (?:async )?(?:(\S+) \()?(.+?):(\d+):(\d+)\)?\s*$/;
const SAFE_FUNCTION = /^[A-Za-z_$][\w$.]*$/;
const SAFE_LOCATION = /^[A-Za-z0-9._/-]+$/;

/**
 * The script location of one stack frame, reduced to what cannot carry page
 * content: a URL keeps its path only (no query string, fragment or
 * ;jsessionid -- those are where a demographic number or a session token
 * would be), a file path keeps its basename, and anything outside a plain
 * path alphabet is dropped. Null when the frame is not a usable location.
 */
function frameLocation(line) {
  const match = FRAME.exec(line);
  if (!match) return null;
  const [, fn, where, row, column] = match;
  let location;
  if (/^https?:\/\//.test(where)) {
    try {
      location = new URL(where).pathname.split(';')[0];
    } catch (error) {
      return null;
    }
  } else if (where.startsWith('node:') || where.includes('/node_modules/')) {
    return { internal: true };
  } else {
    location = where.split('/').pop();
  }
  if (!SAFE_LOCATION.test(location)) return null;
  const name = fn && SAFE_FUNCTION.test(fn) ? `${fn} ` : '';
  return { text: `${name}(${location}:${row}:${column})` };
}

/**
 * Where an error was thrown, as " at fn (path:line:col)", or '' when no frame
 * qualifies. Issue #4412: a finding reading only "Error" could not be told
 * apart from any other failure. The message is still never read; only the
 * stack's frame lines are, and only the first frame outside Node internals
 * and node_modules, so a Playwright error points at the check's own line and
 * a page error at the page script that threw.
 */
function errorSourceLocation(error) {
  const stack = typeof error?.stack === 'string' ? error.stack : '';
  for (const line of stack.split('\n')) {
    const frame = frameLocation(line);
    if (frame && !frame.internal) return ` at ${frame.text}`;
  }
  return '';
}

module.exports = { browserErrorClass, errorSourceLocation };
