/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {webcrypto} = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const client = require('../src/main/webapp/js/demographic/cds-import.js');
const id = '11111111-1111-4111-8111-111111111111';
const clean = {cleanComplete: true, clinicalComplete: true, discoveryComplete: true, reportsDelivered: true,
  cleanupComplete: true, resumeRequested: false, pendingUnits: 0, unresolvedReviewItems: 0, inactiveDraftCount: 0, expectedPatients: 1, outcomes: {COMPLETE: 1}};

test('only reconciled clinical completion with delivered reports is styled as clean', () => {
  assert.equal(client.statusPresentation({id, state: 'COMPLETE'}, clean).clean, true);
  assert.equal(client.statusPresentation({id, state: 'ALREADY_COMPLETE'}, {...clean, outcomes: {ALREADY_COMPLETE: 1}}).clean, true);
});
for (const state of ['ACCEPTED', 'RUNNING', 'REFUSED', 'FAILED_BEFORE_WRITE', 'NOT_STARTED',
  'PARTIAL_COMMITTED', 'RECONCILIATION_REQUIRED', 'CANCELLED', 'DRY_RUN_COMPLETE']) {
  test(`${state} cannot imply clinical success even when a contradictory flag says clean`, () => {
    assert.equal(client.statusPresentation({id, state}, clean).clean, false);
  });
}
for (const changes of [{reportsDelivered: false}, {cleanupComplete: false}, {discoveryComplete: false},
  {clinicalComplete: false}, {pendingUnits: 1}, {unresolvedReviewItems: 1}, {inactiveDraftCount: 1},
  {inactiveDraftCount: undefined}, {resumeRequested: true}, {resumeRequested: undefined}, {resumeRequested: 'false'}, {expectedPatients: 2},
  {outcomes: {REFUSED: 1}}, {outcomes: {COMPLETE: 1, PARTIAL_COMMITTED: 1}}, {outcomes: undefined},
  {outcomes: {COMPLETE: '1'}}, {outcomes: {COMPLETE: -1}}]) {
  test(`incomplete, unknown or contradictory accounting ${JSON.stringify(changes)} cannot be clean`, () => {
    assert.equal(client.statusPresentation({id, state: 'COMPLETE'}, {...clean, ...changes}).clean, false);
  });
}
test('invalid or unavailable job status cannot be success', () => {
  assert.deepEqual(client.statusPresentation(null, clean), {state: 'UNAVAILABLE', clean: false, final: false});
});
test('only the durable operation terminal receipt stops polling', () => {
  for (const summary of [undefined, {}, clean, {reportsDelivered: true}, {reportDeliveryFailed: true},
    {cleanupComplete: true}, {operationTerminal: false}, {operationTerminal: 'true'},
    {operationTerminal: true}, {operationTerminal: true, resumeRequested: true},
    {operationTerminal: true, resumeRequested: 'false'}]) {
    assert.equal(client.mayStopPolling(summary), false);
  }
  assert.equal(client.mayStopPolling({...clean, operationTerminal: true}), true);
  assert.equal(client.mayStopPolling({operationTerminal: true, reportDeliveryFailed: true, resumeRequested: false}), true);
});
test('review declines require an identifiable patient copy and record paths are not scalar targets', () => {
  for (const patientKey of [null, '', 'not-a-patient', 'a'.repeat(63)]) {
    assert.equal(client.reviewEligibility({patientKey}).canDecline, false);
  }
  assert.equal(client.reviewEligibility({patientKey: 'a'.repeat(64)}).canDecline, true);
  for (const sourceXPath of ['/OmdCds[1]/PatientRecord[1]', '/OmdCds[1]/PatientRecord[1]/Demographics[1]',
    '/OmdCds/PatientRecord/Demographics/DateOfBirth', '/OmdCds[1]/PatientRecord[1]/Demographics[1]/DateOfBirth[0]']) {
    assert.equal(client.reviewEligibility({sourceXPath}).initialXPath, '');
  }
  const sourceXPath = '/OmdCds[1]/PatientRecord[1]/Demographics[1]/DateOfBirth[1]';
  assert.equal(client.reviewEligibility({sourceXPath}).initialXPath, sourceXPath);
  assert.equal(client.indexedSourceValuePath('/OmdCds[1]/PatientRecord[1]/Medications[2]/Dose[1]/@unit'), true);
});
test('unwritten revision approval needs the exact source conflict and affirmative durable eligibility', () => {
  const diagnostic = {code: 'SOURCE_REVISION_REVIEW_REQUIRED', patientKey: 'a'.repeat(64)};
  for (const summary of [{}, {unwrittenRevisionApprovalAllowed: false}, {unwrittenRevisionApprovalAllowed: 'true'}]) {
    assert.equal(client.reviewEligibility(diagnostic, summary).canApproveRevision, false);
  }
  assert.equal(client.reviewEligibility(diagnostic, {unwrittenRevisionApprovalAllowed: true}).canApproveRevision, true);
  assert.equal(client.reviewEligibility({code:diagnostic.code}, {unwrittenRevisionApprovalAllowed: true}).canApproveRevision, false);
  assert.equal(client.reviewEligibility({code: 'NATIVE_WRITE_FAILED'}, {unwrittenRevisionApprovalAllowed: true}).canApproveRevision, false);
});
test('history stores bounded opaque IDs, deduplicated, without source filenames', () => {
  const ids = Array.from({length: 25}, (_, n) => `${String(n).padStart(8, '0')}-1111-4111-8111-111111111111`);
  assert.equal(client.boundedIds([...ids, id, id, 'Patient-1971-03-04.xml']).length, 20);
  assert.deepEqual(client.boundedIds([id, id, 'not-a-run']), [id]);
});
test('upload navigation explicitly selects separate actual and preview acceptance IDs', () => {
  const preview = '22222222-2222-4222-8222-222222222222';
  assert.equal(client.latestUploadAttempt({runId: null, dryRunId: preview}), preview);
  assert.equal(client.latestUploadAttempt({runId: id, dryRunId: preview}), id);
  assert.equal(client.latestUploadAttempt({runId: 'invalid', dryRunId: preview}), preview);
  assert.equal(client.latestUploadAttempt({runId: null, dryRunId: null}), null);
  assert.equal(client.latestUploadAttempt(null), null);
});
test('effective options preserve nondefault bindings and reject unsafe synthetic merge', () => {
  const options = client.effectiveOptions({providerNo: '999998', sourceNamespace: 'clinic', sourceProfile: 'omd-5.2',
    programId: '2', facilityId: '3', dryRun: true, mergeExisting: true, matchProviderNames: false});
  assert.equal(options.mergeExisting, true); assert.equal(options.dryRun, true);
  assert.equal(options.matchProviderNames, false); assert.equal(options.programId, 2); assert.equal(options.facilityId, 3);
  assert.equal(Object.isFrozen(options), true);
  assert.equal(client.effectiveOptions({...options, providerNo: 'Dr_77-A'}).providerNo, 'Dr_77-A');
  assert.throws(() => client.effectiveOptions({...options, providerNo: '../77'}), /IMPORT_OPTIONS_INVALID/);
  assert.throws(() => client.effectiveOptions({...options, synthetic: true, courseId: 4}), /IMPORT_OPTIONS_INVALID/);
});
test('native provider input uses a valid modern HTML Unicode pattern for supported local IDs', () => {
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/demographic/demographicImport.jsp'), 'utf8');
  const pattern = jsp.match(/id="providerNo"[^>]*\bpattern="([^"]+)"/)[1];
  const validation = new RegExp(`^(?:${pattern})$`, 'v');
  assert.equal(validation.test('Dr_77-A'), true);
  assert.equal(validation.test('../77'), false);
});
test('real SHA-256 uses only the provided bounded blob', async () => {
  assert.equal(await client.sha256(new Blob(['abc']), webcrypto),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});
test('missing secure-context cryptography refuses before reading source bytes', async () => {
  let read = false;
  await assert.rejects(client.sha256({arrayBuffer() { read = true; return new ArrayBuffer(0); }}, {}), /UPLOAD_SECURE_CONTEXT_REQUIRED/);
  assert.equal(read, false);
});
test('bounded status JSON decodes multibyte Unicode across network chunks without changing it', async () => {
  const bytes = new TextEncoder().encode(JSON.stringify({ok: true, summary: 'é😀'}));
  const response = new Response(new ReadableStream({start(controller) {
    for (const byte of bytes) controller.enqueue(new Uint8Array([byte])); controller.close();
  }}));
  assert.deepEqual(await client.boundedJson(response), {ok: true, summary: 'é😀'});
});
test('oversized status JSON cancels its stream before unbounded browser allocation', async () => {
  let cancelled = false; let reads = 0;
  const response = new Response(new ReadableStream({pull(controller) { reads++; controller.enqueue(new Uint8Array(65536)); },
    cancel() { cancelled = true; }}));
  await assert.rejects(client.boundedJson(response, 131072), /IMPORT_RESPONSE_CAPACITY_EXCEEDED/);
  assert.equal(cancelled, true); assert.ok(reads <= 4);
});
test('malformed UTF8 or invalid JSON cannot be treated as a successful status receipt', async () => {
  await assert.rejects(client.boundedJson(new Response(new Uint8Array([0x22, 0xc3, 0x22]))));
  await assert.rejects(client.boundedJson(new Response('{"ok":true')));
});
test('resume verifies every prior chunk across bounded receipt pages', async () => {
  const block = 65536; const chunks = 101;
  const file = new Blob([Buffer.alloc(block * chunks, 7)]);
  Object.defineProperty(file, 'name', {value: 'source.xml'});
  const hash = await client.sha256(file.slice(0, block), webcrypto);
  const pages = [];
  const receipt = {fileName: file.name, expectedBytes: file.size, receivedBytes: file.size,
    chunkBytes: block, committedChunks: chunks};
  let verified = 0;
  const result = await client.verifyPrefix(file, receipt, async after => {
    pages.push(after);
    return Array.from({length: Math.min(100, chunks - after)}, (_, n) => ({index: after + n,
      offset: (after + n) * block, bytes: block, sha256: hash}));
  }, blob => client.sha256(blob, webcrypto), bytes => { verified = bytes; });
  assert.deepEqual(pages, [0, 100]); assert.equal(result, file.size); assert.equal(verified, file.size);
});
test('resume refuses changed bytes, missing receipts, gaps and mismatched source metadata', async () => {
  const file = new Blob([Buffer.alloc(65536, 1)]); Object.defineProperty(file, 'name', {value: 'source.xml'});
  const receipt = {fileName: file.name, expectedBytes: file.size, receivedBytes: file.size, chunkBytes: 65536, committedChunks: 1};
  const good = {index: 0, offset: 0, bytes: 65536, sha256: await client.sha256(file, webcrypto)};
  await assert.rejects(client.verifyPrefix(file, receipt, async () => [{...good, sha256: '0'.repeat(64)}], blob => client.sha256(blob, webcrypto)), /MISMATCH/);
  await assert.rejects(client.verifyPrefix(file, receipt, async () => []), /MISSING/);
  await assert.rejects(client.verifyPrefix(file, receipt, async () => [{...good, offset: 1}]), /CONFLICT/);
  await assert.rejects(client.verifyPrefix(file, {...receipt, fileName: 'other.xml'}, async () => [good]), /MISMATCH/);
});

test('recovery controls retry a fatal review but block normal pending validation and unresolved drafts', () => {
  assert.equal(client.recoveryControls({pendingReviewCount: 1}).resumeDisabled, true);
  assert.equal(client.recoveryControls({inactiveDraftCount: 1}).resumeDisabled, true);
  assert.deepEqual(client.recoveryControls({pendingReviewCount: 1, reviewErrorCode: 'REVIEW_WORKER_FATAL'}),
    {fatalReview: true, resumePending: false, resumeDisabled: false});
  assert.equal(client.recoveryControls({workerAlert: 'REVIEW_WORKER_FATAL'}).fatalReview, true);
  assert.equal(client.recoveryControls({reviewErrorCode: 'REVIEW_WORKER_FATAL', supervisorAlert: 'IMPORT_SUPERVISOR_FATAL'}).resumeDisabled, true);
  assert.equal(client.recoveryControls({maintenanceReleasePending: true, maintenanceReleaseState: 'RELEASE_DEFERRED'}).resumeDisabled, false);
});

test('queued resume is typed pending intent and disables duplicates independently of owner phase', () => {
  assert.deepEqual(client.recoveryControls({resumeRequested: true}),
    {fatalReview: false, resumePending: true, resumeDisabled: true});
  for (const resumeRequested of [false, undefined, 'true']) {
    assert.equal(client.recoveryControls({resumeRequested}).resumePending, false);
  }
  for (const state of ['RUNNING', 'INCOMPLETE', 'REVIEW_REQUIRED']) {
    assert.equal(client.recoveryControls({state, resumeRequested: true}).resumeDisabled, true);
  }
});

test('missing or untyped recovery status requires refreshed server status', () => {
  for (const resumeRequested of [undefined, 'false', 0]) {
    assert.deepEqual(client.statusPresentation({id, state: 'COMPLETE'}, {...clean, resumeRequested}),
      {state: 'UNAVAILABLE', clean: false, final: false});
  }
});

test('status permalinks contain only a validated job handle and the application context', () => {
  assert.equal(client.jobStatusUrl('/carlos',id), '/carlos/form/importUpload?runId='+id);
  assert.equal(client.jobStatusUrl('',id), '/form/importUpload?runId='+id);
  assert.equal(client.jobStatusUrl('/carlos-2026.08.0-alpha20-SNAPSHOT',id), '/carlos-2026.08.0-alpha20-SNAPSHOT/form/importUpload?runId='+id);
  for(const context of ['https://example.test','//example.test','/carlos?CSRF-TOKEN=private','/carlos#private','/../private','/carlos/../private','/%2e%2e'])
    assert.throws(()=>client.jobStatusUrl(context,id),/IMPORT_STATUS_LINK_INVALID/);
  for(const changed of ['',undefined,id+'?CSRF-TOKEN=private','../private'])
    assert.throws(()=>client.jobStatusUrl('/carlos',changed),/IMPORT_STATUS_LINK_INVALID/);
});
const dashboardSummary = () => ({...clean,dryRun:false,inventoryState:'SEALED',sourcePatients:1,
  categories:[{category:'Demographics',expected:1},{category:'ClinicalNotes',expected:3}],
  committedClinicalOccurrences:4,alreadyCompleteClinicalOccurrences:0,committedUnits:8,alreadyCompleteUnits:0,expectedUnits:8});

test('the dashboard distinguishes source patients, patient copies, clinical records and helper work', () => {
  const value=client.jobPresentation({id,state:'COMPLETE'},{...dashboardSummary(),expectedPatients:2,outcomes:{COMPLETE:2}});
  assert.equal(value.sourcePatients,'1');assert.equal(value.patients,'2 / 2');
  assert.equal(value.records,'4 / 4');assert.equal(value.imported,'4');assert.equal(value.prior,'0');
  assert.deepEqual(value.progress,{value:4,maximum:4});assert.equal(value.clean,true);
});
test('unknown, unsealed, malformed and zero clinical totals never become a completion percentage', () => {
  for(const changed of [{inventoryState:'UNSEALED'},{discoveryComplete:false},{categories:[]},
    {categories:[{expected:0}]},{categories:[{expected:-1}]},{categories:[{expected:NaN}]},
    {categories:[{expected:Number.MAX_SAFE_INTEGER},{expected:1}]}]) {
    const view=client.jobPresentation({id,state:'RUNNING'},{...dashboardSummary(),...changed});
    assert.equal(view.progress,null);assert.equal(view.clean,false);
  }
  assert.equal(client.jobPresentation(null,null).clean,false);
});
test('all saved records remain finalizing until reports, cleanup, recovery and counts are confirmed', () => {
  for(const state of ['COMPLETE','COMPLETE_WITH_NONLOSSY_WARNINGS','ALREADY_COMPLETE']) {
    for(const changed of [{reportsDelivered:false},{cleanupComplete:false},{resumeRequested:true},
      {pendingUnits:1},{unresolvedReviewItems:1},{categories:[{expected:5}]}]) {
      const view=client.jobPresentation({id,state},{...dashboardSummary(),...changed});
      assert.equal(view.clean,false);assert.equal(view.progress,null);
      assert.doesNotMatch(view.stage,/^Import complete|^Previously imported/);
    }
  }
});
test('preview completion never becomes clinical success even when its durable clean flag is true', () => {
  for(const french of [false,true]) {
    const value=client.jobPresentation({id,state:'DRY_RUN_COMPLETE'},{...dashboardSummary(),dryRun:true,cleanComplete:true,
      committedClinicalOccurrences:0,committedUnits:0,pendingUnits:8,outcomes:{PLANNED:1}},french);
    assert.equal(value.preview,true);assert.equal(value.clean,false);assert.equal(value.progress,null);
    assert.match(value.stage,french?/aucune donnée clinique importée/:/no clinical data imported/);
  }
});
test('clinical progress counts verified prior records without treating an uploaded file as an import', () => {
  const view=client.jobPresentation({id,state:'RUNNING'},{...dashboardSummary(),committedClinicalOccurrences:1,
    alreadyCompleteClinicalOccurrences:1,pendingUnits:4,cleanComplete:false});
  assert.deepEqual(view.progress,{value:2,maximum:4});assert.equal(view.records,'2 / 4');
  assert.match(client.uploadPresentation('COMPLETE'),/File received/);
  assert.doesNotMatch(client.uploadPresentation('COMPLETE'),/Import complete/);
  assert.match(client.uploadPresentation('COMPLETE',true),/Fichier reçu/);
});

test('terminal report or cleanup failures require attention rather than an endless finishing state', () => {
  for(const changed of [{reportDeliveryFailed:true,reportState:'FAILED',reportsDelivered:false},
    {cleanupState:'FAILED',cleanupComplete:false},{maintenanceReleaseState:'RELEASE_FAILED',cleanupComplete:false}]) {
    const view=client.jobPresentation({id,state:'COMPLETE'},{...dashboardSummary(),...changed,operationTerminal:true});
    assert.equal(view.clean,false);assert.equal(view.attention,true);assert.equal(view.progress,null);
    assert.match(view.stage,/attention needed/);
    assert.match(changed.reportDeliveryFailed?view.reports:view.cleanup,/Failed/);
  }
  const preview=client.jobPresentation({id,state:'DRY_RUN_COMPLETE'},{...dashboardSummary(),dryRun:true,
    operationTerminal:true,reportState:'FAILED',reportDeliveryFailed:true,reportsDelivered:false});
  assert.equal(preview.clean,false);assert.equal(preview.attention,true);
  assert.match(preview.stage,/reports or cleanup need attention; no clinical data imported/);
});

test('real long-running engine phases have readable labels without inventing write activity or timing', () => {
  for(const state of ['PREFLIGHTING','READY','REPLANNING','RESUME_REQUESTED','DISCOVERING','VALIDATING','PREFLIGHT','PAUSED','REPORTING']) {
    assert.notEqual(client.friendlyState(state),'Check job details');
    assert.notEqual(client.friendlyState(state,true),'Vérifier les détails de la tâche');
  }
  assert.match(client.friendlyState('RUNNING'),/Processing/);
  const failed=client.jobPresentation({id,state:'INCOMPLETE'},{...dashboardSummary(),clinicalComplete:false,cleanComplete:false,
    operationTerminal:true,reportState:'FAILED',reportDeliveryFailed:true,reportsDelivered:false,committedClinicalOccurrences:0});
  assert.match(failed.stage,/Import needs attention/);assert.doesNotMatch(failed.stage,/Data saved/);
});

test('clean imports still surface preserved patient warnings without inventing a warning total', () => {
  const view=client.jobPresentation({id,state:'COMPLETE'},{...dashboardSummary(),outcomes:{COMPLETE_WITH_NONLOSSY_WARNINGS:1}});
  assert.equal(view.clean,true);assert.match(view.stage,/review warnings/);assert.equal(view.reviews,'0');
});


test('known acceptance remains counted once when its first status read fails, with no replacement submission', async () => {
  const requests=[];let acceptedCount=0;let remembered;let bookmark;
  const post=async()=>{requests.push({method:'POST',kind:'accept'});return{accepted:true,job:{id}};};
  const response=await post();
  const accepted=await client.monitorAcceptedRun(response,async handle=>{
    remembered=handle;bookmark=client.jobStatusUrl('/carlos',handle);
    requests.push({method:'GET',kind:'status',handle});throw Error('HTTP503');
  });
  acceptedCount++;
  assert.equal(acceptedCount,1);assert.equal(accepted.id,id);assert.equal(accepted.statusAvailable,false);
  assert.equal(remembered,id);assert.equal(bookmark,'/carlos/form/importUpload?runId='+id);
  assert.deepEqual(requests,[{method:'POST',kind:'accept'},{method:'GET',kind:'status',handle:id}]);
  const recovered=await client.monitorAcceptedRun(response,async handle=>{assert.equal(handle,id);});
  assert.equal(recovered.statusAvailable,true);assert.equal(recovered.id,id);
});
test('an ambiguous POST failure or unconfirmed receipt cannot manufacture acceptance', async () => {
  let monitored=0,acceptedCount=0;
  const submit=async()=>{throw Error('POST outcome unknown');};
  await assert.rejects(async()=>{await client.monitorAcceptedRun(await submit(),async()=>{monitored++;});acceptedCount++;},/POST outcome unknown/);
  for(const receipt of [undefined,{}, {accepted:false,job:{id}},{accepted:true,job:{id:'unknown'}}])
    await assert.rejects(client.monitorAcceptedRun(receipt,async()=>{monitored++;}),/IMPORT_ACCEPTANCE_UNAVAILABLE/);
  assert.equal(monitored,0);assert.equal(acceptedCount,0);
});
