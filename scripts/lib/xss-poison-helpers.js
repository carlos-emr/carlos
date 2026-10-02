/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Shared helpers for the xss-poison sweep (stored markup / output-encoding walk).
//
// Every fixture text field carries an INERT but detectable payload:
//   FAKE-XP<i data-xp="N">q</i> "dq" 'sq' \back &amp; </script data-xp>
// N identifies the field. A surface that encodes correctly shows that text literally; a surface that
// prints it raw creates an element/attribute carrying `data-xp`, shows `&` for `&amp;`, or (inside a
// script block or an inline handler) breaks the JavaScript. The payload can never execute script.
const h = require('./playwright-harness');

const TIERS = [
  n => `FAKE-XP<i data-xp="${n}">q</i> "dq" 'sq' \\back &amp; </script data-xp>`,
  n => `FAKE-<i data-xp="${n}">"dq" 'sq' \\b &amp;</script data-xp>`,
  n => `FAKE-<i data-xp=${n}>"'\\&amp;</script data-xp>`,
  n => `FAKE-<i data-xp=${n}>"'\\&amp;`,
  n => `FAKE-<i data-xp=${n}>'"\\`,
  n => `<i data-xp=${n}>'"\\`,
];

/** Payload for field `n`, using the richest variant that fits `max` characters. */
function payload(n, max = 255) {
  for (const tier of TIERS) {
    const text = tier(n);
    if (text.length <= max) return text;
  }
  throw new Error(`xss-poison payload does not fit ${max} characters`);
}

const norm = text => String(text || '').replace(/[\u00a0\s]+/g, ' ').trim();

