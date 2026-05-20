/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

final class EFormSqlSafety {

    private EFormSqlSafety() {
    }

    static void validateLegacySqlSafety(String sql) {
        if (sql == null) {
            throw new SecurityException("Null SQL is not allowed");
        }

        String normalized = sql.trim();

        // Block unresolved template markers and obvious stacked/multi-statement patterns.
        if (normalized.contains("${")) {
            throw new SecurityException("Unsafe dynamic SQL template detected");
        }
        if (LegacyJdbcQuery.containsUnsafeSqlControlToken(sql)) {
            throw new SecurityException("Unsafe SQL control characters detected");
        }
    }
}
