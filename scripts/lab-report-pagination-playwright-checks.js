#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5733: sixty independently enumerated results and long per-result comments.
// SQL creates the synthetic received reports; the real Inbox and Print path
// produces the PDF. Every result is checked by text, position AND rendered ink.
const fs=require('node:fs');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const x=require('./lib/export-content-helpers');
const graph=require('./lib/pdf-graph');
const q=h.sqlString;
const SERVICE='CARLOS5800PAGES';

/** Strict Poppler P5 raster, at 72 dpi so PDF points and pixels correspond. */
function raster(bytes,page) {
  const image=execFileSync('pdftoppm',['-gray','-r','72','-f',String(page),'-l',String(page),'-singlefile','-'],
    {input:bytes,timeout:30000,maxBuffer:16*1024*1024});
  const header=/^P5\s+(\d+)\s+(\d+)\s+255\s/.exec(image.subarray(0,100).toString('latin1'));
  h.assert(header,'Rendered lab page is not a grayscale raster');
  const width=Number(header[1]),height=Number(header[2]),pixels=image.subarray(header[0].length);
  h.assert(width>0&&height>0&&width*height===pixels.length,'Rendered lab raster dimensions or bytes are incomplete');
  return {width,height,pixels};
}

/** At least one actual dark pixel must lie within this extracted word box. */
function ink(image,page,word) {
  const sx=image.width/page.width,sy=image.height/page.height;
  for(let y=Math.max(0,Math.floor(word.yMin*sy));y<Math.min(image.height,Math.ceil(word.yMax*sy));y++)
    for(let x=Math.max(0,Math.floor(word.xMin*sx));x<Math.min(image.width,Math.ceil(word.xMax*sx));x++)
      if(image.pixels[y*image.width+x]<200)return true;
  return false;
}

/** A complete clinical row/comment oracle, independent of the PDF producer. */
function verify(pages,expected,notice,marker,images) {
  h.assert(pages.length>=3&&pages.length<=20&&expected.length===60,'The owned long report did not reach its required multi-page selection');
  const words=pages.flatMap((page,index)=>page.words.map(word=>({...word,page:index})));
  const at=(left,right)=>left.page-right.page||left.yMin-right.yMin||left.xMin-right.xMin;
  const unique=text=>{const found=words.filter(word=>word.text===text);h.assert(found.length===1,'Missing or duplicated clinical token: '+text);return found[0];};
  let previous;
  for(const result of expected) {
    const name=unique(result.name);h.assert(!previous||at(previous,name)<0,'Clinical observations are out of order');
    const page=pages[name.page];
    const row=page.words.filter(word=>Math.abs((word.yMin+word.yMax-name.yMin-name.yMax)/2)<2);
    const clinical=[name];
    for(const text of [result.value,result.units,result.range,result.flag]) {
      const found=row.filter(word=>word.text===text);h.assert(found.length===1,'Wrong clinical value/unit/range/flag for '+result.name);clinical.push({...found[0],page:name.page});
    }
    let last=name;
    for(const text of result.comment.split(' ')) {
      const word=unique(text);h.assert(at(last,word)<0,'A wrapped comment changed order for '+result.name);last=word;clinical.push(word);
    }
    // No following observation may start before the preceding complete comment.
    previous=last;
    for(const word of clinical) {
      const selected=pages[word.page];
      h.assert(word.xMin>=17.9&&word.xMax<=selected.width-17.9&&word.yMin>=17.9&&word.yMax<=selected.height-17.9,'Clinical content is clipped outside printable bounds');
      h.assert(ink(images[word.page],selected,word),'Clinical text has no visible rendered ink: '+word.text);
      h.assert(!selected.words.some(other=>other!==word&&!(other.text===word.text&&other.xMin===word.xMin&&other.yMin===word.yMin)
        &&Math.min(other.xMax,word.xMax)-Math.max(other.xMin,word.xMin)>0.5
        &&Math.min(other.yMax,word.yMax)-Math.max(other.yMin,word.yMin)>0.5),'Clinical text overlaps another printed row or legal text');
    }
  }
  const noticeWords=notice.split(' ');
  for(const [index,page] of pages.entries()) {
    // Poppler's reading-order extraction dehyphenates the wrapped first-page
    // name. Raw content order retains the actually printed hyphen and both
    // fragments; require that exact identity after whitespace normalization.
    h.assert(x.squash(page.rawText).includes(x.squash(marker)),'A lab page lost its patient identity');
    const start=page.words.findIndex((word,i)=>word.text===noticeWords[0]
      &&page.words.slice(i,i+noticeWords.length).map(item=>item.text).join(' ')===notice);
    h.assert(start>=0,'A lab page omits the complete confidentiality notice');
    for(const word of page.words.slice(start,start+noticeWords.length)) {
      h.assert(word.xMin>=17.9&&word.xMax<=page.width-17.9&&word.yMin>=17.9&&word.yMax<=page.height-17.9,'Confidentiality notice is outside printable bounds');
      h.assert(!page.words.some(other=>other!==word&&Math.min(other.xMax,word.xMax)-Math.max(other.xMin,word.xMin)>0.5
        &&Math.min(other.yMax,word.yMax)-Math.max(other.yMin,word.yMin)>0.5),'Confidentiality notice overlaps other printed content');
    }
    h.assert(page.words.map(word=>word.text).join(' ').includes(`Page ${index+1} of ${pages.length}`),'Lab page numbering is incomplete or incorrect');
  }
}

