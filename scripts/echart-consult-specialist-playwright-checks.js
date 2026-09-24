#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The eChart Consultations box names the specialist (#3896, OntarioMD conformance; adapted
 * from MagentaHealth/Open-O).
 *
 * Each row used to show only the service, so two referrals to the same service could not be
 * told apart without opening them. The row now reads "Service - Last, First" and its tooltip
 * adds the referral date. The specialist name comes from a record staff type in by hand, so
 * the fixture name carries HTML metacharacters: the visible row and the tooltip must render
 * them as text, never as markup.
 *
 * The check owns every row it touches and never depends on the installed reference data:
 * a synthetic FAKE- patient (runWorkflow), a FAKE- consultation service, a FAKE- specialist
 * and two consultation requests -- one naming the specialist, one with no specialist -- all
 * removed afterwards. Creating its own service matters: the Ontario seed stores
 * consultationServices.active as '02' where BC uses '1', so borrowing "an active service"
 * from the install silently skipped every Ontario database.
 *
 * There is no skip path. The tables involved are part of the province-neutral baseline, so
 * the only preconditions are the harness's own (a reachable install, a working test login
 * with a provider, database access); when those are missing the harness reports it.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// EctDisplayAction caps the visible row title at 48 characters (45 + "..."). The fixture
// labels stay under the cap so the visible text can be compared in full, not by prefix.
const MAX_VISIBLE_TITLE = 48;

/** Inserts one row and returns its generated id, failing loudly if nothing was created. */
function insertReturningId(sql, statement, what) {
  const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `the ${what} fixture was not created`);
  return id;
}

/**
 * Finds the one Consultations entry for a request. The id is matched as a whole number
 * (requestId=12 must not match requestId=123), and the entry must be unique and belong to the
 * Consultations module's list, so the assertions below read that exact row and nothing else.
 */
async function consultEntry(chart, requestId) {
  const pattern = new RegExp(`[?&]requestId=${requestId}(?!\\d)`);
  const links = chart.locator('#leftNavBar a.links');
  await chart.waitForFunction((source) => {
    const re = new RegExp(source);
    return [...document.querySelectorAll('#leftNavBar a.links')]
      .some((a) => re.test(`${a.getAttribute('href') || ''} ${a.getAttribute('onclick') || ''}`));
  }, pattern.source, { timeout: 20000 }).catch(() => {});

  const matches = [];
  const total = await links.count();
  for (let i = 0; i < total; i += 1) {
    const link = links.nth(i);
    const target = `${await link.getAttribute('href') || ''} ${await link.getAttribute('onclick') || ''}`;
    if (pattern.test(target)) matches.push(link);
  }
  h.assert(matches.length === 1,
    `expected exactly one eChart entry for the fixture consultation, found ${matches.length}`);
  const [link] = matches;
  const listId = await link.evaluate((a) => (a.closest('ul') || {}).id || '');
  h.assert(/consultation/i.test(listId),
    'the fixture consultation entry is not in the eChart Consultations box');
  h.assert(await link.isVisible(), 'the fixture consultation entry is not visible in the Consultations box');
  return {
    link,
    visible: (await link.innerText()).replace(/\s+/g, ' ').trim(),
    tooltip: (await link.getAttribute('title')) || '',
    rowText: (await link.evaluate((a) => (a.closest('li') || a).textContent || '')).replace(/\s+/g, ' ').trim(),
  };
}

/** The tooltip is "<label> <locale date>"; the date part must be present and non-trivial. */
function assertTooltip(tooltip, label, what) {
  h.assert(tooltip.startsWith(`${label} `), `the ${what} tooltip does not start with "${label}"`);
  const datePart = tooltip.slice(label.length + 1).trim();
  h.assert(/\d/.test(datePart) && datePart !== 'Error',
    `the ${what} tooltip does not carry the referral date`);
}

function assertNoStrayPlaceholders(entry, what) {
  for (const text of [entry.visible, entry.tooltip, entry.rowText]) {
    h.assert(!/\bnull\b/i.test(text), `the ${what} renders a stray "null"`);
    h.assert(!/\bundefined\b/i.test(text), `the ${what} renders a stray "undefined"`);
  }
}

