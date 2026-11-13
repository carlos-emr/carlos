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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the complete native prevention row a CARLOS sender carries in
 * {@code CARLOS.Prevention.Source.v1} beside an Immunizations entry (#6198). Only the columns the
 * importer restores are read: the free-text administering provider (the prevention form's
 * {@code providerName} extension, shown only for the "Other" provider {@code -1}), the reminder
 * date ({@code next_date}) and the native status that binds the carrier to this record's
 * RefusedFlag. A malformed or duplicated carrier requires review; it never falls back to a
 * default. Every other residual value stays opaque protected source.
 */
final class CdsImmunizationResidualReader {
    private final CdsFieldParser fields;

    CdsImmunizationResidualReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /**
     * The sender's native prevention values, exactly as captured (SQL NULL as null).
     *
     * @param providerNo {@code preventions.provider_no}; {@code -1} is the form's "Other" provider
     * @param providerNames every non-empty {@code providerName} extension value; empty rows, which
     *     the form saves for every unfilled input, are not facts
     * @param nextDate {@code preventions.next_date} as captured ({@code yyyy-MM-dd})
     * @param refused {@code preventions.refused}: 0 given, 1 refused, 2 ineligible, 3 completed
     *     externally
     */
    record SourceContext(
            String providerNo, List<String> providerNames, String nextDate, String refused) {
        SourceContext {
            providerNames = List.copyOf(providerNames);
        }

        @Override
        public String toString() {
            return "CdsImmunizationResidualReader.SourceContext[protected]";
        }
    }

    /** @return the carrier's restorable values, or null when the record carries none */
    SourceContext read(CdsRecordDto record) throws IOException {
        if (!record.section().equals("Immunizations"))
            throw new IllegalArgumentException("CDS_IMMUNIZATION_SECTION_REQUIRED");
        var state = new State(record);
        try {
            fields.forEachField(
                    record,
                    field -> {
                        try {
                            state.accept(field);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
            state.finish();
            return state.source;
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private final class State {
        private final CdsRecordDto record;
        private final Map<String, CdsFieldDto> values = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private String group;
        private SourceContext source;

        State(CdsRecordDto record) {
            this.record = record;
        }

        void accept(CdsFieldDto field) throws IOException {
            if (!Set.of("cds", "cds_dt").contains(field.namespace())) return;
            int slash = field.path().lastIndexOf('/');
            if (slash < 0) return;
            String parent = field.path().substring(0, slash);
            if (!CdsFieldParser.unindexed(parent).equals("ResidualInfo/DataElement")) return;
            String leaf = CdsFieldParser.unindexed(field.path().substring(slash + 1));
            if (!Set.of("Name", "DataType", "Content").contains(leaf)) return;
            if (!parent.equals(group)) {
                finish();
                values.clear();
                group = parent;
            }
            if (!field.kind().equals("TEXT") || values.putIfAbsent(leaf, field) != null) invalid();
        }

        void finish() throws IOException {
            var descriptor = values.get("Name");
            if (descriptor == null) return;
            String name;
            try {
                name = fields.readToken(record, descriptor, 64);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                fields.verifyLogicalValue(record, descriptor);
                return; // A longer logical name cannot equal the exact carrier name.
            }
            if (!CdsNativeExtensionContract.PREVENTION_SOURCE_CONTEXT.equals(name)) return;
            if (!seen.add(name) || !values.keySet().equals(Set.of("Name", "DataType", "Content")))
                invalid();
            if (!CdsNativeExtensionContract.STRING_TYPE.equals(token(values.get("DataType"), 7)))
                invalid();
            String content =
                    token(values.get("Content"), CdsSourceContextParser.MAXIMUM_CHARACTERS);
            try {
                source = sourceContext(content);
            } catch (IllegalArgumentException malformed) {
                throw invalidValue();
            }
            values.clear();
        }

        private String token(CdsFieldDto field, int maximum) throws IOException {
            try {
                return fields.readToken(record, field, maximum);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                throw invalidValue();
            }
        }
    }

    private static SourceContext sourceContext(String content) {
        var root = CdsSourceContextParser.parse(content);
        var parent =
                CdsSourceContextParser.row(
                        root, "prevention", "provider_no", "next_date", "refused");
        var extensions = root.get("extensions");
        if (extensions == null || !extensions.isArray()) throw malformed();
        var providerNames = new ArrayList<String>();
        for (var extension : extensions) {
            var row = CdsSourceContextParser.element(extension, "keyval", "val");
            String key = CdsSourceContextParser.text(row, "keyval"),
                    value = CdsSourceContextParser.text(row, "val");
            if ("providerName".equals(key) && value != null && !value.isEmpty())
                providerNames.add(value);
        }
        return new SourceContext(
                CdsSourceContextParser.text(parent, "provider_no"),
                providerNames,
                CdsSourceContextParser.text(parent, "next_date"),
                CdsSourceContextParser.text(parent, "refused"));
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
    }

    private static CdsClinicalException invalidValue() {
        return new CdsClinicalException(
                "CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
    }

    private static void invalid() {
        throw invalidValue();
    }
}
