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
package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

/**
 * Downloads the National Vaccine Catalogue V2 bundle.
 *
 * <p>The address is fixed: HTTPS to {@code nvc-cnv.canada.ca}, with redirects refused, so neither
 * configuration nor a user can point CARLOS at another host. The body is read up to
 * {@link #MAX_BUNDLE_BYTES}; a larger answer fails the download rather than filling memory.</p>
 *
 * @since 2026-10-05
 */
class NationalVaccineCatalogueClient {

    static final URI BUNDLE_URI = URI.create(NationalVaccineCatalogueMapper.NVC_BASE + "/Bundle/NVC");
    /** The full bundle was about 9 MB in October 2026. */
    static final int MAX_BUNDLE_BYTES = 64 * 1024 * 1024;
    private static final String FHIR_JSON = "application/fhir+json";

    /**
     * Returns the bundle as JSON text.
     *
     * @throws IOException if the catalogue cannot be reached, answers anything but 200 with a body,
     *     or sends more than {@link #MAX_BUNDLE_BYTES}
     */
    String fetchBundleJson() throws IOException {
        HttpGet request = new HttpGet(BUNDLE_URI);
        request.setHeader(HttpHeaders.ACCEPT, FHIR_JSON);
        try (CloseableHttpClient client = newClient()) {
            return client.execute(request, NationalVaccineCatalogueClient::readBundle);
        }
    }

    private static CloseableHttpClient newClient() {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(15))
                                .setSocketTimeout(Timeout.ofSeconds(120))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(15))
                        .setResponseTimeout(Timeout.ofSeconds(120))
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    static String readBundle(ClassicHttpResponse response) throws IOException {
        if (response.getCode() != 200) {
            throw new IOException("National Vaccine Catalogue answered HTTP " + response.getCode());
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new IOException("National Vaccine Catalogue answered without a body");
        }
        if (entity.getContentLength() > MAX_BUNDLE_BYTES) {
            throw new IOException("National Vaccine Catalogue bundle is larger than " + MAX_BUNDLE_BYTES + " bytes");
        }
        try (InputStream body = entity.getContent()) {
            String json = readBounded(body, MAX_BUNDLE_BYTES);
            if (json.isBlank()) {
                throw new IOException("National Vaccine Catalogue answered with an empty body");
            }
            return json;
        }
    }

    /** Reads UTF-8 text, failing as soon as it passes {@code maxBytes}. */
    static String readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("National Vaccine Catalogue bundle is larger than " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
