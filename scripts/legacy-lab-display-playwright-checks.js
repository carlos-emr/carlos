#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The legacy lab types an OSCAR 19 import carries (MDS, old CML, PathNet "BCP"): the lab page shows
 * the report, Acknowledge records it, Forward hands it to a colleague (coverage plan: legacy-lab-display).
 *
 * WHY THIS CHECK EXISTS. HL7 text labs have their own checks (lab-acknowledge, inbox-file-forward). The
 * three older vendors do not use that page. Each has its own display (oscarMDS/ViewSegmentDisplay,
 * lab/CA/ON/ViewCMLDisplay, lab/CA/BC/ViewLabDisplay), its own tables (mds*, lab*, hl7_*) and a toolbar
 * with an Acknowledge button (oscarMDS/UpdateStatus) and a Forward button that opens the
 * oscarMDS/ViewSelectProvider picker, which is meant to hand its choice back to the page's own
 * `reassignForm` (lab/CA/ON/Forward, oscarMDS/Forward, lab/CA/BC/Forward). A clinic that imported its
 * OSCAR 19 chart still holds these reports, and until now nothing opened one.
 *
 * HOW THE LAB IS OPENED. The three lists that link these labs (the Inbox, the chart's Labs module and the
 * patient lab list, lab/ViewDemographicLab) list a type only when CML_LABS / MDS_LABS / PATHNET_LABS is
 * "yes", and the package ships all three commented out; changing them needs a restart. So the check opens
 * the owned patient's lab list (the page that really opens these labs, with its own reportWindow()
 * opener), adds the row link that list renders for a CML / MDS / BCP lab, and CLICKS it. The lab page is
 * therefore a popup with a window.opener, which matters: Acknowledge ends in oscarMDS/Close.jsp, whose
 * first statement is `opener.location.reload()`, and Forward is the picker handing its choice to the lab
 * page. The URL is the one DemographicLab.jsp builds (demographicId, segmentID, providerNo,
 * searchProviderNo, status).
 *
 * ONE SCRIPT, THREE CHECKS. The harness stops at the first failing step and the manifest holds one
 * expectedFailure per check, so a broken page of one vendor would hide the defects of the others. The
 * script therefore runs ONE lab type per run, selected by LEGACY_LAB_TYPE (the manifest sets it with
 * envSet, as surface-audit does; each entry has its own npm alias):
 *   cml      legacy-lab-display          expectedFailure finding 184, at the CML forward step.
 *   mds      legacy-lab-display-mds      expectedFailure finding 194, at the MDS display step.
 *   pathnet  legacy-lab-display-pathnet  ends SKIP (never PASS) when the legacy BC tables are absent; on
 *                                        a BC install it would fail at its display step (finding 195).
 * Unset, the type is cml. Any other value is an error.
 *
 * STEPS, in order, for the one type of the run: seed; Display; Acknowledge; Forward picker; Forward.
 *   Display      the page-specific form is there; no error page; the patient's name, birth date and health
 *                number as the lab carries them; the result row (test name, result, flag, range, units);
 *                the link to the OWNED patient (the Msg button renders only when the routing resolved a
 *                patient, and names its demographic_no); the routing status "Not Acknowledged".
 *   Acknowledge  Acknowledge asks for a comment (a real prompt); the popup closes; the patient lab list
 *                that opened it reloads (Close.jsp); providerLabRouting holds exactly one row for the
 *                provider, status A, with the comment; opened again the page shows Acknowledged and it.
 *   Forward picker  Forward opens the picker; its provider search offers the throwaway recipient; the Forward
 *                List holds exactly that provider. The windows stay open for the next step.
 *   Forward      the picker offers a control that sends the list; pressing it makes the lab page post its
 *                Forward request; the recipient then holds a providerLabRouting row, status N, and the
 *                sender's row and every other provider's are untouched. This is the step finding 184 is
 *                pinned at, and it holds only what the finding breaks: the picker step before it holds the
 *                controls. Each stage is asserted on its own, so the failure says which one stands.
 *
 * KNOWN DEFECTS (docs/ui-tests/app-findings-log.md):
 *   184  Forward from these pages cannot be completed: SelectProvider.jsp has no Submit button, the
 *        picker looks its opener's form up under another name, and the form carries plain id lists where
 *        ReportReassign2Action reads JSON. The cml run reaches Forward and fails there.
 *   194  MDS display throws on every lab. MdsMSHDaoImpl.findMdsSementDataById builds invalid HQL and binds
 *        the wrong parameter, MDSSegmentData swallows the exception, and SegmentDisplay.jsp dereferences
 *        the providers object that was never set. The mds run fails at its display step, so its Forward
 *        step is not reached until that is fixed.
 *   195  PathNet display shows an empty report (PathnetLabTest.populateLab reads message 0). The pathnet
 *        run needs the legacy BC tables.
 *
 * PATHNET. hl7_message / hl7_msh / hl7_pid / hl7_orc / hl7_obr / hl7_obx / hl7_link are created by the BC
 * migrations only. An Ontario install has no table for a BCP lab to live in, so the pathnet run ends
 * SKIP there (h.SkipCheck from the preflight, before anything is written): it is recorded as a skip,
 * never as a pass. No BC install was used (BC work is out of scope, issue #4439).
 *
 * NOT ASSERTED, on purpose: the top E-Chart button of the CML and PathNet pages renders a missing-key
 * label (finding 196) and CML's version chain throws a ClassCastException on every view (finding 197).
 * Asserting them here would make these checks fail earlier than the defects they pin.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow, no master record opened); one lab of the run's type
 * seeded by SQL with synthetic names and a synthetic HIN, under a random 10-digit number verified
 * unoccupied, routed to the test provider as New and linked to the patient;
 * and a throwaway provider login that is the Forward recipient. Cleanup removes exactly those rows by key,
 * after proving the lab header still carries this run's marker, and asserts them gone: the lab tables,
 * patientLabRouting, providerLabRouting, providerLabRoutingLock and the audit rows the pages wrote for the
 * owned lab numbers. The test login's forwarding favourites are snapshotted and restored if a Forward
 * rewrote them.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture, bundleMessage } = require('./lib/throwaway-login-fixture');

