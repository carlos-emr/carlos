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

    public void openConnection() throws SQLException {
        try {

            //establish connection with the specified username, password and url
            con = LegacyJdbcQuery.getConnection();
        } catch (SQLException e) {
            MiscUtils.getLogger().debug("Cannot get connection ");
            MiscUtils.getLogger().debug("Exception is: " + e);
            closeConnection();
            throw e;
        }

    }

    public ResultSet executeQuery(String sql, Object... params) throws SQLException {
        closeQuietly(resultSet, "ResultSet");
        closeQuietly(stmt, "PreparedStatement");
        stmt = prepare(sql, params);
        resultSet = stmt.executeQuery();
        return resultSet;
    }

    public ResultSet executeQuery2(String sql, Object... params) throws SQLException {
        closeQuietly(resultSet2, "ResultSet");
        closeQuietly(stmt2, "PreparedStatement");
        stmt2 = prepare(sql, params);
        resultSet2 = stmt2.executeQuery();
        return resultSet2;
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    public void closeConnection() {
        closeQuietly(resultSet, "ResultSet");
        closeQuietly(resultSet2, "ResultSet");
        closeQuietly(stmt2, "Statement");
        closeQuietly(stmt, "Statement");
        closeQuietly(con, "Connection");
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

    private void closeQuietly(AutoCloseable resource, String resourceName) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error closing " + resourceName, e);
        }
    }
}
