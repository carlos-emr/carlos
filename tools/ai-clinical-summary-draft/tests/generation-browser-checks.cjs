/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

(async () => {
    const url = process.env.AI_SUMMARY_URL;
    const storageState = process.env.AI_SUMMARY_STORAGE_STATE;
    const id = process.env.AI_SUMMARY_DEMOGRAPHIC;
    const expected = process.env.AI_SUMMARY_EXPECT || "success";
    assert.ok(url && storageState && /^[1-9][0-9]*$/.test(id),
        "URL, authenticated storage state and one seeded NHS test demographic are required");
    assert.ok(["success", "error"].includes(expected));
    const output = fs.mkdtempSync(path.join(os.tmpdir(), "ai-summary-generation-"));
    const browser = await chromium.launch({headless: true, args: ["--no-sandbox"]});
    try {
        const context = await browser.newContext({storageState, viewport: {width: 1440, height: 1000}});
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.message));
        assert.equal((await page.goto(url + "?demographicNo=" + id)).status(), 200);
        const button = page.locator("#generate-summary button");
        assert.equal(await button.isEnabled(), true, "Only complete, authorized NHS fixtures are eligible");
        const sourcesBefore = await page.locator(".source").allTextContents();
        const action = await page.locator("#generate-summary").getAttribute("action");
        const endpoint = new URL(action, url).href;
        assert.equal((await context.request.get(endpoint)).status(), 405);
        const rejected = await context.request.post(endpoint, {form: {demographicNo: id}});
        assert.equal(rejected.status(), 403, "Missing CSRF token must fail before inference");
        // Observe submit synchronously so an immediate local failure cannot race the check.
        let pendingChecked = false;
        page.on("console", message => {
            if (message.text() === "AI_TEST_PENDING:true") pendingChecked = true;
        });
        await page.evaluate(() => {
            document.querySelector("#generate-summary").addEventListener("submit", event => {
                console.log("AI_TEST_PENDING:" + (event.currentTarget.getAttribute("aria-busy") === "true"
                    && event.currentTarget.querySelector("button").disabled));
            });
        });
        const navigation = page.waitForNavigation({waitUntil: "domcontentloaded", timeout: 1860000});
        await button.click({noWaitAfter: true});
        console.log("Submitted synthetic fixture; waiting for local inference.");
        const response = await navigation;
        assert.equal(pendingChecked, true);
        assert.equal(response.status(), 200);
        await page.locator("#workspace").waitFor();
        assert.deepEqual(await page.locator(".source").allTextContents(), sourcesBefore);
        if (expected === "error") {
            assert.equal(await page.locator(".generation-error").isVisible(), true);
            assert.match(await page.locator(".artifact-footer").innerText(), /no model inference/);
            console.log("Error state:", await page.locator(".generation-error").innerText());
        } else {
            assert.equal(await page.locator(".generation-error").count(), 0,
                await page.locator(".generation-error").allTextContents());
            assert.match(await page.locator(".synthetic-banner").innerText(), /Unverified AI draft/);
            assert.match(await page.locator(".artifact-footer").innerText(), /qwen3\.5:.*local Ollama/);
            assert.ok(await page.locator(".claim").count() > 0);
        }
        for (const width of [1440, 390, 320]) {
            await page.setViewportSize({width, height: 1000});
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
            await page.screenshot({path: path.join(output, `${expected}-${width}.png`)});
        }
        await page.setViewportSize({width: 1440, height: 1000});
        await page.locator(".claim").first().click();
        assert.equal(await page.locator("#selected-statement").isVisible(), true);
        assert.equal(await page.locator(".source:visible").count(), 1);
        if (expected === "success") {
            await page.getByRole("link", {name: "Recorded chart facts", exact: true}).click();
            await page.waitForURL(current => current.pathname.endsWith("/AiSummaryPrototype"));
            assert.match(await page.locator(".artifact-footer").innerText(), /no model inference/);
        }
        assert.deepEqual(errors, []);
        console.log(`PASS: ${expected}, unchanged evidence, citations and three viewport sizes. Screenshots: ${output}`);
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
