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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
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
 * Unit tests for {@link BillingOnReviewDiagPersister}, the optional
 * {@code addToPatientDx} clinical write extracted out of
 * {@link BillingOnReviewViewModelAssembler}.
 *
 * @since 2026-04-25
 */
@DisplayName("BillingOnReviewDiagPersister")
@Tag("unit")
@Tag("billing")
class BillingOnReviewDiagPersisterUnitTest extends CarlosUnitTestBase {

    private static final String LOGGER_NAME =
            "io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnReviewDiagPersister";

    @Mock
    private DxresearchDAO dxresearchDAO;

    private BillingOnReviewDiagPersister persister;
    private MockHttpServletRequest request;
    private AutoCloseable mockitoCloseable;
    private CapturingAppender appender;
    private LoggerContext ctx;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        persister = new BillingOnReviewDiagPersister(dxresearchDAO);
        request = new MockHttpServletRequest();

        // Register a DEDICATED LoggerConfig for the persister's logger name so
        // this suite never mutates a shared/root config. Surefire is configured
        // with parallel=classes (pom.xml) — without scoping, two log-capture
        // tests running concurrently would race on the same root LoggerConfig
        // for add/remove/level state, producing flaky assertions.
        ctx = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender();
        appender.start();
        LoggerConfig dedicated = new LoggerConfig(LOGGER_NAME, Level.ALL, false);
        dedicated.addAppender(appender, Level.ALL, null);
        ctx.getConfiguration().addLogger(LOGGER_NAME, dedicated);
        ctx.updateLoggers();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
        if (appender != null) {
            ctx.getConfiguration().removeLogger(LOGGER_NAME);
            appender.stop();
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
    void shouldThrow_whenOptedInButDemoNoMissing() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("dxCode", "401");
        // demographic_no missing

        // Audit-trail integrity: opt-in clinical write with no demographic_no
        // must throw so the validation-error JSP surfaces the rejection
        // rather than the form quietly returning OK while saving nothing.
        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographic_no is missing");

        verify(dxresearchDAO, never()).save(any());
        assertThat(appender.events()).anyMatch(evt ->
                evt.getLevel() == Level.ERROR
                && evt.getMessage().getFormattedMessage().contains("demographic_no"));
    }

    @Test
    void shouldThrow_whenOptedInButBothDxFieldsEmpty() {
        request.setParameter("addToPatientDx", "yes");
        request.setParameter("demographic_no", "1");
        // both dxCode and codeMatchToPatientDx missing

        // Same loud-failure contract as the missing-demographic case above.
        assertThatThrownBy(() -> persister.persistIfRequested(request, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("no diagnostic code was supplied");

        verify(dxresearchDAO, never()).save(any());
        assertThat(appender.events()).anyMatch(evt ->
                evt.getLevel() == Level.ERROR
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

    /**
     * Locks in the Spring {@link org.springframework.stereotype.Service}
     * annotation. The production no-arg ctor of
     * {@code ViewBillingOnReview2Action} resolves this class via
     * {@code SpringUtils.getBean(BillingOnReviewDiagPersister.class)} — without
     * the {@code @Service} stereotype the bean isn't registered and the
     * constructor throws {@code NoSuchBeanDefinitionException}, which Struts
     * surfaces as a 500 on {@code billing/CA/ON/ViewBillingONReview} (issue
     * #1921). Mock-mode unit tests don't catch this because they
     * {@code registerMock(BillingOnReviewDiagPersister.class, ...)} on the
     * test SpringUtils stub.
     */
    @Test
    void shouldBeAnnotatedAsSpringServiceBean_so1921StaysFixed() {
        assertThat(BillingOnReviewDiagPersister.class)
                .as("BillingOnReviewDiagPersister must be a registered Spring bean " +
                    "for ViewBillingOnReview2Action's no-arg ctor to succeed (#1921)")
                .hasAnnotation(org.springframework.stereotype.Service.class);
    }

    /**
     * The constructor-injection ctor must be {@code public} so Struts2's
     * {@code SpringObjectFactory} (and Spring's autowire-by-constructor) can
     * instantiate the bean. A package-private ctor was the secondary cause of
     * #1921 — even with {@code @Service} present, Spring can't always
     * reflectively invoke a package-private ctor across class loaders.
     */
    @Test
    void shouldExposePublicConstructor_forSpringInstantiation() throws Exception {
        java.lang.reflect.Constructor<BillingOnReviewDiagPersister> ctor =
                BillingOnReviewDiagPersister.class.getDeclaredConstructor(DxresearchDAO.class);
        assertThat(java.lang.reflect.Modifier.isPublic(ctor.getModifiers()))
                .as("DxresearchDAO-arg ctor must be public for Spring DI (#1921)")
                .isTrue();
    }
}
