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
import io.github.carlos_emr.carlos.commn.model.ClientLink;
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
 * Integration tests for {@link ClientLinkDao}.
 *
 * <p>Migrated from legacy {@code ClientLinkDaoTest} (JUnit 4 / DaoTestFixtures).
 * Note: All legacy tests were commented out due to foreign key constraint errors
 * in the original test environment. The tests below replicate the commented-out
 * legacy test logic for findByFacilityIdClientIdType with various parameter
 * combinations.</p>
 *
 * @since 2026-03-07
 * @see ClientLinkDao
 */
@DisplayName("ClientLinkDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class ClientLinkDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ClientLinkDao dao;

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("query")
        @DisplayName("should find client links with null currentlyLinked and matching type")
        void shouldFindClientLinks_whenCurrentlyLinkedNullAndTypeProvided() {
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl1);

            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl2);

            // Wrong facility ID - should not be returned
            ClientLink cl3 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl3);
            cl3.setFacilityId(9999);
            cl3.setClientId(10);
            cl3.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl3);

            // Wrong client ID - should not be returned
            ClientLink cl4 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl4);
            cl4.setFacilityId(3);
            cl4.setClientId(9999);
            cl4.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl4);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, null, ClientLink.Type.OSCAR_CAISI);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder(cl1, cl2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find only currently linked client links when currentlyLinked is true")
        void shouldFindClientLinks_whenCurrentlyLinkedTrue() {
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.OSCAR_CAISI);
            cl1.setUnlinkProviderNo(null);
            dao.persist(cl1);

            // Has unlinkProviderNo set - should not be returned for currentlyLinked=true
            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            cl2.setUnlinkProviderNo("9999999");
            dao.persist(cl2);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, true, ClientLink.Type.OSCAR_CAISI);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(cl1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find only unlinked client links when currentlyLinked is false")
        void shouldFindClientLinks_whenCurrentlyLinkedFalse() {
            // No unlinkProviderNo - should not be returned for currentlyLinked=false
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.OSCAR_CAISI);
            cl1.setUnlinkProviderNo(null);
            dao.persist(cl1);

            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            cl2.setUnlinkProviderNo("9999999");
            dao.persist(cl2);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, false, ClientLink.Type.OSCAR_CAISI);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(cl2);
        }

        @Test
        @Tag("query")
        @DisplayName("should filter by type when type is not null")
        void shouldFilterByType_whenTypeNotNull() {
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.HNR);
            dao.persist(cl1);

            // Wrong type - should not be returned
            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl2);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, null, ClientLink.Type.HNR);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(cl1);
        }

        @Test
        @Tag("query")
        @DisplayName("should filter by type and currentlyLinked true")
        void shouldFilterByTypeAndCurrentlyLinkedTrue() {
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.HNR);
            cl1.setUnlinkProviderNo(null);
            dao.persist(cl1);

            // Wrong type
            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            dao.persist(cl2);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, true, ClientLink.Type.HNR);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(cl1);
        }

        @Test
        @Tag("query")
        @DisplayName("should filter by type and currentlyLinked false")
        void shouldFilterByTypeAndCurrentlyLinkedFalse() {
            ClientLink cl1 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl1);
            cl1.setFacilityId(3);
            cl1.setClientId(10);
            cl1.setLinkType(ClientLink.Type.HNR);
            cl1.setUnlinkProviderNo("9999999");
            dao.persist(cl1);

            // Wrong type
            ClientLink cl2 = new ClientLink();
            EntityDataGenerator.generateTestDataForModelClass(cl2);
            cl2.setFacilityId(3);
            cl2.setClientId(10);
            cl2.setLinkType(ClientLink.Type.OSCAR_CAISI);
            cl2.setUnlinkProviderNo("9999999");
            dao.persist(cl2);

            List<ClientLink> result = dao.findByFacilityIdClientIdType(3, 10, false, ClientLink.Type.HNR);

            assertThat(result).hasSize(1);
            assertThat(result).containsExactly(cl1);
        }
    }
}
