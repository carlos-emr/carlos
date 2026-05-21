/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.decision.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ViewDecision2Action}, the privilege + HTTP-method
 * gate for clinical decision-support JSPs.
 *
 * <p>Matrix covered:
 * <ul>
 *   <li>{GET, POST} × {no submit, submit=Save, submit=Print, submit=SAVE mixed case}
 *       × {_form r only, _form r+w, neither}</li>
 * </ul>
 *
 * @since 2026-04-14
 */
@DisplayName("ViewDecision2Action Unit Tests")
@Tag("unit")
@Tag("decision")
class ViewDecision2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private ViewDecision2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        // Default: _form r granted
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull()))
                .thenReturn(true);

        action = new ViewDecision2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Nested
    @DisplayName("Read gate")
    class ReadGate {

        @Test
        @DisplayName("should succeed on GET with _form r and no submit param")
        void shouldSucceed_onGetWithReadAndNoSubmit() throws Exception {
            mockRequest.setMethod("GET");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should throw SecurityException when session is missing")
        void shouldThrow_whenSessionMissing() {
            mockRequest.setMethod("GET");
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_form r");
        }

        @Test
        @DisplayName("should throw SecurityException when _form r is denied")
        void shouldThrow_whenReadPrivilegeDenied() {
            mockRequest.setMethod("GET");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_form r");
        }
    }

    @Nested
    @DisplayName("Write gate")
    class WriteGate {

        @Test
        @DisplayName("should send 405 on GET when submit starts with Save")
        void shouldSend405_onGetWithSaveSubmit() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", " Save ");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verify(mockSecurityInfoManager, never())
                    .hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull());
        }

        @Test
        @DisplayName("should send 405 on GET when submit is Save and Exit")
        void shouldSend405_onGetWithSaveAndExit() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "Save and Exit");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should send 405 on GET when submit is mixed-case SAVE (Locale.ROOT check)")
        void shouldSend405_onGetWithMixedCaseSave() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "SAVE");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should throw SecurityException on POST with Save when _form w denied")
        void shouldThrow_onPostSaveWithoutWritePrivilege() {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("submit", " Save ");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_form w");
        }

        @Test
        @DisplayName("should succeed on POST with Save when _form r and _form w granted")
        void shouldSucceed_onPostSaveWithReadAndWrite() throws Exception {
            mockRequest.setMethod("POST");
            mockRequest.setParameter("submit", " Save ");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should send 405 on GET when submit is whitespace-padded lowercase save (trim + Locale.ROOT combined)")
        void shouldSend405_onGetWithPaddedLowercaseSave() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "  save  ");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should send 405 on GET when submit starts with save but is not a known save value (deliberate prefix heuristic)")
        void shouldSend405_onGetWithSavepoint() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "Savepoint");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("Non-save submit values")
    class NonSaveSubmit {

        @Test
        @DisplayName("should succeed on GET when submit=Print (not a save)")
        void shouldSucceed_onGetWithPrintSubmit() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "Print");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockSecurityInfoManager, never())
                    .hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull());
        }

        @Test
        @DisplayName("should succeed on POST with no submit param")
        void shouldSucceed_onPostWithNoSubmit() throws Exception {
            mockRequest.setMethod("POST");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockSecurityInfoManager, never())
                    .hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull());
        }

        @Test
        @DisplayName("should succeed on GET when submit is empty string")
        void shouldSucceed_onGetWithEmptySubmit() throws Exception {
            mockRequest.setMethod("GET");
            mockRequest.setParameter("submit", "");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockSecurityInfoManager, never())
                    .hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull());
        }
    }
}
