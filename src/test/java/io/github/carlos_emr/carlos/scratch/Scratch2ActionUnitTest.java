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
package io.github.carlos_emr.carlos.scratch;

import io.github.carlos_emr.carlos.commn.dao.ScratchPadDao;
import io.github.carlos_emr.carlos.commn.model.ScratchPad;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for scratchpad request ownership checks.
 *
 * @since 2026-05-04
 */
@Tag("unit")
@DisplayName("Scratch2Action")
class Scratch2ActionUnitTest extends CarlosUnitTestBase {

    private ScratchPadDao scratchPadDao;

    @BeforeEach
    void setUp() {
        scratchPadDao = mock(ScratchPadDao.class);
        registerMock(ScratchPadDao.class, scratchPadDao);
    }

    @Test
    @DisplayName("should launch scratchpad page when request is not post")
    void shouldLaunchScratchpadPage_whenRequestIsNotPost() throws Exception {
        HttpServletRequest request = mockRequest("GET", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        Scratch2Action action = createAction(request, response);

        assertThat(action.execute()).isEqualTo("success");
        verifyNoInteractions(scratchPadDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "save"})
    @DisplayName("should save for the session provider with the default or explicit save operation")
    void shouldSaveScratchpadForSessionUser_whenProviderNoParameterIsAbsent(String method) throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();

        when(request.getParameter("method")).thenReturn(method);
        when(request.getParameter("id")).thenReturn("0");
        when(request.getParameter("scratchpad")).thenReturn("test note");
        when(request.getParameter("windowId")).thenReturn("window-1");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        ScratchPad saved = new ScratchPad();
        saved.setId(1);
        saved.setText("test note");
        when(scratchPadDao.saveIfCurrent("999998", 0, "test note"))
                .thenReturn(new ScratchPadDao.SaveResult(saved, false));
        assertThat(createAction(request, response).execute()).isEqualTo("none");
        verify(scratchPadDao).saveIfCurrent("999998", 0, "test note");
        assertThat(json.toString()).contains("\"success\":true", "\"id\":\"1\"", "\"text\":\"test note\"");
    }

