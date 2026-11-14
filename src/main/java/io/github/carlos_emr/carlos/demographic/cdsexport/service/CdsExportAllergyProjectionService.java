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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyMappingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyProjectionDto;
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
 * Joins captured allergy rows with their captured precision, never current clinical rows.
 * Index memory is explicitly bounded and orphan metadata is an error. Callers keep all
 * results private until both complete generation reads succeed, then reconcile retained
 * source/lookup/author inputs and independent clinical coverage before any publication.
 */
@Service
public class CdsExportAllergyProjectionService {
    @FunctionalInterface
    public interface Mappings {
        void accept(CdsExportAllergyMappingDto mapping) throws IOException;
    }

    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportAllergyMapper mapper = new CdsExportAllergyMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportAllergyProjectionService(CdsExportNativeCaptureService captures) {
        this(new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportAllergyProjectionService(CdsExportCapturedTextRowsReader rows) {
        this.rows = java.util.Objects.requireNonNull(rows);
    }

    public CdsExportAllergyProjectionDto project(
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
        if (!requiredTables.containsAll(Set.of("allergies", "partial_date"))
                || maximumPrecisionRows < 1
                || maximumPrecisionRows > 131_072) {
            throw new IllegalArgumentException("CDS_EXPORT_ALLERGY_INPUTS_REQUIRED");
        }
        var precision = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        long[] counts = new long[4];
        var first =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        "partial_date",
                        manifest,
                        maximumFields,
                        maximumBytes,
                        65_536,
                        control,
                        row -> {
                            if (++counts[0] > maximumPrecisionRows)
                                throw new IOException("CDS_EXPORT_PRECISION_RESOURCE_LIMIT");
                            String owner = row.fields().get("table_name");
                            if ("2".equals(owner)
                                    && requiredTables.contains("drugs")
                                    && lease.document()
                                            .command()
                                            .categories()
                                            .contains(
                                                    io.github.carlos_emr.carlos.demographic.cds
                                                            .contract.CdsCategory
                                                            .MEDICATIONS_AND_TREATMENTS)) return;
                            String parent = row.fields().get("table_id");
                            if (parent == null || !"1".equals(owner))
                                throw new IOException("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
                            precision
                                    .computeIfAbsent(parent, ignored -> new ArrayList<>())
                                    .add(row);
                        });
        var second =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        "allergies",
                        manifest,
                        maximumFields,
                        maximumBytes,
                        67_108_864,
                        control,
                        row -> {
                            patient.require(lease);
                            String id = row.fields().get("allergyid");
                            var linked = precision.remove(id);
                            var mapping =
                                    mapper.map(
                                            patient.patient(),
                                            row,
                                            linked == null ? List.of() : linked);
                            counts[1] = Math.addExact(counts[1], 1);
                            if (!mapping.nativeRowRepresentable())
                                counts[2] = Math.addExact(counts[2], 1);
                            counts[3] = Math.addExact(counts[3], mapping.fields().size());
                            output.accept(mapping);
                        });
        if (!first.equals(second)
                || !precision.isEmpty()
                || counts[1] != second.tables().get("allergies").rows()
                || counts[0] != second.tables().get("partial_date").rows())
            throw new IOException("CDS_EXPORT_ALLERGY_CAPTURE_SCOPE_INVALID");
        patient.require(lease);
        control.checkpoint();
        return new CdsExportAllergyProjectionDto(
                manifest.plaintextSha256(), counts[1], counts[2], counts[3]);
    }
}
