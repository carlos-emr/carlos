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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AgencyDao} persistence operations.
 *
 * <p>These tests validate that the AgencyDao correctly persists, retrieves,
 * and updates Agency entities. Tests use an H2 in-memory database and
 * are designed to catch Hibernate migration regressions.</p>
 *
 * <p>The {@link Agency} entity represents a healthcare agency within the
 * CARLOS EMR Program Management (PMmodule) system. Each agency tracks
 * intake form configuration via quick and in-depth intake form IDs and
 * their associated state codes (e.g., "HS" for Health Service, "AC" for Active).</p>
 *
 * <p><b>DAO behavior under test:</b></p>
 * <ul>
 *   <li>{@link AgencyDao#saveAgency(Agency)} - persists or updates an Agency
 *       entity using Hibernate's {@code saveOrUpdate}; throws
 *       {@link IllegalArgumentException} when passed {@code null}</li>
 *   <li>{@link AgencyDao#getLocalAgency()} - retrieves the first Agency record
 *       from the database, or returns {@code null} if no agencies exist. The
 *       implementation queries all Agency records and returns the first result,
 *       reflecting the single-agency-per-installation design of CARLOS EMR.</li>
 * </ul>
 *
 * <p><b>Test infrastructure:</b></p>
 * <ul>
 *   <li>Extends {@link CarlosTestBase} which initializes the Spring context
 *       and the legacy {@code SpringUtils} static bean factory via reflection</li>
 *   <li>All tests run within a transaction that is rolled back after each test,
 *       ensuring test isolation and a clean database state</li>
 *   <li>Uses JPA {@link EntityManager} directly for verification queries to
 *       bypass DAO caching and confirm actual database state</li>
 * </ul>
 *
 * @since 2026-02-09
 * @see AgencyDao
 * @see AgencyDaoImpl
 * @see Agency
 * @see CarlosTestBase
 */
@DisplayName("AgencyDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class AgencyDaoIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test, injected by Spring from the test application context.
     *
     * <p>This is the {@link AgencyDaoImpl} instance configured as a Spring bean,
     * backed by Hibernate's {@code HibernateTemplate} for persistence operations.
     * Spring auto-wires this from the test context defined in
     * {@code test-context-full.xml}.</p>
     *
     * @see AgencyDao
     * @see AgencyDaoImpl
     */
    @Autowired
    private AgencyDao agencyDao;

    /**
     * JPA EntityManager used for direct database verification in test assertions.
     *
     * <p>This EntityManager is injected separately from the DAO's Hibernate session
     * to provide an independent verification path. By using {@code entityManager.find()}
     * and {@code entityManager.flush()}, tests can confirm that entities were actually
     * persisted to the database rather than just cached in the Hibernate session.</p>
     *
     * <p>The unit name {@code "entityManagerFactory"} references the test persistence
     * unit configured in the test Spring context, which connects to the H2 in-memory
     * database.</p>
     *
     * @see jakarta.persistence.EntityManager
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a new {@link Agency} instance populated with the specified intake
     * form configuration values.
     *
     * <p>This factory method constructs an Agency using the no-arg constructor
     * and setter methods, matching the pattern used by Hibernate during entity
     * hydration. The Agency is created in a transient state (not yet persisted)
     * and must be saved via {@link AgencyDao#saveAgency(Agency)} to receive
     * a generated ID.</p>
     *
     * <p><b>Field descriptions:</b></p>
     * <ul>
     *   <li><b>intakeQuick</b> - references the quick intake assessment form ID</li>
     *   <li><b>quickState</b> - two-character state code for the quick intake
     *       (e.g., "HS" = Health Service Centre, defaults to "HSC" in the model)</li>
     *   <li><b>intakeIndepth</b> - references the in-depth intake assessment form ID;
     *       nullable for agencies that only use quick intake</li>
     *   <li><b>indepthState</b> - two-character state code for the in-depth intake
     *       (e.g., "AC" = Active, defaults to "HSC" in the model)</li>
     * </ul>
     *
     * @param intakeQuick Integer the quick intake form ID
     * @param quickState String the quick intake state code (max 2 chars)
     * @param intakeIndepth Integer the in-depth intake form ID (nullable)
     * @param indepthState String the in-depth intake state code (max 2 chars)
     * @return Agency a new transient Agency instance with the given values
     * @see Agency#Agency()
     */
    private Agency createAgency(Integer intakeQuick, String quickState, Integer intakeIndepth, String indepthState) {
        Agency agency = new Agency();
        agency.setIntakeQuick(intakeQuick);
        agency.setIntakeQuickState(quickState);
        agency.setIntakeIndepth(intakeIndepth);
        agency.setIntakeIndepthState(indepthState);
        return agency;
    }

    /**
     * Verifies that {@link AgencyDao#saveAgency(Agency)} correctly persists a new
     * Agency entity to the database with all field values intact.
     *
     * <p><b>DAO behavior tested:</b> {@code saveAgency()} delegates to Hibernate's
     * {@code saveOrUpdate()}, which performs an INSERT for transient entities.
     * After persistence, the entity should receive a generated primary key ID,
     * and all field values should be retrievable from the database.</p>
     *
     * <p><b>Test strategy:</b></p>
     * <ol>
     *   <li>Create a transient Agency with known field values</li>
     *   <li>Save via the DAO and flush to force the SQL INSERT</li>
     *   <li>Verify the generated ID is non-null (confirms Hibernate identity generation)</li>
     *   <li>Re-read the entity via EntityManager (bypassing DAO cache) to verify
     *       all four fields were persisted correctly</li>
     * </ol>
     *
     * @see AgencyDao#saveAgency(Agency)
     * @see AgencyDaoImpl#saveAgency(Agency)
     */
    @Test
    @Tag("create")
    @DisplayName("should save agency when valid data provided")
    void shouldSaveAgency_whenValidDataProvided() {
        // Given - create a transient Agency with all intake fields populated
        Agency agency = createAgency(1, "HS", 2, "AC");

        // When - persist via DAO and flush to force the SQL INSERT to execute
        agencyDao.saveAgency(agency);
        hibernateTemplate.flush();

        // Then - verify the entity received a generated ID from Hibernate
        assertThat(agency.getId()).isNotNull();

        // Re-read directly via EntityManager to bypass any DAO-level caching
        // and confirm the actual database state
        Agency found = entityManager.find(Agency.class, agency.getId());
        assertThat(found).isNotNull();
        assertThat(found.getIntakeQuick()).isEqualTo(1);
        assertThat(found.getIntakeQuickState()).isEqualTo("HS");
        assertThat(found.getIntakeIndepth()).isEqualTo(2);
        assertThat(found.getIntakeIndepthState()).isEqualTo("AC");
    }

    /**
     * Verifies that {@link AgencyDao#getLocalAgency()} returns an Agency when at
     * least one record exists in the database.
     *
     * <p><b>DAO behavior tested:</b> {@code getLocalAgency()} executes the HQL query
     * {@code "from Agency a"} and returns the first result from the list. In a
     * typical CARLOS EMR installation, there is exactly one agency record representing
     * the local healthcare facility. This test confirms the query returns a non-null
     * result with a valid ID when agency data exists.</p>
     *
     * <p><b>Note:</b> This test does not assert specific field values on the returned
     * Agency because {@code getLocalAgency()} returns whichever Agency appears first
     * in the result set. The test only validates that the retrieval mechanism works
     * and produces a non-null, identifiable entity.</p>
     *
     * @see AgencyDao#getLocalAgency()
     * @see AgencyDaoImpl#getLocalAgency()
     */
    @Test
    @Tag("read")
    @DisplayName("should return agency when at least one exists")
    void shouldReturnAgency_whenAtLeastOneExists() {
        // Given - persist an agency so the database is not empty
        Agency agency = createAgency(10, "AB", null, "CD");
        agencyDao.saveAgency(agency);
        // Flush Hibernate Session to ensure the INSERT is written before the read query
        hibernateTemplate.flush();

        // When - retrieve the local agency via DAO
        Agency result = agencyDao.getLocalAgency();

        // Then - verify the returned agency matches the one we saved
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(agency.getId());
        assertThat(result.getIntakeQuick()).isEqualTo(10);
        assertThat(result.getIntakeQuickState()).isEqualTo("AB");
        assertThat(result.getIntakeIndepth()).isNull();
        assertThat(result.getIntakeIndepthState()).isEqualTo("CD");
    }

    /**
     * Verifies that {@link AgencyDao#getLocalAgency()} returns {@code null} when
     * no Agency records exist in the database.
     *
     * <p><b>DAO behavior tested:</b> When the HQL query {@code "from Agency a"}
     * returns an empty result list, {@code getLocalAgency()} should return
     * {@code null} rather than throwing an exception. This is the expected behavior
     * for a fresh CARLOS EMR installation before the initial agency configuration
     * has been completed.</p>
     *
     * <p><b>Test strategy:</b> This test relies on the transactional rollback
     * provided by {@link CarlosTestBase} to ensure the database starts empty.
     * No setup is performed, so the Agency table contains zero rows.</p>
     *
     * @see AgencyDao#getLocalAgency()
     * @see AgencyDaoImpl#getLocalAgency()
     */
    @Test
    @Tag("read")
    @DisplayName("should return null when no agencies exist")
    void shouldReturnNull_whenNoAgenciesExist() {
        // When - query for local agency against an empty table
        Agency result = agencyDao.getLocalAgency();

        // Then - null is returned because no Agency records exist
        assertThat(result).isNull();
    }

    /**
     * Verifies that {@link AgencyDao#saveAgency(Agency)} throws an
     * {@link IllegalArgumentException} when called with a {@code null} argument.
     *
     * <p><b>DAO behavior tested:</b> The {@link AgencyDaoImpl#saveAgency(Agency)}
     * implementation performs a null check before delegating to Hibernate's
     * {@code saveOrUpdate()}. Passing {@code null} triggers an explicit
     * {@code IllegalArgumentException}, providing a clear error message rather than
     * allowing a {@code NullPointerException} to propagate from deep within the
     * Hibernate internals.</p>
     *
     * <p><b>Why this matters:</b> In a healthcare EMR, defensive null checks at the
     * DAO layer prevent ambiguous errors from reaching higher layers of the application
     * and ensure that error handling is predictable and testable.</p>
     *
     * @see AgencyDao#saveAgency(Agency)
     * @see AgencyDaoImpl#saveAgency(Agency)
     */
    @Test
    @Tag("create")
    @DisplayName("should throw exception when saving null")
    void shouldThrowExceptionWhenSaveNull() {
        // When / Then - passing null should trigger the DAO's explicit null guard
        assertThatThrownBy(() -> agencyDao.saveAgency(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that {@link AgencyDao#saveAgency(Agency)} correctly updates an
     * existing Agency entity when its fields are modified and saved again.
     *
     * <p><b>DAO behavior tested:</b> The {@code saveOrUpdate()} call in
     * {@link AgencyDaoImpl#saveAgency(Agency)} performs an UPDATE (rather than
     * an INSERT) when the entity already has a persistent identity. This test
     * confirms that modified field values are propagated to the database and that
     * the entity retains its original primary key after the update.</p>
     *
     * <p><b>Test strategy:</b></p>
     * <ol>
     *   <li>Create and persist an Agency with initial values</li>
     *   <li>Capture the generated ID for later verification</li>
     *   <li>Modify two fields ({@code intakeQuick} and {@code intakeQuickState})</li>
     *   <li>Re-save the entity via the DAO</li>
     *   <li>Flush to force the UPDATE SQL statement</li>
     *   <li>Clear the persistence context to evict all cached entities, forcing
     *       a fresh database read on the next query</li>
     *   <li>Re-read via EntityManager and verify the updated values</li>
     * </ol>
     *
     * @see AgencyDao#saveAgency(Agency)
     * @see AgencyDaoImpl#saveAgency(Agency)
     */
    @Test
    @Tag("update")
    @DisplayName("should update agency when existing instance modified")
    void shouldUpdateAgency_whenExistingInstanceModified() {
        // Given - persist an agency with initial intake configuration
        Agency agency = createAgency(5, "HS", 3, "AC");
        agencyDao.saveAgency(agency);
        hibernateTemplate.flush();

        // Capture the generated primary key to verify identity is preserved after update
        Long savedId = agency.getId();

        // When - modify the quick intake fields and re-save
        agency.setIntakeQuick(99);
        agency.setIntakeQuickState("ZZ");
        agencyDao.saveAgency(agency);

        // Flush Hibernate Session to execute the UPDATE SQL, then clear both
        // persistence contexts so the subsequent read performs a real database
        // SELECT rather than returning a cached entity
        hibernateTemplate.flush();
        hibernateTemplate.clear();
        entityManager.clear();

        // Then - re-read from database and verify the updated values
        Agency updated = entityManager.find(Agency.class, savedId);
        assertThat(updated).isNotNull();
        assertThat(updated.getIntakeQuick()).isEqualTo(99);
        assertThat(updated.getIntakeQuickState()).isEqualTo("ZZ");
    }
}
