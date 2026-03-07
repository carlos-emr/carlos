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
import io.github.carlos_emr.carlos.commn.model.UserDSMessagePrefs;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserDSMessagePrefsDao}.
 *
 * <p>Migrated from legacy {@code UserDSMessagePrefsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see UserDSMessagePrefsDao
 */
@DisplayName("UserDSMessagePrefs Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("admin")
@Transactional
public class UserDSMessagePrefsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private UserDSMessagePrefsDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist user DS message prefs with generated ID")
        void shouldPersistUserDSMessagePrefs_whenValidDataProvided() throws Exception {
            UserDSMessagePrefs entity = new UserDSMessagePrefs();
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
        @DisplayName("should get message prefs on type by provider and resource type")
        void shouldGetMessagePrefsOnType_byProviderAndResourceType() throws Exception {
            String providerNo1 = "101";
            String providerNo2 = "202";
            String resourceType1 = "alpha";
            String resourceType2 = "bravo";
            boolean isArchived = true;

            UserDSMessagePrefs userDSMessagePrefs1 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs1);
            userDSMessagePrefs1.setProviderNo(providerNo1);
            userDSMessagePrefs1.setResourceType(resourceType1);
            userDSMessagePrefs1.setArchived(isArchived);
            dao.persist(userDSMessagePrefs1);

            UserDSMessagePrefs userDSMessagePrefs2 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs2);
            userDSMessagePrefs2.setProviderNo(providerNo2);
            userDSMessagePrefs2.setResourceType(resourceType2);
            userDSMessagePrefs2.setArchived(isArchived);
            dao.persist(userDSMessagePrefs2);

            UserDSMessagePrefs userDSMessagePrefs3 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs3);
            userDSMessagePrefs3.setProviderNo(providerNo1);
            userDSMessagePrefs3.setResourceType(resourceType1);
            userDSMessagePrefs3.setArchived(!isArchived);
            dao.persist(userDSMessagePrefs3);

            UserDSMessagePrefs result = dao.getMessagePrefsOnType(providerNo1, resourceType1);

            assertThat(result).isEqualTo(userDSMessagePrefs1);
        }

        @Test
        @Tag("query")
        @DisplayName("should get hash of messages by provider and resource type")
        void shouldGetHashOfMessages_byProviderAndResourceType() throws Exception {
            String providerNo1 = "101";
            String providerNo2 = "202";
            String resourceType1 = "alpha";
            String resourceType2 = "bravo";
            boolean isArchived = true;
            Date resourceUpdatedDate1 = new Date(dfm.parse("20111221").getTime());
            Date resourceUpdatedDate2 = new Date(dfm.parse("20101011").getTime());
            Date resourceUpdatedDate3 = new Date(dfm.parse("20091123").getTime());
            String resourceId1 = "111";
            String resourceId2 = "222";

            UserDSMessagePrefs userDSMessagePrefs1 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs1);
            userDSMessagePrefs1.setResourceId(resourceId1);
            userDSMessagePrefs1.setProviderNo(providerNo1);
            userDSMessagePrefs1.setResourceType(resourceType1);
            userDSMessagePrefs1.setArchived(isArchived);
            userDSMessagePrefs1.setResourceUpdatedDate(resourceUpdatedDate1);
            dao.persist(userDSMessagePrefs1);

            UserDSMessagePrefs userDSMessagePrefs2 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs2);
            userDSMessagePrefs2.setResourceId(resourceId2);
            userDSMessagePrefs2.setProviderNo(providerNo2);
            userDSMessagePrefs2.setResourceType(resourceType2);
            userDSMessagePrefs2.setArchived(isArchived);
            userDSMessagePrefs2.setResourceUpdatedDate(resourceUpdatedDate2);
            dao.persist(userDSMessagePrefs2);

            UserDSMessagePrefs userDSMessagePrefs3 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs3);
            userDSMessagePrefs3.setResourceId(resourceId1);
            userDSMessagePrefs3.setProviderNo(providerNo1);
            userDSMessagePrefs3.setResourceType(resourceType1);
            userDSMessagePrefs3.setArchived(!isArchived);
            userDSMessagePrefs3.setResourceUpdatedDate(resourceUpdatedDate3);
            dao.persist(userDSMessagePrefs3);

            long longDate = resourceUpdatedDate1.getTime();

            Hashtable<String, Long> expectedResult = new Hashtable<>();
            expectedResult.put(resourceType1 + resourceId1, longDate);
            Hashtable<String, Long> result = dao.getHashofMessages(providerNo1, resourceType1);

            assertThat(result).isEqualTo(expectedResult);
        }

        @Test
        @Tag("query")
        @DisplayName("should get DS message by provider, resource type, resource ID and archived flag")
        void shouldGetDsMessage_byProviderResourceTypeResourceIdAndArchived() throws Exception {
            String providerNo1 = "101";
            String providerNo2 = "202";
            String resourceType1 = "alpha";
            String resourceType2 = "bravo";
            boolean isArchived = true;
            String resourceId1 = "111";
            String resourceId2 = "222";

            UserDSMessagePrefs userDSMessagePrefs1 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs1);
            userDSMessagePrefs1.setProviderNo(providerNo1);
            userDSMessagePrefs1.setResourceType(resourceType1);
            userDSMessagePrefs1.setArchived(isArchived);
            userDSMessagePrefs1.setResourceId(resourceId1);
            dao.persist(userDSMessagePrefs1);

            UserDSMessagePrefs userDSMessagePrefs2 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs2);
            userDSMessagePrefs2.setProviderNo(providerNo2);
            userDSMessagePrefs2.setResourceType(resourceType2);
            userDSMessagePrefs2.setArchived(isArchived);
            userDSMessagePrefs2.setResourceId(resourceId2);
            dao.persist(userDSMessagePrefs2);

            UserDSMessagePrefs userDSMessagePrefs3 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs3);
            userDSMessagePrefs3.setProviderNo(providerNo1);
            userDSMessagePrefs3.setResourceType(resourceType1);
            userDSMessagePrefs3.setArchived(!isArchived);
            userDSMessagePrefs3.setResourceId(resourceId1);
            dao.persist(userDSMessagePrefs3);

            UserDSMessagePrefs userDSMessagePrefs4 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs4);
            userDSMessagePrefs4.setProviderNo(providerNo2);
            userDSMessagePrefs4.setResourceType(resourceType1);
            userDSMessagePrefs4.setArchived(isArchived);
            userDSMessagePrefs4.setResourceId(resourceId1);
            dao.persist(userDSMessagePrefs4);

            UserDSMessagePrefs userDSMessagePrefs5 = new UserDSMessagePrefs();
            EntityDataGenerator.generateTestDataForModelClass(userDSMessagePrefs5);
            userDSMessagePrefs5.setProviderNo(providerNo1);
            userDSMessagePrefs5.setResourceType(resourceType1);
            userDSMessagePrefs5.setArchived(isArchived);
            userDSMessagePrefs5.setResourceId(resourceId2);
            dao.persist(userDSMessagePrefs5);

            UserDSMessagePrefs result = dao.getDsMessage(providerNo1, resourceType1, resourceId1, isArchived);

            assertThat(result).isEqualTo(userDSMessagePrefs1);
        }
    }
}
