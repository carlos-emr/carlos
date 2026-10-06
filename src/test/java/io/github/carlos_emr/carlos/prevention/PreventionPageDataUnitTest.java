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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

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
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
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
            assertThat(row.get("age")).isEqualTo(
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
        String hidden = "style=\"display:none;\"";

        assertThat(config.display(adults, patient, 0)).isTrue();
        assertThat(config.display(children, patient, 0)).isFalse();
        assertThat(config.display(men, patient, 0)).isFalse();
        assertThat(config.getDisplay(adultSet, patient)).isEmpty();
        assertThat(config.getDisplay(womenSet, patient)).isEmpty();
        assertThat(config.getDisplay(childSet, patient)).isEqualTo(hidden);
        assertThat(config.getDisplay(menSet, patient)).isEqualTo(hidden);
        assertThat(config.getDisplay(Map.of(), patient)).isEqualTo(hidden);
        assertThat(config.getDisplay(adultSet, null)).isEqualTo(hidden);
        assertThat(config.display(adults, null, 0)).isFalse();
        verifyNoInteractions(demographicManager);

        assertThat(config.display(user, adults, PATIENT, 0)).isTrue();
        assertThat(config.display(user, children, PATIENT, 0)).isFalse();
        verify(demographicManager, times(2)).getDemographic(eq(user), eq(PATIENT));
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
