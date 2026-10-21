/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.measurements.web;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class MeasurementDataPostGuardUnitTest extends CarlosWebTestBase {
    private final MeasurementDao measurements = mock(MeasurementDao.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);

    private void withAction(ThrowingConsumer work) throws Exception {
        Object previous = ReflectionTestUtils.getField(MeasurementData2Action.class, "measurementDao");
        replaceSpringUtilsBean(MeasurementDao.class, measurements);
        replaceSpringUtilsBean(OscarAppointmentDao.class, appointments);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        MeasurementData2Action action = new MeasurementData2Action();
        ReflectionTestUtils.setField(MeasurementData2Action.class, "measurementDao", measurements);
        try { work.run(action); }
        finally { ReflectionTestUtils.setField(MeasurementData2Action.class, "measurementDao", previous); }
    }
    @FunctionalInterface private interface ThrowingConsumer { void run(MeasurementData2Action action) throws Exception; }

    @ParameterizedTest
    @CsvSource({"GET,saveMeasurement", "HEAD,saveMeasurement", "PUT,saveMeasurement", "DELETE,saveMeasurement", "GET,saveValues", "HEAD,saveValues"})
    void shouldRefuseWritesAfterAuthorizationBeforeParsingOrDaoAccess(String method, String operation) throws Exception {
        mockRequest.setMethod(method); addRequestParameter("action", operation);
        addRequestParameter("demographicNo", "invalid"); addRequestParameter("appointmentNo", "invalid");
        addRequestParameter("dateObserved", "invalid"); addRequestParameter("type", "WT"); addRequestParameter("value", "5.5");
        withAction(action -> {
            assertThat(action.execute()).isEqualTo("none");
            assertThat(mockResponse.getStatus()).isEqualTo(405); assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
            verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("invalid"));
            verifyNoInteractions(measurements, appointments);
        });
    }

    @ParameterizedTest
    @CsvSource({"GET,saveMeasurement", "POST,saveMeasurement", "GET,saveValues", "POST,saveValues"})
    void shouldRetainAuthorizationFailureBeforeMethodAndParsing(String method, String operation) throws Exception {
        mockRequest.setMethod(method); addRequestParameter("action", operation); addRequestParameter("demographicNo", "123");
        addRequestParameter("appointmentNo", "invalid");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("w"), eq("123"))).thenReturn(false);
        withAction(action -> {
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            assertThat(mockResponse.getHeader("Allow")).isNull(); verifyNoInteractions(measurements, appointments);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseWellFormedSingleMeasurementWithoutWriting(String method) throws Exception {
        mockRequest.setMethod(method); addRequestParameter("action", "saveMeasurement"); addRequestParameter("demographicNo", "123");
        addRequestParameter("appointmentNo", "17"); addRequestParameter("dateObserved", "2026-09-30");
        addRequestParameter("type", "WT"); addRequestParameter("value", "5.5");
        withAction(action -> {
            assertThat(action.execute()).isEqualTo("none"); assertThat(mockResponse.getStatus()).isEqualTo(405);
            verifyNoInteractions(measurements, appointments);
        });
    }

    @Test
    void shouldPreserveNativeSingleMeasurementPostAndJsonAcceptance() throws Exception {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        mockRequest.setMethod("POST"); addRequestParameter("action", "saveMeasurement"); addRequestParameter("demographicNo", "123");
        addRequestParameter("appointmentNo", "17"); addRequestParameter("dateObserved", "2026-09-30");
        addRequestParameter("type", "WT"); addRequestParameter("value", "5.5"); addRequestParameter("instruction", "kg");
        withAction(action -> {
            assertThat(action.execute()).isNull(); assertThat(mockResponse.getStatus()).isEqualTo(200);
            assertThat(mockResponse.getContentAsString()).contains("\"success\":true");
            ArgumentCaptor<Measurement> saved = ArgumentCaptor.forClass(Measurement.class); verify(measurements).persist(saved.capture());
            assertThat(saved.getValue().getDemographicId()).isEqualTo(123); assertThat(saved.getValue().getAppointmentNo()).isEqualTo(17);
            assertThat(saved.getValue().getDataField()).isEqualTo("5.5"); assertThat(saved.getValue().getMeasuringInstruction()).isEqualTo("kg");
            assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
            verifyNoMoreInteractions(measurements);
        });
    }

    @Test
    void shouldPreserveNativeBatchPost() throws Exception {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        mockRequest.setMethod("POST"); addRequestParameter("action", "saveValues"); addRequestParameter("demographicNo", "123");
        addRequestParameter("appointmentNo", "17"); addRequestParameter("WT", "5.5");
        withAction(action -> {
            assertThat(action.execute()).isNull();
            ArgumentCaptor<Measurement> saved = ArgumentCaptor.forClass(Measurement.class); verify(measurements).persist(saved.capture());
            assertThat(saved.getValue().getType()).isEqualTo("WT"); assertThat(saved.getValue().getDataField()).isEqualTo("5.5");
            verifyNoMoreInteractions(measurements);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldPreserveReadOnlyMeasurementRequests(String method) throws Exception {
        mockRequest.setMethod(method); addRequestParameter("action", "getMeasurementsByType");
        addRequestParameter("demographicNo", "123"); addRequestParameter("measurementType", "WT");
        withAction(action -> { action.execute(); assertThat(mockResponse.getStatus()).isEqualTo(200); });
        verify(measurements).findByType(123, "WT"); verify(measurements, never()).persist(any());
    }
}
