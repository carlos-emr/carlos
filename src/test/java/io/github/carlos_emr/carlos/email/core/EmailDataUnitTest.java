/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for consent audit input validation in {@link EmailData}.
 *
 * @since 2026-08-25
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailData")
class EmailDataUnitTest {
    @Test
    @DisplayName("should reject consent override reasons that cannot be persisted in full")
    void shouldRejectConsentOverrideReason_whenLongerThanColumnLimit() {
        EmailData emailData = new EmailData();

        assertThatThrownBy(() -> emailData.setConsentOverrideReason("a".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consent override reason must not exceed 255 characters");
    }
}
