/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {createHash, randomBytes} = require('node:crypto');
const h = require('./playwright-harness');
const {fixturePdf} = require('../incoming-pdf-extraction-playwright-checks');
const {execFileSync} = require('node:child_process');

function inspect(file) {
  const pdf = fs.readFileSync(file);
  const footer = pdf.toString('latin1').match(/startxref\s+(\d+)\s+%%EOF\s*$/);
  h.assert(footer, 'Stored PDF is missing its completed trailer');
  const offset = Number(footer[1]);
  h.assert(Number.isSafeInteger(offset) && offset > 0 && offset < pdf.length, 'Stored PDF has an invalid cross-reference offset');
  const xref = pdf.subarray(offset, offset + 4096).toString('latin1');
  h.assert(xref.startsWith('xref') || (/^\d+\s+\d+\s+obj/.test(xref) && /\/Type\s*\/XRef\b/.test(xref)),
    'Stored PDF has no final cross-reference table or stream');
  const info = execFileSync('pdfinfo', [file], {encoding: 'utf8'});
  const pages = Number(info.match(/^Pages:\s+(\d+)/m)?.[1]);
  h.assert(pages > 0, 'Stored PDF has no readable pages');
  return {pages, text: execFileSync('pdftotext', [file, '-'], {encoding: 'utf8'})};
}

function id(value) {h.assert(/^[1-9]\d*$/.test(String(value)), 'Invalid owned document identity'); return String(value);}
function quote(value) {h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(value), 'Invalid schema identifier'); return '`' + value + '`';}
function fileProof(file, store) {
  h.assert(path.dirname(file) === store && path.basename(file) === file.slice(store.length + 1), 'Document escaped its configured store');
  const stat = fs.lstatSync(file);
  h.assert(stat.isFile() && !stat.isSymbolicLink() && fs.realpathSync(file) === file, 'Document file identity is unsafe');
  return {dev: stat.dev, ino: stat.ino, mode: stat.mode, uid: stat.uid, gid: stat.gid,
    sha: createHash('sha256').update(fs.readFileSync(file)).digest('hex')};
}
function equal(actual, expected, message) {h.assert(JSON.stringify(actual) === JSON.stringify(expected), message);}

/** Only proven non-document discriminator values may relax a legacy-name match. */
function externalDocumentReferences(references, number, fields, known) {
  const merged = new Map();
  for (const [table, column, declared] of references) {
    quote(table); quote(column);
    h.assert(['0', '1'].includes(String(declared)), 'Reference provenance is unavailable');
    const key = `${table}\0${column}`, previous = merged.get(key);
    merged.set(key, {table, column, declared: String(declared) === '1' || Boolean(previous?.declared)});
  }
  const result = [];
  for (const {table, column, declared} of merged.values()) {
    if (known.has(table)) continue;
    let condition = `${quote(column)}=${id(number)}`;
    const types = {eformdocs: ['E', 'L', 'H'], consultdocs: ['E', 'L', 'F', 'H']}[table.toLowerCase()];
    if (!declared && types && column.toLowerCase() === 'document_no') {
      const discriminator = fields(table).filter(name => name.toLowerCase() === 'doctype');
      if (discriminator.length === 1) {
        const type = quote(discriminator[0]);
        // NULL, unknown, lowercase, padded, and D values remain blocking references.
        condition += ` AND (${type} IS NULL OR BINARY ${type} NOT IN (${types.map(h.sqlString).join(',')}))`;
      }
    }
    result.push({table, condition});
  }
  return result;
}

