#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The eChart's left-column modules list what arrived for the patient: Messages
 * (encounter/displayMessages), Documents (encounter/displayDocuments) and Labs
 * (encounter/displayLabs). Every chart check loads these fragments and echart-navbar-modules
 * opens their links, but no check asserted what they list. This seeds one owned row of each
 * for the workflow's patient and asserts the module shows it with its title, full title and
 * count, that the message row opens that message, the document row links to that document and
 * the lab row opens that lab (unread at first), and that a second owned patient's chart lists
 * none of them.
 *
 * Fixtures: an owned message to the test login (messagetbl, messagelisttbl, msgDemoMap), an
 * owned document (document, ctl_document; no file, so its row is not opened) and an owned HL7
 * lab (hl7TextMessage, hl7TextInfo, patientLabRouting, providerLabRouting), plus a second FAKE
 * patient; all removed by their own keys.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { moduleList, seedSecondPatient, openSecondChart, releaseLocksAfter, closeChart, TIMEOUT } = require('./lib/echart-module-list');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const subject = `${marker} subject`;
  const docDescription = `${marker} document`;
  let messageId;
  let documentNo;
  let lab;
  s.cleanup(() => {
    if (messageId) sql.execute(`DELETE FROM msgDemoMap WHERE messageID=${messageId}; DELETE FROM messagelisttbl WHERE message=${messageId};
      DELETE FROM messagetbl WHERE messageid=${messageId} AND thesubject=${q(subject)}`);
    if (documentNo) sql.execute(`DELETE FROM ctl_document WHERE document_no=${documentNo}; DELETE FROM document WHERE document_no=${documentNo} AND docdesc=${q(docDescription)}`);
    if (lab) sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject=${q(subject)}`) === '0', 'Owned message remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc=${q(docDescription)}`) === '0', 'Owned document remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE label=${q(marker)}`) === '0', 'Owned lab remains');
  });
  messageId = h.insertId(sql, `INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
    VALUES(CURDATE(),CURTIME(),${q(`${marker} body`)},${q(subject)},'FAKE Sender','FAKE Recipient',${q(provider)},0,0)`, 'owned message');
  sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${messageId},${q(provider)},'new',0,0,0);
    INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${messageId},${patient})`);
  documentNo = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
      public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES ('others',${q(docDescription)},${q(`${marker}.pdf`)},${q(provider)},${q(provider)},'A','application/pdf',0,1,0,CURDATE(),NOW(),NOW())`, 'owned document');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${documentNo},'A')`);
  const stamp = '20261019100000';
  const message = [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|${marker}|P|2.3`,
    `PID||9999999999|9999999999||${marker}^FAKE||19800101|F`,
    `OBR|1||${marker}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE^DOC||||||${stamp}||CHEM4|F`,
    `OBX|1|NM|2345-7^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||${stamp}`,
  ].join('\r');
  lab = h.insertId(sql, `INSERT INTO hl7TextMessage(fileUploadCheck_id,message,type,serviceName,created)
    VALUES(0,${q(Buffer.from(message).toString('base64'))},'PATHL7','MODULETEST',NOW())`, 'owned lab');
  sql.execute(`INSERT INTO hl7TextInfo(lab_no,sex,result_status,final_result_count,obr_date,priority,
      requesting_client,discipline,last_name,first_name,report_status,accessionNum,label)
    VALUES(${lab},'F','A',1,NOW(),'R','FAKE, DOC','CHEM',${q(marker)},'FAKE','F',${q(marker)},${q(marker)});
    INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${lab},'HL7',NOW());
    INSERT INTO providerLabRouting(provider_no,lab_no,status,lab_type) VALUES(${q(provider)},${lab},'N','HL7')`);
  const second = seedSecondPatient(s);

  await releaseLocksAfter(s, () => steps(s, { second, subject, docDescription, messageId, documentNo, lab }));
}

async function steps(s, { second, subject, docDescription, messageId, documentNo, lab }) {
  const { marker } = s;
  const chart = await s.chart();
  const msgs = moduleList(chart, 'msgs');
  const docs = moduleList(chart, 'docs');
  const labs = moduleList(chart, 'labs');

  await s.step('the Messages module lists the owned message by its subject and opens it', async () => {
    const row = msgs.row(subject);
    await row.waitFor({ state: 'attached' });
    h.assert((await row.innerText()).trim() === subject, 'The message row does not show the subject');
    const shown = await msgs.shown();
    h.assert(shown === '1', `The Messages module counts ${shown} rows instead of the one owned message`);
    const view = await s.popup(chart, row, 'chart-message');
    await view.waitForURL(url => url.pathname.endsWith('/messenger/ViewMessage') && url.searchParams.get('messageID') === messageId, { timeout: TIMEOUT });
    await view.getByText(subject, { exact: false }).first().waitFor({ timeout: TIMEOUT });
    await view.close();
  });

  await s.step('the Documents module lists the owned document by its description, linking to that document', async () => {
    const row = docs.row(docDescription);
    await row.waitFor({ state: 'attached' });
    h.assert((await row.innerText()).trim() === docDescription, 'The document row does not show the description');
    // (The red "not reviewed" colour is only applied with the inbox flag on, so it is not asserted.)
    // The row opens the viewer (ViewShowDocument, segmentID = the document number); the other
    // branches of EctDisplayDocs2Action name it doc_no, or the file (inbox manager on).
    const onclick = await row.getAttribute('onclick') || '';
    h.assert(/ViewShowDocument\?[^']*\bsegmentID=(\d+)/.exec(onclick)?.[1] === documentNo || onclick.includes(`doc_no=${documentNo}`)
      || onclick.includes(`name=${marker}.pdf`),
      `The document row does not link to the owned document: ${onclick.slice(0, 400)}`);
    const shown = await docs.shown();
    h.assert(shown === '1', `The Documents module counts ${shown} rows instead of the one owned document`);
  });

  await s.step('the Labs module lists the owned lab by its label, unread, and opens it', async () => {
    const row = labs.row(marker);
    await row.waitFor({ state: 'attached' });
    const text = (await row.innerText()).trim();
    h.assert(text === `*${marker}*`, `An unread lab should be listed as *label*; got ${JSON.stringify(text)}`);
    const shown = await labs.shown();
    h.assert(shown === '1', `The Labs module counts ${shown} rows instead of the one owned lab`);
    const view = await s.popup(chart, row, 'chart-lab');
    // The lab is opened for the chart's patient: the one URL where a session-bean mix-up would show.
    await view.waitForURL(url => /\/lab\/CA\/ALL\/ViewLabDisplay/.test(url.pathname) && url.searchParams.get('segmentID') === lab
      && url.searchParams.get('demographicId') === s.patient, { timeout: TIMEOUT });
    await view.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(view, 'chart-lab');
    await view.getByText(marker, { exact: false }).first().waitFor({ timeout: TIMEOUT });
    await view.close();
  });

  await s.step('a second patient\'s chart lists none of them', async () => {
    const other = await openSecondChart(s, second);
    for (const [name, divId] of [['Messages', 'msgs'], ['Documents', 'docs'], ['Labs', 'labs']]) {
      const list = moduleList(other, divId);
      const shown = await list.shown();
      h.assert(shown === '0', `${name} shows ${shown} rows for a patient who has none`);
      h.assert(await list.rows.count() === 0, `${name} lists a row for a patient who has none`);
    }
    await closeChart(s, other);
  });
}

if (require.main === module) runWorkflow('echart-left-modules-list-owned-rows', workflow);
module.exports = { workflow };
