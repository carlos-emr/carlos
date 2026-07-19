/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

//	 Referenced classes of package io.github.carlos_emr.carlos.form:
//	            FrmRecord, FrmRecordHelp

public class FrmBCClientChartChecklistRecord extends FrmRecord {

    private ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);


    public FrmBCClientChartChecklistRecord() {
        _dateFormat = "dd/MM/yyyy";
    }

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID)
            throws SQLException {
        Properties props = new Properties();
        if (existingID <= 0) {

            String sql = "SELECT demographic_no, last_name, first_name, sex, address, city, province, postal, phone, phone2, year_of_birth, month_of_birth, date_of_birth, hin FROM demographic WHERE demographic_no = "
                    + demographicNo;
            ResultSet rs = DBHandler.GetSQL(sql);
            if (rs.next()) {
                java.util.Date date = UtilDateUtilities.calcDate(rs
                        .getString("year_of_birth"), rs
                        .getString("month_of_birth"), rs
                        .getString("date_of_birth"));
                props.setProperty("demographic_no", rs
                        .getString("demographic_no"));
                props.setProperty("formCreated", UtilDateUtilities
                        .DateToString(new Date(), _dateFormat));
                props.setProperty("formEdited", UtilDateUtilities.DateToString(
                        new Date(), _dateFormat));
                props.setProperty("c_surname", Misc.getString(rs, "last_name"));
                props.setProperty("c_givenName", Misc.getString(rs, "first_name"));
                props.setProperty("c_address", Misc.getString(rs, "address"));
                props.setProperty("c_city", Misc.getString(rs, "city"));
                props.setProperty("c_province", Misc.getString(rs, "province"));
                props.setProperty("c_postal", Misc.getString(rs, "postal"));
                props.setProperty("c_phn", Misc.getString(rs, "hin"));
                props.setProperty("pg1_dateOfBirth", UtilDateUtilities
                        .DateToString(date, _dateFormat));
                props.setProperty("pg1_age", String.valueOf(UtilDateUtilities
                        .calcAge(date)));
                props.setProperty("c_phone", Misc.getString(rs, "phone") + "  "
                        + Misc.getString(rs, "phone2"));
                props.setProperty("pg1_formDate", UtilDateUtilities
                        .DateToString(new Date(), _dateFormat));
            }
            Clinic clinic = clinicDao.getClinic();
            if (clinic != null) {
                props.setProperty("c_clinicName", clinic.getClinicName());
            }
        } else {
            String sql = "SELECT * FROM formBCClientChartChecklist WHERE demographic_no = "
                    + demographicNo + " AND ID = " + existingID;
            FrmRecordHelp frh = new FrmRecordHelp();
            frh.setDateFormat(_dateFormat);
            props = frh.getFormRecord(sql);
            sql = "SELECT last_name, first_name, address, city, province, postal, phone,phone2, hin FROM demographic WHERE demographic_no = "
                    + demographicNo;
            ResultSet rs = DBHelp.searchDBRecord(sql);
            if (rs.next()) {
                props.setProperty("c_surname_cur", Misc.getString(rs, "last_name"));
                props
                        .setProperty("c_givenName_cur", rs
                                .getString("first_name"));
                props.setProperty("c_address_cur", Misc.getString(rs, "address"));
                props.setProperty("c_city_cur", Misc.getString(rs, "city"));
                props.setProperty("c_province_cur", Misc.getString(rs, "province"));
                props.setProperty("c_postal_cur", Misc.getString(rs, "postal"));
                props.setProperty("c_phn_cur", Misc.getString(rs, "hin"));
                props.setProperty("c_phone_cur", Misc.getString(rs, "phone") + "  "
                        + Misc.getString(rs, "phone2"));
            }
        }
        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formBCClientChartChecklist WHERE demographic_no="
                + demographic_no + " AND ID=0";
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return frh.saveFormRecord(props, sql);
    }

    public Properties getPrintRecord(int demographicNo, int existingID)
            throws SQLException {
        String sql = "SELECT * FROM formBCClientChartChecklist WHERE demographic_no = "
                + demographicNo + " AND ID = " + existingID;
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return frh.getPrintRecord(sql);
    }

    public String findActionValue(String submit) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return frh.findActionValue(submit);
    }

    public String createActionURL(String where, String action, String demoId,
                                  String formId) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return frh.createActionURL(where, action, demoId, formId);
    }

    private String _dateFormat;
}
