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
package io.github.carlos_emr.carlos.report.data;

import java.sql.SQLException;
import java.util.Set;

/**
 * The database refused a validated Query by Example query because the reporting account has no
 * privilege on a table or column it reads.
 *
 * <p>This is the database-layer boundary from issue #3372 doing its job: the query passed the
 * application's validator, but it reads something that is not granted to the reporting account,
 * such as a restricted table, a hidden column, or a table no one has classified yet.</p>
 *
 * @since 2026-10-22
 */
public class QueryByExampleAccessDeniedException extends SQLException {
    private static final long serialVersionUID = 1L;

    /**
     * MariaDB/MySQL privilege refusals (database, table, column, specific privilege such as FILE,
     * routine) and H2's "not enough rights", used by the isolated integration tests.
     */
    private static final Set<Integer> ACCESS_DENIED_ERROR_CODES = Set.of(1044, 1142, 1143, 1227, 1370, 90096);

    /**
     * Wraps the database's refusal. The vendor message (which names the account) stays on the cause
     * for the server log; the user sees only a fixed message.
     *
     * @param cause the privilege error the database raised
     */
    public QueryByExampleAccessDeniedException(SQLException cause) {
        super("The reporting account may not read part of this query", cause.getSQLState(), cause.getErrorCode(), cause);
    }

    /**
     * Whether a database failure is a privilege refusal rather than a broken query.
     *
     * @param failure the exception the driver raised, or {@code null}
     * @return {@code true} when it, or an exception it wraps, carries a privilege-refusal error code
     */
    public static boolean isAccessDenied(SQLException failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof SQLException sql && ACCESS_DENIED_ERROR_CODES.contains(sql.getErrorCode())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
