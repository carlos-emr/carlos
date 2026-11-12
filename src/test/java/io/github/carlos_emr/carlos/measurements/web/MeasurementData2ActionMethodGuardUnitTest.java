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
package io.github.carlos_emr.carlos.measurements.web;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Measurement data ({@code encounter/MeasurementData}): its two saves come only as the measurement form's POST, and
 * its reads stay open to GET.
 *
 * @since 2026-10-20
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("Measurement data saves accept only POST")
class MeasurementData2ActionMethodGuardUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MeasurementDao measurements = mock(MeasurementDao.class);
    private Object previousMeasurementDao;
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        registerMock(MeasurementDao.class, measurements);
        registerMock(OscarAppointmentDao.class, mock(OscarAppointmentDao.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
        // The DAO is held in a static field bound once per JVM; point it at this test's mock and put it back after.
        previousMeasurementDao = ReflectionTestUtils.getField(MeasurementData2Action.class, "measurementDao");
        ReflectionTestUtils.setField(MeasurementData2Action.class, "measurementDao", measurements);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MeasurementData2Action.class, "measurementDao", previousMeasurementDao);
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    private void measurement(String action) {
        request.setParameter("action", action);
        request.setParameter("demographicNo", "1373");
        request.setParameter("appointmentNo", "0");
        request.setParameter("type", "WT");
        request.setParameter("value", "70");
        request.setParameter("dateObserved", "2026-10-20");
    }

    @ParameterizedTest
    @CsvSource({"saveMeasurement,GET", "saveMeasurement,HEAD", "saveValues,GET", "saveValues,HEAD"})
    @DisplayName("should answer 405 and save nothing for a save sent other than by POST")
    void shouldSaveNothing_whenASaveIsNotPosted(String action, String method) throws Exception {
        request.setMethod(method);
        measurement(action);

        assertThat(new MeasurementData2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(measurements, security);
    }

    @Test
    @DisplayName("should save the measurement the form posts")
    void shouldSaveMeasurement_whenPosted() throws Exception {
        request.setMethod("POST");
        measurement("saveMeasurement");
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("1373"))).thenReturn(true);

        new MeasurementData2Action().execute();

        verify(measurements).persist(any(Measurement.class));
    }

    @Test
    @DisplayName("should let a read through by GET to its own rights check")
    void shouldReachTheReadsRightsCheck_whenReadByGet() {
        request.setMethod("GET");
        request.setParameter("action", "getLatestValues");
        request.setParameter("demographicNo", "1373");

        assertThatThrownBy(() -> new MeasurementData2Action().execute()).isInstanceOf(SecurityException.class);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(security).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq("1373"));
    }
}
