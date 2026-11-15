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
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Base demographics plus the exact frozen primary physician. Provider scan memory is one
 * bounded row and at most one match. This is not complete demographic related-data,
 * retained-source or whole-patient qualification; every source read and final authority must pass.
 */
@Service
public class CdsExportDemographicProjectionService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportDemographicCaptureReader demographic;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportDemographicProjectionService(CdsExportNativeCaptureService captures) {
        this.captures = java.util.Objects.requireNonNull(captures);
        demographic = new CdsExportDemographicCaptureReader(captures);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    /** Base and note inputs share the exact protected manifest; this does not complete other demographic sources. */
    public CdsExportDemographicNoteProjectionDto projectWithPatientNote(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        var base =
                project(
                        lease,
                        patient,
                        requiredTables,
                        manifest,
                        maximumFields,
                        maximumBytes,
                        control);
        var note =
                new CdsExportPatientNoteProjectionReader(captures)
                        .read(
                                lease,
                                patient,
                                requiredTables,
                                manifest,
                                maximumFields,
                                maximumBytes,
                                control);
        if (!note.sourceManifestSha256().equals(manifest.plaintextSha256()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_NOTE_GENERATION_MISMATCH");
        captures.authorizeSources(lease, patient, requiredTables, control);
        patient.require(lease);
        control.checkpoint();
        return new CdsExportDemographicNoteProjectionDto(
                manifest.plaintextSha256(), base, note.rows(), note.value());
    }

    public CdsExportDemographicMappingDto project(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        if (!requiredTables.containsAll(Set.of("demographic", "provider")))
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_INPUTS_REQUIRED");
        var base =
                demographic.read(
                        lease,
                        patient,
                        requiredTables,
                        manifest,
                        maximumFields,
                        maximumBytes,
                        control);
        String id = base.fields().get("provider_no");
        var match = new CdsExportCapturedTextRowDto[1];
        rows.forEach(
                lease,
                patient,
                requiredTables,
                "provider",
                manifest,
                maximumFields,
                maximumBytes,
                65_536,
                control,
                row -> {
                    control.checkpoint();
                    if (!row.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)
                            || row.fields().get("provider_no") == null
                            || !CdsExportPublicProviderSourceReader.key(
                                            row.fields().get("provider_no"))
                                    .equals(row.recordKey())) {
                        throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_CAPTURE_SCOPE_INVALID");
                    }
                    if (id != null && !id.isEmpty() && id.equals(row.fields().get("provider_no"))) {
                        if (match[0] != null)
                            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_SCOPE_INVALID");
                        match[0] = row;
                    }
                });
        var result = new CdsExportDemographicMapper().map(base, match[0]);
        captures.authorizeSources(lease, patient, requiredTables, control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }
}
