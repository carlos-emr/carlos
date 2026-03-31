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

import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.dao.ConsentTypeDao;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PatientConsentManagerImpl} patient consent logic.
 *
 * <p>Tests consent add/revoke workflows, consent type lookups,
 * consent-by-demographic queries, and security enforcement.</p>
 *
 * @since 2026-03-31
 * @see PatientConsentManagerImpl
 * @see PatientConsentManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PatientConsentManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("consent")
class PatientConsentManagerUnitTest extends CarlosUnitTestBase {

    @Mock private ConsentDao mockConsentDao;
    @Mock private ConsentTypeDao mockConsentTypeDao;
    @Mock private SecurityInfoManager mockSecurityInfoManager;

    private PatientConsentManagerImpl manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(ConsentDao.class, mockConsentDao);
        registerMock(ConsentTypeDao.class, mockConsentTypeDao);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        manager = new PatientConsentManagerImpl();
        injectDependency(manager, "consentDao", mockConsentDao);
        injectDependency(manager, "consentTypeDao", mockConsentTypeDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // Grant write privilege by default
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq(SecurityInfoManager.WRITE), any()))
                .thenReturn(true);
    }

    private ConsentType createActiveConsentType(int id, String type) {
        ConsentType ct = new ConsentType();
        ct.setId(id);
        ct.setType(type);
        ct.setActive(true);
        return ct;
    }

    // -----------------------------------------------------------------------
    // addEditConsentRecord
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addEditConsentRecord")
    class AddEditConsentRecord {

        @Test
        @DisplayName("should create new consent when none exists for demographic")
        void shouldCreateNewConsent_whenNoneExists() {
            ConsentType ct = createActiveConsentType(1, "PROVIDER_CONSENT_FILTER");
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findByDemographicAndConsentType(100, ct)).thenReturn(null);

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isTrue();
            verify(mockConsentDao).persist(any(Consent.class));
        }

        @Test
        @DisplayName("should merge existing consent when already exists")
        void shouldMergeExistingConsent_whenAlreadyExists() {
            ConsentType ct = createActiveConsentType(1, "PROVIDER_CONSENT_FILTER");
            Consent existing = new Consent();
            existing.setId(10);
            existing.setConsentType(ct);
            existing.setDemographicNo(100);
            existing.setOptout(false);
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findByDemographicAndConsentType(100, ct)).thenReturn(existing);

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(result).isTrue();
            verify(mockConsentDao).merge(existing);
        }

        @Test
        @DisplayName("should return false when consent type not found")
        void shouldReturnFalse_whenConsentTypeNotFound() {
            when(mockConsentTypeDao.find(999)).thenReturn(null);

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 999, true, false);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when consent type is inactive")
        void shouldReturnFalse_whenConsentTypeInactive() {
            ConsentType ct = createActiveConsentType(1, "INACTIVE");
            ct.setActive(false);
            when(mockConsentTypeDao.find(1)).thenReturn(ct);

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw when write privilege denied")
        void shouldThrow_whenWritePrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), any()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorised Access");
        }
    }

    // -----------------------------------------------------------------------
    // setConsent
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("setConsent")
    class SetConsent {

        @Test
        @DisplayName("should call addConsent when consenting")
        void shouldCallAddConsent_whenConsenting() {
            ConsentType ct = createActiveConsentType(1, "TEST");
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findByDemographicAndConsentType(100, ct)).thenReturn(null);

            manager.setConsent(loggedInInfo, 100, 1, true);

            verify(mockConsentDao).persist(any(Consent.class));
        }
    }

    // -----------------------------------------------------------------------
    // optoutConsent
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("optoutConsent")
    class OptoutConsent {

        @Test
        @DisplayName("should set optout flag and merge when consent exists")
        void shouldSetOptoutFlagAndMerge_whenConsentExists() {
            Consent consent = new Consent();
            consent.setId(10);
            consent.setOptout(false);
            when(mockConsentDao.find(10)).thenReturn(consent);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(consent.isOptout()).isTrue();
            assertThat(consent.getOptoutDate()).isNotNull();
            verify(mockConsentDao).merge(consent);
        }

        @Test
        @DisplayName("should do nothing when consent not found by ID")
        void shouldDoNothing_whenConsentNotFound() {
            when(mockConsentDao.find(999)).thenReturn(null);

            manager.optoutConsent(loggedInInfo, 999);

            verify(mockConsentDao, never()).merge(any());
        }

        @Test
        @DisplayName("should handle null Consent object gracefully")
        void shouldHandleNullConsent_gracefully() {
            manager.optoutConsent(loggedInInfo, (Consent) null);

            verify(mockConsentDao, never()).merge(any());
        }

        @Test
        @DisplayName("should throw when write privilege denied for optout by ID")
        void shouldThrow_whenWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.optoutConsent(loggedInInfo, 10))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getConsentTypes
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getConsentTypes")
    class GetConsentTypes {

        @Test
        @DisplayName("should return all consent types when count > 0")
        void shouldReturnAllTypes_whenCountPositive() {
            when(mockConsentTypeDao.getCountAll()).thenReturn(2);
            List<ConsentType> expected = List.of(createActiveConsentType(1, "A"), createActiveConsentType(2, "B"));
            when(mockConsentTypeDao.findAll(0, 2)).thenReturn(expected);

            List<ConsentType> result = manager.getConsentTypes();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return null when no consent types exist")
        void shouldReturnNull_whenNoTypesExist() {
            when(mockConsentTypeDao.getCountAll()).thenReturn(0);

            List<ConsentType> result = manager.getConsentTypes();

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // getActiveConsentTypes
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getActiveConsentTypes")
    class GetActiveConsentTypes {

        @Test
        @DisplayName("should delegate to DAO findAllActive")
        void shouldDelegateToDao() {
            List<ConsentType> expected = List.of(createActiveConsentType(1, "ACTIVE"));
            when(mockConsentTypeDao.findAllActive()).thenReturn(expected);

            List<ConsentType> result = manager.getActiveConsentTypes();

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // getConsentType by string
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getConsentType")
    class GetConsentTypeByString {

        @Test
        @DisplayName("should return consent type by type string")
        void shouldReturnType_byTypeString() {
            ConsentType expected = createActiveConsentType(1, "PROVIDER_CONSENT_FILTER");
            when(mockConsentTypeDao.findConsentType("PROVIDER_CONSENT_FILTER")).thenReturn(expected);

            ConsentType result = manager.getConsentType("PROVIDER_CONSENT_FILTER");

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should return null when type not found")
        void shouldReturnNull_whenTypeNotFound() {
            when(mockConsentTypeDao.findConsentType("NONEXISTENT")).thenReturn(null);

            ConsentType result = manager.getConsentType("NONEXISTENT");

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // addConsentType
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addConsentType")
    class AddConsentType {

        @Test
        @DisplayName("should persist consent type and return it")
        void shouldPersistAndReturn() {
            ConsentType ct = createActiveConsentType(0, "NEW_TYPE");

            ConsentType result = manager.addConsentType(loggedInInfo, ct);

            assertThat(result).isSameAs(ct);
            verify(mockConsentTypeDao).persist(ct);
        }
    }
}
