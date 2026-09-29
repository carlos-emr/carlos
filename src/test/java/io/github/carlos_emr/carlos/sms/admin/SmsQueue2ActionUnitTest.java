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
package io.github.carlos_emr.carlos.sms.admin;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.assembler.SmsQueueViewModelAssembler;
import io.github.carlos_emr.carlos.sms.service.SmsQueueViewAuditRecorder;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.function.IntPredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Administration &gt; SMS &gt; SMS queue ({@code admin/SmsQueue}): the read-only page needs {@code _admin.sms}
 * read, and without it nothing is queried.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("security")
@DisplayName("SMS queue action")
class SmsQueue2ActionUnitTest {
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final SmsQueueViewModelAssembler assembler = mock(SmsQueueViewModelAssembler.class);
    private final SmsQueueViewAuditRecorder auditRecorder = mock(SmsQueueViewAuditRecorder.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/carlos/admin/SmsQueue");
        request.setContextPath("/carlos");
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("should refuse the page with the paren-form message when the user lacks _admin.sms read")
    void shouldDenyPage_whenUserLacksAdminSmsRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verifyNoInteractions(assembler, auditRecorder);
        assertThat(request.getAttribute("smsQueue")).isNull();
    }

    @Test
    @DisplayName("should refuse the page before any privilege lookup when there is no logged-in session")
    void shouldDenyPage_whenNoSession() {
        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verifyNoInteractions(assembler, auditRecorder);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should render the queue model when the user has _admin.sms read, checked without a patient")
    void shouldRenderQueue_whenUserHasAdminSmsRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(any(), eq(true), any())).thenReturn(queueShowing(model));

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
        verify(auditRecorder).recordViewed(loggedInInfo, SmsQueueWindow.LAST_30_DAYS, Set.of());
    }

    @Test
    @DisplayName("should build the queue without demographic numbers when the user lacks _demographic read")
    void shouldHideDemographicNumbers_whenUserLacksDemographicRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(false);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(any(), eq(false), any())).thenReturn(queueShowing(model));

        action().execute();

        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
        verify(assembler, never()).assemble(any(), eq(true), any());
    }

    @Test
    @DisplayName("should audit the patients shown even when the viewer does not see demographic numbers")
    void shouldAuditShownPatients_whenNumberColumnIsOff() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(false);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(any(), eq(false), any())).thenReturn(queueShowing(model, 123, 456));

        action().execute();

        verify(auditRecorder).recordViewed(loggedInInfo, SmsQueueWindow.LAST_30_DAYS, Set.of(123, 456));
    }

    @Test
    @DisplayName("should audit the patients the assembler says were shown, before handing the page over")
    void shouldAuditShownPatients_beforeShowingThePage() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(any(), eq(true), any())).thenReturn(queueShowing(model, 123, 456));
        // The page must not have been handed over yet when the audit record is written.
        doAnswer(invocation -> {
            assertThat(request.getAttribute("smsQueue")).isNull();
            return null;
        }).when(auditRecorder).recordViewed(any(), any(), any());

        action().execute();

        InOrder order = inOrder(assembler, auditRecorder);
        order.verify(assembler).assemble(any(), eq(true), any());
        order.verify(auditRecorder).recordViewed(loggedInInfo, SmsQueueWindow.LAST_30_DAYS, Set.of(123, 456));
        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
    }

    @Test
    @DisplayName("should show nothing when the view cannot be audited")
    void shouldShowNothing_whenAuditFails() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(assembler.assemble(any(), eq(false), any()))
                .thenReturn(queueShowing(mock(SmsQueueViewModel.class), 123));
        doThrow(new IllegalStateException("audit write failed")).when(auditRecorder)
                .recordViewed(any(), any(), any());
        SmsQueue2Action action = action();

        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
        assertThat(request.getAttribute("smsQueue")).isNull();
    }

    @Test
    @DisplayName("should ask, per patient, whether this viewer may open that patient's record and read it")
    void shouldCheckEachPatient_againstPatientRecordAccess() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 456)).thenReturn(false);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 789)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 123)).thenReturn(true);
        // An entry for patient 789 alone takes this viewer's read right away.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 789)).thenReturn(false);
        when(assembler.assemble(any(), eq(true), any())).thenReturn(queueShowing(mock(SmsQueueViewModel.class)));

        action().execute();

        ArgumentCaptor<IntPredicate> mayAccess = ArgumentCaptor.forClass(IntPredicate.class);
        verify(assembler).assemble(any(), eq(true), mayAccess.capture());
        assertThat(mayAccess.getValue().test(123)).isTrue();
        assertThat(mayAccess.getValue().test(456)).isFalse();
        assertThat(mayAccess.getValue().test(789)).isFalse();
        // A restricted patient never reaches the per-patient privilege check, which would mark the session.
        verify(securityInfoManager, never()).hasPrivilege(loggedInInfo, "_demographic", "r", 456);
    }

    @Test
    @DisplayName("should show a viewer without demographics read the patients they are not restricted from")
    void shouldOnlyCheckRestriction_whenViewerCannotReadDemographics() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(false);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 456)).thenReturn(false);
        when(assembler.assemble(any(), eq(false), any())).thenReturn(queueShowing(mock(SmsQueueViewModel.class)));

        action().execute();

        ArgumentCaptor<IntPredicate> mayAccess = ArgumentCaptor.forClass(IntPredicate.class);
        verify(assembler).assemble(any(), eq(false), mayAccess.capture());
        assertThat(mayAccess.getValue().test(123)).isTrue();
        assertThat(mayAccess.getValue().test(456)).isFalse();
        verify(securityInfoManager, never()).hasPrivilege(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("should show nothing when the assembler cannot read which patients to check")
    void shouldShowNothing_whenRestrictionLookupFails() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        // The assembler reads the patients with an entry of their own first, and lets a failure through.
        when(assembler.assemble(any(), eq(false), any()))
                .thenThrow(new IllegalStateException("synthetic lookup failure"));
        SmsQueue2Action action = action();

        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditRecorder);
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
        assertThat(request.getAttribute("smsQueue")).isNull();
    }

    @Test
    @DisplayName("should pass the chosen time period to the assembler and the audit record")
    void shouldPassChosenWindow_whenParameterIsAllowed() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        request.setParameter("window", "90d");
        when(assembler.assemble(any(), eq(false), any())).thenReturn(queueShowing(mock(SmsQueueViewModel.class)));

        action().execute();

        verify(assembler).assemble(eq(SmsQueueWindow.LAST_90_DAYS), eq(false), any());
        verify(auditRecorder).recordViewed(loggedInInfo, SmsQueueWindow.LAST_90_DAYS, Set.of());
    }

    @Test
    @DisplayName("should use the 30-day default when the time period parameter is missing or not allowed")
    void shouldPassDefaultWindow_whenParameterIsMissingOrNotAllowed() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(assembler.assemble(any(), eq(false), any())).thenReturn(queueShowing(mock(SmsQueueViewModel.class)));

        action().execute();
        request.setParameter("window", "7d' OR '1'='1");
        action().execute();

        verify(assembler, times(2)).assemble(eq(SmsQueueWindow.LAST_30_DAYS), eq(false), any());
    }

    /** What the assembler returns: the page's model and, separately, the patients whose messages are in it. */
    private static SmsQueueViewModelAssembler.Result queueShowing(SmsQueueViewModel model,
                                                                  Integer... demographicNumbers) {
        return new SmsQueueViewModelAssembler.Result(model, Set.of(demographicNumbers));
    }

    private SmsQueue2Action action() {
        return new SmsQueue2Action(securityInfoManager, assembler, auditRecorder);
    }
}
