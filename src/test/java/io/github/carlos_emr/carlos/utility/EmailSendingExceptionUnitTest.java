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
    void shouldCarryMessage() {
        EmailSendingException ex = new EmailSendingException("SMTP failed");
        assertThat(ex.getMessage()).isEqualTo("SMTP failed");
    }

    @Test
    @DisplayName("should be an Exception")
    void shouldBeException() {
        assertThat(new EmailSendingException("test")).isInstanceOf(Exception.class);
    }
}
