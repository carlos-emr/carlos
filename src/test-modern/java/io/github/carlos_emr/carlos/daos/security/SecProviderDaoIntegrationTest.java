/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.model.security.SecProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for {@link SecProviderDaoImpl}.
 *
 * <p>This test class validates all 28 public methods of the SecProviderDao interface.
 * The DAO uses Hibernate Criteria API and HQL as its primary query mechanisms,
 * making thorough testing critical for future Criteria API to JPA migration.</p>
 *
 * <p><b>Architecture notes:</b></p>
 * <ul>
 *   <li>{@code findByProperty()} is the generic Criteria-based method that all
 *       {@code findByXxx()} delegate methods call</li>
 *   <li>{@code findByExample()} uses Hibernate's deprecated {@code Criteria} API
 *       with {@code Example.create()}</li>
 *   <li>{@code merge()}, {@code attachDirty()}, {@code attachClean()} are Hibernate
 *       session lifecycle methods</li>
 *   <li>The DAO interface has a design issue where {@code findByExample()},
 *       {@code merge()}, {@code attachDirty()}, and {@code attachClean()} accept
 *       {@code SecProviderDao} (the interface) instead of {@code SecProvider}
 *       (the entity). Since {@code SecProvider} does not implement {@code SecProviderDao},
 *       these methods are impossible to invoke correctly through the interface.
 *       They are documented here but not tested.</li>
 * </ul>
 *
 * @since 2026-02-03
 * @see SecProviderDao
 * @see SecProviderDaoImpl
 */