const TIMEOUT = 30000;
const q = h.sqlString;

/*
 * The step labels are literals on purpose: the manifest's expectedFailure.step is checked against the
 * script's own text (run-playwright-suite.js validateExpectedFailure), and a label assembled at run time
 * cannot be found there.
 */
const STEP = Object.freeze({
  seed: 'the owned legacy labs are seeded for the owned patient and routed to the test provider',
  CML: Object.freeze({
    display: "CML display shows the lab's values and its patient",
    acknowledge: 'CML acknowledge records status A and the comment',
    picker: 'CML forward opens the provider picker, which offers the recipient and puts it in the Forward List',
    forward: 'CML forward routes the lab to the chosen provider',
  }),
  BCP: Object.freeze({
    display: "PathNet display shows the lab's values and its patient",
    acknowledge: 'PathNet acknowledge records status A and the comment',
    picker: 'PathNet forward opens the provider picker, which offers the recipient and puts it in the Forward List',
    forward: 'PathNet forward routes the lab to the chosen provider',
  }),
  MDS: Object.freeze({
    display: "MDS display shows the lab's values and its patient",
    acknowledge: 'MDS acknowledge records status A and the comment',
    picker: 'MDS forward opens the provider picker, which offers the recipient and puts it in the Forward List',
    forward: 'MDS forward routes the lab to the chosen provider',
  }),
});

/** The lab type each LEGACY_LAB_TYPE value runs, and the manifest name the run reports under. */
const MODES = Object.freeze({
  cml: Object.freeze({ type: 'CML', name: 'legacy-lab-display' }),
  mds: Object.freeze({ type: 'MDS', name: 'legacy-lab-display-mds' }),
  pathnet: Object.freeze({ type: 'BCP', name: 'legacy-lab-display-pathnet' }),
});

/** The run's mode from LEGACY_LAB_TYPE (cml when unset); anything unknown is an error, not a default. */
function modeFrom(value) {
  const key = (value === undefined || value === '') ? 'cml' : String(value).trim().toLowerCase();
  h.assert(Object.prototype.hasOwnProperty.call(MODES, key),
    `LEGACY_LAB_TYPE must be one of ${Object.keys(MODES).join(', ')}; got "${value}"`);
  return MODES[key];
}

const PATHNET_TABLES = ['hl7_message', 'hl7_msh', 'hl7_pid', 'hl7_orc', 'hl7_obr', 'hl7_obx', 'hl7_link'];
const MDS_TABLES = ['mdsMSH', 'mdsPID', 'mdsPV1', 'mdsZFR', 'mdsZLB', 'mdsZMN', 'mdsZRG', 'mdsOBR', 'mdsOBX', 'mdsNTE', 'mdsZMC'];

/** True when every named table exists in the connected schema. */
function tablesExist(sql, tables) {
  return tables.every((table) => sql.value(`SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name=${q(table)}`) === '1');
}

/** Whitespace-collapsed text, for comparing what a page renders. */
const squash = (text) => String(text).replace(/\s+/g, ' ').trim();

/**
 * One unoccupied lab number. `held(id)` is the SQL that counts every row the number could already belong
 * to. The labs never share a number either: routing rows are keyed by the number alone.
 */
function freeId(sql, taken, held) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const id = randomInt(1000000000, 1800000000);
    if (!taken.has(id) && sql.value(held(id)) === '0') {
      taken.add(id);
      return id;
    }
  }
  throw new Error('No unoccupied lab number was found after 20 attempts');
}

