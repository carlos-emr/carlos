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
package ca.openosp.openo.commn.web;

import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.form.*;
import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.WordUtils;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.util.LabelValueBean;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.*;

public class Pregnancy2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private EpisodeDao episodeDao = SpringUtils.getBean(EpisodeDao.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    static String labReqVersion;

    static {
        labReqVersion = OscarProperties.getInstance().getProperty("onare_labreqver", "07");
        if (labReqVersion == "") {
            labReqVersion = "10";
        }
    }

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("getLatestFormIdByPregnancy".equals(method)) {
            return getLatestFormIdByPregnancy();
        } else if ("create".equals(method)) {
            return create();
        } else if ("complete".equals(method)) {
            return complete();
        } else if ("doComplete".equals(method)) {
            return doComplete();
        } else if ("doDelete".equals(method)) {
            return doDelete();
        } else if ("list".equals(method)) {
            return list();
        } else if ("createGBSLabReq".equals(method)) {
            return createGBSLabReq();
        } else if ("createMCVLabReq".equals(method)) {
            return createMCVLabReq();
        } else if ("getAllergies".equals(method)) {
            return getAllergies();
        } else if ("getMeds".equals(method)) {
            return getMeds();
        } else if ("saveFormAjax".equals(method)) {
            return saveFormAjax();
        } else if ("getMeasurementsAjax".equals(method)) {
            return getMeasurementsAjax();
        } else if ("saveMeasurementAjax".equals(method)) {
            return saveMeasurementAjax();
        } else if ("getAR1Labs".equals(method)) {
            return getAR1Labs();
        } else if ("loadEformByName".equals(method)) {
            return loadEformByName();
        } else if ("createGCTLabReq".equals(method)) {
            return createGCTLabReq();
        } else if ("createGTTLabReq".equals(method)) {
            return createGTTLabReq();
        } else if ("getEformsByGroupAjax".equals(method)) {
            return getEformsByGroupAjax();
        } else if ("getFundalImage".equals(method)) {
            return getFundalImage();
        } else if ("recordPrint".equals(method)) {
            return recordPrint();
        } else if ("getPrintData".equals(method)) {
            return getPrintData();
        } 
        return getLatestFormIdByPregnancy();
    }

    public String getLatestFormIdByPregnancy() throws IOException {
        String episodeId = request.getParameter("episodeId");

        Integer formId = 0;
        if (episodeId != null) {
            try {
                formId = PregnancyFormsDao.getLatestFormIdByPregnancy(Integer.parseInt(episodeId));
            } catch (NumberFormatException e) {
                //empty
            }
        }
        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("formId", String.valueOf(formId)));
        response.getWriter().println(json);
        return null;
    }

    public String create() {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        String code = request.getParameter("code");
        String codeType = request.getParameter("codetype");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        //check for an existing pregnancy
        List<String> codes = new ArrayList<String>();
        codes.add("72892002");
        codes.add("47200007");
        codes.add("16356006");
        codes.add("34801009");
        List<Episode> existingEpisodes = episodeDao.findCurrentByCodeTypeAndCodes(demographicNo, "SnomedCore", codes);
        if (existingEpisodes.size() > 0) {
            request.setAttribute("error", "There is already a pregnancy in progress. Please close the existing one before creating a new one.");
            return SUCCESS;
        }

        AbstractCodeSystemDao dao = (AbstractCodeSystemDao) SpringUtils.getBean(WordUtils.uncapitalize(codeType) + "Dao");
        AbstractCodeSystemModel mod = dao.findByCode(code);

        if (mod == null) {
            request.setAttribute("error", "There was an internal error processing this request, please contact your system administrator");
            return SUCCESS;
        }

        //create pregnancy episode
        Episode e = new Episode();
        e.setCode(code);
        e.setCodingSystem(codeType);
        e.setDemographicNo(demographicNo);
        e.setDescription("");
        e.setLastUpdateTime(new Date());
        e.setLastUpdateUser(providerNo);
        e.setStatus("Current");
        e.setStartDate(new Date());
        e.setDescription(mod.getDescription());
        episodeDao.persist(e);

        //start up a new ar on enhanced form
        try {
            FrmONAREnhancedRecord f = new FrmONAREnhancedRecord();
            Properties p = f.getFormRecord(loggedInInfo, demographicNo, 0);
            p.setProperty("episodeId", String.valueOf(e.getId()));
            f.saveFormRecord(p);
        } catch (SQLException ee) {
            MiscUtils.getLogger().error("Error", ee);
        }

        return SUCCESS;
    }

    public String complete() {
        Integer episodeId = Integer.parseInt(request.getParameter("episodeId"));
        Episode e = episodeDao.find(episodeId);
        if (e == null) {
            request.setAttribute("error", "There was an internal error. Please contact tech support.");
        }
        request.setAttribute("episode", e);
        return "complete";
    }

    public String doComplete() {
        //Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        Integer episodeId = Integer.parseInt(request.getParameter("episodeId"));
        String endDate = request.getParameter("endDate");
        String notes = request.getParameter("notes");
        Episode e = episodeDao.find(episodeId);
        if (e != null) {
            e.setStatus("Complete");
            e.setEndDateStr(endDate);
            e.setNotes(notes);
            episodeDao.merge(e);
            request.setAttribute("close", true);
        } else {
            request.setAttribute("error", "There was an internal error. Please contact tech support.");
            return "complete";
        }
        return "complete";
    }

    public String doDelete() {
        //Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        Integer episodeId = Integer.parseInt(request.getParameter("episodeId"));
        Episode e = episodeDao.find(episodeId);
        String notes = request.getParameter("notes");
        if (e != null) {
            e.setNotes(notes);
            e.setStatus("Deleted");
            e.setEndDate(new Date());
            episodeDao.merge(e);
            request.setAttribute("close", true);
        } else {
            request.setAttribute("error", "There was an internal error. Please contact tech support.");
            return "complete";
        }
        return "complete";
    }

    public String list() {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));

        List<String> codes = new ArrayList<String>();
        codes.add("72892002");
        codes.add("47200007");
        codes.add("16356006");
        codes.add("34801009");
        List<Episode> episodes = episodeDao.findCurrentByCodeTypeAndCodes(demographicNo, "SnomedCore", codes);
        List<Episode> episodes2 = episodeDao.findCompletedByCodeTypeAndCodes(demographicNo, "SnomedCore", codes);
        episodes.addAll(episodes2);
        request.setAttribute("episodes", episodes);
        return "list";
    }

    public String createGBSLabReq() throws SQLException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        String penicillin = request.getParameter("penicillin");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);
            if (penicillin != null && penicillin.equals("checked")) {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS w/ sensitivities");
                p.setProperty("o_otherTests2", "pt allergic to penicillin");
            } else {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p);
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);
            if (penicillin != null && penicillin.equals("checked")) {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS w/ sensitivities");
                p.setProperty("o_otherTests2", "pt allergic to penicillin");
            } else {
                p.setProperty("o_otherTests1", "Vaginal Anal GBS");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p);
        }

        return null;
    }

    public String createMCVLabReq() throws SQLException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        String ferritin = request.getParameter("ferritin");
        String hbElectrophoresis = request.getParameter("hb_electrophoresis");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (ferritin != null && ferritin.equals("checked")) {
                p.setProperty("b_ferritin", "checked=\"checked\"");
            }
            if (hbElectrophoresis != null && hbElectrophoresis.equals("checked")) {
                p.setProperty("o_otherTests1", "Hb Electrophoresis");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p);

        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (ferritin != null && ferritin.equals("checked")) {
                p.setProperty("b_ferritin", "checked=\"checked\"");
            }
            if (hbElectrophoresis != null && hbElectrophoresis.equals("checked")) {
                p.setProperty("o_otherTests1", "Hb Electrophoresis");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p);

        }


        return null;
    }

    public String getAllergies() throws IOException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        AllergyDao allergyDao = SpringUtils.getBean(AllergyDao.class);
        List<Allergy> allergies = allergyDao.findActiveAllergies(demographicNo);
        StringBuilder output = new StringBuilder();
        for (Allergy allergy : allergies) {
            output.append(allergy.getDescription());
            if (allergy.getReaction() != null) {
                output.append(":" + allergy.getReaction());
            }
            output.append("\n");
        }

        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("allergies", output.toString().trim()));
        response.getWriter().println(json);
        return null;
    }

    public String getMeds() throws IOException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
        List<Drug> drugs = drugDao.findByDemographicId(demographicNo, false);
        StringBuilder output = new StringBuilder();
        for (Drug drug : drugs) {
            if (drug.isArchived() || drug.isDeleted() || drug.isDiscontinued() || drug.isExpired()) {
                continue;
            }
            if (drug.getBrandName() != null && drug.getBrandName().length() > 0) {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getBrandName());
            } else if (drug.getCustomName() != null && drug.getCustomName().length() > 0) {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getCustomName());
            } else {
                if (output.length() > 0)
                    output.append(",");
                output.append(drug.getSpecial());
            }
        }

        ObjectNode json = objectMapper.valueToTree(new LabelValueBean("meds", output.toString().trim()));
        response.getWriter().println(json);
        return null;
    }

    public String saveFormAjax() throws IOException {
        int newID = 0;
        FrmRecord rec = null;
        ObjectNode jsonObj = null;

        try {
            FrmRecordFactory recorder = new FrmRecordFactory();
            rec = recorder.factory(request.getParameter("form_class"));
            Properties props = new Properties();

            boolean bMulPage = request.getParameter("c_lastVisited") != null ? true : false;
            String name;

            if (bMulPage) {
                String curPageNum = request.getParameter("c_lastVisited");
                String commonField = request.getParameter("commonField") != null ? request
                        .getParameter("commonField") : "&'";
                curPageNum = curPageNum.length() > 3 ? ("" + curPageNum.charAt(0)) : curPageNum;

                //copy an old record
                props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), Integer.parseInt(request.getParameter("demographic_no")), Integer
                        .parseInt(request.getParameter("formId")));

                //empty the current page
                Properties currentParam = new Properties();
                for (Enumeration varEnum = request.getParameterNames(); varEnum.hasMoreElements(); ) {
                    name = (String) varEnum.nextElement();
                    currentParam.setProperty(name, "");
                }
                for (Enumeration varEnum = props.propertyNames(); varEnum.hasMoreElements(); ) {
                    name = (String) varEnum.nextElement();
                    // kick off the current page elements, commonField on the current page
                    if (name.startsWith(curPageNum + "_")
                            || (name.startsWith(commonField) && currentParam.containsKey(name))) {
                        props.remove(name);
                    }
                }
            }

            //update the current record
            for (Enumeration varEnum = request.getParameterNames(); varEnum.hasMoreElements(); ) {
                name = (String) varEnum.nextElement();
                props.setProperty(name, request.getParameter(name));
            }

            props.setProperty("provider_no", (String) request.getSession().getAttribute("user"));
            newID = rec.saveFormRecord(props);
            String ip = request.getRemoteAddr();
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, request
                    .getParameter("form_class"), "" + newID, ip, request.getParameter("demographic_no"));


            jsonObj = objectMapper.valueToTree(new LabelValueBean("result", String.valueOf(newID)));


        } catch (Exception ex) {
            MiscUtils.getLogger().error("error", ex);
            jsonObj = objectMapper.valueToTree(new LabelValueBean("result", "error"));

        }

        response.getWriter().print(jsonObj.toString());

        return null;
    }

    public String getMeasurementsAjax() throws IOException {
        String demographicNo = request.getParameter("demographicNo");
        String type = request.getParameter("type");

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);
        List<Measurement> m = md.findByType(Integer.parseInt(demographicNo), type);

        ArrayNode json = objectMapper.valueToTree(m);
        response.getWriter().print(json.toString());

        return null;
    }

    public String saveMeasurementAjax() throws IOException {
        String demographicNo = request.getParameter("demographicNo");
        String type = request.getParameter("type");
        String value = request.getParameter("value");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);

        Measurement m = new Measurement();
        m.setAppointmentNo(0);
        m.setComments("");
        m.setDataField(value);
        m.setDateObserved(new Date());
        m.setDemographicId(Integer.parseInt(demographicNo));
        m.setMeasuringInstruction("");
        m.setProviderNo(providerNo);
        m.setType(type);

        md.persist(m);

        ObjectNode jsonObj = objectMapper.valueToTree(new LabelValueBean("result", "success"));
        response.getWriter().print(jsonObj);

        return null;
    }

    /*
     * Tests, and the LOINC code, I could find for it
     *
     * Hemoglobin (718-7)
     * HIV (public health - manually entered for now) (X50045) - hiv serology (GDML:HIV)
     * MCV (787-2)
     * ABO - GDML (4490) test name is "Blood Group" - includes RH. Textual though
     * RH (10331-7)
     * PAP Smear (GDML:GY04)
     * Antibody screen (8061-4)?? (GDML:4482)
     * Gonnorhea
     * Rubella (25514-1)?
     * Chlamydia - urine GDML:CHLD
     * HbsAG (5196-1) GDML:HB1
     * Urine C&S
     * VDRL (public health) (X100666)
     * Sickle Cell
     */
    public String getAR1Labs() throws IOException {
        String demographicNo = request.getParameter("demographicNo");

        ArrayNode json = objectMapper.createArrayNode();

        MeasurementDao md = SpringUtils.getBean(MeasurementDao.class);
        List<Measurement> m = md.findByType(Integer.parseInt(demographicNo), "HEMO");
        if (m.size() > 0) {
            json.add(objectMapper.valueToTree(m.get(0)));
        }
        m = md.findByType(Integer.parseInt(demographicNo), "MCV");
        if (m.size() > 0) {
            json.add(objectMapper.valueToTree(m.get(0)));
        }

        response.getWriter().print(json.toString());

        return null;
    }

    public String loadEformByName() {
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        //Prenatal Screening (IPS) Credit Valley
        //Prenatal Screening - North York
        String demographicNo = request.getParameter("demographicNo");
        String name = request.getParameter("name");
        String apptNo = request.getParameter("appointmentNo");
        if (apptNo == null) {
            apptNo = "0";
        }
        EForm eform = eformDao.findByName(name);

        String url;
        if (eform != null) {
            url = "/eform/efmformadd_data.jsp?fid=" + eform.getId() + "&demographic_no=" + demographicNo + "&appointment=" + apptNo;
        } else {
            url = "/pregnancy/eform_not_found.jsp";
        }
        try {
            response.sendRedirect(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return NONE;
    }

    /*
     * Hb
Urine C&S
Repeat antibody screen
1 hour 50 gm glucose screen
     */
    public String createGCTLabReq() throws SQLException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        String hb = request.getParameter("hb");
        String urine = request.getParameter("urine");
        String antibody = request.getParameter("antibody");
        String glucose = request.getParameter("glucose");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();


        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (hb != null && hb.equals("checked")) {
                p.setProperty("h_cbc", "checked=\"checked\"");
            }
            if (urine != null && urine.equals("checked")) {
                p.setProperty("m_urine", "checked=\"checked\"");
            }
            if (antibody != null && antibody.equals("checked")) {
                p.setProperty("i_repeatPrenatalAntibodies", "checked=\"checked\"");
            }
            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "1 Hr 50gm GLUCOSE Screen");
            }

            request.getSession().setAttribute("labReq07" + demographicNo, p);
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (hb != null && hb.equals("checked")) {
                p.setProperty("h_cbc", "checked=\"checked\"");
            }
            if (urine != null && urine.equals("checked")) {
                p.setProperty("m_urine", "checked=\"checked\"");
            }
            if (antibody != null && antibody.equals("checked")) {
                p.setProperty("i_repeatPrenatalAntibodies", "checked=\"checked\"");
            }
            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "1 Hr 50gm GLUCOSE Screen");
            }

            request.getSession().setAttribute("labReq10" + demographicNo, p);
        }
        return null;
    }

    public String createGTTLabReq() throws SQLException {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        String glucose = request.getParameter("glucose");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, providerNo);

            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "2 Hr 75gm GLUCOSE Screen");
            }
            request.getSession().setAttribute("labReq07" + demographicNo, p);
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, providerNo);

            if (glucose != null && glucose.equals("checked")) {
                p.setProperty("o_otherTests1", "2 Hr 75gm GLUCOSE Screen");
            }
            request.getSession().setAttribute("labReq10" + demographicNo, p);
        }
        return null;
    }

    public String getEformsByGroupAjax() throws IOException {
        String name = request.getParameter("name");
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        EFormGroupDao eformGroupDao = (EFormGroupDao) SpringUtils.getBean(EFormGroupDao.class);
        List<LabelValueBean> results = new ArrayList<LabelValueBean>();

        List<EFormGroup> items = eformGroupDao.getByGroupName(name);
        for (EFormGroup item : items) {
            if (item.getFormId() > 0) {
                EForm eform = eformDao.find(item.getFormId());
                if (eform != null) {
                    LabelValueBean bean = new LabelValueBean(eform.getFormName(), String.valueOf(item.getFormId()));
                    results.add(bean);
                }
            }
        }

        ArrayNode jsonObj = objectMapper.valueToTree(results);
        response.getWriter().print(jsonObj);

        return null;
    }

    public static List<LabelValueBean> getEformsByGroup(String name) {
        EFormDao eformDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        EFormGroupDao eformGroupDao = (EFormGroupDao) SpringUtils.getBean(EFormGroupDao.class);
        List<LabelValueBean> results = new ArrayList<LabelValueBean>();

        List<EFormGroup> items = eformGroupDao.getByGroupName(name);
        for (EFormGroup item : items) {
            if (item.getFormId() > 0) {
                EForm eform = eformDao.find(item.getFormId());
                if (eform != null) {
                    LabelValueBean bean = new LabelValueBean(eform.getFormName(), String.valueOf(item.getFormId()));
                    results.add(bean);
                }
            }
        }

        return results;
    }


    public String getFundalImage() throws IOException {

        List<Point2D.Double> points = new ArrayList<Point2D.Double>();
        int index = 1;
        while (true) {
            String ga = request.getParameter("ga" + index);
            String cm = request.getParameter("fh" + index);
            if (ga == null || cm == null)
                break;

            String weeks = null;
            String offset = null;
            try {
                weeks = ga.substring(0, ga.indexOf("w"));
                offset = "0";
                if (ga.indexOf("+") != -1)
                    offset = ga.substring(ga.indexOf("+") + 1);
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Error", e);
            }

            if (weeks != null && offset != null) {
                try {
                    double gaa = Integer.parseInt(weeks) + (Integer.parseInt(offset) / 7);
                    double cma = Double.parseDouble(cm);
                    Point2D.Double p = new Point2D.Double(gaa, cma);
                    points.add(p);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("Error", e);
                }
            }
            index++;
        }

        File file = new File(request.getSession().getServletContext().getRealPath("/") + "WEB-INF/classes/oscar/form/prop/fundal_graph.png");
        BufferedImage bufferedImage = ImageIO.read(file);
        Graphics2D g = bufferedImage.createGraphics();
        g.setColor(Color.black);
        g.setStroke(new BasicStroke(2));

        int xStart = 23;  //each week is 10.25pixels
        int yStart = 318; //each height is 10.6pixels
        int width = 7;
        int height = 7;
        int startingWeek = 20;
        int startingHeight = 15;

        Point2D.Double lastPoint = null;

        for (Point2D.Double p : points) {
            if (p.x < startingWeek || p.x > 42)
                continue;
            if (p.y < startingHeight || p.y > 45)
                continue;

            Rectangle r = new Rectangle((int) (xStart + (10.25 * (p.x - startingWeek)) - (width / 2)), (int) (yStart - (10.60 * (p.y - startingHeight)) - (height / 2)), width, height);
            g.fill(r);

            if (lastPoint != null) {
                g.drawLine((int) (xStart + (10.25 * (lastPoint.x - startingWeek))), (int) (yStart - (10.60 * (lastPoint.y - startingHeight))),
                        (int) (xStart + (10.25 * (p.x - startingWeek))), (int) (yStart - (10.60 * (p.y - startingHeight))));
            }
            lastPoint = p;
        }

        g.dispose();
        response.setContentType("image/png");
        OutputStream outputStream = response.getOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);
        outputStream.close();

        return null;
    }

    public String recordPrint() {
        String printLocation = request.getParameter("printLocation");
        String printMethod = request.getParameter("printMethod");
        String resourceName = request.getParameter("resourceName");
        String resourceId = request.getParameter("resourceId");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        PrintResourceLog item = new PrintResourceLog();
        item.setDateTime(new Date());
        item.setExternalLocation(printLocation);
        item.setExternalMethod(printMethod);
        item.setProviderNo(providerNo);
        item.setResourceId(resourceId);
        item.setResourceName(resourceName);

        PrintResourceLogDao dao = SpringUtils.getBean(PrintResourceLogDao.class);
        dao.persist(item);

        return null;
    }

    public String getPrintData() throws IOException {
        PrintResourceLogDao dao = SpringUtils.getBean(PrintResourceLogDao.class);
        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        String resourceName = request.getParameter("resourceName");
        String resourceId = request.getParameter("resourceId");
        List<PrintResourceLog> results = dao.findByResource(resourceName, resourceId);

        for (PrintResourceLog l : results) {
            l.setProviderName(providerDao.getProviderName(l.getProviderNo()));
        }
        ArrayNode json = objectMapper.valueToTree(results);
        response.getWriter().print(json.toString());
        return null;
    }
}
