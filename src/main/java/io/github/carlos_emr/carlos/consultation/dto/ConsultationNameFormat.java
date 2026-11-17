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
package io.github.carlos_emr.carlos.consultation.dto;

/**
 * Null-tolerant "Last, First" formatting for the Consultations list: the single implementation
 * behind the consultant/MRP option labels, the applied-filter badge and the list's Consultant
 * column ({@code EctViewConsultationRequestsUtil.formatSpecialistName} delegates here), so a
 * suggestion, its badge and its rows always render the same text.
 *
 * @since 2026-09-30
 */
public final class ConsultationNameFormat {

    private ConsultationNameFormat() {
    }

    /**
     * Formats a person as "Last, First", dropping whichever part is missing so an incomplete
     * record never renders as "Smith, null".
     *
     * @param lastName  last name; may be null or blank
     * @param firstName first name; may be null or blank
     * @return "Last, First", the single part present, or an empty string
     */
    public static String lastCommaFirst(String lastName, String firstName) {
        String last = lastName == null ? "" : lastName.trim();
        String first = firstName == null ? "" : firstName.trim();
        if (last.isEmpty()) {
            return first;
        }
        if (first.isEmpty()) {
            return last;
        }
        return last + ", " + first;
    }
}
