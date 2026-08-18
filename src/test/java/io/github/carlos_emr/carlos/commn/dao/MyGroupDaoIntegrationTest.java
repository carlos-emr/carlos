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

import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MyGroupDao} covering create, findAll, getGroupDoctors,
 * getGroups, getGroupByGroupNo, getProviderGroups, getDefaultBillingForm,
 * search_mygroup, searchmygroupno, search_providersgroup, and deleteGroupMember.
 *
 * <p>Migrated from legacy {@code MyGroupDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see MyGroupDao
 */
@DisplayName("MyGroup Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class MyGroupDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private MyGroupDao dao;

    private MyGroup createMyGroup(String groupNo, String providerNo, String lastName, String firstName) {
        MyGroup entity = new MyGroup();
        entity.setId(new MyGroupPrimaryKey(groupNo, providerNo));
        entity.setLastName(lastName);
        entity.setFirstName(firstName);
        entity.setDefaultBillingForm("ONT");
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist my group with composite primary key")
        void shouldPersistMyGroup_whenCompositePrimaryKeyProvided() {
            MyGroup entity = createMyGroup("grpA", "999998", "Smith", "John");

            assertThat(entity.getId()).isNotNull();
            assertThat(entity.getId().getMyGroupNo()).isEqualTo("grpA");
            assertThat(entity.getId().getProviderNo()).isEqualTo("999998");
        }

        @Test
        @Tag("delete")
        @DisplayName("should delete group member by group no and provider no")
        void shouldDeleteGroupMember_whenGroupNoAndProviderNoMatch() {
            createMyGroup("grpD", "111111", "Delete", "Me");
            createMyGroup("grpD", "222222", "Keep", "Me");

            dao.deleteGroupMember("grpD", "111111");

            List<MyGroup> remaining = dao.getGroupByGroupNo("grpD");
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getId().getProviderNo()).isEqualTo("222222");
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all persisted groups")
        void shouldReturnAllGroups_whenGroupsExist() {
            createMyGroup("grpX", "100001", "Alpha", "One");
            createMyGroup("grpY", "100002", "Beta", "Two");

            List<MyGroup> all = dao.findAll();

            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getGroupDoctors")
    class GetGroupDoctors {

        @Test
        @Tag("query")
        @DisplayName("should return provider numbers for matching group")
        void shouldReturnProviderNumbers_whenGroupExists() {
            createMyGroup("grpB", "200001", "Doc", "One");
            createMyGroup("grpB", "200002", "Doc", "Two");
            createMyGroup("grpC", "200003", "Other", "Doc");

            List<String> doctors = dao.getGroupDoctors("grpB");

            assertThat(doctors).hasSize(2);
            assertThat(doctors).containsExactlyInAnyOrder("200001", "200002");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when group has no doctors")
        void shouldReturnNull_whenGroupHasNoDoctors() {
            List<String> doctors = dao.getGroupDoctors("nonExistentGroup");

            assertThat(doctors).isNull();
        }
    }

    @Nested
    @DisplayName("getGroups")
    class GetGroups {

        @Test
        @Tag("query")
        @DisplayName("should return distinct group numbers")
        void shouldReturnDistinctGroupNumbers() {
            createMyGroup("grpE", "300001", "A", "A");
            createMyGroup("grpE", "300002", "B", "B");
            createMyGroup("grpF", "300003", "C", "C");

            List<String> groups = dao.getGroups();

            assertThat(groups).contains("grpE", "grpF");
        }
    }

    @Nested
    @DisplayName("getGroupByGroupNo")
    class GetGroupByGroupNo {

        @Test
        @Tag("query")
        @DisplayName("should return all members of matching group")
        void shouldReturnAllMembers_whenGroupNoMatches() {
            createMyGroup("grpG", "400001", "Doc", "One");
            createMyGroup("grpG", "400002", "Doc", "Two");
            createMyGroup("grpH", "400003", "Other", "Doc");

            List<MyGroup> members = dao.getGroupByGroupNo("grpG");

            assertThat(members).hasSize(2);
            assertThat(members).allMatch(m -> m.getId().getMyGroupNo().equals("grpG"));
        }
    }

    @Nested
    @DisplayName("getProviderGroups")
    class GetProviderGroups {

        @Test
        @Tag("query")
        @DisplayName("should return groups for matching provider")
        void shouldReturnGroups_whenProviderBelongsToGroups() {
            createMyGroup("grpI", "500001", "Doc", "One");
            createMyGroup("grpJ", "500001", "Doc", "One");
            createMyGroup("grpK", "500002", "Other", "Doc");

            List<MyGroup> groups = dao.getProviderGroups("500001");

            assertThat(groups).hasSize(2);
            assertThat(groups).allMatch(g -> g.getId().getProviderNo().equals("500001"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when provider has no groups")
        void shouldReturnEmptyList_whenProviderHasNoGroups() {
            List<MyGroup> groups = dao.getProviderGroups("999999");

            assertThat(groups).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDefaultBillingForm")
    class GetDefaultBillingForm {

        @Test
        @Tag("query")
        @DisplayName("should return billing form for matching group")
        void shouldReturnBillingForm_whenGroupExists() {
            createMyGroup("grpL", "600001", "Doc", "One");

            String billingForm = dao.getDefaultBillingForm("grpL");

            assertThat(billingForm).isEqualTo("ONT");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty string when group does not exist")
        void shouldReturnEmptyString_whenGroupDoesNotExist() {
            String billingForm = dao.getDefaultBillingForm("nonExistent");

            assertThat(billingForm).isEmpty();
        }
    }

    @Nested
    @DisplayName("search_mygroup")
    class SearchMyGroup {

        @Test
        @Tag("query")
        @DisplayName("should return groups matching group number pattern")
        void shouldReturnGroups_whenGroupNoMatchesPattern() {
            createMyGroup("srchA", "700001", "Doc", "One");
            createMyGroup("srchB", "700002", "Doc", "Two");
            createMyGroup("other", "700003", "Doc", "Three");

            List<MyGroup> results = dao.search_mygroup("srch%");

            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("searchmygroupno")
    class SearchMyGroupNo {

        @Test
        @Tag("query")
        @DisplayName("should return grouped results ordered by group number")
        void shouldReturnGroupedResults_orderedByGroupNo() {
            createMyGroup("zGrp", "800001", "Doc", "One");
            createMyGroup("aGrp", "800002", "Doc", "Two");

            List<MyGroup> results = dao.searchmygroupno();

            assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("search_providersgroup")
    class SearchProvidersGroup {

        @Test
        @Tag("query")
        @DisplayName("should return groups matching last name and first name patterns")
        void shouldReturnGroups_whenNamePatternsMatch() {
            createMyGroup("grpM", "900001", "Johnson", "Alice");
            createMyGroup("grpN", "900002", "Johnson", "Bob");
            createMyGroup("grpO", "900003", "Williams", "Alice");

            List<MyGroup> results = dao.search_providersgroup("Johnson%", "%");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(g -> g.getLastName().startsWith("Johnson"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no names match")
        void shouldReturnEmptyList_whenNoNamesMatch() {
            List<MyGroup> results = dao.search_providersgroup("NoSuchName%", "%");

            assertThat(results).isEmpty();
        }
    }
}
