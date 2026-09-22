/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.PortalDeliveryState;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration") @Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PortalEmailDeliveryDaoIntegrationTest extends CarlosTestBase {
    @Autowired private EmailLogDaoImpl logs;

    @Test void shouldPersistIntentAndRejectAConflictingRecoveryDecision_forStaleExpectedState() {
        var log = preparedLog();
        try {
            assertThat(logs.initializePortalDelivery(log)).isTrue();
            assertThat(logs.initializePortalDelivery(log)).isFalse();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.PREPARING, PortalDeliveryState.SENDING, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.REVOKE_PENDING, 77L)).isFalse();
            var stored = logs.find(log.getId());
            assertThat(stored.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.SENT);
            assertThat(stored.getPortalSecretId()).isEqualTo(77L);
            assertThat(stored.getPassword()).isEmpty();
            assertThat(stored.getPasswordClue()).isEmpty();
        } finally { logs.remove(log.getId()); }
    }

    @Test void shouldReachPublished_forAcceptedEmailRecoveredInOrder() {
        var log = preparedLog();
        try {
            assertThat(logs.initializePortalDelivery(log)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.PREPARING, PortalDeliveryState.READY, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.READY, PortalDeliveryState.SENDING, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.SENDING, PortalDeliveryState.SENT, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.SENT, PortalDeliveryState.PUBLISHED, 77L)).isTrue();
            assertThat(logs.find(log.getId()).getPortalDeliveryState()).isEqualTo(PortalDeliveryState.PUBLISHED);
        } finally { logs.remove(log.getId()); }
    }

    @Test void shouldReachRevoked_forDefiniteRejectionWithLostCreateResponse() {
        var log = preparedLog();
        try {
            assertThat(logs.initializePortalDelivery(log)).isTrue();
            // The create response was lost, so the secret id is recorded only on the way to revocation.
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.PREPARING, PortalDeliveryState.REVOKE_PENDING, null)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.REVOKE_PENDING, PortalDeliveryState.REVOKE_PENDING, 77L)).isTrue();
            assertThat(logs.transitionPortalDelivery(log, PortalDeliveryState.REVOKE_PENDING, PortalDeliveryState.REVOKED, 77L)).isTrue();
            var stored = logs.find(log.getId());
            assertThat(stored.getPortalDeliveryState()).isEqualTo(PortalDeliveryState.REVOKED);
            assertThat(stored.getPortalSecretId()).isEqualTo(77L);
        } finally { logs.remove(log.getId()); }
    }

    private EmailLog preparedLog() {
        var log = new EmailLog(); log.setStatus(EmailLog.EmailStatus.FAILED); log.setPassword("legacy-password");
        log.setPasswordClue("legacy clue"); logs.persist(log); logs.detach(log);
        log.setPortalSourceReference("email-" + java.util.UUID.randomUUID());
        log.setPortalOrigin("https://portal.example.org"); log.setPortalClinicId("clinic");
        return log;
    }
}
