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
 * Scalar specialist directory row; excludes entity associations. String components
 * are raw database values, so nullable legacy fields and surrounding whitespace are retained.
 *
 * @param id Integer the persisted specialist ID, non-null
 * @param firstName String the given name, possibly null
 * @param lastName String the family name, possibly null
 * @param professionalLetters String the professional credentials, possibly null
 * @param streetAddress String the unnormalized mailing address, possibly null
 * @param phoneNumber String the contact phone number, possibly null
 * @param faxNumber String the contact fax number, possibly null
 * @param webSite String the website address, possibly null
 * @param emailAddress String the email address, possibly null
 * @param specialtyType String the specialty description, possibly null
 */
public record SpecialistListRow(
        Integer id,
        String firstName,
        String lastName,
        String professionalLetters,
        String streetAddress,
        String phoneNumber,
        String faxNumber,
        String webSite,
        String emailAddress,
        String specialtyType) {
}