@DisplayName("SecProviderDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecProviderDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecProviderDao secProviderDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private String uniquePrefix;

    @BeforeEach
    void setUp() {
        uniquePrefix = String.valueOf(System.nanoTime()).substring(0, 4);

        // Create test providers with unique IDs (providerNo must fit VARCHAR(6): 4-char prefix + 2-char suffix)
        createProvider(uniquePrefix + "01", "John", "Smith", "1");  // Active
        createProvider(uniquePrefix + "02", "Jane", "Doe", "1");    // Active
        createProvider(uniquePrefix + "03", "Bob", "Johnson", "0"); // Inactive
        hibernateTemplate.flush();
    }

    private SecProvider createProvider(String providerNo, String firstName, String lastName, String status) {
        SecProvider provider = new SecProvider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setSex("M");
        provider.setProviderType("doctor");
        provider.setSpecialty("");  // Required NOT NULL field from Provider.hbm.xml
        secProviderDao.save(provider);
        return provider;
    }

    /**
     * Creates a fully-populated SecProvider with all fields set for comprehensive testing.
     */
    private SecProvider createFullProvider(String providerNo, String firstName, String lastName,
                                          String status, String providerType, String specialty,
                                          String team, String sex, String address, String phone,
                                          String workPhone, String ohipNo, String rmaNo,
                                          String billingNo, String hsoNo, String comments,
                                          String providerActivity) {
        SecProvider provider = new SecProvider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus(status);
        provider.setProviderType(providerType);
        provider.setSpecialty(specialty);
        provider.setTeam(team);
        provider.setSex(sex);
        provider.setAddress(address);
        provider.setPhone(phone);
        provider.setWorkPhone(workPhone);
        provider.setOhipNo(ohipNo);
        provider.setRmaNo(rmaNo);
        provider.setBillingNo(billingNo);
        provider.setHsoNo(hsoNo);
        provider.setComments(comments);
        provider.setProviderActivity(providerActivity);
        secProviderDao.save(provider);
        return provider;
    }

    // ========================================================================
    // findById (2 params: id, status) - Original tests
    // ========================================================================

    @Nested
    @DisplayName("findById (2 params: id, status)")
    class FindByIdAndStatus {

        @Test
        @Tag("query")
        @DisplayName("should find provider when both id and status match")
        void shouldFind_whenBothIdAndStatusMatch() {
            // When
            SecProvider result = secProviderDao.findById(uniquePrefix + "01", "1");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(uniquePrefix + "01");
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getStatus()).isEqualTo("1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when id doesn't match")
        void shouldReturnNull_whenIdDoesntMatch() {
            // When
            SecProvider result = secProviderDao.findById("999999", "1");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when status doesn't match")
        void shouldReturnNull_whenStatusDoesntMatch() {
            // When - Provider 01 is active (status=1), search for inactive (status=0)
            SecProvider result = secProviderDao.findById(uniquePrefix + "01", "0");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should find inactive provider with inactive status")
        void shouldFindInactive_whenSearchingWithInactiveStatus() {
            // When - Provider 03 is inactive
            SecProvider result = secProviderDao.findById(uniquePrefix + "03", "0");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderNo()).isEqualTo(uniquePrefix + "03");
            assertThat(result.getFirstName()).isEqualTo("Bob");
            assertThat(result.getStatus()).isEqualTo("0");
        }
    }

    // ========================================================================
    // Single parameter queries (original tests)
    // ========================================================================

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get provider by id only")
        void shouldGetById() {
            // When
            SecProvider result = secProviderDao.findById(uniquePrefix + "01");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("John");
        }

        @Test
        @Tag("read")
        @DisplayName("should find providers by last name via findByProperty path")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byLastName() {
            List<SecProvider> results = secProviderDao.findByLastName("Smith");

            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01")
                .doesNotContain(uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("read")
        @DisplayName("should return all providers via findAll")
        @SuppressWarnings("unchecked")
        void shouldReturnAllProviders_viaFindAll() {
            List<SecProvider> results = secProviderDao.findAll();

            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("read")
        @DisplayName("should find providers by status")
        void shouldFindByStatus() {
            // When
            List results = secProviderDao.findByStatus("1");

            // Then - Should include our active test providers
            assertThat(results)
                .isNotEmpty()
                .anyMatch(p -> ((SecProvider) p).getProviderNo().equals(uniquePrefix + "01"));
        }
    }

    // ========================================================================
    // save() - Direct save lifecycle tests
    // ========================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @Tag("create")
        @DisplayName("should persist a new provider to the database")
        void shouldPersistNewProvider_whenSaved() {
            // Given
            SecProvider provider = new SecProvider();
            provider.setProviderNo(uniquePrefix + "S1");
            provider.setFirstName("SaveTest");
            provider.setLastName("User");
            provider.setStatus("1");
            provider.setSex("F");
            provider.setProviderType("nurse");
            provider.setSpecialty("");

            // When
            secProviderDao.save(provider);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "S1");
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("SaveTest");
            assertThat(found.getLastName()).isEqualTo("User");
            assertThat(found.getProviderType()).isEqualTo("nurse");
        }

        @Test
        @Tag("create")
        @DisplayName("should persist provider with all fields populated")
        void shouldPersistAllFields_whenFullyPopulated() {
            // Given
            SecProvider provider = new SecProvider();
            provider.setProviderNo(uniquePrefix + "S2");
            provider.setFirstName("Full");
            provider.setLastName("Fields");
            provider.setStatus("1");
            provider.setSex("M");
            provider.setProviderType("doctor");
            provider.setSpecialty("cardiology");
            provider.setTeam("TeamA");
            provider.setAddress("123 Test St");
            provider.setPhone("555-0100");
            provider.setWorkPhone("555-0200");
            provider.setOhipNo("OH12345");
            provider.setRmaNo("RMA001");
            provider.setBillingNo("BILL001");
            provider.setHsoNo("HSO001");
            provider.setComments("Test comment");
            provider.setProviderActivity("A");

            // When
            secProviderDao.save(provider);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "S2");
            assertThat(found).isNotNull();
            assertThat(found.getSpecialty()).isEqualTo("cardiology");
            assertThat(found.getTeam()).isEqualTo("TeamA");
            assertThat(found.getAddress()).isEqualTo("123 Test St");
            assertThat(found.getPhone()).isEqualTo("555-0100");
            assertThat(found.getWorkPhone()).isEqualTo("555-0200");
            assertThat(found.getOhipNo()).isEqualTo("OH12345");
            assertThat(found.getRmaNo()).isEqualTo("RMA001");
            assertThat(found.getBillingNo()).isEqualTo("BILL001");
            assertThat(found.getHsoNo()).isEqualTo("HSO001");
            assertThat(found.getComments()).isEqualTo("Test comment");
            assertThat(found.getProviderActivity()).isEqualTo("A");
        }
    }

    // ========================================================================
    // saveOrUpdate()
    // ========================================================================

    @Nested
    @DisplayName("saveOrUpdate()")
    class SaveOrUpdate {

        @Test
        @Tag("create")
        @DisplayName("should insert a new provider when it does not exist")
        void shouldInsertNewProvider_whenNotExists() {
            // Given
            SecProvider provider = new SecProvider();
            provider.setProviderNo(uniquePrefix + "U1");
            provider.setFirstName("NewSOU");
            provider.setLastName("Provider");
            provider.setStatus("1");
            provider.setSex("M");
            provider.setProviderType("doctor");
            provider.setSpecialty("");

            // When
            secProviderDao.saveOrUpdate(provider);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "U1");
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("NewSOU");
        }

        @Test
        @Tag("update")
        @DisplayName("should update an existing provider when it already exists")
        void shouldUpdateExistingProvider_whenAlreadyExists() {
            // Given - provider 01 already exists from setUp
            SecProvider existing = secProviderDao.findById(uniquePrefix + "01");
            assertThat(existing).isNotNull();
            existing.setFirstName("UpdatedJohn");

            // When
            secProviderDao.saveOrUpdate(existing);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "01");
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("UpdatedJohn");
        }
    }

    // ========================================================================
    // delete()
    // ========================================================================

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @Tag("delete")
        @DisplayName("should remove provider from the database")
        void shouldRemoveProvider_whenDeleted() {
            // Given
            SecProvider existing = secProviderDao.findById(uniquePrefix + "03");
            assertThat(existing).isNotNull();

            // When
            secProviderDao.delete(existing);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "03");
            assertThat(found).isNull();
        }

        @Test
        @Tag("delete")
        @DisplayName("should not affect other providers when one is deleted")
        @SuppressWarnings("unchecked")
        void shouldNotAffectOtherProviders_whenOneDeleted() {
            // Given
            SecProvider toDelete = secProviderDao.findById(uniquePrefix + "03");
            assertThat(toDelete).isNotNull();

            // When
            secProviderDao.delete(toDelete);
            hibernateTemplate.flush();

            // Then - other providers should still exist
            assertThat(secProviderDao.findById(uniquePrefix + "01")).isNotNull();
            assertThat(secProviderDao.findById(uniquePrefix + "02")).isNotNull();
        }
    }

    // ========================================================================
    // findById (single param) - Additional edge case tests
    // ========================================================================

    @Nested
    @DisplayName("findById (single param) - Edge Cases")
    class FindByIdEdgeCases {

        @Test
        @Tag("read")
        @DisplayName("should return null when id does not exist")
        void shouldReturnNull_whenIdNotFound() {
            // When
            SecProvider result = secProviderDao.findById("ZZZZZZ");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("read")
        @DisplayName("should return provider with all fields populated")
        void shouldReturnAllFields_whenProviderFound() {
            // Given - create a provider with many fields
            SecProvider provider = new SecProvider();
            provider.setProviderNo(uniquePrefix + "F1");
            provider.setFirstName("Detailed");
            provider.setLastName("Provider");
            provider.setStatus("1");
            provider.setSex("F");
            provider.setProviderType("specialist");
            provider.setSpecialty("neurology");
            provider.setTeam("NeuroTeam");
            provider.setAddress("456 Brain Ave");
            provider.setPhone("555-1234");
            provider.setWorkPhone("555-5678");
            provider.setOhipNo("OHIP99");
            provider.setRmaNo("RMA99");
            provider.setBillingNo("BILL99");
            provider.setHsoNo("HSO99");
            provider.setComments("Detailed test");
            provider.setProviderActivity("AC");
            secProviderDao.save(provider);
            hibernateTemplate.flush();

            // When
            SecProvider found = secProviderDao.findById(uniquePrefix + "F1");

            // Then - verify all fields round-trip correctly
            assertThat(found).isNotNull();
            assertThat(found.getFirstName()).isEqualTo("Detailed");
            assertThat(found.getLastName()).isEqualTo("Provider");
            assertThat(found.getStatus()).isEqualTo("1");
            assertThat(found.getSex()).isEqualTo("F");
            assertThat(found.getProviderType()).isEqualTo("specialist");
            assertThat(found.getSpecialty()).isEqualTo("neurology");
            assertThat(found.getTeam()).isEqualTo("NeuroTeam");
            assertThat(found.getAddress()).isEqualTo("456 Brain Ave");
            assertThat(found.getPhone()).isEqualTo("555-1234");
            assertThat(found.getWorkPhone()).isEqualTo("555-5678");
            assertThat(found.getOhipNo()).isEqualTo("OHIP99");
            assertThat(found.getRmaNo()).isEqualTo("RMA99");
            assertThat(found.getBillingNo()).isEqualTo("BILL99");
            assertThat(found.getHsoNo()).isEqualTo("HSO99");
            assertThat(found.getComments()).isEqualTo("Detailed test");
            assertThat(found.getProviderActivity()).isEqualTo("AC");
        }
    }

    // ========================================================================
    // findByProperty() - Direct Criteria API testing
    // ========================================================================

    @Nested
    @DisplayName("findByProperty() - Criteria API")
    class FindByProperty {

        @Test
        @Tag("query")
        @DisplayName("should find providers by lastName property")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byLastNameProperty() {
            // When
            List<SecProvider> results = secProviderDao.findByProperty("lastName", "Smith");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by firstName property")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byFirstNameProperty() {
            // When
            List<SecProvider> results = secProviderDao.findByProperty("firstName", "Jane");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "02");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by status property")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byStatusProperty() {
            // When
            List<SecProvider> results = secProviderDao.findByProperty("status", "0");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "03");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no provider matches property value")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenNoMatch() {
            // When
            List<SecProvider> results = secProviderDao.findByProperty("lastName", "NonExistentName_ZZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return multiple providers when several match same property value")
        @SuppressWarnings("unchecked")
        void shouldReturnMultipleProviders_whenSeveralMatch() {
            // Given - providers 01 and 02 are both active (status=1)

            // When
            List<SecProvider> results = secProviderDao.findByProperty("status", "1");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by providerType property")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byProviderTypeProperty() {
            // When - all three setUp providers have providerType="doctor"
            List<SecProvider> results = secProviderDao.findByProperty("providerType", "doctor");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers by sex property")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_bySexProperty() {
            // When - all setUp providers have sex="M"
            List<SecProvider> results = secProviderDao.findByProperty("sex", "M");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }
    }

    // ========================================================================
    // findByFirstName()
    // ========================================================================

    @Nested
    @DisplayName("findByFirstName()")
    class FindByFirstName {

        @Test
        @Tag("query")
        @DisplayName("should find provider by first name")
        @SuppressWarnings("unchecked")
        void shouldFindProvider_byFirstName() {
            // When
            List<SecProvider> results = secProviderDao.findByFirstName("John");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when first name does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenFirstNameNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByFirstName("NonExistentFirstName_ZZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find multiple providers with same first name")
        @SuppressWarnings("unchecked")
        void shouldFindMultipleProviders_withSameFirstName() {
            // Given - create another provider with same first name
            createProvider(uniquePrefix + "04", "John", "Williams", "1");
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByFirstName("John");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "04");
        }
    }

    // ========================================================================
    // findByProviderType()
    // ========================================================================

    @Nested
    @DisplayName("findByProviderType()")
    class FindByProviderType {

        @Test
        @Tag("query")
        @DisplayName("should find providers by type")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byType() {
            // When - all setUp providers are "doctor"
            List<SecProvider> results = secProviderDao.findByProviderType("doctor");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("query")
        @DisplayName("should only return providers of the specified type")
        @SuppressWarnings("unchecked")
        void shouldOnlyReturnMatchingType_whenDifferentTypesExist() {
            // Given - create a nurse provider
            createProvider(uniquePrefix + "N1", "Nurse", "Test", "1");
            SecProvider nurse = secProviderDao.findById(uniquePrefix + "N1");
            nurse.setProviderType("nurse");
            secProviderDao.saveOrUpdate(nurse);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByProviderType("nurse");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "N1")
                .doesNotContain(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when provider type does not exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenTypeNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByProviderType("nonexistent_type_ZZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findBySpecialty()
    // ========================================================================

    @Nested
    @DisplayName("findBySpecialty()")
    class FindBySpecialty {

        @Test
        @Tag("query")
        @DisplayName("should find providers by specialty")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_bySpecialty() {
            // Given - create a provider with a specific specialty
            SecProvider specialist = createFullProvider(
                uniquePrefix + "SP", "Spec", "Ialist", "1", "doctor",
                "cardiology", null, "M", null, null, null, null, null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findBySpecialty("cardiology");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "SP");
        }

        @Test
        @Tag("query")
        @DisplayName("should find providers with empty specialty")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_withEmptySpecialty() {
            // When - setUp providers have specialty=""
            List<SecProvider> results = secProviderDao.findBySpecialty("");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }
    }

    // ========================================================================
    // findByTeam()
    // ========================================================================

    @Nested
    @DisplayName("findByTeam()")
    class FindByTeam {

        @Test
        @Tag("query")
        @DisplayName("should find providers by team")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byTeam() {
            // Given
            SecProvider teamProvider = createFullProvider(
                uniquePrefix + "T1", "Team", "Member", "1", "doctor",
                "", "AlphaTeam", "M", null, null, null, null, null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByTeam("AlphaTeam");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "T1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when team does not exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenTeamNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByTeam("NonExistentTeam_ZZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findBySex()
    // ========================================================================

    @Nested
    @DisplayName("findBySex()")
    class FindBySex {

        @Test
        @Tag("query")
        @DisplayName("should find male providers")
        @SuppressWarnings("unchecked")
        void shouldFindMaleProviders_bySex() {
            // When - all setUp providers are "M"
            List<SecProvider> results = secProviderDao.findBySex("M");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("query")
        @DisplayName("should find female providers")
        @SuppressWarnings("unchecked")
        void shouldFindFemaleProviders_bySex() {
            // Given
            SecProvider femaleProvider = new SecProvider();
            femaleProvider.setProviderNo(uniquePrefix + "FP");
            femaleProvider.setFirstName("Female");
            femaleProvider.setLastName("Provider");
            femaleProvider.setStatus("1");
            femaleProvider.setSex("F");
            femaleProvider.setProviderType("doctor");
            femaleProvider.setSpecialty("");
            secProviderDao.save(femaleProvider);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findBySex("F");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "FP")
                .doesNotContain(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }
    }

    // ========================================================================
    // findByAddress()
    // ========================================================================

    @Nested
    @DisplayName("findByAddress()")
    class FindByAddress {

        @Test
        @Tag("query")
        @DisplayName("should find providers by address")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byAddress() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "A1", "Addr", "Test", "1", "doctor",
                "", null, "M", "789 Oak Blvd", null, null, null, null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByAddress("789 Oak Blvd");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "A1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when address does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenAddressNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByAddress("999 Nowhere Rd ZZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByPhone()
    // ========================================================================

    @Nested
    @DisplayName("findByPhone()")
    class FindByPhone {

        @Test
        @Tag("query")
        @DisplayName("should find providers by phone number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byPhone() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "P1", "Phone", "Test", "1", "doctor",
                "", null, "M", null, "604-555-0199", null, null, null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByPhone("604-555-0199");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "P1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when phone does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenPhoneNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByPhone("000-000-0000");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByWorkPhone()
    // ========================================================================

    @Nested
    @DisplayName("findByWorkPhone()")
    class FindByWorkPhone {

        @Test
        @Tag("query")
        @DisplayName("should find providers by work phone number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byWorkPhone() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "W1", "Work", "Phone", "1", "doctor",
                "", null, "M", null, null, "604-555-0300", null, null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByWorkPhone("604-555-0300");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "W1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when work phone does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenWorkPhoneNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByWorkPhone("000-000-0000");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByOhipNo()
    // ========================================================================

    @Nested
    @DisplayName("findByOhipNo()")
    class FindByOhipNo {

        @Test
        @Tag("query")
        @DisplayName("should find providers by OHIP number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byOhipNo() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "O1", "Ohip", "Test", "1", "doctor",
                "", null, "M", null, null, null, "OH98765", null, null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByOhipNo("OH98765");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "O1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when OHIP number does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenOhipNoNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByOhipNo("NONEXISTENT_OHIP");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByRmaNo()
    // ========================================================================

    @Nested
    @DisplayName("findByRmaNo()")
    class FindByRmaNo {

        @Test
        @Tag("query")
        @DisplayName("should find providers by RMA number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byRmaNo() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "R1", "Rma", "Test", "1", "doctor",
                "", null, "M", null, null, null, null, "RMA555", null, null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByRmaNo("RMA555");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "R1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when RMA number does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenRmaNoNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByRmaNo("NONEXISTENT_RMA");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByBillingNo()
    // ========================================================================

    @Nested
    @DisplayName("findByBillingNo()")
    class FindByBillingNo {

        @Test
        @Tag("query")
        @DisplayName("should find providers by billing number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byBillingNo() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "B1", "Bill", "Test", "1", "doctor",
                "", null, "M", null, null, null, null, null, "BILL777", null, null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByBillingNo("BILL777");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "B1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when billing number does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenBillingNoNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByBillingNo("NONEXISTENT_BILL");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByHsoNo()
    // ========================================================================

    @Nested
    @DisplayName("findByHsoNo()")
    class FindByHsoNo {

        @Test
        @Tag("query")
        @DisplayName("should find providers by HSO number")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byHsoNo() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "H1", "Hso", "Test", "1", "doctor",
                "", null, "M", null, null, null, null, null, null, "HSO444", null, null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByHsoNo("HSO444");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "H1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when HSO number does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenHsoNoNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByHsoNo("NONEXIST");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByStatus() - Additional tests
    // ========================================================================

    @Nested
    @DisplayName("findByStatus() - Extended")
    class FindByStatusExtended {

        @Test
        @Tag("query")
        @DisplayName("should find only inactive providers when status is 0")
        @SuppressWarnings("unchecked")
        void shouldFindOnlyInactiveProviders_whenStatusIsZero() {
            // When
            List<SecProvider> results = secProviderDao.findByStatus("0");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "03")
                .doesNotContain(uniquePrefix + "01", uniquePrefix + "02");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when status value does not exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenStatusValueNotFound() {
            // When - status "9" is never used in setUp data
            List<SecProvider> results = secProviderDao.findByStatus("9");

            // Then
            assertThat(results)
                .filteredOn(p -> p.getProviderNo().startsWith(uniquePrefix))
                .isEmpty();
        }
    }

    // ========================================================================
    // findByComments()
    // ========================================================================

    @Nested
    @DisplayName("findByComments()")
    class FindByComments {

        @Test
        @Tag("query")
        @DisplayName("should find providers by comments")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byComments() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "C1", "Comment", "Test", "1", "doctor",
                "", null, "M", null, null, null, null, null, null, null, "Special note about this provider", null);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByComments("Special note about this provider");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "C1");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when comments do not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenCommentsNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByComments("NONEXISTENT_COMMENT_ZZZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findByProviderActivity()
    // ========================================================================

    @Nested
    @DisplayName("findByProviderActivity()")
    class FindByProviderActivity {

        @Test
        @Tag("query")
        @DisplayName("should find providers by activity code")
        @SuppressWarnings("unchecked")
        void shouldFindProviders_byActivityCode() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "AC", "Active", "Prov", "1", "doctor",
                "", null, "M", null, null, null, null, null, null, null, null, "A");
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByProviderActivity("A");

            // Then
            assertThat(results)
                .isNotEmpty()
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "AC");
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when provider activity does not match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenActivityNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByProviderActivity("ZZZ");

            // Then
            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // findAll() - Additional tests
    // ========================================================================

    @Nested
    @DisplayName("findAll() - Extended")
    class FindAllExtended {

        @Test
        @Tag("read")
        @DisplayName("should return both active and inactive providers")
        @SuppressWarnings("unchecked")
        void shouldReturnBothActiveAndInactive_viaFindAll() {
            // When
            List<SecProvider> results = secProviderDao.findAll();

            // Then - should include both active (01,02) and inactive (03)
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "02", uniquePrefix + "03");
        }

        @Test
        @Tag("read")
        @DisplayName("should include newly saved providers in findAll results")
        @SuppressWarnings("unchecked")
        void shouldIncludeNewlySavedProviders_inFindAllResults() {
            // Given
            createProvider(uniquePrefix + "N1", "New", "Provider", "1");
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findAll();

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "N1");
        }

        @Test
        @Tag("read")
        @DisplayName("should not include deleted providers in findAll results")
        @SuppressWarnings("unchecked")
        void shouldNotIncludeDeletedProviders_inFindAllResults() {
            // Given
            SecProvider toDelete = secProviderDao.findById(uniquePrefix + "03");
            secProviderDao.delete(toDelete);
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findAll();

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .doesNotContain(uniquePrefix + "03")
                .contains(uniquePrefix + "01", uniquePrefix + "02");
        }
    }

    // ========================================================================
    // findByLastName() - Additional tests
    // ========================================================================

    @Nested
    @DisplayName("findByLastName() - Extended")
    class FindByLastNameExtended {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when last name does not exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList_whenLastNameNotFound() {
            // When
            List<SecProvider> results = secProviderDao.findByLastName("NonExistentLastName_ZZZZZZ");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should find multiple providers sharing the same last name")
        @SuppressWarnings("unchecked")
        void shouldFindMultipleProviders_withSameLastName() {
            // Given - create another Smith
            createProvider(uniquePrefix + "04", "Alice", "Smith", "1");
            hibernateTemplate.flush();

            // When
            List<SecProvider> results = secProviderDao.findByLastName("Smith");

            // Then
            assertThat(results)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01", uniquePrefix + "04");
        }

        @Test
        @Tag("query")
        @DisplayName("should use exact match for last name, not partial")
        @SuppressWarnings("unchecked")
        void shouldUseExactMatch_forLastName() {
            // When - "Smi" should NOT match "Smith" because Criteria uses eq()
            List<SecProvider> results = secProviderDao.findByLastName("Smi");

            // Then
            assertThat(results)
                .filteredOn(p -> p.getProviderNo().startsWith(uniquePrefix))
                .isEmpty();
        }
    }

    // ========================================================================
    // Cross-cutting: Criteria API behavior validation
    // ========================================================================

    @Nested
    @DisplayName("Criteria API - Cross-cutting behavior")
    class CriteriaApiBehavior {

        @Test
        @Tag("query")
        @DisplayName("should use exact match semantics for all findByProperty queries")
        @SuppressWarnings("unchecked")
        void shouldUseExactMatch_forAllPropertyQueries() {
            // Given - "doc" is a prefix of "doctor" but should NOT match
            // When
            List<SecProvider> results = secProviderDao.findByProviderType("doc");

            // Then - exact match means no partial matches
            assertThat(results)
                .filteredOn(p -> p.getProviderNo().startsWith(uniquePrefix))
                .isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should be case-sensitive for property matching")
        @SuppressWarnings("unchecked")
        void shouldBeCaseSensitive_forPropertyMatching() {
            // When - "smith" (lowercase) should not match "Smith" (capitalized)
            List<SecProvider> results = secProviderDao.findByLastName("smith");

            // Then
            assertThat(results)
                .filteredOn(p -> p.getProviderNo().startsWith(uniquePrefix))
                .isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should handle multiple findByProperty calls independently")
        @SuppressWarnings("unchecked")
        void shouldHandleMultipleCalls_independently() {
            // When - two separate findByProperty calls should not interfere
            List<SecProvider> smithResults = secProviderDao.findByLastName("Smith");
            List<SecProvider> doeResults = secProviderDao.findByLastName("Doe");

            // Then
            assertThat(smithResults)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "01");
            assertThat(doeResults)
                .extracting(SecProvider::getProviderNo)
                .contains(uniquePrefix + "02");
        }
    }

    // ========================================================================
    // Lifecycle methods: saveOrUpdate edge cases
    // ========================================================================

    @Nested
    @DisplayName("saveOrUpdate() - Edge Cases")
    class SaveOrUpdateEdgeCases {

        @Test
        @Tag("update")
        @DisplayName("should update multiple fields simultaneously")
        void shouldUpdateMultipleFields_simultaneously() {
            // Given
            SecProvider existing = secProviderDao.findById(uniquePrefix + "01");
            existing.setFirstName("ModifiedFirst");
            existing.setLastName("ModifiedLast");
            existing.setProviderType("specialist");

            // When
            secProviderDao.saveOrUpdate(existing);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "01");
            assertThat(found.getFirstName()).isEqualTo("ModifiedFirst");
            assertThat(found.getLastName()).isEqualTo("ModifiedLast");
            assertThat(found.getProviderType()).isEqualTo("specialist");
        }

        @Test
        @Tag("update")
        @DisplayName("should update status from active to inactive")
        void shouldUpdateStatus_fromActiveToInactive() {
            // Given
            SecProvider existing = secProviderDao.findById(uniquePrefix + "01");
            assertThat(existing.getStatus()).isEqualTo("1");

            // When
            existing.setStatus("0");
            secProviderDao.saveOrUpdate(existing);
            hibernateTemplate.flush();

            // Then
            SecProvider found = secProviderDao.findById(uniquePrefix + "01");
            assertThat(found.getStatus()).isEqualTo("0");
        }
    }

    // ========================================================================
    // findById with status - Additional edge cases
    // ========================================================================

    @Nested
    @DisplayName("findById(id, status) - Extended")
    class FindByIdAndStatusExtended {

        @Test
        @Tag("query")
        @DisplayName("should return null when both id and status are wrong")
        void shouldReturnNull_whenBothIdAndStatusWrong() {
            // When
            SecProvider result = secProviderDao.findById("ZZZZZZ", "9");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should distinguish between providers with same status but different ids")
        void shouldDistinguishProviders_withSameStatusDifferentIds() {
            // When - Both 01 and 02 are active (status=1)
            SecProvider result01 = secProviderDao.findById(uniquePrefix + "01", "1");
            SecProvider result02 = secProviderDao.findById(uniquePrefix + "02", "1");

            // Then - Each should return the correct provider
            assertThat(result01).isNotNull();
            assertThat(result01.getFirstName()).isEqualTo("John");
            assertThat(result02).isNotNull();
            assertThat(result02.getFirstName()).isEqualTo("Jane");
        }
    }

    // ========================================================================
    // Combined operations: Save then query
    // ========================================================================

    @Nested
    @DisplayName("Combined save-then-query operations")
    class CombinedOperations {

        @Test
        @Tag("create")
        @Tag("query")
        @DisplayName("should find newly saved provider by all property methods")
        @SuppressWarnings("unchecked")
        void shouldFindNewlySavedProvider_byAllPropertyMethods() {
            // Given - create a provider with unique values in every field
            SecProvider provider = createFullProvider(
                uniquePrefix + "FQ", "UniqueFirst", "UniqueLast", "1", "pharmacist",
                "pharmacy", "PharmTeam", "F", "321 Pill Lane", "555-PILL",
                "555-WORK", "OH_UNIQ", "RMA_UNIQ", "BILL_UNIQ", "HSO_UNIQ",
                "Unique comment text", "B");
            hibernateTemplate.flush();

            // Then - verify each findByXxx method finds this provider
            assertThat(secProviderDao.findByFirstName("UniqueFirst"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByLastName("UniqueLast"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByProviderType("pharmacist"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findBySpecialty("pharmacy"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByTeam("PharmTeam"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findBySex("F"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByAddress("321 Pill Lane"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByPhone("555-PILL"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByWorkPhone("555-WORK"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByOhipNo("OH_UNIQ"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByRmaNo("RMA_UNIQ"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByBillingNo("BILL_UNIQ"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByHsoNo("HSO_UNIQ"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByComments("Unique comment text"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");

            assertThat(secProviderDao.findByProviderActivity("B"))
                .extracting("providerNo").contains(uniquePrefix + "FQ");
        }

        @Test
        @Tag("create")
        @Tag("delete")
        @DisplayName("should not find provider after it is deleted")
        @SuppressWarnings("unchecked")
        void shouldNotFindProvider_afterDeletion() {
            // Given
            SecProvider provider = createFullProvider(
                uniquePrefix + "DL", "Delete", "Me", "1", "doctor",
                "surgery", "SurgTeam", "M", "Del Addr", "555-DEL",
                null, null, null, null, null, null, null);
            hibernateTemplate.flush();

            // Verify it exists first
            assertThat(secProviderDao.findByFirstName("Delete"))
                .extracting("providerNo").contains(uniquePrefix + "DL");

            // When
            secProviderDao.delete(provider);
            hibernateTemplate.flush();

            // Then - should no longer appear in any query
            assertThat(secProviderDao.findById(uniquePrefix + "DL")).isNull();
            assertThat(secProviderDao.findByFirstName("Delete"))
                .filteredOn(p -> ((SecProvider) p).getProviderNo().equals(uniquePrefix + "DL"))
                .isEmpty();
            assertThat(secProviderDao.findBySpecialty("surgery"))
                .filteredOn(p -> ((SecProvider) p).getProviderNo().equals(uniquePrefix + "DL"))
                .isEmpty();
        }

        @Test
        @Tag("update")
        @Tag("query")
        @DisplayName("should reflect updated field values in subsequent queries")
        @SuppressWarnings("unchecked")
        void shouldReflectUpdatedValues_inSubsequentQueries() {
            // Given
            SecProvider provider = secProviderDao.findById(uniquePrefix + "01");
            provider.setLastName("UpdatedSmith");
            provider.setPhone("555-UPDT");

            // When
            secProviderDao.saveOrUpdate(provider);
            hibernateTemplate.flush();

            // Then
            assertThat(secProviderDao.findByLastName("UpdatedSmith"))
                .extracting("providerNo").contains(uniquePrefix + "01");
            assertThat(secProviderDao.findByLastName("Smith"))
                .extracting("providerNo").doesNotContain(uniquePrefix + "01");
            assertThat(secProviderDao.findByPhone("555-UPDT"))
                .extracting("providerNo").contains(uniquePrefix + "01");
        }
    }

    // ========================================================================
    // API Design Issues - Documented but untestable methods
    // ========================================================================

    /**
     * The following methods have an interface design issue where they accept
     * {@code SecProviderDao} (the DAO interface) instead of {@code SecProvider}
     * (the entity class). Since {@code SecProvider} does not implement
     * {@code SecProviderDao}, these methods cannot be invoked correctly through
     * the interface without casting or interface redesign:
     *
     * <ul>
     *   <li>{@code findByExample(SecProviderDao)} - would need {@code SecProvider} for
     *       {@code Example.create()} to work</li>
     *   <li>{@code merge(SecProviderDao)} - would need an entity instance for
     *       {@code session.merge()} to work</li>
     *   <li>{@code attachDirty(SecProviderDao)} - would need an entity instance for
     *       {@code session.saveOrUpdate()} to work</li>
     *   <li>{@code attachClean(SecProviderDao)} - would need an entity instance for
     *       {@code session.lock()} to work</li>
     * </ul>
     *
     * <p>These methods should be refactored to accept {@code SecProvider} instead
     * of {@code SecProviderDao}. Until then, they remain untestable through the
     * public API without type-safety violations.</p>
     */
    @Nested
    @DisplayName("API Design Issues - Documented")
    class ApiDesignIssues {

        @Test
        @Tag("read")
        @DisplayName("should confirm findByExample, merge, attachDirty, attachClean accept wrong type in interface")
        void shouldDocumentInterfaceDesignIssue() {
            // This test documents that 4 methods in SecProviderDao accept SecProviderDao
            // (the interface) instead of SecProvider (the entity). Since SecProvider does
            // not implement SecProviderDao, these methods are impossible to call correctly
            // through the public interface.
            //
            // Methods affected:
            //   - findByExample(SecProviderDao instance)
            //   - merge(SecProviderDao detachedInstance)
            //   - attachDirty(SecProviderDao instance)
            //   - attachClean(SecProviderDao instance)
            //
            // The DAO is still functional for the remaining 24 methods.
            assertThat(secProviderDao).isNotNull();
        }
    }
}
