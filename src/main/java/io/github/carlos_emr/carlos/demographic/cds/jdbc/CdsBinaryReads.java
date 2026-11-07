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
package io.github.carlos_emr.carlos.demographic.cds.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Direction-independent binary-result capability for reviewed owning-DAO SELECT statements. */
public final class CdsBinaryReads {
    /** Guarded pools implement this without exposing their vendor connection or raw statements. */
    public interface Capability { PreparedStatement prepareCdsBinaryRead(String sql) throws SQLException; }
    private CdsBinaryReads() { }
    public static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        if (connection.isWrapperFor(Capability.class)) return connection.unwrap(Capability.class).prepareCdsBinaryRead(sql);
        return prepareDriver(connection, sql);
    }
    /**
     * Infrastructure primitive. Guard implementations wrap its statement before returning it.
     * An unguarded owning connection already carries SQL authority; caller SQL must remain a
     * reviewed SELECT with bound values. No session property is changed and no text fallback
     * is accepted. Guarded connections retain their full SQL/admission/result checks.
     */
    public static PreparedStatement prepareDriver(Connection connection, String sql) throws SQLException {
        if (sql == null || sql.length() > 65_536) throw new SQLException("CDS_BINARY_SELECT_REQUIRED", "45000");
        String start = sql.stripLeading();
        if (start.length() <= 6 || !start.regionMatches(true, 0, "SELECT", 0, 6) || " \t\r\n\f".indexOf(start.charAt(6)) < 0)
            throw new SQLException("CDS_BINARY_SELECT_REQUIRED", "45000");
        var statement = connection.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).serverPrepareStatement(sql);
        if (!(statement instanceof com.mysql.cj.jdbc.ServerPreparedStatement)) {
            statement.close(); throw new SQLException("CDS_BINARY_RESULT_PROTOCOL_REQUIRED", "45000");
        }
        return statement;
    }
}
