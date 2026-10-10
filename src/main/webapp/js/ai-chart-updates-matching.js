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
    // 'patient' is not ignored: only a leading 'Patient has/is/was' is dropped (see below).
    const ignore = new Set(('a an the of in with for to at by and has have is ' +
        'medical history pmh diagnosis diagnosed known documented noted arrange schedule ' +
        'please clinician entry').split(' '));
    // Suppress uncertain scopes instead of guessing their meaning or dropping qualifiers.
    const excluded = /\b(?:no|not|none|denies|denied|without|negative|absent|ruled|rule|possible|possibly|probable|suspected|suspect|query|risk|if|unless|pending|resolved|recovered|completed|cancelled|canceled|family|familial|fhx|mother|father|maternal|paternal|sister|brother|daughter|son|wife|husband|parents?|siblings?)\b|\?|\b(?:r\/o|f\/h)\b/;
    const statusContinuation = /^(?:ruled out|resolved|recovered|completed|cancelled|canceled|not confirmed|suspected|unconfirmed|in remission)\b/i;
    const resetHeading = /^(?:past medical history|pmh|medical history|assessment|impression|diagnos(?:is|es)|plan|recommendations|follow[ -]?up|current problems)\s*:/i;
    // A heading line ('Family history: HTN.', 'FHx', 'Plan:') is never glued onto the line before
    // it, so the scope checks below, which only look at the start of a passage, still see it.
    const headingLine = /^(?:[^:\n]{1,64}:|(?:fam(?:ily)?\s+(?:medical\s+)?(?:history|hx)|fhx|f\/h)\b|(?:no history of|possible diagnoses)\s*$)/i;
    // A bare side abbreviation ('L.', 'Rt.') may belong to the next sentence ('L. Knee OA'),
    // so the passage after one gets no keys at all.
    const endsWithSide = /(?:^|[\s(,;])(?:[LlRr][Tt]?|[Bb]ilat|[Bb]\/[Ll])\.$/u;
    // Sorted terms forget word order, and the term pattern below drops most symbols. A missed
    // duplicate hint costs little; a false "already in the chart" hint can hide a real change.
    // So fuzzy matching only covers one short fact whose meaning cannot depend on either:
    // - no word or mark that joins several facts;
    // - no word for order, direction, change, comparison or preference (which drug replaced
    //   which, what went up, which came first);
    // - no side marker (left, R, Lt...): sorted terms cannot keep which side goes with which finding;
    // - no second party: 'patient' anywhere but a leading 'Patient has/is/was', or another person,
    //   since sorted terms cannot keep who did what to whom ('Partner hit patient');
    // - no symbol the term pattern would drop and that can carry meaning (arrows, ↑/↓, ½, ⁹, ×,
    //   +ve/-ve, '>' or '<' not in front of a number);
    // - at most three terms, counting a number and its time unit as one: two facts that each
    //   carry their own qualifier need four, so a short phrase has no qualifiers to swap.
    // Anything else can still match on the exact keys (case, spacing and a final full stop aside).
    // Known limit: a swapped compound noun ('Kidney donor' / 'Donor kidney') still matches.
    const orderSensitive = [
        /[,;&|]|\s\/\s|\b(?:and|or|but|because|due|secondary|caus(?:ed|ing)|related)\b/,
        /\b(?:after|before|following|prior|since|until|from|to|into|than|vs|versus|then|instead|place|lieu|for|by|via|per|vice|now|pour|par|over|under|above|below|après|apres|avant|puis|ensuite|depuis|plutôt|plutot)\b/,
        /\b(?:switch|swap|chang|replac|convert|substitut|increas|decreas|rais|elevat|reduc|lower|high|titrat|wean|taper|worse|improv|deteriorat|prefer|succeed|supersed|stop|start|restart|resum|hold|discontinu|ceas|remov|withh[eo]ld|augment|diminu|arr[eê]t|remplac|aggrav|am[eé]lior)\w*/,
        /\b(?:up|down|more|less|fewer|greater|better|low|off|held|add|added|adding|mieux|pire|hausse|baisse)\b/,
        /\b(?:left|right|bilateral|bilat|l|r|lt|rt|lhs|rhs|unilateral|ipsilateral|contralateral|gauche|droite?)\b/,
        /\b(?:patient|pt|partner|spouse|husband|wife|boyfriend|girlfriend|friend|carer|caregiver|colleague|stranger|neighbou?r|nurse|staff|doctor|physician|child|children|son|daughter|mother|father|brother|sister|parents?|aunt|uncle|cousin|grand\w*)\b/,
        /[^\p{L}0-9\s!-~≤≥±‘’“”]|[#~^]|\+(?!\d)|(?<![\p{L}\d])-(?!\d)|-(?![\p{L}\d])|[<>≤≥](?!=?\s*[+-]?\d)/u,
    ];
    const timeUnit = /^(?:day|week|month|year)$/;
    const isNumber = word => /^[+-]?\d/.test(word);
    // Final-full-stop-insensitive, so 'Left knee OA.' in a note still matches 'Left knee OA'.
    const exactKey = text => 'exact:' + text.toLowerCase().replace(/\s+/g, ' ').trim().replace(/(?<!\.)\.$/, '');
    function prepare(text) {
        if (!text?.trim() || text.length > 100000) return [];
        let blockedHeading = false;
        // Exact whole-entry comparison retains the existing whitespace-insensitive warning,
        // including identical qualified text. Fuzzy rules never bypass their scope guards.
        const facts = [{ passage: text.trim(), key: exactKey(text) }];
        // Join wrapped lines so a trailing 'ruled out' or leading 'No' keeps its scope.
        // Separate explicit bullets/headings and completed sentences, not arbitrary line wraps.
        const lines = [];
        for (const line of text.split(/\r\n?|\n/)) {
            const trimmed = line.trim();
            const previous = lines[lines.length - 1];
            if (previous && statusContinuation.test(trimmed)) {
                lines[lines.length - 1] += '\n' + trimmed;
            } else if (previous && trimmed && !/[.!?:]$/.test(previous) && !/^(?:no history of|possible diagnoses)\s*$/i.test(previous) &&
                    !/^(?:[-*•]|\d+[.)])\s/.test(trimmed) && !headingLine.test(trimmed)) {
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
        let afterSide = false;
        for (const raw of sentences) {
            const passage = raw.trim();
            if (!passage) continue;
            const followsSide = afterSide;
            afterSide = endsWithSide.test(passage);
            const lower = passage.toLowerCase();
            if (resetHeading.test(passage)) blockedHeading = false;
            if ((lower.endsWith(':') && excluded.test(lower)) ||
                    /^(?:fam(?:ily)?\s+(?:medical\s+)?(?:history|hx)|fhx|f\/h)\b/.test(lower) ||
                    /^(?:no history of|possible diagnoses)\s*:?$/.test(lower)) blockedHeading = true;
            // Unknown headings may qualify the entire following list (e.g. dates or status).
            if ((lower.endsWith(':') || /^[^:\n]{1,64}:/.test(passage)) && !resetHeading.test(passage)) blockedHeading = true;
            if (blockedHeading || followsSide) continue;
            facts.push({ passage, key: exactKey(passage) });
            if (excluded.test(lower)) continue;
            let canonical = lower.normalize('NFC').replace(/^\s*(?:[-*•]|\d+[.)])\s*/, '')
                .replace(resetHeading, '').replace(/^\s*(?:the\s+)?(?:patient|pt)\s+(?=(?:has|is|was)\b)/, '');
            for (const [pattern, replacement] of aliases) canonical = canonical.replace(pattern, replacement);
            // Checked after aliases, so 'follow-up' (now 'review') is not read as 'up'.
            if (orderSensitive.some(pattern => pattern.test(canonical))) continue;
            canonical = canonical.replace(/\b(?:weeks|days|months|years)\b/g, unit => unit.slice(0, -1));
            // Retain punctuation inside numbers: dates, ranges and decimals must not collapse.
            const words = [];
            for (const token of canonical.match(/[+-]?\d+(?:[./:-]\d+)*|[<>≤≥=±%]|[\p{L}]+/gu) || []) {
                const word = numbers[token] || token;
                if (timeUnit.test(word) && isNumber(words[words.length - 1] || '')) words[words.length - 1] += ' ' + word;
                else words.push(word);
            }
            const terms = [...new Set(words.filter(word => !ignore.has(word)))].sort();
            if (terms.length && terms.length <= 3) facts.push({ passage, key: JSON.stringify([terms, words.filter(isNumber)]) });
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
