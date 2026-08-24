/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("SMTPEmailSender")
class SMTPEmailSenderUnitTest {

    @Test
    @DisplayName("should apply bounded connection, read, and write timeouts")
    void shouldApplyBoundedSmtpTimeouts() {
        Properties properties = new Properties();

        SMTPEmailSender.applySmtpTimeouts(properties);

        assertThat(properties)
                .containsEntry("mail.smtp.connectiontimeout", "30000")
                .containsEntry("mail.smtp.timeout", "60000")
                .containsEntry("mail.smtp.writetimeout", "60000");
    }
}
