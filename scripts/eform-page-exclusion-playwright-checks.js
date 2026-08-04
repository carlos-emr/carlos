/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * Browser check for the non-page exclusion pass in COMPUTE_PAGE_GEOMETRY_JS.
 *
 * WHY THIS EXISTS AS A BROWSER CHECK: every excludedCount assertion in
 * EFormBrowserPdfServiceUnitTest feeds readPageGeometry a hand-built Map, so the DOM traversal
 * itself was never executed by any test. A scan that could not fire on 98.7% of the real corpus
 * passed the whole Java suite. Only running the real script against a real DOM catches that.
 *
 * The script under test is not duplicated here — it is EXTRACTED from the Java source, so this
 * check cannot drift from what ships. If the extraction stops matching, the check fails loudly
 * rather than silently testing a stale copy.
 *
 * Run: npm run test:eform-page-exclusion-playwright   (no Tomcat, no database)
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const SERVICE_JAVA = path.join(__dirname, '..', 'src', 'main', 'java', 'io', 'github',
    'carlos_emr', 'carlos', 'eform', 'util', 'EFormBrowserPdfService.java');

/**
 * Pulls a `static final String NAME = "..." + "...";` constant out of the Java source and
 * unescapes it back into runnable JavaScript.
 */
function extractJavaStringConstant(source, name) {
    const start = source.indexOf(`static final String ${name} =`);
    if (start < 0) {
        throw new Error(`Could not find constant ${name} — extraction is stale, fix this check.`);
    }
    // The constant ends at the first semicolon that is outside both string literals and comments.
    // Comments must be skipped explicitly: the JS is interleaved with // explainers that themselves
    // contain quotes and semicolons, and treating those as code opens a bogus string literal.
    let i = source.indexOf('=', start) + 1;
    let inString = false;
    let escaped = false;
    const literals = [];
    let current = null;
    for (; i < source.length; i++) {
        const c = source[i];
        if (inString) {
            if (escaped) { current += c; escaped = false; continue; }
            if (c === '\\') { current += c; escaped = true; continue; }
            if (c === '"') { literals.push(current); current = null; inString = false; continue; }
            current += c;
            continue;
        }
        if (c === '/' && source[i + 1] === '/') {
            i = source.indexOf('\n', i);
            if (i < 0) { break; }
            continue;
        }
        if (c === '/' && source[i + 1] === '*') {
            i = source.indexOf('*/', i) + 1;
            if (i < 1) { break; }
            continue;
        }
        if (c === '"') { inString = true; current = ''; continue; }
        if (c === ';') { break; }
    }
    if (literals.length === 0) {
        throw new Error(`Constant ${name} yielded no string literals — extraction is stale.`);
    }
    return literals.join('')
        .replace(/\\n/g, '\n')
        .replace(/\\t/g, '\t')
        .replace(/\\"/g, '"')
        .replace(/\\\\/g, '\\');
}

/** body > form > [page1, interstitial, page2] — the shape 220 of 223 stored forms use. */
const FORM_WRAPPED = `
<body>
  <form method="post" name="FormName" id="FormName">
    <div id="page1" style="height:200px">page one content</div>
    <div id="carlos-interstitial" style="height:60px">RETURN THIS FORM TO THE CLINIC</div>
    <div id="page2" style="height:200px">page two content</div>
  </form>
</body>`;

/** body > [page1, interstitial, page2] — the shape the original scan handled. */
const DIRECT_CHILD = `
<body>
  <div id="page1" style="height:200px">page one content</div>
  <div id="carlos-interstitial" style="height:60px">RETURN THIS FORM TO THE CLINIC</div>
  <div id="page2" style="height:200px">page two content</div>
</body>`;

/** No interstitial at all — must stay at zero, or the pass would flag every ordinary form. */
const CLEAN_FORM = `
<body>
  <form id="FormName">
    <div id="page1" style="height:200px">page one content</div>
    <div id="page2" style="height:200px">page two content</div>
  </form>
</body>`;

const failures = [];
function check(label, actual, expected) {
    if (actual === expected) {
        console.log(`  PASS  ${label} (excludedCount=${actual})`);
    } else {
        failures.push(`${label}: expected excludedCount=${expected}, got ${actual}`);
        console.log(`  FAIL  ${label}: expected ${expected}, got ${actual}`);
    }
}

(async () => {
    const source = fs.readFileSync(SERVICE_JAVA, 'utf8');
    const geometryJs = extractJavaStringConstant(source, 'COMPUTE_PAGE_GEOMETRY_JS');

    // Guard the extraction itself: if these markers vanish the constant moved and the check is
    // testing something else. Better to fail here than to report a green run on the wrong script.
    for (const marker of ['pageNodes', 'carlos-render-nonpage', 'excludedCount']) {
        if (!geometryJs.includes(marker)) {
            throw new Error(`Extracted script is missing '${marker}' — extraction is stale.`);
        }
    }

    const browser = await chromium.launch();
    const page = await browser.newPage();

    // Selenium's executeScript wraps the body in a function, so the script ends in a bare `return`.
    // page.evaluate takes an expression, so reproduce the same wrapping rather than editing the JS.
    const asExpression = `(() => { ${geometryJs} })()`;

    const run = async (html) => {
        await page.setContent(html);
        return page.evaluate(asExpression);
    };

    console.log('eForm non-page exclusion checks:');

    // The regression case. Before the descend fix this returned 0: body.children is [<form>],
    // the form contains both page nodes, and the loop skipped its only iteration.
    const wrapped = await run(FORM_WRAPPED);
    check('interstitial inside <form> is counted', wrapped.excludedCount, 1);

    const direct = await run(DIRECT_CHILD);
    check('interstitial as direct body child still counted', direct.excludedCount, 1);

    const clean = await run(CLEAN_FORM);
    check('form with no interstitial stays at zero', clean.excludedCount, 0);

    // The page divs themselves must never be hidden — hiding them would blank the whole document.
    await page.setContent(FORM_WRAPPED);
    await page.evaluate(asExpression);
    const hidden = await page.evaluate(() => Array.from(
        document.querySelectorAll('.carlos-render-nonpage')).map((el) => el.id));
    if (hidden.length === 1 && hidden[0] === 'carlos-interstitial') {
        console.log(`  PASS  only the interstitial is marked non-page (${hidden.join(',')})`);
    } else {
        failures.push(`expected only carlos-interstitial marked, got [${hidden.join(',')}]`);
        console.log(`  FAIL  expected only carlos-interstitial marked, got [${hidden.join(',')}]`);
    }

    await browser.close();

    if (failures.length > 0) {
        console.error(`\n${failures.length} check(s) failed:`);
        failures.forEach((f) => console.error(`  - ${f}`));
        process.exit(1);
    }
    console.log('\nAll eForm non-page exclusion checks passed.');
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
