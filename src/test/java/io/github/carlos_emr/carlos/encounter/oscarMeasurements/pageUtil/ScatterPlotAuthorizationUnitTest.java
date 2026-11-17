/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises the servlet gate before any clinical lookup, including real privilege overrides. */
@Tag("unit")
@Tag("security")
class ScatterPlotAuthorizationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private MeasurementDao measurements;
    private MeasurementTypeDao types;
    private ValidationsDao validations;
    private LoggedInInfo login;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        types = createAndRegisterMock(MeasurementTypeDao.class);
        validations = createAndRegisterMock(ValidationsDao.class);
        login = mock(LoggedInInfo.class);
        session = new MockHttpSession();
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "2147483648", "0", "-123"})
    void shouldRejectInvalidPatientBeforeClinicalLookup(String patient) throws Exception {
        MockHttpServletResponse response = service(patient, "WT");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(security, measurements, types, validations);
    }

    @Test
    void shouldReturn401ForMissingLoginBeforeSpringLookup() throws Exception {
        session = new MockHttpSession();
        springUtilsMock.clearInvocations();
        assertThat(service("123", "WT").getStatus()).isEqualTo(401);
        springUtilsMock.verifyNoInteractions();
        verifyNoInteractions(security, measurements, types, validations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"measurement", "demographic", "chartLock"})
    void shouldDenyUnauthorizedPatientBeforeClinicalLookup(String denied) throws Exception {
        when(security.hasPrivilege(login, "_measurement", "r", "123"))
                .thenReturn(!denied.equals("measurement"));
        if (!denied.equals("measurement")) {
            when(security.hasPrivilege(login, "_demographic", "r", "123"))
                    .thenReturn(!denied.equals("demographic"));
        }
        assertThat(service("00123", "WT").getStatus()).isEqualTo(403);
        verify(security).hasPrivilege(login, "_measurement", "r", "123");
        verifyNoInteractions(measurements, types, validations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BP", "WT"})
    void shouldRenderJpegForAuthorizedCanonicalPatient(String type) throws Exception {
        when(security.hasPrivilege(login, "_measurement", "r", "123")).thenReturn(true);
        when(security.hasPrivilege(login, "_demographic", "r", "123")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(true);
        Date observed = new Date(1700000000000L);
        Measurement reading = new Measurement();
        reading.setDateObserved(observed);
        reading.setDataField(type.equals("BP") ? "120/80" : "72");
        when(measurements.findByDemographicNoTypeAndDate(123, type, observed)).thenReturn(reading);
        if (type.equals("BP")) {
            when(measurements.findByDemographicNoTypeAndMeasuringInstruction(123, type, "FAKE"))
                    .thenReturn(List.of(observed));
        } else {
            MeasurementType definition = new MeasurementType();
            definition.setValidation("1");
            when(types.findByTypeAndMeasuringInstruction(type, "FAKE")).thenReturn(List.of(definition));
            Validations numeric = new Validations();
            numeric.setNumeric(true);
            when(validations.find(1)).thenReturn(numeric);
            when(measurements.findObservationDatesByDemographicNoTypeAndMeasuringInstruction(123, type, "FAKE"))
                    .thenReturn(List.of(observed));
        }
        MockHttpServletResponse response = service("00123", type);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()))).isNotNull();
        verify(security).hasPrivilege(login, "_demographic", "r", "123");
        verify(security).isAllowedAccessToPatientRecord(login, 123);
        verify(measurements).findByDemographicNoTypeAndDate(123, type, observed);
    }

    @Test
    void shouldHonorRealPatientSpecificDenialDespiteGlobalReadAndLeadingZeros() throws Exception {
        SecuserroleDao roles = mock(SecuserroleDao.class);
        Secuserrole doctor = new Secuserrole();
        doctor.setRoleName("doctor");
        when(login.getLoggedInProviderNo()).thenReturn("FAKE-provider");
        when(login.getSession()).thenReturn(session);
        when(roles.findActiveByProviderNo("FAKE-provider")).thenReturn(List.of(doctor));
        SecObjPrivilegeDao privileges = createAndRegisterMock(SecObjPrivilegeDao.class);
        when(privileges.findByObjectNames(any())).thenAnswer(call -> {
            Collection<String> names = call.getArgument(0);
            for (String name : names) {
                if (name.equals("_measurement$123")) {
                    return List.of(privilege(name, "o"));
                }
                if (name.equals("_measurement") || name.equals("_demographic")) {
                    return List.of(privilege(name, "r"));
                }
            }
            return List.of();
        });
        SecurityInfoManagerImpl real = new SecurityInfoManagerImpl();
        injectDependency(real, "secUserRoleDao", roles);
        registerMock(SecurityInfoManager.class, real);
        assertThat(service("00123", "WT").getStatus()).isEqualTo(403);
        verifyNoInteractions(measurements, types, validations);
        assertThat(session.getAttribute("accountLocked")).isEqualTo(true);
    }

    private SecObjPrivilege privilege(String name, String right) {
        SecObjPrivilege result = new SecObjPrivilege();
        result.setId(new SecObjPrivilegePrimaryKey("doctor", name));
        result.setPrivilege(right);
        result.setPriority(0);
        return result;
    }

    private MockHttpServletResponse service(String patient, String type) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/FAKE-scatterplot");
        request.setSession(session);
        if (patient != null) {
            request.setParameter("demographicNo", patient);
        }
        request.setParameter("type", type);
        request.setParameter("mInstrc", "FAKE");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ScatterPlotChartServlet().service(request, response);
        return response;
    }
}
