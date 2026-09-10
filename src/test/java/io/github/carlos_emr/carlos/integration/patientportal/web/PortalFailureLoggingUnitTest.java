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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * What the portal failure log actually writes, given the log is now the only evidence of a failure.
 *
 * <p>Adding logging to this package moved a latent hazard onto the hot path: every portal failure is
 * now logged with its exception, and the response body a parse failure refers to is the portal's, so
 * it carries patient email addresses, dates of birth, and health card numbers.
 *
 * <p>The response boundary replaces parser exceptions with a body-free contract exception. This
 * test pins that decision so a future refactor cannot accidentally expose Jackson's source text.
 * The sibling timestamp case is also sanitized; see {@code PortalJsonUnitTest}.
 *
 * <p>The assertion is on the rendered stack trace, cause chain included, because the top-level
 * message was always clean — asserting on it would pass with the leak present.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal failure logging")
class PortalFailureLoggingUnitTest {

    private static final String PATIENT_EMAIL = "patient@example.com";
    private static final String HEALTH_CARD = "9876543210";

    private static final class TestAction extends PortalJsonAction {
        private static final long serialVersionUID = 1L;

        @Override
        protected String handleRequest() {
            return NONE;
        }
    }

    /** Everything a log appender would render for this event, cause chain included. */
    private String rendered(LogEvent event) {
        StringWriter writer = new StringWriter();
        writer.write(event.getMessage().getFormattedMessage());
        if (event.getThrown() != null) {
            event.getThrown().printStackTrace(new PrintWriter(writer));
        }
        return writer.toString();
    }

    private String logOf(PatientPortalException exception) throws IOException {
        try (LogCapture capture = LogCapture.forLogger(PortalJsonAction.class)) {
            new TestAction().portalFailure(new MockHttpServletResponse(), exception);
            StringBuilder all = new StringBuilder();
            for (LogEvent event : capture.events()) {
                all.append(rendered(event));
            }
            return all.toString();
        }
    }

    private Level levelOf(PatientPortalException exception) throws IOException {
        try (LogCapture capture = LogCapture.forLogger(PortalJsonAction.class)) {
            new TestAction().portalFailure(new MockHttpServletResponse(), exception);
            assertThat(capture.events()).hasSize(1);
            return capture.events().get(0).getLevel();
        }
    }

    @Test
    @DisplayName("should not log the response body a JSON parse choked on")
    void shouldOmitTheBody_whenTheResponseIsNotValidJson() throws IOException {
        PatientPortalException failure = jsonFailure();

        assertThat(logOf(failure))
                .withFailMessage("the portal response body reached the log through the cause chain")
                .doesNotContain(PATIENT_EMAIL)
                .doesNotContain(HEALTH_CARD);
    }

    @Test
    @DisplayName("should not log an arbitrary authorization exception message")
    void shouldOmitAuthorizationExceptionMessage_fromTheLog() throws IOException {
        try (LogCapture capture = LogCapture.forLogger(PortalJsonAction.class)) {
            new TestAction()
                    .forbidden(
                            new MockHttpServletResponse(),
                            new SecurityException("denied for " + PATIENT_EMAIL));

            assertThat(capture.events())
                    .isNotEmpty()
                    .extracting(event -> event.getMessage().getFormattedMessage())
                    .allSatisfy(message -> assertThat(message).doesNotContain(PATIENT_EMAIL));
        }
    }

    @Test
    @DisplayName("should warn for an expected portal conflict")
    void shouldWarn_whenThePortalRejectsABusinessTransition() throws IOException {
        assertThat(levelOf(PatientPortalException.ofStatus(409, "/x/{id}", null)))
                .isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("should log transport failures as errors")
    void shouldLogAnError_whenThePortalDidNotCompleteTheExchange() throws IOException {
        assertThat(levelOf(PatientPortalException.ofTransportFailure("/x/{id}", null)))
                .isEqualTo(Level.ERROR);
    }

    private PatientPortalException jsonFailure() {
        String body =
                String.format(
                        "{\"email\":\"%s\",\"health_card_number\":\"%s\",",
                        PATIENT_EMAIL, HEALTH_CARD);
        try {
            new ObjectMapper().readTree(body);
            throw new IllegalStateException("that should not have parsed");
        } catch (JsonProcessingException parseFailure) {
            return PatientPortalException.ofMalformedResponse(
                    200, "/internal/carlos/patients/{id}/portal-account", parseFailure);
        }
    }
}
