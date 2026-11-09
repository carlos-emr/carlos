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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.security.auth.module.UnixSystem;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Captures a protected explicit CLI selection once, after exact-request delegation verification.
 * JSON format: {"version":1,"patients":[27,28]}. Neither the parser nor the supervisor looks up
 * patient facts. Current scope/patient authority belongs to acceptance and every later boundary.
 */
@Service
public class CdsExportSelectionFileService {
    private static final int MAX_BYTES = 262144;
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(4)
                                                    .maxStringLength(32)
                                                    .maxNumberLength(10)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
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
            return parse(CdsProcessConfiguration.readBounded(selected, MAX_BYTES));
        } catch (IOException | RuntimeException failure) {
            throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
        }
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
        if (bytes.length > MAX_BYTES) fail();
        final com.fasterxml.jackson.databind.JsonNode root;
        try {
            int offset =
                    bytes.length >= 3
                                    && (bytes[0] & 255) == 239
                                    && (bytes[1] & 255) == 187
                                    && (bytes[2] & 255) == 191
                            ? 3
                            : 0;
            String text =
                    java.nio.charset.StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                            .toString();
            root = JSON.readTree(text);
        } catch (IOException malformed) {
            throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
        }
        if (root == null || !root.isObject()) fail();
        Set<String> fields = new HashSet<>();
        root.fieldNames().forEachRemaining(fields::add);
        var version = root.path("version");
        var patients = root.path("patients");
        if (!fields.equals(Set.of("version", "patients"))
                || !version.isIntegralNumber()
                || version.intValue() != 1
                || !version.canConvertToInt()
                || !patients.isArray()
                || patients.isEmpty()
                || patients.size() > 10_000) fail();
        List<Integer> selected = new ArrayList<>(patients.size());
        Set<Integer> unique = new HashSet<>();
        for (var patient : patients) {
            if (!patient.isIntegralNumber()
                    || !patient.canConvertToInt()
                    || patient.intValue() < 1
                    || !unique.add(patient.intValue())) fail();
            selected.add(patient.intValue());
        }
        return selected.stream().sorted().toList();
    }

    private static void fail() throws IOException {
        throw new IOException("CDS_EXPORT_SELECTION_FILE_INVALID");
    }
}
