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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriaBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.CriteriasBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WaitlistDao}.
 * Migrated from legacy JUnit 4 WaitlistDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("WaitlistDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class WaitlistDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WaitlistDao dao;

    @Test
    @Tag("read")
    @DisplayName("should return client matches for vacancy ID")
    void shouldReturnClientMatches_byVacancyId() {
        assertThat(dao.getClientMatches(1)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when searching for matching eforms with no matches")
    void shouldReturnEmptyList_whenNoMatchingEforms() {
        CriteriasBO crits = new CriteriasBO();
        CriteriaBO crit = new CriteriaBO();
        crit.value = "test";
        CriteriaBO[] critArray = {crit};
        crits.crits = critArray;

        assertThat(dao.searchForMatchingEforms(crits)).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return display vacancies for wait list program")
    void shouldReturnDisplayVacancies_forWaitListProgram() {
        assertThat(dao.listDisplayVacanciesForWaitListProgram(1)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return display vacancies for all wait list programs")
    void shouldReturnDisplayVacancies_forAllWaitListPrograms() {
        assertThat(dao.listDisplayVacanciesForAllWaitListPrograms()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return display vacancies for agency program")
    void shouldReturnDisplayVacancies_forAgencyProgram() {
        assertThat(dao.getDisplayVacanciesForAgencyProgram(1)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return null for non-existent display vacancy")
    void shouldReturnNull_whenDisplayVacancyNotFound() {
        assertThat(dao.getDisplayVacancy(45)).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should load stats for vacancy display without error")
    void shouldLoadStats_withoutError() {
        VacancyDisplayBO vd = new VacancyDisplayBO();
        vd.setVacancyID(1);
        dao.loadStats(vd);
    }
}
