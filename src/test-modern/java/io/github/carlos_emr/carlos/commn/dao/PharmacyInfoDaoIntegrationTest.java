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
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PharmacyInfoDao} covering pharmacy
 * record CRUD, add/update/delete operations, and search queries.
 *
 * <p>Migrated from legacy {@code PharmacyInfoDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see PharmacyInfoDao
 */
@DisplayName("PharmacyInfoDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pharmacy")
@Transactional
public class PharmacyInfoDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private PharmacyInfoDao pharmacyInfoDao;

    private PharmacyInfo createPharmacy(String name, String city, Character status) {
        PharmacyInfo info = new PharmacyInfo();
        info.setName(name);
        info.setAddress("123 Main St");
        info.setCity(city);
        info.setProvince("ON");
        info.setPostalCode("K1A0B1");
        info.setPhone1("613-555-0100");
        info.setPhone2("613-555-0101");
        info.setFax("613-555-0102");
        info.setEmail("test@pharmacy.ca");
        info.setServiceLocationIdentifier("SLI001");
        info.setNotes("Test pharmacy");
        info.setStatus(status);
        pharmacyInfoDao.persist(info);
        return info;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist pharmacy with generated ID")
        void shouldPersistPharmacy_whenValidDataProvided() {
            PharmacyInfo info = createPharmacy("Test Pharmacy", "Ottawa", '1');
            assertThat(info.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find pharmacy by ID")
        void shouldFindPharmacy_whenValidIdProvided() {
            PharmacyInfo saved = createPharmacy("Pharma Plus", "Toronto", '1');
            PharmacyInfo found = pharmacyInfoDao.getPharmacy(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Pharma Plus");
            assertThat(found.getCity()).isEqualTo("Toronto");
        }

        @Test
        @Tag("read")
        @DisplayName("should find pharmacy by record ID")
        void shouldFindPharmacy_byRecordId() {
            PharmacyInfo saved = createPharmacy("Record Pharmacy", "Hamilton", '1');
            PharmacyInfo found = pharmacyInfoDao.getPharmacyByRecordID(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("Record Pharmacy");
        }

        @Test
        @Tag("delete")
        @DisplayName("should soft-delete pharmacy by setting status to DELETED")
        void shouldSoftDeletePharmacy_whenDeleted() {
            PharmacyInfo saved = createPharmacy("To Delete Pharmacy", "London", '1');
            pharmacyInfoDao.deletePharmacy(saved.getId());
            PharmacyInfo found = pharmacyInfoDao.getPharmacy(saved.getId());
            assertThat(found.getStatus()).isEqualTo('0');
        }
    }

    @Nested
    @DisplayName("Add and Update operations")
    class AddUpdateOperations {

        @Test
        @Tag("create")
        @DisplayName("should add pharmacy via addPharmacy method")
        void shouldAddPharmacy_viaAddPharmacyMethod() {
            pharmacyInfoDao.addPharmacy("New Pharmacy", "456 Elm St", "Ottawa",
                    "ON", "K2P1N5", "613-555-0200", "613-555-0201",
                    "613-555-0202", "new@pharmacy.ca", "SLI002", "New notes");
            List<PharmacyInfo> all = pharmacyInfoDao.getAllPharmacies();
            assertThat(all).isNotEmpty();
            assertThat(all).extracting(PharmacyInfo::getName).contains("New Pharmacy");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createPharmacy("Downtown Pharmacy", "Ottawa", '1');
            createPharmacy("Uptown Drugs", "Ottawa", '1');
            createPharmacy("Deleted Pharmacy", "Toronto", '0');
            createPharmacy("Village Pharmacy", "Toronto", '1');
        }

        @Test
        @Tag("query")
        @DisplayName("should find all active pharmacies")
        void shouldFindAllActivePharmacies() {
            List<PharmacyInfo> all = pharmacyInfoDao.getAllPharmacies();
            assertThat(all).hasSizeGreaterThanOrEqualTo(3);
            assertThat(all).extracting(PharmacyInfo::getStatus)
                    .doesNotContain('0');
        }

        @Test
        @Tag("query")
        @DisplayName("should search pharmacies by name and city")
        void shouldSearchPharmacies_byNameAndCity() {
            List<PharmacyInfo> results = pharmacyInfoDao.searchPharmacyByNameAddressCity(
                    "Downtown", "Ottawa");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Downtown Pharmacy");
            assertThat(results.get(0).getCity()).isEqualTo("Ottawa");
        }

        @Test
        @Tag("query")
        @DisplayName("should search pharmacy cities")
        void shouldSearchPharmacyCities() {
            List<String> cities = pharmacyInfoDao.searchPharmacyByCity("Ott%");
            assertThat(cities).isNotEmpty();
        }
    }
}
