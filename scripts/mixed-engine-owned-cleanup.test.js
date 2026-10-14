'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const helper = require('./lib/mixed-engine-owned-cleanup');
function column(table, name, engine = 'InnoDB') {
  return [table, name, '1', engine, null, '', 'int(11)', null, 'NO', 'BASE TABLE'];
}
function plan() {
  return {schema: [column('demographic','demographic_no'), column('formRourke2009','demographic_no','Aria')],
    foreign: [], owned: ['demographic'], tables: ['demographic','formRourke2009'],
    checks: [{table:'demographic',where:'demographic_no=3555 AND SHA2(JSON_ARRAY(demographic_no),256)=\'a\'',count:1},
      {table:'formRourke2009',where:'demographic_no=3555',count:0}],
    deletes: [{table:'demographic',where:'demographic_no=3555 AND SHA2(JSON_ARRAY(demographic_no),256)=\'a\'',expected:1}]};
}
function settings({locks = '1', global = '0', session = '0', triggers = '0', cascades = '0'} = {}) {
  const queries = [];
  return {queries, rows(query) {queries.push(query); return [[locks,global,session]];},
    value(query) {queries.push(query); return query.includes('TRIGGERS') ? triggers : cascades;}};
}
test('only owned parents are WRITE locked and only proven Aria reference is READ locked', () => {
  const p = plan(), sql = helper.build(p);
  helper.capability(settings(), p.schema, p.owned, p.tables, p.foreign);
  assert.match(sql, /LOCK TABLES `demographic` WRITE, `formRourke2009` READ NOWAIT/);
  assert(!sql.includes('READ LOCAL')); assert(!sql.includes('START TRANSACTION'));
  assert.equal([...sql.matchAll(/LOCK TABLES /g)].length, 1);
  assert.deepEqual([...sql.matchAll(/DELETE FROM `([^`]+)`/g)].map(m => m[1]), ['demographic']);
  assert(sql.indexOf('SET SESSION autocommit=0') < sql.indexOf('LOCK TABLES'));
  assert(sql.indexOf('COMMIT;') < sql.indexOf('UNLOCK TABLES;'));
});
test('SQL exceptions rethrow and warnings become fatal errors after rollback, before unlock', () => {
  const sql = helper.build(plan());
  assert.match(sql, /DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;/);
  assert.match(sql, /DECLARE EXIT HANDLER FOR SQLWARNING BEGIN ROLLBACK; SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup warning rejected'; END;/);
  assert.doesNotMatch(sql, /FOR SQLWARNING BEGIN ROLLBACK; RESIGNAL/);
  assert.doesNotMatch(sql, /FOR SQLEXCEPTION, SQLWARNING/);
  assert(sql.indexOf('BEGIN NOT ATOMIC') > sql.indexOf('LOCK TABLES'));
  assert(sql.indexOf('ROLLBACK;') < sql.indexOf('UNLOCK TABLES;'));
  assert.match(sql, /DELIMITER \/\/\nBEGIN NOT ATOMIC/);
});
test('all original row and reference checks precede deletes and exact row count is mandatory', () => {
  const sql = helper.build(plan());
  assert(sql.indexOf('FROM `formRourke2009` WHERE demographic_no=3555') < sql.indexOf('DELETE FROM'));
  assert.match(sql, /IF ROW_COUNT\(\)<>1 THEN SIGNAL SQLSTATE '45000'/);
  assert.match(sql, /Cleanup catalog changed/);
  assert.match(sql, /CAST\(c.ORDINAL_POSITION AS CHAR CHARACTER SET utf8mb4\)/);
});
for (const [label, edit] of [
  ['Aria parent', p => p.schema[0][3] = 'Aria'],
  ['unreviewed MyISAM reference', p => p.schema[1][3] = 'MyISAM'],
  ['unknown Aria reference', p => {p.schema[1][0]='other';p.tables[1]='other';p.checks[1].table='other';}],
  ['reference view', p => p.schema[1][9] = 'VIEW'],
  ['missing schema table', p => p.schema.pop()],
  ['delete references', p => p.deletes[0].table='formRourke2009'],
  ['missing owner lock', p => p.tables.shift()],
]) test('refuses ' + label + ' before SQL execution', () => {
  const p=plan();edit(p);assert.throws(() => helper.build(p));
});
for (const options of [{locks:'0'},{global:'1'},{session:'1'},{triggers:'1'},{cascades:'1'}]) {
  test('preflight refuses unsupported runtime setting '+JSON.stringify(options), () => {
    const p=plan();assert.throws(() => helper.capability(settings(options),p.schema,p.owned,p.tables,p.foreign));
  });
}
test('foreign parent and child tables omitted by conventional names still receive explicit locks', () => {
  const p=plan();p.schema.push(column('opaque','owner'));p.foreign.push(['opaque','owner','demographic','demographic_no']);
  assert.match(helper.build(p), /`opaque` READ/);
});
test('catalog HEX decoding distinguishes SQL NULL, literal NULL, empty and control characters', () => {
  const values=['NULL',null,'', 'tab\tnewline\n'];
  const encoded=values.map(value=>value===null?'n':'x'+Buffer.from(value).toString('hex').toUpperCase());
  assert.deepEqual(helper.readForeign({rows:()=>[encoded]}),[values]);
  assert.notEqual(helper.signature([[null]]),helper.signature([['NULL']]));
  assert.notEqual(helper.signature([['']]),helper.signature([[null]]));
});
test('catalog parser refuses unencoded or truncated cells', () => {
  for (const cells of [['NULL','n','n','n'],['x0','n','n','n'],['n','n','n']]) {
    assert.throws(()=>helper.readForeign({rows:()=>[cells]}));
  }
});
