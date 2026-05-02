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
package io.github.carlos_emr.carlos.utility;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link ErrorPageLogger}.
 *
 * <p>These exercise the exception-extraction logic (explicit page-context
 * exception vs servlet-error attribute fallback vs no-op) without spinning
 * up a JSP container. Each test attaches a Log4j2 capturing appender to
 * the {@code ErrorPageLogger} category and asserts on the captured events:
 * count, level, message content, and the throwable instance reference where
 * relevant. The defensive try/catch around the log call is also verified —
 * malformed inputs (non-{@code Throwable} servlet-error attribute, null
 * request) must not propagate.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("ErrorPageLogger")
@Tag("unit")
@Tag("utility")
class ErrorPageLoggerUnitTest extends CarlosUnitTestBase {

    private static final String LOGGER_NAME =
            "io.github.carlos_emr.carlos.utility.ErrorPageLogger";

    private CapturingAppender appender;
    private LoggerContext ctx;

    @BeforeEach
    void attachAppender() {
        // Register a DEDICATED LoggerConfig scoped to LOGGER_NAME so this
        // suite never mutates a shared/root config — Surefire is configured
        // with parallel=classes (pom.xml), and concurrent tests touching the
        // same root LoggerConfig race on add/remove/level state, producing
        // flaky log-capture assertions.
        //
        // Each test class gets its own dedicated config under its own logger
        // name; tearDown removes it cleanly, leaving the parent configuration
        // untouched. The capturing appender lets us assert what
        // ErrorPageLogger actually emitted rather than just that it didn't
        // throw.
        ctx = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender();
        appender.start();
        LoggerConfig dedicated = new LoggerConfig(LOGGER_NAME, Level.ALL, false);
        dedicated.addAppender(appender, Level.ALL, null);
        ctx.getConfiguration().addLogger(LOGGER_NAME, dedicated);
        ctx.updateLoggers();
    }

    @AfterEach
    void detachAppender() {
        if (appender != null) {
            ctx.getConfiguration().removeLogger(LOGGER_NAME);
            appender.stop();
            ctx.updateLoggers();
        }
    }

