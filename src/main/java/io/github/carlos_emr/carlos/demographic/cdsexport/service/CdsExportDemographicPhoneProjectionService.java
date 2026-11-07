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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Same-generation native phone projection; retained source associations and other demographic domains need their own reconciliation. */
@Service
public class CdsExportDemographicPhoneProjectionService {
    public record Result(String sourceManifestSha256, long propertyRows, CdsExportDemographicMappingDto demographic) {
        public Result {
            if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || propertyRows < 0 || propertyRows > 8192
                    || demographic == null || demographic.relatedFields().keySet().stream().filter(field -> field.table().equals("demographicExt")).count() != propertyRows * 8L)
                throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROJECTION_INVALID");
        }
        @Override public String toString() { return "CdsExportDemographicPhoneProjectionService.Result[protected]"; }
    }
    private final CdsExportNativeCaptureService captures;
    private final CdsExportDemographicProjectionService demographic;
    private final CdsExportDemographicCaptureReader current;
    private final CdsExportCapturedTextRowsReader rows;
    @Autowired public CdsExportDemographicPhoneProjectionService(CdsExportNativeCaptureService captures, CdsExportDemographicProjectionService demographic) {
        this(captures, demographic, new CdsExportDemographicCaptureReader(captures), new CdsExportCapturedTextRowsReader(captures));
    }
    CdsExportDemographicPhoneProjectionService(CdsExportNativeCaptureService captures, CdsExportDemographicProjectionService demographic,
            CdsExportDemographicCaptureReader current, CdsExportCapturedTextRowsReader rows) {
        this.captures = Objects.requireNonNull(captures); this.demographic = Objects.requireNonNull(demographic);
        this.current = Objects.requireNonNull(current); this.rows = Objects.requireNonNull(rows);
    }
    public Result project(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, CdsExportClinicalOccurrenceService.Bounds bounds, CdsExportRunControl control) throws IOException {
        patient.require(lease); control.checkpoint(); var tables = Set.copyOf(requiredTables);
        if (!tables.equals(source.capture().tables().keySet()) || !tables.containsAll(Set.of("demographic", "provider", "demographicExt"))) scope();
        if (source.capture().tables().get("demographicExt").rows() > bounds.nativeRows()) limit();
        var base = demographic.project(lease, patient, tables, source.manifest(), bounds.fields(), bounds.bytes(), control);
        var raw = current.read(lease, patient, tables, source.manifest(), bounds.fields(), bounds.bytes(), control);
        var properties = new ArrayList<CdsExportCapturedTextRowDto>(); long[] bytes = {0};
        var actual = rows.forEach(lease, patient, tables, "demographicExt", source.manifest(), bounds.fields(), bounds.bytes(),
                1_048_576, control, row -> {
                    control.checkpoint(); if (properties.size() >= bounds.nativeRows()) limit(); bytes[0] = Math.addExact(bytes[0], 2048L);
                    for (String value : row.fields().values()) if (value != null) bytes[0] = Math.addExact(bytes[0], 32L + value.getBytes(StandardCharsets.UTF_8).length);
                    if (bytes[0] > bounds.indexBytes()) limit(); properties.add(row);
                });
        if (!actual.equals(source.capture()) || properties.size() != source.capture().tables().get("demographicExt").rows()) scope();
        var value = new CdsExportDemographicPhonesMapper().map(base, raw, properties, bounds.nativeRows(), bounds.indexBytes(), control::checkpoint);
        var result = new Result(source.manifest().plaintextSha256(), properties.size(), value);
        captures.authorizeSources(lease, patient, tables, control); patient.require(lease); control.checkpoint(); return result;
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROJECTION_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PHONE_RESOURCE_LIMIT"); }
}
