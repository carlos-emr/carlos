/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
/*
 * Opens a document's "Send to myCarlos" dialog (mycarlosSendDialog.jspf) from its Send button, an
 * <a data-mycarlos-dialog="<dialog id>"> that still opens the standalone page where there is no
 * script or no <dialog> support. One click handler on the document serves every document on the
 * page, including documents the inbox inserts later without running their scripts, which is why the
 * inbox pages load this file (and mycarlos-send.js) themselves.
 *
 * Each open reads the delivery state again; the dialog keeps its operation id while the page lives.
 * A native modal <dialog> traps focus, sits above the document viewer and returns focus to the
 * button when it closes.
 */
(function () {
    'use strict';
    if (window.CarlosMyCarlosSendDialog) return;

    // Ben, 6 Nov 2026: while a send runs, Close and Esc wait until its result (sent, or what went
    // wrong) shows. Set to true to let the dialog close at once; reopening reads the state again.
    const CLOSE_WHILE_SENDING = false;

    function wire(dialog) {
        if (dialog.mycarlosDialog) return dialog.mycarlosDialog;
        const close = dialog.querySelector('[data-mycarlos-part="close"]');
        let sending = false;
        const held = () => sending && !CLOSE_WHILE_SENDING;
        const sender = window.CarlosMyCarlosSend.mount(dialog, {
            persistInUrl: false,
            onBusyChange: (busy, operation) => {
                if (operation !== 'send') return;
                sending = busy;
                if (close) close.disabled = held();
            }
        });
        if (close) close.addEventListener('click', () => { if (!held()) dialog.close(); });
        // Esc: the close request is refused while a send runs.
        dialog.addEventListener('keydown', event => { if (event.key === 'Escape' && held()) event.preventDefault(); });
        dialog.addEventListener('cancel', event => { if (held()) event.preventDefault(); });
        dialog.addEventListener('close', () => {
            const opener = dialog.mycarlosOpener;
            dialog.mycarlosOpener = null;
            if (opener && opener.isConnected) opener.focus();
        });
        dialog.mycarlosDialog = {sender, held};
        return dialog.mycarlosDialog;
    }

    function open(dialog, opener) {
        const wired = wire(dialog);
        if (!wired.sender) return false;
        dialog.mycarlosOpener = opener;
        if (!dialog.open) dialog.showModal();
        const close = dialog.querySelector('[data-mycarlos-part="close"]');
        Promise.resolve(wired.sender.read()).then(() => {
            if (!dialog.open) return;
            const target = wired.sender.focusTarget() || close;
            if (target) target.focus();
        });
        return true;
    }

    document.addEventListener('click', event => {
        const opener = event.target && event.target.closest ? event.target.closest('[data-mycarlos-dialog]') : null;
        if (!opener || !window.CarlosMyCarlosSend) return;
        const dialog = document.getElementById(opener.dataset.mycarlosDialog);
        // No dialog here, or a browser without one: the link opens the standalone page.
        if (!dialog || typeof dialog.showModal !== 'function') return;
        event.preventDefault();
        open(dialog, opener);
    });

    window.CarlosMyCarlosSendDialog = {open, CLOSE_WHILE_SENDING};
}());
