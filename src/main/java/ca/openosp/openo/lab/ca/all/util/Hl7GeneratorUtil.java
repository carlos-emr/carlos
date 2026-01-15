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

    private Hl7GeneratorUtil() {}

    public static String safe(String value) {
        return value != null ? value : "";
    }

    public static String safeUpper(String value) {
        return value != null ? value.toUpperCase() : "";
    }

    public static String safeUpper(String value, String defaultValue) {
        return value != null ? value.toUpperCase() : defaultValue;
    }

    public static String safeWithDefault(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public static String buildReferenceRange(LabTest test) {
        if (test.getRefRangeText() != null && !test.getRefRangeText().isEmpty()) {
            return test.getRefRangeText();
        } else if (test.getRefRangeLow() != null && test.getRefRangeHigh() != null) {
            return test.getRefRangeLow() + " - " + test.getRefRangeHigh();
        }
        return "";
    }

    public static String getBlockedStatus(LabTest test) {
        return "BLOCKED".equals(test.getBlocked()) ? "BLOCKED" : "";
    }

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

        public NameComponents(String givenName, String familyName) {
            this.givenName = givenName;
            this.familyName = familyName;
        }
    }

    public static String formatDob(Date dob) {
		if (dob == null) {
			return "";
		}
		return toLocalDate(dob).format(DATE_FORMAT);
	}

	public static LocalDate toLocalDate(Date date) {
		if (date == null) {
			return null;
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	public static String formatDate(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT);
	}

	public static String formatDateTime(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_TIME_FORMAT);
	}

    public static String currentDateTime() {
		return LocalDateTime.now().format(DATE_TIME_FORMAT);
	}

	public static String formatShortDateTime(Date date) {
		if (date == null) {
			return "";
		}
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(SHORT_DATE_TIME_FORMAT);
	}
}