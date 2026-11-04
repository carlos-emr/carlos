/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The patient's navigation (WEB-INF/jsp/demographic/patient-nav.jsp), on the master record and the
 * patient portal page. Popup links carry their real targets; a plain click opens them in the
 * record's usual popup windows, with the names and features of popupPage, popupEChart and
 * popupOscarRx, so a link reuses the window it always did, or in a tab when the user prefers tabs
 * (data-open-in-tab on the navigation). A click with a modifier key, or a middle click, is left to
 * the browser (a new tab).
 *
 * @since 2026-10-08
 */
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

    /**
     * The BC eligibility check: posts to Teleplan's checkElig (POST only, with the page's CSRF token)
     * and reports the outcome; the server's HTML answer is shown as the record has always shown it.
     */
    function checkEligibility(ajax, url, demographicNo, report) {
        ajax.request(url, {
            method: 'POST',
            parameters: {demographic: demographicNo, method: 'checkElig'},
            onSuccess: function (transport) {
                report.answered(transport.responseText);
            },
            onFailure: function () {
                report.failed();
            }
        });
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {features: features, popupFor: popupFor, checkEligibility: checkEligibility};
        return;
    }

    function openPopup(link) {
        var nav = link.closest('.patient-nav');
        var prefersTabs = (nav && nav.getAttribute('data-open-in-tab') === 'true') || window.openEncounterInTab === true;
        var plan = popupFor(link.getAttribute('data-nav-popup'), link.getAttribute('data-popup-height'),
            link.getAttribute('data-popup-width'), link.href,
            prefersTabs && typeof window.popupTab === 'function');
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

    // BC billing: the eligibility check opens a box under the link and asks Teleplan once; after a
    // failure the next opening asks again.
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
        var loading = box.querySelector('[data-role="eligibility-loading"]');
        var result = box.querySelector('[data-role="eligibility-result"]');
        var error = box.querySelector('[data-role="eligibility-error"]');
        loading.hidden = false;
        error.hidden = true;
        result.innerHTML = '';
        var report = {
            answered: function (html) {
                loading.hidden = true;
                // Server-rendered eligibility HTML from the same origin, as the record has always shown it.
                result.innerHTML = html;
            },
            failed: function () {
                loading.hidden = true;
                error.hidden = false;
                box.setAttribute('data-loaded', 'false');
            }
        };
        if (typeof window.CarlosAjax === 'undefined') {
            report.failed();
            return;
        }
        checkEligibility(window.CarlosAjax, link.getAttribute('data-nav-eligibility'),
            link.getAttribute('data-demographic-no'), report);
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
