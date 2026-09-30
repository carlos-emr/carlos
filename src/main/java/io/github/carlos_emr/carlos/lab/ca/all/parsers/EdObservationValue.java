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
 * lost. Handlers that can reach their OBX segment use these helpers for
 * {@link MessageHandler#getOBXEmbeddedDocumentData(int, int)} and
 * {@link MessageHandler#getOBXDocumentEncoding(int, int)}. When ED.5 is empty the handler's own
 * result is kept, so a sender that puts the payload in OBX-5.1 (as legacy feeds do) is unaffected.</p>
 *
 * @since 2026-09-30
 */
final class EdObservationValue {

    /** Resolves the OBX segment; any failure means "not available". */
    @FunctionalInterface
    interface ObxLookup {
        Segment obx() throws Exception;
    }

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

    private static String component(ObxLookup lookup, int component) {
        try {
            return Terser.get(lookup.obx(), 5, 0, component, 1);
        } catch (Exception unavailable) {
            return null;
        }
    }
}
