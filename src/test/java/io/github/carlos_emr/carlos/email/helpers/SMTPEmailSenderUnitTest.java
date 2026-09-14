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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("SMTPEmailSender")
@Tag("unit")
@Tag("fast")
@Tag("security")
class SMTPEmailSenderUnitTest extends CarlosUnitTestBase {

    private static final LoggedInInfo LOGGED_IN_INFO = mock(LoggedInInfo.class);

    private String originalKey;

    @BeforeEach
    void setUp() throws Exception {
        createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(JavaMailSender.class);
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @Tag("read")
    @DisplayName("should configure authenticated SMTP with a decrypted stored password")
    void shouldConfigureSmtp_whenPasswordEncryptedAtRest() throws Exception {
        String encryptedPassword = EncryptionUtils.encrypt("smtp-secret");
        EmailConfig config = config("{\"host\":\"smtp.example.com\",\"port\":\"587\","
                + "\"username\":\"mailer\",\"password\":\"" + encryptedPassword + "\"}");
        SMTPEmailSender sender = sender(config);

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) sender.createTLSMailSender(config);

        assertThat(mailSender.getHost()).isEqualTo("smtp.example.com");
        assertThat(mailSender.getPort()).isEqualTo(587);
        assertThat(mailSender.getUsername()).isEqualTo("mailer");
        assertThat(mailSender.getPassword()).isEqualTo("smtp-secret");
        assertThat(mailSender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    @Tag("read")
    @DisplayName("should convert malformed SMTP configuration into a sanitized send exception")
    void shouldThrowSanitizedException_whenSmtpConfigMalformed() {
        EmailConfig config = config("{\"password\":\"do-not-log\"");

        assertThatThrownBy(() -> sender(config).createTLSMailSender(config))
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Invalid credentials configured for clinic@example.com")
                .hasMessageNotContaining("do-not-log");
    }

    @Test
    @Tag("read")
    @DisplayName("should not load a password for the unauthenticated local provider")
    void shouldNotLoadPassword_whenProviderIsLocal() throws Exception {
        String encryptedPassword = EncryptionUtils.encrypt("unused-local-secret");
        EmailConfig config = config("{\"host\":\"localhost\",\"port\":\"25\","
                + "\"password\":\"" + encryptedPassword + "\"}");
        LocalSMTPEmailSender sender = new LocalSMTPEmailSender(
                LOGGED_IN_INFO, config, new String[] {"patient@example.com"},
                "subject", "body", Collections.emptyList());

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) sender.createTLSMailSender(config);

        assertThat(mailSender.getPassword()).isNull();
        assertThat(mailSender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
    }

    @Test
    @Tag("read")
    @DisplayName("should reject missing and invalid SMTP ports with a sanitized exception")
    void shouldThrowSanitizedException_whenPortInvalid() {
        EmailConfig config = config("{\"host\":\"smtp.example.com\",\"port\":\"invalid\","
                + "\"username\":\"mailer\",\"password\":\"do-not-log\"}");

        assertThatThrownBy(() -> sender(config).createTLSMailSender(config))
                .isInstanceOf(EmailSendingException.class)
                .hasMessage("Invalid credentials configured for clinic@example.com")
                .hasMessageNotContaining("do-not-log")
                .hasMessageNotContaining("invalid");
    }

    private SMTPEmailSender sender(EmailConfig config) {
        return new SMTPEmailSender(LOGGED_IN_INFO, config,
                new String[] {"patient@example.com"}, "subject", "body", Collections.emptyList());
    }

    private EmailConfig config(String details) {
        EmailConfig config = new EmailConfig();
        config.setSenderEmail("clinic@example.com");
        config.setConfigDetailsJson(details);
        return config;
    }
}
