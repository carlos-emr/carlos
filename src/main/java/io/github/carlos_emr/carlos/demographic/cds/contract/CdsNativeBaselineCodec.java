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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Additional versioned field witnesses bound to the exact, unchanged protocol-2 receipt.
 * Absence is a legacy/unknown baseline, never permission to backfill from current rows.
 * Every supplied row must reconstruct its receipt digest and the complete referenced-row
 * set. Stored receipt JSON and its digest are not rewritten or reinterpreted.
 */
public final class CdsNativeBaselineCodec {
    public static final int MAX_BYTES = 8 << 20;
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(
                                            com.fasterxml.jackson.core.StreamReadFeature
                                                    .STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            com.fasterxml.jackson.core.StreamReadConstraints
                                                    .builder()
                                                    .maxNestingDepth(16)
                                                    .maxStringLength(65_536)
                                                    .maxNumberLength(20)
                                                    .build())
                                    .build())
                    .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    public record Document(int protocol, String targetDigest, List<CdsNativeRowState> rows) {
        public Document {
            rows = List.copyOf(rows);
            if (protocol != 1 && protocol != 2
                    || targetDigest == null
                    || !targetDigest.matches("[0-9a-f]{64}")
                    || rows.isEmpty()
                    || rows.size() > 192) invalid();
        }

        @Override
        public String toString() {
            return "CdsNativeBaselineCodec.Document[protected]";
        }
    }

    private CdsNativeBaselineCodec() {}

    public static String encode(
            String references, String targetDigest, List<CdsNativeRowState> rows)
            throws IOException {
        var document = new Document(2, targetDigest, rows);
        verify(document, references, targetDigest);
        String encoded = JSON.writeValueAsString(document);
        if (encoded.length() > MAX_BYTES
                || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) invalid();
        return encoded;
    }

    public static Document decode(String encoded, String references, String targetDigest)
            throws IOException {
        if (encoded == null
                || encoded.isEmpty()
                || encoded.length() > MAX_BYTES
                || encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) invalid();
        JsonNode tree = JSON.readTree(encoded);
        if (tree != null
                && tree.path("protocol").isIntegralNumber()
                && tree.path("protocol").intValue() == 1) {
            // Normalize only the optional field absent from the exact original v1 contract.
            for (var row : tree.path("rows"))
                for (var cell : row.path("cells")) {
                    if (!cell.isObject() || cell.has("exactNumeric")) invalid();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) cell).putNull("exactNumeric");
                }
        }
        Document document = JSON.treeToValue(tree, Document.class);
        if (document.protocol() == 1
                && document.rows().stream()
                        .flatMap(row -> row.cells().stream())
                        .anyMatch(cell -> cell.exactNumeric() != null)) invalid();
        verify(document, references, targetDigest);
        return document;
    }

    private static void verify(Document document, String references, String targetDigest)
            throws IOException {
        if (document == null
                || references == null
                || references.isEmpty()
                || references.length() > 65_536
                || targetDigest == null
                || !targetDigest.matches("[0-9a-f]{64}")
                || !targetDigest.equals(document.targetDigest())
                || !targetDigest.equals(sha(references))) invalid();
        JsonNode receipt = JSON.readTree(references);
        exact(receipt, Set.of("protocol", "refs"));
        if (!receipt.get("protocol").isIntegralNumber()
                || !receipt.get("protocol").canConvertToInt()
                || receipt.get("protocol").intValue() != 2
                || !receipt.get("refs").isArray()
                || receipt.get("refs").isEmpty()
                || receipt.get("refs").size() > 192) invalid();
        var expected = new HashMap<String, String>();
        for (var reference : receipt.get("refs")) {
            exact(reference, Set.of("table", "id", "digest"));
            String table = text(reference, "table"),
                    id = text(reference, "id"),
                    digest = text(reference, "digest");
            if (!table.matches("[A-Za-z0-9_]{1,64}")
                    || !id.matches("[A-Za-z0-9_-]{1,64}")
                    || !digest.matches("[0-9a-f]{64}")) invalid();
            String previous = expected.putIfAbsent(table + "/" + id, digest);
            if (previous != null && !previous.equals(digest)) invalid();
        }
        var actual = new HashMap<String, String>();
        for (var row : document.rows())
            if (actual.putIfAbsent(row.table() + "/" + row.id(), row.digest()) != null) invalid();
        if (!actual.equals(expected)) invalid();
    }

    private static void exact(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || value.size() != fields.size()) invalid();
        for (String field : fields) if (!value.has(field)) invalid();
    }

    private static String text(JsonNode value, String name) {
        if (!value.get(name).isTextual()) invalid();
        return value.get(name).textValue();
    }

    private static String sha(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("CDS_NATIVE_BASELINE_SHA256_UNAVAILABLE");
        }
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_NATIVE_BASELINE_REQUIRES_REVIEW");
    }
}
