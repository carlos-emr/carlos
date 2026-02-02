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

import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.DrugDao;
import ca.openosp.openo.commn.dao.DrugReasonDao;
import ca.openosp.openo.commn.dao.PartialDateDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Drug;
import ca.openosp.openo.commn.model.DrugReason;
import ca.openosp.openo.commn.model.PartialDate;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.managers.CodingSystemManager;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.RxManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.prescript.data.RxDrugData;
import ca.openosp.openo.prescript.data.RxDrugData.DrugMonograph.DrugComponent;
import ca.openosp.openo.prescript.data.RxPrescriptionData;
import ca.openosp.openo.prescript.util.RxUtil;
import ca.openosp.openo.util.StringUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

public final class RxWriteScript2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_WRITE = "w";

    private static final Logger logger = MiscUtils.getLogger();
    private static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    private static final String DEFAULT_QUANTITY = "30";
    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    private final DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class) ;
    private final RxManager rxManager = SpringUtils.getBean(RxManager.class);

    String removeExtraChars(String s) {
        return s.replace("" + ((char) 130), "").replace("" + ((char) 194), "").replace("" + ((char) 195), "").replace("" + ((char) 172), "");
    }


    public String execute() throws IOException, ServletException, Exception {
        String method = request.getParameter("parameterValue");

        String dispatchResult = switch (method != null ? method : "") {
            case "updateReRxDrug" -> updateReRxDrug();
            case "saveCustomName" -> saveCustomName();
            case "newCustomNote" -> newCustomNote();
            case "listPreviousInstructions" -> listPreviousInstructions();
            case "newCustomDrug" -> newCustomDrug();
            case "normalDrugSetCustom" -> normalDrugSetCustom();
            case "createNewRx" -> createNewRx();
            case "updateDrug" -> updateDrug();
            case "iterateStash" -> iterateStash();
            case "updateSpecialInstruction" -> updateSpecialInstruction();
            case "updateProperty" -> updateProperty();
            case "updateSaveAllDrugs" -> updateSaveAllDrugs();
            case "getDemoNameAndHIN" -> getDemoNameAndHIN();
            case "updateLongTermStatus" -> updateLongTermStatus();
            case "checkNoStashItem" -> checkNoStashItem();
            case "searchSpecialInstructions" -> {
                searchSpecialInstructions();
                yield null;
            }
            default -> null;
        };

        if (dispatchResult != null || (method != null && !method.isEmpty())) {
            return dispatchResult;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        //RxWriteScriptForm frm = (RxWriteScriptForm) form;
        String fwd = "refresh";
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");

        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        if (this.getAction().startsWith("update")) {

            RxDrugData drugData = new RxDrugData();
            RxPrescriptionData.Prescription rx = bean.getStashItem(bean.getStashIndex());
            RxPrescriptionData prescription = new RxPrescriptionData();

			if (! this.getGCN_SEQNO().equals("0")) { // not custom
				if (this.getBrandName().equals(rx.getBrandName()) == false) {
					rx.setBrandName(this.getBrandName());
                } else {
					rx.setGCN_SEQNO(this.getGCN_SEQNO());
                }
            } else { // custom
                rx.setBrandName(null);
				rx.setGCN_SEQNO("0");
				rx.setCustomName(this.getCustomName());
            }

            rx.setRxDate(RxUtil.StringToDate(this.getRxDate(), "yyyy-MM-dd"));
            rx.setWrittenDate(RxUtil.StringToDate(this.getWrittenDate(), "yyyy-MM-dd"));
            rx.setTakeMin(this.getTakeMinFloat());
            rx.setTakeMax(this.getTakeMaxFloat());
            rx.setFrequencyCode(this.getFrequencyCode());
            rx.setDuration(this.getDuration());
            rx.setDurationUnit(this.getDurationUnit());
            rx.setQuantity(this.getQuantity());
            rx.setRepeat(this.getRepeat());
            rx.setLastRefillDate(RxUtil.StringToDate(this.getLastRefillDate(), "yyyy-MM-dd"));
            rx.setNosubs(this.getNosubs());
            rx.setPrn(this.getPrn());
            rx.setSpecial(removeExtraChars(this.getSpecial()));
            rx.setAtcCode(this.getAtcCode());
            rx.setRegionalIdentifier(this.getRegionalIdentifier());
            rx.setUnit(removeExtraChars(this.getUnit()));
            rx.setUnitName(this.getUnitName());
            rx.setMethod(this.getMethod());
            rx.setRoute(this.getRoute());
            rx.setCustomInstr(this.getCustomInstr());
            rx.setDosage(removeExtraChars(this.getDosage()));
            rx.setOutsideProviderName(this.getOutsideProviderName());
            rx.setOutsideProviderOhip(this.getOutsideProviderOhip());
            rx.setLongTerm(this.getLongTerm());
            rx.setShortTerm(this.getShortTerm());
            rx.setPastMed(this.getPastMed());
            rx.setPatientCompliance(this.getPatientCompliance());

            try {
                rx.setDrugForm(drugData.getDrugForm(String.valueOf(this.getGCN_SEQNO())));
            } catch (Exception e) {
                logger.error("Unable to get DrugForm from drugref");
            }

            logger.debug("SAVING STASH " + rx.getCustomInstr());
            if (rx.getSpecial() == null) {
                logger.error("Drug.special is null : " + rx.getSpecial() + " : " + this.getSpecial());
            } else if (rx.getSpecial().length() < 6) {
                logger.warn("Drug.special appears to be empty : " + rx.getSpecial() + " : " + this.getSpecial());
            }

            String annotation_attrib = request.getParameter("annotation_attrib");
            if (annotation_attrib == null) {
                annotation_attrib = "";
            }

            bean.addAttributeName(annotation_attrib, bean.getStashIndex());
            bean.setStashItem(bean.getStashIndex(), rx);
            rx = null;

            if (this.getAction().equals("update")) {
                fwd = "refresh";
            }
            if (this.getAction().equals("updateAddAnother")) {
                fwd = "addAnother";
            }
            if (this.getAction().equals("updateAndPrint")) {
                // SAVE THE DRUG
                int i;
                String scriptId = prescription.saveScript(loggedInInfo, bean);
                @SuppressWarnings("unchecked")
                ArrayList<String> attrib_names = bean.getAttributeNames();
                // p("attrib_names", attrib_names.toString());
                StringBuilder auditStr = new StringBuilder();
                for (i = 0; i < bean.getStashSize(); i++) {
                    rx = bean.getStashItem(i);

                    rx.Save(scriptId);
                    auditStr.append(rx.getAuditString());
                    auditStr.append("\n");

                    /* Save annotation */
                    HttpSession se = request.getSession();
                    WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
                    CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
                    String attrib_name = attrib_names.get(i);
                    if (attrib_name != null) {
                        CaseManagementNote cmn = (CaseManagementNote) se.getAttribute(attrib_name);
                        if (cmn != null) {
                            cmm.saveNoteSimple(cmn);
                            CaseManagementNoteLink cml = new CaseManagementNoteLink();
                            cml.setTableName(CaseManagementNoteLink.DRUGS);
                            cml.setTableId((long) rx.getDrugId());
                            cml.setNoteId(cmn.getId());
                            cmm.saveNoteLink(cml);
                            se.removeAttribute(attrib_name);
                            LogAction.addLog(cmn.getProviderNo(), LogConst.ANNOTATE, CaseManagementNoteLink.DISP_PRESCRIP, scriptId, request.getRemoteAddr(), cmn.getDemographic_no(), cmn.getNote());
                        }
                    }
                    rx = null;
                }
                fwd = "viewScript";
                String ip = request.getRemoteAddr();
                request.setAttribute("scriptId", scriptId);
                LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_PRESCRIPTION, scriptId, ip, "" + bean.getDemographicNo(), auditStr.toString());
            }
        }
        return fwd;
    }

    public String updateReRxDrug() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        List<String> reRxDrugIdList = bean.getReRxDrugIdList();
        String action = request.getParameter("action");
        String drugId = request.getParameter("reRxDrugId");
        if (action.equals("addToReRxDrugIdList") && !reRxDrugIdList.contains(drugId)) {
            reRxDrugIdList.add(drugId);
        } else if (action.equals("removeFromReRxDrugIdList") && reRxDrugIdList.contains(drugId)) {
            reRxDrugIdList.remove(drugId);
            try {
				for (Iterator<RxPrescriptionData.Prescription> iterator = bean.getStashList().iterator(); iterator.hasNext(); ) {
					RxPrescriptionData.Prescription prescription = iterator.next();
					if (prescription.getDrugReferenceId() == Integer.parseInt(drugId)) {
						iterator.remove();
						break;
					}
				}
			} catch (NumberFormatException e) {
                logger.error("Error: {}", e.getMessage());
			}
        } else if (action.equals("clearReRxDrugIdList")) {
            bean.clearReRxDrugIdList();
        } else {
            logger.warn("WARNING: reRxDrugId not updated");
        }

        return null;

    }

    public String saveCustomName() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        try {
            String randomId = request.getParameter("randomId");
            String customName = request.getParameter("customName");
            RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
            if (rx == null) {
                logger.error("prescript is null", new NullPointerException());
                return null;
            }
            rx.setCustomName(customName);
            rx.setBrandName(null);
            rx.setGenericName(null);
            bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

        } catch (Exception e) {
            logger.error("Error", e);
        }

        return null;
    }

    private void setDefaultQuantity(final HttpServletRequest request) {
        try {
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            String provider = (String) request.getSession().getAttribute("user");
            if (provider != null) {
                userPropertyDAO = (UserPropertyDAO) ctx.getBean(UserPropertyDAO.class);
                UserProperty prop = userPropertyDAO.getProp(provider, UserProperty.RX_DEFAULT_QUANTITY);
                if (prop != null) RxUtil.setDefaultQuantity(prop.getValue());
                else RxUtil.setDefaultQuantity(DEFAULT_QUANTITY);
            } else {
                logger.error("Provider is null", new NullPointerException());
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }
    }

    private RxPrescriptionData.Prescription setCustomRxDurationQuantity(RxPrescriptionData.Prescription rx) {
        String quantity = rx.getQuantity();
        if (RxUtil.isMitte(quantity)) {
            String duration = RxUtil.getDurationFromQuantityText(quantity);
            String durationUnit = RxUtil.getDurationUnitFromQuantityText(quantity);
            rx.setDuration(duration);
            rx.setDurationUnit(durationUnit);
            rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
            rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity));// this is actually an indicator for Mitte prescript
        } else rx.setDuration(RxUtil.findDuration(rx));

        return rx;
    }

    public String newCustomNote() throws IOException {
        logger.debug("=============Start newCustomNote RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());
            String ra = request.getParameter("randomId");
            rx.setRandomId(Integer.parseInt(ra));
            rx.setCustomNote(true);
            rx.setGenericName(null);
            rx.setBrandName(null);
            rx.setDrugForm("");
            rx.setRoute("");
            rx.setDosage("");
            rx.setUnit("");
			rx.setGCN_SEQNO("0");
            rx.setRegionalIdentifier("");
            rx.setAtcCode("");
            RxUtil.setDefaultSpecialQuantityRepeat(rx);
            rx = setCustomRxDurationQuantity(rx);
            bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getStashIndex()));
            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();

            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
                // p("today's date", today);
            } catch (Exception e) {
                logger.error("Error", e);
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);

            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error", e);
        }
        logger.debug("=============END newCustomNote RxWriteScript2Action.java===============");
        return "newRx";
    }

    public String listPreviousInstructions() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_READ);

        logger.debug("=============Start listPreviousInstructions RxWriteScript2Action.java===============");
        String randomId = request.getParameter("randomId");
        randomId = randomId.trim();
        // get prescript from randomId.
        // if prescript is normal drug, if din is not null, use din to find it
        // if din is null, use BN to find it
        // if prescript is custom drug, use customName to find it.
        // append results to a list.
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        // create Prescription
        RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
        List<HashMap<String, String>> retList = new ArrayList();
        retList = RxUtil.getPreviousInstructions(rx);

        bean.setListMedHistory(retList);
        return null;
    }

    public String newCustomDrug() throws IOException {
        logger.debug("=============Start newCustomDrug RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        // set default quantity;
        setDefaultQuantity(request);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        String customDrugName = request.getParameter("name");

        try {
            RxPrescriptionData rxData = new RxPrescriptionData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());
            String ra = request.getParameter("randomId");

            if (customDrugName != null && !customDrugName.isEmpty()) {
                rx.setCustomName(customDrugName);
            }
            rx.setRandomId(Integer.parseInt(ra));
            rx.setGenericName(null);
            rx.setBrandName(null);
            rx.setDrugForm("");
            rx.setRoute("");
            rx.setDosage("");
            rx.setUnit("");
			rx.setGCN_SEQNO("0");
            rx.setRegionalIdentifier("");
            rx.setAtcCode("");
            RxUtil.setDefaultSpecialQuantityRepeat(rx);// 1 OD, 20, 0;
            rx = setCustomRxDurationQuantity(rx);
            bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getStashIndex()));
            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();

            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            }
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);

            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
                // p("today's date", today);
            } catch (Exception e) {
                logger.error("Error", e);
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);

            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error", e);
        }
        return "newRx";
    }

    public String normalDrugSetCustom() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String randomId = request.getParameter("randomId");
        String customDrugName = request.getParameter("customDrugName");
        logger.debug("radomId=" + randomId);
        if (randomId != null && customDrugName != null) {
            RxPrescriptionData.Prescription normalRx = bean.getStashItem2(Integer.parseInt(randomId));
            if (normalRx != null) {// set other fields same as normal drug, set some fields null like custom drug, remove normal drugfrom stash,add customdrug to stash,
                // forward to prescribe.jsp
                RxPrescriptionData rxData = new RxPrescriptionData();
                RxPrescriptionData.Prescription customRx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());
                customRx = normalRx;
                customRx.setCustomName(customDrugName);
                customRx.setRandomId(Long.parseLong(randomId));
                customRx.setGenericName(null);
                customRx.setBrandName(null);
                customRx.setDrugForm("");
                customRx.setRoute("");
                customRx.setDosage("");
                customRx.setUnit("");
				customRx.setGCN_SEQNO("0");
                customRx.setRegionalIdentifier("");
                customRx.setAtcCode("");
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), customRx);
                List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
                if (RxUtil.isRxUniqueInStash(bean, customRx)) {
                    // p("unique");
                    listRxDrugs.add(customRx);
                }
                request.setAttribute("listRxDrugs", listRxDrugs);
                return "newRx";
            } else {

                return null;
            }
        } else {

            return null;
        }
    }

    public String createNewRx() throws IOException {
        logger.debug("=============Start createNewRx RxWriteScript2Action.java===============");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        response.setContentType("application/json");
        String success = "newRx";
        // set default quantity
        setDefaultQuantity(request);
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        try {
            RxPrescriptionData rxData = new RxPrescriptionData();
            RxDrugData drugData = new RxDrugData();

            // create Prescription
            RxPrescriptionData.Prescription rx = rxData.newPrescription(bean.getProviderNo(), bean.getDemographicNo());

            String ra = request.getParameter("randomId");
			int randomId = 0;
			if(ra != null && !ra.isEmpty()) {
				randomId = Integer.parseInt(ra);
			}

            rx.setRandomId(randomId);
            String drugId = request.getParameter("drugId");
            String text = request.getParameter("text");

			if(text != null) {
				text = Encode.forJava(text);
			}

			if(drugId != null) {
				drugId = Encode.forJava(drugId);
			}

            logger.debug("requesting drug from drugref id=" + drugId);
            RxDrugData.DrugMonograph dmono = drugData.getDrug2(drugId);

			// this is the drug name the user selected from the autocomplete interface
			rx.setDrugPrescribed(text);

            ArrayList<DrugComponent> drugComponents = dmono.getDrugComponentList();
            if (drugComponents != null && drugComponents.size() > 0) {

                StringBuilder stringBuilder = new StringBuilder();
                int count = 0;
                for (RxDrugData.DrugMonograph.DrugComponent drugComponent : drugComponents) {

					String strength = drugComponent.getStrength();
					String unit = drugComponent.getUnit();
					String drugName = drugComponent.getName();
					String drugForm = dmono.drugForm;

					if(strength == null) {
						strength = "";
					}

					if(unit == null) {
						unit = "";
					}

					if (drugName == null) {
						drugName = "";
					}

					if(drugForm == null) {
						drugForm = "";
					}

					if(! strength.contains("/")) {
						strength = strength.toLowerCase().replaceAll(unit.toLowerCase(), "");
						strength += unit.toLowerCase();
					}

					stringBuilder.append(drugName.trim());

					if( ! stringBuilder.toString().toLowerCase().contains(strength.toLowerCase())) {
						stringBuilder.append( " " + strength.toLowerCase() );
					}

					if(! stringBuilder.toString().toLowerCase().endsWith(drugForm.toLowerCase())) {
						stringBuilder.append(" " + drugForm.toLowerCase());
					}

                    count++;
                    if (count > 0 && count != drugComponents.size()) {
                        stringBuilder.append(" / ");
                    }
                }

				rx.setGenericName(stringBuilder.toString().trim());
            } else {
                rx.setGenericName(dmono.name);
            }

			if(dmono.getProduct() != null && ! dmono.getProduct().isEmpty()) {
				rx.setBrandName(dmono.getProduct());
			} else {
				rx.setBrandName(text);
			}

            //there's a change there's multiple forms. Select the first one by default
            //save the list in a separate variable to make a drop down in the interface.
            if (dmono != null && dmono.drugForm != null && dmono.drugForm.indexOf(",") != -1) {
                String[] forms = dmono.drugForm.split(",");
                rx.setDrugForm(forms[0]);
            } else if (dmono.drugForm != null) {
                rx.setDrugForm(dmono.drugForm);
            } else if (dmono.drugForm == null) {
                rx.setDrugForm("");
            }
            rx.setDrugFormList(dmono.drugForm);

            // TO DO: cache the most used route from the drugs table.
            // for now, check to see if ORAL present, if yes use that, if not use the first one.
            boolean oral = false;
            for (int i = 0; i < dmono.route.size(); i++) {
                if (((String) dmono.route.get(i)).equalsIgnoreCase("ORAL")) {
                    oral = true;
                }
            }
            if (oral) {
                rx.setRoute("ORAL");
            } else {
                if (dmono.route.size() > 0) {
                    rx.setRoute((String) dmono.route.get(0));
                }
            }
            // if user specified route in instructions, it'll be changed to the one specified.
            String dosage = "";
            String unit = "";
            Vector comps = dmono.components;
            for (int i = 0; i < comps.size(); i++) {
                RxDrugData.DrugMonograph.DrugComponent drugComp = (RxDrugData.DrugMonograph.DrugComponent) comps.get(i);
                String strength = drugComp.strength;
                unit = drugComp.unit;

				if(strength == null) {
					strength = "";
				}

				if(unit == null) {
					unit = "";
				}

				// covers all cases when unit is missing -or- included with the strength.
				if(! strength.contains("/")) {
					strength = strength.toLowerCase().trim().replaceAll(unit.toLowerCase().trim(), "");
					strength += unit.toLowerCase().trim();
				}

				dosage += (" " + strength.toLowerCase());
            }
            rx.setDosage(removeExtraChars(dosage));
            rx.setUnit(removeExtraChars(unit));
            rx.setGCN_SEQNO(drugId+"");
            rx.setRegionalIdentifier(dmono.regionalIdentifier);
            String atcCode = dmono.atc;
            rx.setAtcCode(atcCode);
            RxUtil.setSpecialQuantityRepeat(rx);
            rx = setCustomRxDurationQuantity(rx);
            List<RxPrescriptionData.Prescription> listRxDrugs = new ArrayList();
            if (RxUtil.isRxUniqueInStash(bean, rx)) {
                listRxDrugs.add(rx);
            }
			bean.addAttributeName(rx.getAtcCode() + "-" + bean.getStashIndex());
            int rxStashIndex = bean.addStashItem(loggedInInfo, rx);
            bean.setStashIndex(rxStashIndex);
            String today = null;
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                today = dateFormat.format(calendar.getTime());
            } catch (Exception e) {
                logger.error("Error", e);
            }
            Date tod = RxUtil.StringToDate(today, "yyyy-MM-dd");
            rx.setRxDate(tod);
            rx.setWrittenDate(tod);
			rx.setDiscontinuedLatest(RxUtil.checkDiscontinuedBefore(rx));// check and set if rx was discontinued before.
            request.setAttribute("listRxDrugs", listRxDrugs);
        } catch (Exception e) {
            logger.error("Error", e);
        }
        logger.debug("=============END createNewRx RxWriteScript2Action.java===============");

        return success;
    }

    @SuppressWarnings("unused")
    public String updateDrug() throws IOException {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        String action = request.getParameter("action");
        response.setContentType("application/json");

        if ("parseInstructions".equals(action)) {

            try {
                String randomId = request.getParameter("randomId");
                RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
                if (rx == null) {
                    logger.error("prescript is null", new NullPointerException());
                }

                String instructions = request.getParameter("instruction");
                logger.debug("instruction:" + instructions);
                rx.setSpecial(instructions);
                RxUtil.instrucParser(rx);
                bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getIndexFromRx(Integer.parseInt(randomId))));
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

                HashMap<String, Object> hm = new HashMap<String, Object>();

                if (rx.getRoute() == null || rx.getRoute().equalsIgnoreCase("null")) {
                    rx.setRoute("");
                }

                hm.put("method", rx.getMethod());
                hm.put("takeMin", rx.getTakeMin());
                hm.put("takeMax", rx.getTakeMax());
                hm.put("duration", rx.getDuration());
                hm.put("frequency", rx.getFrequencyCode());
                hm.put("route", rx.getRoute());
                hm.put("durationUnit", rx.getDurationUnit());
                hm.put("prn", rx.getPrn());
                hm.put("calQuantity", rx.getQuantity());
                hm.put("unitName", rx.getUnitName());
                hm.put("policyViolations", rx.getPolicyViolations());
                ObjectNode jsonObject = objectMapper.valueToTree(hm);
                logger.debug("jsonObject:" + jsonObject.toString());
                response.getOutputStream().write(jsonObject.toString().getBytes());
            } catch (Exception e) {
                logger.error("Error", e);
            }

        } else if ("updateQty".equals(action)) {

            try {
                String quantity = request.getParameter("quantity");
                String randomId = request.getParameter("randomId");
                RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
                // get prescript from randomId
                if (quantity == null || quantity.equalsIgnoreCase("null")) {
                    quantity = "";
                }
                // check if quantity is same as prescript.getquantity(), if yes, do nothing.
                if (quantity.equals(rx.getQuantity()) && rx.getUnitName() == null) {
                    // do nothing
                } else {

                    if (RxUtil.isStringToNumber(quantity)) {
                        rx.setQuantity(quantity);
                        rx.setUnitName(null);
                    } else if (RxUtil.isMitte(quantity)) {// set duration for mitte

                        String duration = RxUtil.getDurationFromQuantityText(quantity);
                        String durationUnit = RxUtil.getDurationUnitFromQuantityText(quantity);
                        rx.setDuration(duration);
                        rx.setDurationUnit(durationUnit);
                        rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
                        rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity));// this is actually an indicator for Mitte prescript
                    } else {
                        rx.setQuantity(RxUtil.getQuantityFromQuantityText(quantity));
                        rx.setUnitName(RxUtil.getUnitNameFromQuantityText(quantity));
                    }

                    String frequency = rx.getFrequencyCode();
                    String takeMin = rx.getTakeMinString();
                    String takeMax = rx.getTakeMaxString();
                    String durationUnit = rx.getDurationUnit();
                    double nPerDay = 0d;
                    double nDays = 0d;
                    if (rx.getUnitName() != null || takeMin.equals("0") || takeMax.equals("0") || frequency.equals("")) {
                    } else {
                        if (durationUnit == null || durationUnit.isEmpty()) {
                            durationUnit = "D";
                        }

                        nPerDay = RxUtil.findNPerDay(frequency);
                        nDays = RxUtil.findNDays(durationUnit);
                        if (RxUtil.isStringToNumber(quantity) && !rx.isDurationSpecifiedByUser()) {// don't not caculate duration if it's already specified by the user
                            double qtyD = Double.parseDouble(quantity);
                            // quantity=takeMax * nDays * duration * nPerDay
                            double durD = qtyD / ((Double.parseDouble(takeMax)) * nPerDay * nDays);
                            int durI = (int) durD;
                            rx.setDuration(Integer.toString(durI));
                        } else {
                            // don't calculate duration if quantity can't be parsed to string
                        }
                        rx.setDurationUnit(durationUnit);
                    }
                    // duration=quantity divide by no. of pills per duration period.
                    // if not, recalculate duration based on frequency if frequency is not empty
                    // if there is already a duration uni present, use that duration unit. if not, set duration unit to days, and output duration in days
                }
                bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(bean.getIndexFromRx(Integer.parseInt(randomId))));
                bean.setStashItem(bean.getIndexFromRx(Integer.parseInt(randomId)), rx);

                if (rx.getRoute() == null) {
                    rx.setRoute("");
                }
                HashMap<String, Object> hm = new HashMap<String, Object>();
                hm.put("method", rx.getMethod());
                hm.put("takeMin", rx.getTakeMin());
                hm.put("takeMax", rx.getTakeMax());
                hm.put("duration", rx.getDuration());
                hm.put("frequency", rx.getFrequencyCode());
                hm.put("route", rx.getRoute());
                hm.put("durationUnit", rx.getDurationUnit());
                hm.put("prn", rx.getPrn());
                hm.put("calQuantity", rx.getQuantity());
                hm.put("unitName", rx.getUnitName());
                ObjectNode jsonObject = objectMapper.valueToTree(hm);

                response.getOutputStream().write(jsonObject.toString().getBytes());
            } catch (Exception e) {
                logger.error("Error", e);
            }
        }

        return null;

    }

    public String iterateStash() {
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        List<RxPrescriptionData.Prescription> listP = Arrays.asList(bean.getStash());
        if (listP.size() == 0) {
            return null;
        } else {
            request.setAttribute("listRxDrugs", listP);
            return "newRx";
        }

    }

    public String updateSpecialInstruction() throws Exception {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        // get special instruction from parameter
        // get prescript from random Id
        // prescript.setspecialisntruction
        String randomId = request.getParameter("randomId");
        String specialInstruction = request.getParameter("specialInstruction");
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(randomId));
        if (specialInstruction.trim().length() > 0 && !specialInstruction.trim().equalsIgnoreCase("Enter Special Instruction")) {
            rx.setSpecialInstruction(specialInstruction.trim());
        } else {
            rx.setSpecialInstruction(null);
        }

        return null;
    }

    public String updateProperty() throws Exception {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        String elem = request.getParameter("elementId");
        String val = request.getParameter("propertyValue");
        val = val.trim();
        if (elem != null && val != null) {
            String[] strArr = elem.split("_");
            if (strArr.length > 1) {
                String num = strArr[1];
                num = num.trim();
                RxPrescriptionData.Prescription rx = bean.getStashItem2(Integer.parseInt(num));
                if (elem.equals("method_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setMethod(val);
                } else if (elem.equals("route_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setRoute(val);
                } else if (elem.equals("frequency_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setFrequencyCode(val);
                } else if (elem.equals("minimum_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setTakeMin(Float.parseFloat(val));
                } else if (elem.equals("maximum_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setTakeMax(Float.parseFloat(val));
                } else if (elem.equals("duration_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setDuration(val);
                } else if (elem.equals("durationUnit_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setDurationUnit(val);
                } else if (elem.equals("repeats_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) rx.setRepeat(Integer.parseInt(val));
                } else if (elem.equals("prnVal_" + num)) {
                    if (!val.equals("") && !val.equalsIgnoreCase("null")) {
                        if (val.equalsIgnoreCase("true")) rx.setPrn(true);
                        else rx.setPrn(false);
                    } else rx.setPrn(false);
                }
            }
        }
        return null;
    }

    public String updateSaveAllDrugs() throws IOException, ServletException, Exception {
        checkPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        request.getSession().setAttribute("rePrint", null);// set to print.
        List<String> paramList = new ArrayList<String>();
        Enumeration em = request.getParameterNames();
        List<String> randNum = new ArrayList<String>();
        while (em.hasMoreElements()) {
            String ele = em.nextElement().toString();
            paramList.add(ele);
            if (ele.startsWith("drugName_")) {
                String rNum = ele.substring(9);
                if (!randNum.contains(rNum)) {
                    randNum.add(rNum);
                }
            }
        }

        List<Integer> allIndex = new ArrayList();
        for (int i = 0; i < bean.getStashSize(); i++) {
            allIndex.add(i);
        }

        List<Integer> existingIndex = new ArrayList();
        for (String num : randNum) {
            int stashIndex = bean.getIndexFromRx(Integer.parseInt(num));
            try {
                if (stashIndex == -1) {
                    continue;
                } else {
                    existingIndex.add(stashIndex);
                    RxPrescriptionData.Prescription rx = bean.getStashItem(stashIndex);

                    Boolean patientCompliance = null;
                    boolean isOutsideProvider = false;
                    Boolean isLongTerm = null;
                    boolean isShortTerm = false;
                    Boolean isPastMed = null;
                    boolean isDispenseInternal = false;
                    boolean isStartDateUnknown = false;
                    boolean isNonAuthoritative = false;
                    boolean nosubs = false;
                    Date pickupDate = null;
                    Date pickupTime = null;

                    em = request.getParameterNames();
                    while (em.hasMoreElements()) {
                        String elem = (String) em.nextElement();
                        String val = request.getParameter(elem);
                        val = val.trim();
                        if (elem.startsWith("drugName_" + num)) {
                            if (rx.isCustom()) {
                                rx.setCustomName(val);
                                rx.setBrandName(null);
                                rx.setGenericName(null);
                            } else {
                                rx.setBrandName(val);
                            }
                        } else if ("rxPharmacyId".equals(elem)) {
                            if (val != null && !val.isEmpty()) {
                                rx.setPharmacyId(Integer.parseInt(val));
                            }
                        } else if (elem.equals("repeats_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setRepeat(0);
                            } else {
                                rx.setRepeat(Integer.parseInt(val));
                            }

                        } else if (elem.equals("codingSystem_" + num)) {
                            if (val != null) {
                                rx.setDrugReasonCodeSystem(val);
                            }

                        } else if (elem.equals("reasonCode_" + num)) {
                            if (val != null) {
                                rx.setDrugReasonCode(val);
                            }
                        } else if (elem.equals("instructions_" + num)) {
                            rx.setSpecial(val);
                        } else if (elem.equals("quantity_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setQuantity("0");
                            } else {
                                if (RxUtil.isStringToNumber(val)) {
                                    rx.setQuantity(val);
                                    rx.setUnitName(null);
                                } else {
                                    rx.setQuantity(RxUtil.getQuantityFromQuantityText(val));
                                    rx.setUnitName(RxUtil.getUnitNameFromQuantityText(val));
                                }
                            }
                        } else if (elem.equals("longTerm_" + num)) {
                            if ("yes".equals(val)) {
                                isLongTerm = true;
                            } else if ("no".equals(val)) {
                                isLongTerm = false;
                            }
                        } else if (elem.equals("shortTerm_" + num)) {
                            if (val.equals("on")) {
                                isShortTerm = true;
                            } else {
                                isShortTerm = false;
                            }
                        } else if (elem.equals("nonAuthoritativeN_" + num)) {
                            if (val.equals("on")) {
                                isNonAuthoritative = true;
                            } else {
                                isNonAuthoritative = false;
                            }
                        } else if (elem.equals("nosubs_" + num)) {
                            nosubs = "on".equals(val);
                        } else if (elem.equals("refillDuration_" + num)) {
                            rx.setRefillDuration(Integer.parseInt(val));
                        } else if (elem.equals("refillQuantity_" + num)) {
                            rx.setRefillQuantity(Integer.parseInt(val));
                        } else if (elem.equals("dispenseInterval_" + num)) {
                            rx.setDispenseInterval(val);
                        } else if (elem.equals("protocol_" + num)) {
                            rx.setProtocol(val);
                        } else if (elem.equals("priorRxProtocol_" + num)) {
                            rx.setPriorRxProtocol(val);
                        } else if (elem.equals("lastRefillDate_" + num)) {
                            rx.setLastRefillDate(RxUtil.StringToDate(val, "yyyy-MM-dd"));
                        } else if (elem.equals("rxDate_" + num)) {
                            if ((val == null) || (val.equals(""))) {
                                rx.setRxDate(RxUtil.StringToDate("0000-00-00", "yyyy-MM-dd"));
                            } else {
                                rx.setRxDateFormat(partialDateDao.getFormat(val));
                                rx.setRxDate(partialDateDao.StringToDate(val));
                            }
                        } else if (elem.equals("pickupDate_" + num)) {
                            if ((val != null) && (!val.equals(""))) {
                                pickupDate = RxUtil.StringToDate(val, "yyyy-MM-dd");
                            }
                        } else if (elem.equals("pickupTime_" + num)) {
                            if ((val != null) && (!val.equals(""))) {
                                pickupTime = RxUtil.StringToDate(val, "hh:mm");
                            }
                        } else if (elem.equals("writtenDate_" + num)) {
                            if (val == null || (val.equals(""))) {
                                rx.setWrittenDate(RxUtil.StringToDate("0000-00-00", "yyyy-MM-dd"));
                            } else {
                                rx.setWrittenDateFormat(partialDateDao.getFormat(val));
                                rx.setWrittenDate(partialDateDao.StringToDate(val));
                            }

                        } else if (elem.equals("outsideProviderName_" + num)) {
                            rx.setOutsideProviderName(val);
                        } else if (elem.equals("outsideProviderOhip_" + num)) {
                            if (val.equals("") || val == null) {
                                rx.setOutsideProviderOhip("0");
                            } else {
                                rx.setOutsideProviderOhip(val);
                            }
                        } else if (elem.equals("ocheck_" + num)) {
                            if (val.equals("on")) {
                                isOutsideProvider = true;
                            } else {
                                isOutsideProvider = false;
                            }
                        } else if (elem.equals("pastMed_" + num)) {
                            if ("yes".equals(val)) {
                                isPastMed = true;
                            } else if ("no".equals(val)) {
                                isPastMed = false;
                            }
                        } else if (elem.equals("dispenseInternal_" + num)) {
                            if (val.equals("on")) {
                                isDispenseInternal = true;
                            } else {
                                isDispenseInternal = false;
                            }
                        } else if (elem.equals("startDateUnknown_" + num)) {
                            if (val.equals("on")) {
                                isStartDateUnknown = true;
                            } else {
                                isStartDateUnknown = false;
                            }
                        } else if (elem.equals("comment_" + num)) {
                            rx.setComment(val);
                        } else if (elem.equals("patientCompliance_" + num)) {
                            if ("yes".equals(val)) {
                                patientCompliance = true;
                            } else if ("no".equals(val)) {
                                patientCompliance = false;
                            }
                        } else if (elem.equals("eTreatmentType_" + num)) {
                            if ("--".equals(val)) {
                                rx.setETreatmentType(null);
                            } else {
                                rx.setETreatmentType(val);
                            }
                        } else if (elem.equals("rxStatus_" + num)) {
                            if ("--".equals(val)) {
                                rx.setRxStatus(null);
                            } else {
                                rx.setRxStatus(val);
                            }
                        } else if (elem.equals("drugForm_" + num)) {
                            rx.setDrugForm(val);
                        }

                    }

                    if (!isOutsideProvider) {
                        rx.setOutsideProviderName("");
                        rx.setOutsideProviderOhip("");
                    }
                    rx.setPastMed(isPastMed);
                    rx.setDispenseInternal(isDispenseInternal);
                    rx.setPatientCompliance(patientCompliance);
                    rx.setStartDateUnknown(isStartDateUnknown);
                    rx.setLongTerm(isLongTerm);
                    rx.setShortTerm(isShortTerm);
                    rx.setNonAuthoritative(isNonAuthoritative);
                    rx.setNosubs(nosubs);
                    String newline = System.getProperty("line.separator");

                    if (pickupDate != null && pickupTime != null) {
                        rx.setPickupDate(RxUtil.combineDateTime(pickupDate, pickupTime));
                    } else if (pickupTime != null) {
                        rx.setPickupDate(RxUtil.combineDateTime(new Date(), pickupTime));
                    } else {
                        rx.setPickupDate(pickupDate);
                    }

                    String special;
                    if (rx.isCustomNote()) {
                        rx.setQuantity(null);
                        rx.setUnitName(null);
                        rx.setRepeat(0);
                        special = rx.getCustomName() + newline + rx.getSpecial();
                        if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                            special += newline + rx.getSpecialInstruction();
                    } else if (rx.isCustom()) {// custom drug
                        if (rx.getUnitName() == null) {
                            special = rx.getCustomName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " Repeats:" + "" + rx.getRepeat();
                        } else {
                            special = rx.getCustomName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " " + rx.getUnitName() + " Repeats:" + "" + rx.getRepeat();
                        }
                    } else {// non-custom drug
                        if (rx.getUnitName() == null) {
                            special = rx.getBrandName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();

                            special += newline + "Qty:" + rx.getQuantity() + " Repeats:" + "" + rx.getRepeat();
                        } else {
                            special = rx.getBrandName() + newline + rx.getSpecial();
                            if (rx.getSpecialInstruction() != null && !rx.getSpecialInstruction().equalsIgnoreCase("null") && rx.getSpecialInstruction().trim().length() > 0)
                                special += newline + rx.getSpecialInstruction();
                            special += newline + "Qty:" + rx.getQuantity() + " " + rx.getUnitName() + " Repeats:" + "" + rx.getRepeat();
                        }
                    }

                    if (!rx.isCustomNote() && rx.isMitte()) {
                        special = special.replace("Qty", "Mitte");
                    }

                    rx.setSpecial(special.trim());

                    bean.addAttributeName(rx.getAtcCode() + "-" + String.valueOf(stashIndex));
                    bean.setStashItem(stashIndex, rx);
                }
            } catch (Exception e) {
                logger.error("Error", e);
                continue;
            }
        }
        for (Integer n : existingIndex) {
            if (allIndex.contains(n)) {
                allIndex.remove(n);
            }
        }
        List<Integer> deletedIndex = allIndex;
        // remove closed Rx from stash
        for (Integer n : deletedIndex) {
            bean.removeStashItem(n);
            if (bean.getStashIndex() >= bean.getStashSize()) {
                bean.setStashIndex(bean.getStashSize() - 1);
            }
        }

        saveDrug(request);
        return "refresh";
    }

    public String getDemoNameAndHIN() throws IOException, Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", PRIVILEGE_READ, null)) {
            throw new RuntimeException("missing required sec object (_demographic)");
        }

        String demoNo = request.getParameter("demoNo").trim();
        Demographic d = demographicManager.getDemographic(loggedInInfo, demoNo);
        HashMap hm = new HashMap();
        if (d != null) {
            hm.put("patientName", d.getDisplayName());
            hm.put("patientHIN", d.getHin());
        } else {
            hm.put("patientName", "Unknown");
            hm.put("patientHIN", "Unknown");
        }
        ObjectNode jo = objectMapper.valueToTree(hm);
        response.getOutputStream().write(jo.toString().getBytes());
        return null;
    }

    public String updateLongTermStatus() throws IOException, Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        HashMap<String, Object> hm = new HashMap<>();
        String strId = request.getParameter("ltDrugId");
        boolean isLongTerm = Boolean.parseBoolean(request.getParameter("isLongTerm"));

        if (Objects.isNull(strId)) {
	        hm.put("success", false);
		} else {
            int drugId = Integer.parseInt(strId);
            RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
            if (bean == null) {
                response.sendRedirect("error.html");
                return null;
            }

            RxPrescriptionData rxData = new RxPrescriptionData();
            RxPrescriptionData.Prescription oldRx = rxData.getPrescription(drugId);
            oldRx.setLongTerm(isLongTerm);
            oldRx.setShortTerm(false);
            boolean saveStatus = oldRx.Save(oldRx.getScript_no());

            if (saveStatus) {
                saveStatus = this.rxManager.archiveDrug(loggedInInfo, drugId, bean.getDemographicNo(),
                        isLongTerm ? Drug.ARCHIVED_REASON_LT_ENABLED : Drug.ARCHIVED_REASON_LT_DISABLED);
            }

            hm.put("success", saveStatus);
        }
        response.setContentType("application/json");
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        response.getOutputStream().write(jsonObject.toString().getBytes());
        return null;
    }
  
    public void saveDrug(final HttpServletRequest request) throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");

        RxPrescriptionData.Prescription rx = null;
        RxPrescriptionData prescription = new RxPrescriptionData();
        String scriptId = prescription.saveScript(loggedInInfo, bean);
        StringBuilder auditStr = new StringBuilder();
        ArrayList<String> attrib_names = bean.getAttributeNames();

        for (int i = 0; i < bean.getStashSize(); i++) {
            try {
                rx = bean.getStashItem(i);
                rx.Save(scriptId);// new drug id available after this line
                rx.setScript_no(scriptId);
                bean.addRandomIdDrugIdPair(rx.getRandomId(), rx.getDrugId());
                auditStr.append(rx.getAuditString());
                auditStr.append("\n");

                // save drug reason. Method borrowed from
                // RxReason2Action.
                if (!StringUtils.isNullOrEmpty(rx.getDrugReasonCode())) {
                    addDrugReason(rx.getDrugReasonCodeSystem(),
                            "false", "", rx.getDrugReasonCode(),
                            rx.getDrugId() + "", rx.getDemographicNo() + "",
                            rx.getProviderNo(), request);
                }

                //write partial date
                if (StringUtils.filled(rx.getWrittenDateFormat()))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, rx.getDrugId(), PartialDate.DRUGS_WRITTENDATE, rx.getWrittenDateFormat());

                if (StringUtils.filled(rx.getRxDateFormat()))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, rx.getDrugId(), PartialDate.DRUGS_STARTDATE, rx.getRxDateFormat());
            } catch (Exception e) {
                logger.error("Error", e);
            }

            // Save annotation
            HttpSession se = request.getSession();
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
            CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);
            String attrib_name = attrib_names.get(i);
            if (attrib_name != null) {
                CaseManagementNote cmn = (CaseManagementNote) se.getAttribute(attrib_name);
                if (cmn != null) {
                    cmm.saveNoteSimple(cmn);
                    CaseManagementNoteLink cml = new CaseManagementNoteLink();
                    cml.setTableName(CaseManagementNoteLink.DRUGS);
                    cml.setTableId((long) rx.getDrugId());
                    cml.setNoteId(cmn.getId());
                    cmm.saveNoteLink(cml);
                    se.removeAttribute(attrib_name);
                    LogAction.addLog(cmn.getProviderNo(), LogConst.ANNOTATE, CaseManagementNoteLink.DISP_PRESCRIP, scriptId, request.getRemoteAddr(), cmn.getDemographic_no(), cmn.getNote());
                }
            }
            rx = null;
        }

        String ip = request.getRemoteAddr();
        request.setAttribute("scriptId", scriptId);

        List<String> reRxDrugList = new ArrayList<String>();
        reRxDrugList = bean.getReRxDrugIdList();

        Iterator<String> i = reRxDrugList.iterator();

        DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);

        while (i.hasNext()) {

            String item = i.next();

            //archive drug(s)
            Drug drug = drugDao.find(Integer.parseInt(item));
            drug.setArchived(true);
            drug.setArchivedDate(new Date());
            drug.setArchivedReason(Drug.REPRESCRIBED);
            drugDao.merge(drug);

            //log that this med is being re-prescribed
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.REPRESCRIBE, LogConst.CON_MEDICATION, "drugid=" + item, ip, "" + bean.getDemographicNo(), auditStr.toString());

            //log that the med is being discontinued buy the system
            LogAction.addLog("-1", LogConst.DISCONTINUE, LogConst.CON_MEDICATION, "drugid=" + item, "", "" + bean.getDemographicNo(), auditStr.toString());

        }
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_PRESCRIPTION, scriptId, ip, "" + bean.getDemographicNo(), auditStr.toString());

        return;
    }

    public String searchSpecialInstructions() throws IOException {
		String str = request.getParameter("query");
		Set<String> set = this.rxManager.getStoredInstructionsMatching(str);

		Map<String, Object> json = new HashMap<>();
        json.put("results", set);

		response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

		ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getWriter(), json);

        return "prescribe";
	}

    public String checkNoStashItem() throws IOException, Exception {
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        int n = bean.getStashSize();
        HashMap hm = new HashMap();
        hm.put("NoStashItem", n);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);

        response.setContentType("application/json");

        response.getOutputStream().write(jsonObject.toString().getBytes());
        return null;
    }


    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
    }

    private void addDrugReason(String codingSystem,
                               String primaryReasonFlagStr, String comments,
                               String code, String drugIdStr, String demographicNo,
                               String providerNo, HttpServletRequest request) {
        DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
        Integer drugId = Integer.parseInt(drugIdStr);

        // should this be instantiated with the Spring Utilities?
        CodingSystemManager codingSystemManager = new CodingSystemManager();

        if (!codingSystemManager.isCodeAvailable(codingSystem, code)) {
            request.setAttribute("message", getText("SelectReason.error.codeValid"));
            return;
        }

        if (drugReasonDao.hasReason(drugId, codingSystem, code, true)) {
            request.setAttribute("message", getText("SelectReason.error.duplicateCode"));
            return;
        }

        MiscUtils.getLogger().debug("addDrugReasonCalled codingSystem " + codingSystem + " code " + code + " drugIdStr " + drugId);

        boolean primaryReasonFlag = true;
        if (!"true".equals(primaryReasonFlagStr)) {
            primaryReasonFlag = false;
        }

        DrugReason dr = new DrugReason();

        dr.setDrugId(drugId);
        dr.setProviderNo(providerNo);
        dr.setDemographicNo(Integer.parseInt(demographicNo));

        dr.setCodingSystem(codingSystem);
        dr.setCode(code);
        dr.setComments(comments);
        dr.setPrimaryReasonFlag(primaryReasonFlag);
        dr.setArchivedFlag(false);
        dr.setDateCoded(new Date());

        drugReasonDao.addNewDrugReason(dr);

        String ip = request.getRemoteAddr();
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DRUGREASON, "" + dr.getId(), ip, demographicNo, dr.getAuditString());

    }

    String action = "";
    int drugId = 0;
    int demographicNo = 0;
    String rxDate = null;       //cnv to Date
    String endDate = null;      //cnv to Date
    String writtenDate = null;    //cnv to Date
    String GN = null;
    String BN = null;
    String GCN_SEQNO = "0";
    String customName = null;
    String takeMin = null;
    String takeMax = null;
    String frequencyCode = null;
    String duration = null;
    String durationUnit = null;
    String quantity = null;
    int repeat = 0;
    String lastRefillDate = null;
    boolean nosubs = false;
    boolean prn = false;
    boolean customInstr = false;
    Boolean longTerm = null;
    boolean shortTerm = false;
    Boolean pastMed = null;
    boolean dispenseInternal = false;
    Boolean patientCompliance = null;
    String special = null;
    String atcCode = null;
    String regionalIdentifier = null;
    String method = null;
    String unit = null;
    String unitName = null;
    String route = null;
    String dosage = null;
    String outsideProviderName = null;
    String outsideProviderOhip = null;


    public String getAction() {
        return this.action;
    }

    public void setAction(String RHS) {
        this.action = RHS;
    }

    public int getDrugId() {
        return this.drugId;
    }

    public void setDrugID(int RHS) {
        this.drugId = RHS;
    }

    public int getDemographicNo() {
        return this.demographicNo;
    }

    public void setDemographicNo(int RHS) {
        this.demographicNo = RHS;
    }

    public String getRxDate() {
        return this.rxDate;
    }

    public void setRxDate(String RHS) {
        this.rxDate = RHS;
    }

    public String getEndDate() {
        return this.endDate;
    }

    public void setEndDate(String RHS) {
        this.endDate = RHS;
    }

    public String getWrittenDate() {
        return this.writtenDate;
    }

    public void setWrittenDate(String RHS) {
        this.writtenDate = RHS;
    }

    public String getGenericName() {
        return this.GN;
    }

    public void setGenericName(String RHS) {
        this.GN = RHS;
    }

    public String getBrandName() {
        return this.BN;
    }

    public void setBrandName(String RHS) {
        this.BN = RHS;
    }

    public String getGCN_SEQNO() {
        return this.GCN_SEQNO;
    }

    public void setGCN_SEQNO(String RHS) {
        this.GCN_SEQNO = RHS;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(String RHS) {
        this.customName = RHS;
    }

    public String getTakeMin() {
        return this.takeMin;
    }

    public void setTakeMin(String RHS) {
        this.takeMin = RHS;
    }

    public float getTakeMinFloat() {
        float i = -1;
        try {
            i = Float.parseFloat(this.takeMin);
        } catch (Exception e) {
        }
        return i;
    }

    public String getTakeMax() {
        return this.takeMax;
    }

    public void setTakeMax(String RHS) {
        this.takeMax = RHS;
    }

    public float getTakeMaxFloat() {
        float i = -1;
        try {
            i = Float.parseFloat(this.takeMax);
        } catch (Exception e) {
        }
        return i;
    }

    public String getFrequencyCode() {
        return this.frequencyCode;
    }

    public void setFrequencyCode(String RHS) {
        this.frequencyCode = RHS;
    }

    public String getDuration() {
        return this.duration;
    }

    public void setDuration(String RHS) {
        this.duration = RHS;
    }

    public String getDurationUnit() {
        return this.durationUnit;
    }

    public void setDurationUnit(String RHS) {
        this.durationUnit = RHS;
    }

    public String getQuantity() {
        return this.quantity;
    }

    public void setQuantity(String RHS) {
        this.quantity = RHS;
    }

    public int getRepeat() {
        return this.repeat;
    }

    public void setRepeat(int RHS) {
        this.repeat = RHS;
    }

    public String getLastRefillDate() {
        return this.lastRefillDate;
    }

    public void setLastRefillDate(String RHS) {
        this.lastRefillDate = RHS;
    }

    public boolean getNosubs() {
        return this.nosubs;
    }

    public void setNosubs(boolean RHS) {
        this.nosubs = RHS;
    }

    public boolean getPrn() {
        return this.prn;
    }

    public void setPrn(boolean RHS) {
        this.prn = RHS;
    }

    public String getSpecial() {
        return this.special;
    }

    public void setSpecial(String RHS) {

        if (RHS == null || RHS.length() < 6)
            MiscUtils.getLogger().error("drug special is either null or empty : " + RHS, new IllegalArgumentException("special is null or empty"));

        this.special = RHS;
    }

    public boolean getCustomInstr() {
        return this.customInstr;
    }

    public void setCustomInstr(boolean c) {
        this.customInstr = c;
    }

    public Boolean getLongTerm() {
        return this.longTerm;
    }

    public void setLongTerm(Boolean trueFalseNull) {
        this.longTerm = trueFalseNull;
    }

    public boolean getShortTerm() {
        return this.shortTerm;
    }

    public void setShortTerm(boolean st) {
        this.shortTerm = st;
    }

    public Boolean getPastMed() {
        return this.pastMed;
    }

    public void setPastMed(Boolean trueFalseNull) {
        this.pastMed = trueFalseNull;
    }

    public boolean getDispenseInternal() {
        return dispenseInternal;
    }

    public boolean isDispenseInternal() {
        return dispenseInternal;
    }

    public void setDispenseInternal(boolean dispenseInternal) {
        this.dispenseInternal = dispenseInternal;
    }

    public Boolean getPatientCompliance() {
        return this.patientCompliance;
    }

    public void setPatientCompliance(Boolean trueFalseNull) {
        this.patientCompliance = trueFalseNull;
    }

    /**
     * Setter accepts String to handle both numeric IDs and composite IDs.
     * Only sets the int property if the input is a pure integer.
     */
    public void setDrugId(String drugId) {
        if (drugId != null) {
            try {
                this.drugId = Integer.parseInt(drugId);
            } catch (NumberFormatException e) {
                // Non-integer drugIds (like Vigilance composite IDs) are handled via request.getParameter()
            }
        }
    }

    public String getGN() {
        return GN;
    }

    public void setGN(String GN) {
        this.GN = GN;
    }

    public String getBN() {
        return BN;
    }

    public void setBN(String BN) {
        this.BN = BN;
    }

    public boolean isNosubs() {
        return nosubs;
    }

    public boolean isPrn() {
        return prn;
    }

    public boolean isCustomInstr() {
        return customInstr;
    }

    public boolean isShortTerm() {
        return shortTerm;
    }

    public String getAtcCode() {
        return atcCode;
    }

    public void setAtcCode(String atcCode) {
        this.atcCode = atcCode;
    }

    public String getRegionalIdentifier() {
        return regionalIdentifier;
    }

    public void setRegionalIdentifier(String regionalIdentifier) {
        this.regionalIdentifier = regionalIdentifier;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getOutsideProviderName() {
        return outsideProviderName;
    }

    public void setOutsideProviderName(String outsideProviderName) {
        this.outsideProviderName = outsideProviderName;
    }

    public String getOutsideProviderOhip() {
        return outsideProviderOhip;
    }

    public void setOutsideProviderOhip(String outsideProviderOhip) {
        this.outsideProviderOhip = outsideProviderOhip;
    }
}
