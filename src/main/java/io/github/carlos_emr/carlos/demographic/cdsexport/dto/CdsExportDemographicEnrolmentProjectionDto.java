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

import java.util.List;
import java.util.Map;

/** Same-generation native enrolment evidence; other demographic associations and retained reconciliation remain separate gates. */
public record CdsExportDemographicEnrolmentProjectionDto(String sourceManifestSha256,
        CdsExportDemographicMappingDto demographic, long sourceRows, long noteRows, CdsExportPatientNoteMappingDto note, List<CdsExportEnrolmentEventDto> events,
        Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> enrolmentFields) {
    public CdsExportDemographicEnrolmentProjectionDto {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || demographic == null
                || sourceRows < 1 || sourceRows > 65_536 || events == null || events.size() > sourceRows
                || noteRows < 0 || noteRows > 1 || (noteRows == 0) != (note == null)
                || note != null && note.patient() != demographic.patient())
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
        events = List.copyOf(events); enrolmentFields = Map.copyOf(enrolmentFields);
        var columns = java.util.Set.of("roster_status", "roster_date", "roster_termination_date", "roster_termination_reason", "roster_enrolled_to");
        String key = String.format(java.util.Locale.ROOT, "%010d", demographic.patient());
        for (String column : columns) {
            var field = enrolmentFields.get(new CdsExportNativeFieldKeyDto("demographic", key, column));
            var base = demographic.fields().get(column);
            if (field == null || !field.presence().name().equals(base.presence().name())
                    || !field.outcome().name().equals(base.outcome().name()) || !field.destination().equals(base.destination())
                    || !field.rule().equals(base.rule()))
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
        }
        var rows = new java.util.HashMap<String, java.util.Set<String>>();
        long mappedStates = 0;
        for (var entry : enrolmentFields.entrySet()) {
            var field = entry.getKey();
            if (!java.util.Set.of("demographic", "demographicArchive", "provider").contains(field.table())
                    || field.table().equals("demographic") && !field.recordKey().equals(key)
                    || !field.table().equals("provider") && !columns.contains(field.column()))
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
            rows.computeIfAbsent(field.table() + "/" + field.recordKey(), ignored -> new java.util.HashSet<>()).add(field.column());
            if (field.column().equals("roster_status") && entry.getValue().outcome() == CdsExportFieldAssessmentDto.Outcome.MAPPED) mappedStates++;
        }
        long nativeRows = 0;
        for (var row : rows.entrySet()) {
            boolean provider = row.getKey().startsWith("provider/");
            var expected = provider ? io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS : columns;
            if (!row.getValue().equals(expected)) throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
            if (!provider) nativeRows++;
        }
        if (nativeRows != sourceRows || events.isEmpty() != (mappedStates == 0) || events.size() > mappedStates)
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
    }
    public boolean representable() {
        return demographic.baseRowRepresentable() && (note == null || note.representable()) && enrolmentFields.values().stream()
                .noneMatch(field -> field.outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }
    @Override public String toString() { return "CdsExportDemographicEnrolmentProjectionDto[protected]"; }
}
