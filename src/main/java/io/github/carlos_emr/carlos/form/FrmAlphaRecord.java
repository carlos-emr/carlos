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
package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Record class for the ALPHA (Antenatal Psychosocial Health Assessment) form, {@code form/formalpha}, stored in
 * {@code formAlpha}.
 *
 * <p>The page, its table and its Forms-menu registration were shipped, but this class was not, so
 * {@link FrmRecordFactory} had nothing to return for {@code Alpha}: the page threw a {@code NullPointerException} for
 * every patient and the save answered 400 (issues #3735 and #4590). It follows the other single-table records: a new
 * form is seeded with the patient's number, name and today's date, a saved one is read back by patient and id, and the
 * save goes through {@link FrmRecordHelp}, which inserts a new row on every save.</p>
 *
 * @since 2026-10-22
 */
public class FrmAlphaRecord extends FrmRecord {

    /** The date format the page shows and posts for {@code formCreated}. */
    private static final String DATE_FORMAT = "yyyy/MM/dd";

    /**
     * The ALPHA record to show: for a positive {@code existingID}, that {@code formAlpha} row of this patient
     * (empty when none matches both); otherwise a new record seeded with the patient's name and today's date.
     *
     * @param loggedInInfo  LoggedInInfo: the signed-in user (unused; the legacy record contract passes it)
     * @param demographicNo int: the patient the record belongs to
     * @param existingID    int: the {@code formAlpha.ID} to load, or 0 for a new record
     * @return Properties: the record's columns
     * @throws SQLException if the record cannot be read
     */
    @Override
    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        if (existingID > 0) {
            String sql = "SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?";
            return newHelper().getFormRecord(sql, demographicNo, existingID);
        }

        Properties props = new Properties();
        String sql = "SELECT demographic_no, CONCAT(last_name, ', ', first_name) AS pName "
                + "FROM demographic WHERE demographic_no = ?";
        try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(sql, demographicNo)) {
            if (rs.next()) {
                props.setProperty("demographic_no", Misc.getString(rs, "demographic_no"));
                // Misc.getString answers "" for SQL NULL (CONCAT is NULL when either name is).
                props.setProperty("pName", Misc.getString(rs, "pName"));
                props.setProperty("formCreated", UtilDateUtilities.DateToString(new Date(), DATE_FORMAT));
            }
        }
        return props;
    }

    /**
     * Inserts the submitted ALPHA record as a new {@code formAlpha} row for the patient it names.
     *
     * @param props Properties: the submitted form values, including {@code demographic_no}
     * @return int: the new row's id
     * @throws SQLException if the row cannot be inserted
     */
    @Override
    public int saveFormRecord(Properties props) throws SQLException {
        String demographicNo = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = 0";
        return newHelper().saveFormRecord(props, sql, demographicNo);
    }

    /**
     * The saved ALPHA record for printing.
     *
     * @param demographicNo int: the patient the record belongs to
     * @param existingID    int: the {@code formAlpha.ID} to print
     * @return Properties: the record's columns, empty when no row matches both values
     * @throws SQLException if the record cannot be read
     */
    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formAlpha WHERE demographic_no = ? AND ID = ?";
        return newHelper().getPrintRecord(sql, demographicNo, existingID);
    }

    /**
     * The form action configured for the submit button pressed.
     *
     * @param submit String: the submit button's value ({@code save}, {@code exit}, ...)
     * @return String: the configured action, as {@link FrmRecordHelp} resolves it
     * @throws SQLException if the configuration cannot be read
     */
    @Override
    public String findActionValue(String submit) throws SQLException {
        return newHelper().findActionValue(submit);
    }

    /**
     * The address the save continues to, built by {@link FrmRecordHelp}.
     *
     * @param where  String: the page to continue to
     * @param action String: the configured action
     * @param demoId String: the patient's demographic number
     * @param formId String: the saved record's id
     * @return String: the continuation address
     * @throws SQLException if the address cannot be built
     */
    @Override
    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return newHelper().createActionURL(where, action, demoId, formId);
    }

    private static FrmRecordHelp newHelper() {
        FrmRecordHelp helper = new FrmRecordHelp();
        helper.setDateFormat(DATE_FORMAT);
        return helper;
    }
}
