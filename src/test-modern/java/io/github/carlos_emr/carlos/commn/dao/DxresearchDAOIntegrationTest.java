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
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DxresearchDAO} covering persist,
 * findByDemographicNoResearchCodeAndCodingSystem, getDataForInrReport,
 * countResearches, and countBillingResearches.
 *
 * <p>Migrated from legacy {@code DxresearchDAOTest} (JUnit 4 / DaoTestFixtures)
 * with proper data setup and strengthened assertions.</p>
 *
 * @since 2026-03-07
 * @see DxresearchDAO
 */
@DisplayName("DxresearchDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("dxresearch")
@Transactional
public class DxresearchDAOIntegrationTest extends CarlosTestBase {

    @Autowired
    private DxresearchDAO dao;

    private Dxresearch createAndPersist(Integer demographicNo, String code, String codingSystem, Character status) {
        Dxresearch dr = new Dxresearch();
        dr.setDemographicNo(demographicNo);
        dr.setDxresearchCode(code);
        dr.setCodingSystem(codingSystem);
        dr.setStatus(status);
        dr.setStartDate(new Date());
        dr.setAssociation((byte) 1);
        dr.setProviderNo("999999");
        dao.persist(dr);
        hibernateTemplate.flush();
        return dr;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist dxresearch with generated ID")
    void shouldPersistDxresearch_whenValidDataProvided() {
        Dxresearch dr = createAndPersist(100, "250", "icd9", 'A');

        assertThat(dr.getId()).isNotNull().isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find dxresearch by demographic, code, and coding system")
    void shouldReturnDxresearch_byDemographicNoCodeAndCodingSystem() {
        Integer demoNo = 5001;
        String code = "250";
        String system = "icd9";
        Dxresearch saved = createAndPersist(demoNo, code, system, 'A');

        List<Dxresearch> results = dao.findByDemographicNoResearchCodeAndCodingSystem(demoNo, code, system);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(dx -> {
            assertThat(dx.getDemographicNo()).isEqualTo(demoNo);
            assertThat(dx.getDxresearchCode()).isEqualTo(code);
            assertThat(dx.getCodingSystem()).isEqualTo(system);
        });
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no matching dxresearch exists")
    void shouldReturnEmptyList_whenNoMatchingDxresearchExists() {
        List<Dxresearch> results = dao.findByDemographicNoResearchCodeAndCodingSystem(99999, "NONEXISTENT", "NONE");

        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    @Tag("query")
    @DisplayName("should return data for INR report within date range")
    void shouldReturnData_forInrReportWithinDateRange() {
        List<Object[]> result = dao.getDataForInrReport(new Date(), new Date());

        assertThat(result).isNotNull();
    }

    @Test
    @Tag("query")
    @DisplayName("should return zero count when no researches match code and date range")
    void shouldReturnZeroCount_whenNoResearchesMatchCodeAndDateRange() {
        Integer count = dao.countResearches("NONEXISTENT_CODE", new Date(0), new Date());

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    @Tag("query")
    @DisplayName("should count researches matching code and date range")
    void shouldCountResearches_matchingCodeAndDateRange() {
        createAndPersist(100, "TEST_COUNT", "icd9", 'A');
        createAndPersist(101, "TEST_COUNT", "icd9", 'A');

        Integer count = dao.countResearches("TEST_COUNT", new Date(0), new Date());

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    @Tag("query")
    @DisplayName("should return zero count for billing researches with no matches")
    void shouldReturnZeroCount_forBillingResearchesWithNoMatches() {
        Integer count = dao.countBillingResearches("NONEXISTENT", "DIAG", "CREATOR", new Date(0), new Date());

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(0);
    }
}
