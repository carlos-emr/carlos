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

import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed antenatal export ordering and cohort-selection compatibility checks. */
@Tag("integration")
@Tag("dao")
class AntenatalReportDaoIntegrationTest extends CarlosTestBase {
    @Autowired private DemographicExtDao extensions;
    @Autowired private AntenatalReportDao reports;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    @EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
    void shouldRetainPhysicalPlannerTextAndTinyintFlags_whenPatientOwnsTheForm() {
        // This unmapped legacy table is absent from the maintained schema. The opt-in
        // MariaDB harness owns its complete disposable database, including this fixture.
        entityManager.createNativeQuery("CREATE TABLE formAR (ID INT PRIMARY KEY, demographic_no INT,"
                + " c_finalEDB DATE, c_pName VARCHAR(80), pg1_wt DECIMAL(6,2), pg1_ht VARCHAR(20),"
                + " risk_yes TINYINT, risk_no TINYINT, risk_null TINYINT, nonflag INT)").executeUpdate();
        try {
            entityManager.createNativeQuery("INSERT INTO formAR VALUES"
                    + " (5547,101,'2026-08-19','  FAKE Planner  ',72.50,NULL,1,2,NULL,1)").executeUpdate();
            var planner = reports.findPlanner(5547, 101);
            assertThat(planner.edb()).isEqualTo("2026-08-19");
            assertThat(planner.patientName()).isEqualTo("  FAKE Planner  ");
            assertThat(planner.weight()).isEqualTo("72.50");
            assertThat(planner.height()).isNull();
            assertThat(planner.checked()).containsExactly("risk_yes");
            assertThat(reports.findPlanner(5547, 202)).isNull();
            assertThat(reports.findPlanner(5548, 101)).isNull();
        } finally {
            entityManager.createNativeQuery("DROP TABLE formAR").executeUpdate();
        }
    }

    @Test
    void shouldKeepOneLatestRowAcrossCohort_forLegacyExport() {
        int firstPatient = patient();
        int secondPatient = patient();
        int outsidePatient = patient();
        DemographicExt older = extension(firstPatient, "report5549", "older", 1000);
        DemographicExt newer = extension(secondPatient, "report5549", "newer", 2000);
        extension(outsidePatient, "report5549", "outside cohort", 3000);
        extension(firstPatient, "another key", "outside key", 4000);
        entityManager.flush();
        assertThat(extensions.findReportValues("report5549", List.of(firstPatient, secondPatient), true))
                .extracting(DemographicExtDao.ReportValue::id).containsExactly(newer.getId());
        assertThat(extensions.findReportValues("report5549", List.of(firstPatient, secondPatient), false))
                .extracting(DemographicExtDao.ReportValue::id).containsExactly(older.getId(), newer.getId());
        assertThat(extensions.findReportValues("report5549", List.of(), true)).isEmpty();
        assertThat(extensions.findReportValues("report5549' or '1'='1", List.of(firstPatient, secondPatient), false)).isEmpty();
    }

    @Test
    void shouldMapDistinctIdentifiersAndNulls_forBirthSummaryRows() throws Exception {
        entityManager.createNativeQuery("create temporary table formBCBirthSumMo (demographic_no integer)").executeUpdate();
        try {
            entityManager.createNativeQuery("insert into formBCBirthSumMo values (7), (7), (null), (11)").executeUpdate();
            assertThat(reports.birthSummaryDemographics()).containsExactlyInAnyOrder(7, 0, 11);
        } finally {
            entityManager.createNativeQuery("drop table formBCBirthSumMo").executeUpdate();
        }
    }

    @Test
    void shouldRejectOversizedCohorts_withoutReturningPartialBirthSummaryIdentifiers() throws Exception {
        entityManager.createNativeQuery("create temporary table formBCBirthSumMo (demographic_no integer)").executeUpdate();
        try {
            entityManager.createNativeQuery("insert into formBCBirthSumMo values (0), (1), (2), (3), (4), (5), (6), (7), (8), (9)").executeUpdate();
            for (int offset = 10; offset < 100_000; offset *= 10) {
                for (int digit = 1; digit <= 9; digit++) {
                    entityManager.createNativeQuery("insert into formBCBirthSumMo select demographic_no + ?1 from formBCBirthSumMo where demographic_no < ?2")
                            .setParameter(1, offset * digit).setParameter(2, offset).executeUpdate();
                }
            }
            assertThat(reports.birthSummaryDemographics()).hasSize(100_000);
            entityManager.createNativeQuery("insert into formBCBirthSumMo values (100000)").executeUpdate();
            assertThatThrownBy(() -> reports.birthSummaryDemographics())
                    .isInstanceOfSatisfying(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.LimitExceededException.class,
                            e -> assertThat(e.limit())
                                    .isEqualTo(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Limit.IDENTIFIERS))
                    .hasMessageContaining("more than 100000 patients or records");
        } finally {
            entityManager.createNativeQuery("drop table formBCBirthSumMo").executeUpdate();
        }
    }

    @Test
    void shouldChooseHighestIdentifier_whenLatestDatesTie() {
        int firstPatient = patient();
        int secondPatient = patient();
        extension(firstPatient, "tie5549", "first", 2000);
        DemographicExt second = extension(secondPatient, "tie5549", "second", 2000);
        entityManager.clear();
        assertThat(extensions.findReportValues("tie5549", List.of(firstPatient, secondPatient), true))
                .extracting(DemographicExtDao.ReportValue::id).containsExactly(second.getId());
    }

    private int patient() {
        var patient = new io.github.carlos_emr.carlos.commn.model.Demographic();
        patient.setFirstName("Synthetic");
        patient.setLastName("Antenatal5549");
        patient.setPrefName("");
        patient.setSex("F");
        patient.setYearOfBirth("1990");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        entityManager.persist(patient);
        entityManager.flush();
        return patient.getDemographicNo();
    }

    private DemographicExt extension(int demographic, String key, String value, long date) {
        DemographicExt row = new DemographicExt("5549", demographic, key, value);
        entityManager.persist(row);
        entityManager.flush();
        // Lifecycle callbacks replace dateCreated on JPA writes; set the native fixture after persist.
        entityManager.createNativeQuery("update demographicExt set date_time=?1 where id=?2")
                .setParameter(1, new java.sql.Timestamp(date)).setParameter(2, row.getId()).executeUpdate();
        return row;
    }
}
