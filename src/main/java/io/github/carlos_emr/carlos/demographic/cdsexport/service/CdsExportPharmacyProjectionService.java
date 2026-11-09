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

/** Bounded native pharmacy projection from one authenticated capture. Imported source reconciliation is a separate mandatory assembly gate. */
@Service
public class CdsExportPharmacyProjectionService {
    public record Result(String sourceManifestSha256, CdsExportPharmacyMapper.Result mapping) {
        public Result {
            if (sourceManifestSha256 == null
                    || !sourceManifestSha256.matches("[0-9a-f]{64}")
                    || mapping == null)
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_PROJECTION_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportPharmacyProjectionService.Result[protected]";
        }
    }

    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    @Autowired
    public CdsExportPharmacyProjectionService(CdsExportNativeCaptureService captures) {
        this(captures, new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportPharmacyProjectionService(
            CdsExportNativeCaptureService captures, CdsExportCapturedTextRowsReader rows) {
        this.captures = Objects.requireNonNull(captures);
        this.rows = Objects.requireNonNull(rows);
    }

    public Result project(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        patient.require(lease);
        control.checkpoint();
        var tables = Set.copyOf(requiredTables);
        if (!tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(Set.of("demographicPharmacy", "pharmacyInfo"))) scope();
        long expected = 0;
        for (String table : List.of("demographicPharmacy", "pharmacyInfo")) {
            long count = source.capture().tables().get(table).rows();
            if (count > bounds.nativeRows() - expected) limit();
            expected += count;
        }
        var sources = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        long[] resource = {0, 0};
        for (String table : List.of("demographicPharmacy", "pharmacyInfo")) {
            var values = new ArrayList<CdsExportCapturedTextRowDto>();
            sources.put(table, values);
            var actual =
                    rows.forEach(
                            lease,
                            patient,
                            tables,
                            table,
                            source.manifest(),
                            bounds.fields(),
                            bounds.bytes(),
                            1_048_576,
                            control,
                            row -> {
                                control.checkpoint();
                                if (++resource[0] > bounds.nativeRows()) limit();
                                resource[1] = Math.addExact(resource[1], 2048L);
                                for (String value : row.fields().values())
                                    if (value != null)
                                        resource[1] =
                                                Math.addExact(
                                                        resource[1],
                                                        value.getBytes(StandardCharsets.UTF_8)
                                                                        .length
                                                                + 32L);
                                if (resource[1] > bounds.indexBytes()) limit();
                                values.add(row);
                            });
            if (!actual.equals(source.capture())
                    || values.size() != source.capture().tables().get(table).rows()) scope();
        }
        if (resource[0] != expected) scope();
        var mapped =
                new CdsExportPharmacyMapper()
                        .map(
                                patient.patient(),
                                sources.get("demographicPharmacy"),
                                sources.get("pharmacyInfo"),
                                bounds.nativeRows(),
                                bounds.indexBytes(),
                                control::checkpoint);
        var result = new Result(source.manifest().plaintextSha256(), mapped);
        captures.authorizeSources(lease, patient, tables, control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_PHARMACY_PROJECTION_SCOPE_INVALID");
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_PHARMACY_RESOURCE_LIMIT");
    }
}
