/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
(() => {
    'use strict';
    const source = document.getElementById('chart-update-source');
    const viewer = document.getElementById('full-source');
    if (!source || !viewer) return;
    const original = source.textContent;
    const cards = Array.from(document.querySelectorAll('article.proposal'));
    // Advisory text comparison only. The server reloads the authorized chart and enforces saves.
    const normalize = text => text.toLowerCase().replace(/[ \t\n\v\f\r]+/g, ' ').trim();
    const entries = Array.from(document.querySelectorAll('.chart-entry')).map(element => ({
        element, text: normalize(element.querySelector('.chart-entry-text').textContent),
        label: element.querySelector('summary').textContent.trim(),
    }));
    const matches = new Map();
    let active;
    const evidence = card => card.querySelector('.proposal-evidence blockquote')?.textContent || '';
    const showMatches = card => {
        const matching = matches.get(card) || [];
        entries.forEach(entry => {
            const found = matching.includes(entry);
            entry.element.classList.toggle('chart-entry-match', found);
            if (found) entry.element.open = true;
        });
    };
    const compare = card => {
        const input = card.querySelector('[name="entryText"]');
        const quote = normalize(evidence(card));
        const draft = normalize(input?.value || '');
        const found = entries.filter(entry => (draft && entry.text.includes(draft)) || (quote && entry.text.includes(quote)));
        matches.set(card, found);
        const notice = card.querySelector('.chart-match-notice');
        notice.hidden = !input || !found.length;
        const links = notice.querySelector('.chart-match-links');
        links.replaceChildren();
        // Keep a common short phrase from producing an unbounded warning panel.
        found.slice(0, 10).forEach(entry => {
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
            item.append(link);
            links.append(item);
        });
        if (active === card) showMatches(card);
    };
    function show(card, jump = false) {
        if (active !== card) {
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
        card.addEventListener('focusin', () => show(card));
        card.addEventListener('click', () => show(card));
        card.querySelector('[name="entryText"]')?.addEventListener('input', () => compare(card));
        card.querySelector('[data-show-source]').addEventListener('click', event => {
            event.preventDefault();
            show(card, true);
        });
    });
    window.CarlosChartUpdateEvidence = { show };
    if (cards.length) show(cards.find(card => card.querySelector('.proposal-form')) || cards[0]);
})();
