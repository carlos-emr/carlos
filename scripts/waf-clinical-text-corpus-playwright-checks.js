#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Real clinical prose saves through the packaged front door on every route that has a prose WAF
 * exclusion (coverage plan: waf-clinical-text-corpus).
 *
 * WHY THIS CHECK EXISTS. On a packaged (deb) install nginx runs ModSecurity with the OWASP CRS in
 * blocking mode, and the CRS content signatures cannot tell a clinician's prose from an attack: a
 * sentence-ending semicolon reads as a shell separator, "../" in a reference to a filed report as
 * path traversal, a value that begins with a pasted internal PACS link as remote file inclusion.
 * Package exclusions 1010, 1030-1045, 1104-1136 and 1141 (REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf) unhook those
 * families per argument, per route. Until now the clinical phrase corpus (lib/clinical-prose-corpus.js)
 * went through two of those routes (clinical-freetext: the consultation request and the master
 * record's Alert/Notes); every other check saves marker text that never scores, so a WAF false
 * positive on a clinician's note would first show up in the field as a bare 403.
 *
 * WHAT IT DOES. One row per route and per defect (lib/waf-corpus-rows.js: 22 rows over 20 of the package's 62 exclusion
 * rules and one after-CRS pattern; the tickler list and the custom Rx drug name are two rows each, see that file). Each row is two steps:
 *   1. a CONTROL step drives the real UI save with plain marker text. That proves the page, the route
 *      and the fixture work through the front door, and for a row that replays it captures the request
 *      the page sent, so the route and the argument names are the page's own and not a guess;
 *   2. a CORPUS step saves the clinical corpus (every phrase, joined with newlines or spaces, the
 *      pasted-link phrase first because CRS 931100 is anchored on the start of an argument; fitted to
 *      the column when the column is short, see lib/waf-corpus.js) and asserts the row the save wrote
 *      holds the EXACT text, byte for byte (HEX compared, so a charset hop or an encoder cannot hide).
 * What the corpus step asserts is the row's `outcome`: 'stored' (the default) is the byte-exact database row above;
 * 'accepted' is for a route that stores nothing for the request (the Rx rename goes to a session stash, a search term, a
 * display page) and asserts it answered as it should; 'echoed' is for a route whose ANSWER carries the text back.
 * A WAF 403, or a response the WAF drops, fails the corpus step naming the route, the exclusion that
 * should have covered it and the CRS rules the ModSecurity audit log reports for the request. The step
 * labels are the fault line for findings: a row's corpus step holds only that row's assertion, and the
 * UI drive that is not about the WAF fails under a different label.
 *
 * HOW EACH ROW IS DRIVEN is its `drive` field, printed with the result:
 *   - replay: the control step is the real UI and the corpus step posts the captured request again from
 *     the same session (cookies, Referer, Origin and CSRF token as the page sent them,
 *     lib/mutation-replay.js) with the prose argument(s) replaced. The sentence is what the WAF measures;
 *     a form's own client validation differs per form and would measure the form.
 *   - typed: the corpus is typed into the real page and the page's own button is pressed (the Rich Text
 *     Letter, whose posted bytes are the editor's HTML, and the Rx rows, whose boxes post to a session
 *     stash as they lose focus and whose Save posts every box again).
 *
 * The sweep keeps going when a row fails, so every route reports, and fails at the end. One failed row
 * keeps its own step label so a manifest `expectedFailure` can pin it; several failed rows are labelled
 * "N route rows failed", which no pin matches. WAF_CORPUS_ONLY=<row,row> runs just those rows and
 * WAF_CORPUS_SKIP=<row,row> leaves them out: the manifest runs each row that has an open finding
 * on its own, pinned, and the rest together.
 *
 * Run with EXPECT_FRONT_DOOR=true, or the run says so and degrades to guarding that the save paths keep
 * the exact text: against bare Tomcat nothing inspects the prose.
 *
 * Fixtures: the workflow's owned FAKE- patient, one demo HL7 lab routed to the test login for the
 * acknowledge (its routing row restored exactly), one owned PDF in DOCUMENT_DIR, owned bills, an owned
 * tickler, and the rows each save writes (found by the patient and by an id watermark taken before the
 * corpus is sent). Cleanup deletes them by key and asserts they are gone. No clinic-wide state is changed.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { watchFrontDoor } = require('./lib/front-door-checks');
const boundary = require('./lib/boundary-values');
const fam = require('./lib/mutation-families');
const tickler = require('./lib/concurrency-tickler');
const { captureRequest, multipartFields, replayParams, takeKnownNoise } = require('./lib/get-reject-probe');
const { createLabRoutingFixture } = require('./lib/lab-routing-fixture');
const pdfs = require('./lib/stored-pdf-documents');
const billing = require('./billing-on-invoice-third-party-playwright-checks');
const rep = require('./lib/mutation-replay');
const waf = require('./lib/waf-corpus');
const { FACTS, controlStepLabel, corpusStepLabel, outcomeOf, selectedKeys } = require('./lib/waf-corpus-rows');

/**
 * What a row's exclusion is called in a message: the numbered rule(s), the after-CRS pattern a row without one relies on,
 * or, for a row that measures the response body, the fact that the package has no exclusion for one.
 */
const exclusionOf = (row) => {
  if (row.rules.length) return `exclusion ${row.rules.join('/')}`;
  return row.afterCrs ? `the after-CRS pattern ${row.afterCrs.join(' ')}` : 'no exclusion (the package has none for a response body)';
};

/** What a row's corpus step proves, in the words of the progress line and of a failure (the row's `outcome`, see lib/waf-corpus-rows.js). */
const OUTCOME_TEXT = {
  stored: { done: 'LANDED', detail: 'stored exactly', unmet: 'the text did not land' },
  accepted: { done: 'ACCEPTED', detail: 'accepted, nothing stored for it', unmet: 'the route did not take it as it should' },
  echoed: { done: 'ECHOED', detail: 'came back exactly', unmet: 'the answer did not carry the text back' },
};

/** What the front door (or the application behind it) did to a row's corpus: the corpus step's assertion. */
class RowFailure extends Error {}

const q = h.sqlString;
const POLL_MS = 15000;

/** `name` as a quoted SQL identifier; only plain names are ever passed. */
function ident(name) {
  h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(name), `not a plain SQL identifier: ${name}`);
  return `\`${name}\``;
}

const hex = (text) => boundary.hex(text);
const decode = (value) => Buffer.from(value || '', 'hex').toString('utf8');
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Landing in a table: the newest rows of `table` that match `scope` and are newer than `mark`, and
 * the question "does one of them hold exactly these values?". Polls, because a save can be
 * asynchronous. On failure the message says how the nearest row differs (truncated, re-encoded).
 *
 * @param fields [{name, column}] request argument name -> column that stores it (or a function of them)
 * @returns async (values) => null when a row holds every value exactly, else why not
 */
function tableLanding(s, { table, idColumn, scope, fields, mark = () => 0 }) {
  const resolve = (value) => (typeof value === 'function' ? value() : value);
  const read = (list) => s.sql.rows(`SELECT ${list.map((field) => `HEX(${ident(field.column)}), CHAR_LENGTH(${ident(field.column)})`).join(', ')}
    FROM ${ident(table)} WHERE (${resolve(scope)}) AND ${ident(idColumn)} > ${mark()} ORDER BY ${ident(idColumn)} DESC LIMIT 200`);
  return async (values) => {
    const list = resolve(fields);
    const wanted = list.map((field) => hex(values[field.name]));
    const deadline = Date.now() + POLL_MS;
    let rows;
    do {
      rows = read(list);
      if (rows.some((row) => wanted.every((value, i) => (row[i * 2] || '') === value))) return null;
      await wait(200);
    } while (Date.now() < deadline);
    if (!rows.length) return `no ${table} row was written for the owned fixtures`;
    // The newest row is the likeliest to be this save; say how its first mismatching field differs.
    const row = rows[0];
    const i = list.findIndex((field, n) => (row[n * 2] || '') !== wanted[n]);
    const stored = { hex: row[i * 2] || '', chars: Number(row[i * 2 + 1] || 0) };
    return `${table}.${list[i].column} differs from what was sent: ${boundary.explainMismatch(values[list[i].name], stored)}`;
  };
}

/** The highest value of `column` in `table` now (0 when empty): an id watermark taken before a save. */
function highWater(s, table, column) {
  return () => Number(s.sql.value(`SELECT IFNULL(MAX(${ident(column)}), 0) FROM ${ident(table)}`));
}

