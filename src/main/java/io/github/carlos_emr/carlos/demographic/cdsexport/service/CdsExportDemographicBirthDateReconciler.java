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

import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.io.*;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.*;

/**
 * One birth-date decision within the larger demographic assembly. The verified prepared
 * date anchors unchanged imports, including synthetic teaching shifts and accepted source
 * corrections. A later native calendar edit supplies its current date without applying the
 * old shift again. A calendar edit supersedes a source offset. This decision does
 * not qualify other demographic fields, identities, history or a complete clinical export.
 */
public final class CdsExportDemographicBirthDateReconciler {
    private static final String PATH = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/DateOfBirth[1]";
    private static final List<String> COLUMNS =
            List.of("year_of_birth", "month_of_birth", "date_of_birth");

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            CdsExportRetainedFieldDto sourceField,
            String sourceLexical,
            String wireValue,
            String rule,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            fields = Map.copyOf(fields);
        }

        public boolean representable() {
            return wireValue != null
                    && fields.values().stream()
                            .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        }

        /** The outer demographic writer still verifies all source IDs and keeps the values scope active. */
        public List<CdsExportRetainedSectionWriter.Change> changes() throws IOException {
            if (!representable())
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_BIRTH_DATE_REQUIRES_REVIEW");
            return wireValue.equals(sourceLexical)
                    ? List.of()
                    : List.of(
                            new CdsExportRetainedSectionWriter.Change(
                                    "DateOfBirth[1]",
                                    Set.of(sourceField.id()),
                                    xml -> xml.text(wireValue)));
        }

        @Override
        public String toString() {
            return "CdsExportDemographicBirthDateReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto witness,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(values);
        Objects.requireNonNull(checkpoint);
        values.requireActive();
        checkpoint.run();
        var evidence =
                new CdsExportDemographicProjectionEvidence()
                        .verify(patient, address, index, checked, current, witness, checkpoint);
        var parentFields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.DEMOGRAPHICS,
                                address,
                                index,
                                checked,
                                allFields,
                                checkpoint);
        var candidates =
                parentFields.stream().filter(field -> PATH.equals(field.sourcePath())).toList();
        if (candidates.size() != 1)
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_BIRTH_SOURCE_REQUIRED");
        var field = candidates.getFirst();
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", "Demographics")
                        .child("cds", "DateOfBirth");
        if (!"cds".equals(field.namespace())
                || !field.valueType()
                        .equals("{" + type.getTypeNamespace() + "}" + type.getTypeName()))
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_BIRTH_SOURCE_INVALID");
        String lexical = read(values, field, checkpoint), wire = null, rule;
        var projection = evidence.groups().get(CdsExportDemographicProjectionEvidence.Group.BIRTH);
        LocalDate nativeDate = nativeDate(current),
                prepared = LocalDate.parse(evidence.preparation().birthDate());
        if (!projection.preparedProjectionProven())
            rule = "PREPARED_NATIVE_BIRTH_PROJECTION_NOT_PROVEN";
        else if (nativeDate == null) rule = "CURRENT_NATIVE_BIRTH_DATE_REQUIRES_REVIEW";
        else {
            try {
                var decision =
                        new CdsExportRetainedCalendarMapper()
                                .map(
                                        lexical,
                                        prepared,
                                        nativeDate,
                                        projection.unchanged(),
                                        "DateOfBirth");
                wire = decision.lexical();
                rule =
                        switch (decision.rule()) {
                            case PREPARED_EFFECTIVE -> "VERIFIED_PREPARED_EFFECTIVE_BIRTH_DATE";
                            case UNCHANGED_SOURCE -> "VERIFIED_UNCHANGED_SOURCE_BIRTH_DATE";
                            case EQUIVALENT_NATIVE_CALENDAR ->
                                    "VERIFIED_EQUIVALENT_NATIVE_BIRTH_CALENDAR";
                            case CURRENT_NATIVE -> "EXACT_CURRENT_NATIVE_BIRTH_DATE";
                            case CURRENT_NATIVE_SUPERSEDES_SOURCE_OFFSET ->
                                    "EDITED_BIRTH_DATE_SUPERSEDES_SOURCE_OFFSET";
                        };
            } catch (CdsClinicalException | DateTimeException | ArithmeticException invalid) {
                throw new IOException("CDS_EXPORT_DEMOGRAPHIC_BIRTH_SOURCE_INVALID");
            }
        }
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (String column : COLUMNS) {
            String value = current.fields().get(column);
            var presence =
                    value == null
                            ? Presence.SQL_NULL
                            : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
            fields.put(
                    new CdsExportNativeFieldKeyDto("demographic", current.recordKey(), column),
                    new CdsExportFieldAssessmentDto(
                            presence,
                            wire == null ? Outcome.REQUIRES_REVIEW : Outcome.MAPPED,
                            "DateOfBirth",
                            rule));
        }
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(), address, field, lexical, wire, rule, fields);
    }

    private static LocalDate nativeDate(CdsExportCapturedTextRowDto row) {
        String year = row.fields().get("year_of_birth"),
                month = row.fields().get("month_of_birth"),
                day = row.fields().get("date_of_birth");
        if (year == null
                || !year.matches("[0-9]{4}")
                || month == null
                || !month.matches("[0-9]{1,2}")
                || day == null
                || !day.matches("[0-9]{1,2}")) return null;
        try {
            var value =
                    LocalDate.of(
                            Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return value.getYear() < 1 ? null : value;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    private static String read(
            CdsExportRetainedSectionWriter.Values values,
            CdsExportRetainedFieldDto field,
            Runnable checkpoint)
            throws IOException {
        var result = new StringBuilder();
        char[] block = new char[128];
        try (Reader input = values.text(field)) {
            int count;
            while ((count = input.read(block)) != -1) {
                checkpoint.run();
                if (count == 0 || result.length() + count > 64)
                    throw new IOException("CDS_EXPORT_DEMOGRAPHIC_BIRTH_SOURCE_INVALID");
                result.append(block, 0, count);
            }
        } finally {
            Arrays.fill(block, '\0');
        }
        return result.toString();
    }
}
