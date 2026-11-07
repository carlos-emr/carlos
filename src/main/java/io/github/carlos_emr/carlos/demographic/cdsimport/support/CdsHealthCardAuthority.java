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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.util.Set;

/** Pinned authority vocabulary; unknown/sentinel values are never identity disambiguators. */
public final class CdsHealthCardAuthority {
    private CdsHealthCardAuthority() { }
    public static final Set<String> CANADIAN = Set.of("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT");
    public static final Set<String> PINNED_US = Set.of("US-AK", "US-AL", "US-AR", "US-AZ", "US-CA", "US-CO", "US-CT", "US-CZ", "US-DC", "US-DE", "US-FL", "US-GA", "US-GU", "US-HI", "US-IA", "US-ID", "US-IL", "US-IN", "US-KS", "US-KY", "US-LA", "US-MA", "US-MD", "US-ME", "US-MI", "US-MN", "US-MO", "US-MS", "US-MT", "US-NC", "US-ND", "US-NE", "US-NH", "US-NJ", "US-NM", "US-NV", "US-NY", "US-OH", "US-OK", "US-OR", "US-PA", "US-PR", "US-RI", "US-SC", "US-SD", "US-TN", "US-TX", "US-UT", "US-VA", "US-VI", "US-VT", "US-WA", "US-WI", "US-WV", "US-WY");

    /** Preserve unknown values for source/native display while normalizing exact Canadian aliases. */
    public static String normalize(String value) {
        if (value != null && value.startsWith("CA-") && CANADIAN.contains(value.substring(3))) return value.substring(3);
        return value;
    }
    /** NULL means identity authority is unknown, including -50/-70/-90 and unrecognized native codes. */
    public static String known(String value) {
        String normalized = normalize(value);
        return normalized != null && (CANADIAN.contains(normalized) || PINNED_US.contains(normalized)) ? normalized : null;
    }
}
