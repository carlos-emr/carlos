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

package ca.openosp.openo.report.data;

import java.sql.Date;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.DocumentDao;
import ca.openosp.openo.commn.dao.DocumentDao.DocumentType;
import ca.openosp.openo.commn.dao.DocumentDao.Module;
import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.dao.forms.FormsDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.commn.model.LabPatientPhysicianInfo;
import ca.openosp.openo.commn.model.MdsMSH;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.ConversionUtils;

/**
 * This classes main function ConsultReportGenerate collects a group of patients with consults in the last specified date
 * Could use a rewrite
 */
public class RptLabReportData {

    public ArrayList demoList = null;
    public String days = null;

    public RptLabReportData() {
    }

    public ArrayList providerList() {
        ArrayList arrayList = new ArrayList();

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> ps = dao.getProvidersByType(ProviderDao.PR_TYPE_DOCTOR);
        Collections.sort(ps, Comparator.comparing(Provider::getLastName));
        for (Provider p : ps) {
            ArrayList a = new ArrayList();
            a.add(p.getProviderNo());
            a.add(p.getFormattedName());
            arrayList.add(a);
        }
        return arrayList;
    }

    public void labReportGenerate(String providerNo, String days) {
        this.days = days;
        try {
            ResultSet rs;
            // mysql function for dates = select date_sub(now(),interval 1 month);
            // Note: MySQL INTERVAL syntax cannot be parameterized, but we validate it's an integer
            if (!verifyInt(days)) {
                throw new IllegalArgumentException("Invalid days parameter: " + days);
            }

            // Build parameterized SQL query
            String sql = "select distinct l.demographic_no from formLabReq07 l , demographic d where "
                    + " ( to_days( now() ) - to_days(formCreated) ) <= " + " ( to_days( now() ) - to_days( date_sub(now(),interval " + days + " month) ) )"
                    + " and l.demographic_no = d.demographic_no ";

            // Build parameters map
            Map<String, Object> params = new HashMap<>();

            if (!providerNo.equals("-1")) {
                sql = sql + " and d.provider_no = :providerNo ";
                params.put("providerNo", providerNo);
            }
            sql = sql + "  order by d.last_name ";

            FormsDao dao = SpringUtils.getBean(FormsDao.class);
            demoList = new ArrayList();
            DemoLabDataStruct d;
            for (Object[] o : dao.runParameterizedNativeQuery(sql, params)) {
                d = new DemoLabDataStruct();
                d.demoNo = String.valueOf(o[0]);
                demoList.add(d);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().debug("Problems");
            MiscUtils.getLogger().error("Error", e);
        }

    }

    /**
     * This is a inner class that stores info on demographics.  It will get Consult letters that have been scanned and consults for the patient
     */
    public class DemoLabDataStruct {
        public String demoNo;
        ArrayList consultList;
        ArrayList conReplyList;

        public ArrayList getLabReqs() {
            try {
                // Note: MySQL INTERVAL syntax cannot be parameterized, but days is validated in labReportGenerate()
                // Build parameterized SQL query
                String sql = " select ID, formCreated, provider_no from formLabReq07 where demographic_no = :demoNo "
                        + " and to_days(now()) - to_days(formCreated) <=  " + " (to_days( now() ) - to_days( date_sub( now(), interval " + days + " month ) ) )";

                // Build parameters map
                Map<String, Object> params = new HashMap<>();
                params.put("demoNo", demoNo);

                Consult con;
                consultList = new ArrayList();
                FormsDao dao = SpringUtils.getBean(FormsDao.class);
                for (Object[] o : dao.runParameterizedNativeQuery(sql, params)) {
                    String id = String.valueOf(o[0]);
                    String formCreated = String.valueOf(o[1]);
                    String provider_no = String.valueOf(o[2]);

                    con = new Consult();
                    con.requestId = id;
                    con.referalDate = formCreated;
                    con.proNo = provider_no;
                    consultList.add(con);
                }
            } catch (Exception e2) {
                MiscUtils.getLogger().debug(e2.getMessage());
            }
            return consultList;
        }

        public ArrayList getLabReplys() {
            DocumentDao dao = SpringUtils.getBean(DocumentDao.class);
            ConLetter conLetter;
            conReplyList = new ArrayList();
            for (Object[] o : dao.findCtlDocsAndDocsByModuleDocTypeAndModuleId(Module.DEMOGRAPHIC, DocumentType.LAB, ConversionUtils.fromIntString(demoNo))) {
                Document d = (Document) o[1];

                conLetter = new ConLetter();
                conLetter.document_no = "" + d.getDocumentNo();
                conLetter.docdesc = d.getDocdesc();
                conLetter.docfileName = d.getDocfilename();
                if (d.getUpdatedatetime() != null) {
                    conLetter.docDate = new Date(d.getUpdatedatetime().getTime());
                }
                conLetter.docStatus = "" + d.getStatus();
                conReplyList.add(conLetter);
            }
            return conReplyList;
        }

        public ArrayList getLabReports(String demographic, java.util.Date startDate) {
            ArrayList list = new ArrayList();
            try {
                PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
                list = new ArrayList();
                for (Object[] o : dao.findRoutingAndPhysicianInfoByTypeAndDemoNo("CML", ConversionUtils.fromIntString(demographic))) {
                    PatientLabRouting p = (PatientLabRouting) o[0];
                    LabPatientPhysicianInfo l = (LabPatientPhysicianInfo) o[1];

                    java.util.Date lab = getDateFromCML(l.getCollectionDate());
                    MiscUtils.getLogger().debug(lab + " " + startDate + " " + lab.after(startDate));
                    if (startDate != null && lab != null && lab.after(startDate)) {
                        Hashtable h = new Hashtable();
                        h.put("collectionDate", getCommonDate(lab));
                        h.put("id", "" + p.getLabNo());
                        h.put("labType", p.getLabType());
                        list.add(h);
                    }
                }

                for (Object[] o : dao.findRoutingsAndMdsMshByDemoNo(ConversionUtils.fromIntString(demographic))) {
                    PatientLabRouting p = (PatientLabRouting) o[0];
                    MdsMSH m = (MdsMSH) o[1];

                    java.util.Date lab = getDateFromMDS(ConversionUtils.toTimestampString(m.getDateTime()));
                    MiscUtils.getLogger().debug(lab + " " + startDate + " " + lab.after(startDate));
                    if (startDate != null && lab != null && lab.after(startDate)) {
                        Hashtable h = new Hashtable();
                        h.put("collectionDate", getCommonDate(lab));
                        h.put("id", "" + p.getLabNo());
                        h.put("labType", p.getLabType());
                        list.add(h);
                    }
                }
            } catch (Exception e3) {
                MiscUtils.getLogger().debug(e3.getMessage());
                MiscUtils.getLogger().error("Error", e3);
            }
            return list;
        }

        private String getCommonDate(java.util.Date date) {
            String s = "unknown date";
            try {
                Format formatter = new SimpleDateFormat("yyyy-mm-dd");
                s = formatter.format(date);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unable to format date " + date, e);
            }
            return s;
        }

        private java.util.Date getDateFromCML(String d) {
            java.util.Date date = null;
            try {
                DateFormat formatter = new SimpleDateFormat("dd-MMM-yy");
                date = formatter.parse(d);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unable to format date " + date, e);
            }
            return date;
        }

        private java.util.Date getDateFromMDS(String d) { //2004-11-17 16:23:23
            java.util.Date date = null;
            try {
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                date = formatter.parse(d);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Unable to format date " + date, e);
            }
            return date;
        }

        public String getDemographicName() {
            String retval = "&nbsp;";
            DemographicDao dao = SpringUtils.getBean(DemographicDao.class);
            Demographic demo = dao.getDemographic(demoNo);
            if (demo != null) {
                retval = demo.getFullName();
            }
            return retval;
        }

        public final class Consult {
            public String requestId;
            public String referalDate;
            public String serviceId;
            public String specialist;
            public String appDate;
            public String proNo;
        }

        ;

        public final class ConLetter {
            public String document_no;
            public String docdesc;
            public String docfileName;
            public String docStatus;
            public java.sql.Date docDate;
        }

        ;

    }

    ;

    /**
     * Validates that a string can be parsed as an integer.
     * Used to validate user input before concatenating into SQL INTERVAL clauses.
     *
     * @param str The string to validate
     * @return true if the string is a valid integer, false otherwise
     */
    private boolean verifyInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
