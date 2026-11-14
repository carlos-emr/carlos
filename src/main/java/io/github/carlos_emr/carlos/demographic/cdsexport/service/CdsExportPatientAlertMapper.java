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
 * files the occurrence as a Reminders note, with the exact text of the residual when the element
 * could not hold it (CdsHistoryPersister). The occurrence exists only when AlertsAndSpecialNeeds
 * is selected (the alert column is captured only then) and the alert is populated.
 *
 * <p>No alert holds a chart. A character XML 1.0 cannot carry (a control character pasted into
 * the Alert box) is written as U+FFFD in AlertDescription, one for one, and the stored text
 * travels exactly in the occurrence's own ResidualInfo as {@value #RESIDUAL_NAME} (JSON, {@link
 * CdsExportResidualJson}). Text longer than the element (the column cannot hold it) would leave
 * AlertDescription out and travel in the same residual.
 */
public final class CdsExportPatientAlertMapper {
    /** Occurrence identity table: one alert per patient, keyed by the patient number. */
    public static final String TABLE = "demographiccust";

    /** The residual DataElement that carries an alert AlertDescription cannot hold exactly. */
    public static final String RESIDUAL_NAME =
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract
                    .PATIENT_ALERT;

    /** Destination of the residual carrier. */
    static final String RESIDUAL_DESTINATION = "AlertsAndSpecialNeeds/ResidualInfo";

    /** AlertDescription is text1K: 1,000 characters as the data dictionary counts them. */
    static final int MAXIMUM = 1000;

    private CdsExportPatientAlertMapper() {}

    /** One patient's populated alert. */
    public record Alert(int patient, CdsExportCapturedTextRowDto row, String text) {
        public Alert {
            Objects.requireNonNull(row);
            if (patient < 1
                    || !row.table().equals(TABLE)
                    || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                    || !text.equals(row.fields().get("cust3"))
                    || text.isEmpty())
                throw new IllegalArgumentException("CDS_EXPORT_PATIENT_ALERT_SCOPE_INVALID");
        }

        /** AlertDescription as written, or null when the element cannot hold the alert at all. */
        String element() {
            String shown = CdsExportXmlText.element(text);
            return shown.codePointCount(0, shown.length()) > MAXIMUM ? null : shown;
        }

        /** True when AlertDescription is the stored text exactly, so no residual is written. */
        boolean exact() {
            return text.equals(element());
        }

        @Override
        public String toString() {
            return "CdsExportPatientAlertMapper.Alert[protected]";
        }
    }

    /** Whether AlertDescription holds the populated alert exactly, with no residual. */
    static boolean legal(String value) {
        return value != null
                && !value.isEmpty()
                && CdsExportNativeFieldAssessments.legal(value, MAXIMUM);
    }

    /** The decision for a populated alert: always exported, exact in the element or the residual. */
    static CdsExportFieldAssessmentDto decision(String value) {
        return legal(value)
                ? new CdsExportFieldAssessmentDto(
                        Presence.VALUE,
                        Outcome.MAPPED,
                        CdsExportDemographicSupportingMapper.ALERT_DESTINATION,
                        "NATIVE_PATIENT_ALERT_AS_ALERT_DESCRIPTION")
                : new CdsExportFieldAssessmentDto(
                        Presence.VALUE,
                        Outcome.MAPPED,
                        CdsExportDemographicSupportingMapper.ALERT_DESTINATION
                                + ";"
                                + RESIDUAL_DESTINATION,
                        "NATIVE_PATIENT_ALERT_EXACT_IN_RESIDUAL");
    }

    /**
     * The patient's alert, or null when the row is absent, was captured without the alert column
     * (AlertsAndSpecialNeeds not selected) or holds no alert.
     *
     * @throws IOException when the row is not the patient's own
     */
    public static Alert map(int patient, CdsExportCapturedTextRowDto custom) throws IOException {
        if (custom == null || !custom.fields().containsKey("cust3")) return null;
        if (!custom.table().equals(TABLE)
                || !Integer.toString(patient).equals(custom.fields().get("demographic_no")))
            throw new IOException("CDS_EXPORT_PATIENT_ALERT_SCOPE_INVALID");
        String text = custom.fields().get("cust3");
        if (text == null || text.isEmpty()) return null;
        return new Alert(patient, custom, text);
    }

    /** The occurrence's own decisions; the row's other columns belong to the Demographics receipt. */
    public static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields(Alert alert) {
        var row = alert.row();
        return Map.of(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "cust3"),
                decision(alert.text()),
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), "demographic_no"),
                new CdsExportFieldAssessmentDto(
                        Presence.VALUE, Outcome.OPERATIONAL, "", "EXACT_PATIENT_OWNER"));
    }

    /** Schema-ordered AlertsAndSpecialNeeds content. */
    public static void write(Alert alert, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        String element = alert.element();
        if (!alert.exact()) {
            var context = new java.util.LinkedHashMap<String, Object>();
            context.put("version", 1);
            context.put("alert", alert.text());
            xml.start("cds", "ResidualInfo");
            CdsExportNativeHistoryXmlWriter.residualElement(
                    xml,
                    RESIDUAL_NAME,
                    "string",
                    CdsExportScreeningMapper.tokenSafe(
                            com.fasterxml.jackson.databind.json.JsonMapper.builder()
                                    .build()
                                    .writeValueAsString(context)));
            xml.end();
        }
        if (element != null) xml.element("cds", "AlertDescription", element);
    }
}
