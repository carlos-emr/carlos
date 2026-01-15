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
import java.sql.SQLException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.commn.model.Demographic;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import ca.openosp.openo.commn.dao.EncounterFormDao;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.dao.MeasurementDaoImpl.SearchCriteria;
import ca.openosp.openo.commn.model.EncounterForm;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.form.FrmRecordHelp;
import ca.openosp.openo.form.data.FrmData;
import ca.openosp.openo.form.util.FrmToXMLUtil;
import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctValidationsBean;
import ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctValidation;
import ca.openosp.openo.encounter.oscarMeasurements.prop.EctFormProp;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;
import ca.openosp.openo.util.UtilDateUtilities;

/*
 * Author: Ivy Chan
 * Company: iConcept Technologes Inc.
 * Created on: October 31, 2004
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FrmForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * To create a new form which can write to measurement and osdsf, you need to
     * ...
     * Create a xml file with all the measurement types named <formName>.xml (check
     * form/VTForm.xml as an example)
     * Create a new jsp file named <formName>.jsp (check form/formVT.jsp)
     * Create a new table named form<formName> which include the name of all the
     * input elements in the <formName>.jsp
     * Add the form description to encounterForm table of the database
     **/

    private String _dateFormat = "yyyy/MM/dd";

    public String execute()
            throws ServletException, IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "w", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }

        boolean valid = true;
        logger.debug("FrmForm2Action is called " + currentMem());

        logger.debug("current mem 1 " + currentMem());

        HttpSession session = request.getSession();
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        request.getSession().setAttribute("EctSessionBean", bean);

        String formName = (String) this.getValue("formName");
        logger.debug("formNme Top " + formName);

        String dateEntered = UtilDateUtilities.DateToString(new Date(), _dateFormat);
        // String visitCod = UtilDateUtilities.DateToString(new Date(),"yyyyMMdd");
        String today = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");

        logger.debug("current mem 2 " + currentMem());

        Properties props = new Properties();

        Vector measurementTypes = EctFormProp.getMeasurementTypes();
        logger.debug("num measurements " + measurementTypes.size());
        String demographicNo = null;
        String providerNo = (String) session.getAttribute("user");
        if (bean != null)
            demographicNo = bean.getDemographicNo();

        logger.debug("current mem 3 " + currentMem());

        valid = true;

        logger.debug("current mem 4 " + currentMem());

        String submit = request.getParameter("submit");

        EctMeasurementTypesBean mt;
        EctValidationsBean validation;
        EctValidation ectValidation = new EctValidation();
        // Validate each measurement
        long startTime = System.currentTimeMillis();

        logger.debug("current mem 5 " + currentMem());
        for (int i = 0; i < measurementTypes.size(); i++) {
            mt = (EctMeasurementTypesBean) measurementTypes.elementAt(i);
            validation = (EctValidationsBean) mt.getValidationRules().elementAt(0);
            String inputValue = (String) this.getValue(mt.getType() + "Value");
            String observationDate = (String) this.getValue(mt.getType() + "Date");
            if (observationDate == null) {
                observationDate = today;
            } else if (observationDate.compareTo("") == 0) {
                observationDate = today;
            }

            // parse the checkbox value
            inputValue = parseCheckBoxValue(inputValue, validation.getName());

            // validate
            valid = validate(inputValue, observationDate, mt, validation, request);
        }
        valid = ectValidation.isDate((String) this.getValue("visitCod"));

        logger.debug("current mem 6 " + currentMem());
        long endTime = System.currentTimeMillis();
        long delTime = endTime - startTime;
        logger.debug("Time spent on validation: " + Long.toString(delTime));

        if (valid) {
            DemographicData demoData = new DemographicData();
            Demographic demo = demoData
                    .getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
            logger.debug("is valid, procede write to table");
            // Store form information as properties for saving to form table
            props.setProperty("demographic_no", demographicNo);
            props.setProperty("provider_no", providerNo);
            props.setProperty("visitCod", (String) this.getValue("visitCod"));
            props.setProperty("dob", DemographicData.getDob(demo, "-"));
            props.setProperty("gender", demo.getSex());
            props.setProperty("surname", demo.getLastName());
            props.setProperty("givenName", demo.getFirstName());

            String diagnosisVT = (String) this.getValue("diagnosisVT");

            String subjective = (String) this.getValue("subjective");
            String objective = (String) this.getValue("objective");
            String assessment = (String) this.getValue("assessment");
            String plan = (String) this.getValue("plan");

            // for VTForm
            props.setProperty("Diagnosis", diagnosisVT);
            props.setProperty("Subjective", subjective);
            props.setProperty("Objective", objective);
            props.setProperty("Assessment", assessment);
            props.setProperty("Plan", plan);

            startTime = System.currentTimeMillis();
            for (int i = 0; i < measurementTypes.size(); i++) {
                logger.debug("current mem 7.1." + i + " " + currentMem());
                mt = (EctMeasurementTypesBean) measurementTypes.elementAt(i);
                validation = (EctValidationsBean) mt.getValidationRules().elementAt(0);
                String type = mt.getType();
                String inputValue = (String) this.getValue(type + "Value");
                String lastData = (String) this.getValue(type + "LastData");
                String lastDataEnteredDate = (String) this.getValue(type + "LastDataEnteredDate");

                String observationDate = (String) this.getValue(type + "Date");
                if (observationDate == null) {
                    observationDate = today;
                } else if (observationDate.compareTo("") == 0) {
                    observationDate = today;
                }

                String comments = (String) this.getValue(type + "Comments");

                logger.debug("type: " + type + " inputValue: " + inputValue);
                // parse the checkbox value
                inputValue = parseCheckBoxValue(inputValue, validation.getName());

                // Write to Measurement Table

                if (inputValue != null) {
                    if (submit.equalsIgnoreCase("exit") && !inputValue.equalsIgnoreCase(""))
                        write2MeasurementTable(demographicNo, providerNo, mt, inputValue, observationDate, comments);
                }

                // Store all input value as properties for saving to form table
                if (lastData != null)
                    props.setProperty(type + "LastData", lastData);
                if (lastDataEnteredDate != null)
                    props.setProperty(type + "LastDataEnteredDate", lastDataEnteredDate);

                props.setProperty(type + "Date", observationDate == null ? dateEntered : observationDate);
                props.setProperty(type + "Comments", comments == null ? "" : comments);

                if (!GenericValidator.isBlankOrNull(inputValue)) {
                    props.setProperty(type + "Value", inputValue);

                    if (type.equalsIgnoreCase("BP")) {
                        // extract SBP and DBP for blood pressure
                        String bp = inputValue;
                        if (bp != null) {
                            int sbpIndex = bp.indexOf("/");
                            if (sbpIndex >= 0) {
                                String sbp = bp.substring(0, sbpIndex);
                                String dbp = bp.substring(sbpIndex + 1);
                                props.setProperty("SBPValue", sbp);
                                props.setProperty("DBPValue", dbp);
                            }
                        }
                    }
                }
                logger.debug("current mem 7.2." + i + " " + currentMem());
            }
            endTime = System.currentTimeMillis();
            delTime = endTime - startTime;
            logger.debug("Time spent on write2Measurements: " + Long.toString(delTime));

            // Store the the form table for keeping the current record
            logger.debug("current mem 8 " + currentMem());
            try {
                String sql = "SELECT * FROM form" + formName + " WHERE demographic_no='" + demographicNo + "' AND ID=0";
                FrmRecordHelp frh = new FrmRecordHelp();
                frh.setDateFormat(_dateFormat);
                (frh).saveFormRecord(props, sql);
            } catch (SQLException e) {
                logger.error("Error", e);
            }

            logger.debug("current mem 9 " + currentMem());
            // Send to Mils thru xml-rpc
            Properties nameProps = convertName(formName);
            String xmlData = FrmToXMLUtil.convertToXml(loggedInInfo, measurementTypes, nameProps, props);
            String decisionSupportURL = connect2OSDSF(xmlData);
            request.setAttribute("decisionSupportURL", decisionSupportURL);
            logger.debug("current mem 9 " + currentMem());
        } else {
            // return to the orignal form
            return "/form/SetupForm.do?formName=" + formName + "&formId=0";
        }

        // return SUCCESS;
        // forward to the for with updated formId

        logger.debug("submit value: " + submit);
        if (submit.equalsIgnoreCase("exit")) {
            request.setAttribute("diagnosisVT", "See Vascular Tracker Template");
            return "/form/formSaveAndExit.jsp";
        }
        logger.debug("formName from Frm ForamAction" + formName);
        EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        EncounterForm encounterForm = encounterFormDao
                .find("../form/SetupForm.do?formName=" + formName + "&demographic_no=");
        String formNameByFormTable = encounterForm.getFormName();
        logger.debug("formNameByFormTable" + formNameByFormTable);
        String[] formPath = {"", "0"};
        try {
            formPath = (new FrmData()).getShortcutFormValue(demographicNo, formNameByFormTable);
        } catch (SQLException e) {
            logger.error("Error", e);
        }
        return "/form/SetupForm.do?formName=" + formName + "&formId=" + formPath[1];
    }

    private boolean validate(String inputValue, String observationDate, EctMeasurementTypesBean mt,
                             EctValidationsBean validation, HttpServletRequest request) {
        EctValidation ectValidation = new EctValidation();
        boolean valid = true;

        String inputTypeDisplay = mt.getTypeDesc();
        String inputValueName = mt.getType() + "Value";
        String inputDateName = mt.getType() + "Date";
        String regExp = validation.getRegularExp();

        double dMax = Double.parseDouble(validation.getMaxValue() == null ? "0" : validation.getMaxValue());
        double dMin = Double.parseDouble(validation.getMinValue() == null ? "0" : validation.getMinValue());
        int iMax = Integer.parseInt(validation.getMaxLength() == null ? "0" : validation.getMaxLength());
        int iMin = Integer.parseInt(validation.getMinLength() == null ? "0" : validation.getMinLength());
        int iIsDate = Integer.parseInt(validation.getIsDate() == null ? "0" : validation.getIsDate());
        int iIsNumeric = Integer.parseInt(validation.getIsNumeric() == null ? "0" : validation.getIsNumeric());

        if (!GenericValidator.isBlankOrNull(inputValue)) {
            if (iIsNumeric == 1 && !ectValidation.isInRange(dMax, dMin, inputValue)) {
                addActionError(getText("errors.range", new String[]{inputValueName}));
                valid = false;
            }
            if (!ectValidation.maxLength(iMax, inputValue)) {
                addActionError(getText("errors.maxlength", new String[]{inputValueName}));
                valid = false;
            }
            if (!ectValidation.minLength(iMin, inputValue)) {
                addActionError(getText("errors.minlength", new String[]{inputValueName}));
                valid = false;
            }
            if (!ectValidation.matchRegExp(regExp, inputValue)) {
                addActionError(getText("errors.invalid", new String[]{inputValueName}));
                valid = false;
            }
            if (mt.getType().equalsIgnoreCase("BP") && !ectValidation.isValidBloodPressure(regExp, inputValue)) {
                addActionError(getText("errors.bloodPressure", new String[]{inputValueName}));
                valid = false;
            }
            if (iIsDate == 1 && !ectValidation.isDate(inputValue)) {
                addActionError(getText("errors.invalidDate", new String[]{inputValueName}));
                valid = false;
            }
            if (!ectValidation.isDate(observationDate)) {
                addActionError(getText("errors.invalidDate", new String[]{inputValueName}));
                valid = false;
            }
        }
        return valid;
    }

    private boolean write2MeasurementTable(String demographicNo, String providerNo,
                                           EctMeasurementTypesBean mt, String inputValue,
                                           String dateObservedString, String comments) {
        boolean newDataAdded = false;
        if (!GenericValidator.isBlankOrNull(inputValue)) {
            SearchCriteria criteria = new SearchCriteria();
            criteria.setDemographicNo(demographicNo);
            criteria.setType(mt.getType());
            criteria.setDataField(inputValue);
            criteria.setMeasuringInstrc(mt.getMeasuringInstrc());
            criteria.setComments(comments);

            Date dateObserved = UtilDateUtilities.StringToDate(dateObservedString, "yyyy-MM-dd");
            criteria.setDateObserved(dateObserved);

            // Find if the same data has already been entered into the system
            MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
            List<Measurement> measurements = dao.find(criteria);

            if (measurements.isEmpty()) {
                newDataAdded = true;

                Measurement measurement = new Measurement();
                measurement.setType(mt.getType());
                measurement.setDemographicId(Integer.parseInt(demographicNo));
                measurement.setProviderNo(providerNo);
                measurement.setDataField(inputValue);
                measurement.setMeasuringInstruction(mt.getMeasuringInstrc());
                measurement.setComments(comments);
                measurement.setDateObserved(dateObserved);

                dao.persist(measurement);
            }
        }

        return newDataAdded;
    }

    private String connect2OSDSF(String xmlResult) {
        Vector data2OSDSF = new Vector();
        data2OSDSF.add("xml");
        data2OSDSF.add(xmlResult);
        String osdsfRPCURL = OscarProperties.getInstance().getProperty("osdsfRPCURL", null);
        if (osdsfRPCURL == null) {
            return null;
        }
        // data2OSDSF.add("dummy");
        // send to osdsf thru XMLRPC
        try {
            XmlRpcClient xmlrpc = new XmlRpcClient(osdsfRPCURL);
            String result = (String) xmlrpc.execute("vt.getAndSaveRlt", data2OSDSF);
            logger.debug("Reverse result: " + result);
            return result;
        } catch (XmlRpcException e) {
            logger.error("Error", e);
            return null;
        } catch (IOException e) {
            logger.error("Error", e);
            return null;
        }
        /*
         * catch(MalformedURLException e){
         * logger.error("Error", e);
         * }
         */
    }

    private Properties convertName(String formName) {
        Properties osdsf = new Properties();
        InputStream is = getClass().getResourceAsStream("/../../form/" + formName + "2Osdsf.properties");
        try {
            osdsf.load(is);
        } catch (Exception e) {
            logger.debug("Error, file " + formName + ".properties not found.");
        }

        try {
            is.close();
        } catch (IOException e) {
            logger.debug("IO error.");
            logger.error("Error", e);
        }
        return osdsf;
    }

    private String parseCheckBoxValue(String inputValue, String validationName) {

        if (validationName.equalsIgnoreCase("Yes/No")) {
            /*
             * if(inputValue==null)
             * inputValue="no";
             * else
             */
            if (inputValue != null) {
                if (inputValue.equalsIgnoreCase("on"))
                    inputValue = "yes";
                else if (inputValue.equalsIgnoreCase("off"))
                    inputValue = "no";
            }
        }
        return inputValue;
    }

    public String currentMem() {
        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        long Used = total - free;
        return "Total " + total + " Free " + free + " USED " + Used;
    }

    private Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }

}
