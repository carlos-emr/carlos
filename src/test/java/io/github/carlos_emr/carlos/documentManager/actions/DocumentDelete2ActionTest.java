/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under GPL v2 or later.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentDelete2Action}.
 *
 * @since 2026-04-14
 */
@DisplayName("DocumentDelete2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentDelete2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private List<String> deletedDocNos;
    private TestDocumentDelete2Action action;

    /** Subclass that captures EDocUtil.deleteDocument(...) calls without touching the real static. */
    static class TestDocumentDelete2Action extends DocumentDelete2Action {
        final List<String> deleted = new ArrayList<>();
        @Override protected void deleteDocument(String docNo) { deleted.add(docNo); }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
            .thenReturn(true);

        mockRequest.setMethod("POST");
        action = new TestDocumentDelete2Action();
        deletedDocNos = action.deleted;
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should throw SecurityException when _edoc w missing")
    void shouldThrow_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
            .thenReturn(false);
        action.setDelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("_edoc w");
        assertThat(deletedDocNos).isEmpty();
    }

    @Test
    @DisplayName("should return methodNotAllowed on GET")
    void shouldReturnMethodNotAllowed_onGet() throws Exception {
        mockRequest.setMethod("GET");
        action.setDelDocumentNo("42");
        assertThat(action.execute()).isEqualTo("methodNotAllowed");
        assertThat(deletedDocNos).isEmpty();
    }

    @Test
    @DisplayName("should reject non-numeric delDocumentNo with 400")
    void shouldReject_whenDocNoNotNumeric() throws Exception {
        action.setDelDocumentNo("abc; DROP TABLE");
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(deletedDocNos).isEmpty();
    }

    @Test
    @DisplayName("should delete and redirect to report view by default")
    void shouldDeleteAndRedirectToReport_byDefault() throws Exception {
        action.setDelDocumentNo("42");
        action.setFunction("demographic");
        action.setFunctionid("1001");
        action.setViewstatus("active");
        action.execute();
        assertThat(deletedDocNos).containsExactly("42");
        assertThat(mockResponse.getRedirectedUrl())
            .contains("/documentManager/ViewDocumentReport.do")
            .contains("function=demographic")
            .contains("functionid=1001")
            .contains("viewstatus=active");
    }

    @Test
    @DisplayName("should redirect to browser view when source=browser")
    void shouldRedirectToBrowser_whenSourceBrowser() throws Exception {
        action.setDelDocumentNo("42");
        action.setSource("browser");
        action.setCategorykey("Private Documents");
        action.execute();
        assertThat(mockResponse.getRedirectedUrl())
            .contains("/documentManager/ViewDocumentBrowser.do")
            .contains("categorykey=");
    }

    @Test
    @DisplayName("should preserve all seven redirect params")
    void shouldPreserveAllParams_onRedirect() throws Exception {
        action.setDelDocumentNo("42");
        action.setFunction("demographic");
        action.setFunctionid("1001");
        action.setDoctype("lab");
        action.setCurUser("provA");
        action.setView("all");
        action.setViewstatus("active");
        action.setCategorykey("Private");
        action.execute();
        String url = mockResponse.getRedirectedUrl();
        assertThat(url)
            .contains("function=demographic")
            .contains("functionid=1001")
            .contains("doctype=lab")
            .contains("curUser=provA")
            .contains("view=all")
            .contains("viewstatus=active")
            .contains("categorykey=Private");
    }

    @Test
    @DisplayName("should redirect without deleting when delDocumentNo is empty")
    void shouldRedirectWithoutDeleting_whenEmpty() throws Exception {
        action.setDelDocumentNo("");
        action.execute();
        assertThat(deletedDocNos).isEmpty();
        assertThat(mockResponse.getStatus()).isNotEqualTo(400);
        assertThat(mockResponse.getRedirectedUrl()).contains("ViewDocumentReport.do");
    }
}
