/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CaseManagementCPPDAO} verifying save, retrieve,
 * null-field conversion, and ordering behavior during the Hibernate migration.
 *
 * <p>The Case Management CPP (Clinical Patient Profile) is a per-patient record
 * that aggregates key clinical summaries: social history, family history, medical
 * history, ongoing concerns, reminders, past medications, and support systems.
 * Each patient (demographic) may have multiple CPP records over time, distinguished
 * by their {@code update_date}.</p>
 *
 * <h3>DAO Behaviors Under Test</h3>
 * <ul>
 *   <li>{@link CaseManagementCPPDAO#saveCPP(CaseManagementCPP)} -- persists or updates
 *       a CPP record via Hibernate {@code saveOrUpdate}, automatically setting
 *       {@code update_date} to the current timestamp and converting any {@code null}
 *       text fields to empty strings to prevent downstream {@link NullPointerException}s
 *       in JSP rendering and clinical note display.</li>
 *   <li>{@link CaseManagementCPPDAO#getCPP(String)} -- retrieves the most recently
 *       updated CPP for a given demographic number using an HQL query with positional
 *       parameter syntax ({@code ?0}) and {@code ORDER BY update_date DESC}, returning
 *       only the first result or {@code null} if no record exists.</li>
 * </ul>
 *
 * <h3>Hibernate Migration Context</h3>
 * <p>These tests were written to validate correct behavior after Hibernate version
 * changes. Specifically, they confirm that:</p>
 * <ul>
 *   <li>HQL positional parameters ({@code ?0}) work correctly with the current
 *       Hibernate 5.x version.</li>
 *   <li>The {@code order by update_date desc} clause in {@code getCPP} returns the
 *       most recent record first, which is critical for clinical display correctness.</li>
 *   <li>The null-to-empty-string conversion in {@code saveCPP} is applied to all
 *       eight text fields before persistence.</li>
 * </ul>
 *
 * <h3>Test Infrastructure</h3>
 * <p>Extends {@link CarlosTestBase} which provides Spring context initialization,
 * SpringUtils anti-pattern handling, and automatic transaction rollback. The
 * {@link Transactional} annotation on this class ensures each test runs in its own
 * transaction that is rolled back after completion, preventing test data leakage
 * between test methods.</p>
 *
 * <h3>HBM Mapping</h3>
 * <p>The {@link CaseManagementCPP} entity is mapped via
 * {@code casemgmt_cpp.hbm.xml} (note the lowercase with underscores convention),
 * located at {@code io/github/carlos_emr/carlos/casemgmt/model/casemgmt_cpp.hbm.xml}.</p>
 *
 * @since 2026-02-09
 * @see CaseManagementCPPDAO
 * @see CaseManagementCPPDAOImpl
 * @see CaseManagementCPP
 * @see CarlosTestBase
 */
@DisplayName("CaseManagementCPPDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementCPPDAOIntegrationTest extends CarlosTestBase {

    /**
     * The DAO under test, injected by Spring using the bean name
     * {@code "CaseManagementCPPDAO"}.
     *
     * <p>This is an instance of {@link CaseManagementCPPDAOImpl}, which extends
     * {@link org.springframework.orm.hibernate5.support.HibernateDaoSupport} and
     * provides two operations: {@code getCPP(String)} for retrieval and
     * {@code saveCPP(CaseManagementCPP)} for persistence with null-field sanitization.</p>
     *
     * @see CaseManagementCPPDAO#getCPP(String)
     * @see CaseManagementCPPDAO#saveCPP(CaseManagementCPP)
     */
    @Autowired
    @Qualifier("CaseManagementCPPDAO")
    private CaseManagementCPPDAO caseManagementCPPDAO;

    /**
     * JPA {@link EntityManager} used for direct database operations in test setup
     * and verification, bypassing the DAO layer when necessary.
     *
     * <p>This is injected with the {@code "entityManagerFactory"} persistence unit,
     * which shares the same Hibernate session factory as the DAO under test. It is
     * used in tests to:</p>
     * <ul>
     *   <li>{@code flush()} -- force SQL execution within the current transaction
     *       so that subsequent reads see the persisted state.</li>
     *   <li>{@code persist()} -- insert entities directly without triggering the DAO's
     *       null-field conversion or automatic {@code update_date} assignment, allowing
     *       controlled test data setup (e.g., setting specific timestamps).</li>
     *   <li>{@code clear()} -- evict all entities from the first-level (session) cache,
     *       forcing the next query to hit the database rather than returning a cached
     *       in-memory reference.</li>
     * </ul>
     *
     * @see javax.persistence.EntityManager
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a {@link CaseManagementCPP} test fixture with all text fields populated.
     *
     * <p>This factory method produces a fully populated but unsaved entity suitable for
     * use in tests that exercise the {@code saveCPP} and {@code getCPP} methods. All
     * eight text fields handled by the DAO's null-to-empty-string conversion are set to
     * non-null values, making this useful for verifying round-trip persistence of
     * populated records. Tests that need to verify null handling should create entities
     * directly rather than using this method.</p>
     *
     * <p>The entity is not persisted; callers must invoke
     * {@link CaseManagementCPPDAO#saveCPP(CaseManagementCPP)} or
     * {@link EntityManager#persist(Object)} to save it.</p>
     *
     * @param demographicNo String the patient demographic number used as the lookup key
     *                      in {@link CaseManagementCPPDAO#getCPP(String)}
     * @param providerNo    String the healthcare provider number associated with this
     *                      CPP record
     * @return CaseManagementCPP a new unsaved entity with all clinical text fields set
     *         to descriptive placeholder values
     *
     * @see CaseManagementCPP
     */
    private CaseManagementCPP createCPP(String demographicNo, String providerNo) {
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setDemographic_no(demographicNo);
        cpp.setProviderNo(providerNo);

        // Populate all eight text fields that saveCPP checks for null
        cpp.setSocialHistory("Social history notes");
        cpp.setFamilyHistory("Family history notes");
        cpp.setMedicalHistory("Medical history notes");
        cpp.setOngoingConcerns("Ongoing concerns notes");
        cpp.setReminders("Reminder notes");
        cpp.setPastMedications("Past medications notes");
        cpp.setOtherFileNumber("OFN-001");
        cpp.setOtherSupportSystems("Support systems notes");
        return cpp;
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#saveCPP(CaseManagementCPP)} persists
     * a new CPP record and that it can be retrieved by demographic number.
     *
     * <p>This is the fundamental save-and-retrieve round-trip test. It confirms that:
     * <ul>
     *   <li>The entity receives a generated primary key ({@code id}) after persistence.</li>
     *   <li>The persisted record is retrievable via
     *       {@link CaseManagementCPPDAO#getCPP(String)} using the same demographic number.</li>
     *   <li>The demographic number and provider number survive the round-trip without
     *       modification.</li>
     * </ul>
     *
     * <p><b>DAO behavior tested:</b> {@code saveCPP} delegates to Hibernate's
     * {@code saveOrUpdate()}, which inserts a new row when the entity has no existing
     * identifier.</p>
     *
     * @see CaseManagementCPPDAO#saveCPP(CaseManagementCPP)
     * @see CaseManagementCPPDAO#getCPP(String)
     */
    @Test
    @Tag("create")
    @DisplayName("should save CPP when valid data provided")
    void shouldSaveCPP_whenValidDataProvided() {
        // Given - create a fully populated CPP for a test patient
        CaseManagementCPP cpp = createCPP("20001", "provA");

        // When - persist via the DAO and flush to force SQL execution
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // Then - verify the entity received a generated ID
        assertThat(cpp.getId()).isNotNull();

        // Verify the record is retrievable by demographic number
        CaseManagementCPP loaded = caseManagementCPPDAO.getCPP("20001");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDemographic_no()).isEqualTo("20001");
        assertThat(loaded.getProviderNo()).isEqualTo("provA");
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#getCPP(String)} returns a complete
     * CPP record with all clinical text fields intact.
     *
     * <p>This test goes beyond the basic round-trip in
     * {@link #shouldSaveCPP_whenValidDataProvided()} by checking that all clinical
     * summary fields -- social history, family history, medical history, ongoing
     * concerns, and reminders -- are correctly persisted and retrieved. These fields
     * are displayed in the patient's case management view and must not be lost or
     * corrupted during save/load cycles.</p>
     *
     * <p><b>DAO behavior tested:</b> {@code getCPP} executes an HQL query using
     * positional parameter syntax ({@code ?0}) and returns the first result from
     * the result set ordered by {@code update_date DESC}.</p>
     *
     * @see CaseManagementCPPDAO#getCPP(String)
     * @see CaseManagementCPP#getSocialHistory()
     * @see CaseManagementCPP#getFamilyHistory()
     * @see CaseManagementCPP#getMedicalHistory()
     * @see CaseManagementCPP#getOngoingConcerns()
     * @see CaseManagementCPP#getReminders()
     */
    @Test
    @Tag("read")
    @DisplayName("should return CPP when demographic number matches")
    void shouldReturnCPP_whenDemographicNoMatches() {
        // Given - save a CPP with known clinical content
        CaseManagementCPP cpp = createCPP("20002", "provB");
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // When - retrieve by demographic number
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("20002");

        // Then - verify the record was found and all clinical fields match
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo("20002");
        assertThat(found.getSocialHistory()).isEqualTo("Social history notes");
        assertThat(found.getFamilyHistory()).isEqualTo("Family history notes");
        assertThat(found.getMedicalHistory()).isEqualTo("Medical history notes");
        assertThat(found.getOngoingConcerns()).isEqualTo("Ongoing concerns notes");
        assertThat(found.getReminders()).isEqualTo("Reminder notes");
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#getCPP(String)} returns {@code null}
     * when no CPP record exists for the given demographic number.
     *
     * <p>This is an important edge case for the clinical UI: when a new patient has
     * no existing CPP, the application must handle a {@code null} return gracefully
     * rather than throwing an exception. The DAO implementation checks
     * {@code results.size() != 0} and returns {@code null} for an empty result set.</p>
     *
     * <p><b>DAO behavior tested:</b> When the HQL query returns an empty list (no
     * matching rows for the demographic number), {@code getCPP} returns {@code null}
     * rather than throwing a {@link javax.persistence.NoResultException} or returning
     * an empty entity.</p>
     *
     * @see CaseManagementCPPDAO#getCPP(String)
     */
    @Test
    @Tag("read")
    @DisplayName("should return null when demographic number does not exist")
    void shouldReturnNull_whenDemographicNoDoesNotExist() {
        // When - query for a demographic number with no persisted CPP
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("99999");

        // Then - null indicates no CPP exists for this patient
        assertThat(found).isNull();
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#saveCPP(CaseManagementCPP)}
     * automatically sets the {@code update_date} field to the current timestamp.
     *
     * <p>The {@code saveCPP} implementation always assigns {@code new Date()} to
     * the entity's {@code update_date} before calling Hibernate's {@code saveOrUpdate}.
     * This ensures that the ordering in {@code getCPP} (which uses
     * {@code ORDER BY update_date DESC}) correctly identifies the most recent record.
     * The test brackets the save operation with timestamp captures to verify the
     * assigned date falls within the expected window.</p>
     *
     * <p><b>DAO behavior tested:</b> The line {@code cpp.setUpdate_date(new Date())}
     * in {@link CaseManagementCPPDAOImpl#saveCPP(CaseManagementCPP)} is executed
     * unconditionally, overwriting any previously set value.</p>
     *
     * @see CaseManagementCPPDAO#saveCPP(CaseManagementCPP)
     * @see CaseManagementCPP#getUpdate_date()
     */
    @Test
    @Tag("create")
    @DisplayName("should set update date when saving")
    void shouldSetUpdateDate_whenSaving() {
        // Given - create a CPP without an explicit update_date
        CaseManagementCPP cpp = createCPP("20004", "provD");

        // Capture a timestamp before saving to establish a lower bound
        Date beforeSave = new Date();

        // When - save via the DAO, which internally calls setUpdate_date(new Date())
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // Capture a timestamp after saving to establish an upper bound
        Date afterSave = new Date();

        // Then - the DAO-assigned update_date must fall within [beforeSave, afterSave]
        assertThat(cpp.getUpdate_date()).isNotNull();
        // inclusive=true on both bounds accounts for sub-millisecond execution
        assertThat(cpp.getUpdate_date()).isBetween(beforeSave, afterSave, true, true);
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#saveCPP(CaseManagementCPP)} converts
     * all {@code null} text fields to empty strings before persisting.
     *
     * <p>This is a critical defensive behavior in the DAO: the {@code saveCPP} method
     * checks eight text fields (familyHistory, medicalHistory, socialHistory,
     * ongoingConcerns, reminders, otherFileNumber, otherSupportSystems, pastMedications)
     * and replaces any {@code null} value with an empty string {@code ""}. This
     * prevents {@link NullPointerException}s in downstream JSP pages and clinical
     * note display logic that concatenate or format these fields without null checks.</p>
     *
     * <p>The test validates the conversion in two ways:</p>
     * <ol>
     *   <li><b>In-memory verification:</b> After {@code saveCPP} returns, the entity
     *       object's fields are checked directly. Since {@code saveCPP} modifies the
     *       entity via setters before calling {@code saveOrUpdate}, the in-memory state
     *       reflects the conversion immediately.</li>
     *   <li><b>Database verification:</b> A fresh load via {@code getCPP} confirms that
     *       the empty strings were actually persisted to the database and survive a
     *       round-trip through Hibernate.</li>
     * </ol>
     *
     * <p><b>DAO behavior tested:</b> The eight null-check-and-replace blocks in
     * {@link CaseManagementCPPDAOImpl#saveCPP(CaseManagementCPP)}, e.g.:
     * {@code String fhist = cpp.getFamilyHistory() == null ? "" : cpp.getFamilyHistory();}
     * followed by {@code cpp.setFamilyHistory(fhist);}</p>
     *
     * @see CaseManagementCPPDAO#saveCPP(CaseManagementCPP)
     * @see CaseManagementCPPDAO#getCPP(String)
     */
    @Test
    @Tag("create")
    @DisplayName("should convert null fields to empty strings when saving")
    void shouldConvertNullFieldsToEmptyStrings_whenSaving() {
        // Given - create a CPP with null values for all eight sanitized text fields
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setDemographic_no("20005");
        cpp.setProviderNo("provE");

        // Explicitly set all eight fields to null to exercise the conversion logic
        cpp.setFamilyHistory(null);
        cpp.setMedicalHistory(null);
        cpp.setSocialHistory(null);
        cpp.setOngoingConcerns(null);
        cpp.setReminders(null);
        cpp.setOtherFileNumber(null);
        cpp.setOtherSupportSystems(null);
        cpp.setPastMedications(null);

        // When - saveCPP should convert all nulls to empty strings before persisting
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // Then (Part 1) - verify in-memory: saveCPP modifies the entity object directly
        // via setters, so the conversion is visible on the same object reference
        assertThat(cpp.getFamilyHistory()).isEqualTo("");
        assertThat(cpp.getMedicalHistory()).isEqualTo("");
        assertThat(cpp.getSocialHistory()).isEqualTo("");
        assertThat(cpp.getOngoingConcerns()).isEqualTo("");
        assertThat(cpp.getReminders()).isEqualTo("");
        assertThat(cpp.getOtherFileNumber()).isEqualTo("");
        assertThat(cpp.getOtherSupportSystems()).isEqualTo("");
        assertThat(cpp.getPastMedications()).isEqualTo("");

        // Then (Part 2) - verify via database round-trip: reload from the database
        // to confirm empty strings were actually persisted, not just set in memory
        entityManager.clear();
        CaseManagementCPP loaded = caseManagementCPPDAO.getCPP("20005");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFamilyHistory()).isEqualTo("");
        assertThat(loaded.getMedicalHistory()).isEqualTo("");
        assertThat(loaded.getSocialHistory()).isEqualTo("");
        assertThat(loaded.getOngoingConcerns()).isEqualTo("");
        assertThat(loaded.getReminders()).isEqualTo("");
        assertThat(loaded.getOtherFileNumber()).isEqualTo("");
        assertThat(loaded.getOtherSupportSystems()).isEqualTo("");
        assertThat(loaded.getPastMedications()).isEqualTo("");
    }

    /**
     * Verifies that {@link CaseManagementCPPDAO#getCPP(String)} returns the most
     * recently updated CPP when multiple records exist for the same demographic.
     *
     * <p>In clinical practice, a patient may accumulate multiple CPP records over time
     * as different providers update their clinical profile. The {@code getCPP} method
     * uses {@code ORDER BY update_date DESC} and returns only the first result, ensuring
     * the clinical UI always displays the most current information. This test validates
     * that ordering behavior by creating two CPP records with deliberately different
     * timestamps.</p>
     *
     * <h4>Test Setup Strategy</h4>
     * <p>This test deliberately bypasses the DAO's {@code saveCPP} method and uses
     * {@link EntityManager#persist(Object)} directly. This is necessary because
     * {@code saveCPP} always overrides {@code update_date} with {@code new Date()},
     * making it impossible to create records with controlled, different timestamps
     * through the DAO API alone. By using {@code persist()}, we can assign specific
     * timestamps (one day apart) to guarantee deterministic ordering.</p>
     *
     * <p>After persisting both records, the test calls {@link EntityManager#clear()}
     * to evict them from Hibernate's first-level (session) cache. Without this step,
     * the subsequent {@code getCPP} query could return a cached entity rather than
     * executing the HQL query against the database, which would not exercise the
     * {@code ORDER BY} clause.</p>
     *
     * <p><b>DAO behavior tested:</b> The HQL clause
     * {@code "order by update_date desc"} in
     * {@link CaseManagementCPPDAOImpl#getCPP(String)}, combined with returning
     * {@code results.get(0)} to select only the newest record.</p>
     *
     * @see CaseManagementCPPDAO#getCPP(String)
     * @see CaseManagementCPP#getUpdate_date()
     */
    @Test
    @Tag("read")
    @DisplayName("should return most recent CPP when multiple exist for demographic")
    void shouldReturnMostRecent_whenMultipleCPPsExistForDemographic() {
        // Given - persist two CPPs for the same demographic with controlled timestamps.
        // We use EntityManager.persist() directly instead of the DAO's saveCPP() because
        // saveCPP() always overwrites update_date with new Date(), preventing us from
        // setting distinct timestamps for ordering verification.

        // Create an older CPP record with update_date set to 24 hours ago
        CaseManagementCPP olderCpp = new CaseManagementCPP();
        olderCpp.setDemographic_no("20006");
        olderCpp.setProviderNo("provOld");
        olderCpp.setSocialHistory("Older social history");
        // 86400000 ms = 24 hours; this ensures a clear ordering gap
        olderCpp.setUpdate_date(new Date(System.currentTimeMillis() - 86400000));
        entityManager.persist(olderCpp);

        // Create a newer CPP record with update_date set to now
        CaseManagementCPP newerCpp = new CaseManagementCPP();
        newerCpp.setDemographic_no("20006");
        newerCpp.setProviderNo("provNew");
        newerCpp.setSocialHistory("Newer social history");
        newerCpp.setUpdate_date(new Date());
        entityManager.persist(newerCpp);

        // Flush to ensure both entities are written to the database
        entityManager.flush();

        // Clear the first-level (session) cache so getCPP's HQL query must actually
        // execute against the database, exercising the ORDER BY clause
        entityManager.clear();

        // When - retrieve the CPP for this demographic; should get the newest one
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("20006");

        // Then - verify the returned record is the newer one based on provider and content
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo("20006");
        assertThat(found.getProviderNo()).isEqualTo("provNew");
        assertThat(found.getSocialHistory()).isEqualTo("Newer social history");
    }
}
