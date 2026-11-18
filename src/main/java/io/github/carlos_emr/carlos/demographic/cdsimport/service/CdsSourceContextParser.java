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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Strict streaming reader of the versioned CARLOS {@code *.Source.v1} residual JSON (#6198).
 *
 * <p>The CARLOS exporter writes each complete native row as one JSON document in a fixed key
 * order, with SQL NULL distinct from empty text and every space written as a JSON unicode
 * escape, so {@code xs:token} normalization of the residual Content cannot alter it. A carrier
 * has no length limit: a row with large text columns makes a large document.
 *
 * <p>This reader therefore never materializes the document. It walks it once and keeps only the
 * members the caller names; every other value, of any size, is skipped where it stands. What it
 * keeps is bounded by what the importer can restore: a kept text is at most
 * {@link #RESTORABLE_UNITS} UTF-16 units, the longest text a carrier value is restored from
 * (an appointment's complete notes), and array elements are handed to the
 * caller one at a time. It accepts only one object with {@code "version":1}, nesting of at most
 * 16, no repeated member among those it keeps, and nothing after the document. Any other
 * content, and a kept text longer than any column could hold, is refused, never repaired; the
 * caller turns the refusal into a clinical review or a reported, unused record.
 */
final class CdsSourceContextParser {
    /**
     * UTF-16 units of the longest text restored from a carrier: the complete notes of an
     * appointment, which are its notes column (32,768 characters) with the labelled lines the
     * exporter composes around it (a reason-list label, the booking name and the remarks, each a
     * short column). Derived from column capacities; not a limit on the carrier, whose other
     * values are skipped whatever their size.
     */
    static final int RESTORABLE_UNITS = 2 * (32768 + 2048);

    private static final JsonFactory JSON =
            JsonFactory.builder()
                    .streamReadConstraints(
                            StreamReadConstraints.builder()
                                    .maxNestingDepth(16)
                                    .maxStringLength(RESTORABLE_UNITS)
                                    .maxNumberLength(64)
                                    .maxNameLength(1024)
                                    .maxDocumentLength(-1)
                                    .build())
                    .build();

    private CdsSourceContextParser() {}

    /** Receives each element of a requested array, reduced to its requested members. */
    @FunctionalInterface
    interface Elements {
        void accept(String array, JsonNode element);
    }

    /**
     * Reads the document, keeping only the requested members.
     *
     * @param content the residual Content as token text; read to its end and closed here
     * @param scalars paths of the wanted members of the root object and of objects nested in
     *     it, joined by {@code /} (for example {@code fields/quantity})
     * @param arrays for each wanted array of the root object, the members wanted of each of its
     *     elements; every element must be an object and is handed to {@code elements}
     * @return the version 1 document root holding only the wanted members
     * @throws IllegalArgumentException when the content is not exactly one version 1 object, a
     *     wanted member is repeated, or a wanted text is longer than any restorable column
     * @throws IOException when the content cannot be read
     */
    static JsonNode read(
            Reader content,
            Set<String> scalars,
            Map<String, Set<String>> arrays,
            Elements elements)
            throws IOException {
        try (Reader input = content;
                JsonParser parser = JSON.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            ObjectNode root = object(parser, "", scalars, arrays, elements);
            if (parser.nextToken() != null) throw malformed();
            JsonNode version = root.get("version");
            if (version == null || !version.isIntegralNumber() || version.longValue() != 1)
                throw malformed();
            return root;
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            // Not JSON, nested too deeply, or a wanted text beyond any restorable column.
            throw malformed();
        }
    }

    /** {@link #read(Reader, Set, Map, Elements)} for a document with no wanted array. */
    static JsonNode read(Reader content, Set<String> scalars) throws IOException {
        return read(content, scalars, Map.of(), (array, element) -> {});
    }

    private static ObjectNode object(
            JsonParser parser,
            String path,
            Set<String> scalars,
            Map<String, Set<String>> arrays,
            Elements elements)
            throws IOException {
        ObjectNode kept = JsonNodeFactory.instance.objectNode();
        for (JsonToken token = parser.nextToken();
                token != JsonToken.END_OBJECT;
                token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            String member = path.isEmpty() ? name : path + "/" + name;
            JsonToken value = parser.nextToken();
            boolean wanted = scalars.contains(member) || path.isEmpty() && name.equals("version");
            if (value == JsonToken.START_OBJECT && parent(scalars, member)) {
                if (kept.has(name)) throw malformed();
                kept.set(name, object(parser, member, scalars, arrays, elements));
            } else if (value == JsonToken.START_ARRAY && path.isEmpty() && arrays.containsKey(name)) {
                if (kept.has(name)) throw malformed();
                kept.set(name, JsonNodeFactory.instance.arrayNode());
                Set<String> members = arrays.get(name);
                var wantedOfElement = new java.util.HashSet<String>();
                for (String each : members) wantedOfElement.add(name + "[]/" + each);
                for (JsonToken element = parser.nextToken();
                        element != JsonToken.END_ARRAY;
                        element = parser.nextToken()) {
                    if (element != JsonToken.START_OBJECT) throw malformed();
                    ObjectNode row =
                            object(parser, name + "[]", wantedOfElement, Map.of(), elements);
                    elements.accept(name, row);
                }
            } else if (wanted) {
                if (kept.has(name)) throw malformed();
                kept.set(name, scalar(parser, value));
            } else if (value == null) throw malformed();
            else parser.skipChildren(); // A value of any size: skipped, never held.
        }
        return kept;
    }

    /** Whether some wanted member lies inside the object at {@code path}. */
    private static boolean parent(Set<String> scalars, String path) {
        for (String wanted : scalars) if (wanted.startsWith(path + "/")) return true;
        return false;
    }

    private static JsonNode scalar(JsonParser parser, JsonToken value) throws IOException {
        if (value == null) throw malformed();
        switch (value) {
            case VALUE_STRING:
                return JsonNodeFactory.instance.textNode(parser.getText());
            case VALUE_NULL:
                return JsonNodeFactory.instance.nullNode();
            case VALUE_NUMBER_INT:
                return JsonNodeFactory.instance.numberNode(parser.getLongValue());
            case VALUE_TRUE:
            case VALUE_FALSE:
                return JsonNodeFactory.instance.booleanNode(value == JsonToken.VALUE_TRUE);
            default:
                // A container or a fraction where a column value was expected.
                throw malformed();
        }
    }

    /**
     * A captured native row: an object whose every kept member is text or SQL NULL, and which
     * holds every named column (the exporter captures every column of its read plan).
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
