/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.billings.MSP;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class dbExtract implements AutoCloseable {
    private Connection con = null;
    private PreparedStatement stmt = null;
    private PreparedStatement stmt2 = null;
    ResultSet resultSet = null;
    ResultSet resultSet2 = null;

    public dbExtract() {
    }

    public void openConnection() {
        try {

            //establish connection with the specified username, password and url
            con = LegacyJdbcQuery.getConnection();
        } catch (SQLException e) {
            MiscUtils.getLogger().debug("Cannot get connection", e);
            closeConnection();
            throw new IllegalStateException("Failed to open database connection", e);
        } catch (RuntimeException e) {
            MiscUtils.getLogger().debug("Cannot get connection", e);
            closeConnection();
            throw e;
        }

    }

    public ResultSet executeQuery(String sql, Object... params) throws SQLException {
        closeQuietly(resultSet);
        resultSet = null;
        closeQuietly(stmt);
        stmt = null;
        stmt = prepare(sql, params);
        try {
            resultSet = stmt.executeQuery();
            return resultSet;
        } catch (SQLException e) {
            closeQuietly(stmt);
            stmt = null;
            throw e;
        }
    }

    public ResultSet executeQuery2(String sql, Object... params) throws SQLException {
        closeQuietly(resultSet2);
        resultSet2 = null;
        closeQuietly(stmt2);
        stmt2 = null;
        stmt2 = prepare(sql, params);
        try {
            resultSet2 = stmt2.executeQuery();
            return resultSet2;
        } catch (SQLException e) {
            closeQuietly(stmt2);
            stmt2 = null;
            throw e;
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        } catch (SQLException | RuntimeException e) {
            closeQuietly(ps);
            throw e;
        }
    }

    public void closeConnection() {
        closeQuietly(resultSet);
        closeQuietly(resultSet2);
        closeQuietly(stmt2);
        closeQuietly(stmt);
        closeQuietly(con);
        resultSet = null;
        resultSet2 = null;
        stmt2 = null;
        stmt = null;
        con = null;
    } //closeConnection ends

    @Override
    public void close() {
        closeConnection();
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error closing JDBC resource", e);
        }
    }
}
