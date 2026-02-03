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


/*
 * Created on 2005-5-19
 *
 */
package io.github.carlos_emr.carlos.login;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.carlos_emr.Misc;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.DBHandler;

/**
 * deprecated Use JPA instead, no new code should be written against this class.
 */
@Deprecated
public final class DBHelp {
    private static final Logger logger = MiscUtils.getLogger();


    public static ResultSet searchDBRecord(String sql) {
        ResultSet ret = null;
        try {

            ret = DBHandler.GetSQL(sql);
        } catch (SQLException e) {
            logger.error("Error", e);
        }

        return ret;
    }

    public static String getString(ResultSet rs, String columnName) throws SQLException {
        return Misc.getString(rs, columnName);
    }

    public static String getString(ResultSet rs, int columnIndex) throws SQLException {
        return Misc.getString(rs, columnIndex);
    }
}
