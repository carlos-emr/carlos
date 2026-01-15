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

import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.appt.ApptStatusData;
import ca.openosp.openo.billings.ca.bc.pageUtil.BillingSessionBean;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.AppointmentArchiveDao;
import ca.openosp.openo.commn.dao.EChartDao;
import ca.openosp.openo.commn.dao.EncounterWindowDao;
import ca.openosp.openo.commn.dao.OscarAppointmentDao;
import ca.openosp.openo.commn.model.Appointment;
import ca.openosp.openo.commn.model.EChart;
import ca.openosp.openo.commn.model.EncounterWindow;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.UtilDateUtilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctSaveEncounter2Action extends ActionSupport {
    HttpServletRequest httpservletrequest = ServletActionContext.getRequest();
    HttpServletResponse httpservletresponse = ServletActionContext.getResponse();

    static Logger log = MiscUtils.getLogger();
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    private String getLatestID(String demoNo) {
        EChartDao dao = SpringUtils.getBean(EChartDao.class);
        Integer maxId = dao.getMaxIdForDemographic(ConversionUtils.fromIntString(demoNo));

        if (maxId != null) {
            return maxId.toString();
        }
        return null;
    }


    //This function will compare the most current id in the echart with the
    // id that is stored in the session variable.  If the ID in the echart is
    // newer, then the user is working with a old (aka dirty) copy of the encounter
    private boolean isDirtyEncounter(String demographicNo, String userEChartID) {
        String latestID = getLatestID(demographicNo);

        //latestID should only be null if the assessed encounter is new, which
        // means that it can't be dirty
        if (latestID == null || latestID.equals("")) {
            return false;
        }
        // if the usrCopyID is null and the latestID isn't null, then
        // two people where probably trying to create a new encounter for
        // the same person at the same time.
        else if (userEChartID == null || userEChartID.equals("")) {
            return true;
        }

        try {
            Integer iLatestID = Integer.valueOf(latestID);
            Integer iUsrCopyID = Integer.valueOf(userEChartID);
            if (iLatestID.longValue() > iUsrCopyID.longValue()) {
                return true;
            } else {
                return false;
            }
        } catch (NumberFormatException e) {
            // already handled the null/empy string case, so shouldn't ever get this
            // exception.
            MiscUtils.getLogger().error("Error", e);
            return true;
        }
    }

    public String execute() throws
            IOException, ServletException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(httpservletrequest), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        log.debug("EctSaveEncounter2Action Start");
        //UtilDateUtilities dateutilities = new UtilDateUtilities();
        EctSessionBean sessionbean = null;
        sessionbean = (EctSessionBean) httpservletrequest.getSession().getAttribute(
                "EctSessionBean");

        sessionbean.socialHistory = httpservletrequest.getParameter("shTextarea");
        sessionbean.familyHistory = httpservletrequest.getParameter("fhTextarea");
        sessionbean.medicalHistory = httpservletrequest.getParameter("mhTextarea");
        sessionbean.ongoingConcerns = httpservletrequest.getParameter("ocTextarea");
        sessionbean.reminders = httpservletrequest.getParameter("reTextarea");
        sessionbean.encounter = httpservletrequest.getParameter("enTextarea");
        sessionbean.subject = httpservletrequest.getParameter("subject");
        java.util.Date date = new Date();
        sessionbean.eChartTimeStamp = date;

        if (!httpservletrequest.getParameter("btnPressed").equals("Exit")) {
            try {
                ResourceBundle prop = ResourceBundle.getBundle("oscarResources",
                        httpservletrequest.getLocale());
                if (httpservletrequest.getParameter("btnPressed").equals(
                        "Sign,Save and Bill")) {
                    sessionbean.encounter = sessionbean.encounter + "\n" + "[" +
                            prop.
                                    getString("oscarEncounter.class.EctSaveEncounterAction.msgSigned") +
                            " " +
                            UtilDateUtilities.DateToString(date,
                                    prop.getString("date.yyyyMMddHHmmss"),
                                    httpservletrequest.getLocale()) +
                            " " +
                            prop.getString(
                                    "oscarEncounter.class.EctSaveEncounterAction.msgSigBy") +
                            " " + sessionbean.userName + "]";
                }
                if (httpservletrequest.getParameter("btnPressed").equals(
                        "Sign,Save and Exit")) {
                    sessionbean.encounter = sessionbean.encounter + "\n" + "[" +
                            prop.
                                    getString("oscarEncounter.class.EctSaveEncounterAction.msgSigned") +
                            " " +
                            UtilDateUtilities.DateToString(date,
                                    prop.getString("date.yyyyMMddHHmmss"),
                                    httpservletrequest.getLocale()) +
                            " " +
                            prop.getString(
                                    "oscarEncounter.class.EctSaveEncounterAction.msgSigBy") +
                            " " + sessionbean.userName + "]";
                }
                if (httpservletrequest.getParameter("btnPressed").equals(
                        "Verify and Sign")) {
                    sessionbean.encounter = sessionbean.encounter + "\n" + "[" +
                            prop.
                                    getString(
                                            "oscarEncounter.class.EctSaveEncounterAction.msgVerAndSig") +
                            " " +
                            UtilDateUtilities.DateToString(date,
                                    prop.getString("date.yyyyMMddHHmmss"),
                                    httpservletrequest.getLocale()) +
                            " " +
                            prop.getString(
                                    "oscarEncounter.class.EctSaveEncounterAction.msgSigBy") +
                            " " + sessionbean.userName + "]";
                }
                if (httpservletrequest.getParameter("btnPressed").equals("Split Chart")) {
                    sessionbean.subject = prop.getString(
                            "oscarEncounter.class.EctSaveEncounterAction.msgSplitChart");
                }
                sessionbean.template = "";
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }

            //This code is synchronized to ensure that only one person is modifying the same patient
            // record at a time
            synchronized (this) {
                //unfortunately, can't use the echart ID stored in the session bean, because it may
                // be overwritten when view split charts.
                String userEChartID = (String) httpservletrequest.getSession().getAttribute("eChartID");
                //make sure that user is trying to save the latest version of the encounter
                if (isDirtyEncounter(sessionbean.demographicNo, userEChartID)) {
                    //If it is an ajax submit, it should cause and exception, if it is a
                    // regular submission, it should just forward on to an error page.
                    if (httpservletrequest.getParameter("submitMethod") != null && httpservletrequest.getParameter("submitMethod").equals("ajax")) {
                        httpservletresponse.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                                "Somebody else is currently modifying this encounter");
                    }
                    return "concurrencyError";
                }


                EChart e = new EChart();
                e.setTimestamp(date);
                e.setDemographicNo(Integer.parseInt(sessionbean.demographicNo));
                e.setProviderNo(sessionbean.providerNo);
                e.setSubject(sessionbean.subject);
                e.setSocialHistory(sessionbean.socialHistory);
                e.setFamilyHistory(sessionbean.familyHistory);
                e.setMedicalHistory(sessionbean.medicalHistory);
                e.setOngoingConcerns(sessionbean.ongoingConcerns);
                e.setReminders(sessionbean.reminders);
                e.setEncounter(sessionbean.encounter);

                EChartDao dao = SpringUtils.getBean(EChartDao.class);
                dao.persist(e);
                sessionbean.eChartId = String.valueOf(e.getId());
                httpservletrequest.getSession().setAttribute("eChartID", sessionbean.eChartId);

                // add log here
                String ip = httpservletrequest.getRemoteAddr();
                LogAction.addLog((String) httpservletrequest.getSession().getAttribute(
                                "user"), LogConst.ADD, LogConst.CON_ECHART,
                        sessionbean.demographicNo, ip);

                //change the appt status
                if (sessionbean.status != null && !sessionbean.status.equals("")) {
                    ApptStatusData as = new ApptStatusData();
                    as.setApptStatus(sessionbean.status);

                    if (httpservletrequest.getParameter("btnPressed").equals(
                            "Sign,Save and Exit")) {
                        Appointment appt = appointmentDao.find(Integer.parseInt(sessionbean.appointmentNo));
                        appointmentArchiveDao.archiveAppointment(appt);
                        if (appt != null) {
                            appt.setStatus(as.signStatus());
                            appt.setLastUpdateUser(sessionbean.providerNo);
                            appointmentDao.merge(appt);
                        }
                    }
                    if (httpservletrequest.getParameter("btnPressed").equals(
                            "Verify and Sign")) {
                        Appointment appt = appointmentDao.find(Integer.parseInt(sessionbean.appointmentNo));
                        appointmentArchiveDao.archiveAppointment(appt);
                        if (appt != null) {
                            appt.setStatus(as.verifyStatus());
                            appt.setLastUpdateUser(sessionbean.providerNo);
                            appointmentDao.merge(appt);
                        }
                    }
                }
            }

        }  //end of the synchronization block


        EncounterWindowDao encounterWindowDao = SpringUtils.getBean(EncounterWindowDao.class);
        encounterWindowDao.remove(sessionbean.providerNo);


        EncounterWindow ew = new EncounterWindow();
        ew.setProviderNo(sessionbean.providerNo);
        ew.setRowOneSize(Integer.parseInt(httpservletrequest.getParameter("rowOneSize")));
        ew.setRowTwoSize(Integer.parseInt(httpservletrequest.getParameter("rowTwoSize")));
        ew.setRowThreeSize(Integer.parseInt(httpservletrequest.getParameter("rowThreeSize")));
        ew.setPresBoxSize(Integer.parseInt(httpservletrequest.getParameter("presBoxSize")));
        encounterWindowDao.persist(ew);

        String forward = null;

        if (httpservletrequest.getParameter("btnPressed").equals(
                "Sign,Save and Bill")) {

            String billRegion = OscarProperties.getInstance().getProperty(
                    "billregion");
            //
            BillingSessionBean bean = new BillingSessionBean();
            bean.setApptProviderNo(sessionbean.providerNo);
            bean.setPatientName(sessionbean.getPatientFirstName() + " " +
                    sessionbean.getPatientLastName());
            bean.setBillRegion(billRegion);
            Properties oscarVariables = (Properties) httpservletrequest.getAttribute("oscarVariables");
            String formBill = "GP";

            if (oscarVariables != null) {
                formBill = oscarVariables.getProperty("default_view", formBill);
            }
            bean.setBillForm(formBill);
            bean.setPatientNo(sessionbean.demographicNo);
            bean.setApptNo(httpservletrequest.getParameter("appointment_no"));
            bean.setApptDate(sessionbean.appointmentDate);
            bean.setApptStatus(httpservletrequest.getParameter("status"));
            httpservletrequest.setAttribute("encounter", "true");
            httpservletrequest.getSession().setAttribute("billingSessionBean", bean);
            forward = "bill";
        } else if (httpservletrequest.getParameter("btnPressed").equals("Sign,Save and Exit")) {
            forward = "success";
        } else if (httpservletrequest.getParameter("btnPressed").equals("Verify and Sign")) {
            forward = "success";
        } else if (httpservletrequest.getParameter("btnPressed").equals("Save") || httpservletrequest.getParameter("btnPressed").equals("AutoSave")) {
            forward = "saveAndStay";
        } else if (httpservletrequest.getParameter("btnPressed").equals("Split Chart")) {
            forward = "splitchart";
        } else if (httpservletrequest.getParameter("btnPressed").equals("Exit")) {
            forward = "close";
        } else {
            forward = "failure";
        }
        return forward;
    }
}