    @Test
    void shouldRejectSave_whenTextParameterMissing() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("id")).thenReturn("0");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        assertThat(createAction(request, response).execute()).isEqualTo("none");
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(scratchPadDao);
        assertThat(json.toString()).contains("\"success\":false");
    }

    @Test
    void shouldSaveEmptyText_whenProviderDeliberatelyClearsScratchpad() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("scratchpad")).thenReturn("");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        ScratchPad saved = new ScratchPad();
        saved.setId(8);
        saved.setText("");
        when(scratchPadDao.saveIfCurrent("999998", 7, ""))
                .thenReturn(new ScratchPadDao.SaveResult(saved, false));
        assertThat(createAction(request, response).execute()).isEqualTo("none");
        verify(scratchPadDao).saveIfCurrent("999998", 7, "");
        assertThat(json.toString()).contains("\"id\":\"8\"", "\"text\":\"\"");
    }

    @Test
    void shouldRejectSaveWithoutAdvancingRevision_whenEditorIsStale() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("scratchpad")).thenReturn("local text");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(scratchPadDao.saveIfCurrent("999998", 7, "local text"))
                .thenReturn(new ScratchPadDao.SaveResult(null, true));
        createAction(request, response).execute();
        verify(response).setStatus(HttpServletResponse.SC_CONFLICT);
        assertThat(json.toString()).contains("\"success\":false").doesNotContain("\"id\":", "local text");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "-1", "abc", "2147483648"})
    void shouldRejectRevisionBeforePersistence_whenInvalid(String revision) throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getParameter("id")).thenReturn(revision);
        when(request.getParameter("scratchpad")).thenReturn("local text");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        createAction(request, response).execute();
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(scratchPadDao);
    }

    @Test
    void shouldReturnLiteralJsonText_whenSavingSpecialCharacters() throws Exception {
        String text = "  A+B %20 &amp; <note>\n";
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("scratchpad")).thenReturn(text);
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        ScratchPad saved = new ScratchPad();
        saved.setId(8);
        saved.setText(text);
        when(scratchPadDao.saveIfCurrent("999998", 7, text))
                .thenReturn(new ScratchPadDao.SaveResult(saved, false));
        createAction(request, response).execute();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.toString()).get("text").asText()).isEqualTo(text);
    }

    @Test
    void shouldReportFailureWithoutPrivateDetails_whenPersistenceFails() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("scratchpad")).thenReturn("private note");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(scratchPadDao.saveIfCurrent("999998", 7, "private note"))
                .thenThrow(new IllegalStateException("private database detail"));
        try (LogCapture capture = LogCapture.forLogger(Scratch2Action.class)) {
            createAction(request, response).execute();
            verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(json.toString()).contains("\"success\":false").doesNotContain("private note", "private database detail");
            assertThat(capture.messages()).allSatisfy(message ->
                    assertThat(message).doesNotContain("private note", "private database detail"));
        }
    }

    @Test
    @DisplayName("should allow save when providerNo request parameter is absent")
    void shouldAllowSave_whenProviderNoRequestParameterIsAbsent() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", null)).isTrue();
    }

    @Test
    @DisplayName("should allow save when providerNo request parameter is blank")
    void shouldAllowSave_whenProviderNoRequestParameterIsBlank() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", " ")).isTrue();
    }

    @Test
    @DisplayName("should allow save when providerNo matches session user")
    void shouldAllowSave_whenProviderNoMatchesSessionUser() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", "999998")).isTrue();
    }

    @Test
    @DisplayName("should reject save when providerNo differs from session user")
    void shouldRejectSave_whenProviderNoDiffersFromSessionUser() {
        assertThat(Scratch2Action.isRequestForSessionProvider("999998", "123456")).isFalse();
    }

    @Test
    @DisplayName("should reject save when session user is absent")
    void shouldRejectSave_whenSessionUserIsAbsent() {
        assertThat(Scratch2Action.isRequestForSessionProvider(null, null)).isFalse();
        assertThat(Scratch2Action.isRequestForSessionProvider(" ", null)).isFalse();
    }

    @Test
    @DisplayName("should omit provider values when rejecting mismatched save")
    void shouldOmitProviderValues_whenRejectingMismatchedSave() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998\r\nforged-session");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("providerNo")).thenReturn("123456\r\nforged-provider");
        when(response.getWriter()).thenReturn(new PrintWriter(json));

        try (LogCapture capture = LogCapture.forLogger(Scratch2Action.class)) {
            Scratch2Action action = createAction(request, response);

            assertThat(action.execute()).isEqualTo("none");

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verifyNoInteractions(scratchPadDao);
            assertThat(json.toString()).contains("\"success\":false", "\"message\":\"Provider mismatch\"");
            assertThat(capture.messages()).hasSize(1);
            String logged = capture.messages().get(0);
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).doesNotContain(
                    "123456\r\nforged-provider",
                    "999998\r\nforged-session",
                    "123456\\r\\nforged-provider",
                    "999998\\r\\nforged-session",
                    "forged-provider",
                    "forged-session");
        }
    }

    @Test
    void shouldDenyVersionRead_whenOwnedByAnotherProvider() throws Exception {
        HttpServletRequest request = mockRequest("GET", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getParameter("id")).thenReturn("7");
        ScratchPad stored = new ScratchPad();
        stored.setProviderNo("123456");
        when(scratchPadDao.find(7)).thenReturn(stored);
        assertThat(createAction(request, response).showVersion()).isEqualTo("none");
        verify(response).setStatus(404);
        verify(request, never()).setAttribute(any(), any());
    }

    @Test
    void shouldDenyVersionDelete_whenOwnedByAnotherProvider() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("providerNo")).thenReturn("123456");
        ScratchPad stored = new ScratchPad();
        stored.setProviderNo("123456");
        stored.setStatus(true);
        when(scratchPadDao.find(7)).thenReturn(stored);
        createAction(request, response).delete();
        verify(response).setStatus(404);
        assertThat(stored.isStatus()).isTrue();
        verify(scratchPadDao, never()).merge(any());
        assertThat(json.toString()).contains("\"success\":false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldShowOwnedVersion_whenDispatchedAsRead(String httpMethod) throws Exception {
        HttpServletRequest request = mockRequest(httpMethod, "999998");
        when(request.getParameter("method")).thenReturn("showVersion");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getParameter("id")).thenReturn("7");
        ScratchPad stored = new ScratchPad();
        stored.setProviderNo("999998");
        when(scratchPadDao.find(7)).thenReturn(stored);
        assertThat(createAction(request, response).execute()).isEqualTo("scratchPadVersion");
        verify(request).setAttribute("ScratchPad", stored);
    }

    @Test
    void shouldAvoidDatabase_whenVersionRequestIsAnonymous() throws Exception {
        HttpServletRequest request = mockRequest("GET", null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        assertThat(createAction(request, response).showVersion()).isEqualTo("none");
        verify(response).setStatus(401);
        verifyNoInteractions(scratchPadDao);
        assertThat(json.toString()).contains("\"success\":false", "Session provider required");
    }

    @Test
    void shouldRejectDelete_whenCalledDirectlyWithGet() throws Exception {
        HttpServletRequest request = mockRequest("GET", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        createAction(request, response).delete();
        verify(response).setStatus(405);
        verifyNoInteractions(scratchPadDao);
    }

    /**
     * Creates a mocked request with the supplied HTTP method and session provider number.
     *
     * @param httpMethod String the HTTP method to expose from the request
     * @param providerNo String the provider number stored in the mocked session
     * @return HttpServletRequest mocked request for action execution
     */
    private HttpServletRequest mockRequest(String httpMethod, String providerNo) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getMethod()).thenReturn(httpMethod);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(providerNo);
        return request;
    }

    @Test
    void shouldReportDeleteSuccess_afterPersistingOwnedVersion() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(request.getParameter("method")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        ScratchPad stored = new ScratchPad();
        stored.setId(7);
        stored.setProviderNo("999998");
        stored.setStatus(true);
        stored.setDateTime(java.sql.Date.valueOf("2026-08-01"));
        when(scratchPadDao.find(7)).thenReturn(stored);
        createAction(request, response).execute();
        verify(scratchPadDao).merge(stored);
        assertThat(stored.isStatus()).isFalse();
        assertThat(json.toString()).contains("\"success\":true", "\"id\":\"7\"");
    }

    @Test
    void shouldReportDeleteFailure_whenPersistenceFails() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(request.getParameter("id")).thenReturn("7");
        ScratchPad stored = new ScratchPad();
        stored.setId(7);
        stored.setProviderNo("999998");
        when(scratchPadDao.find(7)).thenReturn(stored);
        doThrow(new IllegalStateException("database unavailable"))
                .when(scratchPadDao).merge(stored);
        createAction(request, response).delete();
        verify(response).setStatus(500);
        assertThat(json.toString()).contains("\"success\":false").doesNotContain("database unavailable");
    }

    @Test
    void shouldRejectVersionId_beforeDatabaseLookup() throws Exception {
        HttpServletRequest request = mockRequest("GET", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getParameter("id")).thenReturn("not-an-id");
        assertThat(createAction(request, response).showVersion()).isEqualTo("none");
        verify(response).setStatus(400);
        verifyNoInteractions(scratchPadDao);
    }

    @ParameterizedTest
    @CsvSource({"POST,showVersion,405", "PUT,showVersion,405", "GET,delete,405", "HEAD,delete,405",
            "GET,save,405", "POST,unexpected,400", "GET,unexpected,400"})
    void shouldRejectWithoutSaving_whenRequestedOperationCannotRun(String httpMethod, String method,
            int status) throws Exception {
        HttpServletRequest request = mockRequest(httpMethod, "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(request.getParameter("method")).thenReturn(method);
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("scratchpad")).thenReturn("Must not be saved");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        assertThat(createAction(request, response).execute()).isEqualTo("none");
        verify(response).setStatus(status);
        verifyNoInteractions(scratchPadDao);
        assertThat(json.toString()).contains("\"success\":false");
    }

    @ParameterizedTest
    @CsvSource({"execute,missing", "showVersion,missing", "delete,missing",
            "execute,blank", "showVersion,blank", "delete,blank",
            "execute,absent", "showVersion,absent", "delete,absent"})
    void shouldRejectBeforeRequestProcessing_whenSessionProviderUnavailable(String entryPoint, String sessionState) throws Exception {
        HttpServletRequest request = mockRequest("POST", "blank".equals(sessionState) ? " " : null);
        if ("absent".equals(sessionState)) when(request.getSession(false)).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        Scratch2Action action = createAction(request, response);
        String result = switch (entryPoint) {
            case "showVersion" -> action.showVersion();
            case "delete" -> action.delete();
            default -> action.execute();
        };
        assertThat(result).isEqualTo("none");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(request, never()).getParameter(anyString());
        verifyNoInteractions(scratchPadDao);
        assertThat(json.toString()).contains("\"success\":false");
    }

    /**
     * Creates a scratch action with mocked servlet request and response dependencies injected.
     *
     * @param request HttpServletRequest mocked request to inject
     * @param response HttpServletResponse mocked response to inject
     * @return Scratch2Action configured action instance for unit testing
     */
    private Scratch2Action createAction(HttpServletRequest request, HttpServletResponse response) {
        Scratch2Action action = new Scratch2Action();
        injectDependency(action, "request", request);
        injectDependency(action, "response", response);
        return action;
    }
}
