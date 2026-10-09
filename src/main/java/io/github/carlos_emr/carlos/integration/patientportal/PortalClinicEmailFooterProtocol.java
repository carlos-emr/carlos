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
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterSnapshot;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;
import io.github.carlos_emr.carlos.email.core.EmailInlineImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed, clinic-bound read credential and immutable signed footer wire format. */
final class PortalClinicEmailFooterProtocol {
    static final String AUDIENCE = "carlos-patient-portal-email-footer";
    static final int MAX_PAYLOAD_BYTES = 256 * 1024;
    static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final String DOMAIN = "carlos-portal-email-footer-read-v1";

    private PortalClinicEmailFooterProtocol() { }

    /** This derived value grants only read access to the footer endpoint, never staff API access. */
    static PortalSecret readToken(PatientPortalSettings settings) {
        byte[] key = settings.serviceToken().expose().getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(DOMAIN.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) 0);
            byte[] clinic = settings.clinicId().getBytes(StandardCharsets.UTF_8);
            mac.update(length(clinic));
            mac.update(clinic);
            return PortalSecret.of(HexFormat.of().formatHex(mac.doFinal()));
        } catch (GeneralSecurityException failure) {
            throw new PatientPortalConfigurationException("portal footer credential could not be prepared");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    static boolean canonicalNonce(String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(nonce);
        return decoded.length == 32
                && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(nonce);
    }

    static ObjectNode payload(ObjectMapper mapper, PatientPortalSettings settings, String nonce,
            ClinicEmailFooterSnapshot footer, long issuedAt) {
        if (!canonicalNonce(nonce) || footer == null) {
            throw new PatientPortalConfigurationException("portal footer snapshot is unavailable");
        }
        String html = footer.html();
        String text = EmailFooterHtml.toPlainText(html);
        if (html.isEmpty() || text.isEmpty() || html.length() > EmailFooterHtml.MAX_HTML_LENGTH
                || text.length() > EmailData.FOOTER_MAX_LENGTH) {
            throw new PatientPortalConfigurationException("portal clinic footer is not configured");
        }
        EmailInlineImage logo = footer.logo();
        if (logo != null && (!logo.contentId().matches("[A-Za-z0-9][A-Za-z0-9._-]*@[A-Za-z0-9][A-Za-z0-9.-]*")
                || logo.contentId().length() > 128 || logo.bytes().length == 0
                || logo.bytes().length > 100 * 1024
                || !("image/png".equals(logo.contentType()) || "image/jpeg".equals(logo.contentType())))) {
            throw new PatientPortalConfigurationException("portal clinic logo is unavailable");
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("iss", PortalStaffAssertionSigner.ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + 60);
        payload.put("nonce", nonce);
        payload.put("clinic_id", settings.clinicId());
        payload.put("kid", settings.staffAssertionKeyId());
        payload.put("footer_html", html);
        payload.put("footer_text", text);
        payload.put("revision", revision(html, text, logo));
        if (logo == null) {
            payload.putNull("logo");
        } else {
            ObjectNode image = payload.putObject("logo");
            image.put("content_id", logo.contentId());
            image.put("content_type", logo.contentType());
            image.put("bytes_base64", Base64.getEncoder().encodeToString(logo.bytes()));
            image.put("sha256", sha256(logo.bytes()));
        }
        return payload;
    }

    static String revision(String html, String text, EmailInlineImage logo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] component : new byte[][] {
                    html.getBytes(StandardCharsets.UTF_8), text.getBytes(StandardCharsets.UTF_8),
                    (logo == null ? "" : logo.contentId()).getBytes(StandardCharsets.UTF_8),
                    (logo == null ? "" : logo.contentType()).getBytes(StandardCharsets.US_ASCII),
                    logo == null ? new byte[0] : logo.bytes()}) {
                digest.update(length(component));
                digest.update(component);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException failure) {
            throw new PatientPortalConfigurationException("portal footer revision could not be prepared");
        }
    }

    private static byte[] length(byte[] component) {
        return ByteBuffer.allocate(Long.BYTES).putLong(component.length).array();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException failure) {
            throw new PatientPortalConfigurationException("portal footer logo could not be prepared");
        }
    }
}
