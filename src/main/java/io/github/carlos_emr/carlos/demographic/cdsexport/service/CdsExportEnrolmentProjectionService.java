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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Native demographic/archive roster events from one authenticated frozen source. Applied
 * imported demographics need fieldwise retained reconciliation before this path can be used;
 * old source history cannot disappear behind a native current-row projection.
 */
@Service
public class CdsExportEnrolmentProjectionService {
    private static final Set<String> REQUIRED = Set.of("demographic", "demographicArchive", "demographiccust", "demographicExt", "provider", "demographic_merged",
            "cds_import_field", "cds_import_unit", "cds_import_patient", "cds_import_preparation", "cds_import_dependency", "cds_import_run");
    private final CdsExportNativeCaptureService captures;
    private final CdsExportRetainedIndexService retained;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportPatientNoteProjectionReader notes;
    private final CdsExportDemographicPhoneProjectionService phones;
    @Autowired public CdsExportEnrolmentProjectionService(CdsExportNativeCaptureService captures, CdsExportRetainedIndexService retained,
            CdsExportDemographicPhoneProjectionService phones) {
        this(captures, retained, new CdsExportCapturedTextRowsReader(captures), new CdsExportPatientNoteProjectionReader(captures), phones);
    }
    public CdsExportEnrolmentProjectionService(CdsExportNativeCaptureService captures, CdsExportRetainedIndexService retained) {
        this(captures, retained, new CdsExportDemographicPhoneProjectionService(captures, new CdsExportDemographicProjectionService(captures)));
    }
    CdsExportEnrolmentProjectionService(CdsExportNativeCaptureService captures, CdsExportRetainedIndexService retained,
            CdsExportCapturedTextRowsReader rows, CdsExportPatientNoteProjectionReader notes) {
        this(captures, retained, rows, notes, new CdsExportDemographicPhoneProjectionService(captures, new CdsExportDemographicProjectionService(captures)));
    }
    CdsExportEnrolmentProjectionService(CdsExportNativeCaptureService captures, CdsExportRetainedIndexService retained,
            CdsExportCapturedTextRowsReader rows, CdsExportPatientNoteProjectionReader notes, CdsExportDemographicPhoneProjectionService phones) {
        this.captures = Objects.requireNonNull(captures); this.retained = Objects.requireNonNull(retained); this.rows = Objects.requireNonNull(rows);
        this.notes = Objects.requireNonNull(notes); this.phones = Objects.requireNonNull(phones);
    }
    public CdsExportDemographicEnrolmentProjectionDto project(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient,
            Set<String> requiredTables, CdsExportPatientCaptureDto source, CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control) throws IOException {
        patient.require(lease); control.checkpoint(); var tables = Set.copyOf(requiredTables);
        if (!tables.equals(source.capture().tables().keySet()) || !tables.containsAll(REQUIRED)
                || !lease.document().command().categories().contains(CdsCategory.DEMOGRAPHICS)) scope();
        long archiveCount = source.capture().tables().get("demographicArchive").rows();
        if (archiveCount >= Math.min(bounds.nativeRows(), 65_536)) limit();
        new CdsExportMergeScopeValidator(rows).requireUnmerged(lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var index = retained.index(lease, patient, tables, source.manifest(), bounds.fields(), bounds.bytes(),
                bounds.indexRows(), bounds.indexBytes(), control);
        if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256())) scope();
        for (var unit : index.tables().get("cds_import_unit").values()) {
            control.checkpoint(); String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (CdsCategory.DEMOGRAPHICS.wireName().equals(unit.fields().get("category")) && index.appliedUnits().contains(address))
                throw new IOException("CDS_EXPORT_ENROLMENT_RETAINED_RECONCILIATION_REQUIRED");
        }
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>(); long[] lookup = {0, 0};
        var lookupScan = rows.forEach(lease, patient, tables, "provider", source.manifest(), bounds.fields(), bounds.bytes(),
                65_536, control, row -> {
                    control.checkpoint(); String id = row.fields().get("provider_no");
                    if (id == null || !row.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)
                            || !CdsExportPublicProviderSourceReader.key(id).equals(row.recordKey()) || providers.putIfAbsent(id, row) != null) scope();
                    lookup[0]++; lookup[1] = Math.addExact(lookup[1], 1024L);
                    for (String value : row.fields().values()) if (value != null)
                        lookup[1] = Math.addExact(lookup[1], 64L + value.getBytes(StandardCharsets.UTF_8).length);
                    if (lookup[0] > bounds.lookupRows() || lookup[1] > bounds.indexBytes()) limit();
                });
        if (!lookupScan.equals(source.capture()) || lookup[0] != source.capture().tables().get("provider").rows()) scope();
        if (lookup[1] >= bounds.indexBytes()) limit();
        var history = new CdsExportEnrolmentHistory(patient.patient(), (int) Math.min(bounds.nativeRows(), 65_536),
                bounds.indexBytes() - lookup[1], control::checkpoint);
        var archives = rows.forEach(lease, patient, tables, "demographicArchive", source.manifest(), bounds.fields(), bounds.bytes(),
                1_048_576, control, row -> history.accept(row, providers.get(row.fields().get("roster_enrolled_to"))));
        if (!archives.equals(source.capture())) scope();
        var current = new CdsExportCapturedTextRowDto[1];
        var scan = rows.forEach(lease, patient, tables, "demographic", source.manifest(), bounds.fields(), bounds.bytes(),
                1_048_576, control, row -> {
                    if (current[0] != null) scope(); current[0] = row;
                    history.accept(row, providers.get(row.fields().get("roster_enrolled_to")));
                });
        if (!scan.equals(source.capture()) || current[0] == null || source.capture().tables().get("demographic").rows() != 1) scope();
        var events = history.finish(archiveCount);
        var related = phones.project(lease, patient, tables, source, bounds, control);
        if (!related.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || related.propertyRows() != source.capture().tables().get("demographicExt").rows()) scope();
        var base = related.demographic();
        if (base.patient() != patient.patient()) scope();
        var fields = new HashMap<>(base.fields());
        for (String column : CdsExportEnrolmentMapper.COLUMNS.keySet()) {
            var field = events.fields().get(new CdsExportNativeFieldKeyDto("demographic", current[0].recordKey(), column));
            if (field == null) scope();
            fields.put(column, new CdsExportDemographicMappingDto.Assessment(
                    CdsExportDemographicMappingDto.Presence.valueOf(field.presence().name()),
                    CdsExportDemographicMappingDto.Outcome.valueOf(field.outcome().name()), field.destination(), field.rule()));
        }
        var mapped = new CdsExportDemographicMappingDto(base.patient(), base.names(), base.birthDate(), base.healthCard(),
                base.chartNumber(), base.administrativeGender(), base.addresses(), base.phones(), base.officialLanguage(), base.spokenLanguage(),
                base.email(), base.personStatus(), base.enumeratedStatus(), base.personStatusDate(), base.sin(), fields,
                base.primaryPhysician(), base.relatedFields());
        var note = notes.read(lease, patient, tables, source.manifest(),
                bounds.fields(), bounds.bytes(), control);
        if (!note.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || note.rows() != source.capture().tables().get("demographiccust").rows()) scope();
        var result = new CdsExportDemographicEnrolmentProjectionDto(source.manifest().plaintextSha256(), mapped,
                events.sourceRows(), note.rows(), note.value(), events.events(), events.fields());
        captures.authorizeSources(lease, patient, tables, control); patient.require(lease); control.checkpoint(); return result;
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_ENROLMENT_PROJECTION_SCOPE_INVALID"); }
    private static void limit() throws IOException { throw new IOException("CDS_EXPORT_ENROLMENT_HISTORY_RESOURCE_LIMIT"); }
}
