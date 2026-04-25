/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BillingONReviewDxPersister}, the optional
 * {@code addToPatientDx} clinical write extracted out of
 * {@link BillingONReviewDataAssembler}.
 *
 * @since 2026-04-25
 */
@DisplayName("BillingONReviewDxPersister")
@Tag("unit")
@Tag("billing")
class BillingONReviewDxPersisterUnitTest extends CarlosUnitTestBase {

    private static final String LOGGER_NAME =
            "io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewDxPersister";

    @Mock
    private DxresearchDAO dxresearchDAO;

    private BillingONReviewDxPersister persister;
    private MockHttpServletRequest request;
    private AutoCloseable mockitoCloseable;
    private CapturingAppender appender;
    private LoggerConfig loggerConfig;
    private LoggerContext ctx;
    private Level priorLevel;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        persister = new BillingONReviewDxPersister(dxresearchDAO);
        request = new MockHttpServletRequest();

        // Attach a tiny in-memory log4j2 appender so we can assert the WARN
        // emissions on the early-return paths — audit-trail integrity:
        // a stale form resubmit that opted in but didn't fill the form must
        // leave a trail rather than acknowledge "saved" silently.
        //
        // Note: BillingONReviewDxPersister has no class-specific Logger entry
        // in src/test/resources/log4j2.xml, so getLoggerConfig resolves to
        // the shared Root config. Capture the prior level and restore it in
        // tearDown — without that, every subsequent test in the same
        // surefire fork runs at Level.ALL, flooding output.
        ctx = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender();
        appender.start();
        loggerConfig = ctx.getConfiguration().getLoggerConfig(LOGGER_NAME);
        priorLevel = loggerConfig.getLevel();
        loggerConfig.addAppender(appender, Level.ALL, null);
        loggerConfig.setLevel(Level.ALL);
        ctx.updateLoggers();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
        if (loggerConfig != null && appender != null) {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            loggerConfig.setLevel(priorLevel);
            ctx.updateLoggers();
        }
    }

    @Test
    void shouldNotPersist_whenAddToPatientDxNotRequested() {
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
    }

    @Test
    void shouldPersist_whenAddToPatientDxRequested() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        Dxresearch saved = captor.getValue();
        assertThat(saved.getDemographicNo()).isEqualTo(1);
        assertThat(saved.getDxresearchCode()).isEqualTo("401");
        assertThat(saved.getCodingSystem()).isEqualTo("icd9");
        assertThat(saved.getProviderNo()).isEqualTo("999998");
    }

    @Test
    void shouldPreferCodeMatchToPatientDx_whenBothFieldsProvided() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("codeMatchToPatientDx", "401.1");
        request.setParameter("demographic_no", "1");

        persister.persistIfRequested(request, "999998");

        ArgumentCaptor<Dxresearch> captor = ArgumentCaptor.forClass(Dxresearch.class);
        verify(dxresearchDAO, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDxresearchCode()).isEqualTo("401.1");
    }

    @Test
    void shouldNotPersist_whenDemoNoMissing() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        // demographic_no missing

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
        // Audit-trail integrity: opt-in clinical write that early-returns
        // must leave a WARN-level breadcrumb rather than acknowledging
        // "saved" silently.
        assertThat(appender.events()).anyMatch(evt ->
                evt.getLevel() == Level.WARN
                && evt.getMessage().getFormattedMessage().contains("demographic_no"));
    }

    @Test
    void shouldNotPersist_whenBothDxFieldsEmpty() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("demographic_no", "1");
        // both dxCode and codeMatchToPatientDx missing

        persister.persistIfRequested(request, "999998");

        verify(dxresearchDAO, never()).save(any());
        // Audit-trail integrity: opt-in clinical write that early-returns
        // must leave a WARN-level breadcrumb rather than acknowledging
        // "saved" silently.
        assertThat(appender.events()).anyMatch(evt ->
                evt.getLevel() == Level.WARN
                && evt.getMessage().getFormattedMessage().contains("dx code"));
    }

    /**
     * Audit-trail integrity: silently dropping the write on a non-numeric
     * demographic_no would leave the provider believing the dx was added
     * when it wasn't. Throw to surface the bug.
     */
    @Test
    void shouldThrow_whenDemoNoIsNonNumeric() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "abc");

        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("non-numeric demographic_no");

        verify(dxresearchDAO, never()).save(any());
    }

    /**
     * A duplicate (demoNo, dxCode, status='A') row violates the unique
     * constraint via {@link org.springframework.dao.DataIntegrityViolationException}.
     * The persister must surface that as a {@link BillingValidationException}
     * so the user gets the friendly "already in registry" page rather than
     * the generic CARLOS Error 500.
     */
    @Test
    void shouldTranslateDataIntegrityViolation_intoBillingValidationException() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");
        doThrow(new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key (1, 401, A)"))
                .when(dxresearchDAO).save(any());

        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("Could not save dx")
                .hasMessageContaining("401")
                .hasMessageContaining("already");
    }

    /**
     * A Hibernate session conflict ({@link org.hibernate.NonUniqueObjectException})
     * during save must produce a friendly "reload the chart" message — the
     * user can't recover without reloading session-bound state.
     */
    @Test
    void shouldTranslateNonUniqueObjectException_intoBillingValidationException() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");
        doThrow(new org.hibernate.NonUniqueObjectException(
                "different object with same id", 1, "Dxresearch"))
                .when(dxresearchDAO).save(any());

        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("session conflict")
                .hasMessageContaining("reload the chart");
    }

    /**
     * Catch-all for transient JDBC outages, lock-wait timeouts, etc. Without
     * this catch the user would see the generic CARLOS Error 500 page and
     * have no signal that the dx was *not* added. The translation to
     * BillingValidationException routes to the actionable retry page.
     */
    @Test
    void shouldTranslateGenericRuntimeException_intoBillingValidationException() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        request.setParameter("demographic_no", "1");
        doThrow(new IllegalStateException("simulated lock-wait timeout"))
                .when(dxresearchDAO).save(any());

        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("Could not save dx")
                .hasMessageContaining("please retry");
    }

    /**
     * Minimal in-memory log4j2 appender — captures events without filtering
     * so the WARN-on-early-return assertions can run. Mirrors the shape
     * used in {@code ErrorPageLoggerUnitTest}.
     */
    private static final class CapturingAppender extends AbstractAppender {
        private final java.util.List<LogEvent> events = new java.util.ArrayList<>();

        CapturingAppender() {
            super("BillingONReviewDxPersisterUnitTestCaptureAppender", null, null, false, null);
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