    @Test
    void shouldNoOp_whenNoExceptionAvailable() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThatCode(() -> ErrorPageLogger.logIfPresent(null, req))
                .doesNotThrowAnyException();
        assertThat(appender.events()).isEmpty();
    }

    @Test
    void shouldLogAtError_whenExplicitExceptionProvidedDirectly() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        // ErrorPageLogger reads from the standard servlet-error attributes,
        // not from getRequestURI() / getMethod() — that mirrors how Tomcat
        // populates the error dispatcher's request when a JSP errorPage
        // forwards.
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/billingView");
        req.setMethod("GET");
        Throwable t = new RuntimeException("boom");

        ErrorPageLogger.logIfPresent(t, req);

        assertThat(appender.events()).hasSize(1);
        LogEvent evt = appender.events().get(0);
        assertThat(evt.getLevel()).isEqualTo(Level.ERROR);
        String msg = evt.getMessage().getFormattedMessage();
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/billingView");
        assertThat(msg).contains("method=GET");
        assertThat(evt.getThrown()).isSameAs(t);
    }

    @Test
    void shouldFallBackToServletErrorAttribute_whenExplicitExceptionIsNull() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        IllegalStateException fromContainer = new IllegalStateException("from container");
        req.setAttribute("jakarta.servlet.error.exception", fromContainer);
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/billingONReview");
        req.setAttribute("jakarta.servlet.error.status_code", 500);
        req.setMethod("POST");

        ErrorPageLogger.logIfPresent(null, req);

        assertThat(appender.events()).hasSize(1);
        LogEvent evt = appender.events().get(0);
        assertThat(evt.getLevel()).isEqualTo(Level.ERROR);
        assertThat(evt.getThrown()).isSameAs(fromContainer);
        String msg = evt.getMessage().getFormattedMessage();
        assertThat(msg).contains("status=500");
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/billingONReview");
        assertThat(msg).contains("method=POST");
    }

    @Test
    void shouldHandleNullRequest_withoutThrowing() {
        assertThatCode(() -> ErrorPageLogger.logIfPresent(new RuntimeException("x"), null))
                .doesNotThrowAnyException();
        // Single error log emits with method/uri/status all null.
        assertThat(appender.events()).hasSize(1);
        assertThat(appender.events().get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void shouldNotEmit_whenServletErrorAttributeIsNotAThrowable() {
        // A buggy filter could stash a non-Throwable under the attribute key.
        // The defensive instanceof guard in the helper means we no-op rather
        // than ClassCastException.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.exception", "this is a string, not a Throwable");

        assertThatCode(() -> ErrorPageLogger.logIfPresent(null, req))
                .doesNotThrowAnyException();
        assertThat(appender.events()).isEmpty();
    }

    /**
     * PHI hardening: billing flows pass {@code demographic_no},
     * {@code claim_no}, and {@code billing_no} in the query string. These
     * correlate to patient health information per CLAUDE.md and must NEVER
     * appear in catalina.out. Verify the query portion is stripped before
     * the URI is logged.
     */
    @Test
    void shouldStripQueryStringFromRequestUri_beforeLogging() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/BillingONCorrection?billing_no=12345&claim_no=ABC123&demographic_no=42");
        Throwable t = new RuntimeException("boom");

        ErrorPageLogger.logIfPresent(t, req);

        assertThat(appender.events()).hasSize(1);
        String msg = appender.events().get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/BillingONCorrection");
        // PHI-correlated query parameters must not leak into logs.
        assertThat(msg).doesNotContain("billing_no");
        assertThat(msg).doesNotContain("12345");
        assertThat(msg).doesNotContain("claim_no");
        assertThat(msg).doesNotContain("ABC123");
        assertThat(msg).doesNotContain("demographic_no");
        assertThat(msg).doesNotContain("?");
    }

    /**
     * Path-parameter hardening: Tomcat appends {@code ;jsessionid=...} (and
     * occasionally {@code ;path=...} or other matrix params) to the
     * request_uri attribute when cookies are disabled or rewriting is in
     * effect. Session IDs in catalina.out give an operator with log access
     * a session-hijack primitive, so they MUST be stripped before logging.
     * Anything between the path and the {@code ;} stays; the matrix-param
     * tail is dropped.
     */
    @Test
    void shouldStripJsessionidPathParam_beforeLogging() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/billingView;jsessionid=TEST_SESSION_ID_1A2B3C;path=/carlos");
        Throwable t = new RuntimeException("boom");

        ErrorPageLogger.logIfPresent(t, req);

        String msg = appender.events().get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/billingView");
        assertThat(msg).doesNotContain("jsessionid");
        assertThat(msg).doesNotContain("TEST_SESSION_ID_1A2B3C");
        assertThat(msg).doesNotContain(";");
    }

    /**
     * Path parameters and query string can co-occur (Tomcat puts the
     * matrix-param suffix BEFORE the query). Both must be stripped.
     */
    @Test
    void shouldStripBothJsessionidAndQueryString_whenBothPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/BillingONCorrection;jsessionid=TEST_SESSION_ID_2N4P?billing_no=99");
        Throwable t = new RuntimeException("boom");

        ErrorPageLogger.logIfPresent(t, req);

        String msg = appender.events().get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/BillingONCorrection");
        assertThat(msg).doesNotContain("jsessionid");
        assertThat(msg).doesNotContain("TEST_SESSION_ID_2N4P");
        assertThat(msg).doesNotContain("billing_no");
        assertThat(msg).doesNotContain("?");
    }

    /**
     * Path-only URIs (no query string) must pass through unchanged — the
     * strip logic shouldn't truncate a legitimately query-less URI.
     */
    @Test
    void shouldPreservePathOnly_whenNoQueryString() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/billingView");
        Throwable t = new RuntimeException("boom");

        ErrorPageLogger.logIfPresent(t, req);

        String msg = appender.events().get(0).getMessage().getFormattedMessage();
        assertThat(msg).contains("uri=/carlos/billing/CA/ON/billingView");
    }

    @Test
    void shouldNotPrintSuppressedExceptionMessageToSystemErr_whenFallbackLoggingFails() {
        MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public Object getAttribute(String name) {
                throw new RuntimeException("demographic_no=42 claim_no=ABC123");
            }
        };
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            ErrorPageLogger.logIfPresent(null, req);
        } finally {
            System.setErr(originalErr);
        }

        String output = err.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("java.lang.RuntimeException");
        assertThat(output).doesNotContain("demographic_no");
        assertThat(output).doesNotContain("claim_no");
        assertThat(output).doesNotContain("ABC123");
    }

    /**
     * Minimal in-memory log4j2 appender. Captures events without filtering
     * so the test can assert any level / any field. Not a full substitute
     * for log4j-test's ListAppender but sufficient for the 5 cases here.
     */
    private static final class CapturingAppender extends AbstractAppender {
        private final java.util.List<LogEvent> events = new java.util.ArrayList<>();

        CapturingAppender() {
            super("ErrorPageLoggerUnitTestCaptureAppender", null, null, false, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> events() {
            return events;
        }
    }
}
