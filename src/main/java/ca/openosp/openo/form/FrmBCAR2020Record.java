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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.owasp.encoder.Encode;
import ca.openosp.SxmlMisc;

import ca.openosp.openo.commn.dao.forms.FormBCAR2020DataDao;
import ca.openosp.openo.commn.dao.forms.FormBCAR2020TextDao;
import ca.openosp.openo.form.model.FormBCAR2020Data;
import ca.openosp.openo.form.model.FormBCAR2020Text;
import ca.openosp.openo.util.UtilDateUtilities;
import ca.openosp.openo.util.UtilMisc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class FrmBCAR2020Record extends FrmRecord {

    private static Logger logger = MiscUtils.getLogger();

    public FrmBCAR2020Record() {
        this.dateFormat = "yyyy/MM/dd";
    }

    //List of global fields that eixst on all pages
    private static List<String> globalFields = Arrays.asList("t_patientSurname", "t_patientGivenName", "t_patientAddress", "t_patientCity", "t_patientProvince", "t_patientPostal", "t_patientPhone", "t_patientHIN",
            "t_patientPhoneWork", "t_patientPhoneCell", "d_confirmedEDD");

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        return getFormRecord(loggedInInfo, demographicNo, existingID, 1);
    }

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID, int pageNo) throws SQLException {
        Properties props = new Properties();
        FormBCAR2020DataDao bcar2020DataDao = SpringUtils.getBean(FormBCAR2020DataDao.class);
        FormBCAR2020TextDao bcar2020TextDao = SpringUtils.getBean(FormBCAR2020TextDao.class);
        // Regular data elements are stored as FormBCAR2020Data
        // Large text blobs are stored as FormBCAR2020Text
        List<FormBCAR2020Data> records = new ArrayList<>();
        List<FormBCAR2020Text> textRecords = new ArrayList<>();
        Date dob = null;

        if (existingID <= 0) {
            //Set default prop values
            this.setDemoProperties(loggedInInfo, demographicNo, props);

            //Set custom prop values
            props.setProperty("t_patientSurname", StringUtils.trimToEmpty(demographic.getLastName()));
            props.setProperty("t_patientGivenName", StringUtils.trimToEmpty(demographic.getFirstName()));
            props.setProperty("t_patientHIN", demographic.getHin());
            dob = UtilDateUtilities.calcDate(demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
            props.setProperty("t_patientDOB", UtilDateUtilities.DateToString(dob, "dd/MM/yyyy"));
            Provider p = demographic.getProvider();
            String providerName = "";
            if (p != null) {
                providerName = p.getFormattedName();
            }
            props.setProperty("t_primaryCareProvider", providerName);
            String fd = SxmlMisc.getXmlContent(demographic.getFamilyPhysician(), "fd");
            fd = fd != null ? fd : "";
            props.setProperty("t_familyPhysician", fd);
            props.setProperty("t_patientAddress", demographic.getAddress());
            props.setProperty("t_patientCity", demographic.getCity());
            props.setProperty("t_patientProvince", demographic.getProvince());
            props.setProperty("t_patientPostal", demographic.getPostal());
            props.setProperty("t_patientPhone", demographic.getPhone());
            props.setProperty("t_patientPreferredName", demographic.getPrefName() + " / " + demographic.getPronoun());
            props.setProperty("t_patientPhoneWork", demographic.getPhone2());
            String cell = demographicExtDao.getValueForDemoKey(demographicNo, "demo_cell");
            if (cell != null) {
                props.setProperty("t_patientPhoneCell", cell);
            }

            //Currently Unused
            props.setProperty("t_hinVer", demographic.getVer());

            if ("ON".equals(demographic.getHcType())) {
                props.setProperty("t_hinType", "OHIP");
            } else if ("QC".equals(demographic.getHcType())) {
                props.setProperty("t_hinType", "RAMQ");
            } else if ("BC".equals(demographic.getHcType())) {
                props.setProperty("t_hinType", "PHN");
            } else {
                props.setProperty("t_hinType", "OTHER");
            }

        } else {
            records.addAll(bcar2020DataDao.findFieldsForPage(existingID, pageNo));
            textRecords.addAll(bcar2020TextDao.findFieldsForPage(existingID, pageNo));
            props = getRecordValuesAsProperties(records, textRecords);

            // Reset certain fields every time, specifically the Patient Surname/GivenName/HIN and DOB 
            // PHSA did not want these to be "editable" and should stay in sync with the master demographic
            this.setDemoProperties(loggedInInfo, demographicNo, props);

            props.setProperty("t_patientSurname", StringUtils.trimToEmpty(demographic.getLastName()));
            props.setProperty("t_patientGivenName", StringUtils.trimToEmpty(demographic.getFirstName()));
            props.setProperty("t_patientHIN", demographic.getHin());
            dob = UtilDateUtilities.calcDate(demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
            props.setProperty("t_patientDOB", UtilDateUtilities.DateToString(dob, "dd/MM/yyyy"));
        }

        if (props.getProperty("pg" + pageNo + "_formDate") == null) {
            props.setProperty("pg" + pageNo + "_formDate", UtilDateUtilities.DateToString(new Date(), dateFormat));
        }

        return props;
    }

    @Deprecated
    /**
     * Use FrmBCAR20202Action instead
     * This is just required to extend FrmRecord 
     */
    public int saveFormRecord(Properties props) throws SQLException {

        int formId = StringUtils.isNotEmpty(props.getProperty("formId")) ? Integer.parseInt(props.getProperty("formId")) : 0;
        Set<String> keys = props.stringPropertyNames();
        for (String key : keys) {
            logger.info(key + " \t\t\t : \t " + props.getProperty(key));
        }

        return formId;
    }

    public String findActionValue(String submit) throws SQLException {
        return ((new FrmRecordHelp()).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return ((new FrmRecordHelp()).createActionURL(where, action, demoId, formId));
    }

    private Properties getRecordValuesAsProperties(List<FormBCAR2020Data> records, List<FormBCAR2020Text> textRecords) {
        Properties properties = new Properties();
        if (records != null) {
            for (FormBCAR2020Data record : records) {
                properties.setProperty(record.getField(), record.getValue());
            }
        }
        if (textRecords != null) {
            for (FormBCAR2020Text textRecord : textRecords) {
                properties.setProperty(textRecord.getField(), textRecord.getValue());
            }
        }
        return properties;
    }

    public Boolean isGlobalField(String fieldName) {
        return globalFields.contains(fieldName);
    }

    public String createToggleOption(Properties props, String fieldName, String description) {
        return "<tr><td>\n" +
                "<input type=\"checkbox\" name=\"c_" + fieldName + "No\" " + Encode.forHtmlAttribute(props.getProperty("c_" + fieldName + "No", "").equals("X") ? "checked" : "") + " />\n" +
                "</td><td>\n" +
                "<input type=\"checkbox\" name=\"c_" + fieldName + "Yes\" " + Encode.forHtmlAttribute(props.getProperty("c_" + fieldName + "Yes", "").equals("X") ? "checked" : "") + " />\n" +
                "</td><td>\n" +
                "<div class=\"divFlex\">\n" +
                "" + description + "\n" +
                "<input type=\"text\" name=\"t_" + fieldName + "Details\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_" + fieldName + "Details", "")) + "\" />\n" +
                "</div></td></tr>";
    }

    public String createPrenatalVisitRow(Properties props, String rowCount) {
        String row = "<tr>\n" +
                "<td>\n" +
                "    <div class=\"div-center\">\n" +
                "        <input type=\"text\" id=\"d_prenatalVisitDate" + rowCount + "\" name=\"d_prenatalVisitDate" + rowCount + "\" title=\"17. Prenatal Visit Line " + rowCount + " - Visit Date\" size=\"9\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("d_prenatalVisitDate" + rowCount, "")) + "\" />\n" +
                "        <img src=\"../images/cal.gif\" id=\"d_prenatalVisitDate" + rowCount + "_cal\">\n" +
                "    </div>\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitGA" + rowCount + "\" title=\"17. Prenatal Visit Line " + rowCount + " - GA\" class=\"calcField\" size=\"8\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitGA" + rowCount, "")) + "\" onDblClick=\"getGAByFieldDate('t_prenatalVisitGA" + rowCount + "', 'd_confirmedEDD', 'd_prenatalVisitDate" + rowCount + "')\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitBP" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitBP" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitUrine" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitUrine" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" title=\"Enter a value in pounds(lbs) then double click to convert to kilograms(kg)\" name=\"t_prenatalVisitWt" + rowCount + "\" class=\"calcField\" size=\"10\" maxlength=\"150\" onDblClick=\"wtEnglish2Metric(this)\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitWt" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitFundus" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitFundus" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitFHR" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitFHR" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitFM" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitFM" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitPosition" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitPosition" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitComments" + rowCount + "\" class=\"text-style\" size=\"30\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitComments" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitNextVisit" + rowCount + "\" class=\"text-style\" size=\"10\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitNextVisit" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "<td>\n" +
                "    <input type=\"text\" name=\"t_prenatalVisitInitials" + rowCount + "\" class=\"text-style\" size=\"3\" maxlength=\"150\" value=\"" + UtilMisc.htmlEscape(props.getProperty("t_prenatalVisitInitials" + rowCount, "")) + "\" />\n" +
                "</td>\n" +
                "</tr>";
        return row;
    }
}
