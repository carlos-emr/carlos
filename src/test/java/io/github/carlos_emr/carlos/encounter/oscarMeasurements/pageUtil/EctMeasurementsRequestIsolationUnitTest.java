/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDaoImpl.SearchCriteria;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class EctMeasurementsRequestIsolationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private MeasurementDao measurements;
    private MeasurementTypeDao types;
    private LoggedInInfo login;
    private MockHttpSession session;
    private EctSessionBean global;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        types = createAndRegisterMock(MeasurementTypeDao.class);
        login = mock(LoggedInInfo.class);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        global = new EctSessionBean();
        global.demographicNo = "202";
        session.setAttribute("EctSessionBean", global);
        when(security.hasPrivilege(login, "_measurement", "w", null)).thenReturn(true);
    }

    @Test
    void shouldScopeDuplicateChecksWritesAndAjaxIdentityToEachExplicitPatient() throws Exception {
        when(security.hasPrivilege(eq(login), eq("_measurement"), eq("w"), anyString())).thenReturn(true);
        for (String patient : new String[]{"000101", "202", "101"}) {
            MockHttpServletRequest request = request();
            request.setParameter("demographic_no", patient);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(submit(request, response)).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).contains("\"demographicNo\":\"" + Integer.parseInt(patient) + "\"");
        }
        ArgumentCaptor<SearchCriteria> searches = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(measurements, times(3)).find(searches.capture());
        assertThat(searches.getAllValues()).extracting(SearchCriteria::getDemographicNo).containsExactly(101, 202, 101);
        ArgumentCaptor<Measurement> writes = ArgumentCaptor.forClass(Measurement.class);
        verify(measurements, times(3)).persist(writes.capture());
        assertThat(writes.getAllValues()).extracting(Measurement::getDemographicId).containsExactly(101, 202, 101);
        assertThat(writes.getAllValues()).extracting(Measurement::getDataField).containsExactly("70", "70", "70");
        verify(security, times(2)).hasPrivilege(login, "_measurement", "w", "101");
        verify(security).hasPrivilege(login, "_measurement", "w", "202");
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "aliases", "duplicates", "overflow", "blank"})
    void shouldReturn400BeforeValidationOrAnyMeasurementAccess(String kind) throws Exception {
        MockHttpServletRequest request = request();
        switch (kind) {
            case "aliases" -> { request.setParameter("demographicNo", "101"); request.setParameter("demographic_no", "202"); }
            case "duplicates" -> request.setParameter("demographicNo", "101", "202");
            case "overflow" -> request.setParameter("demographicNo", "2147483648");
            case "blank" -> request.setParameter("demographicNo", "");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(submit(request, response)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(types, measurements);
        verify(security).hasPrivilege(login, "_measurement", "w", null);
    }

    @Test
    void shouldDenyExplicitPatientBeforeValidationAndWriteDespiteSessionPatientPrivilege() {
        when(security.hasPrivilege(login, "_measurement", "w", "202")).thenReturn(true);
        MockHttpServletRequest request = request();
        request.setParameter("demographicNo", "101");
        assertThatThrownBy(() -> submit(request, new MockHttpServletResponse())).isInstanceOf(SecurityException.class);
        verify(security).hasPrivilege(login, "_measurement", "w", "101");
        verifyNoInteractions(types, measurements);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/Measurements");
        request.setSession(session);
        request.setParameter("numType", "1");
        request.setParameter("ajax", "true");
        request.setParameter("skipCreateNote", "true");
        request.setParameter("inputType-0", "WT");
        request.setParameter("inputTypeDisplayName-0", "Weight");
        request.setParameter("inputValue-0", "70");
        request.setParameter("inputMInstrc-0", "kg");
        request.setParameter("comments-0", "");
        request.setParameter("date-0", "2026-10-15");
        return request;
    }

    private String submit(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new EctMeasurements2Action().execute();
        }
    }
}
