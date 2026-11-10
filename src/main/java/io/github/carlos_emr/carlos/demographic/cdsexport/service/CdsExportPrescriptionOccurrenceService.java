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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded parent-script context, with independent membership and exact patient/child ownership. */
@Service
public class CdsExportPrescriptionOccurrenceService {
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportPrescriptionOccurrenceService(CdsExportNativeCaptureService captures) {
        rows = new CdsExportCapturedTextRowsReader(Objects.requireNonNull(captures));
    }

    public record Source(
            CdsExportCapturedTextRowDto prescription, CdsExportCapturedTextRowDto author) {
        public Source {
            Objects.requireNonNull(prescription);
            if (!prescription.table().equals("prescription")
                    || author != null
                            && (!author.table().equals("provider")
                                    || !Objects.equals(
                                            prescription.fields().get("provider_no"),
                                            author.fields().get("provider_no"))))
                throw new IllegalArgumentException("CDS_EXPORT_PRESCRIPTION_AUTHOR_SCOPE_INVALID");
        }

        public java.util.List<CdsExportCapturedTextRowDto> rows() {
            return author == null ? List.of(prescription) : List.of(prescription, author);
        }

        @Override
        public String toString() {
            return "CdsExportPrescriptionOccurrenceService.Source[protected]";
        }
    }

    public List<Source> sources(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!tables.contains("prescription")) return List.of();
        patient.require(lease);
        control.authorizeCategory(patient.patient(), CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
        var parents = read(lease, patient, tables, source, bounds, control, "prescription");
        var drugs = read(lease, patient, tables, source, bounds, control, "drugs");
        requireOwnership(patient.patient(), parents, drugs);
        if (!parents.isEmpty()
                && !lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(
                    "CDS_EXPORT_PRESCRIPTION_SUPPORTING_CATEGORY_REQUIRED");
        if (parents.isEmpty()) return List.of();
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var provider : read(lease, patient, tables, source, bounds, control, "provider")) {
            String id = provider.fields().get("provider_no");
            if (!provider.fields()
                            .keySet()
                            .equals(
                                    io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto
                                            .COLUMNS)
                    || id == null
                    || !CdsExportPublicProviderSourceReader.key(id).equals(provider.recordKey())
                    || providers.putIfAbsent(id, provider) != null) invalid();
        }
        var contexts = new ArrayList<Source>();
        for (var parent : parents) {
            String id = parent.fields().get("provider_no");
            var author = id == null || id.isEmpty() ? null : providers.get(id);
            if (id != null && !id.isEmpty() && author == null) invalid();
            contexts.add(new Source(parent, author));
        }
        return List.copyOf(contexts);
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
        int maximum = table.equals("provider") ? bounds.lookupRows() : bounds.nativeRows();
        if (!tables.contains(table) || source.capture().tables().get(table).rows() > maximum)
            invalid();
        var found = new ArrayList<CdsExportCapturedTextRowDto>();
        var captured =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        table,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            if (found.size() >= maximum) invalid();
                            found.add(row);
                        });
        if (!captured.equals(source.capture())
                || found.size() != source.capture().tables().get(table).rows()) invalid();
        return found;
    }

    /** Called independently of the mapper and its receipts. No absent/cross-patient parent is accepted. */
    public static void requireOwnership(
            int patient,
            List<CdsExportCapturedTextRowDto> parents,
            List<CdsExportCapturedTextRowDto> drugs)
            throws IOException {
        if (patient < 1) invalid();
        var ids = new HashSet<String>();
        for (var row : parents) {
            if (!row.table().equals("prescription")
                    || !row.fields()
                            .keySet()
                            .equals(
                                    Set.copyOf(
                                            CdsExportPatientTableReadSupport.Plan.PRESCRIPTIONS
                                                    .columns()))
                    || !Integer.toString(patient).equals(row.fields().get("demographic_no"))
                    || !ids.add(row.fields().get("script_no"))) invalid();
            long id = id(row.fields().get("script_no"));
            if (!CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())) invalid();
        }
        for (var row : drugs) {
            if (!row.table().equals("drugs")
                    || !Integer.toString(patient).equals(row.fields().get("demographic_no")))
                invalid();
            String parent = row.fields().get("script_no");
            if (parent != null && !parent.isEmpty() && !parent.equals("0") && !ids.contains(parent))
                invalid();
        }
    }

    public static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportCapturedTextRowDto row) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : source.rows()) {
            var state = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet())
                state.set(
                        column,
                        CdsExportFieldAssessmentDto.Outcome.MAPPED,
                        "NewCategory/ResidualInfo",
                        "EXACT_NATIVE_PRESCRIPTION_CONTEXT");
        }
        return Map.copyOf(result);
    }

    public static void write(Source source, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        var context = new LinkedHashMap<String, Object>();
        context.put("version", 1);
        context.put("fields", source.prescription().fields());
        context.put("publicAuthor", source.author() == null ? null : source.author().fields());
        String json = JsonMapper.builder().build().writeValueAsString(context);
        if (!CdsExportNativeFieldAssessments.legal(json, 1 << 24)) invalid();
        xml.element("cds", "CategoryName", "CARLOS.NativePrescription.v1");
        xml.element("cds", "CategoryDescription", "Native printed prescription and source context");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml, "CARLOS.Prescription.v1", "string", json);
        xml.end();
    }

    private static long id(String value) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException bad) {
            invalid();
            return 0;
        }
    }

    private static void invalid() throws IOException {
        throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                .CdsExportMappingReviewException(
                "CDS_EXPORT_PRESCRIPTION_OWNERSHIP_REQUIRES_REVIEW");
    }
}
