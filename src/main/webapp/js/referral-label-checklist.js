/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
(function () {
    'use strict';

    var queue = [];
    var sending = false;
    var failed = false;
    var heldAction = null;
    var list = document.getElementById('checked_items');
    var revision = list.dataset.revision;
    var revisionHeader = 'X-Carlos-Checklist-Revision';
    var status = document.getElementById('labelUpdateError');
    var search = document.getElementById('referralSearch');

    function render(rows) {
        var body = document.querySelector('#checked_items_tbl tbody');
        var ids = new Set(rows.map(function (row) { return String(row.id); }));
        body.replaceChildren();
        rows.forEach(function (row) {
            body.insertRow().insertCell().textContent = row.formattedName;
        });
        if (!rows.length) body.insertRow().insertCell().textContent = list.dataset.empty;
        document.querySelectorAll('input[data-referral-id]').forEach(function (box) {
            box.checked = ids.has(box.dataset.referralId);
        });
    }

    function refuse() {
        sending = false;
        failed = true;
        heldAction = null;
        status.hidden = false;
    }

    function sendNext() {
        if (sending || failed || !queue.length) return;
        sending = true;
        var update = queue[0];
        $.ajax({url: search.action, method: 'POST', data: Object.assign({revision: revision}, update), dataType: 'json', timeout: 15000})
            .fail(function (xhr) {
                if (xhr && xhr.status === 409) {
                    var current = xhr.getResponseHeader(revisionHeader);
                    if (current && /^\d+$/.test(current)) revision = current;
                }
                refuse();
            })
            .done(function (rows, statusText, xhr) {
                var current = xhr.getResponseHeader(revisionHeader);
                if (!current || !/^\d+$/.test(current)) return refuse();
                revision = current;
                if (!Array.isArray(rows) || rows.some(function (row) {
                    return !row || row.id == null || typeof row.formattedName !== 'string';
                })) return refuse();
                var confirmed = update.clear ? rows.length === 0 : rows.some(function (row) {
                    return String(row.id) === update.id;
                }) === update.checked;
                if (!confirmed) return refuse();
                sending = false;
                queue.shift();
                if (queue.length) return sendNext();
                render(rows);
                var action = heldAction;
                heldAction = null;
                if (action) action();
            });
    }

    function enqueue(update) {
        if (failed && (update.clear || (update.id === queue[0].id && !update.checked))) {
            // Clear supersedes the entire pending batch. An uncheck supersedes a
            // rejected check for the same specialist, including a deleted referral.
            if (update.clear) queue = [];
            else queue = queue.filter(function (pending) { return pending.id !== update.id; });
            failed = false;
            status.hidden = true;
        }
        queue.push(update);
        sendNext();
    }

    document.addEventListener('change', function (event) {
        var box = event.target;
        if (box.matches('input[data-referral-id]')) {
            enqueue({method: 'modifyBatch', id: box.dataset.referralId, checked: box.checked});
        }
    });
    document.getElementById('clearReferralLabels').addEventListener('click', function () {
        enqueue({method: 'modifyBatch', clear: true});
    });
    document.getElementById('retryLabelUpdate').addEventListener('click', function () {
        failed = false;
        status.hidden = true;
        sendNext();
    });

    // The shared pager handles submit in the bubble phase. Only replay a confirmed
    // selection, and keep the original submitter (including its page or sort value).
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!queue.length || !(form === search || form.matches('[data-server-page-form]'))) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        var submitter = event.submitter;
        if (!failed) heldAction = function () {
            if (form.isConnected) form.requestSubmit(submitter || undefined);
        };
    }, true);
    document.getElementById('printReferralLabels').addEventListener('click', function () {
        function print() { window.location.assign(list.dataset.printUrl); }
        if (!queue.length) print();
        else if (!failed) heldAction = print;
    });
}());
