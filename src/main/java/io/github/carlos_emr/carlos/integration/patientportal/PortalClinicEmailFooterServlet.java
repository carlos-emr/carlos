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

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterSnapshot;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;

/**
 * Read-only provider of the saved clinic footer to the portal's mail worker. The portal's emails need
 * it even when an administrator turned the footer off for CARLOS's own emails.
 */
@WebServlet(name = "PortalClinicEmailFooter", urlPatterns = "/ws/portal/email-footer")
public final class PortalClinicEmailFooterServlet extends HttpServlet {
    private static final Logger logger = MiscUtils.getLogger();
    private final Supplier<PatientPortalSettings> settings;
    private final Supplier<ClinicEmailFooterSnapshot> footers;
    private final ObjectMapper mapper = new ObjectMapper();

    public PortalClinicEmailFooterServlet() {
        this(PatientPortalSettings::fromCarlosProperties,
                () -> SpringUtils.getBean(ClinicEmailFooterService.class).portalSnapshot());
    }

    PortalClinicEmailFooterServlet(Supplier<PatientPortalSettings> settings,
            Supplier<ClinicEmailFooterSnapshot> footers) {
        this.settings = settings;
        this.footers = footers;
    }

    // FindSecBugs SERVLET_QUERY_STRING: the query string is used only when the whole of it is "nonce=" plus 43 URL-safe Base64 characters that pass canonicalNonce; anything else is refused with 400, and it is never logged.
    @SuppressFBWarnings(value = "SERVLET_QUERY_STRING", justification = "the query string is used only when it is exactly a 43-character URL-safe Base64 nonce that passes canonicalNonce; anything else gets 400, and it is never logged")
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            refuse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        try {
            PatientPortalSettings configured = settings.get();
            var authorization = Collections.list(request.getHeaders("Authorization"));
            if (authorization.size() != 1 || !authorized(authorization.get(0), configured)) {
                refuse(response, HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            String query = request.getQueryString();
            if (query == null || !query.matches("nonce=[A-Za-z0-9_-]{43}")
                    || !PortalClinicEmailFooterProtocol.canonicalNonce(query.substring(6))) {
                refuse(response, HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            String assertion = PortalStaffAssertionSigner.from(configured.staffAssertionPrivateKey())
                    .signEmailFooter(configured, query.substring(6), footers.get());
            byte[] bytes = mapper.writeValueAsBytes(mapper.createObjectNode().put("assertion", assertion));
            if (bytes.length > PortalClinicEmailFooterProtocol.MAX_RESPONSE_BYTES) {
                logger.warn("Portal clinic email footer refused: the signed response exceeds the size limit");
                refuse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getOutputStream().write(bytes); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- Jackson-built JSON holding only the assertion signed above, sent as application/json with nosniff
        } catch (RuntimeException failure) {
            // Do not echo or log authorization, query, configured secrets or footer contents: the
            // exception class alone tells an operator whether settings, signing or the footer failed.
            logger.warn("Portal clinic email footer unavailable ({})", failure.getClass().getName());
            refuse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    private static boolean authorized(String header, PatientPortalSettings settings) {
        if (header == null || !header.matches("Bearer [0-9a-f]{64}")) {
            return false;
        }
        byte[] supplied = header.substring(7).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = PortalClinicEmailFooterProtocol.readToken(settings).expose()
                .getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    private void refuse(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.getOutputStream().write(mapper.writeValueAsBytes(
                mapper.createObjectNode().put("error", "clinic footer unavailable")));
    }
}
