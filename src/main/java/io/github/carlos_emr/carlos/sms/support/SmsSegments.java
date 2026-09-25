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
package io.github.carlos_emr.carlos.sms.support;

/**
 * Counts how many SMS segments a message body occupies, the way carriers do.
 * <p>
 * A body made only of GSM 03.38 default-alphabet characters is sent as GSM-7: 160 characters fit one
 * segment, and characters from the extension table ({@code ^ { } [ ] ~ | \ €}, form feed) take two.
 * Any other character, including accents outside the GSM alphabet (for example {@code ô}, {@code ç}),
 * curly quotes and dashes, switches the whole body to UCS-2, where one segment holds 70 UTF-16 units
 * and an emoji takes two. Multipart messages lose room to the concatenation header: 153 units per
 * part in GSM-7 and 67 in UCS-2. Part counts are the usual ceiling estimate; a carrier may use one more
 * part when it avoids splitting an escaped extension character.
 * <p>
 * Used to keep a send within one segment (see {@code SmsSendValidator}) and suitable for a compose
 * screen's live counter.
 *
 * @since 2026-09-23
 */
public final class SmsSegments {
    /** GSM 03.38 default alphabet, less the escape character that introduces the extension table. */
    private static final String GSM_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    /** GSM 03.38 extension table; each is sent as an escape plus the character, so it costs two units. */
    private static final String GSM_EXTENSION = "\f^{}\\[~]|€";

    private static final int GSM_SINGLE_SEGMENT = 160;
    private static final int GSM_MULTIPART_SEGMENT = 153;
    private static final int UCS2_SINGLE_SEGMENT = 70;
    private static final int UCS2_MULTIPART_SEGMENT = 67;

    /** The character set a body is sent in. */
    public enum Encoding {
        GSM_7,
        UCS_2
    }

    /**
     * Result of counting a body.
     *
     * @param encoding the encoding the body is sent in
     * @param units    septets for GSM-7 (extension characters count two) or UTF-16 units for UCS-2
     * @param segments segments needed; zero for an empty body
     */
    public record Count(Encoding encoding, int units, int segments) {
        /** @return the units one segment holds in this encoding: 160 for GSM-7, 70 for UCS-2 */
        public int singleSegmentLimit() {
            return encoding == Encoding.GSM_7 ? GSM_SINGLE_SEGMENT : UCS2_SINGLE_SEGMENT;
        }
    }

    private SmsSegments() {
    }

    /**
     * @param body message text; {@code null} is treated as empty
     * @return the encoding, unit count and segment count for {@code body}
     */
    public static Count count(String body) {
        String text = body == null ? "" : body;
        int gsmUnits = gsmUnits(text);
        if (gsmUnits < 0) {
            return new Count(Encoding.UCS_2, text.length(),
                    segments(text.length(), UCS2_SINGLE_SEGMENT, UCS2_MULTIPART_SEGMENT));
        }
        return new Count(Encoding.GSM_7, gsmUnits,
                segments(gsmUnits, GSM_SINGLE_SEGMENT, GSM_MULTIPART_SEGMENT));
    }

    /** @return the GSM-7 septet count, or {@code -1} when a character is outside the GSM alphabet */
    private static int gsmUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_BASIC.indexOf(c) >= 0) {
                units++;
            } else if (GSM_EXTENSION.indexOf(c) >= 0) {
                units += 2;
            } else {
                return -1;
            }
        }
        return units;
    }

    private static int segments(int units, int singleSegment, int multipartSegment) {
        if (units == 0) {
            return 0;
        }
        if (units <= singleSegment) {
            return 1;
        }
        return (units + multipartSegment - 1) / multipartSegment;
    }
}
