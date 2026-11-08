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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparationSeal;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparedAllergyDefaults;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reads historical, sealed native preparations from the authenticated retained index, without
 * reopening import staging or reinterpreting an old payload under today's import options.
 * Preparations carry post-option temporal/identity meanings; retained XML text alone does not.
 * A decoded value is historical evidence, never proof that the current native projection or
 * external provider/catalogue association is unchanged. No value is selected automatically.
 */
@Service
public class CdsExportRetainedPreparationReader {
    private static final int MAXIMUM = 1_048_576;
    private static final JsonMapper JSON = JsonMapper.builder(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(MAXIMUM).maxNumberLength(64).build()).build())
            .addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public record Evidence<T>(String sourceManifestSha256, String unitAddress, String preparationDigest, T value) {
        public Evidence { java.util.Objects.requireNonNull(value); }
        @Override public String toString() { return "CdsExportRetainedPreparationReader.Evidence[protected]"; }
    }
    public Evidence<CdsAllergyDto> allergy(CdsExportRetainedIndexDto index, String address) throws IOException {
        var row = verify(index, address, "AllergiesAndAdverseReactions", CdsAllergyDto.class);
        try {
            var tree = CdsPreparedAllergyDefaults.apply(JSON.readTree(row.fields().get("payload")));
            var value = JSON.treeToValue(tree, CdsAllergyDto.class);
            if (value.start() == null || value.recorded() == null) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    public Evidence<CdsMedicationNativeDto> medication(CdsExportRetainedIndexDto index, String address) throws IOException {
        var row = verify(index, address, "MedicationsAndTreatments", CdsMedicationNativeDto.class);
        try {
            var value = readRequired(row, CdsMedicationNativeDto.class);
            if (value.source() == null || value.provider() == null || value.source().written() == null || value.source().start() == null) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    public Evidence<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto> laboratory(CdsExportRetainedIndexDto index, String address) throws IOException {
        var type = io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto.class;
        var row = verify(index, address, "LaboratoryResults", type);
        try {
            var value = readRequired(row, type);
            if (value.collection() == null || value.requisition() == null) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    public Evidence<CdsImmunizationDto> immunization(CdsExportRetainedIndexDto index, String address) throws IOException {
        var type = CdsImmunizationDto.class;
        var row = verify(index, address, "Immunizations", type);
        try {
            var value = readRequired(row, type);
            if (value.date() == null) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    public Evidence<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentNativeDto> appointment(CdsExportRetainedIndexDto index, String address)throws IOException {
        var type=io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentNativeDto.class; var row=verify(index, address, "Appointments", type);
        try {
            var value=readRequired(row, type);
            if (!value.representable()||value.date()==null||value.start()==null||value.end()==null)invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        }catch (IOException|IllegalArgumentException invalid){throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    /** The importer stores each extracted Demographics/Contact as a separate native child unit. */
    public Evidence<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto> contact(CdsExportRetainedIndexDto index, String address) throws IOException {
        var type = io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto.class;
        var row = verify(index, address, "Demographics_CONTACT", type);
        try {
            var value = readRequired(row, type);
            if (!java.util.Set.of("R", "W", "C").containsAll(value.phones().keySet())
                    || value.phones().values().stream().anyMatch(phone -> phone == null || phone.number() == null)) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException malformed) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    public Evidence<CdsDemographicNativeDto> demographic(CdsExportRetainedIndexDto index, String address) throws IOException {
        var row = verify(index, address, "Demographics", CdsDemographicNativeDto.class);
        try {
            var value = readRequired(row, CdsDemographicNativeDto.class);
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    /** Historical prepared note meanings remain evidence only; current note, issue and program-access receipts are checked separately. */
    public Evidence<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto> history(
            CdsExportRetainedIndexDto index, String address,
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory category) throws IOException {
        if (category == null) invalid();
        String expectedIssue = switch (category) {
            case PERSONAL_HISTORY -> "SocHistory";
            case FAMILY_HISTORY -> "FamHistory";
            case PAST_HEALTH -> "MedHistory";
            case PROBLEM_LIST -> "Concerns";
            case RISK_FACTORS -> "RiskFactors";
            case ALERTS_AND_SPECIAL_NEEDS -> "Reminders";
            case CLINICAL_NOTES -> null;
            default -> throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
        };
        var type = io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto.class;
        var row = verify(index, address, category.wireName(), type);
        try {
            var value = readRequired(row, type);
            var dateFields = switch (category) {
                case PERSONAL_HISTORY -> java.util.Set.<String>of();
                case FAMILY_HISTORY, RISK_FACTORS -> java.util.Set.of("StartDate");
                case PAST_HEALTH -> java.util.Set.of("OnsetOrEventDate", "ProcedureDate");
                case PROBLEM_LIST -> java.util.Set.of("OnsetDate");
                case ALERTS_AND_SPECIAL_NEEDS -> java.util.Set.of("DateActive");
                case CLINICAL_NOTES -> java.util.Set.of("EventDateTime");
                default -> throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
            };
            if (!java.util.Objects.equals(expectedIssue, value.issueCode())
                    || dateFields.isEmpty() && (value.observationDate() != null || value.observationField() != null || value.observationMillis() != null)
                    || !dateFields.isEmpty() && (value.observationDate() == null || value.observationField() == null
                    || !dateFields.contains(value.observationField()))) invalid();
            return new Evidence<>(index.sourceManifestSha256(), address, row.fields().get("digest"), value);
        } catch (IOException | IllegalArgumentException invalid) { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
    }
    private static <T> T readRequired(CdsExportCapturedTextRowDto row, Class<T> type) throws IOException {
        T value = JSON.readValue(row.fields().get("payload"), type);
        if (value == null) invalid();
        return value;
    }
    private static CdsExportCapturedTextRowDto verify(CdsExportRetainedIndexDto index, String address, String category, Class<?> type) throws IOException {
        if (index == null || address == null || !address.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}:[0-9a-f]{64}")
                || !index.appliedUnits().contains(address)) invalid();
        int colon = address.indexOf(':'); String run = address.substring(0, colon), key = address.substring(colon+1);
        var unit = index.tables().getOrDefault("cds_import_unit", Map.of()).get(CdsImportKeys.hash(run, key));
        var origin = index.tables().getOrDefault("cds_import_run", Map.of()).get(run);
        var prepared = index.tables().getOrDefault("cds_import_preparation", Map.of()).get(CdsImportKeys.hash(run, key));
        if (unit == null || origin == null || prepared == null || !category.equals(unit.fields().get("category"))
                || !("COMMITTED".equals(unit.fields().get("state")) || "ALREADY_COMPLETE".equals(unit.fields().get("state")))) invalid();
        var fields = prepared.fields(); String payload = fields.get("payload");
        if (!run.equals(fields.get("run_id")) || !key.equals(fields.get("unit_key")) || !type.getName().equals(fields.get("payload_type"))
                || !run.equals(unit.fields().get("run_id")) || !key.equals(unit.fields().get("unit_key")) || !run.equals(origin.fields().get("id"))
                || !same(fields.get("source_digest"), unit.fields().get("source_digest"))
                || !same(fields.get("options_digest"), index.effectiveOptionsByUnit().getOrDefault(address, origin.fields().get("options_digest")))
                || !same(fields.get("ruleset_digest"), origin.fields().get("ruleset_digest")) || payload == null || payload.length() > MAXIMUM
                || payload.getBytes(StandardCharsets.UTF_8).length > MAXIMUM) invalid();
        for (String digest : java.util.List.of("source_digest", "options_digest", "ruleset_digest", "digest")) {
            String value = fields.get(digest); if (value == null || !value.matches("[0-9a-f]{64}")) invalid();
        }
        try {
            if (!CdsPreparationSeal.digest(fields.get("payload_type"), fields.get("source_digest"), fields.get("options_digest"),
                    fields.get("ruleset_digest"), payload).equals(fields.get("digest"))) invalid();
        } catch (IllegalArgumentException failed) { invalid(); }
        return prepared;
    }
    private static boolean same(String first, String second) { return first != null && first.equals(second); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_RETAINED_PREPARATION_INVALID"); }
}
