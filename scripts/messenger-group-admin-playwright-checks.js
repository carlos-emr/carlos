#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for Messenger Group Admin membership rules (issue #3964).
 *
 * Two defects, both invisible until a message is sent:
 *
 *   1. A provider could be added to the same group twice. groupMembers_tbl has no
 *      unique key and nothing checked, so the second click wrote a second row and
 *      every later message to that group reached that provider twice.
 *   2. Deactivated providers (renumbered to a negative provider number) were
 *      still offered as messaging contacts; only the -1 system account was hidden.
 *
 * WHAT IT DRIVES, THE WAY AN ADMINISTRATOR DOES. Schedule > Administration >
 * System Management > Messenger Group Admin, which the Administration shell
 * AJAX-injects into #dynamic-content (the page's own scripts then run inside
 * the shell, so a check that opened /messenger?method=fetch directly would test
 * a shape no operator sees). Then:
 *
 *   a. Manage Contacts lists the active fixture provider and lists NO negative
 *      provider number, including a freshly inserted deactivated one;
 *   b. the "+" tab creates a throwaway group;
 *   c. the group's "Last, First" typeahead offers the fixture provider once
 *      (the group lists are not a source), the Add Contact button stays disabled
 *      until a pick, and adding writes exactly one group row and one registry row;
 *   d. picking the same provider again shows "already in this group", keeps
 *      Add Contact disabled and sends nothing;
 *   e. the server refuses a duplicate on its own: a direct POST for the same
 *      member answers 409 for the group and for the registry (group 0), and the
 *      row counts do not move.
 *
 * Fixtures (all removed in cleanup, including after a failure or a signal): two
 * provider rows -- an active one and a deactivated negative-numbered one -- whose
 * numbers are chosen unused at run time, the throwaway group, and every
 * groupMembers_tbl row for the fixture provider or that group.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:messenger-group-admin-playwright
 *
 * Environment (common contract in lib/playwright-harness.js readConfig()):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (required: the check asserts rows)
 *
 * IMPLEMENTS: coverage plan section 3.4, `messenger-group-admin`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md).
 */

const {
  appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, gotoApp,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickInjectsPanel, clickOpensPopupOrNavigates, typeAutocomplete } = require('./lib/playwright-ui');

const TIMEOUT = 30000;
const stamp = String(Date.now()).slice(-6);
const fixtureLastName = `PWMSGADM${stamp}`;
const deactivatedLastName = `PWMSGNEG${stamp}`;
const groupName = `PW group ${stamp}`;
const childGroupName = `PW child ${stamp}`;
const pickGroupName = `PW pick ${stamp}`;

const state = {
  sql: null,
  activeProviderNo: null,
  deactivatedProviderNo: null,
  groupId: null,
  childGroupId: null,
  pickGroupId: null,
};

