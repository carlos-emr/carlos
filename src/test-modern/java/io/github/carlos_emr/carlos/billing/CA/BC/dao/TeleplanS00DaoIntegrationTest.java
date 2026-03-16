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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS00;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeleplanS00Dao}.
 * <p>Migrated from legacy JUnit 4 TeleplanS00DaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS00Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS00DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS00Dao dao;

    private TeleplanS00 createEntity(String mspCtlNo, String officeNo, String practitionerNo,
                                     Integer s21Id, String s00Type) throws Exception {
        TeleplanS00 entity = new TeleplanS00();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setMspCtlNo(mspCtlNo);
        entity.setOfficeNo(officeNo);
        entity.setPractitionerNo(practitionerNo);
        entity.setS21Id(s21Id);
        entity.setS00Type(s00Type);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated test data")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        TeleplanS00 entity = new TeleplanS00();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find all persisted records")
    void shouldReturnAllRecords_whenFindAllCalled() throws Exception {
        TeleplanS00 entity1 = createEntity("CTL1", "OFF1", "PR1", 1, "T1");
        TeleplanS00 entity2 = createEntity("CTL2", "OFF2", "PR2", 2, "T2");
        dao.persist(entity1);
        dao.persist(entity2);

        List<TeleplanS00> results = dao.findAll();
        assertThat(results).hasSize(2);
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by billing number (mspCtlNo)")
    void shouldReturnMatchingRecords_byBillingNo() throws Exception {
        TeleplanS00 match = createEntity("MATCH1", "OFF1", "PR1", 1, "T1");
        TeleplanS00 noMatch = createEntity("OTHER1", "OFF2", "PR2", 2, "T2");
        dao.persist(match);
        dao.persist(noMatch);

        List<TeleplanS00> results = dao.findByBillingNo("MATCH1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMspCtlNo()).isEqualTo("MATCH1");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when billing number not found")
    void shouldReturnEmptyList_whenBillingNoNotFound() throws Exception {
        TeleplanS00 entity = createEntity("CTL1", "OFF1", "PR1", 1, "T1");
        dao.persist(entity);

        List<TeleplanS00> results = dao.findByBillingNo("NONEXISTENT");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by single office number")
    void shouldReturnMatchingRecords_byOfficeNumber() throws Exception {
        TeleplanS00 match = createEntity("CTL1", "OFF-A", "PR1", 1, "T1");
        TeleplanS00 noMatch = createEntity("CTL2", "OFF-B", "PR2", 2, "T2");
        dao.persist(match);
        dao.persist(noMatch);

        List<TeleplanS00> results = dao.findByOfficeNumber("OFF-A");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOfficeNo()).isEqualTo("OFF-A");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by multiple office numbers")
    void shouldReturnMatchingRecords_byMultipleOfficeNumbers() throws Exception {
        TeleplanS00 match1 = createEntity("CTL1", "OFF-X", "PR1", 1, "T1");
        TeleplanS00 match2 = createEntity("CTL2", "OFF-Y", "PR2", 2, "T2");
        TeleplanS00 noMatch = createEntity("CTL3", "OFF-Z", "PR3", 3, "T3");
        dao.persist(match1);
        dao.persist(match2);
        dao.persist(noMatch);

        List<TeleplanS00> results = dao.findByOfficeNumbers(Arrays.asList("OFF-X", "OFF-Y"));
        assertThat(results).hasSize(2);
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for empty office numbers list")
    void shouldReturnEmptyList_whenOfficeNumbersListEmpty() throws Exception {
        TeleplanS00 entity = createEntity("CTL1", "OFF1", "PR1", 1, "T1");
        dao.persist(entity);

        List<TeleplanS00> results = dao.findByOfficeNumbers(Collections.emptyList());
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find BG records across all exp fields")
    void shouldReturnBgRecords_whenExpFieldContainsBg() throws Exception {
        TeleplanS00 bgInExp1 = createEntity("CTL1", "OFF1", "PR1", 1, "T1");
        bgInExp1.setExp1("BG");
        bgInExp1.setExp2("XX");
        bgInExp1.setExp3("XX");
        bgInExp1.setExp4("XX");
        bgInExp1.setExp5("XX");
        bgInExp1.setExp6("XX");
        bgInExp1.setExp7("XX");
        dao.persist(bgInExp1);

        TeleplanS00 bgInExp3 = createEntity("CTL2", "OFF2", "PR2", 2, "T2");
        bgInExp3.setExp1("XX");
        bgInExp3.setExp2("XX");
        bgInExp3.setExp3("BG");
        bgInExp3.setExp4("XX");
        bgInExp3.setExp5("XX");
        bgInExp3.setExp6("XX");
        bgInExp3.setExp7("XX");
        dao.persist(bgInExp3);

        TeleplanS00 noBg = createEntity("CTL3", "OFF3", "PR3", 3, "T3");
        noBg.setExp1("XX");
        noBg.setExp2("XX");
        noBg.setExp3("XX");
        noBg.setExp4("XX");
        noBg.setExp5("XX");
        noBg.setExp6("XX");
        noBg.setExp7("XX");
        dao.persist(noBg);

        List<TeleplanS00> results = dao.findBgs();
        assertThat(results).hasSize(2);
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no BG records exist")
    void shouldReturnEmptyList_whenNoBgRecordsExist() throws Exception {
        TeleplanS00 noBg = createEntity("CTL1", "OFF1", "PR1", 1, "T1");
        noBg.setExp1("XX");
        noBg.setExp2("YY");
        noBg.setExp3("ZZ");
        noBg.setExp4("AA");
        noBg.setExp5("BB");
        noBg.setExp6("CC");
        noBg.setExp7("DD");
        dao.persist(noBg);

        List<TeleplanS00> results = dao.findBgs();
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should search S00 records by s21Id, excluding type, matching practitioner")
    void shouldReturnFilteredRecords_byS21IdAndTypeAndPractitioner() throws Exception {
        TeleplanS00 match = createEntity("CTL1", "OFF1", "PR100", 10, "S00");
        TeleplanS00 wrongType = createEntity("CTL2", "OFF2", "PR100", 10, "EXCL");
        TeleplanS00 wrongS21 = createEntity("CTL3", "OFF3", "PR100", 99, "S00");
        dao.persist(match);
        dao.persist(wrongType);
        dao.persist(wrongS21);

        // search_taS00 finds where s21Id=10, s00Type<>"EXCL", practitionerNo like "PR100"
        List<TeleplanS00> results = dao.search_taS00(10, "EXCL", "PR100");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getS00Type()).isEqualTo("S00");
        assertThat(results.get(0).getPractitionerNo()).isEqualTo("PR100");
    }

    @Test
    @Tag("read")
    @DisplayName("should search S01 records by s21Id, excluding type, matching practitioner")
    void shouldReturnFilteredRecords_forSearchTaS01() throws Exception {
        TeleplanS00 match = createEntity("CTL1", "OFF1", "PR200", 20, "S01");
        TeleplanS00 excluded = createEntity("CTL2", "OFF2", "PR200", 20, "SKIP");
        dao.persist(match);
        dao.persist(excluded);

        // search_taS01 has same query as search_taS00
        List<TeleplanS00> results = dao.search_taS01(20, "SKIP", "PR200");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getS00Type()).isEqualTo("S01");
    }
}
