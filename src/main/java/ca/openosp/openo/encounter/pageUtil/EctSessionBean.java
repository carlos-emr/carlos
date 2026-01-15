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


package ca.openosp.openo.encounter.pageUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ca.openosp.openo.commn.dao.EChartDao;
import ca.openosp.openo.commn.dao.EncounterTemplateDao;
import ca.openosp.openo.commn.dao.MeasurementGroupStyleDao;
import ca.openosp.openo.commn.dao.MessageTblDao;
import ca.openosp.openo.commn.dao.OscarAppointmentDao;
import ca.openosp.openo.commn.model.Appointment;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.EChart;
import ca.openosp.openo.commn.model.EncounterTemplate;
import ca.openosp.openo.commn.model.MeasurementGroupStyle;
import ca.openosp.openo.commn.model.MessageTbl;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.encounter.oscarConsultation.data.EctConProviderData;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.StringUtils;
import ca.openosp.openo.util.UtilDateUtilities;

@Deprecated
public class EctSessionBean implements java.io.Serializable {
    //data passed from the oscar appointment screen these members are constant for the duration of
    // a session
    public Date currentDate;
    public String eChartId;
    public Date eChartTimeStamp;
    public String providerNo;
    public String userName;
    public String demographicNo;
    public String appointmentNo;
    public String curProviderNo;
    public String reason;
    public String reasonCode;
    public String encType;
    public String appointmentDate;
    public String startTime;
    public String status;
    public String date;
    public String check;
    public String patientFirstName;
    public String patientLastName;
    public String patientSex;
    public String patientAge;
    public String familyDoctorNo;
    public String monthOfBirth;
    public String yearOfBirth;
    public String dateOfBirth;
    public String address;
    public String city;
    public String postal;
    public String phone;
    public String roster;
    public String team = null;
    public String consultationRequestId = null;
    public String currentTeam = null;
    public String socialHistory;
    public String familyHistory;
    public String medicalHistory;
    public String ongoingConcerns;
    public String reminders;
    public String encounter;
    public String subject;
    public String template = "";
    public String oscarMsgID;
    public String oscarMsg = "";
    public ArrayList<String> appointmentsIdArray;
    public ArrayList<String> appointmentsNamesArray;
    public ArrayList<String> templateNames;
    public ArrayList<String> measurementGroupNames;
    public String source;

    public void resetAll() {
        eChartTimeStamp = null;
        eChartId = "";
        patientLastName = "";
        patientFirstName = "";
        yearOfBirth = "";
        monthOfBirth = "";
        dateOfBirth = "";
        patientSex = "";
        patientAge = "";
        familyDoctorNo = "";
        socialHistory = "";
        familyHistory = "";
        medicalHistory = "";
        ongoingConcerns = "";
        reminders = "";
        encounter = "";
        subject = "";
        address = "";
        city = "";
        postal = "";
        phone = "";
        roster = "";
        template = "";
        oscarMsg = "";
    }

