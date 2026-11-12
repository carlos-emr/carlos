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
package io.github.carlos_emr.carlos.report.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.ObecData;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Create Report on the OBEC page (oscarReport/obec) writes an OBEC file, so a request carrying a Begin Date comes
 * only as the page's POST form; opening the page with no date stays open to GET.
 *
 * @since 2026-10-20
 */
@DisplayName("Obec2Action request method")
@Tag("unit")
@Tag("report")
class Obec2ActionMethodGuardUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo login = mock(LoggedInInfo.class);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(login), eq("_report"), eq("r"), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    private Obec2Action action(String begin) {
        Obec2Action action = new Obec2Action();
        action.setXml_vdate(begin);
        action.setNumDays(7);
        return action;
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 and create no report when a Begin Date is not posted")
    void shouldCreateNoReport_whenBeginDateIsNotPosted(String method) throws Exception {
        request.setMethod(method);

        try (MockedConstruction<ObecData> obec = mockConstruction(ObecData.class)) {
            assertThat(action("2026-10-01").execute()).isEqualTo(ActionSupport.NONE);

            assertThat(obec.constructed()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should open the page by GET when no Begin Date is given")
    void shouldOpenPage_whenNoBeginDateIsGiven() throws Exception {
        request.setMethod("GET");

        try (MockedConstruction<ObecData> obec = mockConstruction(ObecData.class)) {
            assertThat(action(null).execute()).isEqualTo(ActionSupport.SUCCESS);
        }
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should create the report when the form posts a Begin Date")
    void shouldCreateReport_whenBeginDateIsPosted() throws Exception {
        request.setMethod("POST");

        try (MockedConstruction<ObecData> obec = mockConstruction(ObecData.class,
                (data, context) -> when(data.generateOBEC(eq("2026-10-01"), any(), any())).thenReturn("OBECE1.TXT"))) {
            assertThat(action("2026-10-01").execute()).isEqualTo(ActionSupport.SUCCESS);

            assertThat(obec.constructed()).hasSize(1);
        }
        assertThat(request.getAttribute("obectxt")).isEqualTo("OBECE1.TXT");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
