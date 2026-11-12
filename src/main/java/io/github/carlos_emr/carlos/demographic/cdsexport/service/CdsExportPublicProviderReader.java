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

import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads only the finite public-provider projection from an authenticated patient capture. */
public final class CdsExportPublicProviderReader {
    private final CdsExportNativeCaptureService captures;

    public CdsExportPublicProviderReader(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
    }

    public Map<String, CdsExportCapturedTextRowDto> read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        patient.require(lease);
        var result = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var table = source.capture().tables().get("provider");
        if (table == null
                || table.rows() > bounds.lookupRows()
                || !table.columns().equals(CdsExportProviderRowDto.COLUMNS)) invalid();
        long[] bytes = {0};
        var actual =
                new CdsExportCapturedTextRowsReader(captures)
                        .forEach(
                                lease,
                                patient,
                                tables,
                                "provider",
                                source.manifest(),
                                bounds.fields(),
                                bounds.bytes(),
                                (int) Math.min(67_108_864L, bounds.indexBytes()),
                                control,
                                row -> {
                                    control.checkpoint();
                                    String id = row.fields().get("provider_no");
                                    if (!row.table().equals("provider")
                                            || id == null
                                            || id.isEmpty()
                                            || !row.fields()
                                                    .keySet()
                                                    .equals(CdsExportProviderRowDto.COLUMNS)
                                            || !row.exactNumbers().isEmpty()
                                            || !row.recordKey()
                                                    .equals(
                                                            CdsExportPublicProviderSourceReader.key(
                                                                    id))
                                            || result.putIfAbsent(id, row) != null
                                            || result.size() > bounds.lookupRows()) invalid();
                                    bytes[0] = Math.addExact(bytes[0], 2048);
                                    for (String value : row.fields().values())
                                        if (value != null)
                                            bytes[0] =
                                                    Math.addExact(
                                                            bytes[0],
                                                            64L
                                                                    + 3L
                                                                            * value.getBytes(
                                                                                            StandardCharsets
                                                                                                    .UTF_8)
                                                                                    .length);
                                    if (bytes[0] > bounds.indexBytes()) invalid();
                                });
        if (!actual.equals(source.capture()) || result.size() != table.rows()) invalid();
        captures.authorizeCapturedSources(lease, patient, tables, actual, control);
        patient.require(lease);
        return Map.copyOf(result);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_SOURCE_INVALID");
    }
}
