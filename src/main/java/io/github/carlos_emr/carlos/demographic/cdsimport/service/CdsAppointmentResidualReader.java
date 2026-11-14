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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds and reads the {@code CARLOS.Appointment.Source.v1} record a CARLOS sender writes for an
 * Appointments element.
 *
 * <p>CDS 5.2 Appointments has no ResidualInfo, so the sender carries each appointment's complete
 * stored row in a NewCategory record of the same patient file (category {@code
 * CARLOS.NativeAppointment.v1}) that names the 1-based position of its element. A CARLOS export
 * writes one such record for every element, after any retained copies of earlier ones, so the
 * record of an element is the <em>last</em> one in document order that names its position; an
 * older retained copy is never used.
 *
 * <p>This reader only locates and decodes the record. It restores nothing: the caller must prove
 * the record against the element ({@link CdsAppointmentPersister}) and reports a record it
 * cannot prove. Records are read from the patient file's own sealed parse (its records manifest
 * is checked against the parse seal); only the columns the importer can restore are returned.
 */
final class CdsAppointmentResidualReader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] COLUMNS = {
        "appointment_date",
        "start_time",
        "end_time",
        "style",
        "billing",
        "location",
        "resources",
        "urgency",
        "type",
        "bookingSource"
    };

    private final CdsFieldParser fields;

    /** Units of different patients interleave; a few files' positions are kept, least recent out. */
    private final Map<Path, Index> indices =
            Collections.synchronizedMap(
                    new LinkedHashMap<Path, Index>(8, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<Path, Index> eldest) {
                            return size() > 8;
                        }
                    });

    CdsAppointmentResidualReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    /**
     * The sender's stored values, exactly as captured (SQL NULL as null).
     *
     * @param date {@code appointment.appointment_date} ({@code yyyy-MM-dd})
     * @param start {@code appointment.start_time} ({@code HH:mm:ss})
     * @param end {@code appointment.end_time}
     * @param completeNotes the complete AppointmentNotes text when the element holds only its
     *     schema-valid form, otherwise null
     */
    record Source(
            String date,
            String start,
            String end,
            String completeNotes,
            String style,
            String billing,
            String location,
            String resources,
            String urgency,
            String type,
            String bookingSource) {
        @Override
        public String toString() {
            return "CdsAppointmentResidualReader.Source[protected]";
        }
    }

    /**
     * @param source the decoded record, or null when a record names the element (or cannot say
     *     which element it names) but is not a readable version 1 document
     */
    record Found(Source source) {}

    /**
     * @return the record naming this Appointments element, or null when the patient file has none
     * @throws CdsImportException when the file's records manifest is not the one its parse sealed
     */
    Found find(CdsRecordDto appointment) throws IOException {
        if (!appointment.section().equals("Appointments"))
            throw new IllegalArgumentException("CDS_APPOINTMENT_SECTION_REQUIRED");
        Path folder = appointment.valuesFile().toAbsolutePath().getParent();
        if (folder == null) return null;
        Path manifest = folder.resolve("records.jsonl"), seal = folder.resolve("parse.seal");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(seal, LinkOption.NOFOLLOW_LINKS)) return null;
        String sealed =
                JSON.readTree(CdsImportJobService.boundedFile(seal, 65536))
                        .path("recordsSha256")
                        .asText();
        Index index = indices.get(manifest);
        if (index == null || !index.recordsSha256.equals(sealed)) {
            index = index(manifest, sealed, appointment);
            indices.put(manifest, index);
        }
        CdsRecordDto record = index.byPosition.get(appointment.ordinal());
        if (record == null) return index.unreadable ? new Found(null) : null;
        try {
            return new Found(source(tree(content(record))));
        } catch (IllegalArgumentException malformed) {
            return new Found(null);
        }
    }

    private Index index(Path manifest, String sealed, CdsRecordDto appointment)
            throws IOException {
        MessageDigest digest = CdsPaths.sha256();
        try (InputStream input = Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        if (!CdsPaths.hex(digest.digest()).equals(sealed))
            throw new CdsImportException("PARSED_METADATA_HASH_MISMATCH");
        var byPosition = new HashMap<Long, CdsRecordDto>();
        boolean[] unreadable = {false};
        try {
            fields.forEachRecord(
                    manifest,
                    listed -> {
                        if (!listed.section().equals("NewCategory")) return;
                        // Reviewed corrections are accepted once for the whole patient file.
                        CdsRecordDto record =
                                listed.corrections() == null && appointment.corrections() != null
                                        ? listed.withCorrections(appointment.corrections())
                                        : listed;
                        try {
                            String content = content(record);
                            if (content == null) return;
                            JsonNode position = tree(content).get("appointment");
                            if (position == null
                                    || !position.isIntegralNumber()
                                    || !position.canConvertToLong()
                                    || position.longValue() < 1)
                                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                            // The last record in document order is the sender's current one.
                            byPosition.put(position.longValue(), record);
                        } catch (IllegalArgumentException malformed) {
                            unreadable[0] = true;
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        return new Index(sealed, byPosition, unreadable[0]);
    }

    private static JsonNode tree(String content) {
        if (content == null) throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
        return CdsSourceContextParser.parse(content);
    }

    /**
     * @return the Content of the record's one {@code CARLOS.Appointment.Source.v1} element, or
     *     null when the record is not an appointment source record
     * @throws IllegalArgumentException when it is one but its element is missing, repeated or not
     *     a string
     */
    private String content(CdsRecordDto record) throws IOException {
        String category;
        try {
            category = fields.firstToken(record, "CategoryName", 64);
        } catch (CdsInputException capacity) {
            if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
            return null; // A longer name cannot equal the exact category name.
        }
        if (!CdsAppointmentSourceContract.CATEGORY_NAME.equals(category)) return null;
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
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        if (state.content == null)
            throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
        return state.content;
    }

    private final class State {
        private final CdsRecordDto record;
        private final Map<String, CdsFieldDto> values = new HashMap<>();
        private String group, content;

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
            if (!field.kind().equals("TEXT") || values.putIfAbsent(leaf, field) != null)
                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
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
            if (!CdsAppointmentSourceContract.ELEMENT_NAME.equals(name)) return;
            try {
                if (content != null
                        || !values.keySet().equals(Set.of("Name", "DataType", "Content"))
                        || !CdsNativeExtensionContract.STRING_TYPE.equals(
                                fields.readToken(record, values.get("DataType"), 7)))
                    throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                content =
                        fields.readToken(
                                record,
                                values.get("Content"),
                                CdsSourceContextParser.MAXIMUM_CHARACTERS);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
            }
            values.clear();
        }
    }

    private static Source source(JsonNode root) {
        JsonNode row = CdsSourceContextParser.row(root, "fields", COLUMNS);
        JsonNode complete = root.get("complete");
        String notes = null;
        if (complete != null && !complete.isNull()) {
            if (!complete.isObject())
                throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
            JsonNode text = complete.get("AppointmentNotes");
            if (text != null) {
                if (!text.isTextual())
                    throw new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
                notes = text.textValue();
            }
        }
        return new Source(
                CdsSourceContextParser.text(row, "appointment_date"),
                CdsSourceContextParser.text(row, "start_time"),
                CdsSourceContextParser.text(row, "end_time"),
                notes,
                CdsSourceContextParser.text(row, "style"),
                CdsSourceContextParser.text(row, "billing"),
                CdsSourceContextParser.text(row, "location"),
                CdsSourceContextParser.text(row, "resources"),
                CdsSourceContextParser.text(row, "urgency"),
                CdsSourceContextParser.text(row, "type"),
                CdsSourceContextParser.text(row, "bookingSource"));
    }

    private record Index(
            String recordsSha256, Map<Long, CdsRecordDto> byPosition, boolean unreadable) {}
}
