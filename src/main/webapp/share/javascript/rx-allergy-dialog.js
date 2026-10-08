/*
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

/**
 * Failure handling for the Rx "Add an Allergy" dialogue (issues #3355 and #3488).
 *
 * The allergy page (ShowAllergies2.jsp) loads the reaction form (AddReaction2.jsp) into the page
 * over AJAX, and the form then saved with a classic POST to /rx/addAllergy2. Two ways that went
 * wrong for a clinician:
 *
 *   - A refused AJAX request (a 403 from CSRFGuard was the #3355 case) had no error handler: the
 *     spinner kept turning and nothing was said. A 200 answer that was not the expected fragment
 *     (the login page, after the session ended) was worse: jQuery's replaceWith() swapped the
 *     dialogue container for nothing, so every later click had nowhere to render.
 *   - A refused save replaced the whole patient page with the generic error page and threw away
 *     the reaction, start date and age of onset the clinician had typed.
 *
 * This script makes both visible where the clinician is working:
 *
 *   - reportRequestFailure() lets the allergy page say, in a role="alert" region, which request
 *     failed and whether anything changed; the page itself refuses to render an answer that lacks
 *     the fragment it expects, so the dialogue container is never replaced with nothing.
 *   - Submitting #RxAddAllergyForm sends the same url-encoded body to the same route with fetch(),
 *     and treats the save as done ONLY when the response lands on the allergy list (the action's
 *     single success result is a redirect to /rx/showAllergy). Anything else keeps the dialogue
 *     and every entered value on screen, says plainly whether the allergy was not saved or could
 *     not be confirmed, and re-enables "Add Allergy" for a retry. A second click while a save is
 *     in flight is ignored, so a double click cannot record the allergy twice.
 *
 * Nothing here relaxes CSRF protection or the patient binding. The body still carries the form's
 * CSRF-TOKEN field (kept current by CSRFGuard's own submit hook, which runs first in the capture
 * phase) and its demographicNo / formDemographicNo pair, and RxAddAllergy2Action still checks
 * both. When fetch, FormData or URLSearchParams is unavailable the form is left to post the
 * classic way, exactly as before.
 *
 * Loaded by ShowAllergies2.jsp (the AJAX-injected dialogue) and AddReaction2.jsp (the standalone
 * page reached from ChooseAllergy2.jsp). Exposed as window.CarlosAllergyDialog in a browser and as
 * a CommonJS module for scripts/rx-allergy-dialog.test.js.
 *
 * @since 2026-10-08
 */