function loadRecovery(options, expected) {
  h.assert(options.browserDrainConfirmed === true, 'Explicit confirmed browser drain is required for recovery');
  const journal = options.journal;
  h.assert(typeof journal === 'string' && path.isAbsolute(journal) && path.basename(journal) === 'journal.json'
    && /^stored-document-owned-[A-Za-z0-9]+$/.test(path.basename(path.dirname(journal)))
    && path.dirname(path.dirname(journal)) === fs.realpathSync(os.tmpdir())
    && fs.realpathSync(journal) === journal, 'Recovery journal must be the original private fixture journal');
  for (const [file, directory] of [[path.dirname(journal), true], [journal, false]]) {
    const info = fs.lstatSync(file);
    h.assert(!info.isSymbolicLink() && (directory ? info.isDirectory() : info.isFile() && info.nlink === 1)
      && info.uid === process.getuid() && (info.mode & 0o777) === (directory ? 0o700 : 0o600),
    'Recovery journal ownership or permissions changed');
  }
  h.assert(/^[a-f0-9]{64}$/.test(options.expectedJournalSha256 || ''), 'Reviewed recovery journal SHA256 is required');
  const before = fs.lstatSync(journal), bytes = fs.readFileSync(journal), after = fs.lstatSync(journal);
  h.assert(before.dev === after.dev && before.ino === after.ino && before.size === after.size && before.mtimeMs === after.mtimeMs,
    'Recovery journal changed while being read');
  h.assert(createHash('sha256').update(bytes).digest('hex') === options.expectedJournalSha256,
    'Recovery journal SHA256 differs from the reviewed journal');
  const record = JSON.parse(bytes);
  for (const key of ['marker', 'patient', 'provider', 'program', 'store']) {
    h.assert(record[key] === expected[key], 'Recovery fixture identity differs from the reviewed identity');
  }
  h.assert(/^FAKE-PW[a-f0-9]{16}$/.test(record.marker) && id(record.sourceId) === id(options.expectedSourceId),
    'Recovery document identity is not the reviewed fixture');
  h.assert(record.phase === 'browser-drained' && record.initialised === true && record.closed === true
    && record.uncertain === false && record.transportUncertain === false && record.cleaned === false
    && record.acceptedSplitId === undefined && Array.isArray(record.removed) && record.removed.length === 0,
  'Recovery requires an unambiguous drained fixture with no partial cleanup or split');
  h.assert(Array.isArray(record.documents) && record.documents.length === 1
    && record.documents[0][0] === record.sourceId && record.documents[0][1].file === expected.sourceFile,
  'Recovery document/file scope changed');
  h.assert(Array.isArray(record.baseline) && record.baseline.every(row => Array.isArray(row) && row.length === 2
    && /^[1-9]\d*$/.test(row[0]) && /^[a-f0-9]{64}$/.test(row[1]) && row[0] !== record.sourceId),
  'Recovery unrelated-document baseline is incomplete');
  return record;
}

