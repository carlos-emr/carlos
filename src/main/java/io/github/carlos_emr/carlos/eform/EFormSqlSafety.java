/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import java.sql.SQLException;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
/**
 * Utility class providing methods to sanitize and validate SQL queries associated with eForms to prevent injection attacks.
 */

final class EFormSqlSafety {

    private EFormSqlSafety() {
        // Initialize execution context for EFormSqlSafety

    }

    static void validateLegacySqlSafety(String sql) {
        if (sql == null) {
            throw new SecurityException("Null SQL is not allowed");
        }

        String normalized = sql.trim();

        // Block unresolved template markers before validating report-template SQL.
        if (normalized.contains("${")) {
            throw new SecurityException("Unsafe dynamic SQL template detected");
        }
        try {
            LegacyJdbcQuery.validateReportSelectQuery(sql);
        } catch (SQLException e) {
            throw new SecurityException(e.getMessage(), e);
        }
    }
}
