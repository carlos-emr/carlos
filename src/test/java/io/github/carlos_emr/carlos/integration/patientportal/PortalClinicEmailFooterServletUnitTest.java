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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterSnapshot;
import io.github.carlos_emr.carlos.email.core.EmailInlineImage;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PortalClinicEmailFooterServletUnitTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROOT = "FAKE-only-footer-root-token-2026-10-09";
    private static final String NONCE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private final Supplier<ClinicEmailFooterSnapshot> footers = mock(Supplier.class);
    private final PatientPortalSettings settings = settings("clinic-a");
    private final PortalClinicEmailFooterServlet servlet =
            new PortalClinicEmailFooterServlet(() -> settings, footers);

    private static PatientPortalSettings settings(String clinic) {
        return new PatientPortalSettings("https://portal.example.test", clinic, PortalSecret.of(ROOT),
                PortalSecret.of(PortalTestKeys.PRIVATE_KEY), "fake-rfc8032", Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(5), Set.of(PortalTestKeys.UNUSED_TLS_PIN));
    }

    private String bearer() { return "Bearer " + PortalClinicEmailFooterProtocol.readToken(settings).expose(); }

    private MockHttpServletRequest request(String method, String query, String auth) {
        var request = new MockHttpServletRequest(method, "/ws/portal/email-footer");
        request.setQueryString(query);
        if (auth != null) request.addHeader("Authorization", auth);
        return request;
    }

    @Test
    void shouldMatchPythonFakeVector_usingProductionDerivationRevisionAndSigning() throws Exception {
        try (var input = getClass().getResourceAsStream("/patientportal/email-footer-contract.json")) {
            JsonNode vector = MAPPER.readTree(input);
            JsonNode payload = vector.get("payload");
            var image = payload.get("logo");
            var snapshot = new ClinicEmailFooterSnapshot(payload.get("footer_html").asText(),
                    new EmailInlineImage(image.get("content_id").asText(), image.get("content_type").asText(),
                            Base64.getDecoder().decode(image.get("bytes_base64").asText())));
            assertThat(PortalClinicEmailFooterProtocol.readToken(settings).expose())
                    .isEqualTo(vector.get("derived_read_token").asText());
            var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getUrlDecoder().decode(PortalTestKeys.PRIVATE_KEY)));
            var signer = new PortalStaffAssertionSigner(key,
                    Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC), UUID::randomUUID, MAPPER);
            assertThat(signer.signEmailFooter(settings, NONCE, snapshot)).isEqualTo(vector.get("assertion").asText());
        }
    }

    @Test
    void shouldReturnSignedCurrentSnapshot_withoutCreatingStaffSession() throws Exception {
        when(footers.get()).thenReturn(new ClinicEmailFooterSnapshot("<b>FAKE Clinic</b>", null));
        var request = request("GET", "nonce=" + NONCE, bearer());
        var response = new MockHttpServletResponse();
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        String assertion = MAPPER.readTree(response.getContentAsByteArray()).get("assertion").asText();
        String[] parts = assertion.split("\\.");
        JsonNode payload = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(payload.get("aud").asText()).isEqualTo("carlos-patient-portal-email-footer");
        assertThat(payload.get("nonce").asText()).isEqualTo(NONCE);
        assertThat(payload.get("footer_text").asText()).isEqualTo("FAKE Clinic");
        assertThat(payload.get("logo").isNull()).isTrue();
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(60);
        byte[] raw = Base64.getUrlDecoder().decode(PortalTestKeys.PUBLIC_KEY);
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        byte[] encoded = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
        var signature = Signature.getInstance("Ed25519");
        signature.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)));
        signature.update(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[1]))).isTrue();
        verify(footers, times(1)).get();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer FAKE-only-footer-root-token-2026-10-09", "Bearer 0000000000000000000000000000000000000000000000000000000000000000", "Basic fake", "bearer invalid"})
    void shouldRefuseUnauthorized_beforeReadingAnyClinicFooter(String authorization) throws Exception {
        var response = new MockHttpServletResponse();
        servlet.service(request("GET", "nonce=" + NONCE, authorization), response);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(footers);
        assertThat(response.getContentAsString()).doesNotContain(ROOT, NONCE);
    }

    @Test
    void shouldRefuseAnotherClinicDerivedToken_andDuplicateAuthorization() throws Exception {
        var response = new MockHttpServletResponse();
        servlet.service(request("GET", "nonce=" + NONCE,
                "Bearer " + PortalClinicEmailFooterProtocol.readToken(settings("clinic-b")).expose()), response);
        assertThat(response.getStatus()).isEqualTo(401);
        var repeated = request("GET", "nonce=" + NONCE, bearer());
        repeated.addHeader("Authorization", bearer());
        response = new MockHttpServletResponse();
        servlet.service(repeated, response);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(footers);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "nonce=short", "nonce=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", "nonce=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh9", "nonce=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8&nonce=other", "nonce=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8&extra=1"})
    void shouldRefuseMalformedOrNoncanonicalNonce_beforeReadingFooter(String query) throws Exception {
        var response = new MockHttpServletResponse();
        servlet.service(request("GET", query, bearer()), response);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(footers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEAD", "POST", "OPTIONS", "PUT", "DELETE"})
    void shouldAllowOnlyGet_withoutTouchingSettingsOrFooter(String method) throws Exception {
        Supplier<PatientPortalSettings> configuration = mock(Supplier.class);
        var response = new MockHttpServletResponse();
        new PortalClinicEmailFooterServlet(configuration, footers).service(request(method, null, null), response);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(configuration, footers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<b> </b>", "<div>\u200b\u2060</div>", "<p>\u00a0</p>"})
    void shouldRefuseMissingOrInvisibleClinicFooter_withoutUnsignedFallback(String html) throws Exception {
        when(footers.get()).thenReturn(new ClinicEmailFooterSnapshot(html, null));
        var response = new MockHttpServletResponse();
        servlet.service(request("GET", "nonce=" + NONCE, bearer()), response);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("assertion", html.isEmpty() ? "footer_html" : html);
    }

    @Test
    void shouldFailClosed_whenConfiguredFooterOrSigningSettingsCannotBeRead() throws Exception {
        var response = new MockHttpServletResponse();
        var unavailable = new PortalClinicEmailFooterServlet(() -> { throw new IllegalStateException("private secret detail"); }, footers);
        unavailable.service(request("GET", "nonce=" + NONCE, bearer()), response);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("private secret detail");
        verifyNoInteractions(footers);
    }

    @Test
    void shouldRefuseOversizeClinicAndMalformedLogo() throws Exception {
        for (var snapshot : java.util.List.of(new ClinicEmailFooterSnapshot("C".repeat(2001), null),
                new ClinicEmailFooterSnapshot("Clinic", new EmailInlineImage("bad\r\nID", "image/png", new byte[]{1})),
                new ClinicEmailFooterSnapshot("Clinic", new EmailInlineImage("valid@carlos", "image/png", new byte[102401])),
                new ClinicEmailFooterSnapshot("Clinic", new EmailInlineImage("valid@carlos", "image/svg+xml", new byte[]{1})))) {
            when(footers.get()).thenReturn(snapshot);
            var response = new MockHttpServletResponse();
            servlet.service(request("GET", "nonce=" + NONCE, bearer()), response);
            assertThat(response.getStatus()).isEqualTo(503);
        }
    }
}
