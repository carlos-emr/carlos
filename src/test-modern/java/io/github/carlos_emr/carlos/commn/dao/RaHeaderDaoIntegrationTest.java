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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link RaHeaderDao} covering
 * create, findCurrentByFilenamePaymentDate, findByFilenamePaymentDate,
 * findAllExcludeStatus, findByHeaderDetailsAndProviderMagic,
 * and findByStatusAndProviderMagic.
 *
 * <p>Migrated from legacy {@code RaHeaderDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see RaHeaderDao
 */
@DisplayName("RaHeaderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class RaHeaderDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private RaHeaderDao dao;

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist RaHeader with generated ID")
        void shouldPersistRaHeader_whenValidDataProvided() {
            RaHeader entity = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find current headers by filename and payment date excluding deleted")
        void shouldFindCurrentHeaders_byFilenameAndPaymentDate() {
            RaHeader rh1 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh1);
            rh1.setFilename("alpha");
            rh1.setStatus("A");
            rh1.setPaymentDate("20110101");
            dao.persist(rh1);

            RaHeader rh2 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh2);
            rh2.setFilename("alpha");
            rh2.setStatus("D");
            rh2.setPaymentDate("20080101");
            dao.persist(rh2);

            RaHeader rh3 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh3);
            rh3.setFilename("alpha");
            rh3.setStatus("A");
            rh3.setPaymentDate("20050101");
            dao.persist(rh3);

            RaHeader rh4 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh4);
            rh4.setFilename("alpha");
            rh4.setStatus("A");
            rh4.setPaymentDate("20110101");
            dao.persist(rh4);

            RaHeader rh5 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh5);
            rh5.setFilename("bravo");
            rh5.setStatus("A");
            rh5.setPaymentDate("20110101");
            dao.persist(rh5);

            List<RaHeader> result = dao.findCurrentByFilenamePaymentDate("alpha", "20110101");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(rh1);
            assertThat(result.get(1)).isEqualTo(rh4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find headers by filename and payment date")
        void shouldFindHeaders_byFilenameAndPaymentDate() {
            RaHeader rh1 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh1);
            rh1.setFilename("alpha");
            rh1.setPaymentDate("20110101");
            dao.persist(rh1);

            RaHeader rh2 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh2);
            rh2.setFilename("alpha");
            rh2.setPaymentDate("20080101");
            dao.persist(rh2);

            RaHeader rh3 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh3);
            rh3.setFilename("alpha");
            rh3.setPaymentDate("20050101");
            dao.persist(rh3);

            RaHeader rh4 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh4);
            rh4.setFilename("alpha");
            rh4.setPaymentDate("20110101");
            dao.persist(rh4);

            RaHeader rh5 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh5);
            rh5.setFilename("bravo");
            rh5.setPaymentDate("20110101");
            dao.persist(rh5);

            List<RaHeader> result = dao.findByFilenamePaymentDate("alpha", "20110101");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(rh1);
            assertThat(result.get(1)).isEqualTo(rh4);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all headers excluding a specific status")
        void shouldFindAllHeaders_excludingStatus() {
            RaHeader rh1 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh1);
            rh1.setStatus("A");
            rh1.setPaymentDate("20110510");
            rh1.setReadDate("20140510");
            dao.persist(rh1);

            RaHeader rh2 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh2);
            rh2.setStatus("B");
            rh2.setPaymentDate("20091004");
            rh2.setReadDate("20131004");
            dao.persist(rh2);

            RaHeader rh3 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh3);
            rh3.setStatus("A");
            rh3.setPaymentDate("20080729");
            rh3.setReadDate("20120729");
            dao.persist(rh3);

            RaHeader rh4 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh4);
            rh4.setStatus("B");
            rh4.setPaymentDate("20041108");
            rh4.setReadDate("20101108");
            dao.persist(rh4);

            RaHeader rh5 = new RaHeader();
            EntityDataGenerator.generateTestDataForModelClass(rh5);
            rh5.setStatus("A");
            rh5.setPaymentDate("20110510");
            rh5.setReadDate("20140510");
            dao.persist(rh5);

            List<RaHeader> result = dao.findAllExcludeStatus("A");

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEqualTo(rh2);
            assertThat(result.get(1)).isEqualTo(rh4);
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByHeaderDetailsAndProviderMagic")
        void shouldReturnResults_whenFindByHeaderDetailsAndProviderMagic() {
            List<RaHeader> result = dao.findByHeaderDetailsAndProviderMagic("STS", "100");
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return results for findByStatusAndProviderMagic")
        void shouldReturnResults_whenFindByStatusAndProviderMagic() {
            List<RaHeader> result = dao.findByStatusAndProviderMagic("STS", "100");
            assertThat(result).isEmpty();
        }
    }
}
