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

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailConsentResult")
class EmailConsentResultUnitTest {
    @Test
    @DisplayName("should defensively copy consent last update date")
    void shouldDefensivelyCopy_whenConsentLastUpdateDateIsMutated() {
        Date sourceDate = new Date(1_000L);
        EmailConsentResult result = new EmailConsentResult("Email", EmailConsentStatus.OPT_IN, 1, sourceDate);

        sourceDate.setTime(2_000L);
        Date returnedDate = result.getConsentLastUpdateDate();
        returnedDate.setTime(3_000L);

        assertThat(result.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
    }
}
