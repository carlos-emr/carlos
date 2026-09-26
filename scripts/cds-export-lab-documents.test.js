/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the #3946 export contract that cds-export-lab-documents-playwright-checks.js asserts
// on a live download, so a weakened assertion fails here without a deployment.
const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('node:zlib');
const {
  buildMessage, readZip, elements, assertLabExport, invalidXmlCharacters, PDF, LONG_TEXT_RESULT,
} = require('./cds-export-lab-documents-playwright-checks');

const ACCESSION = 'PW3946-abc123';

function lab(code, value) {
  return `<cds:LaboratoryResults><cds:LabTestCode>${code}</cds:LabTestCode>`
    + `<cds:AccessionNumber>${ACCESSION}</cds:AccessionNumber>`
    + `<cds:Result><cds:Value>${value}</cds:Value></cds:Result></cds:LaboratoryResults>`;
}

function report({ media = PDF.toString('base64'), format = 'Binary', klass = 'Lab Report', reviewed = true } = {}) {
  return `<cds:Reports><cds:Format>${format}</cds:Format><cds:FileExtensionAndVersion>.pdf</cds:FileExtensionAndVersion>`
    + `<cds:Content><cds:Media>\n  ${media}\n</cds:Media></cds:Content><cds:Class>${klass}</cds:Class>`
    + (reviewed ? '<cds:ReportReviewed><cds:Name/></cds:ReportReviewed>' : '')
    + `<cds:MessageUniqueID>${ACCESSION}</cds:MessageUniqueID></cds:Reports>`;
}

function exportXml({ labs, reports }) {
  return `<?xml version="1.0"?><cds:OmdCds xmlns:cds="cds"><cds:PatientRecord>${labs.join('')}${reports.join('')}`
    + '</cds:PatientRecord></cds:OmdCds>';
}

const GOOD_LABS = [lab('GLU', '5.2'), lab('NOTE', LONG_TEXT_RESULT.slice(0, 120)), lab('SPEC', 'Hemolysedsample')];

test('a fixed export satisfies the lab document contract', () => {
  assert.deepEqual(assertLabExport(exportXml({ labs: GOOD_LABS, reports: [report()] }), { accession: ACCESSION }),
    { labs: 3, reports: 1 });
});

test('the pre-fix export shapes are each rejected', () => {
  const blobAsResult = [...GOOD_LABS, lab('PDF', PDF.toString('base64'))];
  assert.throws(() => assertLabExport(exportXml({ labs: blobAsResult, reports: [] }), { accession: ACCESSION }),
    /expected the lab's 3 discrete results/);
  assert.throws(() => assertLabExport(exportXml({ labs: GOOD_LABS, reports: [] }), { accession: ACCESSION }),
    /exactly one Reports entry/);
  const untruncated = [GOOD_LABS[0], lab('NOTE', LONG_TEXT_RESULT), GOOD_LABS[2]];
  assert.throws(() => assertLabExport(exportXml({ labs: untruncated, reports: [report()] }), { accession: ACCESSION }),
    /150 characters, expected 120/);
  const questionMark = [GOOD_LABS[0], GOOD_LABS[1], lab('SPEC', 'Hemolysed?sample')];
  assert.throws(() => assertLabExport(exportXml({ labs: questionMark, reports: [report()] }), { accession: ACCESSION }),
    /vertical tab/);
  const rawControl = [GOOD_LABS[0], GOOD_LABS[1], lab('SPEC', 'Hemolysed\u000Bsample')];
  assert.throws(() => assertLabExport(exportXml({ labs: rawControl, reports: [report()] }), { accession: ACCESSION }),
    /characters XML 1.0 forbids/);
});

test('a report that is not the embedded PDF is rejected', () => {
  const check = reports => () => assertLabExport(exportXml({ labs: GOOD_LABS, reports }), { accession: ACCESSION });
  assert.throws(check([report({ format: 'Text' })]), /not exported as Binary/);
  assert.throws(check([report({ klass: 'Other Letter' })]), /not classed as a Lab Report/);
  assert.throws(check([report({ media: Buffer.from('not the pdf').toString('base64') })]), /does not carry the embedded PDF/);
  assert.throws(check([report({ reviewed: false })]), /acknowledgement was not carried/);
});

test('element extraction ignores prefixes and does not confuse Reports with ReportReviewed', () => {
  const xml = '<a:Reports><a:ReportReviewed>x</a:ReportReviewed></a:Reports><Reports attr="1">y</Reports>';
  assert.deepEqual(elements(xml, 'Reports'), ['<a:ReportReviewed>x</a:ReportReviewed>', 'y']);
  assert.deepEqual(elements(xml, 'ReportReviewed'), ['x']);
});

test('invalid XML characters are found by code point, keeping surrogate pairs', () => {
  assert.deepEqual(invalidXmlCharacters('ok\t\n\r\u{1F600}'), []);
  assert.deepEqual(invalidXmlCharacters('a\u0000b\u000Bc￾\uD800'), ['\u0000', '\u000B', '￾', '\uD800']);
});

test('the export zip reader handles stored and deflated entries', () => {
  const entries = [
    { name: 'FAKE_patient.xml', data: Buffer.from('<x>deflated</x>'), method: 8 },
    { name: 'ReadMe.txt', data: Buffer.from('stored'), method: 0 },
  ];
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const entry of entries) {
    const body = entry.method === 8 ? zlib.deflateRawSync(entry.data) : entry.data;
    const name = Buffer.from(entry.name);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(entry.method, 8);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(entry.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(entry.method, 10);
    central.writeUInt32LE(body.length, 20);
    central.writeUInt32LE(entry.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, name, body);
    centrals.push(central, name);
    offset += local.length + name.length + body.length;
  }
  const directory = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  const zip = readZip(Buffer.concat([...locals, directory, end]));
  assert.equal(zip.get('FAKE_patient.xml').toString(), '<x>deflated</x>');
  assert.equal(zip.get('ReadMe.txt').toString(), 'stored');
  assert.throws(() => readZip(Buffer.from('not a zip')), /not a zip file/);
});

test('the seeded HL7 message carries the three #3946 shapes', () => {
  const segments = buildMessage(ACCESSION, 'FAKE-PWmarker').split('\r').filter(Boolean);
  assert.equal(segments.filter(s => s.startsWith('OBX|')).length, 4);
  assert.ok(segments.some(s => s.startsWith('OBX|1|ED|') && s.includes(PDF.toString('base64'))));
  assert.ok(segments.some(s => s.includes(LONG_TEXT_RESULT)) && LONG_TEXT_RESULT.length === 150);
  assert.ok(segments.some(s => s.includes('Hemolysed\u000Bsample')));
  assert.ok(!/[|^~\\&]/.test(PDF.toString('base64')), 'the PDF base64 must not contain an HL7 delimiter');
});
