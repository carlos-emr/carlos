/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    const source = document.getElementById('chart-update-source');
    const viewer = document.getElementById('full-source');
    if (!source || !viewer) return;
    const original = source.textContent;
    const cards = Array.from(document.querySelectorAll('article.proposal'));
    // Advisory text comparison only. The server reloads the authorized chart and enforces saves.
    const matcher = window.CarlosChartUpdateMatching;
    const entries = Array.from(document.querySelectorAll('.chart-entry')).map(element => ({
        element, facts: matcher.prepare(element.querySelector('.chart-entry-text').textContent),
        label: element.querySelector('summary').textContent.trim(),
        kind: element.dataset.kind, destinations: (element.dataset.destinations || '').split(' '),
    }));
    const matches = new Map();
    // Taken before the review script moves each card's notices beside it, into the chart check.
    const notices = new Map(cards.map(card => [card, {
        match: card.querySelector('.chart-match-notice'), related: card.querySelector('.related-proposal-notice'),
    }]));
    let active;
    const evidence = card => card.querySelector('.proposal-evidence blockquote')?.textContent || '';
    // Shared wording is an advisory signal, not proof that diagnoses are equivalent.
    // Never remove a proposal or change its approval state based on this comparison.
    const terms = text => new Set((text.toLowerCase().match(/[\p{L}\p{N}]+/gu) || [])
        .filter(word => word.length >= 4 && !['history', 'medical', 'impression', 'assessment',
            'patient', 'follow', 'review', 'weeks', 'days', 'plan', 'with', 'from', 'that', 'this'].includes(word)));
    const related = (left, right) => {
        const shared = [...left].filter(word => right.has(word)).length;
        return shared >= 3 && shared / Math.min(left.size, right.size) >= 0.6;
    };
    const compareProposals = () => {
        const states = cards.map(card => {
            const draft = card.querySelector('[name="entryText"]');
            const text = draft?.value ?? evidence(card);
            return { card, draft, text, words: terms(text) };
        });
        states.forEach(({ card, draft, words }) => {
            const notice = notices.get(card).related;
            if (!notice) return;
            const peers = states.filter(other => other.card !== card && related(words, other.words));
            notice.hidden = !draft || peers.length === 0;
            const container = notice.querySelector('.related-proposal-quotes');
            container.replaceChildren();
            peers.forEach(peer => {
                const details = document.createElement('details');
                const title = document.createElement('summary');
                title.textContent = peer.card.querySelector('.proposal-number').textContent.trim() + ' '
                    + peer.card.querySelector('h3').textContent.trim();
                const outcome = peer.card.querySelector('.alert-success')?.textContent.trim();
                if (outcome) title.textContent += ' — ' + outcome;
                const quote = document.createElement('blockquote');
                quote.textContent = peer.text;
                details.append(title, quote);
                container.append(details);
            });
        });
    };
    const showMatches = card => {
        const matching = matches.get(card) || [];
        entries.forEach(entry => {
            const found = matching.some(match => match.entry === entry);
            entry.element.classList.toggle('chart-entry-match', found);
            if (found) entry.element.open = true;
        });
    };
    const compare = card => {
        const input = card.querySelector('[name="entryText"]');
        const draft = matcher.prepare(input?.value ?? evidence(card));
        const destination = card.querySelector('[name="destination"]')?.value ?? card.dataset.destination;
        const nativeKinds = { Medications: 'medication', Allergies: 'allergy', Preventions: 'prevention' };
        const found = entries.filter(entry => card.dataset.kind === 'history'
                ? entry.kind === 'history' && entry.destinations.includes(destination)
                : entry.kind === (nativeKinds[destination] || card.dataset.kind)).map(entry => ({ entry, fact: matcher.find(draft, entry.facts) }))
            .filter(match => match.fact);
        matches.set(card, found);
        const notice = notices.get(card).match;
        notice.hidden = !input || !found.length;
        const links = notice.querySelector('.chart-match-links');
        links.replaceChildren();
        // Keep a common short phrase from producing an unbounded warning panel.
        found.slice(0, 10).forEach(({ entry, fact }) => {
            const item = document.createElement('li');
            const link = document.createElement('a');
            link.href = '#' + entry.element.id;
            link.textContent = entry.label;
            link.addEventListener('click', event => {
                event.preventDefault();
                show(card);
                entry.element.open = true;
                entry.element.scrollIntoView({ block: 'nearest' });
                entry.element.querySelector('summary').focus({ preventScroll: true });
            });
            const passage = document.createElement('blockquote');
            passage.className = 'source-text chart-match-passage';
            passage.textContent = fact.passage;
            item.append(link, passage);
            links.append(item);
        });
        if (active === card) showMatches(card);
    };
    function show(card, jump = false) {
        if (active !== card || jump) {
            active = card;
            const quote = evidence(card);
            const fragment = document.createDocumentFragment();
            let offset = 0;
            let count = 0;
            let start;
            // Render exact source text as text nodes, never as HTML. Repeated passages can
            // have several matches; cap marks to keep very repetitive documents responsive.
            while (quote && count < 100 && (start = original.indexOf(quote, offset)) !== -1) {
                fragment.append(document.createTextNode(original.slice(offset, start)));
                const mark = document.createElement('mark');
                mark.className = 'source-highlight';
                mark.textContent = quote;
                fragment.append(mark);
                offset = start + quote.length;
                count++;
            }
            fragment.append(document.createTextNode(original.slice(offset)));
            source.replaceChildren(fragment);
            document.querySelector('.source-highlight-help').hidden = count === 0;
            const first = source.querySelector('mark');
            if (first) {
                const top = first.getBoundingClientRect().top - viewer.getBoundingClientRect().top + viewer.scrollTop;
                viewer.scrollTop = Math.max(0, top - viewer.clientHeight / 3);
            }
            showMatches(card);
        }
        if (jump) {
            viewer.scrollIntoView({ block: 'nearest' });
            viewer.focus({ preventScroll: true });
        }
    }
    cards.forEach(card => {
        compare(card);
        // A click on Skip or Previous reaches its card after that card was hidden for the next one.
        card.addEventListener('focusin', () => { if (!card.hidden) show(card); });
        card.addEventListener('click', () => { if (!card.hidden) show(card); });
        card.querySelector('[name="entryText"]')?.addEventListener('input', () => {
            compare(card);
            compareProposals();
        });
        card.querySelector('[name="destination"]')?.addEventListener('change', () => compare(card));
        card.querySelector('[data-show-source]').addEventListener('click', event => {
            event.preventDefault();
            show(card, true);
        });
    });
    // The summary: the source without marks, and no chart entry marked as matching.
    function clear() {
        active = undefined;
        source.replaceChildren(document.createTextNode(original));
        document.querySelector('.source-highlight-help').hidden = true;
        entries.forEach(entry => entry.element.classList.remove('chart-entry-match'));
    }
    compareProposals();
    window.CarlosChartUpdateEvidence = { show, clear };
    if (cards.length) show(cards.find(card => card.querySelector('.proposal-form')) || cards[0]);
})();
