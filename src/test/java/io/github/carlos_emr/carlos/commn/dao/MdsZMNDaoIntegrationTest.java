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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.MdsZMN;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Integration tests for {@link MdsZMNDao} covering find operations.
 *
 * <p>Migrated from legacy {@code MdsZMNDaoTest} (JUnit 4 / DaoTestFixtures)
 * with strengthened assertions and additional test scenarios.</p>
 *
 * @since 2026-03-07
 * @see MdsZMNDao
 */
@DisplayName("MdsZMN Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class MdsZMNDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MdsZMNDao dao;

    private MdsZMN createAndPersist(String reportName, String resultMnemonic) {
        MdsZMN entity = new MdsZMN();
        entity.setReportName(reportName);
        entity.setResultMnemonic(resultMnemonic);
        entity.setResultMnemonicVersion("1.0");
        entity.setUnits("mg/dL");
        entity.setCummulativeSequence("001");
        entity.setReferenceRange("10-100");
        entity.setResultCode("RC01");
        entity.setReportForm("FORM1");
        entity.setReportGroup("GRP1");
        entity.setReportGroupVersion("1.0");
        dao.persist(entity);
        hibernateTemplate.flush();
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity and generate ID")
    void shouldPersistEntity_withGeneratedId() {
        MdsZMN entity = createAndPersist("RPT_TEST", "MNEM_TEST");

        assertThat(entity.getId()).isNotNull().isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by segment ID and report name")
    void shouldReturnEntity_whenFindingBySegmentIdAndReportName() {
        MdsZMN saved = createAndPersist("RPT_MATCH", "MNEM_A");

        MdsZMN found = dao.findBySegmentIdAndReportName(saved.getId(), "RPT_MATCH");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getReportName()).isEqualTo("RPT_MATCH");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when report name does not match")
    void shouldReturnNull_whenReportNameDoesNotMatch() {
        MdsZMN saved = createAndPersist("RPT_REAL", "MNEM_B");

        MdsZMN found = dao.findBySegmentIdAndReportName(saved.getId(), "RPT_NONEXISTENT");

        assertThat(found).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by segment ID and result mnemonic")
    void shouldReturnEntity_whenFindingBySegmentIdAndResultMnemonic() {
        MdsZMN saved = createAndPersist("RPT_C", "MNEM_MATCH");

        MdsZMN found = dao.findBySegmentIdAndResultMnemonic(saved.getId(), "MNEM_MATCH");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getResultMnemonic()).isEqualTo("MNEM_MATCH");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when result mnemonic does not match")
    void shouldReturnNull_whenResultMnemonicDoesNotMatch() {
        MdsZMN saved = createAndPersist("RPT_D", "MNEM_REAL");

        MdsZMN found = dao.findBySegmentIdAndResultMnemonic(saved.getId(), "MNEM_NONEXISTENT");

        assertThat(found).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no result codes match")
    void shouldReturnEmptyList_whenNoResultCodesMatch() {
        List<String> codes = dao.findResultCodes(99999, "NONEXISTENT");

        assertThat(codes).isNotNull().isEmpty();
    }
}
