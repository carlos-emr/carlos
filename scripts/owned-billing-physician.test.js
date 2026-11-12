/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const api = require('./lib/owned-billing-physician');
const vm = require('node:vm');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');
const residue = require('./lib/residue-audit');
// Real SQLite handles every row statement; only catalog/BINARY syntax is adapted.
const PYTHON_SQLITE = [
  "#!/usr/bin/env python3",
  "\"\"\"Isolated real SQLite prepared-SQL test adapter, never an application DB.",
  "",
  "Only information_schema catalog reads are adapted to SQLite PRAGMA. All fixture",
  "row SELECT/INSERT/DELETE statements and their separate parameters execute in the",
  "real database. MariaDB-specific runtime acceptance remains a later ROOT gate.",
  "\"\"\"",
  "import hashlib",
  "import json",
  "import re",
  "import sqlite3",
  "import sys",
  "",
  "request = json.load(sys.stdin)",
  "db = sqlite3.connect(request['database'])",
  "db.execute('PRAGMA foreign_keys=ON')",
  "db.execute('PRAGMA case_sensitive_like=ON')",
  "db.create_function('DATABASE', 0, lambda: 'carlos')",
  "db.create_function('SHA2', 2, lambda value, size: hashlib.sha256(value.encode()).hexdigest() if size == 256 else None)",
  "try:",
  "    query, args = request['query'], request.get('args', [])",
  "    # MariaDB BINARY prefix enforces the same case sensitivity set above.",
  "    query = re.sub(r'\\bBINARY (?=`)', '', query)",
  "    if 'FROM information_schema.TRIGGERS' in query:",
  "        rows = [[db.execute(\"SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND tbl_name IN ('provider','providersite')\").fetchone()[0]]]",
  "    elif 'FROM information_schema.REFERENTIAL_CONSTRAINTS r' in query:",
  "        rows = []",
  "        for table, ddl in db.execute(\"SELECT name,sql FROM sqlite_master WHERE type='table' ORDER BY name\"):",
  "            declared = {}",
  "            for m in re.finditer(r'CONSTRAINT\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+FOREIGN\\s+KEY\\s*\\(([^)]+)\\)', ddl, re.I):",
  "                for name in m.group(2).split(','):",
  "                    declared[name.strip().strip('`\"')] = m.group(1)",
  "            for fk in db.execute('PRAGMA foreign_key_list(\"' + table + '\")'):",
  "                fk_id, seq, target, column, target_column, update, remove, _ = fk",
  "                if table in ('provider', 'providersite') or target in ('provider', 'providersite'):",
  "                    rows.append([declared.get(column, 'sqlite_fk_' + table + '_' + str(fk_id)), table, target,",
  "                                 update, remove, column, target_column, seq + 1, 'carlos'])",
  "        rows.sort(key=lambda row: (row[1], row[0], row[7]))",
  "    elif 'FROM information_schema.REFERENTIAL_CONSTRAINTS' in query:",
  "        rows = [[sum(len(list(db.execute('PRAGMA foreign_key_list(\"' + table + '\")'))) for (table,) in db.execute(\"SELECT name FROM sqlite_master WHERE type='table'\"))]]",
  "    elif 'SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS' in query:",
  "        wanted = re.findall(r\"'([^']+)'\", query)",
  "        rows = [[table, column[1]] for (table,) in db.execute(\"SELECT name FROM sqlite_master WHERE type='table' ORDER BY name\")",
  "                if table in wanted for column in db.execute('PRAGMA table_info(\"' + table + '\")')]",
  "    elif 'FROM information_schema.TABLES' in query:",
  "        rows = list(db.execute(\"SELECT name FROM sqlite_master WHERE type='table' ORDER BY name\"))",
  "    elif 'SELECT COLUMN_NAME FROM information_schema.COLUMNS' in query:",
  "        rows = [[r[1]] for r in db.execute('PRAGMA table_info(\"' + args[0] + '\")')]",
  "    elif 'FROM information_schema.COLUMNS c' in query:",
  "        rows = []",
  "        for (table,) in db.execute(\"SELECT name FROM sqlite_master WHERE type='table' ORDER BY name\"):",
  "            for column in db.execute('PRAGMA table_info(\"' + table + '\")'):",
  "                if column[1].lower() in args:",
  "                    rows.append([table, column[1]])",
  "    elif request.get('script'):",
  "        db.executescript(query)",
  "        rows = []",
  "    else:",
  "        cursor = db.execute(query, args)",
  "        rows = cursor.fetchall() if cursor.description else []",
  "    db.commit()",
  "    print(json.dumps([[None if x is None else str(x) for x in row] for row in rows]))",
  "finally:",
  "    db.close()",
].join('\n');
function owner(extra = {}) { return { marker: 'FAKE-PW0123456789abcdef', operator: '999998', ...extra }; }
function database(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'on-billing-prereq-test-')); fs.chmodSync(dir, 0o700);
  t.after(() => fs.rmSync(dir, {recursive: true, force: true}));
  const file = path.join(dir, 'synthetic.sqlite'); const calls = [];
  const sql = {
    rows(query, args = [], script = false) {
      calls.push({query, args});
      const run = spawnSync('/usr/bin/python3', ['-c', PYTHON_SQLITE], {
        input: JSON.stringify({database: file, query, args, script}), encoding: 'utf8', timeout: 10000,
        env: {...process.env, PYTHONDONTWRITEBYTECODE: '1'}, maxBuffer: 1048576,
      });
      assert.equal(run.status, 0, 'Isolated synthetic SQL adapter refused: ' + run.stderr); return JSON.parse(run.stdout);
    },
    value(query, args) { const rows = this.rows(query, args); return rows.length ? rows[0][0] : ''; },
    execute(query, args) { this.rows(query, args); },
  };
  sql.rows(`CREATE TABLE provider(provider_no VARCHAR(6) PRIMARY KEY,last_name VARCHAR(30),first_name VARCHAR(30),
    provider_type VARCHAR(15),specialty VARCHAR(40),status CHAR(1),ohip_no VARCHAR(20),lastUpdateUser VARCHAR(6),
    lastUpdateDate DATETIME,comments TEXT);
    CREATE TABLE site(site_id INTEGER PRIMARY KEY,name VARCHAR(255) UNIQUE,status INTEGER);
    CREATE TABLE providersite(provider_no VARCHAR(6),site_id INTEGER,PRIMARY KEY(provider_no,site_id));
    CREATE TABLE quickListUser(id INTEGER PRIMARY KEY,providerNo VARCHAR(6),quickListName VARCHAR(10));
    CREATE TABLE security(security_no INTEGER PRIMARY KEY,provider_no VARCHAR(6));
    CREATE TABLE billing_on_cheader1(id INTEGER PRIMARY KEY,provider_no VARCHAR(6));
    CREATE TABLE ClientLink(id INTEGER PRIMARY KEY,linkProviderNo VARCHAR(6),unlinkProviderNo VARCHAR(6),
      CONSTRAINT ClientLink_ibfk_3 FOREIGN KEY(linkProviderNo) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT,
      CONSTRAINT ClientLink_ibfk_4 FOREIGN KEY(unlinkProviderNo) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE DigitalSignature(id INTEGER PRIMARY KEY,providerNo VARCHAR(6),
      CONSTRAINT DigitalSignature_ibfk_2 FOREIGN KEY(providerNo) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE HnrDataValidation(id INTEGER PRIMARY KEY,validatorProviderNo VARCHAR(6),
      CONSTRAINT HnrDataValidation_ibfk_3 FOREIGN KEY(validatorProviderNo) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE document_review(id INTEGER PRIMARY KEY,provider_no VARCHAR(6),
      CONSTRAINT document_review_ibfk_2 FOREIGN KEY(provider_no) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE program_client_restriction(id INTEGER PRIMARY KEY,provider_no VARCHAR(6),
      CONSTRAINT FK_pcr_provider FOREIGN KEY(provider_no) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE program_provider(id INTEGER PRIMARY KEY,provider_no VARCHAR(6),
      CONSTRAINT FKfg27qvr98onqjiqn6wi3r5obw FOREIGN KEY(provider_no) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    CREATE TABLE provider_facility(id INTEGER PRIMARY KEY,provider_no VARCHAR(6),
      CONSTRAINT provider_facility_ibfk_1 FOREIGN KEY(provider_no) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT);
    INSERT INTO provider VALUES('999998','Original','Operator','doctor','GP','1','',NULL,'2020-01-01 01:02:03','unchanged');
    INSERT INTO provider VALUES('2','Original','Other','doctor','GP','1',NULL,NULL,'2020-01-01 01:02:03','unchanged');
    INSERT INTO site VALUES(1,'FAKE Original Clinic',1),(2,'FAKE Other Clinic',1);
    INSERT INTO providersite VALUES('999998',1),('999998',2),('2',1);`, [], true);
  calls.length = 0;
  const saved = []; const ledger = path.join(dir, 'owned.json'); const durable = api.privateStore(ledger, true);
  const save = state => { durable(state); saved.push(JSON.parse(JSON.stringify(state))); };
  return {dir, file, sql, calls, saved, save, ledger};
}
const random = () => { const values = [970001, 960001]; return () => { assert(values.length, 'Unexpected allocator retry'); return values.shift(); }; };
test('real prepared SQL creates exactly one FAKE physician/membership, exposes native eligibility and restores all three tables', t => {
  const db = database(t), auth = owner(); const state = api.prepare(db.sql, auth, db.save, {randomInt: random()});
  assert.equal(state.providerNo, '970001'); assert.equal(state.ohipNo, '960001'); assert.equal(state.siteId, '1');
  assert.equal(state.foreignKeys.length,8);
  for(const [table,column] of api.constants.PROVIDER_FOREIGN_KEYS)assert(state.references.some(r=>r[0]===table&&r[1]===column));
  assert.equal(db.saved[0].status, 'PREPARED_BEFORE_INSERT'); assert.equal(db.saved[0].before.provider.count, 2);
  assert.equal(fs.statSync(db.ledger).mode & 0o777, 0o600);
  assert.equal(api.verify(db.sql, auth, state).status, 'OWNED_PREREQUISITE_VERIFIED');
  assert.equal(db.sql.value("SELECT COUNT(*) FROM provider WHERE provider_no='999998' AND ohip_no=''"), '1');
  assert.equal(db.sql.value("SELECT COUNT(*) FROM providersite WHERE provider_no='999998'"), '2');
  assert.equal(api.cleanup(db.sql, auth, state, db.save).status, 'OWNED_PREREQUISITE_REMOVED');
  assert.equal(state.status, 'CLEANED');
  assert(db.calls.filter(c => /^(INSERT|DELETE)\b/.test(c.query)).every(c => /^(INSERT INTO|DELETE FROM) (provider|providersite)\b/.test(c.query)));
});
test('non-synthetic markers and malformed operators refuse before SQL', () => {
  for (const extra of [{marker:'REAL-PATIENT'}, {operator:'1 OR 1=1'}, {operator:'1234567'}]) {
    assert.throws(() => api.prepare({value(){throw Error('SQL must not be entered');}}, owner(extra), () => {}),
      e => !e.message.includes('SQL must not be entered'));
  }
});
test('no existing operator clinic refuses before ledger creation or mutation', t => {
  const db = database(t); db.sql.execute("DELETE FROM providersite WHERE provider_no='999998'"); db.calls.length=0;
  assert.throws(() => api.prepare(db.sql, owner(), db.save), /No existing active operator-owned site/);
  assert.equal(db.saved.length,0); assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});
