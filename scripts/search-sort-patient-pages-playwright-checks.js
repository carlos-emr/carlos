#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient search results: paging boundaries and order across pages (wave 7, search-sort).
 * User path: Schedule > Search > name search for the run tag > Search (10 rows a page) > Next Page / Last Page, and the
 * Sex / Chart no. / Roster status / Patient status / Doctor headers followed by Next Page (demographicsearchresults.jsp).
 * Asserts, on 20 owned patients (exactly two full pages) and 12 owned patients with French-Canadian accents: page one
 * lists the first ten in surname order and offers Next only; page two lists the other ten and offers Last Page; Last Page
 * returns to page one; the last page does not offer a Next Page that leads to an empty page; paging through a column that
 * every row shares (sex, roster, status, doctor) shows each of the 20 patients exactly once; and a surname that starts with
 * an accented capital is not moved to the end of its page, away from the neighbours it follows across the page boundary.
 * Defects are collected and asserted together in the last step, so every provable step runs first.
 * Fixtures: 32 synthetic patients inserted by SQL whose surname starts with the run tag; cleanup removes them and asserts
 * none remain. Nothing is changed by the searches.
 * Implements the wave-7 "search-sort" pattern (pagination boundaries, page-size edges, unstable sort across pages).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');

const PAGE = 10;

async function workflow(s) {
  const tag = k.nameTag(s.marker);
  k.registerPatientCleanup(s, tag);
  const tagA = `${tag}A`;
  const tagB = `${tag}B`;
  const nn = i => String(i).padStart(2, '0');
  const idsA = [];
  for (let i = 1; i <= 20; i += 1) {
    idsA.push(k.insertPatient(s, { last: `${tagA}-P${nn(i)}`, first: 'Fixture', sex: i % 2 ? 'M' : 'F', roster: i % 2 ? 'RO' : 'NR', chart: `C${nn(i)}` }));
  }
  const bNames = ['Adams', 'Baker', 'Éclair', 'Dunn', 'Evans', 'Frost', 'Green', 'Hall', 'Irwin', 'Jones', 'Kent', 'Lowe'];
  const idsB = bNames.map(name => k.insertPatient(s, { last: `${tagB}-${name}`, first: 'Fixture' }));
  const defects = [];
  const readOwned = async (ids) => k.ownedRows(await k.readResultRows(popup), ids);
  let popup;

  popup = await k.openSearchPopup(s);
  let page1;
  let page2;

  await s.step('page one lists the first ten owned patients in surname order and offers Next only', async () => {
    await k.submitSearch(popup, 'search_name', tagA);
    page1 = await k.readResultRows(popup);
    h.assert(page1.length === PAGE && k.ownedRows(page1, idsA).length === PAGE, `Page one listed ${page1.length} rows, not ${PAGE} owned patients`);
    h.assert(JSON.stringify(page1.map(r => r.id)) === JSON.stringify(idsA.slice(0, PAGE)), 'Page one is not the first ten owned patients in surname order');
    const pager = await k.pagerButtons(popup, 0);
    h.assert(pager.next && !pager.last, `Page one pager is wrong (next=${pager.next}, last=${pager.last})`);
  });

  await s.step('Next Page lists the other ten and offers Last Page; Last Page returns to page one', async () => {
    await k.clickPager(popup, 0, 1);
    page2 = await k.readResultRows(popup);
    h.assert(JSON.stringify(page2.map(r => r.id)) === JSON.stringify(idsA.slice(PAGE)), 'Page two is not the last ten owned patients in surname order');
    const pager = await k.pagerButtons(popup, PAGE);
    h.assert(pager.last, 'Page two does not offer Last Page');
    await k.clickPager(popup, PAGE, -1);
    h.assert(JSON.stringify((await k.readResultRows(popup)).map(r => r.id)) === JSON.stringify(page1.map(r => r.id)), 'Last Page did not return to page one');
  });

  await s.step('paging through a column every patient shares shows each of the 20 patients exactly once', async () => {
    for (const [orderby, label] of [['sex', 'Sex'], ['chart_no', 'Chart no.'], ['roster_status', 'Roster status'], ['patient_status', 'Patient status'], ['provider_no', 'Doctor']]) {
      await k.clickSort(popup, orderby);
      const seen = [];
      let offset = 0;
      for (let guard = 0; guard < 4; guard += 1) {
        seen.push(...k.ownedRows(await k.readResultRows(popup), idsA).map(r => r.id));
        if (!(await k.pagerButtons(popup, offset)).next) break;
        await k.clickPager(popup, offset, 1);
        offset += PAGE;
      }
      const missing = idsA.filter(id => !seen.includes(id)).length;
      const repeated = seen.length - new Set(seen).size;
      if (missing || repeated) defects.push(`paging through ${label}: ${missing} owned patient(s) never listed, ${repeated} listed twice`);
    }
  });

  await s.step('a surname with an accented capital keeps its place across the page boundary', async () => {
    await k.submitSearch(popup, 'search_name', tagB);
    const first = await readOwned(idsB);
    h.assert(first.length === PAGE, `Page one listed ${first.length} accent-fixture rows, not ${PAGE}`);
    await k.clickPager(popup, 0, 1);
    const second = await readOwned(idsB);
    const names = [...first, ...second].map(r => r.name.split(',')[0].replace(/^.*?-/, '').replace(/^.*?-/, ''));
    if (names.length !== 12 || !k.nonDecreasing(names)) {
      defects.push(`surnames are not in order across the page boundary: ${names.join(' | ')}`);
    }
  });

  await s.step('the last full page does not offer a Next Page that leads to an empty page', async () => {
    await k.submitSearch(popup, 'search_name', tagA);
    await k.clickPager(popup, 0, 1);
    const pager = await k.pagerButtons(popup, PAGE);
    if (!pager.next) return;
    await k.clickPager(popup, PAGE, 1);
    const empty = (await k.readResultRows(popup)).length;
    defects.push(`page two is the last page (20 results, ${PAGE} a page) yet offers Next Page, which leads to ${empty} rows`);
  });

  await s.step('paging showed every patient once, in order, with no dead end', async () => {
    h.assert(defects.length === 0, `Patient search paging defects: ${defects.join('; ')} `
      + '(demographicsearchresults.jsp:507 offers Next whenever the page is full; the page re-sort at demographicsearchresults.jsp:375 '
      + 'compares with the accent- and case-sensitive Java comparators of Demographic.java, while the database page order is accent-insensitive)');
  });
}

if (require.main === module) runWorkflow('search-sort-patient-pages', workflow, { openPatient: false });
module.exports = { workflow };
