/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

package io.github.carlos_emr.carlos.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.db.DBHandler;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;


public class FrmConsultantRecord extends FrmRecord {

    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);


    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        Properties props = new Properties();

        if (existingID <= 0) {


            String sql = "SELECT demographic_no, CONCAT(last_name, ', ', first_name) AS pName, address, CONCAT(city, ', ', province, ' ', postal) AS address2, phone, year_of_birth, month_of_birth, date_of_birth, CONCAT(hin, ' ', ver) AS hic FROM demographic WHERE demographic_no = " + demographicNo;

            ResultSet rs = DBHandler.GetSQL(sql);
            if (rs.next()) {
                java.util.Date date = UtilDateUtilities.calcDate(Misc.getString(rs, "year_of_birth"), Misc.getString(rs, "month_of_birth"), Misc.getString(rs, "date_of_birth"));
                props.setProperty("demographic_no", Misc.getString(rs, "demographic_no"));
                props.setProperty("formCreated", UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
                props.setProperty("consultTime", UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
                props.setProperty("formEdited", UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
                props.setProperty("p_name", Misc.getString(rs, "pName"));
                props.setProperty("p_address1", Misc.getString(rs, "address"));
                props.setProperty("p_address2", Misc.getString(rs, "address2"));
                props.setProperty("p_birthdate", UtilDateUtilities.DateToString(date, "yyyy/MM/dd"));
                props.setProperty("p_phone", Misc.getString(rs, "phone"));
                props.setProperty("p_healthcard", Misc.getString(rs, "hic"));
            }
            rs.close();

            Clinic clinic = clinicDao.getClinic();
            if (clinic != null) {
                props.setProperty("cl_name", clinic.getClinicName());
                props.setProperty("cl_address1", clinic.getClinicAddress());
                props.setProperty("cl_address2", clinic.getClinicCity() + ", " + clinic.getClinicProvince() + ", " + clinic.getClinicPostal());
                props.setProperty("cl_phone", clinic.getClinicPhone());
                props.setProperty("cl_fax", clinic.getClinicFax());
            }

        } else {
            String sql = "SELECT * FROM formConsult WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
            props = (new FrmRecordHelp()).getFormRecord(sql);

        }

        return props;
    }


    public Properties getDocInfo(Properties props, String billingreferral_no) {
        ProfessionalSpecialist professionalSpecialist = professionalSpecialistDao.getByReferralNo(billingreferral_no);
        if (professionalSpecialist != null) {
            props.setProperty("t_name", "Dr. " + professionalSpecialist.getFirstName() + " " + professionalSpecialist.getLastName());
            props.setProperty("t_address", professionalSpecialist.getStreetAddress());
            props.setProperty("t_phone", professionalSpecialist.getPhoneNumber());
            props.setProperty("t_fax", professionalSpecialist.getFaxNumber());
        }

        return props;
    }

    public String getProvName(int provider_no) throws SQLException {

        Properties props = new Properties();
        String sql = "SELECT CONCAT('Dr. ', first_name, ' ', last_name) AS doc_Name FROM provider WHERE provider_no = " + provider_no;
        ResultSet rs = DBHandler.GetSQL(sql);
        if (rs.next()) {
            props.setProperty("doc_name", Misc.getString(rs, "doc_Name"));
        }
        rs.close();
        return props.getProperty("doc_name", "");
    }

    public Properties getInitRefDoc(Properties props, int demo_no) throws SQLException {

        String sql = "SELECT family_doctor FROM demographic WHERE demographic_no = '" + demo_no + "';";
        ResultSet rs = DBHandler.GetSQL(sql);
        String refdocno, docno;
        if (rs.next()) {
            docno = Misc.getString(rs, "family_doctor");
            refdocno = docno.substring(8, docno.indexOf("</rdohip>"));
            if (refdocno != "") {
                props.setProperty("refdocno", refdocno);
            }
        }

        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formConsult WHERE demographic_no=" + demographic_no + " AND ID=0";
        return ((new FrmRecordHelp()).saveFormRecord(props, sql));
    }

    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formConsult WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
        return ((new FrmRecordHelp()).getPrintRecord(sql));
    }

    public String findActionValue(String submit) throws SQLException {
        return ((new FrmRecordHelp()).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return ((new FrmRecordHelp()).createActionURL(where, action, demoId, formId));
    }



}
