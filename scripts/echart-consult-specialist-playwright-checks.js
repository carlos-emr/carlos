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
 * The check owns every row it touches: a synthetic patient (runWorkflow), one specialist and
 * one consultation request, all removed afterwards.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(session) {
  const { sql, patient, provider, marker } = session;
  const service = sql.rows(
    "SELECT serviceId, serviceDesc FROM consultationServices WHERE active='1' "
    + "AND serviceDesc <> '' ORDER BY serviceId LIMIT 1",
  )[0];
  if (!service) throw new h.SkipCheck('no active consultation service to refer to');
  const [serviceId, serviceDesc] = service;
  // Short enough to survive the row's title cap alongside the service name.
  const lastName = `PW<b>${marker.slice(-6)}`;
  const firstName = 'Ann&Co';

  const specId = sql.value(`INSERT INTO professionalSpecialists
    (fName, lName, lastUpdated, institutionId, departmentId, hideFromView, deleted)
    VALUES (${h.sqlString(firstName)}, ${h.sqlString(lastName)}, NOW(), 0, 0, 0, 0);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(specId), 'the specialist fixture was not created');
  session.cleanup(() => sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${specId}`));

  const requestId = sql.value(`INSERT INTO consultationRequests
    (referalDate, serviceId, specId, reason, providerNo, demographicNo, status, urgency, sendTo, lastUpdateDate)
    VALUES (CURDATE(), ${serviceId}, ${specId}, ${h.sqlString(marker)}, ${h.sqlString(provider)},
      ${patient}, '1', '2', '', NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(requestId), 'the consultation request fixture was not created');
  session.cleanup(() => sql.execute(`DELETE FROM consultationRequestExt WHERE requestId=${requestId};
    DELETE FROM consultationRequests WHERE requestId=${requestId}`));

  await session.step('Consultations row names the service and the specialist', async () => {
    const chart = await session.chart();
    const link = chart.locator(`#leftNavBar a[href*="requestId=${requestId}"], `
      + `#leftNavBar a[onclick*="requestId=${requestId}"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    const visible = (await link.innerText()).trim();
    const tooltip = (await link.getAttribute('title')) || '';
    const specialist = `${lastName}, ${firstName}`;
    const label = `${serviceDesc.trim()} - ${specialist}`;

    // Visible text is capped; everything that fits must match the full label exactly.
    const shown = visible.replace(/\.\.\.$/, '');
    h.assert(shown.length > 0 && label.startsWith(shown),
      'the Consultations row does not read "Service - Last, First"');
    h.assert(tooltip.startsWith(label), 'the Consultations row tooltip does not start with "Service - Last, First"');
    h.assert(tooltip.length > label.length, 'the Consultations row tooltip does not carry the referral date');

    // Rendered as text: no element was created from the name, and no entity leaked through
    // (double encoding would show "&amp;" to the clinician).
    h.assert(await link.locator('b').count() === 0, 'the specialist name was rendered as markup');
    h.assert(!/&amp;|&lt;/.test(visible) && !/&amp;|&lt;/.test(tooltip),
      'the specialist name was double-encoded in the Consultations row');
  });
}

if (require.main === module) runWorkflow('echart-consult-specialist', workflow);
module.exports = { workflow };
