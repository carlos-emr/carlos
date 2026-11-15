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


package io.github.carlos_emr.carlos.encounter.data;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EChart;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/** Legacy encounter view models backed by managed demographic and chart DAOs. */
public class EctPatientData {

    public static String getProviderNo(LoggedInInfo loggedInInfo, String demographicNo) {
        String ret = "";
        try {
            Demographic patient = SpringUtils.getBean(DemographicDao.class).getDemographic(demographicNo);
            if (patient != null) ret = StringUtils.trimToEmpty(patient.getProviderNo());
        } catch (RuntimeException e) {
            MiscUtils.getLogger().debug("Unable to read encounter provider (causeType={})", e.getClass().getName());
        }
        LogAction.addLogSynchronous(loggedInInfo, "EctPatientData.getProviderNo", "demographicNo=" + demographicNo);


        return ret;
    }

    public Patient getPatient(LoggedInInfo loggedInInfo, String demographicNo) {

        Patient p = null;
        try {
            Demographic patient = SpringUtils.getBean(DemographicDao.class).getDemographic(demographicNo);
            if (patient != null) {
                p = new Patient(patient.getDemographicNo(), StringUtils.trimToEmpty(patient.getLastName()),
                        StringUtils.trimToEmpty(patient.getFirstName()), StringUtils.trimToEmpty(patient.getSex()),
                        UtilDateUtilities.calcDate(StringUtils.trimToEmpty(patient.getYearOfBirth()),
                                patient.getMonthOfBirth(), StringUtils.trimToEmpty(patient.getDateOfBirth())),
                        StringUtils.trimToEmpty(patient.getAddress()), StringUtils.trimToEmpty(patient.getCity()),
                        StringUtils.trimToEmpty(patient.getPostal()), StringUtils.trimToEmpty(patient.getPhone()),
                        StringUtils.trimToEmpty(patient.getRosterStatus()));
            }
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("Unable to read encounter patient (causeType={})", e.getClass().getName());
        }

        LogAction.addLogSynchronous(loggedInInfo, "EctPatientData.getPatient", "demographicNo=" + demographicNo);

        return p;
    }

    public class Patient {
        int demographicNo;
        String surname;
        String firstName;
        String sex;
        Date DOB;
        String address;
        String city;
        String postal;
        String phone;
        String roster;

        public Patient(int demographicNo, String surname, String firstName, String sex, Date DOB, String address,
                       String city, String postal, String phone, String roster) {
            this.demographicNo = demographicNo;
            this.surname = surname;
            this.firstName = firstName;
            this.sex = sex;
            this.DOB = DOB;
            this.address = address;
            this.city = city;
            this.postal = postal;
            this.phone = phone;
            this.roster = roster;
        }

        public int getDemographicNo() {
            return demographicNo;
        }

        public String getSurname() {
            return surname;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getSex() {
            return sex;
        }

        public Date getDOB() {
            return DOB;
        }

        public String getAge() {
            return UtilDateUtilities.calcAge(getDOB());
        }

        public String getAddress() {
            return address;
        }

        public String getCity() {
            return city;
        }

        public String getPostal() {
            return postal;
        }

        public String getPhone() {
            return phone;
        }

        public String getRosterStatus() {
            return roster;
        }

        public eChart getEChart() {
            return new eChart();
        }

        /*******************************************************************************************
         * class eChart
         ******************************************************************************************/
        public class eChart {
            private Date eChartTimeStamp = null;
            private String socialHistory = "";
            private String familyHistory = "";
            private String medicalHistory = "";
            private String ongoingConcerns = "";
            private String reminders = "";
            private String encounter = "";
            private String subject = "";

            eChart() {
                init();
            }

            private void init() {
                CarlosProperties properties = CarlosProperties.getInstance();
                if (!Boolean.parseBoolean(properties.getProperty("AbandonOldChart", "false"))) {
                    try {
                        EChart chart = SpringUtils.getBean(EChartDao.class).getLatestChartById(demographicNo);
                        if (chart != null) {
                            this.eChartTimeStamp = chart.getTimestamp();
                            this.socialHistory = StringUtils.trimToEmpty(chart.getSocialHistory());
                            this.familyHistory = StringUtils.trimToEmpty(chart.getFamilyHistory());
                            this.medicalHistory = StringUtils.trimToEmpty(chart.getMedicalHistory());
                            this.ongoingConcerns = StringUtils.trimToEmpty(chart.getOngoingConcerns());
                            this.reminders = StringUtils.trimToEmpty(chart.getReminders());
                            this.encounter = StringUtils.trimToEmpty(chart.getEncounter());
                            this.subject = StringUtils.trimToEmpty(chart.getSubject());
                        }
                    } catch (RuntimeException e) {
                        MiscUtils.getLogger().error("Unable to read encounter chart (causeType={})", e.getClass().getName());
                    }
                }
            }

            public Date getEChartTimeStamp() {
                return this.eChartTimeStamp;
            }

            public String getSocialHistory() {
                return this.socialHistory;
            }

            public String getFamilyHistory() {
                return this.familyHistory;
            }

            public String getMedicalHistory() {
                return this.medicalHistory;
            }

            public String getOngoingConcerns() {
                return this.ongoingConcerns;
            }

            public String getReminders() {
                return this.reminders;
            }

            public String getEncounter() {
                return this.encounter;
            }

            public String getSubject() {
                return this.subject;
            }
        }
    }
}
