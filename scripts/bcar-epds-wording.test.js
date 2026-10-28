/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// BC-AR 2007 and BC-AR 2012 page 2 show the Edinburgh Postnatal Depression Scale (EPDS) from the
// form.bcar.epds.* bundle keys (issue #5050; the 2012 page printed its own English copy, damaged
// in places, until then). The EPDS is a validated questionnaire, so a locale shows either a
// published, validated translation word for word or the original English, never an ad-hoc
// translation. These checks pin:
//   - each page's scoring: ten items of four answers, items 1, 2 and 4 scored 0..3 from the first
//     answer and items 3 and 5-10 scored 3..0, so a reworded bundle cannot reorder the scale, and
//     both pages showing the same keys;
//   - every EPDS key in every shipped bundle;
//   - English: the wording of Cox, Holden & Sagovsky (1987), item by item as the pages render it;
//   - French: the published version validated by Guedeney & Fermanian (1998), item by item and
//     answer by answer as the page renders it;
//   - Spanish, Portuguese (Brazil) and Polish: still the English wording, because no verified,
//     key-compatible validated text has been adopted for them (see the comments in each bundle).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const PAGE = path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/form/formbcar2007pg2.jsp');
const PAGES = [PAGE, path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/form/formbcar2012pg2.jsp')];
const LOCALES = ['en', 'fr', 'es', 'pt_BR', 'pl'];
const ENGLISH_ONLY_LOCALES = ['es', 'pt_BR', 'pl'];

const { ENGLISH, FRENCH, FORWARD_SCORED, FRENCH_FOLLOW_UP } = require('./lib/epds-wording');

/** Reads a .properties file the way java.util.Properties does for the lines these keys use. */
function readBundle(locale) {
    const text = fs.readFileSync(path.join(ROOT, `src/main/resources/oscarResources_${locale}.properties`), 'utf8');
    const entries = new Map();
    // How often each key is defined: java.util.Properties keeps the last one, so a later duplicate wins silently.
    entries.definitions = new Map();
    const lines = text.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        let line = lines[i].replace(/^[ \t\f]+/, '');
        if (line === '' || line.startsWith('#') || line.startsWith('!')) continue;
        // An odd number of trailing backslashes continues the logical line.
        while (/(^|[^\\])(\\\\)*\\$/.test(line) && i + 1 < lines.length) {
            line = line.slice(0, -1) + lines[++i].replace(/^[ \t\f]+/, '');
        }
        const match = line.match(/^((?:\\.|[^=:\s\\])+)[ \t\f]*[=:]?[ \t\f]*(.*)$/);
        if (!match) continue;
        const key = unescape(match[1]);
        entries.set(key, unescape(match[2]));
        entries.definitions.set(key, (entries.definitions.get(key) || 0) + 1);
    }
    return entries;
}

function unescape(raw) {
    return raw.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, esc) => {
        if (esc[0] === 'u' && esc.length === 5) return String.fromCharCode(parseInt(esc.slice(1), 16));
        return { t: '\t', n: '\n', r: '\r', f: '\f' }[esc] ?? esc;
    });
}

