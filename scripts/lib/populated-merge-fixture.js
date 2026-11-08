/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),crypto=require('node:crypto');
const assert=require('node:assert/strict'),h=require('./playwright-harness');
const {ownedPdfDocuments,seedOwnedPdfDocuments,removeOwnedPdfDocuments,assertOwnedPdfDocumentsRemoved,directory,textPdf}=require('./stored-pdf-documents');
const {newOwnedMarker,createOwnedPatient,removeOwnedPatient}=require('./owned-patient');
const {readRegular,retireOwnedFile}=require('./owned-file-retirement');
const {readPatientState}=require('../echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
/** Reserve above even dangling references, without altering any preexisting row. */
function nextIdentity(sql,table,key,normalised,extra=[]){
  const refs=sql.rows(`SELECT c.TABLE_NAME,c.COLUMN_NAME FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
    ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME WHERE c.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE'
    AND LOWER(REPLACE(c.COLUMN_NAME,'_',''))=${q(normalised)}
    UNION SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE()
    AND REFERENCED_TABLE_NAME=${q(table)} AND REFERENCED_COLUMN_NAME=${q(key)}`);
  refs.push([table,key]);let maximum=0;
  for(const [name,column,condition='1'] of [...refs,...extra]){
    h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)&&/^[A-Za-z_][A-Za-z0-9_]*$/.test(column),'Unsafe fixture reference identifier');
    const where=`(${condition}) AND \`${column}\` IS NOT NULL AND BINARY CAST(\`${column}\` AS CHAR)<>'-1'`;
    h.assert(sql.value(`SELECT COUNT(*) FROM \`${name}\` WHERE ${where} AND CAST(\`${column}\` AS CHAR) NOT REGEXP '^[0-9]+$'`)==='0','Unfamiliar '+normalised+' reference namespace');
    const value=Number(sql.value(`SELECT COALESCE(MAX(CAST(\`${column}\` AS UNSIGNED)),0) FROM \`${name}\` WHERE ${where}`));
    h.assert(Number.isSafeInteger(value)&&value>=0&&value<2147483600,'No safe owned clinical identity range');maximum=Math.max(maximum,value);
  }
  return maximum+1;
}
function populatedMergeFixture(s,{head,duplicate},lifecycle){
  const {sql,marker,provider}=s,store=directory('DOCUMENT_DIR','DOCUMENT_DIR','RX_FAX_DOCUMENT_DIR');
  const controlMarker=newOwnedMarker();let control,started=false;
  const fixtures=[{patient:head,marker,tag:'HEAD',weight:'61.2'},{patient:duplicate,marker,tag:'TAIL',weight:'82.4'}];
  s.cleanup(()=>{
    h.assert(!started||lifecycle.childrenRemoved,'Clinical cleanup did not finish; retain the populated control parent');
    control||=sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlMarker)}`);
    if(control)removeOwnedPatient(sql,control,controlMarker);
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(controlMarker)}`)==='0','Control namespace already exists');
  control=createOwnedPatient(sql,{marker:controlMarker,provider});fixtures.push({patient:control,marker:controlMarker,tag:'CONTROL',weight:'73.6'});
  for(const fixture of fixtures){
    fixture.label=marker+' '+fixture.tag;fixture.docMarker=marker+'-'+fixture.tag;
    fixture.docs=ownedPdfDocuments(store,fixture.docMarker,[{key:'history',pages:1}]);fixture.fileProofs=new Map();
    fixture.uuid=crypto.randomUUID();fixture.notes=[];
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc LIKE ${q(fixture.docMarker+'%')}`)==='0'
      &&fixture.docs.every(doc=>!fs.existsSync(doc.file)),'Populated document namespace already exists');
  }
  s.cleanup(()=>{
    if(!started)return;
    for(const fixture of fixtures){
      const {patient,marker:owner,docs,docMarker}=fixture;
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)} AND provider_no=${q(provider)}`)==='1','Populated patient ownership changed');
      const notes=`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND (note NOT LIKE ${q(fixture.label+'%')} OR provider_no<>${q(provider)})`)==='0','Foreign clinical note in owned fixture; retain it');
      sql.execute(`DELETE FROM hash_audit WHERE type='enc' AND id IN (${notes});DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient};DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
        DELETE FROM eChart WHERE demographicNo=${patient};DELETE FROM measurementsExt WHERE measurement_id IN(SELECT id FROM measurements WHERE demographicNo=${patient});
        DELETE FROM measurements WHERE demographicNo=${patient};DELETE FROM partial_date WHERE table_name=1 AND table_id IN(SELECT allergyid FROM allergies WHERE demographic_no=${patient});
        DELETE FROM allergies WHERE demographic_no=${patient};DELETE FROM drugs WHERE demographic_no=${patient};DELETE FROM prescription WHERE demographic_no=${patient}`);
      // Retire the exact original bytes atomically before the shared metadata
      // cleanup; that helper then observes the proven files as already absent.
      for(const doc of docs){const proof=fixture.fileProofs.get(doc.file);if(proof){
        const current=readRegular(doc.file);h.assert(!current||current.stat.dev===proof.dev&&current.stat.ino===proof.ino,'Owned PDF identity changed; retain it');
        retireOwnedFile(doc.file,bytes=>digest(bytes)===proof.hash);
      }}
      const owned={sql,marker:docMarker,patient,docs,files:docs.filter(doc=>!fixture.fileProofs.has(doc.file)).map(doc=>doc.file)};
      removeOwnedPdfDocuments(owned);assertOwnedPdfDocumentsRemoved({...owned,files:docs.map(doc=>doc.file)});
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
        +(SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`)==='0','Populated clinical rows remain');
    }
    lifecycle.childrenRemoved=true;
  });
  const program=sql.value(`SELECT program_id FROM provider_default_program WHERE provider_no=${q(provider)}`);
  h.assert(/^[1-9]\d*$/.test(program),'A unique native chart program is required');
  const role=sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${q(program)}`);
  h.assert(/^[1-9]\d*$/.test(role),'A unique native chart role is required');
  let noteId=nextIdentity(sql,'casemgmt_note','note_id','noteid',[['hash_audit','id',"type='enc'"]]);
  let drugId=nextIdentity(sql,'drugs','drugid','drugid'),scriptId=nextIdentity(sql,'prescription','script_no','scriptno');
  started=true;lifecycle.childrenRemoved=false;
  for(const fixture of fixtures){
    const {patient,label}=fixture;
    // Explicit SQL fixtures are never reported as tested signing/entry behavior.
    for(let revision=1;revision<=2;revision++){
      const id=String(noteId++),text=label+' signed history revision '+revision+(revision===2?'\n'+label+' prior revision retained; new clinical addendum.':'');
      fixture.notes.push({id,text,revision});
      const history=fixture.notes.map(note=>note.text+'[[2026-11-0'+note.revision+' 08:30:00]]').reverse().join('---------History Record---------');
      sql.execute(`INSERT INTO casemgmt_note(note_id,update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,program_no,reporter_caisi_role,reporter_program_team,encounter_type,history,uuid,archived,position)
        VALUES(${id},'2026-11-0${revision} 08:30:00','2026-11-01 08:30:00',${patient},${q(provider)},${q(text)},1,${q(provider)},${q(program)},${q(role)},0,'face to face encounter with client',${q(history)},${q(fixture.uuid)},0,0);
        INSERT INTO hash_audit(signature,id,type,algorithm) VALUES(${q(digest(Buffer.from(text,'utf8')))},${id},'enc','SHA-256')`);
    }
    fixture.drugId=String(drugId++);fixture.scriptId=String(scriptId++);fixture.drug=label+' medication';fixture.allergy=label+' allergy';fixture.reaction=label+' rash';
    sql.execute(`INSERT INTO prescription(script_no,provider_no,demographic_no,date_prescribed,date_printed,lastUpdateDate) VALUES(${fixture.scriptId},${q(provider)},${patient},CURDATE(),CURDATE(),NOW());
      INSERT INTO drugs(drugid,script_no,provider_no,demographic_no,rx_date,end_date,written_date,customName,BN,special,archived,archived_reason,create_date,position,lastUpdateDate,
        dispenseInternal,quantity,duration,durunit,freqcode,long_term) VALUES(${fixture.drugId},${fixture.scriptId},${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),
        ${q(fixture.drug)},${q(fixture.drug)},${q(fixture.drug+'\nTake 1 tablet daily')},0,'',NOW(),1,NOW(),0,'30','30','D','OD',1);
      INSERT INTO allergies(demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,severity_of_reaction,onset_of_reaction,archived,position,lastUpdateDate,providerNo)
        VALUES(${patient},CURDATE(),'2026-01-02',${q(fixture.allergy)},0,${q(fixture.reaction)},1,'2','1',0,0,NOW(),${q(provider)});
      INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered,appointmentNo)
        VALUES('WT',${patient},${q(provider)},${q(fixture.weight)},'in kg',${q(label)},'2026-01-02 09:00:00',NOW(),0)`);
    seedOwnedPdfDocuments({sql,store,patient,provider,docs:fixture.docs,onFileCreated:(file,stat)=>{
      const doc=fixture.docs.find(doc=>doc.file===file);fixture.fileProofs.set(file,{dev:stat.dev,ino:stat.ino,hash:digest(textPdf(doc.label,doc.pages))});
    }});
    for(const doc of fixture.docs){const value=readRegular(doc.file);h.assert(value&&digest(value.bytes)===fixture.fileProofs.get(doc.file).hash,'Owned PDF disappeared or changed during setup');}
  }
  function snapshot(fixture){
    const {patient,docs}=fixture,notes=`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`,docIds=docs.map(doc=>doc.id).join(',');
    const clinical=readPatientState(sql,patient);
    // Opening a chart creates its legacy eChart infrastructure row; it is not
    // clinical content. Drafts, signed histories and every other captured row
    // remain exact, including the unrelated populated control.
    delete clinical.echart;
    const state={clinical,notes:sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`),
      hashes:sql.rows(`SELECT * FROM hash_audit WHERE type='enc' AND id IN(${notes}) ORDER BY pkid`),
      noteLinks:sql.rows(`SELECT * FROM casemgmt_note_link WHERE note_id IN(${notes}) ORDER BY id`),noteExtensions:sql.rows(`SELECT * FROM casemgmt_note_ext WHERE note_id IN(${notes}) ORDER BY id`),
      noteIssues:sql.rows(`SELECT * FROM casemgmt_issue_notes WHERE note_id IN(${notes}) ORDER BY note_id,id`),
      drugs:sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`),prescriptions:sql.rows(`SELECT * FROM prescription WHERE demographic_no=${patient} ORDER BY script_no`),
      allergies:sql.rows(`SELECT * FROM allergies WHERE demographic_no=${patient} ORDER BY allergyid`),
      partialDates:sql.rows(`SELECT * FROM partial_date WHERE table_name=1 AND table_id IN(SELECT allergyid FROM allergies WHERE demographic_no=${patient}) ORDER BY id`),
      measurements:sql.rows(`SELECT * FROM measurements WHERE demographicNo=${patient} ORDER BY id`),
      measurementExtensions:sql.rows(`SELECT * FROM measurementsExt WHERE measurement_id IN(SELECT id FROM measurements WHERE demographicNo=${patient}) ORDER BY id`),
      documents:sql.rows(`SELECT * FROM document WHERE document_no IN(${docIds}) ORDER BY document_no`),
      documentLinks:sql.rows(`SELECT * FROM ctl_document WHERE document_no IN(${docIds}) ORDER BY module,module_id,document_no`),
      files:docs.map(doc=>[doc.filename,digest(readRegular(doc.file).bytes)])};
    for(const note of fixture.notes){const rows=sql.rows(`SELECT n.note,a.signature,a.algorithm,n.signed,n.signing_provider_no,n.uuid FROM casemgmt_note n JOIN hash_audit a ON a.id=n.note_id AND a.type='enc' WHERE n.note_id=${note.id} AND n.demographic_no=${patient}`);
      assert.deepEqual(rows,[[note.text,digest(Buffer.from(note.text,'utf8')),'SHA-256','1',provider,fixture.uuid]],'Signed clinical revision/ownership/hash changed');}
    return state;
  }
  for(const fixture of fixtures)fixture.before=snapshot(fixture);
  return {fixtures,head:fixtures[0],tail:fixtures[1],control:fixtures[2],snapshot,
    assertPreserved(label){for(const fixture of fixtures)assert.deepEqual(snapshot(fixture),fixture.before,label+': '+fixture.tag+' clinical history or file changed');}};
}
module.exports={nextIdentity,populatedMergeFixture};
