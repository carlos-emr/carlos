/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {nextEmailSenderId}=require('./lib/email-sender-fixture-id');
function runner({logical=[['emailLog','configId']],foreign=[['emailLog','configId','id']],allocation=[['4','8','5']],occupied='0'}={}) {
  return {queries:[],rows(query){this.queries.push(query);return query.includes('information_schema.COLUMNS')?logical:query.includes('KEY_COLUMN_USAGE')?foreign:allocation;},value(query){this.queries.push(query);return occupied;}};
}
test('sender allocation skips all orphan log identities without changing any row',()=>{
  const sql=runner();assert.equal(nextEmailSenderId(sql),'9');
  assert(sql.queries.every(query=>query.startsWith('SELECT ')));
  assert(sql.queries.at(-1).includes('emailConfig WHERE id=9')&&sql.queries.at(-1).includes('emailLog WHERE configId=9'));
});
test('sender allocation preserves a higher existing auto increment',()=>assert.equal(nextEmailSenderId(runner({allocation:[['4','8','100']]})),'100'));
test('empty tables allocate the first identity without a foreign key requirement',()=>assert.equal(nextEmailSenderId(runner({foreign:[],allocation:[['0','0','1']]})),'1'));
for(const [label,options,reason] of [
  ['unknown logical reference',{logical:[['emailLog','configId'],['futureLog','configId']]},/logical reference/],
  ['unknown foreign reference',{foreign:[['other','sender','id']]},/foreign reference/],
  ['missing allocation evidence',{allocation:[]},/allocation evidence/],
  ['unreadable allocation evidence',{allocation:[['4','not a count','5']]},/allocation evidence/],
  ['exhausted reference range',{allocation:[['4','2147483647','5']]},/range exhausted/],
  ['last usable identity exceeded',{allocation:[['4','2147483646','5']]},/range exhausted/],
  ['newly occupied identity',{occupied:'1'},/became referenced/],
])test('sender allocation refuses '+label,()=>assert.throws(()=>nextEmailSenderId(runner(options)),reason));
