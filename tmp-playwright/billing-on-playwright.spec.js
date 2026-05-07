const { test } = require('@playwright/test');

test('Billing ON menu and interaction sweep', async ({ page }) => {
  const base = 'http://localhost:8080';
  const fullBase = `${base}/carlos`;
  const failures = [];

  const isLikelyErrorPage = async () => {
    const body = (await page.textContent('body')) || '';
    const markers = [
      'HTTP Status 500',
      'Internal Server Error',
      'Exception',
      'StackTrace',
      'Stacktrace',
      'java.lang',
      'jsp error',
      'Error processing',
    ];
    return markers.some((m) => body.toLowerCase().includes(m.toLowerCase()));
  };

  const clickLinkAndReport = async (name) => {
    const info = {
      name,
      success: true,
      status: null,
      url: null,
      errors: {
        console: [],
        page: [],
        network: [],
      },
    };

    const link = page.getByRole('link', { name }).first();
    const responses = [];
    const pageErrors = [];
    const consoleErrors = [];

    page.on('pageerror', (e) => pageErrors.push(e.message));
    page.on('console', (msg) => {
      if (['error', 'warning'].includes(msg.type())) {
        consoleErrors.push(`[${msg.type()}] ${msg.text()}`);
      }
    });

    const onResponse = (response) => {
      if (response.request().resourceType() === 'document' && response.url().startsWith(fullBase)) {
        responses.push({
          url: response.url(),
          status: response.status(),
          ok: response.ok(),
        });
      }
    };
    page.on('response', onResponse);

    try {
      const href = await link.getAttribute('href');
      await link.scrollIntoViewIfNeeded();
      await Promise.all([
        page.waitForLoadState('domcontentloaded', { timeout: 15000 }),
        link.click({ timeout: 10000 }),
      ]);
      const snapshot = responses[responses.length - 1] || { status: null, url: page.url() };
      info.status = snapshot.status;
      info.url = snapshot.url;
      const errText = await isLikelyErrorPage();
      if (errText) {
        failures.push({ name, issue: 'error_text', status: info.status, url: info.url, href });
      }
      if (!snapshot.ok && snapshot.status && snapshot.status >= 400) {
        failures.push({ name, issue: 'network_status', status: snapshot.status, url: info.url, href });
      }
      if (pageErrors.length || consoleErrors.length) {
        info.errors.page = pageErrors;
        info.errors.console = consoleErrors;
      }
    } catch (error) {
      info.success = false;
      failures.push({ name, issue: 'interaction', detail: error.message });
    } finally {
      page.removeListener('response', onResponse);
    }

    return info;
  };

  await page.goto(`${fullBase}/`, { waitUntil: 'domcontentloaded' });

  await page.getByLabel('Username').fill('carlosdoc');
  await page.getByLabel('Password').fill('carlos2026');
  await page.getByLabel('Pin').fill('2026');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    page.getByRole('button', { name: /Sign In|Login|Sign in|Login/i }).click(),
  ]);

  const topActions = [];
  const admin = page.getByRole('link', { name: 'Administration' }).first();
  await admin.scrollIntoViewIfNeeded();
  await admin.click();
  await page.waitForTimeout(800);

  const billingOpen = page.getByRole('link', { name: /^Billing$/i }).first();
  if (await billingOpen.count()) {
    await billingOpen.click();
    await page.waitForTimeout(800);
  }

  const billingMenus = [
    'Upload Schedule Of Benefits',
    'Manage Billing Service Code',
    'Manage Private Billing Code',
    'Manage Service Code Display Styles',
    'Manage GST Control',
    'GST Report',
    'Add Billing Location',
    'Manage Billing Form',
    'Simulation OHIP File',
    'Generate OHIP File',
    'Billing Correction',
    'Batch Billing',
    'INR Batch Billing',
    'Upload MOH files',
    'View MOH files',
    'MCEDT Mailbox',
    'Billing Reconciliation',
    'Invoice Reports',
    'End Year Statement',
    'Payment Received Report',
    'Manage Payment Type',
    'Settings',
    'Flu Billing Report',
  ];

  for (const name of billingMenus) {
    const result = await clickLinkAndReport(name);
    topActions.push(result);
    if (result.name === 'Simulation OHIP File') {
      const fields = await page.locator('input[name], select[name], textarea[name]');
      if ((await fields.count()) > 0) {
        const fillable = page.locator('input[type="text"], input[type="date"], select, input[name="xml_vdate"], input[name="xml_appointment_date"]');
        const firstDate = page.locator('input[name="xml_vdate"], input[name$="_vdate"], input[name*="vdate"]').first();
        if (await firstDate.count()) {
          await firstDate.fill('2026-05-01');
        }
        const secondDate = page.locator('input[name="xml_appointment_date"], input[name*="appointment"], input[name*="appt"]').first();
        if (await secondDate.count()) {
          await secondDate.fill('2026-05-03');
        }

        const providerAll = page.locator('input[name="providers"]').first();
        if (await providerAll.count()) {
          await providerAll.check();
        } else {
          const providersDropdown = page.locator('select[name="providers"], select[name*="provider"]').first();
          if (await providersDropdown.count()) {
            await providersDropdown.selectOption({ label: /All/i });
          }
        }

        const submit = page.locator('button[type="submit"], input[type="submit"]').first();
        if (await submit.count()) {
          const before = page.url();
          await Promise.all([
            page.waitForLoadState('domcontentloaded', { timeout: 20000 }),
            submit.click(),
          ]);
          const simulationError = await isLikelyErrorPage();
          if (simulationError) {
            failures.push({
              name: 'Simulation OHIP File submit',
              issue: 'error_text_after_submit',
              url: page.url(),
            });
          }
          if (page.url() === before) {
            failures.push({ name: 'Simulation OHIP File submit', issue: 'no_navigation', url: page.url() });
          }
        }
      }
    }

    if (result.name === 'Generate OHIP File') {
      const submit = page.locator('button[type="submit"], input[type="submit"]').first();
      if (await submit.count()) {
        const preview = page.locator('button:has-text("Generate"), input[value*="Generate" i], input[value*="Preview" i]').first();
        if (await preview.count()) {
          await preview.click();
          await page.waitForLoadState('domcontentloaded');
          if (await isLikelyErrorPage()) {
            failures.push({ name: 'Generate OHIP File submit', issue: 'error_text_after_submit', url: page.url() });
          }
        }
      }
    }

    if (result.name === 'Billing Correction') {
      await page.waitForTimeout(500);
      const openDemo = page.getByRole('link', { name: 'Invoice Reports' }).first();
      if (await openDemo.count()) {
        const report = await clickLinkAndReport('Invoice Reports');
        topActions.push(report);
      }
    }

    if (result.name === 'Settings') {
      const settingsCards = page.locator('a').filter({ hasText: /Billing|OHIP|GST|Report|Provider|Private/i });
      if ((await settingsCards.count()) > 0) {
        await settingsCards.first().click({ timeout: 5000 }).catch(() => {});
        await page.waitForLoadState('domcontentloaded');
      }
    }

    await page.waitForTimeout(250);
  }

  // Additional route action from patient context: open invoice correction path via direct route from known demo chart.
  const patientRoutes = [
    '/carlos/provider/ViewAppointmentAdminDay',
    '/carlos/DemographicSearch.jsp',
  ];

  for (const route of patientRoutes) {
    const response = await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded' });
    if (!response || response.status() >= 400) {
      failures.push({ name: `Direct route ${route}`, issue: 'route_status', status: response ? response.status() : null });
    }
    await page.waitForTimeout(250);
  }

  await page.goto(`${fullBase}/billing?billRegion=ON&billForm=GP`, { waitUntil: 'domcontentloaded' });
  const invoiceStatus = await isLikelyErrorPage();
  if (invoiceStatus) {
    failures.push({ name: 'Create Invoice by route', issue: 'error_text_after_navigation', url: page.url() });
  }

  console.log('### BILLING ON SWEEP RESULTS ###');
  for (const item of topActions) {
    const status = item.status || 'n/a';
    console.log(`[menu] ${item.name} -> ${item.url} (${status})${!item.success ? ' FAIL' : ''}`);
  }
  for (const f of failures) {
    console.log(`[FAIL] ${f.name}: ${f.issue} ${f.status ? `(status ${f.status})` : ''} ${f.url || ''} ${f.detail || ''}`);
  }

  if (failures.length > 0) {
    console.log(`FAILURES: ${failures.length}`);
    throw new Error(`Billing ON sweep reported ${failures.length} issue(s)`);
  }
});
