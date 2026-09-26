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
package io.github.carlos_emr.carlos.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the signed-out-elsewhere marker used to explain a revoked session to its browser (issue #3980).
 */
@Tag("unit")
@Tag("security")
@DisplayName("RevokedUserSessions")
class RevokedUserSessionsUnitTest {

    @Test
    @DisplayName("should report a marked session until the marker is consumed once")
    void shouldReportMarkedSession_untilConsumedOnce() {
        String sessionId = "revoked-" + UUID.randomUUID();

        RevokedUserSessions.mark(sessionId);

        assertThat(RevokedUserSessions.isRevoked(sessionId)).isTrue();
        assertThat(RevokedUserSessions.isRevoked(sessionId)).as("peeking does not consume").isTrue();
        assertThat(RevokedUserSessions.consume(sessionId)).isTrue();
        assertThat(RevokedUserSessions.consume(sessionId)).as("the notice is shown once").isFalse();
        assertThat(RevokedUserSessions.isRevoked(sessionId)).isFalse();
    }

    @Test
    @DisplayName("should ignore unknown, null and blank session ids")
    void shouldIgnoreUnknownNullAndBlank_forSessionIds() {
        RevokedUserSessions.mark(null);
        RevokedUserSessions.mark(" ");

        assertThat(RevokedUserSessions.isRevoked("never-" + UUID.randomUUID())).isFalse();
        assertThat(RevokedUserSessions.isRevoked(null)).isFalse();
        assertThat(RevokedUserSessions.consume(null)).isFalse();
        assertThat(RevokedUserSessions.consume("")).isFalse();
    }

    @Test
    @DisplayName("should forget every marker when cleared")
    void shouldForgetMarkers_whenCleared() {
        String sessionId = "revoked-" + UUID.randomUUID();
        RevokedUserSessions.mark(sessionId);

        RevokedUserSessions.clearForTesting();

        assertThat(RevokedUserSessions.isRevoked(sessionId)).isFalse();
    }
}
