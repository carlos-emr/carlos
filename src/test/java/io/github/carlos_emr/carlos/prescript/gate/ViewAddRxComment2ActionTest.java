/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("ViewAddRxComment2Action")
class ViewAddRxComment2ActionTest {

    @Test
    @DisplayName("should update comment when script belongs to session patient")
    void shouldUpdateComment_whenScriptBelongsToSessionPatient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rx/ViewAddRxComment");
        request.addParameter("scriptNo", "123");
        request.addParameter("comment", "Follow up");
        setSessionPatient(request, 456);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PrescriptionDao prescriptionDao = mock(PrescriptionDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Prescription prescription = new Prescription();
        prescription.setDemographicId(456);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);
            springUtils.when(() -> SpringUtils.getBean(PrescriptionDao.class)).thenReturn(prescriptionDao);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("w"), isNull()))
                    .thenReturn(true);
            when(prescriptionDao.find(123)).thenReturn(prescription);

            ViewAddRxComment2Action action = new ViewAddRxComment2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(prescriptionDao).updatePrescriptionsByScriptNo(123, "Follow up");
        }
    }

    @Test
    @DisplayName("should reject comment when script belongs to another patient")
    void shouldRejectComment_whenScriptBelongsToAnotherPatient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rx/ViewAddRxComment");
        request.addParameter("scriptNo", "123");
        request.addParameter("comment", "Follow up");
        setSessionPatient(request, 456);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        PrescriptionDao prescriptionDao = mock(PrescriptionDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Prescription prescription = new Prescription();
        prescription.setDemographicId(789);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);
            springUtils.when(() -> SpringUtils.getBean(PrescriptionDao.class)).thenReturn(prescriptionDao);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_rx"), eq("w"), isNull()))
                    .thenReturn(true);
            when(prescriptionDao.find(123)).thenReturn(prescription);

            ViewAddRxComment2Action action = new ViewAddRxComment2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(prescriptionDao, never()).updatePrescriptionsByScriptNo(any(), any());
        }
    }

    private static void setSessionPatient(MockHttpServletRequest request, int demographicNo) {
        RxSessionBean rxSessionBean = new RxSessionBean();
        rxSessionBean.setDemographicNo(demographicNo);
        request.getSession().setAttribute("RxSessionBean", rxSessionBean);
    }
}
