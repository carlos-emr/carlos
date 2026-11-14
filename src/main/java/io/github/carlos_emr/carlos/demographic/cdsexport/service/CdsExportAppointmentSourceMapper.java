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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.io.IOException;
import java.util.*;

/**
 * The per-appointment carrier {@value #ELEMENT_NAME}.
 *
 * <p>CDS 5.2 Appointments has no {@code ResidualInfo}. Every current appointment therefore has one
 * NewCategory record, {@value #CATEGORY_NAME}, whose single {@value #ELEMENT_NAME} residual holds a
 * versioned JSON object in a fixed key order with every space escaped
 * ({@link CdsExportScreeningMapper#tokenSafe}), so {@code xs:token} normalization cannot change it:
 *
 * <ul>
 *   <li>{@code appointment}: the 1-based position of the appointment's {@code Appointments}
 *       element in the same patient record, which binds the two;
 *   <li>{@code fields}: every captured column of the {@code appointment} row exactly as stored
 *       (SQL NULL as JSON null), including the exact start and end clock, the status code, the
 *       provider number, the booking name and remarks, and the scheduler context CDS has no
 *       element for (style, billing, location, resources, urgency, type, booking source, program);
 *   <li>{@code complete}: by element name, the complete text of an element that holds only its
 *       schema-valid form (notes cut at 32,000 characters, text without XML-illegal characters);
 *   <li>{@code statusDefinitions}: the captured {@code appointment_status} rows of the stored
 *       status code;
 *   <li>{@code publicProvider}: the captured public provider columns of the stored provider
 *       number, or null when no provider record has that number.
 * </ul>
 *
 * <p>JSON escapes control characters itself and U+FFFE/U+FFFF are escaped with the spaces, so a stored value
 * no XML document can hold is still carried exactly. Nothing in this record can hold a chart.
 * When the job does not select NewCategory the record is not written; the appointment's own
 * receipt then decides those values {@code OPERATIONAL}
 * {@link CdsExportDemographicSupportingMapper#NOT_SELECTED_RULE} and the package ReadMe counts
 * them per patient ({@link #NOT_EXPORTED_LABEL}).
 */
public final class CdsExportAppointmentSourceMapper {
    /** NewCategory {@code CategoryName} of the per-appointment record. */
    public static final String CATEGORY_NAME =
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract
                    .CATEGORY_NAME;

    /** Residual {@code DataElement} name whose Content is the versioned JSON. */
    public static final String ELEMENT_NAME =
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract
                    .ELEMENT_NAME;

    /** ReadMe label counting a patient's appointments whose source record was not selected. */
    public static final String NOT_EXPORTED_LABEL =
            "Appointment source records ("
                    + CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE
                    + ")";

    static final String RULE = "EXACT_NATIVE_APPOINTMENT_CONTEXT";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CdsExportAppointmentSourceMapper() {}

    /** The record's JSON, the captured rows it carries and their field decisions. */
    public record Source(
            String json,
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Source {
            Objects.requireNonNull(json);
            rows = List.copyOf(rows);
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportAppointmentSourceMapper.Source[protected]";
        }
    }

    /**
     * @param ordinal 1-based position of the appointment's element among the patient's
     *     {@code Appointments} elements
     * @param complete complete element texts ({@link CdsExportAppointmentMappingDto#complete})
     */
    public static Source map(
            long ordinal,
            CdsExportCapturedTextRowDto row,
            CdsExportAppointmentLookupsDto lookups,
            Map<String, String> complete)
            throws IOException {
        if (ordinal < 1 || row == null || lookups == null || !"appointment".equals(row.table()))
            throw new IOException("CDS_EXPORT_APPOINTMENT_SOURCE_SCOPE_INVALID");
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(row);
        var definitions = new TreeMap<String, CdsExportCapturedTextRowDto>();
        String status = row.fields().get("status");
        if (status != null && !status.isEmpty()) {
            for (var definition : lookups.statuses().getOrDefault(status, List.of()))
                definitions.put(definition.recordKey(), definition);
            // A signed or verified encounter appends S or V to the status (CdsExportAppointmentMapper).
            if (status.length() == 2 && (status.endsWith("S") || status.endsWith("V")))
                for (var definition :
                        lookups.statuses().getOrDefault(status.substring(0, 1), List.of()))
                    definitions.put(definition.recordKey(), definition);
        }
        rows.addAll(definitions.values());
        String providerNo = row.fields().get("provider_no");
        var provider =
                providerNo == null || providerNo.isEmpty()
                        ? null
                        : lookups.providers().get(providerNo);
        if (provider != null) rows.add(provider);
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("appointment", ordinal);
        context.put("fields", new TreeMap<>(row.fields()));
        context.put("complete", new TreeMap<>(complete));
        var listed = new ArrayList<Map<String, String>>();
        for (var definition : definitions.values()) listed.add(new TreeMap<>(definition.fields()));
        context.put("statusDefinitions", listed);
        context.put("publicProvider", provider == null ? null : new TreeMap<>(provider.fields()));
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var carried : rows) {
            var state = new CdsExportNativeFieldAssessments(carried, decisions);
            for (String column : carried.fields().keySet())
                state.set(
                        column,
                        Outcome.MAPPED,
                        CdsExportDemographicSupportingMapper.DESTINATION,
                        RULE);
        }
        // tokenSafe escapes every space and the two code points XML forbids that JSON leaves
        // raw, so the content is xs:token text no reader can alter.
        return new Source(
                CdsExportScreeningMapper.tokenSafe(JSON.writeValueAsString(context)),
                rows,
                decisions);
    }

    public static void write(Source source, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "CategoryName", CATEGORY_NAME);
        xml.element(
                "cds", "CategoryDescription", "Native appointment source record and scheduler context");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(xml, ELEMENT_NAME, "string", source.json());
        xml.end();
    }
}
