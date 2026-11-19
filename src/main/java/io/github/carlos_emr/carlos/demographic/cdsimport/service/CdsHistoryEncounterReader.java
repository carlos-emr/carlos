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
import java.io.IOException;
import java.io.Reader;

/** Reads only the current revision's encounter type, without retaining older revisions or bodies. */
final class CdsHistoryEncounterReader {
    private CdsHistoryEncounterReader() {}

    static String read(Reader content) throws IOException {
        var factory = JsonFactory.builder().streamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(200)
                        .maxNumberLength(64).maxNameLength(1024).maxDocumentLength(-1).build()).build();
        try (Reader input = content; JsonParser parser = factory.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            String result = document(parser, true);
            if (parser.nextToken() != null) throw malformed();
            return result;
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            throw malformed();
        }
    }

    private static String document(JsonParser parser, boolean root) throws IOException {
        Long version = null;
        boolean found = false;
        String current = null;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT;
                token = parser.nextToken()) {
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
                    current = revision(parser);
                }
            } else if (root && name.equals("nativeHistory")) {
                if (found || value != JsonToken.START_OBJECT) throw malformed();
                found = true;
                current = document(parser, false);
            } else if (root && name.equals("patientIssue") && value == JsonToken.START_OBJECT) {
                if (found) throw malformed();
                found = true;
                parser.skipChildren(); // A note-free diagnosis has no encounter type.
            } else parser.skipChildren();
        }
        if (version == null || version != 1 || !found) throw malformed();
        return current;
    }

    private static String revision(JsonParser parser) throws IOException {
        String encounter = null;
        boolean found = false;
        for (JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT;
                token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) throw malformed();
            if (name.equals("encounterType")) {
                if (found || value != JsonToken.VALUE_STRING) throw malformed();
                found = true;
                encounter = parser.getText();
                if (encounter.codePointCount(0, encounter.length()) > 100) throw malformed();
            } else parser.skipChildren();
        }
        // A current revision without a type never inherits an older revision's type.
        return encounter;
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_HISTORY_ENCOUNTER_DETAILS_MALFORMED");
    }
}
