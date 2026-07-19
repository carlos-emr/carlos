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
package io.github.carlos_emr.carlos.fax.action;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Fax2Action authorization unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionAuthorizationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should reject getPageCount when fax read privilege is missing")
    void shouldRejectGetPageCount_whenFaxReadPrivilegeMissing() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/example.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }


    @Test
    @DisplayName("should reject prepareFax when fax read privilege is missing")
    void shouldRejectPrepareFax_whenFaxReadPrivilegeMissing() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("transactionType", "eform");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            assertThat(action.prepareFax()).isEqualTo(Fax2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verifyNoInteractions(documentAttachmentManager);
        }
    }

    @Test
    @DisplayName("should reject getPreview when jobId is not numeric")
    void shouldRejectGetPreview_whenJobIdIsNotNumeric() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("jobId", "abc");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("should reject getPreview when pageNumber is not numeric")
    void shouldRejectGetPreview_whenPageNumberIsNotNumeric() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNumber", "abc");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPreview();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
