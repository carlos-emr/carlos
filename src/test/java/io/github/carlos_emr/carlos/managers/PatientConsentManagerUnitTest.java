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

        // Grant demographic privileges by default. SecurityInfoManager overloads int and String targets.
        // Shared permission default; individual tests exercise different privilege overloads.
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), anyString(), anyInt()))
                .thenReturn(true);
        // Shared permission default; individual tests exercise different privilege overloads.
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), anyString(), nullable(String.class)))
                .thenReturn(true);
    }

    private ConsentType createActiveConsentType(int id, String type) {
        ConsentType ct = new ConsentType();
        ct.setId(id);
        ct.setType(type);
        ct.setActive(true);
        return ct;
    }

    private Consent consent(int id, boolean optout, java.util.Date editDate) {
        Consent consent = new Consent();
        setConsentId(consent, id);
        consent.setDemographicNo(100);
        consent.setOptout(optout);
        consent.setEditDate(editDate);
        return consent;
    }

    private void setConsentId(Consent consent, Integer id) {
        try {
            java.lang.reflect.Field idField = Consent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(consent, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set Consent ID for test fixture", e);
        }
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
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, ct.getId())).thenReturn(java.util.List.of());

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isTrue();
            verify(mockConsentDao).persist(any(Consent.class));
        }

        @Test
        @DisplayName("should merge existing consent when already exists")
        void shouldMergeExistingConsent_whenAlreadyExists() {
            ConsentType ct = createActiveConsentType(1, "PROVIDER_CONSENT_FILTER");
            Consent existing = new Consent();
            setConsentId(existing, 10);
            existing.setConsentType(ct);
            existing.setDemographicNo(100);
            existing.setOptout(false);
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, ct.getId())).thenReturn(java.util.List.of(existing));

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(result).isTrue();
            verify(mockConsentDao).merge(existing);
        }

        @Test
        @DisplayName("should edit the deciding record and retire the other live duplicates")
        void shouldRetireOtherLiveDuplicates_whenSavingConsent() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent newerOptIn = consent(11, false, new java.util.Date(2_000L));
            Consent olderOptOut = consent(12, true, new java.util.Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1))
                    .thenReturn(new java.util.ArrayList<>(java.util.List.of(newerOptIn, olderOptOut)));

            // Staff opt the patient in. The opt-out decided (and was shown), so it is the one edited.
            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isTrue();
            assertThat(olderOptOut.isOptout()).isFalse();
            assertThat(olderOptOut.isDeleted()).isFalse();
            assertThat(newerOptIn.isDeleted()).isTrue();
            verify(mockConsentDao).merge(olderOptOut);
            verify(mockConsentDao).merge(newerOptIn);
        }

        @Test
        @DisplayName("should retire nothing when the patient has a single live record")
        void shouldNotRetireAnything_whenOnlyOneLiveRecordExists() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent only = consent(11, false, new java.util.Date(2_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(java.util.List.of(only));

            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(only.isDeleted()).isFalse();
            verify(mockConsentDao, org.mockito.Mockito.times(1)).merge(any(Consent.class));
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
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
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
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, ct.getId())).thenReturn(java.util.List.of());

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
            setConsentId(consent, 10);
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
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
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
        void shouldDelegateToDao_forTheLookup() {
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
        void shouldPersistAndReturn_theSavedEntity() {
            ConsentType ct = createActiveConsentType(0, "NEW_TYPE");

            ConsentType result = manager.addConsentType(loggedInInfo, ct);

            assertThat(result).isSameAs(ct);
            verify(mockConsentTypeDao).persist(ct);
        }
    }

    @Nested
    @DisplayName("deleteConsent")
    class DeleteConsent {

        @Test
        @DisplayName("should delete every live record, not just one, so no duplicate keeps deciding")
        void shouldDeleteEveryLiveRecord_whenDuplicatesExist() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent first = consent(11, false, new java.util.Date(2_000L));
            Consent second = consent(12, true, new java.util.Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(java.util.List.of(first, second));

            manager.deleteConsent(loggedInInfo, 100, 1);

            assertThat(first.isDeleted()).isTrue();
            assertThat(second.isDeleted()).isTrue();
            verify(mockConsentDao).merge(first);
            verify(mockConsentDao).merge(second);
        }

        @Test
        @DisplayName("should throw and change nothing when write privilege denied")
        void shouldThrow_whenWritePrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.deleteConsent(loggedInInfo, 100, 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorised Access");
            verifyNoInteractions(mockConsentDao);
        }
    }

    @Nested
    @DisplayName("recordExplicitConsent")
    class RecordExplicitConsent {

        private Consent impliedOptIn() {
            Consent consent = consent(21, false, new Date(1_000L));
            consent.setExplicit(false);
            return consent;
        }

        @Test
        @DisplayName("should mark an implied opt-in explicit and stamp its dates and author")
        void shouldUpgradeImpliedOptIn_toExplicit() {
            Consent implied = impliedOptIn();
            when(mockConsentTypeDao.find(1)).thenReturn(createActiveConsentType(1, "email"));
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(List.of(implied));
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            boolean result = manager.recordExplicitConsent(loggedInInfo, 100, 1);

            assertThat(result).isTrue();
            assertThat(implied.isExplicit()).isTrue();
            assertThat(implied.getConsentDate()).isAfter(new Date(1_000L));
            assertThat(implied.getEditDate()).isAfter(new Date(1_000L));
            assertThat(implied.getLastEnteredBy()).isEqualTo("999998");
            verify(mockConsentDao).merge(implied);
        }

        @Test
        @DisplayName("should change nothing when the record is already explicit")
        void shouldLeaveRecordUntouched_whenAlreadyExplicit() {
            Consent explicit = consent(22, false, new Date(1_000L));
            explicit.setExplicit(true);
            when(mockConsentTypeDao.find(1)).thenReturn(createActiveConsentType(1, "email"));
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(List.of(explicit));

            assertThat(manager.recordExplicitConsent(loggedInInfo, 100, 1)).isTrue();
            assertThat(explicit.getEditDate()).isEqualTo(new Date(1_000L));
            verify(mockConsentDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse to confirm consent the patient opted out of")
        void shouldNotUpgrade_whenPatientOptedOut() {
            Consent optedOut = consent(23, true, new Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(createActiveConsentType(1, "email"));
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(List.of(optedOut));

            assertThat(manager.recordExplicitConsent(loggedInInfo, 100, 1)).isFalse();
            assertThat(optedOut.isExplicit()).isFalse();
            verify(mockConsentDao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse when there is no live record to confirm")
        void shouldNotUpgrade_whenNoLiveRecordExists() {
            when(mockConsentTypeDao.find(1)).thenReturn(createActiveConsentType(1, "email"));
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(List.of());

            assertThat(manager.recordExplicitConsent(loggedInInfo, 100, 1)).isFalse();
            verify(mockConsentDao, never()).merge(any());
            verify(mockConsentDao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse for an inactive consent type")
        void shouldNotUpgrade_whenConsentTypeInactive() {
            ConsentType inactive = createActiveConsentType(1, "email");
            inactive.setActive(false);
            when(mockConsentTypeDao.find(1)).thenReturn(inactive);

            assertThat(manager.recordExplicitConsent(loggedInInfo, 100, 1)).isFalse();
            verifyNoInteractions(mockConsentDao);
        }

        @Test
        @DisplayName("should throw and change nothing when write privilege denied")
        void shouldThrow_whenWritePrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.recordExplicitConsent(loggedInInfo, 100, 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorised Access");
            verifyNoInteractions(mockConsentDao);
        }

        @Test
        @DisplayName("should keep a routine save from upgrading an implied record")
        void shouldKeepImplied_whenChartIsResavedWithOptIn() {
            Consent implied = impliedOptIn();
            when(mockConsentTypeDao.find(1)).thenReturn(createActiveConsentType(1, "email"));
            when(mockConsentDao.findLiveByDemographicAndConsentTypeId(100, 1)).thenReturn(List.of(implied));

            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(implied.isExplicit()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAllConsentsByDemographic")
    class GetAllConsentsByDemographic {

        @Test
        @DisplayName("should return one deciding record per consent type")
        void shouldReturnEffectiveRecord_perConsentType() {
            Consent emailOptIn = consent(31, false, new Date(3_000L));
            emailOptIn.setConsentTypeId(1);
            Consent emailOptOut = consent(32, true, new Date(1_000L));
            emailOptOut.setConsentTypeId(1);
            Consent smsOptIn = consent(33, false, new Date(2_000L));
            smsOptIn.setConsentTypeId(2);
            when(mockConsentDao.findByDemographic(100)).thenReturn(List.of(emailOptIn, smsOptIn, emailOptOut));

            List<Consent> result = manager.getAllConsentsByDemographic(loggedInInfo, 100);

            assertThat(result).containsExactly(emailOptOut, smsOptIn);
        }

        @Test
        @DisplayName("should return an empty list when the patient has no consent records")
        void shouldReturnEmptyList_whenNoRecords() {
            when(mockConsentDao.findByDemographic(100)).thenReturn(List.of());

            assertThat(manager.getAllConsentsByDemographic(loggedInInfo, 100)).isEmpty();
        }

        @Test
        @DisplayName("should throw when read privilege denied")
        void shouldThrow_whenReadPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.getAllConsentsByDemographic(loggedInInfo, 100))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorised Access");
            verifyNoInteractions(mockConsentDao);
        }
    }
}
