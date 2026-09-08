/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

(async () => {
    const url = process.env.AI_SUMMARY_URL;
    const storageState = process.env.AI_SUMMARY_STORAGE_STATE;
    const demographics = (process.env.AI_SUMMARY_DEMOGRAPHICS || "").split(",");
    assert.ok(url && storageState, "AI_SUMMARY_URL and AI_SUMMARY_STORAGE_STATE are required");
    assert.ok(demographics.length >= 2 && demographics.every(id => /^[1-9][0-9]*$/.test(id)),
        "AI_SUMMARY_DEMOGRAPHICS must contain at least two comma-separated authorized test demographic numbers");
    const output = fs.mkdtempSync(path.join(os.tmpdir(), "ai-summary-chart-browser-"));
    const browser = await chromium.launch({headless: true, args: ["--no-sandbox"]});
    try {
        const context = await browser.newContext({storageState, viewport: {width: 1440, height: 1000}});
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.message));
        assert.equal((await page.goto(url)).status(), 200);
        await page.locator("#workspace").waitFor();
        for (const [index, id] of demographics.entries()) {
            await page.locator("#demographic-no").fill(id);
            await page.getByRole("button", {name: "Open demographic", exact: true}).click();
            await page.waitForURL(current => current.searchParams.get("demographicNo") === id);
            await page.locator("#workspace").waitFor();
            assert.match(await page.locator(".synthetic-banner").innerText(), /partial chart extract/i);
            assert.match(await page.locator(".artifact-footer").innerText(), /no model inference/);
            assert.match(await page.locator("#source-identity > p").innerText(), new RegExp("Demographic number: " + id + "\\b"));
            assert.equal(await page.locator("#source-identity").count(), 1);
            for (const width of [1440, 390, 320]) {
                await page.setViewportSize({width, height: 1000});
                assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
                await page.screenshot({path: path.join(output, `chart-${index}-${width}.png`)});
            }
            await page.setViewportSize({width: 1440, height: 1000});
            if (await page.locator(".claim").count()) {
                await page.locator(".claim").first().click();
                assert.equal(await page.locator("#selected-statement").isVisible(), true);
                assert.equal(await page.locator(".source:visible").count(), 1);
            }
        }
        for (const query of ["0", "-1", "2147483648", "1&demographicNo=2"]) {
            assert.equal((await page.goto(url + "?demographicNo=" + query)).status(), 400);
            assert.equal(await page.locator("#workspace").count(), 0);
        }
        assert.deepEqual(errors, []);
        console.log("PASS: authenticated chart selection, scoped identity, source evidence, three viewport sizes and invalid demographic rejection");
        console.log("Local screenshots (test charts only): " + output);
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