/** Everything a user could read in one frame: rendered text plus option, textarea, input and title text. */
async function frameFacts(frame) {
  return frame.evaluate(() => {
    const parts = [document.body ? document.body.innerText : ''];
    for (const e of document.querySelectorAll('option,textarea,button,label,a,td,span,div,li'))
      if (e.children.length === 0) parts.push(e.textContent);
    for (const e of document.querySelectorAll('input,textarea')) parts.push(e.value || '');
    for (const e of document.querySelectorAll('[title],[alt]')) parts.push(e.getAttribute('title') || '', e.getAttribute('alt') || '');
    const injected = [...document.querySelectorAll('[data-xp]')].map(e => ({
      tag: e.tagName.toLowerCase(), id: e.getAttribute('data-xp'),
      context: (e.parentElement ? e.parentElement.tagName.toLowerCase() : '') + '>' + e.tagName.toLowerCase(),
    }));
    // Inline handlers that carry a payload are COMPILED (never run): a value that was HTML-encoded but not
    // JavaScript-escaped decodes back to a quote inside the handler and fails to parse when clicked.
    const handlers = [];
    for (const e of document.querySelectorAll('*')) {
      for (const a of e.attributes) {
        const isHandler = /^on/i.test(a.name);
        if (!isHandler && !/^\s*javascript:/i.test(a.value)) continue;
        if (!a.value.includes('data-xp')) continue;
        try { new Function('event', isHandler ? a.value : a.value.replace(/^\s*javascript:/i, '')); } catch (err) {
          handlers.push({ tag: e.tagName.toLowerCase(), attr: a.name, id: (a.value.match(/data-xp=\\?"?(\d+)/) || [])[1] || '?', error: String(err.message).slice(0, 60) });
        }
      }
    }
    // Inline <script> blocks that carry a payload are compiled too, and the failing neighbourhood is reported
    // so the stored field that broke the block can be named even when the browser only says "SyntaxError".
    const scripts = [];
    for (const sc of document.querySelectorAll('script:not([src])')) {
      const code = sc.textContent || '';
      const at = code.indexOf('data-xp');
      if (at < 0) continue;
      try { new Function(code); } catch (err) {
        scripts.push({ id: (code.slice(at).match(/data-xp=\\?"?(\d+)/) || [])[1] || '?', error: String(err.message).slice(0, 60),
          snippet: code.slice(Math.max(0, at - 70), at + 40).replace(/\s+/g, ' ') });
      }
    }
    return { text: parts.join('\n'), injected, handlers, scripts, url: location.pathname };
  }).catch(() => ({ text: '', injected: [], handlers: [], scripts: [], url: '' }));
}

/** Collects findings across surfaces so a sweep reports every defect, then fails once at the end. */
class Findings {
  constructor(recorder) {
    this.recorder = recorder;
    this.items = [];
    this.observed = [];
    this.coverage = [];
    this.seenInjected = new Set();
    this.ignoredPaths = [];
    // Fields a surface renders as sanitised rich text BY DESIGN (Markdown through DOMPurify): inert markup in
    // them is expected to become markup there, so it is noted, not reported. Each entry is { field, path }.
    this.richText = [];
  }
  isRichText(field, url) {
    return this.richText.some(r => String(r.field) === String(field) && String(url).endsWith(r.path));
  }
  mark() {
    const r = this.recorder;
    return { pageErrors: r.pageErrors.length, console: r.consoleIssues.length, responses: r.badResponses.length,
      failures: r.requestFailures.length, dialogs: r.unexpectedDialogs.length };
  }
  /** Moves JavaScript-layer signals raised since `since` into findings so the sweep can continue. */
  drain(surface, since) {
    const r = this.recorder;
    for (const e of r.pageErrors.splice(since.pageErrors)) this.add(surface, 'SCRIPT-ERROR', e.text.split('\n')[0]);
    const responses = r.badResponses.splice(since.responses);
    const ignored = responses.filter(e => this.ignoredPaths.some(p => new URL(e.url).pathname.endsWith(p)));
    for (const e of r.consoleIssues.splice(since.console)) {
      // A fixture limitation (for example a document row with no file behind it) is not a finding: skip the
      // resource-load console line that belongs to an ignored response, nothing else.
      if (ignored.length && /Failed to load resource/.test(e.text)) continue;
      this.add(surface, 'CONSOLE', `${e.type}: ${e.text.split('\n')[0]}`);
    }
    for (const e of responses) if (!ignored.includes(e)) this.add(surface, 'HTTP', `${e.status} ${new URL(e.url).pathname}`);
    for (const e of r.requestFailures.splice(since.failures)) this.add(surface, 'REQUEST-FAILED', `${new URL(e.url).pathname}`);
    for (const e of r.unexpectedDialogs.splice(since.dialogs)) this.add(surface, 'DIALOG', `${e.type}: ${e.text.slice(0, 80)}`);
  }
  /**
   * A step runner that also takes in whatever the browser reported during the step and no inspection
   * claimed (a popup's own load errors fire before the page is handed back), so the sweep keeps going
   * and reports it at the end instead of aborting the step.
   */
  stepper(session) {
    return (label, body) => session.step(label, async () => {
      const since = this.mark();
      try { await body(); } finally { this.drain(`step: ${label}`.slice(0, 90), since); }
    });
  }
  add(surface, kind, detail) { this.items.push({ surface, kind, detail }); }
  note(surface, text) { this.observed.push({ surface, text }); }
  summary() {
    return this.items.map(i => `[${i.surface}] ${i.kind} ${i.detail}`).join(' | ');
  }
  assertNone(what) {
    for (const o of this.observed) console.log(`  NOTE ${o.surface}: ${o.text}`);
    for (const c of this.coverage) console.log(`  ENCODED-OK ${c.surface}: fields ${c.ok.join(',') || '-'}`);
    for (const i of this.items) console.log(`  FINDING [${i.surface}] ${i.kind} ${i.detail}`);
    h.assert(this.items.length === 0, `${what}: ${this.items.length} output-encoding defect(s): ${this.summary()}`.slice(0, 3500));
  }
}

/**
 * Inspect `page` and every frame in it for the three signals. `fields` maps field number -> label for
 * the values this surface is expected to show; a field that is not visible at all is only noted.
 */
async function inspect(findings, surface, page, fields = {}, since = null) {
  const all = [];
  for (const frame of page.frames()) all.push(await frameFacts(frame));
  // Case-insensitive: stylesheets such as text-transform:uppercase change the visible text, not the encoding.
  const text = norm(all.map(a => a.text).join('\n')).toLowerCase();
  // One finding per page and field: a raw unclosed <i> makes the parser re-create it in every later
  // block, so a single injection otherwise reports once per following element.
  for (const a of all) for (const i of a.injected) {
    // A payload number this check did not create belongs to a concurrent xss-poison run sharing a global
    // list (document types, lookups); it is that run's finding, not this one's. An empty number is a
    // payload cut short by the page itself and is kept.
    if (i.id && !fields[i.id]) continue;
    const key = `${a.url}|${i.id}`;
    if (findings.seenInjected.has(key)) continue;
    findings.seenInjected.add(key);
    if (findings.isRichText(i.id, a.url)) {
      findings.note(surface, `field ${i.id} (${fields[i.id]}) rendered as sanitised rich text on ${a.url} (by design)`);
      continue;
    }
    findings.add(surface, 'MARKUP-INJECTED', `field ${i.id} (${fields[i.id] || '?'}) became <${i.tag}> (in ${i.context}) on ${a.url}`);
  }
  for (const a of all) for (const x of a.handlers || []) {
    if (x.id !== '?' && !fields[x.id]) continue;
    const key = `${a.url}|handler|${x.tag}|${x.attr}|${x.id}`;
    if (findings.seenInjected.has(key)) continue;
    findings.seenInjected.add(key);
    findings.add(surface, 'HANDLER-SYNTAX', `field ${x.id} (${fields[x.id] || '?'}) breaks the inline ${x.attr} handler on <${x.tag}> (${x.error}) on ${a.url}`);
  }
  for (const a of all) for (const x of a.scripts || []) {
    if (x.id !== '?' && !fields[x.id]) continue;
    const key = `${a.url}|script|${x.id}|${x.snippet}`;
    if (findings.seenInjected.has(key)) continue;
    findings.seenInjected.add(key);
    findings.add(surface, 'SCRIPT-SYNTAX', `field ${x.id} (${fields[x.id] || '?'}) breaks an inline script on ${a.url} (${x.error}): ...${x.snippet}...`);
  }
  const injectedIds = new Set(all.flatMap(a => a.injected.map(i => String(i.id))));
  const okFields = [];
  for (const [n, name] of Object.entries(fields)) {
    const seen = text.includes(`data-xp="${n}"`) || text.includes(`data-xp=${n}>`);
    if (!seen) continue;
    // A page may legitimately cut a long value short ("..."); only the first 32 characters carry the markup
    // characters under test, so an intact head is still proof of encoding.
    if (TIERS.some(tier => { const lit = norm(tier(n)).toLowerCase(); return text.includes(lit) || (lit.length > 40 && text.includes(lit.slice(0, 32))); })) okFields.push(n);
    else if (!injectedIds.has(String(n)) && !all.some(a => findings.isRichText(n, a.url))) {
      const at = Math.max(text.indexOf(`data-xp="${n}"`), text.indexOf(`data-xp=${n}>`));
      findings.add(surface, 'TEXT-MANGLED', `field ${n} (${name}) visible but not literally: ...${text.slice(Math.max(0, at - 12), at + 70)}`);
    }
  }
  findings.coverage.push({ surface, ok: okFields });
  if (since) findings.drain(surface, since);
}

module.exports = { payload, inspect, Findings, norm, frameFacts, TIERS };

/*
 * FIXTURE LIFECYCLE. Poisoned rows are global: a provider, a lookup item or a document type carrying the
 * payload shows up on screens other checks drive, and their forms then echo it back into a POST the WAF
 * refuses. So every row has to go, including after a run that was killed (SIGKILL skips runCheck's cleanup).
 *
 *   1. Each row is recorded by the key it was created with, never by a broad filter, and cleanup deletes
 *      exactly those keys and asserts each one is gone. Natural-key inserts refuse to start when the key
 *      already exists, so a collision can never make cleanup delete a row this run did not create.
 *   2. The recorded keys are also written to a ledger file (one per run, under LEDGER_DIR) as they are
 *      created. A later run deletes a dead run's ledger rows by key before it seeds.
 *   3. A row committed in the instant before a kill, or left by a run that predates the ledger, is caught by
 *      the payload sweep: every row whose text carries `<i data-xp=` (all payload tiers do; real data never
 *      does), with the rows that hang off such patients, providers, documents, messages and lists. The sweep
 *      runs only when no other xss-poison run is alive, and seeding waits behind the same lock, so one run's
 *      sweep can never take another run's live fixture.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const LEDGER_DIR = process.env.XSS_POISON_LEDGER_DIR || path.join(os.tmpdir(), 'carlos-xss-poison');
const MARK = '<i data-xp=';
const LIKE = `LIKE ${h.sqlString(`%${MARK}%`)}`;
// Rows the sweep rewrites instead of deleting: a fixture owned by another helper (the billing check poisons
// the first name of the billing fixture's own provider, and its claim header), and the single shared
// consultation-service script cache (EctConConstructSpecialistsScriptsFile), which only loses its lines.
const NEUTRALISE = new Set(['provider', 'demographic', 'billing_on_cheader1']);
const SWEPT = 'FAKE-XP-swept';

function sleepSync(ms) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); }

function pidAlive(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
  if (pid === process.pid) return true;
  try { process.kill(pid, 0); } catch (error) { if (error.code !== 'EPERM') return false; }
  // A recycled pid belongs to some other program; only a live xss-poison run keeps its ledger.
  try { return fs.readFileSync(`/proc/${pid}/cmdline`, 'utf8').includes('xss-poison'); } catch { return true; }
}

/** Cross-process mutex (an atomic mkdir), so a sweep and another run's seeding never interleave. */
function withLock(body) {
  fs.mkdirSync(LEDGER_DIR, { recursive: true, mode: 0o700 });
  const lock = path.join(LEDGER_DIR, '.lock');
  const deadline = Date.now() + 180000;
  for (;;) {
    try { fs.mkdirSync(lock); break; } catch (error) {
      if (error.code !== 'EEXIST') throw error;
      let holder = NaN; let age = 0;
      try { holder = Number(fs.readFileSync(path.join(lock, 'pid'), 'utf8')); } catch { /* not written yet */ }
      try { age = Date.now() - fs.statSync(lock).mtimeMs; } catch { continue; }
      if ((!Number.isNaN(holder) && !pidAlive(holder)) || age > 600000) { fs.rmSync(lock, { recursive: true, force: true }); continue; }
      h.assert(Date.now() < deadline, 'Another xss-poison run held the fixture lock for three minutes');
      sleepSync(250);
    }
  }
  try {
    fs.writeFileSync(path.join(lock, 'pid'), String(process.pid));
    return body();
  } finally { fs.rmSync(lock, { recursive: true, force: true }); }
}

function readLedgers() {
  let names = [];
  try { names = fs.readdirSync(LEDGER_DIR).filter(n => n.endsWith('.json')); } catch { return []; }
  return names.map(name => {
    const file = path.join(LEDGER_DIR, name);
    try { return { file, ...JSON.parse(fs.readFileSync(file, 'utf8')) }; } catch { return { file, pid: 0, entries: [] }; }
  });
}

/** Runs one recorded cleanup entry and proves it took effect. */
function undo(sql, entry) {
  if (entry.statement) {
    sql.execute(entry.statement);
    if (entry.verify) h.assert(sql.value(entry.verify) === '0', `${entry.table} fixture text was not removed`);
    return;
  }
  sql.execute(`DELETE FROM ${entry.table} WHERE ${entry.where}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM ${entry.table} WHERE ${entry.where}`) === '0', `${entry.table} fixture row was not removed`);
}

function undoAll(sql, entries) {
  const failures = [];
  for (const entry of [...entries].reverse()) {
    if (!entry || (!entry.where && !entry.statement)) continue;
    try { undo(sql, entry); } catch (error) { failures.push(error.message); }
  }
  return failures;
}

/** Text columns of every base table, for the sweep and its proof. */
function textColumns(sql) {
  return sql.rows(`SELECT c.TABLE_NAME, c.COLUMN_NAME FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME AND t.TABLE_TYPE='BASE TABLE'
    WHERE c.TABLE_SCHEMA=DATABASE() AND c.DATA_TYPE IN ('char','varchar','tinytext','text','mediumtext','longtext')
    ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION`).reduce((map, [table, column]) => {
    (map[table] = map[table] || []).push(column);
    return map;
  }, {});
}

/** Tables that still hold a payload row, with the matching row count. */
function payloadTables(sql, columns = textColumns(sql)) {
  const tables = Object.keys(columns);
  const found = {};
  // One UNION per slice keeps each statement well inside the runner's 30s and output limits.
  for (let i = 0; i < tables.length; i += 60) {
    const union = tables.slice(i, i + 60).map(t => `SELECT ${h.sqlString(t)}, COUNT(*) FROM \`${t}\` WHERE ${columns[t].map(c => `\`${c}\` ${LIKE}`).join(' OR ')}`).join(' UNION ALL ');
    for (const [table, count] of sql.rows(union)) if (count !== '0') found[table] = Number(count);
  }
  return found;
}

/**
 * Removes every payload row an aborted run left behind, children first, then proves none is left.
 * Only called under the lock and only when no other xss-poison run is alive.
 */
function sweepPayloadRows(sql) {
  const P = `SELECT demographic_no FROM demographic WHERE last_name ${LIKE}`;
  const V = `SELECT provider_no FROM provider WHERE last_name ${LIKE}`;
  const DOC = `SELECT document_no FROM document WHERE docdesc ${LIKE}`;
  const MSG = `SELECT messageid FROM messagetbl WHERE thesubject ${LIKE} OR themessage ${LIKE}`;
  const sub = q => `SELECT * FROM (${q}) x`; // MariaDB refuses a subquery on the table being deleted from
  const children = [
    `DELETE FROM casemgmt_issue_notes WHERE note_id IN (SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${P}) OR note ${LIKE})`,
    `DELETE FROM casemgmt_issue WHERE demographic_no IN (${P})`,
    `DELETE FROM casemgmt_note WHERE demographic_no IN (${P})`,
    `DELETE FROM preventionsExt WHERE prevention_id IN (SELECT id FROM preventions WHERE demographic_no IN (${P}))`,
    `DELETE FROM tickler_comments WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE demographic_no IN (${P}) OR message ${LIKE})`,
    `DELETE FROM ctl_document WHERE document_no IN (${DOC}) OR (module='demographic' AND module_id IN (${P}))`,
    `DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${DOC})`,
    `DELETE FROM patientLabRouting WHERE (lab_type='DOC' AND lab_no IN (${DOC})) OR demographic_no IN (${P})`,
    `DELETE FROM messagelisttbl WHERE message IN (${MSG})`,
    `DELETE FROM msgDemoMap WHERE messageID IN (${MSG}) OR demographic_no IN (${P})`,
    `DELETE FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name ${LIKE})`,
    `DELETE FROM eform_groups WHERE fid IN (SELECT fid FROM eform WHERE form_name ${LIKE})`,
    `DELETE FROM waitingList WHERE listID IN (SELECT ID FROM waitingListName WHERE name ${LIKE})`,
    `DELETE FROM LookupListItem WHERE lookupListId IN (SELECT id FROM LookupList WHERE listTitle ${LIKE})`,
    `DELETE FROM serviceSpecialists WHERE serviceId IN (SELECT serviceId FROM consultationServices WHERE serviceDesc ${LIKE}) OR specId IN (SELECT specId FROM professionalSpecialists WHERE lName ${LIKE})`,
    `DELETE FROM DemographicContact WHERE demographicNo IN (${P})`,
    `DELETE FROM secUserRole WHERE provider_no IN (${V})`,
    `DELETE FROM mygroup WHERE provider_no IN (${V})`,
    `DELETE FROM scheduledate WHERE provider_no IN (${V})`,
    `DELETE FROM scheduletemplate WHERE provider_no IN (${V})`,
    `DELETE FROM appointment WHERE provider_no IN (${V}) OR demographic_no IN (${P})`,
    `DELETE FROM provider WHERE provider_no IN (${sub(V)})`,
  ];
  for (const [table, column] of PATIENT_TABLES) children.push(`DELETE FROM ${table} WHERE ${column} IN (${P})`);
  children.push(`DELETE FROM demographic WHERE demographic_no IN (${sub(P)})`);
  sql.execute(children.join(';\n'));
  // The consultation-service script cache is one shared row: drop only the lines that carry a payload.
  sql.execute(`UPDATE specialistsJavascript SET javascriptString=REGEXP_REPLACE(javascriptString, '(?m)^[KD]\\\\([-0-9]+,[^\\n]*data-xp[^\\n]*\\n', '') WHERE javascriptString LIKE '%data-xp%'`);
  // Whatever still carries the payload is a standalone fixture row (a role, a queue, a lookup item ...).
  const columns = textColumns(sql);
  for (const table of Object.keys(payloadTables(sql, columns))) {
    const cols = columns[table];
    const where = cols.map(c => `\`${c}\` ${LIKE}`).join(' OR ');
    if (NEUTRALISE.has(table)) sql.execute(`UPDATE \`${table}\` SET ${cols.map(c => `\`${c}\`=IF(\`${c}\` ${LIKE}, ${h.sqlString(SWEPT)}, \`${c}\`)`).join(', ')} WHERE ${where}`);
    else if (table !== 'specialistsJavascript') sql.execute(`DELETE FROM \`${table}\` WHERE ${where}`);
  }
  const left = payloadTables(sql, columns);
  h.assert(Object.keys(left).length === 0, `xss-poison sweep left payload rows in ${Object.keys(left).join(', ')}`);
}

