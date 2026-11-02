/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Isolated MariaDB proof using the repository's actual baseline and lock migration.
// Run: CARLOS_DISPOSABLE_HOST=true STORED_PDF_TEST_CONTAINER=<owned-mariadb> node scripts/stored-pdf-routing-integration-check.js
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {randomBytes}=require('node:crypto'),{execFileSync}=require('node:child_process');
const pdf=require('./lib/stored-pdf-documents');
assert.equal(process.env.CARLOS_DISPOSABLE_HOST,'true','An explicitly disposable MariaDB container is required');
const container=process.env.STORED_PDF_TEST_CONTAINER;
assert(/^[A-Za-z0-9][A-Za-z0-9_.-]+$/.test(container||''),'Set the owned MariaDB container name');
const database='pdf_routing_test_'+randomBytes(8).toString('hex');
const env={...process.env};for(const key of ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'])delete env[key];
function query(text,selectDatabase=true) {
  return execFileSync('docker',['--host=unix:///var/run/docker.sock','exec','-i',container,
    'mariadb','--no-defaults','-uroot','-N','-B',...(selectDatabase?[database]:[])],
  {env,input:text,encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:60000,maxBuffer:16*1024*1024}).trim();
}
const sql={rows:text=>{const value=query(text);return value?value.split('\n').map(row=>row.split('\t')):[];},
  value:text=>query(text),execute:text=>{query(text);}};
const store=fs.mkdtempSync(path.join(os.tmpdir(),'pdf-routing-proof-'));
const marker='FAKE-PW'+randomBytes(8).toString('hex'),patient='42',provider='999998';
let created=false;
try {
  query(`CREATE DATABASE ${database}`,false);created=true;
  query(fs.readFileSync(path.join(__dirname,'../database/mysql/migration/common/V1__baseline_schema.sql'),'utf8'));
  query(fs.readFileSync(path.join(__dirname,'../database/mysql/migration/common/V1.0.21__serialize_missing_lab_routing_creation.sql'),'utf8'));
  query(`INSERT INTO provider(provider_no,lastUpdateDate) VALUES ('999998',NOW()),('999999',NOW());
    INSERT INTO incomingLabRules(provider_no,frwdProvider_no,archive) VALUES ('999998','999999','0');
    INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('999998',9000,'DOC');
    INSERT INTO providerLabRoutingLock(lab_no) VALUES (9000);`);
  const docs=pdf.ownedPdfDocuments(store,marker,[{key:'routing'}]);
  pdf.seedOwnedPdfDocuments({sql,store,patient,provider,docs});
  const id=docs[0].id,files=docs.map(doc=>doc.file),owned={sql,marker,patient,docs,files};
  assert(BigInt(id)>9000n,'A stale route/lock must reserve its old identity');
  query(`INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('999998',${id},'DOC'),('999999',${id},'DOC');
    INSERT INTO providerLabRoutingLock(lab_no) VALUES (${id});`);
  const baseline=query(`SELECT * FROM document WHERE document_no=${id}`);
  query(`INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('123456',${id},'DOC')`);
  assert.throws(()=>pdf.removeOwnedPdfDocuments(owned),/unproven provider route/);
  assert.equal(query(`SELECT * FROM document WHERE document_no=${id}`),baseline);
  assert(fs.existsSync(files[0]));
  query(`DELETE FROM providerLabRouting WHERE provider_no='123456' AND lab_no=${id}`);
  for(const type of ["'HL7'",'NULL']) {
    query(`INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('999998',${id},${type})`);
    assert.throws(()=>pdf.removeOwnedPdfDocuments(owned),/unproven provider route/);
    assert.equal(query(`SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=${id}`),'1');
    query(`DELETE FROM providerLabRouting WHERE lab_no=${id} AND (lab_type='HL7' OR lab_type IS NULL)`);
  }
  query(`DELETE FROM providerLabRouting WHERE lab_no=${id}`);
  assert.throws(()=>pdf.removeOwnedPdfDocuments({...owned,docs:[{id}]}),/lock ownership is unproven/);
  query(`INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('999998',${id},'DOC'),('999999',${id},'DOC')`);
  let inserted=false;
  const interleaved={...sql,execute(text){
    if(!inserted){inserted=true;query(`INSERT INTO providerLabRouting(provider_no,lab_no,lab_type) VALUES ('123456',${id},'DOC')`);}
    query(text);
  }};
  assert.throws(()=>pdf.removeOwnedPdfDocuments({...owned,sql:interleaved}));
  assert.equal(query(`SELECT * FROM document WHERE document_no=${id}`),baseline,'The transactional recheck must retain the document');
  assert.equal(query(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${id}`),'3');
  assert(fs.existsSync(files[0]));
  query(`DELETE FROM providerLabRouting WHERE provider_no='123456' AND lab_no=${id}`);
  pdf.removeOwnedPdfDocuments(owned);pdf.assertOwnedPdfDocumentsRemoved(owned);
  assert.equal(query('SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=9000'),'1');
  assert.equal(query('SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=9000'),'1');
  console.log('PASS stored PDF routing: expected forwarding, stale lock preservation, foreign-route refusal, transactional interleaving and complete owned cleanup');
} finally {
  try {if(created)query(`DROP DATABASE ${database}`,false);}
  finally {fs.rmSync(store,{recursive:true,force:true});}
}
