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
package io.github.carlos_emr.carlos.form;

import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020Dao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020DataDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBCAR2020TextDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020;
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Data;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The BC-AR 2020 pages and print load a stored form through {@code getFormRecord}, naming the patient and the form
 * separately; a form that is not the named patient's is refused before any of its rows are read.
 *
 * @since 2026-10-18
 */
@DisplayName("BC-AR 2020 stored form: the named patient's only")
@Tag("unit")
@Tag("form")
@Tag("security")
class FrmBCAR2020RecordFormOwnershipUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final int OWN_FORM = 7;
    private static final int OTHER_PATIENTS_FORM = 8;

    private final FormBCAR2020DataDao dataDao = mock(FormBCAR2020DataDao.class);
    private final FormBCAR2020TextDao textDao = mock(FormBCAR2020TextDao.class);

    @BeforeEach
    void registerBeans() {
        Demographic patient = new Demographic();
        patient.setDemographicNo(PATIENT);
        patient.setLastName("FAKE-Surname");
        patient.setFirstName("FAKE-Given");
        // Blank rather than NULL: a NULL contact field is #4920, fixed separately.
        patient.setHin("");
        patient.setVer("");
        patient.setAddress("");
        patient.setCity("");
        patient.setProvince("BC");
        patient.setPostal("");
        patient.setYearOfBirth("1985");
        patient.setMonthOfBirth("03");
        patient.setDateOfBirth("04");
        DemographicManager demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(any(), eq(PATIENT))).thenReturn(patient);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));

        FormBCAR2020Dao formDao = mock(FormBCAR2020Dao.class);
        when(formDao.find(OWN_FORM)).thenReturn(new FormBCAR2020(PATIENT, "999998"));
        when(formDao.find(OTHER_PATIENTS_FORM)).thenReturn(new FormBCAR2020(PATIENT + 1, "999998"));
        registerMock(FormBCAR2020Dao.class, formDao);
        when(dataDao.findFieldsForPage(anyInt(), anyInt())).thenReturn(List.of(
                new FormBCAR2020Data(OWN_FORM, "999998", 1, "t_comments", "FAKE-comment")));
        when(textDao.findFieldsForPage(anyInt(), anyInt())).thenReturn(List.of());
        registerMock(FormBCAR2020DataDao.class, dataDao);
        registerMock(FormBCAR2020TextDao.class, textDao);
    }

    @Test
    void shouldLoadTheStoredForm_whenItIsTheNamedPatients() throws Exception {
        Properties props = new FrmBCAR2020Record().getFormRecord(null, PATIENT, OWN_FORM, 1);

        assertThat(props.getProperty("t_comments")).isEqualTo("FAKE-comment");
        assertThat(props.getProperty("t_patientSurname")).isEqualTo("FAKE-Surname");
    }

    @Test
    void shouldRefuseTheStoredForm_whenItIsAnotherPatients() {
        assertThatThrownBy(() -> new FrmBCAR2020Record().getFormRecord(null, PATIENT, OTHER_PATIENTS_FORM, 1))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(dataDao, textDao);
    }

    @Test
    void shouldRefuseTheStoredForm_whenItDoesNotExist() {
        assertThatThrownBy(() -> new FrmBCAR2020Record().getFormRecord(null, PATIENT, 999, 2))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(dataDao, textDao);
    }

    @Test
    void shouldOpenANewForm_withoutLookingUpAStoredOne() throws Exception {
        Properties props = new FrmBCAR2020Record().getFormRecord(null, PATIENT, 0, 1);

        assertThat(props.getProperty("t_patientSurname")).isEqualTo("FAKE-Surname");
    }
}
