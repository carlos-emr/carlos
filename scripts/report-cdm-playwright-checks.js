#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// report-cdm (coverage plan §3.6 report-cdm): CDM report counts over owned measurements.
// User path: CDM Report entry (oscarReport/oscarMeasurements/SetupSelectCDMReport) ▸ select
// the owned CDM group ▸ Continue to "patients who met guideline", "patients in abnormal range"
// and "frequency of relevant tests" ▸ Generate Report. NO MENU OPENS THIS ENTRY PAGE (finding:
// the plan's "Query By Example ▸ CDM report" link does not exist), so this check alone opens
// its route by address and drives every form from there. Invalid submissions return the
// same authorized form with visible validation feedback.
// Asserts each report line for the owned measurement type equals the counts SQL gives for the
// same window (two owned patients, latest reading semantics, date-window exclusion), and that
// the clinic-wide "patients seen" figure equals SQL. cdm-measurement-report already covers the
// AACP instruction lists and single-patient numeric guideline/range lines; nothing here repeats it.
// Fixtures: a per-run measurement type and CDM group (marker names), the runWorkflow patient and
// a second marker patient, five readings; all deleted and checked gone in cleanup.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

const SETUP_ROUTE = '/oscarReport/oscarMeasurements/SetupSelectCDMReport';
const INSTRUCTION = 'fixture reading';

