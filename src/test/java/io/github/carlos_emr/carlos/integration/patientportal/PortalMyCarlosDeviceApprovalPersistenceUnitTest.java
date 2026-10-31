/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Jdbc;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Refusal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** JDBC ownership/mapping unit checks only; no driver, database schema or durable proof. */
class PortalMyCarlosDeviceApprovalPersistenceUnitTest {
    @Test void allFourDaosRefuseBeforeAnyPersistenceAccessOutsideTheApprovalOwner() {
        var factory = mock(EntityManagerFactory.class);
        var binding = new PortalMyCarlosPatientBindingDao(factory);
        var challenge = new PortalMyCarlosDeviceApprovalChallengeDao(factory);
        var device = new PortalMyCarlosAcceptedDeviceDao(factory);
        var outbox = new PortalMyCarlosDeviceApprovalOutboxDao(factory);
        var id = new PortalMyCarlosDeviceApprovalTypes.Bytes(new byte[32]);
        List<Executable> calls = List.of(
                () -> binding.findByClinicPatientForUpdate("FAKE", 1), () -> binding.findByClinicRecipientForUpdate("FAKE", "recipient"), () -> binding.insert(null),
                () -> challenge.findByChallengeForUpdate(id), () -> challenge.findByRequestForUpdate("FAKE", UUID.randomUUID()),
                () -> challenge.findByConsumedJtiForUpdate(1, UUID.randomUUID()), () -> challenge.findOpenForPatientForUpdate("FAKE", 1),
                () -> challenge.insert(null), () -> challenge.terminal(null, null),
                () -> device.findByKidForUpdate("FAKE"), () -> device.historyForUpdate(1, 0, 20), () -> device.insert(null), () -> device.markRemoved(null, null),
                () -> outbox.findMessageForUpdate(id, "decision"), () -> outbox.insert(null));
        for (var call : calls) assertThrows(IllegalStateException.class, call);
        verifyNoInteractions(factory);
    }
    private static void connection(Fixture f, Session session, Connection connection) {
        Runnable prior = f.owner.manager.onBegin;
        f.owner.manager.onBegin = () -> {
            prior.run();
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(f.owner.factory);
            when(holder.getEntityManager().unwrap(Session.class)).thenReturn(session);
        };
        when(session.doReturningWork(any())).thenAnswer(a -> {
            try { return ((ReturningWork<?>) a.getArgument(0)).execute(connection);
            }
            catch (SQLException failure) { throw new org.hibernate.HibernateException("FAKE JDBC translation", failure);
            }
        });
    }
    @Test void everyDaoUsesTheSameOwnedSessionConnectionWithoutClosingOrReconfiguringIt() throws Exception {
        var f = new Fixture();
        var session = mock(Session.class);
        var c = mock(Connection.class);
        var q = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(c.prepareStatement(anyString())).thenReturn(q);
        when(q.executeQuery()).thenReturn(rows);
        connection(f, session, c);
        var binding = new PortalMyCarlosPatientBindingDao(f.owner.factory);
        var challenge = new PortalMyCarlosDeviceApprovalChallengeDao(f.owner.factory);
        var device = new PortalMyCarlosAcceptedDeviceDao(f.owner.factory);
        var outbox = new PortalMyCarlosDeviceApprovalOutboxDao(f.owner.factory);
        f.owner.transactions.executeApprovalOnly(() -> {
            assertTrue(binding.findByClinicPatientForUpdate("FAKE", 1).isEmpty());
            assertTrue(challenge.findByChallengeForUpdate(new PortalMyCarlosDeviceApprovalTypes.Bytes(new byte[32])).isEmpty());
            assertTrue(device.findByKidForUpdate("FAKE").isEmpty());
            assertTrue(outbox.findMessageForUpdate(new PortalMyCarlosDeviceApprovalTypes.Bytes(new byte[32]), "decision").isEmpty());
            return null;
        });
        verify(session, times(4)).doReturningWork(any());
        verify(c, never()).close();
        verify(c, never()).commit();
        verify(c, never()).rollback();
        verify(c, never()).setAutoCommit(anyBoolean());
        verify(c, never()).setTransactionIsolation(anyInt());
        verify(c, never()).setReadOnly(anyBoolean());
    }
    @Test void aSecondBoundEntityManagerIsRefusedBeforeJdbc() throws Exception {
        var f = new Fixture();
        var dao = new PortalMyCarlosPatientBindingDao(f.owner.factory);
        var otherFactory = mock(EntityManagerFactory.class);
        var otherManager = mock(EntityManager.class);
        var extra = new EntityManagerHolder(otherManager);
        extra.setSynchronizedWithTransaction(true);
        f.owner.transactions.executeApprovalOnly(() -> {
            TransactionSynchronizationManager.bindResource(otherFactory, extra);
            try { assertThrows(IllegalStateException.class, () -> dao.findByClinicPatientForUpdate("FAKE", 1));
            }
            finally { TransactionSynchronizationManager.unbindResource(otherFactory);
            }
            return null;
        });
        verifyNoInteractions(otherManager);
    }
    @Test void utcTimestampMappingKeepsMicrosecondsAndHandlesSqlNullWithoutGlobalTimezoneChanges() throws Exception {
        var q = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        Instant instant = Instant.parse("2026-10-23T12:34:56.123456Z");
        Jdbc.timestamp(q, 1, instant);
        Jdbc.timestamp(q, 2, null);
        var utc = ArgumentCaptor.forClass(Calendar.class);
        verify(q).setTimestamp(eq(1), eq(Timestamp.from(instant)), utc.capture());
        assertEquals("UTC", utc.getValue().getTimeZone().getID());
        when(rows.getTimestamp(eq("created_at"), any(Calendar.class))).thenAnswer(a -> {
            assertEquals("UTC", ((Calendar) a.getArgument(1)).getTimeZone().getID());
            return Timestamp.from(instant);
        });
        assertEquals(instant, Jdbc.instant(rows, "created_at"));
        assertNull(Jdbc.instant(rows, "removed_at"));
        verify(q).setTimestamp(eq(2), isNull(), any(Calendar.class));
    }
    @Test void binaryUuidAndBytesMappingRetainExactIdentityAndDefensiveCopies() {
        UUID id = UUID.fromString("87a38d20-594e-4c16-861a-49a79a1e58bb");
        byte[] encoded = Jdbc.uuid(id);
        assertEquals(id, Jdbc.uuid(encoded));
        var copy = Jdbc.bytes(encoded);
        encoded[0] ^= 1;
        assertNotEquals(Jdbc.uuid(encoded), Jdbc.uuid(copy.copy()));
        byte[] exposed = Jdbc.raw(copy);
        exposed[1] ^= 1;
        assertNotEquals(Jdbc.uuid(exposed), Jdbc.uuid(copy.copy()));
        assertThrows(IllegalArgumentException.class, () -> Jdbc.uuid(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> Jdbc.uuid(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> Jdbc.ascii(new byte[] {(byte) 0xc0}));
    }
    @Test void removalCasCountZeroFailsClosedAndDoesNotCommitItsUnitOwner() throws Exception {
        var f = new Fixture();
        var root = f.install(f.first, false);
        var accepted = f.acceptFirst(root).device();
        int committedBefore = f.owner.manager.commits;
        var session = mock(Session.class);
        var c = mock(Connection.class);
        var q = mock(PreparedStatement.class);
        when(c.prepareStatement(anyString())).thenReturn(q);
        when(q.executeUpdate()).thenReturn(0);
        connection(f, session, c);
        var dao = new PortalMyCarlosAcceptedDeviceDao(f.owner.factory);
        var removed = accepted.removed(UUID.fromString("46f7bf9a-36b4-44dc-a9af-1ca7f0d16850"), "123", f.clock.value);
        assertEquals("approval_conflict", assertThrows(Refusal.class, () -> f.owner.transactions.executeApprovalOnly(() -> {
            dao.markRemoved(accepted, removed);
            return null;
        })).code);
        assertEquals(committedBefore, f.owner.manager.commits);
        verify(c, never()).commit();
    }
    @Test void realDaoLeavesSqlCauseAvailableToTheExistingWholeOwnerClassifier() throws Exception {
        var f = new Fixture();
        var session = mock(Session.class);
        var c = mock(Connection.class);
        var q = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(c.prepareStatement(anyString())).thenReturn(q);
        when(q.executeQuery()).thenThrow(new SQLException("FAKE transient unit fault", "HY000", 1020)).thenReturn(rows);
        connection(f, session, c);
        var dao = new PortalMyCarlosPatientBindingDao(f.owner.factory);
        assertTrue(f.owner.transactions.executeApprovalOnly(() -> dao.findByClinicPatientForUpdate("FAKE", 1)).isEmpty());
        assertEquals(2, f.owner.manager.begins);
        assertEquals(1, f.owner.manager.rollbacks);
        assertEquals(1, f.owner.manager.commits);
    }
}
