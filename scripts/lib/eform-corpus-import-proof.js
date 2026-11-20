'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

function positiveId(value) { return typeof value === 'string' && /^[1-9][0-9]*$/.test(value) && Number(value) <= 2147483647; }
function validatePackages(packages) {
  assert(packages.length > 0, 'No corpus packages');
  const names = new Set();
  for (const item of packages) {
    assert(typeof item.formName === 'string' && item.formName.length > 0, 'Unreadable package form name');
    assert(!names.has(item.formName), 'Duplicate corpus form name: ' + item.formName);
    assert(/^[a-f0-9]{64}$/.test(item.sha256), 'Missing ZIP digest');
    names.add(item.formName);
  }
}
function managerRows(rows, baseUrl) {
  const base = new URL(baseUrl);
  const expectedPath = (base.pathname + '/eform/efmformmanageredit').replace(/\/{2,}/g, '/');
  const ids = new Set();
  return rows.map(row => {
    assert(typeof row.name === 'string' && row.name.length > 0 && typeof row.href === 'string', 'Incomplete manager row');
    const url = new URL(row.href, base);
    const fid = url.searchParams.get('fid');
    assert(url.origin === base.origin && url.pathname === expectedPath
      && url.searchParams.getAll('fid').length === 1 && positiveId(fid), 'Invalid manager form identity');
    assert(!ids.has(fid), 'Duplicate manager form ID');
    ids.add(fid);
    return { name: row.name, fid };
  });
}
function existingId(rows, name) {
  const matches = rows.filter(row => row.name === name);
  assert(matches.length === 1 && positiveId(matches[0].fid), 'Expected exactly one exact-name form: ' + name);
  return matches[0].fid;
}
function preflight(packages, rows, reuse) {
  validatePackages(packages);
  for (const item of packages) {
    if (reuse) existingId(rows, item.formName);
    else assert(!rows.some(row => row.name === item.formName), 'Existing corpus form must not be adopted: ' + item.formName);
  }
}
function importedId(before, after, name, status, body) {
  assert(status === 200 && /class="alert alert-success"/.test(body)
    && body.includes('Your eform was imported.'), 'Production import did not report success');
  assert(!before.some(row => row.name === name), 'Form existed before upload');
  const prior = new Map(before.map(row => [row.fid, row.name]));
  const current = new Map(after.map(row => [row.fid, row.name]));
  assert(before.every(row => current.get(row.fid) === row.name), 'Existing form inventory changed during import');
  const added = after.filter(row => !prior.has(row.fid));
  assert(added.length === 1 && added[0].name === name, 'Import identity is missing or ambiguous');
  assert(existingId(after, name) === added[0].fid, 'Imported name is ambiguous');
  return added[0].fid;
}
function receipt(directory, key, data) {
  assert(/^[a-z0-9-]+$/.test(key), 'Invalid receipt key');
  const fd = fs.openSync(path.join(directory, key + '.json'), 'wx', 0o600);
  try { fs.writeFileSync(fd, JSON.stringify(data, null, 2) + '\n'); fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
  const dir = fs.openSync(directory, 'r');
  try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
}
function failedOutcome(result) {
  if (result.outcome === 'PDF OK') return !(result.pdfBytes > 0 && positiveId(result.fid));
  return result.outcome !== 'PDF WITHHELD' && !String(result.outcome).startsWith('FORM REQUIRES INPUT:');
}
module.exports = { positiveId, validatePackages, managerRows, existingId, preflight, importedId, receipt, failedOutcome };
