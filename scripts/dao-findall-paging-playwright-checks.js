#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Generic DAO paging order (issue #5308). User path: the session REST list of
 * pharmacies, GET /ws/rs/pharmacies/?offset=&limit=, which pages straight through
 * AbstractDaoImpl.findAll(offset, limit) -- the read every JPA DAO inherits and the
 * one HRMFixMissingReportHelper and the eForm report tool list also page with.
 * Asserted, against the database's rows in id order: a maximum-size (5000-row) first
 * page holds exactly the first rows; small pages read with increasing offsets
 * concatenate to the whole directory (no row repeated, none skipped, every owned row
 * present), each page is itself ascending and re-reading a page returns the same rows;
 * an offset past the end returns an empty page; a limit above the 5000-row maximum
 * select size is refused with HTTP 400 (issue #5528). Negative pagination on both the
 * pharmacy and demographic lists also returns 400. A directory larger than one maximum-size page
 * (an imported provincial list) is handled by paging, not by the first page.
 * Fixtures: seven pharmacies named after the run marker, inserted in reverse name
 * order so name order and id order disagree. Read-only otherwise. Cleanup deletes only
 * marker rows above the pre-run id high-water mark and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const FIXTURE_ROWS = 7;
const MAX_SELECT = 5000;
// Pages stay small enough to cross several boundaries, but a clinic with a large
// pharmacy directory must not turn the check into thousands of requests.
const TARGET_PAGE_COUNT = 60;

/** One session GET of the pharmacy list; returns the HTTP status and the listed ids. */
async function listPharmacies(s, params) {
  const response = await s.context.request.get(h.appUrl(s.config.baseUrl, '/ws/rs/pharmacies/'), {
    params, headers: { Accept: 'application/json' }, failOnStatusCode: false, maxRedirects: 0, timeout: 60000,
  });
  const status = response.status();
  if (status !== 200) return { status, ids: null };
  const json = await response.json();
  // OscarSearchResponse names the list "content"; JAXB-driven writers may use its XML name.
  const rows = [json.content, json.Item, json.item].find(Array.isArray);
  h.assert(rows, 'The pharmacy list response carried no row array');
  return { status, ids: rows.map(row => Number(row.id)) };
}

function assertAscending(ids, label) {
  for (let i = 1; i < ids.length; i++) {
    h.assert(ids[i - 1] < ids[i], `${label} is not in strictly ascending id order at position ${i}`);
  }
}

async function workflow(s) {
  const { sql, marker } = s;
  const high = Number(sql.value('SELECT COALESCE(MAX(recordID),0) FROM pharmacyInfo'));
  // Owned rows: above the pre-run high-water mark and named after the run marker.
  const owned = 'recordID>? AND name LIKE ?';
  const ownedValues = [high, `${marker}%`];
  s.cleanup(() => {
    sql.execute(`DELETE FROM pharmacyInfo WHERE ${owned}`, ownedValues);
    h.assert(sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE ${owned}`, ownedValues) === '0',
      'Owned pharmacy fixtures were not removed');
  });
  const fixtureIds = [];
  for (let i = 0; i < FIXTURE_ROWS; i++) {
    const name = `${marker} pharmacy ${String.fromCharCode(90 - i)}`;
    // uid is NOT NULL with no default: strict sql_mode refuses a row that omits it.
    sql.execute(`INSERT INTO pharmacyInfo(uid,name,city,province,status) VALUES(0,?,'Fixture City','ON','1')`, [name]);
    // Each bound statement runs in its own client session, so the id is read back by its unique name.
    const id = sql.value('SELECT recordID FROM pharmacyInfo WHERE recordID>? AND name=?', [high, name]);
    h.assert(/^[1-9]\d*$/.test(id) && Number(id) > high, 'A pharmacy fixture was not created');
    fixtureIds.push(Number(id));
  }

  // The whole directory in id order, straight from the database. A large directory
  // (an imported provincial list) can hold more than one maximum-size page, so the
  // REST reads below are compared with slices of this, never with each other.
  const readDirectory = () => sql.rows('SELECT recordID FROM pharmacyInfo ORDER BY recordID').map(([id]) => Number(id));
  const all = readDirectory();
  h.assert(fixtureIds.every(id => all.includes(id)), 'An owned pharmacy is missing from the database');
  // Offset paging is not a snapshot (see AbstractDao#findAll): another writer during the run
  // would make a correct endpoint look broken. Report that as what it is, not as a paging defect.
  const assertDirectoryUnchanged = () => h.assert(JSON.stringify(readDirectory()) === JSON.stringify(all),
    'pharmacyInfo changed while the check ran, so its paging comparison is void; run it exclusively (EXCLUSIVE=1)');

  await s.step('a maximum-size first page is in ascending id order and matches the database', async () => {
    const listed = await listPharmacies(s, { offset: 0, limit: MAX_SELECT });
    h.assert(listed.status === 200, `The pharmacy list answered HTTP ${listed.status}`);
    assertAscending(listed.ids, 'The maximum-size first page');
    h.assert(JSON.stringify(listed.ids) === JSON.stringify(all.slice(0, MAX_SELECT)),
      'The maximum-size first page does not hold the first database rows in id order');
  });

  await s.step('consecutive pages concatenate to the full list with no repeat or gap', async () => {
    const pageSize = Math.max(3, Math.ceil(all.length / TARGET_PAGE_COUNT));
    h.assert(all.length > 2 * pageSize, 'The list is too short to cross two page boundaries');
    const visited = [];
    for (let offset = 0; ; offset += pageSize) {
      const page = await listPharmacies(s, { offset, limit: pageSize });
      h.assert(page.status === 200, `Page at offset ${offset} answered HTTP ${page.status}`);
      h.assert(page.ids.length <= pageSize, `Page at offset ${offset} returned more rows than its limit`);
      assertAscending(page.ids, `The page at offset ${offset}`);
      visited.push(...page.ids);
      if (page.ids.length < pageSize) break;
    }
    assertDirectoryUnchanged();
    h.assert(new Set(visited).size === visited.length, 'A pharmacy was returned on more than one page');
    h.assert(JSON.stringify(visited) === JSON.stringify(all),
      'The pages do not concatenate to the database rows in id order');
    h.assert(fixtureIds.every(id => visited.includes(id)), 'The paged traversal lost an owned pharmacy');

    const again = await listPharmacies(s, { offset: pageSize, limit: pageSize });
    h.assert(JSON.stringify(again.ids) === JSON.stringify(all.slice(pageSize, 2 * pageSize)),
      'Re-reading the second page returned different rows');
  });

  await s.step('an offset past the end is empty', async () => {
    const past = await listPharmacies(s, { offset: all.length + 10, limit: 3 });
    h.assert(past.status === 200 && past.ids.length === 0, 'An offset past the last row did not return an empty page');
  });

  await s.step('omitted and zero pharmacy limits keep their supported behavior', async () => {
    const defaults = await listPharmacies(s, {});
    h.assert(defaults.status === 200 && JSON.stringify(defaults.ids) === JSON.stringify(all.slice(0, MAX_SELECT)),
      'Omitted pagination did not return the maximum-size first page');
    const zero = await listPharmacies(s, { offset: 0, limit: 0 });
    h.assert(zero.status === 200 && zero.ids.length === 0, 'A zero pharmacy limit did not return an empty page');
  });

  for (const [resource, maximum] of [['pharmacies/', MAX_SELECT], ['demographics', 500]]) {
    // Integer.MAX_VALUE is covered by the CXF endpoint tests: CRS 942220 rejects
    // that literal with 403 before REST on the packaged front door.
    for (const params of [{ offset: 0, limit: maximum + 1 }, { offset: 0, limit: maximum * 2 },
      { offset: 0, limit: -1 }, { offset: -1, limit: 10 }]) {
      await s.step(`${resource} rejects offset=${params.offset}, limit=${params.limit} with HTTP 400`, async () => {
        const response = await s.context.request.get(h.appUrl(s.config.baseUrl, `/ws/rs/${resource}`), {
          params, headers: { Accept: 'application/json' }, failOnStatusCode: false, maxRedirects: 0, timeout: 60000,
        });
        h.assert(response.status() === 400, `Invalid ${resource} pagination answered HTTP ${response.status()}, expected 400`);
      });
    }
  }
}

if (require.main === module) runWorkflow('dao-findall-paging', workflow, { openPatient: false });
module.exports = { workflow };
