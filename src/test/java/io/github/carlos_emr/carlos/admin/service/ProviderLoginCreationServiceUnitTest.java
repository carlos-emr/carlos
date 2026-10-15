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
package io.github.carlos_emr.carlos.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that a login created from Add a Login Record always carries the role the
 * administrator chose (issue #2689), written the same way Assign Role to Provider writes it.
 */
@Tag("unit")
@Tag("security")
@DisplayName("ProviderLoginCreationService")
@Isolated("Temporarily sets the caisi, CASEMANAGEMENT and multisite properties")
class ProviderLoginCreationServiceUnitTest {

    private static final String[] PROPERTIES = {"caisi", "CASEMANAGEMENT", "multisites", "multioffice.admin.role.name"};

    private final SecurityDao securityDao = mock(SecurityDao.class);
    private final SecuserroleDao secuserroleDao = mock(SecuserroleDao.class);
    private final SecRoleDao secRoleDao = mock(SecRoleDao.class);
    private final ProgramDao programDao = mock(ProgramDao.class);
    private final ProgramProviderDAO programProviderDao = mock(ProgramProviderDAO.class);
    private final ProviderDao providerDao = mock(ProviderDao.class);
    private final ProviderSiteDao providerSiteDao = mock(ProviderSiteDao.class);
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final Map<String, String> originalProperties = new HashMap<>();
    private final LoggedInInfo actor = mock(LoggedInInfo.class);

    private ProviderLoginCreationService service;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        for (String name : PROPERTIES) {
            originalProperties.put(name, CarlosProperties.getInstance().getProperty(name));
        }
        property("caisi", "off");
        property("CASEMANAGEMENT", "all");
        property("multisites", "off");
        property("multioffice.admin.role.name", "");
        service = new ProviderLoginCreationService(securityDao, secuserroleDao, secRoleDao,
                programDao, programProviderDao, providerDao, providerSiteDao, securityInfoManager);
        logAction = mockStatic(LogAction.class);
    }

    @AfterEach
    void restoreProperties() {
        logAction.close();
        originalProperties.forEach((name, value) -> {
            if (value == null) {
                CarlosProperties.getInstance().remove(name);
            } else {
                CarlosProperties.getInstance().setProperty(name, value);
            }
        });
    }

    private static void property(String name, String value) {
        CarlosProperties.getInstance().setProperty(name, value);
    }

    /** SecRole has no id setter (the id is generated), so the role row is a stub. */
    private static SecRole role(int id, String name) {
        SecRole role = mock(SecRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getName()).thenReturn(name);
        return role;
    }

    private static Security login(String providerNo) {
        Security login = new Security();
        login.setUserName("fixture2689");
        login.setProviderNo(providerNo);
        return login;
    }

    private static Program program(int id) {
        Program program = new Program();
        program.setId(id);
        program.setName(ProviderLoginCreationService.CASE_MANAGEMENT_PROGRAM_NAME);
        return program;
    }

    @Nested
    @DisplayName("createLoginWithRole")
    class CreateLoginWithRole {

        @Test
        @DisplayName("should persist the login before its single active role assignment")
        void shouldPersistLoginAndActiveRole_whenRoleExists() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            Security login = login("700001");

            service.createLoginWithRole(actor, login, "doctor");

            InOrder order = inOrder(securityDao, secuserroleDao);
            order.verify(securityDao).persist(login);
            ArgumentCaptor<Secuserrole> assignment = ArgumentCaptor.forClass(Secuserrole.class);
            order.verify(secuserroleDao).save(assignment.capture());
            assertThat(assignment.getValue().getProviderNo()).isEqualTo("700001");
            assertThat(assignment.getValue().getRoleName()).isEqualTo("doctor");
            assertThat(assignment.getValue().getActiveyn()).isEqualTo(1);
        }

        @Test
        @DisplayName("should audit the login and the role strictly, after writing them")
        void shouldAuditLoginAndRoleStrictly_afterWrites() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            Security login = login("700001");

            service.createLoginWithRole(actor, login, "doctor");

            InOrder order = inOrder(securityDao, secuserroleDao);
            order.verify(securityDao).persist(login);
            order.verify(secuserroleDao).save(any(Secuserrole.class));
            logAction.verify(() -> LogAction.addLogStrict(
                    eq(actor), eq(LogConst.ADD), eq(LogConst.CON_SECURITY), eq("fixture2689"), isNull(), isNull()));
            logAction.verify(() -> LogAction.addLogStrict(
                    eq(actor), eq(LogConst.ADD), eq(LogConst.CON_ROLE), eq("700001|doctor"), isNull(), isNull()));
            logAction.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), anyString()),
                    never());
        }

        @Test
        @DisplayName("should let an audit failure propagate so the transaction rolls the login back")
        void shouldPropagateAuditFailure_soLoginRollsBack() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            logAction.when(() -> LogAction.addLogStrict(any(), anyString(), anyString(), anyString(), isNull(), isNull()))
                    .thenThrow(new IllegalStateException("audit store unavailable"));

            Security login = login("700001");

            assertThatThrownBy(() -> service.createLoginWithRole(actor, login, "doctor"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should refuse to write anything without the acting administrator")
        void shouldRefuseWithoutActor_forAuditTrail() {
            Security login = login("700001");

            assertThatThrownBy(() -> service.createLoginWithRole(null, login, "doctor"))
                    .isInstanceOf(NullPointerException.class);
            verifyNoInteractions(securityDao, secuserroleDao, programProviderDao);
        }

        @Test
        @DisplayName("should reuse the provider's existing assignment of the role instead of adding a duplicate")
        void shouldReuseExistingAssignment_whenProviderAlreadyHoldsRole() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            Secuserrole held = new Secuserrole();
            held.setId(41);
            held.setProviderNo("700001");
            held.setRoleName("doctor");
            held.setActiveyn(1);
            Secuserrole other = new Secuserrole();
            other.setId(42);
            other.setProviderNo("700001");
            other.setRoleName("receptionist");
            other.setActiveyn(1);
            when(secuserroleDao.findByProviderNo("700001")).thenReturn(List.of(other, held));

            service.createLoginWithRole(actor, login("700001"), "doctor");

            ArgumentCaptor<Secuserrole> saved = ArgumentCaptor.forClass(Secuserrole.class);
            verify(secuserroleDao).save(saved.capture());
            assertThat(saved.getValue()).isSameAs(held);
            assertThat(held.getActiveyn()).isEqualTo(1);
        }

        @Test
        @DisplayName("should reactivate an inactive assignment of the role instead of adding a second row")
        void shouldReactivateAssignment_whenProviderHeldRoleInactive() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            Secuserrole inactive = new Secuserrole();
            inactive.setId(41);
            inactive.setProviderNo("700001");
            inactive.setRoleName("doctor");
            inactive.setActiveyn(0);
            when(secuserroleDao.findByProviderNo("700001")).thenReturn(List.of(inactive));

            service.createLoginWithRole(actor, login("700001"), "doctor");

            verify(secuserroleDao).save(inactive);
            assertThat(inactive.getActiveyn()).isEqualTo(1);
        }

        @Test
        @DisplayName("should write nothing when the role is not configured")
        void shouldWriteNothing_whenRoleIsUnknown() {
            when(secRoleDao.findByName("ghost")).thenReturn(null);

            assertThatThrownBy(() -> service.createLoginWithRole(actor, login("700001"), "ghost"))
                    .isInstanceOf(ProviderLoginCreationService.UnknownRoleException.class);

            verify(securityDao, never()).persist(any(Security.class));
            verifyNoInteractions(secuserroleDao, programProviderDao);
        }

        @Test
        @DisplayName("should write nothing when no role is given")
        void shouldWriteNothing_whenRoleIsNull() {
            assertThatThrownBy(() -> service.createLoginWithRole(actor, login("700001"), null))
                    .isInstanceOf(ProviderLoginCreationService.UnknownRoleException.class);

            verify(securityDao, never()).persist(any(Security.class));
            verifyNoInteractions(secuserroleDao, programProviderDao);
        }

        @Test
        @DisplayName("should add the OSCAR program membership with the role when case management is on")
        void shouldAddProgramMembership_withCaseManagementOn() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            when(programDao.getProgramByName("OSCAR")).thenReturn(program(10016));
            when(programProviderDao.getProgramProvider("700001", 10016L)).thenReturn(null);

            service.createLoginWithRole(actor, login("700001"), "doctor");

            ArgumentCaptor<ProgramProvider> membership = ArgumentCaptor.forClass(ProgramProvider.class);
            verify(programProviderDao).saveProgramProvider(membership.capture());
            assertThat(membership.getValue().getProgramId()).isEqualTo(10016L);
            assertThat(membership.getValue().getProviderNo()).isEqualTo("700001");
            assertThat(membership.getValue().getRoleId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should set the role on an existing OSCAR program membership instead of adding another")
        void shouldUpdateExistingMembership_whenProviderAlreadyInProgram() {
            SecRole receptionist = role(1, "receptionist");
            when(secRoleDao.findByName("receptionist")).thenReturn(receptionist);
            when(programDao.getProgramByName("OSCAR")).thenReturn(program(10016));
            ProgramProvider existing = new ProgramProvider();
            existing.setId(55L);
            existing.setProgramId(10016L);
            existing.setProviderNo("700001");
            existing.setRoleId(2L);
            when(programProviderDao.getProgramProvider("700001", 10016L)).thenReturn(existing);

            service.createLoginWithRole(actor, login("700001"), "receptionist");

            verify(programProviderDao).saveProgramProvider(existing);
            assertThat(existing.getRoleId()).isEqualTo(1L);
            assertThat(existing.getId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("should skip the program membership when case management is not set to all")
        void shouldSkipProgramMembership_whenCaseManagementIsOff() {
            property("CASEMANAGEMENT", "");
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);

            service.createLoginWithRole(actor, login("700001"), "doctor");

            verify(secuserroleDao).save(any(Secuserrole.class));
            verifyNoInteractions(programDao, programProviderDao);
        }

        @Test
        @DisplayName("should skip the program membership when CAISI is enabled")
        void shouldSkipProgramMembership_whenCaisiIsEnabled() {
            property("caisi", "on");
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);

            service.createLoginWithRole(actor, login("700001"), "doctor");

            verify(secuserroleDao).save(any(Secuserrole.class));
            verifyNoInteractions(programDao, programProviderDao);
        }

        @Test
        @DisplayName("should skip the program membership when the OSCAR program does not exist")
        void shouldSkipProgramMembership_whenProgramIsMissing() {
            SecRole doctor = role(2, "doctor");
            when(secRoleDao.findByName("doctor")).thenReturn(doctor);
            when(programDao.getProgramByName("OSCAR")).thenReturn(null);

            service.createLoginWithRole(actor, login("700001"), "doctor");

            verify(secuserroleDao).save(any(Secuserrole.class));
            verify(programProviderDao, never()).getProgramProvider(anyString(), anyLong());
            verify(programProviderDao, never()).saveProgramProvider(any(ProgramProvider.class));
        }
    }

    @Nested
    @DisplayName("isEligibleProvider")
    class IsEligibleProvider {

        private final LoggedInInfo admin = mock(LoggedInInfo.class);

        private Provider provider(String providerNo, String status) {
            Provider provider = new Provider();
            provider.setProviderNo(providerNo);
            provider.setStatus(status);
            return provider;
        }

        @BeforeEach
        void admin() {
            when(admin.getLoggedInProviderNo()).thenReturn("999998");
        }

        @Test
        @DisplayName("should accept an active provider on a standalone install")
        void shouldAcceptActiveProvider_forStandaloneInstall() {
            Provider active = provider("700001", "1");
            when(providerDao.getProvider("700001")).thenReturn(active);

            assertThat(service.isEligibleProvider(admin, "700001")).isTrue();
        }

        @Test
        @DisplayName("should refuse an inactive, unknown, blank or system provider number")
        void shouldRefuseProvider_whenInactiveUnknownBlankOrSystem() {
            Provider inactive = provider("700002", "0");
            when(providerDao.getProvider("700002")).thenReturn(inactive);

            assertThat(service.isEligibleProvider(admin, "700002")).isFalse();
            assertThat(service.isEligibleProvider(admin, "700003")).isFalse();
            assertThat(service.isEligibleProvider(admin, null)).isFalse();
            assertThat(service.isEligibleProvider(admin, "")).isFalse();
            assertThat(service.isEligibleProvider(admin, "-1")).isFalse();
            verify(providerDao, never()).getProvider("-1");
        }

        @Test
        @DisplayName("should limit a site-scoped administrator to providers sharing one of their sites")
        void shouldLimitToSharedSites_forSiteScopedAdministrator() {
            property("multisites", "on");
            when(securityInfoManager.hasPrivilege(admin, "_site_access_privacy", "r", null)).thenReturn(true);
            List<Provider> shared = List.of(provider("700001", "1"));
            when(providerSiteDao.findActiveProvidersBySharedSites("999998")).thenReturn(shared);
            Provider elsewhere = provider("700009", "1");
            when(providerDao.getProvider("700009")).thenReturn(elsewhere);

            assertThat(service.isEligibleProvider(admin, "700001")).isTrue();
            assertThat(service.isEligibleProvider(admin, "700009")).isFalse();
        }
    }

    @Nested
    @DisplayName("assignableRoleNames")
    class AssignableRoleNames {

        private final LoggedInInfo admin = mock(LoggedInInfo.class);

        @BeforeEach
        void roles() {
            List<SecRole> configured = List.of(
                    role(3, "admin"), role(2, "doctor"), role(1, "receptionist"), role(40, "siteroot"));
            when(secRoleDao.findAllOrderByRole()).thenReturn(configured);
        }

        @Test
        @DisplayName("should offer every configured role on a standalone install")
        void shouldOfferEveryRole_forStandaloneInstall() {
            when(securityInfoManager.hasPrivilege(admin, "_site_access_privacy", "r", null)).thenReturn(true);
            property("multioffice.admin.role.name", "siteroot");

            assertThat(service.assignableRoleNames(admin))
                    .containsExactly("admin", "doctor", "receptionist", "siteroot");
        }

        @Test
        @DisplayName("should withhold the multisite super-root role from a site-scoped administrator")
        void shouldWithholdSuperRootRole_forSiteScopedAdministrator() {
            property("multisites", "on");
            property("multioffice.admin.role.name", "siteroot");
            when(securityInfoManager.hasPrivilege(admin, "_site_access_privacy", "r", null)).thenReturn(true);

            assertThat(service.assignableRoleNames(admin))
                    .containsExactly("admin", "doctor", "receptionist");
        }

        @Test
        @DisplayName("should keep the super-root role for an administrator without site scoping")
        void shouldKeepSuperRootRole_withoutSiteAccessPrivacy() {
            property("multisites", "on");
            property("multioffice.admin.role.name", "siteroot");
            when(securityInfoManager.hasPrivilege(admin, "_site_access_privacy", "r", null)).thenReturn(false);

            assertThat(service.assignableRoleNames(admin)).contains("siteroot");
        }
    }
}
