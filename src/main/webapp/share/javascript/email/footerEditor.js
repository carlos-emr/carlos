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

/*
 * The shared "Edit footer" window (WEB-INF/jsp/email/footerEditorModal.jspf, issue #3981).
 *
 * Everything shown is cleaned first by DOMPurify against the footer's allow-list (bold, italic,
 * links to https: or mailto:, line breaks), the same one the server applies with jsoup in
 * EmailFooterHtml. The server cleans again before saving or sending; this cleaning only keeps the
 * preview honest and the posted value tidy. Without DOMPurify nothing is ever put into the page as
 * HTML: each footer card shows its text only and its Edit footer button is disabled, so the footer
 * is still sent as it is but cannot be edited or erased by mistake.
 */
(function () {
    'use strict';

    var ALLOWED = {
        ALLOWED_TAGS: ['b', 'strong', 'i', 'em', 'br', 'p', 'div', 'a'],
        ALLOWED_ATTR: ['href'],
        // DOMPurify keeps data-* and aria-* attributes unless told not to; the server keeps neither.
        ALLOW_DATA_ATTR: false,
        ALLOW_ARIA_ATTR: false,
        ALLOWED_URI_REGEXP: /^(?:https:|mailto:)/i
    };
    // What an editor leaves after the last line, as the server strips it: a break, or an empty
    // line (Chrome writes <div><br></div>).
    var EMPTY_TAILS = ['<br>', '<div><br></div>', '<p><br></p>', '<div></div>', '<p></p>'];

    // A loop, not one pattern, as on the server: linear however long the input.
    function stripEmptyTail(html) {
        var end = html.length;
        for (;;) {
            while (end > 0 && /\s/.test(html.charAt(end - 1))) {
                end--;
            }
            var tail = null;
            for (var i = 0; i < EMPTY_TAILS.length && tail === null; i++) {
                var candidate = EMPTY_TAILS[i];
                if (end >= candidate.length && html.substring(end - candidate.length, end) === candidate) {
                    tail = candidate;
                }
            }
            if (tail === null) {
                return html.substring(0, end).trim();
            }
            end -= tail.length;
        }
    }
    var BLOCKS = {P: true, DIV: true};

    // As on the server: a link address with control or invisible formatting characters loses its
    // address, so the address a reader sees is the one the link goes to.
    var HIDDEN_CHARACTERS = /[\p{Cc}\p{Cf}\uFFFD]/u;

    function dropHiddenCharacterAddresses(node, data) {
        if (data.attrName === 'href' && HIDDEN_CHARACTERS.test(data.attrValue)) {
            data.keepAttr = false;
        }
    }

    function clean(html) {
        if (!window.DOMPurify || !html) {
            return '';
        }
        // Added for this call only, so any other use of DOMPurify on the page is unaffected.
        window.DOMPurify.addHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);
        try {
            return stripEmptyTail(window.DOMPurify.sanitize(html, ALLOWED));
        } finally {
            window.DOMPurify.removeHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);
        }
    }

    // The plain-text version, as EmailFooterHtml.toPlainText builds it on the server.
    function toText(html) {
        var holder = document.createElement('div');
        holder.innerHTML = clean(html); // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method -- DOMPurify-sanitized just above
        var out = '';
        function startLine() {
            if (out.length && out.charAt(out.length - 1) !== '\n') {
                out += '\n';
            }
        }
        function walk(node) {
            node.childNodes.forEach(function (child) {
                if (child.nodeType === Node.TEXT_NODE) {
                    out += child.textContent.replace(/\s+/g, ' ').replace(/\u00A0/g, ' ');
                } else if (child.nodeName === 'BR') {
                    out += '\n';
                } else {
                    if (BLOCKS[child.nodeName]) {
                        startLine();
                    }
                    walk(child);
                    if (child.nodeName === 'A') {
                        var href = child.getAttribute('href') || '';
                        var address = /^mailto:/i.test(href) ? href.substring(7) : href;
                        if (address && child.textContent.trim() !== address) {
                            out += ' <' + address + '>';
                        }
                    } else if (BLOCKS[child.nodeName]) {
                        startLine();
                    }
                }
            });
        }
        walk(holder);
        return out.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
    }

    function show(element, visible) {
        element.classList.toggle('d-none', !visible);
    }

    // The footer's text without DOMPurify: its tags dropped, nothing parsed as HTML. Callers put the
    // result into textContent only (an entity such as &amp; then shows as written).
    function inertText(html) {
        return String(html || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function renderPreview(target, preview) {
        if (!target || !preview) {
            return;
        }
        if (!window.DOMPurify) {
            var text = inertText(target.value);
            preview.textContent = text || preview.getAttribute('data-empty-text') || '';
            preview.classList.toggle('text-muted', !text);
            preview.classList.toggle('fst-italic', !text);
            return;
        }
        var html = clean(target.value);
        if (html) {
            preview.innerHTML = html; // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method -- DOMPurify-sanitized footer
            preview.classList.remove('text-muted', 'fst-italic');
        } else {
            preview.textContent = preview.getAttribute('data-empty-text') || '';
            preview.classList.add('text-muted', 'fst-italic');
        }
    }

    function init(modal) {
        var editor = modal.querySelector('#footerEditorText');
        var count = modal.querySelector('#footerEditorCount');
        var tooLong = modal.querySelector('#footerEditorTooLong');
        var tooMuchFormatting = modal.querySelector('#footerEditorTooMuchFormatting');
        var apply = modal.querySelector('#footerEditorApply');
        var previewFooter = modal.querySelector('#footerEditorPreviewFooter');
        var previewText = modal.querySelector('#footerEditorPreviewText');
        var previewHtml = modal.querySelector('#footerEditorPreviewHtml');
        var previewLogoTemplate = modal.querySelector('#footerEditorPreviewLogoTemplate');
        var previewLogo = null;
        var logoNote = modal.querySelector('#footerEditorLogoNote');
        var linkRow = modal.querySelector('#footerEditorLinkRow');
        var linkAddress = modal.querySelector('#footerEditorLinkAddress');
        var linkError = modal.querySelector('#footerEditorLinkError');
        var maxLength = parseInt(modal.getAttribute('data-max-length'), 10) || 2000;
        // The server also caps the formatting itself; many links can reach it before the text does.
        var maxHtmlLength = parseInt(modal.getAttribute('data-max-html-length'), 10) || 10000;
        var target = null;
        var targetPreview = null;
        var savedRange = null;
        var logoLoaded = false;

        // The clinic logo shows only when one is set: the address answers 404 otherwise. It is
        // fetched when the window first opens, not with every page: the image waits in a
        // template, where nothing loads, until it is moved into the preview.
        function loadLogo() {
            var source = previewLogoTemplate ? previewLogoTemplate.content.firstElementChild : null;
            if (previewLogo || !source) {
                return;
            }
            previewLogo = source.cloneNode(true);
            // Lazy only to keep Firefox from fetching it early; it stays hidden until it loads, and a
            // hidden lazy image would never load.
            previewLogo.loading = 'eager';
            previewLogo.addEventListener('load', function () {
                logoLoaded = true;
                show(logoNote, true);
                refresh();
            });
            previewLogo.addEventListener('error', function () {
                logoLoaded = false;
                show(previewLogo, false);
                show(logoNote, false);
            });
            previewLogoTemplate.replaceWith(previewLogo);
        }

        function refresh() {
            var html = clean(editor.innerHTML);
            previewFooter.innerHTML = html; // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method -- DOMPurify-sanitized footer
            var text = toText(html);
            previewText.textContent = text;
            // The logo travels only with a footer, as the server sends it.
            if (previewLogo) {
                show(previewLogo, logoLoaded && html !== '');
            }
            count.textContent = text.length;
            var overText = text.length > maxLength;
            var overHtml = !overText && html.length > maxHtmlLength;
            var over = overText || overHtml;
            show(tooLong, overText);
            show(tooMuchFormatting, overHtml);
            apply.disabled = over;
        }

        function rememberSelection() {
            var selection = window.getSelection();
            if (selection.rangeCount && editor.contains(selection.anchorNode)) {
                savedRange = selection.getRangeAt(0).cloneRange();
            }
        }

        function restoreSelection() {
            editor.focus();
            if (savedRange) {
                var selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(savedRange);
            }
        }

        editor.addEventListener('input', refresh);
        editor.addEventListener('keyup', rememberSelection);
        editor.addEventListener('mouseup', rememberSelection);
        // Pasted text keeps no formatting: whatever another program put on the clipboard as HTML.
        editor.addEventListener('paste', function (event) {
            event.preventDefault();
            var text = (event.clipboardData || window.clipboardData).getData('text/plain');
            document.execCommand('insertText', false, text);
        });
        editor.addEventListener('drop', function (event) {
            event.preventDefault();
        });

        modal.querySelectorAll('[data-footer-command]').forEach(function (button) {
            button.addEventListener('mousedown', function (event) {
                // Keep the selection in the editor while the button is pressed.
                event.preventDefault();
            });
            button.addEventListener('click', function () {
                var command = button.getAttribute('data-footer-command');
                rememberSelection();
                if (command === 'link') {
                    show(linkRow, true);
                    show(linkError, false);
                    linkAddress.value = '';
                    linkAddress.focus();
                    return;
                }
                restoreSelection();
                document.execCommand(command, false, null);
                refresh();
            });
        });

        function addLink() {
            var address = linkAddress.value.trim();
            if (address && !/^[a-z][a-z0-9+.-]*:/i.test(address)) {
                address = (address.indexOf('@') > 0 && address.indexOf('/') < 0 ? 'mailto:' : 'https://') + address;
            }
            // Hidden characters (often carried over when copying) would be dropped on save, so the
            // address is refused here with the same message instead of losing its link later.
            if (!/^(?:https:\/\/[^\s]+|mailto:[^\s@]+@[^\s@]+)$/i.test(address) || HIDDEN_CHARACTERS.test(address)) {
                show(linkError, true);
                return;
            }
            show(linkError, false);
            show(linkRow, false);
            restoreSelection();
            var selection = window.getSelection();
            if (selection.isCollapsed) {
                // No text selected: the address itself becomes the link's text.
                document.execCommand('insertText', false, address.replace(/^mailto:/i, ''));
                var range = selection.getRangeAt(0);
                range.setStart(range.startContainer, Math.max(0, range.startOffset - address.replace(/^mailto:/i, '').length));
                selection.removeAllRanges();
                selection.addRange(range);
            }
            document.execCommand('createLink', false, address);
            refresh();
        }
        modal.querySelector('#footerEditorLinkApply').addEventListener('click', addLink);
        linkAddress.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                addLink();
            }
        });

        modal.querySelectorAll('[data-footer-preview]').forEach(function (button) {
            button.addEventListener('click', function () {
                var formatted = button.getAttribute('data-footer-preview') === 'html';
                show(previewHtml, formatted);
                show(previewText, !formatted);
                modal.querySelectorAll('[data-footer-preview]').forEach(function (other) {
                    var active = other === button;
                    other.classList.toggle('active', active);
                    other.setAttribute('aria-pressed', active ? 'true' : 'false');
                });
            });
        });

        modal.addEventListener('show.bs.modal', function (event) {
            var opener = event.relatedTarget;
            target = opener ? document.getElementById(opener.getAttribute('data-footer-editor-target')) : null;
            targetPreview = opener ? document.getElementById(opener.getAttribute('data-footer-editor-preview')) : null;
            editor.innerHTML = clean(target ? target.value : ''); // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method -- DOMPurify-sanitized footer
            savedRange = null;
            show(linkRow, false);
            show(linkError, false);
            refresh();
            // Last, so the optional logo can never stop the editor from opening.
            loadLogo();
        });
        modal.addEventListener('shown.bs.modal', function () {
            editor.focus();
        });

        apply.addEventListener('click', function () {
            if (target) {
                target.value = clean(editor.innerHTML);
                renderPreview(target, targetPreview);
            }
            window.bootstrap.Modal.getOrCreateInstance(modal).hide();
        });
    }

    // Footer previews are pictures of the footer: a link in one, clicked or reached with the
    // keyboard, must not navigate away from an unsent email.
    document.addEventListener('click', function (event) {
        if (event.target.closest && event.target.closest('.footer-editor-mail a')) {
            event.preventDefault();
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
        var modal = document.getElementById('footerEditorModal');
        if (!window.DOMPurify) {
            // Fail visibly rather than show an empty editor whose Apply would erase the footer.
            var message = modal ? modal.getAttribute('data-unavailable-text') : '';
            document.querySelectorAll('[data-footer-editor-target]').forEach(function (opener) {
                opener.disabled = true;
                var preview = document.getElementById(opener.getAttribute('data-footer-editor-preview'));
                if (message && preview) {
                    var note = document.createElement('div');
                    note.className = 'form-text text-danger';
                    note.textContent = message;
                    preview.after(note);
                }
            });
        } else if (modal) {
            init(modal);
        }
        // Footers a page only shows (the clinic footer, a previous footer) carry their HTML in
        // data-footer-html and are drawn here, cleaned the same way: never written into the page raw.
        document.querySelectorAll('[data-footer-html]').forEach(function (holder) {
            var source = holder.getAttribute('data-footer-html');
            var html = window.DOMPurify ? clean(source) : '';
            if (html) {
                holder.innerHTML = html; // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method -- DOMPurify-sanitized footer
            } else {
                holder.textContent = (window.DOMPurify ? '' : inertText(source)) || holder.getAttribute('data-empty-text') || '';
            }
        });
        // Each footer on the page shows its current value, cleaned, until the window changes it.
        document.querySelectorAll('[data-footer-editor-target]').forEach(function (opener) {
            renderPreview(document.getElementById(opener.getAttribute('data-footer-editor-target')),
                document.getElementById(opener.getAttribute('data-footer-editor-preview')));
        });
    });
})();