async function workflow(session) {
  const { sql, patient, provider, marker } = session;
  const tag = marker.slice(-6);

  // Own service: never borrowed from the install (see the header on Ontario's active flag).
  const serviceDesc = `FAKE-Svc ${tag}`;
  const serviceId = insertReturningId(sql, `INSERT INTO consultationServices (serviceDesc, active)
    VALUES (${h.sqlString(serviceDesc)}, '1')`, 'consultation service');
  session.cleanup(() => sql.execute(`DELETE FROM consultationServices
    WHERE serviceId=${serviceId} AND serviceDesc=${h.sqlString(serviceDesc)}`));

  // FAKE- specialist whose name carries HTML metacharacters.
  const lastName = `FAKE-<b>${tag}`;
  const firstName = 'Ann&Co';
  const specId = insertReturningId(sql, `INSERT INTO professionalSpecialists
    (fName, lName, lastUpdated, institutionId, departmentId, hideFromView, deleted)
    VALUES (${h.sqlString(firstName)}, ${h.sqlString(lastName)}, NOW(), 0, 0, 0, 0)`, 'specialist');
  session.cleanup(() => sql.execute(`DELETE FROM professionalSpecialists
    WHERE specId=${specId} AND lName=${h.sqlString(lastName)}`));

  const insertConsult = (spec, daysAgo, what) => {
    const requestId = insertReturningId(sql, `INSERT INTO consultationRequests
      (referalDate, serviceId, specId, reason, providerNo, demographicNo, status, urgency, sendTo, lastUpdateDate)
      VALUES (DATE_SUB(CURDATE(), INTERVAL ${daysAgo} DAY), ${serviceId}, ${spec}, ${h.sqlString(marker)},
        ${h.sqlString(provider)}, ${patient}, '1', '2', '', NOW())`, what);
    session.cleanup(() => sql.execute(`DELETE FROM consultationRequestExt WHERE requestId=${requestId};
      DELETE FROM consultationRequests WHERE requestId=${requestId} AND demographicNo=${patient}`));
    return requestId;
  };
  const withSpecialistId = insertConsult(specId, 0, 'consultation request with a specialist');
  const withoutSpecialistId = insertConsult('NULL', 1, 'consultation request without a specialist');

  const specialist = `${lastName}, ${firstName}`;
  const label = `${serviceDesc} - ${specialist}`;
  h.assert(label.length <= MAX_VISIBLE_TITLE, 'the fixture label would be truncated; shorten it');

  await session.step('Consultations entry names the service and the specialist', async () => {
    const entry = await consultEntry(await session.chart(), withSpecialistId);
    // The exact entry reads the full label, so the specialist is visible in it.
    h.assert(entry.visible === label,
      'the Consultations entry for the fixture consultation does not read exactly "Service - Last, First"');
    h.assert(entry.visible.includes(specialist), 'the specialist is not visible in the Consultations entry');
    assertTooltip(entry.tooltip, label, 'Consultations entry');
    assertNoStrayPlaceholders(entry, 'Consultations entry with a specialist');

    // Rendered as text: no element was created from the name, and no entity leaked through
    // (double encoding would show "&amp;" to the clinician).
    h.assert(await entry.link.locator('b').count() === 0, 'the specialist name was rendered as markup');
    h.assert(!/&amp;|&lt;/.test(entry.visible) && !/&amp;|&lt;/.test(entry.tooltip),
      'the specialist name was double-encoded in the Consultations entry');
  });

  await session.step('Consultations entry without a specialist shows only the service', async () => {
    const entry = await consultEntry(await session.chart(), withoutSpecialistId);
    // No specialist: the label is the service alone -- no " - " separator, no "N/A"
    // placeholder, no "null".
    h.assert(entry.visible === serviceDesc,
      'the Consultations entry without a specialist does not read exactly the service name');
    assertTooltip(entry.tooltip, serviceDesc, 'Consultations entry without a specialist');
    h.assert(!entry.tooltip.includes(' - ') && !/\bN\/A\b/.test(entry.tooltip),
      'the Consultations entry without a specialist carries a separator or "N/A" placeholder');
    assertNoStrayPlaceholders(entry, 'Consultations entry without a specialist');
  });
}

if (require.main === module) runWorkflow('echart-consult-specialist', workflow);
module.exports = { workflow };
