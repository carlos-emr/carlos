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
 * One specialist offered by the Consultant type-ahead on the Consultations list page.
 *
 * <p>Projection of {@code ProfessionalSpecialist} limited to what the suggestion list shows:
 * specialist directory data only, never patient data.</p>
 *
 * @param id        {@code professionalSpecialists.specId}
 * @param lastName  specialist last name; may be null on legacy rows
 * @param firstName specialist first name; may be null on legacy rows
 * @since 2026-09-30
 */
public record ConsultantOptionDto(Integer id, String lastName, String firstName) {

    /**
     * @return "Last, First", or whichever part is present, never containing the text "null"
     */
    public String label() {
        return ConsultationNameFormat.lastCommaFirst(lastName, firstName);
    }
}
