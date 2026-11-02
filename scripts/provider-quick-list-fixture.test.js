/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{validateRows}=require('./lib/provider-quick-list-fixture');
const options={provider:'999998',names:['native'],start:'2026-11-01 06:00:00',end:'2026-11-01 06:10:00'},before=[['1','999998','previous',null]],native=['2','999998','native','2026-11-01 06:05:00'];
test('quick-list restoration preserves unknown preexisting preferences and admits only one native selection from this read window',()=>{validateRows(before,before,options);validateRows(before,[...before,native],options);validateRows(before,[['1','999998','previous','2026-11-01 06:05:00']],options);});
for(const [label,after] of [['deletion',[]],['replaced identity',[['3','999998','previous',null]]],['changed name',[['1','999998','different','NULL']]],['foreign owner',[...before,['2','other','native','2026-11-01 06:05:00']]],['unoffered catalog',[...before,['2','999998','foreign','2026-11-01 06:05:00']]],['old timestamp',[...before,['2','999998','native','2020-01-01 00:00:00']]],['extra selections',[...before,native,['3','999998','native','2026-11-01 06:05:00']]]])test('quick-list restoration retains unexpected '+label,()=>assert.throws(()=>validateRows(before,after,options)));

test('quick-list cleanup emits SQL NULL for an actual nullable baseline timestamp',()=>{
  const {providerQuickListFixture}=require('./lib/provider-quick-list-fixture');let reads=0,statement;
  const sql={rows(query){if(query.includes('DISTINCT'))return [['native']];return ++reads===2?[['1','999998','previous','2026-11-01 06:05:00']]:before;},value(){return '2026-11-01 06:05:00';},execute(query){statement=query;}};
  providerQuickListFixture(sql,'999998').cleanup();
  assert.match(statement,/SET lastUsed=NULL WHERE/);assert.doesNotMatch(statement,/lastUsed='null'/i);
});
