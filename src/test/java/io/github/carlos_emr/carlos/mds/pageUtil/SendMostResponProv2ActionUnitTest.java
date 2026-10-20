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
package io.github.carlos_emr.carlos.mds.pageUtil;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.DemographicManager;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Send to MRP ({@code oscarMDS/SendMRP}) routes a document or lab into the patient's most responsible
 * provider's inbox. It is a write, so only POST reaches it; every caller posts.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("lab")
@DisplayName("Send to MRP accepts only POST")
class SendMostResponProv2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private DemographicManager demographics;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedStatic<CommonLabResultData> routing;

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        demographics = createAndRegisterMock(DemographicManager.class);
        Demographic patient = new Demographic();
        patient.setProviderNo("999001");
        when(demographics.getDemographic(any(LoggedInInfo.class), anyInt())).thenReturn(patient);
        // CommonLabResultData looks these up when its class initializes.
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(mock(LoggedInInfo.class));
        routing = mockStatic(CommonLabResultData.class);
        request.setParameter("demoId", "42");
        request.setParameter("docLabId", "77");
        request.setParameter("docLabType", "DOC");
    }

    @AfterEach
    void tearDown() {
        if (routing != null) routing.close();
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should answer 405 and route nothing for a method other than POST")
    void shouldRouteNothing_whenNotPost(String method) throws Exception {
        request.setMethod(method);

        assertThat(new SendMostResponProv2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), anyString()), never());
        verify(demographics, never()).getDemographic(any(LoggedInInfo.class), anyInt());
    }

    @Test
    @DisplayName("should route the document to the patient's MRP on POST")
    @SuppressWarnings("unchecked")
    void shouldRouteToMrp_onPost() throws Exception {
        request.setMethod("POST");

        assertThat(new SendMostResponProv2Action().execute()).isEqualTo(ActionSupport.NONE);

        routing.verify(() -> CommonLabResultData.updateLabRouting(
                argThat((ArrayList<String[]> labs) -> labs.size() == 1 && "77".equals(labs.get(0)[0]) && "DOC".equals(labs.get(0)[1])),
                eq("999001")));
    }

    @Test
    @DisplayName("should answer 400 and route nothing when a parameter is missing")
    void shouldAnswerBadRequest_whenParameterMissing() throws Exception {
        request.setMethod("POST");
        request.removeParameter("docLabId");

        assertThat(new SendMostResponProv2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), anyString()), never());
    }
}