/** Count expressions (to be added together) for rows keyed by `id` in each [table, column]. */
const counts = (id, tables) => tables.map(([table, column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${id})`);
const routingCounts = (id) => counts(id, [['patientLabRouting', 'lab_no'], ['providerLabRouting', 'lab_no'], ['providerLabRoutingLock', 'lab_no']]);

/**
 * The lab descriptor for one type. It carries its seed, what its display must show, and how to remove it.
 * `headerSql` counts the one row that carries the run's marker: ownership is proven from it before a delete.
 * `rows` is the expression counting every row the lab owns in its own tables.
 */
function describeLab({ sql, marker, hex, patient, provider, type }) {
  const taken = new Set();
  const first8 = hex.slice(0, 8);
  const last8 = hex.slice(8, 16);

  const builders = {
    MDS() {
      const mdsTables = MDS_TABLES.map((table) => [table, 'segmentID']);
      const mdsId = freeId(sql, taken, (id) => `SELECT ${[...routingCounts(id), ...counts(id, mdsTables)].join('+')}`);
      const lab = {
        key: 'MDS', title: 'MDS', anchor: 'legacy-lab-mds', id: mdsId, labType: 'MDS',
        displayPath: `/oscarMDS/ViewSegmentDisplay?demographicId=${patient}&segmentID=${mdsId}&providerNo=${provider}&searchProviderNo=${provider}&status=N`,
        forwardPath: '/oscarMDS/Forward',
        // MdsPID.patientName "marker^Workflow" renders "marker, Workflow"; the birth date as dd-MMM-yyyy; the page
        // drops the health number's first character, which it treats as a prefix.
        patient: { name: `${marker}, Workflow`, dob: '02-Jan-1980', hin: 'FAKE00000' },
        result: { name: `${marker} Glucose`, cells: ['7.7', 'HI', '3.5-5.0', 'mmol/L'] },
        headerSql: `SELECT COUNT(*) FROM mdsMSH WHERE segmentID=${mdsId} AND sendingApp=${q(marker)}`,
        rows: counts(mdsId, mdsTables).join('+'),
        deleteSql: MDS_TABLES.map((table) => `DELETE FROM ${table} WHERE segmentID=${mdsId}`),
        seed() {
          // messageConID is "<client>-<accession>"; the marker itself has a hyphen, so it goes in sendingApp.
          sql.execute(`INSERT INTO mdsMSH(segmentID,sendingApp,dateTime,type,messageConID,processingID,versionID,acceptAckType,appAckType,demographic_no)
            VALUES(${mdsId},${q(marker)},NOW(),'ORU^R01',${q(`C${first8}-A${first8}`)},'P','2.3','AL','NE',${patient})`);
          sql.execute(`INSERT INTO mdsPID(segmentID,intPatientID,altPatientID,patientName,dOB,sex,homePhone,healthNumber)
              VALUES(${mdsId},'FAKEPID','FAKEALT',${q(`${marker}^Workflow`)},'19800102','F','555-0100','XFAKE00000');
            INSERT INTO mdsPV1(segmentID,patientClass,patientLocation,refDoctor,conDoctor,admDoctor,vNumber,accStatus,admDateTime)
              VALUES(${mdsId},'O','FAKELOC','000000^FakeRef^Alex^^^Dr','','','V1','A','20260101');
            INSERT INTO mdsZFR(segmentID,reportForm,reportFormStatus,testingLab,medicalDirector,editFlag,abnormalFlag)
              VALUES(${mdsId},'1','1','FK','FAKEDIR','','');
            INSERT INTO mdsZLB(segmentID,labID,labIDVersion,labAddress,primaryLab,primaryLabVersion,MDSLU,MDSLV)
              VALUES(${mdsId},'FK','1','FAKE ADDR','FK','1','a','b');
            INSERT INTO mdsZRG(segmentID,reportSequence,reportGroupID,reportGroupVersion,reportFlags,reportGroupDesc,MDSIndex,reportGroupHeading)
              VALUES(${mdsId},'1','FKGRP','1','','FAKE CHEMISTRY','1','');
            INSERT INTO mdsZMN(segmentID,resultMnemonic,resultMnemonicVersion,reportName,units,cumulativeSequence,referenceRange,resultCode,reportForm,reportGroup,reportGroupVersion)
              VALUES(${mdsId},'FKGLU','1',${q(`${marker} Glucose`)},'mmol/L','1','3.5-5.0','FKGLU','1','1','1');
            INSERT INTO mdsOBR(segmentID,obrID,placerOrderNo,universalServiceID,observationDateTime,specimenRecDateTime,fillerFieldOne,quantityTiming)
              VALUES(${mdsId},1,'FAKEORD','FAKE^SVC','20260101080000','20260101080000','','R');
            INSERT INTO mdsOBX(segmentID,obxID,valueType,observationIden,observationSubID,observationValue,abnormalFlags,observationResultStatus,producersID,associatedOBR)
              VALUES(${mdsId},1,'NM','XFKGLU^Glucose','FKGLU','7.7','HI','F','FK^Fake',1)`);
        },
      };
      return lab;
    },
    CML() {
      const cmlTables = [['labPatientPhysicianInfo', 'id'], ['labTestResults', 'labPatientPhysicianInfo_id']];
      const cmlLink = (id) => `(SELECT COUNT(*) FROM labRequestReportLink WHERE report_table='labPatientPhysicianInfo' AND report_id=${id})`;
      const cmlId = freeId(sql, taken, (id) => `SELECT ${[...routingCounts(id), ...counts(id, cmlTables), cmlLink(id)].join('+')}`);
      const cmlInfoId = freeId(sql, taken, (id) => `SELECT ${counts(id, [['labReportInformation', 'id']]).join('+')}`);
      const lab = {
        key: 'CML', title: 'CML', anchor: 'legacy-lab-cml', id: cmlId, labType: 'CML',
        displayPath: `/lab/CA/ON/ViewCMLDisplay?demographicId=${patient}&segmentID=${cmlId}&providerNo=${provider}&searchProviderNo=${provider}&status=N`,
        forwardPath: '/lab/CA/ON/Forward',
        // CMLDisplay.jsp prints the lab's own name, birth date and health number verbatim.
        patient: { name: `${marker}, Workflow`, dob: '19800102', hin: '0000000000' },
        result: { name: `${marker} Sodium`, cells: ['150', 'A', '135 - 145', 'mmol/L'] },
        headerSql: `SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE id=${cmlId} AND patient_last_name=${q(marker)}`,
        rows: [...counts(cmlId, cmlTables), cmlLink(cmlId), ...counts(cmlInfoId, [['labReportInformation', 'id']])].join('+'),
        deleteSql: [`DELETE FROM labTestResults WHERE labPatientPhysicianInfo_id=${cmlId}`,
          `DELETE FROM labRequestReportLink WHERE report_table='labPatientPhysicianInfo' AND report_id=${cmlId}`,
          `DELETE FROM labPatientPhysicianInfo WHERE id=${cmlId}`, `DELETE FROM labReportInformation WHERE id=${cmlInfoId}`],
        seed() {
          sql.execute(`INSERT INTO labReportInformation(id,location_id,print_date,print_time,total_BType,total_CType,total_DType)
            VALUES(${cmlInfoId},'70','20260101','08:00','1','1','0')`);
          sql.execute(`INSERT INTO labPatientPhysicianInfo(id,labReportInfo_id,accession_num,physician_account_num,service_date,patient_first_name,
              patient_last_name,patient_sex,patient_health_num,patient_dob,lab_status,doc_num,doc_name,doc_addr1,doc_addr2,doc_addr3,doc_postal,
              doc_route,comment1,comment2,patient_phone,doc_phone,collection_date,lastUpdateDate)
            VALUES(${cmlId},${cmlInfoId},${q(`CA${last8}`)},'FAKEACCT','20260101','Workflow',${q(marker)},'F','0000000000','19800102','F','000000',
              'Fake Doctor','','','','','','','','555-0100','555-0101','01 JAN 26',NOW());
            INSERT INTO labTestResults(labPatientPhysicianInfo_id,line_type,title,notUsed1,notUsed2,test_name,abn,minimum,maximum,units,result,
              description,location_id,last)
            VALUES(${cmlId},'C','FAKE CHEM','','',${q(`${marker} Sodium`)},'A','135','145','mmol/L','150','','70','Y')`);
        },
      };
      return lab;
    },
    BCP() {
      const pathTables = [['hl7_message', 'message_id'], ['hl7_msh', 'message_id'], ['hl7_pid', 'pid_id'], ['hl7_pid', 'message_id'],
        ['hl7_orc', 'pid_id'], ['hl7_obr', 'pid_id'], ['hl7_obx', 'obr_id'], ['hl7_link', 'pid_id']];
      const pathId = freeId(sql, taken, (id) => `SELECT ${[...routingCounts(id), ...counts(id, pathTables)].join('+')}`);
      const lab = {
        key: 'BCP', title: 'PathNet', anchor: 'legacy-lab-bcp', id: pathId, labType: 'BCP',
        displayPath: `/lab/CA/BC/ViewLabDisplay?demographicId=${patient}&segmentID=${pathId}&providerNo=${provider}&searchProviderNo=${provider}&status=N`,
        forwardPath: '/lab/CA/BC/Forward',
        // removeCarat turns "marker^Workflow" into "marker Workflow"; the birth date prints as yyyy-MM-dd.
        patient: { name: `${marker} Workflow`, dob: '1980-01-02', hin: 'FAKE-PATHNET' },
        result: { name: `${marker} Glucose`, cells: ['7.7', 'H', '3.5-5.0', 'mmol/L'] },
        headerSql: `SELECT COUNT(*) FROM hl7_message WHERE message_id=${pathId} AND notes=${q(marker)}`,
        rows: counts(pathId, pathTables).join('+'),
        deleteSql: [`DELETE FROM hl7_obx WHERE obr_id=${pathId}`, `DELETE FROM hl7_obr WHERE pid_id=${pathId}`,
          `DELETE FROM hl7_orc WHERE pid_id=${pathId}`, `DELETE FROM hl7_link WHERE pid_id=${pathId}`,
          `DELETE FROM hl7_pid WHERE pid_id=${pathId} AND message_id=${pathId}`, `DELETE FROM hl7_msh WHERE message_id=${pathId}`,
          `DELETE FROM hl7_message WHERE message_id=${pathId} AND notes=${q(marker)}`],
        seed() {
          // The legacy projections join pid_id to message_id, so all of this lab's rows share the one number.
          sql.execute(`INSERT INTO hl7_message(message_id,date_time,notes) VALUES(${pathId},NOW(),${q(marker)})`);
          sql.execute(`INSERT INTO hl7_msh(message_id,seperator,encoding_characters,sending_facility,date_time_of_message,message_type,
              message_control_id,processing_id,version_id)
            VALUES(${pathId},'|','^~\\\\&','FAKE FACILITY',NOW(),'ORU','FAKECTRL','P','2.3');
            INSERT INTO hl7_pid(pid_id,message_id,external_id,internal_id,patient_name,date_of_birth,sex,home_number)
            VALUES(${pathId},${pathId},'FAKE-PATHNET','FAKEINT',${q(`${marker}^Workflow`)},'1980-01-02','F','555-0100');
            INSERT INTO hl7_orc(orc_id,pid_id,order_control,filler_order_number,ordering_provider)
            VALUES(${pathId},${pathId},'RE',${q(`P${last8}-1-1`)},${q(`000000^${marker}^Doc`)});
            INSERT INTO hl7_obr(obr_id,pid_id,set_id,placer_order_number,filler_order_number,universal_service_id,priority,requested_date_time,
              observation_date_time,specimen_received_date_time,ordering_provider,results_report_status_change,diagnostic_service_sect_id,
              result_status,result_copies_to,note)
            VALUES(${pathId},${pathId},'1','FAKEPLC',${q(`P${last8}-1-1`)},'FKSVC^Fake Service','R',NOW(),NOW(),NOW(),
              ${q(`000000^${marker}^Doc`)},NOW(),'CHEM','F','','');
            INSERT INTO hl7_obx(obx_id,obr_id,set_id,value_type,observation_identifier,observation_sub_id,observation_results,units,
              reference_range,abnormal_flags,observation_result_status,observation_date_time,note)
            VALUES(${pathId},${pathId},'1','NM',${q(`FKGLU^${marker} Glucose`)},'1','7.7','mmol/L','3.5-5.0','H','F',NOW(),'')`);
        },
      };
      return lab;
    },
  };
  h.assert(builders[type], `No lab builder for type ${type}`);
  return builders[type]();
}

/**
 * Remove one lab's rows by key and prove them gone. Ownership comes first: the header row must still carry
 * this run's marker. A lab that was never written (the seed step did not reach it) has nothing to remove.
 */
function removeLab({ sql, lab, patient, provider }) {
  const id = lab.id;
  const routing = `(SELECT COUNT(*) FROM patientLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${id})
    +(SELECT COUNT(*) FROM providerLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${id})
    +(SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=${id})`;
  const audit = `(SELECT COUNT(*) FROM log WHERE content='lab' AND contentId=${q(String(id))})`;
  const owned = sql.value(lab.headerSql);
  if (owned === '0' && sql.value(`SELECT ${routing}+${lab.rows}+${audit}`) === '0') return;
  h.assert(owned === '1', `The owned ${lab.title} lab header changed; refusing to delete rows keyed by its number`);
  sql.execute([
    `DELETE FROM patientLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${id} AND demographic_no=${patient}`,
    `DELETE FROM providerLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${id}`,
    `DELETE FROM providerLabRoutingLock WHERE lab_no=${id}`,
    // The audit rows the page and the acknowledgement wrote carry the lab number as their content id.
    `DELETE FROM log WHERE content='lab' AND contentId=${q(String(id))} AND provider_no=${q(provider)}
      AND (demographic_no=${patient} OR demographic_no IS NULL)`,
    ...lab.deleteSql,
  ].join(';'));
  h.assert(sql.value(`SELECT ${routing}+${lab.rows}+${audit}`) === '0', `The owned ${lab.title} lab fixture was not removed`);
}

/**
 * The pathnet run needs the legacy BC tables. Without them there is nowhere for a BCP lab to live, so the
 * run ends SKIP (never PASS) before the browser starts and before anything is written.
 */
async function preflight({ sql }, mode = modeFrom(process.env.LEGACY_LAB_TYPE)) {
  if (mode.type !== 'BCP') return;
  const absent = PATHNET_TABLES.filter((table) => !tablesExist(sql, [table]));
  if (absent.length) {
    throw new h.SkipCheck(`PathNet (BCP) labs need the legacy BC tables, which this schema lacks (${absent.join(', ')}); `
      + 'they are created by the BC migrations only');
  }
}

async function workflow(s, mode = modeFrom(process.env.LEGACY_LAB_TYPE)) {
  const { sql, config, recorder, marker, patient, provider } = s;
  const NAME = mode.name;
  const hex = marker.slice('FAKE-PW'.length);
  h.assert(/^[0-9a-f]{16}$/.test(hex), 'The run marker is not in the FAKE-PW<hex> form this check relies on');
  const recipient = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  const labs = [describeLab({ sql, marker, hex, patient, provider, type: mode.type })];
  const promptText = bundleMessage('oscarMDS.segmentDisplay.msgComment', 'Please enter a comment (max. 255 characters)');
  const recipientSearch = `${marker}, Throwaway`;
  const commentOf = (lab) => `${marker} ${lab.key} acknowledged`;

  // Routing of one lab by who holds it. "Unchanged" means no write touched the row: id, status, comment, timestamp.
  const rowsOf = (lab, scope) => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
    FROM providerLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${lab.id} AND ${scope} ORDER BY id`));
  const mine = (lab) => rowsOf(lab, `provider_no=${q(provider)}`);
  const theirs = (lab) => rowsOf(lab, `provider_no=${q(recipient.providerNo)}`);
  const others = (lab) => rowsOf(lab, `provider_no NOT IN (${q(provider)},${q(recipient.providerNo)})`);
  const statusOf = (lab, who) => sql.value(`SELECT IFNULL(GROUP_CONCAT(status ORDER BY id),'') FROM providerLabRouting
    WHERE lab_type=${q(lab.labType)} AND lab_no=${lab.id} AND provider_no=${q(who)}`);

  // Forward rewrites the sender's forwarding favourites from what the page posts; put them back as found.
  const favourites = () => JSON.stringify(sql.rows(`SELECT id, route_to_provider_no FROM providerLabRoutingFavorites
    WHERE provider_no=${q(provider)} ORDER BY id`));
  const favouritesBefore = favourites();

  // Cleanup runs last-registered first. Everything is registered before its first write.
  s.cleanup(() => recipient.cleanup());
  s.cleanup(() => {
    if (favourites() === favouritesBefore) return;
    sql.execute(`DELETE FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)}`);
    for (const [id, to] of JSON.parse(favouritesBefore)) {
      h.assert(/^[1-9]\d*$/.test(id) && /^[A-Za-z0-9]{1,6}$/.test(to), 'A snapshotted favourite has an unexpected shape');
      sql.execute(`INSERT INTO providerLabRoutingFavorites (id,provider_no,route_to_provider_no) VALUES (${id},${q(provider)},${q(to)})`);
    }
    h.assert(favourites() === favouritesBefore, 'The test login\'s forwarding favourites were not restored');
    console.log('  note: a Forward rewrote the test login\'s forwarding favourites; they were restored as found');
  });
  s.cleanup(() => {
    const failures = [];
    for (const lab of [...labs].reverse()) {
      try { removeLab({ sql, lab, patient, provider }); } catch (error) { failures.push(error.message); }
    }
    h.assert(failures.length === 0, failures.join('; '));
  });

  const opener = await s.context.newPage();

  /** The patient lab list, the page a clinician opens these labs from. */
  async function openList() {
    await h.gotoApp(opener, config.baseUrl, `/lab/ViewDemographicLab?demographicNo=${patient}`);
    await h.assertNotErrorPage(opener, 'the patient lab list');
    await opener.locator('#labResultsTbl').waitFor({ state: 'attached', timeout: TIMEOUT });
  }

  /**
   * Add the row link DemographicLab.jsp renders for this lab type (it calls the page's own reportWindow())
   * and click it. Returns the lab popup, which has the list as its opener.
   */
  async function openLab(lab, label) {
    const url = h.appUrl(config.baseUrl, lab.displayPath);
    await opener.evaluate(({ id, href }) => {
      const old = document.getElementById(id);
      if (old) old.remove();
      const link = document.createElement('a');
      link.id = id;
      link.href = `javascript:reportWindow(${JSON.stringify(href)})`;
      link.textContent = 'Legacy lab report';
      document.body.prepend(link);
    }, { id: lab.anchor, href: url });
    const popup = await s.popup(opener, opener.locator(`#${lab.anchor}`), label);
    await popup.waitForLoadState('load', { timeout: TIMEOUT });
    // The page-specific control comes before any claim about the outcome. A page that failed to render
    // says so (an error page) rather than timing out on a selector it never drew.
    const form = popup.locator('form[name="acknowledgeForm"]');
    await form.waitFor({ state: 'attached', timeout: 10000 }).catch(() => {});
    await h.assertNotErrorPage(popup, `${lab.title} lab display`);
    h.assert(await form.count() === 1, `The ${lab.title} lab page has no acknowledge form`);
    return popup;
  }

  /**
   * The popup that Close.jsp closes ends with a script load Chromium abandons (the CSRFGuard client script
   * the response filter injects). It is the browser giving up a load because its document went away, not an
   * application failure; excuse exactly that, proven by navigatedAway(), and nothing else.
   */
  function excuseAbandonedLoads() {
    for (let i = recorder.requestFailures.length - 1; i >= 0; i -= 1) {
      const entry = recorder.requestFailures[i];
      if (entry.resourceType === 'script' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/csrfguard')
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
        recorder.requestFailures.splice(i, 1);
      }
    }
  }

  /** What the lab page must show: the patient, the result row and the link to the owned patient. Returns the page text. */
  async function expectDisplay(popup, lab) {
    await h.assertNotErrorPage(popup, `${lab.title} lab display`);
    const text = squash(await popup.locator('body').innerText({ timeout: TIMEOUT }));
    for (const [what, shown] of [['name', lab.patient.name], ['birth date', lab.patient.dob], ['health number', lab.patient.hin]]) {
      h.assert(text.includes(shown), `The ${lab.title} lab page does not show the patient's ${what} the lab carries`);
    }
    const row = popup.locator('#tblDiscs tr').filter({ hasText: lab.result.name });
    const rows = await row.count();
    h.assert(rows === 1, `The ${lab.title} lab page shows ${rows} result rows for the seeded test, expected 1`);
    const cells = (await row.first().locator('td').allInnerTexts()).map(squash);
    for (const value of lab.result.cells) {
      h.assert(cells.includes(value), `The ${lab.title} result row does not show "${value}" (${cells.filter(Boolean).length} cells are filled)`);
    }
    // Msg renders only when the routing resolved a patient for the lab, and it names that patient's number.
    const msg = popup.locator('input[type="button"][value="Msg"]').first();
    h.assert(await msg.count() === 1 && (await msg.getAttribute('onclick') || '').includes(`demographic_no=${patient}`),
      `The ${lab.title} lab page is not linked to the owned patient (no Msg button names demographic_no=${patient})`);
    return text;
  }

  // ---- Fixtures ---------------------------------------------------------------------------------------------
  await s.step(STEP.seed, async () => {
    recipient.create();
    for (const lab of labs) {
      lab.seed();
      sql.execute(`INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${lab.id},${q(lab.labType)},NOW());
        INSERT INTO providerLabRouting(provider_no,lab_no,lab_type,status) VALUES(${q(provider)},${lab.id},${q(lab.labType)},'N')`);
      h.assert(sql.value(lab.headerSql) === '1', `The ${lab.title} lab header was not seeded`);
      h.assert(statusOf(lab, provider) === 'N', `The ${lab.title} lab is not routed to the test provider as New`);
      h.assert(theirs(lab) === '[]', `The recipient already holds the ${lab.title} lab`);
    }
    await openList();
  });

  // ---- Display and acknowledge --------------------------------------------------------------------------------------
  for (const lab of labs) {
    await s.step(STEP[lab.key].display, async () => {
      excuseAbandonedLoads();
      const popup = await openLab(lab, `${NAME}-${lab.key}-display`);
      try {
        const text = await expectDisplay(popup, lab);
        h.assert(text.includes('Not Acknowledged'), `The ${lab.title} lab page does not show the provider's routing as Not Acknowledged`);
      } finally {
        await popup.close().catch(() => {});
      }
    });

    await s.step(STEP[lab.key].acknowledge, async () => {
      const comment = commentOf(lab);
      const popup = await openLab(lab, `${NAME}-${lab.key}-acknowledge`);
      try {
        const sentinel = await ui.markOpener(opener);
        const reloaded = opener.waitForEvent('load', { timeout: TIMEOUT });
        reloaded.catch(() => {});
        const dialogs = await h.withExpectedDialogs(popup, async () => {
          const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
          closed.catch(() => {});
          await popup.locator('form[name="acknowledgeForm"] input[type="submit"]').first().click({ timeout: TIMEOUT });
          await closed;
        }, { promptText: comment });
        h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt' && dialogs[0].text === promptText,
          `Acknowledge on the ${lab.title} page did not ask for a comment with the expected prompt (${dialogs.map((d) => d.type).join(',') || 'no dialog'})`);
        await reloaded;
        await opener.waitForLoadState('load', { timeout: TIMEOUT });
        h.assert(await opener.evaluate((name) => window[name], sentinel.marker) === undefined, // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant
          `The patient lab list was not reloaded after the ${lab.title} acknowledgement (Close.jsp refreshes its opener)`);
        await expectValue(sql, `SELECT CONCAT(COUNT(*),':',IFNULL(MIN(status),''),':',IFNULL(MIN(comment),'')) FROM providerLabRouting
          WHERE lab_type=${q(lab.labType)} AND lab_no=${lab.id} AND provider_no=${q(provider)}`, `1:A:${comment}`,
        `Acknowledging the ${lab.title} lab did not leave exactly one routing row, status A, with the comment`);
        h.assert(theirs(lab) === '[]' && others(lab) === '[]', `Acknowledging the ${lab.title} lab touched another provider's routing`);
        // Let the abandoned load that closing the popup causes arrive, then excuse it (and again at the next step).
        await opener.waitForTimeout(500);
        excuseAbandonedLoads();
      } finally {
        await popup.close().catch(() => {});
      }
      const again = await openLab(lab, `${NAME}-${lab.key}-acknowledged`);
      try {
        const text = await expectDisplay(again, lab);
        h.assert(!text.includes('Not Acknowledged') && text.includes('Acknowledged') && text.includes(`comment : ${comment}`),
          `The ${lab.title} lab page opened again does not show it Acknowledged with the comment`);
      } finally {
        await again.close().catch(() => {});
      }
    });
  }

  // ---- Forward ------------------------------------------------------------------------------------------------------
  // Two steps per lab. The first holds everything the pinned one depends on (the lab page opens, its Forward button opens
  // the picker, the picker searches and puts the recipient in the Forward List) and leaves both windows open; the
  // pinned one holds only what finding 184 breaks (a control that sends the list back, and what comes of pressing it).
  // A page or search that fails to work therefore reads failed-elsewhere, never as the known failure.
  for (const lab of labs) {
    let held = null;
    await s.step(STEP[lab.key].picker, async () => {
      excuseAbandonedLoads();
      const beforeMine = mine(lab);
      const beforeOthers = others(lab);
      const labPage = await openLab(lab, `${NAME}-${lab.key}-forward`);
      let picker;
      try {
        picker = await s.popup(labPage, labPage.getByRole('button', { name: 'Forward', exact: true }).first(), `${NAME}-${lab.key}-picker`);
        await picker.waitForLoadState('load', { timeout: TIMEOUT });
        await picker.locator('#autocompleteprov').waitFor({ state: 'visible', timeout: TIMEOUT });
        await picker.locator('#fwdProviders').waitFor({ state: 'attached', timeout: TIMEOUT });
        await picker.locator('#autocompleteprov').fill(recipientSearch);
        const offered = picker.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: marker }).first();
        await offered.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
        h.assert(await offered.isVisible(), `The provider picker opened from the ${lab.title} page offered nothing for the recipient`);
        await offered.click();
        const listed = await picker.locator('#fwdProviders option').evaluateAll((options) => options.map((option) => option.value));
        h.assert(JSON.stringify(listed) === JSON.stringify([recipient.providerNo]),
          'Selecting the search result did not put exactly the intended provider in the Forward List');
        held = { labPage, picker, beforeMine, beforeOthers };
      } catch (error) {
        if (picker) await picker.close().catch(() => {});
        await labPage.close().catch(() => {});
        throw error;
      }
    });

    await s.step(STEP[lab.key].forward, async () => {
      const { labPage, picker, beforeMine, beforeOthers } = held;
      try {
        // The picker must offer a control that sends the Forward List back to the lab page.
        const submit = picker.locator('#submitButton')
          .or(picker.getByRole('button', { name: /^\s*(submit|forward|ok|send|route)\s*$/i }));
        h.assert(await submit.count() > 0,
          `The provider picker opened from the ${lab.title} page has no control that sends the chosen provider back to the lab page `
          + '(it offers the search box, the Forward List and Favorites lists and the >> and << buttons), so Forward cannot be completed');

        const sent = labPage.waitForRequest((request) => request.method() === 'POST'
          && new URL(request.url()).pathname.endsWith(lab.forwardPath), { timeout: 15000 }).catch(() => null);
        await submit.first().click();
        h.assert(await sent, `Pressing the picker's Submit made the ${lab.title} page post no Forward request to ${lab.forwardPath} `
          + '(the picker looks its opener\'s form up under another name)');
        await expectValue(sql, `SELECT COUNT(*) FROM providerLabRouting WHERE lab_type=${q(lab.labType)} AND lab_no=${lab.id}
          AND provider_no=${q(recipient.providerNo)}`, '1',
        `The ${lab.title} page posted a Forward request but the recipient has no routing row `
          + '(the request carries plain id lists, ReportReassign reads JSON)');
        h.assert(statusOf(lab, recipient.providerNo) === 'N', 'The recipient\'s routing row is not New');
        h.assert(mine(lab) === beforeMine, 'Forwarding changed the sender\'s own routing row');
        h.assert(others(lab) === beforeOthers, 'Forwarding changed another provider\'s routing row');
      } finally {
        if (picker) await picker.close().catch(() => {});
        await labPage.close().catch(() => {});
      }
    });
  }
}

if (require.main === module) {
  const mode = modeFrom(process.env.LEGACY_LAB_TYPE);
  runWorkflow(mode.name, (s) => workflow(s, mode), { openMaster: false, preflight: (context) => preflight(context, mode) });
}
module.exports = { workflow, preflight, modeFrom, MODES, STEP };
