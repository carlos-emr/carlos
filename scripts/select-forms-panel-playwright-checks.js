#!/usr/bin/env node
/*
 * Browser regression checks for the Administration > Forms/eForms > Select Forms panel.
 *
 * Issue #3377: Add, Delete, Move Up, and Move Down each POST the shared form to
 * /form/select, whose response is injected into #dynamic-content by the administration
 * AJAX handler. When the success result was a servlet-dispatch forward to the
 * /form/setupSelect action path, that response came back as HTTP 200 with zero bytes and
 * the panel rendered white. This script drives all four buttons through the real
 * administration shell and asserts the panel keeps rendering.
 *
 * The check is state-neutral: it adds an available form, moves it up and back down, then
 * deletes it, leaving the encounter form display order as it was found.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:select-forms-panel-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

// Declared before the validateBaseUrl() call below: `const` bindings are in the
// temporal dead zone until initialized, so a set declared further down the file
// would throw when the validator runs at module load.
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

const badResponses = [];
const consoleIssues = [];

// The Select Forms panel is a table of <select> lists; anything materially smaller than this
// means the AJAX handler injected an error fragment or an empty body rather than the panel.
const MIN_PANEL_BYTES = 500;

// Matches a full dotted-quad IPv4 address only (anchored start-to-end), so a
// hostname like "10.attacker.example" cannot be mistaken for the private
// 10.0.0.0/8 range just because it starts with the same characters as one.
function isPrivateIpv4(host) {
  const match = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!match) {
    return false;
  }
  const octets = match.slice(1).map(Number);
  if (octets.some((octet) => octet > 255)) {
    return false;
  }
  const [a, b] = octets;
  return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

function isLocalHost(rawHost) {
  // URL.hostname keeps the brackets on IPv6 literals (e.g. "[::1]"); strip
  // them so bracketed loopback addresses match the same as their bare form.
  const host = rawHost.toLowerCase().replace(/^\[|\]$/g, '');
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in BASE_URL would ride along on every navigation and could surface
  // in the badResponses diagnostics this script prints on failure.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }

  const host = parsed.hostname.toLowerCase();
  if (!isLocalHost(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    if (status >= 400) {
      badResponses.push({ label, status, url: response.url() });
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    if (/(ReferenceError|SyntaxError|TypeError|Cannot reset buffer|Server did not return expected success response)/i.test(text)) {
      consoleIssues.push({ label, type: message.type(), text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded') {
  const url = appUrl(appPath);
  // BASE_URL is restricted by validateBaseUrl(), and appUrl() only accepts root-relative app paths.
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
  return page.goto(url, { waitUntil, timeout: 30000 });
}

async function login(page) {
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

/** Opens Administration and loads Select Forms into #dynamic-content via the nav link. */
async function openSelectFormsPanel(page) {
  await gotoApp(page, '/administration');
  await page.locator('#dynamic-content').waitFor({ state: 'attached', timeout: 30000 });
  await page.evaluate(() => {
    const link = [...document.querySelectorAll('a.contentLink')]
      .find((anchor) => /\/form\/setupSelect$/.test(anchor.getAttribute('href') || ''));
    if (!link) {
      throw new Error('Administration nav has no Select Forms contentLink');
    }
    link.click();
  });
  await page.locator('#dynamic-content #selectForm').waitFor({ state: 'attached', timeout: 30000 });
}

/** Reads the two form lists plus the injected panel size out of #dynamic-content. */
async function readPanel(page) {
  return page.evaluate(() => {
    const panel = document.getElementById('dynamic-content');
    const options = (name) => [...panel.querySelectorAll(`select[name="${name}"] option`)].map((o) => o.value);
    return {
      bytes: panel.innerHTML.length,
      hasSelectForm: !!panel.querySelector('#selectForm'),
      available: options('selectedAddTypes'),
      selected: options('selectedDeleteTypes'),
    };
  });
}

/**
 * Selects `formName` in the given list, clicks the named button, and returns the refreshed
 * panel together with the size of the POST response that produced it.
 *
 * The POST body is captured directly because that is the contract issue #3377 broke: the
 * response must carry the re-rendered panel. Once a non-empty body has arrived, the panel is
 * read after the handler swaps #dynamic-content — the old #selectForm node detaching is what
 * proves the new markup landed, and that holds even when a no-op action returns byte-identical
 * markup.
 */
async function clickPanelButton(page, { buttonSelector, listName, formName, label }) {
  const previousForm = await page.locator('#dynamic-content #selectForm').elementHandle();
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/form/select'),
    { timeout: 30000 },
  );

  await page.evaluate(({ selector, list, name }) => {
    const panel = document.getElementById('dynamic-content');
    if (list && name) {
      const box = panel.querySelector(`select[name="${list}"]`);
      [...box.options].forEach((option) => { option.selected = option.value === name; });
    }
    const button = panel.querySelector(selector);
    if (!button) {
      throw new Error(`Select Forms panel has no button matching ${selector}`);
    }
    button.click();
  }, { selector: buttonSelector, list: listName, name: formName });

  const response = await responsePromise;
  const responseBytes = (await response.text()).length;
  assert(
    responseBytes >= MIN_PANEL_BYTES,
    `${label}: POST /form/select returned ${response.status()} with ${responseBytes} bytes;`
      + ' the AJAX handler needs the re-rendered Select Forms panel in the response'
      + ' (issue #3377 blank panel)',
  );

  await page.waitForFunction(
    (stale) => {
      const form = document.querySelector('#dynamic-content #selectForm');
      return !!form && form !== stale;
    },
    previousForm,
    { timeout: 30000 },
  );
  return { ...(await readPanel(page)), responseBytes };
}

