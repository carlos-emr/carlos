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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DemographicContactDao} covering persist,
 * findByDemographicNo, findByDemographicNoAndCategory, and find(demographicNo, contactId).
 *
 * <p>Migrated from legacy {@code DemographicContactDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see DemographicContactDao
 */
@DisplayName("DemographicContactDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class DemographicContactDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DemographicContactDao dao;

    private DemographicContact createContact(int demoNo, boolean deleted, String category, String contactId) {
        DemographicContact contact = new DemographicContact();
        EntityDataGenerator.generateTestDataForModelClass(contact);
        contact.setDemographicNo(demoNo);
        contact.setDeleted(deleted);
        contact.setCategory(category);
        contact.setContactId(contactId);
        dao.persist(contact);
        hibernateTemplate.flush();
        return contact;
    }

    @Test
    @Tag("create")
    @DisplayName("should persist contact with generated ID")
    void shouldPersistContact_whenValidDataProvided() {
        DemographicContact entity = new DemographicContact();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        hibernateTemplate.flush();

        assertThat(entity.getId()).isNotNull();
    }

    @Nested
    @DisplayName("findByDemographicNo")
    class FindByDemographicNo {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted contacts for demographic number")
        void shouldReturnNonDeletedContacts_forDemographicNo() {
            int demoNo = 10;

            DemographicContact contact1 = createContact(demoNo, false, "CAT1", "100");
            // deleted - should not be returned
            createContact(demoNo, true, "CAT1", "100");
            DemographicContact contact3 = createContact(demoNo, false, "CAT2", "101");
            // different demographic - should not be returned
            createContact(5, false, "CAT1", "100");

            List<DemographicContact> result = dao.findByDemographicNo(demoNo);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DemographicContact::getId)
                    .containsExactlyInAnyOrder(contact1.getId(), contact3.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when all contacts are deleted")
        void shouldReturnEmptyList_whenAllContactsAreDeleted() {
            createContact(20, true, "CAT1", "100");

            List<DemographicContact> result = dao.findByDemographicNo(20);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByDemographicNoAndCategory")
    class FindByDemographicNoAndCategory {

        @Test
        @Tag("query")
        @DisplayName("should return contacts matching demographic and category")
        void shouldReturnContacts_forMatchingDemographicAndCategory() {
            int demoNo = 10;
            String category = "CAT1";

            DemographicContact contact1 = createContact(demoNo, false, category, "100");
            // deleted - should not be returned
            createContact(demoNo, true, category, "101");
            // different category - should not be returned
            createContact(demoNo, false, "CAT2", "102");
            // different demographic - should not be returned
            createContact(5, false, category, "103");
            DemographicContact contact5 = createContact(demoNo, false, category, "104");

            List<DemographicContact> result = dao.findByDemographicNoAndCategory(demoNo, category);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DemographicContact::getId)
                    .containsExactlyInAnyOrder(contact1.getId(), contact5.getId());
        }
    }

    @Nested
    @DisplayName("find(demographicNo, contactId)")
    class FindByDemographicNoAndContactId {

        @Test
        @Tag("query")
        @DisplayName("should return non-deleted contacts matching demographic and contact ID")
        void shouldReturnContacts_forMatchingDemographicAndContactId() {
            int demoNo = 10;
            String contactId = "101";

            DemographicContact contact1 = createContact(demoNo, false, "CAT1", contactId);
            // deleted - should not be returned
            createContact(demoNo, true, "CAT1", contactId);
            // different category but same contactId - should still be returned (find filters by demoNo+contactId)
            DemographicContact contact3 = createContact(demoNo, false, "CAT2", contactId);
            // different demographic - should not be returned
            createContact(5, false, "CAT1", contactId);
            // different contactId - should not be returned
            createContact(demoNo, false, "CAT1", "102");
            DemographicContact contact6 = createContact(demoNo, false, "CAT1", contactId);

            List<DemographicContact> result = dao.find(demoNo, 101);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(DemographicContact::getId)
                    .containsExactlyInAnyOrder(contact1.getId(), contact3.getId(), contact6.getId());
        }
    }
}
