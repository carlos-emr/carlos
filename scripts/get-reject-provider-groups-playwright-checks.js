#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, provider groups. User path: Schedule ▸ Preferences (personal
 * settings icon) ▸ My Group "View groups" (provider/ViewProviderDisplayMyGroup) ▸ tick a
 * member ▸ Delete (confirm), and ▸ New Group/Add a Member ▸ tick a provider ▸ Save.
 *
 * Both buttons used to POST to provider/providercontrol, the day sheet's dispatcher, which
 * include()s a gate chosen by `displaymode`. Delete included provider/ViewProviderNewGroup,
 * a plain view gate with no POST check whose JSP deleted every `<group><provider>`
 * parameter when submit_form=Delete, so a GET deleted; and a redirect from an include()d
 * page is ignored, so Save and Delete both left a blank page. Since #4515 Delete posts to
 * provider/DeleteMyGroupMembers and Save to provider/SaveMyGroup, both POST-only actions
 * reached directly, and the include routes no longer write.
 *
 * Probes (all against rows this run owns, asserted together in the LAST step):
 *   1. the captured Delete replayed as GET/HEAD for another member (DeleteMyGroupMembers);
 *   2. the retired Delete routes (providercontrol displaymode=newgroup and
 *      ViewProviderNewGroup, submit_form=Delete): a view now answers, so only "no write"
 *      is asserted;
 *   3. the captured Save replayed as GET/HEAD for a new member (SaveMyGroup);
 *   4. the retired Save routes through providercontrol (displaymode=savemygroup, and
 *      displaymode=vary into providersavemygroup.jsp): only "no write" is asserted.
 *
 * Fixtures: one group named from the run marker (10-char limit) seeded by SQL with the
 * test provider and three active demo providers (names copied from `provider`, exactly as
 * the page does); cleanup deletes only rows carrying the group name and asserts it.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-provider-groups';