    /**
     * sets up the encounter page as befits entrance into the oscarEncounter module from the oscar
     * appointment scheduling screen
     */
    public void setUpEncounterPage(LoggedInInfo loggedInInfo) {
        resetAll();

        appointmentsIdArray = new ArrayList<String>();
        appointmentsNamesArray = new ArrayList<String>();
        templateNames = new ArrayList<String>();
        measurementGroupNames = new ArrayList<String>();

        //This block gets the patient age and
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic d = demographicManager.getDemographic(loggedInInfo, demographicNo);

        patientLastName = d.getLastName();
        patientFirstName = d.getFirstName();
        address = d.getAddress();
        city = d.getCity();
        postal = d.getPostal();
        phone = d.getPhone();
        familyDoctorNo = d.getProviderNo();
        yearOfBirth = d.getYearOfBirth();
        monthOfBirth = d.getMonthOfBirth();
        dateOfBirth = d.getDateOfBirth();
        roster = d.getRosterStatus();
        patientSex = d.getSex();

        // Normalize birth date fields - set to "0" if null, empty, or "null" string
        yearOfBirth = (StringUtils.empty(yearOfBirth) || "null".equals(yearOfBirth)) ? "0" : yearOfBirth;
        monthOfBirth = (StringUtils.empty(monthOfBirth) || "null".equals(monthOfBirth)) ? "0" : monthOfBirth;
        dateOfBirth = (StringUtils.empty(dateOfBirth) || "null".equals(dateOfBirth)) ? "0" : dateOfBirth;

        if (StringUtils.filled(yearOfBirth) && !"0".equals(yearOfBirth)) {
            patientAge = UtilDateUtilities
                    .calcAge(UtilDateUtilities.calcDate(yearOfBirth, monthOfBirth, dateOfBirth));
        }

        OscarAppointmentDao apptDao = SpringUtils.getBean(OscarAppointmentDao.class);
        for (Appointment appt : apptDao.findByProviderAndDate(curProviderNo, ConversionUtils.fromDateString(appointmentDate))) {
            appointmentsIdArray.add(appt.getId().toString());
            appointmentsNamesArray.add(appt.getName() + " " + ConversionUtils.toTimeString(appt.getStartTime()));
        }

        EncounterTemplateDao ectDao = SpringUtils.getBean(EncounterTemplateDao.class);
        for (EncounterTemplate ect : ectDao.findAll()) {
            templateNames.add(ect.getEncounterTemplateName());
        }

        MeasurementGroupStyleDao mgsDao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
        for (MeasurementGroupStyle mgs : mgsDao.findAll()) {
            measurementGroupNames.add(mgs.getGroupName());
        }

        OscarProperties properties = OscarProperties.getInstance();
        if (!Boolean.parseBoolean(properties.getProperty("AbandonOldChart", "false"))) {
            EChartDao ecDao = SpringUtils.getBean(EChartDao.class);
            List<EChart> ecs = ecDao.getChartsForDemographic(ConversionUtils.fromIntString(demographicNo));
            if (!ecs.isEmpty()) {
                EChart ec = ecs.get(0);
                eChartId = ec.getId().toString();
                eChartTimeStamp = ec.getTimestamp();
                socialHistory = ec.getSocialHistory();
                familyHistory = ec.getFamilyHistory();
                medicalHistory = ec.getMedicalHistory();
                ongoingConcerns = ec.getOngoingConcerns();
                reminders = ec.getReminders();
                encounter = ec.getEncounter();
                subject = ec.getSubject();
            } else {
                eChartTimeStamp = null;
                socialHistory = "";
                familyHistory = "";
                medicalHistory = "";
                ongoingConcerns = "";
                reminders = "";
                encounter = "";
                subject = "";
            }
        }

        if (oscarMsgID != null) {
            MessageTblDao mtDao = SpringUtils.getBean(MessageTblDao.class);
            MessageTbl mt = mtDao.find(ConversionUtils.fromIntString(oscarMsgID));
            if (mt != null) {
                String message = mt.getMessage();
                String subject = mt.getSubject();
                String sentby = mt.getSentBy();
                String sentto = mt.getSentTo();
                String thetime = ConversionUtils.toTimeString(mt.getTime());
                String thedate = ConversionUtils.toDateString(mt.getDate());
                oscarMsg = "From: " + sentby + "\n" + "To: " + sentto + "\n" + "Date: " + thedate + " " + thetime
                        + "\n" + "Subject: " + subject + "\n" + message;
            }
        }


    }

    /**
     * over loaded method sets up the encounter page as befits entrance from the select box of
     * today's appointments on the oscarEncounter.Index.jsp page
     *
     * @param appointmentNo
     */
    public void setUpEncounterPage(LoggedInInfo loggedInInfo, String appointmentNo) {
        resetAll();
        
        appointmentsIdArray = new ArrayList<String>();
        appointmentsNamesArray = new ArrayList<String>();
        templateNames = new ArrayList<String>();

        OscarAppointmentDao apptDao = SpringUtils.getBean(OscarAppointmentDao.class);
        Appointment appt = apptDao.find(ConversionUtils.fromIntString(appointmentNo));
        demographicNo = "" + appt.getDemographicNo();
        this.appointmentNo = appointmentNo;
        reason = appt.getReason();
        reasonCode = appt.getReasonCode() != null ? appt.getReasonCode().toString() : null;

        // If reason text is empty but reasonCode exists, use the reasonCode label
        if ((reason == null || reason.trim().isEmpty()) && appt.getReasonCode() != null) {
            ca.openosp.openo.commn.dao.LookupListItemDao lookupListItemDao = SpringUtils.getBean(ca.openosp.openo.commn.dao.LookupListItemDao.class);
            ca.openosp.openo.commn.model.LookupListItem item = lookupListItemDao.find(appt.getReasonCode());
            if (item != null && item.getLabel() != null && !item.getLabel().trim().isEmpty()) {
                reason = item.getLabel().trim();
            }
        }

        encType = new String("face to face encounter with client");
        appointmentDate = ConversionUtils.toDateString(appt.getAppointmentDate());
        startTime = ConversionUtils.toDateString(appt.getStartTime());
        status = appt.getStatus();

        for (Appointment a : apptDao.findByProviderAndDate(curProviderNo, appt.getAppointmentDate())) {
            appointmentsIdArray.add(a.getId().toString());
            appointmentsNamesArray.add(a.getName() + " " + ConversionUtils.toTimeString(a.getStartTime()));
        }

        EncounterTemplateDao ectDao = SpringUtils.getBean(EncounterTemplateDao.class);
        for (EncounterTemplate ect : ectDao.findAll()) {
            templateNames.add(ect.getEncounterTemplateName());
        }

        OscarProperties properties = OscarProperties.getInstance();
        if (!Boolean.parseBoolean(properties.getProperty("AbandonOldChart", "false"))) {
            EChartDao ecDao = SpringUtils.getBean(EChartDao.class);
            List<EChart> ecs = ecDao.getChartsForDemographic(ConversionUtils.fromIntString(demographicNo));
            if (!ecs.isEmpty()) {
                EChart ec = ecs.get(0);
                eChartId = ec.getId().toString();
                eChartTimeStamp = ec.getTimestamp();
                socialHistory = ec.getSocialHistory();
                familyHistory = ec.getFamilyHistory();
                medicalHistory = ec.getMedicalHistory();
                ongoingConcerns = ec.getOngoingConcerns();
                reminders = ec.getReminders();
                encounter = ec.getEncounter();
                subject = ec.getSubject();
            } else {
                eChartTimeStamp = null;
                socialHistory = "";
                familyHistory = "";
                medicalHistory = "";
                ongoingConcerns = "";
                reminders = "";
                encounter = "";
                subject = "";
            }
        }

        //apointmentsIdArray and the appointmentsNamesArray are
        //already set up so no need to get them again

        //
        DemographicManager demogaphicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demogaphicManager.getDemographic(loggedInInfo, demographicNo);
        if (demo != null) {
            patientLastName = demo.getLastName();
            patientFirstName = demo.getFirstName();
            address = demo.getAddress();
            city = demo.getCity();
            postal = demo.getPostal();
            phone = demo.getPhone();
            familyDoctorNo = demo.getProviderNo();
            yearOfBirth = demo.getYearOfBirth();
            monthOfBirth = demo.getMonthOfBirth();
            dateOfBirth = demo.getDateOfBirth();
            roster = demo.getRosterStatus();
            patientSex = demo.getSex();
            if (yearOfBirth.equals("null")) {
                yearOfBirth = "0";
            }
            if (monthOfBirth.equals("null")) {
                monthOfBirth = "0";
            }
            if (dateOfBirth.equals("null")) {
                dateOfBirth = "0";
            }
        }
        patientAge = UtilDateUtilities
                .calcAge(UtilDateUtilities.calcDate(yearOfBirth, monthOfBirth, dateOfBirth));

    }

