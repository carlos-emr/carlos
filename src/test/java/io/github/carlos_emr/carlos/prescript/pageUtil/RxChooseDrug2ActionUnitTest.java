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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link RxChooseDrug2Action}: the results page's "drug not found" link chooses a custom drug with a
 * blank id, and that must stage a card the prescriber names next (#3908).
 */
@Tag("unit")
@Tag("prescript")
@DisplayName("RxChooseDrug2Action custom drug staging")
class RxChooseDrug2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String PROVIDER_NO = "999998";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxSessionBean bean;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/chooseDrug");
        response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        // addStashItem preloads interactions (DrugDao) and allergy warnings (RxPatientData's static
        // DemographicManager, AllergyDao); the latter's class init is an Error the bean does not catch.
        registerMock(DrugDao.class, mock(DrugDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(AllergyDao.class, mock(AllergyDao.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), eq(DEMOGRAPHIC_NO))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(true);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo(PROVIDER_NO);
        RxSessionBeanResolver.register(request.getSession(), bean);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should stage a custom card when the chooser names no drug id")
    void shouldStageCustomCard_whenDrugIdIsBlank() throws Exception {
        request.setParameter("BN", "");
        request.setParameter("drugId", "");

        assertThat(new RxChooseDrug2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashIndex()).isEqualTo(0);
        RxPrescriptionData.Prescription staged = bean.getStashItem(0);
        // A custom card has no brand and GCN "0" (prescribe.jsp keys the editable name on that);
        // Prescription normalises the empty custom name to null until the prescriber types one.
        assertThat(staged.getBrandName()).isNull();
        assertThat(staged.getGCN_SEQNO()).isEqualTo("0");
        assertThat(staged.getDemographicNo()).isEqualTo(DEMOGRAPHIC_NO);
    }

    @Test
    @DisplayName("should stage a custom card when the chooser sends no drug id parameter at all")
    void shouldStageCustomCard_whenDrugIdIsAbsent() throws Exception {
        assertThat(new RxChooseDrug2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashItem(0).getGCN_SEQNO()).isEqualTo("0");
    }

    @Test
    @DisplayName("should refuse GET before staging anything")
    void shouldRejectGet_beforeStaging() throws Exception {
        request.setMethod("GET");

        assertThat(new RxChooseDrug2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(bean.getStashSize()).isZero();
    }
}
