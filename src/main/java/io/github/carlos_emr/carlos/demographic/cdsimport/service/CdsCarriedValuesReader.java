/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads chosen stored values of a CARLOS source carrier ({@code CARLOS.Medication.Source.v1},
 * {@code CARLOS.Medication.CurrentSource.v1}, {@code CARLOS.Allergy.Source.v1}: a version 1
 * object whose {@code fields} member is the native row; {@code CARLOS.Prevention.Source.v1}:
 * {@code prevention} and its {@code extensions} rows) so that an element written with U+FFFD can
 * be proven against them.
 *
 * <p>The carrier is read as a stream and only the values asked for are kept, each only when it
 * is no longer than asked: a longer value cannot stand in the element it is wanted for, so it is
 * reported as longer and never held, whatever its size. The values a carrier holds have no length
 * of their own (MEDIUMTEXT columns travel whole), so such a value is skipped by reading the
 * document again without it rather than failing the read. A document that is not one version 1
 * object, nested at most 16 deep and with nothing after it, is refused: the caller reports the
 * elements it could not prove and restores nothing.
 */
final class CdsCarriedValuesReader {
    private static final String EXTENSIONS = "extensions";

    private CdsCarriedValuesReader() {}

    /** Opens the carrier again; each reader is read to its end and closed here. */
    @FunctionalInterface
    interface Source {
        Reader open() throws IOException;
    }

    /**
     * What was read.
     *
     * @param values each wanted member that holds text no longer than asked ({@code row/column})
     * @param longer each wanted member, or {@code extensions[i]/val}, longer than asked
     * @param extensions the extension rows in document order: key and value (null when SQL NULL,
     *     absent or longer than asked; see {@code longer})
     */
    record Values(
            Map<String, String> values, Set<String> longer, List<Map.Entry<String, String>> extensions) {
        Values {
            values = Collections.unmodifiableMap(new HashMap<>(values));
            longer = Set.copyOf(longer);
            extensions = List.copyOf(extensions);
        }

        @Override
        public String toString() {
            return "CdsCarriedValuesReader.Values[protected]";
        }
    }

    /**
     * @param wanted for each wanted member of an object of the root ({@code fields/route}), the
     *     most UTF-16 units it may have
     * @param extensionUnits the most UTF-16 units of an extension row's {@code val}, or 0 when no
     *     extension row is wanted
     * @throws IllegalArgumentException when the content is not such a document
     */
    static Values read(Source content, Map<String, Integer> wanted, int extensionUnits)
            throws IOException {
        var longer = new HashSet<String>();
        while (true) {
            try {
                return walk(content.open(), wanted, extensionUnits, longer);
            } catch (Longer value) {
                // Read again without it; a member reported twice is not a document read twice.
                if (!longer.add(value.path)) throw malformed();
            }
        }
    }

