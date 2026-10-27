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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Bounded immutable copy into one parse attempt; reviewed master files and original spools are never changed. */
public final class CdsCorrectionFiles {
    private CdsCorrectionFiles() { }

    public static CdsCorrectionSetDto stage(CdsCorrectionSetDto master, Path parseSpool) throws IOException {
        Path source = CdsPaths.within(master.manifestFile(), master.manifestFile().getParent());
        byte[] bytes = read(source, 1024 * 1024);
        if (!CdsPaths.hex(CdsPaths.sha256().digest(bytes)).equals(master.sha256())) throw failure();
        var factory = com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        JsonNode document = JsonMapper.builder(factory)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(bytes);
        if (document == null || !document.isObject() || document.path("version").asInt(-1) != 1
                || !master.acceptedXmlSha256().equals(document.path("acceptedXmlSha256").asText())
                || !document.path("corrections").isArray() || document.path("corrections").size() < 1
                || document.path("corrections").size() > 128) throw failure();
        Path directory = parseSpool.resolve("corrections");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) CdsPaths.privateDirectory(directory, parseSpool);
        else if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) throw failure();
        Set<String> seen = new HashSet<>();
        for (JsonNode correction : document.path("corrections")) {
            String name = correction.path("replacementFile").asText();
            String digest = correction.path("replacementSha256").asText();
            JsonNode length = correction.path("replacementBytes");
            if (!name.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}") || !digest.matches("[0-9a-f]{64}")
                    || !length.isIntegralNumber() || !length.canConvertToInt()
                    || length.intValue() < 0 || length.intValue() > 262144) throw failure();
            // Repeated equal value blobs are allowed, but never conflicting content under one name.
            byte[] value = read(CdsPaths.within(source.getParent().resolve(name), source.getParent()), 262144);
            if (value.length != length.intValue() || !CdsPaths.hex(CdsPaths.sha256().digest(value)).equals(digest)) throw failure();
            if (seen.add(name)) publish(directory.resolve(name), directory, value, digest);
            else if (!CdsPaths.hex(CdsPaths.sha256().digest(read(directory.resolve(name), 262144))).equals(digest)) throw failure();
        }
        Path staged = directory.resolve(master.sha256() + ".json");
        publish(staged, directory, bytes, master.sha256());
        CdsPaths.forceDirectory(directory); CdsPaths.forceDirectory(parseSpool);
        return new CdsCorrectionSetDto(staged, master.sha256(), master.acceptedXmlSha256());
    }

    private static void publish(Path path, Path root, byte[] bytes, String digest) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!CdsPaths.hex(CdsPaths.sha256().digest(read(path, bytes.length))).equals(digest)) throw failure();
            return;
        }
        try (OutputStream output = CdsPaths.newPrivateFile(path, root)) { output.write(bytes); }
        CdsPaths.forceFile(path);
    }

    private static byte[] read(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) throw failure();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maximum + 1); if (bytes.length > maximum) throw failure(); return bytes;
        }
    }
    private static IOException failure() { return CdsPaths.failure("CDS_CORRECTION_STAGE_INVALID"); }
}