// Rows an opened chart (or this sweep's own fixtures) add under a patient; removed by patient number.
const PATIENT_TABLES = [
  ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'], ['demographicExt', 'demographic_no'],
  ['demographicArchive', 'demographic_no'], ['demographiccust', 'demographic_no'], ['measurementsDeleted', 'demographicNo'],
  ['demographicaccessory', 'demographic_no'], ['eChart', 'demographicNo'], ['allergies', 'demographic_no'], ['drugs', 'demographic_no'],
  ['measurements', 'demographicNo'], ['preventions', 'demographic_no'], ['eform_data', 'demographic_no'], ['tickler', 'demographic_no'],
  ['consultationRequests', 'demographicNo'], ['relationships', 'demographic_no'], ['waitingList', 'demographic_no'],
  ['reportagesex', 'demographic_no'], ['log', 'demographic_no'],
];

/**
 * Owned fixture rows (see FIXTURE LIFECYCLE above). `cleanup` is the session's registration hook; the
 * Seeder registers itself before its first insert so a failed run still removes whatever it created.
 */
class Seeder {
  constructor(sql, cleanup, marker = `pid${process.pid}`) {
    this.sql = sql;
    this.rows = [];
    this.ledger = path.join(LEDGER_DIR, `${String(marker).replace(/[^A-Za-z0-9-]/g, '')}.json`);
    this.opened = false;
    cleanup(() => this.cleanup());
  }

