/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.exception.SnapshotIsolationException;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * The submitted edit no longer describes the latest persisted note revision.
 *
 * @since 2026-10-14
 */
public class StaleNoteRevisionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StaleNoteRevisionException() {
        super("The note has a newer revision. Reload it before saving another edit.");
    }

    /**
     * Classifies failures only after the transactional save has unwound, so rollback is retained.
     * Hibernate 7 maps MariaDB ER_CHECKREAD to SnapshotIsolationException; Spring 7 can wrap it in
     * JpaSystemException. Inspect typed causes, without treating arbitrary SQL/system failures as stale.
     */
    public static boolean isConflict(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof StaleNoteRevisionException
                    || cause instanceof ConcurrencyFailureException
                    || cause instanceof PessimisticLockException
                    || cause instanceof LockTimeoutException
                    || cause instanceof OptimisticLockException
                    || cause instanceof org.hibernate.PessimisticLockException
                    || cause instanceof SnapshotIsolationException) {
                return true;
            }
        }
        return false;
    }
}
