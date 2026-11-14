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
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable copy into one parse attempt; reviewed master files and original spools are never
 * changed. A correction manifest is read as a stream, whatever its size and however many
 * corrections it holds: a source file from another EMR needs one audited correction for each
 * value its schema refuses, and nothing counts them.
 */
public final class CdsCorrectionFiles {
    /** Strict JSON: no repeated member, nothing after the one document; the stream stays open. */
    private static final JsonMapper MANIFEST =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                                    .build())
                    .enable(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .FAIL_ON_TRAILING_TOKENS)
                    .build();

    private CdsCorrectionFiles() {}

    /**
     * A correction manifest read once, as a stream: its document (null when it is not one strict
     * JSON document) and the SHA-256 of every byte of the file. A caller compares the digest
     * before it trusts the document.
     */
    public record Manifest(JsonNode document, String sha256) {
        @Override
        public String toString() {
            return "CdsCorrectionFiles.Manifest[protected]";
        }
    }

    /** Reads a correction manifest of any size as a stream (see {@link Manifest}). */
    public static Manifest readManifest(Path path) throws IOException {
        MessageDigest hash = CdsPaths.sha256();
        JsonNode document;
        try (InputStream input =
                new DigestInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), hash)) {
            try {
                document = MANIFEST.readTree(input);
            } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
                document = null;
            }
            // The digest covers the whole file, including anything after the document.
            input.transferTo(OutputStream.nullOutputStream());
        }
        return new Manifest(document, CdsPaths.hex(hash.digest()));
    }

    public static CdsCorrectionSetDto stage(CdsCorrectionSetDto master, Path parseSpool)
            throws IOException {
        Path source = CdsPaths.within(master.manifestFile(), master.manifestFile().getParent());
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw failure();
        Manifest read = readManifest(source);
        if (!read.sha256().equals(master.sha256())) throw failure();
        JsonNode document = read.document();
        if (document == null
                || !document.isObject()
                || document.path("version").asInt(-1) != 1
                || !master.acceptedXmlSha256().equals(document.path("acceptedXmlSha256").asText())
                || !document.path("corrections").isArray()
                || document.path("corrections").size() < 1) throw failure();
        Path directory = parseSpool.resolve("corrections");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
            CdsPaths.privateDirectory(directory, parseSpool);
        else if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) throw failure();
        Set<String> seen = new HashSet<>();
        for (JsonNode correction : document.path("corrections")) {
            String name = correction.path("replacementFile").asText();
            String digest = correction.path("replacementSha256").asText();
            JsonNode length = correction.path("replacementBytes");
            if (!name.matches("[A-Za-z0-9_-]{1,128}\\.[A-Za-z0-9]{1,12}")
                    || !digest.matches("[0-9a-f]{64}")
                    || !length.isIntegralNumber()
                    || !length.canConvertToInt()
                    || length.intValue() < 0
                    || length.intValue() > 262144) throw failure();
            // Repeated equal value blobs are allowed, but never conflicting content under one name.
            byte[] value =
                    read(
                            CdsPaths.within(source.getParent().resolve(name), source.getParent()),
                            262144);
            if (value.length != length.intValue()
                    || !io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                            CdsPaths.hex(CdsPaths.sha256().digest(value)), digest))
                throw failure();
            if (seen.add(name)) publish(directory.resolve(name), directory, value, digest);
            else if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                    CdsPaths.hex(CdsPaths.sha256().digest(read(directory.resolve(name), 262144))),
                    digest)) throw failure();
        }
        Path staged = directory.resolve(master.sha256() + ".json");
        publishCopy(staged, directory, source, master.sha256());
        CdsPaths.forceDirectory(directory);
        CdsPaths.forceDirectory(parseSpool);
        return new CdsCorrectionSetDto(staged, master.sha256(), master.acceptedXmlSha256());
    }

    private static void publish(Path path, Path root, byte[] bytes, String digest)
            throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                    CdsPaths.hex(CdsPaths.sha256().digest(read(path, bytes.length))), digest))
                throw failure();
            return;
        }
        try (OutputStream output = CdsPaths.newPrivateFile(path, root)) {
            output.write(bytes);
        }
        CdsPaths.forceFile(path);
    }

    /**
     * Copies a manifest into the parse attempt as a stream. The copy must have the digest the
     * master was read with, so a master changed after it was read is never staged.
     */
    private static void publishCopy(Path path, Path root, Path source, String digest)
            throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(digest(path), digest))
                throw failure();
            return;
        }
        MessageDigest hash = CdsPaths.sha256();
        try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
                OutputStream output =
                        new DigestOutputStream(CdsPaths.newPrivateFile(path, root), hash)) {
            input.transferTo(output);
        }
        if (!io.github.carlos_emr.carlos.utility.CdsImportKeys.sameDigest(
                CdsPaths.hex(hash.digest()), digest)) {
            Files.deleteIfExists(path);
            throw failure();
        }
        CdsPaths.forceFile(path);
    }

    /** The SHA-256 of a file, read as a stream. */
    private static String digest(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw failure();
        MessageDigest hash = CdsPaths.sha256();
        try (InputStream input =
                new DigestInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), hash)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return CdsPaths.hex(hash.digest());
    }

    private static byte[] read(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
            throw failure();
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw failure();
            return bytes;
        }
    }

    private static IOException failure() {
        return CdsPaths.failure("CDS_CORRECTION_STAGE_INVALID");
    }
}
