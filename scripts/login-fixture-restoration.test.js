// Verify login-test cleanup against an actual SQL database.
'use strict';

const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, 'login-playwright-checks.js'), 'utf8');
const functions = source.slice(source.indexOf('function escapeSql('),
  source.indexOf('function setForcedResetBaseline('));
const executeSql = [
  'import sqlite3,sys',
  'db=sqlite3.connect(sys.argv[1])',
  'cursor=db.execute(sys.stdin.read())',
  'rows=cursor.fetchall()',
  'db.commit()',
  'print("\\n".join("\\t".join(str(value) for value in row) for row in rows),end="")',
].join('\n');

for (const nullable of [false, true]) {
  test('login fixture restores credentials and original update timestamps' + (nullable ? ' including nulls' : ''), () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-login-fixture-'));
    try {
      const sql = (query) => execFileSync('python3', ['-c', executeSql, path.join(dir, 'fixture.db')],
        { input: query, encoding: 'utf8' }).trim();
      sql('CREATE TABLE security (user_name TEXT, password TEXT, forcePasswordReset INTEGER, passwordUpdateDate TEXT, lastUpdateDate TEXT)');
      const date = nullable ? 'NULL' : "'2020-01-02 03:04:05'";
      sql("INSERT INTO security VALUES ('fixture', 'original-hash', 1, " + date + ', ' + date + ')');
      const before = sql('SELECT * FROM security');
      const context = vm.createContext({ sql, testUser: 'fixture', original: {} });
      vm.runInContext(functions, context);
      Object.assign(context.original, context.securityRow());
      sql("UPDATE security SET password='temporary-hash', forcePasswordReset=0, passwordUpdateDate='2026-09-27 23:00:00', lastUpdateDate='2026-09-27 23:00:00'");
      context.restoreOriginal();
      assert.equal(sql('SELECT * FROM security'), before);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
}
