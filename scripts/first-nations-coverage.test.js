/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'first-nations-encoding-playwright-checks.js'),'utf8');
const start=source.indexOf('function reportFirstNationsCoverage('),end=source.indexOf('\n(async () => {',start);
function report(master,community){const lines=[],process={};const c={console:{log:s=>lines.push(s)},process,EXIT_SKIP:2};vm.runInNewContext(source.slice(start,end)+'\nthis.report=reportFirstNationsCoverage;',c);return{full:c.report(master,community),lines,exitCode:process.exitCode};}
test('complete gate/master/community rendering permits full success',()=>{const r=report(true,true);assert.equal(r.full,true);assert.deepEqual(r.lines,[]);assert.equal(r.exitCode,undefined);});
test('disabled master-record branch explicitly reports incomplete SKIP instead of full PASS',()=>{const r=report(false,true);assert.equal(r.full,false);assert.equal(r.exitCode,2);assert.match(r.lines[0],/^SKIP .*patient master record.*FIRST_NATIONS_MODULE=true/);assert.ok(r.lines.every(s=>!/^PASS /.test(s)));});
test('missing community option is also explicit incomplete coverage',()=>{const r=report(true,false);assert.equal(r.full,false);assert.equal(r.exitCode,2);assert.match(r.lines[0],/owned visible lookup option/);});
test('both omissions are named without changing completed gate assertions',()=>{const r=report(false,false);assert.equal(r.exitCode,2);assert.match(r.lines[0],/patient master record.*encoded community option.*Completed gate-route assertions/);});
