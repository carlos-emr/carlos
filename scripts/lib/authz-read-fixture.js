/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Role-matrix fixture for the authz-read-* checks ("does a read route serve data to a
 * login whose role lacks the sec object it needs?").
 *
 * Like scripts/lib/throwaway-login-fixture.js this copies the shared test login's `security`
 * row (bcrypt hash, PIN, flags), so every login it creates signs in with config.testPassword /
 * config.testPin -- but each login receives ONE role of the caller's choosing (any name from
 * secRole) instead of the test login's roles, and a run can own several logins at once.
 * Every row it writes carries a provider number drawn from 800000-899999 and the run marker
 * as the provider's last name; cleanup deletes by those owned keys only and asserts they are
 * gone, including the rows the application itself writes for the login (audit `log`,
 * provider_facility, ProviderPreference*, property, program_provider, providersite).
 *
 * Patient locks follow the convention of hrm-report-print-download-playwright-checks.js: a
 * `secObjPrivilege` row (roleUserGroup = the login's provider number, objectName =
 * `_demographic$<demographic_no>` / `_eChart$<demographic_no>`, privilege `|o|`). The row is
 * keyed on the login's own provider number, so no other login is ever affected, and
 * cleanup() removes it.
 *
 * Roles are looked up in the live secRole table; a role the install does not seed makes
 * addLogin() throw SkipCheck so the runner reports SKIP rather than FAIL.
 *
 * A check that needs a role no install seeds (the write half of the role matrix: read a chart,
 * book appointments, write nothing clinical) builds one with addRole(privileges): a secRole row
 * named `FAKEPW<hex>R<n>` (described by the run marker) and exactly the secObjPrivilege rows
 * asked for, keyed on that role name. Nothing else holds the role, so no seeded login gains or
 * loses a right; cleanup() removes the logins first, then the role and its privilege rows, and
 * asserts them gone. WRITE_RESTRICTED_PRIVILEGES is that write-restricted role.
 */
const { randomInt } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { assert, sqlString, SkipCheck } = require('./playwright-harness');

const PROVIDER_LINKED_TABLES = ['provider_facility', 'program_provider', 'providersite', 'property', 'secUserRole'];
const PROVIDER_PREFERENCE_TABLES = ['ProviderPreferenceAppointmentScreenEForm', 'ProviderPreferenceAppointmentScreenForm',
  'ProviderPreferenceAppointmentScreenQuickLink', 'ProviderPreference'];

/**
 * The write-restricted role (authz-write-role-matrix): it may read a patient's demographics and
 * open the chart, and book, edit and cancel appointments -- a front-desk shape with chart read --
 * and nothing more. It holds no sec object a clinical, billing or administrative write needs.
 *
 * Nothing is added for the login itself: a login with no sec object at all signs in through the
 * form and lands on the schedule (authz-read-role-matrix proves it with er_clerk, which the
 * seed leaves without a single secObjPrivilege row), and providercontrol needs `_appointment` r,
 * which `w` already includes (OscarRoleObjectPrivilege's r < u < w hierarchy).
 */
const WRITE_RESTRICTED_PRIVILEGES = Object.freeze({ _demographic: 'r', _appointment: 'w', _eChart: 'r' });

// Privilege values the r/u/w/d/x/o grammar (OscarRoleObjectPrivilege.checkRights) understands.
const PRIVILEGE_VALUE = /^[ruwdxo]$/;
const OBJECT_NAME = /^_[A-Za-z0-9_.]{1,60}$/;

/**
 * @param sql the session's createSqlRunner result
 * @param marker the run marker (`FAKE-PW<16 hex>`)
 * @param provider provider_no of the shared test login (lastUpdateUser, orgcd source)
 * @param testUser user_name of the shared test login whose security row is copied
 */
