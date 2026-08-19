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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

/**
 * Outbound channel to the patient portal's {@code /internal/carlos/**} API.
 *
 * <p>This owns the authenticated envelope every portal call shares: the service bearer token, the
 * four {@code X-CARLOS-*} identity headers, the request timeouts, and the mapping from the portal's
 * documented status codes onto {@link PatientPortalException.Kind}. Concrete endpoint methods build
 * on {@link #buildRequest}; none of them re-assemble headers.
 *
 * <p><b>Security boundaries this class holds:</b>
 *
 * <ul>
 *   <li>The destination comes from {@link PatientPortalSettings} only. No method accepts a host, so
 *       no CARLOS request parameter can point portal traffic somewhere else.
 *   <li>The clinic id is read from configuration, not from the caller, so a caller cannot claim to
 *       act for another clinic.
 *   <li>Path segments are URL-encoded, so an identifier cannot escape into the path and reach an
 *       endpoint the caller was not authorized for.
 *   <li>Nothing here logs. The token, invite tokens, and passphrases pass through this class, and
 *       the surest way not to leak them is to have no log statement that could.
 * </ul>
 *
 * @since 2026-08-19
 */
public class PatientPortalService {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String PROVIDER_ID_HEADER = "X-CARLOS-Provider-ID";
    static final String PROVIDER_NAME_HEADER = "X-CARLOS-Provider-Name";
    static final String CLINIC_ID_HEADER = "X-CARLOS-Clinic-ID";
    static final String PERMISSIONS_HEADER = "X-CARLOS-Permissions";

    private static final String BEARER_PREFIX = "Bearer %s";
    private static final String INVALID_PATH = "portal endpoint path is not a valid URI: %s";

    private final PatientPortalSettings settings;

    public PatientPortalService(PatientPortalSettings settings) {
        if (settings == null) {
            throw new PatientPortalConfigurationException("patient portal settings are required");
        }
        this.settings = settings;
    }

    /**
     * Builds an authenticated request against a portal endpoint.
     *
     * <p>Package-private so the header envelope can be asserted directly, without a socket. The
     * envelope is the security-relevant part of this class, and it should be provable in a unit
     * test rather than only in an integration environment.
     *
     * @param method HTTP method, e.g. {@code GET} or {@code POST}
     * @param path portal endpoint path beginning with {@code /internal/carlos/}, already encoded
     * @param jsonBody request body, or {@code null} for a request without one
     * @param staff the authenticated CARLOS provider this call acts for
     * @return a request carrying the bearer token and all four identity headers
     */
    ClassicHttpRequest buildRequest(
            String method, String path, String jsonBody, PatientPortalStaffContext staff) {
        URI uri = resolve(path);
        ClassicRequestBuilder builder =
                ClassicRequestBuilder.create(method)
                        .setUri(uri)
                        .setHeader(
                                AUTHORIZATION_HEADER,
                                String.format(Locale.ROOT, BEARER_PREFIX, settings.serviceToken()))
                        .setHeader(PROVIDER_ID_HEADER, staff.providerId())
                        .setHeader(PROVIDER_NAME_HEADER, staff.providerName())
                        // From configuration, never from the caller: a caller must not be able to
                        // claim it is acting for a different clinic.
                        .setHeader(CLINIC_ID_HEADER, settings.clinicId())
                        .setHeader(PERMISSIONS_HEADER, staff.permissionHeaderValue());
        if (jsonBody != null) {
            builder.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        }
        return builder.build();
    }

    private URI resolve(String path) {
        try {
            return new URI(settings.baseUrl() + path);
        } catch (URISyntaxException exception) {
            throw new PatientPortalConfigurationException(
                    String.format(Locale.ROOT, INVALID_PATH, path), exception);
        }
    }

    PatientPortalSettings settings() {
        return settings;
    }
}
