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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Immunizations;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ImmunizationsDao} covering persist operations.
 *
 * <p>Migrated from legacy {@code ImmunizationsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ImmunizationsDao
 */
@DisplayName("ImmunizationsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("immunization")
@Transactional
public class ImmunizationsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ImmunizationsDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist immunization with generated ID")
    void shouldPersistImmunization_whenValidDataProvided() throws Exception {
        Immunizations entity = new Immunizations();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Autowired
    private DemographicDao demographicDao;

    private int patient() {
        Demographic patient = new Demographic();
        patient.setFirstName("Test");
        patient.setLastName("ScheduleRevision");
        patient.setSex("F");
        patient.setProviderNo("999998");
        patient.setHcType("ON");
        patient.setPatientStatus("AC");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("01");
        demographicDao.save(patient);
        hibernateTemplate.flush();
        return patient.getDemographicNo();
    }

    @Test
    void shouldAdvanceRevisionAndKeepHistory_whenExpectedVersionMatches() {
        int patient = patient();
        assertThat(dao.replaceCurrent(patient, "999998", "<immunizations/>", 0)).isTrue();
        Immunizations first = dao.findCurrentByDemographicNo(patient).getFirst();
        assertThat(dao.replaceCurrent(patient, "999998", "<immunizations updated=\"true\"/>", first.getId())).isTrue();
        hibernateTemplate.flush();

        assertThat(dao.findCurrentByDemographicNo(patient)).singleElement()
                .satisfies(current -> {
                    assertThat(current.getId()).isGreaterThan(first.getId());
                    assertThat(current.getImmunizations()).isEqualTo("<immunizations updated=\"true\"/>");
                });
        assertThat(dao.find(first.getId()).getArchived()).isEqualTo(1);
        assertThat(dao.find(first.getId()).getImmunizations()).isEqualTo("<immunizations/>");
    }

    @Test
    void shouldRejectOldAndEmptyVersions_withoutChangingTheWinner() {
        int patient = patient();
        assertThat(dao.replaceCurrent(patient, "999998", "first", 0)).isTrue();
        Immunizations first = dao.findCurrentByDemographicNo(patient).getFirst();
        assertThat(dao.replaceCurrent(patient, "999998", "winner", first.getId())).isTrue();
        Immunizations winner = dao.findCurrentByDemographicNo(patient).getFirst();

        assertThat(dao.replaceCurrent(patient, "999998", "stale", first.getId())).isFalse();
        assertThat(dao.replaceCurrent(patient, "999998", "second initial save", 0)).isFalse();
        hibernateTemplate.flush();

        assertThat(dao.findCurrentByDemographicNo(patient)).singleElement()
                .satisfies(current -> {
                    assertThat(current.getId()).isEqualTo(winner.getId());
                    assertThat(current.getImmunizations()).isEqualTo("winner");
                });
        assertThat(dao.find(first.getId()).getArchived()).isEqualTo(1);
        assertThat(hibernateTemplate.find("from Immunizations i where i.demographicNo=?1", patient)).hasSize(2);
    }

    @Test
    void shouldRejectAnotherPatientsVersion_withoutCreatingASchedule() {
        int firstPatient = patient();
        int secondPatient = patient();
        assertThat(dao.replaceCurrent(firstPatient, "999998", "first patient", 0)).isTrue();
        int firstVersion = dao.findCurrentByDemographicNo(firstPatient).getFirst().getId();

        assertThat(dao.replaceCurrent(secondPatient, "999998", "wrong version", firstVersion)).isFalse();
        assertThat(dao.findCurrentByDemographicNo(secondPatient)).isEmpty();
        assertThat(dao.replaceCurrent(secondPatient, "999998", "second patient", 0)).isTrue();
        assertThat(dao.findCurrentByDemographicNo(firstPatient)).singleElement()
                .extracting(Immunizations::getImmunizations).isEqualTo("first patient");
    }
}
