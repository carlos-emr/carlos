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
import io.github.carlos_emr.carlos.PMmodule.wlmatch.MatchBO;
import io.github.carlos_emr.carlos.PMmodule.wlmatch.VacancyDisplayBO;
import io.github.carlos_emr.carlos.match.client.ClientData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WaitlistDao}.
 *
 * <p>WaitlistDao uses native SQL queries that join multiple tables
 * ({@code vacancy}, {@code vacancy_template}, {@code vacancy_client_match},
 * {@code demographic}, {@code eform_data}, {@code eform_values},
 * {@code client_referral}, {@code program}, {@code criteria},
 * {@code criteria_type}, {@code criteria_selection_option}).
 * These tables must exist in the test database for the native queries
 * to execute. Tests verify empty-result behavior when no matching
 * data exists, and null/boundary handling.</p>
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

    @Nested
    @DisplayName("Client Match Tests")
    class ClientMatchTests {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no client matches exist for vacancy ID")
        void shouldReturnEmptyList_whenNoClientMatchesExist() {
            List<MatchBO> result = dao.getClientMatches(99999);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no matches meet minimum percentage")
        void shouldReturnEmptyList_whenNoMatchesMeetMinPercentage() {
            List<MatchBO> result = dao.getClientMatchesWithMinPercentage(99999, 50.0);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("EForm Search Tests")
    class EFormSearchTests {

        @Test
        @Tag("read")
        @DisplayName("should return empty collection when no eforms match single value criteria")
        void shouldReturnEmptyCollection_whenNoEformsMatchSingleValue() {
            CriteriasBO crits = new CriteriasBO();
            CriteriaBO crit = new CriteriaBO();
            crit.field = "test-field";
            crit.value = "nonexistent-value";
            crits.crits = new CriteriaBO[]{crit};

            Collection<?> result = dao.searchForMatchingEforms(crits);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty collection when no eforms match range criteria")
        void shouldReturnEmptyCollection_whenNoEformsMatchRange() {
            CriteriasBO crits = new CriteriasBO();
            CriteriaBO crit = new CriteriaBO();
            crit.field = "age-years";
            crit.rangeStart = 18;
            crit.rangeEnd = 65;
            crits.crits = new CriteriaBO[]{crit};

            Collection<?> result = dao.searchForMatchingEforms(crits);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty collection when no eforms match multi-value criteria")
        void shouldReturnEmptyCollection_whenNoEformsMatchMultiValue() {
            CriteriasBO crits = new CriteriasBO();
            CriteriaBO crit = new CriteriaBO();
            crit.field = "gender";
            crit.values = new String[]{"Male", "Female"};
            crits.crits = new CriteriaBO[]{crit};

            Collection<?> result = dao.searchForMatchingEforms(crits);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vacancy Display Tests")
    class VacancyDisplayTests {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no vacancies exist for wait list program")
        void shouldReturnEmptyList_whenNoVacanciesForWaitListProgram() {
            List<VacancyDisplayBO> result = dao.listDisplayVacanciesForWaitListProgram(99999);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no active vacancies exist for any program")
        void shouldReturnEmptyList_whenNoActiveVacanciesExist() {
            List<VacancyDisplayBO> result = dao.listDisplayVacanciesForAllWaitListPrograms();
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no vacancies exist for agency program")
        void shouldReturnEmptyList_whenNoVacanciesForAgencyProgram() {
            List<VacancyDisplayBO> result = dao.getDisplayVacanciesForAgencyProgram(99999);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null when display vacancy does not exist")
        void shouldReturnNull_whenDisplayVacancyNotFound() {
            VacancyDisplayBO result = dao.getDisplayVacancy(99999);
            assertThat(result).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return null program ID for non-existent vacancy")
        void shouldReturnNull_whenVacancyIdDoesNotExist() {
            Integer programId = dao.getProgramIdByVacancyId(99999);
            assertThat(programId).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list for listNoOfVacanciesForWaitListProgram when no data")
        void shouldReturnEmptyList_whenNoVacanciesForCount() {
            List<VacancyDisplayBO> result = dao.listNoOfVacanciesForWaitListProgram();
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list for listVacanciesForWaitListProgram when no data")
        void shouldReturnEmptyList_whenNoVacanciesForList() {
            List<VacancyDisplayBO> result = dao.listVacanciesForWaitListProgram();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Load Stats Tests")
    class LoadStatsTests {

        @Test
        @Tag("read")
        @DisplayName("should set all stats to zero for non-existent vacancy")
        void shouldSetStatsToZero_forNonExistentVacancy() {
            VacancyDisplayBO bo = new VacancyDisplayBO();
            bo.setVacancyID(99999);
            dao.loadStats(bo);

            assertThat(bo.getRejectedCount()).isEqualTo(0);
            assertThat(bo.getAcceptedCount()).isEqualTo(0);
            assertThat(bo.getPendingCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Client Data Tests")
    class ClientDataTests {

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no client data exists")
        void shouldReturnEmptyList_whenNoClientDataExists() {
            List<ClientData> result = dao.getAllClientsData();
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no client data exists for program")
        void shouldReturnEmptyList_whenNoClientDataForProgram() {
            List<ClientData> result = dao.getAllClientsDataByProgramId(99999);
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return client data with correct client ID even when no form data")
        void shouldReturnClientDataWithId_whenNoFormData() {
            ClientData result = dao.getClientData(99999);
            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo(99999);
            assertThat(result.getClientData()).isEmpty();
        }
    }
}
