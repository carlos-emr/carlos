/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the section assertions cds-export-patient-sections-playwright-checks.js makes on a live export, so a
// weakened assertion fails here without a deployment.
const test = require('node:test');
const assert = require('node:assert/strict');
const { SECTION_CHECKS, heightsOf, tagNames } = require('./cds-export-patient-sections-playwright-checks');

const WANT = {
  allergy: 'FAKE-ALLERGEN t1', reaction: 'FAKE rash t1', drug: 'FAKE-DRUG t1', immunization: 'FAKE-TD t1',
  purpose: 'FAKE visit t1', height: '170', today: '2026-10-17',
};

// The shape the export writes: entries in the main schema's namespace, dates and Height parts in the datatypes one.
const SECTIONS = {
  allergy: ({ reaction = WANT.reaction } = {}) => '<cds:AllergiesAndAdverseReactions>'
    + `<cds:OffendingAgentDescription>${WANT.allergy}</cds:OffendingAgentDescription><cds:Reaction>${reaction}</cds:Reaction>`
    + '</cds:AllergiesAndAdverseReactions>',
  medication: ({ written = `<cdsd:FullDate>${WANT.today}</cdsd:FullDate>` } = {}) => '<cds:MedicationsAndTreatments>'
    + `<cds:PrescriptionWrittenDate>${written}</cds:PrescriptionWrittenDate><cds:DrugName>${WANT.drug}</cds:DrugName>`
    + '</cds:MedicationsAndTreatments>',
  immunization: ({ date = WANT.today } = {}) => '<cds:Immunizations>'
    + `<cds:ImmunizationName>${WANT.immunization}</cds:ImmunizationName><cds:ImmunizationType>Td</cds:ImmunizationType>`
    + `<cds:Date><cdsd:FullDate>${date}</cdsd:FullDate></cds:Date><cds:RefusedFlag><cdsd:boolean>false</cdsd:boolean></cds:RefusedFlag>`
    + '</cds:Immunizations>',
  appointment: ({ time = '10:30:00-04:00' } = {}) => '<cds:Appointments>'
    + `<cds:AppointmentTime>${time}</cds:AppointmentTime><cds:Duration>14</cds:Duration>`
    + `<cds:AppointmentDate><cdsd:FullDate>${WANT.today}</cdsd:FullDate></cds:AppointmentDate>`
    + `<cds:AppointmentPurpose>${WANT.purpose}</cds:AppointmentPurpose></cds:Appointments>`,
  height: ({ unit = 'cm' } = {}) => '<cds:CareElements><cds:Height>\n'
    + `  <cdsd:Height>${WANT.height}</cdsd:Height>\n  <cdsd:HeightUnit>${unit}</cdsd:HeightUnit>\n`
    + `  <cdsd:Date>${WANT.today}</cdsd:Date>\n</cds:Height></cds:CareElements>`,
};

function exportXml(overrides = {}) {
  const body = Object.entries(SECTIONS).map(([name, build]) => (name in overrides ? overrides[name] : build())).join('');
  return '<?xml version="1.0"?><cds:OmdCds xmlns:cds="cds" xmlns:cdsd="cdsd"><cds:PatientRecord>'
    + `${body}</cds:PatientRecord></cds:OmdCds>`;
}

const checkOf = name => SECTION_CHECKS[name].check;

test('a complete export passes every section check', () => {
  assert.deepEqual(Object.keys(SECTION_CHECKS), Object.keys(SECTIONS));
  for (const { check } of Object.values(SECTION_CHECKS)) check(exportXml(), WANT);
});

test('each section check fails when its entry is missing or doubled', () => {
  for (const [name, build] of Object.entries(SECTIONS)) {
    assert.throws(() => checkOf(name)(exportXml({ [name]: '' }), WANT), undefined, `${name} missing`);
    assert.throws(() => checkOf(name)(exportXml({ [name]: build() + build() }), WANT), undefined, `${name} doubled`);
  }
});

test('a wrong field fails its section', () => {
  const wrong = {
    allergy: SECTIONS.allergy({ reaction: 'FAKE hives' }),
    medication: SECTIONS.medication({ written: '<cdsd:FullDate>2026-10-16</cdsd:FullDate>' }),
    immunization: SECTIONS.immunization({ date: '2026-10-16' }),
    appointment: SECTIONS.appointment({ time: '10:00:00' }),
    height: SECTIONS.height({ unit: 'in' }),
  };
  for (const [name, section] of Object.entries(wrong)) {
    assert.throws(() => checkOf(name)(exportXml({ [name]: section }), WANT), undefined, name);
  }
});

test('a written date given as a date-time counts as its day', () => {
  const xml = exportXml({ medication: SECTIONS.medication({ written: `<cdsd:FullDateTime>${WANT.today}T00:00:00</cdsd:FullDateTime>` }) });
  checkOf('medication')(xml, WANT);
});

test('a Height reading is read whatever prefix its parts carry', () => {
  const reading = '<Height><x:Height>170</x:Height><x:HeightUnit>cm</x:HeightUnit><x:Date>2026-10-17</x:Date></Height>';
  assert.deepEqual(heightsOf(reading), [{ value: '170', unit: 'cm', date: '2026-10-17' }]);
});

test('the failure detail names elements without their values', () => {
  assert.deepEqual(tagNames(SECTIONS.allergy()), ['AllergiesAndAdverseReactions', 'OffendingAgentDescription', 'Reaction']);
});
