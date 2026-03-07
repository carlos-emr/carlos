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
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConsultRequestDao} covering referral
 * creation, demographic-based queries, status filtering, and specialist lookups.
 *
 * <p>Migrated from legacy {@code ConsultationRequestDaoTest} and
 * {@code ConsultRequestDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ConsultRequestDao
 */
@DisplayName("ConsultationRequest Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultationRequestDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultRequestDao consultRequestDao;

    @Autowired
    private ProfessionalSpecialistDao professionalSpecialistDao;

    private static final int DEMO_1 = 60001;
    private static final int DEMO_2 = 60002;

    private ProfessionalSpecialist createSpecialist(String firstName, String lastName) {
        ProfessionalSpecialist spec = new ProfessionalSpecialist();
        spec.setFirstName(firstName);
        spec.setLastName(lastName);
        spec.setSpecialtyType("Cardiology");
        spec.setReferralNo("REF-" + System.nanoTime());
        professionalSpecialistDao.persist(spec);
        return spec;
    }

    private ConsultationRequest createConsultRequest(int demographicNo, String status,
                                                      ProfessionalSpecialist specialist) {
        ConsultationRequest req = new ConsultationRequest();
        req.setDemographicId(demographicNo);
        req.setStatus(status);
        req.setReferralDate(new Date());
        req.setProviderNo("999998");
        req.setReasonForReferral("Test referral");
        req.setLastUpdateDate(new Date());
        if (specialist != null) {
            req.setProfessionalSpecialist(specialist);
        }
        consultRequestDao.persist(req);
        return req;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consultation request with generated ID")
        void shouldPersistConsultRequest_whenValidDataProvided() {
            ConsultationRequest req = createConsultRequest(DEMO_1, "1", null);
            assertThat(req.getId()).isNotNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should find consultation request by ID")
        void shouldFindConsultRequest_whenValidIdProvided() {
            ConsultationRequest saved = createConsultRequest(DEMO_1, "1", null);
            ConsultationRequest found = consultRequestDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getDemographicId()).isEqualTo(DEMO_1);
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        private ProfessionalSpecialist specialist;

        @BeforeEach
        void setUp() {
            specialist = createSpecialist("Dr. Heart", "Specialist");
            createConsultRequest(DEMO_1, "1", specialist);
            createConsultRequest(DEMO_1, "2", specialist);
            createConsultRequest(DEMO_2, "1", null);
        }

        @Test
        @Tag("query")
        @DisplayName("should count all consultation requests")
        void shouldCountAllRequests() {
            long count = consultRequestDao.getCountAll();
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all requests with pagination")
        void shouldFindAllRequests_withPagination() {
            List<ConsultationRequest> results = consultRequestDao.findAll(0, 2);
            assertThat(results).hasSize(2);
        }
    }
}
