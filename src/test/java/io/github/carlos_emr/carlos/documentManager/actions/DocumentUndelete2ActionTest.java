/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under GPL v2 or later.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentUndelete2Action}.
 *
 * @since 2026-04-14
 */
@DisplayName("DocumentUndelete2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentUndelete2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private TestDocumentUndelete2Action action;

    /** Subclass that overrides EDocUtil seams. */
    static class TestDocumentUndelete2Action extends DocumentUndelete2Action {
        final List<String> undeleted = new ArrayList<>();
        final Map<String, EDoc> docsByNo = new HashMap<>();
        @Override protected EDoc loadDoc(String docNo) { return docsByNo.get(docNo); }
        @Override protected void undeleteDocument(String docNo) { undeleted.add(docNo); }
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

        mockRequest.setMethod("POST");
        action = new TestDocumentUndelete2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private void grantAdmin(boolean admin) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.edocdelete"), eq("w"), isNull()))
            .thenReturn(admin);
    }
    private void grantEdocWrite(boolean write) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
            .thenReturn(write);
    }

    @Test
    @DisplayName("should throw when neither admin nor edoc writer")
    void shouldThrow_whenNoPrivileges() {
        grantAdmin(false);
        grantEdocWrite(false);
        action.setUndelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should return methodNotAllowed on GET")
    void shouldReturnMethodNotAllowed_onGet() throws Exception {
        grantAdmin(true);
        grantEdocWrite(true);
        mockRequest.setMethod("GET");
        action.setUndelDocumentNo("42");
        assertThat(action.execute()).isEqualTo("methodNotAllowed");
    }

    @Test
    @DisplayName("should allow admin to undelete any document")
    void shouldAllowAdmin_toUndeleteAny() throws Exception {
        grantAdmin(true);
        grantEdocWrite(false);
        action.setUndelDocumentNo("42");
        action.execute();
        assertThat(action.undeleted).containsExactly("42");
        assertThat(mockResponse.getRedirectedUrl()).contains("ViewDocumentReport");
    }

    @Test
    @DisplayName("should allow creator with _edoc w to undelete own document")
    void shouldAllowCreator_toUndeleteOwnDoc() throws Exception {
        grantAdmin(false);
        grantEdocWrite(true);

        EDoc doc = mock(EDoc.class);
        when(doc.getCreatorId()).thenReturn("provA");
        action.docsByNo.put("42", doc);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provA");

        action.setUndelDocumentNo("42");
        action.execute();
        assertThat(action.undeleted).containsExactly("42");
    }

    @Test
    @DisplayName("should reject non-creator with only _edoc w")
    void shouldReject_whenNonCreatorEdocWriter() {
        grantAdmin(false);
        grantEdocWrite(true);

        EDoc doc = mock(EDoc.class);
        when(doc.getCreatorId()).thenReturn("provOther");
        action.docsByNo.put("42", doc);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provA");

        action.setUndelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("creator");
        assertThat(action.undeleted).isEmpty();
    }

    @Test
    @DisplayName("should reject non-numeric undelDocumentNo with 400")
    void shouldReject_whenDocNoNotNumeric() throws Exception {
        grantAdmin(true);
        action.setUndelDocumentNo("junk");
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should throw when loggedInInfo is null")
    void shouldThrow_whenNotLoggedIn() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(
                any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(null);
        action.setUndelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("not logged in");
    }

    @Test
    @DisplayName("should reject creator path when doc lookup returns null")
    void shouldReject_whenDocLookupNull() {
        grantAdmin(false);
        grantEdocWrite(true);
        // action.docsByNo has no mapping for "42" -> loadDoc returns null
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provA");
        action.setUndelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("creator");
        assertThat(action.undeleted).isEmpty();
    }

    @Test
    @DisplayName("should reject creator path when doc creatorId is null")
    void shouldReject_whenDocCreatorIdNull() {
        grantAdmin(false);
        grantEdocWrite(true);
        EDoc doc = mock(EDoc.class);
        when(doc.getCreatorId()).thenReturn(null);
        action.docsByNo.put("42", doc);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provA");
        action.setUndelDocumentNo("42");
        assertThatThrownBy(() -> action.execute())
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("creator");
        assertThat(action.undeleted).isEmpty();
    }
}
