/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {parseProperties}=require('./lib/java-properties');
const parse=text=>parseProperties(Buffer.from(text,'latin1'));
test('Java stream properties support whitespace, escaped separators and last duplicate key',()=>{
  const values=parse(' # comment\r\n! ignored\nkey : first\nkey=last\na\\ b\\:c\\=d : value\\tend\nempty\n');
  assert.deepEqual([...values],[['key','last'],['a b:c=d','value\tend'],['empty','']]);
});
test('Java continuations fold only odd trailing backslashes and retain continued comments',()=>{
  const values=parse('notice=First\\\r\n  second\\\n\t#third\neven=two\\\\\nlast=end\\');
  assert.equal(values.get('notice'),'Firstsecond#third');assert.equal(values.get('even'),'two\\');assert.equal(values.get('last'),'end');
});
test('Java stream decoding preserves Latin-1 and decodes Unicode and standard escapes',()=>{
  assert.equal(parse('n=caf\xe9 \\u03bb\\n\\r\\f\\t\\z').get('n'),'café λ\n\r\f\tz');
});
for(const escape of ['\\u','\\u123','\\u12xz'])test('malformed Java Unicode escape is fatal: '+escape,()=>{
  assert.throws(()=>parse('n='+escape),/Malformed.*Unicode/);
});
test('installed notice uses installed defaults plus actual operator overrides, independently of checkout',t=>{
  const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
  const {expectedNotice}=require('./lab-report-pagination-playwright-checks');
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'installed-notice-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  fs.mkdirSync(path.join(root,'WEB-INF/classes'),{recursive:true});
  fs.writeFileSync(path.join(root,'WEB-INF/classes/carlos.properties'),'confidentiality_statement.v1=Installed default\n');
  const override=path.join(root,'operator.properties');fs.writeFileSync(override,'confidentiality_statement.v1=Operator \\u00e9\\\n   notice\n');
  const env={CARLOS_INSTALLED_WEBAPP_DIR:root,CARLOS_INSTALLED_PROPERTIES_FILE:override};
  assert.equal(expectedNotice(env),'Operator énotice');
  assert.equal(expectedNotice({...env,LAB_PDF_EXPECTED_NOTICE:'Explicit notice'}),'Explicit notice');
  assert.throws(()=>expectedNotice({...env,LAB_PDF_EXPECTED_NOTICE:''}),/not configured/);
  fs.unlinkSync(override);assert.throws(()=>expectedNotice(env),/ENOENT/);
});
