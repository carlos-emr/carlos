/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Classifies pre-write lock/snapshot conflicts by type/code, never by a potentially private message. */
public final class DemographicWriteConflict {
    private DemographicWriteConflict() {}
    public static boolean isConflict(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; failure != null && depth < 16 && seen.add(failure); depth++, failure = failure.getCause()) {
            if (failure instanceof org.springframework.dao.ConcurrencyFailureException
                    || failure instanceof jakarta.persistence.OptimisticLockException
                    || failure instanceof jakarta.persistence.PessimisticLockException
                    || failure instanceof jakarta.persistence.LockTimeoutException) return true;
            if (failure instanceof SQLException sql && ("40001".equals(sql.getSQLState())
                    || sql.getErrorCode() == 1020 || sql.getErrorCode() == 1205)) return true;
        }
        return false;
    }
}
