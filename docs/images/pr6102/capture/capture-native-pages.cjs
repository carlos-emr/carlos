/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// Passive screenshot observer. No application responses, DOM, assertions or fixture data are changed.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {createRequire} = require('node:module');
const root = process.env.CARLOS_TEST_ROOT || '/root/carlos';
const output = process.env.NATIVE_SCREENSHOT_DIR;
if (!output || !path.isAbsolute(output)) throw new Error('NATIVE_SCREENSHOT_DIR must be an absolute path');
fs.mkdirSync(output, {recursive: true});
const pw = createRequire(path.join(root, 'package.json'))('playwright');
const launch = pw.chromium.launch.bind(pw.chromium);
let sequence = 0;
const records = [];
const save = () => fs.writeFileSync(path.join(output, 'captures.json'), JSON.stringify(records, null, 2) + '\n');
pw.chromium.launch = async (...args) => {
    const browser = await launch(...args);
    const newContext = browser.newContext.bind(browser);
    browser.newContext = async (...contextArgs) => {
        const context = await newContext(...contextArgs);
        context.on('page', page => {
            const close = page.close.bind(page);
            let closing = false;
            page.close = async (...closeArgs) => {
                if (!closing && !page.isClosed()) {
                    closing = true;
                    const route = new URL(page.url()).pathname;
                    if (/lab|GraphMeasurements|Cumulative|CDM|Measurements|casemgmt\/forward/i.test(route)) {
                        const name = String(++sequence).padStart(3, '0') + '-' + path.basename(route).replace(/[^a-z0-9_.-]/gi, '_') + '.png';
                        const target = path.join(output, name);
                        try {
                            await page.screenshot({path: target, fullPage: true, timeout: 10000});
                            records.push({image: name, route, browser: browser.version(),
                                sha256: crypto.createHash('sha256').update(fs.readFileSync(target)).digest('hex')});
                            save();
                        } catch (error) {
                            const failures = [error];
                            records.push({image: name, route, failure: error.name});
                            try { save(); } catch (saveError) { failures.push(saveError); }
                            try { await close(...closeArgs); } catch (closeError) { failures.push(closeError); }
                            throw failures.length === 1 ? error : new AggregateError(failures, 'Capture failed; evidence/cleanup also failed');
                        }
                    }
                }
                return close(...closeArgs);
            };
        });
        return context;
    };
    return browser;
};
