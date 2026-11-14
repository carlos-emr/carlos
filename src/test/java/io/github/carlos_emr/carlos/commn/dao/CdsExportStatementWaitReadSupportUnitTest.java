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
 * itself ends. Reads shared CARLOS code makes run as a unit, which is read again whole, in a new
 * transaction, when the server ended any of its statements at the unit's deadline.
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

    /** A unit's transactions: one new transaction on the server's connection for each sending. */
    private static org.springframework.transaction.support.TransactionTemplate units(
            CdsExportStatementWaitTestServer server) {
        var units = new org.springframework.transaction.support.TransactionTemplate(server.manager());
        units.setReadOnly(true);
        return units;
    }

    @Test
    void shouldSendTheWholeUnitAgain_whenItsSharedCodeSwallowedAStatementEndedAtTheDeadline()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        String answer =
                CdsExportStatementWaitReadSupport.transaction(
                        units(server),
                        status -> {
                            CdsExportStatementWaitReadSupport.arm();
                            // The shared privilege check: the server ends its statement at the
                            // deadline once, and the check answers that as a refusal.
                            if (server.transactions() == 1) {
                                server.endStatement();
                                return "refused";
                            }
                            return "granted";
                        });
        // The refusal was the deadline's, not the chart's: the unit was read again.
        assertThat(answer).isEqualTo("granted");
        assertThat(server.transactions()).isEqualTo(2);
        // 30 s, then twice that, each put back to the connection's own deadline before the
        // connection could go back to its pool.
        assertThat(server.deadlines()).containsExactly(30, 0.0d, 60, 0.0d);
        assertThat(server.deadline()).isZero();
        // 30 s and its grace fit the 60 s socket timeout; 60 s was given 90 s, then put back.
        verify(server.connection()).setNetworkTimeout(any(), eq(90_000));
        assertThat(server.socket()).isEqualTo(60_000);
    }

    @Test
    void shouldSendTheUnitAgain_whenTheEndedStatementFailedTheWork() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var answer =
                CdsExportStatementWaitReadSupport.transaction(
                        units(server),
                        1,
                        status -> {
                            CdsExportStatementWaitReadSupport.arm();
                            if (server.transactions() < 3) {
                                server.endStatement();
                                throw new IllegalStateException("provider lookup ended", ended());
                            }
                            return "provider";
                        });
        assertThat(answer).isEqualTo("provider");
        assertThat(server.deadlines()).containsExactly(1, 0.0d, 2, 0.0d, 4, 0.0d);
    }

    @Test
    void shouldReadTheUnitOnce_whenItsOwnWaitingReadsSentTheirEndedStatementAgain()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var sent = new int[1];
        CdsExportStatementWaitReadSupport.transaction(
                units(server),
                status -> {
                    CdsExportStatementWaitReadSupport.arm();
                    // A read through the wait support: ended once, sent again, answered.
                    return CdsExportStatementWaitReadSupport.await(
                            server.em(),
                            null,
                            30,
                            seconds -> {
                                if (++sent[0] == 1) {
                                    server.endStatement();
                                    throw ended();
                                }
                                return "row";
                            });
                });
        assertThat(server.transactions()).isEqualTo(1);
        assertThat(sent[0]).isEqualTo(2);
    }

    @Test
    void shouldPassTheWorksOwnFailureOn_whenNoStatementWasEnded() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var refused = new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED");
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.transaction(
                                        units(server),
                                        status -> {
                                            CdsExportStatementWaitReadSupport.arm();
                                            throw refused;
                                        }))
                .isSameAs(refused);
        assertThat(server.transactions()).isEqualTo(1);
        assertThat(server.deadline()).isZero();
    }

    @Test
    void shouldReportTheDatabase_whenTheConnectionIsLostWhileTheUnitIsArmed() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.transaction(
                                        units(server),
                                        status -> {
                                            CdsExportStatementWaitReadSupport.arm();
                                            server.lose();
                                            // What the shared check makes of the lost read.
                                            throw new SecurityException(
                                                    "CDS_EXPORT_AUTHORIZATION_DENIED");
                                        }))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportDatabaseUnavailableException.class)
                .hasMessage("CDS_EXPORT_DATABASE_UNAVAILABLE");
        assertThat(server.transactions()).isEqualTo(1);
    }

    @Test
    void shouldAbortTheConnection_whenItsOwnDeadlineCannotBePutBack() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        server.refuseDeadlineBack();
        String answer =
                CdsExportStatementWaitReadSupport.transaction(
                        units(server),
                        status -> {
                            CdsExportStatementWaitReadSupport.arm();
                            return "answered";
                        });
        assertThat(answer).isEqualTo("answered");
        // It never goes back to its pool with the unit's deadline.
        verify(server.connection()).abort(any());
    }

    @Test
    void shouldArmNothing_onAConnectionWithoutASocketTimeoutOrOutsideAUnit() throws Exception {
        var server = new CdsExportStatementWaitTestServer(0);
        CdsExportStatementWaitReadSupport.transaction(
                units(server),
                status -> {
                    CdsExportStatementWaitReadSupport.arm();
                    return null;
                });
        // Outside a unit an arm changes nothing either.
        CdsExportStatementWaitReadSupport.arm();
        verify(server.connection(), never()).prepareStatement(any(String.class));
        verify(server.connection(), never()).setNetworkTimeout(any(), anyInt());
    }

    @Test
    void shouldEndTheUnitsWaiting_whenTheObserverSaysTheAttemptHasEnded() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        waited -> {
                            throw new IllegalStateException("CDS_EXPORT_OWNER_FENCED");
                        });
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.transaction(
                                        units(server),
                                        status -> {
                                            CdsExportStatementWaitReadSupport.arm();
                                            server.endStatement();
                                            return "refused";
                                        }))
                .hasMessage("CDS_EXPORT_OWNER_FENCED");
        assertThat(server.transactions()).isEqualTo(1);
        assertThat(server.deadline()).isZero();
    }

    /** A write unit's transactions: one new read-write transaction for each sending. */
    private static org.springframework.transaction.support.TransactionTemplate writes(
            CdsExportStatementWaitTestServer server) {
        return new org.springframework.transaction.support.TransactionTemplate(server.manager());
    }

    @Test
    void shouldWriteTheUnitAgainWhole_whenTheServerEndedOneOfItsStatementsBeforeItsCommit()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        String answer =
                CdsExportStatementWaitReadSupport.writing(
                        writes(server),
                        status -> {
                            // The lease row's lock, waited for past the deadline once.
                            if (server.transactions() == 1) {
                                server.endStatement();
                                throw new jakarta.persistence.LockTimeoutException(
                                        "ended", ended());
                            }
                            return "renewed";
                        });
        assertThat(answer).isEqualTo("renewed");
        // The first sending never reached its commit: it was rolled back, then written again
        // with twice the wait, and committed once, with the connection's own deadline back.
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK", "COMMITTED@0.0");
        assertThat(server.deadlines()).containsExactly(30, 0.0d, 60, 0.0d);
        verify(server.connection()).setNetworkTimeout(any(), eq(90_000));
        assertThat(server.socket()).isEqualTo(60_000);
    }

    @Test
    void shouldNeverCommitASending_whoseEndedStatementTheWorkSwallowed() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var sendings = new int[1];
        CdsExportStatementWaitReadSupport.writing(
                writes(server),
                status -> {
                    // A statement ended at its deadline, and the failure lost on the way.
                    if (++sendings[0] == 1) server.endStatement();
                    return null;
                });
        // Ended by the count, whatever the work made of it: rolled back, never committed.
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK", "COMMITTED@0.0");
        assertThat(sendings[0]).isEqualTo(2);
    }

    @Test
    void shouldSendTheWritesItHoldsBeforeItsCommit_underTheUnitsDeadline() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var deadlinesAtFlush = new ArrayList<Double>();
        org.mockito.Mockito.doAnswer(
                        call -> {
                            deadlinesAtFlush.add(server.deadline());
                            if (deadlinesAtFlush.size() == 1) {
                                // The attempt row's update, ended at the deadline.
                                server.endStatement();
                                throw new jakarta.persistence.PersistenceException(
                                        "ended", ended());
                            }
                            return null;
                        })
                .when(server.em())
                .flush();
        CdsExportStatementWaitReadSupport.writing(writes(server), status -> "recorded");
        // Sent under the unit's deadline, before the commit; ended, rolled back, sent again.
        assertThat(deadlinesAtFlush).containsExactly(30.0d, 60.0d);
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK", "COMMITTED@0.0");
    }

    @Test
    void shouldWriteTheUnitAgain_whenALockWaitRanOutOrADeadlockEndedItsTransaction()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var failures =
                new java.util.ArrayDeque<RuntimeException>(
                        List.of(
                                new jakarta.persistence.PessimisticLockException(
                                        new SQLException(
                                                "Lock wait timeout exceeded", "HY000", 1205)),
                                new IllegalStateException(
                                        new java.sql.SQLTransactionRollbackException(
                                                "Deadlock found", "40001", 1213))));
        String answer =
                CdsExportStatementWaitReadSupport.writing(
                        writes(server),
                        status -> {
                            var failure = failures.poll();
                            if (failure != null) throw failure;
                            return "stopped";
                        });
        assertThat(answer).isEqualTo("stopped");
        assertThat(server.outcomes())
                .containsExactly("ROLLED_BACK", "ROLLED_BACK", "COMMITTED@0.0");
    }

    @Test
    void shouldPassAFenceOrARefusalOn_atOnceWithoutWritingTheUnitAgain() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var fenced =
                new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportOwnerFencedException();
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.writing(
                                        writes(server),
                                        status -> {
                                            throw fenced;
                                        }))
                .isSameAs(fenced);
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK");
        assertThat(server.deadline()).isZero();
    }

    @Test
    void shouldNeverWriteTheUnitAgain_onceItsCommitWasSent() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        server.loseNextCommitAnswer();
        var sendings = new int[1];
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.writing(
                                        writes(server),
                                        status -> ++sendings[0]))
                .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        // The commit's outcome is the caller's to establish (publication reads it back); the
        // unit is never sent a second time, even had the server ended something before.
        assertThat(sendings[0]).isEqualTo(1);
        assertThat(server.outcomes()).containsExactly("COMMITTED@0.0");
    }

    @Test
    void shouldGiveAWriteUnitItsDeadline_evenOnAConnectionWithoutASocketTimeout()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(0);
        CdsExportStatementWaitReadSupport.writing(writes(server), status -> null);
        assertThat(server.deadlines()).containsExactly(30, 0.0d);
        verify(server.connection(), never()).setNetworkTimeout(any(), anyInt());
        assertThat(server.outcomes()).containsExactly("COMMITTED@0.0");
    }

    @Test
    void shouldCutAWriteUnitsWaitsToItsObserversWindow_andEndThemWhenTheWindowIsGone()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        var told = new ArrayList<String>();
        // A renewal's observer: 10 s of lease left before the first sending, 3.5 s after it,
        // then none.
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        new CdsExportStatementWaitReadSupport.Observer() {
                            @Override
                            public long before() {
                                told.add("before");
                                return 10_000;
                            }

                            @Override
                            public long waiting(long waited) {
                                told.add("waited " + waited);
                                if (waited < 13_000) return 3_500;
                                throw new io.github.carlos_emr.carlos.demographic.cdsexport
                                        .support.CdsExportOwnerFencedException();
                            }
                        });
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.writing(
                                        writes(server),
                                        status -> {
                                            server.endStatement();
                                            throw new jakarta.persistence.QueryTimeoutException(
                                                    "ended", ended());
                                        }))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportOwnerFencedException.class);
        // Asked before the first sending too: 10 s of window, then 3.5 s, then none.
        assertThat(told).containsExactly("before", "waited 10000", "waited 13000");
        assertThat(server.deadlines()).containsExactly(10, 0.0d, 3, 0.0d);
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK", "ROLLED_BACK");
    }

    @Test
    void shouldStillWriteAUnit_whoseAttemptEndedBeforeItWaitedAtAll() throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        // The attempt has ended (a fence, a cancel): its observer ends every wait.
        restore =
                CdsExportStatementWaitReadSupport.observe(
                        waited -> {
                            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                                    .CdsExportOwnerFencedException();
                        });
        // The chart audit of the stopped job is not asked about before it is sent, and is
        // written.
        String answer =
                CdsExportStatementWaitReadSupport.writing(writes(server), status -> "audited");
        assertThat(answer).isEqualTo("audited");
        assertThat(server.outcomes()).containsExactly("COMMITTED@0.0");
    }

    @Test
    void shouldReportTheDatabase_whenTheConnectionIsLostBeforeAWriteUnitCommits()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        assertThatThrownBy(
                        () ->
                                CdsExportStatementWaitReadSupport.writing(
                                        writes(server),
                                        status -> {
                                            server.lose();
                                            return "written";
                                        }))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportDatabaseUnavailableException.class);
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK");
    }

    @Test
    void shouldAbortTheConnectionAndWriteTheUnitAgain_whenItsOwnDeadlineCannotBePutBack()
            throws Exception {
        var server = new CdsExportStatementWaitTestServer(60_000);
        server.refuseDeadlineBackOnce();
        CdsExportStatementWaitReadSupport.writing(writes(server), status -> "checkpointed");
        // That connection never reaches its pool, or its commit, with the unit's deadline.
        verify(server.connection()).abort(any());
        assertThat(server.outcomes()).containsExactly("ROLLED_BACK", "COMMITTED@0.0");
    }
}
