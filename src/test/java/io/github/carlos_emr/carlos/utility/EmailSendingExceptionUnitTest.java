/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EmailSendingException} email failure exception.
 *
 * @since 2026-03-31
 */
@DisplayName("EmailSendingException Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class EmailSendingExceptionUnitTest {

    @Test
    @DisplayName("should carry message")
    void shouldCarryMessage_fromTheConstructor() {
        EmailSendingException ex = new EmailSendingException("SMTP failed");
        assertThat(ex.getMessage()).isEqualTo("SMTP failed");
    }

    @Test
    @DisplayName("should be an Exception")
    void shouldBeException_forTheTypeContract() {
        assertThat(new EmailSendingException("test")).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("should report no refusal from every constructor that does not name one")
    void shouldReportNoRefusal_forConstructorsWithoutRefusal() {
        Throwable cause = new IllegalStateException("cause");
        assertThat(new EmailSendingException().getRefusal()).isEqualTo(EmailSendingException.Refusal.NONE);
        assertThat(new EmailSendingException("m").getRefusal()).isEqualTo(EmailSendingException.Refusal.NONE);
        assertThat(new EmailSendingException(cause).getRefusal()).isEqualTo(EmailSendingException.Refusal.NONE);
        assertThat(new EmailSendingException("m", cause).getRefusal()).isEqualTo(EmailSendingException.Refusal.NONE);
        assertThat(new EmailSendingException("m", cause, true).getRefusal()).isEqualTo(EmailSendingException.Refusal.NONE);
    }

    @Test
    @DisplayName("should mark a refusal as a definite failure and treat a null refusal as none")
    void shouldMarkRefusalDefinite_withNullAsNone() {
        EmailSendingException refused = new EmailSendingException("m", null, EmailSendingException.Refusal.RECIPIENT);
        assertThat(refused.isDeliveryOutcomeUncertain()).isFalse();
        assertThat(refused.getRefusal()).isEqualTo(EmailSendingException.Refusal.RECIPIENT);
        assertThat(new EmailSendingException("m", null, (EmailSendingException.Refusal) null).getRefusal())
                .isEqualTo(EmailSendingException.Refusal.NONE);
    }
}
