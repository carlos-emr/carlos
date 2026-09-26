/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the sender entry points without contacting any mail provider. */
@Tag("unit")
@Tag("email")
class EmailTransportConfigurationUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "[]", "true", "{}", "{\"host\":null}",
            "{\"host\":\"localhost\",\"port\":\"invalid\"}",
            "{\"host\":\"localhost\",\"port\":65536}",
            "{\"password\":\"private-sentinel\",invalid}"})
    void shouldReportCheckedPrivateErrors_withInvalidStoredConfiguration(String json) {
        createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(org.springframework.mail.javamail.JavaMailSender.class);
        EmailConfig config = new EmailConfig();
        config.setConfigDetailsJson(json);
        for (SMTPEmailSender sender : List.of(
                new SMTPEmailSender(null, config, new String[0], "", "", List.of()),
                new LocalSMTPEmailSender(null, config, new String[0], "", "", List.of()))) {
            assertThatThrownBy(() -> sender.createTLSMailSender(config))
                    .isInstanceOf(EmailSendingException.class)
                    .hasMessageNotContaining("private-sentinel")
                    .hasNoCause();
        }
    }

    @Test
    void shouldKeepValidTransportSettings_withStringOrNumericPorts() throws Exception {
        createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(org.springframework.mail.javamail.JavaMailSender.class);
        EmailConfig config = new EmailConfig();
        config.setConfigDetailsJson("{\"host\":\"localhost\",\"port\":25}");
        var local = new LocalSMTPEmailSender(null, config, new String[0], "", "", List.of());
        assertThat(((JavaMailSenderImpl) local.createTLSMailSender(config)).getPort()).isEqualTo(25);
        config.setConfigDetailsJson("{\"host\":\"smtp.example.invalid\",\"port\":\"587\","
                + "\"username\":\"operator\",\"password\":\"private-sentinel\"}");
        var smtp = new SMTPEmailSender(null, config, new String[0], "", "", List.of());
        var sender = (JavaMailSenderImpl) smtp.createTLSMailSender(config);
        assertThat(sender.getHost()).isEqualTo("smtp.example.invalid");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getPassword()).isEqualTo("private-sentinel");
    }

    @Test
    void shouldRejectAbsentApiCredentials_withSendGridConfiguration() throws Exception {
        EmailConfig config = new EmailConfig();
        config.setConfigDetailsJson("{\"api_key\":null}");
        // Parsed outside the assertion: parse and requiredText throw the same checked
        // exception, and the test is about the missing key, not the JSON.
        var settings = EmailTransportConfiguration.parse(config);
        assertThatThrownBy(() -> EmailTransportConfiguration.requiredText(settings, "api_key"))
                .isInstanceOf(EmailSendingException.class).hasMessageContaining("api_key");
    }
}
