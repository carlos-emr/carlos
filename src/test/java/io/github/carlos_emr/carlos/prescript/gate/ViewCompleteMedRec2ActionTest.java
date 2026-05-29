/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
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
import org.mockito.ArgumentCaptor;
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
@DisplayName("ViewCompleteMedRec2Action")
class ViewCompleteMedRec2ActionTest {

    @Test
    @DisplayName("should persist MedRec measurement when demographic matches session patient")
    void shouldPersistMedRecMeasurement_whenDemographicMatchesSessionPatient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rx/ViewCompleteMedRec");
        request.addParameter("demographicNo", "456");
        setSessionPatient(request, 456);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        MeasurementDao measurementDao = mock(MeasurementDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);
            springUtils.when(() -> SpringUtils.getBean(MeasurementDao.class)).thenReturn(measurementDao);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999");
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_measurement"), eq("w"), isNull()))
                    .thenReturn(true);
            ViewCompleteMedRec2Action action = new ViewCompleteMedRec2Action();
            ArgumentCaptor<Measurement> measurement = ArgumentCaptor.forClass(Measurement.class);

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(measurementDao).persist(measurement.capture());
            assertThat(measurement.getValue().getType()).isEqualTo("medr");
            assertThat(measurement.getValue().getProviderNo()).isEqualTo("999");
            assertThat(measurement.getValue().getDataField()).isEqualTo("Yes");
            assertThat(measurement.getValue().getDemographicId()).isEqualTo(456);
            assertThat(measurement.getValue().getMeasuringInstruction()).isEmpty();
            assertThat(measurement.getValue().getComments()).isEmpty();
            assertThat(measurement.getValue().getAppointmentNo()).isZero();
            assertThat(measurement.getValue().getDateObserved()).isNotNull();
        }
    }

    @Test
    @DisplayName("should reject MedRec measurement when demographic does not match session patient")
    void shouldRejectMedRecMeasurement_whenDemographicDoesNotMatchSessionPatient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rx/ViewCompleteMedRec");
        request.addParameter("demographicNo", "456");
        setSessionPatient(request, 789);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        MeasurementDao measurementDao = mock(MeasurementDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);
            springUtils.when(() -> SpringUtils.getBean(MeasurementDao.class)).thenReturn(measurementDao);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_measurement"), eq("w"), isNull()))
                    .thenReturn(true);

            ViewCompleteMedRec2Action action = new ViewCompleteMedRec2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            verify(measurementDao, never()).persist(any());
        }
    }

    private static void setSessionPatient(MockHttpServletRequest request, int demographicNo) {
        RxSessionBean rxSessionBean = new RxSessionBean();
        rxSessionBean.setDemographicNo(demographicNo);
        request.getSession().setAttribute("RxSessionBean", rxSessionBean);
    }
}
