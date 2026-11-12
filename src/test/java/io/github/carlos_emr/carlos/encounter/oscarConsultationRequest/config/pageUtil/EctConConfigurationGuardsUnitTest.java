/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Department;
import io.github.carlos_emr.carlos.commn.model.Institution;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.managers.ProfessionalSpecialistsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Runs the real actions: an editor may be opened by GET, but every configuration write needs
 * administrator write permission and POST before parsing identifiers or touching stored data. */
@Isolated("Replaces static consultation configuration security managers")
@Tag("unit")
@Tag("fast")
@Tag("consultation")
class EctConConfigurationGuardsUnitTest extends CarlosUnitTestBase {
    private static final List<Class<? extends ActionSupport>> ACTIONS = List.of(
            EctConAddService2Action.class, EctConDeleteServices2Action.class,
            EctConDisplayService2Action.class, EctConDisplayInstitution2Action.class,
            EctConEnableReqResp2Action.class, EctConAddSpecialist2Action.class,
            EctConAddInstitution2Action.class, EctConAddDepartment2Action.class,
            EctConEditSpecialists2Action.class, EctConEditInstitutions2Action.class,
            EctConEditDepartments2Action.class);
    private final Map<Class<?>, Object> previousSecurity = new HashMap<>();
    private MockedStatic<ServletActionContext> context;
    private MockedStatic<LoggedInInfo> sessions;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo info;
    private ConsultationServiceDao services;
    private InstitutionDao institutions;
    private DepartmentDao departments;
    private ProfessionalSpecialistDao specialists;
    private ServiceSpecialistsDao serviceLinks;
    private InstitutitionDepartmentDao institutionLinks;
    private PropertyDao properties;
    private ProfessionalSpecialistsManager specialistManager;

    @BeforeEach
    void prepare() {
        services = createAndRegisterMock(ConsultationServiceDao.class);
        institutions = createAndRegisterMock(InstitutionDao.class);
        departments = createAndRegisterMock(DepartmentDao.class);
        specialists = createAndRegisterMock(ProfessionalSpecialistDao.class);
        serviceLinks = createAndRegisterMock(ServiceSpecialistsDao.class);
        institutionLinks = createAndRegisterMock(InstitutitionDepartmentDao.class);
        properties = createAndRegisterMock(PropertyDao.class);
        specialistManager = createAndRegisterMock(ProfessionalSpecialistsManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_admin,_admin.consult", "w", null)).thenReturn(true);
        request = new MockHttpServletRequest("POST", "/carlos/encounter");
        request.setPreferredLocales(List.of(Locale.ENGLISH));
        response = new MockHttpServletResponse();
        context = mockStatic(ServletActionContext.class);
        context.when(ServletActionContext::getRequest).thenReturn(request);
        context.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(info);
        for (Class<?> type : ACTIONS) {
            previousSecurity.put(type, ReflectionTestUtils.getField(type, "securityInfoManager"));
            ReflectionTestUtils.setField(type, "securityInfoManager", security);
        }
    }

    @AfterEach
    void restore() {
        previousSecurity.forEach((type, old) -> ReflectionTestUtils.setField(type, "securityInfoManager", old));
        previousSecurity.clear();
        if (sessions != null) sessions.close();
        if (context != null) context.close();
    }

    static Stream<Arguments> deniedWriters() {
        return ACTIONS.stream().flatMap(type -> Stream.of("consultation", "admin-read-only", "anonymous")
                .map(role -> Arguments.of(type, role)));
    }

    static Stream<Arguments> refusedMethods() {
        return ACTIONS.stream().flatMap(type -> Stream.of("GET", "HEAD", "PUT")
                .map(method -> Arguments.of(type, method)));
    }

    private ActionSupport configuredWrite(Class<? extends ActionSupport> type) throws Exception {
        ActionSupport action = type.getDeclaredConstructor().newInstance();
        // Deliberately unusable identifiers distinguish early guards from parse/DAO failures.
        if (action instanceof EctConDeleteServices2Action a) a.setService(new String[]{"not-an-id"});
        if (action instanceof EctConDisplayService2Action a) a.setServiceId("not-an-id");
        if (action instanceof EctConDisplayInstitution2Action a) a.setId("not-an-id");
        if (action instanceof EctConAddSpecialist2Action a) { a.setWhichType(2); a.setSpecId("not-an-id"); }
        if (action instanceof EctConAddInstitution2Action a) { a.setWhichType(2); a.setId("not-an-id"); }
        if (action instanceof EctConAddDepartment2Action a) { a.setWhichType(2); a.setId("not-an-id"); }
        if (action instanceof EctConEditSpecialists2Action a) {
            a.setDelete("Delete Specialist"); a.setSpecialists(new String[]{"not-an-id"});
        }
        if (action instanceof EctConEditInstitutions2Action a) {
            a.setDelete("Delete Institution"); a.setInstitutions(new String[]{"not-an-id"});
        }
        if (action instanceof EctConEditDepartments2Action a) {
            a.setDelete("Delete Specialist"); a.setSpecialists(new String[]{"not-an-id"});
        }
        return action;
    }

    private void assertNoStoredDataAccess() {
        verifyNoInteractions(services, institutions, departments, specialists, serviceLinks,
                institutionLinks, properties, specialistManager);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}: {1} cannot write configuration")
    @MethodSource("deniedWriters")
    void refusesWithoutAdminWriteBeforeParsingOrDataAccess(Class<? extends ActionSupport> type,
                                                           String role) throws Exception {
        when(security.hasPrivilege(info, "_admin,_admin.consult", "w", null)).thenReturn(false);
        // Consultation clinical rights and administration read rights do not confer config write.
        when(security.hasPrivilege(eq(info), eq("_con"), anyString(), isNull())).thenReturn(true);
        when(security.hasPrivilege(info, "_admin,_admin.consult", "r", null))
                .thenReturn("admin-read-only".equals(role));
        if ("anonymous".equals(role)) {
            sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(null);
        }
        ActionSupport action = configuredWrite(type);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertNoStoredDataAccess();
        if ("anonymous".equals(role)) verifyNoInteractions(security);
        else verify(security).hasPrivilege(info, "_admin,_admin.consult", "w", null);
    }

    @ParameterizedTest(name = "{0}: {1} cannot perform its write")
    @MethodSource("refusedMethods")
    void refusesNonPostBeforeParsingOrDataAccess(Class<? extends ActionSupport> type,
                                                 String method) throws Exception {
        when(security.hasPrivilege(eq(info), eq("_con"), anyString(), isNull())).thenReturn(true);
        request.setMethod(method);
        assertThat(configuredWrite(type).execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertNoStoredDataAccess();
    }

    @Test
    void getStillOpensSpecialistEditorWithoutWriting() throws Exception {
        request.setMethod("GET");
        ProfessionalSpecialist saved = new ProfessionalSpecialist();
        saved.setFirstName("Owned"); saved.setLastName("Consultant");
        when(specialists.find(41)).thenReturn(saved);
        EctConEditSpecialists2Action action = new EctConEditSpecialists2Action(); action.setSpecId("41");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("lName")).isEqualTo("Consultant");
        verify(specialists).find(41); verifyNoMoreInteractions(specialists);
        verifyNoInteractions(services, serviceLinks, institutionLinks, institutions, departments, properties);
    }

    @Test
    void getStillOpensInstitutionEditorWithoutWriting() throws Exception {
        request.setMethod("GET");
        Institution saved = new Institution(); saved.setName("Owned institution");
        when(institutions.find(42)).thenReturn(saved);
        EctConEditInstitutions2Action action = new EctConEditInstitutions2Action(); action.setId("42");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("name")).isEqualTo("Owned institution");
        verify(institutions).find(42); verifyNoMoreInteractions(institutions);
        verifyNoInteractions(services, serviceLinks, institutionLinks, specialists, departments, properties);
    }