test('unrelated orphan quickListUser and membership collisions are not adopted or deleted', t => {
  const db = database(t); db.sql.execute("INSERT INTO quickListUser VALUES(8,'970001','FOREIGN')");
  db.sql.execute("INSERT INTO providersite VALUES('970002',2)");
  const ids=[970001,970002,970003,960001]; const state=api.prepare(db.sql,owner(),db.save,{randomInt:()=>ids.shift()});
  assert.equal(state.providerNo,'970003'); api.cleanup(db.sql,owner(),state,db.save);
  assert.equal(db.sql.value("SELECT COUNT(*) FROM quickListUser WHERE id=8 AND providerNo='970001'"),'1');
  assert.equal(db.sql.value("SELECT COUNT(*) FROM providersite WHERE provider_no='970002' AND site_id=2"),'1');
});
test('durable ownership ledger is present before first INSERT and a failed ledger write prevents all DML', t => {
  const db=database(t), execute=db.sql.execute.bind(db.sql);
  db.sql.execute=(query,args)=>{if(/^INSERT/.test(query))assert(fs.existsSync(db.ledger));return execute(query,args);};
  const state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});api.cleanup(db.sql,owner(),state,db.save);
  const other=database(t); assert.throws(()=>api.prepare(other.sql,owner(),()=>{throw Error('synthetic fsync failure');},{randomInt:random()}),/fsync failure/);
  assert(!other.calls.some(c=>/^INSERT/.test(c.query)));
});
test('lost provider INSERT acknowledgement leaves exact pre-registered row recoverable', t => {
  const db=database(t), execute=db.sql.execute.bind(db.sql); let failed=false;
  db.sql.execute=(query,args)=>{execute(query,args);if(!failed&&/^INSERT INTO provider \(/.test(query)){failed=true;throw Error('lost reply');}};
  assert.throws(()=>api.prepare(db.sql,owner(),db.save,{randomInt:random()}),/lost reply/);
  const state=JSON.parse(fs.readFileSync(db.ledger));assert.equal(state.status,'PREPARED_BEFORE_INSERT');
  api.cleanup(db.sql,owner(),state,db.save);assert.equal(db.sql.value("SELECT COUNT(*) FROM provider WHERE provider_no='970001'"),'0');
});
test('failed membership INSERT removes its exact provider only and preserves all original rows', t => {
  const db=database(t), execute=db.sql.execute.bind(db.sql);
  db.sql.execute=(query,args)=>{if(/^INSERT INTO providersite/.test(query))throw Error('synthetic membership failure');return execute(query,args);};
  assert.throws(()=>api.prepare(db.sql,owner(),db.save,{randomInt:random()}),/membership failure/);
  const state=JSON.parse(fs.readFileSync(db.ledger));assert.equal(state.status,'PROVIDER_CREATED_BEFORE_MEMBERSHIP');
  api.cleanup(db.sql,owner(),state,db.save);assert.equal(db.sql.value('SELECT COUNT(*) FROM providersite'),'3');
});
test('changed owned provider blocks both child and parent removal', t => {
  const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
  db.sql.execute("UPDATE provider SET comments='foreign change' WHERE provider_no='970001'");db.calls.length=0;
  assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/missing or changed/);
  assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
});
test('foreign membership on owned number blocks all removal', t => {
  const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
  db.sql.execute("INSERT INTO providersite VALUES('970001',2)");db.calls.length=0;
  assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/Pre-existing provider\/site rows changed/);
  assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
});
test('new application child reference blocks parent deletion and preserves the evidence', t => {
  const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
  db.sql.execute("INSERT INTO billing_on_cheader1 VALUES(9,'970001')");db.calls.length=0;
  assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/external references/);
  assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
});
test('changed original provider or site is never silently restored or adopted', t => {
  const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
  db.sql.execute("UPDATE provider SET comments='changed original' WHERE provider_no='2'");db.calls.length=0;
  assert.throws(()=>api.verify(db.sql,owner(),state),/Pre-existing provider\/site rows changed/);
  assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/Pre-existing provider\/site rows changed/);
  assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
});
test('child deletion failure never attempts provider deletion', t => {
  const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()}),execute=db.sql.execute.bind(db.sql);
  db.calls.length=0;db.sql.execute=(query,args)=>{if(/^DELETE FROM providersite/.test(query))throw Error('synthetic child refusal');return execute(query,args);};
  assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/child refusal/);
  assert(!db.calls.some(c=>/^DELETE FROM provider WHERE/.test(c.query)));
  assert.equal(db.sql.value("SELECT COUNT(*) FROM provider WHERE provider_no='970001'"),'1');
});
test('private ledger refuses foreign file, symlink, overwrite and world-writable directory', t => {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'on-billing-store-test-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));fs.chmodSync(dir,0o700);
  const file=path.join(dir,'ledger');api.privateStore(file,true)({first:true});assert.throws(()=>api.privateStore(file,true)({second:true}));
  fs.chmodSync(file,0o644);assert.throws(()=>api.privateStore(file,false)({second:true}),/Invalid private ledger inode/);
  fs.unlinkSync(file);fs.symlinkSync(path.join(dir,'foreign'),file);assert.throws(()=>api.privateStore(file,false)({second:true}),/Invalid private ledger inode/);
  fs.chmodSync(dir,0o777);assert.throws(()=>api.privateStore(path.join(dir,'new'),true),/Private ownership ledger directory/);
});
test('provider DML trigger refuses provisioning before writes', t => {
  const db=database(t);db.sql.execute('CREATE TRIGGER foreign_trigger BEFORE INSERT ON provider BEGIN SELECT 1; END');db.calls.length=0;
  assert.throws(()=>api.prepare(db.sql,owner(),db.save),/triggers require separate review/);
  assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});
