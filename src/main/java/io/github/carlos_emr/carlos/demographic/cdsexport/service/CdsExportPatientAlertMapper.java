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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * The patient alert of the demographic forms ({@code demographiccust.cust3}, the "Alert" box of
 * add-form-clinical.jsp and edit-form-clinical.jsp) as one AlertsAndSpecialNeeds occurrence.
 *
 * <p>CDS 5.2 AlertsAndSpecialNeeds/AlertDescription ({@code text1K}, whitespace preserved) is the
 * element that means the same thing; the 255-character column always fits it, and a carriage
 * return is written as a character reference, so the text travels exactly. The CARLOS importer
 * files the occurrence as a Reminders note. The occurrence exists only when AlertsAndSpecialNeeds
 * is selected (the alert column is captured only then) and the alert is populated; text that no
 * XML document can hold is genuinely malformed and still holds the chart.
 */
public final class CdsExportPatientAlertMapper {
    /** Occurrence identity table: one alert per patient, keyed by the patient number. */
    public static final String TABLE = "demographiccust";

    private CdsExportPatientAlertMapper() {}

    /** One patient's populated alert. */
    public record Alert(int patient, CdsExportCapturedTextRowDto row, String text) {
        public Alert {
            Objects.requireNonNull(row);
            if (patient < 1
                    || !row.table().equals(TABLE)
                    || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                    || !text.equals(row.fields().get("cust3"))
                    || !legal(text))
                throw new IllegalArgumentException("CDS_EXPORT_PATIENT_ALERT_SCOPE_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportPatientAlertMapper.Alert[protected]";
        }
    }

    /** Whether the populated alert is legal XML text within AlertDescription's 1,024 characters. */
    static boolean legal(String value) {
        return value != null
                && !value.isEmpty()
                && CdsExportNativeFieldAssessments.legal(value, 1024);
    }

    /**
     * The patient's alert, or null when the row is absent, was captured without the alert column
     * (AlertsAndSpecialNeeds not selected) or holds no alert.
     *
     * @throws IOException review when the populated alert cannot be written as XML text
     */
    public static Alert map(int patient, CdsExportCapturedTextRowDto custom) throws IOException {
        if (custom == null || !custom.fields().containsKey("cust3")) return null;
        if (!custom.table().equals(TABLE)
                || !Integer.toString(patient).equals(custom.fields().get("demographic_no")))
            throw new IOException("CDS_EXPORT_PATIENT_ALERT_SCOPE_INVALID");
        String text = custom.fields().get("cust3");
        if (text == null || text.isEmpty()) return null;
        if (!legal(text))
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_PATIENT_ALERT_WIRE_TEXT_REQUIRES_REVIEW");
        return new Alert(patient, custom, text);
    }

    /** The occurrence's own decisions; the row's other columns belong to the Demographics receipt. */
    public static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields(Alert alert) {
        var row = alert.row();
        return Map.of(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "cust3"),
                new CdsExportFieldAssessmentDto(
                        Presence.VALUE,
                        Outcome.MAPPED,
                        CdsExportDemographicSupportingMapper.ALERT_DESTINATION,
                        "NATIVE_PATIENT_ALERT_AS_ALERT_DESCRIPTION"),
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "demographic_no"),
                new CdsExportFieldAssessmentDto(
                        Presence.VALUE, Outcome.OPERATIONAL, "", "EXACT_PATIENT_OWNER"));
    }

    /** Schema-ordered AlertsAndSpecialNeeds content. */
    public static void write(Alert alert, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "AlertDescription", alert.text());
    }
}
