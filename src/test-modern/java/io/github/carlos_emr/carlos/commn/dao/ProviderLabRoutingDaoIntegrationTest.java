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

import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProviderLabRoutingDao} covering persist,
 * findByLabNoAndLabTypeAndProviderNo, findByLabNo, findByLabNoAndLabType,
 * findByStatusANDLabNoType, findByProviderNo, findByLabNoTypeAndStatus,
 * findByLabNoIncludingPotentialDuplicates, getProviderLabRoutingDocuments,
 * getProviderLabRoutingForLabAndType, findAllLabRoutingByIdandType, and updateStatus.
 *
 * <p>Migrated from legacy {@code ProviderLabRoutingDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderLabRoutingDao
 */
@DisplayName("ProviderLabRouting Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("provider")
@Transactional
public class ProviderLabRoutingDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderLabRoutingDao dao;

    private ProviderLabRoutingModel createRouting(String providerNo, int labNo, String labType, String status) {
        ProviderLabRoutingModel entity = new ProviderLabRoutingModel();
        entity.setProviderNo(providerNo);
        entity.setLabNo(labNo);
        entity.setLabType(labType);
        entity.setStatus(status);
        entity.setTimestamp(new Date());
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider lab routing with generated ID")
        void shouldPersistProviderLabRouting_whenValidDataProvided() {
            ProviderLabRoutingModel entity = createRouting("100001", 1, "HL7", "N");

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find provider lab routing by ID with correct values")
        void shouldFindProviderLabRouting_whenValidIdProvided() {
            ProviderLabRoutingModel saved = createRouting("100002", 2, "DOC", "A");

            ProviderLabRoutingModel found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getLabNo()).isEqualTo(2);
            assertThat(found.getLabType()).isEqualTo("DOC");
            assertThat(found.getStatus()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("findByLabNoAndLabTypeAndProviderNo")
    class FindByLabNoAndLabTypeAndProviderNo {

        @Test
        @Tag("query")
        @DisplayName("should return routings matching all three parameters")
        void shouldReturnRoutings_whenAllParametersMatch() {
            ProviderLabRoutingModel matching = createRouting("200001", 10, "HL7", "N");
            createRouting("200002", 10, "HL7", "N");
            createRouting("200001", 10, "DOC", "N");

            List<ProviderLabRoutingModel> results = dao.findByLabNoAndLabTypeAndProviderNo(10, "HL7", "200001");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no match")
        void shouldReturnEmptyList_whenNoMatch() {
            List<ProviderLabRoutingModel> results = dao.findByLabNoAndLabTypeAndProviderNo(99999, "HL7", "999999");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLabNo")
    class FindByLabNo {

        @Test
        @Tag("query")
        @DisplayName("should return single result for matching lab number")
        void shouldReturnSingleResult_whenLabNoMatches() {
            ProviderLabRoutingModel saved = createRouting("300001", 30, "HL7", "N");

            ProviderLabRoutingModel found = dao.findByLabNo(30);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when lab number not found")
        void shouldReturnNull_whenLabNoNotFound() {
            ProviderLabRoutingModel found = dao.findByLabNo(99999);

            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("findByLabNoAndLabType")
    class FindByLabNoAndLabType {

        @Test
        @Tag("query")
        @DisplayName("should return result for matching lab number and type")
        void shouldReturnResult_whenLabNoAndTypeMatch() {
            createRouting("400001", 40, "HL7", "N");
            createRouting("400002", 40, "DOC", "N");

            ProviderLabRoutingModel found = dao.findByLabNoAndLabType(40, "HL7");

            assertThat(found).isNotNull();
            assertThat(found.getLabType()).isEqualTo("HL7");
            assertThat(found.getLabNo()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("findByStatusANDLabNoType")
    class FindByStatusANDLabNoType {

        @Test
        @Tag("query")
        @DisplayName("should return routings matching lab number, type, and status")
        void shouldReturnRoutings_whenAllParametersMatch() {
            createRouting("500001", 50, "HL7", "N");
            createRouting("500002", 50, "HL7", "A");
            createRouting("500003", 50, "DOC", "N");

            List<ProviderLabRoutingModel> results = dao.findByStatusANDLabNoType(50, "HL7", "N");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProviderNo()).isEqualTo("500001");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when status does not match")
        void shouldReturnEmptyList_whenStatusDoesNotMatch() {
            createRouting("500001", 51, "HL7", "N");

            List<ProviderLabRoutingModel> results = dao.findByStatusANDLabNoType(51, "HL7", "X");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProviderNo")
    class FindByProviderNo {

        @Test
        @Tag("query")
        @DisplayName("should return routings for matching provider and status")
        void shouldReturnRoutings_whenProviderAndStatusMatch() {
            createRouting("600001", 60, "HL7", "N");
            createRouting("600001", 61, "DOC", "N");
            createRouting("600001", 62, "HL7", "A");
            createRouting("600002", 63, "HL7", "N");

            List<ProviderLabRoutingModel> results = dao.findByProviderNo("600001", "N");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getProviderNo().equals("600001") && r.getStatus().equals("N"));
        }
    }

    @Nested
    @DisplayName("findByLabNoTypeAndStatus")
    class FindByLabNoTypeAndStatus {

        @Test
        @Tag("query")
        @DisplayName("should return routings matching lab ID, type, and status")
        void shouldReturnRoutings_whenAllParametersMatch() {
            createRouting("700001", 70, "HL7", "N");
            createRouting("700002", 70, "HL7", "N");
            createRouting("700003", 70, "HL7", "A");

            List<ProviderLabRoutingModel> results = dao.findByLabNoTypeAndStatus(70, "HL7", "N");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getStatus().equals("N") && r.getLabType().equals("HL7"));
        }
    }

    @Nested
    @DisplayName("findByLabNoIncludingPotentialDuplicates")
    class FindByLabNoIncludingPotentialDuplicates {

        @Test
        @Tag("query")
        @DisplayName("should return all routings for lab number including duplicates")
        void shouldReturnAllRoutings_forLabNo() {
            createRouting("800001", 80, "HL7", "N");
            createRouting("800002", 80, "DOC", "A");
            createRouting("800003", 81, "HL7", "N");

            List<ProviderLabRoutingModel> results = dao.findByLabNoIncludingPotentialDuplicates(80);

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getLabNo().equals(80));
        }
    }

    @Nested
    @DisplayName("getProviderLabRoutingDocuments")
    class GetProviderLabRoutingDocuments {

        @Test
        @Tag("query")
        @DisplayName("should return DOC type routings for lab number")
        void shouldReturnDocRoutings_forLabNo() {
            createRouting("900001", 90, "DOC", "N");
            createRouting("900002", 90, "HL7", "N");

            List<ProviderLabRoutingModel> results = dao.getProviderLabRoutingDocuments(90);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getLabType()).isEqualTo("DOC");
        }
    }

    @Nested
    @DisplayName("getProviderLabRoutingForLabAndType")
    class GetProviderLabRoutingForLabAndType {

        @Test
        @Tag("query")
        @DisplayName("should return routings with status N for lab and type")
        void shouldReturnNewRoutings_forLabAndType() {
            createRouting("101001", 101, "HL7", "N");
            createRouting("101002", 101, "HL7", "A");

            List<ProviderLabRoutingModel> results = dao.getProviderLabRoutingForLabAndType(101, "HL7");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo("N");
        }
    }

    @Nested
    @DisplayName("findAllLabRoutingByIdandType")
    class FindAllLabRoutingByIdandType {

        @Test
        @Tag("query")
        @DisplayName("should return all routings matching lab ID and type regardless of status")
        void shouldReturnAllRoutings_forLabIdAndType() {
            createRouting("102001", 102, "HL7", "N");
            createRouting("102002", 102, "HL7", "A");
            createRouting("102003", 102, "DOC", "N");

            List<ProviderLabRoutingModel> results = dao.findAllLabRoutingByIdandType(102, "HL7");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getLabType().equals("HL7"));
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @Tag("update")
        @DisplayName("should set status to N for matching lab number and type")
        void shouldSetStatusToNew_whenLabNoAndTypeMatch() {
            createRouting("103001", 103, "HL7", "A");
            createRouting("103002", 103, "HL7", "X");

            dao.updateStatus(103, "HL7");

            List<ProviderLabRoutingModel> results = dao.findAllLabRoutingByIdandType(103, "HL7");
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getStatus().equals("N"));
        }
    }
}
