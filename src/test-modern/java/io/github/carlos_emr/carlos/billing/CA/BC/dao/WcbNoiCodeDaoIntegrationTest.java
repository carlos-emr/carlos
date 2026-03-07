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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.billing.CA.BC.model.WcbNoiCode;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WcbNoiCodeDao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("WcbNoiCode Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class WcbNoiCodeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private WcbNoiCodeDao dao;

    private WcbNoiCode createEntity(String code, String level1, String level2, String level3) {
        WcbNoiCode entity = new WcbNoiCode();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCode(code);
        entity.setLevel1(level1);
        entity.setLevel2(level2);
        entity.setLevel3(level3);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() {
        WcbNoiCode entity = new WcbNoiCode();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with correct field values")
    void shouldReturnEntity_whenValidIdProvided() {
        WcbNoiCode saved = createEntity("C001", "Injuries", "Head", "Skull");
        dao.persist(saved);

        WcbNoiCode found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCode()).isEqualTo("C001");
        assertThat(found.getLevel1()).isEqualTo("Injuries");
        assertThat(found.getLevel2()).isEqualTo("Head");
        assertThat(found.getLevel3()).isEqualTo("Skull");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by exact code match")
    void shouldReturnMatchingRecords_byCodeMatch() {
        WcbNoiCode match = createEntity("BURN", "Burns", "Chemical", "Acid");
        WcbNoiCode noMatch = createEntity("FRAC", "Fractures", "Arm", "Radius");
        dao.persist(match);
        dao.persist(noMatch);

        // findByCodeOrLevel uses LIKE, so exact match works
        List<WcbNoiCode> results = dao.findByCodeOrLevel("BURN");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCode()).isEqualTo("BURN");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by level1 match")
    void shouldReturnMatchingRecords_byLevel1Match() {
        WcbNoiCode match = createEntity("C010", "Sprains", "Knee", "ACL");
        WcbNoiCode noMatch = createEntity("C020", "Fractures", "Hip", "Femur");
        dao.persist(match);
        dao.persist(noMatch);

        List<WcbNoiCode> results = dao.findByCodeOrLevel("Sprains");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLevel1()).isEqualTo("Sprains");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by level2 match")
    void shouldReturnMatchingRecords_byLevel2Match() {
        WcbNoiCode match = createEntity("C030", "Injuries", "Shoulder", "Rotator");
        WcbNoiCode noMatch = createEntity("C040", "Injuries", "Elbow", "Tendon");
        dao.persist(match);
        dao.persist(noMatch);

        List<WcbNoiCode> results = dao.findByCodeOrLevel("Shoulder");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLevel2()).isEqualTo("Shoulder");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by level3 match")
    void shouldReturnMatchingRecords_byLevel3Match() {
        WcbNoiCode match = createEntity("C050", "Injuries", "Hand", "Carpal");
        WcbNoiCode noMatch = createEntity("C060", "Injuries", "Hand", "Finger");
        dao.persist(match);
        dao.persist(noMatch);

        List<WcbNoiCode> results = dao.findByCodeOrLevel("Carpal");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLevel3()).isEqualTo("Carpal");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no matching code or level")
    void shouldReturnEmptyList_whenNoMatchFound() {
        WcbNoiCode entity = createEntity("ABC", "Level1", "Level2", "Level3");
        dao.persist(entity);

        List<WcbNoiCode> results = dao.findByCodeOrLevel("NONEXISTENT");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find records matching across multiple fields via OR")
    void shouldReturnMultipleRecords_whenMatchAcrossFields() {
        // "MATCH" appears as code in one record and level1 in another
        WcbNoiCode matchByCode = createEntity("MATCH", "Other1", "Other2", "Other3");
        WcbNoiCode matchByLevel1 = createEntity("DIFF", "MATCH", "Other2", "Other3");
        WcbNoiCode noMatch = createEntity("DIFF2", "Other1", "Other2", "Other3");
        dao.persist(matchByCode);
        dao.persist(matchByLevel1);
        dao.persist(noMatch);

        List<WcbNoiCode> results = dao.findByCodeOrLevel("MATCH");
        assertThat(results).hasSize(2);
    }
}
