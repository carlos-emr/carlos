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

import java.sql.SQLException;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Abstract base class for all clinical form record handlers in the CARLOS EMR form subsystem.
 *
 * <p>Provides the template pattern for loading, saving, and navigating clinical forms.
 * Subclasses implement form-specific logic for various medical forms (Rourke growth charts,
 * BCAR antenatal records, lab requisitions, etc.) while inheriting common demographic
 * data loading and date formatting capabilities.</p>
 *
 * <p>Each concrete subclass corresponds to a specific medical form type and is instantiated
 * by {@link FrmRecordFactory} based on the form name.</p>
 *
 * @see FrmRecordFactory
 * @see FrmRecordHelp
 * @since 2026-03-17
 */
public abstract class FrmRecord {

    protected Demographic demographic;
    protected DemographicExt demographicExt;
    protected Map<String, String> demographicExtMap;

    protected DemographicManager demographicManager;
    protected DemographicExtDao demographicExtDao;

    protected java.util.Date date;
    protected String dateFormat;

    /**
     * Retrieves the form record data for a specific patient and form instance.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information
     * @param demographicNo int the patient's demographic number
     * @param existingID int the existing form record ID, or 0 for a new form
     * @return Properties containing the form field name-value pairs
     * @throws SQLException if a database access error occurs
     */
    public abstract Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException;

    /**
     * Saves the form record data to the database.
     *
     * @param props Properties containing the form field name-value pairs to persist
     * @return int the generated form record ID for new records
     * @throws SQLException if a database access error occurs
     */
    public abstract int saveFormRecord(Properties props) throws SQLException;

    public abstract String findActionValue(String submit) throws SQLException;

    public abstract String createActionURL(String where, String action, String demoId, String formId) throws SQLException;

    public Properties getGraph(LoggedInInfo loggedInInfo, int demographicNo, int existingID) {
        return new Properties();
    }

    public Properties getGraph(int demographicNo, int existingID) {
        return new Properties();
    }

    public Properties getCaisiFormRecord(int demographicNo, int existingID, int providerNo, int programNo) {
        return new Properties();
    }

    public void setGraphType(String graphType) { /*Rourke needs to know whether plotting head circ or height*/
    }


    public FrmRecord() {
        this.demographicManager = SpringUtils.getBean(DemographicManager.class);
        this.demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    }


    protected void setDemoProperties(LoggedInInfo loggedInInfo, int demographicNo, Properties demoProps) {

        this.setDemographic(loggedInInfo, demographicNo);

        date = UtilDateUtilities.calcDate(demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
        demoProps.setProperty("demographic_no", demographic.getDemographicNo().toString());

        demoProps.setProperty("c_surname", StringUtils.trimToEmpty(demographic.getLastName()));
        demoProps.setProperty("c_givenName", StringUtils.trimToEmpty(demographic.getFirstName()));
        demoProps.setProperty("c_address", StringUtils.trimToEmpty(demographic.getAddress()));
        demoProps.setProperty("c_city", StringUtils.trimToEmpty(demographic.getCity()));
        demoProps.setProperty("c_province", StringUtils.trimToEmpty(demographic.getProvince()));
        demoProps.setProperty("c_postal", StringUtils.trimToEmpty(demographic.getPostal()));
        demoProps.setProperty("c_phn", StringUtils.trimToEmpty(demographic.getHin()));
        demoProps.setProperty("pg1_dateOfBirth", UtilDateUtilities.DateToString(date, dateFormat));
        demoProps.setProperty("pg1_age", String.valueOf(UtilDateUtilities.getNumYears(date, GregorianCalendar.getInstance().getTime())));
        demoProps.setProperty("c_phone", StringUtils.trimToEmpty(demographic.getPhone()));
        demoProps.setProperty("c_phoneAlt1", StringUtils.trimToEmpty(demographic.getPhone2()));

        String rd = SxmlMisc.getXmlContent(demographic.getFamilyDoctor(), "rd");
        rd = rd != null ? rd : "";
        demoProps.setProperty("pg1_famPhy", rd);

        Map<String, String> demoExt = demographicExtDao.getAllValuesForDemo(demographicNo);
        String cell = demoExt.get("demo_cell");
        if (cell != null) {
            demoProps.setProperty("c_phoneAlt2", cell);
        }
    }

    protected void setDemoCurProperties(LoggedInInfo loggedInInfo, int demographicNo, Properties demoProps) {

        this.setDemographic(loggedInInfo, demographicNo);

        demoProps.setProperty("c_surname_cur", StringUtils.trimToEmpty(demographic.getLastName()));
        demoProps.setProperty("c_givenName_cur", StringUtils.trimToEmpty(demographic.getFirstName()));
        demoProps.setProperty("c_address_cur", StringUtils.trimToEmpty(demographic.getAddress()));
        demoProps.setProperty("c_city_cur", StringUtils.trimToEmpty(demographic.getCity()));
        demoProps.setProperty("c_province_cur", StringUtils.trimToEmpty(demographic.getProvince()));
        demoProps.setProperty("c_postal_cur", StringUtils.trimToEmpty(demographic.getPostal()));
        demoProps.setProperty("c_phn_cur", StringUtils.trimToEmpty(demographic.getHin()));
        demoProps.setProperty("c_phone_cur", StringUtils.trimToEmpty(demographic.getPhone()));
        demoProps.setProperty("c_phoneAlt1_cur", StringUtils.trimToEmpty(demographic.getPhone2()));

        Map<String, String> demoExt = demographicExtDao.getAllValuesForDemo(demographicNo);
        String cell = demoExt.get("demo_cell");
        if (cell != null) {
            demoProps.setProperty("c_phoneAlt2_cur", cell);
        }
    }

    protected void setDemographic(LoggedInInfo loggedInInfo, int demographicNo) {
        if (this.demographicManager != null) {
            this.demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
            this.setDemographicExt(demographicNo);
        }
    }

    protected void setDemographicExt(int demographicNo) {
        if (this.demographicExtDao != null) {
            this.demographicExt = demographicExtDao.getDemographicExt(demographicNo);
            this.demographicExtMap = demographicExtDao.getAllValuesForDemo(demographicNo);
        }
    }
}
