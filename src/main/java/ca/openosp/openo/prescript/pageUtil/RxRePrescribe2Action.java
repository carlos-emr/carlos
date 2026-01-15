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

package ca.openosp.openo.prescript.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.model.Drug;
import ca.openosp.openo.managers.PrescriptionManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.prescript.data.RxPrescriptionData;
import ca.openosp.openo.prescript.data.RxPrescriptionData.Prescription;
import ca.openosp.openo.prescript.util.RxUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RxRePrescribe2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_WRITE = "w";

    private static final Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws IOException {
        String method = request.getParameter("method");
        if ("reprint2".equals(method)) {
            return reprint2();
        } else if ("represcribe".equals(method)) {
            return represcribe();
        } else if ("saveReRxDrugIdToStash".equals(method)) {
            return saveReRxDrugIdToStash();
        } else if ("represcribe2".equals(method)) {
            return represcribe2();
        } else if ("repcbAllLongTerm".equals(method)) {
            return repcbAllLongTerm();
        } else if ("represcribeMultiple".equals(method)) {
            return represcribeMultiple();
        }
        return reprint();
    }

    public String reprint() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        RxSessionBean sessionBeanRX = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (sessionBeanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }

        RxSessionBean beanRX = new RxSessionBean();
        beanRX.setDemographicNo(sessionBeanRX.getDemographicNo());
        beanRX.setProviderNo(sessionBeanRX.getProviderNo());

        String script_no = this.getDrugList();

        String ip = request.getRemoteAddr();

        RxPrescriptionData rxData = new RxPrescriptionData();
        List<Prescription> list = rxData.getPrescriptionsByScriptNo(Integer.parseInt(script_no), sessionBeanRX.getDemographicNo());
        RxPrescriptionData.Prescription p = null;
        StringBuilder auditStr = new StringBuilder();
        for (int idx = 0; idx < list.size(); ++idx) {
            p = list.get(idx);
            beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, p));
            auditStr.append(p.getAuditString() + "\n");
        }

        // save print date/time to prescription table
        if (p != null) {
            p.Print(loggedInInfo);
        }

        String comment = rxData.getScriptComment(script_no);

        request.getSession().setAttribute("tmpBeanRX", beanRX);
        request.setAttribute("rePrint", "true");
        request.setAttribute("comment", comment);

        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.REPRINT, LogConst.CON_PRESCRIPTION, script_no, ip, "" + beanRX.getDemographicNo(), auditStr.toString());

        return "reprint";
    }

    public String reprint2() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        RxSessionBean sessionBeanRX = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (sessionBeanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }

        RxSessionBean beanRX = new RxSessionBean();
        beanRX.setDemographicNo(sessionBeanRX.getDemographicNo());
        beanRX.setProviderNo(sessionBeanRX.getProviderNo());

        String script_no = request.getParameter("scriptNo");
        String ip = request.getRemoteAddr();
        RxPrescriptionData rxData = new RxPrescriptionData();
        List<Prescription> list = rxData.getPrescriptionsByScriptNo(Integer.parseInt(script_no), sessionBeanRX.getDemographicNo());
        RxPrescriptionData.Prescription p = null;
        StringBuilder auditStr = new StringBuilder();
        for (int idx = 0; idx < list.size(); ++idx) {
            p = list.get(idx);
            beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, p));
            auditStr.append(p.getAuditString() + "\n");
        }
        // p("auditStr "+auditStr.toString());
        // save print date/time
        if (p != null) {
            p.Print(loggedInInfo);
        }

        String comment = rxData.getScriptComment(script_no);
        request.getSession().setAttribute("tmpBeanRX", beanRX);
        request.getSession().setAttribute("rePrint", "true");
        request.getSession().setAttribute("comment", comment);
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.REPRINT, LogConst.CON_PRESCRIPTION, script_no, ip, "" + beanRX.getDemographicNo(), auditStr.toString());

        return null;
    }

    public String represcribe() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean beanRX = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (beanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }
        StringBuilder auditStr = new StringBuilder();
        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            //String drugList = frm.getDrugList();

            String[] drugArr = drugList.split(",");

            int drugId;
            int i;

            for (i = 0; i < drugArr.length; i++) {
                try {
                    drugId = Integer.parseInt(drugArr[i]);
                } catch (Exception e) {
                    logger.error("Unexpected error.", e);
                    break;
                }

                // get original drug
                RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);

                // create copy of Prescription
                RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx);

                beanRX.setStashIndex(beanRX.addStashItem(loggedInInfo, rx));
                auditStr.append(rx.getAuditString() + "\n");

                // allocate space for annotation
                beanRX.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(beanRX.getStashIndex()));
                // p("beanRX.getStashIndex() in represcribe after", "" + beanRX.getStashIndex());
                request.setAttribute("BoxNoFillFirstLoad", "true");
            }
        } catch (Exception e) {
            logger.error("Unexpected error occurred.", e);
        }

        return SUCCESS;
    }

