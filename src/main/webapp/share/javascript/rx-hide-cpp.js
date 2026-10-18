/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The medication profile's "Hide from CPP" checkboxes (ListDrugs.jsp, loaded into SearchDrug3.jsp).
 *
 * Each change posts rx/hideCpp, which answers "ok" once the flag is stored. The checkbox used to
 * fire the request and forget it, so a refused or failed save left it showing a state the
 * database did not have, with no message (#5001). Now the box is held while the save is in
 * flight; anything but a completed "ok" puts it back to the stored state and tells the user.
 */
var RxHideCpp = (function () {
    'use strict';

    /**
     * Saves one checkbox's new state.
     *
     * @param {Object} options
     * @param {HTMLInputElement} options.checkbox the changed checkbox (already showing the new state)
     * @param {string} options.url the rx/hideCpp URL
     * @param {string|number} options.prescriptId the drug id
     * @param {string} options.csrfToken the page's CSRF token
     * @param {string} options.failureMessage shown when the change was not stored
     * @param {Function} options.fetch window.fetch
     * @param {Function} options.alert window.alert
     * @return {Promise<boolean>} whether the change was stored
     */
    function save(options) {
        var checkbox = options.checkbox;
        var requested = checkbox.checked;
        checkbox.disabled = true;
        var body = 'method=update&prescriptId=' + encodeURIComponent(options.prescriptId)
            + '&value=' + encodeURIComponent(requested);
        return Promise.resolve().then(function () {
            return options.fetch(options.url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest',
                    'CSRF-TOKEN': options.csrfToken
                },
                credentials: 'same-origin',
                body: body
            });
        }).then(function (response) {
            // A redirect (expired session, login page) or an error page is not a stored change.
            if (!response || !response.ok || response.redirected) throw new Error('refused');
            return response.text();
        }).then(function (text) {
            if (String(text).trim() !== 'ok') throw new Error('unexpected response');
        }).then(function () {
            checkbox.checked = requested;
            checkbox.disabled = false;
            return true;
        }, function () {
            checkbox.checked = !requested;
            checkbox.disabled = false;
            options.alert(options.failureMessage);
            return false;
        });
    }

    return {save: save};
}());
if (typeof module !== 'undefined' && module.exports) module.exports = RxHideCpp;
