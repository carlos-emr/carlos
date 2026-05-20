/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailLogDaoImpl Unit Tests")
class EmailLogDaoImplUnitTest {

    private final EmailLogDaoImpl dao = new EmailLogDaoImpl();

    @Test
    @DisplayName("should reject non-numeric demographicNo before querying")
    void shouldRejectNonNumericDemographicNo() {
        Date now = new Date();

        assertThatThrownBy(() -> dao.getEmailStatusByDateDemographicSenderStatus(
                now, now, "not-a-number", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demographicNo must be numeric");
    }

    @Test
    @DisplayName("should reject invalid emailStatus before querying")
    void shouldRejectInvalidEmailStatus() {
        Date now = new Date();

        assertThatThrownBy(() -> dao.getEmailStatusByDateDemographicSenderStatus(
                now, now, null, null, "BOUNCED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("emailStatus must be one of")
                .hasMessageContaining("SUCCESS")
                .hasMessageContaining("FAILED")
                .hasMessageContaining("RESOLVED");
    }
}
