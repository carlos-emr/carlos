/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');
const trustedCatalog = require('../fixtures/vascular-tracker-catalog.json');
const q = h.sqlString;
const columns = Object.freeze({
  measurementType: ['id', 'type', 'typeDisplayName', 'typeDescription', 'measuringInstruction', 'validation', 'createDate'],
  validations: ['id', 'name', 'regularExp', 'maxValue1', 'minValue', 'maxLength', 'minLength', 'isNumeric', 'isTrue', 'isDate'],
});
const expression = table => `JSON_ARRAY(${columns[table].map(name => `\`${name}\``).join(',')})`;
const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);

/** Read only the bundled, trusted form's simple measurement declarations, including XML entities. */
function readDefinitions(xml = fs.readFileSync(path.join(__dirname, '../../src/main/webapp/form/VTForm.xml'), 'utf8')) {
  // The parity reader runs in the repository's locked npm test environment. Live
  // browser fixtures use trustedCatalog directly and do not need this XML parser.
  const { xml2js } = require('xml-js');
  const document = xml2js(xml, { compact: false, ignoreComment: true, ignoreDeclaration: true });
  const elements = (parent, name) => (parent?.elements || [])
    .filter(element => element.type === 'element' && element.name === name);
  const root = elements(document, 'formProp')[0];
  const tag = (body, name) => {
    const element = elements(body, name)[0];
    if (!element) return null;
    h.assert((element.elements || []).every(child => child.type === 'text' || child.type === 'cdata'),
      'VT catalog scalar definition contains nested elements');
    return (element.elements || []).map(child => child.text ?? child.cdata).join('').trim();
  };
  const number = (body, name, fallback = null) => {
    const text = tag(body, name);
    if (text == null) return fallback;
    h.assert(text !== '' && Number.isFinite(Number(text)), 'VT catalog definition has an invalid numeric rule');
    return Number(text);
  };
  const definitions = elements(root, 'measurement').map(body => {
    const metadata = ['type', 'typeDisplayName', 'typeDesc', 'measuringInstrc'].map(name => tag(body, name));
    const rule = elements(body, 'validationRule')[0];
    h.assert(metadata.every(value => typeof value === 'string') && metadata[0] !== ''
      && rule?.elements?.some(child => child.type === 'element'),
      'VT catalog definition is incomplete');
    const ruleValues = [tag(rule, 'name'), tag(rule, 'regularExp'), number(rule, 'maxValue'),
      number(rule, 'minValue'), number(rule, 'maxLength', 0), number(rule, 'minLength'),
      number(rule, 'isNumeric'), number(rule, 'isTrue'), number(rule, 'isDate')];
    // Match the application's reuse query: supplied rule fields constrain the row;
    // an omitted field and the display name do not rewrite an existing clinic rule.
    const criteria = ['name', 'regularExp', 'maxValue', 'minValue', 'maxLength', 'minLength', 'isNumeric', 'isTrue', 'isDate']
      .flatMap((name, index) => index !== 0 && index !== 7 && tag(rule, name) != null ? [[index, ruleValues[index]]] : []);
    return {metadata, rule: ruleValues, criteria};
  });
  h.assert(definitions.length > 0, 'VT catalog definition contains no measurements');
  return definitions;
}

function snapshot(sql, table) {
  return new Map(sql.rows(`SELECT id,${expression(table)} FROM ${table} ORDER BY id`).map(([id, json]) => {
    h.assert(/^[1-9]\d*$/.test(id), 'VT catalog snapshot has an invalid row identity');
    const values = JSON.parse(json);
    h.assert(Array.isArray(values) && values.length === columns[table].length && String(values[0]) === id,
      'VT catalog snapshot is incomplete');
    return [id, {json, values}];
  }));
}

/**
 * SetupForm lazily installs shared VT types/rules. The suite owns the install exclusively.
 * Snapshot just before that request, record its exact new rows, and remove them only after
 * all owned clinical rows have been removed. Existing definitions are never rewritten.
 */
function vascularTrackerCatalogFixture(sql, definitions = trustedCatalog) {
  let baseline;
  let owned;
  const tables = Object.keys(columns);
  const read = () => Object.fromEntries(tables.map(table => [table, snapshot(sql, table)]));
  const capture = () => {
    if (!baseline) return;
    const current = read();
    for (const table of tables) for (const [id, before] of baseline[table]) {
      h.assert(current[table].get(id)?.json === before.json, 'A preexisting VT catalog row changed; refusing cleanup');
    }
    const added = Object.fromEntries(tables.map(table => [table,
      new Map([...current[table]].filter(([id]) => !baseline[table].has(id)))]));
    for (const {values} of added.measurementType.values()) {
      const linkedRule = current.validations.get(String(values[5]))?.values.slice(1);
      h.assert(/^[1-9]\d*$/.test(String(values[5])) && linkedRule && definitions.some(def => {
        const criteria = def.criteria || def.rule.flatMap((value, index) =>
          index !== 0 && index !== 7 && value != null ? [[index, value]] : []);
        return same(def.metadata, values.slice(1, 5)) && criteria.every(([index, value]) => linkedRule[index] === value);
      }), 'An unexpected measurement definition or linked rule appeared; refusing cleanup');
    }
    for (const {values} of added.validations.values()) {
      h.assert(definitions.some(def => same(def.rule, values.slice(1))),
        'An unexpected validation definition appeared; refusing cleanup');
    }
    if (owned) for (const table of tables) {
      h.assert(owned[table].size === added[table].size && [...owned[table]].every(([id, row]) => added[table].get(id)?.json === row.json),
        'Owned VT catalog definitions changed; refusing cleanup');
    }
    owned = added;
  };
  return {
    begin() { if (!baseline) baseline = read(); },
    capture,
    cleanup() {
      if (!baseline) return;
      capture();
      // Preserve another patient's readings even if the definition happens to match this form.
      for (const {values} of owned.measurementType.values()) {
        for (const table of ['measurements', 'measurementsDeleted']) {
          h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE BINARY type=${q(values[1])}
            AND BINARY measuringInstruction=${q(values[4])}`) === '0',
          'A VT catalog definition still has clinical references; refusing cleanup');
        }
      }
      const typeIds = [...owned.measurementType.keys()].join(',') || '0';
      for (const id of owned.validations.keys()) {
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurementType WHERE validation=${q(id)} AND id NOT IN (${typeIds}))
          + (SELECT COUNT(*) FROM measurementTypeDeleted WHERE validation=${q(id)})`) === '0',
        'A VT validation is referenced outside the fixture; refusing cleanup');
      }
      // All ownership and reference checks happen before the first DELETE. Match the full
      // recorded row as well as its ID, so a changed row is retained rather than overwritten.
      for (const table of tables) for (const [id, row] of owned[table]) {
        h.assert(sql.value(`DELETE FROM ${table} WHERE id=${id} AND BINARY ${expression(table)}=BINARY ${q(row.json)};
          SELECT ROW_COUNT()`) === '1', 'VT catalog cleanup lost exact row ownership');
      }
      const after = read();
      for (const table of tables) h.assert(same([...after[table]], [...baseline[table]]),
        'The complete VT catalog was not restored');
      owned = Object.fromEntries(tables.map(table => [table, new Map()]));
    },
  };
}

module.exports = { vascularTrackerCatalogFixture, readDefinitions };