    /**
     * over loaded method sets up the encounter page for print
     *
     * @param echartid
     * @param demographicNo
     */
    public void setUpEncounterPage(LoggedInInfo loggedInInfo, String echartid, String demographicNo) {
        resetAll();

        OscarProperties properties = OscarProperties.getInstance();
        if (!Boolean.parseBoolean(properties.getProperty("AbandonOldChart", "false"))) {
            EChartDao ecDao = SpringUtils.getBean(EChartDao.class);
            EChart ec = ecDao.find(ConversionUtils.fromIntString(echartid));
            if (ec.getDemographicNo() != ConversionUtils.fromIntString(demographicNo)) {
                ec = null;
            }

            if (ec != null) {
                eChartId = ec.getId().toString();
                eChartTimeStamp = ec.getTimestamp();
                socialHistory = ec.getSocialHistory();
                familyHistory = ec.getFamilyHistory();
                medicalHistory = ec.getMedicalHistory();
                ongoingConcerns = ec.getOngoingConcerns();
                reminders = ec.getReminders();
                encounter = ec.getEncounter();
                subject = ec.getSubject();
            }
        }

        DemographicManager demogaphicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demogaphicManager.getDemographic(loggedInInfo, demographicNo);
        if (demo != null) {
            patientLastName = demo.getLastName();
            patientFirstName = demo.getFirstName();
            address = demo.getAddress();
            city = demo.getCity();
            postal = demo.getPostal();
            phone = demo.getPhone();
            familyDoctorNo = demo.getProviderNo();
            yearOfBirth = demo.getYearOfBirth();
            monthOfBirth = demo.getMonthOfBirth();
            dateOfBirth = demo.getDateOfBirth();
            roster = demo.getRosterStatus();
            patientSex = demo.getSex();
            if (yearOfBirth.equals("null")) {
                yearOfBirth = "0";
            }
            if (monthOfBirth.equals("null")) {
                monthOfBirth = "0";
            }
            if (dateOfBirth.equals("null")) {
                dateOfBirth = "0";
            }
        }
        patientAge = UtilDateUtilities
                .calcAge(UtilDateUtilities.calcDate(yearOfBirth, monthOfBirth, dateOfBirth));
    }

    public String getTeam() {
        if (team == null) {
            EctConProviderData providerData = new EctConProviderData();
            team = providerData.getTeam(providerNo);
        }
        return team;
    }

    public void setDemographicNo(String str) {
        demographicNo = str;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public String getCurProviderNo() {
        return curProviderNo;
    }

    public void setConsultationRequestId(String str) {
        consultationRequestId = str;
    }

    public String getConsultationRequestId() {
        return consultationRequestId;
    }

    public void unsetConsultationRequestId() {
        consultationRequestId = null;
    }

    public String getCurrentTeam() {
        if (currentTeam == null) {
            currentTeam = new String();
        }
        return currentTeam;
    }

    public void setCurrentTeam(String str) {
        currentTeam = str;
    }

    public void unsetCurrentTeam() {
        currentTeam = null;
    }

    public boolean isCurrentTeam() {
        boolean retval = true;
        if (currentTeam == null) {
            retval = false;
        }
        return retval;
    }

    public boolean isValid() {
        return demographicNo.length() > 0 && providerNo != null && providerNo.length() > 0;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public String getPatientAge() {
        return patientAge;
    }

}
