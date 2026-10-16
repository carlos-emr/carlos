/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
/*
 * Drug-drug interaction markers on the Rx page's staged prescription cards (issue #4506).
 *
 * Each staged card (fieldset set_<randomId>, rendered by prescribe.jsp) carries five hidden
 * markers, major_/moderate_/minor_/unknown_/unchecked_<randomId>, each marked data-rx-interaction.
 * The server (GET rx/interactionWarnings?demographicNo=N) checks every staged drug against the
 * other staged drugs and the patient's current medications and answers
 *   {status: 'checked'|'off'|'unavailable', level, warnings: [{randomId, severity, partner,
 *    summary, evidence, comment}], unchecked: [randomId]}
 * where unchecked names the cards whose drug has no ATC code, so DrugRef could not check them;
 * those are marked "not checked" rather than left looking clean.
 * render() clears every marker and shows the warnings it is given. Anything other than a
 * well-formed 'checked' or 'off' answer is shown as "check unavailable" in #interactingDrugErrorMsg,
 * never as a clean result: a failed check must not look like "no interactions".
 *
 * The message element also carries data-check-status: "pending" from the moment a card change
 * is seen until its answer is drawn, then "checked", "off" or "unavailable".
 *
 * DrugRef's drug names and comments are data: they are written with textContent and title only.
 */
(function (root) {
    'use strict';

    var SEVERITIES = ['major', 'moderate', 'minor', 'unknown'];
    var LABELS = {
        major: 'Major interaction',
        moderate: 'Moderate interaction',
        minor: 'Minor interaction',
        unknown: 'Interaction of unknown significance'
    };
    var UNAVAILABLE = 'Drug interaction check unavailable. '
        + 'Review the patient’s medications for interactions before prescribing.';
    var NOT_CHECKED = 'Interactions not checked';
    var NOT_CHECKED_DETAIL = 'This drug has no ATC code, so DrugRef cannot check it for interactions. '
        + 'Review its interactions before prescribing.';
    var CARD = /^set_/;

    function isText(value) {
        return typeof value === 'string';
    }

    function isWarning(item) {
        return !!item && isText(item.randomId) && SEVERITIES.indexOf(item.severity) >= 0
            && isText(item.partner) && isText(item.summary) && isText(item.evidence) && isText(item.comment);
    }

    function isAnswer(result) {
        return !!result && (result.status === 'checked' || result.status === 'off')
            && Array.isArray(result.warnings) && result.warnings.every(isWarning)
            && (result.unchecked === undefined || (Array.isArray(result.unchecked) && result.unchecked.every(isText)));
    }

    function clear(marker) {
        marker.style.display = 'none';
        marker.textContent = '';
        marker.title = '';
    }

    function setStatus(document, status) {
        var message = document.getElementById('interactingDrugErrorMsg');
        if (message) message.setAttribute('data-check-status', status);
    }

    function unique(values) {
        return values.filter(function (value, index) { return values.indexOf(value) === index; });
    }

    /**
     * Shows one interaction-check answer on the page.
     *
     * @param document the Rx page's document
     * @param result   the parsed JSON answer, or null when the request failed
     */
    function render(document, result) {
        Array.prototype.forEach.call(document.querySelectorAll('[data-rx-interaction]'), clear);
        var answered = isAnswer(result);
        var message = document.getElementById('interactingDrugErrorMsg');
        if (message) {
            message.textContent = answered ? '' : UNAVAILABLE;
            message.style.display = answered ? 'none' : 'block';
        }
        setStatus(document, answered ? result.status : 'unavailable');
        if (!answered || result.status !== 'checked') return;

        (result.unchecked || []).forEach(function (randomId) {
            var marker = document.getElementById('unchecked_' + randomId);
            if (!marker) return;
            marker.textContent = NOT_CHECKED;
            marker.title = NOT_CHECKED_DETAIL;
            marker.style.display = 'inline';
        });

        var groups = {};
        var order = [];
        result.warnings.forEach(function (warning) {
            var id = warning.severity + '_' + warning.randomId;
            if (!groups[id]) {
                groups[id] = [];
                order.push(id);
            }
            groups[id].push(warning);
        });
        order.forEach(function (id) {
            var marker = document.getElementById(id);
            if (!marker) return; // The card was removed while the check was running.
            var warnings = groups[id];
            marker.textContent = LABELS[warnings[0].severity] + ': '
                + unique(warnings.map(function (warning) { return warning.partner; })).join(', ');
            marker.title = warnings.map(function (warning) {
                return warning.summary + ' (evidence: ' + warning.evidence + ')'
                    + (warning.comment ? '\n' + warning.comment : '');
            }).join('\n\n');
            marker.style.display = 'inline';
        });
    }

    /**
     * A checker for one patient's Rx page. Only the answer to the newest request is shown, and a
     * card change seen after a request was sent discards that request's answer: it describes a
     * set of cards the page no longer has.
     *
     * @param options {document, url, fetch, setTimeout, clearTimeout, delay}: url already names
     *                the page's patient; delay (ms) batches the bursts of card changes one action
     *                makes (a multi-drug re-prescribe inserts several cards).
     * @return {refresh, schedule}
     */
    function create(options) {
        var latest = 0;
        var timer = null;

        function refresh() {
            var request = ++latest;
            setStatus(options.document, 'pending');
            function show(result) {
                if (request === latest) render(options.document, result);
            }
            try {
                options.fetch(options.url, {
                    credentials: 'same-origin',
                    cache: 'no-store',
                    headers: {Accept: 'application/json'}
                }).then(function (response) {
                    // An expired session follows a redirect to the login page: not JSON, so unavailable.
                    return response.ok ? response.json() : null;
                }).then(show, function () { show(null); });
            } catch (error) {
                show(null);
            }
        }

        function schedule() {
            latest++;
            setStatus(options.document, 'pending');
            if (timer !== null) options.clearTimeout(timer);
            timer = options.setTimeout(function () {
                timer = null;
                refresh();
            }, options.delay === undefined ? 100 : options.delay);
        }

        return {refresh: refresh, schedule: schedule};
    }

    function touchesCard(node) {
        if (!node || node.nodeType !== 1) return false;
        if (CARD.test(node.id || '')) return true;
        return typeof node.querySelector === 'function' && node.querySelector('[id^="set_"]') !== null;
    }

    /**
     * Calls onChange whenever a staged card is added to or removed from the staging area, however
     * that happens (search, favourite, re-prescribe, the X button, clearing the stash, a reload).
     * Changes inside a card, including the markers render() writes, are ignored.
     *
     * @param container the staging area (#rxText)
     * @param onChange  called once per batch of mutations that adds or removes a card
     * @param Observer  MutationObserver (injected for tests)
     * @return the observer, or null when it cannot watch
     */
    function watch(container, onChange, Observer) {
        if (!container || typeof Observer !== 'function') return null;
        var observer = new Observer(function (records) {
            var changed = records.some(function (record) {
                return Array.prototype.some.call(record.addedNodes, touchesCard)
                    || Array.prototype.some.call(record.removedNodes, touchesCard);
            });
            if (changed) onChange();
        });
        observer.observe(container, {childList: true, subtree: true});
        return observer;
    }

    var api = {render: render, create: create, watch: watch, UNAVAILABLE: UNAVAILABLE, NOT_CHECKED: NOT_CHECKED};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.RxInteractionWarnings = api;
}(typeof window === 'undefined' ? globalThis : window));
