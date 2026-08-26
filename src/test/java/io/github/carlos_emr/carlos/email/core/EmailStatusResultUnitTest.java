/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EmailLog;

/**
 * Unit tests for email-status consent snapshot presentation.
 *
 * @since 2026-07-06
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailStatusResult")
class EmailStatusResultUnitTest {
    @Test
    @DisplayName("should defensively copy consent last update date")
    void shouldDefensivelyCopyConsentLastUpdateDate_whenApplyingSnapshot() {
        Date sourceDate = new Date(1_000L);
        EmailLog emailLog = new EmailLog();
        emailLog.setConsentLastUpdateDate(sourceDate);
        EmailStatusResult result = new EmailStatusResult();

        result.applyConsentSnapshot(emailLog);
        sourceDate.setTime(2_000L);
        Date returnedDate = result.getConsentLastUpdateDate();
        returnedDate.setTime(3_000L);

        assertThat(result.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
    }
}
