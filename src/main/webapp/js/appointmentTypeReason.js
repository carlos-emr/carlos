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

/**
 * Replaces only the previous type's autofill, preserving reason text entered by the user.
 * Empty types remove the old autofill without introducing a separator.
 * Throws RangeError when combining text exceeds the appointment reason limit; callers must
 * keep the previous selection/text and report the error rather than silently truncate it.
 */
function appointmentTypeReason(current = '', previous = '', next = '') {
    let manual = current;
    if (previous && current === previous) manual = '';
    else if (previous && current.startsWith(previous + ' -- ')) manual = current.slice(previous.length + 4);
    const combined = next && manual ? next + ' -- ' + manual : next || manual;
    if (combined.length > 80) throw new RangeError('Appointment reason exceeds 80 characters');
    return combined;
}

if (typeof module !== 'undefined' && module.exports) module.exports = appointmentTypeReason;