  /** Recover dead runs, sweep when alone, then claim this run's ledger, all under the lock. */
  open() {
    if (this.opened) return;
    withLock(() => {
      const ledgers = readLedgers().filter(l => l.file !== this.ledger);
      for (const dead of ledgers.filter(l => !pidAlive(l.pid))) {
        const failures = undoAll(this.sql, dead.entries || []);
        h.assert(failures.length === 0, `Recovering an aborted xss-poison run failed: ${failures.join('; ')}`);
        fs.rmSync(dead.file, { force: true });
      }
      if (!ledgers.some(l => pidAlive(l.pid))) sweepPayloadRows(this.sql);
      this.opened = true;
      this.save();
    });
  }

  save() {
    fs.writeFileSync(this.ledger, JSON.stringify({ pid: process.pid, started: new Date().toISOString(), entries: this.rows }), { mode: 0o600 });
  }

  /** Record a cleanup that deletes `table` rows matching `where` (rows the application may add later). */
  track(table, where) {
    this.open();
    this.rows.push({ table, where });
    this.save();
  }

  /** Record an arbitrary cleanup statement and a COUNT query that must return 0 afterwards. */
  trackStatement(table, statement, verify) {
    this.open();
    this.rows.push({ table, statement, verify });
    this.save();
  }

