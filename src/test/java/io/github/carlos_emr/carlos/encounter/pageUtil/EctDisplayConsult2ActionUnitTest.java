/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.DateUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.apache.struts2.ServletActionContext;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Consultation chart panel")
class EctDisplayConsult2ActionUnitTest {
    @Test
    @DisplayName("should retain every consultation and overdue warnings when a status is null")
    void shouldRetainConsultationsAndWarnings_whenStatusIsNull() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(security.hasPrivilege(loggedInInfo, "_con", "r", null)).thenReturn(true);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean(UserPropertyDAO.class)).thenReturn(mock(UserPropertyDAO.class));
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", loggedInInfo);
        request.setContextPath("/carlos");
        EctSessionBean session = new EctSessionBean();
        session.providerNo = "999998";
        session.demographicNo = "1";
        session.appointmentNo = "11";

        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<EctViewConsultationRequestsUtil> requests = mockConstruction(
                     EctViewConsultationRequestsUtil.class, (rows, construction) -> {
                         rows.ids = List.of("101", "102", "103");
                         rows.service = List.of("Missing status", "Completed", "Pending");
                         rows.vSpecialist = List.of("N/A", "N/A", "N/A");
                         rows.date = List.of("2000-01-01", "2000-01-01", "2000-01-01");
                         rows.status = Arrays.asList(null, "4", "1");
                     })) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            EctDisplayConsult2Action action = spy(new EctDisplayConsult2Action());
            doReturn("Consultations").when(action).getText(anyString());
            NavBarDisplayDAO panel = new NavBarDisplayDAO();

            assertThat(action.getInfo(session, request, panel)).isTrue();

            assertThat(panel.numItems()).isEqualTo(3);
            assertThat(panel.getItem(0).getTitle()).isEqualTo("Pending");
            assertThat(panel.getItem(0).getColour()).isEqualTo("red");
            assertThat(panel.getItem(1).getTitle()).isEqualTo("Completed");
            assertThat(panel.getItem(1).getColour()).isNotEqualTo("red");
            assertThat(panel.getItem(2).getTitle()).isEqualTo("Missing status");
            assertThat(panel.getItem(2).getColour()).isEqualTo("red");
            assertThat(panel.getItem(2).getURL()).contains("requestId=101");
            verify(requests.constructed().getFirst()).estConsultationVecByDemographic(loggedInInfo, "1");
        }
    }

    @ParameterizedTest(name = "[{index}] service=''{0}'' specialist=''{1}'' -> ''{2}''")
    @CsvSource(value = {
            "Cardiology|Smith, John|Cardiology - Smith, John",
            "Cardiology|N/A|Cardiology",
            "Cardiology|'  N/A  '|Cardiology",
            "Cardiology|''|Cardiology",
            "Cardiology|'   '|Cardiology",
            "Cardiology|NULL|Cardiology",
            "''|Smith, John|Smith, John",
            "NULL|'  Smith, John  '|Smith, John",
            "'  Cardiology  '|'  Smith, John '|Cardiology - Smith, John",
            "''|N/A|''",
            "NULL|NULL|''"
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("should combine service and specialist without stray separators")
    void shouldBuildReferralLabel_forServiceAndSpecialistCombinations(String service, String specialist, String expected) {
        assertThat(EctDisplayConsult2Action.buildReferralLabel(service, specialist)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] label=''{0}'' date=''{1}'' -> ''{2}''")
    @CsvSource(value = {
            "Cardiology - Smith, John|2026-01-05|Cardiology - Smith, John 2026-01-05",
            "''|2026-01-05|2026-01-05",
            "NULL|2026-01-05|2026-01-05",
            "Cardiology|''|Cardiology",
            "Cardiology|NULL|Cardiology",
            "''|''|''"
    }, delimiter = '|', nullValues = "NULL")
    @DisplayName("should append the date to the hover text without stray spaces")
    void shouldBuildLinkTitle_forLabelAndDateCombinations(String label, String date, String expected) {
        assertThat(EctDisplayConsult2Action.buildLinkTitle(label, date)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should show the specialist, encode the visible title and keep the hover text raw for the JSP encoder")
    void shouldRenderSpecialistLabel_withEncodedTruncatedTitle() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(security.hasPrivilege(loggedInInfo, "_con", "r", null)).thenReturn(true);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean(UserPropertyDAO.class)).thenReturn(mock(UserPropertyDAO.class));
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", loggedInInfo);
        request.setContextPath("/carlos");
        EctSessionBean session = new EctSessionBean();
        session.providerNo = "999998";
        session.demographicNo = "1";
        session.appointmentNo = "11";
        String longService = "Otolaryngology Head and Neck Surgery Clinic";

        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedConstruction<EctViewConsultationRequestsUtil> requests = mockConstruction(
                     EctViewConsultationRequestsUtil.class, (rows, construction) -> {
                         rows.ids = List.of("201", "202");
                         rows.service = List.of(longService, "Cardiology");
                         rows.vSpecialist = List.of("FAKE-Smith, Anne", "<b>FAKE-O'Neil</b>");
                         rows.date = List.of("2099-01-01", "2098-02-02");
                         rows.status = List.of("1", "1");
                     })) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            EctDisplayConsult2Action action = spy(new EctDisplayConsult2Action());
            doReturn("Consultations").when(action).getText(anyString());
            NavBarDisplayDAO panel = new NavBarDisplayDAO();

            assertThat(action.getInfo(session, request, panel)).isTrue();

            // Distinct row dates, so each hover title must carry its own row's date.
            String cardiologyDate = DateUtils.formatDate(dateOf(2098, 2, 2), request.getLocale());
            String longServiceDate = DateUtils.formatDate(dateOf(2099, 1, 1), request.getLocale());

            NavBarDisplayDAO.Item encoded = panel.getItem(0);
            assertThat(encoded.getTitle()).isEqualTo("Cardiology - &lt;b&gt;FAKE-O'Neil&lt;/b&gt;");
            assertThat(encoded.getLinkTitle()).isEqualTo("Cardiology - <b>FAKE-O'Neil</b> " + cardiologyDate);

            NavBarDisplayDAO.Item truncated = panel.getItem(1);
            String fullLabel = longService + " - FAKE-Smith, Anne";
            assertThat(truncated.getTitle()).isEqualTo(fullLabel.substring(0, 45) + "...");
            assertThat(truncated.getLinkTitle()).isEqualTo(fullLabel + " " + longServiceDate);
        }
    }

    private static Date dateOf(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
