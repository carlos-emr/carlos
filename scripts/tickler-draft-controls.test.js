/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const add=fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp'),'utf8');
test('draft POST fields cannot shadow visible date and priority controls',()=>{
 assert.equal([...add.matchAll(/<(?:input|select)\b[^>]*\bname="xml_appointment_date"/g)].length,1);
 assert.equal([...add.matchAll(/<(?:input|select)\b[^>]*\bname="priority"/g)].length,1);
 assert.match(add, /name="ticklerDraftDate"/);
 assert.match(add, /name="ticklerDraftPriority"/);
});
