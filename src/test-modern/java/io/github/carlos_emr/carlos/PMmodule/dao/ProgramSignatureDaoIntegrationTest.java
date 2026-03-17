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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramSignature;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ProgramSignatureDao} persistence operations.
 *
 * <p>These tests validate that the ProgramSignatureDao correctly persists,
 * retrieves, and queries ProgramSignature entities. Tests use an H2 in-memory
 * database and are designed to catch Hibernate migration regressions,
 * particularly around HQL positional parameter binding and date ordering.</p>
 *
 * <p>The {@link ProgramSignature} entity represents a record of who created or
 * modified a program within the CARLOS EMR Program Management (PMmodule) system.
 * Each signature captures the provider, the program, and a timestamp. The DAO
 * provides methods to retrieve the first (earliest) signature for a program --
 * effectively identifying the program creator -- as well as the complete
 * chronological list of all signatures.</p>
 *
 * <h3>DAO Methods Under Test</h3>
 * <ul>
 *   <li>{@link ProgramSignatureDao#saveProgramSignature(ProgramSignature)} --
 *       Persists a signature, automatically setting {@code updateDate} to now</li>
 *   <li>{@link ProgramSignatureDao#getProgramFirstSignature(Integer)} --
 *       Returns the earliest signature (by {@code updateDate ASC}) for a program,
 *       or {@code null} for invalid/missing program IDs</li>
 *   <li>{@link ProgramSignatureDao#getProgramSignatures(Integer)} --
 *       Returns all signatures for a program ordered by {@code updateDate ASC},
 *       or {@code null} for invalid program IDs</li>
 * </ul>
 *
 * <h3>Implementation Details Validated</h3>
 * <ul>
 *   <li>HQL uses positional parameter syntax ({@code ?0}) for Hibernate 5.x compatibility</li>
 *   <li>Results are ordered by {@code updateDate ASC} to ensure chronological ordering</li>
 *   <li>Guard clauses return {@code null} for null, zero, or negative program IDs</li>
 *   <li>{@code saveProgramSignature} throws {@link IllegalArgumentException} for null input</li>
 *   <li>{@code saveProgramSignature} overwrites any existing {@code updateDate} with the current time</li>
 * </ul>
 *
 * <h3>Test Infrastructure</h3>
 * <p>Extends {@link CarlosTestBase} which provides the Spring context, H2 in-memory
 * database, and Hibernate session/transaction management. The {@code @Transactional}
 * annotation ensures each test runs within a transaction that is rolled back after
 * completion, preventing test data from leaking between tests.</p>
 *
 * @since 2026-02-09
 * @see ProgramSignatureDao
 * @see ProgramSignatureDaoImpl
 * @see ProgramSignature
 */
@DisplayName("ProgramSignatureDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramSignatureDaoIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test. Injected by Spring from the test application context.
     *
     * <p>This is the {@link ProgramSignatureDaoImpl} instance backed by
     * {@link org.springframework.orm.hibernate5.support.HibernateDaoSupport},
     * which delegates to Hibernate's {@code SessionFactory} for all persistence
     * operations.</p>
     *
     * @see ProgramSignatureDao
     * @see ProgramSignatureDaoImpl
     */
    @Autowired
    private ProgramSignatureDao programSignatureDao;

    /**
     * JPA EntityManager used for direct database verification in tests.
     *
     * <p>Bound to the {@code entityManagerFactory} persistence unit defined in
     * the test persistence configuration. This EntityManager is used to persist
     * test fixtures directly (bypassing the DAO's {@code saveProgramSignature}
     * method and its automatic date-setting behavior) and to independently
     * verify that the DAO correctly persisted entities.</p>
     *
     * <p>Using a separate EntityManager for verification ensures that assertions
     * test actual database state rather than cached in-memory objects.</p>
     *
     * @see javax.persistence.PersistenceContext
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Factory method to create a new {@link ProgramSignature} with the specified field values.
     *
     * <p>Constructs a detached (not yet persisted) ProgramSignature entity with all
     * fields populated. The returned entity has no {@code id} assigned -- it will be
     * set by Hibernate upon persistence. This method does not interact with the
     * database; it only creates an in-memory object.</p>
     *
     * <p>Note that {@code programName} is limited to 70 characters and
     * {@code providerId} to 6 characters in the database schema. Test values
     * should respect these constraints to avoid truncation errors.</p>
     *
     * @param programId    Integer the program ID this signature belongs to; maps to
     *                     the {@code program_id} column in the {@code program_signature} table
     * @param programName  String the name of the program (max 70 chars); denormalized
     *                     from the program table for display convenience
     * @param providerId   String the provider ID (max 6 chars); identifies the healthcare
     *                     provider who signed this program record
     * @param providerName String the provider name (max 60 chars); denormalized from the
     *                     provider table for display convenience
     * @param updateDate   Date the update timestamp for this signature; may be {@code null}
     *                     if the caller intends for the DAO to set it during save
     * @return ProgramSignature a new, detached ProgramSignature instance with the given values
     *         and a {@code null} id (not yet persisted)
     */
    private ProgramSignature createSignature(Integer programId, String programName, String providerId, String providerName, Date updateDate) {
        ProgramSignature ps = new ProgramSignature();
        ps.setProgramId(programId);
        ps.setProgramName(programName);
        ps.setProviderId(providerId);
        ps.setProviderName(providerName);
        ps.setUpdateDate(updateDate);
        return ps;
    }

    /**
     * Verifies that {@link ProgramSignatureDao#saveProgramSignature(ProgramSignature)}
     * correctly persists a valid ProgramSignature entity to the database.
     *
     * <p>This test validates the full save lifecycle:</p>
     * <ol>
     *   <li>The entity receives a generated ID after save (auto-increment primary key)</li>
     *   <li>The generated ID is a positive integer</li>
     *   <li>The entity can be independently retrieved from the database via EntityManager</li>
     *   <li>All field values (programId, programName, providerId, providerName) are
     *       persisted correctly and match the original input</li>
     * </ol>
     *
     * <p><strong>DAO behavior:</strong> {@code saveProgramSignature} delegates to
     * {@code HibernateTemplate.saveOrUpdate()} followed by {@code flush()}, which
     * forces the SQL INSERT to execute immediately rather than deferring to
     * transaction commit. The method also overwrites {@code updateDate} with the
     * current time before persisting.</p>
     *
     * @see ProgramSignatureDao#saveProgramSignature(ProgramSignature)
     */
    @Test
    @Tag("create")
    @DisplayName("should save program signature when valid data provided")
    void shouldSaveProgramSignature_whenValidDataProvided() {
        // Given - create a detached signature entity with all required fields populated
        ProgramSignature ps = createSignature(100, "Test Program", "P001", "Dr. Smith", new Date());

        // When - persist through the DAO, which sets updateDate and calls saveOrUpdate + flush
        programSignatureDao.saveProgramSignature(ps);

        // Then - verify the entity received an auto-generated primary key
        assertThat(ps.getId()).isNotNull();
        assertThat(ps.getId()).isGreaterThan(0);

        // Verify the entity was actually written to the database by loading it
        // independently through the EntityManager (bypassing DAO and any caching)
        ProgramSignature found = entityManager.find(ProgramSignature.class, ps.getId());
        assertThat(found).isNotNull();
        assertThat(found.getProgramId()).isEqualTo(100);
        assertThat(found.getProgramName()).isEqualTo("Test Program");
        assertThat(found.getProviderId()).isEqualTo("P001");
        assertThat(found.getProviderName()).isEqualTo("Dr. Smith");
    }

    /**
     * Verifies that {@link ProgramSignatureDao#getProgramFirstSignature(Integer)}
     * returns the chronologically earliest signature when multiple signatures
     * exist for the same program.
     *
     * <p>This test validates the {@code ORDER BY ps.updateDate ASC} clause in the
     * HQL query. By inserting two signatures with different dates for the same
     * program ID, we confirm that the DAO returns the one with the earlier
     * {@code updateDate} -- effectively identifying the original program creator.</p>
     *
     * <p><strong>Important:</strong> Signatures are persisted directly via
     * {@code EntityManager.persist()} rather than through the DAO's
     * {@code saveProgramSignature()} method. This is necessary because
     * {@code saveProgramSignature()} unconditionally overwrites {@code updateDate}
     * with {@code new Date()}, which would make both signatures have nearly
     * identical timestamps and defeat the purpose of testing date ordering.</p>
     *
     * @see ProgramSignatureDao#getProgramFirstSignature(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return first signature when multiple exist")
    void shouldReturnFirstSignature_whenMultipleExist() {
        // Given - save signatures with different dates; earliest should be returned
        // 86400000L milliseconds = 24 hours, placing "yesterday" clearly before "now"
        Date yesterday = new Date(System.currentTimeMillis() - 86400000L);
        Date now = new Date();

        // Both signatures share programId 200 but have different providers and dates
        ProgramSignature earliest = createSignature(200, "Program A", "P002", "Dr. Jones", yesterday);
        ProgramSignature latest = createSignature(200, "Program A", "P003", "Dr. Brown", now);

        // Persist directly via EntityManager to preserve the explicit updateDate values.
        // The DAO's saveProgramSignature() would overwrite updateDate with new Date(),
        // making both timestamps nearly identical and breaking the ordering test.
        entityManager.persist(earliest);
        entityManager.persist(latest);
        // Flush to force SQL INSERT execution so the subsequent HQL query can find them
        entityManager.flush();

        // When - retrieve the first (earliest by updateDate) signature for program 200
        ProgramSignature result = programSignatureDao.getProgramFirstSignature(200);

        // Then - the result should be the signature with the earlier updateDate
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(earliest.getId());
    }

    /**
     * Verifies that {@link ProgramSignatureDao#getProgramFirstSignature(Integer)}
     * returns {@code null} when called with a {@code null} program ID.
     *
     * <p>This test validates the guard clause in the DAO implementation:
     * {@code if (programId == null || programId.intValue() <= 0) return null;}.
     * The DAO should short-circuit before executing any HQL query, preventing
     * a potential {@link NullPointerException} in the Hibernate parameter binding.</p>
     *
     * @see ProgramSignatureDao#getProgramFirstSignature(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return null when program ID is null")
    void shouldReturnNull_whenProgramIdIsNull() {
        // When - call with null programId, triggering the guard clause
        ProgramSignature result = programSignatureDao.getProgramFirstSignature(null);

        // Then - guard clause returns null without executing HQL
        assertThat(result).isNull();
    }

    /**
     * Verifies that {@link ProgramSignatureDao#getProgramFirstSignature(Integer)}
     * returns {@code null} for zero and negative program IDs.
     *
     * <p>This test validates the boundary condition in the guard clause:
     * {@code programId.intValue() <= 0}. Both zero and negative values are
     * considered invalid program IDs in the CARLOS EMR system, as the
     * {@code program_id} column uses auto-increment starting from 1.
     * The DAO should reject these without hitting the database.</p>
     *
     * <p>Both edge cases (zero and negative) are tested within the same method
     * because they exercise the same guard clause branch and share identical
     * expected behavior.</p>
     *
     * @see ProgramSignatureDao#getProgramFirstSignature(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return null when program ID is zero or negative")
    void shouldReturnNull_whenProgramIdIsZeroOrNegative() {
        // When - test both boundary values that should trigger the <= 0 guard
        ProgramSignature resultZero = programSignatureDao.getProgramFirstSignature(0);
        ProgramSignature resultNegative = programSignatureDao.getProgramFirstSignature(-1);

        // Then - both should be null since zero and negative are invalid program IDs
        assertThat(resultZero).isNull();
        assertThat(resultNegative).isNull();
    }

    /**
     * Verifies that {@link ProgramSignatureDao#getProgramSignatures(Integer)}
     * returns all signatures for a given program, filtered by program ID and
     * ordered by {@code updateDate ASC}.
     *
     * <p>This test validates three key behaviors:</p>
     * <ol>
     *   <li><strong>Filtering:</strong> Only signatures matching the requested
     *       {@code programId} are returned. A signature with a different program ID
     *       (999) is inserted as a control to verify the WHERE clause works.</li>
     *   <li><strong>Completeness:</strong> All matching signatures are returned
     *       (exactly 2 for program 300).</li>
     *   <li><strong>Ordering:</strong> Results are sorted by {@code updateDate ASC},
     *       so the earliest signature appears first. This is verified by asserting
     *       that element 0's date is before or equal to element 1's date.</li>
     * </ol>
     *
     * <p>Like the first-signature test, entities are persisted directly via
     * EntityManager to preserve explicit {@code updateDate} values.</p>
     *
     * @see ProgramSignatureDao#getProgramSignatures(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return all signatures when multiple exist for program")
    void shouldReturnAllSignatures_whenMultipleExistForProgram() {
        // Given - create two signatures for program 300 and one for a different program
        // 86400000L milliseconds = 24 hours
        Date yesterday = new Date(System.currentTimeMillis() - 86400000L);
        Date now = new Date();

        ProgramSignature sig1 = createSignature(300, "Program B", "P010", "Dr. Alpha", yesterday);
        ProgramSignature sig2 = createSignature(300, "Program B", "P011", "Dr. Beta", now);
        // Control record: different programId (999) to verify WHERE clause filtering
        ProgramSignature sigOther = createSignature(999, "Other Program", "P099", "Dr. Other", now);

        // Persist directly to preserve explicit updateDate values
        entityManager.persist(sig1);
        entityManager.persist(sig2);
        entityManager.persist(sigOther);
        // Flush ensures all three INSERTs are executed before the DAO query
        entityManager.flush();

        // When - retrieve all signatures for program 300 only
        List<ProgramSignature> results = programSignatureDao.getProgramSignatures(300);

        // Then - exactly 2 results, both belonging to program 300
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProgramSignature::getProgramId)
                .containsOnly(300);
        // Verify chronological ordering: first element's date <= second element's date
        // This validates the ORDER BY ps.updateDate ASC clause in the HQL
        assertThat(results.get(0).getUpdateDate())
                .isBeforeOrEqualTo(results.get(1).getUpdateDate());
    }

    /**
     * Verifies that {@link ProgramSignatureDao#getProgramSignatures(Integer)}
     * returns an empty list when a valid (positive) program ID has no matching
     * signatures in the database.
     *
     * <p>This test uses a program ID of 99999, which is valid (positive) and thus
     * passes the guard clause ({@code programId > 0}), causing the HQL query to
     * execute. Since no signatures exist for program 99999 in the H2 test database,
     * the query returns an empty list.</p>
     *
     * <p><strong>Note on DAO behavior:</strong> The DAO implementation returns
     * {@code null} for invalid program IDs (null, zero, negative) via the guard
     * clause, but returns an empty {@link List} for valid program IDs that simply
     * have no matching records. This test validates the latter case.</p>
     *
     * @see ProgramSignatureDao#getProgramSignatures(Integer)
     */
    @Test
    @Tag("read")
    @DisplayName("should return empty list when no signatures for program")
    void shouldReturnEmptyList_whenNoSignaturesForProgram() {
        // When - query with a valid but non-existent program ID
        List<ProgramSignature> results = programSignatureDao.getProgramSignatures(99999);

        // Then - the HQL query executes (programId > 0 passes guard clause) but
        // finds no matching rows, resulting in an empty list rather than null
        assertThat(results).isEmpty();
    }

    /**
     * Verifies that {@link ProgramSignatureDao#saveProgramSignature(ProgramSignature)}
     * automatically sets the {@code updateDate} field to the current time upon save.
     *
     * <p>This test validates a critical implementation detail: the DAO's
     * {@code saveProgramSignature()} method unconditionally calls
     * {@code programSignature.setUpdateDate(new Date())} before persisting,
     * overwriting any previously set date. This ensures the {@code updateDate}
     * always reflects when the signature was actually saved, regardless of what
     * value was passed in.</p>
     *
     * <p>The test creates a signature with a {@code null} updateDate and captures
     * a timestamp immediately before calling save. After saving, it asserts that
     * the updateDate was populated and is not earlier than the pre-save timestamp,
     * confirming the DAO set it to approximately "now".</p>
     *
     * @see ProgramSignatureDao#saveProgramSignature(ProgramSignature)
     */
    @Test
    @Tag("create")
    @DisplayName("should set update date when saving")
    void shouldSetUpdateDate_whenSaving() {
        // Given - create a signature with null updateDate to prove the DAO sets it
        ProgramSignature ps = createSignature(400, "Program C", "P020", "Dr. Gamma", null);
        // Capture the time just before saving to establish a lower bound for updateDate
        Date beforeSave = new Date();

        // When - save through the DAO, which sets updateDate = new Date() internally
        programSignatureDao.saveProgramSignature(ps);

        // Then - updateDate should be set to approximately "now" (not null, not before save)
        Date afterSave = new Date();
        assertThat(ps.getUpdateDate()).isAfterOrEqualTo(beforeSave);
        assertThat(ps.getUpdateDate()).isBeforeOrEqualTo(afterSave);
    }

    /**
     * Verifies that {@link ProgramSignatureDao#saveProgramSignature(ProgramSignature)}
     * throws an {@link IllegalArgumentException} when called with a {@code null} argument.
     *
     * <p>This test validates the null-check guard clause at the beginning of
     * {@code saveProgramSignature()}: {@code if (programSignature == null) throw new
     * IllegalArgumentException();}. This defensive check prevents a
     * {@link NullPointerException} that would otherwise occur when the method
     * attempts to call {@code setUpdateDate()} on the null reference.</p>
     *
     * <p>The test uses AssertJ's {@code assertThatThrownBy} to verify both that
     * an exception is thrown and that it is specifically an
     * {@code IllegalArgumentException} (not a generic NPE or other exception type).</p>
     *
     * @see ProgramSignatureDao#saveProgramSignature(ProgramSignature)
     */
    @Test
    @Tag("create")
    @DisplayName("should throw exception when saving null")
    void shouldThrowException_whenSaveNull() {
        // When / Then - passing null must trigger the guard clause and throw
        // IllegalArgumentException before any Hibernate interaction occurs
        assertThatThrownBy(() -> programSignatureDao.saveProgramSignature(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
