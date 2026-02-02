//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * Modifications made by Magenta Health in 2026.
 */

package ca.openosp.openo.lab.ca.all.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import ca.openosp.openo.commn.model.LabTest;

public final class Hl7GeneratorUtil {

    public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
	public static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
	public static final DateTimeFormatter SHORT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
 * Prevents instantiation of this utility class.
 */
private Hl7GeneratorUtil() {}

    /**
     * Return the input string, or an empty string if the input is null.
     *
     * @param value the string to make null-safe
     * @return value if not null, empty string otherwise
     */
    public static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Convert the input string to upper case, or return an empty string when the input is null.
     *
     * @param value the string to convert; may be null
     * @return the input converted to upper case, or an empty string if {@code value} is null
     */
    public static String safeUpper(String value) {
        return value != null ? value.toUpperCase() : "";
    }

    /**
     * Return the input converted to upper case, or the supplied default when the input is null.
     *
     * @param value the string to convert to upper case; may be null
     * @param defaultValue the value to return when {@code value} is null
     * @return the upper-case form of {@code value}, or {@code defaultValue} if {@code value} is null
     */
    public static String safeUpper(String value, String defaultValue) {
        return value != null ? value.toUpperCase() : defaultValue;
    }

    /**
     * Return the original string when it is non-null and not empty, otherwise return the provided default.
     *
     * @param value        the primary value to use
     * @param defaultValue the fallback value returned when {@code value} is null or empty
     * @return the input {@code value} if it is non-null and not empty, otherwise {@code defaultValue}
     */
    public static String safeWithDefault(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Builds a human-readable reference range for the given lab test.
     *
     * @param test the LabTest whose reference range fields are used
     * @return the test's `refRangeText` if non-empty; otherwise `refRangeLow + " - " + refRangeHigh` when both are present; otherwise an empty string
     */
    public static String buildReferenceRange(LabTest test) {
        if (test.getRefRangeText() != null && !test.getRefRangeText().isEmpty()) {
            return test.getRefRangeText();
        } else if (test.getRefRangeLow() != null && test.getRefRangeHigh() != null) {
            return test.getRefRangeLow() + " - " + test.getRefRangeHigh();
        }
        return "";
    }

    /**
     * Provide the HL7 blocked status string for a lab test.
     *
     * @param test the lab test to inspect
     * @return "BLOCKED" if the test's blocked field equals "BLOCKED", empty string otherwise
     */
    public static String getBlockedStatus(LabTest test) {
        return "BLOCKED".equals(test.getBlocked()) ? "BLOCKED" : "";
    }

    /**
     * Converts a semicolon-delimited list of doctor entries into an HL7-style name component string.
     *
     * Each doctor entry is expected as comma-separated components (id, given name, family name). Components are trimmed,
     * joined with carets (`^`) and multiple doctors are separated with a tilde (`~`). If the input is null or empty,
     * an empty StringBuilder is returned.
     *
     * @param cc semicolon-separated doctor entries where each entry is "id[,givenName[,familyName]]"
     * @return a StringBuilder containing the HL7-formatted doctor list (components joined by `^`, doctors by `~`)
     */
    public static StringBuilder parseCCDoctors(String cc) {
        StringBuilder ccString = new StringBuilder();
        if (cc != null && !cc.isEmpty()) {
            String[] ccs = cc.split(";");
            for (int x = 0; x < ccs.length; x++) {
                String[] idName = ccs[x].split(",");
                if (x > 0) ccString.append("~");
                // Trim CC doctor components when building HL7 string
                ccString.append(idName[0].trim()).append("^")
                        .append(idName.length > 1 ? idName[1].trim() : "").append("^")
                        .append(idName.length > 2 ? idName[2].trim() : "");
            }
        }
        return ccString;
    }

    /**
     * Normalize a full personal name and split it into given and family name components.
     *
     * The method strips a leading "Dr" prefix (case-insensitive), trims surrounding whitespace,
     * splits the remaining string on whitespace, and converts name parts to upper case.
     *
     * @param fullName the raw full name string (may include a leading "Dr", punctuation, or extra whitespace)
     * @return a {@code NameComponents} instance whose {@code givenName} is the first name token (upper case)
     *         and whose {@code familyName} is the last name token (upper case); either field is an empty
     *         string when the corresponding part is not present
     */
    public static NameComponents parseName(String fullName) {
        if (fullName == null) {
            return new NameComponents("", "");
        }
        String cleanName = fullName.replaceAll("(?i)^DR\\.?\\s*", "").trim();
        String[] parts = cleanName.split("\\s+");

        String given = "";
        String family = "";
        if (parts.length >= 2) {
            given = parts[0].toUpperCase();
            family = parts[parts.length - 1].toUpperCase();
        } else if (parts.length == 1) {
            given = parts[0].toUpperCase();
        }
        return new NameComponents(given, family);
    }

    public static final class NameComponents {
        public final String givenName;
        public final String familyName;

        /**
         * Creates a NameComponents instance holding a person's given name and family name.
         *
         * @param givenName  the person's given (first) name; may be null or empty
         * @param familyName the person's family (last) name; may be null or empty
         */
        public NameComponents(String givenName, String familyName) {
            this.givenName = givenName;
            this.familyName = familyName;
        }
    }

    /**
	 * Formats a date of birth using the HL7 `yyyyMMdd` pattern.
	 *
	 * @param dob the date of birth; may be null
	 * @return the formatted date as `yyyyMMdd`, or an empty string if `dob` is null
	 */
	public static String formatDob(Date dob) {
		if (dob == null) {
			return "";
		}
		return toLocalDate(dob).format(DATE_FORMAT);
	}

	/**
	 * Converts a Date to a LocalDate using the system default time zone.
	 *
	 * @param date the Date to convert; interpreted in the system default time zone
	 * @return the corresponding LocalDate
	 */
	public static LocalDate toLocalDate(Date date) {
		if (date == null) {
			return null;
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	/**
	 * Format a Date as an HL7-style yyyyMMdd string.
	 *
	 * @param date the date to format; if null an empty string is returned
	 * @return the date formatted as "yyyyMMdd", or an empty string if `date` is null
	 */
	public static String formatDate(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
	}

	/**
	 * Format a Date as an HL7 timestamp using the pattern yyyyMMddHHmmss.
	 *
	 * @param date the date to format; may be null
	 * @return the formatted timestamp in `yyyyMMddHHmmss` format, or an empty string if `date` is null
	 */
	public static String formatDateTime(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_TIME_FORMAT);
	}

    /**
	 * Get the current date and time formatted for HL7 timestamps.
	 *
	 * @return the current date-time as a string using pattern "yyyyMMddHHmmss" (based on the system default time zone)
	 */
	public static String currentDateTime() {
		return LocalDateTime.now().format(DATE_TIME_FORMAT);
	}

	/**
	 * Format the given Date as an HL7 short date-time string (yyyyMMddHHmm).
	 *
	 * @param date the date to format; if null an empty string is returned
	 * @return the formatted date/time string in `yyyyMMddHHmm` format, or an empty string if `date` is null
	 */
	public static String formatShortDateTime(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(SHORT_DATE_TIME_FORMAT);
	}
}