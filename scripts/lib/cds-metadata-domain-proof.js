/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h = require('./playwright-harness');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

async function verifyMetadataPage(receipt, frame, readApi) {
  const observed = receipt.page;
  if (!observed) return;
  const params = {section:observed.section,limit:observed.limit};
  if (['uploads','patients'].includes(observed.section)) params.afterKey = observed.afterKey;
  else params.afterId = observed.afterId;
  if (['patients','diagnostics'].includes(observed.section)) params.runId = receipt.runId;
  if (observed.section === 'chunks') params.uploadId = receipt.uploadId;
  const response = await readApi(frame,'cdsImportStatus',params);
  h.assert(response.status === 200 && response.body?.ok === true && Array.isArray(response.body.rows),
    'Independent exact metadata page is unavailable');
  const rows = response.body.rows;
  const key = observed.section === 'patients' ? 'patientKey' : observed.section === 'chunks' ? 'index' : 'id';
  h.assert(rows.length <= observed.limit && observed.rows.every((row,index) => rows[index]?.[key] === row[key]),
    'The observed page identities differ from the independent same-cursor page');
  // Diagnostic/chunk/upload rows may append between reads; existing identities and
  // immutable values cannot change. No endpoint silently becomes a different page.
  for (let index = 0; index < observed.rows.length; index++) {
    const old = observed.rows[index], current = rows[index];
    const immutable = observed.section === 'patients' ? ['patientKey','copyKey']
      : observed.section === 'diagnostics' ? ['id','code','blocking']
        : observed.section === 'chunks' ? ['index','offset','bytes','sha256'] : ['id','expectedBytes'];
    for (const name of immutable) h.assert(old[name] === current[name], 'Immutable bounded-page content changed');
    if (observed.section === 'patients' && old.targetDemographicNo != null) {
      h.assert(current.targetDemographicNo === old.targetDemographicNo, 'A native patient binding changed');
    }
    if (observed.section === 'diagnostics' && old.resolved) h.assert(current.resolved, 'A resolved diagnostic became unresolved');
    if (observed.section === 'uploads') h.assert(current.receivedBytes >= old.receivedBytes, 'Upload progress moved backward');
  }
  if (rows.length === observed.rows.length) {
    h.assert((response.body.nextCursor ?? null) === observed.nextCursor, 'Independent page cursor differs');
  } else {
    h.assert(observed.rows.length < observed.limit && rows.length > observed.rows.length,
      'Only a bounded append can advance the observed page cursor');
  }
  if (observed.section === 'chunks') {
    const progress = await frame.locator('#uploadProgress').evaluate(node => node.value);
    h.assert(observed.rows.every(row => progress >= row.offset + row.bytes), 'Verified chunk prefix is not represented by UI progress');
    return;
  }
  const selector = {patients:'#patientRows',diagnostics:'#diagnosticRows',uploads:'#uploadRows'}[observed.section];
  const rendered = await frame.locator(selector + ' tr').evaluateAll(nodes => nodes.map(node => [...node.cells].map(cell => cell.textContent)));
  h.assert(rendered.length >= observed.rows.length && rendered.length <= observed.limit, 'Bounded page rows are not rendered');
  for (let index = 0; index < observed.rows.length; index++) {
    const old = observed.rows[index], current = rows[index];
    if (observed.section === 'patients') {
      const label = (current.sourcePatientId || current.patientKey) + (current.copyKey ? ' / ' + current.copyKey : '');
      const row = rendered.find(cells => cells[0] === label);
      h.assert(row && [old.state,current.state].includes(row[1])
        && [String(old.held),String(current.held)].includes(row[2]), 'The patient page does not render its observed identity/state');
    } else if (observed.section === 'diagnostics') {
      h.assert(rendered.some(cells => cells[0] === String(old.id) && cells[1].startsWith(old.code + ' / ')),
        'The diagnostic page does not render its observed identity/code');
    } else {
      h.assert(rendered.some(cells => cells[0].includes(old.id)), 'The upload page does not render its observed identity');
    }
  }
}

