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


package ca.openosp.openo.billings.ca.bc.pageUtil;

import ca.openosp.openo.commn.model.Demographic;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.entities.PaymentType;
import ca.openosp.openo.entities.WCB;
import ca.openosp.openo.billings.ca.bc.MSP.AgeValidator;
import ca.openosp.openo.billings.ca.bc.MSP.ServiceCodeValidationLogic;
import ca.openosp.openo.billings.ca.bc.MSP.SexValidator;
import ca.openosp.openo.billings.ca.bc.Teleplan.WCBCodes;
import ca.openosp.openo.billings.ca.bc.data.BillingFormData;
import ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO;
import ca.openosp.openo.billings.ca.bc.pageUtil.BillingBillingManager.BillingItem;
import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.util.SqlUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class BillingCreateBilling2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger log = MiscUtils.getLogger();

    private ServiceCodeValidationLogic vldt = new ServiceCodeValidationLogic();
    private ArrayList<String> patientDX = new ArrayList<String>(); //List of disease codes for current patient

    public String execute() throws IOException, ServletException {
        List<String> errors = new ArrayList<>();
        BillingBillingManager bmanager = new BillingBillingManager();

        bmanager.setBillTtype(this.getXml_billtype());

        /**
         * This service list is not necessary
         */
        String[] service = new String[0]; //this.getService();
        String other_service1 = StringUtils.trimToEmpty(this.getXml_other1());
        String other_service2 = StringUtils.trimToEmpty(this.getXml_other2());
        String other_service3 = StringUtils.trimToEmpty(this.getXml_other3());
        String other_service1_unit = this.getXml_other1_unit();
        String other_service2_unit = this.getXml_other2_unit();
        String other_service3_unit = this.getXml_other3_unit();

        BillingSessionBean bean = (BillingSessionBean) request.getSession().getAttribute("billingSessionBean");
        Demographic demo = new DemographicData().getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), bean.getPatientNo());
        this.patientDX = vldt.getPatientDxCodes(demo.getDemographicNo().toString());
        ArrayList<BillingItem> billItem = bmanager.getDups2(service, other_service1,
                other_service2, other_service3,
                other_service1_unit,
                other_service2_unit,
                other_service3_unit);
        BillingFormData billform = new BillingFormData();
        String payMeth = this.getXml_encounter();
        bean.setGrandtotal(bmanager.getGrandTotal(billItem));
        bean.setPatientLastName(demo.getLastName());
        bean.setPatientFirstName(demo.getFirstName());
        bean.setPatientDoB(DemographicData.getDob(demo));
        bean.setPatientAddress1(demo.getAddress());
        bean.setPatientAddress2(demo.getCity());
        bean.setPatientPostal(demo.getPostal());
        bean.setPatientSex(demo.getSex());
        bean.setPatientPHN(demo.getHin() + demo.getVer());
        bean.setPatientHCType(demo.getHcType());
        bean.setPatientAge(demo.getAge());
        bean.setBillingType(this.getXml_billtype());
        bean.setPaymentType(payMeth);

        if (payMeth.equals("8")) {
            bean.setEncounter("E");
        } else {
            bean.setEncounter("O");
        }

        bean.setWcbId(request.getParameter("WCBid"));
        bean.setVisitType(this.getXml_visittype());
        bean.setVisitLocation(this.getXml_location());
        bean.setServiceDate(this.getXml_appointment_date());
        bean.setStartTimeHr(this.getXml_starttime_hr());
        bean.setStartTimeMin(this.getXml_starttime_min());
        bean.setEndTimeHr(this.getXml_endtime_hr());
        bean.setEndTimeMin(this.getXml_endtime_min());

        bean.setAdmissionDate(this.getXml_vdate());
        bean.setBillingProvider(this.getXml_provider());
        bean.setBillingPracNo(billform.getPracNo(this.getXml_provider()));
        bean.setBillingGroupNo(billform.getGroupNo(this.getXml_provider()));
        bean.setDx1(this.getXml_diagnostic_detail1());
        bean.setDx2(this.getXml_diagnostic_detail2());
        bean.setDx3(this.getXml_diagnostic_detail3());
        bean.setReferral1(this.getXml_refer1());
        bean.setReferral2(this.getXml_refer2());
        bean.setReferType1(this.getRefertype1());
        bean.setReferType2(this.getRefertype2());
        bean.setBillItem(billItem);
        bean.setCorrespondenceCode(this.getCorrespondenceCode());
        bean.setNotes(this.getNotes());
        bean.setDependent(this.getDependent());
        bean.setAfterHours(this.getAfterHours());
        bean.setTimeCall(this.getTimeCall());
        bean.setSubmissionCode(this.getSubmissionCode());
        bean.setShortClaimNote(this.getShortClaimNote());
        bean.setService_to_date(this.getService_to_date());
        bean.setIcbc_claim_no(this.getIcbc_claim_no());
        bean.setMessageNotes(this.getMessageNotes());
        bean.setMva_claim_code(this.getMva_claim_code());
        bean.setFacilityNum(this.getFacilityNum());
        bean.setFacilitySubNum(this.getFacilitySubNum());
        ArrayList<PaymentType> lst = billform.getPaymentTypes();
        for (int i = 0; i < lst.size(); i++) {
            PaymentType tp = lst.get(i);
            if (tp.getId().equals(payMeth)) {
                bean.setPaymentTypeName(tp.getPaymentType());
                break;
            }

        }
        log.debug("Ignore warnings ? " + request.getParameter("ignoreWarn"));
        if (request.getParameter("ignoreWarn") == null) {
            validateServiceCodeList(billItem, demo, errors);
            validateDxCodeList(bean, errors);
            validateServiceCodeTimes(billItem, errors);

            for (Iterator<BillingItem> iter = billItem.iterator(); iter.hasNext(); ) {
                BillingItem item = iter.next();
                validateCDMCodeConditions(errors, demo.getDemographicNo().toString(),
                        item.getServiceCode());
            }

            if (!errors.isEmpty()) {
                validateCodeLastBilled(request, errors, demo.getDemographicNo().toString());
                response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingBC.jsp");
                return NONE;
            }
            validate00120(errors, demo, billItem, bean.getServiceDate());
            if (!errors.isEmpty()) {
                validateCodeLastBilled(request, errors, demo.getDemographicNo().toString());
                response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingBC.jsp");
                return NONE;
            }
            this.validatePatientManagementCodes(errors, demo, billItem,
                    bean.getServiceDate());
            if (!errors.isEmpty()) {
                validateCodeLastBilled(request, errors, demo.getDemographicNo().toString());
                response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingBC.jsp");
                return NONE;
            }
        }

        if (request.getParameter("WCBid") != null) {
            MiscUtils.getLogger().debug("WCB id is not null " + request.getParameter("WCBid"));
            List<String> errs = null;
            WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
            BillingmasterDAO billingmasterDAO = (BillingmasterDAO) ctx.getBean(BillingmasterDAO.class);
            WCB wcbForm = billingmasterDAO.getWCBForm(request.getParameter("WCBid"));

            for (Iterator<BillingItem> iter = billItem.iterator(); iter.hasNext(); ) {
                BillingItem item = iter.next();
                String sc = item.getServiceCode();
                boolean formNeeded = WCBCodes.getInstance().isFormNeeded(sc);
                MiscUtils.getLogger().debug("code:" + sc + " form needed " + formNeeded);
                if (formNeeded) {
                    MiscUtils.getLogger().debug("Setting form needed 1");
                    errs = wcbForm.verifyEverythingOnForm();
                    if (errs != null && errs.size() > 0) {
                        request.setAttribute("WCBcode", sc);
                        request.setAttribute("WCBFormNeeds", errs);
                        response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingBC.jsp");
                        return NONE;
                    }
                } else {
                    errs = wcbForm.verifyFormNotNeeded();
                }
            }
            if (errs != null && errs.size() > 0) {
                MiscUtils.getLogger().debug("Setting form needed 2");
                request.setAttribute("WCBFormNeeds", errs);
                response.sendRedirect(request.getContextPath() + "/billing/CA/BC/billingBC.jsp");
                return NONE;
            }
        }

        //We want this alert to show up regardless
        //However we don't necessarily want it to force the user to enter a bill
        validateCodeLastBilled(request, errors, demo.getDemographicNo().toString());


        //if fromBilling is true set forward to WCB Form
