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
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RtlPreventions2Action}.
 *
 * <p>Tests the secure AJAX endpoint that returns OWASP-encoded prevention
 * summaries for the Rich Text Letter eForm. Validates security checks,
 * input validation, output encoding, and edge case handling.</p>
 *
 * @since 2026-03-23
 */
@DisplayName("RtlPreventions2Action Unit Tests")
@Tag("unit")
@Tag("eform")
class RtlPreventions2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private PreventionManager mockPreventionManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private RtlPreventions2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        // Mock ServletActionContext statics
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        // Mock LoggedInInfo.getLoggedInInfoFromSession()
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);

        // Register mocks in SpringUtils registry (CarlosUnitTestBase handles SpringUtils mocking)
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(PreventionManager.class, mockPreventionManager);

        // Default: allow eform read privilege
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), isNull()))
            .thenReturn(true);

        // Create action (field initializers call ServletActionContext and SpringUtils)
        action = new RtlPreventions2Action();
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
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("Should throw SecurityException when eform read privilege denied")
        void shouldThrowSecurityException_whenEformPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), isNull()))
                .thenReturn(false);

            mockRequest.setParameter("demographic_no", "123");

            assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");
        }

        @Test
        @DisplayName("Should check _eform read privilege before processing")
        void shouldCheckEformPrivilege_beforeProcessing() throws Exception {
            mockRequest.setParameter("demographic_no", "123");
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(new ArrayList<>());

            action.execute();

            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), isNull());
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Should return 400 when demographic_no is missing")
        void shouldReturn400_whenDemographicNoMissing() throws Exception {
            // No parameter set

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(mockResponse.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 when demographic_no is non-numeric")
        void shouldReturn400_whenDemographicNoNonNumeric() throws Exception {
            mockRequest.setParameter("demographic_no", "abc");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(mockResponse.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 when demographic_no contains special characters")
        void shouldReturn400_whenDemographicNoHasSpecialChars() throws Exception {
            mockRequest.setParameter("demographic_no", "123; DROP TABLE preventions;--");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(mockResponse.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 when demographic_no overflows integer range")
        void shouldReturn400_whenDemographicNoOverflows() throws Exception {
            mockRequest.setParameter("demographic_no", "99999999999");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(mockResponse.getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 when demographic_no is empty string")
        void shouldReturn400_whenDemographicNoEmpty() throws Exception {
            mockRequest.setParameter("demographic_no", "");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(mockResponse.getStatus()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Prevention Output")
    class PreventionOutput {

        @Test
        @DisplayName("Should return HTML table when preventions exist")
        void shouldReturnHtmlTable_whenPreventionsExist() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention p = createPrevention("Flu Shot", new GregorianCalendar(2026, 0, 15).getTime(), false);
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("<table");
            assertThat(content).contains("Flu Shot");
            assertThat(content).contains("2026-01-15");
            assertThat(content).contains("</table>");
        }

        @Test
        @DisplayName("Should return no-preventions message when list is empty")
        void shouldReturnNoPreventionsMessage_whenListEmpty() throws Exception {
            mockRequest.setParameter("demographic_no", "123");
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(new ArrayList<>());

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).isEqualTo("No preventions on file.");
        }

        @Test
        @DisplayName("Should return no-preventions message when list is null")
        void shouldReturnNoPreventionsMessage_whenListNull() throws Exception {
            mockRequest.setParameter("demographic_no", "123");
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(null);

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).isEqualTo("No preventions on file.");
        }

        @Test
        @DisplayName("Should filter out deleted preventions from output")
        void shouldFilterDeletedPreventions_fromOutput() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention active = createPrevention("Tetanus", new GregorianCalendar(2026, 2, 1).getTime(), false);
            Prevention deleted = createPrevention("OldVaccine", new GregorianCalendar(2025, 5, 1).getTime(), true);

            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(active, deleted));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("Tetanus");
            assertThat(content).doesNotContain("OldVaccine");
        }

        @Test
        @DisplayName("Should emit table header but no data rows when all preventions are deleted")
        void shouldEmitTableWithoutDataRows_whenAllPreventionsDeleted() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention deleted1 = createPrevention("A", new Date(), true);
            Prevention deleted2 = createPrevention("B", new Date(), true);

            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(deleted1, deleted2));

            action.execute();

            String content = mockResponse.getContentAsString();
            // Table is still emitted (with header) because the list is non-empty,
            // but no data rows appear for deleted preventions
            assertThat(content).contains("<table");
            assertThat(content).doesNotContain("<td>");
        }

        @Test
        @DisplayName("Should render multiple preventions in table rows")
        void shouldRenderMultiplePreventions_inTableRows() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention p1 = createPrevention("Flu Shot", new GregorianCalendar(2026, 0, 15).getTime(), false);
            Prevention p2 = createPrevention("COVID-19", new GregorianCalendar(2026, 2, 22).getTime(), false);

            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p1, p2));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("Flu Shot");
            assertThat(content).contains("COVID-19");
            assertThat(content).contains("2026-01-15");
            assertThat(content).contains("2026-03-22");
        }
    }

    @Nested
    @DisplayName("Output Encoding")
    class OutputEncoding {

        @Test
        @DisplayName("Should OWASP-encode prevention type with HTML special characters")
        void shouldOwaspEncodePreventionType_withHtmlSpecialChars() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention p = createPrevention("<script>alert('xss')</script>", new Date(), false);
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).doesNotContain("<script>");
            assertThat(content).contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("Should handle null prevention type gracefully")
        void shouldHandleNullPreventionType_gracefully() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention p = createPrevention(null, new Date(), false);
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("<td></td>");
        }

        @Test
        @DisplayName("Should handle null prevention date gracefully")
        void shouldHandleNullPreventionDate_gracefully() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            Prevention p = createPrevention("Flu Shot", null, false);
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("Flu Shot");
            // Date cell should be empty
            assertThat(content).contains("<td></td>");
        }
    }

    @Nested
    @DisplayName("Response Headers")
    class ResponseHeaders {

        @Test
        @DisplayName("Should set content type to text/html with UTF-8")
        void shouldSetContentType_toTextHtmlUtf8() throws Exception {
            mockRequest.setParameter("demographic_no", "123");
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(new ArrayList<>());

            action.execute();

            assertThat(mockResponse.getContentType()).isEqualTo("text/html; charset=UTF-8");
        }

        @Test
        @DisplayName("Should return null result (writes directly to response)")
        void shouldReturnNull_asStrutsResult() throws Exception {
            mockRequest.setParameter("demographic_no", "123");
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(new ArrayList<>());

            String result = action.execute();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Date Formatting")
    class DateFormatting {

        @Test
        @DisplayName("Should format prevention date as yyyy-MM-dd")
        void shouldFormatDate_asYyyyMmDd() throws Exception {
            mockRequest.setParameter("demographic_no", "123");

            // March 5, 2026
            Prevention p = createPrevention("Test", new GregorianCalendar(2026, 2, 5).getTime(), false);
            when(mockPreventionManager.getPreventionsByDemographicNo(any(), eq(123)))
                .thenReturn(List.of(p));

            action.execute();

            String content = mockResponse.getContentAsString();
            assertThat(content).contains("2026-03-05");
        }
    }

    /**
     * Creates a mock Prevention with the given type, date, and deleted status.
     */
    private Prevention createPrevention(String type, Date date, boolean deleted) {
        Prevention p = mock(Prevention.class);
        when(p.getPreventionType()).thenReturn(type);
        when(p.getPreventionDate()).thenReturn(date);
        when(p.isDeleted()).thenReturn(deleted);
        return p;
    }
}
