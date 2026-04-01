/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was developed for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.servlet.http.HttpSession;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecurityInfoManagerImpl}, the security gatekeeper for CARLOS EMR.
 *
 * <p>This test class comprehensively verifies the role-based access control (RBAC) logic
 * that protects patient health information throughout the application. It covers:</p>
 * <ul>
 *   <li>Role retrieval for logged-in providers</li>
 *   <li>Security object privilege lookups</li>
 *   <li>Hierarchical privilege checking (read, update, write, delete, full access)</li>
 *   <li>Patient-specific privilege overrides and account locking</li>
 *   <li>Null safety and error handling</li>
 *   <li>Interface constant validation</li>
 * </ul>
 *
 * <p>The privilege hierarchy tested here is central to CARLOS EMR security:
 * {@code x} (full) &gt; {@code w} (write) &gt; {@code u} (update) &gt; {@code r} (read),
 * with {@code d} (delete) as an independent privilege and {@code o} (no-rights) as a blocker.</p>
 *
 * @since 2026-02-09
 * @see SecurityInfoManager
 * @see SecurityInfoManagerImpl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SecurityInfoManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("security")
public class SecurityInfoManagerUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecuserroleDao mockSecuserroleDao;

    @Mock
    private SecobjprivilegeDao mockSecobjprivilegeDao;

    @Mock
    private SecObjPrivilegeDao mockSecObjPrivilegeDao;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private HttpSession mockSession;

    private SecurityInfoManagerImpl securityInfoManager;

    private static final String TEST_PROVIDER_NO = "999998";
    private static final String TEST_OBJECT_NAME = "_tickler";
    private static final Integer TEST_DEMOGRAPHIC_NO = 1001;
    private static final String ROLE_DOCTOR = "doctor";
    private static final String ROLE_ADMIN = "admin";

    /**
     * Initializes the test environment before each test method.
     *
     * <p>Registers SecObjPrivilegeDao with SpringUtils (required by
     * OscarRoleObjectPrivilege static access), creates a fresh
     * {@link SecurityInfoManagerImpl} instance with injected dependencies,
     * and configures default stubs: LoggedInInfo returns a known provider number,
     * the provider has a "doctor" role, and no privilege data is loaded (to
     * prevent NPE in OscarRoleObjectPrivilege.getPrivilegeProp).</p>
     */
    @BeforeEach
    void setUp() {
        // Register SecObjPrivilegeDao for OscarRoleObjectPrivilege static access via SpringUtils
        registerMock(SecObjPrivilegeDao.class, mockSecObjPrivilegeDao);

        // Create manager and inject @Autowired dependencies
        securityInfoManager = new SecurityInfoManagerImpl();
        injectDependency(securityInfoManager, "secUserRoleDao", mockSecuserroleDao);
        injectDependency(securityInfoManager, "secobjprivilegeDao", mockSecobjprivilegeDao);

        // Default LoggedInInfo stubs
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER_NO);
        when(mockLoggedInInfo.getSession()).thenReturn(mockSession);

        // Default: provider has "doctor" role
        when(mockSecuserroleDao.findByProviderNo(TEST_PROVIDER_NO))
            .thenReturn(Collections.singletonList(createRole(TEST_PROVIDER_NO, ROLE_DOCTOR)));

        // Default: no privilege data (prevents NPE in OscarRoleObjectPrivilege.getPrivilegeProp)
        when(mockSecObjPrivilegeDao.findByObjectNames(any()))
            .thenReturn(Collections.emptyList());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Creates a {@link Secuserrole} with the given provider number and role name.
     *
     * @param providerNo String the provider number
     * @param roleName String the role name (e.g., "doctor", "admin")
     * @return Secuserrole a configured role instance
     */
    private Secuserrole createRole(String providerNo, String roleName) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        return role;
    }

    /**
     * Creates a {@link SecObjPrivilege} used by OscarRoleObjectPrivilege internally.
     *
     * @param roleUserGroup String the role or user group name
     * @param objectName String the security object name
     * @param privilege String the privilege code (e.g., "r", "w", "x")
     * @param priority int the priority level
     * @return SecObjPrivilege a configured privilege instance
     */
    private SecObjPrivilege createPrivilege(String roleUserGroup, String objectName,
                                            String privilege, int priority) {
        SecObjPrivilegePrimaryKey key = new SecObjPrivilegePrimaryKey(roleUserGroup, objectName);
        SecObjPrivilege priv = new SecObjPrivilege();
        priv.setId(key);
        priv.setPrivilege(privilege);
        priv.setPriority(priority);
        return priv;
    }

    /**
     * Configures the mock SecObjPrivilegeDao to return a privilege entry when
     * OscarRoleObjectPrivilege.getPrivilegeProp() is called for the given object name.
     *
     * @param objectName String the security object name to match
     * @param roleUserGroup String the role that holds the privilege
     * @param privilege String the privilege code
     */
    private void setUpPrivilegeForObject(String objectName, String roleUserGroup, String privilege) {
        when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            if (names.contains(objectName)) {
                return Collections.singletonList(
                    createPrivilege(roleUserGroup, objectName, privilege, 0));
            }
            return Collections.emptyList();
        });
    }

    // =========================================================================
    // Interface Constants
    // =========================================================================

    /** Tests that the interface constants defining privilege levels are correct. */
    @Nested
    @DisplayName("Interface Constants")
    @Tag("read")
    class InterfaceConstants {

        @Test
        @DisplayName("should define READ constant as 'r'")
        void shouldDefineReadConstant_whenInterfaceLoaded() {
            assertThat(SecurityInfoManager.READ).isEqualTo("r");
        }

        @Test
        @DisplayName("should define WRITE constant as 'w'")
        void shouldDefineWriteConstant_whenInterfaceLoaded() {
            assertThat(SecurityInfoManager.WRITE).isEqualTo("w");
        }

        @Test
        @DisplayName("should define UPDATE constant as 'u'")
        void shouldDefineUpdateConstant_whenInterfaceLoaded() {
            assertThat(SecurityInfoManager.UPDATE).isEqualTo("u");
        }

        @Test
        @DisplayName("should define DELETE constant as 'd'")
        void shouldDefineDeleteConstant_whenInterfaceLoaded() {
            assertThat(SecurityInfoManager.DELETE).isEqualTo("d");
        }

        @Test
        @DisplayName("should define NORIGHTS constant as 'o'")
        void shouldDefineNoRightsConstant_whenInterfaceLoaded() {
            assertThat(SecurityInfoManager.NORIGHTS).isEqualTo("o");
        }
    }

    // =========================================================================
    // getRoles
    // =========================================================================

    /** Tests for {@link SecurityInfoManagerImpl#getRoles(LoggedInInfo)}. */
    @Nested
    @DisplayName("getRoles")
    @Tag("read")
    class GetRolesTests {

        @Test
        @DisplayName("should return empty list when loggedInInfo is null")
        void shouldReturnEmptyList_whenLoggedInInfoIsNull() {
            List<Secuserrole> roles = securityInfoManager.getRoles(null);

            assertThat(roles).isEmpty();
            verify(mockSecuserroleDao, never()).findByProviderNo(any());
        }

        @Test
        @DisplayName("should return empty list when provider number is null")
        void shouldReturnEmptyList_whenProviderNumberIsNull() {
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(null);

            List<Secuserrole> roles = securityInfoManager.getRoles(mockLoggedInInfo);

            assertThat(roles).isEmpty();
            verify(mockSecuserroleDao, never()).findByProviderNo(any());
        }

        @Test
        @DisplayName("should return roles from DAO when provider is valid")
        void shouldReturnRolesFromDao_whenProviderIsValid() {
            Secuserrole doctorRole = createRole(TEST_PROVIDER_NO, ROLE_DOCTOR);
            Secuserrole adminRole = createRole(TEST_PROVIDER_NO, ROLE_ADMIN);
            when(mockSecuserroleDao.findByProviderNo(TEST_PROVIDER_NO))
                .thenReturn(Arrays.asList(doctorRole, adminRole));

            List<Secuserrole> roles = securityInfoManager.getRoles(mockLoggedInInfo);

            assertThat(roles).hasSize(2);
            assertThat(roles).containsExactly(doctorRole, adminRole);
            verify(mockSecuserroleDao).findByProviderNo(TEST_PROVIDER_NO);
        }

        @Test
        @DisplayName("should return empty list when provider has no roles")
        void shouldReturnEmptyList_whenProviderHasNoRoles() {
            when(mockSecuserroleDao.findByProviderNo(TEST_PROVIDER_NO))
                .thenReturn(Collections.emptyList());

            List<Secuserrole> roles = securityInfoManager.getRoles(mockLoggedInInfo);

            assertThat(roles).isEmpty();
        }
    }

    // =========================================================================
    // getSecurityObjects
    // =========================================================================

    /** Tests for {@link SecurityInfoManagerImpl#getSecurityObjects(LoggedInInfo)}. */
    @Nested
    @DisplayName("getSecurityObjects")
    @Tag("read")
    class GetSecurityObjectsTests {

        @Test
        @DisplayName("should return security objects for provider roles")
        void shouldReturnSecurityObjects_forProviderRoles() {
            Secobjprivilege privilege = new Secobjprivilege();
            when(mockSecobjprivilegeDao.getByRoles(anyList()))
                .thenReturn(Collections.singletonList(privilege));

            List<Secobjprivilege> result = securityInfoManager.getSecurityObjects(mockLoggedInInfo);

            assertThat(result).hasSize(1);
            assertThat(result).contains(privilege);
        }

        @Test
        @DisplayName("should include both role names and provider number in role list")
        void shouldIncludeRoleNamesAndProviderNo_inRoleList() {
            when(mockSecobjprivilegeDao.getByRoles(anyList()))
                .thenReturn(Collections.emptyList());

            securityInfoManager.getSecurityObjects(mockLoggedInInfo);

            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
            verify(mockSecobjprivilegeDao).getByRoles(captor.capture());

            List<String> roleNames = captor.getValue();
            assertThat(roleNames).contains(ROLE_DOCTOR, TEST_PROVIDER_NO);
        }

        @Test
        @DisplayName("should throw NullPointerException when loggedInInfo is null")
        void shouldThrowNpe_whenLoggedInInfoIsNull() {
            assertThatThrownBy(() -> securityInfoManager.getSecurityObjects(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // hasPrivilege (String demographicNo)
    // =========================================================================

    /** Tests for {@link SecurityInfoManagerImpl#hasPrivilege(LoggedInInfo, String, String, String)}. */
    @Nested
    @DisplayName("hasPrivilege with String demographicNo")
    @Tag("security")
    class HasPrivilegeStringOverloadTests {

        /** Verifies that write privilege grants read, update, and write but not delete. */
        @Nested
        @DisplayName("Write Access Privilege Hierarchy")
        class WriteAccessTests {

            @BeforeEach
            void setUpWriteAccess() {
                setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "w");
            }

            @Test
            @DisplayName("should grant read when role has write privilege")
            void shouldGrantRead_whenRoleHasWritePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant update when role has write privilege")
            void shouldGrantUpdate_whenRoleHasWritePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.UPDATE, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant write when role has write privilege")
            void shouldGrantWrite_whenRoleHasWritePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should deny delete when role has write privilege")
            void shouldDenyDelete_whenRoleHasWritePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.DELETE, (String) null);

                assertThat(result).isFalse();
            }
        }

        /** Verifies that update privilege grants read and update but not write or delete. */
        @Nested
        @DisplayName("Update Access Privilege Hierarchy")
        class UpdateAccessTests {

            @BeforeEach
            void setUpUpdateAccess() {
                setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "u");
            }

            @Test
            @DisplayName("should grant read when role has update privilege")
            void shouldGrantRead_whenRoleHasUpdatePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant update when role has update privilege")
            void shouldGrantUpdate_whenRoleHasUpdatePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.UPDATE, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should deny write when role has update privilege")
            void shouldDenyWrite_whenRoleHasUpdatePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE, (String) null);

                assertThat(result).isFalse();
            }
        }

        /** Verifies that read privilege grants only read access. */
        @Nested
        @DisplayName("Read Access Privilege Hierarchy")
        class ReadAccessTests {

            @BeforeEach
            void setUpReadAccess() {
                setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "r");
            }

            @Test
            @DisplayName("should grant read when role has read privilege")
            void shouldGrantRead_whenRoleHasReadPrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should deny update when role has read privilege")
            void shouldDenyUpdate_whenRoleHasReadPrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.UPDATE, (String) null);

                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should deny write when role has read privilege")
            void shouldDenyWrite_whenRoleHasReadPrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE, (String) null);

                assertThat(result).isFalse();
            }
        }

        /** Verifies that full access (x) grants all privileges. */
        @Nested
        @DisplayName("Full Access Privilege (x)")
        class FullAccessTests {

            @BeforeEach
            void setUpFullAccess() {
                setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "x");
            }

            @Test
            @DisplayName("should grant read when role has full access")
            void shouldGrantRead_whenRoleHasFullAccess() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant write when role has full access")
            void shouldGrantWrite_whenRoleHasFullAccess() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant delete when role has full access")
            void shouldGrantDelete_whenRoleHasFullAccess() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.DELETE, (String) null);

                assertThat(result).isTrue();
            }
        }

        /** Verifies that delete privilege grants only delete access. */
        @Nested
        @DisplayName("Delete Access Privilege Hierarchy")
        class DeleteAccessTests {

            @BeforeEach
            void setUpDeleteAccess() {
                setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "d");
            }

            @Test
            @DisplayName("should grant delete when role has delete privilege")
            void shouldGrantDelete_whenRoleHasDeletePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.DELETE, (String) null);

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should deny read when role has only delete privilege")
            void shouldDenyRead_whenRoleHasOnlyDeletePrivilege() {
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isFalse();
            }
        }

        /** Verifies that access is denied when no privilege entries exist. */
        @Nested
        @DisplayName("No Privilege Data")
        class NoPrivilegeTests {

            @Test
            @DisplayName("should deny access when no privileges exist for object")
            void shouldDenyAccess_whenNoPrivilegesExistForObject() {
                // Default mock returns empty list - no privilege data
                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isFalse();
            }
        }

        /** Verifies patient-specific privilege override behavior. */
        @Nested
        @DisplayName("Patient-Specific Privileges")
        class PatientSpecificTests {

            @Test
            @DisplayName("should fall back to general privileges when no patient-specific role match")
            void shouldFallBackToGeneralPrivileges_whenNoPatientSpecificRoleMatch() {
                // Patient-specific has privilege for a different role; general has write for doctor
                String patientObjName = TEST_OBJECT_NAME + "$" + TEST_DEMOGRAPHIC_NO;
                when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(inv -> {
                    Collection<String> names = inv.getArgument(0);
                    if (names.contains(patientObjName)) {
                        return Collections.singletonList(
                            createPrivilege("nurse", patientObjName, "r", 0));
                    }
                    if (names.contains(TEST_OBJECT_NAME)) {
                        return Collections.singletonList(
                            createPrivilege(ROLE_DOCTOR, TEST_OBJECT_NAME, "w", 0));
                    }
                    return Collections.emptyList();
                });

                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE,
                    String.valueOf(TEST_DEMOGRAPHIC_NO));

                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("should grant read access for patient-specific READ privilege (not lock account)")
            void shouldGrantReadAccess_forPatientSpecificReadPrivilege() {
                // Patient-specific has READ privilege for doctor; must NOT lock account
                String patientObjName = TEST_OBJECT_NAME + "$" + TEST_DEMOGRAPHIC_NO;
                when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(inv -> {
                    Collection<String> names = inv.getArgument(0);
                    if (names.contains(patientObjName)) {
                        return Collections.singletonList(
                            createPrivilege(ROLE_DOCTOR, patientObjName, "r", 0));
                    }
                    return Collections.emptyList();
                });

                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ,
                    String.valueOf(TEST_DEMOGRAPHIC_NO));

                assertThat(result).isTrue();
                verify(mockSession, never()).setAttribute("accountLocked", true);
            }

            @Test
            @DisplayName("should lock account and deny access for patient-specific NORIGHTS")
            void shouldLockAccountAndDenyAccess_forPatientSpecificNoRights() {
                // Patient-specific has NORIGHTS for doctor
                String patientObjName = TEST_OBJECT_NAME + "$" + TEST_DEMOGRAPHIC_NO;
                when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(inv -> {
                    Collection<String> names = inv.getArgument(0);
                    if (names.contains(patientObjName)) {
                        return Collections.singletonList(
                            createPrivilege(ROLE_DOCTOR, patientObjName, "o", 0));
                    }
                    return Collections.emptyList();
                });

                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ,
                    String.valueOf(TEST_DEMOGRAPHIC_NO));

                assertThat(result).isFalse();
                verify(mockSession).setAttribute("accountLocked", true);
            }
        }

        /** Verifies privilege resolution when a provider has multiple roles. */
        @Nested
        @DisplayName("Multiple Roles")
        class MultipleRolesTests {

            @Test
            @DisplayName("should grant access through secondary role when primary has no privilege")
            void shouldGrantAccess_throughSecondaryRole() {
                // Provider has both doctor and admin roles
                when(mockSecuserroleDao.findByProviderNo(TEST_PROVIDER_NO))
                    .thenReturn(Arrays.asList(
                        createRole(TEST_PROVIDER_NO, ROLE_DOCTOR),
                        createRole(TEST_PROVIDER_NO, ROLE_ADMIN)));

                // Only admin role has write privilege on _admin_panel
                setUpPrivilegeForObject("_admin_panel", ROLE_ADMIN, "w");

                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, "_admin_panel", SecurityInfoManager.WRITE, (String) null);

                assertThat(result).isTrue();
            }
        }

        /** Verifies error handling behavior in privilege checking. */
        @Nested
        @DisplayName("Error Handling")
        class ErrorHandlingTests {

            @Test
            @DisplayName("should return false when null loggedInInfo causes exception")
            void shouldReturnFalse_whenNullLoggedInInfoCausesException() {
                boolean result = securityInfoManager.hasPrivilege(
                    (LoggedInInfo) null, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("should propagate PatientDirectiveException without catching")
            void shouldPropagatePatientDirectiveException_whenDirectiveViolated() {
                when(mockSecObjPrivilegeDao.findByObjectNames(any()))
                    .thenThrow(new PatientDirectiveException("Patient directive block"));

                assertThatThrownBy(() -> securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null))
                    .isInstanceOf(PatientDirectiveException.class)
                    .hasMessage("Patient directive block");
            }

            @Test
            @DisplayName("should return false when general exception occurs")
            void shouldReturnFalse_whenGeneralExceptionOccurs() {
                when(mockSecObjPrivilegeDao.findByObjectNames(any()))
                    .thenThrow(new RuntimeException("Database error"));

                boolean result = securityInfoManager.hasPrivilege(
                    mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

                assertThat(result).isFalse();
            }
        }
    }

    // =========================================================================
    // hasPrivilege (int demographicNo)
    // =========================================================================

    /** Tests for {@link SecurityInfoManagerImpl#hasPrivilege(LoggedInInfo, String, String, int)}. */
    @Nested
    @DisplayName("hasPrivilege with int demographicNo")
    @Tag("security")
    class HasPrivilegeIntOverloadTests {

        @Test
        @DisplayName("should delegate to String overload converting int to String")
        void shouldDelegateToStringOverload_convertingIntToString() {
            setUpPrivilegeForObject(TEST_OBJECT_NAME, ROLE_DOCTOR, "w");

            boolean result = securityInfoManager.hasPrivilege(
                mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.READ,
                TEST_DEMOGRAPHIC_NO.intValue());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no privilege exists via int overload")
        void shouldReturnFalse_whenNoPrivilegeExistsViaIntOverload() {
            // Default mock returns empty privilege data
            boolean result = securityInfoManager.hasPrivilege(
                mockLoggedInInfo, TEST_OBJECT_NAME, SecurityInfoManager.WRITE, 5555);

            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // isAllowedAccessToPatientRecord
    // =========================================================================

    /** Tests for {@link SecurityInfoManagerImpl#isAllowedAccessToPatientRecord(LoggedInInfo, Integer)}. */
    @Nested
    @DisplayName("isAllowedAccessToPatientRecord")
    @Tag("security")
    class IsAllowedAccessToPatientRecordTests {

        @Test
        @DisplayName("should allow access when no blocking directives exist")
        void shouldAllowAccess_whenNoBlockingDirectivesExist() {
            // Default mock returns empty list - no NORIGHTS entries
            boolean result = securityInfoManager.isAllowedAccessToPatientRecord(
                mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should deny access when demographic record has NORIGHTS block")
        void shouldDenyAccess_whenDemographicRecordHasNoRightsBlock() {
            String demoObjName = "_demographic$" + TEST_DEMOGRAPHIC_NO;
            when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(inv -> {
                Collection<String> names = inv.getArgument(0);
                if (names.contains(demoObjName)) {
                    return Collections.singletonList(
                        createPrivilege(ROLE_DOCTOR, demoObjName, "o", 0));
                }
                return Collections.emptyList();
            });

            boolean result = securityInfoManager.isAllowedAccessToPatientRecord(
                mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should deny access when eChart record has NORIGHTS block")
        void shouldDenyAccess_whenEChartRecordHasNoRightsBlock() {
            String eChartObjName = "_eChart$" + TEST_DEMOGRAPHIC_NO;
            when(mockSecObjPrivilegeDao.findByObjectNames(any())).thenAnswer(inv -> {
                Collection<String> names = inv.getArgument(0);
                if (names.contains(eChartObjName)) {
                    return Collections.singletonList(
                        createPrivilege(ROLE_DOCTOR, eChartObjName, "o", 0));
                }
                return Collections.emptyList();
            });

            boolean result = securityInfoManager.isAllowedAccessToPatientRecord(
                mockLoggedInInfo, TEST_DEMOGRAPHIC_NO);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw NullPointerException when loggedInInfo is null")
        void shouldThrowNpe_whenLoggedInInfoIsNull() {
            assertThatThrownBy(() ->
                securityInfoManager.isAllowedAccessToPatientRecord(null, TEST_DEMOGRAPHIC_NO))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // Null LoggedInInfo Handling
    // =========================================================================

    /** Cross-cutting tests verifying null safety across all methods. */
    @Nested
    @DisplayName("Null LoggedInInfo Handling")
    @Tag("security")
    class NullLoggedInInfoHandling {

        @Test
        @DisplayName("getRoles should return empty list for null LoggedInInfo")
        void shouldReturnEmptyList_whenLoggedInInfoIsNull() {
            List<Secuserrole> roles = securityInfoManager.getRoles(null);

            assertThat(roles).isEmpty();
        }

        @Test
        @DisplayName("getRoles should return empty list when provider number is null")
        void shouldReturnEmptyList_whenProviderNoIsNull() {
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(null);

            List<Secuserrole> roles = securityInfoManager.getRoles(mockLoggedInInfo);

            assertThat(roles).isEmpty();
        }

        @Test
        @DisplayName("hasPrivilege with String should return false for null LoggedInInfo")
        void hasPrivilegeStringShouldReturnFalse_forNullLoggedInInfo() {
            boolean result = securityInfoManager.hasPrivilege(
                (LoggedInInfo) null, TEST_OBJECT_NAME, SecurityInfoManager.READ, (String) null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("hasPrivilege with int should return false for null LoggedInInfo")
        void hasPrivilegeIntShouldReturnFalse_forNullLoggedInInfo() {
            boolean result = securityInfoManager.hasPrivilege(
                (LoggedInInfo) null, TEST_OBJECT_NAME, SecurityInfoManager.READ, 123);

            assertThat(result).isFalse();
        }
    }
}
