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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the CPP section tags a CARLOS sender carries for an item filed under several CPP sections.
 *
 * <p>A CDS item has one section, so the CARLOS exporter writes such an item once, under the first
 * of its sections in CDS PatientRecord order, and puts every section tag of its current revision
 * in the item's {@code CARLOS.NativeHistory.v1} revision details as {@link
 * CdsNativeExtensionContract#HISTORY_SECTIONS}: at the root of the details, or at the root of
 * their {@code nativeHistory} object for an imported item the sender edited. The persister links
 * the item to every other tag, so the receiving chart shows it in each CPP box the sender's chart
 * did.
 *
 * <p>This reader only finds that list. It walks the details as a stream and keeps nothing else:
 * every other value, of any size, is skipped where it stands. It refuses a document that is not
 * one JSON object with nothing after it, nested more than 16 deep, and a list that is not two to
 * six distinct section tags including the record's own; a document without the member is an item
 * of one section.
 */
final class CdsHistorySectionsReader {
    private static final String NATIVE_HISTORY = "nativeHistory";

    /** The longest text this reader reads: no section tag is longer. */
    private static final int TAG_LENGTH = 64;

    private CdsHistorySectionsReader() {}

    /**
     * The section tags of an item other than its own, in CDS PatientRecord order ({@link
     * CdsNativeExtensionContract#HISTORY_SECTION_TAGS}); empty when its details carry none.
     *
     * @param content the details document; read to its end and closed here
     * @param own the section tag of the record's own section
     * @throws IllegalArgumentException when the content is not such a document, or its section
     *     tags are not such a list
     */
    static List<String> otherSections(Reader content, String own) throws IOException {
        if (!CdsNativeExtensionContract.HISTORY_SECTION_TAGS.contains(own)) throw malformed();
        JsonFactory factory =
                JsonFactory.builder()
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(16)
                                        // Only a section tag is ever read as text; a
                                        // skipped value of any length is never held.
                                        .maxStringLength(TAG_LENGTH)
                                        .maxNumberLength(64)
                                        .maxNameLength(1024)
                                        .maxDocumentLength(-1)
                                        .build())
                        .build();
        Set<String> tags;
        try (Reader input = content;
                JsonParser parser = factory.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            tags = document(parser, true);
            if (parser.nextToken() != null) throw malformed();
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            // Not JSON, nested too deeply, or a listed text longer than any section tag.
            throw malformed();
        }
        if (tags == null) return List.of();
        if (tags.size() < 2 || !tags.contains(own)) throw malformed();
        var others = new ArrayList<String>();
        for (String tag : CdsNativeExtensionContract.HISTORY_SECTION_TAGS)
            if (tags.contains(tag) && !tag.equals(own)) others.add(tag);
        return List.copyOf(others);
    }

    /**
     * The members of the object just opened, up to its end: its section tags, or null when it
     * names none. At the root, the {@code nativeHistory} object is read the same way.
     */
    private static Set<String> document(JsonParser parser, boolean root) throws IOException {
        Set<String> tags = null;
        for (JsonToken token = parser.nextToken();
                token != JsonToken.END_OBJECT;
                token = parser.nextToken()) {
            if (token != JsonToken.FIELD_NAME) throw malformed();
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (value == null) throw malformed();
            Set<String> found = null;
            if (name.equals(CdsNativeExtensionContract.HISTORY_SECTIONS)) {
                if (value != JsonToken.START_ARRAY) throw malformed();
                found = tags(parser);
            } else if (root && name.equals(NATIVE_HISTORY) && value == JsonToken.START_OBJECT)
                found = document(parser, false);
            else parser.skipChildren(); // A value of any size: skipped, never held.
            if (found != null) {
                // One list per item: the sender's own details, or those of its edit.
                if (tags != null) throw malformed();
                tags = found;
            }
        }
        return tags;
    }

    /** The elements of the array just opened: distinct section tags. */
    private static Set<String> tags(JsonParser parser) throws IOException {
        var tags = new LinkedHashSet<String>();
        for (JsonToken element = parser.nextToken();
                element != JsonToken.END_ARRAY;
                element = parser.nextToken()) {
            if (element != JsonToken.VALUE_STRING) throw malformed();
            String tag = parser.getText();
            if (!CdsNativeExtensionContract.HISTORY_SECTION_TAGS.contains(tag) || !tags.add(tag))
                throw malformed();
        }
        return tags;
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_HISTORY_SECTIONS_MALFORMED");
    }
}
