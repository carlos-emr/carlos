/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function (root, factory) {
    'use strict';
    if (typeof module === 'object' && module.exports) {
        module.exports = factory;
    } else {
        root.jQuery(function () { factory(root.document, root.jQuery); });
    }
}(typeof window === 'undefined' ? this : window, function (document, $) {
    'use strict';
    var trigger = document.getElementById('checkEligibility');
    var panel = document.getElementById('menu2');
    // The server omits this control outside the existing BC billing permissions.
    if (!trigger || !panel) return;
    var spinner = document.getElementById('search_spinner');
    var result = document.getElementById('returnTeleplanMsg');
    var pending = false;
    var dialog = $(panel).dialog({
        autoOpen: false,
        width: 350,
        title: panel.dataset.title,
        closeText: panel.dataset.close,
        close: function () {
            trigger.setAttribute('aria-expanded', 'false');
            trigger.focus();
        }
    });

    function open(event) {
        event.preventDefault();
        dialog.dialog('open');
        trigger.setAttribute('aria-expanded', 'true');
        // Reopening a pending check must not send a second Teleplan request.
        if (pending) return;
        pending = true;
        panel.setAttribute('aria-busy', 'true');
        spinner.hidden = false;
        result.textContent = '';
        $.ajax({
            url: panel.dataset.url,
            method: 'GET',
            dataType: 'html',
            cache: false,
            timeout: 30000,
            data: { demographic: panel.dataset.demographic, method: 'checkElig' }
        }).done(function (html) {
            // Same-origin, server-rendered eligibility fragment, as in the existing control.
            result.innerHTML = html;
        }).fail(function () {
            result.textContent = panel.dataset.error;
        }).always(function () {
            pending = false;
            spinner.hidden = true;
            panel.setAttribute('aria-busy', 'false');
        });
    }
    trigger.addEventListener('click', open);
}));
