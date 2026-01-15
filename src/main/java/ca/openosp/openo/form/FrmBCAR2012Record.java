//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.form;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.SxmlMisc;


public class FrmBCAR2012Record extends FrmRecord {

    private final String _dateFormat = "dd/MM/yyyy";

    private Properties props;

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(_dateFormat);
        Date dateOfBirth;
        Date dateToday = new Date();

        if (demographicNo > 0) {
            super.setDemographic(loggedInInfo, demographicNo);
        }

        if (existingID <= 0) {

            props = new Properties();

            dateOfBirth = demographic.getBirthDay().getTime();

            this.setDemographicProperties();

            props.setProperty("demographic_no", demographic.getDemographicNo() + "");
            props.setProperty("formCreated", simpleDateFormat.format(dateToday));
            props.setProperty("pg1_dateOfBirth", simpleDateFormat.format(dateOfBirth));
            props.setProperty("pg1_age", demographic.getAge());
            props.setProperty("pg1_formDate", simpleDateFormat.format(dateToday));
            props.setProperty("pg2_formDate", simpleDateFormat.format(dateToday));
            props.setProperty("pg3_formDate", simpleDateFormat.format(dateToday));
            String rd = SxmlMisc.getXmlContent(demographic.getFamilyDoctor(), "rd");
            rd = rd != null ? rd : "";
            props.setProperty("pg1_famPhy", rd);


        } else {

            String sql = "SELECT * FROM formBCAR2012 WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
            FrmRecordHelp frh = new FrmRecordHelp();
            frh.setDateFormat(_dateFormat);

            try {

                props = frh.getFormRecord(sql);
                this.setDemographicProperties();

            } catch (SQLException e) {

                MiscUtils.getLogger().error("Error", e);

            }

        }

        return props;
    }

    private void setDemographicProperties() {

        props.setProperty("c_surname", StringUtils.trimToEmpty(demographic.getLastName()));
        props.setProperty("c_givenName", StringUtils.trimToEmpty(demographic.getFirstName()));
        props.setProperty("c_address", StringUtils.trimToEmpty(demographic.getAddress()));
        props.setProperty("c_city", StringUtils.trimToEmpty(demographic.getCity()));
        props.setProperty("c_province", StringUtils.trimToEmpty(demographic.getProvince()));
        props.setProperty("c_postal", StringUtils.trimToEmpty(demographic.getPostal()));
        props.setProperty("c_phn", StringUtils.trimToEmpty(demographic.getHin()));
        props.setProperty("c_phone", StringUtils.trimToEmpty(demographic.getPhone()));
        props.setProperty("c_phoneAlt1", StringUtils.trimToEmpty(demographic.getPhone2()));

        String cell = null;

        if (demographicExtMap.containsKey("demo_cell")) {
            cell = demographicExtMap.get("demo_cell");
        }

        if (cell != null) {
            props.setProperty("c_phoneAlt2", cell);
        }

    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formBCAR2012 WHERE demographic_no=" + demographic_no + " AND ID=0";

        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        int recordId = ((frh).saveFormRecord(props, sql));
        return recordId;
    }

    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formBCAR2012 WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
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
