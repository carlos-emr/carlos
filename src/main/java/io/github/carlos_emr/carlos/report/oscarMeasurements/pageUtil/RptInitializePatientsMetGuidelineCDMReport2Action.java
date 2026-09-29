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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;
import io.github.carlos_emr.carlos.report.oscarMeasurements.data.RptMeasurementsData;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class RptInitializePatientsMetGuidelineCDMReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
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
            response.sendRedirect(request.getContextPath() + "/oscarReport/oscarMeasurements/ViewInitializePatientsMetGuidelineCDMReport");
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
        // The hidden type/instruction fields are echoes of the session definitions; anything else
        // is a tampered request and its row is skipped, here and in the report loop.
        CdmReportSelectionValidator selection = CdmReportSelectionValidator.fromSession(request);

        if (guidelineCheckbox != null) {
            for (int i = 0; i < guidelineCheckbox.length; i++) {
                // The index is request data: parse and range-check it before any array access.
                int ctr = selection.acceptedRow(guidelineCheckbox[i], CdmReportSelectionValidator.length(startDateB),
                        CdmReportSelectionValidator.length(endDateB), CdmReportSelectionValidator.length(guidelineB));
                if (ctr < 0) {
                    continue;
                }
                String startDate = startDateB[ctr];
                String endDate = endDateB[ctr];
                String guideline = guidelineB[ctr];
                String measurementType = selection.acceptedMeasurementType(ctr, (String) this.getValue("measurementType" + ctr));
                if (measurementType == null) {
                    continue;
                }
                // Validate the aggregate row too, even when every instruction is unchecked.
                if (new RptCheckGuideline().getValidation(measurementType) == 1
                        && RptCheckGuideline.numericValue(guideline) == null) {
                    addActionError(getText("errors.invalid", measurementType));
                    valid = false;
                    continue;
                }
                // The posted value(mNbInstrcsN) count is ignored: the rendered list bounds the loop.
                int iNumMInstrc = selection.instructionCount(ctr);

                if (!ectValidation.isDate(startDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));
                    valid = false;
                }
                if (!ectValidation.isDate(endDate)) {
                    addActionError(getText("errors.invalidDate", measurementType));
                    valid = false;
                }
                for (int j = 0; j < iNumMInstrc; j++) {

                    String mInstrc = selection.acceptedMeasuringInstruction(ctr, (String) this.getValue("mInstrcsCheckbox" + ctr + j));
                    if (mInstrc != null) {
                        List<Validations> vs = ectValidation.getValidationType(measurementType, mInstrc);
                        String regExp = null;
                        double dMax = 0;
                        double dMin = 0;

                        if (!vs.isEmpty()) {
                            Validations v = vs.iterator().next();
                            // A non-numeric rule (Yes/No/NA, Provided/Revised/Reviewed) stores no bounds; unboxing
                            // its NULL max/min into a double threw before any report line was produced.
                            dMax = v.getMaxValue() != null ? v.getMaxValue() : 0;
                            dMin = v.getMinValue() != null ? v.getMinValue() : 0;
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

    private static final String ABOVE = ">";
    private static final String BELOW = "<";
    private static final String SQL_MET_WITH_INSTRUCTION_PREFIX = "SELECT dataField FROM measurements WHERE dateEntered = :dateEntered"
            + " AND demographicNo = :demographicNo AND type = :measurementType AND measuringInstruction = :measuringInstruction AND dataField";
    private static final String SQL_MET_PREFIX = "SELECT dataField FROM measurements WHERE dateEntered = :dateEntered"
            + " AND demographicNo = :demographicNo AND type = :measurementType AND dataField";
    // Constant SQL per operator: the operator can't be a bind parameter, and request data must
    // never be concatenated into the statement.
    static final String SQL_MET_ABOVE_WITH_INSTRUCTION = SQL_MET_WITH_INSTRUCTION_PREFIX + " > :guideline";
    static final String SQL_MET_BELOW_WITH_INSTRUCTION = SQL_MET_WITH_INSTRUCTION_PREFIX + " < :guideline";
    static final String SQL_MET_ABOVE = SQL_MET_PREFIX + " > :guideline";
    static final String SQL_MET_BELOW = SQL_MET_PREFIX + " < :guideline";

    /**
     * Maps the submitted "above/below" radio value to the only two comparison operators the
     * met-guideline form offers.
     *
     * @param raw the submitted {@code value(aboveBelowN)} field
     * @return {@code ">"} or {@code "<"}, or {@code null} for any other value
     */
    static String guidelineComparator(String raw) {
        if (ABOVE.equals(raw)) {
            return ABOVE;
        }
        if (BELOW.equals(raw)) {
            return BELOW;
        }
        return null;
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
        CdmReportSelectionValidator selection = CdmReportSelectionValidator.fromSession(request);

        if (guidelineCheckbox == null) {
            return metGLPercentageMsg;
        }

        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        FormsDao fDao = SpringUtils.getBean(FormsDao.class);
        MiscUtils.getLogger().debug("the length of guideline checkbox is " + guidelineCheckbox.length);
        for (int i = 0; i < guidelineCheckbox.length; i++) {
            // The index is request data: parse and range-check it before any array access.
            int ctr = selection.acceptedRow(guidelineCheckbox[i], CdmReportSelectionValidator.length(startDateB),
                    CdmReportSelectionValidator.length(endDateB), CdmReportSelectionValidator.length(guidelineB));
            if (ctr < 0) {
                continue;
            }
            MiscUtils.getLogger().debug("the value of guildline Checkbox is: " + ctr);
            String startDate = startDateB[ctr];
            String endDate = endDateB[ctr];
            String guideline = guidelineB[ctr];
            // Only a type the server rendered for this row may drive the patient-wide queries.
            String measurementType = selection.acceptedMeasurementType(ctr, (String) this.getValue("measurementType" + ctr));
            if (measurementType == null) {
                continue;
            }
            // The comparison operator selects a constant statement below, so only the two operators
            // the form offers are accepted; anything else (a tampered request) skips this row.
            String comparator = guidelineComparator((String) this.getValue("aboveBelow" + ctr));
            if (comparator == null) {
                MiscUtils.getLogger().warn("CDM met-guideline report: rejected an unsupported guideline comparator");
                continue;
            }
            // The posted value(mNbInstrcsN) count is ignored: the rendered list bounds the loop.
            int iNumMInstrc = selection.instructionCount(ctr);
            double metGLPercentage = 0;
            double nbMetGL = 0;

            for (int j = 0; j < iNumMInstrc; j++) {
                metGLPercentage = 0;
                nbMetGL = 0;
                String mInstrc = selection.acceptedMeasuringInstruction(ctr, (String) this.getValue("mInstrcsCheckbox" + ctr + j));

                if (mInstrc != null) {
                    double nbGeneral = 0;

                    List<Object[]> os = dao.findLastEntered(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), measurementType, mInstrc);
                    if (measurementType.compareTo("BP") == 0) {

                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];
                            for (Measurement m : dao.findByDemographicNoTypeAndDate(demographicNo, maxDateEntered, measurementType, mInstrc)) {
                                if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), guideline, comparator)) {
                                    nbMetGL++;
                                }
                            }
                            nbGeneral++;
                        }
                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, mInstrc, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), comparator, guideline};
                        String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                        MiscUtils.getLogger().debug(msg);
                        metGLPercentageMsg.add(msg);
                    } else if (checkGuideline.getValidation(measurementType) == 1) {
                        for (Object[] o : os) {
                            Integer demographicNo = (Integer) o[0];
                            Date maxDateEntered = (Date) o[1];

                            // Preserve the full entry timestamp; formatting as a date loses non-midnight readings.
                            String sql = ABOVE.equals(comparator) ? SQL_MET_ABOVE_WITH_INSTRUCTION : SQL_MET_BELOW_WITH_INSTRUCTION;
                            List<Object[]> rs = fDao.runParameterizedNativeQuery(sql, 
                                "dateEntered", maxDateEntered,
                                "demographicNo", demographicNo,
                                "measurementType", measurementType,
                                "measuringInstruction", mInstrc,
                                "guideline", RptCheckGuideline.numericValue(guideline));

                            if (!rs.isEmpty()) {
                                nbMetGL++;
                            }
                            nbGeneral++;
                        }

                        if (nbGeneral != 0) {
                            metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                        }
                        String[] param = {startDate, endDate, measurementType, mInstrc, "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), comparator, guideline};

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
                        if (checkGuideline.isBloodPressureMetGuideline(m.getDataField(), guideline, comparator)) {
                            nbMetGL++;
                        }
                        break;
                    }
                    nbGeneral++;
                }
                if (nbGeneral != 0) {
                    metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                }

                String[] param = {startDate, endDate, measurementType, "", "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), comparator, guideline};
                String msg = getText("oscarReport.CDMReport.msgNbOfPatientsMetGuideline", param);
                MiscUtils.getLogger().debug(msg);
                metGLPercentageMsg.add(msg);
            } else if (checkGuideline.getValidation(measurementType) == 1) {
                for (Object[] o : os) {
                    Integer demographicNo = (Integer) o[0];
                    Date maxDateEntered = (Date) o[1];

                    String sql = ABOVE.equals(comparator) ? SQL_MET_ABOVE : SQL_MET_BELOW;
                    List<Object[]> rs = fDao.runParameterizedNativeQuery(sql,
                        "dateEntered", maxDateEntered,
                        "demographicNo", demographicNo,
                        "measurementType", measurementType,
                        "guideline", RptCheckGuideline.numericValue(guideline));
                    if (!rs.isEmpty()) {
                        nbMetGL++;
                    }
                    nbGeneral++;
                }

                if (nbGeneral != 0) {
                    metGLPercentage = Math.round((nbMetGL / nbGeneral) * 100);
                }
                String[] param = {startDate, endDate, measurementType, "", "(" + nbMetGL + "/" + nbGeneral + ") " + Double.toString(metGLPercentage), comparator, guideline};
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

    /**
     * Returns a {@code value(key)} form field. Struts 7 never binds these names through
     * {@link #setValue}, so the posted request parameter is the source; see
     * {@link MappedFormValues}.
     */
    public Object getValue(String key) {
        Object value = values.get(key);
        return value != null ? value : MappedFormValues.get(request, key);
    }

    private String[] patientSeenCheckbox;

    public String[] getPatientSeenCheckbox() {
        return patientSeenCheckbox;
    }

    @StrutsParameter
    public void setPatientSeenCheckbox(String[] patientSeenCheckbox) {
        this.patientSeenCheckbox = patientSeenCheckbox;
    }

    private String startDateA;

    public String getStartDateA() {
        return startDateA;
    }

    @StrutsParameter
    public void setStartDateA(String startDateA) {
        this.startDateA = startDateA;
    }

    private String endDateA;

    public String getEndDateA() {
        return endDateA;
    }

    @StrutsParameter
    public void setEndDateA(String endDateA) {
        this.endDateA = endDateA;
    }

    private String[] guidelineCheckbox;

    public String[] getGuidelineCheckbox() {
        return guidelineCheckbox;
    }

    @StrutsParameter
    public void setGuidelineCheckbox(String[] guidelineCheckbox) {
        this.guidelineCheckbox = guidelineCheckbox;
    }

    private String[] startDateB;

    public String[] getStartDateB() {
        return startDateB;
    }

    @StrutsParameter
    public void setStartDateB(String[] startDateB) {
        this.startDateB = startDateB;
    }

    private String[] endDateB;

    public String[] getEndDateB() {
        return endDateB;
    }

    @StrutsParameter
    public void setEndDateB(String[] endDateB) {
        this.endDateB = endDateB;
    }

    private String[] idB;

    public String[] getIdB() {
        return idB;
    }

    @StrutsParameter
    public void setIdB(String[] idB) {
        this.idB = idB;
    }

    private String[] guildlineB;

    public String[] getGuidelineB() {
        return guildlineB;
    }

    @StrutsParameter
    public void setGuidelineB(String[] guildlineB) {
        this.guildlineB = guildlineB;
    }

    private String aboveBelow;

    public String getAboveBelow() {
        return aboveBelow;
    }

    @StrutsParameter
    public void setAboveBelow(String aboveBelow) {
        this.aboveBelow = aboveBelow;
    }

}
