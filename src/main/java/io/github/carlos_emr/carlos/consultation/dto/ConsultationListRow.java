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

/** Scalar consultation row; excludes clinical text and eager entity relationships. */
public record ConsultationListRow(
        Integer id,
        String status,
        Integer demographicId,
        String patientAlias,
        String providerNo,
        Integer serviceId,
        Integer resolvedServiceId,
        String serviceDescription,
        Integer specialistId,
        String specialistLastName,
        String specialistFirstName,
        String mrpNo,
        String mrpLastName,
        String mrpFirstName,
        String urgency,
        String siteName,
        String sendTo,
        java.util.Date referralDate,
        java.util.Date appointmentDate,
        java.util.Date appointmentTime,
        java.util.Date followUpDate,
        Boolean patientWillBook) {
}
