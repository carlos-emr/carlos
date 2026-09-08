/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
"use strict";
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const url = process.env.AI_SUMMARY_URL;
if (!url) throw new Error("Set AI_SUMMARY_URL to the enabled synthetic prototype or isolated JSP preview.");
const screenshots = fs.mkdtempSync(path.join(os.tmpdir(), "ai-summary-browser-"));
const storageState = process.env.AI_SUMMARY_STORAGE_STATE || undefined;
const previewStates = process.env.AI_SUMMARY_PREVIEW_STATES === "true";

(async () => {
    const browser = await chromium.launch({headless: true, args: ["--no-sandbox"]});
    const errors = [];
    try {
        for (const width of [1440, 1180, 768, 390, 320]) {
            const context = await browser.newContext({viewport: {width, height: 1000}, storageState});
            const page = await context.newPage();
            page.on("pageerror", error => errors.push(error.message));
            page.on("response", response => {
                if (response.status() >= 400) errors.push(response.status() + " " + response.url());
            });
            assert.equal((await page.goto(url)).status(), 200);
            await page.getByRole("tab", {name: "Overview", exact: true}).waitFor();
            await page.evaluate(() => document.fonts.ready);
            assert.equal(await page.locator("#empty-evidence").isVisible(), true);
            assert.equal(await page.locator("#ledger").isVisible(), false);
            await page.screenshot({path: path.join(screenshots, "overview-" + width + ".png"), fullPage: true});
            for (const [name, id] of [["Fact ledger", "ledger"], ["Coverage", "coverage"], ["Validation", "validation"]]) {
                await page.getByRole("tab", {name, exact: true}).click();
                await page.locator("#" + id).waitFor({state: "visible"});
            }
            await page.getByRole("tab", {name: "Overview", exact: true}).click();
            await page.getByRole("tab", {name: "Overview", exact: true}).focus();
            await page.keyboard.press("ArrowRight");
            await page.locator("#ledger").waitFor({state: "visible"});
            await page.getByRole("tab", {name: "Overview", exact: true}).click();

            await page.locator("[data-claim-id='claim-2']").click();
            assert.equal(await page.locator("#source-picker option").count(), 2);
            assert.equal(await page.locator("#source-meds-1").isVisible(), true);
            assert.equal(await page.evaluate(() => document.activeElement.parentElement.id), "source-meds-1");
            await page.getByRole("button", {name: "Next source", exact: true}).click();
            assert.equal(await page.locator("#source-note-1").isVisible(), true);
            assert.equal(await page.locator("#source-meds-1").isVisible(), false);
            assert.equal(await page.getByRole("button", {name: "Next source", exact: true}).isDisabled(), true);
            await page.locator("#source-picker").selectOption("0");
            assert.equal(await page.locator("#source-meds-1").isVisible(), true);
            await page.getByRole("button", {name: "Next statement", exact: true}).click();
            assert.equal(await page.locator("#source-allergy-1").isVisible(), true);
            assert.equal(await page.getByRole("button", {name: "Next statement", exact: true}).isDisabled(), true);
            await page.getByRole("button", {name: "Previous statement", exact: true}).click();

            if (width > 1100) {
                const before = await page.locator("#evidence").boundingBox();
                await page.getByRole("separator").focus();
                await page.keyboard.press("ArrowLeft");
                const after = await page.locator("#evidence").boundingBox();
                assert.ok(after.width > before.width);
                const splitter = await page.getByRole("separator").boundingBox();
                await page.mouse.move(splitter.x + splitter.width / 2, splitter.y + 100);
                await page.mouse.down();
                await page.mouse.move(splitter.x - 60, splitter.y + 100);
                await page.mouse.up();
                assert.ok((await page.locator("#evidence").boundingBox()).width > after.width);
            }
            await page.screenshot({path: path.join(screenshots, "evidence-" + width + ".png"), fullPage: true});
            await page.getByRole("button", {name: "Close source evidence", exact: true}).click();
            assert.equal(await page.locator("#evidence").isVisible(), false);
            await page.getByRole("button", {name: "Show source evidence", exact: true}).click();
            assert.equal(await page.locator("#source-meds-1").isVisible(), true);
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
            assert.equal(await page.evaluate(() => [...document.querySelectorAll(".icon-button i")]
                .every(icon => getComputedStyle(icon, "::before").content !== "none")), true);

            if (previewStates) {
                for (const mode of ["error", "empty", "xss"]) {
                    const stateUrl = new URL(url);
                    stateUrl.searchParams.set("mode", mode);
                    await page.goto(stateUrl.href);
                    if (mode === "error") {
                        assert.equal(await page.getByText("Summary withheld:", {exact: false}).isVisible(), true);
                        assert.equal(await page.locator(".claim").count(), 0);
                        await page.getByRole("tab", {name: "Validation", exact: true}).click();
                        assert.equal(await page.locator(".finding.error").count(), 1);
                    } else if (mode === "empty") {
                        assert.equal(await page.getByText("No summary claims available.", {exact: true}).isVisible(), true);
                    } else {
                        assert.equal(await page.locator("img").count(), 0);
                        assert.equal(await page.evaluate(() => window.injected), undefined);
                        await page.locator(".claim").first().click();
                        assert.ok((await page.locator("#source-note-1").innerText()).includes("<img src=x"));
                    }
                }
            }
            await context.close();
        }
        const context = await browser.newContext({javaScriptEnabled: false, storageState});
        const page = await context.newPage();
        await page.goto(url);
        for (const id of ["summary", "ledger", "coverage", "validation"]) {
            assert.equal(await page.locator("#" + id).isVisible(), true);
        }
        assert.equal(await page.locator(".source:visible").count(), 4);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await context.close();
        assert.deepEqual(errors, []);
        console.log("PASS: five viewport sizes; tabs, source selection, statement navigation, resize, close/reopen, no-JS fallback" +
            (previewStates ? ", error/empty states and HTML escaping" : ""));
        console.log("Screenshots: " + screenshots);
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
