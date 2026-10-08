/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Plumbing for direct-response-contract: the download servlets (BackupDownload, OscarDownload) and the
 * admin pages that front server directories (backup download, server log viewer).
 *
 * Three things live here so the check reads as a list of claims and the claims are testable without a
 * running install (scripts/download-contract.test.js):
 *
 *   1. markerFiles()      seeds FAKE marker files into the server directories the application serves from
 *                         and removes exactly those files again. A file is created with the exclusive flag
 *                         (an existing file is never overwritten) and removed only while its content is
 *                         still the bytes this run wrote, so a name that collided with a real file is
 *                         never deleted.
 *   2. the response judges  assertServedExactly() (the bytes, the Content-Disposition, nosniff, no HTML),
 *                         assertNothingServed() (a refusal or an error is never a download) and
 *                         judgeBlocked() (the application's own 400/403/405, or the front door's block page,
 *                         which is reported as "application not reached" and never counted as the
 *                         application's refusal).
 *   3. propertyValue()    reads one key of a java .properties text, so the check can tell which directory the
 *                         installed configuration uses (the application reads its properties once, at start,
 *                         so a check cannot change them without a restart and must use what is configured).
 *
 * Nothing here echoes file content or a request address into a message: the messages land in stdout and
 * RESULT_JSON.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');

const MARKER_PATTERN = /^FAKE-PW[0-9a-f]{16}$/;
const FILE_NAME_PATTERN = /^[A-Za-z0-9._-]{1,120}$/;

/**
 * The value of `key` in a java .properties text. Like java.util.Properties.load the LAST assignment wins;
 * comment lines (`#`, `!`) are ignored; `=` and `:` both separate. Returns undefined when the key is not set
 * (a commented-out default is not set), and '' for a key set to nothing. Continuation lines are not folded:
 * none of the keys this check reads uses one.
 */
function propertyValue(text, key) {
  let value;
  for (const line of String(text || '').split(/\r?\n/)) {
    if (/^\s*[#!]/.test(line)) continue;
    const match = /^\s*([^\s=:]+)\s*[=:]\s*(.*?)\s*$/.exec(line);
    if (match && match[1] === key) value = match[2];
  }
  return value;
}

/**
 * Can this run write into `dir`, and is it a directory? Returns { ok: true, dir } or { ok: false, reason }
 * so the caller records a skipped row with the reason instead of failing (or passing) blind.
 */
function usableDirectory(dir, what) {
  if (!dir || !String(dir).trim()) return { ok: false, reason: `${what} is not set` };
  const resolved = path.resolve(String(dir).trim());
  let stat;
  try {
    stat = fs.statSync(resolved);
  } catch (error) {
    return { ok: false, reason: `${what} (${resolved}) does not exist on this host` };
  }
  if (!stat.isDirectory()) return { ok: false, reason: `${what} (${resolved}) is not a directory` };
  try {
    fs.accessSync(resolved, fs.constants.W_OK | fs.constants.X_OK);
  } catch (error) {
    return { ok: false, reason: `${what} (${resolved}) is not writable by this run` };
  }
  return { ok: true, dir: resolved };
}

/** Marker files seeded into server directories, with exact removal. */
function markerFiles(marker) {
  h.assert(MARKER_PATTERN.test(String(marker)), 'markerFiles needs the run marker (FAKE-PW<16 hex>)');
  // Creation order; each entry is { path, kind: 'file' | 'dir', bytes? }. Registered BEFORE the write so a
  // write that fails half way is still cleaned up.
  const owned = [];

  function payload(label) {
    // A text header naming the run, then bytes that are not valid UTF-8 (NUL, 0xFF, 0xFE, CRLF) and random
    // ones: a servlet that re-encodes, trims or line-ends the file changes them, and a body that is an
    // error page cannot equal them.
    return Buffer.concat([
      Buffer.from(`${marker} ${label}\n`, 'latin1'),
      Buffer.from([0x00, 0xff, 0xfe, 0x0d, 0x0a]),
      crypto.randomBytes(96),
    ]);
  }

  function ownLikeDirectory(target, dir) {
    const stat = fs.statSync(dir);
    try {
      fs.chownSync(target, stat.uid, stat.gid);
    } catch (error) {
      // The application runs as its own user: when this run is root the file must be handed over (a
      // failure is real), when it is not root the file is already the invoking user's own.
      if (typeof process.getuid === 'function' && process.getuid() === 0) throw error;
    }
  }

  function writeFile(dir, name, label, bytes) {
    h.assert(FILE_NAME_PATTERN.test(name) && !name.startsWith('.'), `The marker file name ${JSON.stringify(name)} is not a plain file name`);
    const target = path.join(dir, name);
    const content = bytes || payload(label);
    const entry = { path: target, kind: 'file', bytes: content };
    owned.push(entry);
    // 'wx': an existing file (a real report that happens to carry the name) fails the seed, never gets overwritten.
    fs.writeFileSync(target, content, { flag: 'wx', mode: 0o640 });
    ownLikeDirectory(target, dir);
    return { file: target, name, bytes: content };
  }

  return {
    /** Seed `name` into `dir`. `bytes` overrides the generated payload (a log file the viewer renders as text). */
    seed(dir, name, label, bytes) {
      return writeFile(dir, name, label, bytes);
    },

    /** Seed `<dir>/<subdir>/<name>`, to prove a path component cannot reach below the served directory. */
    seedNested(dir, subdir, name, label) {
      h.assert(FILE_NAME_PATTERN.test(subdir) && !subdir.startsWith('.'), 'The marker sub-directory name is not a plain name');
      const sub = path.join(dir, subdir);
      owned.push({ path: sub, kind: 'dir' });
      fs.mkdirSync(sub, { mode: 0o750 });
      ownLikeDirectory(sub, dir);
      const seeded = writeFile(sub, name, label);
      return { ...seeded, name: `${subdir}/${name}`, relative: `${subdir}/${name}` };
    },

    /** Every path this run created, for the check to assert against a directory listing. */
    get paths() { return owned.map(entry => entry.path); },

    /**
     * Remove what this run created, newest first, and assert it is gone. A file is unlinked only while it
     * still holds the bytes this run wrote; one that does not (a real file that appeared under the name)
     * is left alone and fails the check, because deleting it would be worse than the residue.
     */
    remove() {
      const problems = [];
      for (const entry of owned.slice().reverse()) {
        try {
          if (entry.kind === 'file') {
            if (!fs.existsSync(entry.path)) continue;
            if (!fs.readFileSync(entry.path).equals(entry.bytes)) {
              problems.push('a marker file now holds other content and was left in place');
              continue;
            }
            fs.unlinkSync(entry.path);
          } else if (fs.existsSync(entry.path)) {
            fs.rmdirSync(entry.path);
          }
        } catch (error) {
          problems.push(`${entry.kind} removal failed (${error.code || error.message})`);
        }
      }
      const left = owned.filter(entry => fs.existsSync(entry.path));
      if (!problems.length && !left.length) owned.length = 0;
      h.assert(!problems.length && !left.length,
        `Marker files were not all removed (${left.length} left): ${problems.join('; ') || 'still present'}`);
    },
  };
}

/** What the judges read from a response; fetchRaw() builds it, a test builds it by hand. */
function describeResponse({ status, headers = {}, body = Buffer.alloc(0) }) {
  const lower = {};
  for (const [name, value] of Object.entries(headers)) lower[name.toLowerCase()] = String(value);
  const bytes = Buffer.isBuffer(body) ? body : Buffer.from(String(body), 'utf8');
  const text = bytes.subarray(0, 65536).toString('utf8');
  return {
    status,
    headers: lower,
    body: bytes,
    text,
    type: (lower['content-type'] || '').split(';')[0].trim().toLowerCase(),
    disposition: lower['content-disposition'] || '',
    fromApp: Object.prototype.hasOwnProperty.call(lower, h.APPLICATION_HEADER),
    waf: h.isWafPage(status, text),
    nginx: /nginx/i.test(lower.server || ''),
  };
}

/** One request through a login's browser context; never follows redirects. */
async function fetchRaw(context, url, { method = 'GET' } = {}) {
  const response = await context.request.fetch(url, { method, maxRedirects: 0, timeout: 40000, failOnStatusCode: false });
  return describeResponse({ status: response.status(), headers: response.headers(), body: await response.body() });
}

/** Does the body start like an HTML document (an error page delivered as a "file")? */
function startsLikeHtml(body) {
  return /^\s*(<!doctype\s+html|<html|<head|<body|<\?xml)/i.test(Buffer.from(body).subarray(0, 256).toString('latin1'));
}

/**
 * The response is the file: HTTP 200 from the application, octet-stream, `attachment;filename="<name>"`,
 * nosniff, a Content-Length that matches, and EXACTLY the bytes seeded -- which is also not an HTML page.
 */
function assertServedExactly(res, { bytes, name, label }) {
  const problems = [];
  if (res.status !== 200) problems.push(`HTTP ${res.status}, not 200`);
  if (!res.fromApp) problems.push(`no ${h.APPLICATION_HEADER} header (not shown to be the application's answer)`);
  if (res.type !== 'application/octet-stream') problems.push(`Content-Type ${res.type || '(none)'}, not application/octet-stream`);
  if (!/^attachment\s*;/i.test(res.disposition)) problems.push('no attachment Content-Disposition');
  else if (!res.disposition.includes(`filename="${name}"`)) problems.push('the Content-Disposition names another file');
  if (res.headers['x-content-type-options'] !== 'nosniff') problems.push('no X-Content-Type-Options: nosniff');
  if (res.headers['content-length'] !== undefined && res.headers['content-length'] !== String(bytes.length)) {
    problems.push(`Content-Length ${res.headers['content-length']}, not ${bytes.length}`);
  }
  if (startsLikeHtml(res.body)) problems.push('the body starts like an HTML page');
  if (!res.body.equals(bytes)) problems.push(`the body is ${res.body.length} bytes and not the ${bytes.length} seeded bytes`);
  h.assert(!problems.length, `${label} was not served exactly: ${problems.join('; ')}`);
}

/**
 * The response served none of the seeded files: no attachment, no octet-stream, and none of the files'
 * leading bytes (the marker header and the first non-UTF-8 bytes) anywhere in the body. `forbidden` is the
 * list of seeded payloads that must not appear.
 */
function assertNothingServed(res, { label, forbidden = [] }) {
  const problems = [];
  if (/^attachment\s*;/i.test(res.disposition)) problems.push('it carries an attachment Content-Disposition');
  if (res.type === 'application/octet-stream') problems.push('it is application/octet-stream');
  for (const bytes of forbidden) {
    if (res.body.length > 0 && res.body.includes(bytes.subarray(0, Math.min(bytes.length, 48)))) {
      problems.push('its body holds the leading bytes of a seeded file');
      break;
    }
  }
  h.assert(!problems.length, `${label} served a file: ${problems.join('; ')}`);
}

/**
 * A request that must NOT yield a file (a traversal, an unknown key): returns 'waf' when the front door's
 * block page answered (the application never saw the request: reported, never counted as its refusal) or
 * `app-<status>` when the application itself refused (400, 403 or 405 carrying its response header).
 * Anything else -- bytes, a 404, a 5xx, a bare status of unknown origin -- throws.
 */
function judgeBlocked(res, { label, forbidden = [] }) {
  assertNothingServed(res, { label, forbidden });
  if (res.waf) return 'waf';
  if ([400, 403, 405].includes(res.status) && res.fromApp) return `app-${res.status}`;
  throw new Error(`${label}: HTTP ${res.status}${res.fromApp ? '' : ` from outside the application (no ${h.APPLICATION_HEADER} header)`} `
    + 'is neither the application\'s 400/403/405 nor the front door\'s block page');
}

/** The script element the seeded log carries: the viewer must show it as text, never as markup. */
const LOG_SCRIPT = '<script>window.__logXss=1</script>';

/**
 * The server log viewer rendered the seeded log through <pre id="log-results"> HTML-encoded: the marker text
 * is there, and the script element the seeded file carries (LOG_SCRIPT) is text, not markup.
 * Returns the problems (empty when the page is right).
 */
function logViewerProblems(html, marker) {
  const problems = [];
  const pre = /<pre id="log-results">([\s\S]*?)<\/pre>/.exec(html || '');
  if (!pre) return ['no <pre id="log-results"> in the page'];
  if (!pre[1].includes(marker)) problems.push('the marker text is not in the log result');
  if ((html || '').includes(LOG_SCRIPT)) problems.push('the seeded script element was rendered unencoded');
  // Encoded, the seeded element's opening tag reads &lt;script&gt; in the page source.
  if (!pre[1].includes('&lt;script&gt;')) problems.push('the seeded script element is not shown as encoded text');
  return problems;
}

module.exports = {
  assertNothingServed,
  assertServedExactly,
  describeResponse,
  fetchRaw,
  judgeBlocked,
  LOG_SCRIPT,
  logViewerProblems,
  markerFiles,
  propertyValue,
  startsLikeHtml,
  usableDirectory,
};
