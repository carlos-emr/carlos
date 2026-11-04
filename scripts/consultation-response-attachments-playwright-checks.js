#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// Exercises the installed REST endpoint through a real authenticated browser session.
// Every clinical row belongs to an owned synthetic patient; audit records are retained.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const patient = Number(s.patient);
  const quote = h.sqlString;
  const responseId = Number(s.sql.value(`INSERT INTO consultationResponse
    (demographicNo,providerNo,referralReason,plan) VALUES
    (${patient},${quote(s.provider)},${quote(s.marker)},'original'); SELECT LAST_INSERT_ID()`));
  h.assert(Number.isSafeInteger(responseId) && responseId > 0, 'Response fixture was not created');
  const routingIds = [];
  s.cleanup(() => {
    const remaining = s.sql.value(`SELECT COUNT(*) FROM consultationResponse WHERE responseId=${responseId}`);
    if (remaining !== '0') {
      h.assert(s.sql.value(`SELECT COUNT(*) FROM consultationResponse WHERE responseId=${responseId}
        AND demographicNo=${patient} AND referralReason=${quote(s.marker)}`) === '1', 'Response ownership changed');
      s.sql.execute(`DELETE FROM consultResponseDoc WHERE responseId=${responseId};
        DELETE FROM consultationResponse WHERE responseId=${responseId} AND demographicNo=${patient}`);
    }
    for (const id of routingIds) {
      s.sql.execute(`DELETE FROM patientLabRouting WHERE id=${id} AND demographic_no=${patient}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE id=${id}`) === '0', 'Owned routing cleanup failed');
    }
    h.assert(s.sql.value(`SELECT COUNT(*) FROM consultResponseDoc WHERE responseId=${responseId}`) === '0', 'Owned attachments remain');
  });
  // A unique synthetic segment number needs routing only: this endpoint does not render lab PDFs.
  const labNo = Number(s.sql.value('SELECT COALESCE(MAX(lab_no),0)+1 FROM patientLabRouting'));
  h.assert(Number.isSafeInteger(labNo) && labNo > 0 && labNo < 2147483647, 'No safe synthetic lab number');
  for (const source of ['HL7', 'MDS']) {
    const id = Number(s.sql.value(`INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created)
      VALUES (${patient},${labNo},${quote(source)},NOW()); SELECT LAST_INSERT_ID()`));
    h.assert(Number.isSafeInteger(id) && id > 0, 'Routing fixture was not created');
    routingIds.push(id);
  }
  const lab = source => ({ documentType: 'L', documentNo: labNo, labType: source });
  const payload = overrides => ({ id: responseId, referringDoctor: { id: 7 }, providerNo: s.provider,
    referralReason: s.marker, plan: 'saved', attachments: [lab('MDS'), lab('HL7'), lab('MDS')], ...overrides });
  const endpoint = `${s.config.baseUrl}/ws/rs/consults/saveResponse`;
  const post = async (data, expected) => {
    const response = await s.context.request.post(endpoint, { data });
    h.assert(response.status() === expected, `saveResponse expected HTTP ${expected}, received ${response.status()}`);
    if (expected === 200) h.assert((await response.json()).id === responseId, 'Saved response identity changed');
    await response.dispose();
  };
  const clinical = () => s.sql.rows(`SELECT demographicNo,referralReason,plan FROM consultationResponse WHERE responseId=${responseId}`);
  const attachments = () => s.sql.rows(`SELECT id,documentNo,docType,lab_type,deleted FROM consultResponseDoc WHERE responseId=${responseId} ORDER BY id`);
  const unchangedAfter = async data => {
    const before = JSON.stringify([clinical(), attachments()]);
    await post(data, 400);
    h.assert(JSON.stringify([clinical(), attachments()]) === before, 'Rejected save changed clinical data or attachments');
  };
  await s.step('reject patient reassignment before clinical or attachment writes', async () => {
    await unchangedAfter(payload({ demographic: { demographicNo: patient + 1 } }));
  });
  await s.step('omitted demographic retains stored patient and deduplicates by lab source', async () => {
    await post(payload(), 200);
    h.assert(clinical()[0][0] === String(patient) && clinical()[0][2] === 'saved', 'Stored patient or plan is incorrect');
    h.assert(JSON.stringify(s.sql.rows(`SELECT lab_type,COUNT(*) FROM consultResponseDoc
      WHERE responseId=${responseId} AND (deleted IS NULL OR deleted<>'Y') GROUP BY lab_type ORDER BY lab_type`))
      === JSON.stringify([['HL7', '1'], ['MDS', '1']]), 'Source collision or duplicate attachment persisted');
  });
  await s.step('repeated duplicate selections retain existing rows', async () => {
    const before = JSON.stringify(attachments());
    await post(payload({ demographic: { demographicNo: patient } }), 200);
    h.assert(JSON.stringify(attachments()) === before, 'Repeated save changed attachment identities');
  });
  await s.step('invalid lab source rolls back clinical edits and attachment changes', async () => {
    await unchangedAfter(payload({ plan: 'must roll back', attachments: [lab('CML')] }));
  });
  await s.step('missing referring doctor and invalid patient return explicit validation failures', async () => {
    await unchangedAfter(payload({ referringDoctor: null }));
    await unchangedAfter(payload({ demographic: { demographicNo: 0 } }));
  });
  await s.step('new response for an unknown patient is rejected without creating a row', async () => {
    const unknown = Number(s.sql.value('SELECT COALESCE(MAX(demographic_no),0)+1000 FROM demographic'));
    h.assert(Number.isSafeInteger(unknown) && unknown > patient, 'No unused patient number');
    const before = s.sql.value(`SELECT COUNT(*) FROM consultationResponse WHERE referralReason=${quote(s.marker)}`);
    await post(payload({ id: null, demographic: { demographicNo: unknown }, attachments: null }), 400);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM consultationResponse WHERE referralReason=${quote(s.marker)}`) === before,
      'A response was created for an unknown patient');
  });
  await s.step('new response for the owned patient is created under that patient', async () => {
    const response = await s.context.request.post(endpoint,
      { data: payload({ id: null, demographic: { demographicNo: patient }, attachments: null }) });
    h.assert(response.status() === 200, `new saveResponse expected HTTP 200, received ${response.status()}`);
    const createdId = Number((await response.json()).id);
    await response.dispose();
    h.assert(Number.isSafeInteger(createdId) && createdId > 0 && createdId !== responseId, 'New response id was not returned');
    s.cleanup(() => s.sql.execute(`DELETE FROM consultResponseDoc WHERE responseId=${createdId};
      DELETE FROM consultationResponse WHERE responseId=${createdId} AND demographicNo=${patient}
      AND referralReason=${quote(s.marker)}`));
    h.assert(s.sql.value(`SELECT demographicNo FROM consultationResponse WHERE responseId=${createdId}`) === String(patient),
      'New response was not saved under the submitted patient');
  });
  await s.step('reads without a consultation id return 400 instead of a server error', async () => {
    for (const path of [`getRequest?demographicId=${patient}`, `getResponse?demographicNo=${patient}`,
      `getRequestAttachments?demographicId=${patient}&attached=true`,
      `getResponseAttachments?demographicNo=${patient}&attached=true`]) {
      const response = await s.context.request.get(`${s.config.baseUrl}/ws/rs/consults/${path}`);
      h.assert(response.status() === 400, `${path.split('?')[0]} expected HTTP 400, received ${response.status()}`);
      await response.dispose();
    }
  });
  const requestId = Number(s.sql.value(`INSERT INTO consultationRequests
    (demographicNo,providerNo,reason,lastUpdateDate) VALUES
    (${patient},${quote(s.provider)},${quote(s.marker)},NOW()); SELECT LAST_INSERT_ID()`));
  h.assert(Number.isSafeInteger(requestId) && requestId > 0, 'Request fixture was not created');
  s.cleanup(() => {
    h.assert(s.sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${requestId}
      AND demographicNo=${patient} AND reason=${quote(s.marker)}`) === '1', 'Request ownership changed');
    s.sql.execute(`DELETE FROM consultdocs WHERE requestId=${requestId};
      DELETE FROM consultationRequests WHERE requestId=${requestId} AND demographicNo=${patient}`);
  });
  s.sql.execute(`INSERT INTO consultdocs (requestId,document_no,doctype,lab_type,provider_no,attach_date)
    VALUES (${requestId},${labNo},'L','MDS',${quote(s.provider)},CURDATE())`);
  const requestPayload = { id: requestId, demographicId: patient, providerNo: s.provider,
    reasonForReferral: s.marker, referralDate: '2026-09-27T00:00:00Z', serviceId: 1,
    urgency: '2', status: '1' };
  const postRequest = async (data, expected) => {
    const response = await s.context.request.post(`${s.config.baseUrl}/ws/rs/consults/saveRequest`, { data });
    h.assert(response.status() === expected, `saveRequest expected HTTP ${expected}, received ${response.status()}`);
    await response.dispose();
  };
  const requestRows = () => s.sql.rows(`SELECT demographicNo,reason FROM consultationRequests WHERE requestId=${requestId}`);
  const requestAttachments = () => s.sql.rows(`SELECT id,deleted FROM consultdocs WHERE requestId=${requestId} ORDER BY id`);
  await s.step('request rejects patient reassignment without modifying existing attachments', async () => {
    const foreignPatient = Number(s.sql.value(`SELECT demographic_no FROM demographic WHERE demographic_no<>${patient} ORDER BY demographic_no LIMIT 1`));
    h.assert(foreignPatient > 0 && foreignPatient !== patient, 'An existing comparison patient is required');
    const before = JSON.stringify([requestRows(), requestAttachments()]);
    await postRequest({ ...requestPayload, demographicId: foreignPatient, attachments: [] }, 400);
    h.assert(JSON.stringify([requestRows(), requestAttachments()]) === before, 'Rejected request changed saved data');
  });
  await s.step('omitted request attachments preserve rows and explicit empty list detaches all', async () => {
    const before = JSON.stringify(requestAttachments());
    await postRequest(requestPayload, 200);
    h.assert(JSON.stringify(requestAttachments()) === before, 'Omitted request attachments were changed');
    await postRequest({ ...requestPayload, attachments: [] }, 200);
    h.assert(requestAttachments().length === 1 && requestAttachments()[0][1] === 'Y', 'Empty selection did not detach the existing lab');
  });
  await s.step('deleted response returns validation failure without recreating it', async () => {
    s.sql.execute(`DELETE FROM consultResponseDoc WHERE responseId=${responseId};
      DELETE FROM consultationResponse WHERE responseId=${responseId} AND demographicNo=${patient}
      AND referralReason=${quote(s.marker)}`);
    await post(payload(), 400);
    h.assert(clinical().length === 0 && attachments().length === 0, 'Deleted response was recreated');
  });
}

if (require.main === module) runWorkflow('consultation-response-attachments', workflow, { openMaster: false });
module.exports = { workflow };
