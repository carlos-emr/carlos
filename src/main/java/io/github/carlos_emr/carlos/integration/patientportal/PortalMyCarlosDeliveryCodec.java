/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Frozen delivery v1 encoding; verification always uses original signed bytes. */
final class PortalMyCarlosDeliveryCodec {
    static final int MAX_SEALED_BYTES = 20 * 1024 * 1024;
    static final int MAX_OUTER_BYTES = 4096;
    static final int MAX_BODY_BYTES = MAX_SEALED_BYTES + MAX_OUTER_BYTES + 16;
    static final int MAX_RECEIPT_BYTES = 4096;
    static final long MAX_LIFETIME = 604800;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(16384).maxNumberLength(20).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PortalMyCarlosDeliveryCodec() {}

    static JsonNode object(byte[] exact, int maximum) {
        if (exact == null || exact.length == 0 || exact.length > maximum) {
            throw invalid();
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(exact)).toString();
            JsonNode node = JSON.readTree(text);
            if (node == null || !node.isObject()) {
                throw invalid();
            }
            return node;
        } catch (IOException failure) {
            throw invalid();
        }
    }

    /** Extract original object bytes, so per-event limits include original whitespace and escapes. */
    static List<byte[]> arrayObjects(byte[] exactPage, String field, int maximumObjectBytes) {
        try (var parser = JSON.getFactory().createParser(exactPage)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName();
                parser.nextToken();
                if (!field.equals(name)) {
                    parser.skipChildren();
                    continue;
                }
                if (parser.currentToken() != JsonToken.START_ARRAY) throw invalid();
                List<byte[]> objects = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
                    int start = Math.toIntExact(parser.currentTokenLocation().getByteOffset());
                    parser.skipChildren();
                    int end = Math.toIntExact(parser.currentLocation().getByteOffset());
                    if (end - start > maximumObjectBytes) throw invalid();
                    objects.add(Arrays.copyOfRange(exactPage, start, end));
                }
                return objects;
            }
            throw invalid();
        } catch (IOException | ArithmeticException failure) {
            throw invalid();
        }
    }

    static void fields(JsonNode object, Set<String> expected) {
        if (object == null || !object.isObject()) {
            throw invalid();
        }
        Set<String> actual = new HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid();
        }
    }

    static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid();
        }
        return text(value.textValue());
    }

    static String text(String text) {
        if (text == null) {
            throw invalid();
        }
        PortalMyCarlosDeviceApprovalCodec.utf8(text);
        return text;
    }

    static long epoch(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalid();
        }
        return value.longValue();
    }

    static void uuid(String text, boolean version4) {
        try {
            UUID id = UUID.fromString(text);
            if (!id.toString().equals(text) || (version4 && (id.version() != 4 || id.variant() != 2))) {
                throw invalid();
            }
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw invalid();
        }
    }

    static void recipient(String text) {
        if (text == null || !text.matches("[A-Za-z0-9_-]{1,64}")) {
            throw invalid();
        }
    }

    static void hash(String text) {
        if (text == null || !text.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
    }

    static void lifetime(long created, long expires) {
        if (created < 0 || expires <= created || expires - created > MAX_LIFETIME
                || expires > 9_007_199_254_740_991L) {
            throw invalid();
        }
    }

    /** JCS for this protocol's nested object/string/exact nonnegative integer subset. */
    static byte[] canonical(Map<String, Object> values) {
        StringBuilder out = new StringBuilder();
        write(out, values);
        return PortalMyCarlosDeviceApprovalCodec.utf8(out.toString());
    }

    private static void write(StringBuilder out, Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalid();
                }
                sorted.put(key, entry.getValue());
            }
            out.append('{');
            boolean first = true;
            for (var entry : sorted.entrySet()) {
                if (!first) out.append(',');
                first = false;
                string(out, entry.getKey());
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof String string) {
            string(out, string);
        } else if (value instanceof Long number && number >= 0 && number <= 9_007_199_254_740_991L) {
            out.append(number);
        } else {
            throw invalid();
        }
    }

    private static void string(StringBuilder out, String value) {
        text(value); // Refuse unpaired UTF-16 surrogates before UTF-8 encoding.
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append("0123456789abcdef".charAt(c >>> 4))
                                .append("0123456789abcdef".charAt(c & 15));
                    } else out.append(c);
                }
            }
        }
        out.append('"');
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("myCarlos delivery data is invalid");
    }
}
