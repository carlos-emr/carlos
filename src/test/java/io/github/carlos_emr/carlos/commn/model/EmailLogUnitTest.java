/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for email-log consent audit snapshots.
 *
 * @since 2026-07-06
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailLog")
class EmailLogUnitTest {
    @Test
    @DisplayName("should defensively copy consent last update date")
    void shouldDefensivelyCopy_whenConsentLastUpdateDateIsMutated() {
        Date sourceDate = new Date(1_000L);
        EmailLog emailLog = new EmailLog();
        emailLog.setConsentLastUpdateDate(sourceDate);

        sourceDate.setTime(2_000L);
        Date returnedDate = emailLog.getConsentLastUpdateDate();
        returnedDate.setTime(3_000L);

        assertThat(emailLog.getConsentLastUpdateDate()).isEqualTo(new Date(1_000L));
    }

    @Test
    @DisplayName("should localize every consent status in supported bundles")
    void shouldLocalizeEveryConsentStatus_forSupportedBundles() {
        List<Locale> supportedLocales = List.of(
                Locale.ENGLISH,
                Locale.FRENCH,
                Locale.forLanguageTag("es"),
                Locale.forLanguageTag("pl"),
                Locale.forLanguageTag("pt-BR"));

        for (Locale locale : supportedLocales) {
            ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);
            for (EmailLog.EmailConsentStatus status : EmailLog.EmailConsentStatus.values()) {
                assertThat(bundle.getString(status.getMessageKey())).isNotBlank();
            }
        }
    }
}
