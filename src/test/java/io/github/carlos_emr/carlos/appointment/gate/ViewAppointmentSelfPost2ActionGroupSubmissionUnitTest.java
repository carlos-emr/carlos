/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentGroupSubmission;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The Group Appointment page saves once per rendering ({@link FormSubmissionGuard}): the gate claims
 * the page's token for each group action that writes, before the page validates or writes anything,
 * and answers a repeat of a rendering that already saved with that save's own result page.
 *
 * <p>The page itself is simulated by the request dispatcher: it reports its writes through
 * {@link AppointmentGroupSubmission} as {@code appointmentgrouprecords.jsp} does.</p>
 */
@Tag("unit")
@Tag("gate")
@Tag("appointment")
@DisplayName("ViewAppointmentSelfPost2Action: Group Appointment page submission token")
class ViewAppointmentSelfPost2ActionGroupSubmissionUnitTest extends CarlosUnitTestBase {

    private static final String GROUP_URI = "/carlos/appointment/appointmentgrouprecords";
    private static final String MESSAGE_KEY = "formSubmissionMessageKey";

    private final MockHttpSession session = new MockHttpSession();
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final List<String> pageRuns = Collections.synchronizedList(new ArrayList<>());
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private ViewAppointmentSelfPost2Action action;
    private Page page;

