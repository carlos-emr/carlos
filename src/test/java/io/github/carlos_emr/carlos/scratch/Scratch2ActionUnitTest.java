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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        TestableScratch2Action action = new TestableScratch2Action(request, response);

        assertThat(action.execute()).isEqualTo("success");
        verifyNoInteractions(scratchPadDao);
    }

    @Test
    @DisplayName("should save scratchpad for session user when providerNo request parameter is absent")
    void shouldSaveScratchpadForSessionUser_whenProviderNoRequestParameterIsAbsent() throws Exception {
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

        TestableScratch2Action action = new TestableScratch2Action(request, response);

        assertThat(action.execute()).isNull();
        verify(scratchPadDao).persist(any(ScratchPad.class));
        assertThat(json.toString()).contains("\"id\":\"1\"", "\"text\":\"test note\"", "\"windowId\":\"window-1\"");
    }

    @Test
    @DisplayName("should post scratchpad form without providerNo parameter")
    void shouldPostScratchpadForm_withoutProviderNoParameter() throws Exception {
        String jsp = Files.readString(
            Path.of("src/main/webapp/WEB-INF/jsp/scratch/index.jsp"),
            StandardCharsets.UTF_8
        );

        assertThat(jsp).contains("id=\"scratch\"", "method=\"post\"", "/Scratch");
        assertThat(jsp).doesNotContain("name=\"providerNo\"");
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

    private HttpServletRequest mockRequest(String httpMethod, String providerNo) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getMethod()).thenReturn(httpMethod);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("user")).thenReturn(providerNo);
        return request;
    }

    private static class TestableScratch2Action extends Scratch2Action {

        TestableScratch2Action(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }
    }
}
