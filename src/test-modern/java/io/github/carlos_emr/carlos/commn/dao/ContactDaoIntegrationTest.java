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
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ContactDao} covering search with
 * search_name mode (last name + first name, last name only) and
 * non-search_name mode (province search).
 *
 * <p>Migrated from legacy {@code ContactDaoTest}
 * (JUnit 4 / DaoTestFixtures) with exact same test logic and assertions.</p>
 *
 * @since 2026-03-07
 * @see ContactDao
 */
@DisplayName("ContactDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
public class ContactDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ContactDao dao;

    @Nested
    @DisplayName("search with search_name mode - LastName, FirstName")
    @Tag("search")
    class SearchByLastNameFirstName {

        /**
         * Ensures that search() returns accurate list of contacts where
         * the keyword is comma separated (lastname, firstname), and searchMode is search_name.
         */
        @Test
        @DisplayName("should return contacts matching last name and first name")
        void shouldReturnContacts_whenLastNameAndFirstNameMatch() throws Exception {
            String keyword = "Smith, Jon";
            String orderBy = "c.id";
            String searchMode = "search_name";

            Contact contact1 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact1);
            contact1.setLastName("Smith");
            contact1.setFirstName("Jon");

            Contact contact2 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact2);
            contact2.setLastName("Smith");
            contact2.setFirstName("Jon");

            Contact contact3 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact3);
            contact3.setLastName("Smith");
            contact3.setFirstName("Jim");

            dao.persist(contact1);
            dao.persist(contact2);
            dao.persist(contact3);
            hibernateTemplate.flush();

            List<Contact> result = dao.search(searchMode, orderBy, keyword);
            List<Contact> expectedResult = Arrays.asList(contact1, contact2);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }

    @Nested
    @DisplayName("search with search_name mode - LastName only")
    @Tag("search")
    class SearchByLastName {

        /**
         * Ensures that search() returns accurate list of contacts where
         * the keyword is lastname only, and searchMode is search_name.
         */
        @Test
        @DisplayName("should return contacts matching last name only")
        void shouldReturnContacts_whenLastNameMatches() throws Exception {
            String keyword = "Smith";
            String orderBy = "c.id";
            String searchMode = "search_name";

            Contact contact1 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact1);
            contact1.setLastName("Smith");
            dao.persist(contact1);

            Contact contact2 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact2);
            contact2.setLastName("Jackson");
            dao.persist(contact2);

            Contact contact3 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact3);
            contact3.setLastName("Smith");
            dao.persist(contact3);
            hibernateTemplate.flush();

            List<Contact> result = dao.search(searchMode, orderBy, keyword);
            List<Contact> expectedResult = Arrays.asList(contact1, contact3);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }

    @Nested
    @DisplayName("search with non-search_name mode")
    @Tag("search")
    class SearchByProvince {

        /**
         * Ensures that search() returns accurate list of contacts where
         * the searchMode is anything other than search_name.
         */
        @Test
        @DisplayName("should return contacts matching province when search mode is province")
        void shouldReturnContacts_whenProvinceMatches() throws Exception {
            String keyword = "ON";
            String orderBy = "c.id";
            String searchMode = "province";

            Contact contact1 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact1);
            contact1.setProvince("BC");

            Contact contact2 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact2);
            contact2.setProvince("ON");

            Contact contact3 = new Contact();
            EntityDataGenerator.generateTestDataForModelClass(contact3);
            contact3.setProvince("ON");

            dao.persist(contact1);
            dao.persist(contact2);
            dao.persist(contact3);
            hibernateTemplate.flush();

            List<Contact> result = dao.search(searchMode, orderBy, keyword);
            List<Contact> expectedResult = Arrays.asList(contact2, contact3);

            assertThat(result).hasSameSizeAs(expectedResult);
            for (int i = 0; i < expectedResult.size(); i++) {
                assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
            }
        }
    }
}