async function workflow(s) {
  const { sql, provider, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const groupName = `GR${marker.slice(-8)}`;
  const group = q(groupName);
  const member = p => `SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group} AND provider_no=${q(p)}`;
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`) === '0', 'The marker group name already exists');
  const demos = sql.rows(`SELECT provider_no,last_name,first_name FROM provider WHERE status='1' AND provider_no<>${q(provider)}
    AND provider_no NOT LIKE '-%' AND last_name NOT LIKE '%''%' AND first_name NOT LIKE '%''%' ORDER BY provider_no LIMIT 6`);
  if (demos.length < 6) throw new h.SkipCheck('Fewer than six active demo providers are available for a group');
  const [d1, d2, d3, d4, d5, d6] = demos.map(([no]) => no);
  s.cleanup(() => {
    sql.execute(`DELETE FROM mygroup WHERE mygroup_no=${group}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`) === '0', 'Owned group rows were not removed');
  });
  const self = sql.rows(`SELECT last_name,first_name FROM provider WHERE provider_no=${q(provider)}`)[0];
  sql.execute(`INSERT INTO mygroup(mygroup_no,provider_no,last_name,first_name) VALUES
    (${group},${q(provider)},${q(self[0])},${q(self[1])}),`
    + demos.slice(0, 3).map(([no, last, first]) => `(${group},${q(no)},${q(last)},${q(first)})`).join(','));
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`) === '4', 'The marker group was not seeded');

  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  const openGroups = label => s.popup(prefs, prefs.locator('a[href$="/provider/ViewProviderDisplayMyGroup"]'), label);
  let deleted;
  let saved;

  /** Save and Delete must land back on the group list, not on the blank page of #4515. */
  const assertOnGroupList = async (groups, after) => {
    h.assert(new URL(groups.url()).pathname.endsWith('/provider/ViewProviderDisplayMyGroup'),
      `${after} did not return to the group list (left on ${new URL(groups.url()).pathname})`);
    await h.assertNotErrorPage(groups, `group list after ${after}`);
  };

  await s.step('View groups ▸ Delete removes the ticked member of the owned group (POST provider/DeleteMyGroupMembers)', async () => {
    const groups = await openGroups('my-groups-delete');
    const box = groups.locator(`input[name="${groupName}${d1}"]`);
    await box.waitFor({ state: 'attached' });
    await box.check();
    const dialogs = await h.withExpectedDialogs(groups, async () => {
      deleted = await captureRequest(groups, url => url.pathname.endsWith('/provider/DeleteMyGroupMembers'),
        () => clickAndAwaitReload(groups, groups.locator('input[type="submit"].btn-danger'), { label: 'Delete group member' }));
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Deleting a member did not ask for confirmation');
    await expectValue(sql, member(d1), '0', 'Delete through the page did not remove the member');
    h.assert(sql.value(member(d2)) === '1' && sql.value(member(d3)) === '1', 'Delete removed an unticked member');
    await assertOnGroupList(groups, 'Delete');
    await groups.close();
  });

  await s.step('the Delete replayed as GET/HEAD (DeleteMyGroupMembers, and the retired providercontrol and ViewProviderNewGroup routes) against other owned members is recorded', async () => {
    const forMember = (p, extra = {}) => replayParams(deleted.params, { [`${groupName}${d1}`]: null, [`${groupName}${p}`]: groupName, ...extra });
    await ledger.probe(s, { label: 'provider/DeleteMyGroupMembers',
      path: deleted.path, params: forMember(d2), snapshot: () => sql.value(member(d2)) });
    // The routes the Delete used before #4515. They now render the new-group form (HTTP 200),
    // so the probe asserts only that nothing is deleted.
    const retired = { displaymode: 'newgroup', submit_form: 'Delete' };
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=newgroup submit_form=Delete (retired)',
      path: deleted.path.replace(/DeleteMyGroupMembers$/, 'providercontrol'), params: forMember(d3, retired),
      snapshot: () => sql.value(member(d3)), requireStatus: false });
    await ledger.probe(s, { label: 'provider/ViewProviderNewGroup submit_form=Delete (retired)',
      path: deleted.path.replace(/DeleteMyGroupMembers$/, 'ViewProviderNewGroup'), params: forMember(d3, retired),
      snapshot: () => sql.value(member(d3)), requireStatus: false });
  });

  await s.step('New Group/Add a Member ▸ Save adds a provider to the owned group (POST provider/SaveMyGroup)', async () => {
    const groups = await openGroups('my-groups-add');
    await clickAndAwaitReload(groups, groups.locator('a.btn-primary[href$="/provider/ViewProviderNewGroup"]'), { label: 'New Group/Add a Member' });
    await groups.locator('input[name="mygroup_no"]').fill(groupName);
    await groups.locator(`tr:has(input[name^="provider_no"][value="${d4}"]) input.provider-check`).check();
    saved = await captureRequest(groups, url => url.pathname.endsWith('/provider/SaveMyGroup'),
      () => clickAndAwaitReload(groups, groups.locator('input[type="submit"].btn-primary'), { label: 'Save group member' }));
    h.assert(!saved.params.has('displaymode'), 'Save still posts a providercontrol displaymode');
    await expectValue(sql, member(d4), '1', 'Save through the page did not add the member');
    await assertOnGroupList(groups, 'Save');
    await groups.close();
  });

  await s.step('the Save replayed as GET/HEAD, directly and through the retired providercontrol routes, is recorded', async () => {
    // Keep only the ticked row's fields: the page posts hidden fields for every provider.
    const index = [...saved.params.keys()].map(k => /^data(\d+)$/.exec(k)).find(Boolean);
    h.assert(index, 'The captured Save carries no ticked dataN field');
    const i = index[1];
    const forMember = (p, extra = {}) => {
      const [last, first] = demos.find(([no]) => no === p).slice(1);
      return replayParams(new URLSearchParams([['mygroup_no', groupName]]),
        { [`data${i}`]: i, [`provider_no${i}`]: p, [`last_name${i}`]: last, [`first_name${i}`]: first, ...extra });
    };
    await ledger.probe(s, { label: 'provider/SaveMyGroup',
      path: saved.path, params: forMember(d5), snapshot: () => sql.value(member(d5)) });
    // The include routes the Save used before #4515 (providercontrol no longer has a
    // savemygroup mode, and never includes an arbitrary path): only "no write" is asserted.
    const providerControl = saved.path.replace(/SaveMyGroup$/, 'providercontrol');
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=savemygroup (retired include of SaveMyGroup)',
      path: providerControl, params: forMember(d6, { displaymode: 'savemygroup' }),
      snapshot: () => sql.value(member(d6)), requireStatus: false });
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=vary -> providersavemygroup.jsp',
      path: providerControl,
      params: forMember(d6, { displaymode: 'vary', displaymodevariable: '/WEB-INF/jsp/provider/providersavemygroup.jsp' }),
      snapshot: () => sql.value(member(d6)), requireStatus: false });
  });

  await s.step('every provider-group mutation refused GET/HEAD and left the owned group unchanged', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
