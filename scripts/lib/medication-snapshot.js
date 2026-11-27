/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// Only digests leave MariaDB. JSON preserves column boundaries and SQL NULL, and HEX
// preserves text bytes (including trailing spaces) independent of the client charset.
function medicationSnapshot(rows, patient) {
  if (!/^[0-9]+$/.test(String(patient))) throw new Error('Invalid medication snapshot patient');
  const columns = rows(`SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drugs' ORDER BY ORDINAL_POSITION`);
  if (!columns.length) throw new Error('Medication columns unavailable');
  const expressions = columns.map(([name, type]) => {
    const id = '`' + name.replaceAll('`', '``') + '`';
    // Widen a stored float exactly before MariaDB converts it to decimal text.
    const value = type === 'float' ? `CAST(${id} AS DOUBLE)` : id;
    return `HEX(CAST(${value} AS BINARY))`;
  });
  return JSON.stringify(rows(`SELECT drugid, SHA2(JSON_ARRAY(${expressions.join(', ')}), 256)
    FROM drugs WHERE demographic_no = ${patient} ORDER BY drugid`));
}

module.exports = { medicationSnapshot };
