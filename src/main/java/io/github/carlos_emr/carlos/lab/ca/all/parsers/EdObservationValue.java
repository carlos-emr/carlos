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
 *
 */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.util.Terser;

/**
 * Reads the parts of an HL7 {@code ED} (encapsulated data) observation value that the handlers'
 * {@code getOBXResult} does not: ED.5, the data, and ED.4, its encoding.
 *
 * <p>Most handlers read OBX-5 component 1, which for a standards-compliant {@code ED} value
 * ({@code ^TEXT^PDF^Base64^<data>}) is the empty source application, so the document itself is
 * lost. The {@link MessageHandler} defaults for
 * {@link MessageHandler#getOBXEmbeddedDocumentData(int, int)},
 * {@link MessageHandler#getOBXEmbeddedDocumentText(int, int)} and
 * {@link MessageHandler#getOBXDocumentEncoding(int, int)} use these helpers with the segment from
 * {@link MessageHandler#getOBXSegment(int, int)}, which every handler that can reach it overrides.
 * When ED.5 is empty the handler's own result is kept, so a sender that puts the payload in
 * OBX-5.1 (as legacy feeds do) is unaffected.</p>
 *
 * <p>{@link #isBase64Shaped(String)} is public so the embedded-document loader and
 * {@link #text} share one rule for which undeclared payloads are text.</p>
 *
 * @since 2026-09-30
 */
public final class EdObservationValue {

    /** Resolves the OBX segment; any failure means "not available". */
    @FunctionalInterface
    interface ObxLookup {
        Segment obx() throws Exception;
    }

    /** The HL7 formatting escape for a line break, as HAPI leaves it in a text value. */
    private static final String LINE_BREAK_ESCAPE = "\\.br\\";

    private EdObservationValue() {
    }

    /**
     * The embedded document's data: ED.5 when the OBX is {@code ED} and ED.5 is present,
     * otherwise the handler's own {@code getOBXResult}.
     */
    static String data(MessageHandler handler, int i, int j, ObxLookup lookup) {
        if (handler.isOBXEmbeddedDocument(i, j)) {
            String data = component(lookup, 5);
            if (data != null && !data.isBlank()) {
                return data;
            }
        }
        return handler.getOBXResult(i, j);
    }

    /**
     * ED.4 when the OBX is {@code ED} and carries its data in ED.5; otherwise {@code null}, which
     * leaves the loader's signature-based fallback in place (the ED.4 of a value whose payload is
     * in OBX-5.1 describes nothing).
     */
    static String encoding(MessageHandler handler, int i, int j, ObxLookup lookup) {
        if (!handler.isOBXEmbeddedDocument(i, j)) {
            return null;
        }
        String data = component(lookup, 5);
        if (data == null || data.isBlank()) {
            return null;
        }
        String encoding = component(lookup, 4);
        return encoding == null || encoding.isBlank() ? null : encoding.trim();
    }

    /**
     * A text payload (ED.4 {@code A}) for display: ED.5 normalised the way the handlers'
     * {@code getString} normalises ordinary results (trimmed, HL7 {@code \.br\} turned into the
     * {@code <br />} marker that the {@code htmlWithBreakMarkers} rendering expects), otherwise
     * the handler's own {@code getOBXResult}, which is already normalised. Never applied to the
     * data handed to the PDF decoder, and only for ED.5 the handler declares as text or that is
     * undeclared and not shaped like base64: a declared Base64/Hex ED.5, or an undeclared
     * base64-shaped one, is a document, not a value to show. Views call this only for rows the
     * loader classed {@code Status.TEXT}.
     */
    static String text(MessageHandler handler, int i, int j, ObxLookup lookup) {
        if (handler.isOBXEmbeddedDocument(i, j)) {
            String data = component(lookup, 5);
            if (data != null && !data.isBlank()) {
                String encoding = handler.getOBXDocumentEncoding(i, j);
                // Text is ED.5 declared A, or undeclared ED.5 not even shaped like base64: the same
                // rule EmbeddedLabDocumentLoader applies before classing a payload Status.TEXT.
                if ("A".equals(encoding) || (encoding == null && !isBase64Shaped(data))) {
                    return normaliseText(data);
                }
            }
        }
        String result = handler.getOBXResult(i, j);
        return result == null ? "" : result;
    }

    /**
     * Whether a payload has the shape of base64 as the loader's decoders accept it, so that
     * without a declared ED.4 encoding it cannot be told from encoded bytes and is never treated
     * as text; anything else undeclared is text.
     *
     * <ul>
     *   <li>Line breaks (senders wrap base64 at 76 or 80 columns) are ignored; any other
     *       whitespace, such as the spaces between words, means text.</li>
     *   <li>Characters are the standard or the URL-safe alphabet ({@code - _}), both of which
     *       the lenient decoder reads, with at most two {@code =} only at the end.</li>
     *   <li>Padded input is a multiple of four characters; unpadded input may stop short of one,
     *       but never one character into a quantum, which encodes no byte.</li>
     * </ul>
     *
     * @param payload the embedded-document payload, as sent
     * @return {@code true} when it is base64-shaped
     * @since 2026-10-02
     */
    public static boolean isBase64Shaped(String payload) {
        if (payload == null) {
            return false;
        }
        String compact = payload.replaceAll("[\\r\\n]+", "").trim();
        int length = compact.length();
        if (length == 0) {
            return false;
        }
        int padding = compact.endsWith("==") ? 2 : compact.endsWith("=") ? 1 : 0;
        if (padding > 0 ? length % 4 != 0 : length % 4 == 1) {
            return false;
        }
        for (int i = 0; i < length - padding; i++) {
            char c = compact.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** Trims and turns each HL7 {@code \.br\} escape into the shared {@code <br />} marker. */
    static String normaliseText(String text) {
        return text.trim().replace(LINE_BREAK_ESCAPE, "<br />");
    }

    /**
     * Whether the embedded-document payload is the handler's {@code getOBXResult} because the
     * OBX is {@code ED} and its segment is reachable but ED.5 is empty: the value a legacy feed
     * sends in OBX-5.1. {@code false} when the segment cannot be reached, since the payload's
     * origin is then unknown.
     */
    static boolean resultFallback(MessageHandler handler, int i, int j, ObxLookup lookup) {
        if (!handler.isOBXEmbeddedDocument(i, j)) {
            return false;
        }
        Segment obx;
        try {
            obx = lookup.obx();
        } catch (Exception unavailable) {
            return false;
        }
        if (obx == null) {
            return false;
        }
        String data = component(() -> obx, 5);
        return data == null || data.isBlank();
    }

    private static String component(ObxLookup lookup, int component) {
        try {
            Segment obx = lookup.obx();
            return obx == null ? null : Terser.get(obx, 5, 0, component, 1);
        } catch (Exception unavailable) {
            return null;
        }
    }
}
