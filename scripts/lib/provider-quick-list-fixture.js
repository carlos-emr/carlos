/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
function validateRows(before,after,{provider,names,start,end}) {
  const old=new Map(before.map(row=>[row[0],row])),current=new Map(after.map(row=>[row[0],row]));
  h.assert(old.size===before.length&&current.size===after.length,'Ambiguous quick-list preference identities');
  for(const [id,row] of old)h.assert(current.has(id)&&same(row.slice(0,3),current.get(id).slice(0,3)),'A preexisting quick-list preference disappeared or changed owner/name');
  let added=0;
  for(const row of after){const original=old.get(row[0]);h.assert(row[1]===provider,'Unexpected quick-list preference owner');
    if(!original){added++;h.assert(names.includes(row[2]),'New quick-list preference is not a native offered catalog name');}
    if(!original||row[3]!==original[3])h.assert(/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/.test(row[3])&&row[3]>=start&&row[3]<=end,'Quick-list last-used value is not from the owned read window');
  }
  h.assert(added<=1,'Unexpected extra quick-list preferences; retain them');
}
function providerQuickListFixture(sql,provider) {
  h.assert(/^[1-9]\d*$/.test(provider),'Quick-list fixture needs the authenticated provider');
  const q=h.sqlString,scope=`BINARY providerNo=BINARY ${q(provider)}`,read=()=>sql.rows(`SELECT id,providerNo,quickListName,lastUsed FROM quickListUser WHERE ${scope} ORDER BY id`),before=read(),names=sql.rows('SELECT DISTINCT LEFT(quickListName,10) FROM quickList').map(row=>row[0]),start=sql.value('SELECT NOW()');let restored=false;
  const date=value=>value===null||value==='NULL'?'NULL':q(value),where=row=>`id=${q(row[0])} AND ${scope} AND BINARY quickListName=BINARY ${q(row[2])} AND lastUsed<=>${date(row[3])}`;
  return {cleanup(){if(restored)return;const after=read();validateRows(before,after,{provider,names,start,end:sql.value('SELECT NOW()')});
    if(!same(before,after)){
      const statements=['START TRANSACTION','CREATE TEMPORARY TABLE carlos_quick_restore_guard (ok INT NOT NULL CHECK(ok=1))',`UPDATE quickListUser SET id=id WHERE ${scope}`,`INSERT INTO carlos_quick_restore_guard VALUES(IF((SELECT COUNT(*) FROM quickListUser WHERE ${scope})=${after.length},1,0))`];
      for(const row of after)statements.push(`INSERT INTO carlos_quick_restore_guard VALUES(IF((SELECT COUNT(*) FROM quickListUser WHERE ${where(row)})=1,1,0))`);
      for(const row of after){const original=before.find(old=>old[0]===row[0]);if(!original)statements.push(`DELETE FROM quickListUser WHERE ${where(row)}`);else if(!same(original,row))statements.push(`UPDATE quickListUser SET lastUsed=${date(original[3])} WHERE ${where(row)}`);}
      statements.push('COMMIT');sql.execute(statements.join(';'));h.assert(same(read(),before),'Quick-list preference did not restore exactly');
    }restored=true;
  }};
}
module.exports={providerQuickListFixture,validateRows};
