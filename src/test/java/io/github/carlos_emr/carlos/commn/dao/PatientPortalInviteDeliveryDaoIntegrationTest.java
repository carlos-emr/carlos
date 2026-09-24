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

import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Outcome;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The delivery DAO against a real database. Its writes each commit in their own transaction, because
 * the invitation workflow must record every step before the next network call; so this test runs
 * outside the base class's rolled-back transaction, sees exactly what other requests would see, and
 * removes its rows afterwards.
 */
@DisplayName("PatientPortalInviteDeliveryDao")
@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PatientPortalInviteDeliveryDaoIntegrationTest extends CarlosTestBase {

    private static final int PATIENT = 424242;

    @Autowired
    private PatientPortalInviteDeliveryDao deliveries;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @AfterEach
    void removeRows() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createQuery("DELETE FROM PatientPortalInviteDelivery d WHERE d.demographicNo IN (:a, :b)")
                .setParameter("a", PATIENT)
                .setParameter("b", PATIENT + 1)
                .executeUpdate());
    }

    @Test
    @DisplayName("should commit a claimed attempt in its own transaction, at PREPARING")
    void shouldCommitClaim_asPreparing() {
        PatientPortalInviteDelivery claimed = deliveries.claim(attempt(PATIENT));

        PatientPortalInviteDelivery stored = deliveries.find(claimed.getId());
        assertThat(stored.getState()).isEqualTo(State.PREPARING);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.isRevokeFailed()).isFalse();
        assertThat(stored.getOutcome()).isNull();
    }

    @Test
    @DisplayName("should move an attempt only from the expected state, applying the change")
    void shouldAdvance_onlyFromTheExpectedState() {
        Long id = deliveries.claim(attempt(PATIENT)).getId();

        PatientPortalInviteDelivery moved = deliveries.advance(id, State.PREPARING, State.PREPARED,
                row -> row.setPortalInviteId(41L));
        PatientPortalInviteDelivery refused = deliveries.advance(id, State.PREPARING, State.ABANDONED,
                row -> row.setOutcome(Outcome.ABANDONED_BY_STAFF));

        assertThat(moved.getState()).isEqualTo(State.PREPARED);
        assertThat(refused).isNull();
        PatientPortalInviteDelivery stored = deliveries.find(id);
        assertThat(stored.getState()).isEqualTo(State.PREPARED);
        assertThat(stored.getPortalInviteId()).isEqualTo(41L);
        assertThat(stored.getOutcome()).isNull();
    }

    @Test
    @DisplayName("should answer null for an attempt that does not exist")
    void shouldAdvanceNothing_whenTheAttemptIsMissing() {
        assertThat(deliveries.advance(Long.MAX_VALUE, State.PREPARING, State.PREPARED, null)).isNull();
    }

    @Test
    @DisplayName("should release a claim exactly as it was, last-changed time included")
    void shouldRestoreTheAttempt_whenAClaimIsReleased() {
        Long id = deliveries.claim(attempt(PATIENT)).getId();
        deliveries.advance(id, State.PREPARING, State.SEND_UNCERTAIN, row -> row.setOutcome(Outcome.SEND_UNCONFIRMED));
        PatientPortalInviteDelivery before = deliveries.find(id);
        Date idleSince = new Date(before.getUpdatedAt().getTime() - 20 * 60 * 1000L);
        deliveries.advance(id, State.SEND_UNCERTAIN, State.REVOKED, row -> row.setOutcome(Outcome.CONFIRMED_NOT_SENT));

        boolean released = deliveries.release(id, State.REVOKED, State.SEND_UNCERTAIN, Outcome.SEND_UNCONFIRMED,
                idleSince);

        assertThat(released).isTrue();
        PatientPortalInviteDelivery after = deliveries.find(id);
        assertThat(after.getState()).isEqualTo(State.SEND_UNCERTAIN);
        assertThat(after.getOutcome()).isEqualTo(Outcome.SEND_UNCONFIRMED);
        assertThat(after.getUpdatedAt().getTime() / 1000).isEqualTo(idleSince.getTime() / 1000);
        // A later compare-and-set must still work on the released row: the version moved on with it.
        assertThat(deliveries.advance(id, State.SEND_UNCERTAIN, State.SENT, row -> row.setOutcome(null)))
                .isNotNull();
    }

    @Test
    @DisplayName("should release nothing once the attempt has left the claimed state")
    void shouldReleaseNothing_whenNoLongerClaimed() {
        Long id = deliveries.claim(attempt(PATIENT)).getId();

        assertThat(deliveries.release(id, State.REVOKED, State.SEND_UNCERTAIN, null, new Date())).isFalse();
        assertThat(deliveries.find(id).getState()).isEqualTo(State.PREPARING);
    }

    @Test
    @DisplayName("should list a patient's unfinished attempts, oldest first, and nothing else")
    void shouldFindOnlyUnfinishedAttempts_forThatPatient() throws Exception {
        Long first = deliveries.claim(attempt(PATIENT)).getId();
        Thread.sleep(1100);
        Long second = deliveries.claim(attempt(PATIENT)).getId();
        deliveries.advance(second, State.PREPARING, State.SEND_UNCERTAIN, null);
        Long finished = deliveries.claim(attempt(PATIENT)).getId();
        deliveries.advance(finished, State.PREPARING, State.SENT, null);
        deliveries.claim(attempt(PATIENT + 1));

        List<PatientPortalInviteDelivery> unfinished = deliveries.findUnfinishedByDemographic(PATIENT);

        assertThat(unfinished).extracting(PatientPortalInviteDelivery::getId).containsExactly(first, second);
    }

    @Test
    @DisplayName("should list a patient's recent attempts newest first, up to the limit")
    void shouldFindRecentAttempts_newestFirstAndLimited() throws Exception {
        Long first = deliveries.claim(attempt(PATIENT)).getId();
        Thread.sleep(1100);
        Long second = deliveries.claim(attempt(PATIENT)).getId();
        Thread.sleep(1100);
        Long third = deliveries.claim(attempt(PATIENT)).getId();

        assertThat(deliveries.findRecentByDemographic(PATIENT, 2))
                .extracting(PatientPortalInviteDelivery::getId).containsExactly(third, second);
        assertThat(deliveries.findRecentByDemographic(PATIENT, 10))
                .extracting(PatientPortalInviteDelivery::getId).containsExactly(third, second, first);
    }

    private static PatientPortalInviteDelivery attempt(int demographicNo) {
        return new PatientPortalInviteDelivery("inv-" + UUID.randomUUID(), demographicNo, "maplecreek",
                "https://portal.example", Channel.EMAIL, null, "999998");
    }
}
