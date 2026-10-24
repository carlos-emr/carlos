/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Clinic-wide state the MCEDT mailbox check touches, and the exact way it is put back.
 *
 * Two things outlive the browser session:
 *
 *   1. The MCEDT account password. Change Password writes the `property` row named
 *      `mcedt_account_password`. That value is a credential for a provincial claims service, so this
 *      check never reads it into the process, never prints it and never puts it in a statement:
 *      createPropertyParking() PARKS the clinic's own rows under a name that carries the run marker
 *      (an UPDATE of the `name` column, nothing else), lets the check create and change its own
 *      fake-valued row, and on restore deletes that row and renames the parked ones back. The
 *      original value is therefore untouched byte for byte by construction, and a hard kill between
 *      the park and the restore leaves the credential in the table under a name that says exactly how
 *      to put it back (parkedHint()) instead of lost with the process. Before and after are compared
 *      by a SHA-256 digest computed inside MariaDB, so the proof needs no value either.
 *
 *   2. The EDT outbox directory (ONEDT_OUTBOX). The Upload tab lists it, Add writes into it, Delete
 *      removes from it, and merely opening the tab makes the application create two timestamp files.
 *      createOutboxLedger() takes a listing (name, kind, size and SHA-256 of every entry, hidden ones
 *      included) before the run, remembers which files the run is allowed to have caused, removes
 *      only those, and fails when the directory is not exactly as it was found. A file is removed
 *      only while it still holds the bytes the run knows it wrote, so a real claim file that took a
 *      name the run once used is never deleted.
 *
 * THE TWO TIMESTAMP FILES. ActionUtils.getOutboxTimestamp() reads `<outbox>/.timestamp` while
 * setOutboxTimestamp() writes `<parent>/<outbox name>.timestamp` (validateGeneratedSiblingPath appends
 * the suffix to the directory's NAME); app-findings-log.md finding 216. Until that is fixed an open
 * leaves an empty file at the first path and a "dd-MM-yyyy HH:mm" file at the second, so both are
 * snapshotted: a run that found neither removes both, and one that found either puts its bytes back.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { assert } = require('./playwright-harness');

/** The property row Change Password writes (UserProperty.MCEDT_ACCOUNT_PASSWORD). */
const PASSWORD_PROPERTY = 'mcedt_account_password';

const PROPERTY_NAME = /^[A-Za-z0-9_.-]{1,100}$/;
const RUN_MARKER = /^FAKE-PW[0-9a-f]{16}$/;
/** What the application writes into the sibling timestamp file: new SimpleDateFormat("dd-MM-yyyy HH:mm"). */
const TIMESTAMP_TEXT = /^\d{2}-\d{2}-\d{4} \d{2}:\d{2}$/;

function quoted(name) {
  assert(PROPERTY_NAME.test(name), 'not a plain property name');
  return `'${name}'`;
}

/**
 * Park the property rows called `name` under `<name>.parked-<marker>` for the length of the run.
 *
 * @param {object} options
 * @param {object} options.sql the harness mysql client
 * @param {string} options.name the property name (a plain identifier-like string)
 * @param {string} options.marker the run marker (FAKE-PW<16 hex>)
 * @returns {{ park(): void, restore(): void, digest(): string, parkedName: string, parkedHint(): string }}
 */
function createPropertyParking({ sql, name, marker }) {
  assert(RUN_MARKER.test(String(marker)), 'createPropertyParking needs the run marker (FAKE-PW<16 hex>)');
  const parkedName = `${name}.parked-${marker}`;
  const parkedPrefix = `${name}.parked-`;
  quoted(name);
  quoted(parkedName);

  /**
   * SHA-256, inside MariaDB, of the rows called `selector`: id, value and provider_no, in id order.
   * The name is left out so the parked set can be compared with the original one. The digest of no
   * rows is the digest of the empty text, so "absent" compares equal to "absent".
   */
  function digestOf(selector) {
    return sql.value('SET SESSION group_concat_max_len=16777216; SELECT SHA2(IFNULL(GROUP_CONCAT('
      + "CONCAT_WS('|', `id`, IFNULL(HEX(`value`),'N'), IFNULL(HEX(`provider_no`),'N')) ORDER BY `id` SEPARATOR ';'),''),256)"
      + ` FROM \`property\` WHERE \`name\`=${selector}`);
  }
  const count = (selector) => Number(sql.value(`SELECT COUNT(*) FROM \`property\` WHERE \`name\`=${selector}`));

  let before = null;
  let parked = false;

  const parking = {
    parkedName,

    /** How a person puts the credential back if this process was killed between park() and restore(). */
    parkedHint() {
      return `UPDATE property SET name=${quoted(name)} WHERE name=${quoted(parkedName)}`;
    },

    /** The digest of the live rows called `name` (the run's own row included while it exists). */
    digest() {
      return digestOf(quoted(name));
    },

    /**
     * Take the digest, then move every row called `name` out of the way. Refuses when an earlier
     * run's parked rows are still there (the credential is not where it belongs).
     */
    park() {
      const leftover = Number(sql.value('SELECT COUNT(*) FROM `property` WHERE `name` LIKE '
        + `'${parkedPrefix.replace(/[\\%_]/g, '\\$&')}%'`));
      assert(leftover === 0, `${leftover} property row(s) from an earlier interrupted run are still parked under `
        + `${parkedPrefix}*; put them back first (UPDATE property SET name=${quoted(name)} WHERE name LIKE '${parkedPrefix}%')`);
      before = { digest: digestOf(quoted(name)), rows: count(quoted(name)) };
      parked = true;
      sql.execute(`UPDATE \`property\` SET \`name\`=${quoted(parkedName)} WHERE \`name\`=${quoted(name)}`);
      assert(count(quoted(name)) === 0, 'the property rows could not be parked');
      assert(count(quoted(parkedName)) === before.rows && digestOf(quoted(parkedName)) === before.digest,
        'the parked property rows are not the rows that were found');
    },

    /**
     * Delete whatever the run created under `name`, move the parked rows back, and prove the rows
     * called `name` have the digest and the count they had. Safe to call twice and before park().
     */
    restore() {
      if (!parked) return;
      sql.execute('START TRANSACTION;'
        + ` DELETE FROM \`property\` WHERE \`name\`=${quoted(name)};`
        + ` UPDATE \`property\` SET \`name\`=${quoted(name)} WHERE \`name\`=${quoted(parkedName)};`
        + ' COMMIT;');
      const rows = count(quoted(name));
      const left = count(quoted(parkedName));
      assert(left === 0 && rows === before.rows && digestOf(quoted(name)) === before.digest,
        `the ${name} property was not restored to the rows found at the start (${before.rows} row(s) expected, `
        + `${rows} found, ${left} still parked; to put the parked ones back: ${parking.parkedHint()})`);
      parked = false;
    },
  };
  return parking;
}

/** Name, kind, size and SHA-256 of every entry of `dir`, hidden ones included, sorted by name. */
function listDirectory(dir, { hidden = true } = {}) {
  return fs.readdirSync(dir, { withFileTypes: true })
    .filter((entry) => hidden || !entry.name.startsWith('.'))
    .map((entry) => {
      const full = path.join(dir, entry.name);
      if (!entry.isFile()) return { name: entry.name, kind: entry.isDirectory() ? 'dir' : 'other' };
      const bytes = fs.readFileSync(full);
      return { name: entry.name, kind: 'file', size: bytes.length, sha256: crypto.createHash('sha256').update(bytes).digest('hex') };
    })
    .sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
}

const describeEntry = (entry) => `${entry.name}:${entry.kind}${entry.sha256 ? `:${entry.size}:${entry.sha256}` : ''}`;

/** The names that differ between two listings, as { added, removed, changed }. */
function diffListings(was, now) {
  const index = (list) => new Map(list.map((entry) => [entry.name, describeEntry(entry)]));
  const a = index(was);
  const b = index(now);
  return {
    added: [...b.keys()].filter((name) => !a.has(name)),
    removed: [...a.keys()].filter((name) => !b.has(name)),
    changed: [...b.keys()].filter((name) => a.has(name) && a.get(name) !== b.get(name)),
  };
}

/**
 * The outbox as the run found it, and the way back to it.
 *
 * @param {object} options
 * @param {string} options.outbox the absolute ONEDT_OUTBOX directory
 * @returns the ledger described in the file header
 */
function createOutboxLedger({ outbox }) {
  assert(path.isAbsolute(outbox), 'the outbox must be an absolute path');
  const dir = path.resolve(outbox);
  const parent = path.dirname(dir);
  const inside = path.join(dir, '.timestamp');
  const beside = path.join(parent, `${path.basename(dir)}.timestamp`);
  const timestampPaths = [inside, beside];

  const readOptional = (file) => (fs.existsSync(file) ? fs.readFileSync(file) : null);
  const found = {
    listing: listDirectory(dir),
    parent: fs.readdirSync(parent, { withFileTypes: true }).map((entry) => `${entry.name}:${entry.isDirectory() ? 'dir' : 'file'}`).sort(),
    timestamps: new Map(timestampPaths.map((file) => [file, readOptional(file)])),
  };
  // Files the run may have caused under a known name, each with the bytes the run knows it wrote.
  const copies = [];

  function ownLike(file, reference) {
    const stat = fs.statSync(reference);
    try {
      fs.chownSync(file, stat.uid, stat.gid);
    } catch (error) {
      if (typeof process.getuid === 'function' && process.getuid() === 0) throw error;
    }
  }

  const ledger = {
    dir,
    parent,
    timestampPaths,
    /** The listing taken before the run touched anything. */
    get found() { return found.listing; },

    /** The files the Upload tab lists: non-hidden regular files, as the page's own filter has it. */
    visible() {
      return listDirectory(dir, { hidden: false }).filter((entry) => entry.kind === 'file');
    },

    /** Entries that differ from the start, hidden ones and the application's timestamp files excluded. */
    changedVisible() {
      const strip = (list) => list.filter((entry) => !entry.name.startsWith('.'));
      return diffListings(strip(found.listing), strip(listDirectory(dir)));
    },

    /**
     * The run expects the application (or itself) to put a file at `name` holding `bytes`; cleanup
     * removes it again while it still holds exactly those bytes.
     */
    expect(name, bytes) {
      assert(/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/.test(name), 'the expected outbox file name is not a plain file name');
      assert(Buffer.isBuffer(bytes), 'the expected outbox file needs its bytes');
      copies.push({ file: path.join(dir, name), bytes });
    },

    /**
     * Put the outbox and its two timestamp files back exactly as found, then prove it. Throws one
     * error naming every difference; counts and file names only, never content.
     */
    restore() {
      const problems = [];
      for (const { file, bytes } of copies.slice().reverse()) {
        if (!fs.existsSync(file)) continue;
        try {
          if (!fs.lstatSync(file).isFile() || !fs.readFileSync(file).equals(bytes)) {
            problems.push(`${path.basename(file)} now holds other bytes and was left in place`);
          } else {
            fs.unlinkSync(file);
          }
        } catch (error) {
          problems.push(`${path.basename(file)} could not be removed (${error.code || 'error'})`);
        }
      }
      for (const file of timestampPaths) {
        const was = found.timestamps.get(file);
        try {
          const now = readOptional(file);
          if (was === null) {
            if (now === null) continue;
            if (now.length === 0 || TIMESTAMP_TEXT.test(now.toString('latin1'))) fs.unlinkSync(file);
            else problems.push(`${path.basename(file)} holds text the application does not write and was left in place`);
          } else if (now === null || !now.equals(was)) {
            fs.writeFileSync(file, was, { mode: 0o640 });
            ownLike(file, path.dirname(file));
          }
        } catch (error) {
          problems.push(`${path.basename(file)} could not be restored (${error.code || 'error'})`);
        }
      }
      const outboxDiff = diffListings(found.listing, listDirectory(dir));
      const parentNow = fs.readdirSync(parent, { withFileTypes: true }).map((entry) => `${entry.name}:${entry.isDirectory() ? 'dir' : 'file'}`).sort();
      const parentDiff = {
        added: parentNow.filter((name) => !found.parent.includes(name)),
        removed: found.parent.filter((name) => !parentNow.includes(name)),
      };
      for (const [label, names] of [['added to the outbox', outboxDiff.added], ['removed from the outbox', outboxDiff.removed],
        ['changed in the outbox', outboxDiff.changed], ['added beside the outbox', parentDiff.added],
        ['removed from beside the outbox', parentDiff.removed]]) {
        if (names.length) problems.push(`${names.length} entr${names.length === 1 ? 'y' : 'ies'} ${label}: ${names.join(', ')}`);
      }
      assert(problems.length === 0, `the EDT outbox is not as it was found: ${problems.join('; ')}`);
    },
  };
  return ledger;
}

module.exports = {
  PASSWORD_PROPERTY,
  TIMESTAMP_TEXT,
  createOutboxLedger,
  createPropertyParking,
  diffListings,
  listDirectory,
};
