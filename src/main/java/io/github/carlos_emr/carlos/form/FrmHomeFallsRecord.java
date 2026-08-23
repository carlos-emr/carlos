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


package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class FrmHomeFallsRecord extends FrmRecord {
    private String _dateFormat = "yyyy/MM/dd";

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID)
            throws SQLException {
        Properties props = new Properties();

        String sql;

        if (existingID <= 0) {
            sql = "SELECT demographic_no, sex, year_of_birth, month_of_birth, date_of_birth, phone FROM demographic WHERE demographic_no = ?";
            try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, demographicNo)) {
                if (rs.next()) {
                    java.util.Date dob = UtilDateUtilities.calcDate(Misc.getString(rs, "year_of_birth"), Misc.getString(rs, "month_of_birth"), Misc.getString(rs, "date_of_birth"));
                    props.setProperty(
                            "demographic_no",
                            Misc.getString(rs, "demographic_no"));
                    props.setProperty(
                            "formCreated",
                            UtilDateUtilities.DateToString(
                                    new Date(),
                                    _dateFormat));
                    props.setProperty("dob", UtilDateUtilities.DateToString(dob, "yyyy/MM/dd"));
                    props.setProperty("sex", Misc.getString(rs, "sex"));
                    props.setProperty("phone", Misc.getString(rs, "phone"));
                }
            }
            sql = "SELECT studyID FROM rehabStudy2004 WHERE demographic_no=?";
            try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, demographicNo)) {
                if (rs.next()) {
                    props.setProperty("studyID", Misc.getString(rs, "studyID"));
                } else {
                    props.setProperty("studyID", "N/A");
                }
            }
        } else {
            sql =
                    "SELECT * FROM formHomeFalls WHERE demographic_no = ? AND ID = ?";
            try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, demographicNo, existingID)) {

                if (rs.next()) {
                    MiscUtils.getLogger().debug("getting metaData");
                    ResultSetMetaData md = rs.getMetaData();

                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String name = md.getColumnName(i);

                        String value;
                        MiscUtils.getLogger().debug(" name = " + name + " type = " + md.getColumnTypeName(i) + " scale = " + md.getScale(i));
                        if (md.getColumnTypeName(i).equalsIgnoreCase("TINY")) {

                            if (rs.getInt(i) == 1) {
                                value = "checked='checked'";
                                MiscUtils.getLogger().debug("checking " + name);
                            } else {
                                value = "";
                                MiscUtils.getLogger().debug("not checking " + name);
                            }
                        } else {
                            if (md.getColumnTypeName(i).equalsIgnoreCase("date")) {
                                value = UtilDateUtilities.DateToString(rs.getDate(i), "yyyy/MM/dd");
                            } else {
                                value = Misc.getString(rs, i);
                            }
                        }

                        if (value != null) {
                            props.setProperty(name, value);
                        }
                    }
                }
            }
        }
        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql =
                "SELECT * FROM formHomeFalls WHERE demographic_no=? AND ID=0";

        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).saveFormRecord(props, sql, demographic_no));
    }

    public Properties getPrintRecord(int demographicNo, int existingID)
            throws SQLException {
        String sql =
                "SELECT * FROM formHomeFalls WHERE demographic_no = ? AND ID = ?";
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).getPrintRecord(sql, demographicNo, existingID));
    }

    public String findActionValue(String submit) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).findActionValue(submit));
    }

    public String createActionURL(
            String where,
            String action,
            String demoId,
            String formId)
            throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).createActionURL(where, action, demoId, formId));
    }

}
