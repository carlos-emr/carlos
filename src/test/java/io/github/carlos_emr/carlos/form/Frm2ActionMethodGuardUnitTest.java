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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legacy encounter forms ({@code form/formname}, {@link Frm2Action}): every save comes as the form page's POST, and
 * only the read-only growth graph and print-all views open by GET.
 *
 * @since 2026-10-20
 */
@DisplayName("Frm2Action saves accept only POST")
@Tag("unit")
@Tag("form")
class Frm2ActionMethodGuardUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final FrmRecord record = mock(FrmRecord.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedConstruction<FrmRecordFactory> factories;

    @BeforeEach
    void setUp() throws Exception {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("w"), isNull())).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
        factories = mockConstruction(FrmRecordFactory.class,
                (factory, context) -> when(factory.factory("Rourke2017")).thenReturn(record));
        when(record.getFormRecord(any(), anyInt(), anyInt())).thenReturn(new Properties());
        when(record.getGraph(any(LoggedInInfo.class), anyInt(), anyInt())).thenReturn(new Properties());
        request.setParameter("form_class", "Rourke2017");
        request.setParameter("demographic_no", "1373");
        request.setParameter("formId", "0");
    }

    @AfterEach
    void tearDown() {
        if (factories != null) factories.close();
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"GET,save", "HEAD,save", "GET,exit", "GET,print", "GET,autosaveAjax", "GET,saveFormLetter", "GET,''"})
    @DisplayName("should answer 405 and save nothing for a save sent other than by POST")
    void shouldSaveNothing_whenASaveIsNotPosted(String method, String submit) throws Exception {
        request.setMethod(method);
        if (!submit.isEmpty()) request.setParameter("submit", submit);

        assertThat(new Frm2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(record, never()).saveFormRecord(any());
    }

    @Test
    @DisplayName("should answer 405 and save nothing for an update sent by GET")
    void shouldSaveNothing_whenAnUpdateIsNotPosted() throws Exception {
        request.setMethod("GET");
        request.setParameter("update", "true");

        assertThat(new Frm2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        verify(record, never()).saveFormRecord(any());
    }

    @Test
    @DisplayName("should still draw the growth graph by GET, as the graph links open it")
    void shouldDrawTheGraph_whenOpenedByGet() {
        request.setMethod("GET");
        request.setParameter("submit", "graph");

        new Frm2Action().execute();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(record).getGraph(any(LoggedInInfo.class), eq(1373), eq(0));
    }

    @Test
    @DisplayName("should save the form the page posts")
    void shouldSaveTheForm_whenPosted() throws Exception {
        request.setMethod("POST");
        request.setParameter("submit", "save");

        new Frm2Action().execute();

        verify(record).saveFormRecord(any(Properties.class));
    }
}
