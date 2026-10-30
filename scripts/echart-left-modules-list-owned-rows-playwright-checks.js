#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
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
const { moduleList, seedSecondPatient, openSecondChart, assertDistinctCharts, releaseLocksAfter, closeChart, TIMEOUT } = require('./lib/echart-module-list');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const subject = `${marker} subject`;
  const docDescription = `${marker} document`;
  let messageId;
  let documentNo;
  let lab;
  s.cleanup(() => {
    if (messageId) {
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID=?`, [messageId]);
      sql.execute(`DELETE FROM messagelisttbl WHERE message=?`, [messageId]);
      sql.execute(`DELETE FROM messagetbl WHERE messageid=? AND thesubject=?`, [messageId, subject]);
    }
    if (documentNo) {
      sql.execute(`DELETE FROM ctl_document WHERE document_no=?`, [documentNo]);
      sql.execute(`DELETE FROM document WHERE document_no=? AND docdesc=?`, [documentNo, docDescription]);
    }
    if (lab) {
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=? AND lab_type='HL7'`, [lab]);
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=? AND lab_type='HL7'`, [lab]);
      sql.execute(`DELETE FROM hl7TextInfo WHERE lab_no=?`, [lab]);
      sql.execute(`DELETE FROM hl7TextMessage WHERE lab_id=?`, [lab]);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject=?`, [subject]) === '0', 'Owned message remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE docdesc=?`, [docDescription]) === '0', 'Owned document remains');
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE label=?`, [marker]) === '0', 'Owned lab remains');
  });
  messageId = h.insertId(sql, `INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
    VALUES(CURDATE(),CURTIME(),?,?,'FAKE Sender','FAKE Recipient',?,0,0)`, 'owned message', [`${marker} body`, subject, provider]);
  sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(?,?,'new',0,0,0)`, [messageId, provider]);
  sql.execute(`INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(?,?)`, [messageId, patient]);
  documentNo = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
      public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES ('others',?,?,?,?,'A','application/pdf',0,1,0,CURDATE(),NOW(),NOW())`, 'owned document', [docDescription, `${marker}.pdf`, provider, provider]);
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',?,?,'A')`, [patient, documentNo]);
  const stamp = '20261019100000';
  const message = [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|${marker}|P|2.3`,
    `PID||9999999999|9999999999||${marker}^FAKE||19800101|F`,
    `OBR|1||${marker}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE^DOC||||||${stamp}||CHEM4|F`,
    `OBX|1|NM|2345-7^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||${stamp}`,
  ].join('\r');
  lab = h.insertId(sql, `INSERT INTO hl7TextMessage(fileUploadCheck_id,message,type,serviceName,created)
    VALUES(0,?,'PATHL7','MODULETEST',NOW())`, 'owned lab', [Buffer.from(message).toString('base64')]);
  sql.execute(`INSERT INTO hl7TextInfo(lab_no,sex,result_status,final_result_count,obr_date,priority,
      requesting_client,discipline,last_name,first_name,report_status,accessionNum,label)
    VALUES(?,'F','A',1,NOW(),'R','FAKE, DOC','CHEM',?,'FAKE','F',?,?)`, [lab, marker, marker, marker]);
  sql.execute(`INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(?,?,'HL7',NOW())`, [patient, lab]);
  sql.execute(`INSERT INTO providerLabRouting(provider_no,lab_no,status,lab_type) VALUES(?,?,'N','HL7')`, [provider, lab]);
  const second = seedSecondPatient(s);
  const messageDate = sql.value("SELECT DATE_FORMAT(thedate,'%d-%c-%Y') FROM messagetbl WHERE messageid=?", [messageId]);
  const documentDate = sql.value('SELECT observationdate FROM document WHERE document_no=?', [documentNo]);

  await releaseLocksAfter(s, () => steps(s, { second, subject, docDescription, messageId, documentNo, lab, messageDate, documentDate }));
}

async function steps(s, { second, subject, docDescription, messageId, documentNo, lab, messageDate, documentDate }) {
  const { marker } = s;
  const chart = await s.chart();
  const msgs = moduleList(chart, 'msgs');
  const docs = moduleList(chart, 'docs');
  const labs = moduleList(chart, 'labs');

  await s.step('the Messages module lists the owned message by its subject and opens it', async () => {
    const row = msgs.row(subject);
    await row.waitFor({ state: 'attached' });
    h.assert((await row.innerText()).trim() === subject, 'The message row does not show the subject');
    h.assert(await row.getAttribute('title') === `${subject} ${messageDate}`, 'The message row has the wrong full title');
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
    h.assert(await row.getAttribute('title') === `${docDescription}${documentDate}`, 'The document row has the wrong full title');
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
    // EctDisplayLabAction22Action currently leaves formattedDate empty.
    h.assert(await row.getAttribute('title') === `${marker} `, 'The lab row has the wrong full title');
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
    const other = await openSecondChart(s, second, chart);
    for (const [name, divId] of [['Messages', 'msgs'], ['Documents', 'docs'], ['Labs', 'labs']]) {
      const list = moduleList(other, divId);
      const shown = await list.shown();
      h.assert(shown === '0', `${name} shows ${shown} rows for a patient who has none`);
      h.assert(await list.rows.count() === 0, `${name} lists a row for a patient who has none`);
      assertDistinctCharts(s, chart, other);
    }
    await closeChart(s, other);
  });
}

if (require.main === module) runWorkflow('echart-left-modules-list-owned-rows', workflow);
module.exports = { workflow };