test('provider foreign-key effects refuse provisioning before writes', t => {
  const db=database(t);db.sql.execute('CREATE TABLE foreign_links(provider_no VARCHAR(6),FOREIGN KEY(provider_no) REFERENCES provider(provider_no))');db.calls.length=0;
  assert.throws(()=>api.prepare(db.sql,owner(),db.save),/Unreviewed provider foreign-key/);
  assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});
for(const [table,column] of [['ClientLink','linkProviderNo'],['ClientLink','unlinkProviderNo'],['DigitalSignature','providerNo'],
  ['HnrDataValidation','validatorProviderNo'],['document_review','provider_no'],['program_client_restriction','provider_no'],
  ['program_provider','provider_no'],['provider_facility','provider_no']]) {
  test(`actual incoming FK child ${table}.${column} prevents membership and parent cleanup`,t=>{
    const db=database(t), state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
    db.sql.execute(`INSERT INTO \`${table}\`(id,\`${column}\`) VALUES (?,?)`,['19',state.providerNo]);db.calls.length=0;
    assert.throws(()=>api.verify(db.sql,owner(),state),/external references/);
    assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/external references/);
    assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
    assert.equal(db.sql.value('SELECT COUNT(*) FROM providersite WHERE provider_no=?',[state.providerNo]),'1');
    assert.equal(db.sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?',[state.providerNo]),'1');
  });
}
for(const rule of ['CASCADE','SET NULL']) {
  test(`incoming FK ${rule} cannot enter the reviewed RESTRICT scope`,t=>{
    const db=database(t);db.sql.execute('DROP TABLE HnrDataValidation');
    db.sql.execute(`CREATE TABLE HnrDataValidation(id INTEGER PRIMARY KEY,validatorProviderNo VARCHAR(6),
      CONSTRAINT HnrDataValidation_ibfk_3 FOREIGN KEY(validatorProviderNo) REFERENCES provider(provider_no) ON DELETE ${rule} ON UPDATE RESTRICT)`);
    db.calls.length=0;assert.throws(()=>api.prepare(db.sql,owner(),db.save),/Unreviewed provider foreign-key effect or column/);
    assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
  });
}
test('known NO ACTION incoming keys preserve valid prepare and exact cleanup',t=>{
  const db=database(t);db.sql.execute('DROP TABLE HnrDataValidation');
  db.sql.execute(`CREATE TABLE HnrDataValidation(id INTEGER PRIMARY KEY,validatorProviderNo VARCHAR(6),
    CONSTRAINT HnrDataValidation_ibfk_3 FOREIGN KEY(validatorProviderNo) REFERENCES provider(provider_no) ON UPDATE NO ACTION ON DELETE NO ACTION)`);
  const state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});api.cleanup(db.sql,owner(),state,db.save);
  assert.equal(state.status,'CLEANED');
});
test('incoming FK schema renaming during phase refuses cleanup before child deletion',t=>{
  const db=database(t),state=api.prepare(db.sql,owner(),db.save,{randomInt:random()});
  db.sql.execute('DROP TABLE HnrDataValidation');
  db.sql.execute(`CREATE TABLE HnrDataValidation(id INTEGER PRIMARY KEY,validatorProviderNo VARCHAR(6),
    CONSTRAINT changed_name FOREIGN KEY(validatorProviderNo) REFERENCES provider(provider_no) ON UPDATE RESTRICT ON DELETE RESTRICT)`);
  db.calls.length=0;assert.throws(()=>api.cleanup(db.sql,owner(),state,db.save),/foreign-key schema changed/);
  assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
});
test('outgoing provider dependency is unreviewed and cannot reach INSERT',t=>{
  const db=database(t);db.sql.execute('CREATE TABLE foreign_owner(id INTEGER PRIMARY KEY)');
  db.sql.execute('ALTER TABLE provider ADD COLUMN foreign_owner_id INTEGER REFERENCES foreign_owner(id)');
  db.calls.length=0;assert.throws(()=>api.prepare(db.sql,owner(),db.save),/Unreviewed provider foreign-key/);
  assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});
