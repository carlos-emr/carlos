/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// The patient's navigation (WEB-INF/jsp/demographic/patient-nav.jsp), on the master record and the
// patient portal page. Popup links carry their real targets; a plain click opens them in the
// record's usual popup windows, with the names and features of popupPage, popupEChart and
// popupOscarRx, so a link reuses the window it always did. A click with a modifier key, or a middle
// click, is left to the browser (a new tab).
(function () {
    'use strict';

    var WINDOWS = {
        page: {name: 'demodetail', origin: 'screenX=50,screenY=50,top=20,left=20'},
        echart: {name: 'encounter', origin: 'screenX=50,screenY=50,top=20,left=20'},
        rx: {name: 'rx', origin: 'screenX=0,screenY=0,top=0,left=0'}
    };

    /** The window features the record's popup helpers use for this kind of window. */
    function features(kind, height, width) {
        return 'height=' + height + ',width=' + width
            + ',location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,' + WINDOWS[kind].origin;
    }

    /** What a click on a navigation link should do: null to leave it to the browser. */
    function popupFor(kind, height, width, href, openInTab) {
        if (kind === 'window') {
            return {url: href, name: '_blank', features: 'resizable=yes,status=yes,scrollbars=yes'};
        }
        if (!Object.prototype.hasOwnProperty.call(WINDOWS, kind)) {
            return null;
        }
        if (openInTab) {
            return {url: href, tab: true};
        }
        return {url: href, name: WINDOWS[kind].name, features: features(kind, height, width)};
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {features: features, popupFor: popupFor};
        return;
    }

    function openPopup(link) {
        var plan = popupFor(link.getAttribute('data-nav-popup'), link.getAttribute('data-popup-height'),
            link.getAttribute('data-popup-width'), link.href,
            window.openEncounterInTab === true && typeof window.popupTab === 'function');
        if (!plan) {
            return false;
        }
        if (plan.tab) {
            window.popupTab(plan.url);
            return true;
        }
        var popup = window.open(plan.url, plan.name, plan.features);
        if (popup) {
            if (popup.opener == null) {
                popup.opener = window;
            }
            popup.focus();
        }
        return true;
    }

    // BC billing: the eligibility check opens a box under the link and asks Teleplan once.
    function toggleEligibility(link) {
        var box = document.getElementById(link.getAttribute('aria-controls'));
        if (!box) {
            return;
        }
        var opening = box.hidden;
        box.hidden = !opening;
        link.setAttribute('aria-expanded', opening ? 'true' : 'false');
        if (!opening || box.getAttribute('data-loaded') === 'true') {
            return;
        }
        box.setAttribute('data-loaded', 'true');
        var params = new URLSearchParams({
            demographic: link.getAttribute('data-demographic-no'),
            method: 'checkElig',
            rand: String(Math.round(Math.random() * 1000000))
        });
        fetch(link.getAttribute('data-nav-eligibility') + '?' + params.toString(), {
            method: 'GET', credentials: 'same-origin', headers: {'X-Requested-With': 'XMLHttpRequest'}
        }).then(function (response) {
            return response.text();
        }).then(function (text) {
            // Server-rendered eligibility HTML from the same origin, as the record has always shown it.
            box.querySelector('[data-role="eligibility-result"]').innerHTML = text;
            box.querySelector('[data-role="eligibility-loading"]').textContent = '';
        }).catch(function () {
            box.setAttribute('data-loaded', 'false');
        });
    }

    document.addEventListener('click', function (event) {
        if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest('.patient-nav a');
        if (!link) {
            return;
        }
        if (link.hasAttribute('data-nav-eligibility')) {
            event.preventDefault();
            toggleEligibility(link);
            return;
        }
        if (link.hasAttribute('data-nav-popup') && openPopup(link)) {
            event.preventDefault();
        }
    });
}());
