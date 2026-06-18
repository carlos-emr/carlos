/**
 * faxRecipientAutocomplete.js
 *
 * Provides a vanilla-JS autocomplete for fax recipient name/fax fields backed by the
 * /fax/SearchFaxRecipient endpoint.  Results combine pharmacies and specialists; each
 * entry carries a Bootstrap 5 badge indicating its type:
 *
 *  - PHARMACY  → badge "pharmacy" (bg-info text-dark)
 *  - SPECIALIST → badge with the service description (bg-secondary)
 *
 * Usage:
 *   setupFaxRecipientAutocomplete({
 *     contextPath : '/carlos',
 *     nameInputId : 'recipient_name_input_id',
 *     faxInputId  : 'recipient_fax_input_id',
 *     dropdownId  : 'recipient_dropdown_id',   // container <div class="fax-ac-dropdown">
 *   });
 *
 * The caller is responsible for placing a positioned <div id="…" class="fax-ac-dropdown">
 * immediately after the name <input>.  A minimal CSS block is injected automatically the
 * first time this module runs.
 *
 * CSRF: the endpoint is GET-only and does not require a CSRF token.
 *
 * @since 2026-06
 */

(function () {
    'use strict';

    var _styleInjected = false;

    function injectStyles() {
        if (_styleInjected) return;
        _styleInjected = true;
        var style = document.createElement('style');
        style.textContent = [
            '.fax-ac-dropdown {',
            '  position: absolute;',
            '  z-index: 9999;',
            '  background: #fff;',
            '  border: 1px solid #ced4da;',
            '  border-radius: 0 0 .25rem .25rem;',
            '  max-height: 300px;',
            '  overflow-y: auto;',
            '  width: 100%;',
            '  box-shadow: 0 4px 8px rgba(0,0,0,.12);',
            '  display: none;',
            '}',
            '.fax-ac-item {',
            '  padding: .45rem .75rem;',
            '  cursor: pointer;',
            '  display: block;',
            '  font-size: .875rem;',
            '  border-bottom: 1px solid #f0f0f0;',
            '}',
            '.fax-ac-item:last-child { border-bottom: none; }',
            '.fax-ac-item:hover, .fax-ac-item.fax-ac-active {',
            '  background-color: #e9ecef;',
            '}',
            '.fax-ac-name { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-weight: 500; }',
            '.fax-ac-meta { display: flex; align-items: center; gap: .4rem; margin-top: .2rem; }',
            '.fax-ac-fax  { color: #6c757d; font-size: .8rem; white-space: nowrap; }'
        ].join('\n');
        document.head.appendChild(style);
    }

    /**
     * Returns a Bootstrap 5 badge element for the given item type and badge text.
     *
     * @param {string} type  - "PHARMACY" or "SPECIALIST"
     * @param {string} badge - badge label text
     * @returns {HTMLElement}
     */
    function makeBadge(type, badge) {
        var span = document.createElement('span');
        span.className = type === 'PHARMACY'
            ? 'badge bg-info text-dark'
            : 'badge bg-secondary';
        span.textContent = badge || (type === 'PHARMACY' ? 'pharmacy' : 'Specialist');
        return span;
    }

    /**
     * Attaches fax-recipient autocomplete behaviour to a name+fax input pair.
     *
     * @param {Object} opts
     * @param {string} opts.contextPath  - servlet context path (e.g. "/carlos")
     * @param {string} opts.nameInputId  - id of the recipient name <input>
     * @param {string} opts.faxInputId   - id of the recipient fax <input>
     * @param {string} opts.dropdownId   - id of the <div class="fax-ac-dropdown"> container
     */
    function setupFaxRecipientAutocomplete(opts) {
        injectStyles();

        var nameEl = document.getElementById(opts.nameInputId);
        var faxEl  = document.getElementById(opts.faxInputId);
        var dropEl = document.getElementById(opts.dropdownId);

        if (!nameEl || !faxEl || !dropEl) {
            console.warn('faxRecipientAutocomplete: one or more elements not found', opts);
            return;
        }

        nameEl.setAttribute('autocomplete', 'off');

        var abortCtrl = null;
        var activeIdx = -1;

        function hideDropdown() {
            dropEl.style.display = 'none';
            dropEl.innerHTML = '';
            activeIdx = -1;
        }

        function setActive(idx) {
            var items = dropEl.querySelectorAll('.fax-ac-item');
            items.forEach(function (el, i) {
                el.classList.toggle('fax-ac-active', i === idx);
            });
            activeIdx = idx;
        }

        function selectItem(item) {
            if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
            nameEl.value = item.name;
            faxEl.value  = item.fax;
            hideDropdown();
            // Dispatch change so any surrounding form listeners can react
            nameEl.dispatchEvent(new Event('change', { bubbles: true }));
            faxEl.dispatchEvent(new Event('change', { bubbles: true }));
        }

        function renderResults(items) {
            dropEl.innerHTML = '';
            activeIdx = -1;

            if (!items || items.length === 0) {
                hideDropdown();
                return;
            }

            items.forEach(function (item) {
                var row = document.createElement('div');
                row.className = 'fax-ac-item';

                // First line: full-width name
                var nameSpan = document.createElement('span');
                nameSpan.className = 'fax-ac-name';
                nameSpan.textContent = item.name;

                // Second line: badge + fax number
                var metaRow = document.createElement('div');
                metaRow.className = 'fax-ac-meta';

                var faxSpan = document.createElement('span');
                faxSpan.className = 'fax-ac-fax';
                faxSpan.textContent = item.fax;

                metaRow.appendChild(makeBadge(item.type, item.badge));
                metaRow.appendChild(faxSpan);

                row.appendChild(nameSpan);
                row.appendChild(metaRow);

                row.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    selectItem(item);
                });

                dropEl.appendChild(row);
            });

            dropEl.style.display = 'block';
        }

        nameEl.addEventListener('input', function () {
            var term = nameEl.value.trim();
            if (term.length < 2) {
                if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
                hideDropdown();
                return;
            }

            if (abortCtrl) { abortCtrl.abort(); }
            abortCtrl = new AbortController();

            fetch(opts.contextPath + '/fax/SearchFaxRecipient?term=' + encodeURIComponent(term), {
                method: 'GET',
                credentials: 'same-origin',
                signal: abortCtrl.signal
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error(
                            'HTTP ' + response.status + ' ' + response.statusText
                        );
                    }
                    return response.json();
                })
                .then(renderResults)
                .catch(function (err) {
                    if (err.name === 'AbortError') {
                        return; // request was intentionally aborted
                    }
                    // Clear stale suggestions when fetch fails
                    hideDropdown();
                    console.error('Error searching fax recipients:', err);
                });
        });

        nameEl.addEventListener('keydown', function (e) {
            var items = dropEl.querySelectorAll('.fax-ac-item');
            if (dropEl.style.display === 'none' || items.length === 0) return;

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActive(Math.min(activeIdx + 1, items.length - 1));
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActive(Math.max(activeIdx - 1, 0));
            } else if (e.key === 'Enter' && activeIdx >= 0) {
                e.preventDefault();
                items[activeIdx].dispatchEvent(new MouseEvent('mousedown'));
            } else if (e.key === 'Escape') {
                hideDropdown();
            }
        });

        nameEl.addEventListener('blur', function () {
            // Short delay so mousedown on an item fires first
            setTimeout(hideDropdown, 200);
        });
    }

    // Expose to global scope for use in inline JSP scripts
    window.setupFaxRecipientAutocomplete = setupFaxRecipientAutocomplete;
}());
