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
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDrugReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Public reference inputs are frozen before capture and bound to the actual scalar drug snapshot. */
@Service
public class CdsExportDrugReferenceSourceReader implements CdsExportNativeSourceReader {
    public static final Set<String> COLUMNS =
            Set.of("drugid", "GCN_SEQNO", "regional_identifier", "public_reference");
    private final DrugDao drugs;
    private final CdsExportDrugReferenceProjectionService projection;
    private final CdsExportDrugReferenceLookupService lookup;
    private final int patient;
    private final List<CdsExportDrugReferenceInputDto> inputs;
    private final Map<Long, CdsExportDrugReferenceDto> references;

    @Autowired
    public CdsExportDrugReferenceSourceReader(
            DrugDao drugs,
            CdsExportDrugReferenceProjectionService projection,
            CdsExportDrugReferenceLookupService lookup) {
        this(drugs, projection, lookup, 0, List.of(), Map.of());
    }

    private CdsExportDrugReferenceSourceReader(
            DrugDao drugs,
            CdsExportDrugReferenceProjectionService projection,
            CdsExportDrugReferenceLookupService lookup,
            int patient,
            List<CdsExportDrugReferenceInputDto> inputs,
            Map<Long, CdsExportDrugReferenceDto> references) {
        this.drugs = Objects.requireNonNull(drugs);
        this.projection = Objects.requireNonNull(projection);
        this.lookup = Objects.requireNonNull(lookup);
        this.patient = patient;
        this.inputs = List.copyOf(inputs);
        this.references = Map.copyOf(references);
    }

    @Override
    public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control)
            throws IOException {
        if (this.patient != 0 || patient < 1) invalid();
        control.checkpoint();
        var inputs = projection.read(patient);
        if (inputs == null || inputs.size() > 8192) invalid();
        var references = new LinkedHashMap<Long, CdsExportDrugReferenceDto>();
        var codes = new HashMap<String, CdsExportDrugReferenceDto>();
        Long previous = null;
        for (var input : inputs) {
            if (input == null || previous != null && input.drugId() <= previous) invalid();
            previous = input.drugId();
            if (input.catalogueId().signum() == 0) continue;
            control.checkpoint();
            String din = input.regionalIdentifier();
            // A catalogue selection whose stored identifier is not an eight-digit DIN, or whose
            // DIN DrugRef no longer knows (a product removed from the installed dictionary), has
            // no public reference to bind. The prescription is still exported, as an unqualified
            // catalogue selection: authored name and strength as stored, no DIN element, and the
            // stored identifier exactly in the medication residual (CdsExportNativeMedicationMapper).
            if (din == null || !din.matches("[0-9]{8}")) continue;
            if (!codes.containsKey(din)) codes.put(din, lookup.read(din));
            var reference = codes.get(din);
            if (reference == null) continue;
            if (!din.equals(reference.din())) invalid();
            references.put(input.drugId(), reference);
            control.checkpoint();
        }
        return new CdsExportDrugReferenceSourceReader(
                drugs, projection, lookup, patient, inputs, references);
    }

    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        control.authorizeCategory(patient, CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
    }

    @Override
    public String table() {
        return "medication_reference";
    }

    @Override
    public Set<String> columns() {
        return COLUMNS;
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.MEDICATIONS_AND_TREATMENTS);
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("drugs");
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        var fields =
                COLUMNS.stream()
                        .sorted()
                        .map(
                                name ->
                                        new CdsExportTableSchemaDto.Column(
                                                name,
                                                name.equals("public_reference")
                                                        ? "reference_json_v1"
                                                        : "reference_text_v1",
                                                "NO",
                                                null,
                                                null,
                                                null))
                        .toList();
        return new CdsExportTableSchemaDto(
                table(), "TRUSTED_PUBLIC_REFERENCE", fields, List.of("drugid"));
    }

    private void requireSnapshot(int patient) throws IOException {
        if (this.patient == 0
                || this.patient != patient
                || !inputs.equals(drugs.readCdsExportDrugReferenceInputs(patient, 8192))) invalid();
    }

    @Override
    public long count(int patient) {
        try {
            requireSnapshot(patient);
            return references.size();
        } catch (IOException failed) {
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportReviewException(
                    io.github.carlos_emr.carlos.demographic.cdsexport.support
                            .CdsExportReviewException.Reason.SOURCE_CHANGED);
        }
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        requireSnapshot(patient);
        for (var input : inputs) {
            var reference = references.get(input.drugId());
            if (reference == null) continue;
            String json = JsonMapper.builder().build().writeValueAsString(reference);
            output.row(
                    CdsExportPatientTableSourceReader.key(input.drugId()),
                    row -> {
                        row.text("GCN_SEQNO", input.catalogueId().toString());
                        row.text("drugid", Long.toString(input.drugId()));
                        row.text("public_reference", json);
                        row.text("regional_identifier", input.regionalIdentifier());
                    });
        }
    }

    private static void invalid() throws IOException {
        throw new CdsExportMappingReviewException("CDS_EXPORT_DRUG_REFERENCE_REQUIRES_REVIEW");
    }
}
