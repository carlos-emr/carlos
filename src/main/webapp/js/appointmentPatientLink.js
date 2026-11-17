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
 * Keeps the Add/Edit Appointment patient name field (#keyword) and the patient
 * link (#demographic_no, plus the read-only #mrp display) in step.
 *
 * WHY THIS EXISTS (issue #3883). The jQuery UI autocomplete's focus callback,
 * which runs while the user arrows or hovers through results, writes only the
 * name; select, the only place the link was written, fires only on Enter, Tab
 * or a click on the row. Arrowing to a patient and then clicking or tabbing
 * away saved the appointment with that patient's name and demographic_no = 0.
 *
 * HOW THE SAVE ACTIONS READ THESE FIELDS, which is what the semantics below
 * are built around:
 *   - AppointmentAddRecord2Action: a non-empty demographic_no WINS. The
 *     appointment is booked for that patient and the typed name is replaced by
 *     the patient's name from the database. A stale link therefore books the
 *     old patient silently, whatever the name field says.
 *   - AppointmentUpdateRecord2Action: demographic_no and keyword are saved
 *     independently, so a stale link saves a name that belongs to one patient
 *     against another patient's chart.
 * Either way, a name that no longer matches the link is a wrong-patient risk,
 * so the two must agree by the time the field is left or the form submitted.
 *
 * SEMANTICS (adapted from MagentaHealth/Open-O 4dcd933fb9 and b82ce7d85a):
 *   1. Typing never unlinks. Only the highlighted row is dropped, so a search
 *      in progress cannot flip the link underneath the user (b82ce7d85a).
 *   2. On blur, on submit, and when the menu closes other than by Escape, a
 *      highlighted row that the field still shows is committed exactly as
 *      select would commit it (4dcd933fb9).
 *   3. Otherwise, on blur or submit, the field is reconciled against the link:
 *      - same name as the linked patient (ignoring surrounding whitespace):
 *        the link is kept and the exact linked name restored;
 *      - field cleared to blank: the linked name is put back and the link kept.
 *        Blanking the field is how users start a new search, and dropping the
 *        booked patient because a search was abandoned is what b82ce7d85a
 *        fixed. The restored name makes the kept link visible, not silent;
 *      - any other text: the user has deliberately named someone or something
 *        else, so the link is removed (demographic_no and the MRP display are
 *        cleared, the patient banners hidden). This diverges from b82ce7d85a,
 *        which kept the link here; on Add that booked the old patient under
 *        the old name despite the new text. Picking a patient again relinks.
 *   Page code that writes the fields directly calls rebase() (pasteAppt) or
 *   unlink() (Do Not Book), since assigning .value fires no input event. When
 *   rebase() finds a different patient, the MRP display and the patient
 *   banners belonged to the old one: without the new patient's metadata the
 *   MRP is cleared and the banners hidden (onStale); with it, onCommit
 *   refreshes them.
 *   Page code that submits programmatically calls submitForm(form), never
 *   form.submit(): the native method dispatches no submit event, so rule 3
 *   would otherwise be skipped. requestSubmit() is deliberately not used; it
 *   would also run the pages' onsubmit handlers (onAdd/onSub), whose
 *   confirmations and saveTemp branching those call sites bypass today.
 *
 * Plain DOM, no jQuery: the pages feed it from their existing autocomplete
 * callbacks. Exposed as window.CarlosAppointmentPatientLink and as a CommonJS
 * module for scripts/appointment-patient-link.test.js.
 *
 * @since 2026-09-24
 */
(function (root) {
    'use strict';

    var ESCAPE_KEY_CODE = 27;

    // One controller per name field, so page functions defined outside the
    // ready handler (pasteAppt, onNotBook) can reach it from the element alone.
    var controllers = typeof WeakMap === 'function' ? new WeakMap() : null;

    function text(value) {
        return value === undefined || value === null ? '' : String(value);
    }

    /**
     * True when a jQuery UI event was caused by the Escape key. jQuery UI wraps
     * the triggering event, so walk the originalEvent chain.
     */
    function isEscape(event) {
        var current = event;
        var depth = 0;
        while (current && depth < 5) {
            if (current.type === 'keydown'
                    && (current.keyCode === ESCAPE_KEY_CODE || current.key === 'Escape')) {
                return true;
            }
            current = current.originalEvent;
            depth++;
        }
        return false;
    }

    /**
     * Wire a patient name field to its link fields.
     *
     * @param {Object} options
     * @param {HTMLInputElement} options.nameField          the #keyword input
     * @param {HTMLInputElement} options.demographicField   the demographic_no input
     * @param {HTMLInputElement} [options.providerField]    the read-only MRP display
     * @param {function(Object)} [options.onCommit]  called with the autocomplete item
     *        whenever a patient is linked, by select or by blur (patient banners)
     * @param {function()} [options.onUnlink]  called when the link is removed
     * @param {function(?Object)} [options.onStale]  called with the new baseline
     *        (or null) when rebase() finds the link changed and no patient metadata
     *        was supplied, so banners describing the old patient must go.
     *        Defaults to onUnlink.
     * @returns {Object} the controller: highlight, commit, menuClosed, settle,
     *          rebase, unlink, and state() for tests
     */
    function create(options) {
        var nameField = options.nameField;
        var demographicField = options.demographicField;
        var providerField = options.providerField || null;
        var onCommit = options.onCommit || function () {};
        var onUnlink = options.onUnlink || function () {};
        var onStale = options.onStale || function () { onUnlink(); };

        // The row highlighted with the arrow keys or the mouse but not yet selected.
        var highlighted = null;
        // The patient the field is linked to, with the name shown when the link was
        // made, or null when the appointment is not linked to a patient.
        var linked = null;

        // The linked demographic_no in the field now, or null for "no patient".
        // "0" is how a pasted free-text appointment carries "no patient".
        function fieldLink() {
            var demographicNo = text(demographicField.value);
            return demographicNo === '' || demographicNo === '0' ? null : demographicNo;
        }

        // Record the fields as the baseline, silently. Only for states whose
        // banners and MRP are already right: page load and commit().
        function snapshot() {
            highlighted = null;
            var demographicNo = fieldLink();
            linked = demographicNo === null ? null : {
                value: demographicNo,
                provider: providerField ? text(providerField.value) : '',
                formattedName: text(nameField.value)
            };
        }

        /**
         * Adopt a name and link that code outside this controller wrote.
         *
         * @param {Object} [item] the new patient's autocomplete-shaped metadata
         *        (provider, alert, status, rosterStatus), when the caller has it
         */
        function rebase(item) {
            var previous = linked ? linked.value : null;
            var current = fieldLink();
            if (item && current !== null) {
                if (providerField) {
                    providerField.value = text(item.provider);
                }
                snapshot();
                onCommit(item);
                return;
            }
            if (current !== previous && providerField) {
                // The MRP shown belongs to the previous patient (or to nobody now).
                providerField.value = '';
            }
            snapshot();
            if (current !== previous) {
                onStale(linked);
            }
        }

        function highlight(item) {
            nameField.value = text(item.formattedName);
            highlighted = item;
        }

        function commit(item) {
            demographicField.value = text(item.value);
            if (providerField) {
                providerField.value = text(item.provider);
            }
            nameField.value = text(item.formattedName);
            snapshot();
            onCommit(item);
        }

        function unlink() {
            highlighted = null;
            linked = null;
            demographicField.value = '';
            if (providerField) {
                providerField.value = '';
            }
            onUnlink();
        }

        // Rule 2: commit the highlighted row only while the field still shows it.
        // Escape, or arrowing past either end of the menu, puts the typed term back
        // without an input event, and this comparison is what catches that.
        function commitHighlightedIfShown() {
            if (highlighted && text(highlighted.formattedName) === text(nameField.value)) {
                commit(highlighted);
                return true;
            }
            return false;
        }

        function menuClosed(event) {
            if (isEscape(event)) {
                highlighted = null;
                return;
            }
            // The menu also closes mid-typing when a search returns nothing, so this
            // only ever commits; unlinking waits for blur or submit (rule 1).
            commitHighlightedIfShown();
        }

        // Rules 2 and 3: run when the user leaves the field or submits the form.
        function settle() {
            if (commitHighlightedIfShown()) {
                return;
            }
            highlighted = null;
            if (!linked) {
                return;
            }
            if (text(demographicField.value) !== linked.value) {
                // Something other than this controller changed the link; trust it.
                rebase();
                return;
            }
            var name = text(nameField.value).trim();
            if (name === '' || name === linked.formattedName.trim()) {
                nameField.value = linked.formattedName;
                return;
            }
            unlink();
        }

        nameField.addEventListener('focus', function () {
            // Pick up a link written by page code that did not call rebase().
            if (fieldLink() !== (linked ? linked.value : null)) {
                rebase();
            }
        });
        nameField.addEventListener('input', function () {
            highlighted = null;
        });
        nameField.addEventListener('keydown', function (event) {
            if (isEscape(event)) {
                highlighted = null;
            }
        });
        nameField.addEventListener('blur', settle);
        // A submit that does not blur the field first (implicit submission, or
        // requestSubmit()) must reconcile too, and BEFORE the form's inline
        // onsubmit="return onAdd()" / "return onSub()" runs: those read #keyword
        // and #demographic_no (onAdd's "." no-show rule, for one). An inline
        // handler is registered when the attribute is parsed, long before this
        // code, so a plain listener on the form would run after it. A capture
        // listener on the document runs in the capture phase, which always
        // precedes the target phase the form's own handlers run in.
        var form = nameField.form;
        if (form) {
            var doc = form.ownerDocument;
            if (doc && typeof doc.addEventListener === 'function') {
                doc.addEventListener('submit', function (event) {
                    if (event.target === form) {
                        settle();
                    }
                }, true);
            } else {
                form.addEventListener('submit', settle, true);
            }
        }

        // The server rendered the initial banners and MRP for this link.
        snapshot();

        var controller = {
            highlight: highlight,
            commit: commit,
            menuClosed: menuClosed,
            settle: settle,
            rebase: rebase,
            unlink: unlink,
            state: function () {
                return { highlighted: highlighted, linked: linked };
            }
        };
        if (controllers) {
            controllers.set(nameField, controller);
            if (nameField.form) {
                controllers.set(nameField.form, controller);
            }
        }
        return controller;
    }

    /**
     * Show the patient alert and status banners for a linked patient. Text is
     * set with textContent only, so autocomplete data can never become markup.
     * AC (active) and RO (rostered) are the expected defaults and show no status
     * banner. The roster label comes from the banner's data-roster-label, which
     * the page renders from its message bundle.
     */
    function showPatientBanners(doc, item) {
        var alertBanner = doc.getElementById('patientAlertBanner');
        var alertText = doc.getElementById('patientAlertText');
        if (alertBanner && alertText) {
            revealPatientBanner(alertBanner);
            var patientAlert = text(item.alert);
            alertText.textContent = patientAlert;
            alertBanner.style.display = patientAlert ? '' : 'none';
        }

        var statusBanner = doc.getElementById('patientStatusBanner');
        var statusText = doc.getElementById('patientStatusText');
        if (!statusBanner || !statusText) {
            return;
        }
        revealPatientBanner(statusBanner);
        var rawStatus = text(item.status);
        var rawRoster = text(item.rosterStatus);
        var displayStatus = rawStatus === 'AC' ? '' : rawStatus;
        var displayRoster = rawRoster === 'RO' ? '' : rawRoster;
        if (displayStatus || displayRoster) {
            var parts = [];
            if (displayStatus) {
                parts.push(displayStatus);
            }
            if (displayRoster) {
                parts.push((statusBanner.getAttribute('data-roster-label') || '') + ':\u00a0' + displayRoster);
            }
            statusText.textContent = parts.join('\u00a0');
            statusBanner.style.display = '';
        } else {
            statusBanner.style.display = 'none';
        }
    }

    /**
     * Hide both patient banners, e.g. once no patient is linked.
     *
     * A shown banner is hidden with visibility, not display, so it keeps its space. The unlink
     * that calls this usually runs on the name field's blur, and the mouse-down that causes that
     * blur is often on the Add/Update button below the banners: collapsing them moved the button
     * up (by ~75px on Add Appointment) before the mouse was released, so the click landed on
     * nothing and the clinician's first click on Add/Update did nothing at all.
     */
    function hidePatientBanners(doc) {
        ['patientAlertBanner', 'patientStatusBanner'].forEach(function (id) {
            var banner = doc.getElementById(id);
            if (banner && banner.style.display !== 'none') {
                banner.style.visibility = 'hidden';
                if (typeof banner.setAttribute === 'function') {
                    banner.setAttribute('aria-hidden', 'true');
                }
            }
        });
    }

    /** Undo hidePatientBanners() before a banner is shown or hidden for a newly linked patient. */
    function revealPatientBanner(banner) {
        banner.style.visibility = '';
        if (typeof banner.removeAttribute === 'function') {
            banner.removeAttribute('aria-hidden');
        }
    }

    /**
     * Wire the standard Add/Edit Appointment page: #keyword, #demographic_no,
     * #mrp and the patientAlertBanner / patientStatusBanner pair. Both pages
     * share this markup, so the banner handling lives here once rather than in
     * each JSP.
     *
     * @param {Document} doc the appointment page's document
     * @returns {Object} the controller, as create() returns it
     */
    function attach(doc) {
        return create({
            nameField: doc.getElementById('keyword'),
            demographicField: doc.getElementById('demographic_no'),
            providerField: doc.getElementById('mrp'),
            onCommit: function (item) {
                showPatientBanners(doc, item);
            },
            onUnlink: function () {
                hidePatientBanners(doc);
            }
        });
    }

    /** The controller attached to a name field, or null if none was created. */
    function forField(nameField) {
        return controllers && nameField ? (controllers.get(nameField) || null) : null;
    }

    /**
     * Take the field's current name and link as the baseline after a direct write
     * (pasteAppt). Pass the patient's metadata if known; otherwise a changed link
     * clears the MRP and hides the banners.
     */
    function rebase(nameField, item) {
        var controller = forField(nameField);
        if (controller) {
            controller.rebase(item);
        }
    }

    /**
     * Reconcile the patient link now (rule 2/3) for a form or its name field.
     * Page code that reads #keyword or #demographic_no before a scripted submit
     * (onButRepeat's calculateEndTime(), whose "." no-show rule tests for an
     * unlinked name) calls this first, so it sees the settled link rather than a
     * stale one. A target with no controller is ignored.
     */
    function settle(target) {
        var controller = controllers && target ? (controllers.get(target) || null) : null;
        if (controller) {
            controller.settle();
        }
    }

    /**
     * Submit an appointment form from script. Reconciles the patient link first
     * (settle), then calls the native submit, which fires no submit event and so
     * would otherwise skip that step. A form with no controller is submitted as is.
     */
    function submitForm(form) {
        var controller = controllers && form ? (controllers.get(form) || null) : null;
        if (controller) {
            controller.settle();
        }
        // The prototype method, so a form control named "submit" cannot shadow it.
        var nativeSubmit = typeof HTMLFormElement !== 'undefined'
            ? HTMLFormElement.prototype.submit : form.submit;
        nativeSubmit.call(form);
    }

    /** Remove the patient link (after page code replaced the name directly). */
    function unlink(nameField) {
        var controller = forField(nameField);
        if (controller) {
            controller.unlink();
        }
    }

    var api = {
        create: create,
        attach: attach,
        showPatientBanners: showPatientBanners,
        hidePatientBanners: hidePatientBanners,
        forField: forField,
        rebase: rebase,
        unlink: unlink,
        settle: settle,
        submitForm: submitForm,
        isEscape: isEscape
    };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    root.CarlosAppointmentPatientLink = api;
}(typeof window !== 'undefined' ? window : this));
