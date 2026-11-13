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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.*;
import java.util.*;

/** Exact versioned CPP carriers. Invalid known values never default to an active note. */
final class CdsHistoryResidualReader {
    private static final Set<String> KNOWN =
            Set.of(
                    CdsNativeExtensionContract.HISTORY_ARCHIVED,
                    CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE,
                    CdsNativeExtensionContract.HISTORY_NARRATIVE);
    private static final Set<String> CPP =
            Set.of(
                    "PersonalHistory",
                    "FamilyHistory",
                    "PastHealth",
                    "ProblemList",
                    "RiskFactors",
                    "AlertsAndSpecialNeeds");
    private final CdsFieldParser fields;

    CdsHistoryResidualReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /**
     * @param personalNarrative the PersonalHistory narrative carrier, or null
     * @param narrative the narrative carrier of another CPP item whose text did not fit its
     *     description element, or null; the persister uses it only when that element is absent
     */
    record NativeState(boolean archived, CdsFieldDto personalNarrative, CdsFieldDto narrative) {}

    NativeState read(CdsRecordDto record) throws IOException {
        if (!CPP.contains(record.section())) return new NativeState(false, null, null);
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
            return new NativeState(
                    Boolean.TRUE.equals(state.archived), state.personalNarrative, state.narrative);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private final class State {
        private final CdsRecordDto record;
        private final Map<String, CdsFieldDto> values = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private String group;
        private Boolean archived;
        private CdsFieldDto personalNarrative;
        private CdsFieldDto narrative;

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
                return;
            }
            if (!KNOWN.contains(name)) return;
            if (!seen.add(name) || !values.keySet().equals(Set.of("Name", "DataType", "Content")))
                invalid();
            boolean archive = name.equals(CdsNativeExtensionContract.HISTORY_ARCHIVED);
            String type = fields.readToken(record, values.get("DataType"), 7);
            if (!type.equals(
                    archive
                            ? CdsNativeExtensionContract.BOOLEAN_TYPE
                            : CdsNativeExtensionContract.STRING_TYPE)) invalid();
            if (archive) {
                String value = fields.readToken(record, values.get("Content"), 5);
                archived =
                        switch (value) {
                            case "true", "1" -> true;
                            case "false", "0" -> false;
                            default -> throw invalidValue();
                        };
            } else if (name.equals(CdsNativeExtensionContract.HISTORY_PERSONAL_NARRATIVE)) {
                if (!record.section().equals("PersonalHistory")) invalid();
                personalNarrative = values.get("Content");
            } else {
                if (record.section().equals("PersonalHistory")) invalid();
                narrative = values.get("Content");
            }
            values.clear();
        }
    }

    private static CdsClinicalException invalidValue() {
        return new CdsClinicalException("CDS_HISTORY_NATIVE_STATE_REQUIRES_REVIEW", "ResidualInfo");
    }

    private static void invalid() {
        throw invalidValue();
    }
}
