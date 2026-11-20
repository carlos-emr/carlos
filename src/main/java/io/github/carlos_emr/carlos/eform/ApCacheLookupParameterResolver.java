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
package io.github.carlos_emr.carlos.eform;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the form and patient identity an interactive APCache lookup runs under.
 *
 * <p>{@code APCache.js} forwards the page's own query string with every lookup it sends to
 * {@code eform/efmformapconfig_lookup}. The add/edit viewer is opened with {@code fid} and
 * {@code demographic_no}, but the save result view is a different URL: {@link
 * io.github.carlos_emr.carlos.eform.data.EForm} writes the form action as
 * {@code addEForm?efmfid=..&efmdemographic_no=..&efmprovider_no=..}, so once a form has been
 * saved the same lookups arrive carrying only the {@code efm}-prefixed names. Reading the plain
 * names alone answered every post-save lookup with an "Invalid fid" 400, which left the form's
 * APCache fields blank behind the library's "an error has occurred" alert. Both spellings are
 * accepted here; the plain name wins when both are present.</p>
 *
 * <p>Static utility, no state; not instantiable.</p>
 */
public final class ApCacheLookupParameterResolver {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private ApCacheLookupParameterResolver() {
    }

    /**
     * The eForm id the lookup runs against.
     *
     * @return the digits-only form id from {@code fid} or, failing that, {@code efmfid}; {@code null}
     *     when neither carries a numeric value, in which case the caller must refuse the lookup
     *     rather than construct an {@code EForm} from it
     */
    public static String resolveFid(HttpServletRequest request) {
        return firstNumeric(request, "fid", "efmfid");
    }

    /**
     * The patient the lookup runs for.
     *
     * @return the first non-blank of {@code demographic_no} and {@code efmdemographic_no}, or
     *     {@code null} when neither is present. Not validated beyond presence: the value only
     *     ever reaches the database as a bound query parameter, and patient-independent forms
     *     legitimately run without one
     */
    public static String resolveDemographicNo(HttpServletRequest request) {
        for (String name : new String[] {"demographic_no", "efmdemographic_no"}) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstNumeric(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getParameter(name);
            if (value != null && DIGITS.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }
}
