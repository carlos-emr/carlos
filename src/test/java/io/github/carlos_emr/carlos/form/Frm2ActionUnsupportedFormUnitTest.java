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
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
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
 * Pins how {@link Frm2Action} answers a form class it cannot service.
 *
 * <p>{@link FrmRecordFactory#factory(String)} returns {@code null} by design — it is the guard
 * on reflective instantiation and refuses anything outside
 * {@link FrmRecordFactory#ALLOWED_FORM_CLASSES}. Every later use of that record dereferences it,
 * so a refused class raised a {@code NullPointerException} which the action's own catch
 * downgraded to the {@code failure} forward, reaching the clinician as "CARLOS Error: 500".
 * The chart can reach this: the form selector offers ALPHA and {@code formAlpha} carries rows,
 * but no {@code FrmAlphaRecord} exists. See issue #3735.
 */
@DisplayName("Frm2Action unsupported form class Tests")
@Tag("unit")
@Tag("form")
class Frm2ActionUnsupportedFormUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should answer 400 when the form class is not on the allow-list")
    void shouldAnswerBadRequest_whenFormClassNotAllowed() {
        // "Alpha" is offered by the form selector and has rows, but has no FrmAlphaRecord.
        mockRequest.setParameter("form_class", "Alpha");
        mockRequest.setParameter("demographic_no", "1");
        mockRequest.setParameter("formId", "1");

        assertThat(new Frm2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should answer 400 when no form class is supplied at all")
    void shouldAnswerBadRequest_whenFormClassAbsent() {
        // factory(null) is refused by the same guard, so this must not become a 500 either.
        mockRequest.setParameter("demographic_no", "1");

        assertThat(new Frm2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should not echo the rejected class name into the error message")
    void shouldNotEchoClassName_inErrorMessage() {
        // The value is caller-controlled and the container renders this message into an error
        // page, so it is logged rather than reflected.
        mockRequest.setParameter("form_class", "<script>alert(1)</script>");
        mockRequest.setParameter("demographic_no", "1");

        new Frm2Action().execute();

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockResponse.getErrorMessage()).doesNotContain("script");
    }

    @Test
    @DisplayName("should reject before resolving the form when _form write is denied")
    void shouldRejectRequest_whenFormWriteDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("form_class", "Alpha");
        mockRequest.setParameter("demographic_no", "1");

        assertThatThrownBy(() -> new Frm2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_form");

        assertThat(mockResponse.getStatus()).isEqualTo(200);
    }
}
