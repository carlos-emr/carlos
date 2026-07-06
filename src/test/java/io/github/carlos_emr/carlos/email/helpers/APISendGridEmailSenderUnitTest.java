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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link APISendGridEmailSender} focused on the request payload.
 *
 * <p>Verifies the leak-channel fix from issue #3112: the SendGrid API key must travel only in the
 * {@code Authorization: Bearer} header and must never appear in the serialized request body.</p>
 */
class APISendGridEmailSenderUnitTest extends CarlosUnitTestBase {

    private Field keySpecField;
    private Object originalKeySpec;
    private String originalProp;

    @BeforeEach
    void setUp() throws Exception {
        createAndRegisterMock(SecurityInfoManager.class);

        // Seed a fresh process-global AES key so the encrypted-api_key case can round-trip, and
        // restore prior state afterwards. Plaintext/blank/missing cases are unaffected by the key.
        keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
        EncryptionUtils.prepareSecretKeySpec();
    }

    @AfterEach
    void restoreEncryptionKey() throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        if (originalProp != null) {
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
        } else {
            props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
        keySpecField.set(null, originalKeySpec);
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
                null, emailConfig, new String[] {"patient@example.com"},
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
                null, emailConfig, new String[] {"patient@example.com"},
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
                null, emailConfig, new String[] {"patient@example.com"},
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
                null, emailConfig, new String[] {"patient@example.com"},
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
                null, emailConfig, new String[] {"patient@example.com"},
                "Subject line", "Body text", Collections.emptyList());

        assertThat(sender.getAPIKey()).isEqualTo("SG.encrypted-key");
    }
}
