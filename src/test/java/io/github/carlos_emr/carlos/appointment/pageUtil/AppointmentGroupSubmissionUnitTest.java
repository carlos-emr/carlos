/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;

/** The Group Appointment page's side of its submission token: what it reports, and to which claim. */
@Tag("unit")
@Tag("appointment")
@DisplayName("AppointmentGroupSubmission")
class AppointmentGroupSubmissionUnitTest {

    private final MockHttpSession session = new MockHttpSession();

    private MockHttpServletRequest post(String operation) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/appointment/appointmentgrouprecords");
        request.setSession(session);
        if (operation != null) request.setParameter("groupappt", operation);
        return request;
    }

    private FormSubmissionGuard.Claim claim(String token) {
        return FormSubmissionGuard.attempt(session, token, FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null, 0).claim();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Add Group Appointment", "Group Update", "Group Cancel", "Group Delete"})
    void shouldNameTheGroupAction_forEachButtonThatWrites(String operation) {
        assertThat(AppointmentGroupSubmission.operation(post(operation))).isEqualTo(operation);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "group update", "Group Copy"})
    void shouldNameNoGroupAction_forAValueThatWritesNothing(String operation) {
        assertThat(AppointmentGroupSubmission.operation(post(operation))).isNull();
    }

    @Test
    void shouldNameNoGroupAction_whenThePageIsOnlyRendered() {
        assertThat(AppointmentGroupSubmission.operation(post(null))).isNull();
    }

    @Test
    void shouldIgnoreTheWriteReports_whenTheRequestCarriesNoClaim() {
        // A post without a token runs the page through the gate's success result, with no claim.
        MockHttpServletRequest request = post("Group Update");

        assertThatCode(() -> {
            AppointmentGroupSubmission.storageStarted(request);
            AppointmentGroupSubmission.saved(request);
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldRecordTheGroupActionAsTheSavedResult_whenThePageReportsItsSave() {
        String token = FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/appointment/appointmentgrouprecords") {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest forwarded, ServletResponse response) {
                        AppointmentGroupSubmission.storageStarted((MockHttpServletRequest) forwarded);
                        AppointmentGroupSubmission.saved((MockHttpServletRequest) forwarded);
                    }

                    @Override
                    public void include(ServletRequest included, ServletResponse response) {
                        throw new AssertionError("not included");
                    }
                };
            }
        };
        request.setSession(session);
        request.setParameter("groupappt", "Group Cancel");

        try (FormSubmissionGuard.Claim claim = claim(token)) {
            assertThatCode(() -> AppointmentGroupSubmission.render(request, new MockHttpServletResponse(), claim))
                    .doesNotThrowAnyException();
        }

        FormSubmissionGuard.Attempt repeat = FormSubmissionGuard.attempt(session, token,
                FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null, 0);
        assertThat(repeat.verdict()).isEqualTo(FormSubmissionGuard.Verdict.ALREADY_SAVED);
        assertThat(repeat.result()).isEqualTo("Group Cancel");
    }

    @Test
    void shouldForwardToThePageAndDropTheClaimAfterwards_whenThePageFails() {
        String token = FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null);
        AtomicReference<String> forwardedTo = new AtomicReference<>();
        AtomicReference<Object> claimDuringPage = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/appointment/appointmentgrouprecords") {
            @Override
            public RequestDispatcher getRequestDispatcher(String path) {
                forwardedTo.set(path);
                return new RequestDispatcher() {
                    @Override
                    public void forward(ServletRequest forwarded, ServletResponse response) throws ServletException {
                        claimDuringPage.set(forwarded.getAttribute(AppointmentGroupSubmission.CLAIM_ATTRIBUTE));
                        throw new ServletException("page failed");
                    }

                    @Override
                    public void include(ServletRequest included, ServletResponse response) {
                        throw new AssertionError("not included");
                    }
                };
            }
        };
        request.setSession(session);

        try (FormSubmissionGuard.Claim claim = claim(token)) {
            assertThatThrownBy(() -> AppointmentGroupSubmission.render(request, new MockHttpServletResponse(), claim))
                    .isInstanceOf(ServletException.class);
            assertThat(claimDuringPage.get()).isSameAs(claim);
        }

        assertThat(forwardedTo.get()).isEqualTo(AppointmentGroupSubmission.VIEW);
        assertThat(request.getAttribute(AppointmentGroupSubmission.CLAIM_ATTRIBUTE)).isNull();
        // The page wrote nothing before it failed, so the form may be submitted again.
        assertThat(FormSubmissionGuard.attempt(session, token, FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null, 0)
                .verdict()).isEqualTo(FormSubmissionGuard.Verdict.PROCEED);
    }
}