function pickUnusedProviderNo(sql, prefix) {
  // provider_no is varchar(6): prefix + digits, retried until the number is free.
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const digits = String(Math.floor(Math.random() * 10 ** (6 - prefix.length))).padStart(6 - prefix.length, '0');
    const candidate = `${prefix}${digits}`;
    if (sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${sqlString(candidate)}`) === '0') {
      return candidate;
    }
  }
  throw new Error(`could not find an unused provider number with prefix ${prefix}`);
}

function insertProvider(sql, providerNo, lastName) {
  sql.execute('INSERT INTO provider (provider_no, last_name, first_name, provider_type, specialty, sex, status, lastUpdateDate)'
    + ` VALUES (${sqlString(providerNo)}, ${sqlString(lastName)}, 'Fixture', 'doctor', '', 'U', '1', NOW())`);
}

function memberRows(groupId) {
  return Number(state.sql.value('SELECT COUNT(*) FROM groupMembers_tbl'
    + ` WHERE provider_No=${sqlString(state.activeProviderNo)} AND facilityId=0 AND groupID=${Number(groupId)}`));
}

function cleanup() {
  const { sql } = state;
  if (!sql) {
    return;
  }
  try {
    const childIds = new Set(sql.rows(`SELECT groupID FROM groups_tbl WHERE groupDesc IN (${sqlString(childGroupName)}, ${sqlString(pickGroupName)})`)
      .map(row => Number(row[0])));
    if (state.childGroupId) childIds.add(state.childGroupId);
    for (const childId of childIds) {
      sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${Number(childId)}`);
      sql.execute(`DELETE FROM groups_tbl WHERE groupID=${Number(childId)}`);
    }
    for (const providerNo of [state.activeProviderNo, state.deactivatedProviderNo]) {
      if (providerNo) sql.execute(`DELETE FROM groupMembers_tbl WHERE provider_No=${sqlString(providerNo)}`);
    }
    if (state.groupId === null) {
      // The group may exist even if the step that records its id failed.
      const id = sql.value(`SELECT groupID FROM groups_tbl WHERE groupDesc=${sqlString(groupName)} LIMIT 1`);
      state.groupId = id ? Number(id) : null;
    }
    if (state.groupId !== null) {
      sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${Number(state.groupId)}`);
      sql.execute(`DELETE FROM groups_tbl WHERE groupID=${Number(state.groupId)}`);
    }
    for (const providerNo of [state.activeProviderNo, state.deactivatedProviderNo]) {
      if (providerNo) {
        sql.execute(`DELETE FROM provider WHERE provider_no=${sqlString(providerNo)}`);
      }
    }
  } finally {
    sql.dispose();
    state.sql = null;
  }
}

/** Expand the accordion section that holds a left-nav link, then return the link. */
async function revealAdminLink(adminPage, selector) {
  const link = adminPage.locator(selector).first();
  assert(await link.count() > 0, `the Administration panel has no ${selector} link for this user`);
  const sectionId = await link.evaluate((node) => {
    const section = node.closest('.accordion-collapse');
    return section ? section.id : null;
  });
  if (sectionId && !(await link.isVisible())) {
    await adminPage.locator(`[data-bs-target="#${sectionId}"]`).first().click();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
  }
  return link;
}

/**
 * POST an add from inside the page, the way the page's own addMember() does, so
 * CSRFGuard's XHR hijack supplies the token. Answers the HTTP status and body.
 */
async function postAdd(page, memberId, groupId) {
  return page.evaluate(({ member, group }) => new Promise((resolve) => {
    // eslint-disable-next-line no-undef
    $.post(`${window.ctx}/messenger?method=add&member=${encodeURIComponent(member)}&group=${encodeURIComponent(group)}`)
      .always((dataOrXhr, _status, xhrOrError) => {
        const xhr = dataOrXhr && dataOrXhr.status !== undefined ? dataOrXhr : xhrOrError;
        resolve({ status: xhr.status, body: xhr.responseText });
      });
  }), { member: memberId, group: String(groupId) });
}

async function postMutation(page, query, endpoint = '/messenger') {
  return page.evaluate(({ queryString, route }) => new Promise(resolve => {
    $.post(`${window.ctx}${route}?${queryString}`).always((dataOrXhr, _status, xhrOrError) => {
      const xhr = dataOrXhr && dataOrXhr.status !== undefined ? dataOrXhr : xhrOrError;
      resolve({ status: xhr.status, body: xhr.responseText });
    });
  }), { queryString: query, route: endpoint });
}

// Consume only the deliberately exercised failures, by exact method, URL,
// status and count. Preserve the recorder and all unrelated strict signals.
function assertExpectedProbeResponses(recorder, expectedResponses) {
  const key = (method, url, status) => JSON.stringify([method, url, status]);
  const remaining = new Map();
  for (const expected of expectedResponses) {
    const id = key(expected.method, expected.url, expected.status);
    remaining.set(id, (remaining.get(id) || 0) + expected.count);
  }
  const networkConsole = new Map();
  const badResponses = recorder.badResponses.filter(entry => {
    const id = key(entry.method, entry.url, entry.status);
    if (entry.label !== 'duplicate-probe' || !(remaining.get(id) > 0)) return true;
    remaining.set(id, remaining.get(id) - 1);
    const consoleId = key('CONSOLE', entry.url, entry.status);
    networkConsole.set(consoleId, (networkConsole.get(consoleId) || 0) + 1);
    return false;
  });
  assert([...remaining.values()].every(count => count === 0), 'A deliberate probe response was not observed');
  // Chromium also reports these exact failed HTTP requests as console errors.
  // Match that built-in message and location against a consumed expected response;
  // application console messages and extra occurrences still fail the check.
  const consoleIssues = recorder.consoleIssues.filter(entry => {
    const match = /^Failed to load resource: the server responded with a status of (\d{3}) \([^\n]*\)$/.exec(entry.text);
    const id = key('CONSOLE', entry.location?.url, match ? Number(match[1]) : 0);
    if (entry.label !== 'duplicate-probe' || entry.type !== 'error' || !match
        || !(networkConsole.get(id) > 0)) return true;
    networkConsole.set(id, networkConsole.get(id) - 1);
    return false;
  });
  assertStrictPage({ ...recorder, badResponses, consoleIssues }, ['duplicate-probe']);
}

async function main() {
  const config = readConfig();
  state.sql = createSqlRunner(config.mysql);
  const { sql } = state;

  state.activeProviderNo = pickUnusedProviderNo(sql, '9');
  state.deactivatedProviderNo = pickUnusedProviderNo(sql, '-9');
  insertProvider(sql, state.activeProviderNo, fixtureLastName);
  insertProvider(sql, state.deactivatedProviderNo, deactivatedLastName);
  sql.execute(`INSERT INTO groups_tbl (parentID,groupDesc) VALUES (0,${sqlString(pickGroupName)})`);
  state.pickGroupId = Number(sql.value(`SELECT groupID FROM groups_tbl WHERE groupDesc=${sqlString(pickGroupName)}`));

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);

    // Schedule > Administration.
    const opener = schedulePage.locator('#admin-panel, #admin2').first();
    assert(await opener.count() > 0, 'the schedule offers no Administration control (#admin-panel / #admin2)');
    const { page: adminPage } = await clickOpensPopupOrNavigates(schedulePage, opener, {
      context, label: 'administration', recorder, timeout: TIMEOUT,
    });
    wireStrictPage(adminPage, 'administration', recorder);

    // System Management > Messenger Group Admin, injected into the shell.
    const adminLink = await revealAdminLink(adminPage, 'a.contentLink[href$="/messenger?method=fetch"]');
    await clickInjectsPanel(adminPage, adminLink, { marker: '#local-contacts', timeout: TIMEOUT });
    await assertNotErrorPage(adminPage, 'messenger group admin');

    // a. Contact list: active fixture present once; no negative provider number at all.
    const contactBoxes = adminPage.locator('#local-contacts input[type="checkbox"]');
    const contactValues = await contactBoxes.evaluateAll((nodes) => nodes.map((node) => node.value));
    assert(contactValues.filter((value) => value.startsWith(`${state.activeProviderNo}-`)).length === 1,
      `Manage Contacts did not list the active fixture provider exactly once (${contactValues.length} contacts listed)`);
    const negatives = contactValues.filter((value) => value.startsWith('-'));
    assert(negatives.length === 0,
      `Manage Contacts listed ${negatives.length} negative (system or deactivated) provider number(s): ${negatives.join(', ')}`);
    assert(!(await adminPage.locator('#local-contacts').innerText()).includes(deactivatedLastName),
      'Manage Contacts listed the deactivated fixture provider by name');
    const memberId = contactValues.find((value) => value.startsWith(`${state.activeProviderNo}-`));
    const initialContactStates = await contactBoxes.evaluateAll(nodes =>
      nodes.map(node => ({ value: node.value, checked: node.checked })));

    // b. Create a throwaway group through the "+" tab.
    await adminPage.locator('a.nav-link[href="#manageGroups"]').click();
    await adminPage.locator('a.nav-link[href="#new-group"]').click();
    await adminPage.locator('#new-group-name').fill(groupName);
    await Promise.all([
      adminPage.waitForResponse((response) => response.request().method() === 'GET'
        && /\/messenger\?method=fetch/.test(response.url()), { timeout: TIMEOUT }),
      adminPage.locator('#add-group-btn').click(),
    ]);
    const groupIdText = sql.value(`SELECT groupID FROM groups_tbl WHERE groupDesc=${sqlString(groupName)} LIMIT 1`);
    assert(/^\d+$/.test(groupIdText), 'creating a group from the "+" tab wrote no groups_tbl row');
    state.groupId = Number(groupIdText);
    const groupTab = adminPage.locator(`a.nav-link[href="#group-${state.groupId}"]`);
    await groupTab.waitFor({ state: 'visible', timeout: TIMEOUT });
    await groupTab.click();

    const search = adminPage.locator(`input.search-provider[id="${state.groupId}"]`);
    const addButton = adminPage.locator(`#add-${state.groupId}`);
    const duplicateAlert = adminPage.locator(`#duplicate-member-${state.groupId}`);
    assert(await addButton.isDisabled(), 'Add Contact was enabled before any contact had been picked');

    // Stage an independent pick in another group before adding in the main group.
    await adminPage.locator(`a.nav-link[href="#group-${state.pickGroupId}"]`).click();
    const otherSearch = adminPage.locator(`input.search-provider[id="${state.pickGroupId}"]`);
    await typeAutocomplete(adminPage, otherSearch, fixtureLastName.slice(0, 10), {
      option: fixtureLastName, hidden: `#add-member-id-${state.pickGroupId}`, timeout: TIMEOUT,
    });
    const otherPickedText = await otherSearch.inputValue();
    const otherPickedId = await adminPage.locator(`#add-member-id-${state.pickGroupId}`).inputValue();
    await groupTab.click();

    // c. Typeahead offers the fixture once; adding writes one group row + one registry row.
    await search.click();
    await search.type(fixtureLastName.slice(0, 10), { delay: 40 });
    const menu = adminPage.locator('.ui-autocomplete:visible li');
    await menu.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const offered = await menu.allInnerTexts();
    assert(offered.filter((text) => text.includes(fixtureLastName)).length === 1,
      `the group typeahead offered the fixture provider ${offered.filter((t) => t.includes(fixtureLastName)).length} times`);
    await search.fill('');
    await typeAutocomplete(adminPage, search, fixtureLastName.slice(0, 10), {
      option: fixtureLastName, hidden: `#add-member-id-${state.groupId}`, timeout: TIMEOUT,
    });
    assert(await addButton.isEnabled(), 'Add Contact stayed disabled after a contact was picked');
    const [addResponse] = await Promise.all([
      adminPage.waitForResponse((response) => response.request().method() === 'POST'
        && /\/messenger\?method=add&/.test(response.url()), { timeout: TIMEOUT }),
      addButton.click(),
    ]);
    assert(addResponse.status() === 200, `adding the contact answered HTTP ${addResponse.status()}`);
    await adminPage.locator(`#group-member-list-${state.groupId} [data-member-key^="${state.activeProviderNo}-"]`)
      .first().waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(memberRows(state.groupId) === 1, `expected one group row after the add, found ${memberRows(state.groupId)}`);
    assert(memberRows(0) === 1, `expected one registry (group 0) row after the add, found ${memberRows(0)}`);
    const updatedContactStates = await contactBoxes.evaluateAll(nodes =>
      nodes.map(node => ({ value: node.value, checked: node.checked })));
    assert(updatedContactStates.find(contact => contact.value === memberId)?.checked === true,
      'successful group add did not check the matching contact');
    assert(initialContactStates.filter(contact => contact.value !== memberId).every(before =>
      updatedContactStates.find(after => after.value === before.value)?.checked === before.checked),
    'successful group add changed an unrelated contact checkbox');
    assert(await addButton.isDisabled(), 'Add Contact stayed enabled after the add, inviting a double submit');
    assert(await otherSearch.inputValue() === otherPickedText && otherPickedText.length > 0,
      'adding in one group cleared another group\'s visible selection');
    assert(await adminPage.locator(`#add-member-id-${state.pickGroupId}`).inputValue() === otherPickedId,
      'adding in one group changed another group\'s pending identifier');
    assert(await adminPage.locator(`#add-${state.pickGroupId}`).isEnabled(),
      'the other group\'s visible pending selection became unusable');
    console.log('PASS independent pending picks in two groups');


    // d. Picking the same provider again is stopped in the page; nothing is posted.
    let duplicatePosts = 0;
    const countAdds = (request) => {
      if (request.method() === 'POST' && /\/messenger\?method=add&/.test(request.url())) {
        duplicatePosts += 1;
      }
    };
    adminPage.on('request', countAdds);
    await typeAutocomplete(adminPage, search, fixtureLastName.slice(0, 10), { option: fixtureLastName, timeout: TIMEOUT });
    await duplicateAlert.waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(await addButton.isDisabled(), 'Add Contact was enabled for a provider already in the group');
    assert(await adminPage.locator(`#add-member-id-${state.groupId}`).inputValue() === '',
      'picking a provider already in the group still staged its id for Add Contact');
    await addButton.click({ force: true, timeout: 2000 }).catch(() => {});
    assert(duplicatePosts === 0, `the page posted ${duplicatePosts} add(s) for a provider already in the group`);
    assert(memberRows(state.groupId) === 1, 'the group row count moved after the in-page duplicate pick');

    // e. The server refuses a duplicate on its own. A separate page so its
    //    deliberate 409s are not read as findings on the admin shell.
    const probe = await context.newPage();
    wireStrictPage(probe, 'duplicate-probe', recorder);
    const expectedResponses = [];
    const expectFailure = (query, status, count = 1) => expectedResponses.push({
      method: 'POST', url: appUrl(config.baseUrl, `/messenger?${query}`), status, count,
    });
    const expectAddFailure = (member, group, status, count = 1) => expectFailure(
      `method=add&member=${encodeURIComponent(member)}&group=${encodeURIComponent(String(group))}`, status, count);
    expectAddFailure(memberId, state.groupId, 409);
    expectAddFailure(memberId, 0, 409);
    expectAddFailure(memberId, 'abc', 400);
    await gotoApp(probe, config.baseUrl, '/messenger?method=fetch');
    await probe.locator('#local-contacts').waitFor({ state: 'attached', timeout: TIMEOUT });
    const groupDuplicate = await postAdd(probe, memberId, state.groupId);
    assert(groupDuplicate.status === 409 && /"reason":"duplicate"/.test(groupDuplicate.body),
      `a duplicate group add answered HTTP ${groupDuplicate.status} (${String(groupDuplicate.body).slice(0, 80)}), expected 409 duplicate`);
    const registryDuplicate = await postAdd(probe, memberId, 0);
    assert(registryDuplicate.status === 409,
      `a duplicate registry (group 0) add answered HTTP ${registryDuplicate.status}, expected 409`);
    // A different administrator may have committed the member while this page was stale.
    await probe.locator('a.nav-link[href="#manageGroups"]').click();
    await probe.locator(`a.nav-link[href="#group-${state.groupId}"]`).click();
    await probe.locator(`#group-member-list-${state.groupId}`).evaluate(node => { node.replaceChildren(); });
    expectAddFailure(memberId, state.groupId, 409);
    await probe.evaluate(({ member, group }) => window.addMember(member, group), { member: memberId, group: state.groupId });
    await probe.locator(`#group-member-list-${state.groupId} [data-member-key^="${state.activeProviderNo}-"]`)
      .first().waitFor({ state: 'visible', timeout: TIMEOUT });
    await probe.locator(`#duplicate-member-${state.groupId}`).waitFor({ state: 'visible', timeout: TIMEOUT });
    const refreshUrl = appUrl(config.baseUrl, '/messenger?method=fetch');
    expectedResponses.push({ method: 'GET', url: refreshUrl, status: 503, count: 1 });
    expectAddFailure(memberId, state.groupId, 409);
    await probe.route(refreshUrl, route => route.fulfill({ status: 503, body: 'Unavailable' }));
    await probe.evaluate(({ member, group }) => window.addMember(member, group), { member: memberId, group: state.groupId });
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    await probe.unroute(refreshUrl);
    console.log('PASS concurrent duplicate refresh and refresh failure feedback');
    const invalidGroup = await postAdd(probe, memberId, 'abc');
    assert(invalidGroup.status === 400, `an add with a non-numeric group answered HTTP ${invalidGroup.status}, expected 400`);
    for (const malformed of ['abc-def', '-9-0-145', '123--1', '123-0-2147483648', '123-0-1-2-3']) {
      const rejected = await postAdd(probe, malformed, state.groupId);
      // A double hyphen is also a SQL-comment signature rejected by the WAF.
      // Action tests still require HTTP 400 when that value reaches Struts.
      const expected = malformed === '123--1' ? [400, 403] : [400];
      assert(expected.includes(rejected.status), `malformed contact answered unexpected HTTP ${rejected.status}`);
      expectAddFailure(malformed, state.groupId, rejected.status);
    }

    // Only the owned fixture memberships are reset. Verify a failed checkbox add
    // restores its unchecked state and shows an actionable error.
    sql.execute(`DELETE FROM groupMembers_tbl WHERE provider_No=${sqlString(state.activeProviderNo)}`);
    await probe.reload({ waitUntil: 'domcontentloaded' });
    expectAddFailure(memberId, 0, 503);
    const addUrl = /\/messenger\?method=add&/;
    await probe.route(addUrl, route => route.fulfill({
      status: 503, contentType: 'application/json', body: '{"success":false}',
    }));
    const fixtureBox = probe.locator(`#local-contacts input[type="checkbox"][value="${memberId}"]`);
    assert(!(await fixtureBox.isChecked()), 'fixture was unexpectedly registered before failure test');
    await fixtureBox.click();
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(!(await fixtureBox.isChecked()), 'failed contact add left the checkbox checked');
    assert(await fixtureBox.isEnabled(), 'failed contact add left the checkbox disabled');
    assert(memberRows(0) === 0, 'failed contact add wrote a registry row');
    await probe.unroute(addUrl);

    // Deliberately concurrent HTTP requests are one regression test: the server
    // must return exactly one new group membership and one registry row.
    expectAddFailure(memberId, state.groupId, 409, 7);
    const raced = await Promise.all(Array.from({ length: 8 }, () => postAdd(probe, memberId, state.groupId)));
    assert(raced.filter(response => response.status === 200).length === 1,
      'concurrent first adds did not report exactly one created membership');
    assert(raced.filter(response => response.status === 409).length === 7,
      'concurrent duplicate adds did not return conflict');
    await probe.reload({ waitUntil: 'domcontentloaded' });
    expectFailure(`method=remove&member=${encodeURIComponent(memberId)}`, 503);
    expectFailure(`method=remove&member=${encodeURIComponent(memberId)}&group=${state.groupId}`, 503);
    expectFailure(`method=remove&group=${state.groupId}`, 503);
    const removeUrl = /\/messenger\?method=remove&/;
    await probe.route(removeUrl, route => route.fulfill({ status: 503, body: 'Unavailable' }));
    await fixtureBox.uncheck();
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(await fixtureBox.isChecked(), 'failed removal left the registry checkbox unchecked');
    assert(await fixtureBox.isEnabled(), 'failed removal left the checkbox disabled');
    assert(memberRows(0) === 1, 'failed removal changed the registry');
    await probe.locator('a.nav-link[href="#manageGroups"]').click();
    await probe.locator(`a.nav-link[href="#group-${state.groupId}"]`).click();
    await probe.locator(`#group-member-list-${state.groupId} i.group-member`).first().click();
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(await probe.locator(`#group-member-list-${state.groupId} .contact-entry`).count() === 1,
      'failed group member removal hid the member');
    await probe.locator(`#delete-${state.groupId}`).click();
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(await probe.locator(`#group-${state.groupId}`).count() === 1, 'failed group deletion hid the group');
    await probe.unroute(removeUrl);
    expectFailure(`method=create&groupName=${encodeURIComponent(groupName + ' rejected')}`, 503);
    const createUrl = /\/messenger\?method=create&/;
    await probe.route(createUrl, route => route.fulfill({ status: 503, body: 'Unavailable' }));
    await probe.locator('a.nav-link[href="#new-group"]').click();
    await probe.locator('#new-group-name').fill(groupName + ' rejected');
    await probe.locator('#add-group-btn').click();
    await probe.locator('#membership-error').waitFor({ state: 'visible', timeout: TIMEOUT });
    assert(sql.value(`SELECT COUNT(*) FROM groups_tbl WHERE groupDesc=${sqlString(groupName + ' rejected')}`) === '0',
      'failed group creation wrote a group');
    await probe.unroute(createUrl);
    console.log('PASS membership removal and group mutation failure feedback');

    // Use a non-signature overflow value so the request reaches application validation
    // through the WAF; its built-in integer-overflow signatures reject 2147483648 first.
    for (const query of [
      'method=remove&group=abc',
      `method=remove&member=123-2147483648&group=${state.groupId}`,
      `method=create&groupName=${encodeURIComponent(childGroupName)}&parentId=2147483649`,
      'method=create&groupName=',
      'method=delete&grpNo=abc',
      `method=update&grpNo=${state.groupId}`,
      `method=update&grpNo=${state.groupId}&update=${encodeURIComponent('Update group members')}&delete=${encodeURIComponent('Delete this group')}`,
    ]) {
      expectFailure(query, 400);
      const result = await postMutation(probe, query);
      assert(result.status === 400, `malformed group mutation returned ${result.status}: ${query}`);
    }
    const childCreated = await postMutation(probe,
      `method=create&groupName=${encodeURIComponent(childGroupName)}&parentId=${state.groupId}`);
    assert(childCreated.status === 200, 'could not create the owned child-group fixture');
    state.childGroupId = Number(sql.value(`SELECT groupID FROM groups_tbl WHERE groupDesc=${sqlString(childGroupName)}`));
    assert(Number.isSafeInteger(state.childGroupId) && state.childGroupId > 0, 'missing child-group fixture');
    assert((await postAdd(probe, `${state.activeProviderNo}-0-149`, state.childGroupId)).status === 200,
      'could not add the child membership with clinic metadata');
    const replaceSelected = `method=update&grpNo=${state.childGroupId}&update=${encodeURIComponent('Update group members')}&providers=${state.activeProviderNo}`;
    assert((await postMutation(probe, replaceSelected)).status === 200, 'legacy replacement of a live group failed');
    assert(sql.value(`SELECT clinicLocationNo FROM groupMembers_tbl WHERE groupID=${state.childGroupId} AND provider_no=${sqlString(state.activeProviderNo)}`) === '149',
      'legacy replacement discarded the selected contact clinic metadata');
    const removeParent = `method=remove&group=${state.groupId}`;
    expectFailure(removeParent, 409);
    assert((await postMutation(probe, removeParent)).status === 409, 'parent with children was deleted');
    assert(sql.value(`SELECT COUNT(*) FROM groups_tbl WHERE groupID=${state.groupId}`) === '1',
      'parent-group rejection still deleted its row');
    assert((await postMutation(probe, `method=remove&group=${state.childGroupId}`)).status === 200,
      'could not delete the child fixture');
    for (const providers of ['', `&providers=${encodeURIComponent(state.activeProviderNo)}`]) {
      const staleUpdate = `method=update&grpNo=${state.childGroupId}&update=${encodeURIComponent('Update group members')}${providers}`;
      expectFailure(staleUpdate, 400);
      assert((await postMutation(probe, staleUpdate)).status === 400, 'stale legacy replacement did not return 400');
    }
    assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${state.childGroupId}`) === '0',
      'stale replacement created orphan memberships');
    for (const type of ['1', '2']) {
      const query = `type2=${type}&parentID=${state.childGroupId}&groupName=${encodeURIComponent(childGroupName)}`;
      expectedResponses.push({ method: 'POST', url: appUrl(config.baseUrl, `/messenger/AddGroup?${query}`), status: 400, count: 1 });
      assert((await postMutation(probe, query, '/messenger/AddGroup')).status === 400,
        'legacy create/rename of a deleted group did not return 400');
    }
    const readMutation = await probe.request.get(appUrl(config.baseUrl,
      `/messenger/AddGroup?type2=1&parentID=${state.groupId}&groupName=${encodeURIComponent(childGroupName)}`));
    assert(readMutation.status() === 405, 'legacy group creation accepted GET');
    assert(sql.value(`SELECT COUNT(*) FROM groups_tbl WHERE groupDesc=${sqlString(childGroupName)}`) === '0',
      'rejected legacy creation still wrote a group');
    console.log('PASS malformed mutations, stale legacy updates, and child-group protection');
    await probe.waitForLoadState('networkidle');
    assertExpectedProbeResponses(recorder, expectedResponses);
    console.log('PASS malformed contacts, failure recovery, and concurrent membership creation');
    await probe.close();
    assert(memberRows(state.groupId) === 1, `the server wrote a second group row (${memberRows(state.groupId)} rows)`);
    assert(memberRows(0) === 1, `the server wrote a second registry row (${memberRows(0)} rows)`);

    // Historical duplicate/retired membership rows must not become duplicate
    // recipients. Keep the stored rows intact and verify the rendered group.
    sql.execute('INSERT INTO groupMembers_tbl (groupID,provider_No,facilityId) VALUES'
      + ` (${Number(state.groupId)},${sqlString(state.activeProviderNo)},0),`
      + ` (${Number(state.groupId)},${sqlString(state.deactivatedProviderNo)},0)`);
    await gotoApp(adminPage, config.baseUrl, '/messenger?method=fetch');
    await adminPage.locator('a.nav-link[href="#manageGroups"]').click();
    await adminPage.locator(`a.nav-link[href="#group-${state.groupId}"]`).click();
    const groupMembers = adminPage.locator(`#group-member-list-${state.groupId}`);
    assert(await groupMembers.locator(`[data-member-key^="${state.activeProviderNo}-"]`).count() === 1,
      'legacy duplicate rows produced duplicate group recipients');
    assert(!(await groupMembers.innerText()).includes(deactivatedLastName),
      'legacy group membership exposed a retired provider');
    assert(memberRows(state.groupId) === 2, 'rendering the group unexpectedly rewrote legacy memberships');
    console.log('PASS legacy duplicate and retired group memberships');
    await Promise.all([
      adminPage.waitForResponse(response => response.request().method() === 'POST'
        && /\/messenger\?method=remove&member=/.test(response.url()), { timeout: TIMEOUT }),
      groupMembers.locator('i.group-member').first().click(),
    ]);
    await groupMembers.locator('.contact-entry').waitFor({ state: 'detached', timeout: TIMEOUT });
    assert(memberRows(state.groupId) === 0, 'removing the legacy recipient left duplicate membership rows');
    assert(memberRows(0) === 1, 'group removal also removed the registry membership');
    await Promise.all([
      adminPage.waitForResponse(response => response.request().method() === 'GET'
        && /\/messenger\?method=fetch/.test(response.url()), { timeout: TIMEOUT }),
      adminPage.locator(`#delete-${state.groupId}`).click(),
    ]);
    assert(sql.value(`SELECT COUNT(*) FROM groups_tbl WHERE groupID=${Number(state.groupId)}`) === '0',
      'group deletion did not delete the group');
    await adminPage.locator('a.nav-link[href="#addContacts"]').click();
    await Promise.all([
      adminPage.waitForResponse(response => response.request().method() === 'POST'
        && /\/messenger\?method=remove&member=/.test(response.url()), { timeout: TIMEOUT }),
      adminPage.locator(`#local-contacts input[type="checkbox"][value="${memberId}"]`).uncheck(),
    ]);
    assert(memberRows(0) === 0, 'removing the contact left its registry membership');
    console.log('PASS group and registry removal without duplicate memberships');


    adminPage.off('request', countAdds);
    assert(duplicatePosts === 0, 'the page posted a delayed add after the duplicate pick');
    assertStrictPage(recorder, ['login', 'administration']);
    return { groupId: state.groupId };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'messenger-group-admin', run: main, cleanup });
}

module.exports = { pickUnusedProviderNo, assertExpectedProbeResponses };