/**
 * captureRequest for a page that closes itself on Save: the request's headers and body are read the
 * moment it is sent (the synchronous parts of the Playwright request), because by the time the
 * response has arrived the popup that sent it is gone and allHeaders() would throw. Same shape as
 * lib/get-reject-probe.js captureRequest, which mutation-replay.js consumes.
 */
async function captureOnSend(context, match, act, { timeout = 30000 } = {}) {
  const seen = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { context.off('request', listener); reject(new Error('no matching request was sent')); }, timeout);
    const listener = (request) => {
      if (request.method() !== 'POST' || !match(new URL(request.url()))) return;
      context.off('request', listener);
      clearTimeout(timer);
      const url = new URL(request.url());
      const headers = request.headers();
      const type = headers['content-type'] || '';
      const body = new URLSearchParams();
      if (/application\/x-www-form-urlencoded/i.test(type)) {
        for (const [key, value] of new URLSearchParams(request.postData() || '')) body.append(key, value);
      } else if (/multipart\/form-data/i.test(type)) {
        for (const [key, value] of multipartFields(request.postDataBuffer(), type)) body.append(key, value);
      }
      resolve({
        path: url.pathname, method: 'POST', query: new URLSearchParams(url.search), body, headers, contentType: type,
        postData: request.postData() || '', params: new URLSearchParams([...new URLSearchParams(url.search), ...body]),
        status: 0, request,
      });
    };
    context.on('request', listener);
  });
  seen.catch(() => {});
  await act();
  const captured = await seen;
  const response = await captured.request.response().catch(() => null);
  captured.status = response ? response.status() : 0;
  return captured;
}

/** Close pages a control step opened, whether it finished or threw, so a named popup can open again. */
async function closeAll(pages) {
  for (const page of pages) if (page && !page.isClosed()) await page.close().catch(() => {});
}

// ---------------------------------------------------------------------------------------------
// Cleanup by key. Each removes what a row's saves wrote for the owned patient and asserts it is gone.

const expectGone = (s, label, sql) => h.assert(s.sql.value(sql) === '0', `${label} were not removed`);

function removeTicklers(s) {
  const ids = s.sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no=${s.patient}`).map(([id]) => id);
  ids.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned tickler id is invalid'));
  if (ids.length) {
    const list = ids.join(',');
    s.sql.execute(`DELETE FROM tickler_comments WHERE tickler_no IN (${list}); DELETE FROM tickler_update WHERE tickler_no IN (${list});
      DELETE FROM ticklerdocs WHERE tickler_id IN (${list}); DELETE FROM tickler WHERE tickler_no IN (${list}) AND demographic_no=${s.patient}`);
  }
  expectGone(s, 'Owned ticklers', `SELECT COUNT(*) FROM tickler WHERE demographic_no=${s.patient}`);
}

function removeAppointments(s) {
  s.sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${s.patient}; DELETE FROM appointment WHERE demographic_no=${s.patient}`);
  expectGone(s, 'Owned appointments', `SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${s.patient})
    + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${s.patient})`);
}

function removeNotes(s) {
  const ids = s.sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${s.patient}`).map(([id]) => id);
  ids.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned note id is invalid'));
  if (ids.length) {
    const list = ids.join(',');
    s.sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list}); DELETE FROM casemgmt_note_ext WHERE note_id IN (${list});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${list}); DELETE FROM casemgmt_note WHERE note_id IN (${list}) AND demographic_no=${s.patient}`);
  }
  s.sql.execute(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${s.patient}; DELETE FROM casemgmt_note_lock WHERE demographic_no=${s.patient}`);
  expectGone(s, 'Owned chart notes', `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${s.patient}`);
}

function removeBills(s) {
  const ids = s.sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${s.patient}`).map(([id]) => id);
  ids.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned bill id is invalid'));
  for (const id of ids) {
    s.sql.execute(`DELETE FROM billing_on_transaction WHERE ch1_id=${id}; DELETE FROM billing_on_ext WHERE billing_no=${id};
      DELETE FROM billing_on_item WHERE ch1_id=${id}; DELETE FROM billing_on_cheader1 WHERE id=${id} AND demographic_no=${s.patient}`);
  }
  expectGone(s, 'Owned bills', `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${s.patient}`);
}

function removeMeasurements(s) {
  s.sql.execute(`DELETE FROM measurements WHERE demographicNo=${s.patient}; DELETE FROM measurementsDeleted WHERE demographicNo=${s.patient}`);
  expectGone(s, 'Owned measurements', `SELECT COUNT(*) FROM measurements WHERE demographicNo=${s.patient}`);
}

/**
 * A message has no patient, so the run takes the highest ids before it sends anything and removes what
 * the test provider sent above them (the demo install carries orphan delivery rows whose message ids a
 * new message can reuse, so those are removed only above their own marks).
 */
function removeMessages(s, mark) {
  const ids = s.sql.rows(`SELECT messageid FROM messagetbl WHERE sentbyNo=${q(s.provider)} AND messageid > ${mark.message}`).map(([id]) => id);
  ids.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned message id is invalid'));
  if (ids.length) {
    const list = ids.join(',');
    s.sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${list}) AND id > ${mark.list};
      DELETE FROM msgDemoMap WHERE messageID IN (${list}) AND id > ${mark.demoMap}; DELETE FROM messagetbl WHERE messageid IN (${list})`);
  }
  expectGone(s, 'Owned messages', `SELECT COUNT(*) FROM messagetbl WHERE sentbyNo=${q(s.provider)} AND messageid > ${mark.message}`);
}

function removeDrugs(s) {
  const patient = s.patient;
  const drugs = s.sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(([id]) => id);
  drugs.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned drug id is invalid'));
  removeNotes(s);
  s.sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
    ${drugs.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugs.join(',')});` : ''}
    DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
    DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
  expectGone(s, 'Owned Rx rows', `SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`);
}

// ---------------------------------------------------------------------------------------------
// Row builders

/**
 * A row that captures the page's own request in its control step and replays it with the corpus.
 *
 * @param spec.key, title, rules, route, drive   what the row is called, the exclusion(s) it tests, and how it is driven
 * @param spec.fields     [{name, column, limit?, joiner?}] (or a function of them) the prose arguments to fill
 * @param spec.prepare    () => void  fixtures; registers their cleanup before writing anything
 * @param spec.control    async () => captured request (lib/get-reject-probe.js shape)
 * @param spec.where      'body' (default) or 'query': where the prose argument travels in the captured request
 * @param spec.landing    tableLanding(...), or a function (values, answer) => null | why not
 * @param spec.watermark  () => void  takes the id watermark before the corpus is sent
 * @param spec.aim        async (captured) => {overrides, query}: fresh server state the replay needs
 */
function replayRow(s, ctx, spec) {
  let captured = null;
  return {
    key: spec.key, title: spec.title, rules: spec.rules, afterCrs: spec.afterCrs, route: spec.route, method: spec.method, drive: spec.drive,
    get fields() { return typeof spec.fields === 'function' ? spec.fields() : spec.fields; },
    get captured() { return captured; },
    early() { if (spec.early) spec.early(); },
    async prepare() { if (spec.prepare) await spec.prepare(); },
    async control() {
      captured = await spec.control();
      h.assert(captured.path.endsWith(`/${spec.route}`), `the page posted to ${captured.path}, not /${spec.route}`);
      return captured;
    },
    watermark() { if (spec.watermark) spec.watermark(); },
    async send(values) {
      const aim = spec.aim ? await spec.aim(captured) : {};
      const place = spec.where === 'query' ? { query: { ...aim.query, ...values }, overrides: aim.overrides || {} }
        : { overrides: { ...aim.overrides, ...values }, query: aim.query || {} };
      const replay = rep.buildReplay(captured, { origin: s.config.baseUrl.origin, token: await ctx.token(), ...place });
      return rep.sendReplay(s.context, replay);
    },
    landed: spec.landing,
  };
}

/** A replayRow over a mutation family (lib/mutation-families.js): the family drives the UI and aims the replay. */
function familyRow(s, ctx, spec) {
  const family = spec.family;
  let marks = 0;
  return replayRow(s, ctx, {
    ...spec,
    async prepare() {
      s.cleanup(() => family.cleanup());
      s.cleanup(() => spec.cleanup());
      if (family.prepare) await family.prepare();
    },
    control: () => rep.uiWrite(s, family, 'UI'),
    watermark: () => {
      marks = spec.inPlace ? 0 : highWater(s, spec.table, spec.idColumn)();
      if (spec.watermark) spec.watermark();
    },
    aim: (captured) => family.aim(captured, 'CORPUS', { write: true }),
    landing: spec.landing || tableLanding(s, { table: spec.table, idColumn: spec.idColumn, scope: spec.scope, fields: spec.fields, mark: () => marks }),
  });
}

