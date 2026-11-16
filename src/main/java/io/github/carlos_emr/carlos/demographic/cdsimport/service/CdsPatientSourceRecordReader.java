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

/** Locates a patient's latest named source carrier only through its sealed records manifest. */
final class CdsPatientSourceRecordReader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CdsFieldParser fields;

    CdsPatientSourceRecordReader(CdsFieldParser fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    CdsRecordDto last(CdsRecordDto demographics, String categoryName) throws IOException {
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
        if (!MessageDigest.isEqual(
                CdsPaths.hex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                sealed.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
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
                            if (named(record, categoryName)) last[0] = record;
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        return last[0];
    }

    boolean named(CdsRecordDto record, String categoryName) throws IOException {
        try {
            return categoryName.equals(
                    fields.firstToken(record, "CategoryName", 64));
        } catch (CdsInputException capacity) {
            if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
            return false; // A longer name cannot equal the exact category name.
        }
    }

    /**
     * @return the Content field of the record's one named string
     *     element, or null when it has none, several, or one of another shape
     */
    CdsFieldDto content(CdsRecordDto record, String elementName) throws IOException {
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
                if (!elementName.equals(
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
