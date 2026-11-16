/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.io.Reader;

/** Streams the current revision's extension rows, retaining one row at a time. */
final class CdsHistoryExtensionReader {
    static final int VALUE_CAPACITY = 16_777_215; // casemgmt_note_ext.value MEDIUMTEXT bytes.

    record Value(String key, String value, String date) {
        @Override public String toString() { return "CdsHistoryExtensionReader.Value[protected]"; }
    }

    @FunctionalInterface interface Source { Reader open() throws IOException; }
    @FunctionalInterface interface Sink { void accept(Value value) throws IOException; }

    private CdsHistoryExtensionReader() {}

    static void current(Source source, Sink sink) throws IOException {
        long count = walk(source.open(), -1, ignored -> {});
        if (count == 0) return;
        if (walk(source.open(), count - 1, sink) != count) throw malformed();
    }

    private static long walk(Reader content, long selected, Sink sink) throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(VALUE_CAPACITY).maxNameLength(1024)
                .maxNumberLength(64).maxDocumentLength(-1).build()).build();
        try (Reader input = content; JsonParser parser = factory.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            long count = document(parser, true, selected, sink);
            if (parser.nextToken() != null) throw malformed();
            return count;
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            throw malformed();
        }
    }

    private static long document(JsonParser parser, boolean root, long selected, Sink sink) throws IOException {
        Long version = null;
        long count = 0;
        boolean found = false;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) throw malformed();
            if (name.equals("version")) {
                if (version != null || value != JsonToken.VALUE_NUMBER_INT) throw malformed();
                version = parser.getLongValue();
            } else if (name.equals("revisions")) {
                if (found || value != JsonToken.START_ARRAY) throw malformed();
                found = true;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) throw malformed();
                    if (count == selected) revision(parser, sink);
                    else parser.skipChildren();
                    count = Math.incrementExact(count);
                }
            } else if (root && name.equals("nativeHistory")) {
                if (found || value != JsonToken.START_OBJECT) throw malformed();
                found = true;
                count = document(parser, false, selected, sink);
            } else if (root && name.equals("patientIssue") && value == JsonToken.START_OBJECT) {
                if (found) throw malformed();
                found = true;
                parser.skipChildren();
            } else parser.skipChildren();
        }
        if (version == null || version != 1 || !found) throw malformed();
        return count;
    }

    private static void revision(JsonParser parser, Sink sink) throws IOException {
        boolean found = false;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) throw malformed();
            if (name.equals("extensions")) {
                if (found || value != JsonToken.START_ARRAY) throw malformed();
                found = true;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() != JsonToken.START_OBJECT) throw malformed();
                    sink.accept(extension(parser));
                }
            } else parser.skipChildren();
        }
    }

    private static Value extension(JsonParser parser) throws IOException {
        var members = new java.util.HashMap<String, String>();
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (!java.util.Set.of("key", "value", "date").contains(name)
                    || value != JsonToken.VALUE_STRING || members.containsKey(name)) throw malformed();
            String text = parser.getText();
            if (name.equals("key") && (text.isEmpty() || text.codePointCount(0, text.length()) > 64)
                    || name.equals("date") && text.length() > 128
                    || name.equals("value") && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > VALUE_CAPACITY)
                throw malformed();
            members.put(name, text);
        }
        if (!members.containsKey("key")) throw malformed();
        return new Value(members.get("key"), members.get("value"), members.get("date"));
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_HISTORY_EXTENSION_DETAILS_MALFORMED");
    }
}