// ---------------------------------------------------------------------------------------------
// The rows

/** What a row needs from the install, judged before any fixture exists so a missing piece SKIPs the check. */
const PREREQUISITES = {
  'manage-document': () => { pdfs.directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR'); },
  'eform-letter': ({ sql }) => {
    const name = process.env.RTL_FORM_NAME || 'Rich Text Letter';
    if (!/^[1-9]\d*$/.test(sql.value(`SELECT IFNULL(MIN(fid), 0) FROM eform WHERE form_name=${q(name)} AND status=1`))) {
      throw new h.SkipCheck(`the eForm library has no active "${name}"; set RTL_FORM_NAME`);
    }
  },
  'lab-status': ({ sql }) => {
    if (!sql.rows(labCandidates()).length) throw new h.SkipCheck('the install has no HL7 lab linked to a patient to acknowledge');
  },
  'billing-on-correction': ({ sql }) => billFee(sql),
  'billing-on-display': ({ sql }) => billFee(sql),
};

/** A demo lab linked to a patient, not CLS, and the only version of its accession: the page renders exactly it. */
function labCandidates() {
  return `SELECT h.lab_no FROM hl7TextInfo h
    JOIN patientLabRouting pl ON pl.lab_no=h.lab_no AND pl.lab_type='HL7'
    JOIN hl7TextMessage m ON m.lab_id=h.lab_no AND IFNULL(m.type,'') <> 'CLS'
    WHERE h.accessionNum = '' OR (SELECT COUNT(*) FROM hl7TextInfo x WHERE x.accessionNum = h.accessionNum) = 1
    ORDER BY h.lab_no LIMIT 1`;
}

/** The OHIP code, its schedule fee and the bill date the correction and display rows seed a bill with (SKIP without a fee). */
function billFee(sql) {
  const code = (process.env.BILLING_CORRECTION_CODE || 'A007A').toUpperCase();
  h.assert(/^[A-Z]\d{3}[A-Z]$/.test(code), 'BILLING_CORRECTION_CODE must be an Ontario service code like A007A');
  const date = billing.billDate();
  const fee = sql.value(`SELECT IFNULL((SELECT value FROM billingservice WHERE service_code=${q(code)} AND billingservice_date<=${q(date)}
    ORDER BY billingservice_date DESC LIMIT 1), 'none')`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) {
    throw new h.SkipCheck(`service code ${code} has no positive schedule fee on ${date}; set BILLING_CORRECTION_CODE`);
  }
  return { code, date, fee };
}

function rows(s, ctx) {
  const { patient, marker } = s;
  const audit = ctx.audit;
  const owned = `demographic_no=${patient}`;
  const baseUrl = s.config.baseUrl;
  const pathOf = (route) => `${baseUrl.pathname.replace(/\/$/, '')}/${route}`;
  const all = {};

  all['tickler-add'] = () => familyRow(s, ctx, {
    key: 'tickler-add', ...FACTS['tickler-add'],
    drive: 'replay: real UI (chart ▸ Tickler ▸ Add), then the captured POST',
    family: fam.ticklerAdd(s), table: 'tickler', idColumn: 'tickler_no', scope: owned,
    fields: [{ name: 'ticklerMessage', column: 'message' }],
    cleanup: () => removeTicklers(s),
  });

  all['tickler-edit'] = () => {
    // Editing a tickler appends a comment (tickler_comments); the edit form is the page's own.
    let fixture;
    let mark = 0;
    const { route } = FACTS['tickler-edit'];
    const fields = [{ name: 'newMessage', column: 'message' }];
    return replayRow(s, ctx, {
      key: 'tickler-edit', ...FACTS['tickler-edit'],
      drive: 'replay: real UI (Tickler list ▸ edit ▸ Update Tickler), then the captured POST',
      fields,
      prepare() { fixture = tickler.seedTickler(s, 'edit'); },
      async control() {
        let list;
        let edit;
        try {
          list = await tickler.openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list');
          edit = await tickler.openTicklerEdit(s.context, s.recorder, list, fixture.message, 'tickler-edit');
          await edit.locator('#newMessage').fill(`${marker}-TE-UI`);
          const captured = await fam.captureInContext(s.context, (url) => url.pathname.endsWith(`/${route}`), () => tickler.saveTicklerEdit(edit));
          h.assert(captured.status > 0 && captured.status < 400, `the page's own save answered HTTP ${captured.status}`);
          await rep.expectCount(s.sql, 'tickler_comments', `tickler_no=${fixture.id} AND message=${q(`${marker}-TE-UI`)}`, 1, 'the page\'s own comment did not land');
          return captured;
        } finally { await closeAll([edit, list]); }
      },
      watermark: () => { mark = highWater(s, 'tickler_comments', 'id')(); },
      landing: tableLanding(s, { table: 'tickler_comments', idColumn: 'id', scope: () => `tickler_no=${fixture.id}`, fields, mark: () => mark }),
    });
  };

  // ---- The tickler list is a DataTables GET: the search box sends its term as search[value] (exclusion 1141) and the
  // JSON that comes back carries each listed tickler's message. The two directions are two rows, because a manifest
  // pin names one defect: the term going out (tickler-list) and a saved message coming back (tickler-list-response).
  const SEARCH = 'search[value]';

  /** The owned patient's tickler list, searched the way a clinician does; returns the GET the search box sent. */
  async function captureListSearch(message) {
    const { route } = FACTS['tickler-list'];
    let list;
    try {
      list = await tickler.openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list');
      await tickler.listRow(list, message);
      const captured = await captureRequest(list, (url) => url.pathname.endsWith(`/${route}`) && url.searchParams.get(SEARCH) === message,
        () => list.locator('#ticklerResults_filter input[type="search"]').fill(message), { timeout: 20000, method: 'GET' });
      h.assert(captured.status === 200, `the list's own search answered HTTP ${captured.status}`);
      return captured;
    } finally { await closeAll([list]); }
  }

  /** The captured list GET again, from the same session with the headers the page sent, and the search term replaced. */
  function replayListSearch(captured, term) {
    const params = replayParams(captured.params, { [SEARCH]: term }, { keepEmpty: true });
    const headers = {};
    // nosemgrep: javascript.express.security.audit.remote-property-injection.remote-property-injection -- name iterates a fixed list of three header names; headers is a local object handed to this check's own request
    for (const name of ['accept', 'referer', 'x-requested-with']) if (captured.headers[name] !== undefined) headers[name] = captured.headers[name];
    return s.context.request.get(new URL(`${captured.path}?${params}`, baseUrl.origin).toString(), { headers, maxRedirects: 0, failOnStatusCode: false });
  }

  all['tickler-list'] = () => {
    // The fixture tickler is plain; only the search term carries the corpus, so nothing in the answer can trip a
    // response rule. The list must answer, and a term no message contains must not match the fixture: that proves the
    // term reached the application whole and was applied as a filter, not that it was ignored.
    let fixture;
    let captured = null;
    return {
      key: 'tickler-list', ...FACTS['tickler-list'],
      drive: 'replay: real UI (Tickler list ▸ search box), then the captured GET with the search term set to the corpus',
      // The search box is a single-line input, so the corpus is joined with spaces.
      fields: [{ name: SEARCH, joiner: ' ' }],
      get captured() { return captured; },
      prepare() { fixture = tickler.seedTickler(s, 'list'); },
      async control() { captured = await captureListSearch(fixture.message); return captured; },
      watermark() {},
      send: (values) => replayListSearch(captured, values[SEARCH]),
      async landed(values, answer) {
        let json;
        try { json = JSON.parse(answer.body); } catch { return 'the list did not answer with JSON'; }
        if (!Array.isArray(json.data)) return 'the list answer has no data array';
        return json.data.some((entry) => String(entry.id) === String(fixture.id))
          ? 'the list matched the owned tickler against a search term that tickler does not contain' : null;
      },
    };
  };

  all['tickler-list-response'] = () => {
    // The tickler's message is the corpus, written by SQL (saving it through the page is the tickler-add row's business),
    // behind a plain tag the search finds it by. The list's JSON answer has to carry the message back exactly. The package
    // has no exclusion for a response body, so CRS 953120 (a "<?" in the answer) is measured here unshielded.
    let fixture;
    let captured = null;
    let message = '';
    return {
      key: 'tickler-list-response', ...FACTS['tickler-list-response'],
      drive: 'replay: real UI (Tickler list ▸ search box), then the captured GET against a tickler whose message the check set to the corpus',
      fields: [{ name: 'message' }],
      get captured() { return captured; },
      prepare() { fixture = tickler.seedTickler(s, 'list-response'); },
      async control() { captured = await captureListSearch(fixture.message); return captured; },
      watermark() {},
      async send(values) {
        message = `${fixture.message} ${values.message}`;
        s.sql.execute(`UPDATE tickler SET message=${q(message)} WHERE tickler_no=${fixture.id} AND demographic_no=${patient}`);
        return replayListSearch(captured, fixture.message);
      },
      async landed(values, answer) {
        let json;
        try { json = JSON.parse(answer.body); } catch { return 'the list did not answer with JSON'; }
        const row = (json.data || []).find((entry) => String(entry.id) === String(fixture.id));
        if (!row) return `the list does not show the owned tickler (it lists ${(json.data || []).length} ticklers)`;
        return row.message === message ? null : 'the list shows the owned tickler with a message that differs from the one stored';
      },
    };
  };

  // ---- Rx. Writing a prescription is a conversation with a session stash: each box posts to the server
  // as it loses focus (rx/UpdateScript for the sig, rx/WriteScript updateSpecialInstruction and
  // saveCustomName), and Save Only posts every card's boxes at once (rx/WriteScript updateSaveAllDrugs).
  // The Rx rows are therefore driven through the real page throughout, with the corpus TYPED into the
  // box a row is about, and end at the database.
  const rxUi = {
    page: null,
    card: null,
    async open() {
      this.page = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
      await this.page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
      await this.page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    },
    /** Stage a custom drug named `name` from the search box; the sig is typed too unless it is null. */
    async stage(name, sig) {
      const page = this.page;
      const before = await page.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id));
      await page.locator('#searchString').fill(name);
      await h.withExpectedDialogs(page, () => page.locator('#customDrug').click(), { accept: true });
      await page.waitForFunction((count) => document.querySelectorAll("[id^='drugName_']").length > count, before.length, { timeout: 30000 });
      const added = (await page.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id))).filter((id) => !before.includes(id));
      h.assert(added.length === 1, `Staging a custom drug added ${added.length} cards`);
      this.card = added[0].slice('drugName_'.length);
      if (sig !== null) await this.sig(sig);
    },
    /** Type the sig and leave the box: the page posts it to rx/UpdateScript. */
    async sig(text) {
      const page = this.page;
      await page.locator(`#instructions_${this.card}`).fill(text);
      return this.ajax('rx/UpdateScript', (r) => /\/rx\/UpdateScript$/.test(new URL(r.url()).pathname),
        () => page.locator(`label[for="jsonDxSearch_${this.card}"]`).click());
    },
    /** Show the card's special-instruction box, type, leave: the page posts rx/WriteScript updateSpecialInstruction. */
    async special(text) {
      const page = this.page;
      const box = page.locator(`#siInput_${this.card}`);
      if (!(await box.isVisible())) await page.locator(`a[onclick*="showHideSpecInst('siAutoComplete_${this.card}')"]`).click();
      await box.waitFor({ state: 'visible', timeout: 15000 });
      await box.click();
      await box.fill(text);
      return this.ajax('rx/WriteScript', (r) => new URL(r.url()).searchParams.get('parameterValue') === 'updateSpecialInstruction',
        () => page.locator(`#quantity_${this.card}`).click());
    },
    /** Retype the staged custom drug's name in its own box: the key-up posts it to rx/WriteScript saveCustomName. */
    async rename(text) {
      const box = this.page.locator(`#drugName_${this.card}`);
      await box.fill(text);
      return this.ajax('rx/WriteScript', (r) => /parameterValue=saveCustomName/.test(r.request().postData() || ''), () => box.press('End'));
    },
    /** Save Only: the page posts every card's boxes to rx/WriteScript updateSaveAllDrugs. */
    async save() {
      const page = this.page;
      return this.ajax('rx/WriteScript', (r) => new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs',
        () => page.locator('#saveOnlyButton').click());
    },
    /** One page-made request and its answer, with a WAF refusal turned into a row failure naming the rules. */
    async ajax(route, match, act) {
      const mark = audit.mark();
      const [response] = await Promise.all([this.page.waitForResponse((r) => r.request().method() === 'POST' && match(r), { timeout: 30000 }), act()]);
      const status = response.status();
      const body = await response.text().catch(() => '');
      if (h.isWafPage(status, body)) {
        const { rules, note } = await audit.rulesSince(mark, pathOf(route));
        takeKnownNoise(s.recorder, `/${route}`);
        throw new RowFailure(`WAF 403 on the page's own POST /${route}: CRS rule(s): ${rules.length ? rules.join('; ') : `unknown (${note})`}`);
      }
      h.assert(status < 400, `the page's own POST /${route} answered HTTP ${status}`);
      return { status, body, headers: response.headers() };
    },
    /** Leave no staged card behind: a card that was never saved would ride along on the next row's Save. */
    async close() {
      if (this.page && !this.page.isClosed()) {
        await this.page.evaluate(() => (typeof resetStash === 'function' ? resetStash() : null)).catch(() => {});
        await this.page.waitForTimeout(500);
        await this.page.close().catch(() => {});
      }
      this.page = null;
    },
  };

  /**
   * An Rx row: `drive_` types the corpus into the box the row is about and ends at the page's own request. A row with a
   * `column` ends with Save Only and asserts the drugs row; one without it (the rename key-up posts only to the session
   * stash) asserts that the page's own request was accepted.
   */
  function rxRow(spec) {
    let mark = 0;
    return {
      key: spec.key, title: spec.title, rules: spec.rules, route: spec.route, method: spec.method, drive: spec.drive,
      fields: spec.fields,
      prepare() { s.cleanup(() => removeDrugs(s)); },
      async control() {
        await rxUi.open();
        try {
          const name = `${marker}-RX-${spec.key.replace(/^rx-/, '')}`.slice(0, 60);
          await rxUi.stage(name, '1 tab PO BID x 14 days');
          await rxUi.save();
          await rep.expectCount(s.sql, 'drugs', `demographic_no=${patient} AND customName=${q(name)}`, 1, 'the page\'s own Rx Save did not land');
        } finally { await rxUi.close(); }
      },
      watermark: () => { mark = highWater(s, 'drugs', 'drugid')(); },
      async send(values) {
        await rxUi.open();
        try {
          const answer = await spec.drive_(values, rxUi);
          // The page's own response, header and all: the front-door assertion reads the real Server header.
          return { status: () => answer.status, text: async () => answer.body, headers: () => answer.headers };
        } finally { await rxUi.close(); }
      },
      async landed(values) {
        if (!spec.column) return null;
        const wanted = spec.stored(values);
        const deadline = Date.now() + POLL_MS;
        let seen = [];
        do {
          seen = s.sql.rows(`SELECT HEX(${ident(spec.column)}) FROM drugs WHERE demographic_no=${patient} AND drugid > ${mark} ORDER BY drugid DESC LIMIT 20`).map(([value]) => decode(value));
          if (seen.some((stored) => spec.match(stored, wanted))) return null;
          await wait(200);
        } while (Date.now() < deadline);
        return seen.length ? `drugs.${spec.column} differs from what was typed` : 'no drug was written for the owned patient';
      },
    };
  }

  all['rx-special-instruction'] = () => rxRow({
    key: 'rx-special-instruction', ...FACTS['rx-special-instruction'],
    drive: 'typed: real UI (Rx ▸ custom drug ▸ special instruction typed and left, Save Only)',
    // The special instruction is a single-line box; Save Only keeps it, trimmed, in drugs.special_instruction.
    fields: [{ name: 'specialInstruction', column: 'special_instruction', joiner: ' ' }], column: 'special_instruction',
    stored: (values) => values.specialInstruction.trim(), match: (stored, wanted) => stored === wanted,
    async drive_(values, ui) {
      await ui.stage(`${marker}-RX-SI`, '1 tab PO BID x 14 days');
      await ui.special(values.specialInstruction);
      return ui.save();
    },
  });

  all['rx-custom-drug-name'] = () => rxRow({
    key: 'rx-custom-drug-name', ...FACTS['rx-custom-drug-name'],
    drive: 'typed: real UI (Rx ▸ custom drug staged from the search box, then renamed in its own box; the key-up post is the row)',
    // A single-line box over drugs.customName, which is 60 characters wide. The rename is posted to the session stash
    // only (saveCustomName); what Save Only then stores is the next row's business, so this one stops at the key-up.
    fields: [{ name: 'name', limit: 60, joiner: ' ' }],
    async drive_(values, ui) {
      await ui.stage(values.name, '1 tab PO BID x 14 days');
      return ui.rename(values.name);
    },
  });

  all['rx-custom-drug-save'] = () => rxRow({
    key: 'rx-custom-drug-save', ...FACTS['rx-custom-drug-save'],
    drive: 'typed: real UI (Rx ▸ custom drug named from the search box with the corpus, Save Only)',
    // Save Only posts the card's drugName_<n> box and the action stores it as drugs.customName, 60 characters wide.
    fields: [{ name: 'name', column: 'customName', limit: 60, joiner: ' ' }], column: 'customName',
    stored: (values) => values.name.trim(), match: (stored, wanted) => stored === wanted,
    async drive_(values, ui) {
      await ui.stage(values.name, '1 tab PO BID x 14 days');
      return ui.save();
    },
  });

  all['rx-update-script'] = () => rxRow({
    key: 'rx-update-script', ...FACTS['rx-update-script'],
    drive: 'typed: real UI (Rx ▸ custom drug ▸ sig typed and left, Save Only)',
    // The sig is a single-line box; Save Only stores it inside drugs.special beside the drug name and quantity.
    fields: [{ name: 'instruction', column: 'special', joiner: ' ' }], column: 'special',
    stored: (values) => values.instruction, match: (stored, wanted) => stored.includes(wanted),
    async drive_(values, ui) {
      await ui.stage(`${marker}-RX-SG`, null);
      await ui.sig(values.instruction);
      return ui.save();
    },
  });

  all['allergy-add'] = () => familyRow(s, ctx, {
    key: 'allergy-add', ...FACTS['allergy-add'],
    drive: 'replay: real UI (Rx ▸ Allergies ▸ Custom Allergy), then the captured POST',
    family: fam.allergyAdd(s), table: 'allergies', idColumn: 'allergyid', scope: owned,
    fields: [{ name: 'reactionDescription', column: 'reaction' }],
    cleanup: () => {
      s.sql.execute(`DELETE FROM allergies WHERE ${owned}`);
      expectGone(s, 'Owned allergies', `SELECT COUNT(*) FROM allergies WHERE ${owned}`);
    },
  });

  all['measurement-data'] = () => {
    // The Rourke form's measurement dialog saves one measurement, with the instruction that goes with it.
    let mark = 0;
    const { route } = FACTS['measurement-data'];
    const fields = [{ name: 'instruction', column: 'measuringInstruction', limit: 255 }];
    return replayRow(s, ctx, {
      key: 'measurement-data', ...FACTS['measurement-data'],
      drive: 'replay: real UI (E-Chart ▸ Forms ▸ Rourke2017 ▸ weight ▸ Save), then the captured POST',
      fields,
      prepare() { s.cleanup(() => removeMeasurements(s)); },
      async control() {
        let form;
        try {
          const chart = await s.chart();
          await chart.locator('#menuTitle1 a').hover();
          form = await s.popup(chart, chart.getByRole('link', { name: 'Rourke2017', exact: true }), 'rourke');
          await form.locator('#frmP1').waitFor();
          await form.locator(`a[onclick*="displayDemographicMeasurements('p1_wt1w'"]`).first().click();
          await form.locator('#currentMeasurementValue').fill('3.75');
          await form.locator('#currentMeasurementObservationDate').fill('2026-01-08');
          const captured = await captureRequest(form, (url) => url.pathname.endsWith(`/${route}`) && url.searchParams.get('action') === 'saveMeasurement',
            () => form.locator('.meas-btn-save').click(), { timeout: 10000 });
          await rep.expectCount(s.sql, 'measurements', `demographicNo=${patient} AND type='WT' AND dataField='3.75'`, 1, 'the dialog\'s own measurement did not land');
          return captured;
        } finally { await closeAll([form]); }
      },
      watermark: () => { mark = highWater(s, 'measurements', 'id')(); },
      landing: tableLanding(s, { table: 'measurements', idColumn: 'id', scope: `demographicNo=${patient} AND type='WT'`, fields, mark: () => mark }),
    });
  };

  all['measurement-comment'] = () => {
    const family = fam.measurementSave(s);
    return familyRow(s, ctx, {
      key: 'measurement-comment', ...FACTS['measurement-comment'],
      drive: 'replay: real UI (E-Chart ▸ Measurements ▸ Vitals ▸ Submit), then the captured POST',
      family, table: 'measurements', idColumn: 'id', scope: `demographicNo=${patient} AND type='BP'`,
      // The comment box is named for its row (comments-<n>), the one family the AFTER-CRS file exempts.
      fields: () => [{ name: family.commentsField, column: 'comments', limit: 255 }],
      cleanup: () => removeMeasurements(s),
    });
  };

  all['prevention-add'] = () => {
    // Prevention comments and the reason are stored as key/value rows (preventionsExt), one per key.
    const family = fam.preventionAdd(s);
    let mark = 0;
    const fields = [{ name: 'comments', column: 'val' }, { name: 'reason', column: 'val' }];
    return familyRow(s, ctx, {
      key: 'prevention-add', ...FACTS['prevention-add'],
      drive: 'replay: real UI (E-Chart ▸ Preventions ▸ Fluzone ▸ Save), then the captured POST with the Comments box and the reason set',
      family, table: 'preventions', idColumn: 'id', fields,
      cleanup: () => {
        s.sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
          DELETE FROM preventions WHERE demographic_no=${patient}`);
        expectGone(s, 'Owned preventions', `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`);
      },
      watermark: () => { mark = highWater(s, 'preventions', 'id')(); },
      async landing(values) {
        const deadline = Date.now() + POLL_MS;
        let seen = [];
        do {
          const stored = s.sql.rows(`SELECT x.prevention_id, x.keyval, HEX(x.val) FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
            WHERE p.demographic_no=${patient} AND p.id > ${mark} AND x.keyval IN ('comments','reason')`);
          const byPrevention = new Map();
          for (const [id, key, value] of stored) byPrevention.set(id, { ...byPrevention.get(id), [key]: value });
          seen = [...byPrevention.values()];
          if (seen.some((entry) => entry.comments === hex(values.comments) && entry.reason === hex(values.reason))) return null;
          await wait(200);
        } while (Date.now() < deadline);
        return seen.length ? 'preventionsExt holds a prevention whose comments or reason differ from what was sent' : 'no prevention was written for the owned patient';
      },
    });
  };

  all['manage-document'] = () => {
    // The stored-document viewer's Save rewrites the document's description (documentUpdateAjax).
    let docs = null;
    const { route } = FACTS['manage-document'];
    // The description is a single-line <input> over a 255-character column.
    const fields = [{ name: 'documentDescription', column: 'docdesc', limit: 255, joiner: ' ' }];
    return replayRow(s, ctx, {
      key: 'manage-document', ...FACTS['manage-document'],
      drive: 'replay: real UI (E-Chart ▸ Documents ▸ viewer ▸ description ▸ Save), then the captured POST',
      fields,
      // The chart lists its Documents when it is opened, and an earlier row may already have opened it, so the
      // document is seeded before the first row runs (a reload would also abort the chart's unload ping).
      early() {
        const store = pdfs.directory('DOCUMENT_DIR', 'DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR');
        docs = pdfs.ownedPdfDocuments(store, marker, [{ key: 'doc' }]);
        // Registered before the first file or row is written; a description rewritten by the save is
        // still found through the captured id.
        s.cleanup(() => {
          pdfs.removeOwnedPdfDocuments({ sql: s.sql, marker, patient, files: docs.map((doc) => doc.file), docs });
          pdfs.assertOwnedPdfDocumentsRemoved({ sql: s.sql, marker, patient, files: docs.map((doc) => doc.file), docs });
        });
        pdfs.seedOwnedPdfDocuments({ sql: s.sql, store, patient, provider: s.provider, docs });
      },
      async control() {
        let viewer;
        try {
          const chart = await s.chart();
          viewer = await s.popup(chart, chart.locator('#leftNavBar a, #rightNavBar a').filter({ hasText: docs[0].label }).first(), 'document-viewer');
          const input = viewer.locator(`#docDesc_${docs[0].id}`);
          await input.waitFor({ state: 'visible', timeout: 30000 });
          await input.fill(`${marker}-MD-UI`);
          const captured = await fam.captureInContext(s.context, (url) => url.pathname.endsWith(`/${route}`), () => viewer.locator(`#save${docs[0].id}`).click());
          h.assert(captured.status > 0 && captured.status < 400, `the viewer's own Save answered HTTP ${captured.status}`);
          await rep.expectCount(s.sql, 'document', `document_no=${docs[0].id} AND docdesc=${q(`${marker}-MD-UI`)}`, 1, 'the viewer\'s own description did not land');
          return captured;
        } finally { await closeAll([viewer]); }
      },
      landing: tableLanding(s, { table: 'document', idColumn: 'document_no', scope: () => `document_no=${docs[0].id}`, fields }),
    });
  };

  all['lab-status'] = () => {
    // Acknowledging a lab with a comment: the review comment is a column of the provider's routing row.
    let lab = null;
    const { route } = FACTS['lab-status'];
    // The comment is a prompt() answer: one line, and the routing column holds 255 characters.
    const fields = [{ name: 'comment', column: 'comment', limit: 255, joiner: ' ' }];
    const scope = () => `provider_no=${q(s.provider)} AND lab_type='HL7' AND lab_no=${lab}`;
    return replayRow(s, ctx, {
      key: 'lab-status', ...FACTS['lab-status'],
      drive: 'replay: real UI (Inbox ▸ lab ▸ Acknowledge ▸ comment prompt), then the captured POST',
      fields,
      prepare() {
        lab = s.sql.rows(labCandidates())[0][0];
        h.assert(/^[1-9]\d*$/.test(lab), 'the lab fixture id is invalid');
        const routing = createLabRoutingFixture((query) => s.sql.execute(query), (query) => s.sql.rows(query), s.provider, [lab]);
        s.cleanup(() => routing.cleanup());
        routing.prepare();
      },
      async control() {
        let nav;
        let display;
        try {
          nav = await s.context.newPage();
          await h.gotoApp(nav, baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1&scheduleNav=1');
          await nav.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
          await clickAndAwaitReload(nav, nav.locator('a#inboxLink').first(), { timeout: 45000, label: 'the Inbox link' });
          const row = nav.locator(`tr[data-segment-id="${lab}"][data-lab-type="HL7"]`).first();
          await row.waitFor({ state: 'attached', timeout: 30000 });
          display = await s.popup(nav, row.locator('a[onclick*="reportWindow"]').first(), 'lab-display');
          await display.waitForLoadState('domcontentloaded', { timeout: 45000 });
          await display.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
          const control = display.locator('input[onclick*="ackLab"], button[onclick*="ackLab"]').first();
          h.assert(await control.count() > 0, 'the lab display rendered no Acknowledge control');
          let captured;
          await h.withExpectedDialogs(display, async () => {
            captured = await fam.captureInContext(s.context, (url) => url.pathname.endsWith(`/${route}`), () => control.click());
          }, { accept: true, promptText: `${marker}-LS-UI` });
          h.assert(captured.status > 0 && captured.status < 400, `the page's own acknowledge answered HTTP ${captured.status}`);
          await rep.expectCount(s.sql, 'providerLabRouting', `${scope()} AND comment=${q(`${marker}-LS-UI`)}`, 1, 'the page\'s own comment did not land');
          return captured;
        } finally { await closeAll([display, nav]); }
      },
      landing: tableLanding(s, { table: 'providerLabRouting', idColumn: 'id', scope, fields }),
    });
  };

  all['messenger-send'] = () => familyRow(s, ctx, {
    key: 'messenger-send', ...FACTS['messenger-send'],
    drive: 'replay: real UI (Inbox ▸ new message ▸ Send Message), then the captured POST',
    family: fam.messengerSend(s), table: 'messagetbl', idColumn: 'messageid', scope: `sentbyNo=${q(s.provider)}`,
    // subject is a 128-character <input>; the message body is the editor's text.
    fields: [{ name: 'subject', column: 'thesubject', limit: 128, joiner: ' ' }, { name: 'message', column: 'themessage' }],
    cleanup: () => removeMessages(s, ctx.messageMark),
  });

  all['appointment-add'] = () => familyRow(s, ctx, {
    key: 'appointment-add', ...FACTS['appointment-add'],
    drive: 'replay: real UI (day sheet slot ▸ booking form ▸ Add Appointment), then the captured POST',
    family: fam.appointmentAdd(s), table: 'appointment', idColumn: 'appointment_no', scope: owned,
    // reason is a single-line <input> of 80 characters, notes a <textarea> of 255.
    fields: [{ name: 'reason', column: 'reason', limit: 80, joiner: ' ' }, { name: 'notes', column: 'notes', limit: 255 }],
    cleanup: () => removeAppointments(s),
  });

  all['appointment-update'] = () => {
    const row = familyRow(s, ctx, {
      key: 'appointment-update', ...FACTS['appointment-update'],
      drive: 'replay: real UI (day sheet ▸ appointment ▸ Update Appt), then the captured POST',
      family: fam.appointmentUpdate(s), table: 'appointment', idColumn: 'appointment_no', inPlace: true,
      // The update rewrites one appointment in place, so the row is found by its number.
      scope: () => `appointment_no=${row.captured.body.get('appointment_no')} AND ${owned}`,
      fields: [{ name: 'reason', column: 'reason', limit: 80, joiner: ' ' }, { name: 'notes', column: 'notes', limit: 255 }],
      cleanup: () => removeAppointments(s),
    });
    return row;
  };

  all['chart-note'] = () => familyRow(s, ctx, {
    key: 'chart-note', ...FACTS['chart-note'],
    drive: 'replay: real UI (E-Chart ▸ note box ▸ Save), then the captured POST',
    // A second save from the same chart window edits the note the first one made, in place.
    family: fam.chartNoteSave(s), table: 'casemgmt_note', idColumn: 'note_id', scope: owned, inPlace: true,
    fields: [{ name: 'caseNote_note', column: 'note' }],
    cleanup: () => removeNotes(s),
  });

  all['eform-letter'] = () => {
    // The Rich Text Letter is the one eForm field the packaged WAF knows is prose (Letter, rule 1045): the
    // editor's HTML, entity-escaped by the form's own saveRTL() before it is posted. The corpus is TYPED
    // into the real editor and submitted from the toolbar, so the posted bytes are the page's own.
    const { route } = FACTS['eform-letter'];
    const fields = [{ name: 'Letter', column: 'var_value' }];
    let fid = null;
    let sent = null;
    let lastAnswer = null;
    const letters = () => s.sql.rows(`SELECT v.fdid, HEX(v.var_value), CHAR_LENGTH(v.var_value) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid
      WHERE d.demographic_no=${patient} AND d.fid=${fid} AND v.var_name='Letter' ORDER BY v.fdid DESC`);

    /** Open a new letter, type `lines` into the editor, Submit from the toolbar; returns the request the page sent. */
    async function submitLetter(lines, subject) {
      const page = await s.context.newPage();
      try {
        await page.addInitScript(() => { window.close = () => { window.__closeIntercepted = true; }; });
        await h.gotoApp(page, baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${patient}&appointment=0`);
        await h.assertNotErrorPage(page, 'the Rich Text Letter');
        await page.locator('#remoteSubmitButton').waitFor({ state: 'attached', timeout: 30000 });
        await page.locator('iframe#edit').waitFor({ state: 'attached', timeout: 30000 });
        await page.waitForFunction(() => {
          const frame = document.getElementById('edit');
          try { return frame && frame.contentWindow.document.readyState === 'complete' && frame.contentWindow.document.body != null; } catch (e) { return false; }
        }, null, { timeout: 30000 });
        await page.waitForFunction(() => typeof window.measurementHistoryStillLoading !== 'function' || !window.measurementHistoryStillLoading(),
          null, { timeout: 30000 });
        // A stock install has no stamps.js (clinic signature stamps are never auto-deployed); that 404 is
        // documented in docs/ui-tests/deb-install-validation.md and is not what this check is about.
        takeKnownNoise(s.recorder, 'imagefile=stamps.js');
        const body = page.frameLocator('#edit').locator('body');
        await body.click();
        for (const [n, line] of lines.entries()) {
          if (n > 0) await page.keyboard.press('Enter');
          await body.pressSequentially(line, { delay: 0 });
        }
        await page.locator('#remote_eform_subject').fill(subject);
        const captured = await captureOnSend(s.context, (url) => url.pathname.endsWith(`/${route}`), () => page.locator('#remoteSubmitButton').click());
        // Read the answer before the page closes: its body is gone afterwards.
        const response = await captured.request.response().catch(() => null);
        const text = response ? await response.text().catch(() => '') : '';
        lastAnswer = { status: () => (response ? response.status() : 0), text: async () => text, headers: () => (response ? response.headers() : {}) };
        takeKnownNoise(s.recorder, 'imagefile=stamps.js');
        // The browser logged the WAF's page as a failed document load; that is this row's failure, already counted.
        if (response && h.isWafPage(response.status(), text)) takeKnownNoise(s.recorder, `/${route}`);
        return captured;
      } finally { await closeAll([page]); }
    }

    return {
      key: 'eform-letter', ...FACTS['eform-letter'],
      drive: 'typed: real UI throughout (E-Chart eForm ▸ Rich Text Letter, the corpus typed into the editor, toolbar Submit)',
      fields,
      get captured() { return sent; },
      prepare() {
        fid = s.sql.value(`SELECT MIN(fid) FROM eform WHERE form_name=${q(process.env.RTL_FORM_NAME || 'Rich Text Letter')} AND status=1`);
        h.assert(/^[1-9]\d*$/.test(fid), 'the eForm library has no active Rich Text Letter');
        s.cleanup(() => {
          const ids = s.sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND fid=${fid}`).map(([id]) => id);
          ids.forEach((id) => h.assert(/^[1-9]\d*$/.test(id), 'An owned eForm instance id is invalid'));
          for (const id of ids) s.sql.execute(`DELETE FROM eform_values WHERE fdid=${id}; DELETE FROM eform_data WHERE fdid=${id} AND demographic_no=${patient}`);
          expectGone(s, 'Owned eForm instances', `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient} AND fid=${fid}`);
        });
      },
      async control() {
        const captured = await submitLetter(['Plain control letter, one line.'], `${marker}-EL-UI`);
        h.assert(captured.status > 0 && captured.status < 400, `the letter's own Submit answered HTTP ${captured.status}`);
        await rep.expectCount(s.sql, 'eform_data', `demographic_no=${patient} AND fid=${fid} AND subject=${q(`${marker}-EL-UI`)}`, 1, 'the letter\'s own save did not land');
        return captured;
      },
      watermark() {},
      async send(values) {
        sent = await submitLetter(values.Letter.split('\n'), `${marker}-EL-CORPUS`);
        return lastAnswer;
      },
      async landed(values) {
        const postedLetter = sent.body.get('Letter');
        if (!postedLetter) return 'the page posted no Letter field';
        // What the page posted is the editor's HTML with & " < > ' escaped to entities by saveRTL(); undo that,
        // drop the editor's line tags, then undo the editor's own text escaping (the second pair of passes), to
        // compare with what was typed, so the proof is "the typed corpus survived", not "something was stored".
        // Each pass decodes in one sweep, so it never re-reads an '&' it has just produced; the second
        // pass is the editor's own layer of escaping, not a re-decode of the first pass's output.
        const decode = (text, entities) => text.replace(/&(?:#39|quot|lt|gt|amp|nbsp);/g, (entity) => entities[entity] ?? entity);
        const plain = decode(decode(postedLetter, { '&#39;': "'", '&quot;': '"', '&lt;': '<', '&gt;': '>', '&amp;': '&' })
          .replace(/<\/?(?:div|p|br)\s*\/?>/gi, '\n'), { '&nbsp;': ' ', '&lt;': '<', '&gt;': '>', '&amp;': '&' });
        const lost = values.Letter.split('\n').filter((line) => !plain.includes(line));
        if (lost.length) return `the editor did not keep ${lost.length} typed line(s), so the posted Letter is not the corpus`;
        const deadline = Date.now() + POLL_MS;
        do {
          if (letters().some(([, stored]) => stored === hex(postedLetter))) return null;
          await wait(200);
        } while (Date.now() < deadline);
        const newest = letters()[0];
        return newest ? `eform_values.var_value for Letter differs from what the page posted (stored ${newest[2]} characters, posted ${Array.from(postedLetter).length})`
          : 'no eForm instance with a Letter value was written for the owned patient';
      },
    };
  };

  /** An owned open Ontario bill (a header and one item) for the correction and display rows. */
  const ownedBill = () => {
    const { code, date, fee } = billFee(s.sql);
    return billing.seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code, fee, date });
  };

  all['billing-on-save'] = () => familyRow(s, ctx, {
    key: 'billing-on-save', ...FACTS['billing-on-save'],
    drive: 'replay: real UI (day sheet ▸ Bill ▸ review ▸ Save), then the captured POST against a fresh owned appointment',
    family: fam.billingOnSave(s), table: 'billing_on_cheader1', idColumn: 'id', scope: owned,
    fields: [{ name: 'comment', column: 'comment1' }],
    cleanup: () => removeBills(s),
  });

  all['billing-on-correction'] = () => {
    // The correction popup's "Billing Notes" box rewrites the bill's comment in place.
    let bill = null;
    const { route } = FACTS['billing-on-correction'];
    const fields = [{ name: 'comment', column: 'comment1' }];
    return replayRow(s, ctx, {
      key: 'billing-on-correction', ...FACTS['billing-on-correction'],
      drive: 'replay: real UI (Master Record ▸ Billing History ▸ Edit ▸ Billing Notes ▸ Save), then the captured POST',
      fields,
      prepare() { bill = ownedBill(); },
      async control() {
        let history;
        let popup;
        try {
          history = await billing.openHistory(s);
          popup = await billing.openCorrection(s, history, bill.headerId);
          await popup.locator('textarea[name="comment"]').fill(`${marker}-BC-UI`);
          const captured = await captureOnSend(s.context, (url) => url.pathname.endsWith(`/${route}`),
            () => popup.locator('form[action$="/billing/CA/ON/UpdateBillingONCorrection"] input[type="submit"][value="Save"]').click());
          h.assert(captured.status > 0 && captured.status < 400, `the correction page's own Save answered HTTP ${captured.status}`);
          await rep.expectCount(s.sql, 'billing_on_cheader1', `id=${bill.headerId} AND comment1=${q(`${marker}-BC-UI`)}`, 1, 'the correction page\'s own note did not land');
          return captured;
        } finally { await closeAll([popup, history]); }
      },
      landing: tableLanding(s, { table: 'billing_on_cheader1', idColumn: 'id', scope: () => `id=${bill.headerId}`, fields }),
    });
  };

  all['billing-on-display'] = () => {
    // The invoice display page posts only its invoice number: its "Billing Notes" box sits outside the
    // form, and nothing stores it. The row proves the front door lets the POST through with the comment
    // on it and that the route writes nothing, which is all there is to assert for a display route.
    let bill = null;
    const { route } = FACTS['billing-on-display'];
    return replayRow(s, ctx, {
      key: 'billing-on-display', ...FACTS['billing-on-display'],
      drive: 'replay: real UI (Billing History ▸ invoice number ▸ Enter in the invoice box), then the captured POST with the comment added',
      fields: [{ name: 'comment', column: 'comment1' }],
      prepare() { bill = ownedBill(); },
      async control() {
        let history;
        let popup;
        try {
          history = await billing.openHistory(s);
          const link = billing.historyRow(history, bill.headerId).locator(`a[onclick*="ViewBillingONDisplay?billing_no=${bill.headerId}'"]`).first();
          popup = await s.popup(history, link, 'billing-display');
          // The page opens its form inside a <table>, so the browser leaves the form empty and the box owns it by association only.
          const box = popup.locator('input[name="billing_no"]').first();
          await box.waitFor({ state: 'visible', timeout: 20000 });
          const captured = await captureOnSend(s.context, (url) => url.pathname.endsWith(`/${route}`), () => box.press('Enter'));
          h.assert(captured.status > 0 && captured.status < 400, `the display page's own POST answered HTTP ${captured.status}`);
          return captured;
        } finally { await closeAll([popup, history]); }
      },
      async landing(values, answer) {
        if (answer.status !== 200 || !answer.body.includes(`value="${bill.headerId}"`)) return `the display page did not render invoice ${bill.headerId} (HTTP ${answer.status})`;
        const stored = s.sql.value(`SELECT HEX(comment1) FROM billing_on_cheader1 WHERE id=${bill.headerId}`);
        return stored === hex(marker) ? null : 'the display route changed the stored billing note, and it should only show it';
      },
    });
  };

  return all;
}

