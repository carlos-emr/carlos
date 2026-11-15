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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.Map;

/**
 * One native appointment and its used reference fields; retained source and complete category
 * coverage remain separate. {@code complete} holds, by element name, the complete value of each
 * element whose text is only its schema-valid form (notes cut at the CDS limit, text without
 * XML-illegal characters); the appointment's NewCategory source record carries it.
 */
public record CdsExportAppointmentMappingDto(CdsExportAppointmentDto value,
        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
        Map<String, String> complete) {
    public CdsExportAppointmentMappingDto(CdsExportAppointmentDto value,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        this(value, fields, Map.of());
    }
    public CdsExportAppointmentMappingDto {
        fields = Map.copyOf(fields);
        complete = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(complete));
        if (fields.isEmpty() || value != null && fields.values().stream().anyMatch(field ->
                field.outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW)) {
            throw new IllegalArgumentException("CDS_EXPORT_APPOINTMENT_MAPPING_INVALID");
        }
    }
    public boolean nativeRowRepresentable() { return value != null; }
    @Override public String toString() { return "CdsExportAppointmentMappingDto[protected]"; }
}
