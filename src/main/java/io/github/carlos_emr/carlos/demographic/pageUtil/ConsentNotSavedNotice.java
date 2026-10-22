/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.model.ConsentType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Carries "the consent part of this chart save was refused" from the save to the chart page it
 * redirects to.
 *
 * <p>Request attributes do not survive a redirect, so the save adds the refused consent type ids
 * to the redirect as the {@value #PARAMETER} query parameter. The value holds consent type ids
 * only, never anything about the patient. The chart page trusts none of it: a value that is not
 * digits and commas is ignored, and the ids are only ever used to look up consent type names
 * server-side, so the raw value is never written to the page.</p>
 *
 * @since 2026-09-29
 */
public final class ConsentNotSavedNotice {

    /** Query parameter, and request attribute on a forward, holding the refused consent type ids. */
    public static final String PARAMETER = "consentNotSaved";

    /** Request attribute holding the display names of the refused consent types, for the page. */
    public static final String NAMES_ATTRIBUTE = "consentNotSavedNames";

    /** The most consent types one notice names. */
    private static final int MAX_IDS = 50;

    /** Comma-separated non-negative integers; bounded so a crafted link cannot grow the lookup. */
    private static final Pattern WELL_FORMED = Pattern.compile("\\d{1,9}(,\\d{1,9}){0," + (MAX_IDS - 1) + "}");

    private ConsentNotSavedNotice() {
    }

    /**
     * @param refused the consent types whose consent change was refused
     * @return their ids, comma-separated, or an empty string when there are none
     */
    public static String parameterValue(List<ConsentType> refused) {
        if (refused == null) {
            return "";
        }
        return refused.stream()
                .filter(consentType -> consentType != null && consentType.getId() != null)
                .map(consentType -> String.valueOf(consentType.getId()))
                .distinct()
                // No more than the chart page accepts, so a long list still shows a notice.
                .limit(MAX_IDS)
                .collect(Collectors.joining(","));
    }

    /**
     * @param raw a value received from a request
     * @return the value when it is digits and commas only, otherwise {@code null}
     */
    public static String validated(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return WELL_FORMED.matcher(trimmed).matches() ? trimmed : null;
    }

    /**
     * Adds the parameter to a URL. The value is written only after {@link #validated} accepts it,
     * so it needs no escaping: digits and commas are literal in a query string.
     *
     * @param url   an application URL, with or without a query string
     * @param value consent type ids as {@link #parameterValue} builds them; may come from a request
     * @return the URL with the parameter, or the URL unchanged when the value is empty or malformed
     */
    public static String appendTo(String url, String value) {
        String safeValue = validated(value);
        if (safeValue == null) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + PARAMETER + '=' + safeValue;
    }

    /**
     * @param raw         the {@value #PARAMETER} value received by the chart page
     * @param activeTypes the consent types the chart page shows
     * @return display names of the consent types the value names, in the page's order; empty when
     *         the value is missing or malformed, or names no consent type the page shows
     */
    public static List<String> consentTypeNames(String raw, List<ConsentType> activeTypes) {
        List<String> names = new ArrayList<>();
        String safeValue = validated(raw);
        if (safeValue == null || activeTypes == null) {
            return names;
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (String id : safeValue.split(",")) {
            ids.add(Integer.valueOf(id));
        }
        for (ConsentType consentType : activeTypes) {
            if (consentType != null && consentType.getId() != null && ids.contains(consentType.getId())) {
                String name = consentType.getName();
                // A type without a display name is still named, by its code, rather than hidden.
                names.add(name == null || name.isBlank() ? consentType.getType() : name);
            }
        }
        return names;
    }
}
