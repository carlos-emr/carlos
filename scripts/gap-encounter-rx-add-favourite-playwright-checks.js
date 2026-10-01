#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx "Add to Favorites" from a staged prescription card and from the legacy write-script page, then
 * re-use of the saved favourite.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ Prescriptions "+" (the Rx page) ▸ Custom Drug ▸
 * dosing fields ▸ the card's "Add to Favorites" (browser prompt, POST rx/addFavorite2) ▸ the side
 * panel's Favorites list ▸ the saved favourite stages a card again (rx/useFavorite); then Search for
 * a drug that does not exist ▸ Custom Drug link (rx/chooseDrug) ▸ the write-script page's "Add to
 * Favorites" (rx/addFavoriteWriteScript). Cancelling the name prompt must write nothing.
 * Asserts the favorites row each save writes (provider, name, the custom drug name, the instruction
 * text and repeats typed on the card), that the Rx page lists the favourite and re-staging
 * it reproduces the drug name and instruction on a new card, that the write-script save answers the
 * write-script page and stores a row for the staged drug, and that a refused prompt stores nothing.
 * rx-favorites-choose-drug seeds favourites by SQL and covers the Edit favourites page, copy and
 * sharing; rx-legacy-null-fields covers favouriting a saved drug from the history; neither adds a
 * favourite from a card or from the write-script page.
 * Fixtures: the owned synthetic patient and marker-named favourites of the signed-in provider, and
 * session-only custom drug cards (nothing is saved to the chart). Cleanup deletes the favourites by
 * marker and asserts them gone. Implements gap-encounter "save a prescription as a favourite".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, provider, patient } = s;
  const cardDrug = `${marker}-card`;
  const cardFavourite = `${marker}-card-fav`;
  const legacyDrug = `${marker}-legacy`;
  const legacyFavourite = `${marker}-legacy-fav`;
    const favouriteRows = name => sql.rows(`SELECT favoriteid, favoritename, customName, special, quantity, \`repeat\`
    FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(name)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM favorites WHERE provider_no=${q(provider)} AND favoritename LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE favoritename LIKE ${q(`${marker}%`)}`) === '0',
      'Owned favourites were not removed');
  });

  const chart = await s.chart();
  let rx;
  let key;
  await s.step('E-Chart ▸ Prescriptions "+" opens Rx; a custom drug card takes dosing text', async () => {
    rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
    key = await stageCustomDrug(rx, cardDrug);
    await rx.locator(`#instructions_${key}`).fill('Take one tablet twice daily');
    await rx.locator(`#instructions_${key}`).blur();
    // Leaving the Qty box throws (getCost: Insertion is not defined, known defect), so Qty is left alone here.
    await rx.locator(`#repeats_${key}`).fill('2');
    await rx.locator(`#repeats_${key}`).blur();
    h.assert(await rx.locator(`#set_${key}`).count() === 1, 'The custom drug card was not staged');
  });

  await s.step('cancelling the favourite-name prompt writes nothing', async () => {
    const dialogs = await h.withExpectedDialogs(rx, () => rx.locator(`#set_${key} a[onclick^="addFav("]:visible`).first().click(),
      { accept: false });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Add to Favorites did not ask for a name');
    h.assert(sql.value(`SELECT COUNT(*) FROM favorites WHERE provider_no=${q(provider)} AND favoritename LIKE ${q(`${marker}%`)}`) === '0',
      'A cancelled favourite prompt stored a favourite');
  });

  await s.step('card ▸ Add to Favorites stores the favourite with the typed dosing text', async () => {
    const saved = rx.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addFavorite2') && r.request().method() === 'POST');
    const dialogs = await h.withExpectedDialogs(rx, async () => {
      await rx.locator(`#set_${key} a[onclick^="addFav("]:visible`).first().click();
      h.assert((await saved).ok(), 'rx/addFavorite2 was refused');
    }, { accept: true, promptText: cardFavourite });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Add to Favorites did not ask for a name');
    await expectValue(sql, `SELECT COUNT(*) FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(cardFavourite)}`, '1',
      'Add to Favorites stored no favourite for the signed-in provider');
    const [row] = favouriteRows(cardFavourite);
    h.assert(row[2] === cardDrug, 'The favourite lost the custom drug name');
    h.assert(row[3] === 'Take one tablet twice daily', 'The favourite lost the instruction typed on the card');
    h.assert(row[5] === '2', 'The favourite lost the repeats typed on the card');
  });

  await s.step('the reloaded Rx page lists the favourite and choosing it stages a card with the same drug and instruction', async () => {
    await rx.waitForURL(/\/rx\/searchDrug\?/, { timeout: 30000 });
    await rx.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
    const link = rx.locator(`a[title="${cardFavourite}"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    h.assert(/usefav|goSD3|useFav2/.test(await link.getAttribute('onclick')), 'The favourites entry is not a link that stages the favourite');
    await Promise.all([rx.waitForURL(/usefav=true|\/rx\/searchDrug\?/, { timeout: 30000 }), link.click()]);
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await rx.locator("[id^='drugName_']").first().waitFor({ state: 'attached', timeout: 30000 });
    const names = await rx.locator("[id^='drugName_']").evaluateAll(els => els.map(el => el.value));
    h.assert(names.includes(cardDrug), 'Choosing the favourite did not stage its drug');
    const instructions = await rx.locator("[id^='instructions_']").evaluateAll(els => els.map(el => el.value));
    h.assert(instructions.includes('Take one tablet twice daily'), 'The re-staged card lost the favourite instruction');
  });

  // The write-script page lists every staged card of the patient, each with its own "Add to Favorites".
  let writeScript;
  const favouriteByLink = async (link, favouriteName) => {
    const saved = writeScript.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addFavoriteWriteScript'), { timeout: 30000 });
    let response;
    const dialogs = await h.withExpectedDialogs(writeScript, async () => {
      await link.click();
      response = await saved;
    }, { accept: true, promptText: favouriteName });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'The write-script Add to Favorites did not ask for a name');
    h.assert(response.request().method() === 'POST', 'rx/addFavoriteWriteScript was not a POST');
    return response;
  };

  await s.step('Search (no match) ▸ Custom Drug ▸ the write-script page lists the staged cards', async () => {
    writeScript = await s.context.newPage();
    await h.gotoApp(writeScript, s.config.baseUrl, `/rx/searchDrug?demographicNo=${patient}&searchString=${encodeURIComponent(`${legacyDrug}-nosuchdrug`)}`);
    await writeScript.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const chosen = writeScript.waitForResponse(r => /\/rx\/chooseDrug(\?|$)/.test(r.url()), { timeout: 30000 });
    chosen.catch(() => {});
    await h.withExpectedDialogs(writeScript, async () => {
      await writeScript.locator('a[href="javascript:customWarning();"]').click();
      await chosen;
    }, { accept: true });
    await writeScript.locator('form#frm textarea[name="customName"]').waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await writeScript.locator('a[href^="javascript:addFavorite("]').count() >= 2,
      'The write-script page does not offer Add to Favorites for the staged cards');
  });

  await s.step('write-script page ▸ Add to Favorites on the card with instructions stores a second favourite', async () => {
    const row = writeScript.locator('tr', { hasText: cardDrug }).first();
    const response = await favouriteByLink(row.locator('a[href^="javascript:addFavorite("]').first(), legacyFavourite);
    h.assert(response.status() < 400, `rx/addFavoriteWriteScript answered HTTP ${response.status()}`);
    await writeScript.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(writeScript, 'write-script page after Add to Favorites');
    h.assert(await writeScript.locator('form#frm').count() === 1, 'The write-script page was not redisplayed after saving the favourite');
    await expectValue(sql, `SELECT COUNT(*) FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(legacyFavourite)}`, '1',
      'The write-script Add to Favorites stored no favourite');
    const [row2] = favouriteRows(legacyFavourite);
    h.assert(row2[2] === cardDrug && row2[3] === 'Take one tablet twice daily', 'The write-script favourite is not the chosen card');
  });

  // Last: a drug with no instruction text is an ordinary favourite, but the insert violates the NOT NULL
  // `special` column (RxPrescriptionData.Favorite.Save) and the page answers HTTP 500.
  await s.step('write-script page ▸ Add to Favorites on a custom drug with no instructions stores the favourite', async () => {
    const empty = `${marker}-noinstr-fav`;
    const response = await favouriteByLink(writeScript.locator('a[href^="javascript:addFavorite("]').last(), empty);
    h.assert(response.status() < 400, `Add to Favorites on a drug with no instructions answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM favorites WHERE provider_no=${q(provider)} AND favoritename=${q(empty)}`, '1',
      'A drug with no instructions could not be saved as a favourite');
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-add-favourite', workflow, { openPatient: true });
module.exports = { workflow };
