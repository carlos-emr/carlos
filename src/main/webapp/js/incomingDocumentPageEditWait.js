/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
(function () {
    'use strict';
    var form = document.getElementById('incoming-page-edit-wait');
    if (!form || form.dataset.accepted !== 'false' || form.dataset.bound || form.method.toLowerCase() !== 'post') return;
    var action = new URL(form.action, window.location.href);
    var context = window.location.pathname.split('/documentManager/')[0];
    if (action.origin !== window.location.origin || action.pathname !== context + '/documentManager/ViewIncomingDocs'
            || action.username || action.password || action.search || action.hash) return;
    var fields = new FormData(form);
    function single(name) { var values = fields.getAll(name); return values.length === 1 ? values[0] : ''; }
    if (!/^[a-f0-9]{64}$/.test(single('sourceRevision')) || !single('CSRF-TOKEN')
            || !['Rotate90', 'Rotate180', 'RotateM90', 'RotateAll90', 'RotateAll180', 'RotateAllM90', 'DeletePage', 'DeletePDF', 'ExtractPagePDF'].includes(single('pdfAction'))) return;
    form.dataset.bound = 'true';
    var inactive = false, sent = false;
    // Normal navigation performs exactly one POST. Only another explicit server admission
    // refusal can create another wait page; network/HTML/unknown outcomes never replay.
    var jitter;
    if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
        var random = new Uint32Array(1); window.crypto.getRandomValues(random); jitter = random[0] % 251;
    } else { jitter = Date.now() % 251; }
    var timer = window.setTimeout(function () {
        if (inactive || sent) return;
        sent = true;
        // Preserve the server-rendered snapshot, even if another local script edits a field.
        Array.from(form.elements).forEach(function (element) { element.disabled = true; });
        fields.forEach(function (value, name) {
            var input = document.createElement('input');
            input.type = 'hidden'; input.name = name; input.value = String(value); form.appendChild(input);
        });
        form.action = action.href;
        form.method = 'post';
        form.target = '_self';
        HTMLFormElement.prototype.submit.call(form);
    }, 1000 + jitter);
    function cancel() { inactive = true; window.clearTimeout(timer); }
    window.addEventListener('pagehide', cancel);
    var refresh = document.getElementById('incoming-page-edit-cancel');
    if (refresh) refresh.addEventListener('click', cancel);
})();
