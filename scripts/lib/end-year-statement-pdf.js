/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/** Invoice rows from pdftotext -layout, excluding patient details, dates and totals. */
function statementInvoiceRows(text) {
  return String(text).split(/[\r\n\f]+/).flatMap(line => {
    const row = /^\s*([1-9]\d*)\s+(\d{4}-\d{2}-\d{2})\s+(-?\d+\.\d{2})\s+(-?\d+\.\d{2})\s*$/.exec(line);
    return row ? [{ id: row[1], date: row[2], invoiced: row[3], paid: row[4] }] : [];
  });
}

module.exports = { statementInvoiceRows };
