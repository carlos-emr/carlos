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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.sun.security.auth.module.UnixSystem;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Captures a protected explicit CLI selection once, after exact-request delegation verification.
 * JSON format: {"version":1,"patients":[27,28]}. Neither the parser nor the supervisor looks up
 * patient facts. Current scope/patient authority belongs to acceptance and every later boundary.
 *
 * <p>The file names as many patients as the operator listed. It is read as a stream, one number
 * at a time, so neither its size nor the count of patients is bounded here; what is kept is one
 * {@code int} per patient.
 */
@Service
public class CdsExportSelectionFileService {
    private static final JsonFactory JSON =
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(4)
                                    .maxStringLength(32)
                                    .maxNumberLength(10)
                                    .build())
                    .build();
    private final CdsExportConfigurationService configuration;

    public CdsExportSelectionFileService(CdsExportConfigurationService configuration) {
        this.configuration = configuration;
    }

    /** Flat private-root inputs only. No links, world-readable file, other user's file, duplicate IDs or silent list shortening. */
    public List<Integer> capture(Path selected) throws IOException {
        Path root = configuration.selectionRoot();
        if (selected == null
                || !selected.isAbsolute()
                || !selected.equals(selected.normalize())
                || !root.equals(selected.getParent())) fail();
        try {
            if (!root.equals(root.toRealPath())
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(selected)) fail();
            long service = new UnixSystem().getUid();
            permissions(root, service, true);
            permissions(selected, service, false);
            PathValidationUtils.validatePrivateImportPath(selected.toFile(), root.toFile());
            return read(selected);
        } catch (IOException | RuntimeException failure) {
            throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
        }
    }

    /** One regular file, opened without following a link, that did not change while it was read. */
    private static List<Integer> read(Path selected) throws IOException {
        BasicFileAttributes before =
                Files.readAttributes(
                        selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) fail();
        List<Integer> patients;
        try (var channel =
                        Files.newByteChannel(
                                selected,
                                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var input = java.nio.channels.Channels.newInputStream(channel)) {
            patients = parse(input);
        }
        BasicFileAttributes after =
                Files.readAttributes(
                        selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.fileKey() == null
                || !before.fileKey().equals(after.fileKey())
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) fail();
        return patients;
    }

    private static void permissions(Path path, long service, boolean directory) throws IOException {
        long uid =
                ((Number) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS))
                        .longValue();
        int mode =
                ((Number) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS))
                        .intValue();
        if (uid != 0 && uid != service || (mode & (directory ? 0027 : 0137)) != 0) fail();
        // Group read/execute is allowed for the service account; group write and all other access
        // are not.
    }

    static List<Integer> parse(byte[] bytes) throws IOException {
        return parse(new ByteArrayInputStream(bytes));
    }

    /**
     * Strict UTF-8 (an optional byte-order mark is skipped), exactly the members {@code version}
     * and {@code patients} once each, version 1, at least one patient, every patient a positive
     * {@code int} named once, and nothing after the object.
     *
     * @return the selected patient numbers in ascending order
     */
    static List<Integer> parse(InputStream source) throws IOException {
        try {
            var input = new PushbackInputStream(source, 3);
            byte[] mark = input.readNBytes(3);
            if (!(mark.length == 3
                    && (mark[0] & 255) == 239
                    && (mark[1] & 255) == 187
                    && (mark[2] & 255) == 191)) input.unread(mark);
            var text =
                    new InputStreamReader(
                            input,
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT));
            try (JsonParser parser = JSON.createParser(text)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) fail();
                boolean versioned = false;
                int[] patients = null;
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    JsonToken value = parser.nextToken();
                    if ("version".equals(name)) {
                        if (value != JsonToken.VALUE_NUMBER_INT
                                || parser.getNumberType() != JsonParser.NumberType.INT
                                || parser.getIntValue() != 1) fail();
                        versioned = true;
                    } else if ("patients".equals(name)) {
                        if (value != JsonToken.START_ARRAY) fail();
                        patients = patients(parser);
                    } else fail();
                }
                if (parser.currentToken() != JsonToken.END_OBJECT
                        || parser.nextToken() != null
                        || !versioned
                        || patients == null
                        || patients.length == 0) fail();
                // Sorted once; a number named twice is then beside itself.
                Arrays.sort(patients);
                for (int index = 1; index < patients.length; index++)
                    if (patients[index] == patients[index - 1]) fail();
                return Arrays.stream(patients).boxed().toList();
            }
        } catch (IOException | RuntimeException malformed) {
            throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
        }
    }

    private static int[] patients(JsonParser parser) throws IOException {
        int[] values = new int[1024];
        int count = 0;
        for (JsonToken token = parser.nextToken();
                token != JsonToken.END_ARRAY;
                token = parser.nextToken()) {
            if (token != JsonToken.VALUE_NUMBER_INT
                    || parser.getNumberType() != JsonParser.NumberType.INT
                    || parser.getIntValue() < 1) fail();
            // Grows by half; the only ceiling is the JVM's own array length.
            if (count == values.length)
                values = Arrays.copyOf(values, Math.addExact(count, count >> 1));
            values[count++] = parser.getIntValue();
        }
        return Arrays.copyOf(values, count);
    }

    private static void fail() throws IOException {
        throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
    }
}
