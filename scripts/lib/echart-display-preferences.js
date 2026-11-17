/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The per-provider eChart display settings (Preferences > eChart; Java:
 * io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreference), shared by the browser checks
 * that read or change them.
 *
 * Each setting is one `property` row (provider_no, name, value "true"/"false"); a provider with no row
 * gets the default listed here. A check that changes them must put the provider's rows back exactly
 * as it found them, ids included, because the property table has no unique key on (provider_no, name)
 * and a duplicate row left behind would change what the next run reads.
 */
const h = require('./playwright-harness');

/** In the order the Preferences page lists them, with the defaults the alpha tester asked for (click-to-dial: issue #852). */
const ECHART_DISPLAY_SETTINGS = Object.freeze([
  { name: 'echart_show_timer', defaultValue: true },
  { name: 'echart_show_group_document_by_type', defaultValue: true },
  { name: 'echart_show_DOB', defaultValue: true },
  { name: 'echart_show_cell', defaultValue: true },
  { name: 'echart_phone_tel_links', defaultValue: false },
  { name: 'echart_show_HIN', defaultValue: true },
  { name: 'echart_email_indicator', defaultValue: true },
  { name: 'echart_show_fam_doc_widget', defaultValue: true },
  { name: 'echart_show_ref_doc_widget', defaultValue: false },
  { name: 'echart_show_ocean', defaultValue: false },
]);

const NAME = /^[A-Za-z0-9_]+$/;

/**
 * Snapshot-and-restore access to one provider's eChart display rows.
 *
 * @param {object} sql   the harness SQL runner
 * @param {string} provider the provider_no whose rows are touched
 * @param {string[]} [names] the settings in scope (default: all of them)
 * @returns {{ snapshot: Array, restore: Function, clear: Function, set: Function, value: Function }}
 */
function providerEchartDisplayRows(sql, provider, names = ECHART_DISPLAY_SETTINGS.map((setting) => setting.name)) {
  h.assert(names.length > 0 && names.every((name) => NAME.test(name)), 'Invalid eChart display setting name');
  const owner = h.sqlString(provider);
  const inList = names.map((name) => h.sqlString(name)).join(',');
  const rowsQuery = `SELECT id, name, \`value\`, IF(\`value\` IS NULL,1,0) FROM property
    WHERE provider_no=${owner} AND name IN (${inList}) ORDER BY id`;
  const snapshot = sql.rows(rowsQuery);

  return {
    snapshot,
    /** Puts back exactly the rows that existed when this helper was created. */
    restore() {
      sql.execute(`DELETE FROM property WHERE provider_no=${owner} AND name IN (${inList})`);
      for (const [id, name, value, valueIsNull] of snapshot) {
        h.assert(/^[1-9]\d*$/.test(id), 'Invalid property fixture identity');
        sql.execute(`INSERT INTO property (id, name, \`value\`, provider_no) VALUES (${id}, ${h.sqlString(name)},
          ${valueIsNull === '1' ? 'NULL' : h.sqlString(value)}, ${owner})`);
      }
      h.assert(JSON.stringify(sql.rows(rowsQuery)) === JSON.stringify(snapshot),
        'The provider\'s eChart display settings were not restored');
    },
    /** Removes the rows, so the provider gets every default. */
    clear() {
      sql.execute(`DELETE FROM property WHERE provider_no=${owner} AND name IN (${inList})`);
    },
    /** Stores one setting as "true"/"false", replacing any row(s) for it. */
    set(name, enabled) {
      h.assert(names.includes(name), `${name} is not in scope for this fixture`);
      sql.execute(`DELETE FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)};
        INSERT INTO property (name, \`value\`, provider_no) VALUES (${h.sqlString(name)}, '${enabled ? 'true' : 'false'}', ${owner})`);
    },
    /** The stored value of one setting, or null when the provider has no row for it. */
    value(name) {
      const rows = sql.rows(`SELECT \`value\` FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)} ORDER BY id`);
      return rows.length ? rows[0][0] : null;
    },
  };
}

module.exports = { ECHART_DISPLAY_SETTINGS, providerEchartDisplayRows };
