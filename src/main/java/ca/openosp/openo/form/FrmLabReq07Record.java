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


package ca.openosp.openo.form;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import ca.openosp.Misc;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicForm;
import ca.openosp.openo.caisi_integrator.ws.CachedProgram;
import ca.openosp.openo.caisi_integrator.ws.CachedProvider;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdIntegerCompositePk;
import ca.openosp.openo.caisi_integrator.ws.FacilityIdStringCompositePk;
import ca.openosp.openo.commn.dao.ClinicDAO;
import ca.openosp.openo.commn.model.Clinic;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.utility.LocaleUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.db.DBHandler;
import ca.openosp.openo.util.UtilDateUtilities;

public class FrmLabReq07Record extends FrmRecord {
    private static Logger logger = MiscUtils.getLogger();

    private ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);


    @Override
    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        Properties props = new Properties();

        if (existingID <= 0) {
            Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);

            if (demographic != null) {
                props.setProperty("demographic_no", String.valueOf(demographic.getDemographicNo()));
                props.setProperty("patientName", demographic.getLastName() + ", " + demographic.getFirstName());

                String uhipStatus = OscarProperties.getInstance().getProperty("demo_uhip_status", "");
                if (!uhipStatus.isEmpty() && demographic.getRosterStatus().equals(uhipStatus)) {
                    props.setProperty("healthNumber", LocaleUtils.getMessage(Locale.getDefault(), "oscarEncounter.form.uhipLbl") + StringUtils.trimToEmpty(demographic.getChartNo()));
                    props.setProperty("version", "");
                } else {
                    props.setProperty("healthNumber", StringUtils.trimToEmpty(demographic.getHin()));
                    props.setProperty("version", StringUtils.trimToEmpty(demographic.getVer()));
                }

                props.setProperty("hcType", StringUtils.trimToEmpty(demographic.getHcType()));
                props.setProperty("formCreated", UtilDateUtilities.DateToString(new Date(),
                        "yyyy/MM/dd"));

                //props.setProperty("formEdited",
                // UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
                java.util.Date dob = UtilDateUtilities.calcDate(demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
                props.setProperty("birthDate", StringUtils.trimToEmpty(UtilDateUtilities.DateToString(dob, "yyyy/MM/dd")));

                props.setProperty("phoneNumber", StringUtils.trimToEmpty(demographic.getPhone()));
                props.setProperty("patientAddress", StringUtils.trimToEmpty(demographic.getAddress()));
                props.setProperty("patientCity", StringUtils.trimToEmpty(demographic.getCity()));
                props.setProperty("patientPC", StringUtils.trimToEmpty(demographic.getPostal()));
                props.setProperty("province", StringUtils.trimToEmpty(demographic.getProvince()));
                props.setProperty("sex", StringUtils.trimToEmpty(demographic.getSex()));
                props.setProperty("demoProvider", StringUtils.trimToEmpty(demographic.getProviderNo()));
                props.setProperty("patientChartNo", StringUtils.trimToEmpty(demographic.getChartNo()));
            }

            //get local clinic information
            Clinic clinic = clinicDao.getClinic();
            if (clinic != null) {
                props.setProperty("clinicName", clinic.getClinicName());
                props.setProperty("clinicProvince", clinic.getClinicProvince());
                props.setProperty("clinicAddress", clinic.getClinicAddress());
                props.setProperty("clinicCity", clinic.getClinicCity());
                props.setProperty("clinicPC", clinic.getClinicPostal());
            }

        } else {
            String sql = "SELECT * FROM formLabReq07 WHERE demographic_no = " + demographicNo + " AND ID = "
                    + existingID;
            props = (new FrmRecordHelp()).getFormRecord(sql);
            String chartNo = props.getProperty("patientChartNo");
            String chartNoLbl = LocaleUtils.getMessage(Locale.getDefault(), "oscarEncounter.form.labreq.patientChartNo") + ":";
            int beginIdx = chartNo.lastIndexOf(chartNoLbl);
            if (beginIdx >= 0) {
                chartNo = chartNo.substring(beginIdx + chartNoLbl.length());
                props.setProperty("patientChartNo", chartNo);
            }

            OscarProperties oscarProps = OscarProperties.getInstance();
            if (oscarProps.getBooleanProperty("use_lab_clientreference", "true")) {
                String additionalInfo = LocaleUtils.getMessage(Locale.getDefault(), "oscarEncounter.form.labreq.clientreference") + ":" + String.valueOf(existingID);
                props.setProperty("clientRefNo", additionalInfo);
            }
        }

        return props;
    }

    public Properties getFormCustRecord(LoggedInInfo loggedInInfo, Facility facility, Properties props, String provNo) throws SQLException {
        String demoProvider = props.getProperty("demoProvider", "");
        String xmlSpecialtyCode = "<xml_p_specialty_code>";
        String xmlSpecialtyCode2 = "</xml_p_specialty_code>";

        ResultSet rs = null;
        String sql = null;


        if (demoProvider.equals(provNo)) {
            // from provider table
            sql = "SELECT CONCAT(last_name, ', ', first_name) AS provName, ohip_no, comments "
                    + "FROM provider WHERE provider_no = '" + provNo + "'";
            rs = DBHandler.GetSQL(sql);

            if (rs.next()) {
                String comments = Misc.getString(rs, "comments");
                String strSpecialtyCode = "00";
                if (comments.indexOf(xmlSpecialtyCode) != -1) {
                    strSpecialtyCode = comments.substring(comments.indexOf(xmlSpecialtyCode) + xmlSpecialtyCode.length(), comments.indexOf(xmlSpecialtyCode2));
                    strSpecialtyCode = strSpecialtyCode.trim();
                    if (strSpecialtyCode.equals("")) {
                        strSpecialtyCode = "00";
                    }
                }
                String num = Misc.getString(rs, "ohip_no");
                props.setProperty("reqProvName", Misc.getString(rs, "provName"));
                props.setProperty("provName", Misc.getString(rs, "provName"));
                props.setProperty("practitionerNo", "0000-" + num + "-" + strSpecialtyCode);
            }
            rs.close();
        } else {
            // from provider table
            sql = "SELECT CONCAT(last_name, ', ', first_name) AS provName, ohip_no, comments FROM provider WHERE provider_no = '"
                    + provNo + "'";
            rs = DBHandler.GetSQL(sql);

            String num = "";
            if (rs.next()) {
                String comments = Misc.getString(rs, "comments");
                String strSpecialtyCode = "00";
                if (comments.indexOf(xmlSpecialtyCode) != -1) {
                    strSpecialtyCode = comments.substring(comments.indexOf(xmlSpecialtyCode) + xmlSpecialtyCode.length(), comments.indexOf(xmlSpecialtyCode2));
                    strSpecialtyCode = strSpecialtyCode.trim();
                    if (strSpecialtyCode.equals("")) {
                        strSpecialtyCode = "00";
                    }
                }
                num = Misc.getString(rs, "ohip_no");
                props.setProperty("reqProvName", Misc.getString(rs, "provName"));
                props.setProperty("practitionerNo", "0000-" + num + "-" + strSpecialtyCode);
            }
            rs.close();

            if (!demoProvider.equals("")) {
                // from provider table
                sql = "SELECT CONCAT(last_name, ', ', first_name) AS provName, ohip_no FROM provider WHERE provider_no = "
                        + demoProvider;
                rs = DBHandler.GetSQL(sql);

                if (rs.next()) {
                    if (num.equals("")) {
                        num = Misc.getString(rs, "ohip_no");
                        props.setProperty("practitionerNo", "0000-" + num + "-00");
                    }
                    props.setProperty("provName", Misc.getString(rs, "provName"));

                }
                rs.close();
            }
        }

        //get local clinic information
        Clinic clinic = clinicDao.getClinic();
        if (clinic != null) {
            props.setProperty("clinicName", clinic.getClinicName());
            props.setProperty("clinicProvince", clinic.getClinicProvince());
            props.setProperty("clinicAddress", clinic.getClinicAddress());
            props.setProperty("clinicCity", clinic.getClinicCity());
            props.setProperty("clinicPC", clinic.getClinicPostal());
        }

        //lab_req_override=true
        OscarProperties oscarProps = OscarProperties.getInstance();
        if (oscarProps.getProperty("lab_req_provider", "").length() > 0) {
            props.setProperty("reqProvName", oscarProps.getProperty("lab_req_provider"));
        }
        if (oscarProps.getProperty("lab_req_billing_no", "").length() > 0) {
            props.setProperty("practitionerNo", oscarProps.getProperty("lab_req_billing_no"));
        }

        if (facility.isIntegratorEnabled()) {
            //if patient was from integrator link up doc from other site
            try {
                Integer localDemographicId = Integer.parseInt(props.getProperty("demographic_no"));
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, facility);
                List<DemographicTransfer> directLinks = demographicWs.getDirectlyLinkedDemographicsByDemographicId(localDemographicId);

                if (directLinks.size() > 0) {
                    props.setProperty("copy2clinician", "checked");
                    DemographicTransfer demographicTransfer = directLinks.get(0);

                    FacilityIdStringCompositePk providerPk = new FacilityIdStringCompositePk();
                    providerPk.setIntegratorFacilityId(demographicTransfer.getIntegratorFacilityId());
                    providerPk.setCaisiItemId(demographicTransfer.getLastUpdateUser());
                    CachedProvider p = CaisiIntegratorManager.getProvider(loggedInInfo, facility, providerPk);
                    if (p != null) {
                        props.setProperty("copyLname", p.getLastName());
                        props.setProperty("copyFname", p.getFirstName());

                        List<CachedProgram> cps = CaisiIntegratorManager.getAllPrograms(loggedInInfo, facility);
                        for (CachedProgram cp : cps) {
                            if (providerPk.getIntegratorFacilityId() == cp.getFacilityIdIntegerCompositePk().getIntegratorFacilityId() && "OSCAR".equals(cp.getName()) && cp.getAddress() != null) {
                                props.setProperty("copyAddress", cp.getAddress());
                            }
                        }

                    }
                }

            } catch (Exception e) {
                logger.error("error", e);
            }
        }

        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formLabReq07 WHERE demographic_no=" + demographic_no + " AND ID=0";

        return ((new FrmRecordHelp()).saveFormRecord(props, sql));
    }

    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formLabReq07 WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
        return ((new FrmRecordHelp()).getPrintRecord(sql));
    }

    public static List<Properties> getPrintRecords(int demographicNo) throws SQLException {
        String sql = "SELECT * FROM formLabReq07 WHERE demographic_no = " + demographicNo;
        return ((new FrmRecordHelp()).getPrintRecords(sql));
    }

    public static List<Properties> getPrintRecordsSince(int demographicNo, Date lastUpdateDate) throws SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = "SELECT * FROM formLabReq07 WHERE demographic_no = " + demographicNo + " and formEdited > '" + formatter.format(lastUpdateDate) + "'";
        return ((new FrmRecordHelp()).getPrintRecords(sql));
    }

    public static List<Integer> getDemogaphicIdsSince(Date lastUpdateDate) throws SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = "SELECT demographic_no FROM formLabReq07 WHERE formEdited > '" + formatter.format(lastUpdateDate) + "'";
        return ((new FrmRecordHelp()).getDemographicIds(sql));
    }


    public String findActionValue(String submit) throws SQLException {
        return ((new FrmRecordHelp()).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return ((new FrmRecordHelp()).createActionURL(where, action, demoId, formId));
    }


    public static Properties getRemoteRecordProperties(LoggedInInfo loggedInInfo, Integer remoteFacilityId, Integer formId, Integer demoNo) throws IOException {
        FacilityIdIntegerCompositePk pk = new FacilityIdIntegerCompositePk();
        pk.setIntegratorFacilityId(remoteFacilityId);
        pk.setCaisiItemId(formId);

        CachedDemographicForm form = null;
        try {
            if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                form = demographicWs.getCachedDemographicForm(pk);
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
        }


        if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
            Integer demographicNo = 0;
            List<CachedDemographicForm> forms = IntegratorFallBackManager.getRemoteForms(loggedInInfo, demoNo, "formLabReq07");
            for (CachedDemographicForm f : forms) {
                if (f.getFacilityIdIntegerCompositePk().getCaisiItemId() == pk.getCaisiItemId() && f.getFacilityIdIntegerCompositePk().getIntegratorFacilityId() == pk.getIntegratorFacilityId()) {
                    form = f;
                    break;
                }
            }
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(form.getFormData().getBytes());

        Properties p = new Properties();
        p.load(bais);

        // missing
        // props.setProperty("hcType", demographic.getHcType());
        // props.setProperty("demoProvider", demographic.getProviderNo());
        // props.setProperty("clinicProvince",oscar.Misc.getString(rs, "clinic_province"));

        logger.debug("Remote properties : " + p);

        return (p);
    }
}
