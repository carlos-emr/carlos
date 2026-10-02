/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(function (root) {
    'use strict';
    // Advisory English text matching, not a clinical terminology service. No network access.
    // Deliberately omit ambiguous abbreviations (e.g. MS) and symptom-to-diagnosis mappings.
    const aliases = [
        [/\b(?:htn|hypertension)\b/g, 'hypertension'],
        [/\b(?:oa|osteoarthritis)\b/g, 'osteoarthritis'],
        [/\b(?:copd|chronic obstructive pulmonary disease)\b/g, 'copd'],
        [/\b(?:t2dm|type (?:2|two) diabetes(?: mellitus)?)\b/g, 't2dm'],
        [/\b(?:follow[ -]?up|review|recheck)\b/g, 'review'],
        [/\b(?:physio|physiotherapy)\b/g, 'physiotherapy'],
        [/\b(?:gp|general practitioner)\b/g, 'gp'],
    ];
    const numbers = Object.assign(Object.create(null), { one: '1', two: '2', three: '3', four: '4', five: '5', six: '6',
        seven: '7', eight: '8', nine: '9', ten: '10', eleven: '11', twelve: '12' });
    const ignore = new Set(('a an the of in with for to at by and patient has have is ' +
        'medical history pmh diagnosis diagnosed known documented noted arrange schedule ' +
        'please clinician entry').split(' '));
    // Suppress uncertain scopes instead of guessing their meaning or dropping qualifiers.
    const excluded = /\b(?:no|not|none|denies|denied|without|negative|absent|ruled|rule|possible|possibly|probable|suspected|suspect|query|risk|if|unless|pending|resolved|recovered|completed|cancelled|canceled|family|familial|fhx|mother|father|maternal|paternal|sister|brother|daughter|son|wife|husband|parents?|siblings?)\b|\?|\b(?:r\/o|f\/h)\b/;
    const statusContinuation = /^(?:ruled out|resolved|recovered|completed|cancelled|canceled|not confirmed|suspected|unconfirmed|in remission)\b/i;
    const resetHeading = /^(?:past medical history|pmh|medical history|assessment|impression|diagnos(?:is|es)|plan|recommendations|follow[ -]?up|current problems)\s*:/i;
    function prepare(text) {
        if (!text?.trim() || text.length > 100000) return [];
        let blockedHeading = false;
        // Exact whole-entry comparison retains the existing whitespace-insensitive warning,
        // including identical qualified text. Fuzzy rules never bypass their scope guards.
        const facts = [{ passage: text.trim(), key: 'exact:' + text.toLowerCase().replace(/\s+/g, ' ').trim() }];
        // Join wrapped lines so a trailing 'ruled out' or leading 'No' keeps its scope.
        // Separate explicit bullets/headings and completed sentences, not arbitrary line wraps.
        const lines = [];
        for (const line of text.split(/\r?\n/)) {
            const trimmed = line.trim();
            const previous = lines[lines.length - 1];
            if (previous && statusContinuation.test(trimmed)) {
                lines[lines.length - 1] += '\n' + trimmed;
            } else if (previous && trimmed && !/[.!?:]$/.test(previous) &&
                    !/^(?:[-*•]|\d+[.)])\s/.test(trimmed) && !resetHeading.test(trimmed)) {
                lines[lines.length - 1] += '\n' + trimmed;
            } else lines.push(trimmed);
        }
        // Keep semicolon/conjunction scopes together. Never split decimal values or dates.
        const sentences = [];
        for (const line of lines) {
            const qualifiedHeading = /^[^:\n]{1,64}:/.test(line) && !resetHeading.test(line);
            const parts = qualifiedHeading || /\b(?:if|unless|pending)\b/i.test(line) ? [line] : line.split(/(?<=[.!?])\s+(?=[A-Z])/);
            const joined = [];
            for (const part of parts) {
                if (joined.length && statusContinuation.test(part)) joined[joined.length - 1] += ' ' + part;
                else joined.push(part);
            }
            sentences.push(...joined);
        }
        for (const raw of sentences) {
            const passage = raw.trim();
            if (!passage) continue;
            const lower = passage.toLowerCase();
            if (resetHeading.test(passage)) blockedHeading = false;
            if ((lower.endsWith(':') && excluded.test(lower)) ||
                    /^(?:family history|family hx|fhx|f\/h)\b/.test(lower) ||
                    /^(?:no history of|possible diagnoses)\s*:?$/.test(lower)) blockedHeading = true;
            // Unknown headings may qualify the entire following list (e.g. dates or status).
            if ((lower.endsWith(':') || /^[^:\n]{1,64}:/.test(passage)) && !resetHeading.test(passage)) blockedHeading = true;
            if (blockedHeading) continue;
            facts.push({ passage, key: 'exact:' + lower.replace(/\s+/g, ' ').trim() });
            if (excluded.test(lower)) continue;
            // Do not detach a qualifier from one of several facts, or reverse causality.
            if (/[,;]|\s\/\s|\b(?:and|or|but|because|due|secondary|caus(?:ed|ing)|related)\b/.test(lower) ||
                    (lower.match(/\b(?:left|right|bilateral)\b/g) || []).length > 1) continue;
            let canonical = lower.replace(/^\s*(?:[-*•]|\d+[.)])\s*/, '')
                .replace(resetHeading, '');
            for (const [pattern, replacement] of aliases) canonical = canonical.replace(pattern, replacement);
            canonical = canonical.replace(/\b(?:weeks|days|months|years)\b/g, unit => unit.slice(0, -1));
            // Retain punctuation inside numbers: dates, ranges and decimals must not collapse.
            const words = canonical.match(/[+-]?\d+(?:[./:-]\d+)*|[<>≤≥=±%]|[\p{L}]+/gu) || [];
            const terms = [...new Set(words.map(word => numbers[word] || word).filter(word => !ignore.has(word)))].sort();
            if (terms.length) facts.push({ passage, key: JSON.stringify([terms, words.map(word => numbers[word] || word).filter(word => /^[+-]?\d/.test(word))]) });
        }
        return facts;
    }
    function find(left, right) {
        const keys = new Set(left.map(fact => fact.key));
        return right.find(fact => keys.has(fact.key)) || null;
    }
    const api = { prepare, find };
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.CarlosChartUpdateMatching = api;
})(typeof globalThis === 'object' ? globalThis : this);