test('a historical orphan in nonstandard ClientLink column is not adopted by allocation or cleanup',t=>{
  const db=database(t);
  db.sql.rows("PRAGMA foreign_keys=OFF; INSERT INTO ClientLink(id,linkProviderNo) VALUES(91,'970001'); PRAGMA foreign_keys=ON;",[],true);
  const values=[970001,970002,960001];
  const state=api.prepare(db.sql,owner(),db.save,{randomInt:()=>values.shift()});assert.equal(state.providerNo,'970002');
  api.cleanup(db.sql,owner(),state,db.save);
  assert.equal(db.sql.value("SELECT COUNT(*) FROM ClientLink WHERE id=91 AND linkProviderNo='970001'"),'1');
});
test('multi-column provider foreign key is unreviewed and cannot reach INSERT',t=>{
  const db=database(t);db.sql.execute('DROP TABLE HnrDataValidation');
  db.sql.execute('CREATE UNIQUE INDEX provider_composite ON provider(provider_no,status)');
  db.sql.execute(`CREATE TABLE HnrDataValidation(id INTEGER PRIMARY KEY,validatorProviderNo VARCHAR(6),provider_status CHAR(1),
    CONSTRAINT HnrDataValidation_ibfk_3 FOREIGN KEY(validatorProviderNo,provider_status) REFERENCES provider(provider_no,status) ON UPDATE RESTRICT ON DELETE RESTRICT)`);
  db.calls.length=0;assert.throws(()=>api.prepare(db.sql,owner(),db.save),/Unreviewed provider foreign-key/);
  assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});
