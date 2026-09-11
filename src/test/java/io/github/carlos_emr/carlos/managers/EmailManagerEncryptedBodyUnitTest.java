/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.email.core.EmailData;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that encrypted delivery does not add secrets or clues to the visible MIME body. */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("EmailManager encrypted visible body")
class EmailManagerEncryptedBodyUnitTest {

    @Test
    @DisplayName("should preserve the fixed notice when encrypting an email")
    void shouldPreserveFixedNotice_whenEncryptingEmail() throws Exception {
        EmailData emailData = new EmailData();
        emailData.setBody("SECURE_NOTICE");
        emailData.setPassword("valid-password");
        emailData.setPasswordClue("Sensitive clue");
        emailData.setEncryptedMessage("");
        emailData.setAttachments(List.of());

        new EmailManager().encryptEmail(emailData);

        assertThat(emailData.getBody()).isEqualTo("SECURE_NOTICE");
        assertThat(emailData.getBody()).doesNotContain(emailData.getPasswordClue());
    }
}