/**
 * Asserts the AJAX handler injected a real panel rather than an empty body or an
 * error fragment. Deliberately says nothing about how many forms are selected: an
 * install with every encounter form hidden renders a valid panel with an empty
 * selected list, and failing that would be a false alarm rather than a regression.
 */
function assertPanelRendered(panel, label) {
  assert(panel.hasSelectForm, `${label}: #dynamic-content lost the Select Forms form`);
  assert(
    panel.bytes >= MIN_PANEL_BYTES,
    `${label}: #dynamic-content collapsed to ${panel.bytes} bytes (issue #3377 blank panel)`,
  );
}

function sameOrder(actual, expected) {
  return actual.length === expected.length && actual.every((value, i) => value === expected[i]);
}

function sameMembers(actual, expected) {
  return sameOrder([...actual].sort(), [...expected].sort());
}

/**
 * Best-effort rollback for a run that died between the Add and the Delete.
 *
 * Deleting the subject undoes the whole sequence: Add appends it last, and Delete
 * zeroes its display order while decrementing everything above it, so any form the
 * Move Up/Move Down steps displaced lands back on its original order.
 *
 * The decision is made against a panel freshly reloaded from the server, not the
 * DOM left behind by the failure. A mutation whose POST succeeded but whose panel
 * refresh did not would otherwise look un-applied in stale markup and be skipped,
 * leaving the change on disk. Exactly one compensating delete is issued, and only
 * when the server still reports the subject as selected. Failures here are
 * swallowed — the real assertion error must be what surfaces.
 */
async function restoreSubject(page, subject) {
  try {
    await openSelectFormsPanel(page);
    const panel = await readPanel(page);
    if (!panel.selected.includes(subject)) {
      return;
    }
    await clickPanelButton(page, {
      buttonSelector: '#delete',
      label: 'cleanup delete',
      listName: 'selectedDeleteTypes',
      formName: subject,
    });
    console.log(`cleanup: returned ${subject} to the available list`);
  } catch (cleanupError) {
    console.error(
      `WARN cleanup could not return ${subject} to the available list; the encounter form`
        + ` display order may still be modified: ${cleanupError.message}`,
    );
  }
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  let page = null;
  let subject = null;
  let subjectRestored = false;
  try {
    page = await context.newPage();
    wirePage(page, 'select-forms');
    await login(page);
    await openSelectFormsPanel(page);

    const initial = await readPanel(page);
    assertPanelRendered(initial, 'initial load');
    assert(
      initial.available.length > 0,
      'Select Forms has no available forms to exercise Add/Delete; seed at least one hidden encounter form',
    );
    subject = initial.available[0];

    // Acceptance criterion: submitting with nothing selected must not blank the panel.
    const noSelection = await clickPanelButton(page, { buttonSelector: '#add', label: 'add with no selection' });
    assertPanelRendered(noSelection, 'add with no selection');
    assert(
      sameOrder(noSelection.selected, initial.selected)
        && sameMembers(noSelection.available, initial.available),
      'add with no selection changed the form lists',
    );

    const afterAdd = await clickPanelButton(page, {
      buttonSelector: '#add',
      label: 'add',
      listName: 'selectedAddTypes',
      formName: subject,
    });
    assertPanelRendered(afterAdd, 'add');
    assert(afterAdd.selected.includes(subject), `add did not move ${subject} into the selected list`);
    assert(!afterAdd.available.includes(subject), `add left ${subject} in the available list`);

    const afterUp = await clickPanelButton(page, {
      buttonSelector: '#up',
      label: 'move up',
      listName: 'selectedDeleteTypes',
      formName: subject,
    });
    assertPanelRendered(afterUp, 'move up');
    assert(
      afterUp.selected.indexOf(subject) === afterAdd.selected.indexOf(subject) - 1,
      `move up did not raise ${subject} by one position`,
    );

    const afterDown = await clickPanelButton(page, {
      buttonSelector: '#down',
      label: 'move down',
      listName: 'selectedDeleteTypes',
      formName: subject,
    });
    assertPanelRendered(afterDown, 'move down');
    assert(
      afterDown.selected.indexOf(subject) === afterAdd.selected.indexOf(subject),
      `move down did not restore the original position of ${subject}`,
    );

    const afterDelete = await clickPanelButton(page, {
      buttonSelector: '#delete',
      label: 'delete',
      listName: 'selectedDeleteTypes',
      formName: subject,
    });
    assertPanelRendered(afterDelete, 'delete');
    assert(afterDelete.available.includes(subject), `delete did not return ${subject} to the available list`);
    assert(!afterDelete.selected.includes(subject), `delete left ${subject} in the selected list`);
    // Enforce the state-neutrality this check claims: the add/up/down/delete sequence
    // must leave the selected forms in exactly their original order, not merely the same
    // set. Hidden forms all share display order 0, so their order carries no meaning and
    // only membership is compared.
    assert(
      sameOrder(afterDelete.selected, initial.selected),
      `selected form order was not restored: expected ${JSON.stringify(initial.selected)},`
        + ` got ${JSON.stringify(afterDelete.selected)}`,
    );
    assert(
      sameMembers(afterDelete.available, initial.available),
      `available form list was not restored: expected ${JSON.stringify(initial.available)},`
        + ` got ${JSON.stringify(afterDelete.available)}`,
    );
    // The Delete step is itself the rollback; nothing is left for the finally block to undo.
    subjectRestored = true;

    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);

    console.log(`PASS Select Forms panel renders after add, delete, move up, and move down (subject form ${subject})`);
  } finally {
    if (page && subject && !subjectRestored) {
      await restoreSubject(page, subject);
    }
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(`FAIL Select Forms panel checks: ${error.stack || error.message}`);
  process.exit(1);
});