async function openScreen(s, group, forward) {
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, SETUP_ROUTE);
  await h.assertNotErrorPage(page, 'CDM report entry page');
  await page.locator('select[name="value(CDMgroup)"]').selectOption(group);
  await Promise.all([
    page.waitForURL(/\/oscarReport\/oscarMeasurements\/SelectCDMReport(?:$|[?#])/),
    page.locator(`input[type="button"][onclick*="set('${forward}')"]`).click(),
  ]);
  await h.assertNotErrorPage(page, `CDM ${forward} screen`);
  return page;
}

/** Index of the owned type's row, from the hidden type field each row carries. */
async function rowOf(page, prefix, type) {
  const rows = await page.locator(`input[type="hidden"][name^="value(${prefix}"]`).evaluateAll(
    (inputs, p) => inputs.map(input => ({ index: input.name.slice(`value(${p}`.length, -1), type: input.value })), prefix);
  const row = rows.find(candidate => candidate.type === type);
  h.assert(row && /^\d+$/.test(row.index), 'The CDM screen has no row for the owned measurement type');
  return Number(row.index);
}

async function generate(page, route) {
  await Promise.all([
    page.waitForURL(url => url.pathname.endsWith(`/oscarReport/oscarMeasurements/${route}`)),
    page.locator('input[type="submit"][name="submitBtn"]').click(),
  ]);
  await h.assertNotErrorPage(page, `CDM ${route}`);
  return (await page.locator('body').innerText()).replace(/\s+/g, ' ');
}

/** "(met/total)" from the report line for the owned type and instruction. */
function counts(text, prefix, label) {
  const start = text.indexOf(prefix);
  h.assert(start >= 0, `The ${label} report has no line for the owned type and instruction`);
  const match = /^\D*?(\d+(?:\.\d+)?)\/(\d+(?:\.\d+)?)/.exec(text.slice(start + prefix.length));
  h.assert(match, `The ${label} report line has no met/total counts`);
  return [Number(match[1]), Number(match[2])];
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const type = marker;
  const display = `${marker}-display`;
  const group = `${marker}-CDM`;
  let second;

  s.cleanup(() => {
    const owned = [patient, second].filter(Boolean).join(',');
    sql.execute(`DELETE FROM measurements WHERE type=${q(type)} AND demographicNo IN (${owned});
      DELETE FROM measurementGroup WHERE name=${q(group)};
      DELETE FROM measurementType WHERE type=${q(type)} AND typeDisplayName=${q(display)}`);
    if (second) sql.execute(`DELETE FROM demographic WHERE demographic_no=${second} AND last_name=${q(`${marker}-B`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE type=${q(type)})
      + (SELECT COUNT(*) FROM measurementGroup WHERE name=${q(group)})
      + (SELECT COUNT(*) FROM measurementType WHERE type=${q(type)})
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(`${marker}-B`)})`) === '0', 'The CDM report fixtures were not removed');
  });

  const validation = sql.value('SELECT id FROM validations WHERE isNumeric=1 AND minValue<=1 AND maxValue1>=30 ORDER BY id LIMIT 1');
  h.assert(/^\d+$/.test(validation), 'This install has no numeric validation for the fixture type');
  sql.execute(`INSERT INTO measurementType (type, typeDisplayName, typeDescription, measuringInstruction, validation, createDate)
      VALUES (${q(type)}, ${q(display)}, 'CDM report workflow fixture', ${q(INSTRUCTION)}, ${q(validation)}, NOW());
    INSERT INTO measurementGroup (name, typeDisplayName) VALUES (${q(group)}, ${q(display)})`);
  second = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(`${marker}-B`)},'Workflow','1970','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(second), 'The second patient fixture was not created');
  // Patient A: 5 forty days ago, 10 ten days ago (latest). Patient B: 20 five hundred days ago
  // (outside the one-year window), 4 five days ago (latest).
  const readings = [[patient, '5', 40], [patient, '10', 10], [second, '20', 500], [second, '4', 5]];
  sql.execute(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction, comments, dateObserved, dateEntered)
    VALUES ${readings.map(([demographic, value, days]) => `(${q(type)}, ${demographic}, ${q(provider)}, ${q(value)}, ${q(INSTRUCTION)}, '',
      CONCAT(CURDATE() - INTERVAL ${days} DAY, ' 09:00:00'), CONCAT(CURDATE() - INTERVAL ${days} DAY, ' 09:30:00'))`).join(',')}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE type=${q(type)}`) === '4', 'The reading fixtures were not created');
  const [narrowStart, narrowEnd] = sql.rows('SELECT CURDATE() - INTERVAL 45 DAY, CURDATE() - INTERVAL 20 DAY')[0];
  const line = `${type} ${INSTRUCTION} -> `;

  await s.step('"Patients who met guideline" counts the latest in-window reading of each owned patient (> 6: 1 of 2)', async () => {
    const page = await openScreen(s, group, 'patientWhoMetGuideline');
    const row = await rowOf(page, 'measurementType', type);
    await page.locator('input[name="patientSeenCheckbox"]').uncheck();
    await page.locator(`input[name="guidelineCheckbox"][value="${row}"]`).check();
    await page.locator('input[name="guidelineB"]').nth(row).fill('6');
    await page.locator(`input[name="value(aboveBelow${row})"][value=">"]`).check();
    const text = await generate(page, 'InitializePatientsMetGuidelineCDMReport');
    const [met, total] = counts(text, line, 'met-guideline');
    h.assert(met === 1 && total === 2, `Met guideline > 6 counted ${met} of ${total}; SQL fixtures give 1 of 2`);
    await page.close();
  });

  await s.step('"Patients who met guideline" honours the date window (one owned patient in range, below 6)', async () => {
    const page = await openScreen(s, group, 'patientWhoMetGuideline');
    const row = await rowOf(page, 'measurementType', type);
    await page.locator('input[name="patientSeenCheckbox"]').uncheck();
    await page.locator(`input[name="guidelineCheckbox"][value="${row}"]`).check();
    await page.locator('input[name="guidelineB"]').nth(row).fill('6');
    await page.locator(`input[name="value(aboveBelow${row})"][value=">"]`).check();
    await page.locator('input[name="startDateB"]').nth(row).fill(narrowStart);
    await page.locator('input[name="endDateB"]').nth(row).fill(narrowEnd);
    const text = await generate(page, 'InitializePatientsMetGuidelineCDMReport');
    const [met, total] = counts(text, line, 'met-guideline');
    h.assert(met === 0 && total === 1, `Met guideline in ${narrowStart}..${narrowEnd} counted ${met} of ${total}; SQL fixtures give 0 of 1`);
    await page.close();
  });

  await s.step('"Patients in abnormal range" 3 to 5 counts only the owned patient whose latest reading is 4 (1 of 2)', async () => {
    const page = await openScreen(s, group, 'patientInAbnormalRange');
    const row = await rowOf(page, 'measurementTypeC', type);
    await page.locator('input[name="patientSeenCheckbox"]').uncheck();
    await page.locator(`input[name="abnormalCheckbox"][value="${row}"]`).check();
    await page.locator('input[name="lowerBound"]').nth(row).fill('3');
    await page.locator('input[name="upperBound"]').nth(row).fill('5');
    const text = await generate(page, 'InitializePatientsInAbnormalRangeCDMReport');
    const [inRange, total] = counts(text, `${line}From 3 to 5: `, 'abnormal-range');
    h.assert(inRange === 1 && total === 2, `Abnormal range 3..5 counted ${inRange} of ${total}; SQL fixtures give 1 of 2`);
    await page.close();
  });

  // Invalid dates return the same authorized form with escaped validation feedback.
  const invalidDate = [
    ['patientWhoMetGuideline', 'measurementType', 'guidelineCheckbox', 'startDateB', 'InitializePatientsMetGuidelineCDMReport'],
    ['patientInAbnormalRange', 'measurementTypeC', 'abnormalCheckbox', 'startDateC', 'InitializePatientsInAbnormalRangeCDMReport'],
    ['freqencyOfReleventTests', 'measurementTypeD', 'frequencyCheckbox', 'startDateD', 'InitializeFrequencyOfRelevantTestsCDMReport'],
  ];
  await s.step('an invalid start date returns each CDM form with its error, owned row, and no report', async () => {
    for (const [forward, prefix, checkbox, dateField, route] of invalidDate) {
      const page = await openScreen(s, group, forward);
      const row = await rowOf(page, prefix, type);
      for (const field of ['startDateA', `${dateField}[${row}]`, `${dateField.replace('start', 'end')}[${row}]`]) {
        const calendar = page.locator(`button[onclick*="type=${field}&"]`);
        const label = await calendar.getAttribute('aria-label');
        h.assert(label.trim().length > 0, `${route} has an unnamed calendar control`);
        if (field !== 'startDateA') {
          h.assert(label.includes(display), 'The per-measurement calendar label omits its measurement');
          const input = page.locator(`input[name="${field.split('[')[0]}"]`).nth(row);
          h.assert((await input.getAttribute('aria-label')) === label,
            'The measurement date input and calendar have different accessible labels');
        }
        await calendar.focus();
        const [popup] = await Promise.all([page.waitForEvent('popup'), calendar.press('Enter')]);
        await popup.waitForURL(url => url.pathname.endsWith('/oscarReport/ViewOscarReportCalendarPopup'));
        await popup.locator('span.title').waitFor({state: 'visible'});
        await h.assertNotErrorPage(popup, `${route} calendar`);
        const params = new URL(popup.url()).searchParams;
        h.assert(params.get('type') === field && /^[0-9]{4}$/.test(params.get('year'))
          && Number(params.get('month')) >= 1 && Number(params.get('month')) <= 12,
          `${route} keyboard calendar opened the wrong date field or an invalid year/month`);
        await popup.close();
      }
      await page.locator(`input[name="${checkbox}"][value="${row}"]`).check();
      if (forward === 'patientWhoMetGuideline') await page.locator('input[name="guidelineB"]').nth(row).fill('6');
      if (forward === 'patientInAbnormalRange') {
        await page.locator('input[name="lowerBound"]').nth(row).fill('3');
        await page.locator('input[name="upperBound"]').nth(row).fill('5');
      }
      if (forward === 'freqencyOfReleventTests') {
        for (const name of ['exactly', 'moreThan', 'lessThan']) await page.locator(`input[name="${name}"]`).nth(row).fill('1');
      }
      await page.locator('input[name="patientSeenCheckbox"]').uncheck();
      const instructionPrefix = prefix.replace('measurementType', 'mInstrcsCheckbox');
      const instruction = page.locator(`input[type="checkbox"][name^="value(${instructionPrefix}${row}"]`).first();
      const instructionName = await instruction.getAttribute('name');
      await instruction.uncheck();
      if (forward === 'patientWhoMetGuideline') {
        await page.locator(`input[name="value(aboveBelow${row})"][value="<"]`).check();
      }
      await page.locator(`input[name="${dateField}"]`).nth(row).fill('not-a-date');
      const [validationResponse] = await Promise.all([
        page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith(`/oscarReport/oscarMeasurements/${route}`) && response.status() === 200),
        page.locator('input[type="submit"][name="submitBtn"]').click(),
      ]);
      // Response headers arrive before the navigation's HTML body is parsed.
      // Wait for the returned validation form before inspecting its contents.
      await validationResponse.finished();
      await page.locator('.action-errors[role="alert"]').waitFor({state: 'visible'});
      await h.assertNotErrorPage(page, `CDM ${route}`);
      h.assert(await rowOf(page, prefix, type) === row, `${route} lost the owned measurement row`);
      const text = await page.locator('body').innerText();
      h.assert(!text.includes(line), `${route} produced a report for an invalid date`);
      h.assert(await page.locator('.action-errors[role="alert"]').innerText() === `The date of ${type} is invalid`, `${route} does not show the invalid-date error`);
      h.assert(await page.locator(`input[name="${dateField}"]`).nth(row).inputValue() === 'not-a-date',
        `${route} replaced the submitted invalid date`);
      h.assert(await page.locator(`input[name="${checkbox}"][value="${row}"]`).isChecked(), `${route} cleared the selected report row`);
      h.assert(!(await page.locator('input[name="patientSeenCheckbox"]').isChecked()), `${route} reselected the patient count`);
      h.assert(!(await page.locator(`input[name="${instructionName}"]`).isChecked()), `${route} reselected an omitted instruction`);
      const numbers = forward === 'patientWhoMetGuideline' ? [['guidelineB', '6']]
        : forward === 'patientInAbnormalRange' ? [['lowerBound', '3'], ['upperBound', '5']]
          : [['exactly', '1'], ['moreThan', '1'], ['lessThan', '1']];
      for (const [name, value] of numbers) {
        h.assert(await page.locator(`input[name="${name}"]`).nth(row).inputValue() === value, `${route} lost ${name}`);
      }
      if (forward === 'patientWhoMetGuideline') {
        h.assert(await page.locator(`input[name="value(aboveBelow${row})"][value="<"]`).isChecked(), `${route} lost the comparison`);
      }
      await page.close();
    }
  });

  await s.step('"frequency of relevant tests" and "patients seen" equal SQL', async () => {
    const page = await openScreen(s, group, 'freqencyOfReleventTests');
    const row = await rowOf(page, 'measurementTypeD', type);
    const [start, end] = await Promise.all(['startDateA', 'endDateA'].map(name => page.locator(`input[name="${name}"]`).inputValue()));
    const seenSql = `SELECT COUNT(DISTINCT demographicNo) FROM measurements
      WHERE dateEntered >= STR_TO_DATE(${q(start)}, '%Y-%m-%d') AND dateEntered <= STR_TO_DATE(${q(end)}, '%Y-%m-%d')`;
    await page.locator(`input[name="frequencyCheckbox"][value="${row}"]`).check();
    await page.locator('input[name="exactly"]').nth(row).fill('2');
    await page.locator('input[name="moreThan"]').nth(row).fill('0');
    await page.locator('input[name="lessThan"]').nth(row).fill('1');
    const seenBefore = Number(sql.value(seenSql));
    const text = await generate(page, 'InitializeFrequencyOfRelevantTestsCDMReport');
    const seenAfter = Number(sql.value(seenSql));
    const defects = [];
    // Other workflows may add or remove readings while the report runs: bracket the SQL figure.
    const seen = /There are (\d+) patients seen from/.exec(text);
    const inBracket = n => n >= Math.min(seenBefore, seenAfter) && n <= Math.max(seenBefore, seenAfter);
    if (!seen || !inBracket(Number(seen[1]))) {
      defects.push(`"patients seen" reads ${seen ? seen[1] : 'nothing'}; SQL counts ${seenBefore}..${seenAfter} distinct patients with readings entered ${start}..${end}`);
    }
    const frequency = (kind, times) => {
      if (!text.includes(line)) return null;
      // line is regex-escaped; kind and times come from fixed test calls.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      const match = new RegExp(`${line.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?) \\([^)]*\\) of patients has done the test for ${kind} ${times} times`).exec(text);
      return match ? [Number(match[1]), Number(match[2])] : null;
    };
    const exact = frequency('exactly', 2);
    const more = frequency('more than', 0);
    const less = frequency('less than', 1);
    if (!exact || !more || !less) {
      defects.push('the frequency report printed no exactly/more-than/less-than lines for the selected owned test');
    } else {
      // Patient A has two in-window readings and B one; every other patient seen has none.
      if (exact[0] !== 1) defects.push(`exactly 2 counted ${exact[0]} patients; SQL gives 1`);
      if (more[0] !== 2) defects.push(`more than 0 counted ${more[0]} patients; SQL gives 2`);
      if (less[0] !== exact[1] - 2) defects.push(`less than 1 counted ${less[0]} of ${exact[1]} patients seen; expected ${exact[1] - 2}`);
      if (!inBracket(exact[1])) defects.push(`the frequency denominator is ${exact[1]}; SQL counts ${seenBefore}..${seenAfter}`);
      if (!inBracket(more[1])) defects.push(`the more-than denominator is ${more[1]}; SQL counts ${seenBefore}..${seenAfter}`);
      if (!inBracket(less[1])) defects.push(`the less-than denominator is ${less[1]}; SQL counts ${seenBefore}..${seenAfter}`);
    }
    h.assert(!defects.length, `CDM report defects: ${defects.join('; ')}`);
    await page.close();
  });
  await s.step('A frequency conversion failure preserves authorized access and refuses an unprivileged user', async () => {
    const route = '/oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport';
    const authorized = await openScreen(s, group, 'freqencyOfReleventTests');
    const authorizedToken = await authorized.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    for (const field of ['exactly', 'moreThan', 'lessThan']) {
      const allowed = await s.context.request.post(h.appUrl(s.config.baseUrl, route), {
        form: {'CSRF-TOKEN': authorizedToken, [field]: 'not-an-integer'}, maxRedirects: 0,
      });
      const body = await allowed.text();
      h.assert(allowed.status() === 200 && body.includes('name="submitBtn"'),
        `An authorized ${field} conversion failure did not return the frequency INPUT form`);
      const errorText = await authorized.evaluate(html => new DOMParser().parseFromString(html, 'text/html')
        .querySelector('.action-errors[role="alert"]')?.textContent, body);
      h.assert(errorText && errorText.includes(field),
        `The ${field} conversion failure returned no visible field error`);
      const submitted = await authorized.evaluate(({html, field}) => new DOMParser().parseFromString(html, 'text/html')
        .querySelector(`input[name="${field}"]`)?.value, {html: body, field});
      h.assert(submitted === 'not-an-integer', `The ${field} conversion failure discarded the invalid value`);
      await allowed.dispose();
    }
    await authorized.close();
    const fixture = throwawayLoginFixture({sql, marker, provider, testUser: s.config.testUser});
    const role = `${marker}-nr`; // secObjPrivilege.roleUserGroup is limited to 30 characters.
    let roleNo;
    let roleNameWasAbsent = false;
    s.cleanup(() => {
      fixture.cleanup();
      if (/^[1-9]\d*$/.test(roleNo || '')) {
        sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)} AND objectName IN ('_appointment','_msg')`);
        h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0', 'An owned role grant remains');
      }
      // A successful INSERT can leave its owned row even if retrieving its ID fails.
      if (roleNameWasAbsent) {
        sql.execute(`DELETE FROM secRole WHERE role_name=${q(role)}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned no-report role remains');
      }
    });
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned role already exists');
    roleNameWasAbsent = true;
    roleNo = sql.value(`INSERT INTO secRole (role_name, description) VALUES (${q(role)}, 'Owned report denial fixture'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(roleNo), 'No role ID was returned');
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${q(role)},'_appointment','r',0,${q(provider)}), (${q(role)},'_msg','r',0,${q(provider)});
      `);
    fixture.create({roleNames: [role], expiresTomorrow: true});
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(fixture.providerNo)} AND role_name<>${q(role)}`) === '0',
      'The denial fixture inherited another role');
    h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE security_no=${fixture.securityNo} AND b_ExpireSet=1
      AND date_ExpireDate=DATE_ADD(CURDATE(), INTERVAL 1 DAY)`) === '1', 'The denial login has no enforced expiry');
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}
      AND objectName='_appointment' AND privilege='r'`) === '1', 'The schedule-only grant was not stored exactly');
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)} AND objectName='_report'`) === '0',
      'The denial fixture unexpectedly has report access');
    const context = await h.newContext(s.context.browser(), s.config);
    s.cleanup(() => context.close());
    const schedule = await h.login(context, {...s.config, testUser: fixture.username}, s.recorder, {label: 'cdm-no-report'});
    const token = await schedule.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const response = await context.request.post(h.appUrl(s.config.baseUrl, route), {
      form: {'CSRF-TOKEN': token, lessThan: 'not-an-integer'}, maxRedirects: 0,
    });
    h.assert(response.status() === 403, `Frequency INPUT without report permission answered HTTP ${response.status()}`);
    h.assert(!(await response.text()).includes('name="submitBtn"'), 'An unprivileged user received the report form');
    await response.dispose();
    await context.close();
  });

}

if (require.main === module) runWorkflow('report-cdm', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
