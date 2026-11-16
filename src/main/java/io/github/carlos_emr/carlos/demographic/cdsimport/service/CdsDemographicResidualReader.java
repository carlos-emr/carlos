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

import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds what a CARLOS sender carries in its native demographic record: the exact patient note,
 * and the exact base-row columns and {@code demographicExt} rows it could not write exactly
 * ({@link #carried}).
 *
 * <p>When NoteAboutPatient cannot hold the stored note exactly (a note longer than the element,
 * a character XML cannot carry, a legacy frame), a CARLOS export writes the element by its
 * documented rule and the whole stored column as {@code note.content} of the {@code
 * CARLOS.Demographic.v1} document, in a NewCategory record of the same patient file (category
 * {@code CARLOS.NativeDemographic.v1}). A CARLOS export writes its current record after any
 * retained copy of an earlier sender's, so the last such record in document order is the one
 * used.
 *
 * <p>This reader only locates and decodes that member. It restores nothing: the caller proves
 * the column against the element first ({@link CdsDemographicDetailsService}). Records are read
 * from the patient file's own sealed parse (its records manifest is checked against the parse
 * seal), and the document is read as a stream, so only the note is ever held.
 */
final class CdsDemographicResidualReader {
    private final CdsFieldParser fields;

    CdsDemographicResidualReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /**
     * @param column the stored note column the record carries, or null when the patient file
     *     has such a record but its note cannot be read as the version 1 document's text
     */
    record Note(String column) {
        @Override
        public String toString() {
            return "CdsDemographicResidualReader.Note[protected]";
        }
    }

    /**
     * The base-row columns and property rows of the sending chart's own record.
     *
     * @param columns by column, the exact stored value of each restorable column the record
     *     carries (a null value is SQL NULL); a column the record does not carry is absent
     * @param properties the carried {@code demographicExt} rows in record order, or null when the
     *     record's property list cannot be read (a value longer than any restorable text)
     * @param readable false when the record exists but is not a readable version 1 document
     */
    record Carried(Map<String, String> columns, List<CarriedProperty> properties, boolean readable) {
        static final Carried NONE = new Carried(Map.of(), List.of(), true);

        @Override
        public String toString() {
            return "CdsDemographicResidualReader.Carried[protected]";
        }
    }

    /** One carried {@code demographicExt} row: its key, value and flag as stored. */
    record CarriedProperty(String key, String value, String hidden) {
        @Override
        public String toString() {
            return "CdsDemographicResidualReader.CarriedProperty[protected]";
        }
    }

    /**
     * Reads the restorable columns ({@code columns}) and the properties of the last native
     * demographic record of this Demographics record's patient file. A CARLOS export writes that
     * record after every retained copy of an earlier sender's, and always writes one when it
     * re-exports such a copy, so the last record is the sending chart's own.
     *
     * @return {@link Carried#NONE} when the file has no such record
     */
    Carried carried(CdsRecordDto demographics, Set<String> columns) throws IOException {
        CdsRecordDto record = last(demographics);
        if (record == null) return Carried.NONE;
        CdsFieldDto content;
        try {
            content = content(record);
        } catch (IllegalArgumentException malformed) {
            return new Carried(Map.of(), List.of(), false);
        }
        if (content == null) return new Carried(Map.of(), List.of(), false);
        var wanted = new java.util.HashSet<String>();
        for (String column : columns) wanted.add("demographic/" + column);
        Map<String, String> values = new java.util.TreeMap<>();
        try {
            JsonNode root;
            try (var token = fields.openToken(record, content)) {
                root = CdsSourceContextParser.read(token, wanted);
            }
            JsonNode demographic = root.get("demographic");
            if (demographic != null) {
                if (!demographic.isObject()) return new Carried(Map.of(), List.of(), false);
                for (var it = demographic.fields(); it.hasNext(); ) {
                    var entry = it.next();
                    if (!entry.getValue().isTextual() && !entry.getValue().isNull())
                        return new Carried(Map.of(), List.of(), false);
                    values.put(
                            entry.getKey(),
                            entry.getValue().isNull() ? null : entry.getValue().textValue());
                }
            }
        } catch (IllegalArgumentException malformed) {
            return new Carried(Map.of(), List.of(), false);
        }
        // A separate pass: a property value longer than any restorable text leaves the rows
        // unread without losing the columns read above.
        List<CarriedProperty> rows = new java.util.ArrayList<>();
        try {
            try (var token = fields.openToken(record, content)) {
                CdsSourceContextParser.read(
                        token,
                        Set.of(),
                        Map.of("properties", Set.of("key", "value", "hidden")),
                        (array, element) -> {
                            JsonNode row = CdsSourceContextParser.element(element, "key", "value");
                            JsonNode hidden = element.get("hidden");
                            if (hidden != null && !hidden.isTextual() && !hidden.isNull())
                                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                            rows.add(
                                    new CarriedProperty(
                                            CdsSourceContextParser.text(row, "key"),
                                            CdsSourceContextParser.text(row, "value"),
                                            hidden == null
                                                    ? "0"
                                                    : hidden.isNull() ? null : hidden.textValue()));
                        });
            }
        } catch (IllegalArgumentException unreadable) {
            return new Carried(java.util.Collections.unmodifiableMap(values), null, true);
        }
        return new Carried(
                java.util.Collections.unmodifiableMap(values),
                List.copyOf(rows),
                true);
    }

    /** The public provider columns a carried roster or care-team reference names. */
    static final List<String> PUBLIC_PROVIDER =
            List.of(
                    "provider_no",
                    "first_name",
                    "last_name",
                    "ohip_no",
                    "practitionerNo",
                    "practitionerNoType");

    /** The current roster columns the record may carry, in its order. */
    static final List<String> ROSTER =
            List.of(
                    "roster_status",
                    "roster_date",
                    "roster_termination_date",
                    "roster_termination_reason",
                    "roster_enrolled_to");

    /**
     * The sending chart's current roster state the record carries ({@code roster}): a state that
     * is not a CDS enrolment event (fee for service, a clinic-defined status, NR, RO without a
     * roster date...), whose columns no EnrolmentHistory states.
     *
     * @param columns by column, each roster column the record carries (a null value is SQL NULL);
     *     a column the record does not carry is absent
     * @param provider the enrolled provider's public columns, or null when the record names none
     * @param readable false when the record exists but its roster is not a readable version 1 member
     */
    record Roster(Map<String, String> columns, Map<String, String> provider, boolean readable) {
        static final Roster NONE = new Roster(Map.of(), null, true);

        @Override
        public String toString() {
            return "CdsDemographicResidualReader.Roster[protected]";
        }
    }

    /**
     * One carried care-team reference ({@code careTeam}): the demographiccust column, the stored
     * provider number (local to the sender) and, when the sender's captured provider row matched
     * it exactly, that row's public columns.
     */
    record CareTeamMember(String column, String providerNo, Map<String, String> provider) {
        @Override
        public String toString() {
            return "CdsDemographicResidualReader.CareTeamMember[protected]";
        }
    }

    /**
     * Reads the current roster state of the last native demographic record of this Demographics
     * record's patient file.
     *
     * @return {@link Roster#NONE} when the file has no such record or it carries no roster value
     */
    Roster roster(CdsRecordDto demographics) throws IOException {
        CdsRecordDto record = last(demographics);
        if (record == null) return Roster.NONE;
        try {
            CdsFieldDto content = content(record);
            if (content == null) return new Roster(Map.of(), null, false);
            var wanted = new java.util.HashSet<String>();
            for (String column : ROSTER) wanted.add("roster/" + column);
            wanted.add("roster/enrolledToPublicProvider");
            for (String column : PUBLIC_PROVIDER)
                wanted.add("roster/enrolledToPublicProvider/" + column);
            JsonNode root;
            try (var token = fields.openToken(record, content)) {
                root = CdsSourceContextParser.read(token, wanted);
            }
            JsonNode roster = root.get("roster");
            if (roster == null) return Roster.NONE;
            if (!roster.isObject()) return new Roster(Map.of(), null, false);
            Map<String, String> columns = new java.util.TreeMap<>();
            for (String column : ROSTER) {
                JsonNode value = roster.get(column);
                if (value == null) continue;
                if (!value.isTextual() && !value.isNull()) return new Roster(Map.of(), null, false);
                columns.put(column, value.isNull() ? null : value.textValue());
            }
            JsonNode named = roster.get("enrolledToPublicProvider");
            Map<String, String> provider = null;
            if (named != null && !named.isNull()) {
                if (!named.isObject()) return new Roster(Map.of(), null, false);
                provider = publicProvider(named);
            }
            return new Roster(
                    java.util.Collections.unmodifiableMap(columns),
                    provider == null ? null : java.util.Collections.unmodifiableMap(provider),
                    true);
        } catch (IllegalArgumentException malformed) {
            return new Roster(Map.of(), null, false);
        }
    }

    /**
     * Reads the care-team references (nurse, resident, midwife) of the last native demographic
     * record of this Demographics record's patient file.
     *
     * @return the references in record order, empty when there is none, or null when the record
     *     exists but its care team is not a readable version 1 list
     */
    List<CareTeamMember> careTeam(CdsRecordDto demographics) throws IOException {
        CdsRecordDto record = last(demographics);
        if (record == null) return List.of();
        try {
            CdsFieldDto content = content(record);
            if (content == null) return null;
            var members = new java.util.HashSet<String>(Set.of("column", "providerNo", "publicProvider"));
            for (String column : PUBLIC_PROVIDER) members.add("publicProvider/" + column);
            List<CareTeamMember> result = new java.util.ArrayList<>();
            try (var token = fields.openToken(record, content)) {
                CdsSourceContextParser.read(
                        token,
                        Set.of(),
                        Map.of("careTeam", members),
                        (array, element) -> {
                            JsonNode column = element.get("column"), number = element.get("providerNo");
                            JsonNode named = element.get("publicProvider");
                            if (column == null
                                    || !column.isTextual()
                                    || number == null
                                    || !number.isTextual()
                                    || named != null && !named.isNull() && !named.isObject())
                                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                            result.add(
                                    new CareTeamMember(
                                            column.textValue(),
                                            number.textValue(),
                                            named == null || named.isNull()
                                                    ? null
                                                    : java.util.Collections.unmodifiableMap(
                                                            publicProvider(named))));
                        });
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * One row of the record's {@code related} list: the table, the sender's own row id (local to
     * the sender, used only to pair a link with its pharmacy), and the carried columns asked for.
     */
    record RelatedRow(String table, String id, Map<String, String> fields) {
        @Override
        public String toString() {
            return "CdsDemographicResidualReader.RelatedRow[protected]";
        }
    }

    /**
     * Reads the pharmacy rows ({@code demographicPharmacy} and {@code pharmacyInfo}) of the
     * {@code related} list of the last native demographic record of this Demographics record's
     * patient file, each with the carried columns named in {@code columns} (a link's
     * {@code pharmacyID} is always read).
     *
     * @return the rows in record order, empty when there is none, or null when the record exists
     *     but its related list is not a readable version 1 list
     */
    List<RelatedRow> relatedPharmacies(CdsRecordDto demographics, Set<String> columns)
            throws IOException {
        CdsRecordDto record = last(demographics);
        if (record == null) return List.of();
        try {
            CdsFieldDto content = content(record);
            if (content == null) return null;
            var members = new java.util.HashSet<String>(Set.of("table", "id", "fields"));
            for (String column : columns) members.add("fields/" + column);
            members.add("fields/pharmacyID");
            List<RelatedRow> result = new java.util.ArrayList<>();
            try (var token = fields.openToken(record, content)) {
                CdsSourceContextParser.read(
                        token,
                        Set.of(),
                        Map.of("related", members),
                        (array, element) -> {
                            JsonNode table = element.get("table"), id = element.get("id");
                            JsonNode values = element.get("fields");
                            if (table == null
                                    || !table.isTextual()
                                    || id != null && !id.isTextual() && !id.isNull()
                                    || values != null && !values.isObject())
                                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                            if (!Set.of("demographicPharmacy", "pharmacyInfo")
                                    .contains(table.textValue())) return;
                            Map<String, String> carried = new java.util.TreeMap<>();
                            if (values != null)
                                for (var it = values.fields(); it.hasNext(); ) {
                                    var entry = it.next();
                                    if (!entry.getValue().isTextual() && !entry.getValue().isNull())
                                        throw new IllegalArgumentException(
                                                "CDS_SOURCE_CONTEXT_MALFORMED");
                                    carried.put(
                                            entry.getKey(),
                                            entry.getValue().isNull()
                                                    ? null
                                                    : entry.getValue().textValue());
                                }
                            result.add(
                                    new RelatedRow(
                                            table.textValue(),
                                            id == null || id.isNull() ? null : id.textValue(),
                                            java.util.Collections.unmodifiableMap(carried)));
                        });
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static Map<String, String> publicProvider(JsonNode named) {
        Map<String, String> provider = new java.util.TreeMap<>();
        for (String column : PUBLIC_PROVIDER) {
            JsonNode value = named.get(column);
            if (value == null) continue;
            if (!value.isTextual() && !value.isNull())
                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
            provider.put(column, value.isNull() ? null : value.textValue());
        }
        return provider;
    }

    /**
     * @return the carried note of this Demographics record's patient file, or null when the
     *     file's native demographic record (if any) carries no note
     * @throws CdsImportException when the file's records manifest is not the one its parse sealed
     */
    Note note(CdsRecordDto demographics) throws IOException {
        CdsRecordDto record = last(demographics);
        if (record == null) return null;
        try {
            CdsFieldDto content = content(record);
            if (content == null) return new Note(null);
            // "note" is named too, so a note member that is not an object is seen and reported
            // rather than read as no note at all.
            JsonNode note;
            try (var token = fields.openToken(record, content)) {
                note = CdsSourceContextParser.read(token, Set.of("note", "note/content")).get("note");
            }
            if (note == null) return null;
            if (!note.isObject()) return new Note(null);
            JsonNode text = note.get("content");
            return new Note(text != null && text.isTextual() ? text.textValue() : null);
        } catch (IllegalArgumentException malformed) {
            return new Note(null);
        }
    }

    /**
     * The last NewCategory record of this Demographics record's patient file named {@link
     * CdsNativeExtensionContract#NATIVE_DEMOGRAPHIC_CATEGORY}, read from the file's own sealed
     * parse, or null when there is none.
     */
    private CdsRecordDto last(CdsRecordDto demographics) throws IOException {
        return new CdsPatientSourceRecordReader(fields)
                .last(demographics, CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY);
    }

    private CdsFieldDto content(CdsRecordDto record) throws IOException {
        return new CdsPatientSourceRecordReader(fields)
                .content(record, CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC);
    }
}
