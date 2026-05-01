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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MoveMohFiles2Action} pinning the result-name contract
 * and the privilege gate.
 *
 * <p>Pre-existing bug: the action returned the literal string {@code "Success"}
 * (capital S) but the struts mapping at {@code struts-billing.xml:213} declared
 * {@code <result name="success">}. Struts2 result names are case-sensitive and
 * the global-results don't catch unmatched names, so every successful archival
 * call produced a {@code ConfigurationException} 500 instead of the
 * {@code viewMOHFiles.jsp} forward. The fix is to return
 * {@link ActionSupport#SUCCESS} (the inherited constant equal to {@code "success"}).
 *
 * @since 2026-04-29
 */
@DisplayName("MoveMohFiles2Action")
@Tag("unit")
@Tag("billing")
class MoveMohFiles2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        // ServletActionContext.getRequest()/getResponse() must be stubbed before
        // the action is constructed — both fields are populated by the action's
        // field initializers.
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    /**
     * The whole point of this test class: returning the literal {@code "Success"}
     * silently bypasses the {@code <result name="success">} forward. Pin the
     * inherited constant explicitly so any future drift is caught.
     */
    @Test
    void shouldReturnLowercaseSuccessConstant_whenInvokedWithMissingFolderParam() throws Exception {
        // No folder/mohFile params → execute() falls through to the assemble +
        // return path without entering the file-archival loop. We don't care
        // about the assembled view model here — only the result name.
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        String result = action.execute();

        assertThat(result)
                .as("must equal ActionSupport.SUCCESS so the struts mapping resolves")
                .isEqualTo(ActionSupport.SUCCESS)
                .isEqualTo("success");
    }

    @Test
    void shouldThrowSecurityException_whenLackingAdminWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    /**
     * GET without mohFile = render path (allowed). The action is dual-purpose
     * by design: GET renders the file listing, POST archives selected files.
     * The HttpMethodGuardFilter previously blocked all GETs to this URL; the
     * conditional gate restores legitimate render-on-GET while still
     * enforcing POST-only for archival.
     */
    @Test
    void shouldReturnSuccess_onGetWithoutMutationIntent() throws Exception {
        mockRequest.setMethod("GET");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    /**
     * GET with mohFile = mutation intent on the wrong method. Action must 405
     * with an Allow header rather than silently archiving via GET.
     */
    @Test
    void shouldReturn405WithAllowHeader_onGetWithMutationIntent() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("mohFile", "claim.000");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReturn405WithAllowHeader_onGetWithUnzipMutationIntent() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("unzipfile", "claim.zip");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldTreatBlankMohFileParameterAsRenderOnly_onGet() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("mohFile", " ");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void shouldReturn405WithAllowHeader_onGetWithAnyNonBlankMohFile() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("mohFile", " ", "claim.000");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldPropagateProgrammingErrorsFromFileLocationValidation() {
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        try (MockedStatic<PathValidationUtils> pathMock = mockStatic(PathValidationUtils.class)) {
            pathMock.when(() -> PathValidationUtils.validateExistingPath(any(), any()))
                    .thenThrow(new NullPointerException("programming error"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    action, "validateFileLocation", new java.io.File("claim.000")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("programming error");
        }
    }
}
