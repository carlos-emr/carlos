/**
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
 * Location List dropdown helpers for the booking screens (WEB-INF/tags/appointment/locationSelect.tag).
 *
 * The dropdown posts locationCode; AppointmentLocationResolver saves the chosen item's id and label.
 * "Not specified" has the value "", a legacy free-text location the value "legacy" (marked
 * data-legacy), and a location deactivated since booking is marked data-inactive.
 */

/**
 * Chooses the active location whose name matches, ignoring case and surrounding spaces, as when an
 * appointment type names its location. "Not specified", a legacy location and an inactive location
 * are never chosen, and the choice is left alone when nothing matches.
 *
 * @param {HTMLSelectElement} select the locationCode dropdown
 * @param {string} name the location's name, or blank
 * @returns {boolean} true if a location was chosen
 */
function selectAppointmentLocationByName(select, name) {
    const wanted = String(name || '').trim().toLowerCase();
    if (!select || !wanted) {
        return false;
    }
    const match = Array.from(select.options).find(function (option) {
        return option.value && !option.hasAttribute('data-inactive') && !option.hasAttribute('data-legacy')
            && option.text.trim().toLowerCase() === wanted;
    });
    if (match) {
        select.value = match.value;
        return true;
    }
    return false;
}

/**
 * Chooses a location by its code, as pasting a copied appointment does. A code the dropdown does not
 * offer (a location deactivated since the copy, or a copied legacy location) falls back to
 * "Not specified", so a paste never leaves the dropdown blank or keeps the target's own legacy text.
 *
 * @param {HTMLSelectElement} select the locationCode dropdown
 * @param {string} code the location code, or blank
 */
function selectAppointmentLocationCode(select, code) {
    if (!select) {
        return;
    }
    const wanted = String(code == null ? '' : code);
    const offered = wanted !== '' && Array.from(select.options).some(function (option) {
        return option.value === wanted && !option.hasAttribute('data-legacy');
    });
    select.value = offered ? wanted : '';
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {selectAppointmentLocationByName, selectAppointmentLocationCode};
}