/** Independent domain evidence for the narrowly observed metadata EOF bookkeeping case. */
function createCdsMetadataProver({frame, config, sql, readApi}) {
  return async function prove(receipt) {
    const owner = 'web-' + sql.value('SELECT security_no FROM security WHERE user_name=' + h.sqlString(config.testUser));
    h.assert(receipt.owner === owner, 'Metadata response is not bound to the native authenticated operator');
    h.assert(await frame.locator('#cdsImport').getAttribute('data-owner') === owner,
      'The visible import form has a different native operator');
    const proof = {kind:'read-only', owner, uiVerified:false, independentVerified:false};
    await verifyMetadataPage(receipt,frame,readApi);
    if (receipt.page) proof.page = receipt.page;
    if (receipt.uploadIds) {
      h.assert(receipt.uploadIds.every(id => UUID.test(id)), 'Upload page has an invalid identity');
      const visible = await frame.locator('#uploadRows').innerText();
      h.assert(await frame.locator('#uploadRows tr').count() <= 50
        && receipt.uploadIds.every(id => visible.includes(id)), 'The observed bounded upload page is not rendered');
      const owned = receipt.uploadIds.length ? sql.rows(`SELECT id FROM cds_import_upload WHERE operator_key=${h.sqlString(owner)}
        AND id IN (${receipt.uploadIds.map(h.sqlString).join(',')})`).flat() : [];
      h.assert(owned.length === receipt.uploadIds.length, 'An observed upload is not independently owned');
      return {...proof, uiVerified:true, independentVerified:true, uploadIds:owned};
    }
    let upload;
    if (receipt.uploadId) {
      h.assert(UUID.test(receipt.uploadId), 'Observed upload identity is invalid');
      const independent = await readApi(frame, 'cdsImportStatus', {section:'upload', uploadId:receipt.uploadId});
      h.assert(independent.status === 200 && independent.body?.ok === true
        && independent.body.upload?.id === receipt.uploadId, 'Independent owner upload receipt is unavailable');
      upload = independent.body.upload;
      h.assert(sql.value('SELECT operator_key FROM cds_import_upload WHERE id=' + h.sqlString(receipt.uploadId)) === owner,
        'Durable upload has another owner');
      const row = frame.locator('#uploadRows tr').filter({hasText:receipt.uploadId});
      const rendered = await row.count() === 1 && (await row.innerText()).includes(upload.receivedBytes + ' / ' + upload.expectedBytes);
      const progress = await frame.locator('#uploadProgress').evaluate(node => node.value);
      const current = await frame.locator('#currentUpload').innerText();
      h.assert(rendered || (current.includes(upload.fileName) && progress >= (receipt.receivedBytes || 0)),
        'The acknowledged upload is not represented by the visible owner receipt/progress');
      Object.assign(proof, {kind:'owned-upload', uploadId:upload.id, receivedBytes:upload.receivedBytes});
    }
    if (receipt.runId) {
      h.assert(UUID.test(receipt.runId), 'Observed run identity is invalid');
      const independent = await readApi(frame, 'cdsImportStatus', {runId:receipt.runId});
      h.assert(independent.status === 200 && independent.body?.ok === true
        && independent.body.job?.id === receipt.runId, 'Independent owner run receipt is unavailable');
      const {job,summary} = independent.body;
      h.assert(sql.value('SELECT operator FROM cds_import_run WHERE id=' + h.sqlString(receipt.runId)) === owner,
        'Durable run has another owner');
      let visible;
      try { visible = JSON.parse(await frame.locator('#runSummary').innerText()); } catch (_) { /* Checked below. */ }
      h.assert(visible?.job?.id === job.id, 'The exact observed run is not rendered');
      for (const key of ['sourceDigest','optionsDigest','rulesetDigest']) {
        h.assert(visible.job[key] === job[key] && (!receipt[key] || receipt[key] === job[key]),
          'Visible/observed/independent immutable run provenance differs');
        proof[key] = job[key];
      }
      if (upload) h.assert([upload.runId,upload.dryRunId].includes(job.id), 'Acceptance is not bound to its immutable upload');
      const missing = sql.value(`SELECT COUNT(*) FROM cds_import_patient p LEFT JOIN demographic d
        ON d.demographic_no=p.target_demographic_no WHERE p.run_id=${h.sqlString(job.id)}
        AND p.target_demographic_no IS NOT NULL AND d.demographic_no IS NULL`);
      h.assert(missing === '0', 'A durable target binding has no native patient');
      Object.assign(proof, {kind:'owned-run', runId:job.id, nativeVerified:true});
      if (receipt.cleanComplete || summary.cleanComplete) {
        h.assert(summary.cleanComplete === true && summary.reportsDelivered === true
          && summary.cleanupComplete === true && summary.resumeRequested === false,
          'Report/cleanup completion is not independently proven');
        h.assert(Number.isSafeInteger(summary.expectedPatients) && summary.expectedPatients > 0, 'Completion has no proven source patients');
        const mode = sql.value('SELECT dry_run FROM cds_import_run WHERE id=' + h.sqlString(job.id));
        h.assert((mode === '1' && summary.dryRun === true && job.state === 'DRY_RUN_COMPLETE' && summary.clinicalComplete === false)
          || (mode === '0' && summary.dryRun === false && job.state === 'COMPLETE' && summary.clinicalComplete === true),
        'Dry-run/execution completion mode differs from durable state');
        if (summary.dryRun) {
          h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE run_id=${h.sqlString(job.id)}
            AND (held=1 OR target_demographic_no IS NOT NULL)`) === '0', 'Preview created or held a native target');
          h.assert(sql.value('SELECT COUNT(*) FROM cds_import_checkpoint WHERE first_run_id=' + h.sqlString(job.id)) === '0',
            'Preview committed a native checkpoint');
          h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_unit WHERE run_id=${h.sqlString(job.id)} AND state<>'EXPECTED'`) === '0',
            'Preview advanced a clinical unit');
        } else {
          h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE run_id=${h.sqlString(job.id)}
            AND (held=1 OR target_demographic_no IS NULL)`) === '0', 'Completed import has a held or unbound patient');
          h.assert(Number(sql.value('SELECT COUNT(*) FROM cds_import_patient WHERE run_id=' + h.sqlString(job.id))) === summary.expectedPatients,
            'Completed native patient count differs from durable accounting');
          h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_unit u LEFT JOIN cds_import_checkpoint c ON c.id=u.unit_key
            WHERE u.run_id=${h.sqlString(job.id)} AND (u.state NOT IN ('COMMITTED','ALREADY_COMPLETE') OR c.id IS NULL)`) === '0',
            'Completed native units are missing their committed/replayed receipts');
        }
        const report = await readApi(frame, 'cdsImportReport', {runId:job.id,type:'receipt'});
        h.assert(report.status === 200 && report.body?.runId === job.id && report.body.cleanComplete === true
          && report.body.dryRun === summary.dryRun && report.body.clinicalComplete === summary.clinicalComplete
          && report.body.sourceDigest === job.sourceDigest && report.body.optionsDigest === job.optionsDigest
          && report.body.manifestDigest === job.manifestDigest && report.body.rulesetDigest === job.rulesetDigest,
          'Protected completion receipt differs from native/visible provenance');
        proof.reportsVerified = true;
      }
    }
    h.assert(proof.runId || proof.uploadId, 'Metadata response has no independently verifiable owner receipt');
    return {...proof, uiVerified:true, independentVerified:true};
  };
}
module.exports = {createCdsMetadataProver,verifyMetadataPage};
