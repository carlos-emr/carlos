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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Bounded reference join over authenticated captured values. No provider entity, status
 * cache, reason-list cache, live clinical table or guessed reference is used. Callers keep every
 * derived mapping/output private until this entire call completes and retained-source
 * reconciliation, historical archive qualification and independent clinical coverage also
 * succeed.
 */
@Service
public class CdsExportAppointmentProjectionService {
    @FunctionalInterface
    public interface Mappings {
        void accept(CdsExportAppointmentMappingDto mapping) throws IOException;
    }

    private static final Set<String> TABLES =
            Set.of(
                    "appointment",
                    "appointmentArchive",
                    "appointment_status",
                    "LookupListItem",
                    "provider",
                    "clinical_timezone");
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportClinicalZoneService zones;

    public CdsExportAppointmentProjectionService(CdsExportNativeCaptureService captures) {
        this(captures, new CdsExportClinicalZoneService(captures));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportAppointmentProjectionService(
            CdsExportNativeCaptureService captures, CdsExportClinicalZoneService zones) {
        this(new CdsExportCapturedTextRowsReader(captures), zones);
    }

    CdsExportAppointmentProjectionService(
            CdsExportCapturedTextRowsReader rows, CdsExportClinicalZoneService zones) {
        this.rows = java.util.Objects.requireNonNull(rows);
        this.zones = java.util.Objects.requireNonNull(zones);
    }

    public CdsExportAppointmentProjectionDto project(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportStagedFileDto manifest,
            long maximumFields,
            long maximumBytes,
            int maximumLookupRows,
            long maximumLookupBytes,
            ZoneId clinicalZone,
            CdsExportRunControl control,
            Mappings output)
            throws IOException {
        java.util.Objects.requireNonNull(output);
        java.util.Objects.requireNonNull(control);
        if (!requiredTables.containsAll(TABLES)
                || maximumLookupRows < 1
                || maximumLookupRows > 131_072
                || maximumLookupBytes < 1
                || maximumLookupBytes > 67_108_864
                || clinicalZone == null) {
            throw new IllegalArgumentException("CDS_EXPORT_APPOINTMENT_INPUTS_REQUIRED");
        }
        var zone =
                zones.read(
                        lease,
                        patient,
                        requiredTables,
                        manifest,
                        maximumFields,
                        maximumBytes,
                        control);
        if (!manifest.plaintextSha256().equals(zone.sourceManifestSha256())
                || !clinicalZone.equals(zone.zone()))
            throw new IOException("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_MISMATCH");
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        var statuses = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        var reasonCodes = new HashMap<String, List<CdsExportCapturedTextRowDto>>();
        // 0 rows, 1 bytes, 2 providers, 3 statuses, 4 appointments, 5 blocked, 6 fields,
        // 7 reason-list items
        long[] counts = new long[8];
        var first =
                rows.forEachTable(
                        lease,
                        patient,
                        requiredTables,
                        Set.of("provider", "appointment_status", "LookupListItem"),
                        manifest,
                        maximumFields,
                        maximumBytes,
                        65_536,
                        control,
                        row -> {
                            control.checkpoint();
                            if (++counts[0] > maximumLookupRows)
                                throw new IOException(
                                        "CDS_EXPORT_APPOINTMENT_LOOKUP_RESOURCE_LIMIT");
                            for (var field : row.fields().entrySet()) {
                                long size =
                                        field.getKey().getBytes(StandardCharsets.UTF_8).length
                                                + (field.getValue() == null
                                                        ? 1L
                                                        : field.getValue()
                                                                .getBytes(StandardCharsets.UTF_8)
                                                                .length);
                                if (size > maximumLookupBytes - counts[1])
                                    throw new IOException(
                                            "CDS_EXPORT_APPOINTMENT_LOOKUP_RESOURCE_LIMIT");
                                counts[1] += size;
                            }
                            if (row.table().equals("provider")) {
                                String id = row.fields().get("provider_no");
                                if (id == null
                                        || !row.fields()
                                                .keySet()
                                                .equals(CdsExportProviderRowDto.COLUMNS)
                                        || !CdsExportPublicProviderSourceReader.key(id)
                                                .equals(row.recordKey())
                                        || providers.putIfAbsent(id, row) != null) scope();
                                counts[2]++;
                            } else if (row.table().equals("appointment_status")) {
                                String code = row.fields().get("status");
                                if (code == null
                                        || !row.fields()
                                                .keySet()
                                                .equals(
                                                        Set.copyOf(
                                                                CdsExportPatientTableReadSupport
                                                                        .Plan.APPOINTMENT_STATUSES
                                                                        .columns()))) scope();
                                statuses.computeIfAbsent(code, ignored -> new ArrayList<>())
                                        .add(row);
                                counts[3]++;
                            } else if (row.table().equals("LookupListItem")) {
                                String id = row.fields().get("id");
                                if (id == null
                                        || !row.fields()
                                                .keySet()
                                                .equals(
                                                        Set.copyOf(
                                                                CdsExportPatientTableReadSupport
                                                                        .Plan
                                                                        .APPOINTMENT_REASON_CODES
                                                                        .columns()))) scope();
                                reasonCodes.computeIfAbsent(id, ignored -> new ArrayList<>())
                                        .add(row);
                                counts[7]++;
                            } else scope();
                        });
        if (counts[2] != first.tables().get("provider").rows()
                || counts[3] != first.tables().get("appointment_status").rows()
                || counts[7] != first.tables().get("LookupListItem").rows()) scope();
        // Archive rows are versions/deletion history, not additional current appointments.
        // Until their historical representation is qualified, none can silently disappear.
        if (first.tables().get("appointmentArchive").rows() != 0)
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException("CDS_EXPORT_APPOINTMENT_HISTORY_REQUIRES_REVIEW");
        // No demographic snapshot is joined here, so a populated patient display stays review.
        var lookups =
                new CdsExportAppointmentLookupsDto(
                        manifest.plaintextSha256(), providers, statuses, reasonCodes, Set.of());
        var mapper = new CdsExportAppointmentMapper();
        var second =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        "appointment",
                        manifest,
                        maximumFields,
                        maximumBytes,
                        67_108_864,
                        control,
                        row -> {
                            patient.require(lease);
                            control.checkpoint();
                            var mapping = mapper.map(patient.patient(), row, lookups, clinicalZone);
                            counts[4] = Math.addExact(counts[4], 1);
                            if (!mapping.nativeRowRepresentable())
                                counts[5] = Math.addExact(counts[5], 1);
                            counts[6] = Math.addExact(counts[6], mapping.fields().size());
                            output.accept(mapping);
                        });
        if (!first.equals(second) || counts[4] != second.tables().get("appointment").rows())
            scope();
        patient.require(lease);
        control.checkpoint();
        return new CdsExportAppointmentProjectionDto(
                manifest.plaintextSha256(), counts[4], counts[5], counts[6]);
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_APPOINTMENT_CAPTURE_SCOPE_INVALID");
    }
}
