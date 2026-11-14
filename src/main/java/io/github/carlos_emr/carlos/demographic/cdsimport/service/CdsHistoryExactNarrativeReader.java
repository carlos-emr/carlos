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
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Reads the exact note texts a CARLOS sender carries in a note's revision details.
 *
 * <p>The CARLOS exporter writes a note or CPP item with U+FFFD for each character XML 1.0 cannot
 * carry and keeps the stored text, as JSON, in the details of the revision it belongs to: member
 * {@code exactNarrative} (or {@code narrative}, which a PersonalHistory item and every earlier
 * revision of a CPP item always have) of an element of {@code revisions}. The details are the
 * {@code CARLOS.NativeHistory.v1} residual of a CPP item, or follow an encounter note's text; an
 * encounter note that carries a text also states each revision's {@value
 * CdsNativeExtensionContract#NOTE_BODY_LENGTH}. An imported item the sender edited wraps the
 * details as {@code nativeHistory} in a document of its own.
 *
 * <p>This reader only finds those values. It restores nothing: the caller must prove each against
 * the element text ({@link CdsHistoryPersister}). It walks the document as a stream and keeps
 * nothing but the values it is asked for; every other value, of any size, is skipped where it
 * stands. It refuses anything that is not exactly one version 1 object with one {@code
 * revisions} array of objects, nested at most 16 deep, with nothing after it, and a kept text
 * longer than a note the importer can store (never cut).
 */
final class CdsHistoryExactNarrativeReader {
    private static final String EXACT = "exactNarrative";
    private static final String NARRATIVE = "narrative";

    private CdsHistoryExactNarrativeReader() {}

    /** Opens the details document again; each reader is read to its end and closed here. */
    @FunctionalInterface
    interface Source {
        Reader open() throws IOException;
    }

    /**
     * What one revision of an encounter note carries.
     *
     * @param exactNarrative the stored body of a revision written with U+FFFD, or null
     * @param bodyLength UTF-16 units of the body as written in the note's text, or null
     */
    record NoteRevision(String exactNarrative, Long bodyLength) {
        @Override
        public String toString() {
            return "CdsHistoryExactNarrativeReader.NoteRevision[protected]";
        }
    }

    /**
     * Every revision of an encounter note's details, in document order.
     *
     * @param content the details document; read to its end and closed here
     * @param maximumUnits UTF-16 units of the longest note the importer stores: the carried texts
     *     together are no longer than the note they belong to
     * @throws IllegalArgumentException when the content is not such a document
     */
    static List<NoteRevision> noteRevisions(Reader content, int maximumUnits) throws IOException {
        var texts = new ArrayList<String>();
        var lengths = new ArrayList<Long>();
        walk(content, maximumUnits, index -> true, false, texts, lengths);
        var result = new ArrayList<NoteRevision>(texts.size());
        for (int i = 0; i < texts.size(); i++)
            result.add(new NoteRevision(texts.get(i), lengths.get(i)));
        return result;
    }

    /**
     * The exact text a CPP item's details carry for its current revision (the last one): its
     * {@code exactNarrative}, else its {@code narrative}, or null when it carries neither. The
     * document is read twice, so the text of no earlier revision is ever held, whatever its size.
     *
     * @param content opens the details document
     * @param maximumUnits UTF-16 units of the longest note the importer stores
     * @throws IllegalArgumentException when the content is not such a document
     */
    static String currentText(Source content, int maximumUnits) throws IOException {
        var counted = new ArrayList<String>();
        walk(content.open(), maximumUnits, index -> false, true, counted, new ArrayList<>());
        if (counted.isEmpty()) return null;
        int last = counted.size() - 1;
        var texts = new ArrayList<String>();
        walk(content.open(), maximumUnits, index -> index == last, true, texts, new ArrayList<>());
        if (texts.size() != counted.size()) throw malformed(); // Not the document read before.
        return texts.get(last);
    }

