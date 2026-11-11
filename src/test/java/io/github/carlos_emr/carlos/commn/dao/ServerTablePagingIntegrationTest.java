// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration") @Tag("dao") @Transactional
class ServerTablePagingIntegrationTest extends CarlosTestBase {
    @Autowired private ProfessionalSpecialistDao specialists;
    @Autowired private DemographicDao demographics;
    @jakarta.persistence.PersistenceContext(unitName = "entityManagerFactory")
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldBoundRowsAndRetainEverySpecialist_whenTraversingDuplicateNames() {
        String prefix = "Page" + Long.toString(System.nanoTime(), 36);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 37; i++) {
            var specialist = new ProfessionalSpecialist();
            specialist.setFirstName("Same"); specialist.setLastName(prefix); specialist.setDeleted(false);
            specialists.persist(specialist); expected.add(specialist.getId());
        }
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = specialists.findDirectoryPage(null, prefix, "", null, null, true, i);
            assertThat(page.total()).isEqualTo(37);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(15);
            for (var row : page.rows()) assertThat(actual.add(row.getId())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        var last = specialists.findDirectoryPage(null, prefix, "", null, null, true, 999);
        assertThat(last.page()).isEqualTo(2);
        assertThat(last.rows()).hasSize(7);
    }

    @Test
    void shouldKeepFiltersAndAllSortColumns_whenPagingClientSearch() {
        String prefix = "Fake" + Long.toString(System.nanoTime(), 36);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            Demographic patient = new Demographic();
            patient.setFirstName(prefix); patient.setLastName("Same"); patient.setSex("M");
            patient.setPrefName("");
            patient.setYearOfBirth("1990"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("01");
            entityManager.persist(patient); expected.add(patient.getDemographicNo());
        }
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName(prefix); criteria.setSearchOutsideDomain(true);
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = demographics.searchClientPage(criteria, i, 1, false);
            assertThat(page.total()).isEqualTo(23);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(10);
            for (var row : page.rows()) assertThat(actual.add(row.getDemographicNo())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        for (int sort = 2; sort <= 7; sort++) {
            assertThat(demographics.searchClientPage(criteria, 0, sort, true).rows()).hasSize(10);
        }
        criteria.setFirstName(prefix + "absent");
        assertThat(demographics.searchClientPage(criteria, 999, 1, false).rows()).isEmpty();
    }

    @Test
    void shouldBoundNativeSoundexAndKeepItsFilters_whenPagingClientSearch() {
        String chart = "SX" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            Demographic patient = new Demographic();
            patient.setFirstName("Robert"); patient.setLastName("Synthetic"); patient.setSex("M");
            patient.setPrefName(""); patient.setChartNo(chart);
            patient.setYearOfBirth("1990"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("01");
            entityManager.persist(patient); expected.add(patient.getDemographicNo());
        }
        entityManager.flush();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName("Rupert"); criteria.setChartNo(chart); criteria.setSearchUsingSoundex(true);
        criteria.setSearchOutsideDomain(true); criteria.setActive("0"); criteria.setGender("M");
        Set<Integer> actual = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            var page = demographics.searchClientPage(criteria, i, 2, false);
            assertThat(page.total()).isEqualTo(23);
            assertThat(page.rows()).hasSizeLessThanOrEqualTo(10);
            for (var row : page.rows()) assertThat(actual.add(row.getDemographicNo())).isTrue();
        }
        assertThat(actual).isEqualTo(expected);
        for (int sort = 3; sort <= 7; sort++) {
            assertThat(demographics.searchClientPage(criteria, 0, sort, true).rows()).hasSize(10);
        }
        criteria.setGender("F");
        assertThat(demographics.searchClientPage(criteria, 0, 1, false).total()).isZero();
        criteria.setGender("M"); criteria.setSearchOutsideDomain(false);
        assertThat(demographics.searchClientPage(criteria, 0, 1, false).total()).isZero();
    }

    @Test
    void shouldKeepSearchValuesBound_whenCriteriaContainSqlSyntax() {
        String literal = "no_match_' OR 1=1 --";
        assertThat(specialists.findDirectoryPage(null, literal, "", null, null, true, 0).total()).isZero();
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setFirstName(literal); criteria.setSearchOutsideDomain(true);
        assertThat(demographics.searchClientPage(criteria, 0, 2, false).total()).isZero();
        criteria.setFirstName("Robert"); criteria.setChartNo(literal); criteria.setSearchUsingSoundex(true);
        assertThat(demographics.searchClientPage(criteria, 0, 2, false).total()).isZero();
    }

}