(function (root) {
    'use strict';

    var FORM_ID = 'RxAddAllergyForm';
    var SAVE_STATUS_CLASS = 'allergySaveStatus';
    var SAVING_ATTRIBUTE = 'data-allergy-saving';

    /** What each allergy-page AJAX route was doing, in the words the alert uses. */
    var REQUESTS = [
        {route: '/rx/addReaction2', action: 'The allergy details form could not be opened',
            outcome: 'Nothing was saved.'},
        {route: '/rx/deleteAllergy2', action: 'The allergy could not be inactivated',
            outcome: 'Reload this page to check the allergy\'s current status before trying again.'},
        {route: '/rx/searchAllergy2', action: 'The allergy search did not complete',
            outcome: 'Nothing was saved.'}
    ];

    /** The URL without its query string or fragment; routes are matched on the path alone. */
    function pathOf(url) {
        var text = String(url || '');
        var end = text.search(/[?#]/);
        return end < 0 ? text : text.slice(0, end);
    }

    /** Whether the URL's path ends with route (so it matches with or without the context path). */
    function routeEndsWith(url, route) {
        var path = pathOf(url);
        return path.length >= route.length && path.slice(path.length - route.length) === route;
    }

    /**
     * Why a request failed, as a short clause. status 0 means no HTTP answer at all; a 2xx means
     * the server answered with a page this dialogue cannot use (in practice the login page after
     * the session ended).
     */
    function describeStatus(status) {
        var code = Number(status) || 0;
        if (code === 0) {
            return 'the server could not be reached';
        }
        if (code >= 200 && code < 300) {
            return 'the server answered with an unexpected page; your session may have ended';
        }
        if (code === 401 || code === 403) {
            return 'the server refused the request (HTTP ' + code + '); your session may have ended';
        }
        return 'the server answered HTTP ' + code;
    }

    /**
     * The alert text for a failed allergy-page AJAX request.
     *
     * @param {string} url the request URL (only its path is read)
     * @param {number} status the HTTP status, 0 for no answer, or 200 for an unusable answer
     * @returns {string} a sentence that names the failed step and what it means for the chart
     */
    function requestFailureMessage(url, status) {
        var request = null;
        for (var i = 0; i < REQUESTS.length; i++) {
            if (routeEndsWith(url, REQUESTS[i].route)) {
                request = REQUESTS[i];
                break;
            }
        }
        var action = request ? request.action : 'The allergy request did not complete';
        var outcome = request ? request.outcome : 'Reload this page before trying again.';
        return action + ': ' + describeStatus(status) + '. ' + outcome;
    }

    /**
     * The alert text for a save that was not confirmed.
     *
     * A 4xx is a refusal: RxAddAllergy2Action and the filters in front of it refuse before writing
     * anything, so the allergy is known not to be saved. A 5xx, no answer, or an answer that is not
     * the allergy list cannot be read that way (the write may have happened before the failure),
     * so the clinician is told to check the list before adding it again.
     *
     * @param {number} status the HTTP status, 0 for no answer, or 200 when the answer was not the list
     * @returns {string} the message shown in the dialogue
     */
    function saveFailureMessage(status) {
        var code = Number(status) || 0;
        var kept = ' Your entries are still in the form below.';
        if (code >= 400 && code < 500) {
            return 'Allergy NOT saved: ' + describeStatus(code) + '.' + kept
                + ' Press "Add Allergy" to try again; if it is refused again, copy your entries,'
                + ' reload this page (logging in again if asked) and re-enter them.';
        }
        return 'The allergy save could not be confirmed: ' + describeStatus(code) + '.' + kept
            + ' It may or may not have been recorded. Check the patient\'s allergy list in another'
            + ' window before pressing "Add Allergy" again, so it is not recorded twice.';
    }

    /**
     * Shows message in a live region, or hides the region for an empty message. textContent only,
     * never markup. The look is applied here so a region rendered by a JSP and one created by
     * this script read the same without a stylesheet of their own.
     */
    function showMessage(region, message) {
        if (!region) {
            return;
        }
        region.textContent = message;
        var style = region.style;
        if (style) {
            style.color = '#a00000';
            style.fontWeight = 'bold';
            style.border = '2px solid #a00000';
            style.background = '#fff4f4';
            style.padding = '6px';
            style.margin = '4px 0';
            style.display = message ? 'block' : 'none';
        }
    }

    /**
     * Builds the dialogue handler for one window. Kept separate from the browser bootstrap below so
     * the tests can drive it with a fake window, document, fetch and form.
     *
     * @param {Window} win the window whose document holds the allergy page or reaction form
     * @returns {{save: function, install: function, reportRequestFailure: function,
     *            clearRequestFailure: function}}
     */
    function create(win) {
        var doc = win.document;

        /** A hidden, empty role="alert" region, for markup that did not render one. */
        function newRegion(className) {
            var region = doc.createElement('div');
            region.className = className;
            // role="alert" is announced as soon as text is put into it; the region exists before
            // the text arrives so assistive technology reliably notices the change.
            region.setAttribute('role', 'alert');
            region.setAttribute('aria-live', 'assertive');
            showMessage(region, '');
            return region;
        }

        /**
         * The save-status region inside the form, created at the top of the form when the
         * rendered markup has none.
         */
        function saveStatusRegion(form) {
            var region = form.querySelector('.' + SAVE_STATUS_CLASS);
            if (!region) {
                region = newRegion(SAVE_STATUS_CLASS);
                form.insertBefore(region, form.firstChild);
            }
            return region;
        }

        /** The form's submit controls, disabled together while a save is out. */
        function submitButtons(form) {
            return Array.prototype.slice.call(form.querySelectorAll('input[type="submit"], button[type="submit"]'));
        }

        /**
         * Marks a save as in flight (or finished): the attribute is the double-submit guard,
         * aria-busy tells assistive technology, and the submit buttons are disabled.
         */
        function setBusy(form, busy) {
            if (busy) {
                form.setAttribute(SAVING_ATTRIBUTE, 'true');
                form.setAttribute('aria-busy', 'true');
            } else {
                form.removeAttribute(SAVING_ATTRIBUTE);
                form.removeAttribute('aria-busy');
            }
            submitButtons(form).forEach(function (button) {
                button.disabled = busy;
            });
        }

        /** True only for the allergy list, which is where a successful save redirects. */
        function landedOnAllergyList(response) {
            return response.ok && routeEndsWith(response.url, '/rx/showAllergy');
        }

        /** Ends a save that was not confirmed: re-enables the form and says why, keeping every value. */
        function fail(form, submitter, status) {
            setBusy(form, false);
            showMessage(saveStatusRegion(form), saveFailureMessage(status));
            // The button was disabled while the request was out, which drops keyboard focus to the
            // page. Put it back so a keyboard user can retry with Enter.
            if (submitter && typeof submitter.focus === 'function') {
                submitter.focus();
            }
        }

        /**
         * The form's data set as the classic submission would build it, the submit button's own
         * name=value included, so the server and the WAF see the same body as before. FormData
         * throws when the submitter is not one of the form's submit buttons, and ignores the
         * second argument where it is unsupported; both fall back to the fields alone, which
         * RxAddAllergy2Action does not miss.
         */
        function formData(form, submitter) {
            if (submitter) {
                try {
                    return new win.FormData(form, submitter);
                } catch (e) {
                    // fall through to the fields alone
                }
            }
            return new win.FormData(form);
        }

        /**
         * Saves the allergy form over fetch() and navigates to the allergy list only once the
         * server has confirmed the save.
         *
         * @param {HTMLFormElement} form the #RxAddAllergyForm being submitted
         * @param {HTMLElement} [submitter] the button that submitted it, to refocus after a failure
         * @returns {Promise<string>} 'saved', 'failed', or 'ignored' for a click while a save is out
         */
        function save(form, submitter) {
            if (form.getAttribute(SAVING_ATTRIBUTE) === 'true') {
                return Promise.resolve('ignored');
            }
            // Read the fields before anything is disabled: a disabled control is left out.
            var body = new win.URLSearchParams(formData(form, submitter));
            setBusy(form, true);
            showMessage(saveStatusRegion(form), '');
            return win.fetch(form.action, {
                method: 'POST',
                body: body,
                credentials: 'same-origin',
                redirect: 'follow',
                headers: {'Accept': 'text/html'}
            }).then(function (response) {
                // Drain the body before acting on the answer: a body left unread is cancelled
                // (ERR_ABORTED) when the page navigates away, which reads as a failed request.
                return response.text().then(function () {
                    return response;
                }, function () {
                    return response;
                });
            }).then(function (response) {
                if (landedOnAllergyList(response)) {
                    // Leave the button disabled: the page is about to be replaced.
                    win.location.assign(response.url);
                    return 'saved';
                }
                fail(form, submitter, response.ok ? 200 : response.status);
                return 'failed';
            }, function () {
                fail(form, submitter, 0);
                return 'failed';
            });
        }

        /** Whether this browser can save in the page; otherwise the form posts the classic way. */
        function canSaveInPage() {
            return typeof win.fetch === 'function' && typeof win.FormData === 'function'
                && typeof win.URLSearchParams === 'function';
        }

        /**
         * The save, as a listener on the form itself. At the target it runs after CSRFGuard's
         * capture-phase hook on the document has put the current token in the form and after any
         * handler of the form's own, so a submission one of them cancelled is left alone. The
         * button's onclick validation (doSubmit) runs earlier still: an invalid form fires no
         * submit event at all.
         */
        function onSubmit(event) {
            var form = event.currentTarget || event.target;
            if (event.defaultPrevented || !canSaveInPage()) {
                return;
            }
            event.preventDefault();
            save(form, event.submitter || null);
        }

        var boundForms = typeof WeakSet === 'function' ? new WeakSet() : null;

        /**
         * Capture-phase submit listener on the document that binds onSubmit to the allergy form the
         * first time that form is submitted. It cannot simply listen on the document: the injected
         * dialogue sits inside the allergy page's search form, and Blink stops a nested form's
         * submit event at the enclosing form, so it never bubbles to the document. Capture still
         * reaches the target, and a listener added to the form here is invoked when the dispatch
         * gets there. Each injection is a new form element, bound once.
         */
        function bindOnSubmit(event) {
            var form = event.target;
            if (!form || form.id !== FORM_ID || !canSaveInPage()) {
                return;
            }
            if (boundForms ? boundForms.has(form) : form.getAttribute('data-allergy-save-bound') === 'true') {
                return;
            }
            if (boundForms) {
                boundForms.add(form);
            } else {
                form.setAttribute('data-allergy-save-bound', 'true');
            }
            form.addEventListener('submit', onSubmit, false);
        }

        /** Installs the capture-phase binding once per document; repeated calls are no-ops. */
        function install() {
            if (doc.carlosAllergyDialogInstalled === true) {
                return;
            }
            doc.carlosAllergyDialogInstalled = true;
            doc.addEventListener('submit', bindOnSubmit, true);
        }

        /**
         * Shows a failed allergy-page AJAX request in the page's #allergyRequestStatus region
         * (ShowAllergies2.jsp renders it beside the dialogue), creating one at the top of the page
         * when the markup has none.
         */
        function reportRequestFailure(url, status) {
            var region = doc.getElementById('allergyRequestStatus');
            if (!region) {
                region = newRegion('allergyRequestStatus');
                region.id = 'allergyRequestStatus';
                doc.body.insertBefore(region, doc.body.firstChild);
            }
            showMessage(region, requestFailureMessage(url, status));
        }

        /** Hides the page's request-failure alert once a later request has rendered. */
        function clearRequestFailure() {
            showMessage(doc.getElementById('allergyRequestStatus'), '');
        }

        return {
            save: save,
            install: install,
            reportRequestFailure: reportRequestFailure,
            clearRequestFailure: clearRequestFailure
        };
    }

    var api = {
        create: create,
        requestFailureMessage: requestFailureMessage,
        saveFailureMessage: saveFailureMessage
    };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    if (root && root.document) {
        var dialog = create(root);
        dialog.install();
        root.CarlosAllergyDialog = {
            save: dialog.save,
            reportRequestFailure: dialog.reportRequestFailure,
            clearRequestFailure: dialog.clearRequestFailure,
            requestFailureMessage: requestFailureMessage,
            saveFailureMessage: saveFailureMessage
        };
    }
})(typeof window !== 'undefined' ? window : null);
