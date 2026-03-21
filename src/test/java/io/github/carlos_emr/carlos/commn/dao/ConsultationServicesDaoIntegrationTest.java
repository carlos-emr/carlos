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
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConsultationServiceDao} covering consultation
 * service types CRUD and active service lookups.
 *
 * <p>Migrated from legacy {@code ConsultationServicesDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ConsultationServiceDao
 */
@DisplayName("ConsultationServicesDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultationServicesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultationServiceDao consultationServiceDao;

    private ConsultationServices createService(String serviceDesc, boolean active) {
        ConsultationServices svc = new ConsultationServices();
        svc.setServiceDesc(serviceDesc);
        svc.setActive(active ? "1" : "0");
        consultationServiceDao.persist(svc);
        return svc;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consultation service with generated ID")
        void shouldPersistService_whenValidDataProvided() {
            ConsultationServices svc = createService("Cardiology Referral", true);
            assertThat(svc.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find consultation service by ID")
        void shouldFindService_whenValidIdProvided() {
            ConsultationServices saved = createService("Neurology Referral", true);
            ConsultationServices found = consultationServiceDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getServiceDesc()).isEqualTo("Neurology Referral");
        }

        @Test
        @Tag("delete")
        @DisplayName("should remove consultation service")
        void shouldRemoveService_whenValidIdProvided() {
            ConsultationServices saved = createService("To Delete", true);
            Integer id = saved.getId();
            consultationServiceDao.remove(id);
            hibernateTemplate.flush();
            ConsultationServices found = consultationServiceDao.find(id);
            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find all consultation services")
        void shouldFindAllServices() {
            createService("Service A", true);
            createService("Service B", false);
            List<ConsultationServices> all = consultationServiceDao.findAll(0, 100);
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should count all consultation services")
        void shouldCountAllServices() {
            createService("Count Service", true);
            long count = consultationServiceDao.getCountAll();
            assertThat(count).isEqualTo(1);
        }
    }
}
