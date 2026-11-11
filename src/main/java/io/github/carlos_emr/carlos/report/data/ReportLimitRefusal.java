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

import java.sql.SQLTimeoutException;

/**
 * Tells a report's size or time refusal apart from a failure (#5850).
 *
 * <p>{@link ConfiguredReportQuery} refuses a report that passes its row, output or identifier
 * budget with {@link ConfiguredReportQuery.LimitExceededException}, and the bounded JDBC read
 * refuses one that passes its deadline with {@link SQLTimeoutException}. Callers wrap both
 * (a servlet in {@code ServletException}, the clinical reports in {@code IllegalStateException}),
 * so the whole cause chain is searched. A refusal is the user's to fix by narrowing the filters:
 * the pages show it as a message and the CSV download answers 413, never an error page.</p>
 */
public final class ReportLimitRefusal {

    /** Deep enough for any real wrapping; a longer or cyclic chain is not followed further. */
    private static final int MAX_CAUSE_DEPTH = 32;

    private ReportLimitRefusal() {
    }

    /**
     * @param failure a report failure, possibly wrapped; may be {@code null}
     * @return whether {@code failure} or one of its causes is a size or time refusal
     */
    public static boolean isRefusal(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof ConfiguredReportQuery.LimitExceededException || cause instanceof SQLTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
