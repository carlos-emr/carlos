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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;

import java.time.Duration;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Case management note lock inactivity leases")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementNoteLockLeaseUnitTest {

    private static final long FIVE_MINUTES = Duration.ofMinutes(5).toMillis();
    private static final Date NOW = new Date(Duration.ofHours(2).toMillis());

    @Test
    @DisplayName("should protect an active lock held by another provider")
    void shouldProtectLock_whenAnotherProviderIsActive() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock activeLock = noteLock(11L, "provider-a", "session-a",
                new Date(NOW.getTime() - Duration.ofMinutes(1).toMillis()));
        when(noteLockDao.findByNoteDemo(1, 42L)).thenReturn(activeLock);

        CasemgmtNoteLock result = CaseManagementEntry2Action.acquireNoteLock(noteLockDao,
                42L, 1, "provider-b", "127.0.0.1", "session-b", NOW, FIVE_MINUTES);

        assertThat(result).isSameAs(activeLock);
        assertThat(result.isLocked()).isTrue();
        assertThat(result.isLockedBySameUser()).isFalse();
        verify(noteLockDao, never()).remove(11L);
        verify(noteLockDao, never()).persist(result);
    }

    @Test
    @DisplayName("should retain the takeover prompt for an active lock held by the same provider")
    void shouldPromptForTakeover_whenSameProviderIsActiveElsewhere() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock activeLock = noteLock(12L, "provider-a", "session-a",
                new Date(NOW.getTime() - Duration.ofMinutes(1).toMillis()));
        when(noteLockDao.findByNoteDemo(1, 42L)).thenReturn(activeLock);

        CasemgmtNoteLock result = CaseManagementEntry2Action.acquireNoteLock(noteLockDao,
                42L, 1, "provider-a", "127.0.0.1", "session-b", NOW, FIVE_MINUTES);

        assertThat(result).isSameAs(activeLock);
        assertThat(result.isLocked()).isFalse();
        assertThat(result.isLockedBySameUser()).isTrue();
        verify(noteLockDao, never()).remove(12L);
        verify(noteLockDao, never()).persist(result);
    }

    @Test
    @DisplayName("should replace an expired lock from an abandoned session")
    void shouldReplaceLock_whenPriorSessionIsAbandoned() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock expiredLock = noteLock(13L, "provider-a", "session-a",
                new Date(NOW.getTime() - FIVE_MINUTES));
        when(noteLockDao.findByNoteDemo(1, 42L)).thenReturn(expiredLock);

        CasemgmtNoteLock result = CaseManagementEntry2Action.acquireNoteLock(noteLockDao,
                42L, 1, "provider-b", "127.0.0.2", "session-b", NOW, FIVE_MINUTES);

        assertThat(result).isNotSameAs(expiredLock);
        assertThat(result.getProviderNo()).isEqualTo("provider-b");
        assertThat(result.getSessionId()).isEqualTo("session-b");
        assertThat(result.getDemographicNo()).isEqualTo(1);
        assertThat(result.getNoteId()).isEqualTo(42L);
        assertThat(result.getLockAcquired()).isEqualTo(NOW);
        assertThat(result.isLocked()).isFalse();
        assertThat(result.isLockedBySameUser()).isFalse();
        verify(noteLockDao).remove(13L);
        verify(noteLockDao).persist(result);
    }

    @Test
    @DisplayName("should treat a legacy lock without an activity timestamp as abandoned")
    void shouldReplaceLock_whenActivityTimestampIsMissing() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock undatedLock = noteLock(14L, "provider-a", "session-a", null);
        when(noteLockDao.findByNoteDemo(1, 42L)).thenReturn(undatedLock);

        CasemgmtNoteLock result = CaseManagementEntry2Action.acquireNoteLock(noteLockDao,
                42L, 1, "provider-b", "127.0.0.2", "session-b", NOW, FIVE_MINUTES);

        assertThat(result).isNotSameAs(undatedLock);
        verify(noteLockDao).remove(14L);
        verify(noteLockDao).persist(result);
    }

    @Test
    @DisplayName("should use the safe default for invalid timeout configuration")
    void shouldUseDefaultTimeout_whenConfigurationIsInvalid() {
        assertThat(CaseManagementEntry2Action.parseNoteLockTimeoutMillis(null))
                .isEqualTo(CaseManagementEntry2Action.DEFAULT_NOTE_LOCK_TIMEOUT_MILLIS);
        assertThat(CaseManagementEntry2Action.parseNoteLockTimeoutMillis("not-a-number"))
                .isEqualTo(CaseManagementEntry2Action.DEFAULT_NOTE_LOCK_TIMEOUT_MILLIS);
        assertThat(CaseManagementEntry2Action.parseNoteLockTimeoutMillis("0"))
                .isEqualTo(CaseManagementEntry2Action.DEFAULT_NOTE_LOCK_TIMEOUT_MILLIS);
        assertThat(CaseManagementEntry2Action.parseNoteLockTimeoutMillis("1441"))
                .isEqualTo(CaseManagementEntry2Action.DEFAULT_NOTE_LOCK_TIMEOUT_MILLIS);
        assertThat(CaseManagementEntry2Action.parseNoteLockTimeoutMillis("15"))
                .isEqualTo(Duration.ofMinutes(15).toMillis());
    }

    @Test
    @DisplayName("should allow takeover only when the provider owns the lock")
    void shouldAllowTakeover_onlyForOwningProvider() {
        CasemgmtNoteLock lock = noteLock(15L, "provider-a", "session-a", NOW);

        assertThat(CaseManagementEntry2Action.canTransferNoteLock(lock, "provider-a")).isTrue();
        assertThat(CaseManagementEntry2Action.canTransferNoteLock(lock, "provider-b")).isFalse();
        assertThat(CaseManagementEntry2Action.canTransferNoteLock(null, "provider-a")).isFalse();
    }

    @Test
    @DisplayName("should transfer an active lock owned by the same provider")
    void shouldTransferActiveLock_whenProviderOwnsLease() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock requestedLock = noteLock(20L, "provider-a", "session-a",
                new Date(NOW.getTime() - Duration.ofMinutes(1).toMillis()));
        CasemgmtNoteLock databaseLock = noteLock(20L, "provider-a", "session-a",
                requestedLock.getLockAcquired());
        when(noteLockDao.find(20L)).thenReturn(databaseLock);

        CasemgmtNoteLock transferred = CaseManagementEntry2Action.transferNoteLock(noteLockDao,
                requestedLock, "provider-a", "127.0.0.2", "session-b", NOW, FIVE_MINUTES);

        assertThat(transferred).isSameAs(databaseLock);
        assertThat(transferred.getIpAddress()).isEqualTo("127.0.0.2");
        assertThat(transferred.getSessionId()).isEqualTo("session-b");
        assertThat(transferred.getLockAcquired()).isEqualTo(NOW);
        verify(noteLockDao).merge(databaseLock);
    }

    @Test
    @DisplayName("should reject takeover after the inactivity timeout")
    void shouldRejectTakeover_afterInactivityTimeout() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock requestedLock = noteLock(21L, "provider-a", "session-a",
                new Date(NOW.getTime() - FIVE_MINUTES));
        CasemgmtNoteLock databaseLock = noteLock(21L, "provider-a", "session-a",
                requestedLock.getLockAcquired());
        when(noteLockDao.find(21L)).thenReturn(databaseLock);

        CasemgmtNoteLock transferred = CaseManagementEntry2Action.transferNoteLock(noteLockDao,
                requestedLock, "provider-a", "127.0.0.2", "session-b", NOW, FIVE_MINUTES);

        assertThat(transferred).isNull();
        assertThat(databaseLock.getSessionId()).isEqualTo("session-a");
        verify(noteLockDao, never()).merge(databaseLock);
    }

    @Test
    @DisplayName("should renew an owned lock after the heartbeat interval")
    void shouldRenewLock_whenHeartbeatIntervalElapsed() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock sessionLock = noteLock(16L, "provider-a", "session-a",
                new Date(NOW.getTime() - Duration.ofMinutes(1).toMillis()));
        CasemgmtNoteLock databaseLock = noteLock(16L, "provider-a", "session-a",
                sessionLock.getLockAcquired());
        when(noteLockDao.find(16L)).thenReturn(databaseLock);

        boolean renewed = CaseManagementEntry2Action.renewNoteLock(noteLockDao,
                sessionLock, "session-a", NOW, FIVE_MINUTES);

        assertThat(renewed).isTrue();
        assertThat(databaseLock.getLockAcquired()).isEqualTo(NOW);
        assertThat(sessionLock.getLockAcquired()).isEqualTo(NOW);
        verify(noteLockDao).merge(databaseLock);
    }

    @Test
    @DisplayName("should reject renewal after another session takes the lock")
    void shouldRejectRenewal_whenSessionLostLock() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock sessionLock = noteLock(17L, "provider-a", "session-a",
                new Date(NOW.getTime() - Duration.ofMinutes(1).toMillis()));
        CasemgmtNoteLock databaseLock = noteLock(17L, "provider-a", "session-b", NOW);
        when(noteLockDao.find(17L)).thenReturn(databaseLock);

        boolean renewed = CaseManagementEntry2Action.renewNoteLock(noteLockDao,
                sessionLock, "session-a", NOW, FIVE_MINUTES);

        assertThat(renewed).isFalse();
        verify(noteLockDao, never()).merge(databaseLock);
    }

    @Test
    @DisplayName("should reject renewal after the inactivity timeout")
    void shouldRejectRenewal_afterInactivityTimeout() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock sessionLock = noteLock(18L, "provider-a", "session-a",
                new Date(NOW.getTime() - FIVE_MINUTES));
        CasemgmtNoteLock databaseLock = noteLock(18L, "provider-a", "session-a",
                sessionLock.getLockAcquired());
        when(noteLockDao.find(18L)).thenReturn(databaseLock);

        boolean renewed = CaseManagementEntry2Action.renewNoteLock(noteLockDao,
                sessionLock, "session-a", NOW, FIVE_MINUTES);

        assertThat(renewed).isFalse();
        assertThat(databaseLock.getLockAcquired()).isNotEqualTo(NOW);
        verify(noteLockDao, never()).merge(databaseLock);
    }

    @Test
    @DisplayName("should reject renewal of a legacy lock without activity timestamp")
    void shouldRejectRenewal_whenActivityTimestampIsMissing() {
        CasemgmtNoteLockDao noteLockDao = mock(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock sessionLock = noteLock(19L, "provider-a", "session-a", null);
        CasemgmtNoteLock databaseLock = noteLock(19L, "provider-a", "session-a", null);
        when(noteLockDao.find(19L)).thenReturn(databaseLock);

        boolean renewed = CaseManagementEntry2Action.renewNoteLock(noteLockDao,
                sessionLock, "session-a", NOW, FIVE_MINUTES);

        assertThat(renewed).isFalse();
        verify(noteLockDao, never()).merge(databaseLock);
    }

    private static CasemgmtNoteLock noteLock(Long id, String providerNo, String sessionId,
            Date lastActivity) {
        CasemgmtNoteLock lock = new CasemgmtNoteLock();
        lock.setId(id);
        lock.setDemographicNo(1);
        lock.setNoteId(42L);
        lock.setProviderNo(providerNo);
        lock.setSessionId(sessionId);
        lock.setLockAcquired(lastActivity);
        return lock;
    }
}