    /**
     * Walks the document once. For each revision {@code keep} accepts, its carried text ({@code
     * exactNarrative}; else {@code narrative} when {@code narrative} is true) and its body length
     * are read; the values of every other revision are skipped unread. One entry per revision is
     * added to {@code texts} and to {@code lengths} (null where nothing was read).
     */
    private static void walk(
            Reader content,
            int maximumUnits,
            IntPredicate keep,
            boolean narrative,
            List<String> texts,
            List<Long> lengths)
            throws IOException {
        JsonFactory factory =
                JsonFactory.builder()
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(16)
                                        .maxStringLength(Math.max(1, maximumUnits))
                                        .maxNumberLength(64)
                                        .maxNameLength(1024)
                                        .maxDocumentLength(-1)
                                        .build())
                        .build();
        try (Reader input = content;
                JsonParser parser = factory.createParser(input)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw malformed();
            var walk = new Walk(parser, maximumUnits, keep, narrative, texts, lengths);
            walk.document(true);
            if (parser.nextToken() != null) throw malformed();
        } catch (com.fasterxml.jackson.core.JacksonException invalid) {
            // Not JSON, nested too deeply, or a kept text longer than any note.
            throw malformed();
        }
    }

    private static final class Walk {
        private final JsonParser parser;
        private final int maximumUnits;
        private final IntPredicate keep;
        private final boolean narrative;
        private final List<String> texts;
        private final List<Long> lengths;
        private long kept;

        Walk(
                JsonParser parser,
                int maximumUnits,
                IntPredicate keep,
                boolean narrative,
                List<String> texts,
                List<Long> lengths) {
            this.parser = parser;
            this.maximumUnits = maximumUnits;
            this.keep = keep;
            this.narrative = narrative;
            this.texts = texts;
            this.lengths = lengths;
        }

        /** The members of the object just opened, up to its end. */
        void document(boolean root) throws IOException {
            Long version = null;
            boolean revisions = false;
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
                } else if (name.equals("revisions") && value == JsonToken.START_ARRAY) {
                    if (revisions) throw malformed();
                    revisions = true;
                    revisions();
                } else if (root && name.equals("nativeHistory") && value == JsonToken.START_OBJECT) {
                    // An imported item the sender edited: its revisions are one level down.
                    if (revisions) throw malformed();
                    revisions = true;
                    document(false);
                } else parser.skipChildren(); // A value of any size: skipped, never held.
            }
            if (version == null || version != 1 || !revisions) throw malformed();
        }

        private void revisions() throws IOException {
            for (JsonToken element = parser.nextToken();
                    element != JsonToken.END_ARRAY;
                    element = parser.nextToken()) {
                if (element != JsonToken.START_OBJECT) throw malformed();
                boolean wanted = keep.test(texts.size());
                String exact = null, plain = null;
                Long length = null;
                boolean exactSeen = false, plainSeen = false, lengthSeen = false;
                for (JsonToken token = parser.nextToken();
                        token != JsonToken.END_OBJECT;
                        token = parser.nextToken()) {
                    if (token != JsonToken.FIELD_NAME) throw malformed();
                    String name = parser.currentName();
                    JsonToken value = parser.nextToken();
                    if (value == null) throw malformed();
                    if (wanted && name.equals(EXACT)) {
                        if (exactSeen || value != JsonToken.VALUE_STRING) throw malformed();
                        exactSeen = true;
                        exact = parser.getText();
                    } else if (wanted && narrative && name.equals(NARRATIVE)) {
                        if (plainSeen || value != JsonToken.VALUE_STRING) throw malformed();
                        plainSeen = true;
                        plain = parser.getText();
                    } else if (wanted
                            && !narrative
                            && name.equals(CdsNativeExtensionContract.NOTE_BODY_LENGTH)) {
                        if (lengthSeen || value != JsonToken.VALUE_NUMBER_INT) throw malformed();
                        lengthSeen = true;
                        length = parser.getLongValue();
                        if (length < 0) throw malformed();
                    } else parser.skipChildren(); // Not read: skipped where it stands.
                }
                // A revision states its body once: exactNarrative beside an element that shows
                // U+FFFD, or narrative when the element does not hold the body at all.
                String text = exact != null ? exact : plain;
                if (text != null && (kept += text.length()) > maximumUnits) throw malformed();
                texts.add(text);
                lengths.add(length);
            }
        }
    }

    private static IllegalArgumentException malformed() {
        return new IllegalArgumentException("CDS_HISTORY_REVISION_DETAILS_MALFORMED");
    }
}