function authzReadFixture({ sql, marker, provider, testUser }) {
  const hex = marker.replace(/^FAKE-PW/, '');
  assert(/^[0-9a-f]{16}$/.test(hex), 'The run marker is not in the FAKE-PW<hex> form this fixture relies on');
  const logins = [];
  const locks = [];
  const roles = [];
  let sequence = 0;

  function unusedProviderNo() {
    for (let attempt = 0; attempt < 30; attempt++) {
      const candidate = String(randomInt(800000, 899999));
      const c = sqlString(candidate);
      if (logins.some(login => login.providerNo === candidate)) continue;
      const taken = sql.value(`SELECT ${[
        `(SELECT COUNT(*) FROM provider WHERE provider_no=${c})`,
        `(SELECT COUNT(*) FROM security WHERE provider_no=${c})`,
        `(SELECT COUNT(*) FROM log WHERE provider_no=${c})`,
        `(SELECT COUNT(*) FROM SecurityArchive WHERE provider_no=${c})`,
        `(SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${c} OR provider_no=${c})`,
        ...PROVIDER_LINKED_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${c})`),
        ...PROVIDER_PREFERENCE_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE providerNo=${c})`),
      ].join('+')}`);
      if (taken === '0') return candidate;
    }
    throw new Error('No unused provider number was found for an authz-read login');
  }

  function removeLogin(login) {
    const providerNo = sqlString(login.providerNo);
    const user = sqlString(login.username);
    assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${providerNo} AND last_name<>${sqlString(marker)}`) === '0',
      'authz-read provider ownership changed; refusing to delete');
    assert(sql.value(`SELECT COUNT(*) FROM security WHERE provider_no=${providerNo} AND user_name<>${user}`) === '0',
      'authz-read security ownership changed; refusing to delete');
    sql.execute([
      `DELETE FROM log WHERE provider_no=${providerNo} OR (contentId=${user} AND action IN ('failed','unlock'))`,
      `DELETE FROM SecurityArchive WHERE user_name=${user} OR provider_no=${providerNo}`,
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${providerNo} AND provider_no=${providerNo}`,
      ...PROVIDER_LINKED_TABLES.map(table => `DELETE FROM ${table} WHERE provider_no=${providerNo}`),
      ...PROVIDER_PREFERENCE_TABLES.map(table => `DELETE FROM ${table} WHERE providerNo=${providerNo}`),
      `DELETE FROM security WHERE user_name=${user} AND provider_no=${providerNo}`,
      `DELETE FROM provider WHERE provider_no=${providerNo} AND last_name=${sqlString(marker)}`,
    ].join(';'));
    const remaining = [
      `(SELECT COUNT(*) FROM log WHERE provider_no=${providerNo})`,
      `(SELECT COUNT(*) FROM SecurityArchive WHERE user_name=${user} OR provider_no=${providerNo})`,
      `(SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${providerNo} OR provider_no=${providerNo})`,
      ...PROVIDER_LINKED_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${providerNo})`),
      ...PROVIDER_PREFERENCE_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE providerNo=${providerNo})`),
      `(SELECT COUNT(*) FROM security WHERE user_name=${user} OR provider_no=${providerNo})`,
      `(SELECT COUNT(*) FROM provider WHERE provider_no=${providerNo})`,
    ];
    assert(sql.value(`SELECT ${remaining.join('+')}`) === '0', 'authz-read login rows were not all removed');
  }

  function removeRole(roleName) {
    const role = sqlString(roleName);
    // A role this run made can only be held by this run's logins, which are removed first; anyone
    // else holding it means the name was reused, and nothing is deleted from under them.
    assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE role_name=${role}`) === '0',
      'A provider still holds the run\'s custom role; refusing to delete it');
    assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${role} AND IFNULL(description,'')<>${sqlString(marker)}`) === '0',
      'The custom role\'s ownership changed; refusing to delete it');
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${role};
      DELETE FROM secRole WHERE role_name=${role} AND description=${sqlString(marker)}`);
    assert(sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${role})
      + (SELECT COUNT(*) FROM secRole WHERE role_name=${role})`) === '0', 'The custom role rows were not all removed');
  }

  return {
    get logins() { return logins; },

    /** Names of the seeded roles (secRole). */
    roleNames() { return sql.rows('SELECT role_name FROM secRole ORDER BY role_name').map(row => row[0]); },

    /** True when the role has no secObjPrivilege row of its own (holds no sec object at all). */
    roleHoldsNothing(roleName) {
      return sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${sqlString(roleName)}`) === '0';
    },

    /** The `object:right` pairs a seeded role holds, e.g. ['_appointment:x']. */
    rolePrivileges(roleName) {
      return sql.rows(`SELECT objectName,privilege FROM secObjPrivilege WHERE roleUserGroup=${sqlString(roleName)}`)
        .map(row => `${row[0]}:${String(row[1]).replace(/\|/g, '')}`);
    },

    /**
     * Create a role no install seeds, holding EXACTLY `privileges` ({ objectName: 'r'|'u'|'w'|'d'|'x'|'o' }),
     * for addLogin(). Stored like the seed stores rights (a bare letter, priority 0). Returns the role name.
     */
    addRole(privileges) {
      const entries = Object.entries(privileges || {});
      assert(entries.length > 0 && entries.every(([object, right]) => OBJECT_NAME.test(object) && PRIVILEGE_VALUE.test(right)),
        'addRole needs { objectName: right } pairs with a sec object name and one right letter');
      // secObjPrivilege.roleUserGroup is varchar(30): FAKEPW + 16 hex + R + a digit fits.
      const roleName = `FAKEPW${hex}R${roles.length + 1}`;
      const role = sqlString(roleName);
      assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${role})
        + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${role})`) === '0', 'The run\'s custom role name is already in use');
      // Registered before the first INSERT so cleanup() covers a half-built role.
      roles.push(roleName);
      sql.execute(`INSERT INTO secRole (role_name,description) VALUES (${role},${sqlString(marker)})`);
      sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no) VALUES ${entries
        .map(([object, right]) => `(${role},${sqlString(object)},${sqlString(right)},0,${sqlString(provider)})`).join(',')}`);
      const held = this.rolePrivileges(roleName).sort();
      const wanted = entries.map(([object, right]) => `${object}:${right}`).sort();
      assert(JSON.stringify(held) === JSON.stringify(wanted), 'The custom role does not hold exactly the requested privileges');
      return roleName;
    },

    /**
     * Create a login holding exactly one seeded role.
     * @return {username, providerNo, role}; sign in with config.testPassword / config.testPin.
     */
    addLogin(roleName) {
      assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${sqlString(testUser)}`) === '1',
        'The shared test login has no single security row to copy');
      if (sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${sqlString(roleName)}`) !== '1') {
        throw new SkipCheck(`The install does not seed the ${roleName} role`);
      }
      const username = `FAKEPW${hex}${String.fromCharCode(97 + sequence++)}`;
      assert(sql.value(`SELECT COUNT(*) FROM security WHERE user_name=${sqlString(username)}`) === '0',
        'A security row already carries this run\'s authz-read username');
      const providerNo = unusedProviderNo();
      const login = { username, providerNo, role: roleName };
      // Registered before the first INSERT so cleanup() covers a half-built login.
      logins.push(login);
      const p = sqlString(providerNo);
      sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
        SELECT ${p},${sqlString(marker)},${sqlString(`Role${login.username.slice(-1)}`)},provider_type,specialty,sex,'1',${sqlString(provider)},NOW()
        FROM provider WHERE provider_no=${sqlString(provider)}`);
      assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${p} AND last_name=${sqlString(marker)}`) === '1',
        'The authz-read provider row was not created');
      const securityNo = sql.value(`INSERT INTO security (user_name,password,provider_no,pin,b_ExpireSet,date_ExpireDate,
          b_LocalLockSet,b_RemoteLockSet,forcePasswordReset,passwordUpdateDate,pinUpdateDate,lastUpdateUser,lastUpdateDate,usingMfa,mfaSecret)
        SELECT ${sqlString(username)},password,${p},pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet,b_RemoteLockSet,
          0,NOW(),NOW(),${sqlString(provider)},NOW(),0,NULL
        FROM security WHERE user_name=${sqlString(testUser)}; SELECT LAST_INSERT_ID()`);
      assert(/^[1-9]\d*$/.test(securityNo), 'The authz-read security row was not created');
      sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
        SELECT ${p},${sqlString(roleName)},COALESCE(MAX(orgcd),'R0000001'),1,NOW() FROM secUserRole
        WHERE provider_no=${sqlString(provider)}`);
      assert(sql.value(`SELECT GROUP_CONCAT(role_name) FROM secUserRole WHERE provider_no=${p}`) === roleName,
        'The authz-read login did not receive exactly the requested role');
      return login;
    },

    /**
     * Put a login in the same programs (program_provider) as the shared test login, the way a clinic
     * sets up staff (PMmodule Staff). Program membership is not a sec object, but the chart files
     * every note under the provider's program (EctProgram.getProgram), and a provider in no
     * program has its note save fail for that reason before any authorization question is asked.
     * removeLogin() deletes the rows (program_provider is a provider-linked table).
     */
    joinTestLoginPrograms(login) {
      assert(logins.includes(login), 'joinTestLoginPrograms needs a login created by this fixture');
      const p = sqlString(login.providerNo);
      sql.execute(`INSERT INTO program_provider (program_id, provider_no, role_id, team_id)
        SELECT program_id, ${p}, role_id, team_id FROM program_provider WHERE provider_no=${sqlString(provider)}`);
      const joined = sql.value(`SELECT COUNT(*) FROM program_provider WHERE provider_no=${p}`);
      assert(joined === sql.value(`SELECT COUNT(*) FROM program_provider WHERE provider_no=${sqlString(provider)}`),
        'The login did not join exactly the test login\'s programs');
      return Number(joined);
    },

    /**
     * Lock a patient away from one login: |o| on `_demographic$N` and `_eChart$N` keyed on that
     * login's provider number (the shape isAllowedAccessToPatientRecord and the per-patient
     * hasPrivilege branch read). Returns the object names written.
     */
    lockPatient(login, demographicNo, objects = ['_demographic', '_eChart']) {
      assert(/^[1-9]\d*$/.test(String(demographicNo)), 'lockPatient needs a numeric demographic_no');
      assert(logins.includes(login), 'lockPatient needs a login created by this fixture');
      const written = [];
      for (const object of objects) {
        const name = `${object}$${demographicNo}`;
        assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=${sqlString(name)}
          AND roleUserGroup=${sqlString(login.providerNo)}`) === '0', 'A patient lock row already exists');
        sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
          VALUES (${sqlString(login.providerNo)},${sqlString(name)},'|o|',0,${sqlString(login.providerNo)})`);
        locks.push({ login, name });
        written.push(name);
      }
      return written;
    },

    /** Remove one login's patient locks (e.g. to prove the unlocked control). */
    unlockPatient(login, demographicNo) {
      assert(/^[1-9]\d*$/.test(String(demographicNo)), 'unlockPatient needs a numeric demographic_no');
      assert(logins.includes(login), 'unlockPatient needs a login created by this fixture');
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${sqlString(login.providerNo)}
        AND objectName IN (${sqlString(`_demographic$${demographicNo}`)},${sqlString(`_eChart$${demographicNo}`)})`);
    },

    /** Delete every owned row and prove it; safe after an addLogin() or addRole() that failed midway. */
    cleanup() {
      for (const login of logins.slice().reverse()) removeLogin(login);
      logins.length = 0;
      locks.length = 0;
      for (const roleName of roles.slice().reverse()) removeRole(roleName);
      roles.length = 0;
    },
  };
}


/**
 * Seed one owned row per chart domain for a patient the caller owns, every text field carrying
 * `<tag>-<domain>` (for example `FAKE-PW...-LOCKED-doc`), so a response that contains the string
 * proves which domain's data it served. Returns the ids a by-ID route needs plus remove().
 * Domains: note, allergy, rx, prev, meas, tickler, eform, doc, consult, appt, dx, cpp, pharmacy.
 * Only rows this call inserted (captured ids / the patient's own key) are ever deleted.
 *
 * @param demo owned demographic_no
 * @param tag text prefix, e.g. `${marker}-LOCKED`
 * @param provider provider_no stored as the author/owner
 * @param documentStore directory the application serves stored documents from; when given, a real
 *        one-page PDF named `<tag>-doc.pdf` (text `<tag>-doc page 1`) is written there so the binary
 *        document routes (display, page images) have a file to serve; removed by ids.remove()
 * @param register called with the (still empty) ids object BEFORE the first write, so the caller can
 *        keep it for cleanup even when a later insert throws; ids.remove() deletes whatever was seeded so far
 */
function seedPatientDomains({ sql, demo, tag, provider, register, documentStore }) {
  assert(/^[1-9]\d*$/.test(String(demo)), 'seedPatientDomains needs an owned numeric demographic_no');
  const d = String(demo);
  const p = sqlString(provider);
  const T = domain => sqlString(`${tag}-${domain}`);
  const ids = { demo: d };
  const owned = [];
  const files = [];
  ids.remove = () => {
    // File removal and SQL cleanup are independent: a file that will not unlink must not leave the seeded rows behind.
    // The first failure is rethrown once both have been attempted.
    let firstError = null;
    const attempt = action => { try { action(); } catch (error) { if (!firstError) firstError = error; } };
    attempt(() => {
      for (const file of files) if (fs.existsSync(file)) fs.unlinkSync(file);
      assert(!files.some(file => fs.existsSync(file)), 'Seeded document files were not removed');
    });
    attempt(() => {
      const parts = owned.map(([table, key, id]) => [`DELETE FROM ${table} WHERE ${key}=${id}`, `(SELECT COUNT(*) FROM ${table} WHERE ${key}=${id})`]);
      if (ids.doc) parts.unshift([`DELETE FROM ctl_document WHERE module='demographic' AND module_id=${d} AND document_no=${ids.doc}`,
        `(SELECT COUNT(*) FROM ctl_document WHERE document_no=${ids.doc})`]);
      if (!parts.length) return;
      sql.execute(parts.map(part => part[0]).join(';'));
      assert(sql.value(`SELECT ${parts.map(part => part[1]).join('+')}`) === '0', 'Seeded chart-domain rows were not all removed');
    });
    if (firstError) throw firstError;
  };
  if (register) register(ids);
  const track = (table, key, id) => { assert(/^[1-9]\d*$/.test(id), `${table} seed row was not created`); owned.push([table, key, id]); return id; };
  ids.note = track('casemgmt_note', 'note_id', sql.value(`INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,include_issue_innote,signing_provider_no,encounter_type,
     program_no,reporter_caisi_role,reporter_program_team,history,locked,archived,position,uuid)
    VALUES (NOW(),NOW(),${d},${p},${T('note')},1,0,${p},'',10034,'2','0',${T('note')},0,0,0,UUID()); SELECT LAST_INSERT_ID()`));
  ids.allergy = track('allergies', 'allergyid', sql.value(`INSERT INTO allergies
    (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${d},NOW(),${T('allergy')},0,${T('allergy')},0,0,NOW(),${p},1); SELECT LAST_INSERT_ID()`));
  ids.rx = track('drugs', 'drugid', sql.value(`INSERT INTO drugs
    (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,position,lastUpdateDate,dispenseInternal,script_no)
    VALUES (${p},${d},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${T('rx')},${T('rx')},0,0,NOW(),0,0); SELECT LAST_INSERT_ID()`));
  ids.prev = track('preventions', 'id', sql.value(`INSERT INTO preventions
    (demographic_no,creation_date,prevention_date,provider_no,provider_name,prevention_type,deleted,refused,never,creator,lastUpdateDate)
    VALUES (${d},NOW(),CURDATE(),${p},${T('prev')},'Flu','0','0','0',${p},NOW()); SELECT LAST_INSERT_ID()`));
  ids.meas = track('measurements', 'id', sql.value(`INSERT INTO measurements
    (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES ('WT',${d},${p},'77','kg',${T('meas')},NOW(),NOW()); SELECT LAST_INSERT_ID()`));
  ids.tickler = track('tickler', 'tickler_no', sql.value(`INSERT INTO tickler
    (demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to,creation_date)
    VALUES (${d},${T('tickler')},'A',NOW(),NOW(),${p},'Normal',${p},NOW()); SELECT LAST_INSERT_ID()`));
  ids.eform = track('eform_data', 'fdid', sql.value(`INSERT INTO eform_data
    (fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,form_data,showLatestFormOnly,patient_independent)
    VALUES (1,${T('eform')},${T('eform')},${d},1,CURDATE(),CURTIME(),${p},${T('eform')},0,0); SELECT LAST_INSERT_ID()`));
  ids.doc = track('document', 'document_no', sql.value(`INSERT INTO document
    (doctype,docClass,docdesc,docfilename,doccreator,source,program_id,updatedatetime,status,contenttype,contentdatetime,public1,observationdate,number_of_pages,restrictToProgram)
    VALUES ('others','',${T('doc')},${sqlString(`${tag}-doc.pdf`)},${p},'',0,NOW(),'A','application/pdf',NOW(),0,CURDATE(),1,0); SELECT LAST_INSERT_ID()`));
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${d},${ids.doc},'A')`);
  if (documentStore) {
    const { textPdf } = require('./stored-pdf-documents');
    const file = path.join(documentStore, `${tag}-doc.pdf`);
    files.push(file); // registered before the write so a failed write is still cleaned up
    const owner = fs.statSync(documentStore);
    fs.writeFileSync(file, textPdf(`${tag}-doc`), { flag: 'wx', mode: 0o640 });
    fs.chownSync(file, owner.uid, owner.gid);
  }
  ids.consult = track('consultationRequests', 'requestId', sql.value(`INSERT INTO consultationRequests
    (referalDate,reason,clinicalInfo,providerNo,demographicNo,status,urgency,lastUpdateDate)
    VALUES (CURDATE(),${T('consult')},${T('consult')},${p},${d},'1','2',NOW()); SELECT LAST_INSERT_ID()`));
  ids.appt = track('appointment', 'appointment_no', sql.value(`INSERT INTO appointment
    (provider_no,appointment_date,start_time,end_time,name,demographic_no,program_id,notes,reason,status,createdatetime,updatedatetime,creator,lastupdateuser)
    VALUES (${p},DATE_ADD(CURDATE(),INTERVAL 40 DAY),'09:00:00','09:14:00',${T('appt')},${d},0,${T('appt')},${T('appt')},'t',NOW(),NOW(),${p},${p}); SELECT LAST_INSERT_ID()`));
  ids.dx = track('dxresearch', 'dxresearch_no', sql.value(`INSERT INTO dxresearch
    (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${d},CURDATE(),NOW(),'A','250','icd9',0,${p}); SELECT LAST_INSERT_ID()`));
  ids.cpp = track('casemgmt_cpp', 'id', sql.value(`INSERT INTO casemgmt_cpp
    (demographic_no,provider_no,socialHistory,familyHistory,medicalHistory,ongoingConcerns,reminders,update_date)
    VALUES (${d},${p},${T('cpp')},${T('cpp')},${T('cpp')},${T('cpp')},${T('cpp')},NOW()); SELECT LAST_INSERT_ID()`));
  const pharmacyId = track('pharmacyInfo', 'recordID', sql.value(`INSERT INTO pharmacyInfo
    (name,address,city,province,postalCode,phone1,fax,notes,addDate,status,uid)
    VALUES (${T('pharmacy')},${T('pharmacy')},'Testville','ON','A1A1A1','5550100','5550101',${T('pharmacy')},NOW(),'1',${900000 + (Number(d) % 99999)}); SELECT LAST_INSERT_ID()`));
  ids.pharmacy = track('demographicPharmacy', 'id', sql.value(`INSERT INTO demographicPharmacy
    (pharmacyID,demographic_no,status,addDate,preferredOrder,consentToContact)
    VALUES (${pharmacyId},${d},'1',NOW(),1,0); SELECT LAST_INSERT_ID()`));
  return ids;
}

/**
 * Run every cleanup action even when an earlier one throws, then rethrow the first failure, so one
 * failed teardown (a login row that will not delete) cannot leave the other owned rows behind.
 */
function cleanupAll(...actions) {
  const failures = [];
  for (const action of actions) {
    try { action(); } catch (error) { failures.push(error); }
  }
  if (failures.length) {
    const first = failures[0];
    if (failures.length > 1) first.message += ` (+${failures.length - 1} more cleanup failure(s): ${failures.slice(1).map(error => error.message).join('; ')})`;
    throw first;
  }
}

module.exports = { authzReadFixture, seedPatientDomains, cleanupAll, WRITE_RESTRICTED_PRIVILEGES };
