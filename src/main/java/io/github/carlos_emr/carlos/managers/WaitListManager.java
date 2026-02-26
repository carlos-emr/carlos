/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.velocity.VelocityContext;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.VelocityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WaitListManager {
    private static final Logger logger = MiscUtils.getLogger();

    private static final String WAIT_LIST_EMAIL_PROPERTIES_FILE = "/wait_list_email.properties";
    private static final String WAIT_LIST_URGENT_ADMISSION_EMAIL_TEMPLATE_FILE = "/wait_list_immediate_admission_email_template.txt";
    private static final String WAIT_LIST_A_NEW_APP_TEMPLATE_FILE = "/wait_list_received_a_new_app_template.txt";
    private static final String WAIT_LIST_DAILY_ADMISSION_EMAIL_TEMPLATE_FILE = "/wait_list_daily_admission_email_notification_template.txt";
    private static final String WAIT_LIST_VACANCY_EMAIL_TEMPLATE_FILE = "/wait_list_immediate_vacancy_email_template.txt";

    protected static Properties waitListProperties = getWaitListProperties();

    @Autowired
    private ProgramDao programDao;

    @Autowired
    private AdmissionDao admissionDao;

    @Autowired
    private DemographicDao demographicDao;


    public static class AdmissionDemographicPair {
        private Admission admission;
        private Demographic demographic;

        public Admission getAdmission() {
            return admission;
        }

        public void setAdmission(Admission admission) {
            this.admission = admission;
        }

        public Demographic getDemographic() {
            return demographic;
        }

        public void setDemographic(Demographic demographic) {
            this.demographic = demographic;
        }
    }

    private static Properties getWaitListProperties() {
        Properties p = new Properties();

        try {
            InputStream is = null;
            try {
                is = WaitListManager.class.getResourceAsStream(WAIT_LIST_EMAIL_PROPERTIES_FILE);
                p.load(is);
            } finally {
                if (is != null) is.close();
            }
        } catch (Exception e) {
            logger.error("Error reading properties file : " + WAIT_LIST_EMAIL_PROPERTIES_FILE, e);
        }
        return (p);
    }

    /*
     * This method is deprecated as it relies on WaitListManager's sendAdmissionNotification method.
     * For more details, please refer to the sendAdmissionNotification method.
     *
     * This method will send an email immediately. The expectation is that
     * when an urgent admission / referral is made, that code should call
     * this method immediately.
     */
    @Deprecated
    public void sendImmediateAdmissionNotification(Program program, Demographic demographic, Admission admission, String notes) throws IOException {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (!enableEmailNotifications) return;

        // try {
        // 	is = WaitListManager.class.getResourceAsStream(WAIT_LIST_URGENT_ADMISSION_EMAIL_TEMPLATE_FILE);
        // 	if (is != null) is.close();


    }


    /*
     * This method is deprecated as it relies on WaitListManager's sendAdmissionNotification method.
     * For more details, please refer to the sendAdmissionNotification method.
     *
     * This method will check the given program for admissions/referrals
     * in the last "day". It will then send an email if any admission/referrals
     * have been made in that timeframe.
     *
     * The expectation is that a background thread will call this method, not
     * end-user-actions.
     */
    @Deprecated
    public void checkAndSendAdmissionIntervalNotification(Program program) throws IOException {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (!enableEmailNotifications) return;

        // try {
        // 	is = WaitListManager.class.getResourceAsStream(WAIT_LIST_DAILY_ADMISSION_EMAIL_TEMPLATE_FILE);
        // 	if (is != null) is.close();


        // // check if we need to run, might already have been run
        // // if first run, set default to a few days ago and let it sort itself out.
        // if (lastNotificationDate == null) lastNotificationDate = new Date(System.currentTimeMillis() - (DateUtils.MILLIS_PER_DAY * 4));
        // if (DateUtils.isSameDay(lastNotificationDate, today) || lastNotificationDate.after(today)) return;

        // // get a list of the admissions between since last notification date.
        // // send a notification for those admissions
        // // update last notification date

        // for (Admission admission : admissions) {

    }

    @Deprecated
    private void sendAdmissionNotification(String emailSubject, String emailTemplate, Program program, String notes, Date startDate, Date endDate, ArrayList<AdmissionDemographicPair> admissionDemographicPairs) {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (temp != null) {


        // 	for (String emailAddress : splitEmailAddresses) {
        // 		try {
    }

    protected static VelocityContext getAdmissionVelocityContext(Program program, String notes, Date startDate, Date endDate, ArrayList<AdmissionDemographicPair> admissionDemographicPairs) {
        VelocityContext velocityContext = VelocityUtils.createVelocityContextWithTools();
        velocityContext.put("program", program);
        velocityContext.put("admissionDemographicPairs", admissionDemographicPairs);
        if (notes != null) velocityContext.put("notes", notes);
        if (startDate != null) velocityContext.put("startDate", startDate);
        if (endDate != null) velocityContext.put("endDate", endDate);
        return velocityContext;
    }

    protected static VelocityContext getVacancyVelocityContext(Vacancy vacancy, String notes, Date date) {
        VelocityContext velocityContext = VelocityUtils.createVelocityContextWithTools();
        velocityContext.put("vacancy", vacancy);
        if (notes != null) velocityContext.put("notes", notes);
        if (date != null) velocityContext.put("date", date);
        return velocityContext;
    }

    /*
     * This method is deprecated as it relies on WaitListManager's sendVacancyNotification method.
     * For more details, please refer to the sendVacancyNotification method.
     */
    @Deprecated
    public void sendImmediateVacancyNotification(Vacancy vacancy, String notes) throws IOException {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (!enableEmailNotifications) return;

        // try {
        // 	is = WaitListManager.class.getResourceAsStream(WAIT_LIST_VACANCY_EMAIL_TEMPLATE_FILE);
        // 	if (is != null) is.close();


    }

    @Deprecated
    private void sendVacancyNotification(String emailSubject, String emailTemplate, Vacancy vacancy, String notes, Date date) {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (temp != null) {


        // 	for (String emailAddress : splitEmailAddresses) {
        // 		try {
    }
}
