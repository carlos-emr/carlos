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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One pooled MariaDB connection as a unit of {@link CdsExportStatementWaitReadSupport#transaction}
 * sees it, without a database: its socket timeout, its own statement deadline and the count of
 * its statements the server ended at a deadline ({@code Max_statement_time_exceeded}), which a
 * test raises to stand for a statement ended under the unit. Its transaction manager opens one
 * transaction on this connection for each {@code getTransaction} and completes it as Spring does:
 * every synchronization before completion, then after it.
 *
 * @since 2026-11-14
 */
public final class CdsExportStatementWaitTestServer {
    private final Connection connection = mock(Connection.class);
    private final EntityManager em = mock(EntityManager.class);
    private final List<Object> deadlines = new ArrayList<>();
    private final List<String> outcomes = new ArrayList<>();
    private int socket;
    private long ended;
    private double deadline;
    private boolean lost, unrestorable, commitAnswerLost;
    private int transactions, refusalsLeft;

    public CdsExportStatementWaitTestServer(int socketMillis) throws SQLException {
        socket = socketMillis;
        when(connection.getNetworkTimeout()).thenAnswer(call -> socket);
        doAnswer(
                        call -> {
                            socket = call.getArgument(1);
                            return null;
                        })
                .when(connection)
                .setNetworkTimeout(any(), anyInt());
        when(connection.prepareStatement(CdsExportStatementWaitReadSupport.SEEN))
                .thenAnswer(call -> rows(deadline, ended));
        when(connection.prepareStatement(CdsExportStatementWaitReadSupport.ENDED))
                .thenAnswer(
                        call -> {
                            if (lost)
                                throw new java.sql.SQLNonTransientConnectionException(
                                        "gone", "08000");
                            return rows(ended, 0);
                        });
        when(connection.prepareStatement(CdsExportStatementWaitReadSupport.DEADLINE))
                .thenAnswer(call -> deadlineStatement());
        // An aborted connection is dropped by its pool: the next transaction has a new one, with
        // the server's own deadline.
        doAnswer(
                        call -> {
                            deadline = 0;
                            return null;
                        })
                .when(connection)
                .abort(any());
        var session = mock(org.hibernate.Session.class);
        doAnswer(
                        call -> {
                            call.<org.hibernate.jdbc.Work>getArgument(0).execute(connection);
                            return null;
                        })
                .when(session)
                .doWork(any());
        when(em.unwrap(org.hibernate.Session.class)).thenReturn(session);
    }

    /** The connection. */
    public Connection connection() {
        return connection;
    }

    /** An entity manager whose session lends the connection. */
    public EntityManager em() {
        return em;
    }

    /** The server ends one more statement of the connection at its deadline. */
    public void endStatement() {
        ended++;
    }

    /** From now on the connection is gone: the unit's count of ended statements cannot be read. */
    public void lose() {
        lost = true;
    }

    /** The connection refuses its own deadline back. */
    public void refuseDeadlineBack() {
        unrestorable = true;
    }

    /** The connection refuses its own deadline back once, then takes it. */
    public void refuseDeadlineBackOnce() {
        refusalsLeft = 1;
    }

    /** The next commit is made, and its answer never comes back. */
    public void loseNextCommitAnswer() {
        commitAnswerLost = true;
    }

    /**
     * How each transaction ended, in order: {@code COMMITTED} with the connection's own deadline
     * at that moment, or {@code ROLLED_BACK}.
     */
    public List<String> outcomes() {
        return List.copyOf(outcomes);
    }

    /** Every deadline the connection was given, in order: a unit's, then its own put back. */
    public List<Object> deadlines() {
        return List.copyOf(deadlines);
    }

    /** The connection's own deadline now. */
    public double deadline() {
        return deadline;
    }

    /** The connection's socket timeout now. */
    public int socket() {
        return socket;
    }

    /** How many transactions were opened on the connection. */
    public int transactions() {
        return transactions;
    }

    /** A transaction manager whose every transaction is on this connection. */
    public PlatformTransactionManager manager() {
        return new PlatformTransactionManager() {
            private Object key;

            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                transactions++;
                TransactionSynchronizationManager.initSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(
                        definition != null && definition.isReadOnly());
                key = new Object();
                TransactionSynchronizationManager.bindResource(key, new EntityManagerHolder(em));
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                if (status.isRollbackOnly()) {
                    complete(1);
                    return;
                }
                complete(0);
                if (commitAnswerLost) {
                    commitAnswerLost = false;
                    throw new org.springframework.transaction.TransactionSystemException(
                            "SYNTHETIC_LOST_COMMIT_ANSWER");
                }
            }

            @Override
            public void rollback(TransactionStatus status) {
                complete(1);
            }

            private void complete(int outcome) {
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                synchronizations.forEach(TransactionSynchronization::beforeCompletion);
                // The commit (or rollback) itself, after everything before completion.
                outcomes.add(outcome == 0 ? "COMMITTED@" + deadline : "ROLLED_BACK");
                for (var synchronization : synchronizations)
                    synchronization.afterCompletion(outcome);
                TransactionSynchronizationManager.unbindResource(key);
                TransactionSynchronizationManager.clear();
            }
        };
    }

    private PreparedStatement rows(double first, long second) throws SQLException {
        var statement = mock(PreparedStatement.class);
        var rows = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getDouble(1)).thenReturn(first);
        when(rows.getLong(1)).thenReturn((long) first);
        when(rows.getLong(2)).thenReturn(second);
        return statement;
    }

    private PreparedStatement deadlineStatement() throws SQLException {
        var statement = mock(PreparedStatement.class);
        Object[] value = new Object[1];
        doAnswer(call -> value[0] = call.getArgument(1)).when(statement).setInt(eq(1), anyInt());
        doAnswer(call -> value[0] = call.getArgument(1))
                .when(statement)
                .setDouble(eq(1), anyDouble());
        when(statement.execute())
                .thenAnswer(
                        call -> {
                            if (value[0] instanceof Double
                                    && (unrestorable || refusalsLeft > 0)) {
                                if (refusalsLeft > 0) refusalsLeft--;
                                throw new SQLException("refused", "HY000", 1105);
                            }
                            deadlines.add(value[0]);
                            deadline = ((Number) value[0]).doubleValue();
                            return false;
                        });
        return statement;
    }
}
