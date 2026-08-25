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
package io.github.carlos_emr.carlos.email.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link APISendGridEmailSender}.
 *
 * <p>Covers two independent concerns that landed on separate branches and are kept together here:
 * the credential-handling fix from issue #3112 (the SendGrid API key must travel only in the
 * {@code Authorization: Bearer} header, must never appear in the serialized request body, and is
 * decrypted only at send time), and the transport-acceptance rules on {@code develop} (only HTTP
 * 202 counts as queued, and the endpoint must be a validated HTTPS host).</p>
 */
@DisplayName("APISendGridEmailSender")
@Tag("unit")
@Tag("fast")
@Tag("security")
class APISendGridEmailSenderUnitTest extends CarlosUnitTestBase {

    // develop's constructor requires a non-null LoggedInInfo; these credential tests never read it.
    private static final LoggedInInfo LOGGED_IN_INFO = mock(LoggedInInfo.class);

    private String originalProp;

    @BeforeEach
    void setUp() throws Exception {
        createAndRegisterMock(SecurityInfoManager.class);

        // Seed a fresh process-global AES key so the encrypted-api_key case can round-trip, and
        // restore prior state afterwards. Plaintext/blank/missing cases are unaffected by the key.
        CarlosProperties props = CarlosProperties.getInstance();
        originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
        EncryptionUtils.prepareSecretKeySpec();
    }

    @AfterEach
    void restoreEncryptionKey() {
        // Restore via the public property + prepareSecretKeySpec() contract (which re-derives the
        // spec, or resets it to null when no key is configured) rather than reflecting into the
        // private SECRET_KEY_SPEC field.
        CarlosProperties props = CarlosProperties.getInstance();
        if (originalProp != null) {
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
        } else {
            props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
        EncryptionUtils.prepareSecretKeySpec();
    }

    @Test
    @Tag("read")
    @DisplayName("should not embed the API key in the SendGrid request body")
    void shouldNotEmbedApiKey_inRequestBody() throws Exception {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderFirstName("Clinic");
        emailConfig.setSenderLastName("Sender");
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"SG.super-secret-key\"}");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                LOGGED_IN_INFO, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        String payload = sender.createEmailJSON();

        // The body must carry the message but neither the "apiKey" body field nor the key value.
        assertThat(payload)
                .doesNotContain("apiKey", "SG.super-secret-key")
                .contains("patient@example.com", "Subject line");
    }

    @Test
    @Tag("read")
    @DisplayName("should return the stored key when a legacy plaintext api_key is configured")
    void shouldReturnApiKey_whenPlaintextConfigured() throws Exception {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"SG.plaintext-key\"}");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                LOGGED_IN_INFO, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        assertThat(sender.getAPIKey()).isEqualTo("SG.plaintext-key");
    }

    @Test
    @Tag("read")
    @DisplayName("should throw exception when the api_key field is absent")
    void shouldThrowException_whenApiKeyMissing() {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("{\"host\":\"smtp.example.com\"}");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                LOGGED_IN_INFO, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        assertThatExceptionOfType(EmailSendingException.class)
                .isThrownBy(sender::getAPIKey);
    }

    @Test
    @Tag("read")
    @DisplayName("should throw exception when the stored configuration JSON is blank")
    void shouldThrowException_whenConfigJsonBlank() {
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("   ");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                LOGGED_IN_INFO, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        assertThatExceptionOfType(EmailSendingException.class)
                .isThrownBy(sender::getAPIKey);
    }

    @Test
    @Tag("read")
    @DisplayName("should return the decrypted key when an encrypted api_key is configured")
    void shouldReturnDecryptedApiKey_whenEncryptedApiKeyConfigured() throws Exception {
        // The core at-rest behaviour: a stored {ENC}-wrapped api_key is decrypted only at send time.
        String encryptedApiKey = EncryptionUtils.encrypt("SG.encrypted-key");
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSenderEmail("clinic@example.com");
        emailConfig.setConfigDetailsJson("{\"api_key\":\"" + encryptedApiKey + "\"}");

        APISendGridEmailSender sender = new APISendGridEmailSender(
                LOGGED_IN_INFO, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        assertThat(sender.getAPIKey()).isEqualTo("SG.encrypted-key");
    }

    @Test
    @DisplayName("should require a validated HTTPS endpoint")
    void shouldRequireValidatedHttpsEndpoint() throws Exception {
        assertThat(APISendGridEmailSender.validateEndpoint(
                "https://203.0.113.10/v3/mail/send").uri().getScheme()).isEqualTo("https");

        assertThatThrownBy(() -> APISendGridEmailSender.validateEndpoint(
                "http://203.0.113.10/v3/mail/send"))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> APISendGridEmailSender.validateEndpoint(
                "https://127.0.0.1/v3/mail/send"))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    @DisplayName("should accept only HTTP 202 as a queued send")
    void shouldAcceptOnly202_asQueuedSend() {
        assertThatCode(() -> APISendGridEmailSender.assertAccepted(202))
                .as("202 Accepted is SendGrid's success signal")
                .doesNotThrowAnyException();
    }

    /**
     * The regression that motivated the change. Redirect handling is disabled on the client for SSRF
     * containment, so a 3xx arrives here instead of being followed — and 3xx is not {@code >= 400}, so
     * the previous check reported an email that was never queued as sent. Each of these fails against
     * a {@code >= 400} test, which is what makes this test able to detect the defect.
     */
    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {301, 302, 303, 307, 308})
    @DisplayName("should reject redirect statuses that a >= 400 check let through")
    void shouldRejectRedirectStatuses_whenRedirectsAreNotFollowed(int redirectStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(redirectStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(redirectStatus));
    }

    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {200, 201, 204})
    @DisplayName("should reject 2xx statuses that are not 202")
    void shouldRejectNon202Success_forQueuedSemantics(int successStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(successStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(successStatus));
    }

    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    @DisplayName("should still reject error statuses")
    void shouldRejectErrorStatuses_asBefore(int errorStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(errorStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(errorStatus));
    }
}