/** The EPDS as the page lays it out: item number -> question key and answers in display order. */
function pageItems(page = PAGE) {
    const jsp = fs.readFileSync(page, 'utf8');
    const items = new Map();
    for (const [, key, item] of jsp.matchAll(/<fmt:message\s+key=['"]form\.bcar\.epds\.(q(\d+))['"]\s*\/>/g)) {
        items.set(Number(item), { question: key, answers: [] });
    }
    const answer = /name="ar2_EPDS(\d+)"\s+value="(\d)"[^>]*>\s*<fmt:message\s+key=['"]form\.bcar\.epds\.(\w+)['"]\s*\/>/g;
    for (const [, item, score, key] of jsp.matchAll(answer)) {
        assert.ok(items.has(Number(item)), `answer for item ${item} has no question on the page`);
        items.get(Number(item)).answers.push({ score: Number(score), key });
    }
    return items;
}

test('each page shows ten EPDS items of four answers, scored in the published direction', () => {
    for (const page of PAGES) {
        const items = pageItems(page);
        const name = path.basename(page);
        assert.deepEqual([...items.keys()], [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], `${name} lost, added or reordered an item`);
        for (const [item, { question, answers }] of items) {
            assert.equal(question, `q${item}`, `${name} item ${item} shows the wrong question`);
            const expected = FORWARD_SCORED.has(item) ? [0, 1, 2, 3] : [3, 2, 1, 0];
            assert.deepEqual(answers.map((a) => a.score), expected, `${name} item ${item} answers are scored in the wrong order`);
        }
    }
});

test('BC-AR 2012 shows the same EPDS keys as BC-AR 2007 and no English copy of its own', () => {
    const keysOf = (page) => JSON.stringify([...pageItems(page).values()]);
    assert.equal(keysOf(PAGES[1]), keysOf(PAGES[0]));
    const jsp = fs.readFileSync(PAGES[1], 'utf8');
    for (const text of ['I have been able to laugh', 'Things have been getting on top of me', 'card card-body bg-body-tertiary as']) {
        assert.ok(!jsp.includes(text), `formbcar2012pg2.jsp still prints "${text}" itself`);
    }
});

test('English shows the Cox, Holden & Sagovsky (1987) wording as the pages render it', () => {
    const english = readBundle('en');
    for (const [item, { question, answers }] of pageItems()) {
        const [expectedQuestion, expectedAnswers] = ENGLISH[item];
        assert.equal(english.get(`form.bcar.epds.${question}`), expectedQuestion, `English item ${item} question`);
        answers.forEach(({ key }, position) => assert.equal(english.get(`form.bcar.epds.${key}`), expectedAnswers[position],
            `English item ${item} answer ${position + 1} (${key})`));
    }
});

test('every EPDS key is defined in every locale bundle', () => {
    const english = readBundle('en');
    const epdsKeys = [...english.keys()].filter((key) => key.startsWith('form.bcar.epds.'));
    assert.ok(epdsKeys.length >= 30, 'the English bundle lost its EPDS keys');
    const pageKeys = [...pageItems().values()].flatMap(({ question, answers }) =>
        [question, ...answers.map((a) => a.key)]).map((key) => `form.bcar.epds.${key}`);
    for (const locale of LOCALES) {
        const bundle = readBundle(locale);
        for (const key of new Set([...epdsKeys, ...pageKeys])) {
            assert.ok(bundle.has(key), `oscarResources_${locale} lacks ${key}`);
            assert.notEqual(bundle.get(key).trim(), '', `oscarResources_${locale} has an empty ${key}`);
        }
    }
});

test('each EPDS key is defined once per bundle, in a bundle that escapes every non-ASCII character', () => {
    // A second definition later in a bundle replaces the reviewed wording without any of the checks above noticing,
    // and a raw UTF-8 character would be read as two Latin-1 characters by a reader that assumes ISO-8859-1.
    const english = readBundle('en');
    const epdsKeys = [...english.keys()].filter((key) => key.startsWith('form.bcar.epds.'));
    for (const locale of LOCALES) {
        const file = path.join(ROOT, `src/main/resources/oscarResources_${locale}.properties`);
        const raw = fs.readFileSync(file);
        const nonAscii = raw.findIndex((byte) => byte > 0x7f);
        assert.equal(nonAscii, -1, `oscarResources_${locale} has a raw non-ASCII byte at offset ${nonAscii}; use a \\u escape`);
        const { definitions } = readBundle(locale);
        const repeated = epdsKeys.filter((key) => (definitions.get(key) || 0) > 1);
        assert.deepEqual(repeated, [], `oscarResources_${locale} defines these EPDS keys more than once`);
    }
});

test('French shows the validated Guedeney & Fermanian (1998) wording as the page renders it', () => {
    const french = readBundle('fr');
    const english = readBundle('en');
    for (const [item, { question, answers }] of pageItems()) {
        const [expectedQuestion, expectedAnswers] = FRENCH[item];
        assert.equal(french.get(`form.bcar.epds.${question}`), expectedQuestion, `French item ${item} question`);
        answers.forEach(({ key }, position) => {
            const shown = french.get(`form.bcar.epds.${key}`);
            assert.notEqual(shown, english.get(`form.bcar.epds.${key}`), `French item ${item} answer ${position + 1} is still English`);
            assert.equal(shown, expectedAnswers[position], `French item ${item} answer ${position + 1} (${key})`);
        });
    }
});

test('French follow-up guidance is translated without changing its clinical thresholds', () => {
    const french = readBundle('fr');
    const english = readBundle('en');
    for (const [key, expected] of Object.entries(FRENCH_FOLLOW_UP)) {
        const name = `form.bcar.epds.${key}`;
        assert.equal(french.get(name), expected, name);
        assert.notEqual(french.get(name), english.get(name), `${name} is still English`);
        assert.deepEqual(french.get(name).match(/\d+/g), english.get(name).match(/\d+/g), `${name} changed thresholds`);
    }
});

test('locales without an adopted validated translation keep the original English wording', () => {
    const english = readBundle('en');
    const keys = [...pageItems().values()].flatMap(({ question, answers }) => [question, ...answers.map((a) => a.key)]);
    for (const locale of ENGLISH_ONLY_LOCALES) {
        const bundle = readBundle(locale);
        for (const key of new Set(keys)) {
            assert.equal(bundle.get(`form.bcar.epds.${key}`), english.get(`form.bcar.epds.${key}`),
                `oscarResources_${locale} form.bcar.epds.${key} must stay English until a validated translation is adopted`);
        }
    }
});
