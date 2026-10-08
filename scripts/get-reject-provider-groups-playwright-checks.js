#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, provider groups. User path: Schedule ▸ Preferences (personal
 * settings icon) ▸ My Group "View groups" (provider/ViewProviderDisplayMyGroup) ▸ tick a
 * member ▸ Delete (confirm), and ▸ New Group/Add a Member ▸ tick a provider ▸ Save.
 *
 * Both buttons POST to provider/providercontrol, the day sheet's dispatcher, which
 * include()s a gate chosen by `displaymode`. Delete includes provider/ViewProviderNewGroup,
 * a plain view gate with no POST check whose JSP (providernewgroup.jsp) deletes every
 * `<group><provider>` parameter when submit_form=Delete. Neither "providercontrol" nor
 * "ViewProviderNewGroup" has a mutator prefix, so HttpMethodGuardFilter passes the GET.
 * Save includes provider/SaveMyGroup, which does require POST; but providercontrol also
 * accepts displaymode=vary&displaymodevariable=<any path> and include()s it unchecked, so
 * /WEB-INF/jsp/provider/providersavemygroup.jsp can be reached directly, bypassing that
 * POST check (and any gate's privilege check).
 *
 * Probes (all against rows this run owns, asserted together in the LAST step):
 *   1. the captured Delete replayed as GET/HEAD for another member (providercontrol);
 *   2. the same Delete sent straight to provider/ViewProviderNewGroup;
 *   3. the captured Save replayed as GET/HEAD for a new member (providercontrol ▸
 *      SaveMyGroup; the outer controller must propagate the included gate's 405);
 *   4. that Save replayed through displaymode=vary into providersavemygroup.jsp.
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

  await s.step('View groups ▸ Delete removes the ticked member of the owned group (POST providercontrol, displaymode=newgroup)', async () => {
    const groups = await openGroups('my-groups-delete');
    const box = groups.locator(`input[name="${groupName}${d1}"]`);
    await box.waitFor({ state: 'attached' });
    await box.check();
    const dialogs = await h.withExpectedDialogs(groups, async () => {
      deleted = await captureRequest(groups, url => url.pathname.endsWith('/provider/providercontrol'),
        () => clickAndAwaitReload(groups, groups.locator('input[type="submit"].btn-danger'), { label: 'Delete group member' }));
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Deleting a member did not ask for confirmation');
    h.assert(deleted.params.get('displaymode') === 'newgroup' && deleted.params.get('submit_form') === 'Delete',
      'The Delete button did not post displaymode=newgroup&submit_form=Delete');
    await expectValue(sql, member(d1), '0', 'Delete through the page did not remove the member');
    h.assert(sql.value(member(d2)) === '1' && sql.value(member(d3)) === '1', 'Delete removed an unticked member');
    await groups.close();
  });

  await s.step('the Delete replayed as GET/HEAD (providercontrol and ViewProviderNewGroup) against other owned members is recorded', async () => {
    const forMember = p => replayParams(deleted.params, { [`${groupName}${d1}`]: null, [`${groupName}${p}`]: groupName });
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=newgroup submit_form=Delete',
      path: deleted.path, params: forMember(d2), snapshot: () => sql.value(member(d2)) });
    await ledger.probe(s, { label: 'provider/ViewProviderNewGroup submit_form=Delete',
      path: deleted.path.replace(/providercontrol$/, 'ViewProviderNewGroup'), params: forMember(d3),
      snapshot: () => sql.value(member(d3)) });
  });

  await s.step('New Group/Add a Member ▸ Save adds a provider to the owned group (POST providercontrol, displaymode=savemygroup)', async () => {
    const groups = await openGroups('my-groups-add');
    await clickAndAwaitReload(groups, groups.locator('input[type="submit"].btn-primary'), { label: 'New Group/Add a Member' });
    await groups.locator('input[name="mygroup_no"]').fill(groupName);
    await groups.locator(`tr:has(input[name^="provider_no"][value="${d4}"]) input.provider-check`).check();
    saved = await captureRequest(groups, url => url.pathname.endsWith('/provider/providercontrol'),
      () => clickAndAwaitReload(groups, groups.locator('input[type="submit"].btn-primary'), { label: 'Save group member' }));
    h.assert(saved.params.get('displaymode') === 'savemygroup', 'Save did not post displaymode=savemygroup');
    await expectValue(sql, member(d4), '1', 'Save through the page did not add the member');
    await groups.close();
  });

  await s.step('the Save replayed as GET/HEAD, directly and through displaymode=vary, is recorded', async () => {
    // Keep only the ticked row's fields: the page posts hidden fields for every provider.
    const index = [...saved.params.keys()].map(k => /^data(\d+)$/.exec(k)).find(Boolean);
    h.assert(index, 'The captured Save carries no ticked dataN field');
    const i = index[1];
    const forMember = (p, extra = {}) => {
      const [last, first] = demos.find(([no]) => no === p).slice(1);
      return replayParams(new URLSearchParams([['displaymode', saved.params.get('displaymode')], ['mygroup_no', groupName]]),
        { [`data${i}`]: i, [`provider_no${i}`]: p, [`last_name${i}`]: last, [`first_name${i}`]: first, ...extra });
    };
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=savemygroup (include of SaveMyGroup)',
      path: saved.path, params: forMember(d5), snapshot: () => sql.value(member(d5)) });
    await ledger.probe(s, { label: 'provider/providercontrol displaymode=vary -> providersavemygroup.jsp',
      path: saved.path,
      params: forMember(d6, { displaymode: 'vary', displaymodevariable: '/WEB-INF/jsp/provider/providersavemygroup.jsp' }),
      snapshot: () => sql.value(member(d6)) });
  });

  await s.step('every provider-group mutation refused GET/HEAD and left the owned group unchanged', async () => {
    ledger.assertAllRefused();
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow };
