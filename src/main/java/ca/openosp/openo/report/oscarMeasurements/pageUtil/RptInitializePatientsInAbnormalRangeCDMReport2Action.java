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

package ca.openosp.openo.report.oscarMeasurements.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.Validations;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.encounter.oscarMeasurements.pageUtil.EctValidation;
import ca.openosp.openo.report.oscarMeasurements.data.RptMeasurementsData;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class RptInitializePatientsInAbnormalRangeCDMReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }


        RptMeasurementsData mData = new RptMeasurementsData();
        String[] patientSeenCheckbox = this.getPatientSeenCheckbox();
        String startDateA = this.getStartDateA();
        String endDateA = this.getEndDateA();

        ArrayList<String> reportMsg = new ArrayList<String>();

        if (!validateForm()) {
            MiscUtils.getLogger().debug("the form is invalid");
            response.sendRedirect(request.getContextPath() + "/oscarReport/oscarMeasurements/InitializePatientsInAbnormalRangeCDMReport.jsp");
            return NONE;
        }

        if (patientSeenCheckbox != null) {
            int nbPatient = mData.getNbPatientSeen(startDateA, endDateA);
            String msg = getText("oscarReport.CDMReport.msgPatientSeen", new String[]{Integer.toString(nbPatient), startDateA, endDateA});
            MiscUtils.getLogger().debug(msg);
            reportMsg.add(msg);
            reportMsg.add("");
        }

        getInAbnormalRangePercentage(reportMsg);
        String title = getText("oscarReport.CDMReport.msgPercentageOfPatientInAbnormalRange");
        request.setAttribute("title", title);
        request.setAttribute("messages", reportMsg);

        return SUCCESS;
    }

    /*****************************************************************************************
     * validate the input value
     *
     * @return boolean
     ******************************************************************************************/
    private boolean validateForm() {
        EctValidation ectValidation = new EctValidation();

        String[] startDateC = this.getStartDateC();
        String[] endDateC = this.getEndDateC();
        String[] upperBound = this.getUpperBound();
        String[] lowerBound = this.getLowerBound();
        String[] abnormalCheckbox = this.getAbnormalCheckbox();
        boolean valid = true;

        if (abnormalCheckbox != null) {

            for (int i = 0; i < abnormalCheckbox.length; i++) {
                int ctr = Integer.parseInt(abnormalCheckbox[i]);
                String startDate = startDateC[ctr];
                String endDate = endDateC[ctr];
                String upper = upperBound[ctr];
                String lower = lowerBound[ctr];
                String measurementType = (String) this.getValue("measurementTypeC" + ctr);
                String sNumMInstrc = (String) this.getValue("mNbInstrcsC" + ctr);
                int iNumMInstrc = Integer.parseInt(sNumMInstrc);
                String upperMsg = "The upper bound value of " + measurementType;
                String lowerMsg = "The lower bound value of " + measurementType;

                if (!ectValidation.isDate(startDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));

                    valid = false;
                }
                if (!ectValidation.isDate(endDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));

                    valid = false;
                }
                for (int j = 0; j < iNumMInstrc; j++) {

                    String mInstrc = (String) this.getValue("mInstrcsCheckboxC" + ctr + j);
                    if (mInstrc != null) {
                        List<Validations> vs = ectValidation.getValidationType(measurementType, mInstrc);
                        String regExp = null;
                        double dMax = 0;
                        double dMin = 0;

                        if (!vs.isEmpty()) {
                            Validations v = vs.iterator().next();
                            dMax = v.getMaxValue();
                            dMin = v.getMinValue();
                            regExp = v.getRegularExp();
                        }

                        if (!ectValidation.isInRange(dMax, dMin, upper)) {
                            addActionError(getText("errors.range", new String[]{upperMsg, Double.toString(dMin), Double.toString(dMax)}));

                            valid = false;
                        } else if (!ectValidation.isInRange(dMax, dMin, lower)) {
                            addActionError(getText("errors.range", new String[]{lowerMsg, Double.toString(dMin), Double.toString(dMax)}));

                            valid = false;
                        } else if (!ectValidation.matchRegExp(regExp, upper)) {
                            addActionError(getText("errors.invalid", new String[]{upperMsg}));

                            valid = false;
                        } else if (!ectValidation.matchRegExp(regExp, lower)) {
                            addActionError(getText("errors.invalid", lowerMsg));

                            valid = false;
                        } else if (!ectValidation.isValidBloodPressure(regExp, upper)) {
                            addActionError(getText("error.bloodPressure"));

                            valid = false;
                        } else if (!ectValidation.isValidBloodPressure(regExp, lower)) {
                            addActionError(getText("error.bloodPressure"));

                            valid = false;
                        }

                    }
                }
            }
        }
        return valid;
    }

    /**
     * Gets the number of Patient in the abnormal range during a time period
     *
     * @return ArrayList which contain the result in String format
     */
    private ArrayList<String> getInAbnormalRangePercentage(ArrayList<String> metGLPercentageMsg) {
        String[] startDateC = this.getStartDateC();
        String[] endDateC = this.getEndDateC();
        String[] upperBound = this.getUpperBound();
        String[] lowerBound = this.getLowerBound();
        String[] abnormalCheckbox = this.getAbnormalCheckbox();
        RptCheckGuideline checkGuideline = new RptCheckGuideline();
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);

        if (abnormalCheckbox != null) {
            try {
                MiscUtils.getLogger().debug("the length of abnormal range checkbox is " + abnormalCheckbox.length);

                for (int i = 0; i < abnormalCheckbox.length; i++) {
                    int ctr = Integer.parseInt(abnormalCheckbox[i]);
                    MiscUtils.getLogger().debug("the value of abnormal range Checkbox is: " + abnormalCheckbox[i]);
                    String startDate = startDateC[ctr];
                    String endDate = endDateC[ctr];
                    String upper = upperBound[ctr];
                    String lower = lowerBound[ctr];
                    String measurementType = (String) this.getValue("measurementTypeC" + ctr);
                    String sNumMInstrc = (String) this.getValue("mNbInstrcsC" + ctr);
                    int iNumMInstrc = Integer.parseInt(sNumMInstrc);
                    double nbMetGL = 0;
                    double metGLPercentage = 0;

                    for (int j = 0; j < iNumMInstrc; j++) {
                        metGLPercentage = 0;
                        nbMetGL = 0;

                        String mInstrc = (String) this.getValue("mInstrcsCheckboxC" + ctr + j);
                        if (mInstrc != null) {
                            List<Object[]> os = dao.findLastEntered(ConversionUtils.fromDateString(startDate),
                                    ConversionUtils.fromDateString(endDate), measurementType, mInstrc);
                            double nbGeneral = 0;
                            if (measurementType.compareTo("BP") == 0) {
                                for (Object[] o : os) {
                                    Integer demographicNo = (Integer) o[0];
                                    Date maxDateEntered = (Date) o[1];

                                    for (Measurement m : dao.findByDemographicNoTypeAndDate(demographicNo, maxDateEntered, measurementType, mInstrc)) {
                                        if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), upper, "<")
                                                && checkGuideline.isBloodPressureMetGuideline(m.getDataField(), lower, ">")) {
                                            nbMetGL++;
                                        }
                                    }
                                    nbGeneral++;
                                }
                                if (nbGeneral != 0) {
                                    MiscUtils.getLogger().debug("the total number of patients seen: "
                                            + nbGeneral + " nb of them pass the test: " + nbMetGL);
                                    metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                                }

                                String[] param = {startDate, endDate, measurementType, mInstrc, lower, upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsInAbnormalRange", param);
                                MiscUtils.getLogger().debug(msg);
                                metGLPercentageMsg.add(msg);
                            } else if (checkGuideline.getValidation(measurementType) == 1) {
                                for (Object[] o : os) {
                                    Integer demographicNo = (Integer) o[0];
                                    Date maxDateEntered = (Date) o[1];

                                    List<Object[]> dfs = dao.findByDemoNoDateTypeMeasuringInstrAndDataField(demographicNo,
                                            maxDateEntered, measurementType, mInstrc, upper, lower);

                                    if (!dfs.isEmpty()) {
                                        nbMetGL++;
                                    }
                                    nbGeneral++;
                                }

                                if (nbGeneral != 0) {
                                    metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                                }

                                String[] param = {startDate, endDate, measurementType, mInstrc, lower, upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsInAbnormalRange", param);
                                MiscUtils.getLogger().debug(msg);
                                metGLPercentageMsg.add(msg);
                            } else {
                                for (Object[] o : os) {
                                    Integer demographicNo = (Integer) o[0];
                                    Date maxDateEntered = (Date) o[1];

                                    for (Measurement m : dao.findByDemographicNoTypeAndDate(demographicNo, maxDateEntered, measurementType, mInstrc)) {
                                        if (checkGuideline.isYesNoMetGuideline(m.getDataField(), upper)) {
                                            nbMetGL++;
                                        }
                                        break;
                                    }
                                    nbGeneral++;
                                }
                                if (nbGeneral != 0) {
                                    metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                                }
                                String[] param = {startDate, endDate, measurementType, mInstrc, upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsIs", param);
                                MiscUtils.getLogger().debug(msg);
                                metGLPercentageMsg.add(msg);
                            }
                        }
                    }

                    // percentage of patients who are in abnormal range for the same test with all measuring instruction
                    metGLPercentage = 0;
                    nbMetGL = 0;

                    List<Object[]> os = dao.findLastEntered(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), measurementType);
                    double nbGeneral = 0;

                    if (measurementType.compareTo("BP") == 0) {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];

                            for (Measurement m : dao.findByDemoNoDateAndType(demographicNo, maxDateEntered, measurementType)) {
                                if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), upper, "<")
                                        && checkGuideline.isBloodPressureMetGuideline(m.getDataField(), lower, ">")) {
                                    nbMetGL++;
                                }
                            }
                            nbGeneral++;
                        }
                        if (nbGeneral != 0) {
                            MiscUtils.getLogger().debug("the total number of patients seen: " + nbGeneral + " nb of them pass the test: " + nbMetGL);
                            metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, "", lower, upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsInAbnormalRange", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    } else if (checkGuideline.getValidation(measurementType) == 1) {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];
                            List<Object[]> data = dao.findByDemoNoDateTypeAndDataField(demographicNo, maxDateEntered, measurementType, upper, lower);
                            if (!data.isEmpty()) {
                                nbMetGL++;
                            }
                            nbGeneral++;
                        }

                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, "", lower, upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsInAbnormalRange", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    } else {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];

                            for (Measurement m : dao.findByDemoNoDateAndType(demographicNo, maxDateEntered, measurementType)) {
                                if (checkGuideline.isYesNoMetGuideline(m.getDataField(), upper)) {
                                    nbMetGL++;
                                }
                            }
                            nbGeneral++;
                        }
                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round(nbMetGL / nbGeneral * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, "", upper, "(" + nbMetGL + "/" + nbGeneral + ")" + Double.toString(metGLPercentage)};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsIs", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    }
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            MiscUtils.getLogger().debug("guideline checkbox is null");
        }
        return metGLPercentageMsg;
    }

    private final Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }

    private String[] patientSeenCheckbox;

    public String[] getPatientSeenCheckbox() {
        return patientSeenCheckbox;
    }

    public void setPatientSeenCheckbox(String[] patientSeenCheckbox) {
        this.patientSeenCheckbox = patientSeenCheckbox;
    }

    private String startDateA;

    public String getStartDateA() {
        return startDateA;
    }

    public void setStartDateA(String startDateA) {
        this.startDateA = startDateA;
    }

    private String endDateA;

    public String getEndDateA() {
        return endDateA;
    }

    public void setEndDateA(String endDateA) {
        this.endDateA = endDateA;
    }


    /***************************************************************
     *Getter and Setter for Percentage of Patient in Abnormal range
     **************************************************************/
    private String[] abnormalCheckbox;

    public String[] getAbnormalCheckbox() {
        return abnormalCheckbox;
    }

    public void setAbnormalCheckbox(String[] abnormalCheckbox) {
        this.abnormalCheckbox = abnormalCheckbox;
    }

    private String[] startDateC;

    public String[] getStartDateC() {
        return startDateC;
    }

    public void setStartDateC(String[] startDateC) {
        this.startDateC = startDateC;
    }

    private String[] endDateC;

    public String[] getEndDateC() {
        return endDateC;
    }

    public void setEndDateC(String[] endDateC) {
        this.endDateC = endDateC;
    }

    private String[] upperBound;

    public String[] getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(String[] upperBound) {
        this.upperBound = upperBound;
    }

    private String[] lowerBound;

    public String[] getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(String[] lowerBound) {
        this.lowerBound = lowerBound;
    }

}
