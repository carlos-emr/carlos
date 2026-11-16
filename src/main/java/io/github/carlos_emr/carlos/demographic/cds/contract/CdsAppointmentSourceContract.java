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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.time.Duration;
import java.time.LocalTime;

/**
 * The rules the CARLOS exporter and importer share for the per-appointment NewCategory record
 * {@value #ELEMENT_NAME} (category {@value #CATEGORY_NAME}).
 *
 * <p>CDS 5.2 Appointments has no ResidualInfo. The exporter writes each Appointments element in
 * its schema-valid form by the rules below and one NewCategory record that holds the complete
 * stored row; the importer recomputes the same rules to prove that a record belongs to the
 * element it names before it restores anything from it. A record that does not prove itself is
 * reported and never used.
 */
public final class CdsAppointmentSourceContract {
    /** NewCategory {@code CategoryName} of the per-appointment record. */
    public static final String CATEGORY_NAME = "CARLOS.NativeAppointment.v1";

    /** Residual {@code DataElement} name whose Content is the versioned JSON. */
    public static final String ELEMENT_NAME = "CARLOS.Appointment.Source.v1";

    /** AppointmentNotes holds 32,000 characters (CDS 5.2 schema). */
    public static final int NOTES_MAXIMUM = 32000;

    /**
     * Ends AppointmentNotes when the complete text is longer than the element: the element holds
     * the first characters that fit before this mark, the record the complete text.
     */
    public static final String SHORTENED_MARK =
            "\n[CARLOS: shortened to the CDS limit of 32,000 characters]";

    private CdsAppointmentSourceContract() {}

    /** The text without the code points no XML 1.0 document can hold. */
    public static String xmlText(String value) {
        if (value == null || value.codePoints().noneMatch(CdsAppointmentSourceContract::illegal))
            return value;
        var kept = new StringBuilder(value.length());
        value.codePoints().filter(code -> !illegal(code)).forEach(kept::appendCodePoint);
        return kept.toString();
    }

    private static boolean illegal(int code) {
        return code < 0x20 && code != '\t' && code != '\r' && code != '\n'
                || code >= 0xd800 && code <= 0xdfff
                || code == 0xfffe
                || code == 0xffff;
    }

    /**
     * AppointmentNotes for the complete notes text: the text without XML-illegal characters and,
     * past 32,000 characters (code points), its first characters followed by {@link
     * #SHORTENED_MARK}. Text the element can hold is returned unchanged.
     */
    public static String notesElement(String complete) {
        if (complete == null) return null;
        String text = xmlText(complete);
        if (text.codePointCount(0, text.length()) <= NOTES_MAXIMUM) return text;
        return text.substring(
                        0,
                        text.offsetByCodePoints(
                                0,
                                NOTES_MAXIMUM
                                        - SHORTENED_MARK.codePointCount(
                                                0, SHORTENED_MARK.length())))
                + SHORTENED_MARK;
    }

    /**
     * The slot's length in whole minutes from its stored start and end clock, or 0 when the
     * stored end states no positive length (Duration is then left out).
     *
     * <p>The CARLOS scheduler stores an inclusive end, start + duration - one minute; an
     * appointment written by a CDS import keeps the exclusive end, start + duration. A stored end
     * earlier than the start is an end on the next day. A started minute is counted whole. An
     * inclusive end up to one minute before the start (the add form's duration 0, which is also
     * what a full 24 hours stores) and an exclusive end equal to the start state no length.
     *
     * @param inclusive whether {@code end} is the scheduler's inclusive end
     * @return 0, or 1 to 1,440
     */
    public static int minutes(LocalTime start, LocalTime end, boolean inclusive) {
        long seconds = Duration.between(start, end).getSeconds();
        if (inclusive ? seconds < 0 && seconds >= -60 : seconds == 0) return 0;
        if (seconds < 0) seconds += 86_400;
        return Math.toIntExact(inclusive ? seconds / 60 + 1 : (seconds + 59) / 60);
    }
}
