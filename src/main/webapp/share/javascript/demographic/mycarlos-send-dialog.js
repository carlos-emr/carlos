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

    // The dialogs held open by a running send.
    const holding = new Set();

    function wire(dialog) {
        if (dialog.mycarlosDialog) return dialog.mycarlosDialog;
        const close = dialog.querySelector('[data-mycarlos-part="close"]');
        const status = dialog.querySelector('[data-mycarlos-part="message"]');
        let sending = false;
        const held = () => sending && !CLOSE_WHILE_SENDING;
        const sender = window.CarlosMyCarlosSend.mount(dialog, {
            persistInUrl: false,
            onBusyChange: (busy, operation) => {
                if (operation !== 'send') return;
                sending = busy;
                if (held()) holding.add(dialog); else holding.delete(dialog);
                if (close) close.disabled = held();
                // The Send (or Retry) button just pressed is disabled while the send runs, and a
                // browser then drops the focus out of the dialog. The status line, which says what
                // is happening and then how it went, takes the focus instead, so the keyboard and
                // Esc stay with the dialog.
                if (busy && status) {
                    status.tabIndex = -1;
                    status.focus();
                }
            }
        });
        if (close) close.addEventListener('click', () => { if (!held()) dialog.close(); });
        // Esc: the close request is refused while a send runs (see also the page-wide guard below).
        dialog.addEventListener('keydown', event => { if (event.key === 'Escape' && held()) event.preventDefault(); });
        dialog.addEventListener('cancel', event => { if (held()) event.preventDefault(); });
        dialog.addEventListener('close', () => {
            holding.delete(dialog);
            const opener = dialog.mycarlosOpener;
            dialog.mycarlosOpener = null;
            // The button that opened it, or, if the page redrew the document meanwhile, its Send
            // button now.
            const target = opener && opener.isConnected ? opener
                : document.getElementById('mycarlos-document-send-' + dialog.dataset.document);
            if (target) target.focus();
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

    // Esc while a send runs, wherever the focus is. A browser may stop honouring a dialog's refused
    // cancel when Esc is pressed again without other input, so the key itself is refused here, before
    // it can become a close request. A dialog the page has removed (an inbox redraw) holds nothing.
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        if (Array.from(holding).some(dialog => dialog.open && dialog.isConnected)) event.preventDefault();
    }, true);

    document.addEventListener('click', event => {
        // A Ctrl, Cmd, Shift or Alt click, or another mouse button, opens the standalone page, as any
        // link does.
        if (event.defaultPrevented || (event.button !== undefined && event.button !== 0)
                || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const opener = event.target && event.target.closest ? event.target.closest('[data-mycarlos-dialog]') : null;
        if (!opener || !window.CarlosMyCarlosSend) return;
        const dialog = document.getElementById(opener.dataset.mycarlosDialog);
        // No dialog here, a browser without one, or a dialog its script cannot wire (a part missing):
        // the link opens the standalone page.
        if (!dialog || typeof dialog.showModal !== 'function' || !wire(dialog).sender) return;
        event.preventDefault();
        open(dialog, opener);
    });

    window.CarlosMyCarlosSendDialog = {open, CLOSE_WHILE_SENDING};
}());
