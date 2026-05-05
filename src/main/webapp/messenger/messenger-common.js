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

/**
 * Shared utility functions for the CARLOS EMR messaging module.
 *
 * Extracted from DisplayMessages.jsp, CreateMessage.jsp, ViewMessage.jsp,
 * and SentMessage.jsp to eliminate duplication.
 */

/**
 * Notifies the parent (opener) window to refresh message alert badges,
 * then closes this popup window after a short delay.
 *
 * Falls back to a plain window.close() if the opener is unavailable or
 * does not expose callRefreshTabAlerts.
 */
function BackToCarlos() {
    if (opener && opener.callRefreshTabAlerts) {
        opener.callRefreshTabAlerts("oscar_new_msg");
        setTimeout(function() { window.close(); }, 100);
    } else {
        window.close();
    }
}

/**
 * Opens the demographic search popup for linking a patient to a message.
 *
 * Routes through the privilege-checked `demographic/DemographicLinkMsg`
 * Struts action, which forwards to `/WEB-INF/jsp/messenger/msgSearchDemo.jsp`.
 *
 * @param {string} contextPath - Application context path (e.g. "/carlos")
 * @param {string} keyword     - Search keyword entered by the user
 */
function popupSearchDemo(contextPath, keyword) {
    var vheight = 700;
    var vwidth = 980;
    var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
    var page = contextPath + '/demographic/DemographicLinkMsg?keyword=' + encodeURIComponent(keyword) + '&firstSearch=true';
    var popUp = window.open(page, "msgSearchDemo", windowprops);
    if (popUp != null) {
        if (popUp.opener == null) {
            popUp.opener = self;
        }
        popUp.focus();
    }
}

/**
 * Initializes a demographic keyword autocomplete on messenger pages.
 *
 * Posts to SearchDemographic via XMLHttpRequest so that CSRFGuard's XHR
 * override automatically injects the session token. Renders an inline dropdown
 * of matching patient records beneath the keyword input.
 *
 * Selecting an item:
 *   - Sets keywordInput to the patient's formatted name
 *   - Sets demoNoInput (hidden) to the patient's demographic number
 *   - Optionally sets selectedDemoInput (read-only display) to the formatted name
 *
 * @param {string}           contextPath       - Application context path (e.g. "/carlos")
 * @param {HTMLInputElement} keywordInput      - Visible keyword search text input
 * @param {HTMLInputElement} demoNoInput       - Hidden demographic_no input
 * @param {HTMLInputElement} [selectedDemoInput] - Optional read-only selected-name display input
 */
function initDemographicAutocomplete(contextPath, keywordInput, demoNoInput, selectedDemoInput) {
    var searchUrl = contextPath + '/demographic/SearchDemographic';
    var minLength = 2;
    var currentXhr = null;

    // Wrap the keyword input in a relatively-positioned container so the dropdown
    // can be absolutely positioned directly beneath it.
    var wrapper = document.createElement('div');
    wrapper.style.cssText = 'position:relative; display:block;';
    keywordInput.parentNode.insertBefore(wrapper, keywordInput);
    wrapper.appendChild(keywordInput);

    var dropdown = document.createElement('ul');
    dropdown.className = 'demographic-autocomplete-list';
    dropdown.style.cssText = 'position:absolute; z-index:9999; background:#fff;'
        + ' border:1px solid #ced4da; border-radius:0 0 .25rem .25rem; margin:0; padding:0;'
        + ' list-style:none; width:100%; max-height:220px; overflow-y:auto; display:none;'
        + ' box-shadow:0 4px 8px rgba(0,0,0,.15);';
    wrapper.appendChild(dropdown);

    function closeDropdown() {
        dropdown.style.display = 'none';
        dropdown.innerHTML = '';
    }

    function selectItem(item) {
        var name = item.formattedName || item.label || '';
        keywordInput.value = name;
        demoNoInput.value = item.value || item.demographicNo || '';
        if (selectedDemoInput) {
            selectedDemoInput.value = name;
        }
        closeDropdown();
    }

    function renderItems(items) {
        dropdown.innerHTML = '';
        if (!items || items.length === 0) {
            closeDropdown();
            return;
        }
        items.forEach(function (item) {
            var li = document.createElement('li');
            li.style.cssText = 'padding:6px 10px; cursor:pointer; border-bottom:1px solid #f0f0f0; font-size:0.9em;';
            var bold = document.createElement('b');
            bold.textContent = item.label || item.formattedName || '';
            li.appendChild(bold);
            if (item.provider) {
                li.appendChild(document.createElement('br'));
                li.appendChild(document.createTextNode(item.provider));
            }
            li.addEventListener('mouseover', function () { li.style.backgroundColor = '#e9ecef'; });
            li.addEventListener('mouseout', function () { li.style.backgroundColor = ''; });
            // mousedown fires before blur so the item click is not swallowed by the
            // input losing focus first.
            li.addEventListener('mousedown', function (e) {
                e.preventDefault();
                selectItem(item);
            });
            dropdown.appendChild(li);
        });
        dropdown.style.display = 'block';
    }

    keywordInput.addEventListener('input', function () {
        var term = keywordInput.value;
        // Clear any previously selected demographic when user edits the keyword
        demoNoInput.value = '';
        if (selectedDemoInput) {
            selectedDemoInput.value = '';
        }
        if (term.length < minLength) {
            closeDropdown();
            return;
        }
        if (currentXhr) {
            currentXhr.abort();
        }
        var xhr = new XMLHttpRequest();
        currentXhr = xhr;
        var searchUrlWithQuery = searchUrl + '?jqueryJSON=true&activeOnly=true&term=' + encodeURIComponent(term);
        xhr.open('GET', searchUrlWithQuery, true);
        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    renderItems(JSON.parse(xhr.responseText));
                } catch (e) {
                    closeDropdown();
                }
            } else {
                closeDropdown();
            }
        };
        xhr.onerror = function () { closeDropdown(); };
        xhr.send(null);
    });

    // Delay closing so a mousedown on a list item fires before input blur closes the dropdown.
    keywordInput.addEventListener('blur', function () {
        setTimeout(closeDropdown, 200);
    });

    // Close if user clicks anywhere outside the autocomplete wrapper.
    document.addEventListener('click', function (e) {
        if (!wrapper.contains(e.target)) {
            closeDropdown();
        }
    });
}