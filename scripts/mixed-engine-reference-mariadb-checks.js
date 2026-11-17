#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const {randomBytes}=require('node:crypto');
const {execFileSync}=require('node:child_process');
const h=require('./lib/playwright-harness');
const mixed=require('./lib/mixed-engine-owned-cleanup');
const q=h.sqlString;

/** Real MariaDB proof in a nonce-owned database; application tables are untouched. */
async function main() {
  const token=randomBytes(16).toString('hex'),database='pw_mixed_'+token.slice(0,16),db=mixed.identifier(database);
  let admin,sql,claimed=false,guardRefusal=false;
  return h.runCheck({name:'mixed-engine-reference-mariadb',async run(){
    if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Requires disposable/exclusive MariaDB CREATE/DROP DATABASE privileges');
    const execute=(command,args,options)=>execFileSync(command,
      process.env.MARIADB_REFERENCE_SOCKET?[args[0],'--socket',process.env.MARIADB_REFERENCE_SOCKET,...args.slice(1)]:args,options);
    const config=h.readConfig();admin=h.createSqlRunner(config.mysql,{exec:execute});
    h.assert(admin.value(`SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=${q(database)}`)==='0','Private engine-test database already exists');
    claimed=true;console.log('  EVIDENCE private engine fixture database='+database);
    admin.execute(`CREATE DATABASE ${db}; CREATE TABLE ${db}.owned_identity(token VARCHAR(64) NOT NULL PRIMARY KEY) ENGINE=InnoDB;
      INSERT INTO ${db}.owned_identity VALUES(${q(token)})`);
    sql=h.createSqlRunner({...config.mysql,database},{exec(command,args,options){
      try{return execute(command,args,options);}catch(error){
        guardRefusal=/^ERROR 1644 \(45000\)(?: at line \d+)?: Cleanup ownership or reference changed\s*$/m.test(String(error.stderr||''));
        throw error;
      }
    }});
    sql.execute("CREATE TABLE demographic(demographic_no INT NOT NULL PRIMARY KEY,marker VARCHAR(64) NOT NULL) ENGINE=InnoDB; INSERT INTO demographic VALUES(2,'CONTROL')");
    console.log('  EVIDENCE MariaDB version/page-size='+sql.value("SELECT CONCAT(VERSION(),':',@@innodb_page_size)"));
    for(const engine of ['Aria','MyISAM']) {
      sql.execute(`CREATE TABLE formRourke2009(id INT NOT NULL PRIMARY KEY,demographic_no INT NOT NULL) ENGINE=${engine}; INSERT INTO formRourke2009 VALUES(2,2)`);
      h.assert(sql.value("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='formRourke2009'")===engine,'Server substituted the requested legacy engine');
      const schema=mixed.readSchema(sql),foreign=mixed.readForeign(sql),owned=['demographic'],tables=['demographic','formRourke2009'];
      mixed.capability(sql,schema,owned,tables,foreign);
      const build=marker=>mixed.build({schema,foreign,owned,tables,checks:[
        {table:'demographic',where:`demographic_no=1 AND marker=${q(marker)}`,count:1},
        {table:'demographic',where:"demographic_no=2 AND marker='CONTROL'",count:1},
        {table:'formRourke2009',where:'demographic_no=1',count:0},
      ],deletes:[{table:'demographic',where:`demographic_no=1 AND marker=${q(marker)}`,expected:1}]});
      const success=token+'_'+engine;
      sql.execute(`INSERT INTO demographic VALUES(1,${q(success)})`);
      const legacy=JSON.stringify(sql.rows('SELECT * FROM formRourke2009 ORDER BY id'));
      h.assert(sql.value(build(success))==='1','Empty-reference cleanup did not confirm one deletion');
      h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=1')==='0','Allowed cleanup retained its owned parent');
      h.assert(JSON.stringify(sql.rows('SELECT * FROM formRourke2009 ORDER BY id'))===legacy,'Allowed cleanup changed the read-only legacy table');
      console.log('  PASS mixed-engine-reference-mariadb: '+engine+' empty reference permits only the owned InnoDB parent deletion');
      const blocked=token+'_blocked';sql.execute(`INSERT INTO demographic VALUES(1,${q(blocked)}); INSERT INTO formRourke2009 VALUES(1,1)`);
      const before=JSON.stringify({parent:sql.rows('SELECT * FROM demographic ORDER BY demographic_no'),legacy:sql.rows('SELECT * FROM formRourke2009 ORDER BY id')});
      let refused=false;guardRefusal=false;
      try{sql.value(build(blocked));}catch(error){h.assert(guardRefusal,'Cleanup failed without the precise reference guard refusal');refused=true;}
      h.assert(refused,'A live legacy reference did not refuse parent deletion');
      h.assert(JSON.stringify({parent:sql.rows('SELECT * FROM demographic ORDER BY demographic_no'),legacy:sql.rows('SELECT * FROM formRourke2009 ORDER BY id')})===before,
        'Refused cleanup changed its parent, control or legacy rows');
      console.log('  PASS mixed-engine-reference-mariadb: '+engine+' live reference refuses without changing any row');
      sql.execute('DROP TABLE formRourke2009; DELETE FROM demographic WHERE demographic_no=1');
    }
  },async cleanup(){
    const failures=[];
    try {if(claimed){
      h.assert(admin.value(`SELECT token FROM ${db}.owned_identity`)===token,'Engine fixture ownership unconfirmed; retain '+database);
      const names=admin.rows(`SELECT TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=${q(database)}`);
      h.assert(names.every(([name,type])=>type==='BASE TABLE'&&['owned_identity','demographic','formRourke2009'].includes(name)),'Unexpected engine fixture object; retain '+database);
      admin.execute(`DROP DATABASE ${db}`);
      h.assert(admin.value(`SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=${q(database)}`)==='0','Private engine fixture database remains');
    }}catch(error){failures.push(error);}
    for(const runner of [sql,admin])try{if(runner)runner.dispose();}catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'Engine fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
  }});
}
if(require.main===module)main();
module.exports={main};
