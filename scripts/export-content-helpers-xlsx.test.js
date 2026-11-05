/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('node:zlib');
const x = require('./lib/export-content-helpers');

/** A minimal stored-or-deflated ZIP with the given {name: text} entries, as the JDK writer lays it out. */
function zip(entries) {
  const locals = [], central = [];
  let offset = 0;
  for (const [name, text] of Object.entries(entries)) {
    const data = zlib.deflateRawSync(Buffer.from(text, 'utf8'));
    const nameBytes = Buffer.from(name, 'utf8');
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(8, 8);
    local.writeUInt32LE(data.length, 18); local.writeUInt32LE(Buffer.byteLength(text), 22); local.writeUInt16LE(nameBytes.length, 26);
    const header = Buffer.alloc(46);
    header.writeUInt32LE(0x02014b50, 0); header.writeUInt16LE(8, 10); header.writeUInt32LE(data.length, 20);
    header.writeUInt32LE(Buffer.byteLength(text), 24); header.writeUInt16LE(nameBytes.length, 28); header.writeUInt32LE(offset, 42);
    locals.push(local, nameBytes, data); central.push(header, nameBytes);
    offset += local.length + nameBytes.length + data.length;
  }
  const centralBytes = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(central.length / 2, 8); end.writeUInt16LE(central.length / 2, 10);
  end.writeUInt32LE(centralBytes.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, centralBytes, end]);
}

const WORKBOOK = zip({
  'xl/workbook.xml': '<workbook><sheets><sheet name="OSCAR_Report" sheetId="1"/></sheets></workbook>',
  'xl/worksheets/sheet1.xml': '<worksheet><sheetData>'
    + '<row r="1"><c r="A1" t="inlineStr"><is><t>chart_no</t></is></c><c r="B1" t="inlineStr"><is><t>fee</t></is></c></row>'
    + '<row r="2"><c r="A2" t="inlineStr"><is><t xml:space="preserve">00123</t></is></c><c r="B2" t="n"><v>1234.5</v></c>'
    + '<c r="C2" t="inlineStr"><is><t>a &amp; b</t></is></c></row>'
    + '</sheetData></worksheet>',
});

test('xlsxCells keeps the cell type: inline strings are text, numeric cells are numbers', () => {
  const rows = x.xlsxCells(WORKBOOK);
  assert.deepEqual(rows[0], [{ type: 'text', value: 'chart_no' }, { type: 'text', value: 'fee' }]);
  assert.deepEqual(rows[1], [{ type: 'text', value: '00123' }, { type: 'number', value: 1234.5 }, { type: 'text', value: 'a & b' }]);
});

test('xlsxRows still returns the same cells as strings', () => {
  assert.deepEqual(x.xlsxRows(WORKBOOK)[1], ['00123', '1234.5', 'a & b']);
});
