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
package io.github.carlos_emr.carlos.utility;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * How a statement on a dedicated connection an import owns (its coordinator or maintenance
 * connection) waits for the database: it is sent with a deadline, and sent again with a longer one
 * each time the server ends it at that deadline, until it is answered or its scope ends. A slow
 * but working database therefore never fails an import's lock probes, readbacks or session
 * settings.
 *
 * <p>The import supplies the implementation ({@code CdsImportStatementWaits.OWNED}, which answers
 * to the import's stall window and cancel). Classes outside the import's packages that run such
 * statements, the coordinator DAO and {@link CdsJdbcConnectionTimeouts}, take it as a parameter
 * so they never depend on those packages.
 *
 * @since 2026-11-14
 */
@FunctionalInterface
public interface CdsOwnedStatementWaits {
    /**
     * Sends {@code statement} on {@code connection} until the server answers it.
     *
     * @return the statement's own result
     * @throws SQLException a failure other than the server ending the statement at its deadline
     */
    <T> T await(Connection connection, Statement<T> statement) throws SQLException;

    /** One statement, sent with the given deadline in seconds; it may be sent more than once. */
    @FunctionalInterface
    interface Statement<T> {
        T send(int seconds) throws SQLException;
    }
}
