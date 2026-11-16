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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a CARLOS export writes the Demographics values whose stored form no element holds exactly,
 * shared with the CARLOS importer that restores the stored value.
 *
 * <p>No stored value holds a chart. A required element (a name part, DateOfBirth, Gender,
 * PersonStatusCode) gets the value these rules give, an optional one is left out, and whenever
 * the element is not the stored value exactly the stored column travels in the {@code
 * demographic} object of the native demographic record ({@link
 * CdsNativeExtensionContract#NATIVE_DEMOGRAPHIC}), SQL NULL as JSON {@code null}. The importer
 * restores a carried column only when its element is exactly what these rules write from it.
 */
public final class CdsDemographicCarrierContract {
    /** A personNamePart is an {@code xs:token} of at most 50 characters (code points). */
    public static final int NAME_PART_LENGTH = 50;

    /**
     * DateOfBirth written when the stored year, month and day are not one calendar date: the
     * earliest {@code xs:date}, which OSCAR and the legacy CDS export use for "no date".
     */
    public static final String NO_BIRTH_DATE = "0001-01-01";

    /** The Administrative Gender codes CARLOS stores under the same code. */
    private static final Set<String> GENDERS = Set.of("M", "F", "O", "U");

    /** NamePrefix codes by stored title: each schema code, its capitals, and RT_HON. */
    private static final Map<String, String> TITLES = titles();

    /** The native statuses with a PersonStatusAsEnum value. */
    private static final Map<String, String> ENUMERATED_STATUSES =
            Map.of("AC", "A", "IN", "I", "DE", "D");

    private CdsDemographicCarrierContract() {}

    /**
     * The value every schema reader sees for an {@code xs:token}: leading and trailing space, tab,
     * carriage return and line feed dropped, each inner run of them one space.
     */
    public static String token(String value) {
        var result = new StringBuilder(value.length());
        boolean pending = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pending = result.length() > 0;
                continue;
            }
            if (pending) result.append(' ');
            pending = false;
            result.append(c);
        }
        return result.toString();
    }

    /**
     * The Part of a required legal name (FirstName, LastName) for a stored name: its {@code
     * xs:token} form with U+FFFD for each character XML cannot carry ({@link
     * CdsNativeExtensionContract#xmlElementText}), cut to its first 50 characters when longer.
     * Nothing is appended inside a name, so another EMR reads a plain name. A missing or blank
     * name is the empty Part, which the schema allows.
     */
    public static String namePart(String stored) {
        if (stored == null) return "";
        return cut(CdsNativeExtensionContract.xmlElementText(token(stored)));
    }

    /**
     * The OtherName parts (type GIV) for stored middle names, in order. Middle names that fit one
     * part are that one part; longer ones (the column holds 100) are their space-separated names,
     * one part each, and a single name longer than a part is cut to its first 50 characters. The
     * CARLOS importer joins the parts with single spaces. Blank middle names have no part.
     */
    public static List<String> middleNameParts(String stored) {
        if (stored == null) return List.of();
        String shown = CdsNativeExtensionContract.xmlElementText(token(stored));
        if (shown.isEmpty()) return List.of();
        if (shown.codePointCount(0, shown.length()) <= NAME_PART_LENGTH) return List.of(shown);
        var parts = new ArrayList<String>();
        for (String name : shown.split(" ", -1)) parts.add(cut(name));
        return List.copyOf(parts);
    }

    /**
     * Administrative Gender for a stored sex code. M, F, O and U are written as stored; X
     * (Intersex, which the add form offers) is O; any other code, or none, is U, the schema's
     * code for an unknown gender.
     */
    public static String gender(String stored) {
        if (stored != null && GENDERS.contains(stored)) return stored;
        return "X".equals(stored) ? "O" : "U";
    }

    /**
     * The calendar date the three stored birth columns state: a four-digit year from 0001, a
     * month and day of one or two digits that make a real date. Null when they do not.
     */
    public static LocalDate birthDate(String year, String month, String day) {
        if (year == null
                || !year.matches("[0-9]{4}")
                || month == null
                || !month.matches("[0-9]{1,2}")
                || day == null
                || !day.matches("[0-9]{1,2}")) return null;
        try {
            LocalDate date =
                    LocalDate.of(
                            Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return date.getYear() < 1 ? null : date;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    /**
     * The three columns the CARLOS importer writes for a calendar birth date: a four-digit year,
     * two-digit month and day.
     */
    public static List<String> birthColumns(LocalDate date) {
        return List.of(
                String.format(java.util.Locale.ROOT, "%04d", date.getYear()),
                String.format(java.util.Locale.ROOT, "%02d", date.getMonthValue()),
                String.format(java.util.Locale.ROOT, "%02d", date.getDayOfMonth()));
    }

    /** PersonStatusCode: one of its two choices and the text written in it. */
    public record PersonStatus(boolean enumerated, String text) {
        @Override
        public String toString() {
            return "PersonStatus[protected]";
        }
    }

    /**
     * PersonStatusCode for a stored patient status. AC, IN and DE are PersonStatusAsEnum A, I and
     * D. A missing status is an empty PersonStatusAsPlainText: the element is required and an
     * empty text states no status, where any code would assert one. Any other status is
     * PersonStatusAsPlainText: CARLOS's own codes by meaning ({@link CdsPatientStatusLabels}),
     * a clinic-defined status as stored, with U+FFFD for each character XML cannot carry.
     */
    public static PersonStatus personStatus(String stored) {
        if (stored == null || stored.isEmpty()) return new PersonStatus(false, "");
        String enumerated = ENUMERATED_STATUSES.get(stored);
        if (enumerated != null) return new PersonStatus(true, enumerated);
        return new PersonStatus(
                false,
                CdsNativeExtensionContract.xmlElementText(
                        CdsPatientStatusLabels.LABELS.getOrDefault(stored, stored)));
    }

    /**
     * NamePrefix for a stored title, or null when the schema lists no code for it. The add form
     * stores the codes in capitals (DR, MRS) and "Right Honourable" as RT_HON; the schema code is
     * the listed spelling (Dr, Mrs, RtHon).
     */
    public static String title(String stored) {
        return stored == null ? null : TITLES.get(stored);
    }

    private static Map<String, String> titles() {
        var result = new java.util.LinkedHashMap<String, String>();
        for (String title :
                List.of(
                        "Bro", "Capt", "Chief", "Cst", "Corp", "Dr", "Fr", "Hon", "Lt", "Madam",
                        "Mme", "Mlle", "Major", "Mayor", "Miss", "Mr", "Mssr", "Mrs", "Ms", "Prof",
                        "Reeve", "Rev", "RtHon", "Sen", "Sgt", "Sr")) {
            result.put(title, title);
            result.put(title.toUpperCase(java.util.Locale.ROOT), title);
        }
        result.put("RT_HON", "RtHon");
        return Map.copyOf(result);
    }

    private static String cut(String value) {
        if (value.codePointCount(0, value.length()) <= NAME_PART_LENGTH) return value;
        return token(value.substring(0, value.offsetByCodePoints(0, NAME_PART_LENGTH)));
    }
}
