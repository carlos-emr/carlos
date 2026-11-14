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

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import org.hibernate.Session;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * How an import read waits for the database: a statement's deadline is a wait, never a budget.
 *
 * <p>A statement sent through this class carries a server-side deadline (the JDBC query timeout,
 * which MariaDB Connector/J sends as {@code max_statement_time}), first {@link
 * #FIRST_WAIT_SECONDS}. When the server ends the statement at that deadline (a busy server, a
 * metadata or row lock held by another session), the same statement is sent again on the same
 * connection, inside the same transaction, with twice the wait, and again, until it is answered.
 * Nothing is refused or failed for how long an answer takes. A wait ends only with what ends its
 * scope: a cancel (the thread is interrupted, {@code IMPORT_CANCELLED}), the scope's stall window
 * through its {@link Observer}, a deadline the surrounding transaction itself carries, and the loss
 * of the connection, which fails the statement's unit loudly under its existing code so that the
 * run can be resumed.
 *
 * <p><b>Why sending it again is sound.</b> Only reads, including locking reads ({@code FOR UPDATE},
 * {@code LOCK IN SHARE MODE}), are sent through this class. A statement the server ended at its own
 * deadline is rolled back by itself: it changed nothing and acquired no lock, and the transaction,
 * with every lock it already holds, is as it was, so the repeated statement reads what the first
 * would have. A statement ended for any other reason is passed on and never repeated: a lock-wait
 * timeout or a deadlock can roll back the whole transaction, and a lost connection loses it.
 *
 * <p><b>Why keep a deadline at all.</b> A live server answers every statement within its deadline,
 * with rows or with the timeout, so silence past the deadline plus {@link #ANSWER_GRACE_SECONDS}
 * means the connection or the server is gone, and the connection's socket timeout can say so. That
 * socket timeout (60 s by default where the CDS network policy applies) is therefore raised to at
 * least that long while a statement waits: for a transaction's connection until the transaction
 * completes, when it is restored before the connection returns to its pool, and for a dedicated
 * connection until the statement is answered. A connection without a socket timeout gets none, and
 * a connection held outside a transaction is never changed, because nothing would put it back.
 *
 * <p>This mirrors the exporter's {@code CdsExportStatementWaitReadSupport}. The import keeps its own
 * copy because its waits answer to the import's stall window and cancel, under import codes.
 *
 * @since 2026-11-14
 */
public final class CdsImportStatementWaits {
    /** The first wait for a statement's answer; every later wait for the same statement doubles. */
    public static final int FIRST_WAIT_SECONDS = 30;

    /** How long past its own deadline a statement may stay unanswered before the socket gives up. */
    static final int ANSWER_GRACE_SECONDS = 30;

    /** MariaDB's own maximum for {@code max_statement_time}, in seconds. */
    static final int SERVER_MAXIMUM_SECONDS = 31_536_000;

    /** The JPA hint that carries a statement's deadline, in milliseconds. */
    public static final String TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /** MariaDB: the statement was ended by its own {@code max_statement_time}. */
    private static final int ER_STATEMENT_TIMEOUT = 1969;

    private static final ThreadLocal<Observer> OBSERVER = new ThreadLocal<>();

    /** Transaction resource key of the socket timeout this transaction raised, if it raised one. */
    private static final Object NETWORK = new Object();

    private CdsImportStatementWaits() {}

    /**
     * The scope's view of a wait. It is told after every wait that ended without an answer, before
     * the statement is sent again. A wait is not progress: an observer must never count it as a
     * checkpoint.
     */
    @FunctionalInterface
    public interface Observer {
        /**
         * @param waitedMillis how long this statement has waited so far
         * @return the longest the next wait may last, in milliseconds, or zero for no bound of the
         *     observer's own
         * @throws RuntimeException to end the wait because the scope itself has ended (its stall
         *     window passed)
         */
        long waiting(long waitedMillis);
    }

    /** One JDBC statement, sent with the given deadline; it may be sent more than once. */
    @FunctionalInterface
    public interface Statement<T> {
        T send(int seconds) throws SQLException;
    }

    /**
     * One JPA query, built afresh and run with the given deadline (set it with {@link
     * #TIMEOUT_HINT} and {@link #hintMillis(int)}); it may be sent more than once.
     */
    @FunctionalInterface
    public interface Query<T> {
        T send(int seconds);
    }

    /** An observer bound to the calling thread; closing it, once or more, restores the previous. */
    public static final class Binding implements AutoCloseable {
        private final Observer previous;
        private boolean closed;

        private Binding(Observer previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) OBSERVER.remove();
            else OBSERVER.set(previous);
        }
    }

    /** Binds a scope's observer to the calling thread until the returned binding is closed. */
    public static Binding observe(Observer observer) {
        var previous = OBSERVER.get();
        OBSERVER.set(Objects.requireNonNull(observer));
        return new Binding(previous);
    }

    /** The {@link #TIMEOUT_HINT} value for a deadline of {@code seconds}. */
    public static int hintMillis(int seconds) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds * 1000L));
    }

    /**
     * A JPA read, in the caller's transaction when it has one. The query is built again for every
     * sending, so it does not matter whether the entity manager outlives one execution.
     */
    public static <T> T query(EntityManager em, Query<T> query) {
        Objects.requireNonNull(query);
        return loop(em, null, false, FIRST_WAIT_SECONDS, query::send);
    }

    /**
     * A JDBC read on the connection the caller's session lent it inside its transaction ({@code em}
     * names that transaction's entity manager), or on a connection the caller merely borrows
     * ({@code em} null), whose socket timeout is then left alone.
     */
    public static <T> T await(EntityManager em, Connection connection, Statement<T> statement)
            throws SQLException {
        Objects.requireNonNull(connection);
        try {
            return loop(em, connection, false, FIRST_WAIT_SECONDS, statement);
        } catch (Unanswered wrapped) {
            throw wrapped.failure;
        }
    }

    /**
     * A JDBC read on a dedicated connection the caller opened for itself and closes itself (the
     * maintenance owner's): its socket timeout is raised while the statement waits and put back
     * once it is answered.
     */
    public static <T> T awaitOwned(Connection connection, Statement<T> statement)
            throws SQLException {
        Objects.requireNonNull(connection);
        try {
            return loop(null, connection, true, FIRST_WAIT_SECONDS, statement);
        } catch (Unanswered wrapped) {
            throw wrapped.failure;
        }
    }

    /** A checked failure of a statement, carried through the shared loop. */
    private static final class Unanswered extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient SQLException failure;

        private Unanswered(SQLException failure) {
            super(null, null, false, false);
            this.failure = failure;
        }
    }

    static <T> T loop(
            EntityManager em,
            Connection connection,
            boolean owned,
            int firstSeconds,
            Statement<T> statement) {
        if (firstSeconds < 1) throw new IllegalArgumentException("CDS_IMPORT_STATEMENT_WAIT_INVALID");
        int original = owned ? networkTimeout(connection) : 0;
        long seconds = firstSeconds, waited = 0;
        try {
            while (true) {
                // A deadline of the surrounding transaction is its owner's: it shortens a wait
                // and, once passed, ends the statement as it always did.
                int wait = (int) Math.max(1, Math.min(seconds, remainingSeconds(seconds)));
                allow(em, connection, owned, wait);
                try {
                    return statement.send(wait);
                } catch (SQLException failed) {
                    if (!expired(failed)) throw new Unanswered(failed);
                } catch (RuntimeException failed) {
                    if (!expired(failed) || rollbackOnly(em)) throw failed;
                }
                waited += wait * 1000L;
                if (Thread.currentThread().isInterrupted())
                    throw new CdsImportException("IMPORT_CANCELLED");
                var observer = OBSERVER.get();
                long bound = observer == null ? 0 : observer.waiting(waited);
                seconds = Math.min(seconds * 2, SERVER_MAXIMUM_SECONDS);
                if (bound > 0) seconds = Math.max(firstSeconds, Math.min(seconds, bound / 1000));
            }
        } finally {
            if (owned) restore(connection, original);
        }
    }

    /** Whether the server ended the statement at its own deadline; the connection is intact. */
    public static boolean expired(Throwable failure) {
        var seen = new HashSet<Throwable>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            // The transaction's own deadline, not the statement's: the transaction is over.
            if (cause instanceof org.springframework.transaction.TransactionTimedOutException)
                return false;
            if (cause instanceof java.sql.SQLTimeoutException) return true;
            if (cause instanceof SQLException sql && sql.getErrorCode() == ER_STATEMENT_TIMEOUT)
                return true;
        }
        return false;
    }

    /** A transaction marked for rollback cannot use an answer: the failure is passed on. */
    private static boolean rollbackOnly(EntityManager em) {
        try {
            var session = em == null ? null : em.unwrap(Session.class);
            return session != null
                    && session.getTransaction() != null
                    && session.getTransaction().getRollbackOnly();
        } catch (RuntimeException unknown) {
            return false;
        }
    }

    private static long remainingSeconds(long wanted) {
        long milliseconds = wanted * 1000L;
        for (var value : TransactionSynchronizationManager.getResourceMap().values()) {
            if (value instanceof ResourceHolderSupport resource && resource.hasTimeout())
                milliseconds = Math.min(milliseconds, resource.getTimeToLiveInMillis());
        }
        return (milliseconds + 999) / 1000;
    }

    /** The socket timeout a transaction found on its connection, and whether it raised it. */
    private static final class Network implements TransactionSynchronization {
        private final EntityManager em;
        private int original;
        private long allowed;
        private boolean raised;

        private Network(EntityManager em) {
            this.em = em;
        }

        @Override
        public void beforeCompletion() {
            if (!raised) return;
            try {
                var session = em.unwrap(Session.class);
                if (session != null)
                    session.doWork(
                            connection -> connection.setNetworkTimeout(Runnable::run, original));
            } catch (RuntimeException gone) {
                // The connection is lost; a lost connection does not go back to its pool.
            }
        }

        // A transaction opened inside this one has its own connection: this transaction's
        // finding must not answer for it.
        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResourceIfPossible(NETWORK);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(NETWORK, this);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(NETWORK);
        }
    }

    /**
     * Before a statement that may wait {@code seconds}: the connection's socket timeout, where it
     * has one, must not end the connection before the server's own deadline has had {@link
     * #ANSWER_GRACE_SECONDS} to answer.
     */
    private static void allow(EntityManager em, Connection connection, boolean owned, int seconds) {
        long needed = (seconds + (long) ANSWER_GRACE_SECONDS) * 1000L;
        if (owned) {
            // A dedicated connection: loop() puts its socket timeout back when the statement ends.
            try {
                int current = connection.getNetworkTimeout();
                if (current > 0 && current < needed)
                    connection.setNetworkTimeout(
                            Runnable::run, (int) Math.min(needed, Integer.MAX_VALUE));
            } catch (SQLException | RuntimeException unsupported) {
                // A driver without a socket timeout has none to raise.
            }
            return;
        }
        // Raised only where the transaction can put it back: a connection that returned to its
        // pool with another socket timeout would change every later user of the pool.
        var state = state(em);
        if (state == null || state.allowed == 0 || state.allowed >= needed) return;
        if (connection != null) {
            // The caller holds the transaction's connection for this one statement.
            raise(connection, needed, state);
            return;
        }
        try {
            var session = em.unwrap(Session.class);
            if (session == null) state.allowed = 0;
            else session.doWork(held -> raise(held, needed, state));
        } catch (RuntimeException unavailable) {
            // No session to ask: the statement runs under the connection's own settings.
            state.allowed = 0;
        }
    }

    private static Network state(EntityManager em) {
        if (em == null
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) return null;
        var bound = (Network) TransactionSynchronizationManager.getResource(NETWORK);
        if (bound != null) return bound;
        var state = new Network(em);
        state.allowed = -1;
        TransactionSynchronizationManager.bindResource(NETWORK, state);
        TransactionSynchronizationManager.registerSynchronization(state);
        return state;
    }

    private static void raise(Connection connection, long needed, Network state) {
        try {
            int current = connection.getNetworkTimeout();
            if (state.allowed < 0) state.original = current;
            if (current <= 0 || current >= needed) {
                state.allowed = Math.max(0, current);
                return;
            }
            connection.setNetworkTimeout(Runnable::run, (int) Math.min(needed, Integer.MAX_VALUE));
            state.raised = true;
            state.allowed = needed;
        } catch (SQLException | RuntimeException unsupported) {
            // A driver without a socket timeout has none to raise.
            if (state.allowed < 0) state.allowed = 0;
        }
    }

    private static int networkTimeout(Connection connection) {
        try {
            return connection.getNetworkTimeout();
        } catch (SQLException | RuntimeException unsupported) {
            return -1;
        }
    }

    private static void restore(Connection connection, int original) {
        if (original <= 0) return;
        try {
            if (connection.getNetworkTimeout() != original)
                connection.setNetworkTimeout(Runnable::run, original);
        } catch (SQLException | RuntimeException gone) {
            // A lost connection keeps whatever it had; its owner closes it.
        }
    }
}
