/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.hibernate.Session;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * How an export read waits for the database: a statement's timeout is a wait, never a budget.
 *
 * <p>Every statement of the export is sent with a server-side deadline
 * ({@code max_statement_time}), first {@link #FIRST_WAIT_SECONDS}. When the server ends the
 * statement at that deadline (a busy server, a metadata or table lock held by someone else), the
 * same statement is sent again on the same connection with twice the wait, and again, until it is
 * answered. Nothing is refused, held or failed for how long an answer takes; the only things that
 * end a wait are the ones that end the whole attempt (a cancel, the job's stall detector through
 * the {@link Observer}), a deadline the surrounding transaction itself carries, and the loss of
 * the connection.</p>
 *
 * <p><b>Why sending it again is sound.</b> The statements are reads. A statement the server ended
 * at its deadline changed nothing and leaves its connection and its transaction as they were, so
 * inside a read-only repeatable-read snapshot the repeated statement reads exactly the rows the
 * first one would have returned. It is never moved to another connection: another connection is
 * another snapshot. When the connection itself is lost the snapshot is lost with it; that is not
 * repaired here but by the capture, which reads the patient's chart again from the start
 * ({@code CdsExportNativeCaptureService}).</p>
 *
 * <p><b>Why keep a deadline at all.</b> A live server always answers a statement within its
 * deadline, with rows or with the timeout, so silence beyond the deadline plus
 * {@link #ANSWER_GRACE_SECONDS} means the connection or the server is gone, and the connection's
 * socket timeout can say so. The socket timeout of a pooled connection (60 s by default where the
 * CDS network policy applies) is therefore raised, for this transaction only, to at least that
 * long, and restored before the connection goes back to its pool; a connection without a socket
 * timeout is given none.</p>
 *
 * <p><b>Reads the export does not build.</b> Some reads of an export are made by code every
 * other part of CARLOS shares: the account, provider, facility and membership lookups and the
 * {@code SecurityInfoManager} role and privilege reads of an authorization, and the eForm
 * renderer's and the signature manager's own reads. Their statements cannot be wrapped one by
 * one, and the shared code answers a failed read in its own way (the privilege check answers it
 * as a refusal). They run as a {@link #transaction unit}: one transaction whose connection, once
 * {@link #arm armed}, gives every statement the unit's deadline ({@code max_statement_time}) and
 * outlasts it by the same grace. When any statement of the unit was ended at that deadline,
 * whatever the shared code made of it, the whole unit is sent again in a new transaction with
 * twice the wait. That is sound because a unit only reads, and every answer it gives is a fresh
 * read-committed one: sending it again is reading again. The server counts the statements it
 * ended at a deadline for each connection ({@code Max_statement_time_exceeded}), so the unit
 * knows even when the shared code swallowed the failure; the deadline and the socket timeout are
 * put back before the connection returns to its pool.</p>
 *
 * <p><b>The export's own writes.</b> The ledger's short metadata transactions (the job, its lease,
 * its attempts, events and held charts) and the chart audit rows of an export run as {@link
 * #writing write units}: every statement of the transaction, the writes the persistence context
 * holds included, is sent with the unit's deadline before the transaction commits, and the
 * commit is sent only when the server ended none of them. A sending the server did end (at its
 * deadline, after a lock wait, or to break a deadlock) never reached its commit: InnoDB undid the
 * ended statement and the transaction is rolled back whole, so nothing of it was committed, and
 * the unit is sent again in a new transaction with twice the wait. Each sending reads its rows
 * again, the lease's fence included, so a sending that comes late finds a lease lost meanwhile
 * and is fenced; it can never revive one. A unit whose transaction committed is never sent again,
 * and its commit is never sent with a deadline: an answer lost on the way back from a commit is
 * left to the caller, as it always was.</p>
 */
public final class CdsExportStatementWaitReadSupport {
    /** The first wait for a statement's answer; every later wait for the same statement doubles. */
    static final int FIRST_WAIT_SECONDS = 30;

    /** How long past its own deadline a statement may stay unanswered before the socket gives up. */
    static final int ANSWER_GRACE_SECONDS = 30;

    /** MariaDB's own maximum for {@code max_statement_time}, in seconds. */
    static final int SERVER_MAXIMUM_SECONDS = 31_536_000;

    /** MariaDB: the statement was ended by its own {@code max_statement_time}. */
    private static final int ER_STATEMENT_TIMEOUT = 1969;

    /**
     * InnoDB, or the metadata lock: the statement waited its lock wait timeout for a lock and was
     * undone alone; the transaction stays open.
     */
    private static final int ER_LOCK_WAIT_TIMEOUT = 1205;

    /** InnoDB: the transaction was chosen to break a deadlock and was rolled back whole. */
    private static final int ER_LOCK_DEADLOCK = 1213;

    private static final Set<String> READS =
            Set.of("getResultList", "getSingleResult", "getSingleResultOrNull");

    private static final ThreadLocal<Observer> OBSERVER = new ThreadLocal<>();

    /** The sending of a unit that is running on this thread, if any. */
    private static final ThreadLocal<Unit> UNIT = new ThreadLocal<>();

    /** Transaction resource key of the socket timeout this transaction raised, if it raised one. */
    private static final Object NETWORK = new Object();

    /**
     * What the unit's connection had and has counted: its own deadline, to put back, and how many
     * statements the server has ended at a deadline on it. No row: a server that cannot say, so
     * the unit is given no deadline.
     */
    static final String SEEN =
            "SELECT @@SESSION.max_statement_time, CAST(VARIABLE_VALUE AS SIGNED) FROM"
                    + " information_schema.SESSION_STATUS WHERE VARIABLE_NAME ="
                    + " 'MAX_STATEMENT_TIME_EXCEEDED'";

    /** How many statements the server has ended at a deadline on this connection. */
    static final String ENDED =
            "SELECT CAST(VARIABLE_VALUE AS SIGNED) FROM information_schema.SESSION_STATUS WHERE"
                    + " VARIABLE_NAME = 'MAX_STATEMENT_TIME_EXCEEDED'";

    /** Every later statement of the connection, until put back, has this deadline. */
    static final String DEADLINE = "SET SESSION max_statement_time = ?";

    private CdsExportStatementWaitReadSupport() {}

    /**
     * The attempt's view of a wait. It is told after every wait that ended without an answer and
     * before the statement is sent again. A wait is not progress: an observer must not count it
     * as proof of life.
     */
    @FunctionalInterface
    public interface Observer {
        /**
         * @param waitedMillis how long this statement has waited so far
         * @return the longest the next wait may last, in milliseconds, or zero for no bound of
         *     the observer's own
         * @throws RuntimeException to end the wait because the attempt itself has ended
         */
        long waiting(long waitedMillis);

        /**
         * Asked before the first sending of a {@linkplain #writing write unit}, by an observer
         * that bounds even that one: a lease renewal, which may only wait for what its lease has
         * left. By default nothing is asked of an attempt before a unit has waited at all, so a
         * unit sent after its attempt ended (the chart audit of a stopped job, say) is still
         * sent.
         *
         * @return the longest the first sending may wait, in milliseconds, or zero for no bound
         *     of the observer's own
         * @throws RuntimeException to end the unit before anything is sent
         */
        default long before() {
            return 0;
        }
    }

    /** One statement, sent with the given deadline; it may be sent more than once. */
    @FunctionalInterface
    public interface Statement<T> {
        T send(int seconds) throws SQLException;
    }

    /**
     * Binds the attempt's observer to the calling thread until the returned handle is run, which
     * restores whatever was bound before.
     */
    public static Runnable observe(Observer observer) {
        var previous = OBSERVER.get();
        OBSERVER.set(java.util.Objects.requireNonNull(observer));
        return () -> {
            if (previous == null) OBSERVER.remove();
            else OBSERVER.set(previous);
        };
    }

    /**
     * The query, with its reads ({@code getResultList}, {@code getSingleResult},
     * {@code getSingleResultOrNull}) waiting as this class describes. Every other method is the
     * query's own.
     */
    public static Query waiting(EntityManager em, Query query) {
        return waiting(em, query, FIRST_WAIT_SECONDS);
    }

    static Query waiting(EntityManager em, Query query, int firstSeconds) {
        java.util.Objects.requireNonNull(query);
        var proxy = new Query[1];
        proxy[0] =
                (Query)
                        Proxy.newProxyInstance(
                                Query.class.getClassLoader(),
                                new Class<?>[] {Query.class},
                                (self, method, arguments) -> {
                                    if (READS.contains(method.getName())
                                            && method.getParameterCount() == 0)
                                        return await(
                                                em,
                                                null,
                                                firstSeconds,
                                                seconds -> {
                                                    query.setHint("org.hibernate.timeout", seconds);
                                                    try {
                                                        return method.invoke(query);
                                                    } catch (InvocationTargetException thrown) {
                                                        throw rethrow(thrown);
                                                    } catch (IllegalAccessException refused) {
                                                        throw new IllegalStateException(refused);
                                                    }
                                                });
                                    Object result;
                                    try {
                                        result = method.invoke(query, arguments);
                                    } catch (InvocationTargetException thrown) {
                                        throw rethrow(thrown);
                                    }
                                    // A fluent setter returns the query: keep the caller on
                                    // the waiting one.
                                    return result == query ? proxy[0] : result;
                                });
        return proxy[0];
    }

    /** A statement on the connection the caller's session lent it inside its transaction. */
    public static <T> T await(EntityManager em, Connection connection, Statement<T> statement)
            throws SQLException {
        java.util.Objects.requireNonNull(connection);
        try {
            return await(em, connection, FIRST_WAIT_SECONDS, statement);
        } catch (Unanswered wrapped) {
            throw wrapped.failure;
        }
    }

    /**
     * A statement on a dedicated connection the caller opened for itself and closes itself (the
     * legacy table's lock): its socket timeout is raised as the waits grow and never restored.
     */
    public static <T> T awaitOwned(Connection connection, Statement<T> statement)
            throws SQLException {
        java.util.Objects.requireNonNull(connection);
        try {
            return await(null, connection, FIRST_WAIT_SECONDS, statement);
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

    private static RuntimeException rethrow(InvocationTargetException thrown) {
        Throwable cause = thrown.getCause();
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(cause);
    }

    static <T> T await(
            EntityManager em, Connection connection, int firstSeconds, Statement<T> statement) {
        if (firstSeconds < 1) throw new IllegalArgumentException("CDS_EXPORT_STATEMENT_WAIT_INVALID");
        long seconds = firstSeconds, waited = 0;
        while (true) {
            // A deadline of the surrounding transaction is its owner's: it shortens a wait and,
            // once passed, ends the statement as it always did.
            int wait = (int) Math.max(1, Math.min(seconds, remainingSeconds(seconds)));
            allow(em, connection, wait);
            try {
                return statement.send(wait);
            } catch (SQLException failed) {
                if (!expired(failed)) throw new Unanswered(failed);
            } catch (RuntimeException failed) {
                if (!expired(failed) || rollbackOnly(em)) throw failed;
            }
            // Sent again here: a unit armed on this transaction does not count it as its own.
            var network = network(em);
            if (network != null) network.resent++;
            waited += wait * 1000L;
            seconds = next(seconds, waited);
        }
    }

    /**
     * The wait after one that ended without an answer: twice as long, within the observer's
     * window, however short that window has become (a lease that is running out), but never
     * shorter than a second. The observer and an interrupt end the waiting here, between two
     * sendings.
     */
    private static long next(long seconds, long waitedMillis) {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
        var observer = OBSERVER.get();
        long bound = observer == null ? 0 : observer.waiting(waitedMillis);
        long next = Math.min(seconds * 2, SERVER_MAXIMUM_SECONDS);
        if (bound > 0) next = Math.max(1, Math.min(next, bound / 1000));
        return next;
    }

    /**
     * The wait of a write unit's first sending: {@code firstSeconds}, within what the observer
     * allows before anything is sent ({@link Observer#before}): a renewal is sent only with the
     * time its lease has left, and not at all once nothing is left.
     */
    private static long first(int firstSeconds) {
        var observer = OBSERVER.get();
        long bound = observer == null ? 0 : observer.before();
        return bound > 0 ? Math.max(1, Math.min(firstSeconds, bound / 1000)) : firstSeconds;
    }

    /**
     * Runs {@code work} as one unit in its own transaction of {@code transactions}: a unit of
     * reads that shared CARLOS code makes for the export (see the class description). Once the
     * work {@linkplain #arm arms} the transaction, every statement of it is sent with the unit's
     * deadline, first {@link #FIRST_WAIT_SECONDS}. When the server ended any of them at that
     * deadline, the outcome of this sending is set aside, whether the work returned or threw, and
     * the unit is sent again in a new transaction with twice the wait, until a sending has no
     * statement ended. The same things end the waiting as for one statement: a cancel, the
     * attempt's {@link Observer} and an interrupt.
     *
     * <p>A connection lost during an armed unit is the database's failure, whatever the work made
     * of it: the shared privilege check, for one, answers a failed read as a refusal, which would
     * otherwise be reported as changed access. It is reported as {@code
     * CDS_EXPORT_DATABASE_UNAVAILABLE}.</p>
     *
     * @param transactions the unit's transaction; it must start a transaction of its own (a new
     *     one, not one the caller already holds, which another sending could not replace)
     * @return what the work returned in the sending that no deadline ended
     */
    public static <T> T transaction(TransactionOperations transactions, TransactionCallback<T> work) {
        return transaction(transactions, FIRST_WAIT_SECONDS, work);
    }

    static <T> T transaction(
            TransactionOperations transactions, int firstSeconds, TransactionCallback<T> work) {
        java.util.Objects.requireNonNull(transactions);
        java.util.Objects.requireNonNull(work);
        if (firstSeconds < 1) throw new IllegalArgumentException("CDS_EXPORT_STATEMENT_WAIT_INVALID");
        long seconds = firstSeconds, waited = 0;
        while (true) {
            var unit = new Unit((int) seconds, false);
            var previous = UNIT.get();
            UNIT.set(unit);
            T result = null;
            RuntimeException failure = null;
            try {
                result = transactions.execute(work);
            } catch (RuntimeException failed) {
                failure = failed;
            } finally {
                if (previous == null) UNIT.remove();
                else UNIT.set(previous);
            }
            if (unit.lost)
                throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportDatabaseUnavailableException(failure);
            if (!unit.expired) {
                if (failure != null) throw failure;
                return result;
            }
            waited += seconds * 1000L;
            seconds = next(seconds, waited);
        }
    }

    /**
     * Runs {@code work} as one write unit in its own transaction of {@code transactions}: one of
     * the export's own short metadata transactions (its ledger, lease, held-chart and event rows,
     * its chart audit rows; see the class description). The transaction is {@linkplain #arm armed}
     * before the work's first statement, even on a connection without a socket timeout, so every
     * statement of it is sent with the unit's deadline, first {@link #FIRST_WAIT_SECONDS} (less
     * where the observer bounds the first sending: {@link Observer#before}), then within the
     * observer's window. When the work returns, the writes its persistence context still holds
     * are sent, under the same deadline, and the server's count of the statements it ended is read:
     * only a sending with none ended goes on to its commit, which is sent with the connection's own
     * deadline back.
     *
     * <p>A sending is sent again, in a new transaction with twice the wait, only when it never
     * reached its commit and its failure was the server's: a statement ended at its deadline (by
     * the count, whatever the work made of it), a lock wait that ran out, or a deadlock. InnoDB
     * undid the ended statement and the sending's transaction is rolled back, so nothing of it was
     * committed; the next sending reads every row again, the lease's fence included. Every other
     * failure is passed on at once: a fence, a cancellation, a refusal of the work's own. A sending
     * that committed is the answer and is never sent again, and a failure of the commit itself is
     * passed on as it was, never taken for a statement the server ended. A connection lost before
     * the commit is the database's failure ({@code CDS_EXPORT_DATABASE_UNAVAILABLE}). The same
     * things end the waiting as for one statement: a cancel, the {@link Observer} (a lost lease,
     * the stall detector) and an interrupt.</p>
     *
     * @param transactions the unit's transaction; it must start a transaction of its own (a new
     *     one, not one the caller already holds, which another sending could not replace)
     * @return what the work returned in the sending that committed
     */
    public static <T> T writing(TransactionOperations transactions, TransactionCallback<T> work) {
        return writing(transactions, FIRST_WAIT_SECONDS, work);
    }

    static <T> T writing(
            TransactionOperations transactions, int firstSeconds, TransactionCallback<T> work) {
        java.util.Objects.requireNonNull(transactions);
        java.util.Objects.requireNonNull(work);
        if (firstSeconds < 1) throw new IllegalArgumentException("CDS_EXPORT_STATEMENT_WAIT_INVALID");
        long seconds = first(firstSeconds), waited = 0;
        while (true) {
            var unit = new Unit((int) seconds, true);
            var previous = UNIT.get();
            UNIT.set(unit);
            try {
                return transactions.execute(
                        status -> {
                            arm();
                            T value = work.doInTransaction(status);
                            settle(unit);
                            return value;
                        });
            } catch (RuntimeException failed) {
                if (unit.lost)
                    throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                            .CdsExportDatabaseUnavailableException(failed);
                // Settled: the failure is the commit's own, whose outcome only the caller can
                // establish. Otherwise only a failure the server made is a reason to send again.
                if (unit.settled || !(unit.expired || expired(failed) || contended(failed)))
                    throw failed;
            } finally {
                if (previous == null) UNIT.remove();
                else UNIT.set(previous);
            }
            waited += seconds * 1000L;
            seconds = next(seconds, waited);
        }
    }

    /**
     * The end of a write unit's work, inside its transaction and before its commit: the writes the
     * persistence context still holds are sent now, under the unit's deadline, and, on an armed
     * connection, the server's count decides whether this sending may commit ({@link
     * Network#settle}).
     */
    private static void settle(Unit unit) {
        var em = transactionEntityManager();
        if (em != null && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            try {
                em.flush();
            } catch (RuntimeException failed) {
                // The same vocabulary a failure of these writes had when the commit sent them.
                RuntimeException translated =
                        org.springframework.orm.jpa.EntityManagerFactoryUtils
                                .convertJpaAccessExceptionIfPossible(failed);
                throw translated != null ? translated : failed;
            }
        }
        var network = em == null ? null : network(em);
        if (network != null && network.unit == unit)
            em.unwrap(Session.class).doWork(network::settle);
        unit.settled = true;
    }

    /** A sending of a write unit that must not reach its commit: it is rolled back, then sent again. */
    private static final class Resend extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Resend() {
            super("CDS_EXPORT_STATEMENT_WAIT_RESEND", null, false, false);
        }
    }

    /**
     * Whether InnoDB ended the statement after a lock wait that ran out ({@code
     * innodb_lock_wait_timeout}, or {@code lock_wait_timeout} for a metadata lock), which undoes
     * the statement alone, or ended the whole transaction to break a deadlock. Neither commits
     * anything of the sending.
     */
    static boolean contended(Throwable failure) {
        var seen = new java.util.HashSet<Throwable>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == ER_LOCK_WAIT_TIMEOUT
                            || sql.getErrorCode() == ER_LOCK_DEADLOCK
                            || "40001".equals(sql.getSQLState()))) return true;
        }
        return false;
    }

    /**
     * Arms the transaction of the unit running on this thread: from here on every statement of
     * it has the unit's deadline, and the connection's socket timeout outlasts that deadline by
     * {@link #ANSWER_GRACE_SECONDS}. Called by the work of a unit before the shared reads it
     * makes, and by every {@link #writing write unit} before its work; the first call of a sending
     * arms it and later calls change nothing. Outside a unit it does nothing. A read unit is not
     * armed on a connection without a socket timeout, where nothing cuts a slow read off; a write
     * unit is, so that its waits reach its observer. No unit is armed on a server that cannot
     * count the statements it ended (the unit then runs as it did before).
     */
    public static void arm() {
        var unit = UNIT.get();
        if (unit == null
                || unit.armed
                || !TransactionSynchronizationManager.isActualTransactionActive()) return;
        unit.armed = true;
        var em = transactionEntityManager();
        var network = em == null ? null : state(em);
        // A transaction armed already, by an earlier sending of another unit on this thread.
        if (network == null || network.unit != null) return;
        try {
            em.unwrap(Session.class).doWork(connection -> network.arm(connection, unit));
        } catch (RuntimeException failed) {
            if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportDatabaseUnavailableException.lost(failed)) throw failed;
            // A server that cannot take the deadline: the unit runs without one.
        }
    }

    /** The entity manager of the transaction on this thread: the one its holder binds. */
    private static EntityManager transactionEntityManager() {
        for (var value : TransactionSynchronizationManager.getResourceMap().values())
            if (value instanceof org.springframework.orm.jpa.EntityManagerHolder holder)
                return holder.getEntityManager();
        return null;
    }

    /** One sending of a unit: its deadline, and what its armed connection found. */
    private static final class Unit {
        private final int seconds;

        /** A write unit: armed even without a socket timeout, settled before its commit. */
        private final boolean writes;

        private boolean armed;

        /** A statement of this sending was ended at a deadline and not sent again by its read. */
        private boolean expired;

        /** The connection was lost while this sending was armed on it. */
        private boolean lost;

        /** A write unit's work returned and every statement of it was answered: only the commit is left. */
        private boolean settled;

        private Unit(int seconds, boolean writes) {
            this.seconds = seconds;
            this.writes = writes;
        }
    }

    /** Whether the server ended the statement at its own deadline; the connection is intact. */
    static boolean expired(Throwable failure) {
        var seen = new java.util.HashSet<Throwable>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof org.springframework.transaction.TransactionTimedOutException)
                return false;
            if (cause instanceof java.sql.SQLTimeoutException) return true;
            if (cause instanceof SQLException sql && sql.getErrorCode() == ER_STATEMENT_TIMEOUT)
                return true;
        }
        return false;
    }

    /**
     * A transaction marked for rollback cannot use an answer: the failure is passed on, and the
     * capture reads the chart again in a new transaction.
     */
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

    /**
     * The socket timeout a transaction found on its connection and whether it was raised, and the
     * unit armed on it, if any: the deadline its connection had, to put back, and the count of
     * ended statements it started from.
     */
    private static final class Network implements TransactionSynchronization {
        private final EntityManager em;
        private int original;
        private long allowed;
        private boolean raised;

        /** Statements of this transaction its own reads sent again after their deadline. */
        private long resent;

        private Unit unit;
        private long resentAtArm, seenAtArm;
        private double deadline;

        private Network(EntityManager em) {
            this.em = em;
        }

        /** Gives every later statement of the connection the unit's deadline; see {@link #arm()}. */
        private void arm(Connection connection, Unit armed) throws SQLException {
            int current = connection.getNetworkTimeout();
            // No socket timeout: nothing cuts a slow read off, so a read unit needs no deadline. A
            // write unit is given one all the same: its waits are how its attempt hears of them.
            if (current <= 0 && !armed.writes) return;
            try (var seen = connection.prepareStatement(SEEN);
                    var rows = seen.executeQuery()) {
                if (!rows.next()) return;
                deadline = rows.getDouble(1);
                seenAtArm = rows.getLong(2);
                if (rows.wasNull()) return;
            }
            raise(connection, (armed.seconds + (long) ANSWER_GRACE_SECONDS) * 1000L, this);
            try (var set = connection.prepareStatement(DEADLINE)) {
                set.setInt(1, armed.seconds);
                set.execute();
            }
            unit = armed;
            resentAtArm = resent;
        }

        /**
         * Finds whether the server ended a statement of the unit at its deadline that no read sent
         * again, and puts the connection's own deadline back. A connection that cannot be given
         * its deadline back is aborted: it never returns to its pool with the unit's.
         */
        private void disarm(Connection connection) {
            try (var ended = connection.prepareStatement(ENDED);
                    var rows = ended.executeQuery()) {
                if (!rows.next() || rows.getLong(1) - seenAtArm > resent - resentAtArm)
                    unit.expired = true;
            } catch (SQLException | RuntimeException failed) {
                // Unknown: reading again is always sound, unless the connection itself is gone.
                if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportDatabaseUnavailableException.lost(failed)) unit.lost = true;
                else unit.expired = true;
            }
            try (var set = connection.prepareStatement(DEADLINE)) {
                set.setDouble(1, deadline);
                set.execute();
            } catch (SQLException | RuntimeException failed) {
                if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportDatabaseUnavailableException.lost(failed)) unit.lost = true;
                abort(connection);
            }
        }

        /**
         * The end of a write unit, before its commit: whether the server ended a statement of it
         * at its deadline that no read sent again, and the connection's own deadline put back, so
         * the commit is never sent with the unit's. A sending that cannot be shown to have none
         * ended is not committed: it is rolled back and sent again. A connection that cannot be
         * given its deadline back is aborted and the sending is sent again on another.
         */
        private void settle(Connection connection) throws SQLException {
            var settling = unit;
            boolean ended;
            try (var count = connection.prepareStatement(ENDED);
                    var rows = count.executeQuery()) {
                ended = !rows.next() || rows.getLong(1) - seenAtArm > resent - resentAtArm;
            } catch (SQLException | RuntimeException failed) {
                if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportDatabaseUnavailableException.lost(failed)) {
                    settling.lost = true;
                    throw failed;
                }
                // Unknown: before its commit, sending the unit again is always sound.
                ended = true;
            }
            if (ended) {
                settling.expired = true;
                throw new Resend();
            }
            try (var set = connection.prepareStatement(DEADLINE)) {
                set.setDouble(1, deadline);
                set.execute();
            } catch (SQLException | RuntimeException failed) {
                if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                        .CdsExportDatabaseUnavailableException.lost(failed)) settling.lost = true;
                else settling.expired = true;
                unit = null;
                abort(connection);
                throw new Resend();
            }
            unit = null;
        }

        @Override
        public void beforeCompletion() {
            if (!raised && unit == null) return;
            try {
                var session = em.unwrap(Session.class);
                if (session != null)
                    session.doWork(
                            connection -> {
                                if (unit != null) disarm(connection);
                                if (raised) connection.setNetworkTimeout(Runnable::run, original);
                            });
            } catch (RuntimeException gone) {
                // The connection is lost; a lost connection does not go back to its pool.
                if (unit != null
                        && io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportDatabaseUnavailableException.lost(gone))
                    unit.lost = true;
            }
        }

        // A transaction opened inside this one (the ledger's checkpoint, a fresh authorization)
        // has its own connection: this transaction's finding must not answer for it.
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
     * Before a statement that may wait {@code seconds}: the connection's socket timeout, where
     * it has one, must not end the connection before the server's own deadline has had
     * {@link #ANSWER_GRACE_SECONDS} to answer.
     */
    private static void allow(EntityManager em, Connection connection, int seconds) {
        long needed = (seconds + (long) ANSWER_GRACE_SECONDS) * 1000L;
        // Raised only where the transaction can put it back: a connection that returned to its
        // pool with another socket timeout would change every later user of the pool.
        if (em == null && connection != null) {
            // A connection the caller owns alone and closes itself: nothing to put back.
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
        var state = state(em);
        if (state == null || state.allowed == 0 || state.allowed >= needed) return;
        if (connection != null) {
            // The caller holds the connection for this one statement inside the transaction.
            raise(connection, needed, state);
            return;
        }
        try {
            var session = em.unwrap(Session.class);
            if (session == null) state.allowed = 0;
            else session.doWork(held -> raise(held, needed, state));
        } catch (RuntimeException unavailable) {
            // No session to ask (a substituted entity manager): the statement runs under the
            // connection's own settings.
            state.allowed = 0;
        }
    }

    /** The transaction's state if a read of it has made one, without making one. */
    private static Network network(EntityManager em) {
        if (em == null || !TransactionSynchronizationManager.isSynchronizationActive()) return null;
        return (Network) TransactionSynchronizationManager.getResource(NETWORK);
    }

    private static Network state(EntityManager em) {
        if (em == null || !TransactionSynchronizationManager.isSynchronizationActive()) return null;
        var bound = (Network) TransactionSynchronizationManager.getResource(NETWORK);
        if (bound != null) return bound;
        var state = new Network(em);
        state.allowed = -1;
        TransactionSynchronizationManager.bindResource(NETWORK, state);
        TransactionSynchronizationManager.registerSynchronization(state);
        return state;
    }

    /** A connection that must never return to its pool as it is: aborted, so the pool drops it. */
    private static void abort(Connection connection) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException ignored) {
            // Aborting is all that is left to do; a lost connection is not reused.
        }
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
}
