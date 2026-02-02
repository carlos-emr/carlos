//CHECKSTYLE:OFF

/*
 * Copyright (c) 2005- <OSCAR TEAM>
 */
package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

public class FrmInvoiceRecord extends FrmRecord {
    private String _dateFormat = "yyyy/MM/dd";

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        Properties props = new Properties();

        if (existingID <= 0) {

            String sql = "SELECT demographic_no, last_name, first_name, sex, address, city, province, postal, phone, phone2, year_of_birth, month_of_birth, date_of_birth, hin, ver, hc_type FROM demographic WHERE demographic_no = "
                    + demographicNo;
            ResultSet rs = DBHandler.GetSQL(sql);
            if (rs.next()) {
                java.util.Date date = UtilDateUtilities.calcDate(Misc.getString(rs, "year_of_birth"), rs
                        .getString("month_of_birth"), Misc.getString(rs, "date_of_birth"));
                props.setProperty("demographic_no", Misc.getString(rs, "demographic_no"));
                props.setProperty("formCreated", UtilDateUtilities.DateToString(new Date(),
                        _dateFormat));
                props.setProperty("patientName", Misc.getString(rs, "first_name") + " " + Misc.getString(rs, "last_name"));
                props.setProperty("patientSex", Misc.getString(rs, "sex"));
                props.setProperty("dateOfBirth", UtilDateUtilities.DateToString(date, _dateFormat));
                //props.setProperty("c_surname", oscar.Misc.getString(rs,"last_name"));
                props.setProperty("c_address", Misc.getString(rs, "address"));
                props.setProperty("c_address2", Misc.getString(rs, "city") + "," + Misc.getString(rs, "province") + "   "
                        + Misc.getString(rs, "postal"));
                //props.setProperty("c_province", oscar.Misc.getString(rs,"province"));
                //props.setProperty("c_postal", oscar.Misc.getString(rs,"postal"));
                props.setProperty("c_phn", Misc.getString(rs, "hin") + Misc.getString(rs, "ver") + "(" + Misc.getString(rs, "hc_type")
                        + ")");
                props.setProperty("c_phone", Misc.getString(rs, "phone") + "  " + Misc.getString(rs, "phone2"));
                props.setProperty("date_invoice", UtilDateUtilities.DateToString(new Date(),
                        _dateFormat));
                props.setProperty("date_signature", UtilDateUtilities.DateToString(new Date(),
                        _dateFormat));
            }
            rs.close();
        } else {
            String sql = "SELECT * FROM formInvoice WHERE demographic_no = " + demographicNo + " AND ID = "
                    + existingID;
            FrmRecordHelp frh = new FrmRecordHelp();
            frh.setDateFormat(_dateFormat);
            props = (frh).getFormRecord(sql);
        }

        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formInvoice WHERE demographic_no=" + demographic_no + " AND ID=0";

        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).saveFormRecord(props, sql));
    }

    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formInvoice WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).getPrintRecord(sql));
    }

    public String findActionValue(String submit) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).createActionURL(where, action, demoId, formId));
    }

}
