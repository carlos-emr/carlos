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
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        verifyNoInteractions(assembler);
        assertThat(request.getAttribute("smsQueue")).isNull();
    }

    @Test
    @DisplayName("should refuse the page before any privilege lookup when there is no logged-in session")
    void shouldDenyPage_whenNoSession() {
        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.sms)");
        verifyNoInteractions(assembler);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should render the queue model when the user has _admin.sms read, checked without a patient")
    void shouldRenderQueue_whenUserHasAdminSmsRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(true)).thenReturn(model);

        String result = action().execute();

        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
    }

    @Test
    @DisplayName("should build the queue without demographic numbers when the user lacks _demographic read")
    void shouldHideDemographicNumbers_whenUserLacksDemographicRead() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.sms", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(false);
        SmsQueueViewModel model = mock(SmsQueueViewModel.class);
        when(assembler.assemble(false)).thenReturn(model);

        action().execute();

        assertThat(request.getAttribute("smsQueue")).isSameAs(model);
        verify(assembler, never()).assemble(true);
    }

    private SmsQueue2Action action() {
        return new SmsQueue2Action(securityInfoManager, assembler);
    }
}
