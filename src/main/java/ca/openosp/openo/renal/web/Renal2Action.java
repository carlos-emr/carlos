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
package ca.openosp.openo.renal.web;

import ca.openosp.OscarProperties;
import ca.openosp.openo.utility.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.velocity.VelocityContext;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.DxresearchDAO;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Dxresearch;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.renal.CkdScreener;
import ca.openosp.openo.renal.ORNCkdScreeningReportThread;
import ca.openosp.openo.renal.ORNPreImplementationReportThread;
import ca.openosp.OscarProperties;
import ca.openosp.openo.form.FrmLabReq07Record;
import ca.openosp.openo.form.FrmLabReq10Record;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Renal2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private DxresearchDAO dxResearchDao = SpringUtils.getBean(DxresearchDAO.class);
    private MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    static String labReqVersion;

    static {
        labReqVersion = OscarProperties.getInstance().getProperty("orn_labreqver", "07");
        if (labReqVersion == "") {
            labReqVersion = "10";
        }
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("checkForDx".equals(method)) {
            return checkForDx();
        } else if ("addtoDx".equals(method)) {
            return addtoDx();
        } else if ("getNextSteps".equals(method)) {
            return getNextSteps();
        } else if ("generatePatientLetter".equals(method)) {
            return generatePatientLetter();
        } else if ("createLabReq".equals(method)) {
            return createLabReq();
        } else if ("sendPatientLetterAsEmail".equals(method)) {
            return sendPatientLetterAsEmail();
        } else if ("submitPreimplementationReport".equals(method)) {
            return submitPreimplementationReport();
        } else if ("submitCkdScreeningReport".equals(method)) {
            return submitCkdScreeningReport();
        } 
        return checkForDx();
    }


    public String checkForDx() {
        String demographicNo = request.getParameter("demographicNo");
        String codingSystem = request.getParameter("codingSystem");
        String code = request.getParameter("code");

        boolean exists = dxResearchDao.activeEntryExists(Integer.parseInt(demographicNo), codingSystem, code);

        ObjectNode jsonArray = objectMapper.createObjectNode();
        jsonArray.put("result", exists);
        response.setContentType("text/x-json");
        try {
            response.getWriter().write(jsonArray.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return null;
    }

    public String addtoDx() {
        String demographicNo = request.getParameter("demographicNo");
        String codingSystem = request.getParameter("codingSystem");
        String code = request.getParameter("code");

        List<Dxresearch> items = dxResearchDao.find(Integer.parseInt(demographicNo), codingSystem, code);
        Integer id = null;
        for (Dxresearch item : items) {
            if (item.getStatus() != 'A') {
                item.setStatus('A');
                dxResearchDao.merge(item);
                id = item.getDxresearchNo();
                break;
            }
        }
        if (id == null) {
            Dxresearch item = new Dxresearch();
            item.setAssociation((byte) 0);
            item.setCodingSystem(codingSystem);
            item.setDemographicNo(Integer.parseInt(demographicNo));
            item.setDxresearchCode(code);
            item.setStartDate(new Date());
            item.setStatus('A');
            item.setUpdateDate(new Date());
            item.setProviderNo(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
            dxResearchDao.persist(item);
            id = item.getDxresearchNo();
        }

        ObjectNode jsonArray = objectMapper.createObjectNode();
        jsonArray.put("result", id);
        response.setContentType("text/x-json");
        try {
            response.getWriter().write(jsonArray.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return null;
    }

    public String getNextSteps() {
        String demographicNo = request.getParameter("demographicNo");

        CkdScreener screener = new CkdScreener();
        List<String> reasons = new ArrayList<String>();
        boolean match = screener.screenDemographic(Integer.parseInt(demographicNo), reasons, null);

        String nextSteps = "N/A";
        if (match)
            nextSteps = "Screen patient for CKD<br/>using eGFR, ACR, and BP";

        //get tests
        List<Measurement> egfrs = measurementDao.findByType(Integer.parseInt(demographicNo), "EGFR");
        List<Measurement> acrs = measurementDao.findByType(Integer.parseInt(demographicNo), "ACR");
        Date latestEgfrDate = null;

        String datafield = null;
        Double latestEgfr = null;
        Double aYearAgoEgfr = null;
        if (egfrs.size() > 0) {
            /*
             *  Some older datasets allowed a comparator to be
             *  saved with a numeric value.
             *  This filters out those comparators.
             */
            datafield = egfrs.get(0).getDataField();
            if (datafield != null) {
                if (datafield.contains(">")) {
                    datafield = datafield.replaceAll(">", "");
                }
                if (datafield.contains("<")) {
                    datafield = datafield.replaceAll("<", "");
                }
                latestEgfr = Double.valueOf(datafield);
            }
            latestEgfrDate = egfrs.get(0).getDateObserved();
        }

        Double latestAcr = null;
        if (acrs.size() > 0) {
            /*
             *  Some older datasets allowed a comparator to be
             *  saved with a numeric value.
             *  This filters out those comparators.
             */
            datafield = acrs.get(0).getDataField();
            if (datafield != null) {
                if (datafield.contains(">")) {
                    datafield = datafield.replaceAll(">", "");
                }
                if (datafield.contains("<")) {
                    datafield = datafield.replaceAll("<", "");
                }
                latestAcr = Double.valueOf(datafield);
            }
        }

        if (latestEgfrDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(latestEgfrDate);
            cal.add(Calendar.MONTH, -12);
            Date aYearBefore = cal.getTime();

            //do we have any egfrs from before this date?
            List<Measurement> tmp = measurementDao.findByTypeBefore(Integer.parseInt(demographicNo), "EGFR", aYearBefore);
            if (tmp.size() > 0) {
                Measurement m = tmp.get(0);
                aYearAgoEgfr = Double.valueOf(m.getDataField());
            }
        }

        if ((latestEgfr != null && latestEgfr < 30) || (latestAcr != null && latestAcr >= 60)) {
            nextSteps = "<a href=\"javascript:void();\" onclick=\"window.open('" + request.getContextPath() + "/oscarEncounter/oscarConsultationRequest/ConsultationFormRequest.jsp?de=" + demographicNo + "&teamVar=','Consultation" + demographicNo + "','width=960,height=700');return false;\">Refer to Nephrology</a>";
        }

        if ((latestAcr != null && latestAcr > 2.8 && latestAcr < 60) && (latestEgfr != null && latestEgfr > 30)) {
            nextSteps = "Check ACR/eGFR q6m for<br/> 2 years, and if stable, yearly";
        }

        if ((latestAcr != null && latestAcr <= 2.8) && (latestEgfr != null && latestEgfr >= 30 && latestEgfr <= 59)) {
            nextSteps = "Check ACR/eGFR q6m for<br/> 2 years, and if stable, yearly";
        }

        if ((latestAcr != null && latestAcr <= 2.8) && (latestEgfr != null && latestEgfr > 60)) {
            nextSteps = "Check ACR/eGFR yearly";
        }

        if (latestEgfr != null && aYearAgoEgfr != null) {
            if ((aYearAgoEgfr.doubleValue() - latestEgfr.doubleValue()) > 20) {
                nextSteps = "Check ACR, and if drop pesistent, <a href=\"javascript:void();\" onclick=\"window.open('" + request.getContextPath() + "/oscarEncounter/oscarConsultationRequest/ConsultationFormRequest.jsp?de=" + demographicNo + "&teamVar=','Consultation" + demographicNo + "','width=960,height=700');return false;\">Refer to Nephrology</a>";
            }
        }

        ObjectNode jsonArray = objectMapper.createObjectNode();
        jsonArray.put("result", StringEscapeUtils.escapeEcmaScript(nextSteps));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
             objectMapper.writeValue(response.getWriter(), jsonArray);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return null;
    }

    public String generatePatientLetter() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String demographicNo = request.getParameter("demographic_no");
        response.setContentType("text/html");
        try {
            String documentDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR", "");
            File f = new File(documentDir, "orn_patient_letter.txt");
            String template = IOUtils.toString(new FileInputStream(f));

            Demographic d = demographicDao.getDemographic(demographicNo);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            VelocityContext velocityContext = VelocityUtils.createVelocityContextWithTools();
            velocityContext.put("patient", d);
            velocityContext.put("currentDate", sdf.format(new Date()));
            if (d.getProviderNo() != null && d.getProviderNo().length() > 0) {
                velocityContext.put("mrp", providerDao.getProvider(d.getProviderNo()));
            } else {
                velocityContext.put("mrp", OscarProperties.getInstance().getProperty("orn.default_mrp", ""));
            }


            String letter = VelocityUtils.velocityEvaluate(velocityContext, template);

            PrintWriter out = response.getWriter();
            out.println(letter);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            OscarAuditLogger.getInstance().log(loggedInInfo, "create", "CkdPatientLetter", Integer.valueOf(demographicNo), "");
        }

        return null;
    }

    public String createLabReq() throws SQLException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));

        if (labReqVersion.equals("07")) {
            FrmLabReq07Record lr = new FrmLabReq07Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(loggedInInfo, loggedInInfo.getCurrentFacility(), p, loggedInInfo.getLoggedInProviderNo());
            p.setProperty("b_creatinine", "checked=\"checked\"");
            p.setProperty("b_acRatioUrine", "checked=\"checked\"");
            p.setProperty("b_urinalysis", "checked=\"checked\"");
            request.getSession().setAttribute("labReq07" + demographicNo, p);
        } else {
            FrmLabReq10Record lr = new FrmLabReq10Record();
            Properties p = lr.getFormRecord(loggedInInfo, demographicNo, 0);
            p = lr.getFormCustRecord(p, loggedInInfo.getLoggedInProviderNo());
            p.setProperty("b_creatinine", "checked=\"checked\"");
            p.setProperty("b_acRatioUrine", "checked=\"checked\"");
            p.setProperty("b_urinalysis", "checked=\"checked\"");
            request.getSession().setAttribute("labReq10" + demographicNo, p);
        }

        OscarAuditLogger.getInstance().log(loggedInInfo, "create", "CkdLabReq", demographicNo, "");

        return null;
    }

    /*
     * This method is no longer supported as it directly utilizes JavaMailSender.
     * Currently, a new email feature (EmailManager.java) is in production.
     *
     * TODO: Once the new emailing feature is fully implemented, refactor and update this method to make it compatible with the latest email handling in EmailManager.java.
     */
    @Deprecated
    public String sendPatientLetterAsEmail() {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // LoggedInInfo loggedInInfo=LoggedInInfo.getLoggedInInfoFromSession(request);

        // String demographicNo = request.getParameter("demographic_no");
        // String error = "";
        // boolean success=true;
        // ObjectNode json = objectMapper.createObjectNode();

        // final Demographic d = demographicDao.getDemographic(demographicNo);

        // if(d == null) {
        // 	error = "Patient not found.";
        // 	success=false;
        // }
        // if(d.getEmail() == null || d.getEmail().length() == 0 || d.getEmail().indexOf("@") == -1) {
        // 	error = "No valid email address found for patient.";
        // 	success=false;
        // }

        // if(success) {

        // 	try {
        // 		String documentDir = oscar.OscarProperties.getInstance().getProperty("DOCUMENT_DIR","");
        // 		File f = new File(documentDir,"orn_patient_letter.txt");
        //         String template=IOUtils.toString(new FileInputStream(f));

        //         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        //         VelocityContext velocityContext=VelocityUtils.createVelocityContextWithTools();
        //         velocityContext.put("patient", d);
        //         velocityContext.put("currentDate", sdf.format(new Date()));
        //         Provider mrp = null;
        //         if(d.getProviderNo() != null && d.getProviderNo().length()>0) {
        //         	mrp = providerDao.getProvider(d.getProviderNo());
        //         } else {
        //         	mrp = providerDao.getProvider(OscarProperties.getInstance().getProperty("orn.default_mrp",""));
        //         }
        //         velocityContext.put("mrp", mrp);

        //         final String mrp1 = mrp.getFullName();

        //        final String letter=VelocityUtils.velocityEvaluate(velocityContext, template);

        //        JavaMailSender mailSender = (JavaMailSender) SpringUtils.getBean(MailSender.class);

        //        MimeMessagePreparator preparator = new MimeMessagePreparator() {
        //            public void prepare(MimeMessage mimeMessage) throws Exception {
        //               MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
        //               message.setTo(d.getEmail());
        //               message.setSubject(OscarProperties.getInstance().getProperty("orn.email.subject", "Important Message from " +  mrp1));
        //               message.setFrom(OscarProperties.getInstance().getProperty("orn.email.from","no-reply@oscarmcmaster.org"));
        //               message.setText(letter, true);
        //            }
        //         };
        //         mailSender.send(preparator);

        // 	}catch(IOException e) {
        // 		MiscUtils.getLogger().error("Error",e);
        // 		success=false;
        // 		error=e.getMessage();
        // 	} finally {
        // 		OscarAuditLogger.getInstance().log(loggedInInfo, "create", "CkdPatientLetter", Integer.valueOf(demographicNo), "");
        // 		OscarAuditLogger.getInstance().log(loggedInInfo, "email", "CkdPatientLetter", Integer.valueOf(demographicNo), "");
        // 	}
        // }
        // json.put("success", String.valueOf(success));
        // json.put("error", error);
        // try {
        // 	response.getWriter().print(json.toString());
        // }catch(IOException e) {
        // 	MiscUtils.getLogger().error("error",e);
        // }
        // return null;
    }

    public String submitPreimplementationReport() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        ORNPreImplementationReportThread t = new ORNPreImplementationReportThread();
        t.setProviderNo(providerNo);
        t.start();


        return null;

    }

    public String submitCkdScreeningReport() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        ORNCkdScreeningReportThread t = new ORNCkdScreeningReportThread();
        t.setProviderNo(providerNo);
        t.start();


        return null;

    }
}
