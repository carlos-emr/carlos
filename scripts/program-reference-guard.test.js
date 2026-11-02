/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {programReferenceGuard}=require('./lib/program-reference-guard');
test('program guard refuses an orphan reference before allocation and a later foreign reference before deletion',()=>{
  let foreign='1';const sql={rows:()=>[['provider_default_program','program_id'],['casemgmt_tmpsave','program_id']],value:query=>query.includes('casemgmt_tmpsave')?foreign:'0'};
  const guard=programReferenceGuard(sql,'900001');assert.throws(()=>guard.assertUnused(),/casemgmt_tmpsave/);
  foreign='0';guard.assertUnused();foreign='1';assert.throws(()=>guard.assertUnused(),/casemgmt_tmpsave/);
});
test('program guard refuses unsafe schema metadata without issuing the reference query',()=>{
  assert.throws(()=>programReferenceGuard({rows:()=>[['program;DELETE','program_id']],value:()=>assert.fail('must not query')},'900001'),/Unsafe/);
});
