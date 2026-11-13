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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the demographic rows that the native demographic NewCategory record and the patient
 * alert occurrence are built from, out of the same authenticated patient capture the Demographics
 * occurrence reads. The exact owned provider slice is the care-team references only; collation
 * candidates are never taken for this patient's providers.
 */
public final class CdsExportDemographicSupportingReader {
    private static final int ROW_BYTES = 1 << 20;
    private final CdsExportNativeCaptureService captures;

    public CdsExportDemographicSupportingReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    /** The patient's demographic, custom, property and care-team provider rows. */
    public CdsExportDemographicSupportingMapper.Rows rows(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var current = read(lease, patient, tables, source, bounds, control, "demographic");
        if (current.size() != 1) invalid();
        var custom = read(lease, patient, tables, source, bounds, control, "demographiccust");
        if (custom.size() > 1) invalid();
        var properties = read(lease, patient, tables, source, bounds, control, "demographicExt");
        var row = custom.isEmpty() ? null : custom.getFirst();
        var references = CdsExportDemographicSupportingMapper.references(current.getFirst(), row);
        var providers = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        if (!references.isEmpty())
            new CdsExportPublicProviderReader(captures)
                    .read(lease, patient, tables, source, bounds, control)
                    .forEach(
                            (id, provider) -> {
                                if (references.contains(id)) providers.put(id, provider);
                            });
        var related = new LinkedHashMap<String, List<CdsExportCapturedTextRowDto>>();
        for (String table : CdsExportDemographicSupportingMapper.Rows.RELATED)
            related.put(table, read(lease, patient, tables, source, bounds, control, table));
        return new CdsExportDemographicSupportingMapper.Rows(
                patient.patient(), current.getFirst(), row, properties, providers, related);
    }

    /**
     * The patient's {@code demographiccust} row, or null when the capture holds none or did not
     * capture the table.
     */
    public CdsExportCapturedTextRowDto custom(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!tables.contains("demographiccust")) return null;
        var custom = read(lease, patient, tables, source, bounds, control, "demographiccust");
        if (custom.size() > 1) invalid();
        return custom.isEmpty() ? null : custom.getFirst();
    }

    private List<CdsExportCapturedTextRowDto> read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            String table)
            throws IOException {
        patient.require(lease);
        var captured = source.capture().tables().get(table);
        if (!tables.contains(table) || captured == null || captured.rows() > bounds.nativeRows())
            invalid();
        var found = new ArrayList<CdsExportCapturedTextRowDto>();
        var actual =
                new CdsExportCapturedTextRowsReader(captures)
                        .forEach(
                                lease,
                                patient,
                                tables,
                                table,
                                source.manifest(),
                                bounds.fields(),
                                bounds.bytes(),
                                ROW_BYTES,
                                control,
                                row -> {
                                    if (found.size() >= bounds.nativeRows()
                                            || !table.equals(row.table())) invalid();
                                    found.add(row);
                                });
        if (!actual.equals(source.capture()) || found.size() != captured.rows()) invalid();
        return List.copyOf(found);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_DEMOGRAPHIC_SUPPORTING_SOURCE_INVALID");
    }
}
