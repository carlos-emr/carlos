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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Sends portal requests over Apache HttpClient 5, the client CARLOS already uses for outbound HTTP.
 *
 * <p>Three settings here are security-relevant rather than tuning:
 *
 * <ul>
 *   <li><b>Redirects are disabled.</b> Following one would replay the {@code Authorization} header
 *       at whatever host the response named, handing the portal service token to it. A redirect
 *       from the portal is a misconfiguration, and failing is the correct response.
 *   <li><b>Timeouts are always set.</b> Without them a stalled portal would pin request threads
 *       until the connector pool was exhausted, taking the EMR down with it.
 *   <li><b>The response body is capped.</b> A compromised or malfunctioning portal should not be
 *       able to exhaust heap through a reply CARLOS reads into memory.
 * </ul>
 *
 * @since 2026-08-19
 */
class PatientPortalHttpClientExchange implements PatientPortalHttpExchange {

    /** Portal replies are small JSON documents; the largest is a capped invite or review page. */
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final PatientPortalSettings settings;

    PatientPortalHttpClientExchange(PatientPortalSettings settings) {
        this.settings = settings;
    }

    @Override
    public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(settings.connectTimeout().toMillis()))
                        .setResponseTimeout(
                                Timeout.ofMilliseconds(settings.readTimeout().toMillis()))
                        .setRedirectsEnabled(false)
                        .build();
        if (request instanceof HttpUriRequestBase uriRequest) {
            uriRequest.setConfig(requestConfig);
        }
        try (CloseableHttpClient client =
                HttpClients.custom()
                        .disableRedirectHandling()
                        .setDefaultRequestConfig(requestConfig)
                        .build()) {
            return client.execute(request, PatientPortalHttpClientExchange::toResponse);
        }
    }

    private static PatientPortalHttpResponse toResponse(ClassicHttpResponse response)
            throws IOException {
        HttpEntity entity = response.getEntity();
        String body = entity == null ? "" : readCapped(entity);
        return new PatientPortalHttpResponse(response.getCode(), body);
    }

    /**
     * Reads at most {@link #MAX_RESPONSE_BYTES} characters, then stops.
     *
     * <p>Truncation is silent on purpose: every documented portal reply fits well inside the cap, so
     * exceeding it means the peer is not behaving like the portal, and the resulting body will fail
     * JSON parsing and surface as a transport failure rather than as partial data.
     */
    private static String readCapped(HttpEntity entity) throws IOException {
        StringBuilder collected = new StringBuilder();
        char[] buffer = new char[8192];
        try (InputStream content = entity.getContent();
                Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) >= 0 && collected.length() < MAX_RESPONSE_BYTES) {
                collected.append(buffer, 0, read);
            }
        }
        return collected.toString();
    }
}
