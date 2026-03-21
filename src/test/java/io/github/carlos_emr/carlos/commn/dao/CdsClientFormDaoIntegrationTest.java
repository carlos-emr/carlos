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
import io.github.carlos_emr.carlos.commn.model.CdsClientForm;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CdsClientFormDao} covering findLatestByFacilityClient,
 * findByFacilityClient, and findSignedCdsForms (legacy: findLatestSignedCdsForms).
 *
 * <p>Migrated from legacy {@code CdsClientFormDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see CdsClientFormDao
 */
@DisplayName("CdsClientFormDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
@Transactional
public class CdsClientFormDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CdsClientFormDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Nested
    @DisplayName("findLatestByFacilityClient tests")
    @Tag("read")
    class FindLatestByFacilityClient {

        /**
         * Ensures that the latest client form is returned.
         */
        @Test
        @DisplayName("should return first persisted form as latest for facility and client")
        void shouldReturnLatestForm_forFacilityAndClient() throws Exception {
            int facilityId = 101;
            int clientId = 109;

            CdsClientForm clientForm1 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm1);
            clientForm1.setClientId(clientId);
            clientForm1.setFacilityId(facilityId);

            CdsClientForm clientForm2 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm2);
            clientForm2.setClientId(clientId);
            clientForm2.setFacilityId(facilityId);

            dao.persist(clientForm1);
            dao.persist(clientForm2);
            hibernateTemplate.flush();

            CdsClientForm result = dao.findLatestByFacilityClient(facilityId, clientId);
            assertThat(result).isEqualTo(clientForm1);
        }
    }

    @Nested
    @DisplayName("findByFacilityClient tests")
    @Tag("read")
    class FindByFacilityClient {

        @Test
        @DisplayName("should return all forms for matching facility and client")
        void shouldReturnAllForms_forMatchingFacilityAndClient() throws Exception {
            int facilityId = 101;
            int clientId = 109;

            CdsClientForm clientForm1 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm1);
            clientForm1.setClientId(clientId);
            clientForm1.setFacilityId(facilityId);

            CdsClientForm clientForm2 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm2);
            clientForm2.setClientId(clientId);
            clientForm2.setFacilityId(facilityId);

            dao.persist(clientForm1);
            dao.persist(clientForm2);
            hibernateTemplate.flush();

            List<CdsClientForm> result = dao.findByFacilityClient(facilityId, clientId);
            List<CdsClientForm> expectedResult = Arrays.asList(clientForm1, clientForm2);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }

    @Nested
    @DisplayName("findSignedCdsForms tests")
    @Tag("read")
    class FindLatestSignedCdsForms {

        @Test
        @DisplayName("should return only signed forms within date range")
        void shouldReturnOnlySignedForms_withinDateRange() throws Exception {
            int facilityId = 101;
            String formVersion = "1.1.0";
            Date startDate = new Date(dfm.parse("20090101").getTime());
            Date endDate = new Date(dfm.parse("21001231").getTime());

            CdsClientForm clientForm1 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm1);
            clientForm1.setFacilityId(facilityId);
            clientForm1.setCdsFormVersion(formVersion);
            clientForm1.setSigned(true);

            CdsClientForm clientForm2 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm2);
            clientForm2.setFacilityId(facilityId);
            clientForm2.setCdsFormVersion(formVersion);
            clientForm2.setSigned(false);

            CdsClientForm clientForm3 = new CdsClientForm();
            EntityDataGenerator.generateTestDataForModelClass(clientForm3);
            clientForm3.setFacilityId(facilityId);
            clientForm3.setCdsFormVersion(formVersion);
            clientForm3.setSigned(true);

            dao.persist(clientForm1);
            dao.persist(clientForm2);
            dao.persist(clientForm3);
            hibernateTemplate.flush();

            List<CdsClientForm> result = dao.findSignedCdsForms(facilityId, formVersion, startDate, endDate);
            List<CdsClientForm> expectedResult = Arrays.asList(clientForm1, clientForm3);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
