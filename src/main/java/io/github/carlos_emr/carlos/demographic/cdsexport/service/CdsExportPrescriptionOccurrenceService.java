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

/**
 * Bounded parent-script context, with independent membership and exact patient/child ownership.
 *
 * <p>A printed prescription has no CDS element of its own: it travels whole in a NewCategory
 * record ({@code CARLOS.Prescription.v1}). When the job does not select NewCategory the record is
 * not written; the chart is still exported, the scripts are decided {@code OPERATIONAL}
 * {@link CdsExportDemographicSupportingMapper#NOT_SELECTED_RULE} in the patient's reconciliation
 * audit and the package ReadMe counts them for the patient ({@link #NOT_EXPORTED_LABEL}).
 */
@Service
public class CdsExportPrescriptionOccurrenceService {
    /** ReadMe label counting a patient's printed prescriptions when NewCategory is not selected. */
    public static final String NOT_EXPORTED_LABEL =
            "Printed prescription records ("
                    + CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE
                    + ")";

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

    /** Receives each printed prescription of a chart, in the order of the script numbers. */
    @FunctionalInterface
    public interface Sources {
        void accept(Source source) throws IOException;
    }

    /**
     * Feeds {@code visitor} every printed prescription of the chart with its public author, one
     * at a time in the order the capture holds them. No number of scripts or drugs holds a
     * chart: one script is held at a time, each checked as it is read (it is this patient's,
     * its number is its row key, and the keys rise, so no script repeats), and every drug row
     * is checked to be this patient's in a pass that keeps none. The providers are the clinic's
     * rows, held together because any script may name any of them.
     *
     * @return the number of scripts visited
     */
    public long forEachSource(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Sources visitor)
            throws IOException {
        Objects.requireNonNull(visitor);
        if (!tables.contains("prescription")) return 0;
        patient.require(lease);
        if (!tables.equals(source.capture().tables().keySet())
                || !lease.runId().equals(source.capture().runId())
                || lease.epoch() != source.capture().epoch()
                || patient.patient() != source.capture().patient()) invalid();
        control.authorizeCategory(patient.patient(), CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
        if (patient.patient() < 1 || !tables.contains("drugs")) invalid();
        String owner = Integer.toString(patient.patient());
        // The scripts are visited whether or not NewCategory is selected: the operator's
        // category choice never holds a chart. The inventory accounts them as not exported
        // when their record is not written.
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        if (source.capture().tables().get("prescription").rows() > 0)
            read(
                    lease,
                    patient,
                    tables,
                    source,
                    bounds,
                    control,
                    "provider",
                    provider -> {
                        String id = provider.fields().get("provider_no");
                        if (!provider.fields()
                                        .keySet()
                                        .equals(
                                                io.github.carlos_emr.carlos.commn.dto
                                                        .CdsExportProviderRowDto.COLUMNS)
                                || id == null
                                || !CdsExportPublicProviderSourceReader.key(id)
                                        .equals(provider.recordKey())
                                || providers.putIfAbsent(id, provider) != null) invalid();
                    });
        read(lease, patient, tables, source, bounds, control, "drugs", row -> requireDrug(owner, row));
        var columns = Set.copyOf(CdsExportPatientTableReadSupport.Plan.PRESCRIPTIONS.columns());
        String[] previous = {null};
        return read(
                lease,
                patient,
                tables,
                source,
                bounds,
                control,
                "prescription",
                parent -> {
                    requireScript(owner, columns, parent);
                    // The capture's row keys rise: a script that repeats is refused.
                    if (previous[0] != null && previous[0].compareTo(parent.recordKey()) >= 0)
                        invalid();
                    previous[0] = parent.recordKey();
                    String id = parent.fields().get("provider_no");
                    // A script whose provider number has no provider record (migrated data) is
                    // exported without public author values; the stored number is in the
                    // record's own fields.
                    var author = id == null || id.isEmpty() ? null : providers.get(id);
                    visitor.accept(new Source(parent, author));
                });
    }

    /** One pass over one captured table; every row the capture states is handed to {@code use}. */
    private long read(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            String table,
            CdsExportCapturedTextRowsReader.Rows use)
            throws IOException {
        if (!tables.contains(table)) invalid();
        long[] found = {0};
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
                            found[0]++;
                            use.accept(row);
                        });
        if (!captured.equals(source.capture())
                || found[0] != source.capture().tables().get(table).rows()) invalid();
        return found[0];
    }

    private static void requireScript(
            String owner, Set<String> columns, CdsExportCapturedTextRowDto row) throws IOException {
        if (!row.table().equals("prescription")
                || !row.fields().keySet().equals(columns)
                || !owner.equals(row.fields().get("demographic_no"))) invalid();
        long id = id(row.fields().get("script_no"));
        if (!CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())) invalid();
    }

    private static void requireDrug(String owner, CdsExportCapturedTextRowDto row)
            throws IOException {
        // A drug whose script_no names no script of this patient (migrated data) is not
        // held: the stored number travels exactly with the drug's own row in
        // CARLOS.Medication.Source.v1, and no script record is invented for it.
        if (!row.table().equals("drugs") || !owner.equals(row.fields().get("demographic_no")))
            invalid();
    }

    /** Called independently of the mapper and its receipts. No absent/cross-patient parent is accepted. */
    public static void requireOwnership(
            int patient,
            List<CdsExportCapturedTextRowDto> parents,
            List<CdsExportCapturedTextRowDto> drugs)
            throws IOException {
        if (patient < 1) invalid();
        String owner = Integer.toString(patient);
        var columns = Set.copyOf(CdsExportPatientTableReadSupport.Plan.PRESCRIPTIONS.columns());
        var ids = new HashSet<String>();
        for (var row : parents) {
            requireScript(owner, columns, row);
            if (!ids.add(row.fields().get("script_no"))) invalid();
        }
        for (var row : drugs) requireDrug(owner, row);
    }

    public static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            Source source) {
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
        if (!CdsExportNativeFieldAssessments.legal(json, Integer.MAX_VALUE)) invalid();
        xml.element("cds", "CategoryName", "CARLOS.NativePrescription.v1");
        xml.element("cds", "CategoryDescription", "Native printed prescription and source context");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml, "CARLOS.Prescription.v1", "string", CdsExportScreeningMapper.tokenSafe(json));
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