    /** The page's behaviour for the next submission it is run for. */
    private Consumer<HttpServletRequest> pageBehaviour = AppointmentGroupSubmissionPage::saves;

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        servletActionContext = mockStatic(ServletActionContext.class);
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
        action = new ViewAppointmentSelfPost2Action();
    }

    @AfterEach
    void tearDown() {
        loggedInInfo.close();
        servletActionContext.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** What the page does with its claim, as {@code appointmentgrouprecords.jsp} reports it. */
    private static final class AppointmentGroupSubmissionPage {
        /** Writes, and records them as saved once they are durable. */
        static void saves(HttpServletRequest request) {
            AppointmentGroupSubmission.storageStarted(request);
            AppointmentGroupSubmission.saved(request);
        }

        /** Refuses a form another user's change overtook: HTTP 409, nothing written. */
        static void refusesAStaleForm(HttpServletRequest request) {
            // AppointmentEditConflict.refuse() then a forward to the conflict page; no write hook is called.
        }
    }

    /** A request whose dispatcher runs {@link #pageBehaviour} as the page would. */
    private final class Page extends MockHttpServletRequest {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        Page(String method, String uri) {
            setMethod(method);
            setRequestURI(uri);
            setSession(session);
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return new RequestDispatcher() {
                @Override
                public void forward(ServletRequest request, ServletResponse response) {
                    pageRuns.add(path);
                    pageBehaviour.accept((HttpServletRequest) request);
                }

                @Override
                public void include(ServletRequest request, ServletResponse response) {
                    throw new AssertionError("the group page is forwarded to, never included");
                }
            };
        }
    }

    private Page post(String uri, String operation, String token) {
        page = new Page("POST", uri);
        if (operation != null) page.setParameter("groupappt", operation);
        if (token != null) page.setParameter(FormSubmissionGuard.PARAMETER, token);
        page.setParameter("appointment_no", "61");
        return page;
    }

    private String execute(Page request) throws Exception {
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(request.response);
        return action.execute();
    }

    private String issue() {
        return FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null);
    }

    private FormSubmissionGuard.Verdict verdictOf(String token) {
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(session, token,
                FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null, 0);
        if (attempt.claim() != null) attempt.claim().close();
        return attempt.verdict();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Add Group Appointment", "Group Update", "Group Cancel", "Group Delete"})
    void shouldRunThePageWithItsClaim_whenAGroupActionFirstPresentsTheToken(String operation) throws Exception {
        String token = issue();

        String result = execute(post(GROUP_URI, operation, token));

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(pageRuns).containsExactly(AppointmentGroupSubmission.VIEW);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        // The page reported its save through the claim, so the token is spent with this action as its result.
        FormSubmissionGuard.Attempt repeat = FormSubmissionGuard.attempt(session, token,
                FormSubmissionGuard.Scope.APPOINTMENT_GROUP, null, 0);
        assertThat(repeat.verdict()).isEqualTo(FormSubmissionGuard.Verdict.ALREADY_SAVED);
        assertThat(repeat.result()).isEqualTo(operation);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Add Group Appointment", "Group Update", "Group Cancel", "Group Delete"})
    void shouldAnswerWithTheFirstSavesResultAndRunNothing_whenASavedRenderingIsSubmittedAgain(String operation)
            throws Exception {
        String token = issue();
        execute(post(GROUP_URI, operation, token));

        String repeat = execute(post(GROUP_URI, operation, token));

        assertThat(repeat).isEqualTo(AppointmentGroupSubmission.SAVED_RESULT);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(pageRuns).as("the page, and so every write, ran for the first submission only").hasSize(1);
    }

    @Test
    void shouldAnswerWithTheSavedResult_whenTheRepeatArrivesWhileTheFirstIsStillSaving() throws Exception {
        String token = issue();
        CountDownLatch firstIsWriting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        pageBehaviour = request -> {
            AppointmentGroupSubmission.storageStarted(request);
            firstIsWriting.countDown();
            try {
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            AppointmentGroupSubmission.saved(request);
        };
        Page first = post(GROUP_URI, "Group Update", token);
        Page second = post(GROUP_URI, "Group Update", token);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = threads.submit(() -> executeOnThisThread(first));
            assertThat(firstIsWriting.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> secondResult = threads.submit(() -> executeOnThisThread(second));
            // The second click waits for the first save's outcome instead of writing or being refused.
            Thread.sleep(300);
            assertThat(secondResult.isDone()).isFalse();
            releaseFirst.countDown();

            assertThat(firstResult.get(10, TimeUnit.SECONDS)).isEqualTo(ActionSupport.NONE);
            assertThat(secondResult.get(10, TimeUnit.SECONDS)).isEqualTo(AppointmentGroupSubmission.SAVED_RESULT);
        } finally {
            releaseFirst.countDown();
            threads.shutdownNow();
        }
        assertThat(second.response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(pageRuns).hasSize(1);
    }

    /** Static mocks are per thread: a submission run off the test thread mocks them for its own thread. */
    private String executeOnThisThread(Page request) throws Exception {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class);
             MockedStatic<LoggedInInfo> info = mockStatic(LoggedInInfo.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(request.response);
            info.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
            return action.execute();
        }
    }

    @Test
    void shouldRunThePageAgain_whenTheFirstSubmissionWasRefusedAsStale() throws Exception {
        // A fresh rendering whose appointments another user changed: the page's own conflict check
        // answers 409 and writes nothing. The token stays free, so the same check answers a repeat too.
        String token = issue();
        pageBehaviour = request -> {
            AppointmentGroupSubmissionPage.refusesAStaleForm(request);
            page.response.setStatus(HttpServletResponse.SC_CONFLICT);
        };

        assertThat(execute(post(GROUP_URI, "Group Update", token))).isEqualTo(ActionSupport.NONE);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(execute(post(GROUP_URI, "Group Update", token))).isEqualTo(ActionSupport.NONE);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);

        assertThat(pageRuns).hasSize(2);
        assertThat(page.response.getHeader("Cache-Control")).isNull();
    }

    @Test
    void shouldRunThePageAgain_whenTheGroupWritesRolledBack() throws Exception {
        String token = issue();
        pageBehaviour = request -> {
            TransactionSynchronizationManager.initSynchronization();
            try {
                AppointmentGroupSubmission.storageStarted(request);
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        };
        execute(post(GROUP_URI, "Group Delete", token));
        pageBehaviour = AppointmentGroupSubmissionPage::saves;

        assertThat(execute(post(GROUP_URI, "Group Delete", token))).isEqualTo(ActionSupport.NONE);
        assertThat(pageRuns).hasSize(2);
        assertThat(verdictOf(token)).isEqualTo(FormSubmissionGuard.Verdict.ALREADY_SAVED);
    }

    @Test
    void shouldRefuseAsUnconfirmedWithoutRunningThePage_whenTheFirstSubmissionFailedAfterItStartedWriting() throws Exception {
        // Add Group Appointment books one appointment at a time: a failure after the first booking
        // leaves bookings behind, so a repeat must not book again and is asked to check the schedule.
        String token = issue();
        pageBehaviour = request -> {
            AppointmentGroupSubmission.storageStarted(request);
            throw new IllegalStateException("second booking failed");
        };
        assertThatThrownBy(() -> execute(post(GROUP_URI, "Add Group Appointment", token)))
                .isInstanceOf(IllegalStateException.class);

        String repeat = execute(post(GROUP_URI, "Add Group Appointment", token));

        assertThat(repeat).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(page.response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(page.getAttribute(MESSAGE_KEY)).isEqualTo("global.formSubmission.unconfirmed");
        assertThat(pageRuns).hasSize(1);
    }

    @Test
    void shouldRefuseAsOutOfDateWithoutRunningThePage_whenAnotherGroupActionOfASavedRenderingIsPosted() throws Exception {
        // Group Update saved; a Group Cancel pressed on the same rendering is not a repeat of it, and
        // answering it with the update's result would report a cancellation that never happened.
        String token = issue();
        execute(post(GROUP_URI, "Group Update", token));

        String result = execute(post(GROUP_URI, "Group Cancel", token));

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(page.getAttribute(MESSAGE_KEY)).isEqualTo("global.formSubmission.stale");
        assertThat(pageRuns).hasSize(1);
    }

    @Test
    void shouldRefuseWithoutRunningThePage_whenTheTokenWasIssuedForAnotherForm() throws Exception {
        // An Add Appointment token, as a page rendered before this release echoed it into the group form.
        String addAppointmentToken = FormSubmissionGuard.issue(session, FormSubmissionGuard.Scope.APPOINTMENT_ADD, null);

        String result = execute(post(GROUP_URI, "Add Group Appointment", addAppointmentToken));

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(page.getAttribute(MESSAGE_KEY)).isEqualTo("global.formSubmission.stale");
        assertThat(pageRuns).isEmpty();
    }

    @Test
    void shouldRefuseWithoutRunningThePage_whenTheTokenIsNotOneThisSessionIssued() throws Exception {
        String result = execute(post(GROUP_URI, "Group Delete", java.util.UUID.randomUUID().toString()));

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(page.getAttribute(MESSAGE_KEY)).isEqualTo("global.formSubmission.stale");
        assertThat(pageRuns).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Add Group Appointment", "Group Update", "Group Cancel", "Group Delete"})
    void shouldKeepThePreviousBehaviour_whenTheGroupActionCarriesNoToken(String operation) throws Exception {
        assertThat(execute(post(GROUP_URI, operation, null))).isEqualTo(ActionSupport.SUCCESS);
        assertThat(execute(post(GROUP_URI, operation, null))).isEqualTo(ActionSupport.SUCCESS);

        assertThat(pageRuns).as("Struts renders the page through its success result, as before").isEmpty();
        assertThat(page.response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void shouldNotClaimTheToken_whenThePageIsOnlyRendered() throws Exception {
        // The Add and Edit Appointment popups post to the page to render it: no group action yet.
        String token = issue();

        assertThat(execute(post(GROUP_URI, null, token))).isEqualTo(ActionSupport.SUCCESS);

        assertThat(pageRuns).isEmpty();
        assertThat(verdictOf(token)).isEqualTo(FormSubmissionGuard.Verdict.PROCEED);
    }

    @Test
    void shouldNotClaimTheToken_whenTheGroupValueIsNotAWritingAction() throws Exception {
        String token = issue();

        assertThat(execute(post(GROUP_URI, "", token))).isEqualTo(ActionSupport.SUCCESS);

        assertThat(pageRuns).isEmpty();
        assertThat(verdictOf(token)).isEqualTo(FormSubmissionGuard.Verdict.PROCEED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/carlos/appointment/appointmentrepeatbooking", "/carlos/appointment/appointmenteditrepeatbooking"})
    void shouldLeaveTheRepeatBookingPagesUnguarded_forTheirGroupActions(String uri) throws Exception {
        String token = issue();

        assertThat(execute(post(uri, "Group Update", token))).isEqualTo(ActionSupport.SUCCESS);

        assertThat(pageRuns).isEmpty();
        assertThat(verdictOf(token)).isEqualTo(FormSubmissionGuard.Verdict.PROCEED);
    }

    @Test
    void shouldRejectAGetBeforeClaimingTheToken_forAGroupAction() throws Exception {
        String token = issue();
        Page get = post(GROUP_URI, "Group Delete", token);
        get.setMethod("GET");

        assertThat(execute(get)).isEqualTo(ActionSupport.NONE);

        assertThat(get.response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(pageRuns).isEmpty();
        assertThat(verdictOf(token)).isEqualTo(FormSubmissionGuard.Verdict.PROCEED);
    }
}
