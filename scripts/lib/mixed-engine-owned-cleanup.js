'use strict';
// Evidence-only SQL generation. One fresh mysql CLI connection executes the result.
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const SCHEMA_FIELDS = ['c.TABLE_NAME','c.COLUMN_NAME','c.ORDINAL_POSITION','t.ENGINE','c.COLUMN_DEFAULT','c.EXTRA',
  'c.COLUMN_TYPE','c.COLLATION_NAME','c.IS_NULLABLE','t.TABLE_TYPE'];
const SCHEMA_FROM = 'information_schema.COLUMNS c JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE()';
const encoded = field => `IFNULL(CONCAT('x',HEX(CAST(${field} AS CHAR CHARACTER SET utf8mb4))),'n')`;
const SCHEMA_SQL = `SELECT ${SCHEMA_FIELDS.map(encoded).join(',')} FROM ${SCHEMA_FROM} ORDER BY BINARY c.TABLE_NAME,c.ORDINAL_POSITION`;
const FOREIGN_FIELDS = ['TABLE_NAME','COLUMN_NAME','REFERENCED_TABLE_NAME','REFERENCED_COLUMN_NAME'];
const FOREIGN_FROM = 'information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL';
const FOREIGN_SQL = `SELECT ${FOREIGN_FIELDS.map(encoded).join(',')} FROM ${FOREIGN_FROM} ORDER BY BINARY TABLE_NAME,BINARY COLUMN_NAME,BINARY REFERENCED_TABLE_NAME,BINARY REFERENCED_COLUMN_NAME`;
function decode(rows, width) {
  return rows.map(row => {
    assert.equal(row.length, width, 'Invalid cleanup catalog width');
    return row.map(value => {
      assert(typeof value === 'string' && (value === 'n' || /^x(?:[0-9A-F]{2})*$/.test(value)), 'Invalid encoded cleanup catalog');
      return value === 'n' ? null : Buffer.from(value.slice(1), 'hex').toString('utf8');
    });
  });
}
function readSchema(sql) { return decode(sql.rows(SCHEMA_SQL), 10); }
function readForeign(sql) { return decode(sql.rows(FOREIGN_SQL), 4); }
function identifier(value) {
  assert(typeof value === 'string' && value.length > 0 && !/[^A-Za-z0-9_]/.test(value), 'Unsafe cleanup identifier');
  return '`' + value + '`';
}
function literal(value) { return "'" + String(value).replace(/\\/g, '\\\\').replace(/'/g, "''") + "'"; }
function signature(rows) {
  return createHash('sha256').update(rows.map(row => row.map(value => value === null ? '-' : Buffer.from(String(value)).toString('hex').toUpperCase()).join(':')).join('|')).digest('hex');
}
function catalogCheck(fields, from, order, rows) {
  const serialized = `CONCAT_WS(':',${fields.map(field => `IFNULL(HEX(CAST(${field} AS CHAR CHARACTER SET utf8mb4)),'-')`).join(',')})`;
  return {sql: `SELECT SHA2(COALESCE(GROUP_CONCAT(${serialized} ORDER BY ${order} SEPARATOR '|'),''),256) FROM ${from}`,
    expected: signature(rows)};
}
function capability(sql, schema, owned, tables, foreign = []) {
  const writes = new Set(owned);
  tables = new Set(tables);
  for (const [table, , parent] of foreign) if (writes.has(table) || writes.has(parent)) {tables.add(table); tables.add(parent);}
  for (const table of tables) {
    identifier(table);
    const rows = schema.filter(row => row[0] === table);
    assert(rows.length && rows.every(row => row.length === 10 && row[9] === 'BASE TABLE'), 'Unknown cleanup table definition');
    const engine = rows[0][3];
    assert(rows.every(row => row[3] === engine), 'Inconsistent cleanup engine');
    assert(engine === 'InnoDB' || (!writes.has(table) && table === 'formRourke2009' && engine === 'Aria'),
      'Unsupported cleanup table engine');
  }
  const settings = sql.rows('SELECT @@session.innodb_table_locks,@@global.wsrep_on,@@session.wsrep_on');
  assert.deepEqual(settings, [['1','0','0']], 'Unsupported cleanup locking configuration');
  const names = owned.map(literal).join(',');
  assert.equal(sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN (${names})`), '0', 'Owned cleanup triggers are unreviewed');
  assert.equal(sql.value(`SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IN (${names}) AND DELETE_RULE NOT IN ('RESTRICT','NO ACTION')`), '0', 'Owned cleanup cascades are unreviewed');
}
function build({schema, foreign, owned, tables, checks, deletes}) {
  assert(owned.length && new Set(owned).size === owned.length && deletes.length === owned.length, 'Incomplete owned delete plan');
  const locked = new Set(tables);
  // Explicitly include both sides of foreign keys touching an owned parent. Parent
  // checks below still refuse cascade/trigger side effects before any DELETE.
  for (const [table, , parent] of foreign) if (owned.includes(table) || owned.includes(parent)) {locked.add(table); locked.add(parent);}
  for (const table of owned) assert(locked.has(table), 'Owned table omitted from lock plan');
  for (const table of locked) {
    const definition = schema.find(row => row[0] === table);
    assert(definition && definition.length === 10 && definition[9] === 'BASE TABLE', 'Missing locked table');
    assert(definition[3] === 'InnoDB' || (!owned.includes(table) && table === 'formRourke2009' && definition[3] === 'Aria'), 'Unsupported locked engine');
  }
  for (const check of checks) {
    assert(locked.has(check.table) && typeof check.where === 'string' && /^\d+$/.test(String(check.count)), 'Invalid cleanup gate');
  }
  for (const item of deletes) assert(owned.includes(item.table) && item.where && item.expected === 1, 'Invalid owned deletion');
  assert(new Set(deletes.map(item => item.table)).size === owned.length, 'Repeated/missing owned deletion');
  const catalogs = [catalogCheck(SCHEMA_FIELDS, SCHEMA_FROM, 'BINARY c.TABLE_NAME,c.ORDINAL_POSITION', schema),
    catalogCheck(FOREIGN_FIELDS, FOREIGN_FROM, 'BINARY TABLE_NAME,BINARY COLUMN_NAME,BINARY REFERENCED_TABLE_NAME,BINARY REFERENCED_COLUMN_NAME', foreign)];
  const names = owned.map(literal).join(',');
  const prefix = [
    'SET SESSION autocommit=0', 'SET SESSION group_concat_max_len=16777216',
    'SET SESSION lock_wait_timeout=5', 'SET SESSION innodb_lock_wait_timeout=5',
    'SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED',
    // These locks are OUTSIDE the compound block: LOCK TABLES is not permitted
    // inside stored programs. No START TRANSACTION follows this statement.
    'LOCK TABLES ' + [...locked].sort().map(table => identifier(table) + (owned.includes(table) ? ' WRITE' : ' READ')).join(', ') + ' NOWAIT',
  ];
  const block = [
    'BEGIN NOT ATOMIC',
    'DECLARE cleanup_count BIGINT DEFAULT 0;', 'DECLARE cleanup_catalog CHAR(64);',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;',
    // RESIGNAL of class 01 remains a warning: mysql may exit zero and execute
    // the final SELECT 1 after rollback. Promote warnings to a fatal error so
    // the completion receipt is unreachable for every rolled-back operation.
    "DECLARE EXIT HANDLER FOR SQLWARNING BEGIN ROLLBACK; SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup warning rejected'; END;",
    "IF @@session.innodb_table_locks<>1 OR @@global.wsrep_on<>0 OR @@session.wsrep_on<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup locking configuration changed'; END IF;",
  ];
  for (const catalog of catalogs) {
    block.push(catalog.sql.replace(' FROM ', ' INTO cleanup_catalog FROM ') + ';');
    block.push(`IF cleanup_catalog IS NULL OR BINARY cleanup_catalog<>${literal(catalog.expected)} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup catalog changed'; END IF;`);
  }
  for (const [from, condition] of [
    ['information_schema.TRIGGERS', `TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE IN (${names})`],
    ['information_schema.REFERENTIAL_CONSTRAINTS', `CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IN (${names}) AND DELETE_RULE NOT IN ('RESTRICT','NO ACTION')`],
  ]) {
    block.push(`SELECT COUNT(*) INTO cleanup_count FROM ${from} WHERE ${condition};`);
    block.push("IF cleanup_count<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup side effect rejected'; END IF;");
  }
  for (const check of checks) {
    block.push(`SELECT COUNT(*) INTO cleanup_count FROM ${identifier(check.table)} WHERE ${check.where};`);
    block.push(`IF cleanup_count<>${check.count} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup ownership or reference changed'; END IF;`);
  }
  for (const item of deletes) {
    block.push(`DELETE FROM ${identifier(item.table)} WHERE ${item.where};`);
    block.push("IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cleanup deletion was not exact'; END IF;");
  }
  block.push('COMMIT;', 'END');
  // mysql -e supports client DELIMITER directives. A SQL error stops this client
  // without --force; handler rollback precedes connection close/unlock. A lost
  // COMMIT acknowledgement is UNKNOWN, never an invitation to replay deletion.
  return prefix.join(';\n') + ';\nDELIMITER //\n' + block.join('\n') + '//\nDELIMITER ;\nUNLOCK TABLES;\nSELECT 1;';
}
module.exports = {SCHEMA_SQL, FOREIGN_SQL, readSchema, readForeign, signature, capability, build, identifier};