/**
 * Saves or updates a digital signature association with a prescription.
 * 
 * This method associates a digital signature with an existing prescription script,
 * allowing prescriptions to be digitally signed by providers. The signature ID
 * can be null to remove an existing signature association.
 * 
 * @return null - indicating no specific view forward (Ajax-style call)
 * @throws IOException if there's an error redirecting to the error page
 * @throws RuntimeException if the user lacks read privileges for prescriptions
 * 
 * Expected request parameters:
 * - digitalSignatureId: Integer ID of the digital signature (optional, can be null)
 * - scriptId: String ID of the prescription script (required)
 */
public String saveDigitalSignature() throws IOException {
    
    // Validate user session and privileges
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    checkPrivilege(loggedInInfo, PRIVILEGE_READ);
    
    // Retrieve and validate the prescription session bean
    RxSessionBean sessionBeanRX =
        (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
    if (sessionBeanRX == null) {
        response.sendRedirect("error.html");
        return null;
    }
    
    // Create a new session bean with current demographic and providers info
    // This ensures we're working with the correct patient/providers context
    RxSessionBean beanRX = new RxSessionBean();
    beanRX.setDemographicNo(sessionBeanRX.getDemographicNo());
    beanRX.setProviderNo(sessionBeanRX.getProviderNo());
    
    // Extract digital signature ID from request (can be null to remove signature)
    Integer digitalSignatureId = Objects.isNull(request.getParameter("digitalSignatureId"))
            ? null : Integer.valueOf(request.getParameter("digitalSignatureId"));
    
    // Extract required script ID parameter
    String scriptId = request.getParameter("scriptId");
    
    // Capture client IP for audit logging
    String ip = request.getRemoteAddr();
    
    // Update the prescription with the digital signature
    PrescriptionManager prescriptionManager = SpringUtils.getBean(PrescriptionManager.class);
    prescriptionManager.setPrescriptionSignature(loggedInInfo, Integer.parseInt(scriptId), digitalSignatureId);
    
    // Log the action for audit trail
    // Note: Using REPRINT constant as this is related to prescription printing/signing workflow
    LogAction.addLog((String) request.getSession().getAttribute("user"), 
                      LogConst.REPRINT, 
                      LogConst.CON_PRESCRIPTION, 
                      scriptId, 
                      ip, 
                      "" + beanRX.getDemographicNo());
    
    // Return null for Ajax-style calls that don't require a view forward
    return null;
}

    public String saveReRxDrugIdToStash() throws IOException {
        MiscUtils.getLogger().debug("================in saveReRxDrugIdToStash  of RxRePrescribe2Action.java=================");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        StringBuilder auditStr = new StringBuilder();

        RxPrescriptionData rxData = new RxPrescriptionData();

        // String strId = (request.getParameter("drugId").split("_"))[1];
        String strId = request.getParameter("drugId");
        try {
            int drugId = Integer.parseInt(strId);
            // get original drug
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), oldRx); // set writtendate, rxdate ,enddate=null.
            Long rand = Math.round(Math.random() * 1000000);
            rx.setRandomId(rand);

            request.setAttribute("BoxNoFillFirstLoad", "true");
            String qText = rx.getQuantity();
            MiscUtils.getLogger().debug("qText in represcribe2=" + qText);
            if (qText != null && RxUtil.isStringToNumber(qText)) {
            } else {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            MiscUtils.getLogger().debug("quantity, unitName represcribe2=" + rx.getQuantity() + "; " + rx.getUnitName());
            // trim Special
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            List<RxPrescriptionData.Prescription> listReRx = new ArrayList<Prescription>();
            rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx));
            // add prescript to prescript list
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listReRx.add(rx);
            }
            // save prescript to stash
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            auditStr.append(rx.getAuditString() + "\n");
            bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getStashIndex()));
            // p("brandName saved in stash", prescript.getBrandName());
            // p("stashIndex becomes", "" + beanRX.getStashIndex());

            // RxUtil.printStashContent(beanRX);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        MiscUtils.getLogger().debug("================end saveReRxDrugIdToStash of RxRePrescribe2Action.java=================");
        return null;
    }

    public String represcribe2() throws IOException {
        MiscUtils.getLogger().debug("================in represcribe2 of RxRePrescribe2Action.java=================");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean beanRX = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (beanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }

        StringBuilder auditStr = new StringBuilder();
        RxPrescriptionData rxData = new RxPrescriptionData();

        String strId = request.getParameter("drugId");
        try {
            int drugId = Integer.parseInt(strId);
            // get original drug
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx); // set writtendate, rxdate ,enddate=null.

            Long rand;
            try {
              	 rand = Long.parseLong(request.getParameter("rand"));
	    }  catch (NumberFormatException e) {
		rand = Math.round(Math.random() * 10001);
            }
            rx.setRandomId(rand);

            request.setAttribute("BoxNoFillFirstLoad", "true");
            String qText = rx.getQuantity();
            MiscUtils.getLogger().debug("qText in represcribe2=" + qText);
            if (qText != null && RxUtil.isStringToNumber(qText)) {
            } else {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            MiscUtils.getLogger().debug("quantity, unitName represcribe2=" + rx.getQuantity() + "; " + rx.getUnitName());
            // trim Special
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            List<RxPrescriptionData.Prescription> listReRx = new ArrayList<Prescription>();
            rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx));
            // add prescript to prescript list
            if (RxUtil.isRxUniqueInStash(beanRX, rx)) {
                listReRx.add(rx);
            }
            // save prescript to stash
            int rxStashIndex = beanRX.addStashItem(loggedInInfo, rx);
            beanRX.setStashIndex(rxStashIndex);

            auditStr.append(rx.getAuditString() + "\n");
            beanRX.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(beanRX.getStashIndex()));
            // p("brandName saved in stash", prescript.getBrandName());
            // p("stashIndex becomes", "" + beanRX.getStashIndex());

            // RxUtil.printStashContent(beanRX);
            request.setAttribute("listRxDrugs", listReRx);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return "represcribe";
    }

    public String repcbAllLongTerm() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);

        RxSessionBean beanRX = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (beanRX == null) {
            response.sendRedirect("error.html");
            return null;
        }
        StringBuilder auditStr = new StringBuilder();
        // String idList = request.getParameter("drugIdList");

        Integer demoNo = Integer.parseInt(request.getParameter("demoNo"));
        String strShow = request.getParameter("showall");

        boolean showall = false;
        if (strShow.equalsIgnoreCase("true")) {
            showall = true;
        }
        // get a list of long term meds
        List<Drug> prescriptDrugs;
        if (showall) {
            prescriptDrugs = caseManagementManager.getPrescriptions(loggedInInfo, demoNo, true);
        } else {
            prescriptDrugs = caseManagementManager.getCurrentPrescriptions(demoNo);
        }
        List<Integer> listLongTermMed = new ArrayList<Integer>();
        for (Drug prescriptDrug : prescriptDrugs) {
            // add all long term med drugIds to an array.
            if (prescriptDrug.isLongTerm()) {
                listLongTermMed.add(prescriptDrug.getId());
            }
        }


        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");

        List<String> reRxDrugIdList = bean.getReRxDrugIdList();

        List<RxPrescriptionData.Prescription> listLongTerm = new ArrayList<Prescription>();
        for (int i = 0; i < listLongTermMed.size(); i++) {
            Long rand = Math.round(Math.random() * 1000000);

            // loop this
            int drugId = listLongTermMed.get(i);

            //add drug to re-prescribe drug list
            reRxDrugIdList.add(Integer.toString(drugId));

            // get original drug
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);

            // create copy of Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(beanRX.getProviderNo(), beanRX.getDemographicNo(), oldRx);

            request.setAttribute("BoxNoFillFirstLoad", "true");

            // give prescript a random id.
            rx.setRandomId(rand);
            String qText = rx.getQuantity();
            MiscUtils.getLogger().debug("qText in represcribe2=" + qText);
            if (qText != null && RxUtil.isStringToNumber(qText)) {
            } else {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            MiscUtils.getLogger().debug("quantity, unitName represcribe2=" + rx.getQuantity() + "; " + rx.getUnitName());
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);

            if (RxUtil.isRxUniqueInStash(beanRX, rx)) {
                listLongTerm.add(rx);
            }
            int rxStashIndex = beanRX.addStashItem(loggedInInfo, rx);
            beanRX.setStashIndex(rxStashIndex);
            auditStr.append(rx.getAuditString() + "\n");

            // allocate space for annotation
            beanRX.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(beanRX.getStashIndex()));
        }
        // RxUtil.printStashContent(beanRX);
        request.setAttribute("listRxDrugs", listLongTerm);

        return "repcbLongTerm";
    }

    public String represcribeMultiple() throws IOException {
        MiscUtils.getLogger().debug("================in represcribeMultiple of RxRePrescribe2Action.java=================");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        CopyOnWriteArrayList<String> reRxDrugList = bean.getReRxDrugIdList();
        MiscUtils.getLogger().debug(reRxDrugList);
        CopyOnWriteArrayList<RxPrescriptionData.Prescription> listReRxDrug = new CopyOnWriteArrayList<Prescription>();
        for (String drugId : reRxDrugList) {
            Long rand = Math.round(Math.random() * 1000000);
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(Integer.parseInt(drugId));
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo(), oldRx);
            rx.setRandomId(rand);
            String qText = rx.getQuantity();
            MiscUtils.getLogger().debug("qText in represcribe2=" + qText);
            if (qText != null && RxUtil.isStringToNumber(qText)) {
            } else {
                rx.setQuantity(RxUtil.getQuantityFromQuantityText(qText));
                rx.setUnitName(RxUtil.getUnitNameFromQuantityText(qText));
            }
            MiscUtils.getLogger().debug("quantity, unitName represcribe2=" + rx.getQuantity() + "; " + rx.getUnitName());
            String spec = RxUtil.trimSpecial(rx);
            rx.setSpecial(spec);
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listReRxDrug.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);
            bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getStashIndex()));
        }
        MiscUtils.getLogger().debug(listReRxDrug);
        request.setAttribute("listRxDrugs", listReRxDrug);
        MiscUtils.getLogger().debug("================END represcribeMultiple of RxRePrescribe2Action.java=================");
        return "represcribe";
    }

    public void p(String s) {
        MiscUtils.getLogger().debug(s);
    }

    public void p(String s, String s1) {
        MiscUtils.getLogger().debug(s + "=" + s1);
    }


    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
    }

    private String drugList = null;

    public String getDrugList() {
        return this.drugList;
    }

    public void setDrugList(String RHS) {
        this.drugList = RHS;
    }
}
