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
 * Unit tests for {@link DocumentRefile2Action}.
 *
 * @since 2026-04-14
 */
@DisplayName("DocumentRefile2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentRefile2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private TestDocumentRefile2Action action;

    /** Subclass that overrides the EDocUtil.refileDocument seam. */
    static class TestDocumentRefile2Action extends DocumentRefile2Action {
        final List<String[]> refileCalls = new ArrayList<>();
        Exception throwOnRefile;
        @Override protected void refileDocument(String docNo, String queue) throws Exception {
            refileCalls.add(new String[]{docNo, queue});
            if (throwOnRefile != null) throw throwOnRefile;
        }
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
        action = new TestDocumentRefile2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should throw when _edoc w missing")
    void shouldThrow_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
            .thenReturn(false);
        action.setRefileDocumentNo("42");
        action.setQueueId("1");
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should return methodNotAllowed on GET")
    void shouldReturnMethodNotAllowed_onGet() throws Exception {
        mockRequest.setMethod("GET");
        action.setRefileDocumentNo("42");
        action.setQueueId("1");
        assertThat(action.execute()).isEqualTo("methodNotAllowed");
    }

    @Test
    @DisplayName("should reject non-numeric params with 400")
    void shouldReject_whenNonNumeric() throws Exception {
        action.setRefileDocumentNo("abc");
        action.setQueueId("1");
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.refileCalls).isEmpty();
    }

    @Test
    @DisplayName("should surface generic errorMessage when refile throws (not raw exception text)")
    void shouldSurfaceGenericError_whenRefileThrows() throws Exception {
        // Simulate an IOException whose message contains PHI-adjacent data.
        action.throwOnRefile = new RuntimeException("Cannot refile document #42 Patient X-ray results ...");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provA");
        action.setRefileDocumentNo("42");
        action.setQueueId("1");
        action.execute();
        String url = mockResponse.getRedirectedUrl();
        assertThat(url)
            .contains("/documentManager/ViewDocumentBrowser.do")
            .contains("errorMessage=Refile%20failed")
            .doesNotContain("X-ray")
            .doesNotContain("Patient");
    }

    @Test
    @DisplayName("should propagate SecurityException (never swallow auth failures)")
    void shouldPropagate_whenRefileThrowsSecurityException() {
        action.throwOnRefile = new SecurityException("nope");
        action.setRefileDocumentNo("42");
        action.setQueueId("1");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should reject when queueId is missing")
    void shouldReject_whenQueueIdMissing() throws Exception {
        action.setRefileDocumentNo("42");
        action.setQueueId(null);
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(action.refileCalls).isEmpty();
    }

    @Test
    @DisplayName("should refile and redirect to browser on success")
    void shouldRefileAndRedirect_onSuccess() throws Exception {
        action.setRefileDocumentNo("42");
        action.setQueueId("1");
        action.setCategorykey("Inbox");
        action.execute();
        assertThat(action.refileCalls).hasSize(1);
        assertThat(action.refileCalls.get(0)).containsExactly("42", "1");
        assertThat(mockResponse.getRedirectedUrl())
            .contains("/documentManager/ViewDocumentBrowser.do")
            .contains("categorykey=Inbox");
    }
}