    private static Values walk(
            Reader content, Map<String, Integer> wanted, int extensionUnits, Set<String> longer)
            throws IOException {
        int maximum = Math.max(1, extensionUnits);
        for (int units : wanted.values()) maximum = Math.max(maximum, units);
        JsonFactory factory =
                JsonFactory.builder()
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(16)
                                        .maxStringLength(maximum)
                                        .maxNumberLength(64)
                                        .maxNameLength(1024)
                                        .maxDocumentLength(-1)
                                        .build())
                        .build();
        var values = new HashMap<String, String>();
        var found = new HashSet<String>();
        var extensions = new ArrayList<Map.Entry<String, String>>();
        try (Reader input = content;
                JsonParser parser = factory.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            Long version = null;
            for (JsonToken token = parser.nextToken();
                    token != JsonToken.END_OBJECT;
                    token = parser.nextToken()) {
                if (token != JsonToken.FIELD_NAME) throw malformed();
                String name = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) throw malformed();
                if (name.equals("version")) {
                    if (version != null || value != JsonToken.VALUE_NUMBER_INT) throw malformed();
                    version = parser.getLongValue();
                } else if (value == JsonToken.START_OBJECT && wantsRow(wanted, name))
                    row(parser, name, wanted, values, found, longer);
                else if (value == JsonToken.START_ARRAY
                        && name.equals(EXTENSIONS)
                        && extensionUnits > 0) {
                    if (!found.add(EXTENSIONS)) throw malformed();
                    extensions(parser, extensionUnits, extensions, longer);
                } else parser.skipChildren(); // A value of any size: skipped, never held.
            }
            if (version == null || version != 1 || parser.nextToken() != null) throw malformed();
        } catch (Longer value) {
            throw value;
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            // Not JSON or nested too deeply.
            throw malformed();
        }
        var stated = new HashSet<String>();
        for (String path : longer) if (wanted.containsKey(path)) stated.add(path);
        for (int i = 0; i < extensions.size(); i++)
            if (longer.contains(EXTENSIONS + "[" + i + "]/val")) stated.add(EXTENSIONS + "[" + i + "]/val");
        return new Values(values, stated, extensions);
    }

    private static boolean wantsRow(Map<String, Integer> wanted, String row) {
        for (String path : wanted.keySet()) if (path.startsWith(row + "/")) return true;
        return false;
    }

    private static void row(
            JsonParser parser,
            String row,
            Map<String, Integer> wanted,
            Map<String, String> values,
            Set<String> found,
            Set<String> longer)
            throws IOException {
        if (!found.add(row)) throw malformed();
        for (JsonToken token = parser.nextToken();
                token != JsonToken.END_OBJECT;
                token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String path = row + "/" + parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) throw malformed();
            Integer units = wanted.get(path);
            if (units == null || longer.contains(path)) {
                parser.skipChildren();
                continue;
            }
            if (!found.add(path)) throw malformed();
            if (value == JsonToken.VALUE_NULL) continue;
            if (value != JsonToken.VALUE_STRING) throw malformed();
            String text = text(parser, path);
            if (text.length() > units) longer.add(path);
            else values.put(path, text);
        }
    }

    private static void extensions(
            JsonParser parser,
            int units,
            List<Map.Entry<String, String>> extensions,
            Set<String> longer)
            throws IOException {
        for (JsonToken element = parser.nextToken();
                element != JsonToken.END_ARRAY;
                element = parser.nextToken()) {
            if (element != JsonToken.START_OBJECT) throw malformed();
            String path = EXTENSIONS + "[" + extensions.size() + "]/val";
            String key = null, value = null;
            boolean keySeen = false, valueSeen = false;
            for (JsonToken token = parser.nextToken();
                    token != JsonToken.END_OBJECT;
                    token = parser.nextToken()) {
                if (token != JsonToken.FIELD_NAME) throw malformed();
                String name = parser.currentName();
                JsonToken next = parser.nextToken();
                if (next == null) throw malformed();
                if (name.equals("keyval")) {
                    if (keySeen) throw malformed();
                    keySeen = true;
                    if (next == JsonToken.VALUE_STRING) key = text(parser, EXTENSIONS + "/keyval");
                    else if (next != JsonToken.VALUE_NULL) throw malformed();
                } else if (name.equals("val") && !longer.contains(path)) {
                    if (valueSeen) throw malformed();
                    valueSeen = true;
                    if (next == JsonToken.VALUE_STRING) {
                        value = text(parser, path);
                        if (value.length() > units) {
                            longer.add(path);
                            value = null;
                        }
                    } else if (next != JsonToken.VALUE_NULL) throw malformed();
                } else parser.skipChildren();
            }
            extensions.add(new java.util.AbstractMap.SimpleImmutableEntry<>(key, value));
        }
    }

    /** The text of the current string, or {@link Longer} when it passes the read's bound. */
    private static String text(JsonParser parser, String path) throws IOException {
        try {
            return parser.getText();
        } catch (StreamConstraintsException beyond) {
            throw new Longer(path);
        }
    }

    /** A wanted value longer than the read's bound: read again without it. */
    private static final class Longer extends IOException {
        private static final long serialVersionUID = 1L;
        private final String path;

        Longer(String path) {
            super("CDS_CARRIED_VALUE_LONGER");
            this.path = path;
        }
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_SOURCE_CONTEXT_MALFORMED");
    }
}
