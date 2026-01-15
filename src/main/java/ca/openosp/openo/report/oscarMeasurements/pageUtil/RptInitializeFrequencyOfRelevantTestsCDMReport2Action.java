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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RptInitializeFrequencyOfRelevantTestsCDMReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        ArrayList<String> reportMsg = new ArrayList<String>();
        ArrayList<String> headings = new ArrayList<String>();
        RptMeasurementsData mData = new RptMeasurementsData();
        String[] patientSeenCheckbox = this.getPatientSeenCheckbox();
        String startDateA = this.getStartDateA();
        String endDateA = this.getEndDateA();
        int nbPatient = 0;

        if (!validateForm()) {
            response.sendRedirect(request.getContextPath() + "/oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport.jsp");
            return NONE;
        }

        addHeading(headings, request);
        if (patientSeenCheckbox != null) {
            nbPatient = mData.getNbPatientSeen(startDateA, endDateA);
            String msg = getText("oscarReport.CDMReport.msgPatientSeen", new String[]{Integer.toString(nbPatient), startDateA, endDateA});
            MiscUtils.getLogger().debug(msg);
            reportMsg.add(msg);
            reportMsg.add("");
        }
        getFrequenceOfTestPerformed(reportMsg);

        String title = getText("oscarReport.CDMReport.msgFrequencyOfRelevantTestsBeingPerformed");
        request.setAttribute("title", title);
        request.setAttribute("messages", reportMsg);
        return SUCCESS;
    }

    private ArrayList<String> addHeading(ArrayList<String> headings, HttpServletRequest request) {
        String hd = getText("oscarReport.CDMReport.msgFrequency");
        MiscUtils.getLogger().debug(hd);
        headings.add(hd);
        hd = getText("oscarReport.CDMReport.msgPercentage");
        MiscUtils.getLogger().debug(hd);
        headings.add(hd);
        return headings;
    }

    private boolean validateForm() {
        EctValidation ectValidation = new EctValidation();
        String[] startDateD = this.getStartDateD();
        String[] endDateD = this.getEndDateD();
        String[] frequencyCheckbox = this.getFrequencyCheckbox();
        boolean valid = true;

        if (frequencyCheckbox != null) {

            for (int i = 0; i < frequencyCheckbox.length; i++) {
                int ctr = Integer.parseInt(frequencyCheckbox[i]);
                String startDate = startDateD[ctr];
                String endDate = endDateD[ctr];
                String measurementType = (String) this.getValue("measurementTypeD" + ctr);

                if (!ectValidation.isDate(startDate)) {
                    addActionError(getText("errors.invalidDate", new String[]{measurementType}));

                    valid = false;
                }
                if (!ectValidation.isDate(endDate)) {
                    addActionError(getText("errors.invalidDate", new String[]{measurementType}));

                    valid = false;
                }
            }
        }
        return valid;
    }

    /**
     * Gets the frequency of tests performed during a time period
     *
     * @return ArrayList which contain the result in String format
     */
    private ArrayList<String> getFrequenceOfTestPerformed(ArrayList<String> percentageMsg) {
        String[] startDateD = this.getStartDateD();
        String[] endDateD = this.getEndDateD();
        int[] exactly = this.getExactly();
        int[] moreThan = this.getMoreThan();
        int[] lessThan = this.getLessThan();
        String[] frequencyCheckbox = this.getFrequencyCheckbox();

        RptMeasurementsData mData = new RptMeasurementsData();

        if (frequencyCheckbox != null) {
            try {

                for (int i = 0; i < frequencyCheckbox.length; i++) {
                    int ctr = Integer.parseInt(frequencyCheckbox[i]);
                    String startDate = startDateD[ctr];
                    String endDate = endDateD[ctr];
                    int exact = exactly[ctr];
                    int more = moreThan[ctr];
                    int less = lessThan[ctr];

                    String measurementType = (String) this.getValue("measurementTypeD" + ctr);
                    String sNumMInstrc = (String) this.getValue("mNbInstrcsD" + ctr);
                    int iNumMInstrc = Integer.parseInt(sNumMInstrc);
                    ArrayList patients = mData.getPatientsSeen(startDate, endDate);
                    int nbPatients = patients.size();

                    for (int j = 0; j < iNumMInstrc; j++) {

                        double exactPercentage = 0;
                        double morePercentage = 0;
                        double lessPercentage = 0;
                        int nbExact = 0;
                        int nbMore = 0;
                        int nbLess = 0;
                        int nbTest = 0;

                        String mInstrc = (String) this.getValue("mInstrcsCheckboxD" + ctr + j);
                        if (mInstrc != null) {
                            MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
                            for (int k = 0; k < nbPatients; k++) {
                                String patient = (String) patients.get(k);

                                List<Measurement> ms = dao.findByDemoNoTypeDateAndMeasuringInstruction(ConversionUtils.fromIntString(patient), ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate), measurementType, mInstrc);
                                nbTest = ms.size();

                                if (nbTest == exact) {
                                    nbExact++;
                                }
                                if (nbTest > more) {
                                    nbMore++;
                                }
                                if (nbTest < less) {
                                    nbLess++;
                                }

                                if (nbPatients != 0) {
                                    exactPercentage = Math.round(((double) nbExact / (double) nbPatients) * 100);
                                    morePercentage = Math.round(((double) nbMore / (double) nbPatients) * 100);
                                    lessPercentage = Math.round(((double) nbLess / (double) nbPatients) * 100);
                                }

                            }

                            String[] param0 = {startDate, endDate, measurementType, mInstrc, Double.toString(nbExact) + "/" + Double.toString(nbPatients) + " (" + Double.toString(exactPercentage) + "%)", Integer.toString(exact)};
                            String msg = getText("oscarReport.CDMReport.msgFrequencyOfRelevantTestsExact", param0);
                            MiscUtils.getLogger().debug(msg);
                            percentageMsg.add(msg);
                            String[] param1 = {startDate, endDate, measurementType, mInstrc, Double.toString(nbMore) + "/" + Double.toString(nbPatients) + " (" + Double.toString(morePercentage) + "%)", Integer.toString(more)};
                            msg = getText("oscarReport.CDMReport.msgFrequencyOfRelevantTestsMoreThan", param1);
                            MiscUtils.getLogger().debug(msg);
                            percentageMsg.add(msg);
                            String[] param2 = {startDate, endDate, measurementType, mInstrc, Double.toString(nbLess) + "/" + Double.toString(nbPatients) + " (" + Double.toString(lessPercentage) + "%)", Integer.toString(less)};
                            msg = getText("oscarReport.CDMReport.msgFrequencyOfRelevantTestsLessThan", param2);
                            MiscUtils.getLogger().debug(msg);
                            percentageMsg.add(msg);
                            percentageMsg.add("");
                        }
                    }
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            MiscUtils.getLogger().debug("guideline checkbox is null");
        }
        return percentageMsg;
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


    /******************************************************************
     *Getter and Setter for Frequency of Relevant tests being performed
     ******************************************************************/
    private String[] frequencyCheckbox;

    public String[] getFrequencyCheckbox() {
        return frequencyCheckbox;
    }

    public void setFrequencyCheckbox(String[] frequencyCheckbox) {
        this.frequencyCheckbox = frequencyCheckbox;
    }

    private String[] startDateD;

    public String[] getStartDateD() {
        return startDateD;
    }

    public void setStartDateD(String[] startDateD) {
        this.startDateD = startDateD;
    }

    private String[] endDateD;

    public String[] getEndDateD() {
        return endDateD;
    }

    public void setEndDateD(String[] endDateD) {
        this.endDateD = endDateD;
    }

    private int[] exactly;

    public int[] getExactly() {
        return exactly;
    }

    public void setExactly(int[] exactly) {
        this.exactly = exactly;
    }

    private int[] moreThan;

    public int[] getMoreThan() {
        return moreThan;
    }

    public void setMoreThan(int[] moreThan) {
        this.moreThan = moreThan;
    }

    private int[] lessThan;

    public int[] getLessThan() {
        return lessThan;
    }
}
