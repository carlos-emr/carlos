/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasuringInstructionBeanHandler;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("measurement")
class MeasurementRequestIsolationUnitTest extends CarlosUnitTestBase {
    enum Read { HISTORY, INDEX, SETUP }
    private SecurityInfoManager security;
    private MeasurementDao measurements;
    private MeasurementsDeletedDao deleted;
    private LoggedInInfo login;
    private MockHttpSession session;
    private EctSessionBean global;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        deleted = createAndRegisterMock(MeasurementsDeletedDao.class);
        login = mock(LoggedInInfo.class);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        global = new EctSessionBean();
        global.demographicNo = "202";
        session.setAttribute("EctSessionBean", global);
        session.setAttribute("eChartID", "202");
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void shouldPassOnlyExplicitPatientToInterleavedReads(Read endpoint) throws Exception {
        when(security.hasPrivilege(eq(login), eq("_measurement"), eq("r"), anyString())).thenReturn(true);
        Object sessionTypes = new Object();
        Object sessionInstructions = new Object();
        session.setAttribute("measurementTypes", sessionTypes);
        session.setAttribute("mInstrcs0", sessionInstructions);
        List<List<?>> arguments = new ArrayList<>();
        EctMeasuringInstructionBeanHandler instruction = mock(EctMeasuringInstructionBeanHandler.class);
        try (var history = mockConstruction(EctMeasurementsDataBeanHandler.class,
                     (mock, context) -> arguments.add(context.arguments()));
             var types = mockConstruction(EctMeasurementTypesBeanHandler.class, (mock, context) -> {
                 arguments.add(context.arguments());
                 when(mock.getMeasurementTypeVector()).thenReturn(new Vector<>());
                 when(mock.getMeasuringInstrcHdVector()).thenReturn(new Vector<>(List.of(instruction)));
             });
             var validation = mockConstruction(EctValidation.class)) {
            MockHttpServletRequest first = request("101");
            assertThat(read(endpoint, first, new MockHttpServletResponse())).isEqualTo("continue");
            MockHttpServletRequest second = request("202");
            assertThat(read(endpoint, second, new MockHttpServletResponse())).isEqualTo("continue");
            MockHttpServletRequest refresh = request("101");
            assertThat(read(endpoint, refresh, new MockHttpServletResponse())).isEqualTo("continue");
            int index = endpoint == Read.SETUP ? 1 : 0;
            assertThat(arguments).hasSize(3);
            assertThat(arguments.stream().map(args -> String.valueOf(args.get(index))))
                    .containsExactly("101", "202", "101");
            assertThat(String.valueOf(first.getAttribute("demographicNo"))).isEqualTo("101");
            assertThat(String.valueOf(second.getAttribute("demographicNo"))).isEqualTo("202");
            if (endpoint == Read.SETUP) {
                assertThat(first.getAttribute("measurementTypes")).isSameAs(types.constructed().get(0));
                assertThat(second.getAttribute("measurementTypes")).isSameAs(types.constructed().get(1));
                assertThat(first.getAttribute("mInstrcs0")).isSameAs(instruction);
                assertThat(session.getAttribute("measurementTypes")).isSameAs(sessionTypes);
                assertThat(session.getAttribute("mInstrcs0")).isSameAs(sessionInstructions);
            }
            verify(security, times(2)).hasPrivilege(login, "_measurement", "r", "101");
            verify(security).hasPrivilege(login, "_measurement", "r", "202");
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void shouldRejectMissingConflictingAndInvalidPatientsBeforeDataAccess(Read endpoint) throws Exception {
        try (var history = mockConstruction(EctMeasurementsDataBeanHandler.class);
             var types = mockConstruction(EctMeasurementTypesBeanHandler.class);
             var validation = mockConstruction(EctValidation.class)) {
            for (String kind : List.of("missing", "aliases", "duplicates", "overflow", "blank")) {
                MockHttpServletRequest request = invalidRequest(kind);
                MockHttpServletResponse response = new MockHttpServletResponse();
                assertThat(read(endpoint, request, response)).as(kind).isEqualTo("none");
                assertThat(response.getStatus()).as(kind).isEqualTo(400);
            }
            assertThat(history.constructed()).isEmpty();
            assertThat(types.constructed()).isEmpty();
            assertThat(validation.constructed()).isEmpty();
            verifyNoInteractions(security, measurements, deleted);
            assertGlobalUnchanged();
        }
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void shouldAuthorizeRequestedPatientBeforeConstructingDataHandlers(Read endpoint) {
        when(security.hasPrivilege(login, "_measurement", "r", "202")).thenReturn(true);
        try (var history = mockConstruction(EctMeasurementsDataBeanHandler.class);
             var types = mockConstruction(EctMeasurementTypesBeanHandler.class);
             var validation = mockConstruction(EctValidation.class)) {
            assertThatThrownBy(() -> read(endpoint, request("101"), new MockHttpServletResponse()))
                    .isInstanceOf(SecurityException.class);
            verify(security).hasPrivilege(login, "_measurement", "r", "101");
            assertThat(history.constructed()).isEmpty();
            assertThat(types.constructed()).isEmpty();
            assertThat(validation.constructed()).isEmpty();
            verifyNoInteractions(measurements, deleted);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "aliases", "duplicates", "overflow", "blank"})
    void shouldRejectInvalidHistoryDeleteIdentityBeforeLookup(String kind) throws Exception {
        allowDelete();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(delete(invalidRequest(kind), response, "WT", "11")).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(measurements, deleted);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectWholeForeignBatchEvenWhenBothPatientsAreAuthorized(boolean localFirst) throws Exception {
        allowDelete();
        when(measurements.find(Integer.valueOf(11))).thenReturn(measurement(11, 101));
        when(measurements.find(Integer.valueOf(22))).thenReturn(measurement(22, 202));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] ids = localFirst ? new String[]{"11", "22"} : new String[]{"22", "11"};
        assertThat(delete(request("101"), response, "WT", ids)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verify(measurements, never()).remove(anyInt());
        verifyNoInteractions(deleted);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void shouldDeleteOnlyRequestedPatientAndCarryCanonicalIdentityIntoHistoryRedirect() throws Exception {
        allowDelete();
        when(measurements.find(Integer.valueOf(11))).thenReturn(measurement(11, 101));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(delete(request("000101"), response, "BP & HR", "11")).isEqualTo("none");
        verify(measurements).remove(11);
        ArgumentCaptor<MeasurementsDeleted> tombstone = ArgumentCaptor.forClass(MeasurementsDeleted.class);
        verify(deleted).persist(tombstone.capture());
        assertThat(tombstone.getValue().getDemographicNo()).isEqualTo(101);
        assertThat(tombstone.getValue().getOriginalId()).isEqualTo(11);
        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/encounter/oscarMeasurements/SetupDisplayHistory?type=BP+%26+HR&demographicNo=101");
        assertGlobalUnchanged();
    }

    @Test
    void shouldRetainRowsAndTombstonesWhenRequestedPatientIsDenied() {
        when(security.hasPrivilege(login, "_measurement", "d", null)).thenReturn(true);
        when(measurements.find(Integer.valueOf(11))).thenReturn(measurement(11, 101));
        assertThatThrownBy(() -> delete(request("101"), new MockHttpServletResponse(), "WT", "11"))
                .isInstanceOf(SecurityException.class);
        verify(security).hasPrivilege(login, "_measurement", "d", "101");
        verify(measurements, never()).remove(anyInt());
        verifyNoInteractions(deleted);
    }

    @Test
    void shouldKeepLegacyDeleteWithoutHistoryPatientProtectedByOwningPatientAuthorization() throws Exception {
        allowDelete();
        when(measurements.find(Integer.valueOf(11))).thenReturn(measurement(11, 101));
        assertThat(delete(request(null), new MockHttpServletResponse(), null, "11")).isEqualTo("success");
        verify(security).hasPrivilege(login, "_measurement", "d", "101");
        verify(security).isAllowedAccessToPatientRecord(login, 101);
        verify(measurements).remove(11);
    }

    private void allowDelete() {
        when(security.hasPrivilege(eq(login), eq("_measurement"), eq("d"), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
    }

    private Measurement measurement(int id, int patient) {
        Measurement row = new Measurement();
        org.springframework.test.util.ReflectionTestUtils.setField(row, "id", id);
        row.setDemographicId(patient);
        row.setType("WT");
        row.setDataField("70");
        return row;
    }

    private MockHttpServletRequest request(String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/encounter/oscarMeasurements/test");
        request.setContextPath("/carlos");
        request.setSession(session);
        request.setParameter("type", "WT");
        request.setParameter("groupName", "Vitals");
        if (patient != null) request.setParameter("demographic_no", patient);
        return request;
    }

    private MockHttpServletRequest invalidRequest(String kind) {
        MockHttpServletRequest request = request(null);
        switch (kind) {
            case "aliases" -> { request.setParameter("demographicNo", "101"); request.setParameter("demographic_no", "202"); }
            case "duplicates" -> request.setParameter("demographicNo", "101", "202");
            case "overflow" -> request.setParameter("demographicNo", "2147483648");
            case "blank" -> request.setParameter("demographicNo", "");
        }
        return request;
    }

    private String read(Read endpoint, MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            ActionSupport action = switch (endpoint) {
                case HISTORY -> new EctSetupDisplayHistory2Action();
                case INDEX -> new EctSetupHistoryIndex2Action();
                case SETUP -> new EctSetupMeasurements2Action();
            };
            return action.execute();
        }
    }

    private String delete(MockHttpServletRequest request, MockHttpServletResponse response,
                          String type, String... ids) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            EctDeleteData2Action action = new EctDeleteData2Action();
            action.setType(type);
            action.setDeleteCheckbox(ids);
            return action.execute();
        }
    }

    private void assertGlobalUnchanged() {
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
        assertThat(session.getAttribute("eChartID")).isEqualTo("202");
    }
}
