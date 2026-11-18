/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.ClinicalReports.ReportEvaluator;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.WebUtils;

/**
 * "Del" next to a report in the Clinical Reports list removes that report, and "Clear" empties the list. The
 * list holds the session's run reports; the page posts the key of the one to remove, so a repeated post (a
 * refresh, a double click) or a page from a second window removes nothing else.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Removing a report from the Clinical Reports list")
class RemoveClinicalReportFromHistory2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/report/RemoveClinicalReport");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ReportEvaluator first = new ReportEvaluator();
    private final ReportEvaluator second = new ReportEvaluator();
    private final ReportEvaluator third = new ReportEvaluator();

    @Test
    @DisplayName("should give each run report its own key, which does not change")
    void shouldGiveEachReportItsOwnKey_whenReportsAreRun() {
        assertThat(first.getKey()).isNotBlank().isEqualTo(first.getKey());
        assertThat(List.of(first.getKey(), second.getKey(), third.getKey())).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should remove the report with the posted key and keep the others")
    void shouldRemoveTheReport_whenItsKeyIsPosted() {
        var reports = reports(first, second, third);
        request.setParameter("key", second.getKey());

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly(first, second, third);
        assertThat(history()).containsExactly(first, third);
    }

    @Test
    @DisplayName("should remove nothing more when the same post comes again (a refresh or a double click)")
    void shouldRemoveNothingMore_whenTheSamePostRepeats() {
        var reports = reports(first, second, third);
        request.setParameter("key", first.getKey());

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly(first, second, third);
        assertThat(history()).containsExactly(second, third);
    }

    @Test
    @DisplayName("should drop the list once its last report is removed")
    void shouldDropTheList_whenItsLastReportIsRemoved() {
        reports(first);
        request.setParameter("key", first.getKey());

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    @DisplayName("should leave the list as it is for a key that is not in it, or no key")
    void shouldLeaveTheList_whenTheKeyIsNotInIt() {
        var reports = reports(first, second);
        request.setParameter("key", "FAKE-no-such-report");
        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        request.removeParameter("key");
        request.setParameter("id", "0"); // a page rendered before reports had keys
        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly(first, second);
    }

    @Test
    @DisplayName("should show the page when no report has been run in this session")
    void shouldShowThePage_whenNoReportHasBeenRun() {
        request.setParameter("key", first.getKey());

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @Test
    @DisplayName("should clear the whole list on clear=yes")
    void shouldClearTheList_whenClearIsYes() {
        reports(first, second);
        request.setParameter("clear", "yes");

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getSession().getAttribute("ClinicalReports")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 to anything but a post, and leave the list as it is")
    void shouldRefuseTheRequest_whenItIsNotAPost(String method) {
        var reports = reports(first, second);
        request.setMethod(method);
        request.setParameter("key", first.getKey());
        request.setParameter("clear", "yes");

        assertThat(remove()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(request.getSession().getAttribute("ClinicalReports")).isSameAs(reports);
        assertThat(reports).containsExactly(first, second);
    }

    @Test
    @DisplayName("should keep what is in the list that is not a run report, and remove only the posted report")
    void shouldKeepOtherEntries_whenTheListHoldsSomethingThatIsNotAReport() {
        var reports = reports(first, "FAKE-not-a-report", second);
        request.setParameter("key", first.getKey());

        assertThat(remove()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(reports).containsExactly(first, "FAKE-not-a-report", second);
        assertThat(history()).containsExactly("FAKE-not-a-report", second);
    }

    @Test
    @DisplayName("should still answer with no page and leave the list as it is when the 405 cannot be sent")
    void shouldAnswerNone_whenTheRefusalCannotBeSent() {
        var reports = reports(first, second);
        request.setMethod("GET");
        request.setParameter("key", first.getKey());
        var unsendable = new MockHttpServletResponse() {
            @Override
            public void sendError(int status) throws IOException {
                throw new IOException("FAKE connection closed");
            }
        };

        assertThat(remove(unsendable)).isEqualTo(ActionSupport.NONE);

        assertThat(unsendable.getHeader("Allow")).isEqualTo("POST");
        assertThat(reports).containsExactly(first, second);
    }

    @Test
    void shouldKeepTheNewReport_whenAppendingOverlapsRemovalOfTheLastReport() throws Exception {
        var removingLast = new CountDownLatch(1);
        var appendRequested = new CountDownLatch(1);
        var session = new MockHttpSession(request.getServletContext()) {
            @Override
            public void removeAttribute(String name) {
                if ("ClinicalReports".equals(name)) {
                    assertThat(Thread.holdsLock(WebUtils.getSessionMutex(this))).isTrue();
                    removingLast.countDown();
                    try {
                        assertThat(appendRequested.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                super.removeAttribute(name);
            }
        };
        request.setSession(session);
        request.setParameter("key", first.getKey());
        var firstSnapshot = reports(first);
        var removal = removalAction(response);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var removed = workers.submit(removal::execute);
            assertThat(removingLast.await(5, TimeUnit.SECONDS)).isTrue();
            var appended = workers.submit(() -> {
                appendRequested.countDown();
                RunClinicalReport2Action.appendToHistory(session, second);
            });
            assertThat(removed.get(5, TimeUnit.SECONDS)).isEqualTo(ActionSupport.SUCCESS);
            appended.get(5, TimeUnit.SECONDS);
        }

        assertThat(firstSnapshot).containsExactly(first);
        List<Object> afterRemoval = history();
        assertThat(afterRemoval).containsExactly(second);

        RunClinicalReport2Action.appendToHistory(session, third);

        assertThat(afterRemoval).containsExactly(second);
        assertThat(history()).containsExactly(second, third);
    }

    @SuppressWarnings("unchecked")
    private List<Object> history() {
        return (List<Object>) request.getSession().getAttribute("ClinicalReports");
    }

    private List<Object> reports(Object... runReports) {
        List<Object> reports = new ArrayList<>(List.of(runReports));
        request.getSession().setAttribute("ClinicalReports", reports);
        return reports;
    }

    private String remove() {
        return remove(response);
    }

    private String remove(MockHttpServletResponse answer) {
        return removalAction(answer).execute();
    }

    private RemoveClinicalReportFromHistory2Action removalAction(MockHttpServletResponse answer) {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(answer);
            return new RemoveClinicalReportFromHistory2Action();
        }
    }
}
