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
package io.github.carlos_emr.carlos.sms.service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * An SMS provider's callback as it reached CARLOS, in whatever shape the provider uses: a signed POST with a JSON
 * body, or a GET with everything in the web address (as VoIP.ms sends incoming texts). The callback endpoint
 * (#3837) builds it from the HTTP request; the provider's client checks and reads it.
 * <p>
 * It holds patient phone numbers and message text, so {@link #toString()} never shows a value, and nothing in it
 * may be logged.
 *
 * @param method          the HTTP method, upper case
 * @param queryParameters the web address's query values, by name, in the order given
 * @param headers         the request headers, by name; looked up ignoring case
 * @param body            the request body, or empty
 * @since 2026-10-08
 */
public record SmsWebhookRequest(String method, Map<String, List<String>> queryParameters,
                                Map<String, String> headers, String body) {

    public SmsWebhookRequest {
        method = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        queryParameters = queryParameters == null ? Map.of() : copyOf(queryParameters);
        TreeMap<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name != null && value != null) {
                    caseInsensitive.put(name, value);
                }
            });
        }
        headers = Collections.unmodifiableMap(caseInsensitive);
        body = body == null ? "" : body;
    }

    /** @return the first value of a query parameter, or empty when it is absent */
    public Optional<String> queryParameter(String name) {
        List<String> values = queryParameters.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.get(0));
    }

    /** @return a header's value, the name matched ignoring case, or empty when it is absent */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    private static Map<String, List<String>> copyOf(Map<String, List<String>> parameters) {
        Map<String, List<String>> copy = new TreeMap<>();
        parameters.forEach((name, values) -> {
            if (name != null && values != null) {
                copy.put(name, values.stream().filter(Objects::nonNull).toList());
            }
        });
        return Map.copyOf(copy);
    }

    @Override
    public String toString() {
        return "SmsWebhookRequest[" + method + ", redacted]";
    }
}
