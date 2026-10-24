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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DepartmentDao;
import io.github.carlos_emr.carlos.commn.dao.InstitutionDao;
import io.github.carlos_emr.carlos.commn.dao.InstitutitionDepartmentDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.Department;
import io.github.carlos_emr.carlos.commn.model.Institution;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialistsPK;
import io.github.carlos_emr.carlos.managers.ProfessionalSpecialistsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The consultation configuration 2Actions after the legacy specialists script was retired (issue #4617,
 * finding 260 of {@code docs/ui-tests/app-findings-log.md}).
 *
 * <p>Every service and consultant write, and each consultant, institution and department editor's open,
 * used to run {@code EctConConstructSpecialistsScriptsFile}: it loaded every active service
 * ({@link ConsultationServiceDao#findActive()}) and each one's consultants
 * ({@link ServiceSpecialistsDao#findSpecialists(Integer)}) to rewrite a {@code specialistsJavascript} row
 * nothing read, and the editors also tried to write {@code encounter/consult.js} relative to the working
 * directory. These tests pin each action to the work its page asks for: no action looks up a bean it does
 * not use (the unit base refuses an unregistered bean, which is how the generator's lookups would show),
 * none calls {@code findActive()}, the editors' open paths only read, and no editor path opens a
 * {@link FileWriter}.
 *
 * @since 2026-10-14
 */
@Isolated("Replaces the static security managers of the consultation configuration 2Actions")
@DisplayName("Consultation configuration 2Actions without the legacy specialists script")
@Tag("unit")
@Tag("fast")
@Tag("consultation")
class EctConConfigurationWrites2ActionUnitTest extends CarlosUnitTestBase {

    /** The actions under test that hold their security manager in a static field initialised at class load. */
    private static final List<Class<?>> ACTIONS = List.of(
            EctConAddService2Action.class,
            EctConDeleteServices2Action.class,
            EctConDisplayService2Action.class,
            EctConDisplayInstitution2Action.class,
            EctConAddSpecialist2Action.class,
            EctConEditSpecialists2Action.class,
            EctConEditInstitutions2Action.class,
            EctConEditDepartments2Action.class);

    private final Map<Class<?>, Object> previousSecurity = new HashMap<>();

    /** A transaction manager that begins and ends nothing, for actions that open a transaction. */
    static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        // Add Service commits its insert in a transaction its submission guard observes (issue #4225).
        registerMock(PlatformTransactionManager.class, new NoOpTransactionManager());
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_con"), anyString(), ArgumentMatchers.<String>isNull()))
                .thenReturn(true);

        request = new MockHttpServletRequest("POST", "/carlos/encounter");
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        // The static field is bound once per JVM; point it at this test's mock and put it back afterwards.
        for (Class<?> action : ACTIONS) {
            previousSecurity.put(action, ReflectionTestUtils.getField(action, "securityInfoManager"));
            ReflectionTestUtils.setField(action, "securityInfoManager", security);
        }
    }

    @AfterEach
    void tearDown() {
        previousSecurity.forEach((action, previous) -> ReflectionTestUtils.setField(action, "securityInfoManager", previous));
        previousSecurity.clear();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    /**
     * Runs an editor action and fails if it opened a {@link FileWriter}, which is how {@code makeFile()}
     * wrote {@code encounter/consult.js}. A failed write left no other trace: it was swallowed at DEBUG and
     * {@code makeFile()} returned null, so asserting the result attribute alone could not see the attempt.
     */
    private static String executeWithoutFileWrites(ActionSupport action) throws Exception {
        try (MockedConstruction<FileWriter> writers = mockConstruction(FileWriter.class)) {
            String result = action.execute();
            assertThat(writers.constructed())
                    .as("FileWriters opened by %s", action.getClass().getSimpleName())
                    .isEmpty();
            return result;
        }
    }

    @Nested
    @DisplayName("service writes")
    class ServiceWrites {

        @Test
        @DisplayName("Add Service persists one active service and loads no service list")
        void shouldPersistActiveService_withoutLoadingTheServiceList() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            EctConAddService2Action action = new EctConAddService2Action();
            action.setService("Cardiology FAKE");

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            ArgumentCaptor<ConsultationServices> saved = ArgumentCaptor.forClass(ConsultationServices.class);
            verify(services).persist(saved.capture());
            assertThat(saved.getValue().getServiceDesc()).isEqualTo("Cardiology FAKE");
            assertThat(saved.getValue().getActive()).isEqualTo(ConsultationServiceDao.ACTIVE);
            assertThat(request.getAttribute("SERVADD")).isEqualTo("Cardiology FAKE");
            verify(services, never()).findActive();
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("Add Service adds one service when one rendered form is submitted twice (issue #4225)")
        void shouldAddOneService_whenOneRenderingIsSubmittedTwice() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            org.mockito.Mockito.doAnswer(inv -> {
                inv.<ConsultationServices>getArgument(0).setServiceId(61);
                return null;
            }).when(services).persist(any(ConsultationServices.class));
            ConsultationServices stored = new ConsultationServices();
            stored.setServiceDesc("Cardiology FAKE");
            when(services.find(Integer.valueOf(61))).thenReturn(stored);
            String token = FormSubmissionGuard.issue(request.getSession(),
                    FormSubmissionGuard.Scope.CONSULTATION_SERVICE_ADD, null);
            request.setParameter(FormSubmissionGuard.PARAMETER, token);
            EctConAddService2Action first = new EctConAddService2Action();
            first.setService("Cardiology FAKE");
            EctConAddService2Action repeat = new EctConAddService2Action();
            // The repeat carries an edited name; the confirmation still names what was saved.
            repeat.setService("Cardiology EDITED");

            assertThat(first.execute()).isEqualTo(ActionSupport.SUCCESS);
            request.removeAttribute("SERVADD");
            assertThat(repeat.execute()).isEqualTo(ActionSupport.SUCCESS);

            verify(services).persist(any(ConsultationServices.class));
            verify(services).find(Integer.valueOf(61));
            assertThat(request.getAttribute("SERVADD")).isEqualTo("Cardiology FAKE");
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("A repeat of an add that recorded no id is refused as unconfirmed, not confirmed with its own entry")
        void shouldRefuseRepeatAsUnconfirmed_whenTheFirstAddRecordedNoId() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            String token = FormSubmissionGuard.issue(request.getSession(),
                    FormSubmissionGuard.Scope.CONSULTATION_SERVICE_ADD, null);
            request.setParameter(FormSubmissionGuard.PARAMETER, token);
            EctConAddService2Action first = new EctConAddService2Action();
            first.setService("Cardiology FAKE");
            EctConAddService2Action repeat = new EctConAddService2Action();
            repeat.setService("Cardiology EDITED");

            assertThat(first.execute()).isEqualTo(ActionSupport.SUCCESS);
            request.removeAttribute("SERVADD");
            assertThat(repeat.execute()).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);

            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(request.getAttribute("formSubmissionMessageKey"))
                    .isEqualTo("global.formSubmission.unconfirmed");
            assertThat(request.getAttribute("SERVADD")).isNull();
            verify(services).persist(any(ConsultationServices.class));
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("Add Service refuses a token this session did not issue and adds nothing")
        void shouldRefuseWithoutAdding_forATokenThisSessionDidNotIssue() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            request.setParameter(FormSubmissionGuard.PARAMETER, FormSubmissionGuard.issue(
                    new org.springframework.mock.web.MockHttpSession(), FormSubmissionGuard.Scope.CONSULTATION_SERVICE_ADD, null));
            EctConAddService2Action action = new EctConAddService2Action();
            action.setService("Cardiology FAKE");

            assertThat(action.execute()).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);

            assertThat(response.getStatus()).isEqualTo(409);
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("Add Service refuses GET and HEAD and adds nothing")
        void shouldRefuseReadMethods_withoutAdding() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            for (String method : List.of("GET", "HEAD")) {
                request.setMethod(method);
                EctConAddService2Action action = new EctConAddService2Action();
                action.setService("Cardiology FAKE");

                assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
                assertThat(response.getStatus()).isEqualTo(405);
                assertThat(response.getHeader("Allow")).isEqualTo("POST");
            }
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("Delete Services inactivates the checked service and loads no service list")
        void shouldInactivateCheckedService_withoutLoadingTheServiceList() throws Exception {
            ConsultationServiceDao services = createAndRegisterMock(ConsultationServiceDao.class);
            ConsultationServices service = new ConsultationServices();
            service.setActive(ConsultationServiceDao.ACTIVE);
            when(services.find(Integer.valueOf(53))).thenReturn(service);
            EctConDeleteServices2Action action = new EctConDeleteServices2Action();
            action.setService(new String[] {"53"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            assertThat(service.getActive()).isEqualTo(ConsultationServiceDao.INACTIVE);
            verify(services).find(Integer.valueOf(53));
            verify(services).merge(service);
            verify(services, never()).findActive();
            verifyNoMoreInteractions(services);
        }

        @Test
        @DisplayName("Update Service Specialists replaces the service's consultants and touches no other service")
        void shouldReplaceServiceConsultants_withoutTouchingOtherServices() throws Exception {
            ServiceSpecialistsDao assignments = createAndRegisterMock(ServiceSpecialistsDao.class);
            ServiceSpecialists old = new ServiceSpecialists();
            old.setId(new ServiceSpecialistsPK(53, 9000));
            when(assignments.findByServiceId(53)).thenReturn(List.of(old));
            EctConDisplayService2Action action = new EctConDisplayService2Action();
            action.setServiceId("53");
            action.setSpecialists(new String[] {"9001"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            ArgumentCaptor<ServiceSpecialists> added = ArgumentCaptor.forClass(ServiceSpecialists.class);
            verify(assignments).findByServiceId(53);
            verify(assignments).remove(old.getId());
            verify(assignments).persist(added.capture());
            assertThat(added.getValue().getId().getServiceId()).isEqualTo(53);
            assertThat(added.getValue().getId().getSpecId()).isEqualTo(9001);
            verify(assignments, never()).findSpecialists(any());
        }

        @Test
        @DisplayName("Update Institution Department replaces the institution's departments only")
        void shouldReplaceInstitutionDepartments_withoutAnyServiceLookup() throws Exception {
            InstitutitionDepartmentDao links = createAndRegisterMock(InstitutitionDepartmentDao.class);
            when(links.findByInstitutionId(12)).thenReturn(List.of());
            EctConDisplayInstitution2Action action = new EctConDisplayInstitution2Action();
            action.setId("12");
            action.setSpecialists(new String[] {"4"});

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            verify(links).findByInstitutionId(12);
            verify(links).persist(any());
            verifyNoMoreInteractions(links);
        }
    }

    @Nested
    @DisplayName("consultant editor")
    class ConsultantEditor {

        @Test
        @DisplayName("opening a consultant only reads it and attempts no script file")
        void shouldOnlyReadTheConsultant_whenOpenedForEditing() throws Exception {
            ProfessionalSpecialistDao specialists = createAndRegisterMock(ProfessionalSpecialistDao.class);
            ProfessionalSpecialist consultant = new ProfessionalSpecialist();
            consultant.setFirstName("Ann");
            consultant.setLastName("FAKE-Consultant");
            consultant.setPhoneNumber("555-0150");
            when(specialists.find(7)).thenReturn(consultant);
            EctConEditSpecialists2Action action = new EctConEditSpecialists2Action();
            action.setSpecId("7");

            assertThat(executeWithoutFileWrites(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("lName")).isEqualTo("FAKE-Consultant");
            assertThat(request.getAttribute("phone")).isEqualTo("555-0150");
            assertThat(request.getAttribute("specId")).isEqualTo("7");
            assertThat(request.getAttribute("upd")).isEqualTo(0);
            // "verd" carried makeFile()'s result, which no page read; the write itself is ruled out above.
            assertThat(request.getAttribute("verd")).isNull();
            verify(specialists).find(7);
            verifyNoMoreInteractions(specialists);
        }

        @Test
        @DisplayName("deleting consultants soft-deletes each checked one and nothing else")
        void shouldSoftDeleteCheckedConsultants_withoutAnyServiceLookup() throws Exception {
            ProfessionalSpecialistDao specialists = createAndRegisterMock(ProfessionalSpecialistDao.class);
            ProfessionalSpecialist consultant = new ProfessionalSpecialist();
            when(specialists.find(7)).thenReturn(consultant);
            EctConEditSpecialists2Action action = new EctConEditSpecialists2Action();
            action.setDelete("Delete Specialist");
            action.setSpecialists(new String[] {"7"});

            assertThat(executeWithoutFileWrites(action)).isEqualTo("delete");

            assertThat(consultant.isDeleted()).isTrue();
            verify(specialists).find(7);
            verify(specialists).merge(consultant);
            verifyNoMoreInteractions(specialists);
        }

        @Test
        @DisplayName("saving an edited consultant goes through the manager and loads no service list")
        void shouldSaveEditedConsultant_withoutLoadingTheServiceList() throws Exception {
            ProfessionalSpecialistDao specialists = createAndRegisterMock(ProfessionalSpecialistDao.class);
            ProfessionalSpecialistsManager manager = createAndRegisterMock(ProfessionalSpecialistsManager.class);
            ProfessionalSpecialist consultant = new ProfessionalSpecialist();
            when(specialists.find(Integer.valueOf(7))).thenReturn(consultant);
            EctConAddSpecialist2Action action = new EctConAddSpecialist2Action();
            action.setWhichType(2);
            action.setSpecId("7");
            action.setFirstName("Ann");
            action.setLastName("FAKE-Consultant");
            action.setPhone("555-0152");
            action.setInstitution("0");
            action.setDepartment("0");
            // AddSpecialist.jsp always posts these selects; the action unboxes hideFromView.
            action.setHideFromView(Boolean.FALSE);

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

            verify(specialists).find(Integer.valueOf(7));
            verify(specialists).detach(consultant);
            verify(manager).saveProfessionalSpecialist(any(LoggedInInfo.class), eq(consultant));
            verifyNoMoreInteractions(specialists, manager);
            assertThat(consultant.getPhoneNumber()).isEqualTo("555-0152");
            assertThat(request.getAttribute("Added")).isEqualTo("Ann FAKE-Consultant");
        }
    }

    @Nested
    @DisplayName("institution and department editors")
    class DirectoryEditors {

        @Test
        @DisplayName("opening an institution only reads it and attempts no script file")
        void shouldOnlyReadTheInstitution_whenOpenedForEditing() throws Exception {
            InstitutionDao institutions = createAndRegisterMock(InstitutionDao.class);
            Institution institution = new Institution();
            institution.setName("FAKE Institution");
            when(institutions.find(12)).thenReturn(institution);
            EctConEditInstitutions2Action action = new EctConEditInstitutions2Action();
            action.setId("12");

            assertThat(executeWithoutFileWrites(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("name")).isEqualTo("FAKE Institution");
            assertThat(request.getAttribute("verd")).isNull();
            verify(institutions).find(12);
            verifyNoMoreInteractions(institutions);
        }

        @Test
        @DisplayName("deleting institutions removes each checked one and nothing else")
        void shouldRemoveCheckedInstitutions_withoutAnyServiceLookup() throws Exception {
            InstitutionDao institutions = createAndRegisterMock(InstitutionDao.class);
            EctConEditInstitutions2Action action = new EctConEditInstitutions2Action();
            action.setDelete("Delete Institution");
            action.setInstitutions(new String[] {"12"});

            assertThat(executeWithoutFileWrites(action)).isEqualTo("delete");

            verify(institutions).remove((Object) 12);
            verifyNoMoreInteractions(institutions);
        }

        @Test
        @DisplayName("opening a department only reads it and attempts no script file")
        void shouldOnlyReadTheDepartment_whenOpenedForEditing() throws Exception {
            DepartmentDao departments = createAndRegisterMock(DepartmentDao.class);
            Department department = new Department();
            department.setName("FAKE Department");
            when(departments.find(4)).thenReturn(department);
            EctConEditDepartments2Action action = new EctConEditDepartments2Action();
            action.setId("4");

            assertThat(executeWithoutFileWrites(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("name")).isEqualTo("FAKE Department");
            assertThat(request.getAttribute("verd")).isNull();
            verify(departments).find(4);
            verifyNoMoreInteractions(departments);
        }

        @Test
        @DisplayName("deleting departments removes each checked one and nothing else")
        void shouldRemoveCheckedDepartments_withoutAnyServiceLookup() throws Exception {
            DepartmentDao departments = createAndRegisterMock(DepartmentDao.class);
            EctConEditDepartments2Action action = new EctConEditDepartments2Action();
            // The department editor's delete button reuses the consultant editor's label (EditDepartments.jsp).
            action.setDelete("Delete Specialist");
            action.setSpecialists(new String[] {"4"});

            assertThat(executeWithoutFileWrites(action)).isEqualTo("delete");

            verify(departments).remove((Object) 4);
            verifyNoMoreInteractions(departments);
        }
    }
}
