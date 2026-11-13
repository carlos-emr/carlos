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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Strict reader of the versioned CARLOS {@code *.Source.v1} residual JSON (#6198).
 *
 * <p>The CARLOS exporter writes each complete native row as one JSON document in a fixed key
 * order, with SQL NULL distinct from empty text and every space written as a JSON unicode
 * escape, so {@code xs:token} normalization of the residual Content cannot alter it, and it
 * bounds the document to {@link #MAXIMUM_CHARACTERS}. This reader accepts only that shape: one
 * object, {@code "version":1}, no duplicate member and nothing after the document. Any other
 * content is refused, never repaired; the caller turns the refusal into a clinical review.
 */
final class CdsSourceContextParser {
    /** The exporter's own bound on a carrier (CdsExportNativeFieldAssessments.legal, 1 << 24). */
    static final int MAXIMUM_CHARACTERS = 1 << 24;

    private static final JsonMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(16)
                                                    .maxStringLength(MAXIMUM_CHARACTERS)
                                                    .maxNumberLength(64)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();

    private CdsSourceContextParser() {}

    /**
     * @return the version 1 document root
     * @throws IllegalArgumentException when the content is not exactly one version 1 object
     */
    static JsonNode parse(String content) {
        if (content == null || content.isEmpty()) throw malformed();
        JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (IOException invalid) {
            throw malformed();
        }
        if (root == null || !root.isObject()) throw malformed();
        JsonNode version = root.get("version");
        if (version == null || !version.isInt() || version.intValue() != 1) throw malformed();
        return root;
    }

    /**
     * A captured native row: an object whose every member is text or SQL NULL, and which holds
     * every named column (the exporter captures every column of its read plan).
     */
    static JsonNode row(JsonNode parent, String member, String... columns) {
        return element(parent.get(member), columns);
    }

    /** As {@link #row(JsonNode, String, String...)}, for a row that is an array element. */
    static JsonNode element(JsonNode row, String... columns) {
        if (row == null || !row.isObject()) throw malformed();
        for (Iterator<Map.Entry<String, JsonNode>> it = row.fields(); it.hasNext(); ) {
            JsonNode value = it.next().getValue();
            if (!value.isTextual() && !value.isNull()) throw malformed();
        }
        for (String column : columns) if (!row.has(column)) throw malformed();
        return row;
    }

    /** The exact text of a captured column, or null for SQL NULL. */
    static String text(JsonNode row, String column) {
        JsonNode value = row.get(column);
        if (value == null) throw malformed();
        return value.isNull() ? null : value.textValue();
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
    }
}