/** Exact ownership journal: uncertain operations are deliberately retained for recovery. */
function createStoredDocumentFixture(session, program, recovery, options = {}) {
  const {sql, marker, patient, provider} = session;
  id(patient); id(program);
  const configured = process.env.RX_FAX_DOCUMENT_DIR || process.env.DOCUMENT_DIR;
  h.assert(configured, 'Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const cacheConfigured = process.env.DOCUMENT_CACHE_DIR || path.join(path.dirname(store), path.basename(store) + '_cache');
  const documents = new Map(), columns = new Map();
  const filename = `${marker}-stored.pdf`, sourceFile = path.join(store, filename);
  let uncertain = false, initialised = false, closed = false, cleaned = false, transportUncertain = false, baseline, sourceId, journal, acceptedSplitId, recoveryOf;
  const removed = [], metadataReceipts = [];
  function checkpoint(phase) {
    if (!journal) return;
    const content = JSON.stringify({marker, patient, provider, program, store, sourceId, acceptedSplitId, uncertain, initialised, closed, cleaned, transportUncertain, phase,
      documents: [...documents], baseline, removed, recoveryOf, metadataReceipts}, null, 2);
    const temporary = journal + '.new';
    fs.writeFileSync(temporary, content, {mode: 0o600, flag: 'wx'});
    const descriptor = fs.openSync(temporary, 'r');
    try {fs.fsyncSync(descriptor);} finally {fs.closeSync(descriptor);}
    fs.renameSync(temporary, journal);
    const directory = fs.openSync(path.dirname(journal), 'r');
    try {fs.fsyncSync(directory);} finally {fs.closeSync(directory);}
  }
  const tableKeys = {document: ['document_no'], ctl_document: ['module_id', 'module', 'document_no'],
    patientLabRouting: ['id'], providerLabRouting: ['id'], queue_document_link: ['id']};
  function fields(table) {
    if (!columns.has(table)) {
      const result = sql.rows(`SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME=${h.sqlString(table)} ORDER BY ORDINAL_POSITION`).map(row => row[0]);
      h.assert(result.length, 'Owned document schema is missing'); result.forEach(quote); columns.set(table, result);
    }
    return columns.get(table);
  }
  function digest(table, stable = false) {
    const selected = fields(table).filter(column => !stable || !['number_of_pages', 'updatedatetime'].includes(column));
    return `SHA2(JSON_ARRAY(${selected.map(quote).join(',')}),256)`;
  }
  function where(table, number) {
    return table === 'document' || table === 'ctl_document' ? `document_no=${number}`
      : table === 'queue_document_link' ? `document_id=${number}` : `lab_no=${number} AND lab_type='DOC'`;
  }
  function rows(table, number) {
    const keys = tableKeys[table].map(quote).join(',');
    return sql.rows(`SELECT ${keys},${digest(table)} FROM ${quote(table)} WHERE ${where(table, number)} ORDER BY ${keys}`);
  }
  function snapshot(number) {
    return Object.fromEntries(Object.keys(tableKeys).map(table => [table, rows(table, number)]));
  }
  function otherDocuments() {
    return sql.rows(`SELECT document_no,${digest('document')} FROM document ORDER BY document_no`)
      .filter(row => !documents.has(row[0]));
  }
  function owner() {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}
      AND first_name='Workflow' AND provider_no=${h.sqlString(provider)}`) === '1', 'Stored document patient ownership changed');
  }
  function noForeignReferences(number) {
    const known = new Set(Object.keys(tableKeys));
    const references = sql.rows(`SELECT c.TABLE_NAME,c.COLUMN_NAME,0 AS declared_fk FROM information_schema.COLUMNS c
      JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
      WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE'
      AND LOWER(REPLACE(c.COLUMN_NAME,'_','')) IN ('documentno','documentid','docno','docid')
      UNION ALL SELECT TABLE_NAME,COLUMN_NAME,1 AS declared_fk FROM information_schema.KEY_COLUMN_USAGE
      WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='document' AND REFERENCED_COLUMN_NAME='document_no'`);
    const guards = [];
    for (const {table, condition} of externalDocumentReferences(references, number, fields, known)) {
      h.assert(sql.value(`SELECT COUNT(*) FROM ${quote(table)} WHERE ${condition}`) === '0',
        'Owned stored document acquired external references');
      guards.push(`NOT EXISTS(SELECT 1 FROM ${quote(table)} WHERE ${condition})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link WHERE table_name=5 AND table_id=${number}`) === '0',
      'Owned stored document acquired clinical note references');
    guards.push(`NOT EXISTS(SELECT 1 FROM casemgmt_note_link WHERE table_name=5 AND table_id=${number})`);
    return guards;
  }
  function verify(number) {
    const record = documents.get(id(number)); h.assert(record, 'Unowned stored document');
    equal(snapshot(number), record.snapshot, 'Owned stored document rows changed outside the expected operation');
    equal(fileProof(record.file, store), record.fileProof, 'Owned stored document file changed outside the expected operation');
  }
  function acceptFile(number, pages, expectedPages) {
    const record = documents.get(id(number));
    const pdf = inspect(record.file);
    h.assert(pdf.pages === pages, 'Committed stored PDF has an incorrect page count');
    for (const page of [1, 2, 3]) h.assert(pdf.text.includes(`${marker} page ${page}`) === expectedPages.includes(page),
      'Committed stored PDF has incorrect clinical page content');
    h.assert(sql.value(`SELECT number_of_pages FROM document WHERE document_no=${number}`) === String(pages),
      'Committed document metadata has an incorrect page count');
    const proof = fileProof(record.file, store);
    for (const key of ['mode', 'uid', 'gid']) h.assert(proof[key] === record.fileProof[key], 'Stored mutation widened file access');
    return proof;
  }
  async function cleanup() {
    h.assert(!uncertain && !transportUncertain && initialised && closed, `Stored mutation/setup or browser drain is unconfirmed; preserve fixture journal ${journal}`);
    owner(); equal(otherDocuments(), baseline, 'Other stored document rows changed during the check');
    const guards = new Map();
    for (const [number] of documents) {verify(number); guards.set(number, noForeignReferences(number));}
    // Exact snapshots prevent deletion of added routes or concurrent changed rows.
    for (const [number, record] of [...documents].reverse()) {
      const order = ['queue_document_link', 'providerLabRouting', 'patientLabRouting', 'ctl_document', 'document'];
      const gatedTables = new Set([...order, 'demographic']);
      for (const guard of guards.get(number)) {
        const reference = /FROM\s+(?:`([A-Za-z_][A-Za-z0-9_]*)`|([A-Za-z_][A-Za-z0-9_]*))/.exec(guard);
        h.assert(reference, 'Cannot verify transactional reference ownership');
        gatedTables.add(reference[1] || reference[2]);
      }
      h.assert(sql.value(`SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME IN(${[...gatedTables].map(h.sqlString).join(',')}) AND ENGINE<>'InnoDB'`) === '0',
      'Owned cleanup requires transactional tables');
      const statements = ['SET TRANSACTION ISOLATION LEVEL SERIALIZABLE', 'START TRANSACTION',
        `SELECT COUNT(*) INTO @stored_fixture_patient FROM demographic WHERE demographic_no=${patient} FOR UPDATE`];
      // Lock all exact document scopes (including empty ranges) before rechecking
      // snapshots. Keep row-count failure handling in this same SQL connection.
      for (const table of order) statements.push(`SELECT COUNT(*) INTO @stored_fixture_lock FROM ${quote(table)}
        WHERE ${where(table, number)} FOR UPDATE`);
      for (const guard of guards.get(number)) statements.push(guard.replace('NOT EXISTS(SELECT 1',
        'SELECT COUNT(*) INTO @stored_fixture_reference').replace(/\)$/, ' FOR UPDATE'));
      const gates = [...guards.get(number)];
      gates.push(`EXISTS(SELECT 1 FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}
        AND first_name='Workflow' AND provider_no=${h.sqlString(provider)})`);
      for (const table of order) {
        gates.push(`(SELECT COUNT(*) FROM ${quote(table)} WHERE ${where(table, number)})=${record.snapshot[table].length}`);
        for (const row of record.snapshot[table]) {
          const key = tableKeys[table].map((column, index) => `${quote(column)}=${h.sqlString(row[index])}`);
          gates.push(`EXISTS(SELECT 1 FROM ${quote(table)} WHERE ${key.join(' AND ')} AND ${digest(table)}=${h.sqlString(row.at(-1))})`);
        }
      }
      statements.push(`SET @stored_fixture_ok=(${gates.join(' AND ')})`);
      for (const table of order) {
        for (const row of record.snapshot[table]) {
          const key = tableKeys[table].map((column, index) => `${quote(column)}=${h.sqlString(row[index])}`);
          statements.push(`DELETE FROM ${quote(table)} WHERE @stored_fixture_ok AND ${key.join(' AND ')}
            AND ${digest(table)}=${h.sqlString(row.at(-1))}`);
          statements.push('SET @stored_fixture_ok=(@stored_fixture_ok AND ROW_COUNT()=1)');
        }
      }
      statements.push("SET @stored_fixture_finish=IF(@stored_fixture_ok,'COMMIT','ROLLBACK')",
        'PREPARE stored_fixture_finish FROM @stored_fixture_finish', 'EXECUTE stored_fixture_finish',
        'DEALLOCATE PREPARE stored_fixture_finish', 'SELECT @stored_fixture_ok');
      h.assert(sql.value(statements.join('; ')) === '1', 'Atomic stored fixture cleanup refused; all clinical routes retained');
      equal(fileProof(record.file, store), record.fileProof, 'Stored file changed before cleanup');
      fs.unlinkSync(record.file);
      removed.push(number); checkpoint('cleanup');
      if (fs.existsSync(cacheConfigured)) {
        const cache = fs.realpathSync(cacheConfigured);
        for (let page = 1; page <= 3; page++) for (const suffix of ['', '_144dpi', '_192dpi']) {
          const file = path.join(cache, `${path.basename(record.file)}_${page}${suffix}.png`);
          if (!fs.existsSync(file)) continue;
          const proof = fileProof(file, cache);
          equal(fileProof(file, cache), proof, 'Stored preview changed after browser drain'); fs.unlinkSync(file);
        }
      }
    }
    cleaned = true; checkpoint('cleaned');
    // Retain the private evidence journal, including committed/deleted identities.
  }
  if (recovery) {
    const saved = loadRecovery(recovery, {marker, patient, provider, program, store, sourceFile});
    const tables = Object.keys(tableKeys).sort();
    const record = saved.documents[0][1];
    h.assert(record.snapshot && JSON.stringify(Object.keys(record.snapshot).sort()) === JSON.stringify(tables),
      'Recovery row snapshot is incomplete');
    for (const table of tables) {
      h.assert(Array.isArray(record.snapshot[table]) && record.snapshot[table].every(row => Array.isArray(row)
        && row.length === tableKeys[table].length + 1 && row.every(value => typeof value === 'string')
        && /^[a-f0-9]{64}$/.test(row.at(-1))), 'Recovery full-row hash snapshot is invalid');
    }
    sourceId = saved.sourceId; baseline = saved.baseline;
    documents.set(sourceId, record); initialised = true; closed = true;
    recoveryOf = {journal: recovery.journal, sha256: recovery.expectedJournalSha256};
    journal = path.join(path.dirname(recovery.journal), `recovery-${randomBytes(12).toString('hex')}.json`);
    checkpoint('recovery-ready');
    return {sourceId, journal, cleanup, isCleaned: () => cleaned};
  }
  session.cleanup(async () => {
    try {await cleanup();}
    catch (error) {throw new Error(`Stored fixture recovery journal ${journal}: ${error.message}`, {cause: error});}
  }); // before the first file or database mutation
  journal = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-owned-')), 'journal.json');
  checkpoint('preparing');
  owner();
  h.assert(!fs.existsSync(sourceFile), 'Stored fixture filename already exists');
  fs.writeFileSync(sourceFile, fixturePdf(marker), {flag: 'wx', mode: 0o640});
  const ownerStat = fs.statSync(store); fs.chownSync(sourceFile, ownerStat.uid, ownerStat.gid);
  uncertain = true; checkpoint('inserting');
  sourceId = id(sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,program_id,
    updatedatetime,status,contenttype,contentdatetime,public1,number_of_pages,restrictToProgram${options.metadataSchema ? ',observationdate' : ''})
    VALUES ('Lab',${h.sqlString(marker + ' stored mutation')},${h.sqlString(filename)},${h.sqlString(provider)},${h.sqlString(provider)},${program},
    NOW(),'A','application/pdf',NOW(),0,3,1${options.metadataSchema ? ',DATE(NOW())' : ''}); SELECT LAST_INSERT_ID()`));
  checkpoint('document-inserted');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${sourceId},'A');
    INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${sourceId},'DOC',NOW());
    INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${h.sqlString(provider)},${sourceId},'DOC','N');
    INSERT INTO queue_document_link (queue_id,document_id,status) VALUES (1,${sourceId},'A')`);
  documents.set(sourceId, {file: sourceFile, fileProof: fileProof(sourceFile, store), snapshot: snapshot(sourceId)});
  baseline = otherDocuments(); initialised = true; uncertain = false; checkpoint('ready');
  return {
    sourceId, sourceFile, journal, ids: () => new Set(documents.keys()),
    begin() {owner(); documents.forEach((record, number) => verify(number)); uncertain = true; checkpoint('mutation-pending');},
    assertUnchanged() {documents.forEach((record, number) => verify(number)); equal(otherDocuments(), baseline, 'Other documents changed');},
    rejectUnaccepted() {
      documents.forEach((record, number) => verify(number));
      equal(otherDocuments(), baseline, 'Rejected operation changed another document');
      uncertain = false; checkpoint('mutation-rejected');
    },
    acceptMetadataTransition(receipt) {
      h.assert(options.metadataSchema && !recovery && initialised && !closed && !cleaned
        && uncertain && !transportUncertain && !acceptedSplitId && removed.length === 0 && documents.size === 1,
      'Metadata acceptance requires an active unambiguous owned operation');
      const {captureMetadataState, validateTransition} = require('./document-metadata-check');
      const intent = receipt.intent;
      h.assert(['metadata', 'queue', 'unlink'].includes(receipt.kind) && intent.kind === receipt.kind
        && intent.document === sourceId && intent.patient === patient && intent.provider === provider,
      'Metadata acceptance escaped its owned scope');
      h.assert(/^[a-f0-9]{64}$/.test(receipt.requestBodySha256)
        && createHash('sha256').update(intent.body).digest('hex') === receipt.requestBodySha256
        && receipt.response?.success === true && receipt.response.accepted === true
        && receipt.response.document === Number(sourceId), 'Metadata acceptance proof incomplete');
      if (receipt.kind === 'metadata') h.assert(receipt.response.patientId === patient
        && intent.description.startsWith(marker + ' '), 'Metadata target or marker changed');
      if (receipt.kind === 'unlink') h.assert(Array.isArray(receipt.response.linkedProviders)
        && receipt.response.linkedProviders.every(link => link && typeof link.providerNo === 'string' && link.providerNo !== provider),
      'Provider unlink was not confirmed');
      const record = documents.get(sourceId);
      const fullSnapshot = proof => Object.fromEntries(Object.entries(proof.rows).map(([table, rows]) => [table, rows.map(row => row.slice(0, -1))]));
      equal(fullSnapshot(receipt.before), record.snapshot, 'Metadata proof did not start from the durable owned snapshot');
      equal(receipt.before.file, record.fileProof, 'Metadata proof changed its starting file');
      owner(); noForeignReferences(sourceId); equal(otherDocuments(), baseline, 'Metadata changed another document');
      const current = captureMetadataState(sql, {document: sourceId, patient, provider, marker, file: sourceFile, store}, options.metadataSchema, receipt.kind);
      equal(current, receipt.after, 'Metadata changed after acceptance was read');
      validateTransition(receipt.kind, receipt.before, current, intent);
      const file = path.join(path.dirname(journal), `metadata-${metadataReceipts.length + 1}.json`);
      // Immutable proof deliberately omits raw body/session token. The original full
      // row snapshot is retained here even after journal advancement.
      const bytes = Buffer.from(JSON.stringify({kind: receipt.kind, document: sourceId, patient, provider,
        requestBodySha256: receipt.requestBodySha256, response: receipt.response,
        before: receipt.before, after: current}, null, 2));
      const descriptor = fs.openSync(file, 'wx', 0o600);
      try {fs.writeFileSync(descriptor, bytes); fs.fsyncSync(descriptor);} finally {fs.closeSync(descriptor);}
      const directory = fs.openSync(path.dirname(journal), 'r');
      try {fs.fsyncSync(directory);} finally {fs.closeSync(directory);}
      metadataReceipts.push({file, sha256: createHash('sha256').update(bytes).digest('hex'), kind: receipt.kind});
      record.snapshot = fullSnapshot(current);
      record.stable = sql.value(`SELECT ${digest('document', true)} FROM document WHERE document_no=${sourceId}`);
      uncertain = false;
      try {checkpoint('metadata-confirmed');}
      catch (error) {uncertain = true; throw error;}
    },
    acceptMutation(pages, expectedPages) {
      const record = documents.get(sourceId);
      const before = record.snapshot;
      const after = snapshot(sourceId);
      for (const table of Object.keys(tableKeys).filter(table => table !== 'document')) equal(after[table], before[table], 'Page edit changed routing');
      // Only the committed page count and JPA update timestamp may change.
      h.assert(sql.value(`SELECT ${digest('document', true)} FROM document WHERE document_no=${sourceId}`) === record.stable,
        'Page edit changed unrelated document metadata');
      record.fileProof = acceptFile(sourceId, pages, expectedPages); record.snapshot = after;
      equal(otherDocuments(), baseline, 'Page edit changed another document'); uncertain = false; checkpoint('mutation-confirmed');
    },
    rememberStable() {documents.get(sourceId).stable = sql.value(`SELECT ${digest('document', true)} FROM document WHERE document_no=${sourceId}`);},
    acceptSplit(number, expectedPages) {
      number = id(number); h.assert(!documents.has(number), 'Split reused an existing document identity');
      acceptedSplitId = number; checkpoint('split-response');
      verify(sourceId);
      const row = sql.rows(`SELECT docfilename,doccreator,contenttype,number_of_pages,program_id,restrictToProgram,public1 FROM document WHERE document_no=${number}`);
      h.assert(row.length === 1 && /^split-[0-9a-f-]{36}\.pdf$/.test(row[0][0]) && row[0][1] === provider
        && row[0][2] === 'application/pdf' && row[0][3] === String(expectedPages.length)
        && row[0][4] === program && row[0][5] === '1' && row[0][6] === '0', 'Split document ownership is not proven');
      const links = sql.rows(`SELECT module,module_id FROM ctl_document WHERE document_no=${number}`);
      equal(links, [['demographic', patient]], 'Split did not retain exactly the owned patient link');
      equal(sql.rows(`SELECT demographic_no FROM patientLabRouting WHERE lab_no=${number} AND lab_type='DOC'`), [[patient]], 'Split lost patient routing');
      const providers = sql.rows(`SELECT DISTINCT provider_no FROM providerLabRouting WHERE lab_no=${number} AND lab_type='DOC'`);
      equal(providers, [[provider]], 'Split changed provider routing ownership');
      equal(sql.rows(`SELECT queue_id,status FROM queue_document_link WHERE document_id=${number}`), [['1', 'A']], 'Split lost queue routing');
      const file = path.join(store, row[0][0]);
      documents.set(number, {file, fileProof: fileProof(file, store), snapshot: snapshot(number)});
      acceptFile(number, expectedPages.length, expectedPages);
      equal(otherDocuments(), baseline, 'Split changed another document'); uncertain = false; checkpoint('split-confirmed');
      return file;
    },
    closeAfterDrain() {closed = true; checkpoint('browser-drained');},
    transportFailed() {transportUncertain = true; checkpoint('transport-unconfirmed');},
    isCleaned() {return cleaned;},
  };
}

/** Explicit operator recovery only; no browser or new fixture is started. Original evidence remains immutable. */
async function recoverStoredDocumentFixture(session, program, options) {
  h.assert(options && typeof options === 'object', 'Explicit reviewed recovery options are required');
  const fixture = createStoredDocumentFixture(session, program, options);
  await fixture.cleanup();
  return {sourceId: fixture.sourceId, cleaned: fixture.isCleaned(), recoveryJournal: fixture.journal};
}
module.exports = {createStoredDocumentFixture, recoverStoredDocumentFixture, externalDocumentReferences, fileProof, inspect};
