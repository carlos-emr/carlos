/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Document description typeahead (autocomplete) utility.
 * Rewritten in plain JavaScript — no Prototype.js or jQuery dependencies.
 *
 * Usage:
 *   1. Include this script and autocomplete.css in your JSP:
 *        <link rel="stylesheet" href="${ctx}/css/autocomplete.css">
 *        <script src="${ctx}/js/documentDescriptionTypeahead.js"></script>
 *
 *   2. Call setupDocDescriptionTypeahead(docId) after the DOM is ready,
 *      where docId matches the suffix in the input id "docDesc_<docId>":
 *        setupDocDescriptionTypeahead('<%= docId %>');
 *
 *   3. For incomingDocs.jsp where the input id is "documentDescription"
 *      (no suffix), pass null or undefined:
 *        setupDocDescriptionTypeahead(null);
 *
 * The autocomplete fetches from:
 *   /ManageDocument.do?method=searchDocumentDescriptions&term=<keyword>
 * and expects: [{"label":"Blood Work Results"}, ...]
 *
 * Styles are provided by css/autocomplete.css (.autocomplete, .ac-item).
 *
 * @since 2026-02-28
 */

'use strict';

/**
 * Attach a live-search document description autocomplete to an input element.
 *
 * @param {string|null} docId - Document ID suffix for the input id "docDesc_<docId>",
 *   or null/undefined to target the "documentDescription" fixed-id input
 *   (used in incomingDocs.jsp).
 */
function setupDocDescriptionTypeahead(docId) {
    const inputId = (docId !== null && docId !== undefined && docId !== '')
        ? ('docDesc_' + docId)
        : 'documentDescription';
    const input = document.getElementById(inputId);
    if (!input) {
        return;
    }

    // Determine the application context path from a global variable set in the JSP
    const contextPath = (typeof ctx !== 'undefined' ? ctx : '') || '';

    // Create and insert the dropdown container immediately after the input
    const dropdown = document.createElement('div');
    dropdown.className = 'autocomplete';
    dropdown.setAttribute('role', 'listbox');
    // Ensure the parent element is a positioning context for the absolute dropdown
    const parent = input.parentNode;
    if (window.getComputedStyle(parent).position === 'static') {
        parent.style.position = 'relative';
    }
    parent.insertBefore(dropdown, input.nextSibling);

    let abortController = null;

    /**
     * Fetch suggestions for the given search term from the server.
     *
     * @param {string} term - The search keyword
     */
    function fetchSuggestions(term) {
        if (abortController) {
            abortController.abort();
        }
        abortController = new AbortController();

        const url = contextPath + '/documentManager/ManageDocument.do?method=searchDocumentDescriptions&term=' +
            encodeURIComponent(term);

        fetch(url, { signal: abortController.signal })
            .then(function(resp) {
                if (!resp.ok) {
                    throw new Error('HTTP ' + resp.status);
                }
                return resp.json();
            })
            .then(function(data) {
                showDropdown(data);
            })
            .catch(function(err) {
                if (err.name !== 'AbortError') {
                    console.warn('documentDescriptionTypeahead fetch error:', err);
                }
            });
    }

    /**
     * Populate and show the dropdown with the given result array.
     *
     * @param {Array<{label: string}>} items - Autocomplete results from the server
     */
    function showDropdown(items) {
        dropdown.innerHTML = '';
        if (!items || items.length === 0) {
            dropdown.style.display = 'none';
            return;
        }
        items.forEach(function(item) {
            const div = document.createElement('div');
            div.className = 'ac-item';
            div.setAttribute('role', 'option');
            div.setAttribute('tabindex', '0');
            // Use textContent to prevent XSS — labels are plain strings from the DB
            div.textContent = item.label;
            div.addEventListener('mousedown', function(e) {
                // Prevent blur from firing before the selection is applied
                e.preventDefault();
                input.value = item.label;
                hideDropdown();
                input.dispatchEvent(new Event('change'));
            });
            div.addEventListener('keydown', function(e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    input.value = item.label;
                    hideDropdown();
                    input.dispatchEvent(new Event('change'));
                    input.focus();
                }
            });
            dropdown.appendChild(div);
        });
        dropdown.style.display = 'block';
    }

    /** Hide and clear the dropdown. */
    function hideDropdown() {
        dropdown.style.display = 'none';
        dropdown.innerHTML = '';
    }

    // Debounced input handler — fetch suggestions 200 ms after the user stops typing
    let debounceTimer = null;
    input.addEventListener('input', function() {
        clearTimeout(debounceTimer);
        const term = input.value.trim();
        if (term.length < 1) {
            hideDropdown();
            return;
        }
        debounceTimer = setTimeout(function() {
            fetchSuggestions(term);
        }, 200);
    });

    // Hide dropdown when focus leaves the input (delayed to allow item click to register)
    input.addEventListener('blur', function() {
        setTimeout(hideDropdown, 150);
    });

    // Keyboard navigation: down-arrow enters the dropdown list, Escape closes it
    input.addEventListener('keydown', function(e) {
        const items = dropdown.querySelectorAll('.ac-item');
        if (!items.length) {
            return;
        }
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            items[0].focus();
        } else if (e.key === 'Escape') {
            hideDropdown();
        }
    });

    // Arrow-key navigation between dropdown items
    dropdown.addEventListener('keydown', function(e) {
        const items = Array.from(dropdown.querySelectorAll('.ac-item'));
        const focused = document.activeElement;
        const idx = items.indexOf(focused);
        if (e.key === 'ArrowDown' && idx < items.length - 1) {
            e.preventDefault();
            items[idx + 1].focus();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (idx > 0) {
                items[idx - 1].focus();
            } else {
                input.focus();
            }
        } else if (e.key === 'Escape') {
            hideDropdown();
            input.focus();
        }
    });
}
