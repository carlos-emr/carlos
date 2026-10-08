/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DepartmentDao;
import io.github.carlos_emr.carlos.commn.dao.InstitutionDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Department;
import io.github.carlos_emr.carlos.commn.model.Institution;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Covers both directory deletion and the read-only form-loading branches. */
@Tag("unit")
class ConsultDirectoryReadMethodUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private InstitutionDao institutions;
    private DepartmentDao departments;
    private ProfessionalSpecialistDao specialists;
    private final Map<Class<?>, Object> previousSecurity = new HashMap<>();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.ENGLISH));
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        institutions = mock(InstitutionDao.class);
        departments = mock(DepartmentDao.class);
        specialists = mock(ProfessionalSpecialistDao.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(InstitutionDao.class, institutions);
        registerMock(DepartmentDao.class, departments);
        registerMock(ProfessionalSpecialistDao.class, specialists);
        when(security.hasPrivilege(login, "_con", "u", null)).thenReturn(true);
        for (Class<?> type : List.of(EctConEditInstitutions2Action.class,
                EctConEditDepartments2Action.class, EctConEditSpecialists2Action.class)) {
            previousSecurity.put(type, ReflectionTestUtils.getField(type, "securityInfoManager"));
            ReflectionTestUtils.setField(type, "securityInfoManager", security);
        }
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        previousSecurity.forEach((type, value) -> ReflectionTestUtils.setField(type, "securityInfoManager", value));
        servlet.close();
    }

    private ActionSupport action(String type, boolean deleting) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);
        String label = deleting ? bundle.getString("encounter.oscarConsultationRequest.config."
                + ("institution".equals(type) ? "EditInstitutions.btnDeleteInstitution"
                : "EditSpecialists.btnDeleteSpecialist")) : "";
        switch (type) {
            case "institution":
                var institution = new EctConEditInstitutions2Action();
                institution.setId("7");
                institution.setInstitutions(new String[]{"7"});
                institution.setDelete(label);
                return institution;
            case "department":
                var department = new EctConEditDepartments2Action();
                department.setId("7");
                department.setSpecialists(new String[]{"7"});
                department.setDelete(label);
                return department;
            case "specialist":
                var specialist = new EctConEditSpecialists2Action();
                specialist.setSpecId("7");
                specialist.setSpecialists(new String[]{"7"});
                specialist.setDelete(label);
                return specialist;
            default:
                throw new IllegalArgumentException(type);
        }
    }

    @ParameterizedTest
    @CsvSource({"institution, GET", "institution, HEAD", "department, GET", "department, HEAD",
            "specialist, GET", "specialist, HEAD"})
    void shouldRefuseDeletionBeforeDaoAccess_whenMethodIsReadOnly(String type, String method) throws Exception {
        request.setMethod(method);
        assertThat(action(type, true).execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(institutions, departments, specialists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"institution", "department", "specialist"})
    void shouldRetainDeletion_whenAuthorizedPostIsSubmitted(String type) throws Exception {
        request.setMethod("POST");
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        ReflectionTestUtils.setField(specialist, "id", 7);
        when(specialists.find(7)).thenReturn(specialist);
        try (var scripts = mockConstruction(EctConConstructSpecialistsScriptsFile.class)) {
            assertThat(action(type, true).execute()).isEqualTo("delete");
            switch (type) {
                case "institution" -> verify(institutions).remove(7);
                case "department" -> verify(departments).remove(7);
                case "specialist" -> {
                    assertThat(specialist.isDeleted()).isTrue();
                    verify(specialists).merge(specialist);
                }
                default -> throw new AssertionError(type);
            }
            assertThat(scripts.constructed()).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"institution", "department", "specialist"})
    void shouldLoadEditFormWithoutDeletion_whenGetHasNoDeleteIntent(String type) throws Exception {
        request.setMethod("GET");
        when(institutions.find(7)).thenReturn(new Institution());
        when(departments.find(7)).thenReturn(new Department());
        when(specialists.find(7)).thenReturn(new ProfessionalSpecialist());
        try (var scripts = mockConstruction(EctConConstructSpecialistsScriptsFile.class)) {
            assertThat(action(type, false).execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(institutions, never()).remove(anyInt());
            verify(departments, never()).remove(anyInt());
            verify(specialists, never()).merge(any(ProfessionalSpecialist.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"institution", "department", "specialist"})
    void shouldRejectDeletion_whenConsultationUpdatePrivilegeIsMissing(String type) {
        request.setMethod("POST");
        when(security.hasPrivilege(login, "_con", "u", null)).thenReturn(false);
        assertThatThrownBy(() -> action(type, true).execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(institutions, departments, specialists);
    }
}
