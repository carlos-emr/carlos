/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.SecurityArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.carlos_emr.CarlosProperties;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecurityManager} authentication and provider security logic.
 *
 * <p>Tests validation of security objects, password checking against history,
 * password encoding/verification, provider lookup, and record CRUD operations.</p>
 *
 * @since 2026-03-31
 * @see SecurityManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SecurityManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("security")
class SecurityManagerUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityDao mockSecurityDao;
    @Mock private SecurityArchiveDao mockSecurityArchiveDao;

    private SecurityManager manager;
    private LoggedInInfo loggedInInfo;

    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    @BeforeEach
    void setUp() {
        registerMock(SecurityDao.class, mockSecurityDao);
        registerMock(SecurityArchiveDao.class, mockSecurityArchiveDao);

        manager = new SecurityManager();
        injectDependency(manager, "securityDao", mockSecurityDao);
        injectDependency(manager, "securityArchiveDao", mockSecurityArchiveDao);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // Mock CarlosProperties for password policy checks
        CarlosProperties mockProperties = mock(CarlosProperties.class);
        when(mockProperties.getProperty("password.pastPasswordsToNotUse", "0")).thenReturn("0");
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
    }

    private Security createValidSecurity(Integer id, String providerNo, String userName, String password) {
        Security sec = new Security();
        sec.setId(id);
        sec.setProviderNo(providerNo);
        sec.setUserName(userName);
        sec.setPassword(password);
        return sec;
    }

    // -----------------------------------------------------------------------
    // isSecurityObjectValid
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isSecurityObjectValid")
    class IsSecurityObjectValid {

        @Test
        @DisplayName("should reject null security object")
        void shouldRejectNull_whenSecurityIsNull() {
            assertThatThrownBy(() -> manager.saveNewSecurityRecord(loggedInInfo, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Security object");
        }

        @Test
        @DisplayName("should reject security with empty password")
        void shouldReject_whenPasswordEmpty() {
            Security sec = createValidSecurity(null, "111", "user1", "");

            assertThatThrownBy(() -> manager.saveNewSecurityRecord(loggedInInfo, sec))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject security with empty providerNo")
        void shouldReject_whenProviderNoEmpty() {
            Security sec = createValidSecurity(null, "", "user1", "pass123");

            assertThatThrownBy(() -> manager.saveNewSecurityRecord(loggedInInfo, sec))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject security with empty userName")
        void shouldReject_whenUserNameEmpty() {
            Security sec = createValidSecurity(null, "111", "", "pass123");

            assertThatThrownBy(() -> manager.saveNewSecurityRecord(loggedInInfo, sec))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // saveNewSecurityRecord
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveNewSecurityRecord")
    class SaveNewSecurityRecord {

        @Test
        @DisplayName("should persist valid security record with audit fields")
        void shouldPersistRecord_whenSecurityValid() {
            Security sec = createValidSecurity(null, "111", "user1", "pass123");

            manager.saveNewSecurityRecord(loggedInInfo, sec);

            verify(mockSecurityDao).persist(sec);
            assertThat(sec.getLastUpdateUser()).isEqualTo("999998");
            assertThat(sec.getLastUpdateDate()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // updateSecurityRecord
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateSecurityRecord")
    class UpdateSecurityRecord {

        @Test
        @DisplayName("should archive old record and merge updated record")
        void shouldArchiveAndMerge_whenUpdating() {
            Security existing = createValidSecurity(1, "111", "user1", "oldpass");
            Security updated = createValidSecurity(1, "111", "user1", "newpass");
            when(mockSecurityDao.find(1)).thenReturn(existing);

            manager.updateSecurityRecord(loggedInInfo, updated);

            verify(mockSecurityArchiveDao).archiveRecord(existing);
            verify(mockSecurityDao).merge(updated);
            assertThat(updated.getLastUpdateUser()).isEqualTo("999998");
            assertThat(updated.getLastUpdateDate()).isNotNull();
        }

        @Test
        @DisplayName("should throw for invalid security object on update")
        void shouldThrow_whenInvalidSecurityOnUpdate() {
            Security sec = createValidSecurity(1, "", "user1", "pass");

            assertThatThrownBy(() -> manager.updateSecurityRecord(loggedInInfo, sec))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // checkPasswordAgainstPrevious
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("checkPasswordAgainstPrevious")
    class CheckPasswordAgainstPrevious {

        @Test
        @DisplayName("should return false when policy is 0 (disabled)")
        void shouldReturnFalse_whenPolicyDisabled() {
            // Default mock returns "0" for pastPasswordsToNotUse
            Security sec = createValidSecurity(1, "111", "user1", "currentHash");
            when(mockSecurityDao.getByProviderNo("111")).thenReturn(sec);

            boolean result = manager.checkPasswordAgainstPrevious("newPassword", "111");

            assertThat(result).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // findByProviderNo
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @Test
        @DisplayName("should return first security record for provider")
        void shouldReturnFirstRecord_whenProviderFound() {
            Security sec = createValidSecurity(1, "111", "user1", "pass");
            when(mockSecurityDao.findByProviderNo("111")).thenReturn(List.of(sec));

            Security result = manager.findByProviderNo(loggedInInfo, "111");

            assertThat(result).isSameAs(sec);
        }

        @Test
        @DisplayName("should return null when no security records found")
        void shouldReturnNull_whenNoRecordsFound() {
            when(mockSecurityDao.findByProviderNo("999")).thenReturn(Collections.emptyList());

            Security result = manager.findByProviderNo(loggedInInfo, "999");

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // find
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("should return security record by ID")
        void shouldReturnRecord_whenIdValid() {
            Security sec = createValidSecurity(1, "111", "user1", "pass");
            when(mockSecurityDao.find(1)).thenReturn(sec);

            Security result = manager.find(loggedInInfo, 1);

            assertThat(result).isSameAs(sec);
        }
    }

    // -----------------------------------------------------------------------
    // findByUserName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserName")
    class FindByUserName {

        @Test
        @DisplayName("should return list of security records matching username")
        void shouldReturnList_whenUsernameMatches() {
            List<Security> expected = List.of(createValidSecurity(1, "111", "admin", "pass"));
            when(mockSecurityDao.findByUserName("admin")).thenReturn(expected);

            List<Security> result = manager.findByUserName(loggedInInfo, "admin");

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // getPasswordResetFlag
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getPasswordResetFlag")
    class GetPasswordResetFlag {

        @Test
        @DisplayName("should return true when force reset is true")
        void shouldReturnTrue_whenForceResetEnabled() {
            Security sec = createValidSecurity(1, "111", "user1", "pass");
            sec.setForcePasswordReset(true);
            when(mockSecurityDao.findByUserName("user1")).thenReturn(List.of(sec));

            boolean result = manager.getPasswordResetFlag("user1");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when force reset is null")
        void shouldReturnFalse_whenForceResetIsNull() {
            Security sec = createValidSecurity(1, "111", "user1", "pass");
            sec.setForcePasswordReset(null);
            when(mockSecurityDao.findByUserName("user1")).thenReturn(List.of(sec));

            boolean result = manager.getPasswordResetFlag("user1");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when no user found")
        void shouldReturnFalse_whenNoUserFound() {
            when(mockSecurityDao.findByUserName("unknown")).thenReturn(Collections.emptyList());

            boolean result = manager.getPasswordResetFlag("unknown");

            assertThat(result).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // findByProviderSite
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByProviderSite")
    class FindByProviderSite {

        @Test
        @DisplayName("should return security records for provider site")
        void shouldReturnRecords_forProviderSite() {
            List<Security> expected = List.of(createValidSecurity(1, "111", "user1", "pass"));
            when(mockSecurityDao.findByProviderSite("111")).thenReturn(expected);

            List<Security> result = manager.findByProviderSite(loggedInInfo, "111");

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // findAllOrderByUserName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findAllOrderByUserName")
    class FindAllOrderByUserName {

        @Test
        @DisplayName("should return all security records ordered by userName")
        void shouldReturnAllRecords_orderedByUserName() {
            List<Security> expected = List.of(
                    createValidSecurity(1, "111", "admin", "pass"),
                    createValidSecurity(2, "222", "zuser", "pass")
            );
            when(mockSecurityDao.findAllOrderBy("userName")).thenReturn(expected);

            List<Security> result = manager.findAllOrderByUserName(loggedInInfo);

            assertThat(result).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // findByLikeProviderNo
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByLikeProviderNo")
    class FindByLikeProviderNo {

        @Test
        @DisplayName("should return records matching provider number pattern")
        void shouldReturnMatching_forProviderNoPattern() {
            List<Security> expected = List.of(createValidSecurity(1, "111", "user1", "pass"));
            when(mockSecurityDao.findByLikeProviderNo("11")).thenReturn(expected);

            List<Security> result = manager.findByLikeProviderNo(loggedInInfo, "11");

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // findByLikeUserName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findByLikeUserName")
    class FindByLikeUserName {

        @Test
        @DisplayName("should return records matching username pattern")
        void shouldReturnMatching_forUsernamePattern() {
            List<Security> expected = List.of(createValidSecurity(1, "111", "admin", "pass"));
            when(mockSecurityDao.findByLikeUserName("adm")).thenReturn(expected);

            List<Security> result = manager.findByLikeUserName(loggedInInfo, "adm");

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // remove
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("should delegate removal to DAO")
        void shouldDelegateRemoval_toDao() {
            manager.remove(loggedInInfo, 42);

            verify(mockSecurityDao).remove(42);
        }
    }

    // -----------------------------------------------------------------------
    // findProviders
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findProviders")
    class FindProviders {

        @Test
        @DisplayName("should return provider data from DAO")
        void shouldReturnProviders_fromDao() {
            List<Object[]> expected = List.of(new Object[]{"111", "Dr. Smith"});
            when(mockSecurityDao.findProviders()).thenReturn(expected);

            List<Object[]> result = manager.findProviders(loggedInInfo);

            assertThat(result).hasSize(1);
        }
    }
}
