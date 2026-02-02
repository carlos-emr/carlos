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

package ca.openosp.openo.form.pageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.Misc;
import ca.openosp.openo.commn.dao.BillingDao;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Billing;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.db.DBHandler;
import ca.openosp.openo.encounter.data.EctEChartBean;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import ca.openosp.openo.encounter.oscarMeasurements.util.EctFindMeasurementTypeUtil;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;
import ca.openosp.openo.prescript.data.RxPatientData;
import ca.openosp.openo.prescript.data.RxPrescriptionData;
import ca.openosp.openo.util.UtilDateUtilities;

/*
 * @Author: Ivy Chan
 * @Company: iConcept Technologes Inc.
 * @Created on: October 31, 2004
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class FrmSetupForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private String _dateFormat = "yyyy-MM-dd";
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    
    // Pattern to validate form names - only alphanumeric characters and underscores allowed
    private static final Pattern VALID_FORM_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        /**
         * To create a new form which can write to measurement, you need to ...
         * Create a xml file with all the measurement types named <formName>.xml (check form/VTForm.xml as an example)
         * Create a new jsp file named <formName>.jsp (check form/formVT.jsp)
         * Create a new table named form<formName> which include the name of all the input elements in the <formName>.jsp
         * Add the form description to encounterForm table of the database
         **/
        //System.gc();
        MiscUtils.getLogger().debug("SetupFormAction is called");
        HttpSession session = request.getSession(true);

        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        EctEChartBean chartBean = new EctEChartBean();
        String contextPath = request.getContextPath();
        String formId = request.getParameter("formId");
        this.setValue("formId", formId == null ? "0" : formId);
        String demo = request.getParameter("demographic_no");
        String providerNo = (String) session.getAttribute("user");
        if (demo == null || bean != null) {
            request.getSession().setAttribute("EctSessionBean", bean);
            demo = bean.getDemographicNo();

        }

        if (demo != null) {
            chartBean.setEChartBean(demo);
        }

        String ongoingConcern = chartBean.ongoingConcerns;
        String formName = request.getParameter("formName");
        
        // Validate formName to prevent path traversal attacks
        if (formName == null || !isValidFormName(formName)) {
            MiscUtils.getLogger().warn("Invalid form name attempted: {}", formName != null ? formName.replaceAll("[\\r\\n\\t]", "_") : "null");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid form name");
            return NONE;
        }
        
        String today = UtilDateUtilities.DateToString(new Date(), _dateFormat);
        String visitCod = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");

        List drugLists = getDrugList(loggedInInfo, demo);
        List allergyList = getDrugAllegyList(loggedInInfo, demo);

        Properties currentRec = getFormRecord(formName, formId, demo);

        request.setAttribute("today", today);
        //specifically for VT Form
        request.setAttribute("drugs", drugLists);
        request.setAttribute("allergies", allergyList);
        request.setAttribute("ongoingConcerns", chartBean.ongoingConcerns.equalsIgnoreCase("") ? "None" : chartBean.ongoingConcerns);
        if (currentRec != null) {
            this.setValue("visitCod", currentRec.getProperty("visitCod", ""));
            this.setValue("diagnosisVT", currentRec.getProperty("Diagnosis", ""));
            this.setValue("subjective", currentRec.getProperty("Subjective", ""));
            this.setValue("objective", currentRec.getProperty("Objective", ""));
            this.setValue("assessment", currentRec.getProperty("Assessment", ""));
            this.setValue("plan", currentRec.getProperty("Plan", ""));
        } else {
            this.setValue("visitCod", visitCod);
        }

        try {
            MiscUtils.getLogger().debug("formId=" + formId + "opening " + formName + ".xml");
            // formName already validated above, safe to use in resource path
            InputStream is = getClass().getResourceAsStream("/../../form/" + formName + ".xml");
            Vector measurementTypes = EctFindMeasurementTypeUtil.checkMeasurmentTypes(is, formName);
            EctMeasurementTypesBean mt;

            ResultSet rs;

            for (int i = 0; i < measurementTypes.size(); i++) {
                mt = (EctMeasurementTypesBean) measurementTypes.elementAt(i);
                request.setAttribute(mt.getType(), mt.getType());
                request.setAttribute(mt.getType() + "Display", mt.getTypeDisplayName());
                request.setAttribute(mt.getType() + "Desc", mt.getTypeDesc());
                request.setAttribute(mt.getType() + "MeasuringInstrc", mt.getMeasuringInstrc());

                addLastData(mt, demo);
                request.setAttribute(mt.getType() + "LastData", mt.getLastData() == null ? "" : mt.getLastData());
                request.setAttribute(mt.getType() + "LDDate", mt.getLastDateEntered() == null ? "" : mt.getLastDateEntered());

                if (currentRec != null) {
                    this.setValue(mt.getType() + "Value", currentRec.getProperty(mt.getType() + "Value", ""));
                    this.setValue(mt.getType() + "Date", currentRec.getProperty(mt.getType() + "Date", ""));
                    this.setValue(mt.getType() + "Comments", currentRec.getProperty(mt.getType() + "Comments", ""));
                    request.setAttribute(mt.getType() + "Date", currentRec.getProperty(mt.getType() + "Date", ""));
                    request.setAttribute(mt.getType() + "Comments", currentRec.getProperty(mt.getType() + "Comments", ""));
                } else {
                    // Set default values for new form entries
                    this.setValue(mt.getType() + "Value", "");
                    this.setValue(mt.getType() + "Date", today);
                    request.setAttribute(mt.getType() + "Date", today);
                    request.setAttribute(mt.getType() + "Comments", "");
                }

            }
            is.close();
        }
		/*
		catch (SQLException e) {
		    MiscUtils.getLogger().error("Error", e);
		}
		/*catch (Exception e) {
		    MiscUtils.getLogger().error("Error", e);
		} */ catch (IOException e) {
            MiscUtils.getLogger().debug("IO error.");
            MiscUtils.getLogger().debug("Error, file " + formName + ".xml not found.");
            MiscUtils.getLogger().debug("This file must be placed at www/form");
            MiscUtils.getLogger().error("Error", e);
        }
        // formName already validated above, safe to use in redirect URL
        response.sendRedirect(contextPath + "/form/form" + formName + ".jsp");
        return NONE;
    }

    private List getDrugList(LoggedInInfo loggedInInfo, String demographicNo) {
        List drugs = new LinkedList();
        String fluShot = getFluShotBillingDate(demographicNo);

        if (fluShot != null) drugs.add(fluShot + "     Flu Shot");

        RxPatientData.Patient p = RxPatientData.getPatient(loggedInInfo, Integer.parseInt(demographicNo));
        RxPrescriptionData.Prescription[] prescribedDrugs = p.getPrescribedDrugsUnique();
        if (prescribedDrugs.length == 0 && fluShot == null) drugs = null;
        for (int i = 0; i < prescribedDrugs.length; i++) {
            drugs.add(prescribedDrugs[i].getRxDate().toString() + "    " + prescribedDrugs[i].getRxDisplay());
        }

        return drugs;
    }

    private List getDrugAllegyList(LoggedInInfo loggedInInfo, String demographicNo) {
        List allergyLst = new LinkedList();

        RxPatientData.Patient p = RxPatientData.getPatient(loggedInInfo, Integer.parseInt(demographicNo));
        Allergy[] allergies = p.getActiveAllergies();
        if (allergies.length == 0) allergyLst = null;
        for (int i = 0; i < allergies.length; i++) {
            Allergy allergy = allergies[i];
            allergyLst.add(allergies[i].getEntryDate() + " " + allergy.getDescription() + " " + Allergy.getTypeDesc(allergy.getTypeCode()));
        }

        return allergyLst;
    }

    /**
     * Retrieves the most recent flu shot billing date for a patient.
     * Searches for billings with codes G590A (influenza vaccine) or G591A.
     *
     * @param demoNo the demographic number as a String
     * @return the billing date formatted as a String if found, null otherwise
     */
    private String getFluShotBillingDate(String demoNo) {
        try {
            BillingDao dao = SpringUtils.getBean(BillingDao.class);
            List<Object[]> billings = dao.findBillings(ConversionUtils.fromIntString(demoNo),
                Arrays.asList(new String[]{"G590A", "G591A"}));
            if (billings.isEmpty()) {
                return null;
            }
            Object[] container = billings.get(0);
            Billing billing = (Billing) container[0];
            return ConversionUtils.toDateString(billing.getBillingDate());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error retrieving flu shot billing date for demographic " + demoNo, e);
            return null;
        }
    }

    private Properties getFormRecord(String formName, String formId, String demographicNo) {
        Properties props = new Properties();
        try {

            if (formId != null) {
                if (Integer.parseInt(formId) > 0) {
                    // Validate formName to prevent SQL injection
                    if (!isValidFormName(formName)) {
                        MiscUtils.getLogger().warn("Invalid form name in getFormRecord: " + formName);
                        return null;
                    }
                    
                    // Using parameterized values for formId and demographicNo
                    // Note: Table name cannot be parameterized, but formName is validated above
                    String sql = "SELECT * FROM form" + formName + " WHERE ID=? AND demographic_no=?";
                    Connection connection = DbConnectionFilter.getThreadLocalDbConnection();
                    PreparedStatement ps = connection.prepareStatement(sql);
                    ps.setInt(1, Integer.parseInt(formId));
                    ps.setInt(2, Integer.parseInt(demographicNo));
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        ResultSetMetaData md = rs.getMetaData();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            String name = md.getColumnName(i);
                            String value = Misc.getString(rs, i);
                            if (value != null) props.setProperty(name, value);
                        }
                    }
                } else return null;
            } else return null;
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return props;
    }

    private void addLastData(EctMeasurementTypesBean mt, String demo) {
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        List<Measurement> measurements = dao.findByIdTypeAndInstruction(Integer.parseInt(demo), mt.getType(), mt.getMeasuringInstrc());
        if (!measurements.isEmpty()) {
            Measurement measurement = measurements.iterator().next();
            mt.setLastData(measurement.getDataField());
            mt.setLastDateEntered(UtilDateUtilities.DateToString(measurement.getCreateDate(), "yyyy-MM-dd"));
        }
    }
    private Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }
    
    /**
     * Validates that a form name contains only safe characters to prevent path traversal attacks.
     * Only allows alphanumeric characters and underscores.
     * 
     * @param formName The form name to validate
     * @return true if the form name is valid, false otherwise
     */
    private boolean isValidFormName(String formName) {
        if (formName == null || formName.isEmpty()) {
            return false;
        }
        
        // Check for path traversal attempts
        if (formName.contains("..") || formName.contains("/") || formName.contains("\\")) {
            return false;
        }
        
        // Only allow alphanumeric characters and underscores
        return VALID_FORM_NAME_PATTERN.matcher(formName).matches();
    }
}
