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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationMappingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMedicationProjectionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Joins captured medication rows with their captured precision, never current clinical rows.
 * Index memory is explicitly bounded and orphan metadata is an error. Callers keep all
 * results private until both complete generation reads succeed, then reconcile retained
 * source/lookup/author inputs and independent clinical coverage before any publication.
 */
@Service
public class CdsExportMedicationProjectionService {
    @FunctionalInterface
    public interface Mappings {
        void accept(CdsExportMedicationMappingDto mapping) throws IOException;
    }

    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportMedicationMapper mapper = new CdsExportMedicationMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportMedicationProjectionService(CdsExportNativeCaptureService captures) {
        this(new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportMedicationProjectionService(CdsExportCapturedTextRowsReader rows) {
        this.rows = java.util.Objects.requireNonNull(rows);
    }

    public CdsExportMedicationProjectionDto project(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            int maximumPrecisionRows,
            CdsExportRunControl control,
            Mappings output)
            throws IOException {
        java.util.Objects.requireNonNull(output);
        if (!requiredTables.containsAll(Set.of("drugs", "partial_date", "provider"))
                || maximumPrecisionRows < 1
                || maximumPrecisionRows > 131_072) {
            throw new IllegalArgumentException("CDS_EXPORT_MEDICATION_INPUTS_REQUIRED");
        }
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        long[] counts = new long[6];
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        var first =
                rows.forEachTable(
                        lease,
                        patient,
                        requiredTables,
                        Set.of("partial_date", "provider"),
                        manifest,
                        maximumFields,
                        maximumBytes,
                        65_536,
                        control,
                        row -> {
                            if (row.table().equals("provider")) {
                                if (++counts[4] > 131_072
                                        || !row.fields()
                                                .keySet()
                                                .equals(
                                                        io.github.carlos_emr.carlos.commn.dto
                                                                .CdsExportProviderRowDto.COLUMNS))
                                    scope();
                                for (var value : row.fields().values())
                                    if (value != null) {
                                        counts[5] =
                                                Math.addExact(
                                                        counts[5],
                                                        value.getBytes(
                                                                        java.nio.charset
                                                                                .StandardCharsets
                                                                                .UTF_8)
                                                                .length);
                                        if (counts[5] > 67_108_864)
                                            throw new IOException(
                                                    "CDS_EXPORT_MEDICATION_LOOKUP_RESOURCE_LIMIT");
                                    }
                                String id = row.fields().get("provider_no");
                                if (id == null
                                        || !CdsExportPublicProviderSourceReader.key(id)
                                                .equals(row.recordKey())
                                        || providers.putIfAbsent(id, row) != null) scope();
                                return;
                            }
                            if (!row.table().equals("partial_date")) scope();
                            if (++counts[0] > maximumPrecisionRows)
                                throw new IOException("CDS_EXPORT_PRECISION_RESOURCE_LIMIT");
                            String owner = row.fields().get("table_name");
                            if ("1".equals(owner)
                                    && requiredTables.contains("allergies")
                                    && lease.document()
                                            .command()
                                            .categories()
                                            .contains(
                                                    io.github.carlos_emr.carlos.demographic.cds
                                                            .contract.CdsCategory
                                                            .ALLERGIES_AND_ADVERSE_REACTIONS))
                                return;
                            String parent = row.fields().get("table_id");
                            if (parent == null || !"2".equals(owner))
                                throw new IOException(
                                        "CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
                            precision
                                    .computeIfAbsent(parent, ignored -> new ArrayList<>())
                                    .add(row);
                        });
        var second =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        "drugs",
                        manifest,
                        maximumFields,
                        maximumBytes,
                        67_108_864,
                        control,
                        row -> {
                            patient.require(lease);
                            String id = row.fields().get("drugid");
                            var linked = precision.remove(id);
                            var mapping =
                                    mapper.map(
                                            patient.patient(),
                                            row,
                                            linked == null ? List.of() : linked,
                                            providers);
                            counts[1] = Math.addExact(counts[1], 1);
                            if (!mapping.nativeRowRepresentable())
                                counts[2] = Math.addExact(counts[2], 1);
                            counts[3] = Math.addExact(counts[3], mapping.fields().size());
                            output.accept(mapping);
                        });
        if (!first.equals(second)
                || !precision.isEmpty()
                || counts[1] != second.tables().get("drugs").rows()
                || counts[0] != second.tables().get("partial_date").rows()
                || counts[4] != first.tables().get("provider").rows())
            throw new IOException("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
        patient.require(lease);
        control.checkpoint();
        return new CdsExportMedicationProjectionDto(
                manifest.plaintextSha256(), counts[1], counts[2], counts[3]);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_MEDICATION_CAPTURE_SCOPE_INVALID");
    }
}