    @Test
    void getStillOpensDepartmentEditorWithoutWriting() throws Exception {
        request.setMethod("GET");
        Department saved = new Department(); saved.setName("Owned department");
        when(departments.find(43)).thenReturn(saved);
        EctConEditDepartments2Action action = new EctConEditDepartments2Action(); action.setId("43");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("name")).isEqualTo("Owned department");
        verify(departments).find(43); verifyNoMoreInteractions(departments);
        verifyNoInteractions(services, serviceLinks, institutionLinks, specialists, institutions, properties);
    }

    @Test
    void adminPostCreatesInstitution() throws Exception {
        EctConAddInstitution2Action action = new EctConAddInstitution2Action();
        action.setWhichType(1); action.setName("Owned institution");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Institution> stored = ArgumentCaptor.forClass(Institution.class);
        verify(institutions).persist(stored.capture());
        assertThat(stored.getValue().getName()).isEqualTo("Owned institution");
        verifyNoMoreInteractions(institutions);
    }

    @Test
    void adminPostEditsInstitution() throws Exception {
        Institution saved = new Institution(); saved.setName("Before");
        when(institutions.find(Integer.valueOf(42))).thenReturn(saved);
        EctConAddInstitution2Action action = new EctConAddInstitution2Action();
        action.setWhichType(2); action.setId("42"); action.setName("After");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(institutions).find(Integer.valueOf(42)); verify(institutions).merge(saved);
        assertThat(saved.getName()).isEqualTo("After"); verifyNoMoreInteractions(institutions);
    }

    @Test
    void adminPostCreatesDepartment() throws Exception {
        EctConAddDepartment2Action action = new EctConAddDepartment2Action();
        action.setWhichType(1); action.setName("Owned department");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Department> stored = ArgumentCaptor.forClass(Department.class);
        verify(departments).persist(stored.capture());
        assertThat(stored.getValue().getName()).isEqualTo("Owned department");
        verifyNoMoreInteractions(departments);
    }

    @Test
    void adminPostEditsDepartment() throws Exception {
        Department saved = new Department(); saved.setName("Before");
        when(departments.find(Integer.valueOf(43))).thenReturn(saved);
        EctConAddDepartment2Action action = new EctConAddDepartment2Action();
        action.setWhichType(2); action.setId("43"); action.setName("After");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(departments).find(Integer.valueOf(43)); verify(departments).merge(saved);
        assertThat(saved.getName()).isEqualTo("After"); verifyNoMoreInteractions(departments);
    }

    @Test
    void adminPostEnablesRequestResponseAndReferringDoctor() throws Exception {
        EctConEnableReqResp2Action action = new EctConEnableReqResp2Action();
        action.setConsultRequestEnabled(true); action.setConsultResponseEnabled(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Property> flags = ArgumentCaptor.forClass(Property.class);
        verify(properties, times(2)).merge(flags.capture());
        assertThat(flags.getAllValues()).allSatisfy(flag -> assertThat(flag.getValue()).isEqualTo("Y"));
        ArgumentCaptor<ConsultationServices> referringDoctor = ArgumentCaptor.forClass(ConsultationServices.class);
        verify(services).merge(referringDoctor.capture());
        assertThat(referringDoctor.getValue().getActive()).isEqualTo(ConsultationServiceDao.ACTIVE);
        assertThat(request.getAttribute("ENABLE_REQUEST_RESPONSE_UPDATED")).isEqualTo("updated");
    }
}
