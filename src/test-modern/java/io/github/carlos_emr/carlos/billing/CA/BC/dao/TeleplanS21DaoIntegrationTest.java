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

import io.github.carlos_emr.carlos.billing.CA.BC.model.TeleplanS21;
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
 * Integration tests for {@link TeleplanS21Dao}.
 * <p>Migrated from legacy JUnit 4 / DaoTestFixtures with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("TeleplanS21 Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class TeleplanS21DaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TeleplanS21Dao dao;

    private TeleplanS21 createEntity(String fileName, String payment, String payeeNo, Character status) throws Exception {
        TeleplanS21 entity = new TeleplanS21();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setFileName(fileName);
        entity.setPayment(payment);
        entity.setPayeeNo(payeeNo);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        TeleplanS21 entity = new TeleplanS21();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entity by ID with matching field values")
    void shouldReturnEntity_whenValidIdProvided() throws Exception {
        TeleplanS21 saved = createEntity("file1.txt", "PAY1", "PN001", 'A');
        dao.persist(saved);

        TeleplanS21 found = dao.find(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getFileName()).isEqualTo("file1.txt");
        assertThat(found.getPayment()).isEqualTo("PAY1");
        assertThat(found.getPayeeNo()).isEqualTo("PN001");
    }

    @Test
    @Tag("read")
    @DisplayName("should find records by filename, payment, and payee number")
    void shouldReturnMatchingRecords_byFilenamePaymentPayeeNo() throws Exception {
        TeleplanS21 match = createEntity("remit.txt", "PMT100", "PY001", 'A');
        TeleplanS21 diffFile = createEntity("other.txt", "PMT100", "PY001", 'A');
        TeleplanS21 diffPay = createEntity("remit.txt", "PMT999", "PY001", 'A');
        dao.persist(match);
        dao.persist(diffFile);
        dao.persist(diffPay);

        List<TeleplanS21> results = dao.findByFilenamePaymentPayeeNo("remit.txt", "PMT100", "PY001");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFileName()).isEqualTo("remit.txt");
        assertThat(results.get(0).getPayment()).isEqualTo("PMT100");
        assertThat(results.get(0).getPayeeNo()).isEqualTo("PY001");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no matching filename/payment/payee")
    void shouldReturnEmptyList_whenNoMatchingFilenamePaymentPayee() throws Exception {
        TeleplanS21 entity = createEntity("file.txt", "PAY1", "PN1", 'A');
        dao.persist(entity);

        List<TeleplanS21> results = dao.findByFilenamePaymentPayeeNo("nofile", "nopay", "nopayee");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should search all records excluding given status")
    void shouldReturnRecordsExcludingStatus_whenSearchAllTahd() throws Exception {
        TeleplanS21 active = createEntity("f1.txt", "PAY1", "PN1", 'A');
        TeleplanS21 deleted = createEntity("f2.txt", "PAY2", "PN2", 'D');
        TeleplanS21 active2 = createEntity("f3.txt", "PAY3", "PN3", 'A');
        dao.persist(active);
        dao.persist(deleted);
        dao.persist(active2);

        // Exclude status 'D' - should return only records with status != 'D'
        // Note: status is Character, query uses String parameter
        List<TeleplanS21> results = dao.search_all_tahd("D");
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> assertThat(r.getStatus()).isNotEqualTo('D'));
    }

    @Test
    @Tag("read")
    @DisplayName("should return all records when excluded status does not match any")
    void shouldReturnAllRecords_whenExcludedStatusNotPresent() throws Exception {
        TeleplanS21 e1 = createEntity("f1.txt", "PAY1", "PN1", 'A');
        TeleplanS21 e2 = createEntity("f2.txt", "PAY2", "PN2", 'B');
        dao.persist(e1);
        dao.persist(e2);

        List<TeleplanS21> results = dao.search_all_tahd("Z");
        assertThat(results).hasSize(2);
    }
}
