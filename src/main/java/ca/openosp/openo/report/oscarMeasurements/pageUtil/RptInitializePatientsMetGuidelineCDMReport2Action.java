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
import ca.openosp.openo.commn.dao.forms.FormsDao;
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

public class RptInitializePatientsMetGuidelineCDMReport2Action extends ActionSupport {
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

        ArrayList reportMsg = new ArrayList();

        if (!validate(request)) {
            MiscUtils.getLogger().debug("the form is invalid");
            response.sendRedirect(request.getContextPath() + "/oscarReport/oscarMeasurements/InitializePatientsMetGuidelineCDMReport.jsp");
            return NONE;
        }

        if (patientSeenCheckbox != null) {
            int nbPatient = mData.getNbPatientSeen(startDateA, endDateA);
            String msg = getText("oscarReport.CDMReport.msgPatientSeen", new String[]{Integer.toString(nbPatient), startDateA, endDateA});
            MiscUtils.getLogger().debug(msg);
            reportMsg.add(msg);
            reportMsg.add("");
        }

        getMetGuidelinePercentage(reportMsg);
        //getPatientsMetAllSelectedGuideline(db, frm, reportMsg, request);

        String title = getText("oscarReport.CDMReport.msgPercentageOfPatientWhoMetGuideline");
        request.setAttribute("title", title);
        request.setAttribute("messages", reportMsg);

