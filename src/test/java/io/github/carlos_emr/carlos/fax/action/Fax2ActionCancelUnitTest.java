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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Fax2Action#cancel()} covering the flush-failure UX fix (item 22).
 *
 * <p>Before the fix, a failed {@code faxManager.flush(...)} call only recorded an
 * {@code addActionError} and then redirected away from the preview page — the redirect discarded
 * the action error, so the user landed on the destination page with no indication that the
 * preview cache / temporary file cleanup had failed. The fix skips the redirect on flush failure,
 * returns to the "preview" result, and sets a {@code faxCleanupFailed} request attribute so
 * CoverPage.jsp can render the failure.</p>
 */
@DisplayName("Fax2Action cancel() flush-failure UX unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionCancelUnitTest extends CarlosUnitTestBase {

    private static final String APP_TEMP_ROOT =
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "carlos-temp").toString();

    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);

        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        // cancel() gates on _fax read before touching the flush/redirect flow.
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when the fax read privilege is missing")
    void shouldThrowSecurityException_whenFaxReadPrivilegeMissing() {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");

            assertThatThrownBy(action::cancel)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_fax)");
            // The privilege gate fires before any flush/deletion side effect.
            verify(faxManager, never()).flush(any(), anyString());
        }
    }

    @Test
    @DisplayName("should return preview with faxCleanupFailed set and skip the redirect when flush fails")
    void shouldReturnPreviewWithCleanupFailedAttribute_whenFlushFails() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(55);
            action.setDemographicNo(10);

            String result = action.cancel();

            assertThat(result).isEqualTo("preview");
            assertThat(request.getAttribute("faxCleanupFailed")).isEqualTo(Boolean.TRUE);
            // The user must never see the CONSULTATION redirect after a failed cleanup: the
            // pre-fix code recorded an addActionError but redirected anyway, silently discarding it.
            assertThat(response.getRedirectedUrl()).isNull();
        }
    }

    @Test
    @DisplayName("should still redirect to the consultation view when flush succeeds")
    void shouldRedirectToConsultationView_whenFlushSucceeds() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("CONSULTATION");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(55);
            action.setDemographicNo(10);

            String result = action.cancel();

            assertThat(result).isEqualTo(Fax2Action.NONE);
            assertThat(request.getAttribute("faxCleanupFailed")).isNull();
            assertThat(response.getRedirectedUrl()).contains("/encounter/ViewRequest");
        }
    }

    @Test
    @DisplayName("should still redirect to the eform view when flush succeeds")
    void shouldRedirectToEformView_whenFlushSucceeds() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(77);

            String result = action.cancel();

            assertThat(result).isEqualTo(Fax2Action.NONE);
            assertThat(request.getAttribute("faxCleanupFailed")).isNull();
            assertThat(response.getRedirectedUrl()).contains("/eform/efmshowform_data");
        }
    }

    @Test
    @DisplayName("should return preview with faxCleanupFailed set for an EFORM cancel when flush fails")
    void shouldReturnPreviewWithCleanupFailedAttribute_forEformWhenFlushFails() {
        setUpCommonMocks();
        when(faxManager.flush(any(LoggedInInfo.class), anyString())).thenReturn(false);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setTransactionId(77);

            String result = action.cancel();

            assertThat(result).isEqualTo("preview");
            assertThat(request.getAttribute("faxCleanupFailed")).isEqualTo(Boolean.TRUE);
            assertThat(response.getRedirectedUrl()).isNull();
        }
    }
}
