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
package io.github.carlos_emr.carlos.webserv.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.NoteManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.SecurityExceptionMapper;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DemographicService}.
 *
 * The existing security cases assert the access-control contract enforced at the REST
 * layer, namely that each endpoint:
 * - returns 401 UNAUTHORIZED when the session is absent, and
 * - returns 403 FORBIDDEN when the required privilege is denied, asking
 *   {@link SecurityInfoManager} for the exact privilege string and access
 *   scope expected for that endpoint.
 *
 * They deliberately do NOT assert the role hierarchy (e.g. whether {@code w}
 * implies {@code r}). That hierarchy is owned and enforced by
 * {@code SecurityInfoManagerImpl}; here the manager is mocked, so the only
 * thing worth verifying is the literal privilege string and scope this layer
 * requests for each route.
 *
 * Privilege requested at the REST layer (object-level = no demographicNo,
 * record-level = patient-specific demographicNo):
 * - GET    /demographics         - r, object-level
 * - GET    /demographics/{id}    - r, record-level
 * - POST   /demographics         - w, object-level
 * - PUT    /demographics         - u, record-level
 * - DELETE /demographics/{id}    - w, record-level
 *
 * Imported-category cases also exercise the real disclosure service at this alternate
 * REST entry point: a requested native-only subset must refuse before clinical reads
 * or transfer conversion, without blocking unrelated requested categories.
 *
 * @since 2026-06-29
 * @see DemographicService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemographicService unit tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("demographic")
class DemographicServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;

    private DemographicService service;
    /** Swapped to null in UNAUTHORIZED tests. */
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new DemographicService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "securityInfoManager", securityInfoManager);
    }

    // The mock returns false for every hasPrivilege call by default, so the
    // required privilege is implicitly denied; no per-privilege stubbing is
    // needed for the FORBIDDEN paths. Tests instead assert which privilege
    // string and scope the REST layer asked for, via Mockito verify(...).

    private static int statusOf(Throwable ex) {
        return ((WebApplicationException) ex).getResponse().getStatus();
    }

    private void assertThrowsWithStatus(ThrowingRunnable call, Response.Status expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(expected.getStatusCode()));
    }

    /** Asserts the object-level privilege string requested (no demographicNo). */
    private void verifyObjectPrivilegeRequested(String privilege) {
        verify(securityInfoManager).hasPrivilege(any(), eq("_demographic"), eq(privilege), isNull());
    }

    /** Asserts the record-level privilege string and demographicNo requested. */
    private void verifyRecordPrivilegeRequested(String privilege, Integer demographicNo) {
        verify(securityInfoManager).hasPrivilege(any(), eq("_demographic"), eq(privilege), eq(demographicNo));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Nested
    @DisplayName("Read privilege (r)")
    class ReadPrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetAllDemographics() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.getAllDemographics(null, null), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request object-level 'r' and throw 403 when denied for GET /demographics")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetAllDemographics() {
            assertThrowsWithStatus(() -> service.getAllDemographics(null, null), Response.Status.FORBIDDEN);
            verifyObjectPrivilegeRequested("r");
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetDemographicData() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.getDemographicData(42, Collections.emptyList()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'r' for the demographicNo and throw 403 when denied for GET /demographics/{id}")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetDemographicData() {
            assertThrowsWithStatus(() -> service.getDemographicData(42, Collections.emptyList()), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("r", 42);
        }
    }

    @Nested
    @DisplayName("Imported optional clinical projections")
    class ImportedCategoryProjection {
        private final Set<String> cppSourceCategories = Set.of("PersonalHistory", "FamilyHistory", "PastHealth",
                "RiskFactors", "AlertsAndSpecialNeeds");
        private CdsLegacyProjectionDao records;
        private DemographicManager demographics;
        private MeasurementManager measurements;
        private AllergyManager allergies;
        private NoteManager notes;
        private RxManager medications;

        @BeforeEach
        void configureProjectionBoundary() {
            records = mock(CdsLegacyProjectionDao.class);
            demographics = mock(DemographicManager.class);
            measurements = mock(MeasurementManager.class);
            allergies = mock(AllergyManager.class);
            notes = mock(NoteManager.class);
            medications = mock(RxManager.class);
            injectDependency(service, "projections", new CdsLegacyProjectionService(records));
            injectDependency(service, "demographicManager", demographics);
            injectDependency(service, "measurementManager", measurements);
            injectDependency(service, "allergyManager", allergies);
            injectDependency(service, "noteManager", notes);
            injectDependency(service, "rxManager", medications);
            injectDependency(service, "contactDao", mock(ContactDao.class));
            injectDependency(service, "waitingListDao", mock(WaitingListDao.class));
            injectDependency(service, "waitingListNameDao", mock(WaitingListNameDao.class));
            injectDependency(service, "providerDao", mock(ProviderDao.class));
            injectDependency(service, "secUserRoleDao", mock(SecUserRoleDao.class));
            injectDependency(service, "specialistDao", mock(ProfessionalSpecialistDao.class));
        }

        private void authorizeRecord() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            var patient = new Demographic(42, "Test", "Patient");
            patient.setYearOfBirth("1970"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("01");
            when(demographics.getDemographic(loggedInInfo, 42)).thenReturn(patient);
        }

        private void authorizeCategories(String... includes) {
            for (String include : includes) {
                String securityObject = switch (include) {
                    case "allergies" -> "_allergy";
                    case "measurements" -> "_measurement";
                    case "medications" -> "_rx";
                    case "notes" -> "_casemgmt.notes";
                    default -> throw new IllegalArgumentException("Not a clinical include");
                };
                when(securityInfoManager.hasPrivilege(loggedInInfo, securityObject, "r", "42")).thenReturn(true);
            }
            when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 42)).thenReturn(true);
        }

        private void assertConflictBeforeClinicalRead(List<String> includes, Set<String> expectedCategories) {
            authorizeCategories(includes.toArray(String[]::new));
            when(records.hasSourceFields(42, expectedCategories)).thenReturn(true);
            assertThrowsWithStatus(() -> service.getDemographicData(42, includes), Response.Status.CONFLICT);
            verify(records).hasSourceFields(42, expectedCategories);
            verifyNoInteractions(measurements, allergies, notes, medications);
            verify(demographics, never()).getDemographicExts(any(), any());
            verify(demographics, never()).getDemographicContacts(any(), any());
        }

        @Test
        void shouldNotConsultImportProvenance_whenSessionIsAbsent() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.getDemographicData(42, List.of("measurements")), Response.Status.UNAUTHORIZED);
            verifyNoInteractions(records, demographics, measurements);
        }

        @Test
        void shouldNotConsultImportProvenance_whenPatientReadIsDenied() {
            assertThrowsWithStatus(() -> service.getDemographicData(42, List.of("measurements")), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("r", 42);
            verifyNoInteractions(records, demographics, measurements);
        }

        @Test
        void shouldRetainNotFound_whenPatientIsMissingBeforeImportProvenance() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            authorizeCategories("measurements");
            assertThrowsWithStatus(() -> service.getDemographicData(42, List.of("measurements")), Response.Status.NOT_FOUND);
            verifyNoInteractions(records, measurements);
        }

        @Test
        void shouldDenyAllergyPermission_beforeConsultingImportProvenance() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            assertThatThrownBy(() -> service.getDemographicData(42, List.of("allergies")))
                    .isInstanceOf(SecurityException.class).hasMessageContaining("_allergy");
            verify(securityInfoManager).hasPrivilege(loggedInInfo, "_allergy", "r", "42");
            verifyNoInteractions(records, demographics, allergies, measurements, notes, medications);
        }

        @Test
        void shouldNotLeakImportedMeasurements_throughDemographicIncludeRoute() {
            authorizeRecord();
            assertConflictBeforeClinicalRead(List.of("measurements"), Set.of("CareElements"));
        }

        @Test
        void shouldNotLeakImportedAllergies_throughDemographicIncludeRoute() {
            authorizeRecord();
            assertConflictBeforeClinicalRead(List.of("allergies"), Set.of("AllergiesAndAdverseReactions"));
        }

        @Test
        void shouldNotPresentImportedMedications_asApparentlyCompleteNativeSummary() {
            authorizeRecord();
            assertConflictBeforeClinicalRead(List.of("medications"), Set.of("MedicationsAndTreatments"));
        }

        @Test
        void shouldRequireSameCompleteProjection_forEverySelectedCppSourceCategory() {
            authorizeRecord();
            authorizeCategories("notes");
            for (String importedCategory : cppSourceCategories) {
                when(records.hasSourceFields(42, cppSourceCategories))
                        .thenAnswer(call -> call.<Set<String>>getArgument(1).contains(importedCategory));
                assertThrowsWithStatus(() -> service.getDemographicData(42, List.of("notes")), Response.Status.CONFLICT);
            }
            verify(records, times(cppSourceCategories.size())).hasSourceFields(42, cppSourceCategories);
            verifyNoInteractions(notes, measurements, allergies, medications);
            verify(demographics, never()).getDemographicExts(any(), any());
        }

        @Test
        void shouldNotBlockCppHistories_forUnrequestedImportedCategory() throws Exception {
            authorizeRecord();
            authorizeCategories("notes");
            Set<String> otherImportedCategories = Set.of("CareElements", "ClinicalNotes", "ProblemList");
            when(records.hasSourceFields(eq(42), any()))
                    .thenAnswer(call -> !Collections.disjoint(call.getArgument(1), otherImportedCategories));
            var result = service.getDemographicData(42, List.of("notes"));
            assertThat(result.getDemographicNo()).isEqualTo(42);
            verify(records).hasSourceFields(42, cppSourceCategories);
            verify(notes).getActiveCppNotes(eq(loggedInInfo), eq(42), any(String[].class));
            verifyNoInteractions(measurements, allergies, medications);
        }

        @Test
        void shouldRetainValuesAndNumericDateWire_forOrdinaryNativeIncludes() throws Exception {
            authorizeRecord();
            authorizeCategories("allergies", "measurements", "notes", "medications");
            var observed = new Date(946684800000L);
            var measurement = new Measurement();
            measurement.setDemographicId(42); measurement.setType("wt");
            measurement.setDataField("72.4"); measurement.setDateObserved(observed);
            when(measurements.getMeasurementByType(eq(loggedInInfo), eq(42), any()))
                    .thenAnswer(call -> List.of("wt").equals(call.getArgument(2)) ? List.of(measurement) : List.of());
            when(medications.getCurrentSingleLineMedications(loggedInInfo, 42)).thenReturn(List.of("Existing medication"));
            var result = service.getDemographicData(42, List.of("allergies", "measurements", "notes", "medications"));
            assertThat(result.getMeasurements()).hasSize(1);
            assertThat(result.getMeasurements().getFirst().getDateObserved()).isEqualTo(observed);
            assertThat(result.getMeasurements().getFirst().getDataField()).isEqualTo("72.4");
            assertThat(result.getMedicationSummary()).containsExactly("Existing medication");
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var wire = mapper.readTree(mapper.writeValueAsBytes(result.getMeasurements().getFirst()));
            assertThat(wire.path("dateObserved").isIntegralNumber()).isTrue();
            assertThat(wire.path("dateObserved").longValue()).isEqualTo(observed.getTime());
            Set<String> requested = new java.util.HashSet<>(cppSourceCategories);
            requested.addAll(Set.of("AllergiesAndAdverseReactions", "CareElements", "MedicationsAndTreatments"));
            var order = inOrder(securityInfoManager, demographics, records);
            order.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", 42);
            for (String object : List.of("_allergy", "_measurement", "_casemgmt.notes", "_rx"))
                order.verify(securityInfoManager).hasPrivilege(loggedInInfo, object, "r", "42");
            order.verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, 42);
            order.verify(demographics).getDemographic(loggedInInfo, 42);
            order.verify(records).hasSourceFields(42, requested);
            verifyNoMoreInteractions(records);
        }

        private void configureUnqueriedProvenance(boolean imported) {
            // A real disclosure service would distinguish these patients if it were reached.
            records = mock(CdsLegacyProjectionDao.class, invocation ->
                    "hasSourceFields".equals(invocation.getMethod().getName())
                            ? imported : RETURNS_DEFAULTS.answer(invocation));
            injectDependency(service, "projections", new CdsLegacyProjectionService(records));
        }

        private void assertClinicalAuthorizationDenied(List<String> includes) {
            assertThatThrownBy(() -> service.getDemographicData(42, includes))
                    .isInstanceOf(SecurityException.class)
                    .satisfies(failure -> {
                        try (Response response = new SecurityExceptionMapper().toResponse((SecurityException) failure)) {
                            assertThat(response.getStatus()).isEqualTo(403);
                            assertThat(response.hasEntity()).isFalse();
                        }
                    });
            verifyNoInteractions(records, demographics, measurements, allergies, notes, medications);
        }

        @Test
        void shouldDenyMeasurementPermissionIndistinguishably_forNativeAndImportedPatients() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            for (boolean imported : List.of(false, true)) {
                configureUnqueriedProvenance(imported);
                assertClinicalAuthorizationDenied(List.of("measurements"));
            }
            verify(securityInfoManager, times(2)).hasPrivilege(loggedInInfo, "_measurement", "r", "42");
        }

        @Test
        void shouldDenyMedicationPermissionIndistinguishably_forNativeAndImportedPatients() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            for (boolean imported : List.of(false, true)) {
                configureUnqueriedProvenance(imported);
                assertClinicalAuthorizationDenied(List.of("medications"));
            }
            verify(securityInfoManager, times(2)).hasPrivilege(loggedInInfo, "_rx", "r", "42");
        }

        @Test
        void shouldDenyCppPermissionIndistinguishably_forNativeAndImportedPatients() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            for (boolean imported : List.of(false, true)) {
                configureUnqueriedProvenance(imported);
                assertClinicalAuthorizationDenied(List.of("notes"));
            }
            verify(securityInfoManager, times(2)).hasPrivilege(loggedInInfo, "_casemgmt.notes", "r", "42");
        }

        @Test
        void shouldApplyPatientRecordRestriction_beforeNativeAndImportedCategoryReads() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "r", "42")).thenReturn(true);
            for (boolean imported : List.of(false, true)) {
                configureUnqueriedProvenance(imported);
                assertClinicalAuthorizationDenied(List.of("measurements"));
            }
            verify(securityInfoManager, times(2)).isAllowedAccessToPatientRecord(loggedInInfo, 42);
        }

        @Test
        void shouldAuthorizeEveryRequestedCategory_beforeAnyCombinedProvenanceRead() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", 42)).thenReturn(true);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", "r", "42")).thenReturn(true);
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "r", "42")).thenReturn(true);
            for (boolean imported : List.of(false, true)) {
                configureUnqueriedProvenance(imported);
                assertClinicalAuthorizationDenied(List.of("allergies", "measurements", "medications"));
            }
            verify(securityInfoManager, times(2)).hasPrivilege(loggedInInfo, "_rx", "r", "42");
            verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), any());
        }

        @Test
        void shouldNotIntroduceBlanketImportBlock_forEmptyUnknownAndContactIncludes() throws Exception {
            authorizeRecord();
            for (List<String> includes : List.of(List.<String>of(), List.of("unrecognized"), List.of("contacts"))) {
                assertThat(service.getDemographicData(42, includes).getDemographicNo()).isEqualTo(42);
            }
            verifyNoInteractions(records, measurements, allergies, notes, medications);
        }

        @Test
        void shouldRetainExistingScopeAndConsentPath_forBasicContactRepresentation() throws Exception {
            authorizeRecord();
            assertThat(service.getBasicDemographicData(42, List.of("contacts", "measurements", "medications"))
                    .getDemographicNo()).isEqualTo(42);
            verify(demographics).getDemographicContacts(loggedInInfo, 42);
            verifyNoInteractions(records, measurements, allergies, notes, medications);
        }
    }

    @Nested
    @DisplayName("Update privilege (u) -- PUT /demographics")
    class UpdatePrivilege {

        private DemographicTo1 validPayload() {
            DemographicTo1 data = new DemographicTo1();
            data.setDemographicNo(42);
            return data;
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for PUT /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forUpdate() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.updateDemographicData(validPayload()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'u' for the demographicNo and throw 403 when denied for PUT /demographics")
        void shouldThrowForbidden_whenUpdatePrivilegeDenied_forUpdate() {
            assertThrowsWithStatus(() -> service.updateDemographicData(validPayload()), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("u", 42);
        }

        @Test
        @DisplayName("should throw 401 before 400 when not authenticated and body is null for PUT /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_andBodyIsNull_forUpdate() {
            // auth must fire before the null-body check, even in the missing-demographicNo branch
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.updateDemographicData(null), Response.Status.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Write privilege (w) -- POST /demographics and DELETE /demographics/{id}")
    class WritePrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for POST /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forCreate() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.createDemographicData(new DemographicTo1()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request object-level 'w' and throw 403 when denied for POST /demographics")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forCreate() {
            assertThrowsWithStatus(() -> service.createDemographicData(new DemographicTo1()), Response.Status.FORBIDDEN);
            verifyObjectPrivilegeRequested("w");
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for DELETE /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forDelete() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.deleteDemographicData(42), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'w' for the demographicNo and throw 403 when denied for DELETE /demographics/{id}")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forDelete() {
            assertThrowsWithStatus(() -> service.deleteDemographicData(42), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("w", 42);
        }
    }
}
