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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Small synthetic field spools exercise the real bounded field reader without a clinical database. */
final class CdsClinicalFixture {
    private CdsClinicalFixture() { }

    static CdsRecordDto record(Path directory, String section, Map<String, String> source) throws IOException {
        Path privateDirectory = Files.createTempDirectory(directory, "clinical-");
        Path fields = privateDirectory.resolve("fields.jsonl");
        Path values = privateDirectory.resolve("values.bin");
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        StringBuilder metadata = new StringBuilder();
        MessageDigest recordDigest;
        try { recordDigest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        ObjectMapper json = new ObjectMapper();
        for (var field : source.entrySet()) {
            byte[] content = field.getValue().getBytes(StandardCharsets.UTF_8);
            String path = java.util.Arrays.stream(field.getKey().split("/"))
                    .map(part -> part.startsWith("@") || part.contains("[") ? part : part + "[1]")
                    .collect(java.util.stream.Collectors.joining("/"));
            boolean attribute = path.substring(path.lastIndexOf('/') + 1).startsWith("@");
            CdsFieldDto descriptor = new CdsFieldDto(path, attribute ? "" : "cds",
                    attribute ? "ATTRIBUTE" : "TEXT", blob.size(), content.length, digest(content));
            metadata.append(json.writeValueAsString(descriptor)).append('\n');
            recordDigest.update(CdsFieldParser.identity(descriptor));
            blob.write(content);
        }
        Files.writeString(fields, metadata, StandardCharsets.UTF_8);
        Files.write(values, blob.toByteArray());
        return new CdsRecordDto(section, 1, "/OmdCds/PatientRecord/" + section + "[1]",
                fields, 0, Files.size(fields), values, HexFormat.of().formatHex(recordDigest.digest()), source.size());
    }

    private static String digest(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static CdsRecordDto corrected(CdsRecordDto original, String path, String replacement) throws IOException {
        CdsFieldDto[] found = new CdsFieldDto[1];
        new CdsFieldParser().forEachField(original, field -> {
            if (CdsFieldParser.unindexed(field.path()).equals(path)) found[0] = field;
        });
        if (found[0] == null) throw new IllegalArgumentException("No correction target");
        CdsFieldDto field = found[0];
        Path folder = Files.createTempDirectory(original.fieldsFile().getParent(), "correction-");
        byte[] value = replacement.getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("replacement.txt"), value);
        ObjectMapper json = new ObjectMapper();
        var document = json.createObjectNode().put("version", 1).put("acceptedXmlSha256", "a".repeat(64));
        document.putArray("corrections").addObject().put("sourceXPath", original.sourceXPath() + "/" + field.path())
                .put("namespace", field.namespace()).put("kind", field.kind()).put("schemaType", field.schemaType())
                .put("whitespace", field.whitespace()).put("originalSha256", field.sha256()).put("originalLength", field.length())
                .put("replacementFile", "replacement.txt").put("replacementSha256", digest(value)).put("replacementBytes", value.length);
        byte[] bytes = json.writeValueAsBytes(document);
        Path manifest = Files.write(folder.resolve("manifest.json"), bytes);
        return original.withCorrections(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto(
                manifest, digest(bytes), "a".repeat(64)));
    }
}
