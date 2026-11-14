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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the exact patient note a CARLOS sender carries in its native demographic record.
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
    private static final ObjectMapper JSON = new ObjectMapper();
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
     * @return the carried note of this Demographics record's patient file, or null when the
     *     file's native demographic record (if any) carries no note
     * @throws CdsImportException when the file's records manifest is not the one its parse sealed
     */
    Note note(CdsRecordDto demographics) throws IOException {
        Path folder = demographics.valuesFile().toAbsolutePath().getParent();
        if (folder == null) return null;
        Path manifest = folder.resolve("records.jsonl"), seal = folder.resolve("parse.seal");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(seal, LinkOption.NOFOLLOW_LINKS)) return null;
        String sealed =
                JSON.readTree(CdsImportJobService.boundedFile(seal, 65536))
                        .path("recordsSha256")
                        .asText();
        MessageDigest digest = CdsPaths.sha256();
        try (InputStream input = Files.newInputStream(manifest, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        if (!CdsPaths.hex(digest.digest()).equals(sealed))
            throw new CdsImportException("PARSED_METADATA_HASH_MISMATCH");
        CdsRecordDto[] last = {null};
        try {
            fields.forEachRecord(
                    manifest,
                    listed -> {
                        if (!listed.section().equals("NewCategory")) return;
                        // Reviewed corrections are accepted once for the whole patient file.
                        CdsRecordDto record =
                                listed.corrections() == null && demographics.corrections() != null
                                        ? listed.withCorrections(demographics.corrections())
                                        : listed;
                        try {
                            if (named(record)) last[0] = record;
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        if (last[0] == null) return null;
        try {
            CdsFieldDto content = content(last[0]);
            if (content == null) return new Note(null);
            // "note" is named too, so a note member that is not an object is seen and reported
            // rather than read as no note at all.
            JsonNode note;
            try (var token = fields.openToken(last[0], content)) {
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

    private boolean named(CdsRecordDto record) throws IOException {
        try {
            return CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY.equals(
                    fields.firstToken(record, "CategoryName", 64));
        } catch (CdsInputException capacity) {
            if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
            return false; // A longer name cannot equal the exact category name.
        }
    }

    /**
     * @return the Content field of the record's one {@code CARLOS.Demographic.v1} string
     *     element, or null when it has none, several, or one of another shape
     */
    private CdsFieldDto content(CdsRecordDto record) throws IOException {
        Map<String, Map<String, CdsFieldDto>> elements = new HashMap<>();
        boolean[] malformed = {false};
        fields.forEachField(
                record,
                field -> {
                    if (!Set.of("cds", "cds_dt").contains(field.namespace())) return;
                    int slash = field.path().lastIndexOf('/');
                    if (slash < 0) return;
                    String parent = field.path().substring(0, slash);
                    if (!CdsFieldParser.unindexed(parent).equals("ResidualInfo/DataElement"))
                        return;
                    String leaf = CdsFieldParser.unindexed(field.path().substring(slash + 1));
                    if (!Set.of("Name", "DataType", "Content").contains(leaf)) return;
                    if (!field.kind().equals("TEXT")
                            || elements.computeIfAbsent(parent, ignored -> new HashMap<>())
                                            .putIfAbsent(leaf, field)
                                    != null) malformed[0] = true;
                });
        if (malformed[0]) return null;
        CdsFieldDto found = null;
        for (var element : elements.values()) {
            var descriptor = element.get("Name");
            if (descriptor == null) continue;
            try {
                if (!CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC.equals(
                        fields.readToken(record, descriptor, 64))) continue;
                if (found != null
                        || !element.keySet().equals(Set.of("Name", "DataType", "Content"))
                        || !CdsNativeExtensionContract.STRING_TYPE.equals(
                                fields.readToken(record, element.get("DataType"), 7))) return null;
                found = element.get("Content");
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                // A longer name or type is not this element's.
            }
        }
        return found;
    }
}
