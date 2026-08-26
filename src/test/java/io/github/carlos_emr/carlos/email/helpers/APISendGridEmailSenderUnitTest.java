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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("APISendGridEmailSender")
@Tag("unit")
@Tag("fast")
@Tag("security")
class APISendGridEmailSenderUnitTest {

    @Test
    @DisplayName("should require a validated HTTPS endpoint")
    void shouldRequireValidatedHttpsEndpoint_forSendGridTransport() throws Exception {
        assertThat(APISendGridEmailSender.validateEndpoint(
                "https://203.0.113.10/v3/mail/send").uri().getScheme()).isEqualTo("https");

        assertThatThrownBy(() -> APISendGridEmailSender.validateEndpoint(
                "http://203.0.113.10/v3/mail/send"))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> APISendGridEmailSender.validateEndpoint(
                "https://127.0.0.1/v3/mail/send"))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("rejected")
                .hasMessageNotContaining("127.0.0.1");
    }

    @Test
    @DisplayName("should accept only HTTP 202 as a queued send")
    void shouldAcceptOnly202_asQueuedSend() {
        assertThatCode(() -> APISendGridEmailSender.assertAccepted(202))
                .as("202 Accepted is SendGrid's success signal")
                .doesNotThrowAnyException();
    }

    /**
     * The regression that motivated the change. Redirect handling is disabled on the client for SSRF
     * containment, so a 3xx arrives here instead of being followed — and 3xx is not {@code >= 400}, so
     * the previous check reported an email that was never queued as sent. Each of these fails against
     * a {@code >= 400} test, which is what makes this test able to detect the defect.
     */
    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {301, 302, 303, 307, 308})
    @DisplayName("should reject redirect statuses that a >= 400 check let through")
    void shouldRejectRedirectStatuses_whenRedirectsAreNotFollowed(int redirectStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(redirectStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(redirectStatus));
    }

    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {200, 201, 204})
    @DisplayName("should reject 2xx statuses that are not 202")
    void shouldRejectNon202Success_forQueuedSemantics(int successStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(successStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(successStatus));
    }

    @ParameterizedTest(name = "HTTP {0} must not count as sent")
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    @DisplayName("should still reject error statuses")
    void shouldRejectErrorStatuses_asBefore(int errorStatus) {
        assertThatThrownBy(() -> APISendGridEmailSender.assertAccepted(errorStatus))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining(String.valueOf(errorStatus));
    }
}
