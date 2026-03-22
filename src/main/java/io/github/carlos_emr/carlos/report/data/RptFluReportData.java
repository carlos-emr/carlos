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

package io.github.carlos_emr.carlos.report.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Data provider for the flu vaccination report. Generates a list of patients eligible
 * for flu vaccination based on provider and retrieves billing history for flu-related
 * service codes (G590A, G591A). Contains the inner class {@link DemoFluDataStruct}
 * to hold per-patient data.
 *
 * @since 2001-01-01
 */
public class RptFluReportData {

    public ArrayList<DemoFluDataStruct> demoList;
    public String years;

    public RptFluReportData() {
        demoList = null;
        years = null;
    }

    @SuppressWarnings("unchecked")
    public List<Provider> providerList() {
        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> pList = dao.getActiveProviders();

        return pList;
    }

    public void fluReportGenerate(String s, String s1) {
        years = s1;

        DemographicDao dao = SpringUtils.getBean(DemographicDao.class);

        demoList = new ArrayList<DemoFluDataStruct>();
        DemoFluDataStruct demofludatastruct;
        for (Object[] o : dao.findDemographicsForFluReport(s)) {
            String demographic_no = String.valueOf(o[0]);
            String demoname = String.valueOf(o[0]);
            String phone = String.valueOf(o[0]);
            String roster_status = String.valueOf(o[0]);
            String patient_status = String.valueOf(o[0]);
            String dob = String.valueOf(o[0]);
            String age = String.valueOf(o[0]);

            demofludatastruct = new DemoFluDataStruct();
            demofludatastruct.demoNo = demographic_no;
            demofludatastruct.demoName = demoname;
            demofludatastruct.demoPhone = phone;
            demofludatastruct.demoRosterStatus = roster_status;
            demofludatastruct.demoPatientStatus = patient_status;
            demofludatastruct.demoDOB = dob;
            demofludatastruct.demoAge = age;

            demoList.add(demofludatastruct);
        }
    }

    public class DemoFluDataStruct {

        public String demoNo;
        public String demoName;
        public String demoPhone;
        public String demoRosterStatus;
        public String demoAge;
        public String demoDOB;
        public String demoPatientStatus;

        public String getDemoNo() {
            return demoNo;
        }

        public String getDemoName() {
            return demoName;
        }

        public String getDemoPhone() {
            return demoPhone;
        }

        public String getDemoAge() {
            return demoAge;
        }

        public String getDemoDOB() {
            return demoDOB;
        }

        public String getBillingDate() {
            String s = "&nbsp;";

            BillingDao dao = SpringUtils.getBean(BillingDao.class);
            for (Billing b : dao.findBillingsByDemoNoServiceCodeAndDate(ConversionUtils.fromIntString(demoNo), ConversionUtils.fromDateString("2003-04-01"), Arrays.asList(new String[]{"G590A", "G591A"}))) {
                s = ConversionUtils.toDateString(b.getBillingDate());
            }
            return s;
        }

        public String getBillingDate(String reportYear) {
            String s = "&nbsp;";

            String sDate = reportYear + "-01-01";
            String eDate = reportYear + "-12-31";

            BillingONCHeader1Dao dao = SpringUtils.getBean(BillingONCHeader1Dao.class);
            for (BillingONCHeader1 b : dao.findBillingsByDemoNoCh1HeaderServiceCodeAndDate(ConversionUtils.fromIntString(demoNo), Arrays.asList(new String[]{"G590A", "G591A"}), ConversionUtils.fromDateString(sDate), ConversionUtils.fromDateString(eDate))) {
                s = ConversionUtils.toDateString(b.getBillingDate());
                break;
            }

            return s;
        }
    }
}