  lit(value) {
    if (value === null || value === undefined) return 'NULL';
    if (typeof value === 'number') return String(value);
    if (value && value.raw) return value.raw;
    return h.sqlString(value);
  }

  /**
   * Insert one row. `key` names the auto-increment column; a natural-key row passes `where` instead, and
   * the insert is refused when that key already exists, so cleanup can only ever delete this run's row.
   */
  insert(table, values, { key = null, where = null } = {}) {
    this.open();
    const cols = Object.keys(values);
    const statement = `INSERT INTO ${table} (${cols.map(c => `\`${c}\``).join(',')}) VALUES (${cols.map(c => this.lit(values[c])).join(',')})`;
    try {
      if (key) {
        const id = h.insertId(this.sql, statement, table);
        this.rows.push({ table, where: `${key}=${id}` });
        this.save();
        return id;
      }
      h.assert(where, `${table}: natural-key fixture needs a where clause`);
      h.assert(this.sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${where}`) === '0', `${table}: the fixture key is already taken`);
      this.sql.execute(statement);
      this.rows.push({ table, where });
      this.save();
      return null;
    } catch (error) {
      // The runner withholds SQL text on purpose; name only the table so the fixture can be fixed.
      throw new Error(`xss-poison fixture insert into ${table} failed (${String(error.message).slice(0, 80)})`);
    }
  }

  cleanup() {
    const failures = undoAll(this.sql, this.rows);
    if (failures.length === 0) fs.rmSync(this.ledger, { force: true });
    else this.save();
    h.assert(failures.length === 0, failures.join('; '));
    if (!this.opened) return;
    // Pages can copy a stored value into rows of their own (an audit row, a regenerated cache). When this is
    // the only xss-poison run alive, prove the database holds no payload at all, sweeping what the walk left.
    withLock(() => {
      if (readLedgers().some(l => l.file !== this.ledger && pidAlive(l.pid))) return;
      const residue = payloadTables(this.sql);
      if (Object.keys(residue).length) {
        console.log(`  NOTE cleanup: payload copies the application wrote were swept from ${Object.keys(residue).join(', ')}`);
        sweepPayloadRows(this.sql);
      }
    });
  }
}

