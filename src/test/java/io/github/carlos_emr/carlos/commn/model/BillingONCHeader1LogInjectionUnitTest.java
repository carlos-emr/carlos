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
package io.github.carlos_emr.carlos.commn.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Log-injection regression tests for {@link BillingONCHeader1}'s status setters.
 *
 * <p>Both {@code setStatus} (legacy permissive path) and {@code setStatusStrict}
 * log the request-derived status token when it is outside the known set. The token
 * is routed through {@code LogSafe.sanitize()} so CR/LF and other control characters
 * cannot forge additional log lines (CWE-117 / SonarQube javasecurity:S5145). These
 * tests pin that neutralization at the exact logger the entity uses.</p>
 *
 * @since 2026-06-18
 */
@DisplayName("BillingONCHeader1 status logging (log injection)")
@Tag("unit")
@Tag("billing")
class BillingONCHeader1LogInjectionUnitTest {

    private static final String FORGED_STATUS =
            "Z\r\nWARN forged-line: bypassed-rejection";

    @Test
    @DisplayName("setStatus neutralizes CR/LF in the logged unknown status value")
    void shouldSanitizeControlCharacters_whenSetStatusLogsUnknownValue() {
        try (LogCapture capture = LogCapture.forLogger(BillingONCHeader1.class)) {
            new BillingONCHeader1().setStatus(FORGED_STATUS);

            String logged = warningMentioningForgedToken(capture.messages());
            assertThat(logged).doesNotContain("\n").doesNotContain("\r");
            // The token is still logged for audit, just escaped rather than dropped.
            assertThat(logged).contains("forged-line");
        }
    }

    @Test
    @DisplayName("setStatusStrict neutralizes CR/LF in the rejected status value before throwing")
    void shouldSanitizeControlCharacters_whenSetStatusStrictRejectsUnknownValue() {
        try (LogCapture capture = LogCapture.forLogger(BillingONCHeader1.class)) {
            assertThatThrownBy(() -> new BillingONCHeader1().setStatusStrict(FORGED_STATUS))
                    .isInstanceOf(IllegalArgumentException.class);

            String logged = warningMentioningForgedToken(capture.messages());
            assertThat(logged).doesNotContain("\n").doesNotContain("\r");
            assertThat(logged).contains("forged-line");
        }
    }

    private static String warningMentioningForgedToken(List<String> messages) {
        return messages.stream()
                .filter(m -> m.contains("forged-line"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected a warning that logged the rejected status token, got: " + messages));
    }
}
