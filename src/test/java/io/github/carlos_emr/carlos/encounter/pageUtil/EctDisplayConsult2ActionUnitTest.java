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

import java.util.Arrays;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
}
