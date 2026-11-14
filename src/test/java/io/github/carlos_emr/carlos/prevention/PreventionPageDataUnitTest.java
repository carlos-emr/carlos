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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMapping;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import io.github.carlos_emr.carlos.prevention.dto.PreventionSourceUnavailableException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The prevention page looks its patient up once per request and reads each type's preventions
 * once per request. {@code PreventionData} resolves its DAOs when the class loads, so the test
 * swaps them through reflection for its duration.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Prevention page lookups, once per request")
class PreventionPageDataUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "7";
    private static final String DENIED = "missing required sec object (_demographic)";
    private static final String HIDDEN = "style=\"display:none;\"";

    private final DemographicManager demographicManager = mock(DemographicManager.class);
    private final PreventionDao preventionDao = mock(PreventionDao.class);
    private final PartialDateDao partialDateDao = mock(PartialDateDao.class);
    private final PreventionManager preventionManager = mock(PreventionManager.class);
    private final LoggedInInfo user = new LoggedInInfo();
    private final Demographic patient = patient("1980", "03", "15", "F");

    private Object savedPreventionDao;
    private Object savedPartialDateDao;

    @BeforeEach
    void setUp() throws Exception {
        // In case this test is the first to load PreventionData or PreventionDisplayConfig.
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, mock(PreventionExtDao.class));
        registerMock(PartialDateDao.class, partialDateDao);
        registerMock(CVCImmunizationDao.class, mock(CVCImmunizationDao.class));
        registerMock(CanadianVaccineCatalogueManager.class, mock(CanadianVaccineCatalogueManager.class));
        registerMock(CVCMappingDao.class, mock(CVCMappingDao.class));
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));
        registerMock(PreventionManager.class, preventionManager);
        savedPreventionDao = swapPreventionDataField("preventionDao", preventionDao);
        savedPartialDateDao = swapPreventionDataField("partialDateDao", partialDateDao);

        when(partialDateDao.getDatePartial(anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> call.getArgument(0));
        when(demographicManager.getDemographic(user, PATIENT)).thenReturn(patient);
        when(demographicManager.getDemographic(user, Integer.valueOf(PATIENT))).thenReturn(patient);
    }

    @AfterEach
    void restore() throws Exception {
        swapPreventionDataField("preventionDao", savedPreventionDao);
        swapPreventionDataField("partialDateDao", savedPartialDateDao);
    }

    @Test
    @DisplayName("should refuse an unavailable source instead of returning an empty or partial chart")
    void shouldRefuseUnavailableSource_insteadOfSilentlyDroppingPreventions() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("synthetic source failure");
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenThrow(failure);
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pageData.getPreventionData("Flu"))
                .isExactlyInstanceOf(PreventionSourceUnavailableException.class).hasCause(failure);
    }

    @Test
    @DisplayName("should refuse every type once one read failed, including types already read")
    void shouldRefuseEveryType_onceOneReadHasFailed() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("synthetic source failure");
        Prevention flu = prevention(1, "Flu", 2020);
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenReturn(List.of(flu));
        when(preventionDao.findByTypeAndDemoNo("PAP", 7)).thenThrow(failure);
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);
        assertThat(pageData.getPreventionData("Flu")).hasSize(1);

        assertThatThrownBy(() -> pageData.getPreventionData("PAP"))
                .isExactlyInstanceOf(PreventionSourceUnavailableException.class);

        // The Flu rows were read, but a page that showed them now would show part of the history
        // as the whole of it.
        assertThatThrownBy(() -> pageData.getPreventionData("Flu"))
                .isExactlyInstanceOf(PreventionSourceUnavailableException.class).hasCause(failure);
        assertThatThrownBy(() -> pageData.getPreventionData("MMR"))
                .isExactlyInstanceOf(PreventionSourceUnavailableException.class);
        verify(preventionDao, times(0)).findByTypeAndDemoNo("MMR", 7);
    }

    @Test
    @DisplayName("should tell a page before it writes anything that the preventions cannot be read")
    void shouldReportUnreadable_beforeThePageWritesAnyType() {
        Prevention flu = prevention(1, "Flu", 2020);
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenReturn(List.of(flu));
        when(preventionDao.findByTypeAndDemoNo("PAP", 7))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic source failure"));
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);

        assertThat(pageData.canReadAll(java.util.Arrays.asList("Flu", null, "PAP", "MMR"))).isFalse();

        assertThatThrownBy(() -> pageData.getPreventionData("Flu"))
                .isExactlyInstanceOf(PreventionSourceUnavailableException.class);
    }

    @Test
    @DisplayName("should read each listed type once when all of them can be read")
    void shouldKeepPreloadedLists_whenEveryTypeCanBeRead() {
        Prevention flu = prevention(1, "Flu", 2020);
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenReturn(List.of(flu));
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);

        assertThat(pageData.canReadAll(List.of("Flu", "PAP"))).isTrue();

        assertThat(pageData.getPreventionData("Flu")).hasSize(1);
        assertThat(pageData.getPreventionData("PAP")).isEmpty();
        verify(preventionDao, times(1)).findByTypeAndDemoNo("Flu", 7);
        verify(preventionDao, times(1)).findByTypeAndDemoNo("PAP", 7);
    }

    @Test
    @DisplayName("should pass a date that needs review through without remembering it as unreadable")
    void shouldPassTheDateReviewRefusalThrough_withoutMarkingThePageUnreadable() {
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenThrow(new PreventionDatePrecisionException());
        Prevention pap = prevention(2, "PAP", 2021);
        when(preventionDao.findByTypeAndDemoNo("PAP", 7)).thenReturn(List.of(pap));
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);

        assertThatThrownBy(() -> pageData.getPreventionData("Flu"))
                .isExactlyInstanceOf(PreventionDatePrecisionException.class);

        assertThat(pageData.getPreventionData("PAP")).hasSize(1);
    }

    @Test
    @DisplayName("should look the patient up once, however many types the page reads")
    void shouldLookPatientUpOnce_whenManyTypesAreRead() {
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);
        for (String type : List.of("Flu", "PAP", "MMR", "Flu")) {
            pageData.getPreventionData(type);
        }

        assertThat(pageData.getDemographic()).isSameAs(patient);
        assertThat(pageData.getDateOfBirth()).isEqualTo(patient.getBirthDay().getTime());
        pageData.getDateOfBirth().setTime(0);
        assertThat(pageData.getDateOfBirth()).isEqualTo(patient.getBirthDay().getTime());
        verify(demographicManager).getDemographic(user, PATIENT);
        verifyNoMoreInteractions(demographicManager);
        // Each distinct type is still read, once, alongside the single patient lookup.
        verify(preventionDao).findByTypeAndDemoNo("Flu", 7);
        verify(preventionDao).findByTypeAndDemoNo("PAP", 7);
        verify(preventionDao).findByTypeAndDemoNo("MMR", 7);
        verifyNoMoreInteractions(preventionDao);
    }

    @Test
    @DisplayName("should read a type's preventions once and give the same list again in the request")
    void shouldReadTypeOnce_whenReadAgainInTheSameRequest() {
        Prevention flu = prevention(11, "Flu", 2024);
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenReturn(List.of(flu));
        PreventionPageData pageData = new PreventionPageData(user, PATIENT, demographicManager);

        ArrayList<Map<String, Object>> first = pageData.getPreventionData("Flu");
        ArrayList<Map<String, Object>> again = pageData.getPreventionData("Flu");

        assertThat(again).isSameAs(first);
        assertThat(first).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("id", "11").containsEntry("type", "Flu");
            assertThat(row).containsEntry("age",
                    UtilDateUtilities.calcAgeAtDate(patient.getBirthDay().getTime(), flu.getPreventionDate()));
        });
        verify(preventionDao, times(1)).findByTypeAndDemoNo("Flu", 7);
    }

    @Test
    @DisplayName("should list preventions without ages when the patient or the birth date is missing")
    void shouldLeaveAgesOut_whenPatientOrBirthDateIsMissing() {
        Prevention patientsFlu = prevention(11, "Flu", 2024);
        Prevention missingPatientsFlu = prevention(12, "Flu", 2024);
        when(preventionDao.findByTypeAndDemoNo("Flu", 7)).thenReturn(List.of(patientsFlu));
        when(demographicManager.getDemographic(user, "8")).thenReturn(null);
        when(preventionDao.findByTypeAndDemoNo("Flu", 8)).thenReturn(List.of(missingPatientsFlu));
        patient.setYearOfBirth(null);

        PreventionPageData noBirthDate = new PreventionPageData(user, PATIENT, demographicManager);
        PreventionPageData noPatient = new PreventionPageData(user, "8", demographicManager);

        assertThat(noBirthDate.getDateOfBirth()).isNull();
        assertThat(noPatient.getDemographic()).isNull();
        assertThat(noPatient.getDateOfBirth()).isNull();
        assertThat(noBirthDate.getPreventionData("Flu")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("id", "11").containsEntry("age", null));
        assertThat(noPatient.getPreventionData("Flu")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("id", "12").containsEntry("age", null));
    }

    @Test
    @DisplayName("should share nothing between requests, patients or users")
    void shouldLookUpAgain_whenAnotherRequestPatientOrUserReads() {
        LoggedInInfo otherUser = new LoggedInInfo();
        Demographic otherPatient = patient("2001", "11", "02", "M");
        when(demographicManager.getDemographic(otherUser, "8")).thenReturn(otherPatient);

        new PreventionPageData(user, PATIENT, demographicManager).getPreventionData("Flu");
        new PreventionPageData(user, PATIENT, demographicManager).getPreventionData("Flu");
        PreventionPageData other = new PreventionPageData(otherUser, "8", demographicManager);
        other.getPreventionData("Flu");

        assertThat(other.getDemographic()).isSameAs(otherPatient);
        verify(demographicManager, times(2)).getDemographic(user, PATIENT);
        verify(demographicManager).getDemographic(otherUser, "8");
        verify(preventionDao, times(2)).findByTypeAndDemoNo("Flu", 7);
        verify(preventionDao).findByTypeAndDemoNo("Flu", 8);
    }

    @Test
    @DisplayName("should refuse, reading no preventions, when the user may not read the patient")
    void shouldThrow_whenUserMayNotReadPatient() {
        when(demographicManager.getDemographic(user, PATIENT)).thenThrow(new RuntimeException(DENIED));

        assertThatThrownBy(() -> new PreventionPageData(user, PATIENT, demographicManager))
                .hasMessage(DENIED);
        verifyNoInteractions(preventionDao);
    }

    @Test
    @DisplayName("should keep the standalone lookup checking the patient on every call")
    void shouldLookPatientUpPerCall_whenCalledWithoutThePage() {
        PreventionData.getPreventionData(user, "Flu", 7);
        PreventionData.getPreventionData(user, "Flu", 7);

        verify(demographicManager, times(2)).getDemographic(user, Integer.valueOf(7));
        verify(preventionDao, times(2)).findByTypeAndDemoNo("Flu", 7);
    }

    @Test
    @DisplayName("should keep the standalone lookup returning nothing when the user may not read the patient")
    void shouldReturnNothing_whenStandaloneLookupIsDenied() {
        when(demographicManager.getDemographic(user, Integer.valueOf(7))).thenThrow(new RuntimeException(DENIED));

        assertThat(PreventionData.getPreventionData(user, "Flu", 7)).isEmpty();
        verifyNoInteractions(preventionDao);
    }

    @Test
    @DisplayName("should decide what shows from the patient the page looked up, as the per-patient lookup does")
    void shouldDecideDisplay_fromThePatientAlreadyLookedUp() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        Map<String, String> adults = Map.of("name", "Flu", "minAge", "18");
        Map<String, String> children = Map.of("name", "MMR", "maxAge", "17");
        Map<String, String> men = Map.of("name", "PSA", "sex", "M");
        Map<String, Object> adultSet = Map.of("minAge", "18");
        Map<String, Object> childSet = Map.of("maxAge", "17");
        Map<String, Object> womenSet = Map.of("sex", "F");
        Map<String, Object> menSet = Map.of("sex", "M");

        assertThat(config.display(adults, patient, 0)).isTrue();
        assertThat(config.display(children, patient, 0)).isFalse();
        assertThat(config.display(men, patient, 0)).isFalse();
        assertThat(config.getDisplay(adultSet, patient)).isEmpty();
        assertThat(config.getDisplay(womenSet, patient)).isEmpty();
        assertThat(config.getDisplay(childSet, patient)).isEqualTo(HIDDEN);
        assertThat(config.getDisplay(menSet, patient)).isEqualTo(HIDDEN);
        assertThat(config.getDisplay(Map.of(), patient)).isEqualTo(HIDDEN);
        assertThat(config.getDisplay(adultSet, null)).isEqualTo(HIDDEN);
        assertThat(config.display(adults, null, 0)).isFalse();
        verifyNoInteractions(demographicManager);

        assertThat(config.display(user, adults, PATIENT, 0)).isTrue();
        assertThat(config.display(user, children, PATIENT, 0)).isFalse();
        verify(demographicManager, times(2)).getDemographic(user, PATIENT);
    }

    @Test
    @DisplayName("should hide every prevention type without log noise when the patient is missing")
    void shouldHideTypesWithoutLogging_whenPatientIsMissing() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        try (LogCapture logs = LogCapture.forLogger(PreventionDisplayConfig.class)) {
            for (String type : List.of("Flu", "PAP", "MMR")) {
                assertThat(config.display(Map.of("name", type, "minAge", "18"), null, 0)).isFalse();
            }
            assertThat(logs.events()).isEmpty();
        }
        verifyNoInteractions(preventionManager);
    }

    @Test
    @DisplayName("should hide configuration sets without log noise when the patient is missing")
    void shouldHideSetsWithoutLogging_whenPatientIsMissing() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        try (LogCapture logs = LogCapture.forLogger(PreventionDisplayConfig.class)) {
            for (Map<String, Object> set : List.<Map<String, Object>>of(
                    Map.of("minAge", "18"), Map.of("maxAge", "17"), Map.of("sex", "F"))) {
                assertThat(config.getDisplay(set, null)).isEqualTo(HIDDEN);
            }
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    @DisplayName("should hide the type without log noise when the patient lookup returns nothing")
    void shouldHideTypeWithoutLogging_whenPatientLookupReturnsNothing() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        when(demographicManager.getDemographic(user, PATIENT)).thenReturn(null);
        try (LogCapture logs = LogCapture.forLogger(PreventionDisplayConfig.class)) {
            assertThat(config.display(user, Map.of("name", "Flu", "minAge", "18"), PATIENT, 0)).isFalse();
            assertThat(logs.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.WARN));
        }
        verify(demographicManager).getDemographic(user, PATIENT);
        verifyNoInteractions(preventionManager);
    }

    @Test
    @DisplayName("should still log unexpected configuration failures as errors")
    void shouldLogUnexpectedFailure_whenAgeConfigurationIsInvalid() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        try (LogCapture logs = LogCapture.forLogger(PreventionDisplayConfig.class)) {
            assertThat(config.display(Map.of("name", "Flu", "minAge", "invalid"), patient, 0)).isFalse();
            assertThat(logs.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrown()).isInstanceOf(NumberFormatException.class);
            });
        }
    }

    @Test
    @DisplayName("should preserve the record threshold override of age and sex filters")
    void shouldPreserveRecordOverride_whenPatientDoesNotMatchFilters() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        Map<String, String> type = Map.of("name", "Flu", "minAge", "200", "sex", "M", "showIfMinRecordNum", "1");
        assertThat(config.display(type, patient, 0)).isFalse();
        assertThat(config.display(type, patient, 1)).isTrue();
        assertThat(config.display(Map.of("name", "Flu", "minAge", "invalid", "showIfMinRecordNum", "1"), patient, 1))
                .isTrue();
        when(preventionManager.hideItem("Flu")).thenReturn(true);
        assertThat(config.display(Map.of("name", "Flu", "showIfMinRecordNum", "0"), patient, 0)).isFalse();
    }

    @Test
    @DisplayName("should apply demographic filters when the record threshold has not been reached")
    void shouldApplyFilters_whenRecordThresholdIsNotReached() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        assertThat(config.display(Map.of("name", "Flu", "minAge", "18", "sex", "F", "showIfMinRecordNum", "2"), patient, 1))
                .isTrue();
        assertThat(config.display(Map.of("name", "Flu", "showIfMinRecordNum", "2"), patient, 1)).isFalse();
        assertThat(config.display(Map.of("name", "Flu"), patient, 0)).isFalse();
    }

    @Test
    @DisplayName("should preserve mapped items, preferred replacements and generic deduplication")
    void shouldPreserveCatalogueMapping_whenLoadingConfiguredPreventions() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        CVCMappingDao savedMappings = PreventionDisplayConfig.cvcMapping;
        CVCImmunizationDao savedImmunizations = PreventionDisplayConfig.cvcImmunizationDao;
        CanadianVaccineCatalogueManager savedCatalogue = PreventionDisplayConfig.cvcManager;
        CVCMappingDao mappings = mock(CVCMappingDao.class);
        CVCImmunizationDao immunizations = mock(CVCImmunizationDao.class);
        CanadianVaccineCatalogueManager catalogue = mock(CanadianVaccineCatalogueManager.class);
        CVCMapping mapped = new CVCMapping();
        mapped.setCvcSnomedId("FAKE-Td");
        CVCMapping preferred = new CVCMapping();
        preferred.setPreferCVC(true);
        CVCImmunization td = mock(CVCImmunization.class);
        when(td.getSnomedConceptId()).thenReturn("FAKE-Td");
        when(td.getPicklistName()).thenReturn("FAKE-Td-picklist");
        CVCImmunization mmr = mock(CVCImmunization.class);
        when(mmr.getSnomedConceptId()).thenReturn("FAKE-MMR");
        when(mmr.getPicklistName()).thenReturn("FAKE-MMR-picklist");
        when(mappings.findByOscarName("Td")).thenReturn(mapped);
        when(mappings.findByOscarName("MMR")).thenReturn(preferred);
        when(immunizations.findBySnomedConceptId("FAKE-Td")).thenReturn(td);
        when(catalogue.getGenericImmunizationList()).thenReturn(List.of(td, mmr));
        try {
            PreventionDisplayConfig.cvcMapping = mappings;
            PreventionDisplayConfig.cvcImmunizationDao = immunizations;
            PreventionDisplayConfig.cvcManager = catalogue;
            config.loadPreventions();
            assertThat(config.getPrevention("Inf")).isNotNull();
            assertThat(config.getPrevention("Td")).containsEntry("snomedConceptCode", "FAKE-Td")
                    .containsEntry("cvcName", "FAKE-Td-picklist");
            assertThat(config.getPrevention("FAKE-Td-picklist")).isNull();
            assertThat(config.getPrevention("MMR")).isNull();
            assertThat(config.getPrevention("FAKE-MMR-picklist")).containsEntry("showIfMinRecordNum", "1");
        } finally {
            PreventionDisplayConfig.cvcMapping = savedMappings;
            PreventionDisplayConfig.cvcImmunizationDao = savedImmunizations;
            PreventionDisplayConfig.cvcManager = savedCatalogue;
        }
    }

    @Test
    @DisplayName("should show or hide every shipped configuration set as the per-call lookup did, at each age boundary")
    void shouldMatchPerCallLookup_forEveryShippedConfigurationSet() throws Exception {
        PreventionDisplayConfig config = newDisplayConfig();
        List<Map<String, Object>> sets = new ArrayList<>(config.getConfigurationSets());
        List<BoundaryPatient> patients = boundaryPatients();

        // The nine sets in oscar/prevention/PreventionConfigSets.xml, read by the real loader.
        assertThat(sets).hasSize(9);
        // No shipped set has an upper age alone or a sex alone; these cover those branches too.
        sets.add(Map.of("title", "FAKE up to 19 only", "maxAge", "19"));
        sets.add(Map.of("title", "FAKE women only", "sex", "F"));
        SoftAssertions softly = new SoftAssertions();
        for (Map<String, Object> set : sets) {
            Set<String> outcomes = new HashSet<>();
            for (BoundaryPatient boundary : patients) {
                // The removed getDisplay(LoggedInInfo, Map, String) looked the patient up through
                // DemographicData on every call, then ran the rules getDisplay(Map, Demographic)
                // now holds unchanged. This rebuilds that path; the rules themselves are checked
                // against shows() below.
                String perCallLookup = config.getDisplay(set,
                        new DemographicData().getDemographic(user, boundary.demographicNo()));
                String pageLookup = config.getDisplay(set,
                        new PreventionPageData(user, boundary.demographicNo(), demographicManager).getDemographic());
                softly.assertThat(pageLookup)
                        .as("%s for %s", set.get("title"), boundary)
                        .isEqualTo(perCallLookup)
                        .isEqualTo(shows(set, boundary) ? "" : HIDDEN);
                outcomes.add(pageLookup);
            }
            // Each set is shown to some of these patients and hidden from others, so a rule that
            // always hid (or always showed) a set could not pass.
            softly.assertThat(outcomes).as("%s outcomes", set.get("title")).containsExactlyInAnyOrder("", HIDDEN);
        }
        softly.assertAll();
    }

    /**
     * A FAKE patient whose age today is exactly {@code age}, or no patient at all when
     * {@code demographic} is {@code null} (the number is not stubbed, so the lookup finds nothing).
     */
    private record BoundaryPatient(String demographicNo, int age, String sex, Demographic demographic) {
        @Override
        public String toString() {
            return demographic == null ? "no patient" : sex + " aged " + age;
        }
    }

    /**
     * Both sexes on each side of every age limit in the shipped sets (19/20, 49/50, 54/55,
     * 64/65, 74/75, 80/81), plus newborns and a number with no patient. Each is born a day (two
     * on 29 February in a common birth year) before the birthday that gives the age, so the age
     * still holds tomorrow and a run that crosses midnight is safe.
     */
    private List<BoundaryPatient> boundaryPatients() {
        List<BoundaryPatient> patients = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int demographicNo = 100;
        for (int age : new int[] {0, 19, 20, 49, 50, 54, 55, 64, 65, 74, 75, 80, 81}) {
            LocalDate born = today.minusYears(age).minusDays(1);
            for (String sex : List.of("F", "M")) {
                Demographic demographic = patient(String.valueOf(born.getYear()),
                        String.format("%02d", born.getMonthValue()), String.format("%02d", born.getDayOfMonth()), sex);
                String number = String.valueOf(demographicNo++);
                when(demographicManager.getDemographic(user, number)).thenReturn(demographic);
                patients.add(new BoundaryPatient(number, age, sex, demographic));
            }
        }
        patients.add(new BoundaryPatient(String.valueOf(demographicNo), 0, null, null));
        return patients;
    }

    /**
     * The rule as the prevention page states it, worked out independently of the code under test:
     * a set shows when it has an age range or a sex and the patient meets every one it has.
     */
    private static boolean shows(Map<String, Object> set, BoundaryPatient boundary) {
        String minAge = (String) set.get("minAge");
        String maxAge = (String) set.get("maxAge");
        String sex = (String) set.get("sex");
        if (boundary.demographic() == null || (minAge == null && maxAge == null && sex == null)) {
            return false;
        }
        return (minAge == null || boundary.age() >= Integer.parseInt(minAge))
                && (maxAge == null || boundary.age() <= Integer.parseInt(maxAge))
                && (sex == null || sex.equals(boundary.sex()));
    }

    private static Demographic patient(String year, String month, String day, String sex) {
        Demographic demographic = new Demographic();
        demographic.setYearOfBirth(year);
        demographic.setMonthOfBirth(month);
        demographic.setDateOfBirth(day);
        demographic.setSex(sex);
        return demographic;
    }

    private static Prevention prevention(int id, String type, int year) {
        Prevention prevention = mock(Prevention.class);
        when(prevention.getId()).thenReturn(id);
        when(prevention.getPreventionType()).thenReturn(type);
        when(prevention.getPreventionDate()).thenReturn(new GregorianCalendar(year, 9, 1).getTime());
        return prevention;
    }

    private static PreventionDisplayConfig newDisplayConfig() throws Exception {
        Constructor<PreventionDisplayConfig> constructor = PreventionDisplayConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object swapPreventionDataField(String name, Object value) throws Exception {
        Field field = PreventionData.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
