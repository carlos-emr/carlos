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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.QueryTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * A statement's deadline is a wait: a read the server ends there is sent again on the same
 * connection with twice the wait, and nothing but the scope's own end stops it.
 */
@Tag("unit")
class CdsImportStatementWaitsUnitTest {
    @AfterEach
    void leaveNoThreadState() {
        Thread.interrupted();
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        for (Object key : new ArrayList<>(TransactionSynchronizationManager.getResourceMap().keySet()))
            TransactionSynchronizationManager.unbindResource(key);
    }

    @Test
    void shouldSendTheReadAgainWithTwiceTheWait_whenTheServerEndsItAtItsDeadline()
            throws Exception {
        List<Integer> waits = new ArrayList<>();
        String answer =
                CdsImportStatementWaits.await(
                        null,
                        mock(Connection.class),
                        seconds -> {
                            waits.add(seconds);
                            if (waits.size() < 4)
                                throw new SQLTimeoutException("synthetic deadline", "70100", 1969);
                            return "answered";
                        });
        assertThat(answer).isEqualTo("answered");
        assertThat(waits).containsExactly(30, 60, 120, 240);
    }

    @Test
    void shouldSendTheReadAgain_whenMariaDbNamesItsStatementTimeoutWithoutTheTimeoutType()
            throws Exception {
        AtomicInteger sent = new AtomicInteger();
        Integer answeredWithin =
                CdsImportStatementWaits.await(
                        null,
                        mock(Connection.class),
                        seconds -> {
                            if (sent.incrementAndGet() == 1)
                                throw new SQLException("max_statement_time", "HY000", 1969);
                            return seconds;
                        });
        assertThat(answeredWithin).isEqualTo(60);
        assertThat(sent).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1205, 1213, 2013})
    void shouldPassTheFailureOnUnsent_whenTheServerEndedTheStatementForAnotherReason(int code) {
        AtomicInteger sent = new AtomicInteger();
        SQLException failure =
                switch (code) {
                    case 1213 -> new SQLTransactionRollbackException("deadlock", "40001", code);
                    case 2013 -> new SQLNonTransientConnectionException("lost", "08S01", code);
                    default -> new SQLException("lock wait timeout", "HY000", code);
                };
        assertThatThrownBy(
                        () ->
                                CdsImportStatementWaits.await(
                                        null,
                                        mock(Connection.class),
                                        seconds -> {
                                            sent.incrementAndGet();
                                            throw failure;
                                        }))
                .isSameAs(failure);
        assertThat(sent).hasValue(1);
    }

    @Test
    void shouldPassTheTransactionsOwnDeadlineOn_withoutSendingAgain() {
        AtomicInteger sent = new AtomicInteger();
        var ended =
                new TransactionTimedOutException(
                        "transaction deadline", new SQLTimeoutException("statement"));
        assertThatThrownBy(
                        () ->
                                CdsImportStatementWaits.query(
                                        null,
                                        seconds -> {
                                            sent.incrementAndGet();
                                            throw ended;
                                        }))
                .isSameAs(ended);
        assertThat(sent).hasValue(1);
    }

    @Test
    void shouldBuildTheQueryAgainForEverySending_whenJpaReportsTheDeadline() {
        var em = mock(EntityManager.class);
        var session = mock(Session.class);
        var transaction = mock(org.hibernate.Transaction.class);
        when(em.unwrap(Session.class)).thenReturn(session);
        when(session.getTransaction()).thenReturn(transaction);
        List<Integer> waits = new ArrayList<>();
        List<String> rows =
                CdsImportStatementWaits.query(
                        em,
                        seconds -> {
                            waits.add(seconds);
                            if (waits.size() == 1)
                                throw new QueryTimeoutException(
                                        "synthetic", new SQLTimeoutException("deadline"), null);
                            return List.of("run");
                        });
        assertThat(rows).containsExactly("run");
        assertThat(waits).containsExactly(30, 60);
        assertThat(CdsImportStatementWaits.hintMillis(60)).isEqualTo(60_000);
    }

    @Test
    void shouldPassTheDeadlineOn_whenTheTransactionCanNoLongerCommit() {
        var em = mock(EntityManager.class);
        var session = mock(Session.class);
        var transaction = mock(org.hibernate.Transaction.class);
        when(em.unwrap(Session.class)).thenReturn(session);
        when(session.getTransaction()).thenReturn(transaction);
        when(transaction.getRollbackOnly()).thenReturn(true);
        var deadline = new QueryTimeoutException("synthetic", new SQLTimeoutException(), null);
        AtomicInteger sent = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                CdsImportStatementWaits.query(
                                        em,
                                        seconds -> {
                                            sent.incrementAndGet();
                                            throw deadline;
                                        }))
                .isSameAs(deadline);
        assertThat(sent).hasValue(1);
    }

    @Test
    void shouldEndTheWait_whenTheScopesStallWindowHasPassed() {
        List<Long> told = new ArrayList<>();
        AtomicInteger sent = new AtomicInteger();
        try (var bound =
                CdsImportStatementWaits.observe(
                        waited -> {
                            told.add(waited);
                            if (told.size() == 2)
                                throw new CdsImportException("IMPORT_WORKER_STALLED");
                            return 0;
                        })) {
            assertThatThrownBy(
                            () ->
                                    CdsImportStatementWaits.await(
                                            null,
                                            mock(Connection.class),
                                            seconds -> {
                                                sent.incrementAndGet();
                                                throw new SQLTimeoutException("deadline");
                                            }))
                    .isInstanceOfSatisfying(
                            CdsImportException.class,
                            stopped -> assertThat(stopped.getCode()).isEqualTo("IMPORT_WORKER_STALLED"));
            bound.close();
        }
        // A wait is told how long it has waited, never counted as progress.
        assertThat(told).containsExactly(30_000L, 90_000L);
        assertThat(sent).hasValue(2);
    }

    @Test
    void shouldBoundEveryLaterWait_byTheObserversWindow() throws Exception {
        List<Integer> waits = new ArrayList<>();
        try (var bound = CdsImportStatementWaits.observe(waited -> 45_000)) {
            CdsImportStatementWaits.await(
                    null,
                    mock(Connection.class),
                    seconds -> {
                        waits.add(seconds);
                        if (waits.size() < 4) throw new SQLTimeoutException("deadline");
                        return null;
                    });
        }
        assertThat(waits).containsExactly(30, 45, 45, 45);
    }

    @Test
    void shouldRestoreTheEnclosingScopesObserver_whenANestedBindingCloses() throws Exception {
        List<String> scopes = new ArrayList<>();
        try (var outer =
                CdsImportStatementWaits.observe(
                        waited -> {
                            scopes.add("attempt");
                            return 0;
                        })) {
            try (var inner =
                    CdsImportStatementWaits.observe(
                            waited -> {
                                scopes.add("report");
                                return 0;
                            })) {
                timesOutOnce();
                inner.close();
            }
            timesOutOnce();
        }
        timesOutOnce();
        assertThat(scopes).containsExactly("report", "attempt");
    }

    @Test
    void shouldStopWaitingAsCancelled_whenTheThreadIsInterrupted() {
        AtomicInteger sent = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                CdsImportStatementWaits.await(
                                        null,
                                        mock(Connection.class),
                                        seconds -> {
                                            sent.incrementAndGet();
                                            Thread.currentThread().interrupt();
                                            throw new SQLTimeoutException("deadline");
                                        }))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        cancelled -> assertThat(cancelled.getCode()).isEqualTo("IMPORT_CANCELLED"));
        assertThat(sent).hasValue(1);
    }

    @Test
    void shouldRaiseADedicatedConnectionsSocketTimeout_onlyWhileItsStatementWaits()
            throws Exception {
        var socket = new AtomicInteger(60_000);
        Connection owner = connection(socket);
        List<Integer> seenBySend = new ArrayList<>();
        CdsImportStatementWaits.awaitOwned(
                owner,
                seconds -> {
                    seenBySend.add(socket.get());
                    if (seenBySend.size() < 3) throw new SQLTimeoutException("deadline");
                    return null;
                });
        // 30 s fits the 60 s socket with its 30 s grace; 60 s and 120 s do not.
        assertThat(seenBySend).containsExactly(60_000, 90_000, 150_000);
        assertThat(socket).hasValue(60_000);
    }

    @Test
    void shouldRaiseATransactionsSocketTimeout_untilTheTransactionCompletes() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var socket = new AtomicInteger(60_000);
        Connection lent = connection(socket);
        var em = mock(EntityManager.class);
        var session = mock(Session.class);
        when(em.unwrap(Session.class)).thenReturn(session);
        doAnswer(
                        call -> {
                            ((Work) call.getArgument(0)).execute(lent);
                            return null;
                        })
                .when(session)
                .doWork(any());
        AtomicInteger sent = new AtomicInteger();
        CdsImportStatementWaits.await(
                em,
                lent,
                seconds -> {
                    if (sent.incrementAndGet() == 1) throw new SQLTimeoutException("deadline");
                    return null;
                });
        // The pooled connection keeps the raised timeout for the rest of its transaction...
        assertThat(socket).hasValue(90_000);
        TransactionSynchronizationUtils.triggerBeforeCompletion();
        // ...and goes back to its pool with the timeout it came with.
        assertThat(socket).hasValue(60_000);
    }

    @Test
    void shouldLeaveTheSocketTimeoutAlone_whenNoTransactionCanPutItBack() throws Exception {
        var socket = new AtomicInteger(60_000);
        Connection borrowed = connection(socket);
        AtomicInteger sent = new AtomicInteger();
        CdsImportStatementWaits.await(
                null,
                borrowed,
                seconds -> {
                    if (sent.incrementAndGet() < 3) throw new SQLTimeoutException("deadline");
                    return null;
                });
        verify(borrowed, never()).setNetworkTimeout(any(), anyInt());
        assertThat(sent).hasValue(3);
    }

    private static void timesOutOnce() throws SQLException {
        AtomicInteger sent = new AtomicInteger();
        CdsImportStatementWaits.await(
                null,
                mock(Connection.class),
                seconds -> {
                    if (sent.incrementAndGet() == 1) throw new SQLTimeoutException("deadline");
                    return null;
                });
    }

    /** A connection whose socket timeout is the given counter. */
    private static Connection connection(AtomicInteger socket) throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenAnswer(call -> socket.get());
        doAnswer(
                        call -> {
                            socket.set(call.getArgument(1));
                            return null;
                        })
                .when(connection)
                .setNetworkTimeout(any(), anyInt());
        return connection;
    }
}