        return SUCCESS;
    }

    /*****************************************************************************************
     * validate the input value
     *
     * @return boolean
     ******************************************************************************************/
    private boolean validate(HttpServletRequest request) {
        EctValidation ectValidation = new EctValidation();

        String[] startDateB = this.getStartDateB();
        String[] endDateB = this.getEndDateB();
        String[] idB = this.getIdB();
        String[] guidelineB = this.getGuidelineB();
        String[] guidelineCheckbox = this.getGuidelineCheckbox();

        boolean valid = true;

        if (guidelineCheckbox != null) {
            for (int i = 0; i < guidelineCheckbox.length; i++) {
                int ctr = Integer.parseInt(guidelineCheckbox[i]);
                String startDate = startDateB[ctr];
                String endDate = endDateB[ctr];
                String guideline = guidelineB[ctr];
                String measurementType = (String) this.getValue("measurementType" + ctr);
                String sNumMInstrc = (String) this.getValue("mNbInstrcs" + ctr);
                int iNumMInstrc = Integer.parseInt(sNumMInstrc);

                if (!ectValidation.isDate(startDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));
                    valid = false;
                }
                if (!ectValidation.isDate(endDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));
                    valid = false;
                }
                for (int j = 0; j < iNumMInstrc; j++) {

                    String mInstrc = (String) this.getValue("mInstrcsCheckbox" + ctr + j);
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

                        if (!ectValidation.isInRange(dMax, dMin, guideline)) {
                            addActionError(getText("errors.range", new String[]{measurementType, Double.toString(dMin), Double.toString(dMax)}));
                            valid = false;
                        } else if (!ectValidation.matchRegExp(regExp, guideline)) {
                            addActionError(getText("errors.invalid", measurementType));
                            valid = false;
                        } else if (!ectValidation.isValidBloodPressure(regExp, guideline)) {
                            addActionError(getText("error.bloodPressure"));
                            valid = false;
                        }
                    }
                }
            }
        }
        return valid;
    }

    /*****************************************************************************************
     * get the number of Patient met the specific guideline during aspecific time period
     *
     * @return ArrayList which contain the result in String format
     ******************************************************************************************/
    private ArrayList getMetGuidelinePercentage(ArrayList metGLPercentageMsg) {
        String[] startDateB = this.getStartDateB();
        String[] endDateB = this.getEndDateB();
        String[] idB = this.getIdB();
        String[] guidelineB = this.getGuidelineB();
        String[] guidelineCheckbox = this.getGuidelineCheckbox();
        RptCheckGuideline checkGuideline = new RptCheckGuideline();

        if (guidelineCheckbox == null) {
            return metGLPercentageMsg;
        }

        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        FormsDao fDao = SpringUtils.getBean(FormsDao.class);
        MiscUtils.getLogger().debug("the length of guideline checkbox is " + guidelineCheckbox.length);
        for (int i = 0; i < guidelineCheckbox.length; i++) {
            int ctr = Integer.parseInt(guidelineCheckbox[i]);
            MiscUtils.getLogger().debug("the value of guildline Checkbox is: " + guidelineCheckbox[i]);
            String startDate = startDateB[ctr];
            String endDate = endDateB[ctr];
            String guideline = guidelineB[ctr];
            String measurementType = (String) this.getValue("measurementType" + ctr);
            String aboveBelow = (String) this.getValue("aboveBelow" + ctr);
            String sNumMInstrc = (String) this.getValue("mNbInstrcs" + ctr);
            int iNumMInstrc = Integer.parseInt(sNumMInstrc);
            double metGLPercentage = 0;
            double nbMetGL = 0;

            for (int j = 0; j < iNumMInstrc; j++) {
                metGLPercentage = 0;
                nbMetGL = 0;
                String mInstrc = (String) this.getValue("mInstrcsCheckbox" + ctr + j);

                if (mInstrc != null) {
                    double nbGeneral = 0;

                    List<Object[]> os = dao.findLastEntered(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), measurementType, mInstrc);
                    if (measurementType.compareTo("BP") == 0) {

                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];
                            for (Measurement m : dao.findByDemographicNoTypeAndDate(demographicNo, maxDateEntered, measurementType, mInstrc)) {
                                if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), guideline, aboveBelow)) {
                                    nbMetGL++;
                                }
                            }
                            nbGeneral++;
                        }
                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, mInstrc, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), aboveBelow, guideline};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    } else if (checkGuideline.getValidation(measurementType) == 1) {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];

                            String sql = "SELECT dataField FROM measurements WHERE dateEntered = :dateEntered AND demographicNo = :demographicNo AND type = :measurementType AND measuringInstruction = :measuringInstruction AND dataField" + aboveBelow + ":guideline";
                            List<Object[]> rs = fDao.runParameterizedNativeQuery(sql, 
                                "dateEntered", ConversionUtils.toDateString(maxDateEntered),
                                "demographicNo", demographicNo,
                                "measurementType", measurementType,
                                "measuringInstruction", mInstrc,
                                "guideline", guideline);

                            if (!rs.isEmpty()) {
                                nbMetGL++;
                            }
                            nbGeneral++;
                        }

                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, mInstrc, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), aboveBelow, guideline};

                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    } else {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];

                            for (Measurement m : dao.findByDemographicNoTypeAndDate(demographicNo, maxDateEntered, measurementType, mInstrc)) {
                                if (checkGuideline.isYesNoMetGuideline(m.getDataField(), guideline)) {
                                    nbMetGL++;
                                }
                                break;
                            }
                            nbGeneral++;
                        }
                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, mInstrc, guideline, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage)};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsIs", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    }
                }
            }

            //percentage of patients who meet guideline for the same test with all measuring instruction

            metGLPercentage = 0;
            nbMetGL = 0;

            List<Object[]> os = dao.findLastEntered(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), measurementType);
            double nbGeneral = 0;

            if (measurementType.compareTo("BP") == 0) {
                for (Object[] o : os) {
                    Integer demographicNo = (Integer) o[0];
                    Date maxDateEntered = (Date) o[1];

                    for (Measurement m : dao.findByDemoNoDateAndType(demographicNo, maxDateEntered, measurementType)) {
                        if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), guideline, aboveBelow)) {
                            nbMetGL++;
                        }
                        break;
                    }
                    nbGeneral++;
                }
                if (nbGeneral != 0) {
                    metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                }

                String[] param = {startDate, endDate, measurementType, "", "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), aboveBelow, guideline};
                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                MiscUtils.getLogger().debug(msg);
                metGLPercentageMsg.add(msg);
            } else if (checkGuideline.getValidation(measurementType) == 1) {
                for (Object[] o : os) {
                    Integer demographicNo = (Integer) o[0];
                    Date maxDateEntered = (Date) o[1];

                    String sql = "SELECT dataField FROM measurements WHERE dateEntered = :dateEntered AND demographicNo = :demographicNo AND type = :measurementType AND dataField" + aboveBelow + ":guideline";
                    List<Object[]> rs = fDao.runParameterizedNativeQuery(sql,
                        "dateEntered", ConversionUtils.toDateString(maxDateEntered),
                        "demographicNo", demographicNo,
                        "measurementType", measurementType,
                        "guideline", guideline);
                    if (!rs.isEmpty()) {
                        nbMetGL++;
                    }
                    nbGeneral++;
                }

                if (nbGeneral != 0) {
                    metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                }
                String[] param = {startDate, endDate, measurementType, "", "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), aboveBelow, guideline};
                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                MiscUtils.getLogger().debug(msg);
                metGLPercentageMsg.add(msg);
            } else {
                for (Object[] o : os) {
                    Integer demographicNo = (Integer) o[0];
                    Date maxDateEntered = (Date) o[1];

                    for (Measurement m : dao.findByDemoNoDateAndType(demographicNo, maxDateEntered, measurementType)) {
                        if (checkGuideline.isYesNoMetGuideline(m.getDataField(), guideline)) {
                            nbMetGL++;
                        }
                        break;
                    }
                    nbGeneral++;
                }
                if (nbGeneral != 0) {
                    metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                }
                String[] param = {startDate, endDate, measurementType, "", guideline, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage)};
                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsIs", param);
                MiscUtils.getLogger().debug(msg);
                metGLPercentageMsg.add(msg);
            }
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

    private String[] guidelineCheckbox;

    public String[] getGuidelineCheckbox() {
        return guidelineCheckbox;
    }

    public void setGuidelineCheckbox(String[] guidelineCheckbox) {
        this.guidelineCheckbox = guidelineCheckbox;
    }

    private String[] startDateB;

    public String[] getStartDateB() {
        return startDateB;
    }

    public void setStartDateB(String[] startDateB) {
        this.startDateB = startDateB;
    }

    private String[] endDateB;

    public String[] getEndDateB() {
        return endDateB;
    }

    public void setEndDateB(String[] endDateB) {
        this.endDateB = endDateB;
    }

    private String[] idB;

    public String[] getIdB() {
        return idB;
    }

    public void setIdB(String[] idB) {
        this.idB = idB;
    }

    private String[] guildlineB;

    public String[] getGuidelineB() {
        return guildlineB;
    }

    public void setGuidelineB(String[] guildlineB) {
        this.guildlineB = guildlineB;
    }

    private String aboveBelow;

    public String getAboveBelow() {
        return aboveBelow;
    }

    public void setAboveBelow(String aboveBelow) {
        this.aboveBelow = aboveBelow;
    }

}