test('unknown membership columns refuse before the ownership ledger or INSERT', t => {
  const db=database(t);db.sql.execute('ALTER TABLE providersite ADD COLUMN foreign_note VARCHAR(20)');db.calls.length=0;
  assert.throws(()=>api.prepare(db.sql,owner(),db.save),/exact shipped two-column membership schema/);
  assert.equal(db.saved.length,0);assert(!db.calls.some(c=>/^INSERT/.test(c.query)));
});

function sessionFor(db, extra = {}) {
  const events = [];
  const browser = { connected: true, isConnected(){ return this.connected; },
    async close(){ events.push('browser-close'); this.connected = false; } };
  db.sql.dispose = () => events.push('dispose');
  const cleanups = [];
  const session = { sql: db.sql, marker: owner().marker, provider: owner().operator,
    context: { browser: () => browser }, cleanup(fn){ cleanups.push(fn); }, ...extra };
  const finish = () => cleanupOwnedWorkflow({ browser, sql: db.sql, cleanups });
  return { session, browser, cleanups, events, finish };
}
function fixtureFor(db, session) {
  return api.createOwnedBillingPhysician(session, { ledgerDirectory: db.dir, randomInt: random() });
}
function auditSql(sql) {
  return { value: sql.value.bind(sql), rows(query, args) {
    const rows = sql.rows(query, args);
    // Only advertise provider to the marker auditor; all count rows remain real.
    // Other synthetic tables deliberately lack the application's clinical columns.
    return query.startsWith('SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS')
      ? rows.filter(row => row[0] === 'provider') : rows;
  } };
}