////    if ("true".equals(fromBilling)) {
////      if (this.getXml_billtype().equalsIgnoreCase("WCB")) {
////        WCBForm wcbForm = new WCBForm();
////        wcbForm.Set(bean);
////        request.setAttribute("WCBForm", wcbForm);
////        wcbForm.setFormNeeded("1");
////        wcbForm.setProviderNo(bean.getApptProviderNo());
////        wcbForm.setDoValidate(true);
////
////        return "WCB";
////      }
////    }
        return SUCCESS;
    }

    /**
     * validateServiceCodeTimes
     *
     * @param errors ActionMessages
     */
    private void validateServiceCodeTimes(ArrayList<BillingItem> billItems,
                                          List<String> errors) {
        String qry = "select bt.billingservice_no,bt.timeRange " +
                "from billing_msp_servicecode_times bt";

        List<String[]> results = SqlUtils.getQueryResultsList(qry);

        for (int i = 0; i < billItems.size(); i++) {
            BillingItem item = billItems.get(i);
            boolean noStartHour = this.getXml_starttime_hr() == null ||
                    "".equals(this.getXml_starttime_hr());
            boolean noStartMinute = (this.getXml_starttime_min() == null ||
                    "".equals(this.getXml_starttime_min()));
            boolean noStartTime = noStartHour && noStartMinute;

            boolean noEndHour = this.getXml_endtime_hr() == null ||
                    "".equals(this.getXml_endtime_hr());
            boolean noEndMinute = (this.getXml_endtime_min() == null ||
                    "".equals(this.getXml_endtime_min()));
            boolean noEndTime = noEndHour && noEndMinute;
            String svcCode = item.getServiceCode();
            for (Iterator<String[]> iter = results.iterator(); iter.hasNext(); ) {
                String[] elem = iter.next();
                String codeToCompare = elem[0];
                if (codeToCompare.equals(svcCode)) {
                    //if the specified code requires a start time
                    if ("0".equals(elem[1])) {
                        if (noStartTime) {
                            errors.add("oscar.billing.CA.BC.billingBC.error.startTimeNeeded");
                            addActionError(getText("oscar.billing.CA.BC.billingBC.error.startTimeNeeded", new String[]{item.getServiceCode()}));
                        }
                    } else if ("1".equals(elem[1])) {
                        if (noStartTime || noEndTime) {
                            errors.add("oscar.billing.CA.BC.billingBC.error.startTimeandEndNeeded");
                            addActionError(getText("oscar.billing.CA.BC.billingBC.error.startTimeandEndNeeded", new String[]{item.getServiceCode()}));
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates a String array of diagnostic codes and adds an ActionMessage
     * to the ActionMessages object, for any of the codes that don't validate
     * successfully
     *
     * @param errors ActionMessages
     */

    private void validateDxCodeList(BillingSessionBean bean,
                                    List<String> errors) {
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        String[] dxcodes = {bean.getDx1(), bean.getDx2(), bean.getDx3()};

        // this code only applies to the special MSP Dx code table when billing MSP.
        String billType = bean.getBillType();
        for (int i = 0; i < dxcodes.length; i++) {
            String code = dxcodes[i];
            if (("MSP".equalsIgnoreCase(billType) || "ICBC".equalsIgnoreCase(billType) || "WCB".equalsIgnoreCase(billType))
                    && (code != null && !code.equals("") && !per.dxcodeExists(code))) {
                errors.add("oscar.billing.CA.BC.billingBC.error.invaliddxcode");
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.invaliddxcode", new String[]{code}));

            }
        }
    }

    /**
     * Validates a String array of service codes and adds and ActionMessage
     * to the ActionMessages object, for any of the codes that don't validate
     * successfully
     *
     * @param demo   Demographic
     * @param errors ActionMessages
     */
    private void validateServiceCodeList(ArrayList<BillingItem> billItems,
                                         Demographic demo,
                                         List<String> errors) {
        BillingAssociationPersistence per = new BillingAssociationPersistence();
        for (int i = 0; i < billItems.size(); i++) {
            BillingItem item = billItems.get(i);
            if (per.serviceCodeExists(item.
                    getServiceCode())) {
                AgeValidator age = (AgeValidator) vldt.getAgeValidator(item.
                        getServiceCode(), demo);
                SexValidator sex = (SexValidator) vldt.getSexValidator(item.
                        getServiceCode(), demo);
                if (!age.isValid()) {
                    errors.add("oscar.billing.CA.BC.billingBC.error.invalidAge");
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidAge", new String[]{item.getServiceCode(),
                            String.valueOf(demo.getAgeInYears()),
                            age.getDescription()}));
                }
                if (!sex.isValid()) {
                    errors.add("oscar.billing.CA.BC.billingBC.error.invalidSex");
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidSex", new String[]{item.getServiceCode(), demo.getSex(), sex.getGender()}));
                }
            } else {
                errors.add("oscar.billing.CA.BC.billingBC.error.invalidsvccode");
                addActionError(getText("oscar.billing.CA.BC.billingBC.error.invalidsvccode", new String[]{item.getServiceCode()}));
            }

        }
    }

    private void validate00120(List<String> errors,
                               Demographic demo,
                               ArrayList<BillingItem> billItem, String serviceDate) {
        for (Iterator<BillingItem> iter = billItem.iterator(); iter.hasNext(); ) {
            BillingItem item = iter.next();
            String[] cnlsCodes = OscarProperties.getInstance().getProperty(
                    "COUNSELING_CODES", "").split(",");
            Vector vCodes = new Vector(Arrays.asList(cnlsCodes));
            if (vCodes.contains(item.getServiceCode())) {
                if (!vldt.hasMore00120Codes(demo.getDemographicNo().toString(),
                        item.getServiceCode(), serviceDate)) {
                    errors.add("oscar.billing.CA.BC.billingBC.error.noMore00120");
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.noMore00120"));
                }
                break;
            }
        }
    }

    /**
     * The rules for the 145015  code are as follows:
     * A maximum of 6 units may be billed per calendar year
     * A maximum of 4 units may be billed on any given day
     *
     * @param serviceDate String - The date of service
     * @return boolean -  true if the specified service is billable
     */
    private void validatePatientManagementCodes(List<String> errors,
                                                Demographic demo,
                                                ArrayList<BillingItem> billItem,
                                                String serviceDate) {
        HashMap<String, Double> mgmCodeCount = new HashMap<String, Double>();
        mgmCodeCount.put("14015", Double.valueOf(0));
        mgmCodeCount.put("14016", Double.valueOf(0));
        for (Iterator<BillingItem> iter = billItem.iterator(); iter.hasNext(); ) {
            BillingItem item = iter.next();
            if (mgmCodeCount.containsKey(item.getServiceCode())) {
                //Increments the service code count by the number of units for
                //the current bill item
                Double svcCodeUnitCount = Double.valueOf(item.getUnit());
                Double unitCount = mgmCodeCount.get(item.getServiceCode());
                unitCount = Double.valueOf(unitCount.doubleValue() +
                        svcCodeUnitCount.doubleValue());
                mgmCodeCount.remove(item.getServiceCode());
                mgmCodeCount.put(item.getServiceCode(), unitCount);
            }
        }
        for (Iterator<String> iter = mgmCodeCount.keySet().iterator(); iter.hasNext(); ) {
            String key = iter.next();
            double count = (mgmCodeCount.get(key)).doubleValue();
            if (count > 0) {
                Map<String, Double> availableUnits = vldt.getCountAvailablePatientManagementUnits(demo.
                        getDemographicNo().toString(), key, serviceDate);
                double dailyAvail = (availableUnits.get(ServiceCodeValidationLogic.
                        DAILY_AVAILABLE_UNITS)).doubleValue();
                double yearAvail = (availableUnits.get(ServiceCodeValidationLogic.
                        ANNUAL_AVAILABLE_UNITS)).doubleValue();

                if ((count > dailyAvail)) {
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.patientManagementCodesDayUsed",
                            new String[]{key, String.valueOf(count), String.valueOf(dailyAvail)}));
                } else if (count > yearAvail) {
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.patientManagementCodesYearUsed",
                            new String[]{key, String.valueOf(count), String.valueOf(yearAvail)}));
                }
            }
        }
    }

    private void validateCDMCodeConditions(List<String> errors, String demoNo,
                                           String serviceCode) {
        String cdmRulesQry =
                "SELECT serviceCode,conditionCode FROM billing_service_code_conditions";
        List<String[]> cdmRules = SqlUtils.getQueryResultsList(cdmRulesQry);
        List<String[]> cdmSvcCodes = vldt.getCDMCodes();
        for (String[] item : cdmSvcCodes) {
            if (patientDX.contains(item[0])) {
                if (serviceCode.equals(item[1])) {
                    validateCDMCodeConditionsHlp(errors, demoNo, cdmRules, item[1]);
                }
            }
        }
    }

    private void validateCDMCodeConditionsHlp(List<String> errors, String demoNo,
                                              List<String[]> cdmRules, String code) {
        for (String[] item : cdmRules) {
            if (code.equals(item[0])) {
                int days = vldt.daysSinceCodeLastBilled(demoNo, item[1]);
                if (days >= 0 && days < 365) {
                    addActionError(getText("oscar.billing.CA.BC.billingBC.error.codeCond", new String[]{item[0], item[1]}));
                }
            }
        }
    }

    /**
     * @param errors ActionMessages
     * @todo Document Me
     */
    private void validateCodeLastBilled(HttpServletRequest request,
                                        List<String> errors, String demoNo) {
        List<String[]> cdmSvcCodes = vldt.getCDMCodes();
        for (Iterator<String[]> iter = cdmSvcCodes.iterator(); iter.hasNext(); ) {
            String[] item = iter.next();
            if (patientDX.contains(item[0])) {
                validateCodeLastBilledHlp(errors, demoNo, item[1]);
            }
        }
    }

    private void validateCodeLastBilledHlp(List<String> errors,
                                           String demoNo, String code) {
        int codeLastBilled = -1;
        String conditionCodeQuery = "select conditionCode from billing_service_code_conditions where serviceCode = '" +
                code + "'";
        List<String[]> conditions = SqlUtils.getQueryResultsList(conditionCodeQuery);

        for (Iterator<String[]> iter = conditions.iterator(); iter.hasNext(); ) {
            String[] row = iter.next();
            codeLastBilled = vldt.daysSinceCodeLastBilled(demoNo, row[0]);
            if (codeLastBilled < 365 && codeLastBilled > -1) {
                break;
            }
        }
        if (codeLastBilled > 365) {
            errors.add("oscar.billing.CA.BC.billingBC.error.codeLastBilled");
            addActionError(getText("oscar.billing.CA.BC.billingBC.error.codeLastBilled", new String[]{String.valueOf(codeLastBilled), code}));

        } else if (codeLastBilled == -1) {
            errors.add("oscar.billing.CA.BC.billingBC.error.codeNeverBilled");
            addActionError(getText("oscar.billing.CA.BC.billingBC.error.codeNeverBilled", new String[]{code}));

        }
    }


    private String[] service;
    private String xml_provider, xml_location, xml_billtype;
    private String xml_appointment_date;
    private String xml_visittype, xml_vdate;
    private String xml_other1, xml_other2, xml_other3;
    private String xml_other1_unit, xml_other2_unit, xml_other3_unit;
    private String xml_refer1 = "", xml_refer2 = "", refertype1, refertype2;
    private String xml_diagnostic_detail1, xml_diagnostic_detail2,
            xml_diagnostic_detail3;
    private String xml_encounter = "9";
    private String notes = "", icbc_claim_no;
    private String correspondenceCode;
    private String dependent = null;
    private String afterHours = null;
    private String timeCall = null;
    private String submissionCode = null;
    private String service_to_date = null;
    private String shortClaimNote = null;
    private String messageNotes = null;
    private String mva_claim_code = null;
    private String facilityNum = null;
    private String facilitySubNum = null;
    private String mode;
    private String xml_endtime_hr = "";
    private String xml_endtime_min = "";
    private String xml_starttime_hr = "";
    private String xml_starttime_min = "";
    String requestId;

    public String[] getService() {
        return service;
    }

    public void setService(String[] service) {
        this.service = service;
    }

    public String getXml_provider() {
        return xml_provider;
    }

    public void setXml_provider(String xml_provider) {
        this.xml_provider = xml_provider;
    }

    public String getXml_location() {
        return xml_location;
    }

    public void setXml_location(String xml_location) {
        this.xml_location = xml_location;
    }

    public String getXml_billtype() {
        return xml_billtype;
    }

    public void setXml_billtype(String xml_billtype) {
        this.xml_billtype = xml_billtype;
    }

    public String getXml_appointment_date() {
        return xml_appointment_date;
    }

    public void setXml_appointment_date(String xml_appointment_date) {
        this.xml_appointment_date = xml_appointment_date;
    }

    public String getXml_visittype() {
        return xml_visittype;
    }

    public void setXml_visittype(String xml_visittype) {
        this.xml_visittype = xml_visittype;
    }

    public String getXml_vdate() {
        return xml_vdate;
    }

    public void setXml_vdate(String xml_vdate) {
        this.xml_vdate = xml_vdate;
    }

    public String getXml_other1() {
        return xml_other1;
    }

    public void setXml_other1(String xml_other1) {
        this.xml_other1 = xml_other1;
    }

    public String getXml_other2() {
        return xml_other2;
    }

    public void setXml_other2(String xml_other2) {
        this.xml_other2 = xml_other2;
    }

    public String getXml_other3() {
        return xml_other3;
    }

    public void setXml_other3(String xml_other3) {
        this.xml_other3 = xml_other3;
    }

    public String getXml_other1_unit() {
        return xml_other1_unit;
    }

    public void setXml_other1_unit(String xml_other1_unit) {
        this.xml_other1_unit = xml_other1_unit;
    }

    public String getXml_other2_unit() {
        return xml_other2_unit;
    }

    public void setXml_other2_unit(String xml_other2_unit) {
        this.xml_other2_unit = xml_other2_unit;
    }

    public String getXml_other3_unit() {
        return xml_other3_unit;
    }

    public void setXml_other3_unit(String xml_other3_unit) {
        this.xml_other3_unit = xml_other3_unit;
    }

    public String getXml_refer1() {
        return xml_refer1;
    }

    public void setXml_refer1(String xml_refer1) {
        this.xml_refer1 = xml_refer1;
    }

    public String getXml_refer2() {
        return xml_refer2;
    }

    public void setXml_refer2(String xml_refer2) {
        this.xml_refer2 = xml_refer2;
    }

    public String getRefertype1() {
        return refertype1;
    }

    public void setRefertype1(String refertype1) {
        this.refertype1 = refertype1;
    }

    public String getRefertype2() {
        return refertype2;
    }

    public void setRefertype2(String refertype2) {
        this.refertype2 = refertype2;
    }

    public String getXml_diagnostic_detail1() {
        return xml_diagnostic_detail1;
    }

    public void setXml_diagnostic_detail1(String xml_diagnostic_detail1) {
        this.xml_diagnostic_detail1 = xml_diagnostic_detail1;
    }

    public String getXml_diagnostic_detail2() {
        return xml_diagnostic_detail2;
    }

    public void setXml_diagnostic_detail2(String xml_diagnostic_detail2) {
        this.xml_diagnostic_detail2 = xml_diagnostic_detail2;
    }

    public String getXml_diagnostic_detail3() {
        return xml_diagnostic_detail3;
    }

    public void setXml_diagnostic_detail3(String xml_diagnostic_detail3) {
        this.xml_diagnostic_detail3 = xml_diagnostic_detail3;
    }

    public String getXml_encounter() {
        return xml_encounter;
    }

    public void setXml_encounter(String xml_encounter) {
        this.xml_encounter = xml_encounter;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getIcbc_claim_no() {
        return icbc_claim_no;
    }

    public void setIcbc_claim_no(String icbc_claim_no) {
        this.icbc_claim_no = icbc_claim_no;
    }

    public String getCorrespondenceCode() {
        return correspondenceCode;
    }

    public void setCorrespondenceCode(String correspondenceCode) {
        this.correspondenceCode = correspondenceCode;
    }

    public String getDependent() {
        return dependent;
    }

    public void setDependent(String dependent) {
        this.dependent = dependent;
    }

    public String getAfterHours() {
        return afterHours;
    }

    public void setAfterHours(String afterHours) {
        this.afterHours = afterHours;
    }

    public String getTimeCall() {
        return timeCall;
    }

    public void setTimeCall(String timeCall) {
        this.timeCall = timeCall;
    }

    public String getSubmissionCode() {
        return submissionCode;
    }

    public void setSubmissionCode(String submissionCode) {
        this.submissionCode = submissionCode;
    }

    public String getService_to_date() {
        return service_to_date;
    }

    public void setService_to_date(String service_to_date) {
        this.service_to_date = service_to_date;
    }

    public String getShortClaimNote() {
        return shortClaimNote;
    }

    public void setShortClaimNote(String shortClaimNote) {
        this.shortClaimNote = shortClaimNote;
    }

    public String getMessageNotes() {
        return messageNotes;
    }

    public void setMessageNotes(String messageNotes) {
        this.messageNotes = messageNotes;
    }

    public String getMva_claim_code() {
        return mva_claim_code;
    }

    public void setMva_claim_code(String mva_claim_code) {
        this.mva_claim_code = mva_claim_code;
    }

    public String getFacilityNum() {
        return facilityNum;
    }

    public void setFacilityNum(String facilityNum) {
        this.facilityNum = facilityNum;
    }

    public String getFacilitySubNum() {
        return facilitySubNum;
    }

    public void setFacilitySubNum(String facilitySubNum) {
        this.facilitySubNum = facilitySubNum;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getXml_endtime_hr() {
        return xml_endtime_hr;
    }

    public void setXml_endtime_hr(String xml_endtime_hr) {
        this.xml_endtime_hr = xml_endtime_hr;
    }

    public String getXml_endtime_min() {
        return xml_endtime_min;
    }

    public void setXml_endtime_min(String xml_endtime_min) {
        this.xml_endtime_min = xml_endtime_min;
    }

    public String getXml_starttime_hr() {
        return xml_starttime_hr;
    }

    public void setXml_starttime_hr(String xml_starttime_hr) {
        this.xml_starttime_hr = xml_starttime_hr;
    }

    public String getXml_starttime_min() {
        return xml_starttime_min;
    }

    public void setXml_starttime_min(String xml_starttime_min) {
        this.xml_starttime_min = xml_starttime_min;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
