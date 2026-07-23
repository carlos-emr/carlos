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

import org.apache.struts2.ActionSupport;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Fax2Action#execute()}'s HTTP-method gate (item 32 / task T17).
 *
 * <p>{@code queue} persists {@link io.github.carlos_emr.carlos.commn.model.FaxJob} rows and
 * promotes files into the outgoing fax queue; {@code cancel} -- including the no-{@code method}
 * fall-through -- deletes temporary files and PHI preview caches. Both are mutations and must be
 * rejected on GET/HEAD before {@code execute()} ever dispatches into them. {@code getPreview},
 * {@code getPageCount}, and {@code prepareFax} are reads ({@code CoverPage.jsp} builds
 * {@code <img src>}/link GETs for the former two, and {@code AddEForm2Action}'s
 * {@code redirectToPreparedFax()} sends a server-side redirect to {@code prepareFax} that the
 * browser always follows with GET) and must stay verb-open.
 */
@DisplayName("Fax2Action execute() HTTP-method gate unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionMethodGateUnitTest extends CarlosUnitTestBase {

    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        // cancel() (the no-method fall-through target) gates on _fax read; the verb-gate tests
        // here are about dispatch, not authorization, so grant it.
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull()))
                .thenReturn(true);

        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @Test
    @DisplayName("should send 405 on GET with method queue before any side effect")
    void shouldSend405_onGetQueue() {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", "queue");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(faxManager);
        }
    }

    @Test
    @DisplayName("should send 405 on HEAD with method queue before any side effect")
    void shouldSend405_onHeadQueue() {
        setUpCommonMocks();
        request.setMethod("HEAD");
        request.setParameter("method", "queue");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(faxManager);
        }
    }

    @Test
    @DisplayName("should send 405 on GET fall-through to cancel")
    void shouldSend405_onGetCancelFallThrough() {
        setUpCommonMocks();
        request.setMethod("GET"); // no method param -- execute() falls through to cancel()

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(faxManager);
        }
    }

    @Test
    @DisplayName("should send 405 on HEAD fall-through to cancel")
    void shouldSend405_onHeadCancelFallThrough() {
        setUpCommonMocks();
        request.setMethod("HEAD"); // no method param -- execute() falls through to cancel()

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(faxManager);
        }
    }

    @Test
    @DisplayName("should route POST with method queue past the verb gate and into queue()")
    void shouldRoutePostQueue_pastVerbGate() {
        setUpCommonMocks();
        // Deny the fax write privilege so queue() fails fast inside validateFaxInputs -- this
        // proves the request reached queue() (not blocked by the verb gate, which would have
        // sent a 405 and never touched securityInfoManager for this privilege at all).
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull()))
                .thenReturn(false);
        request.setMethod("POST");
        request.setParameter("method", "queue");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            assertThatThrownBy(() -> new Fax2Action().execute())
                    .isInstanceOf(SecurityException.class);

            assertThat(response.getStatus())
                    .as("POST must not be rejected by the verb gate with a 405")
                    .isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Test
    @DisplayName("should route POST with no method to cancel() past the verb gate")
    void shouldRoutePostCancelFallThrough_pastVerbGate() {
        setUpCommonMocks();
        request.setMethod("POST"); // no method param -- execute() falls through to cancel()

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(response.getStatus())
                    .as("POST must not be rejected by the verb gate with a 405")
                    .isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            // cancel() with no faxFilePath/transactionType set falls through to returning the
            // (null) transactionType forward -- the point under test is only that dispatch
            // reached cancel() rather than being rejected at the verb gate.
            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("should not 405 a GET for the read-only getPreview method")
    void shouldNotReject_getGetPreview() {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", "getPreview");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            // getPreview streams directly to the response and always returns NONE; the gate must
            // not have short-circuited it with a 405 (the missing-privilege 403 below proves
            // getPreview() itself ran).
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus())
                    .as("getPreview must stay verb-open on GET")
                    .isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should not 405 a GET for the read-only getPageCount method")
    void shouldNotReject_getGetPageCount() {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", "getPageCount");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus())
                    .as("getPageCount must stay verb-open on GET")
                    .isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Test
    @DisplayName("should not 405 a GET for the read-only prepareFax method")
    void shouldNotReject_getPrepareFax() {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", "prepareFax");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            // prepareFax must remain reachable on GET: AddEForm2Action.redirectToPreparedFax()
            // issues a server-side sendRedirect() that the browser always follows with GET.
            String result = new Fax2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus())
                    .as("prepareFax must stay verb-open on GET")
                    .isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verifyNoInteractions(documentAttachmentManager);
        }
    }
}
