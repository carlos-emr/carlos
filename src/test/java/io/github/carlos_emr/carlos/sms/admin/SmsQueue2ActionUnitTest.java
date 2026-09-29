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

import java.util.List;
import java.util.function.IntPredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
        when(assembler.assemble(eq(true), any())).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
        verify(auditRecorder).recordViewed(loggedInInfo, List.of());
    }

    @Test
    @DisplayName("should build the queue without demographic numbers when the user lacks _demographic read")
    void shouldHideDemographicNumbers_whenUserLacksDemographicRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(false);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(eq(false), any())).thenReturn(model);

        action().execute();

        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
        verify(assembler, never()).assemble(eq(true), any());
        verify(auditRecorder).recordViewed(loggedInInfo, List.of());
    }

    @Test
    @DisplayName("should audit which patients' numbers are shown, once each, before handing the page over")
    void shouldAuditShownPatients_beforeShowingThePage() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        SmsQueueViewModel model = queueShowing("123", "", "456", "123");
        when(assembler.assemble(eq(true), any())).thenReturn(model);

        action().execute();

        InOrder order = inOrder(assembler, auditRecorder);
        order.verify(assembler).assemble(eq(true), any());
        order.verify(auditRecorder).recordViewed(loggedInInfo, List.of("123", "456"));
        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
    }

    @Test
    @DisplayName("should show nothing when the view cannot be audited")
    void shouldShowNothing_whenAuditFails() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(assembler.assemble(eq(false), any())).thenReturn(queueShowing());
        doThrow(new IllegalStateException("audit write failed")).when(auditRecorder).recordViewed(any(), any());
        SmsQueue2Action action = action();

        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
        assertThat(request.getAttribute("smsQueue")).isNull();
    }

    @Test
    @DisplayName("should ask, per patient, whether this viewer may read that patient")
    void shouldCheckEachPatient_againstDemographicRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 123)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 456)).thenReturn(false);
        when(assembler.assemble(eq(true), any())).thenReturn(queueShowing());

        action().execute();

        ArgumentCaptor<IntPredicate> mayRead = ArgumentCaptor.forClass(IntPredicate.class);
        verify(assembler).assemble(eq(true), mayRead.capture());
        assertThat(mayRead.getValue().test(123)).isTrue();
        assertThat(mayRead.getValue().test(456)).isFalse();
    }

    /** A queue whose overdue list shows these demographic numbers (empty: a row whose number is hidden). */
    private static SmsQueueViewModel queueShowing(String... demographicNumbers) {
        List<SmsQueueViewModel.Row> rows = java.util.Arrays.stream(demographicNumbers)
                .map(number -> new SmsQueueViewModel.Row("1", "STUB", "QUEUED", "", "", "", "", 0, "", "", number,
                        "***1212"))
                .toList();
        SmsQueueViewModel.ProviderQueue stub = new SmsQueueViewModel.ProviderQueue("STUB", rows.size(), List.of(),
                rows.size(), rows, 0, List.of(), 0, List.of(), List.of(), 0, List.of(), List.of());
        return new SmsQueueViewModel("2026-09-28 14:00", 5, 5, 50, true,
                new SmsQueueViewModel.Scheduler(false, false, false, false, "", "", "", 0), List.of(stub));
    }

    private SmsQueue2Action action() {
        return new SmsQueue2Action(securityInfoManager, assembler, auditRecorder);
    }
}
