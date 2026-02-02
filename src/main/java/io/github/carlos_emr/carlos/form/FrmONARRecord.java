//CHECKSTYLE:OFF

package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

public class FrmONARRecord extends FrmRecord {
    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        Properties props = new Properties();

        if (existingID <= 0) {
            DBHelp db = new DBHelp();
            String sql = "SELECT demographic_no, last_name, first_name, sex, "
                    + "address, city, province, postal, phone, phone2, "
                    + "year_of_birth, month_of_birth, date_of_birth, hin, chart_no FROM demographic WHERE demographic_no = "
                    + demographicNo;
            ResultSet rs = DBHelp.searchDBRecord(sql);
            if (rs.next()) {
                java.util.Date date = UtilDateUtilities.calcDate(Misc.getString(rs, "year_of_birth"), rs
                        .getString("month_of_birth"), Misc.getString(rs, "date_of_birth"));
                props.setProperty("demographic_no", Misc.getString(rs, "demographic_no"));
                props.setProperty("formCreated", UtilDateUtilities
                        .DateToString(new Date(), "yyyy/MM/dd"));
                // props.setProperty("formEdited",
                // UtilDateUtilities.DateToString(new Date(),"yyyy/MM/dd"));
                props.setProperty("c_lastName", Misc.getString(rs, "last_name"));
                props.setProperty("c_firstName", Misc.getString(rs, "first_name"));
                props.setProperty("c_address", Misc.getString(rs, "address"));
                props.setProperty("c_city", Misc.getString(rs, "city"));
                props.setProperty("c_province", Misc.getString(rs, "province"));
                props.setProperty("c_postal", Misc.getString(rs, "postal"));
                props.setProperty("c_hin", Misc.getString(rs, "hin"));
                props.setProperty("c_fileNo", Misc.getString(rs, "chart_no"));
                props.setProperty("pg1_dateOfBirth", UtilDateUtilities.DateToString(date, "yyyy/MM/dd"));
                props.setProperty("pg1_age", String.valueOf(UtilDateUtilities.calcAge(Misc.getString(rs, "year_of_birth"), rs
                        .getString("month_of_birth"), Misc.getString(rs, "date_of_birth"))));
                props.setProperty("pg1_homePhone", Misc.getString(rs, "phone"));
                props.setProperty("pg1_workPhone", Misc.getString(rs, "phone2"));
                props.setProperty("pg1_formDate", UtilDateUtilities.DateToString(new Date(),
                        "yyyy/MM/dd"));
            }
            rs.close();
        } else {
            String sql = "SELECT * FROM formONAR WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
            props = (new FrmRecordHelp()).getFormRecord(sql);
        }

        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formONAR WHERE demographic_no=" + demographic_no + " AND ID=0";

        return ((new FrmRecordHelp()).saveFormRecord(props, sql));
    }

    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formONAR WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
        return ((new FrmRecordHelp()).getPrintRecord(sql));
    }

    public String findActionValue(String submit) throws SQLException {
        return ((new FrmRecordHelp()).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return ((new FrmRecordHelp()).createActionURL(where, action, demoId, formId));
    }


}