// ---------------------------------------------------------------------------------------------
// The sweep

async function workflow(s) {
  const assertFrontDoor = watchFrontDoor(s);
  const audit = waf.auditLog();
  const ctx = {
    audit,
    messageMark: {
      message: Number(s.sql.value('SELECT IFNULL(MAX(messageid), 0) FROM messagetbl')),
      list: Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl')),
      demoMap: Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap')),
    },
    token: (() => { let token; return async () => token || (token = await rep.sessionToken(s.schedule, s.config.baseUrl)); })(),
  };
  const available = rows(s, ctx);
  const built = selectedKeys().map((key) => available[key]());
  const failures = [];
  const clipped = [];
  let ticklerRow = null;
  for (const row of built) if (row.early) row.early();
  console.log(`  rows: ${built.map((row) => row.key).join(', ')}`);
  console.log(audit.available
    ? `  audit log: ${audit.file} (blocked requests are reported with their CRS rule ids)`
    : `  audit log: ${audit.file} cannot be read here (${audit.unreadable}); a WAF block will be reported without its rule ids`);

  /**
   * Run one step, keep going when it fails so every route reports, and remember the failure. What the
   * browser logged while a failed step ran belongs to that failure, which is already recorded, so it is
   * dropped; left in the strict recorder it would fail every later row for a problem that is not theirs.
   * `judged` is the corpus step: only a RowFailure there is the row's assertion, and anything else (the
   * page would not open, a locator timed out) is the drive failing, which must not read as a known failure.
   */
  async function attempt(label, body, { judged = false } = {}) {
    const keep = Object.fromEntries(['pageErrors', 'consoleIssues', 'requestFailures', 'badResponses', 'unexpectedDialogs']
      .map((key) => [key, s.recorder[key].length]));
    try { await s.step(label, body); return true; } catch (error) {
      const own = !judged || error instanceof RowFailure;
      failures.push({ label: own ? label : `${label} -- the drive failed before the corpus could be judged`, error });
      console.error(`  FAIL ${label}: ${error.message}`);
      for (const [key, length] of Object.entries(keep)) {
        for (const entry of s.recorder[key].splice(length)) {
          console.error(`  (dropped from the strict recorder with that failure: ${key} ${JSON.stringify(entry).slice(0, 300)})`);
        }
      }
      return false;
    }
  }

  /**
   * Cleanups a row registers while it runs, so they can run the moment the row is done as well as at the end:
   * what one row saved must not be on the page the next row drives (a tickler whose text the list cannot show
   * would stop the next row from opening the list). Each cleanup is idempotent and asserts its rows are gone.
   */
  async function withRowCleanup(row, body) {
    const registered = [];
    const original = s.cleanup;
    s.cleanup = (fn) => { registered.push(fn); original.call(s, fn); };
    try { await body(); } finally {
      s.cleanup = original;
      for (const fn of registered.reverse()) {
        try { await fn(); } catch (error) {
          failures.push({ label: `${row.title}: removing the rows the row wrote`, error });
          console.error(`  FAIL ${row.title}: cleanup: ${error.message}`);
        }
      }
    }
  }

  for (const row of built) await withRowCleanup(row, async () => {
    let controlled = false;
    await attempt(controlStepLabel(row.key), async () => {
      await row.prepare();
      await row.control();
      controlled = true;
    });
    if (!controlled) return;
    if (row.key === 'tickler-add') ticklerRow = row;
    await attempt(corpusStepLabel(row.key), async () => {
      const { requests, clipped: cuts } = waf.corpusRequests(row.fields);
      for (const cut of cuts) clipped.push(`${row.key} ${cut.field}: "${cut.label}" clipped from ${cut.from} to ${cut.to} characters (column limit)`);
      row.watermark();
      const argNames = row.fields.map((field) => field.name).join(', ');
      for (const [n, values] of requests.entries()) {
        const where = `${s.config.baseUrl.pathname.replace(/\/$/, '')}/${row.route}`;
        const position = `request ${n + 1} of ${requests.length}`;
        const mark = audit.mark();
        let response;
        try {
          response = await row.send(values);
        } catch (error) {
          if (error instanceof RowFailure) throw error;
          // nginx cannot turn a response it has already started into a 403, so a CRS OUTBOUND rule
          // (response-body inspection) drops the connection instead: the client sees no answer at all.
          const { rules } = await audit.rulesSince(mark, where, { method: row.method || 'POST' });
          if (!rules.length) throw error;
          // Only a row that saves can say whether the text was saved; the others stored nothing for this request.
          const fate = outcomeOf(row.key) === 'stored'
            ? `the text ${await row.landed(values, { status: 0, body: '' }).then((problem) => problem === null, () => false) ? 'WAS saved' : 'was NOT saved'} but the client got no answer`
            : 'the client got no answer';
          throw new RowFailure(`WAF dropped the RESPONSE of ${row.method || 'POST'} /${row.route} (${position}; ${fate}): `
            + `the response echoes the clinician's prose from ${argNames} and CRS rule(s) ${rules.join('; ')} blocked it `
            + `(${row.response ? exclusionOf(row) : `${exclusionOf(row)} covers the request only`})`);
        }
        const status = response.status();
        const body = await response.text().catch(() => '');
        if (h.isWafPage(status, body)) {
          const { rules, note } = await audit.rulesSince(mark, where, { method: row.method || 'POST' });
          throw new RowFailure(`WAF 403 on ${row.method || 'POST'} /${row.route} (${position}): ${exclusionOf(row)} did not cover the clinician's `
            + `prose in ${argNames}; CRS rule(s): ${rules.length ? rules.join('; ') : `unknown (${note})`}`);
        }
        if (status >= 400) throw new RowFailure(`${row.method || 'POST'} /${row.route} answered HTTP ${status} (${position}), not a success: the front door let the prose through and the application refused it`);
        if (s.config.expectFrontDoor && !/nginx/i.test(response.headers().server || '')) {
          throw new Error(`the replayed ${row.method || 'POST'} to /${row.route} did not go through the nginx front door`);
        }
        const problem = await row.landed(values, { status, body });
        if (problem !== null) throw new RowFailure(`${row.method || 'POST'} /${row.route} answered HTTP ${status} (${position}) but ${OUTCOME_TEXT[outcomeOf(row.key)].unmet}: ${problem}`);
      }
      const outcome = OUTCOME_TEXT[outcomeOf(row.key)];
      console.log(`  ${outcome.done} ${row.key} (${row.rules.length ? row.rules.join('/') : row.afterCrs ? 'after-CRS' : 'no exclusion'}): ${requests.length} request(s) with ${argNames} ${outcome.detail} [${row.drive}]`);
    }, { judged: true });
  });

  // The proof that the rows above measured the WAF: the same corpus in an argument no exclusion names is
  // refused, with the CRS rule ids, through the same replay path. Without it a pass could mean the WAF was
  // not looking.
  if (ticklerRow && ticklerRow.captured) {
    await attempt('the corpus is refused outside the excluded arguments (the replay path is inspected)', async () => {
      if (!s.config.expectFrontDoor) {
        console.log('  NOTE waf-clinical-text-corpus: WAF refusal outside the exclusions not asserted (EXPECT_FRONT_DOOR is not true)');
        return;
      }
      const mark = audit.mark();
      const replay = rep.buildReplay(ticklerRow.captured, {
        origin: s.config.baseUrl.origin, token: await ctx.token(), overrides: { parentAjaxId: waf.corpusTexts({}).texts[0] },
      });
      const response = await rep.sendReplay(s.context, replay);
      const body = await response.text().catch(() => '');
      h.assert(h.isWafPage(response.status(), body),
        `the corpus in an argument no exclusion names answered HTTP ${response.status()}, not the WAF's 403: this run did not measure the WAF`);
      const { rules } = await audit.rulesSince(mark, `${s.config.baseUrl.pathname.replace(/\/$/, '')}/${ticklerRow.route}`);
      console.log(`  CONTROL the corpus outside the exclusions is refused by the WAF: ${rules.length ? rules.join('; ') : 'no rule ids (audit log not readable)'}`);
    });
  }

  for (const note of clipped) console.log(`  NOTE ${note}`);
  assertFrontDoor();
  if (failures.length) {
    // One failed row keeps its own step label, so a manifest expectedFailure can pin it; several failed
    // rows are a different problem and must not hide behind one pin.
    const message = `${failures.length} route row(s) failed: ${failures.map((failure) => `[${failure.label}] ${failure.error.message}`).join(' || ')}`;
    throw h.markFailedStep(new Error(message), failures.length === 1 ? failures[0].label : `${failures.length} route rows failed`);
  }
}

/** Before any fixture exists: a piece of the install a selected row needs that is missing SKIPs the whole check. */
async function preflight({ sql }) {
  for (const key of selectedKeys()) if (PREREQUISITES[key]) PREREQUISITES[key]({ sql });
}

if (require.main === module) runWorkflow('waf-clinical-text-corpus', workflow, { openPatient: true, preflight });
module.exports = { PREREQUISITES, workflow };
