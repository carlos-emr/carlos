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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the explicit, versioned CARLOS allergy markers. Unrelated residual content is
 * retained by the normal source pipeline without being materialized here. Invalid or duplicate
 * known markers block the clinical unit; they never silently fall back to an active allergy.
 */
final class CdsAllergyResidualReader {
    private static final Set<String> NAMESPACES = Set.of("cds", "cds_dt");
    private static final Set<String> LEAVES = Set.of("Name", "DataType", "Content");
    private static final Set<String> KNOWN =
            Set.of(
                    CdsNativeExtensionContract.ALLERGY_ARCHIVED,
                    CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET,
                    CdsNativeExtensionContract.ALLERGY_ONSET_OF_REACTION);
    private static final int MAXIMUM_NAME =
            KNOWN.stream().mapToInt(String::length).max().orElseThrow();
    private final CdsFieldParser fields;

    CdsAllergyResidualReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    record NativeState(boolean archived, String ageOfOnset, String onsetOfReaction) {}

    NativeState read(CdsRecordDto record) throws IOException {
        if (!record.section().equals("AllergiesAndAdverseReactions"))
            throw new IllegalArgumentException("CDS_ALLERGY_SECTION_REQUIRED");
        var state = new State(record);
        try {
            fields.forEachField(
                    record,
                    field -> {
                        try {
                            state.accept(field);
                        } catch (IOException invalid) {
                            throw new UncheckedIOException(invalid);
                        }
                    });
            state.finish();
            return new NativeState(
                    Boolean.TRUE.equals(state.archived), state.ageOfOnset, state.onsetOfReaction);
        } catch (UncheckedIOException invalid) {
            throw invalid.getCause();
        }
    }

    private final class State {
        private final CdsRecordDto record;
        private String group;
        private final Map<String, CdsFieldDto> values = new HashMap<>();
        private Boolean archived;
        private String ageOfOnset;
        private String onsetOfReaction;
        private final Set<String> seen = new java.util.HashSet<>();

        private State(CdsRecordDto record) {
            this.record = record;
        }

        private void accept(CdsFieldDto field) throws IOException {
            if (!NAMESPACES.contains(field.namespace())) return;
            int slash = field.path().lastIndexOf('/');
            if (slash < 0) return;
            String parent = field.path().substring(0, slash);
            if (!CdsFieldParser.unindexed(parent).equals("ResidualInfo/DataElement")) return;
            String name = CdsFieldParser.unindexed(field.path().substring(slash + 1));
            if (!LEAVES.contains(name)) return;
            if (!parent.equals(group)) {
                finish();
                group = parent;
                values.clear();
            }
            if (!field.kind().equals("TEXT") || values.putIfAbsent(name, field) != null) invalid();
        }

        private void finish() throws IOException {
            var name = values.get("Name");
            if (name == null) return;
            String text;
            try {
                text = fields.readToken(record, name, MAXIMUM_NAME);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                fields.verifyLogicalValue(record, name);
                return; // A longer logical name cannot equal any exact known marker.
            }
            if (!KNOWN.contains(text)) return;
            boolean archive = CdsNativeExtensionContract.ALLERGY_ARCHIVED.equals(text);
            if (!seen.add(text)
                    || !values.containsKey("DataType")
                    || !values.containsKey("Content")) throw invalidValue(archive);
            String type =
                    archive
                            ? CdsNativeExtensionContract.BOOLEAN_TYPE
                            : CdsNativeExtensionContract.STRING_TYPE;
            if (!type.equals(token(values.get("DataType"), 7, archive)))
                throw invalidValue(archive);
            String value = token(values.get("Content"), 9, archive);
            if (archive) {
                archived =
                        switch (value) {
                            case "true", "1" -> true;
                            case "false", "0" -> false;
                            default -> throw invalidValue(true);
                        };
            } else if (CdsNativeExtensionContract.ALLERGY_AGE_OF_ONSET.equals(text)) {
                if (value.isEmpty() || value.codePointCount(0, value.length()) > 4)
                    throw invalidValue(false);
                ageOfOnset = value;
            } else {
                onsetOfReaction =
                        CdsNativeExtensionContract.ALLERGY_ONSET_VALUES.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(value))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElseThrow(() -> invalidValue(false));
            }
            values.clear();
        }

        private String token(CdsFieldDto field, int maximum, boolean archive) throws IOException {
            try {
                return fields.readToken(record, field, maximum);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                throw invalidValue(archive);
            }
        }
    }

    private static CdsClinicalException invalidValue(boolean archive) {
        return new CdsClinicalException(
                archive
                        ? "CDS_ALLERGY_ARCHIVE_STATE_REQUIRES_REVIEW"
                        : "CDS_ALLERGY_NATIVE_FIELD_REQUIRES_REVIEW",
                "ResidualInfo");
    }

    private static void invalid() {
        throw invalidValue(false);
    }
}
