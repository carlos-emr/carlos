/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.QueryTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A statement's deadline is a wait that is sent again, never a failure of the export: the same
 * statement on the same connection with twice the wait, until it is answered or the attempt
 * itself ends.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportStatementWaitReadSupportUnitTest {
    private Runnable restore = () -> {};

    @AfterEach
    void restoreThread() {
        restore.run();
        Thread.interrupted();
    }

    private static SQLTimeoutException ended() {
        return new SQLTimeoutException("ended at its deadline", "70100", 1969);
    }

    @Test
    void shouldSendTheStatementAgainWithTwiceTheWait_whenTheServerEndsItAtItsDeadline() {
        var waits = new ArrayList<Integer>();
        String answer =
                CdsExportStatementWaitReadSupport.await(
                        null,
                        null,
                        30,
                        seconds -> {
                            waits.add(seconds);
                            if (waits.size() < 6) throw ended();
                            return "answered";
                        });
        // No number of unanswered waits fails the read.
        assertThat(answer).isEqualTo("answered");
        assertThat(waits).containsExactly(30, 60, 120, 240, 480, 960);
    }

    @Test
    void shouldRecogniseTheServersDeadline_byItsErrorCodeWhateverTheDriverThrows() {
        var waits = new ArrayList<Integer>();
        CdsExportStatementWaitReadSupport.await(
                null,
                null,
                1,
                seconds -> {
                    waits.add(seconds);
                    if (waits.size() == 1)
                        throw new IllegalStateException(
                                new SQLException("interrupted", "70100", 1969));
                    return null;
                });
        assertThat(waits).containsExactly(1, 2);
    }

    @Test
    void shouldTellTheObserverOfEachUnansweredWait_andKeepOneWaitWithinItsWindow() {
        var told = new ArrayList<Long>();
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        waited -> {
                            told.add(waited);
                            return 5_000;
                        });
        var waits = new ArrayList<Integer>();
        CdsExportStatementWaitReadSupport.await(
                null,
                null,
                1,
                seconds -> {
                    waits.add(seconds);
                    if (waits.size() < 6) throw ended();
                    return null;
                });
        // Doubling stops at the observer's window (5 s), and the waits go on.
        assertThat(waits).containsExactly(1, 2, 4, 5, 5, 5);
        assertThat(told).containsExactly(1_000L, 3_000L, 7_000L, 12_000L, 17_000L);
    }

    @Test
    void shouldEndTheWait_whenTheObserverSaysTheAttemptHasEnded() {
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        waited -> {
                            throw new IllegalStateException("CDS_EXPORT_OWNER_FENCED");
                        });
        var sent = new int[1];
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.await(
                                        null,
                                        null,
                                        1,
                                        seconds -> {
                                            sent[0]++;
                                            throw ended();
                                        }))
                .hasMessage("CDS_EXPORT_OWNER_FENCED");
        assertThat(sent[0]).isEqualTo(1);
    }

    @Test
    void shouldEndTheWait_whenTheThreadWasInterrupted() {
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.await(
                                        null,
                                        null,
                                        1,
                                        seconds -> {
                                            Thread.currentThread().interrupt();
                                            throw ended();
                                        }))
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void shouldPassEveryOtherFailureOn_withoutSendingTheStatementAgain() {
        var sent = new int[1];
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.await(
                                        null,
                                        null,
                                        1,
                                        seconds -> {
                                            sent[0]++;
                                            throw new IllegalStateException("SOURCE_INVALID");
                                        }))
                .hasMessage("SOURCE_INVALID");
        // A deadline of the surrounding transaction is its owner's, not a statement's wait.
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.await(
                                        null,
                                        null,
                                        1,
                                        seconds -> {
                                            sent[0]++;
                                            throw new org.springframework.transaction
                                                    .TransactionTimedOutException(
                                                    "deadline", ended());
                                        }))
                .isInstanceOf(org.springframework.transaction.TransactionTimedOutException.class);
        // A lost connection is not a wait either: the capture reads the chart again.
        var lost = new java.sql.SQLNonTransientConnectionException("gone", "08000");
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.awaitOwned(
                                        mock(Connection.class),
                                        seconds -> {
                                            sent[0]++;
                                            throw lost;
                                        }))
                .isSameAs(lost);
        assertThat(sent[0]).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMakeAQuerysReadsWait_andKeepTheCallerOnTheWaitingQuery() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(query.setParameter(eq("patient"), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList())
                .thenThrow(new QueryTimeoutException("ended", ended()))
                .thenThrow(new QueryTimeoutException("ended", ended()))
                .thenReturn(List.of("row"));
        var waiting = CdsExportStatementWaitReadSupport.waiting(em, query, 30);

        // A fluent setter returns the waiting query, not the one underneath.
        assertThat(waiting.setParameter("patient", 27).setMaxResults(32)).isSameAs(waiting);
        assertThat((List<Object>) waiting.getResultList()).containsExactly("row");

        var order = inOrder(query);
        order.verify(query).setHint("org.hibernate.timeout", 30);
        order.verify(query).getResultList();
        order.verify(query).setHint("org.hibernate.timeout", 60);
        order.verify(query).getResultList();
        order.verify(query).setHint("org.hibernate.timeout", 120);
        order.verify(query).getResultList();
    }

    @Test
    void shouldPassAQuerysOwnFailureOn_asTheQueryThrewIt() {
        var query = mock(Query.class);
        var refused = new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID");
        when(query.getSingleResult()).thenThrow(refused);
        var waiting = CdsExportStatementWaitReadSupport.waiting(mock(EntityManager.class), query);
        assertThatThrownBy(waiting::getSingleResult).isSameAs(refused);
    }

    @Test
    void shouldRaiseAnOwnedConnectionsSocketTimeout_onlyWhenAWaitWouldOutlastIt()
            throws Exception {
        var connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(60_000);
        var waits = new ArrayList<Integer>();
        CdsExportStatementWaitReadSupport.awaitOwned(
                connection,
                seconds -> {
                    waits.add(seconds);
                    if (waits.size() < 2) throw ended();
                    return null;
                });
        assertThat(waits).containsExactly(30, 60);
        // 30 s and its 30 s of grace fit the 60 s socket timeout; 60 s does not.
        verify(connection, never()).setNetworkTimeout(any(), eq(60_000));
        verify(connection).setNetworkTimeout(any(), eq(90_000));
    }

    @Test
    void shouldRaiseAPooledConnectionsSocketTimeoutForItsTransaction_andPutItBackBeforeItEnds()
            throws Exception {
        var connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(60_000);
        var session = mock(org.hibernate.Session.class);
        org.mockito.Mockito.doAnswer(
                        call -> {
                            call.<org.hibernate.jdbc.Work>getArgument(0).execute(connection);
                            return null;
                        })
                .when(session)
                .doWork(any());
        var em = mock(EntityManager.class);
        when(em.unwrap(org.hibernate.Session.class)).thenReturn(session);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            // 30 s and its grace fit the pool's 60 s: nothing is changed for a first wait.
            CdsExportStatementWaitReadSupport.await(em, null, 30, seconds -> "answered");
            verify(connection, never()).setNetworkTimeout(any(), anyInt());
            // A wait of 60 s would be cut short by the socket: raised for this transaction.
            var waits = new ArrayList<Integer>();
            CdsExportStatementWaitReadSupport.await(
                    em,
                    null,
                    30,
                    seconds -> {
                        waits.add(seconds);
                        if (waits.size() < 2) throw ended();
                        return "answered";
                    });
            verify(connection).setNetworkTimeout(any(), eq(90_000));
            var synchronizations =
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            // Before the connection goes back to its pool it has the pool's timeout again.
            synchronizations.getFirst().beforeCompletion();
            verify(connection).setNetworkTimeout(any(), eq(60_000));
            synchronizations.getFirst().afterCompletion(0);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldGiveAConnectionWithoutASocketTimeoutNone_howeverLongItWaits() throws Exception {
        var connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(0);
        var waits = new int[1];
        CdsExportStatementWaitReadSupport.awaitOwned(
                connection,
                seconds -> {
                    if (++waits[0] < 4) throw ended();
                    return null;
                });
        verify(connection, never()).setNetworkTimeout(any(), anyInt());
    }
}
