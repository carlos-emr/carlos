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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPathDto;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * DP01.11 layout. Names remain literal: deleting characters or shortening a component can
 * change identity or create collisions. Unrepresentable names require review. Publication
 * must separately enforce case/Unicode collision accounting and physical root containment.
 */
public final class CdsExportPathComposer {
    private static final DateTimeFormatter DOB = DateTimeFormatter.ofPattern("ddMMuuuu", Locale.ROOT);

    public CdsExportPathDto compose(CdsExportPatientFileDto patient) {
        Objects.requireNonNull(patient, "patient");
        String patientFirst = name(patient.patientFirstName());
        String patientLast = name(patient.patientLastName());
        String id = patient.senderPatientId();
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9-]{0,19}")) {
            throw new IllegalArgumentException("CDS_EXPORT_SENDER_PATIENT_ID_INVALID");
        }
        if (patient.dateOfBirth() == null || patient.dateOfBirth().getYear() < 1
                || patient.dateOfBirth().getYear() > 9999) {
            throw new IllegalArgumentException("CDS_EXPORT_EXACT_BIRTH_DATE_REQUIRED");
        }
        String folder = physicianFolder(patient.physicianFirstName(), patient.physicianLastName(),
                patient.physicianBillingNumber());
        String file = component(patientFirst + "_" + patientLast + "_" + id + "_"
                + DOB.format(patient.dateOfBirth()) + ".xml");
        if ((folder + "/" + file).getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException("CDS_EXPORT_FILENAME_CAPACITY_REQUIRES_REVIEW");
        }
        return new CdsExportPathDto(folder, file);
    }

    /** Logical archive names are separate from opaque physical staging members. */
    public String physicianFolder(String firstName, String lastName, String billingNumber) {
        if (billingNumber == null || !billingNumber.matches("[A-Za-z0-9]{1,6}")) {
            throw new IllegalArgumentException("CDS_EXPORT_PHYSICIAN_BILLING_NUMBER_REQUIRED");
        }
        return component(name(firstName) + "_" + name(lastName) + "_" + billingNumber);
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.length() > 1024
                || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || !value.equals(value.strip()) || value.endsWith(".")) {
            throw new IllegalArgumentException("CDS_EXPORT_FILENAME_NAME_REQUIRES_REVIEW");
        }
        for (int cp : value.codePoints().toArray()) {
            if (Character.isISOControl(cp) || cp >= 0xd800 && cp <= 0xdfff
                    || cp == 0xfffe || cp == 0xffff || "<>:\"/\\|?*".indexOf(cp) >= 0) {
                throw new IllegalArgumentException("CDS_EXPORT_FILENAME_NAME_REQUIRES_REVIEW");
            }
        }
        return value;
    }

    private static String component(String value) {
        if (value.length() > 4096) {
            throw new IllegalArgumentException("CDS_EXPORT_FILENAME_CAPACITY_REQUIRES_REVIEW");
        }
        try {
            return PathValidationUtils.validatePathComponent(value, "CDS export filename");
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("CDS_EXPORT_FILENAME_NAME_REQUIRES_REVIEW");
        }
    }
}
