/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

import org.hibernate.exception.ConstraintViolationException;

/** Classifies demographic write conflicts using exception types and database metadata. */
public final class DemographicWriteConflict {
    private DemographicWriteConflict() {}

    public static boolean isConflict(Throwable failure) {
        return hasCause(failure, cause -> cause instanceof org.springframework.dao.ConcurrencyFailureException
                || cause instanceof jakarta.persistence.OptimisticLockException
                || cause instanceof jakarta.persistence.PessimisticLockException
                || cause instanceof jakarta.persistence.LockTimeoutException
                || cause instanceof SQLException sql && ("40001".equals(sql.getSQLState())
                    || sql.getErrorCode() == 1020 || sql.getErrorCode() == 1205));
    }

    public static boolean isDuplicateExtensionKey(Throwable failure) {
        return hasCause(failure, cause -> {
            if (!(cause instanceof ConstraintViolationException violation) || violation.getErrorCode() != 1062) {
                return false;
            }
            String constraint = violation.getConstraintName();
            return "uk_demo_ext_single_value".equals(constraint)
                    || "demographicExt.uk_demo_ext_single_value".equals(constraint);
        });
    }

    private static boolean hasCause(Throwable failure, Predicate<Throwable> matches) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; failure != null && depth < 16 && seen.add(failure); depth++, failure = failure.getCause()) {
            if (matches.test(failure)) return true;
        }
        return false;
    }
}