/**
 * The consultation-service picker script is cached in one shared row and regenerated by the consultation
 * settings pages, so a seeded service ends up inside it. Strip exactly that service's K()/D() lines.
 */
function trackServiceScript(seed, serviceId) {
  h.assert(/^[1-9]\d*$/.test(String(serviceId)), 'service script cleanup needs an owned service id');
  seed.trackStatement('specialistsJavascript',
    `UPDATE specialistsJavascript SET javascriptString=REGEXP_REPLACE(javascriptString, '(?m)^[KD]\\\\(${serviceId},[^\\n]*\\n', '') WHERE javascriptString LIKE '%(${serviceId},%'`,
    `SELECT COUNT(*) FROM specialistsJavascript WHERE javascriptString LIKE '%K(${serviceId},%' OR javascriptString LIKE '%D(${serviceId},%'`);
}

/** First provider number from `start` that no provider row uses (the hash-derived number can collide). */
function freeProviderNo(sql, start) {
  for (let no = start; no < start + 500; no += 1) {
    if (sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(String(no))}`) === '0') return String(no);
  }
  throw new Error('No free fixture provider number');
}

module.exports.Seeder = Seeder;
module.exports.freeProviderNo = freeProviderNo;
module.exports.trackServiceScript = trackServiceScript;
module.exports.sweepPayloadRows = sweepPayloadRows;
module.exports.payloadTables = payloadTables;
module.exports.PATIENT_TABLES = PATIENT_TABLES;
module.exports.MARK = MARK;

/**
 * Schedule > Search > Chart No > result row > Master Demographic File, entered by clicks. `onResults` gets
 * the search-results page before the row is clicked so the caller can inspect it.
 */
async function openMasterByChartNo(s, chartNo, onResults = null) {
  const ui = require('./playwright-ui');
  const { page: search } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#search a').first(),
    { context: s.context, label: 'patient-search', recorder: s.recorder, timeout: 20000 });
  await search.locator('#search_mode').selectOption('search_chart_no');
  await search.locator('#keyword, input[name="keyword"]').first().fill(chartNo);
  await ui.clickAndAwaitReload(search, search.locator("input[type='submit']").first(), { timeout: 20000, label: 'the patient search' });
  const row = search.locator('a[title="Master Demographic File"]');
  h.assert(await row.count() === 1, 'The chart-number search did not return exactly the owned patient');
  if (onResults) await onResults(search);
  const master = await ui.clickOpensPopup(search, row.first(),
    { context: s.context, label: 'master-record', recorder: s.recorder, timeout: 20000 });
  await master.locator('#editBtn').waitFor({ state: 'visible', timeout: 20000 });
  return { master, search };
}
module.exports.openMasterByChartNo = openMasterByChartNo;

/**
 * Click each catalogued link the way an operator does, inspect whatever it produced (popup, in-place
 * navigation or an injected panel) BEFORE leaving it, then put the host back. Unlike auditCatalogue this
 * hands the destination to the sweep, which is the point: auditCatalogue closes it first.
 */
async function walkLinks({ context, recorder, host, items, findings, fields, label, skip = [], timeout = 20000,
  beforeItem = null, beforeClose = null, limit = 0 }) {
  const audit = require('./playwright-link-audit');
  let count = 0;
  const knownPages = new Set(context.pages());
  for (const item of items) {
    if (skip.some(r => r.match.test(item.text))) continue;
    // A plain link back to the page being walked reaches no new surface (and re-entering the Master Record while
    // its eligibility fetch is still in flight raises an unrelated TypeError).
    if (audit.isCurrentDocumentLink(item, host.url())) continue;
    if (limit && count >= limit) break;
    count += 1;
    const surface = `${label}:${item.text}`;
    const since = findings.mark();
    const hostUrl = host.url();
    let popup = null;
    let navigated = false;
    let lastWhere = '?';
    try {
      if (beforeItem) await beforeItem(item);
      const link = await audit.resolveAuditLink(host, item, timeout);
      try {
        await audit.revealAuditLink(host, link, timeout);
        await link.scrollIntoViewIfNeeded({ timeout }).catch(() => {});
        const fingerprint = await host.evaluate(audit.bodyFingerprint, null).catch(() => null);
        const pagesBefore = [...context.pages()];
        // Whichever happens first wins: a navigation, a new window, or the page body changing in place.
        const never = new Promise(() => {});
        const popupSeen = (async () => {
          const until = Date.now() + 5000;
          while (Date.now() < until) {
            const found = context.pages().find(p => !pagesBefore.includes(p));
            if (found) return found;
            await new Promise(r => setTimeout(r, 100));
          }
          return never;
        })();
        const first = Promise.race([
          host.waitForURL(u => String(u) !== hostUrl, { timeout: 5000 }).then(() => 'navigated', () => never),
          popupSeen.then(p => { popup = p; return 'popup'; }),
          fingerprint === null ? never : host.waitForFunction(audit.bodyFingerprint, fingerprint, { timeout: 5000 }).then(() => 'in-place', () => never),
          new Promise(resolve => setTimeout(() => resolve('nothing'), 5500)),
        ]);
        // noWaitAfter: the outcome race below observes the navigation/popup itself; waiting inside click() made one
        // slow popup stall the host and every later item behind it.
        await link.click({ timeout, noWaitAfter: true });
        const outcome = await first;
        navigated = outcome === 'navigated';
        if (navigated) await host.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
      } finally {
        if (item.identity && typeof link.dispose === 'function') await link.dispose().catch(() => {});
      }
      const target = popup || host;
      if (popup) {
        await popup.waitForURL(u => String(u) !== 'about:blank', { timeout }).catch(() => {});
        await popup.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
      }
      // Some pages poll and never go idle; a short settle is enough for the inspection that follows.
      await target.waitForLoadState('networkidle', { timeout: 4000 }).catch(() => {});
      // Panes such as the eForm upload form load into frames after the list itself.
      for (const frame of target.frames()) await frame.waitForLoadState('load', { timeout: 3000 }).catch(() => {});
      await target.waitForTimeout(600);
      const body = await target.locator('body').innerText({ timeout }).catch(() => '');
      if (audit.ERROR_PAGE_RE.test(body)) findings.add(surface, 'ERROR-PAGE', body.replace(/\s+/g, ' ').slice(0, 100));
      const where = new URL(target.url()).pathname.replace(/^.*\/carlos/, '');
      lastWhere = where;
      await inspect(findings, `${surface} -> ${where}`, target, fields);
      if (beforeClose && popup) await beforeClose(popup);
    } catch (error) {
      // Inconclusive, not a defect: the menu changed under the sweep or the click was swallowed.
      findings.note(surface, `not opened: ${String(error.message).split('\n')[0].slice(0, 110)}`);
    } finally {
      findings.drain(`${surface} -> ${lastWhere}`, since);
      for (const extra of context.pages()) if (extra !== host && extra !== popup && !knownPages.has(extra)) await extra.close().catch(() => {});
      if (popup) await popup.close().catch(() => {});
      else if (navigated && host.url() !== hostUrl) {
        await host.goBack({ timeout }).catch(() => {});
        await host.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
      }
      // One stuck item must not take the rest of the sweep with it: if the host no longer answers, reload it.
      const alive = await Promise.race([host.evaluate(() => true).catch(() => false), new Promise(r => setTimeout(() => r(false), 4000))]);
      if (!alive && !host.isClosed()) {
        await host.reload({ timeout: 20000, waitUntil: 'domcontentloaded' }).catch(() => {});
        findings.note(surface, 'host page stopped answering after this item and was reloaded');
      }
    }
  }
}
module.exports.walkLinks = walkLinks;

/** Administration menu entry by its visible text; reveals its collapsed menu the way an operator opens it. */
async function clickAdminItem(admin, text) {
  const { revealAuditLink } = require('./playwright-link-audit');
  const link = admin.locator('a', { hasText: new RegExp(`^\\s*${text}\\s*$`) }).first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click({ timeout: 20000 });
  await admin.waitForLoadState('networkidle').catch(() => {});
  await admin.waitForTimeout(500);
}
module.exports.clickAdminItem = clickAdminItem;

/**
 * ProviderDaoImpl.getActiveProviders() is cached for five minutes (CacheConfig.ACTIVE_PROVIDERS) and only the
 * DAO's own save/update evicts it, so a provider INSERTed by SQL is not listed in the provider selects until
 * the cache entry written before the insert expires. Wait that out once so the selects are inspected with the
 * fixture present, rather than sometimes, depending on what ran before.
 */
async function waitForProviderCache(session, label = 'wait for the five-minute active-provider cache to expire') {
  await session.step(label, async () => { await new Promise(resolve => setTimeout(resolve, 305000)); });
}
module.exports.waitForProviderCache = waitForProviderCache;