test('unchanged absolute FAKE-PW audit rejects a retained phase provider even when it predates baseline; inline cleanup restores clean', async t => {
  const db = database(t), workflow = sessionFor(db);
  const physician = fixtureFor(db, workflow.session);
  const auditor = auditSql(db.sql);
  const since = residue.captureBaseline({ sql: auditor });
  assert.deepEqual(residue.auditResidueDetailed({sql: auditor, since}).residue, [{table:'provider',count:1}]);
  await workflow.finish();
  assert.equal(JSON.parse(fs.readFileSync(physician.ledgerPath)).status, 'CLEANED');
  // Normal per-case baseline precedes its INSERT. Marker and all table counts
  // are clean after the exact parent/member teardown, with no auditor changes.
  const cleanBaseline = residue.captureBaseline({ sql: auditor });
  const again = sessionFor(db); fixtureFor(db, again.session); await again.finish();
  assert.deepEqual(residue.auditResidueDetailed({sql: auditor, since: cleanBaseline}).residue, []);
});

test('actual workflow finalizer closes browser, removes dependent claims LIFO, then exact membership/provider', async t => {
  const db = database(t), workflow = sessionFor(db), physician = fixtureFor(db, workflow.session);
  const execute = db.sql.execute.bind(db.sql);
  db.sql.execute = (query,args) => {
    if (/^DELETE FROM providersite/.test(query)) workflow.events.push('membership-delete');
    if (/^DELETE FROM provider WHERE/.test(query)) workflow.events.push('provider-delete');
    return execute(query,args);
  };
  for (const id of [11,12,13]) {
    physician.dependentSession.cleanup(() => { workflow.events.push('claim-'+id);
      db.sql.execute('DELETE FROM billing_on_cheader1 WHERE id=? AND provider_no=?',[id,physician.providerNo]); });
    db.sql.execute('INSERT INTO billing_on_cheader1 VALUES (?,?)',[id,physician.providerNo]);
  }
  await workflow.finish();
  assert.deepEqual(workflow.events, ['browser-close','claim-13','claim-12','claim-11','membership-delete','provider-delete','dispose']);
  assert.equal(db.sql.value('SELECT COUNT(*) FROM billing_on_cheader1'),'0');
});

test('actual continue-after-error workflow refuses physician teardown when any dependent cleanup failed', async t => {
  const db=database(t), workflow=sessionFor(db), physician=fixtureFor(db,workflow.session);
  physician.dependentSession.cleanup(()=>{throw Error('synthetic claim refusal');});
  let laterAttempted=false;
  physician.dependentSession.cleanup(()=>{laterAttempted=true;});
  db.calls.length=0;
  await assert.rejects(workflow.finish(), /dependent cleanup is unconfirmed/);
  assert(laterAttempted);
  assert(!db.calls.some(c=>/^DELETE FROM (provider|providersite)/.test(c.query)));
  assert.equal(db.sql.value('SELECT COUNT(*) FROM provider WHERE provider_no=?',[physician.providerNo]),'1');
});

