/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.helpers;

import io.github.carlos_emr.carlos.utility.EmailSendingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("APISendGridEmailSender")
@Tag("unit")
@Tag("fast")
@Tag("security")
class APISendGridEmailSenderUnitTest {

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
}
