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
 * Demographic/Provider autocomplete utility functions.
 * Rewritten in plain JavaScript — no Prototype.js or jQuery dependencies.
 *
 * @since 2006-01-01
 */

'use strict';

const selectedDemos = [];

/**
 * Escape a value for safe insertion into HTML content.
 * @param {*} value - The value to escape
 * @returns {string} HTML-safe string
 */
function escapeHtml(value) {
    var div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

/**
 * Highlights a query substring within a full string by wrapping it in a span element.
 * @param {string} full - The full display string.
 * @param {string} snippet - The query substring to highlight.
 * @param {number} matchindex - The start index of the match in full.
 * @returns {string} HTML string with the match wrapped in a highlight span.
 */
function highlightMatch(full, snippet, matchindex) {
    return escapeHtml(full.substring(0, matchindex)) +
        "<span class='match'>" +
        escapeHtml(full.substring(matchindex, matchindex + snippet.length)) +
        "</span>" +
        escapeHtml(full.substring(matchindex + snippet.length));
}

/**
 * Format a demographic result for display — firstName (DOB) - status.
 * Used by autocomplete sources that return [name, dob, ?, status] arrays.
 *
 * @param {Array} oResultData - Result tuple: [firstName, dob, ?, status]
 * @param {string} sQuery - The current search query
 * @param {string} sResultMatch - The matched result field
 * @returns {string} Formatted HTML display string
 */
function resultFormatter2(oResultData, sQuery, sResultMatch) {
    const query = sQuery.toLowerCase();
    const fname = oResultData[0];
    const dob = oResultData[1];
    const status = oResultData[3];
    const fnameMatchIndex = fname.toLowerCase().indexOf(query);
    const displayfname = fnameMatchIndex > -1
        ? highlightMatch(fname, query, fnameMatchIndex)
        : escapeHtml(fname);
    return displayfname + " (" + escapeHtml(dob) + ") - " + escapeHtml(status);
}

/**
 * Formats a demographic result for display as "firstName lastName".
 *
 * @param {Array} oResultData - Result tuple: [id, firstName, lastName, ...]
 * @param {string} sQuery - The current search query
 * @param {string} sResultMatch - The matched result field
 * @returns {string} Formatted display string
 */
function resultFormatter(oResultData, sQuery, sResultMatch) {
    const query = sQuery.toLowerCase();
    const fname = oResultData[1];
    const lname = oResultData[2];
    const fnameMatchIndex = fname.toLowerCase().indexOf(query);
    const lnameMatchIndex = lname.toLowerCase().indexOf(query);
    const displayfname = fnameMatchIndex > -1
        ? highlightMatch(fname, query, fnameMatchIndex)
        : escapeHtml(fname);
    const displaylname = lnameMatchIndex > -1
        ? highlightMatch(lname, query, lnameMatchIndex)
        : escapeHtml(lname);
    return displayfname + " " + displaylname;
}

/**
 * Formats a demographic result for display — lastName, firstName.
 *
 * @param {Array} oResultData - Result tuple: [id, firstName, lastName, ...]
 * @param {string} sQuery - The current search query
 * @param {string} sResultMatch - The matched result field
 * @returns {string} Formatted HTML display string
 */
function resultFormatter3(oResultData, sQuery, sResultMatch) {
    const query = sQuery.toLowerCase();
    const fname = oResultData[1];
    const lname = oResultData[2];
    const fnameMatchIndex = fname.toLowerCase().indexOf(query);
    const lnameMatchIndex = lname.toLowerCase().indexOf(query);
    const displayfname = fnameMatchIndex > -1
        ? highlightMatch(fname, query, fnameMatchIndex)
        : escapeHtml(fname);
    const displaylname = lnameMatchIndex > -1
        ? highlightMatch(lname, query, lnameMatchIndex)
        : escapeHtml(lname);
    return displaylname + "," + displayfname;
}

/**
 * Enable or disable the Save button based on the current input value.
 *
 * This function retrieves the input and save elements using the provided elementId.
 * It checks if the current input value exists in the selectedDemos array.
 * If the value is valid, the Save button is enabled; otherwise, it is disabled.
 *
 * @param {string} elementId - The suffix identifying the autocomplete and save elements
 */
function checkSave(elementId) {
    const input = document.getElementById('autocompletedemo' + elementId);
    const saveEl = document.getElementById('save' + elementId);
    if (!input || !saveEl) return;
    const curVal = input.value;
    const isCurValValid = selectedDemos.includes(curVal);
    if (isCurValValid) {
        saveEl.removeAttribute('disabled');
    } else {
        saveEl.setAttribute('disabled', 'disabled');
    }
}

/**
 * Remove the parent list item of the clicked provider link element.
 *
 * @param {HTMLElement} th - The clicked element whose parent will be removed
 */
function removeProv(th) {
    const ele = th.parentElement;
    if (ele) {
        ele.remove();
    }
}