test('actual failed browser close prevents both new dependent and physician DML, while retaining durable ownership', async t => {
  const db=database(t), workflow=sessionFor(db), physician=fixtureFor(db,workflow.session);
  workflow.browser.close=async()=>{throw Error('synthetic browser close refusal');};
  let child=false; physician.dependentSession.cleanup(()=>{child=true;}); db.calls.length=0;
  await assert.rejects(workflow.finish(), /browser close is unconfirmed/);
  assert.equal(child,false); assert(!db.calls.some(c=>/^DELETE/.test(c.query)));
  assert.equal(JSON.parse(fs.readFileSync(physician.ledgerPath)).status,'ACTIVE');
});

for (const failure of ['provider reply','membership insert','active ledger']) {
  test(`registered workflow cleanup handles ${failure} failure after pre-DML ownership save`, async t=>{
    const db=database(t), workflow=sessionFor(db), execute=db.sql.execute.bind(db.sql);
    let injected=false;
    db.sql.execute=(query,args)=>{
      if(!injected&&failure==='membership insert'&&/^INSERT INTO providersite/.test(query)){injected=true;throw Error('synthetic setup failure');}
      const result=execute(query,args);
      if(!injected&&failure==='provider reply'&&/^INSERT INTO provider \(/.test(query)){injected=true;throw Error('synthetic setup failure');}
      return result;
    };
    if(failure==='active ledger') {
      // The actual durable writer fails only after both INSERTs: monkeypatch
      // fs.renameSync, then restore before the real finalizer performs teardown.
      const rename=fs.renameSync;
      fs.renameSync=(from,to)=>{if(!injected&&JSON.parse(fs.readFileSync(from)).status==='ACTIVE'){injected=true;throw Error('synthetic setup failure');} return rename(from,to);};
      try{assert.throws(()=>fixtureFor(db,workflow.session),/synthetic setup failure/);}finally{fs.renameSync=rename;}
    } else assert.throws(()=>fixtureFor(db,workflow.session),/synthetic setup failure/);
    assert.equal(workflow.cleanups.length,1);
    await workflow.finish();
    assert.equal(db.sql.value("SELECT COUNT(*) FROM provider WHERE provider_no='970001'"),'0');
    assert.equal(db.sql.value('SELECT COUNT(*) FROM providersite'),'3');
  });
}

test('native clinic selector uses only the exact offered owned site and exact physician binding', async t => {
  const db=database(t), workflow=sessionFor(db), physician=fixtureFor(db,workflow.session);
  let selected; let options=[{value:'FAKE Other Clinic',disabled:false},{value:physician.siteName,disabled:false}];
  const sites={count:async()=>1,locator:()=>({evaluateAll:async()=>options}),
    selectOption:async value=>{selected=value;},inputValue:async()=>selected};
  await physician.selectClinic({locator:()=>sites}); assert.equal(selected,physician.siteName);
  physician.assertContext({providerNo:physician.providerNo,ohipNo:physician.ohipNo,multisites:true,clinic:physician.siteName});
  assert.throws(()=>physician.assertContext({providerNo:owner().operator,ohipNo:physician.ohipNo}),/does not identify/);
  options=[{value:physician.siteName,disabled:true}]; selected=undefined;
  await assert.rejects(physician.selectClinic({locator:()=>sites}),/not offered/); assert.equal(selected,undefined);
  await workflow.finish();
});

function loadEntry(name, doubles) {
  const filename=path.join(__dirname,name);
  const code=fs.readFileSync(filename,'utf8');
  const module={exports:{}};
  const req=id=>{
    if(Object.hasOwn(doubles,id))return doubles[id];
    if(id==='./lib/playwright-harness')return {assert:(ok,message)=>assert(ok,message),sqlString:value=>"'"+String(value).replaceAll("'","''")+"'"};
    if(id==='./lib/workflow-session')return {};
    if(id==='./lib/playwright-ui'||id==='./lib/playwright-link-audit')return {};
    throw Error('Unexpected workflow import '+id);
  };
  req.main={};
  vm.runInNewContext(code,{require:req,module,exports:module.exports,process,console,URL,setTimeout}, {filename});
  return module.exports.workflow;
}

test('actual correction workflow creates its physician before UI and routes all three claim cleanup registrations through its dependency gate',async()=>{
  const events=[], bodyStop=Error('stop after seed assertions');
  const billSession={tag:'dependent claims'};
  const context={providerNo:'970001',ohipNo:'960001',multisites:true,clinic:'Owned Clinic'};
  const physician={providerNo:'970001',dependentSession:billSession,
    async selectClinic(){events.push('clinic');},assertContext(actual){assert.equal(actual,context);events.push('context');}};
  let closed=false;
  const form={isClosed:()=>closed,close:async()=>{closed=true;}};
  const workflow=loadEntry('billing-on-correction-delete-playwright-checks.js',{
    './lib/billing-report-context':{}, // Independently tested report step is beyond this native/seed control.
    './lib/owned-billing-physician':{createOwnedBillingPhysician(s){events.push('fixture');return physician;}},
    './lib/billing-site-context':{async selectBillingSiteAndProvider(page,provider){assert.equal(provider,physician.providerNo);events.push('native');return context;},validateBillingContext(){}},
    './billing-on-invoice-third-party-playwright-checks':{openHistory(){throw bodyStop;},money:value=>Number(value).toFixed(2),billDate:()=> '2026-10-01',seedOwnedBill(s,options){assert.equal(s,billSession);assert.equal(options.billingContext,context);events.push('seed-'+options.status);return {headerId:events.length,itemId:2,provider:physician.providerNo};}},
  });
  let step=0;
  const s={provider:'999998',marker:owner().marker,patient:'12',sql:{value:q=>q.includes('SELECT value FROM billingservice')?'10':q.includes('AND demographic_no=12')?'1':'0'},
    master:{getByRole:()=>({first:()=>({})})},popup:async()=>form,async step(label,body){if(++step>1)throw bodyStop;events.push('ui');await body();}};
  await assert.rejects(workflow(s),error=>error===bodyStop);
  assert.deepEqual(events,['fixture','ui','clinic','native','context','seed-O','seed-B','seed-S']);
});

test('actual Dx workflow reserves the physician before any other fixture INSERT or cleanup registration',async()=>{
  const stop=Error('stop before first existing quick-list INSERT'), events=[];
  const workflow=loadEntry('dx-registry-quicklist-playwright-checks.js',{
    './lib/quick-list-preference-fixture':{createQuickListPreferenceFixture(s,names){assert.equal(names.length,2);events.push('preference-snapshot');}},
    './lib/owned-billing-physician':{createOwnedBillingPhysician(){events.push('physician');return{};}},
    './lib/billing-site-context':{},
  });
  await assert.rejects(workflow({provider:'999998',marker:owner().marker,patient:'12',
    cleanup(){events.push('other-cleanup');},sql:{execute(){events.push('other-insert');throw stop;}}}),error=>error===stop);
  assert.deepEqual(events,['physician','preference-snapshot','other-cleanup','other-insert']);
});

test('actual Dx native billing step selects and verifies the owned physician instead of the operator or a foreign option',async()=>{
  const stop=Error('stop after exact native physician selection'), events=[];
  const context={providerNo:'970001',ohipNo:'960001',multisites:true,clinic:'Owned Clinic'};
  const physician={providerNo:context.providerNo,async selectClinic(){events.push('clinic');},assertContext(value){assert.equal(value,context);events.push('owned-context');}};
  const plain={first(){return this;},async waitFor(){},async click(){},async check(){},locator(){return this;},getByRole(){return this;},
    async evaluateAll(){return ['999998|999999','970001|960001','2|123'];},async selectOption(value){events.push('select-'+value);},
    async fill(){throw stop;}};
  const bill={locator(){return plain;}};
  const admin={locator(){return plain;},getByRole(){return plain;},frameLocator(){return plain;}};
  const workflow=loadEntry('dx-registry-quicklist-playwright-checks.js',{
    './lib/quick-list-preference-fixture':{createQuickListPreferenceFixture(){}},
    './lib/owned-billing-physician':{createOwnedBillingPhysician(){return physician;}},
    './lib/billing-site-context':{async selectBillingSiteAndProvider(page,preferred){assert.equal(page,bill);assert.equal(preferred,context.providerNo);events.push('native');return context;},validateBillingContext(){}},
    './lib/playwright-ui':{async clickOpensPopupOrNavigates(){return {page:admin};}},
    './lib/playwright-link-audit':{async revealAuditLink(){}},
  });
  const s={provider:'999998',patient:'12',marker:owner().marker,sql:{execute(){},value(){return'0';}},
    cleanup(){},context:{},schedule:{locator(){return plain;}},master:{getByRole(){return plain;}},
    popup:async()=>bill,async step(label,body){if(label.startsWith('billing review Dx Quick Pick'))await body();}};
  await assert.rejects(workflow(s),error=>error===stop);
  assert.deepEqual(events,['clinic','native','owned-context','select-970001|960001']);
});
