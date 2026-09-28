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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Duplicate live consent records must resolve deterministically and fail safe (#3845). */
@DisplayName("ConsentDao duplicate records")
@Tag("integration")
@Tag("dao")
@Tag("read")
class ConsentDaoDuplicateRecordsIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsentDao consentDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private ConsentType emailType;

    @BeforeEach
    void setUp() {
        emailType = new ConsentType();
        emailType.setName("Email");
        emailType.setType("dup_test_email_consent");
        emailType.setActive(true);
        entityManager.persist(emailType);
        entityManager.flush();
    }

    private Consent record(int demographicNo, boolean optout, boolean deleted, long editedAt) {
        return record(emailType, demographicNo, optout, deleted, editedAt);
    }

    private Consent record(ConsentType type, int demographicNo, boolean optout, boolean deleted, long editedAt) {
        Consent consent = new Consent();
        consent.setDemographicNo(demographicNo);
        consent.setConsentType(type);
        consent.setExplicit(true);
        consent.setOptout(optout);
        consent.setDeleted(deleted);
        consent.setEditDate(new Date(editedAt));
        entityManager.persist(consent);
        return consent;
    }

    /** Detaches the fixtures, so the DAO reads its records back from the database. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("should let a live opt-out decide over a newer live opt-in")
    void shouldReturnTheOptOut_whenLiveDuplicatesDisagree() {
        Consent optOut = record(501, true, false, 1_000L);
        record(501, false, false, 2_000L);
        flushAndClear();

        Consent deciding = consentDao.findByDemographicAndConsentTypeId(501, emailType.getId());

        assertThat(deciding.getId()).isEqualTo(optOut.getId());
        assertThat(deciding.getPatientConsented()).isFalse();
    }

    @Test
    @DisplayName("should ignore a deleted opt-out and return the newest live record")
    void shouldIgnoreDeletedRecords_whenChoosing() {
        record(502, true, true, 3_000L);
        record(502, false, false, 1_000L);
        Consent newest = record(502, false, false, 2_000L);
        flushAndClear();

        assertThat(consentDao.findByDemographicAndConsentTypeId(502, emailType.getId()).getId()).isEqualTo(newest.getId());
        assertThat(consentDao.findLiveByDemographicAndConsentTypeId(502, emailType.getId()))
                .extracting(consent -> consent.getEditDate().getTime()).containsExactly(2_000L, 1_000L);
    }

    @Test
    @DisplayName("should not return a deleted record from the lookup by type name")
    void shouldExcludeDeletedRecords_whenLookingUpByTypeName() {
        record(503, false, true, 1_000L);
        flushAndClear();

        assertThat(consentDao.findByDemographicAndConsentType(503, "dup_test_email_consent")).isNull();
    }

    @Test
    @DisplayName("should let a live opt-out decide in the lookup by type name, as DHIR submissions use it")
    void shouldReturnTheOptOut_whenLookingUpDuplicatesByTypeName() {
        // Opposite insert orders, so neither the lowest nor the highest id can pass for the rule.
        record(507, false, false, 2_000L);
        Consent optOutInsertedLast = record(507, true, false, 1_000L);
        Consent optOutInsertedFirst = record(508, true, false, 1_000L);
        record(508, false, false, 2_000L);
        flushAndClear();

        assertThat(consentDao.findByDemographicAndConsentType(507, "dup_test_email_consent").getId())
                .isEqualTo(optOutInsertedLast.getId());
        assertThat(consentDao.findByDemographicAndConsentType(508, "dup_test_email_consent").getId())
                .isEqualTo(optOutInsertedFirst.getId());
    }

    @Test
    @DisplayName("should ignore records of an inactive type in the lookup by type name")
    void shouldExcludeInactiveTypes_whenLookingUpByTypeName() {
        ConsentType inactive = new ConsentType();
        inactive.setName("Retired DHIR consent");
        inactive.setType("dup_test_inactive_consent");
        inactive.setActive(false);
        entityManager.persist(inactive);
        record(inactive, 509, false, false, 1_000L);
        flushAndClear();

        assertThat(consentDao.findByDemographicAndConsentType(509, "dup_test_inactive_consent")).isNull();
    }

    @Test
    @DisplayName("should return the live records newest first when reading them for update")
    void shouldReturnLiveRecordsNewestFirst_whenReadingForUpdate() {
        record(510, false, false, 1_000L);
        record(510, true, true, 3_000L);
        record(510, true, false, 2_000L);
        flushAndClear();

        assertThat(consentDao.findLiveByDemographicAndConsentTypeIdForUpdate(510, emailType.getId()))
                .extracting(consent -> consent.getEditDate().getTime()).containsExactly(2_000L, 1_000L);
    }

    @Test
    @DisplayName("should run the patient-row lock in the caller's transaction, whether or not the patient exists")
    void shouldRunPatientRowLock_inCallersTransaction() {
        // The native SQL has to match the real table and column; H2 cannot show the lock itself.
        assertThatCode(() -> consentDao.lockPatientForConsentChange(511)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should list a patient as consented once, and not at all if any live record opts out")
    void shouldListConsentedPatients_onlyWithoutALiveOptOut() {
        record(504, false, false, 1_000L);
        record(504, false, false, 2_000L);
        record(505, false, false, 2_000L);
        record(505, true, false, 1_000L);
        record(506, false, false, 1_000L);
        record(506, true, true, 2_000L);
        flushAndClear();

        assertThat(consentDao.findAllDemoIdsConsentedToType(emailType.getId())).containsExactlyInAnyOrder(504, 506);
    }
}
