/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.stream.Stream;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Validation and authorization must precede even the graph's patient-name lookup. */
@Tag("unit")
@Tag("measurement")
class GraphMeasurementRequestIsolationUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private MeasurementDao measurements;
    private MeasurementsExtDao extensions;
    private LoggedInInfo login;
    private EctSessionBean global;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        extensions = createAndRegisterMock(MeasurementsExtDao.class);
        login = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/carlos/encounter/GraphMeasurements");
        request.setContextPath("/carlos");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        global = new EctSessionBean();
        global.demographicNo = "202";
        request.getSession().setAttribute("EctSessionBean", global);
        request.getSession().setAttribute("eChartID", "202");
        request.setParameter("type", "WT");
        response = new MockHttpServletResponse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPatients")
    void shouldReturn400BeforeAuthorizationOrAnyClinicalLookup(String description, String[] camel, String[] snake) throws Exception {
        if (camel != null) request.setParameter("demographicNo", camel);
        if (snake != null) request.setParameter("demographic_no", snake);
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var jdbc = mockStatic(LegacyJdbcQuery.class);
             var handlers = mockConstruction(EctMeasurementsDataBeanHandler.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new MeasurementGraphAction22Action().execute()).isEqualTo("none");

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            verifyNoInteractions(security, measurements, extensions);
            names.verifyNoInteractions();
            jdbc.verifyNoInteractions();
            assertThat(handlers.constructed()).isEmpty();
            assertGlobalUnchanged();
        }
    }

    static Stream<Arguments> invalidPatients() {
        return Stream.of(
                Arguments.of("missing despite session chart", null, null),
                Arguments.of("conflicting aliases", new String[]{"101"}, new String[]{"202"}),
                Arguments.of("conflicting camel-case repetitions", new String[]{"101", "202"}, null),
                Arguments.of("conflicting underscore repetitions", null, new String[]{"101", "202"}),
                Arguments.of("camel-case overflow", new String[]{"2147483648"}, null),
                Arguments.of("underscore overflow", null, new String[]{"9999999999"}),
                Arguments.of("empty camel-case ID", new String[]{""}, null),
                Arguments.of("nonnumeric underscore ID", null, new String[]{"abc"}),
                Arguments.of("negative ID", new String[]{"-1"}, null),
                Arguments.of("valid camel-case with invalid alias", new String[]{"101"}, new String[]{"abc"}),
                Arguments.of("valid underscore with invalid alias", new String[]{"0"}, new String[]{"101"}),
                Arguments.of("malformed repeated ID", new String[]{"101", "bad"}, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographicNo", "demographic_no"})
    void shouldAuthorizeCanonicalRequestedPatientBeforeAnyClinicalLookupDespiteSessionPatientPrivilege(String alias) {
        request.setParameter(alias, "00101", "101");
        request.setParameter(alias.equals("demographicNo") ? "demographic_no" : "demographicNo", "101");
        when(security.hasPrivilege(login, "_measurement", "r", "202")).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var names = mockStatic(DemographicNameAgeString.class);
             var jdbc = mockStatic(LegacyJdbcQuery.class);
             var handlers = mockConstruction(EctMeasurementsDataBeanHandler.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);

            assertThatThrownBy(() -> new MeasurementGraphAction22Action().execute())
                    .isInstanceOf(SecurityException.class).hasMessageContaining("_measurement");

            verify(security).hasPrivilege(login, "_measurement", "r", "101");
            verifyNoMoreInteractions(security);
            verifyNoInteractions(measurements, extensions);
            names.verifyNoInteractions();
            jdbc.verifyNoInteractions();
            assertThat(handlers.constructed()).isEmpty();
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertGlobalUnchanged();
        }
    }

    private void assertGlobalUnchanged() {
        assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo("202");
        assertThat(request.getSession().getAttribute("eChartID")).isEqualTo("202");
    }
}
