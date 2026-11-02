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

import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** MariaDB-native billing date, age, deletion, provider, and distinct aggregate regression checks. */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class BilledProviderReportDaoIntegrationTest extends CarlosTestBase {
    @Autowired private BilledProviderReportDao reports;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldPreserveProviderDateDeletionAndDistinctCounts_forPhysicalMariaDb() {
        Demographic patient = new Demographic();
        patient.setFirstName("Synthetic");
        patient.setLastName("Report5549");
        patient.setSex("F");
        patient.setYearOfBirth("2000");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("01");
        entityManager.persist(patient);
        entityManager.flush();
        Billing included = billing(patient.getDemographicNo(), "report5549", "O", "2026-08-01");
        detail(included.getId(), "O", "2026-07-31");
        detail(included.getId(), "O", "2026-08-01"); // Duplicate details count as one visit and one patient.
        detail(billing(patient.getDemographicNo(), "other5549", "O", "2026-08-01").getId(), "O", "2026-08-01");
        detail(billing(patient.getDemographicNo(), "report5549", "D", "2026-08-01").getId(), "O", "2026-08-01");
        detail(billing(patient.getDemographicNo(), "report5549", "O", "2026-08-02").getId(), "O", "2026-08-01");
        detail(billing(patient.getDemographicNo(), "report5549", "O", "2026-08-01").getId(), "D", "2026-08-01");
        entityManager.flush();
        Map<String, Long> service = reports.counts(false, "report5549", "2026-08-01", "2026-08-01", "S5549", "Synthetic service")
                .stream().collect(Collectors.toMap(BilledProviderReportDao.MetricCount::key, BilledProviderReportDao.MetricCount::value));
        assertThat(service).containsEntry("pat", 1L).containsEntry("vis", 1L)
                .containsEntry("patSexF", 1L).containsEntry("visSexF", 1L)
                .containsEntry("patSexM", 0L).containsEntry("pat21_34", 1L).containsEntry("pat71_", 0L);
        Map<String, Long> diagnosis = reports.counts(true, "report5549", "2026-08-01", "2026-08-01", "D5549", "ignored")
                .stream().collect(Collectors.toMap(BilledProviderReportDao.MetricCount::key, BilledProviderReportDao.MetricCount::value));
        assertThat(diagnosis).isEqualTo(service);
        assertThat(reports.codes(false, "2026-07-31", "2026-07-31"))
                .contains(new BilledProviderReportDao.Code("S5549", "Synthetic service"));
        assertThat(reports.counts(false, "report5549' OR '1'='1", "2026-08-01", "2026-08-01", "S5549", "Synthetic service"))
                .allMatch(count -> count.value() == 0);
    }

    @Test
    void shouldPreserveDuplicateRolesAndNameOrdering_forProviderReport() {
        provider("R54901", "Z5549", "Last");
        provider("R54902", "A5549", "Last");
        provider("R54903", "A5549", "");
        entityManager.createNativeQuery("insert into secUserRole (provider_no, role_name, orgcd, activeyn) values "
                + "('R54901','doctor','',1), ('R54901','doctor','',1), ('R54902','nurse','',1)").executeUpdate();
        entityManager.flush(); entityManager.clear();
        var roles = reports.providerRoles().stream().filter(row -> row.providerNo().startsWith("R549")).toList();
        assertThat(roles).extracting(BilledProviderReportDao.ProviderRole::providerNo)
                .containsExactly("R54902", "R54901", "R54901");
        var providers = reports.providers().stream().filter(row -> row.providerNo().startsWith("R549")).toList();
        assertThat(providers).extracting(BilledProviderReportDao.ProviderDetails::providerNo)
                .containsExactly("R54903", "R54902", "R54901");
        assertThat(providers.getFirst().lastName()).isEmpty();
    }

    private void provider(String number, String first, String last) {
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider(number, last, "doctor", "F", "", first);
        entityManager.persist(provider);
    }

    private Billing billing(int patient, String provider, String status, String date) {
        Billing row = new Billing();
        row.setDemographicNo(patient);
        row.setCreator(provider);
        row.setStatus(status);
        row.setBillingDate(Date.valueOf(date));
        entityManager.persist(row);
        entityManager.flush();
        return row;
    }

    private void detail(int billingNo, String status, String date) {
        BillingDetail row = new BillingDetail();
        row.setBillingNo(billingNo);
        row.setDiagnosticCode("D5549");
        row.setServiceCode("S5549");
        row.setServiceDesc("Synthetic service");
        row.setBillingAmount("12.30");
        row.setAppointmentDate(Date.valueOf(date));
        row.setStatus(status);
        entityManager.persist(row);
    }
}
