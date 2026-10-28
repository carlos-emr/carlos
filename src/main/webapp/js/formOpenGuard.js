/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function (root) {
    'use strict';
    var pending = false;
    var formCompletionKeys = new Set();

    // Replace this panel's entries, including dated keys that change on Save.
    // Mutate the existing array: the modern autocomplete retains its reference.
    root.replaceEncounterFormCompletions = function (items) {
        var nextKeys = new Set(items.map(function (item) { return item[0]; }));
        for (var index = root.autoCompList.length - 1; index >= 0; index--) {
            if (formCompletionKeys.has(root.autoCompList[index]) || nextKeys.has(root.autoCompList[index])) {
                root.autoCompList.splice(index, 1);
            }
        }
        formCompletionKeys.forEach(function (key) {
            delete root.autoCompleted[key];
            delete root.itemColours[key];
        });
        items.forEach(function (item) {
            if (root.autoCompList.indexOf(item[0]) === -1) root.autoCompList.push(item[0]);
            root.autoCompleted[item[0]] = item[1];
            root.itemColours[item[0]] = item[2];
        });
        formCompletionKeys = nextKeys;
    };

    // Keep the original chart's patient/appointment, never the login-wide encounter bean.
    function openEncounterForm(url, windowName, name) {
        if (pending) return;
        var target = new URL(url, root.location.href);
        if (target.origin !== root.location.origin) return;
        var patient = target.searchParams.get('demographic_no');
        var camelPatient = target.searchParams.get('demographicNo');
        if (patient !== null && camelPatient !== null && patient !== camelPatient) {
            root.alert('The saved form check could not be completed. Please try again.');
            return;
        }
        if (patient === null) patient = camelPatient;
        var context = typeof root.ctx === 'string' ? root.ctx : root.encounterConfig.ctx;
        var check = new URL(context + '/encounter/formExists', root.location.href);
        check.searchParams.set('demographicNo', patient || '');
        check.searchParams.set('formname', name);
        if (target.searchParams.has('appointmentNo')) {
            check.searchParams.set('appointmentNo', target.searchParams.get('appointmentNo'));
        }
        pending = true;
        function release() { pending = false; }
        function open(address, win) {
            var module = root.document.getElementById('forms');
            var reload = module && module.querySelector('input[name="reloadUrl"]');
            if (reload && root.reloadWindows) {
                root.reloadWindows[win] = reload.value;
                root.reloadWindows[win + 'div'] = 'forms';
            }
            root.popupPage(700, 960, win, address);
            release();
        }
        // Reserve a window during the user gesture. Browsers block window.open after fetch.
        var reserved = root.open('', windowName, 'width=960,height=700,resizable=yes,scrollbars=yes');
        function closeReserved() {
            try { if (reserved && !reserved.closed && reserved.location.href === 'about:blank') reserved.close(); }
            catch (ignored) { /* Existing named form windows must stay open. */ }
        }
        return root.fetch(check.href, {credentials: 'same-origin', headers: {Accept: 'application/json'}})
            .then(function (response) {
                if (!response.ok) throw new Error('Form check failed');
                return response.json();
            }).then(function (result) {
                if (!result || typeof result.exists !== 'boolean') throw new Error('Invalid form check');
                if (!result.exists) { open(target.href, windowName); return; }
                closeReserved();
                var dialog = root.jQuery('<div>').text(
                    'This patient already has a saved version of this form. Open it to continue their record, or explicitly start a new blank version.');
                dialog.dialog({
                    title: 'Saved form exists', modal: true, width: 460,
                    buttons: [
                        {text: 'Open the existing version', click: function () {
                            var existing = new URL(result.url, root.location.href);
                            if (existing.origin === root.location.origin) open(existing.href, result.windowName);
                            dialog.dialog('close');
                        }},
                        {text: 'Create a new blank version', click: function () {
                            open(target.href, windowName); dialog.dialog('close');
                        }},
                        {text: 'Cancel', click: function () { dialog.dialog('close'); }}
                    ],
                    close: function () { release(); dialog.dialog('destroy').remove(); }
                });
            }).catch(function () {
                closeReserved(); release();
                root.alert('The saved form check could not be completed. Please try again.');
            });
    }

    root.openEncounterForm = openEncounterForm;
    root.document.addEventListener('click', function (event) {
        var link = event.target.closest && event.target.closest('#forms .menu a[data-encounter-form-name]');
        if (!link) return;
        event.preventDefault(); event.stopImmediatePropagation();
        openEncounterForm(link.dataset.encounterFormUrl, link.dataset.encounterFormWindow, link.dataset.encounterFormName);
    }, true);
}(window));
