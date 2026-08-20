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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
 * <p>Jackson does not quote the source by default — {@code INCLUDE_SOURCE_IN_LOCATION} has been off
 * since 2.16 — so this passes today without any code doing the work. That is precisely why it is
 * pinned: the protection is a library default rather than a decision anyone here made, and an
 * upgrade or a factory tweak could turn it back on silently. The sibling case, a timestamp parse,
 * <em>did</em> leak and is fixed at its source; see {@code PortalJsonUnitTest}.
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

    @Test
    @DisplayName("should not log the response body a JSON parse choked on")
    void shouldOmitTheBody_whenTheResponseIsNotValidJson() throws IOException {
        PatientPortalException failure = jsonFailure();

        assertThat(logOf(failure))
                .withFailMessage("the portal response body reached the log through the cause chain")
                .doesNotContain(PATIENT_EMAIL)
                .doesNotContain(HEALTH_CARD);
    }

    private PatientPortalException jsonFailure() {
        String body =
                String.format(
                        "{\"email\":\"%s\",\"health_card_number\":\"%s\",",
                        PATIENT_EMAIL, HEALTH_CARD);
        try {
            new ObjectMapper().readTree(body);
            throw new IllegalStateException("that should not have parsed");
        } catch (IOException parseFailure) {
            return PatientPortalException.ofMalformedResponse(
                    200, "/internal/carlos/patients/{id}/portal-account", parseFailure);
        }
    }
}
