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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;
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
    @DisplayName("should log an error when the portal rejects CARLOS's own credentials")
    void shouldLogAnError_whenThePortalRejectsCarlosItself() throws IOException {
        // No detail, or the bare "not found" the portal's authentication dependency sends.
        assertThat(levelOf(PatientPortalException.ofStatus(404, "/x/{id}", null))).isEqualTo(Level.ERROR);
        assertThat(levelOf(PatientPortalException.ofStatus(404, "/x/{id}", "not found"))).isEqualTo(Level.ERROR);
        assertThat(logOf(PatientPortalException.ofStatus(404, "/x/{id}", null)))
                .contains("rejected CARLOS itself");
    }

    @Test
    @DisplayName("should only warn when a record is genuinely missing")
    void shouldWarn_whenTheRecordIsGenuinelyMissing() throws IOException {
        assertThat(levelOf(PatientPortalException.ofStatus(404, "/x/{id}", "invite not found")))
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

    private static final class MisconfiguredAction extends PortalJsonAction {
        private static final long serialVersionUID = 1L;
        private final transient Supplier<RuntimeException> failure;

        MisconfiguredAction(Supplier<RuntimeException> failure) {
            this.failure = failure;
        }

        @Override
        protected String handleRequest() {
            throw failure.get();
        }
    }

    private String configurationLogOf(String message) throws IOException {
        return configurationLogOf(() -> new PatientPortalConfigurationException(message));
    }

    private String configurationLogOf(Supplier<RuntimeException> failure) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                LogCapture capture = LogCapture.forLogger(PortalJsonAction.class)) {
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new MisconfiguredAction(failure).execute();
            assertThat(response.getStatus()).isEqualTo(503);
            StringBuilder all = new StringBuilder();
            for (LogEvent event : capture.events()) {
                all.append(rendered(event));
            }
            return all.toString();
        }
    }

    @Test
    @DisplayName("should name the master switch in the log when its value is mistyped")
    void shouldNameTheSwitch_whenItsValueIsMistyped() throws IOException {
        assertThat(configurationLogOf(PatientPortalSettings.ENABLED_VALUE_MESSAGE))
                .contains("patient_portal.enabled must be true or false");
    }

    /** How it arrives in production: Spring wraps the settings failure in bean-creation errors. */
    @Test
    @DisplayName("should name the master switch when Spring wraps the error")
    void shouldNameTheSwitch_whenSpringWrapsTheError() throws IOException {
        Supplier<RuntimeException> wrapped =
                () -> new BeanCreationException(
                        "patientPortalService",
                        "dependency failed",
                        new BeanCreationException(
                                "patientPortalSettings",
                                "creation failed",
                                new BeanInstantiationException(
                                        PatientPortalSettings.class,
                                        "factory method threw",
                                        new PatientPortalConfigurationException(
                                                PatientPortalSettings.ENABLED_VALUE_MESSAGE))));

        assertThat(configurationLogOf(wrapped))
                .contains("patient_portal.enabled must be true or false");
    }

    @Test
    @DisplayName("should stop walking a cause chain that loops back on itself")
    void shouldLogGenerically_whenTheCauseChainLoops() {
        // Thrown directly, as a configuration error reaches the action outside Spring. Wrapped in
        // BeanCreationException it would never get here: Spring's own contains() check, which runs
        // first, does not stop on a cycle either, but Spring never builds one.
        Supplier<RuntimeException> looping =
                () -> {
                    RuntimeException first = new RuntimeException("first");
                    RuntimeException second = new RuntimeException("second");
                    PatientPortalConfigurationException top =
                            new PatientPortalConfigurationException("another setting is invalid");
                    top.initCause(first);
                    first.initCause(second);
                    second.initCause(first);
                    return top;
                };

        String log = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> configurationLogOf(looping));
        assertThat(log).contains("check deployment settings");
    }

    @Test
    @DisplayName("should keep other configuration messages out of the log")
    void shouldOmitOtherConfigurationMessages_fromTheLog() throws IOException {
        assertThat(configurationLogOf("secret-token-value is not valid"))
                .contains("check deployment settings")
                .doesNotContain("secret-token-value");
    }
}
