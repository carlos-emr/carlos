/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),vm=require('node:vm');
test('reinitializing appointment defaults reuses the tracked private directory and cleanup permits a fresh retry',t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'appt-defaults-unit-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const source=fs.readFileSync(path.join(__dirname,'appointment-lifecycle-playwright-checks.js'),'utf8');
 const context={fs,path,os:{tmpdir:()=>root},mysqlDefaults:null,mysqlPassword:'synthetic-only',encodeOptionFileValue:value=>value};
 vm.createContext(context);vm.runInContext(source.slice(source.indexOf('function initMysqlDefaults()'),source.indexOf('function sql(query)')),context);
 context.initMysqlDefaults();const first=context.mysqlDefaults;context.initMysqlDefaults();assert.equal(context.mysqlDefaults,first);assert.equal(fs.readdirSync(root).length,1);assert.equal(fs.statSync(first.file).mode&0o777,0o600);
 context.cleanupMysqlDefaults();assert.equal(fs.readdirSync(root).length,0);context.initMysqlDefaults();assert.notEqual(context.mysqlDefaults.dir,first.dir);context.cleanupMysqlDefaults();assert.equal(fs.readdirSync(root).length,0);
});
