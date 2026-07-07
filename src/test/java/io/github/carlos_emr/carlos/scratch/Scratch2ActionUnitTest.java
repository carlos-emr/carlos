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
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    @Test
    @DisplayName("should save scratchpad for session user when providerNo request parameter is absent")
    void shouldSaveScratchpadForSessionUser_whenProviderNoParameterIsAbsent() throws Exception {
        HttpServletRequest request = mockRequest("POST", "999998");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter json = new StringWriter();

        when(request.getParameter("id")).thenReturn("0");
        when(request.getParameter("scratchpad")).thenReturn("test note");
        when(request.getParameter("windowId")).thenReturn("window-1");
        when(response.getWriter()).thenReturn(new PrintWriter(json));
        when(scratchPadDao.findByProviderNo("999998")).thenReturn(null);
        doAnswer(invocation -> {
            ScratchPad scratchPad = invocation.getArgument(0);
            scratchPad.setId(1);
            return null;
        }).when(scratchPadDao).persist(any(ScratchPad.class));

        Scratch2Action action = createAction(request, response);

        ArgumentCaptor<ScratchPad> scratchPadCaptor = ArgumentCaptor.forClass(ScratchPad.class);

        assertThat(action.execute()).isNull();
        verify(scratchPadDao).persist(scratchPadCaptor.capture());
        assertThat(scratchPadCaptor.getValue().getProviderNo()).isEqualTo("999998");
        assertThat(scratchPadCaptor.getValue().getText()).isEqualTo("test note");
        assertThat(json.toString()).contains("\"id\":\"1\"", "\"text\":\"test note\"", "\"windowId\":\"window-1\"");
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

            assertThat(action.execute()).isNull();

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
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(providerNo);
        return request;
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
