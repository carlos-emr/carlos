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
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    private Consent consent(int id, boolean optout, Date editDate) {
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
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, ct.getId())).thenReturn(List.of());

            // As the database does on insert: the id exists once persist returns.
            doAnswer(invocation -> {
                setConsentId(invocation.getArgument(0), 31);
                return null;
            }).when(mockConsentDao).persist(any(Consent.class));

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isTrue();
            verify(mockConsentDao).persist(any(Consent.class));
            // A first decision is audited with the saved record's id, after it is saved.
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("PatientConsentManager.changeConsent"), eq("consent"), eq("31"), eq(100),
                    eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: 31 Choice: none->opt-in")));
        }

        @Test
        @DisplayName("should lock the patient before reading the records it edits")
        void shouldLockPatientBeforeReading_whenSaving() {
            ConsentType ct = createActiveConsentType(1, "email");
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of());

            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            InOrder order = inOrder(mockConsentDao);
            order.verify(mockConsentDao).lockPatientForConsentChange(100);
            order.verify(mockConsentDao).findLiveByDemographicAndConsentTypeIdForUpdate(100, 1);
            order.verify(mockConsentDao).persist(any(Consent.class));
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
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, ct.getId())).thenReturn(List.of(existing));

            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(result).isTrue();
            verify(mockConsentDao).merge(existing);
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("PatientConsentManager.changeConsent"), eq("consent"), eq("10"), eq(100),
                    eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: 10 Choice: opt-in->opt-out")));
        }

        @Test
        @DisplayName("should edit the deciding record and retire the other live duplicates")
        void shouldRetireOtherLiveDuplicates_whenSavingConsent() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent newerOptIn = consent(11, false, new Date(2_000L));
            Consent olderOptOut = consent(12, true, new Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1))
                    .thenReturn(List.of(newerOptIn, olderOptOut));

            // Staff opt the patient in. The opt-out decided (and was shown), so it is the one edited.
            boolean result = manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(result).isTrue();
            assertThat(olderOptOut.isOptout()).isFalse();
            assertThat(olderOptOut.isDeleted()).isFalse();
            assertThat(newerOptIn.isDeleted()).isTrue();
            verify(mockConsentDao).merge(olderOptOut);
            verify(mockConsentDao).merge(newerOptIn);
            // The reversed decision is audited against the patient and the record, with both values.
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("PatientConsentManager.changeConsent"), eq("consent"), eq("12"), eq(100),
                    eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: 12 Choice: opt-out->opt-in")));
        }

        @Test
        @DisplayName("should keep the older explicit opt-in and retire a newer implied duplicate on a routine save")
        void shouldKeepExplicitRecord_whenNewerDuplicateIsImplied() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent olderExplicit = consent(11, false, new Date(1_000L));
            olderExplicit.setExplicit(true);
            Consent newerImplied = consent(12, false, new Date(2_000L));
            newerImplied.setExplicit(false);
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1))
                    .thenReturn(List.of(newerImplied, olderExplicit));

            // The chart showed the explicit opt-in, and an unrelated save re-posts it.
            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, false);

            assertThat(olderExplicit.isDeleted()).isFalse();
            assertThat(olderExplicit.isExplicit()).isTrue();
            assertThat(olderExplicit.getEditDate()).isEqualTo(new Date(1_000L));
            assertThat(newerImplied.isDeleted()).isTrue();
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("PatientConsentManager.retireDuplicateConsent"), eq("consent"), eq("12"), eq(100),
                    eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: 12 KeptConsentId: 11")));
        }

        @Test
        @DisplayName("should retire a duplicate without rewriting its author or dates, and audit-log it")
        void shouldKeepAuthorAndDates_whenRetiringDuplicate() {
            ConsentType ct = createActiveConsentType(1, "email");
            Date enteredAt = new Date(2_000L);
            Consent newerOptIn = consent(11, false, enteredAt);
            newerOptIn.setLastEnteredBy("clerk2");
            Consent olderOptOut = consent(12, true, new Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(newerOptIn, olderOptOut));

            // An unrelated chart save re-submits the shown opt-out; no one chose to change the opt-in.
            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(newerOptIn.isDeleted()).isTrue();
            assertThat(newerOptIn.getLastEnteredBy()).isEqualTo("clerk2");
            assertThat(newerOptIn.getEditDate()).isSameAs(enteredAt);
            // The shown choice was re-posted unchanged, so no change of decision is audited.
            logActionMock.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class),
                    eq("PatientConsentManager.changeConsent"), anyString(), anyString(), anyInt(), anyString()), never());
            logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                    eq("PatientConsentManager.retireDuplicateConsent"), eq("consent"), eq("11"), eq(100),
                    eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: 11 KeptConsentId: 12")));
        }

        @Test
        @DisplayName("should retire nothing when the patient has a single live record")
        void shouldNotRetireAnything_whenOnlyOneLiveRecordExists() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent only = consent(11, false, new Date(2_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(only));

            manager.addEditConsentRecord(loggedInInfo, 100, 1, true, true);

            assertThat(only.isDeleted()).isFalse();
            verify(mockConsentDao, times(1)).merge(any(Consent.class));
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
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
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
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, ct.getId())).thenReturn(List.of());

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
            Consent consent = consent(10, false, new Date(1_000L));
            when(mockConsentDao.find(10)).thenReturn(consent);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(consent.isOptout()).isTrue();
            assertThat(consent.getOptoutDate()).isNotNull();
            verify(mockConsentDao).merge(consent);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("should audit the refreshed prior choice when opting out by ID")
        void shouldAuditRefreshedChoice_whenOptingOutById(boolean priorOptout) {
            Consent named = consent(10, !priorOptout, new Date(1_000L));
            when(mockConsentDao.find(10)).thenReturn(named);
            doAnswer(invocation -> {
                named.setOptout(priorOptout);
                return null;
            }).when(mockConsentDao).refresh(named);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(named.isOptout()).isTrue();
            String priorChoice = priorOptout ? "opt-out" : "opt-in";
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", "consent", "10", 100,
                    " ConsentId: 10 Choice: " + priorChoice + "->opt-out"));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("should retire and audit duplicates when opting out by record ID or object")
        void shouldRetireDuplicates_whenOptingOutByRecord(boolean useObjectOverload) {
            Consent named = consent(10, false, new Date(1_000L));
            named.setConsentTypeId(1);
            named.setExplicit(true);
            Date duplicateDate = new Date(2_000L);
            Consent duplicate = consent(11, false, duplicateDate);
            duplicate.setConsentTypeId(1);
            duplicate.setLastEnteredBy("original-author");
            when(mockConsentDao.find(10)).thenReturn(named);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1))
                    .thenReturn(List.of(duplicate, named));

            if (useObjectOverload) manager.optoutConsent(loggedInInfo, named);
            else manager.optoutConsent(loggedInInfo, 10);

            assertThat(named.isOptout()).isTrue();
            assertThat(named.isDeleted()).isFalse();
            assertThat(named.isExplicit()).isTrue();
            assertThat(duplicate.isDeleted()).isTrue();
            assertThat(duplicate.isOptout()).isFalse();
            assertThat(duplicate.getEditDate()).isEqualTo(duplicateDate);
            assertThat(duplicate.getLastEnteredBy()).isEqualTo("original-author");
            InOrder order = inOrder(mockConsentDao);
            order.verify(mockConsentDao).lockPatientForConsentChange(100);
            order.verify(mockConsentDao).refresh(named);
            order.verify(mockConsentDao).findLiveByDemographicAndConsentTypeIdForUpdate(100, 1);
            order.verify(mockConsentDao).merge(named);
            order.verify(mockConsentDao).merge(duplicate);
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", "consent", "10", 100,
                    " ConsentId: 10 Choice: opt-in->opt-out"));
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.retireDuplicateConsent", "consent", "11", 100,
                    " Demographic: 100 ConsentTypeId: 1 ConsentId: 11 KeptConsentId: 10"));
        }

        @Test
        @DisplayName("should opt out an untyped record without grouping other untyped records")
        void shouldNotGroupDuplicates_whenRecordHasNoConsentType() {
            Consent untyped = consent(10, false, new Date(1_000L));
            when(mockConsentDao.find(10)).thenReturn(untyped);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(untyped.isOptout()).isTrue();
            verify(mockConsentDao).merge(untyped);
            verify(mockConsentDao, never()).findLiveByDemographicAndConsentTypeIdForUpdate(anyInt(), anyInt());
        }

        @Test
        @DisplayName("should lock the patient and re-read the record before opting it out by ID")
        void shouldLockAndReread_beforeOptingOutById() {
            Consent consent = consent(10, false, new Date(1_000L));
            when(mockConsentDao.find(10)).thenReturn(consent);

            manager.optoutConsent(loggedInInfo, 10);

            InOrder order = inOrder(mockConsentDao);
            order.verify(mockConsentDao).find(10);
            order.verify(mockConsentDao).lockPatientForConsentChange(100);
            order.verify(mockConsentDao).refresh(consent);
            order.verify(mockConsentDao).merge(consent);
            // Audited once, as an opt-out filed under the patient, after the outcome is known.
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", "consent", "10", 100,
                    " ConsentId: 10 Choice: opt-in->opt-out"));
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", "consent", "10", 100,
                    " ConsentId: 10 skipped: no live record"), never());
        }

        @Test
        @DisplayName("should leave a record alone when a concurrent clear retired it before the lock")
        void shouldNotReviveRecord_whenRetiredBeforeLock() {
            Consent consent = consent(10, false, new Date(1_000L));
            consent.setConsentTypeId(1);
            when(mockConsentDao.find(10)).thenReturn(consent);
            // The re-read after the lock sees the clear another request committed meanwhile.
            doAnswer(invocation -> {
                consent.setDeleted(true);
                return null;
            }).when(mockConsentDao).refresh(consent);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(consent.isOptout()).isFalse();
            verify(mockConsentDao, never()).merge(any());
            verify(mockConsentDao, never()).findLiveByDemographicAndConsentTypeIdForUpdate(anyInt(), anyInt());
            // The audit says nothing was opted out.
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", "consent", "10", 100,
                    " ConsentId: 10 skipped: no live record"));
        }

        @Test
        @DisplayName("should leave a record with no patient alone, since it cannot be checked or locked")
        void shouldSkipRecord_whenItHasNoPatient() {
            Consent consent = new Consent();
            setConsentId(consent, 10);
            consent.setOptout(false);
            when(mockConsentDao.find(10)).thenReturn(consent);

            manager.optoutConsent(loggedInInfo, 10);

            assertThat(consent.isOptout()).isFalse();
            verify(mockConsentDao, never()).lockPatientForConsentChange(anyInt());
            verify(mockConsentDao, never()).merge(any());
            logActionMock.verify(() -> LogAction.addLogSynchronous(loggedInInfo,
                    "PatientConsentManager.optoutConsent[consentID]", " ConsentId: 10 skipped: record has no patient"));
        }

        @Test
        @DisplayName("should honour a per-patient write restriction when opting out by ID, before locking")
        void shouldThrow_whenPatientWriteDeniedForOptoutById() {
            Consent consent = consent(10, false, new Date(1_000L));
            when(mockConsentDao.find(10)).thenReturn(consent);
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), eq(100)))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.optoutConsent(loggedInInfo, 10))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verify(mockConsentDao, never()).lockPatientForConsentChange(anyInt());
            verify(mockConsentDao, never()).merge(any());
        }

        @Test
        @DisplayName("should lock the patient before its first read when opting out by patient and type")
        void shouldLockPatientBeforeFirstRead_whenOptingOutByPatientAndType() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent live = consent(11, false, new Date(2_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findByDemographicAndConsentTypeId(100, 1)).thenReturn(live);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(live));

            manager.optoutConsent(loggedInInfo, 100, 1);

            InOrder order = inOrder(mockConsentDao);
            order.verify(mockConsentDao).lockPatientForConsentChange(100);
            order.verify(mockConsentDao).findByDemographicAndConsentTypeId(100, 1);
            order.verify(mockConsentDao).findLiveByDemographicAndConsentTypeIdForUpdate(100, 1);
        }

        @Test
        @DisplayName("should throw and touch nothing when write privilege is denied, by patient and type")
        void shouldThrow_whenWriteDeniedByPatientAndType() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.optoutConsent(loggedInInfo, 100, 1))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verifyNoInteractions(mockConsentDao);
        }

        @Test
        @DisplayName("should opt out the deciding record and retire live duplicates, by patient and type")
        void shouldOptOutAndRetireDuplicates_whenOptingOutByPatientAndType() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent newer = consent(11, false, new Date(2_000L));
            Consent older = consent(12, false, new Date(1_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findByDemographicAndConsentTypeId(100, 1)).thenReturn(newer);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(newer, older));

            manager.optoutConsent(loggedInInfo, 100, 1);

            assertThat(newer.isOptout()).isTrue();
            assertThat(newer.getOptoutDate()).isNotNull();
            assertThat(newer.isDeleted()).isFalse();
            assertThat(older.isDeleted()).isTrue();
            verify(mockConsentDao).merge(newer);
            verify(mockConsentDao).merge(older);
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
                    .isInstanceOf(SecurityException.class);
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
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
                    .thenReturn(true);

            ConsentType result = manager.addConsentType(loggedInInfo, ct);

            assertThat(result).isSameAs(ct);
            verify(mockConsentTypeDao).persist(ct);
        }

        @Test
        @DisplayName("should throw and persist nothing without admin write privilege")
        void shouldThrow_whenAdminWriteDenied() {
            ConsentType ct = createActiveConsentType(0, "NEW_TYPE");
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq(SecurityInfoManager.WRITE), nullable(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addConsentType(loggedInInfo, ct))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin)");
            verifyNoInteractions(mockConsentTypeDao);
        }
    }

    @Nested
    @DisplayName("deleteConsent")
    class DeleteConsent {

        @Test
        @DisplayName("should delete every live record, not just one, so no duplicate keeps deciding")
        void shouldDeleteEveryLiveRecord_whenDuplicatesExist() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent first = consent(11, false, new Date(2_000L));
            Consent second = consent(12, true, new Date(1_000L));
            first.setLastEnteredBy("clerk2");
            second.setLastEnteredBy("clerk2");
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(first, second));

            manager.deleteConsent(loggedInInfo, 100, 1);

            // A Clear is a staff action, so each deleted row records who cleared it and when.
            for (Consent cleared : List.of(first, second)) {
                assertThat(cleared.isDeleted()).isTrue();
                assertThat(cleared.getLastEnteredBy()).isEqualTo("999998");
                assertThat(cleared.getEditDate().getTime()).isGreaterThan(2_000L);
            }
            verify(mockConsentDao).merge(first);
            verify(mockConsentDao).merge(second);
            for (int id : new int[] {11, 12}) {
                logActionMock.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                        eq("PatientConsentManager.deleteConsent()"), eq("consent"), eq(String.valueOf(id)), eq(100),
                        eq(" Demographic: 100 ConsentTypeId: 1 ConsentId: " + id)));
            }
        }

        @Test
        @DisplayName("should lock the patient before reading the records it clears")
        void shouldLockPatientBeforeReading_whenClearing() {
            ConsentType ct = createActiveConsentType(1, "email");
            Consent live = consent(11, false, new Date(2_000L));
            when(mockConsentTypeDao.find(1)).thenReturn(ct);
            when(mockConsentDao.findLiveByDemographicAndConsentTypeIdForUpdate(100, 1)).thenReturn(List.of(live));

            manager.deleteConsent(loggedInInfo, 100, 1);

            InOrder order = inOrder(mockConsentDao);
            order.verify(mockConsentDao).lockPatientForConsentChange(100);
            order.verify(mockConsentDao).findLiveByDemographicAndConsentTypeIdForUpdate(100, 1);
            order.verify(mockConsentDao).merge(live);
        }

        @Test
        @DisplayName("should delete and log nothing when the consent type is inactive")
        void shouldChangeNothing_whenConsentTypeInactive() {
            ConsentType ct = createActiveConsentType(1, "email");
            ct.setActive(false);
            when(mockConsentTypeDao.find(1)).thenReturn(ct);

            manager.deleteConsent(loggedInInfo, 100, 1);

            verifyNoInteractions(mockConsentDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should delete and log nothing when the consent type does not exist")
        void shouldChangeNothing_whenConsentTypeUnknown() {
            when(mockConsentTypeDao.find(1)).thenReturn(null);

            manager.deleteConsent(loggedInInfo, 100, 1);

            verifyNoInteractions(mockConsentDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should throw and change nothing when write privilege denied")
        void shouldThrow_whenWritePrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.WRITE), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.deleteConsent(loggedInInfo, 100, 1))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verifyNoInteractions(mockConsentDao);
        }
    }

    @Nested
    @DisplayName("getAllConsentsByDemographic")
    class GetAllConsentsByDemographic {

        @Test
        @DisplayName("should give the chart the deciding record for each type, not a duplicate opt-in")
        void shouldReturnTheDecidingRecord_forEachConsentType() {
            Consent olderOptOut = consent(11, true, new Date(1_000L));
            Consent newerOptIn = consent(12, false, new Date(2_000L));
            Consent otherType = consent(13, false, new Date(1_500L));
            olderOptOut.setConsentTypeId(1);
            newerOptIn.setConsentTypeId(1);
            otherType.setConsentTypeId(2);
            // The chart form shows the last record of each type it is given, here the opt-in.
            when(mockConsentDao.findByDemographic(100)).thenReturn(List.of(olderOptOut, newerOptIn, otherType));

            List<Consent> result = manager.getAllConsentsByDemographic(loggedInInfo, 100);

            assertThat(result).containsExactly(olderOptOut, otherType);
        }

        @Test
        @DisplayName("should preserve unrelated untyped records in the patient consent list")
        void shouldPreserveUntypedRecords_whenReadingPatientConsents() {
            Consent firstUntyped = consent(10, false, new Date(1_000L));
            Consent secondUntyped = consent(11, true, new Date(2_000L));
            when(mockConsentDao.findByDemographic(100)).thenReturn(List.of(firstUntyped, secondUntyped));

            assertThat(manager.getAllConsentsByDemographic(loggedInInfo, 100))
                    .containsExactly(firstUntyped, secondUntyped);
        }

        @Test
        @DisplayName("should throw and read nothing when read privilege denied")
        void shouldThrow_whenReadPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), anyInt()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.getAllConsentsByDemographic(loggedInInfo, 100))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verifyNoInteractions(mockConsentDao);
        }
    }

    @Nested
    @DisplayName("transaction attributes")
    class TransactionAttributes {

        @Test
        @DisplayName("should run reads without a transaction of their own, and each write in one")
        void shouldUseSupportsForReadsAndRequiredForWrites_forEveryPublicMethod() {
            AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
            for (Method method : PatientConsentManagerImpl.class.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                String name = method.getName();
                // Relies on the naming rule here: reads are get*, has* or filter*; anything else writes.
                // Name a new read that way, or it is held to REQUIRED.
                boolean read = name.startsWith("get") || name.startsWith("has") || name.startsWith("filter");
                TransactionAttribute attribute = source.getTransactionAttribute(method, PatientConsentManagerImpl.class);

                assertThat(attribute).as(name).isNotNull();
                assertThat(attribute.getPropagationBehavior()).as(name).isEqualTo(read
                        ? TransactionDefinition.PROPAGATION_SUPPORTS
                        : TransactionDefinition.PROPAGATION_REQUIRED);
                // Writes wait on the patient lock and must then read what the other write committed.
                assertThat(attribute.getIsolationLevel()).as(name).isEqualTo(read
                        ? TransactionDefinition.ISOLATION_DEFAULT
                        : TransactionDefinition.ISOLATION_READ_COMMITTED);
            }
        }
    }

    @Nested
    @DisplayName("getConsentsByTypeAndEditDate")
    class GetConsentsByTypeAndEditDate {

        @Test
        @DisplayName("should return nothing, not throw, when there is no consent type")
        void shouldReturnEmpty_whenConsentTypeIsNull() {
            assertThat(manager.getConsentsByTypeAndEditDate(loggedInInfo, null, new Date(0L))).isEmpty();
            verifyNoInteractions(mockConsentDao);
        }

        @Test
        @DisplayName("should throw and read nothing when read privilege denied")
        void shouldThrow_whenReadPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(SecurityInfoManager.READ), nullable(String.class)))
                    .thenReturn(false);

            ConsentType emailType = createActiveConsentType(1, "email");
            Date since = new Date(0L);

            assertThatThrownBy(() -> manager.getConsentsByTypeAndEditDate(loggedInInfo, emailType, since))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_demographic)");
            verifyNoInteractions(mockConsentDao);
        }
    }
}
