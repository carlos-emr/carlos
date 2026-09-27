/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { assert, sqlString } = require('./lib/playwright-harness');
const { stageRxFaxAccount, cleanupRxFaxAccount } = require('./rx-fax-account-fixture');

/** Own all clinical fixtures for one pharmacy-phone journey, with independently testable teardown. */
function createFaxPhoneFixtures({ db, config, marker, fromFaxNumber, drugNamePrefix, artifactDirectories, expectedProvider }) {
  let demographicNo;
  let providerNo;
  let pharmacyId;
  let faxConfig;
  /** Create the patient and pharmacy only after resolving the authenticated provider. */
  function stagePatientAndPharmacy() {
    providerNo = db.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)}`);
    assert(/^\d+$/.test(providerNo), 'The test login must have a numeric provider');
    if (expectedProvider) {
      assert(providerNo === expectedProvider.trim(), 'RX_FAX_PROVIDER_NO does not match the login');
    }
    demographicNo = db.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${sqlString(marker)},'FaxPhone','1980','01','02','F','AC',
      ${sqlString(providerNo)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(demographicNo), 'Owned patient was not created');
    pharmacyId = db.value(`INSERT INTO pharmacyInfo (name,address,city,province,postalCode,fax,status,uid)
      VALUES (${sqlString(marker)},'1 Fixture Lane','Fixture City','ON','M1A 1A1','5550100100','1',0);
      SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(pharmacyId), 'Owned pharmacy was not created');
    db.execute(`INSERT INTO demographicPharmacy (pharmacyID,demographic_no,status,preferredOrder)
      VALUES (${pharmacyId},${demographicNo},'1',0)`);
  }

  const sqlValue = (value) => (value === null ? 'NULL' : sqlString(value));

  /** Update only the contact fields of the pharmacy created by this fixture. */
  function stagePharmacyPhones(testCase) {
    assert(db.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${pharmacyId} AND name=${sqlString(marker)}`) === '1',
      'Pharmacy fixture ownership changed');
    db.execute(`UPDATE pharmacyInfo SET fax=${sqlString(testCase.pharmacyFax)}, phone1=${sqlValue(testCase.phone1)},
      phone2=${sqlValue(testCase.phone2)} WHERE recordID=${pharmacyId} AND name=${sqlString(marker)}`);
  }

  /** Attempt all owned cleanup groups; retain the patient if any group fails. */
  function cleanupFixtures() {
    const failures = [];
    const attempt = (label, fn) => {
      try { fn(); } catch (error) { failures.push(label); }
    };
    if (!db || !demographicNo) return;
    assert(db.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${demographicNo} AND last_name=${sqlString(marker)}`) === '1',
      'Patient fixture ownership changed');
    let scriptNos = [];
    attempt('list fixture scripts', () => {
      scriptNos = db.rows(`SELECT DISTINCT script_no FROM drugs WHERE customName LIKE ${sqlString(`${drugNamePrefix} %`)} AND demographic_no=${demographicNo};`)
        .map(([n]) => String(n)).filter((n) => /^\d+$/.test(n));
    });
    for (const scriptNo of scriptNos) {
      attempt(`prescription ${scriptNo}`, () => {
        const sigId = db.value(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${scriptNo};`);
        db.execute(`DELETE FROM drugs WHERE script_no=${scriptNo};`);
        db.execute(`DELETE FROM prescription WHERE script_no=${scriptNo};`);
        if (/^\d+$/.test(String(sigId))) db.execute(`DELETE FROM DigitalSignature WHERE id=${sigId} AND demographicId=${demographicNo} AND moduleType='PRESCRIPTION' AND NOT EXISTS (SELECT 1 FROM prescription WHERE digital_signature_id=${sigId});`);
      });
    }
    attempt('faxes', () => {
      const faxIds = db.rows(`SELECT id FROM faxes WHERE faxline=${sqlString(fromFaxNumber)} AND demographicNo=${demographicNo};`)
        .map(([id]) => String(id)).filter((id) => /^\d+$/.test(id));
      if (faxIds.length) {
        db.execute(`DELETE FROM FaxClientLog WHERE transactionType='RX' AND faxId IN (${faxIds.map((id) => `'${id}'`).join(',')});`);
      }
      const filenames = db.rows(`SELECT filename FROM faxes WHERE faxline=${sqlString(fromFaxNumber)} AND demographicNo=${demographicNo}`);
      for (const [filename] of filenames) {
        assert(/^prescription_[a-zA-Z0-9_-]{1,128}\.pdf$/.test(filename), 'Owned fax filename is invalid');
        for (const directory of new Set(artifactDirectories)) {
          for (const suffix of ['.pdf', '.txt']) {
            const target = path.join(directory, filename.replace(/\.pdf$/, suffix));
            if (!fs.existsSync(target)) continue;
            assert(fs.lstatSync(target).isFile() && !fs.lstatSync(target).isSymbolicLink(), 'Owned fax artifact is not a regular file');
            fs.unlinkSync(target);
          }
        }
      }
      db.execute(`DELETE FROM faxes WHERE faxline=${sqlString(fromFaxNumber)} AND demographicNo=${demographicNo};`);
    });
    attempt('fax_config', () => {
      cleanupRxFaxAccount(db, faxConfig);
    });
    attempt('owned encounter notes', () => {
      const notes = db.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${demographicNo}`);
      for (const [id] of notes) {
        assert(/^[1-9]\d*$/.test(id), 'Invalid owned note ID');
        db.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id=${id};
          DELETE FROM casemgmt_note_ext WHERE note_id=${id};
          DELETE FROM casemgmt_note_link WHERE note_id=${id};
          DELETE FROM casemgmt_note WHERE note_id=${id} AND demographic_no=${demographicNo}`);
      }
    });
    attempt('owned pharmacy', () => {
      if (!pharmacyId) return;
      assert(db.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${pharmacyId} AND name=${sqlString(marker)}`) === '1',
        'Pharmacy fixture ownership changed');
      assert(db.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${pharmacyId} AND demographic_no<>${demographicNo}`) === '0',
        'Owned pharmacy is now associated with another patient');
      db.execute(`DELETE FROM demographicPharmacy WHERE pharmacyID=${pharmacyId} AND demographic_no=${demographicNo};
        DELETE FROM pharmacyInfo WHERE recordID=${pharmacyId} AND name=${sqlString(marker)}`);
      assert(db.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${pharmacyId}`) === '0', 'Owned pharmacy cleanup failed');
    });
    attempt('remaining owned children', () => {
      const remaining = db.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${demographicNo})
        +(SELECT COUNT(*) FROM prescription WHERE demographic_no=${demographicNo})
        +(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${demographicNo})
        +(SELECT COUNT(*) FROM faxes WHERE demographicNo=${demographicNo})
        +(SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${demographicNo})`);
      assert(remaining === '0', 'Owned clinical fixture rows remain');
    });
    if (failures.length) {
      throw new Error(`fixture cleanup failed for: ${failures.join(', ')}`);
    }
  }

  return {
    stage() { stagePatientAndPharmacy(); faxConfig = stageRxFaxAccount(db, fromFaxNumber); },
    stagePharmacyPhones,
    cleanup: cleanupFixtures,
    get patient() { return demographicNo; },
    get provider() { return providerNo; },
  };
}
module.exports = { createFaxPhoneFixtures };