async function workflow(s) {
  const {sql,patient,provider,marker}=s;
  const scratch=x.scratchDir();s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  const expected=Array.from({length:60},(_,index)=>{
    const n=String(index+1).padStart(4,'0');
    return {name:'PWR'+n,value:String(1001+index)+'.25',units:'mmol/L',range:'0-'+String(2001+index),flag:['N','H','L'][index%3],
      comment:['START'+n,...Array.from({length:32},(_,part)=>'C'+n+'_'+String(part+1).padStart(2,'0')),'END'+n].join(' ')};
  });
  const accession='PAGES-'+marker.slice(-10),controlAccession='CONTROL-'+marker.slice(-10);
  const stamp=new Date().toISOString().replace(/[-:T]/g,'').slice(0,14);
  const reports=[];
  s.cleanup(()=>{
    const failures=[];
    for(const report of reports)try {
      const ids=report.id?[report.id]:sql.rows(`SELECT lab_id FROM hl7TextMessage WHERE serviceName=${q(SERVICE)} AND message=${q(report.encoded)}`).flat();
      h.assert(ids.length<=1&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous owned pagination lab');
      for(const id of ids) {
        h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${id} AND serviceName=${q(SERVICE)} AND message=${q(report.encoded)}`)==='1','Pagination lab ownership changed; retain its records');
        sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${id} AND lab_type='HL7'; DELETE FROM providerLabRouting WHERE lab_no=${id} AND lab_type='HL7'; DELETE FROM hl7TextInfo WHERE lab_no=${id}; DELETE FROM hl7TextMessage WHERE lab_id=${id} AND serviceName=${q(SERVICE)} AND message=${q(report.encoded)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${id} AND lab_type='HL7')+(SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${id} AND lab_type='HL7')+(SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${id})+(SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${id})`)==='0','Owned pagination lab rows remain');
      }
    }catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'Pagination fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
  });
  function seed(number,results) {
    const lines=[`MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|${number}|P|2.3|||ER|AL`,
      `PID||9999999999|9999999999||${marker}^Workflow||19800102|F`,
      `ORC|RE||${number}|||||||||99999^FAKE-ORDERING^DOC`,
      `OBR|1||${number}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`];
    results.forEach((result,index)=>{lines.push(`OBX|${index+1}|NM|PW${index+1}^${result.name}||${result.value}|${result.units}|${result.range}|${result.flag}|||F|||${stamp}`);
      lines.push(`NTE|${index+1}|L|${result.comment}`);});
    const report={encoded:Buffer.from(lines.join('\r'),'utf8').toString('base64')};reports.push(report);
    report.id=sql.value(`INSERT INTO hl7TextMessage(fileUploadCheck_id,message,type,serviceName,created) VALUES(0,${q(report.encoded)},'PATHL7',${q(SERVICE)},NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(report.id),'Owned pagination lab did not insert');
    sql.execute(`INSERT INTO hl7TextInfo(lab_no,sex,health_no,result_status,final_result_count,obr_date,priority,requesting_client,discipline,last_name,first_name,report_status,accessionNum,filler_order_num,sending_facility)
      VALUES(${report.id},'F','9999999999','A',${results.length},NOW(),'R','FAKE-ORDERING, DOC','CHEM',${q(marker)},'Workflow','F',${q(number)},${q(number)},'CW');
      INSERT INTO providerLabRouting(provider_no,lab_no,status,lab_type) VALUES(${q(provider)},${report.id},'N','HL7');
      INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${report.id},'HL7',NOW())`);
    return report;
  }
  const report=seed(accession,expected),control=seed(controlAccession,[{name:'PWRCONTROL',value:'88776655',units:'mmol/L',range:'0-1',flag:'H',comment:'CONTROLONLY'}]);
  const sourceState=()=>JSON.stringify({message:sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id=${report.id}`),
    info:sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no=${report.id}`),patients:sql.rows(`SELECT * FROM patientLabRouting WHERE lab_no=${report.id} AND lab_type='HL7' ORDER BY id`)});
  const beforeSource=sourceState();
  const controlState=()=>JSON.stringify({message:sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id=${control.id}`),info:sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no=${control.id}`),
    patients:sql.rows(`SELECT * FROM patientLabRouting WHERE lab_no=${control.id} AND lab_type='HL7' ORDER BY id`),providers:sql.rows(`SELECT * FROM providerLabRouting WHERE lab_no=${control.id} AND lab_type='HL7' ORDER BY id`)});
  const beforeControl=controlState();
  let lab,bytes,pages,images;
  await s.step('the exact owned long report opens from the native Inbox and its Print control returns the PDF bytes',async()=>{
    const {page:inbox}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('a#inboxLink').first(),{context:s.context,recorder:s.recorder,label:'pagination-inbox',timeout:30000});
    const row=inbox.locator(`tr[data-segment-id="${report.id}"][data-lab-type="HL7"]`);await row.waitFor({state:'attached',timeout:45000});
    lab=await s.popup(inbox,row.locator('a[onclick*="reportWindow"]').first(),'pagination-lab');
    h.assert((await lab.locator('body').innerText()).includes(accession),'Inbox opened another lab report');
    const file=await x.saveDownload(lab,scratch,()=>lab.locator('input[type="button"][value*="Print"]').first().click(),{route:/\/lab\/CA\/ALL\/PrintPDF$/});
    h.assert(file.status===200&&file.bytes.subarray(0,5).toString()==='%PDF-','Native lab Print returned an error instead of PDF');
    bytes=file.bytes;x.assertEmbeddedFonts(file.file);
    const count=graph.pageCount(bytes);h.assert(count>=3&&count<=20,'Long report did not force several bounded PDF pages');
    pages=Array.from({length:count},(_,i)=>({...graph.pageWords(bytes,i+1),
      rawText:execFileSync('pdftotext',['-raw','-f',String(i+1),'-l',String(i+1),'-','-'],{input:bytes,encoding:'utf8',timeout:30000,maxBuffer:16*1024*1024})}));
    images=pages.map((_,i)=>raster(bytes,i+1));
    if(process.env.LAB_PDF_ARTIFACT_DIR) {
      fs.writeFileSync(h.buildArtifactPath(process.env.LAB_PDF_ARTIFACT_DIR,marker+'-report','.pdf'),bytes,{flag:'wx',mode:0o600});
      fs.writeFileSync(h.buildArtifactPath(process.env.LAB_PDF_ARTIFACT_DIR,marker+'-expected','.json'),JSON.stringify({expected,accession,controlAccession},null,2),{flag:'wx',mode:0o600});
      pages.forEach((_,i)=>fs.writeFileSync(h.buildArtifactPath(process.env.LAB_PDF_ARTIFACT_DIR,marker+'-page'+(i+1),'.png'),graph.renderPng(bytes,{page:i+1,dpi:72}),{flag:'wx',mode:0o600}));
      console.log('  Retained synthetic pagination evidence in '+process.env.LAB_PDF_ARTIFACT_DIR);
    }
  });
  const properties=fs.readFileSync(path.join(__dirname,'../src/main/resources/carlos.properties'),'utf8');
  const notice=(process.env.LAB_PDF_EXPECTED_NOTICE||properties.match(/^confidentiality_statement\.v1\s*=\s*(.*)$/m)?.[1]||'').trim().replace(/\s+/g,' ');
  h.assert(notice,'Expected confidentiality notice is not configured');
  await s.step('all results, flags and complete wrapped comments appear exactly once in order with visible ink and clear printable bounds',async()=>{
    verify(pages,expected,notice,marker,images);
    const text=x.squash(x.pdfTextBuffer(bytes,{raw:true}));
    h.assert(text.includes(x.squash(accession))&&!text.includes(x.squash(controlAccession))&&!text.includes('PWRCONTROL')&&!text.includes('88776655'),'Printed report has the wrong accession or leaks the control report');
    h.assert(controlState()===beforeControl,'Printing the long report changed its unrelated control');
    h.assert(sourceState()===beforeSource,'Lab viewing/printing changed the received clinical source or patient routing');
    console.log('  Verified '+expected.length+' complete clinical results across '+pages.length+' rendered pages.');
  });
  await s.step('offline negative controls reject missing, duplicate and invisible clinical data rather than accepting page count alone',async()=>{
    const clone=()=>pages.map(page=>({...page,words:page.words.map(word=>({...word}))}));
    let changed=clone();for(const page of changed)page.words=page.words.filter(word=>word.text!==expected[0].name);
    require('node:assert/strict').throws(()=>verify(changed,expected,notice,marker,images),/Missing or duplicated clinical token/);
    changed=clone();const source=changed.flatMap(page=>page.words).find(word=>word.text===expected[0].name);changed[0].words.push({...source});
    require('node:assert/strict').throws(()=>verify(changed,expected,notice,marker,images),/Missing or duplicated clinical token/);
    const blank=images.map(image=>({...image,pixels:Buffer.alloc(image.pixels.length,255)}));
    require('node:assert/strict').throws(()=>verify(pages,expected,notice,marker,blank),/no visible rendered ink/);
  });
}
if(require.main===module)runWorkflow('lab-report-pagination',workflow,{openMaster:false,preflight:()=>x.requirePoppler('pdftotext','pdffonts','pdfinfo','pdftoppm')});
module.exports={workflow,verify};
