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


package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sec.AuthenticationRejectionHandler;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPBillingNote;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingNote;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class BillingSaveBilling2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static Logger log = MiscUtils.getLogger();
    private AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    private OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);

    private BillingmasterDAO billingmasterDAO = SpringUtils.getBean(BillingmasterDAO.class);

    @Override
    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = authenticatedBillingUser(request);
        if (loggedInInfo == null) {
            rejectUnauthenticatedBillingSave(request, response);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingSessionBean bean = billingSessionBean(request);
        if (bean == null) {
            rejectExpiredBillingSession(request, response);
            return NONE;
        }

        bean.setCreator(loggedInInfo.getLoggedInProviderNo());

        MiscUtils.getLogger().debug("appointment_no---: " + bean.getApptNo());

        Date curDate = new Date();
        String billingid = "";
        ArrayList<String> billingIds = new ArrayList<String>();
        String dataCenterId = CarlosProperties.getInstance().getProperty("dataCenterId");
        String billingMasterId = "";

        normalizeAppointmentNo(bean);
        updateAppointmentStatus(bean);

        char billingAccountStatus = getBillingAccountStatus(bean);

        ArrayList<BillingBillingManager.BillingItem> billItem = bean.getBillItem();

        char paymentMode = (bean.getEncounter().equals("E") && !bean.getBillingType().equals("ICBC") && !bean.getBillingType().equals("Pri") && !bean.getBillingType().equals("WCB")) ? 'E' : '0';

        String billedAmount;

        for (BillingBillingManager.BillingItem bItem : billItem) {

            Billing billing = getBillingObj(bean, curDate, billingAccountStatus);
            if (request.getParameter("dispPrice+" + bItem.getServiceCode()) != null) {
                String updatedPrice = request.getParameter("dispPrice+" + bItem.getServiceCode());
                log.debug(bItem.getServiceCode() + "Original " + bItem.price + " updated price " + Double.parseDouble(updatedPrice));
                bItem.price = Double.parseDouble(updatedPrice);
                bItem.getLineTotal();
            }

            billingmasterDAO.save(billing);
            billingid = "" + billing.getId();

            billingIds.add(billingid);
            if (paymentMode == 'E') {
                billedAmount = "0.00";
            } else {
                billedAmount = bItem.getDispLineTotal();
            }

            Billingmaster billingmaster = saveBill(billingid, "" + billingAccountStatus, dataCenterId, billedAmount, "" + paymentMode, bean, bItem); //billItem.get(i));

            String WCBid = request.getParameter("WCBid");
            MiscUtils.getLogger().debug("WCB:" + WCBid);
            if (bean.getBillingType().equals("WCB")) {
                billingmaster.setWcbId(Integer.parseInt(bean.getWcbId()));
            }
            billingmasterDAO.save(billingmaster);
            billingMasterId = "" + billingmaster.getBillingmasterNo();
            this.createBillArchive(billingMasterId);

            //Changed March 8th to be included side this loop,  before only one billing would get this information.
            if (bean.getCorrespondenceCode().equals("N") || bean.getCorrespondenceCode().equals("B")) {

                MSPBillingNote n = new MSPBillingNote();
                n.addNote(billingMasterId, bean.getCreator(), bean.getNotes());

            }
            if (bean.getMessageNotes() != null && !bean.getMessageNotes().trim().equals("")) {
                BillingNote n = new BillingNote();
                n.addNote(billingMasterId, bean.getCreator(), bean.getMessageNotes());
            }
        }

        if (bean.getBillingType().equals("WCB")) {

            // HOW TO DO THIS PART
            /* Need to link the id of a WCB for with a bill
                -Continue to put it in the WCB form ?   + no data structure change - not sure how will it work.
                On submission how would this work??  for each bill submission that would look for it's id in the wcb table?
                The problem is that it's not really logical but it would work.  Not every form would have a billing.


                -Add a field to Billingmaster?   + data structure change + data migration + initial reaction
                Data conversion wouldn't be that big of a deal though.  because everything else would be coming over too.
                Most logical

                -Add a separate table ?       + data structure change + data migration + 2nd initial reacion

             */
            MiscUtils.getLogger().debug("WCB BILL!!");


        }


        if ("Another Bill".equals(submit)) {
            return "anotherBill";
        } else if ("Save & Print Receipt".equals(submit)) {
            String redirectUrl = receiptRedirectUrl(request.getContextPath(), billingIds);
            response.sendRedirect(redirectUrl);
            return NONE;
        }

        return SUCCESS;
    }

    private LoggedInInfo authenticatedBillingUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return null;
        }
        return LoggedInInfo.getLoggedInInfoFromSession(request);
    }

    private BillingSessionBean billingSessionBean(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object billingSessionAttr = session.getAttribute("billingSessionBean");
        return billingSessionAttr instanceof BillingSessionBean
                ? (BillingSessionBean) billingSessionAttr
                : null;
    }

    private void rejectExpiredBillingSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn("Rejected BC billing save because billingSessionBean is missing: method={}, uri={}, remote={}",
                LogSanitizer.sanitize(request.getMethod()),
                LogSanitizer.sanitize(request.getRequestURI()),
                LogSanitizer.sanitize(request.getRemoteAddr()));
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Billing session expired");
    }

    private void rejectUnauthenticatedBillingSave(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);
        } catch (IOException e) {
            log.warn("Unable to reject unauthenticated BC billing save request: method={}, uri={}, remote={}",
                    LogSanitizer.sanitize(request.getMethod()),
                    LogSanitizer.sanitize(request.getRequestURI()),
                    LogSanitizer.sanitize(request.getRemoteAddr()),
                    e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else {
                log.error("BC billing save rejection failed after response commit", e);
            }
        }
    }

    static String receiptRedirectUrl(String contextPath, Iterable<String> billingIds) {
        StringBuilder redirectUrl = new StringBuilder(contextPath == null ? "" : contextPath);
        redirectUrl.append("/billing/CA/BC/billingView?");
        for (String billingId : billingIds) {
            redirectUrl.append("billing_no=").append(billingId).append("&");
        }
        redirectUrl.append("receipt=yes");
        return redirectUrl.toString();
    }

    private void normalizeAppointmentNo(BillingSessionBean bean) {
        String rawApptNo = bean.getApptNo();
        String trimmedApptNo = rawApptNo == null ? "" : rawApptNo.trim();
        if (trimmedApptNo.isEmpty() || trimmedApptNo.equalsIgnoreCase("null")) {
            bean.setApptNo("0");
            return;
        }
        try {
            bean.setApptNo(String.valueOf(Integer.parseInt(trimmedApptNo)));
        } catch (NumberFormatException e) {
            log.warn("BC billing save ignored malformed appointment number: apptNo={}",
                    LogSanitizer.sanitize(rawApptNo), e);
            bean.setApptNo("0");
        }
    }

    private void updateAppointmentStatus(BillingSessionBean bean) {
        int apptNo = parseOptionalAppointmentNo(bean.getApptNo());
        if (apptNo == 0) {
            return;
        }

        Appointment appt = appointmentDao.find(apptNo);
        if (appt == null) {
            log.warn("BC billing save could not update appointment because apptNo={} was not found",
                    LogSanitizer.sanitize(bean.getApptNo()));
            return;
        }

        String billStatus = new ApptStatusData().billStatus(appt.getStatus());
        log.debug("appointment_no: " + bean.getApptNo());
        log.debug("BillStatus:" + billStatus);

        appointmentArchiveDao.archiveAppointment(appt);
        appt.setStatus(billStatus);
        appt.setLastUpdateUser(bean.getCreator());
        appointmentDao.merge(appt);
    }

    private Billing getBillingObj(final BillingSessionBean bean, final Date curDate, final char billingAccountStatus) {

        int apptNo = parseOptionalAppointmentNo(bean.getApptNo());


        Billing bill = new Billing();
        bill.setDemographicNo(Integer.parseInt(bean.getPatientNo()));
        bill.setProviderNo(bean.getBillingProvider());
        bill.setAppointmentNo(apptNo);
        bill.setDemographicName(bean.getPatientName());
        bill.setHin(bean.getPatientPHN());
        bill.setUpdateDate(curDate);
        bill.setBillingDate(MyDateFormat.getSysDate(bean.getServiceDate()));
        bill.setTotal(bean.getGrandtotal());
        bill.setStatus("" + billingAccountStatus);
        bill.setDob(bean.getPatientDoB());
        bill.setVisitDate(MyDateFormat.getSysDate(bean.getAdmissionDate()));
        bill.setVisitType(bean.getVisitType());
        bill.setProviderOhipNo(bean.getBillingPracNo());
        bill.setApptProviderNo(bean.getApptProviderNo());
        bill.setCreator(bean.getCreator());
        bill.setBillingtype(bean.getBillingType());
        return bill;
    }

    private char getBillingAccountStatus(BillingSessionBean bean) {
        char billingAccountStatus = 'O';
        if ("DONOTBILL".equals(bean.getBillingType())) {
            //bean.setBillingType("MSP"); //RESET this to MSP to get processed
            billingAccountStatus = 'N';
        } else if ("WCB".equals(bean.getBillingType())) {
            billingAccountStatus = 'O';
        } else if (MSPReconcile.BILLTYPE_PRI.equals(bean.getBillingType())) {
            billingAccountStatus = 'P';
        }
        return billingAccountStatus;
    }

    public String convertDate8Char(String s) {
        String sdate = "00000000", syear = "", smonth = "", sday = "";
        log.debug("s=" + s);
        if (s != null) {

            if (s.indexOf("-") != -1) {

                syear = s.substring(0, s.indexOf("-"));
                s = s.substring(s.indexOf("-") + 1);
                smonth = s.substring(0, s.indexOf("-"));
                if (smonth.length() == 1) {
                    smonth = "0" + smonth;
                }
                s = s.substring(s.indexOf("-") + 1);
                sday = s;
                if (sday.length() == 1) {
                    sday = "0" + sday;
                }

                log.debug("Year" + syear + " Month" + smonth + " Day" + sday);
                sdate = syear + smonth + sday;

            } else {
                sdate = s;
            }
            log.debug("sdate:" + sdate);
        } else {
            sdate = "00000000";

        }
        return sdate;
    }


    String moneyFormat(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "0.00";
        }
        try {
            return new java.math.BigDecimal(str.trim()).movePointLeft(2).toString();
        } catch (NumberFormatException moneyException) {
            throw new IllegalArgumentException(
                    "BC billing amount is malformed [" + LogSanitizer.sanitizeForDisplay(str) + "]",
                    moneyException);
        }
    }

    private static int parseOptionalAppointmentNo(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("BC billing save treated malformed appointment number as no appointment: apptNo={}",
                    LogSanitizer.sanitize(raw), e);
            return 0;
        }
    }

    /**
     * Adds a new entry into the billing_history table
     */
    private void createBillArchive(String billingMasterNo) {
        BillingHistoryDAO dao = new BillingHistoryDAO();
        dao.createBillingHistoryArchive(billingMasterNo);
    }

    private Billingmaster saveBill(String billingid, String billingAccountStatus, String dataCenterId, String billedAmount, String paymentMode, BillingSessionBean bean, BillingBillingManager.BillingItem billItem) {
        return saveBill(billingid, billingAccountStatus, dataCenterId, billedAmount, paymentMode, bean, "" + billItem.getUnit(), "" + billItem.getServiceCode());
    }

    private Billingmaster saveBill(String billingid, String billingAccountStatus, String dataCenterId, String billedAmount, String paymentMode, BillingSessionBean bean, String billingUnit, String serviceCode) {
        Billingmaster bill = new Billingmaster();

        String timeCall = bean.getTimeCall();
        String startTime = bean.getStartTime();
        String endTime = bean.getEndTime();

        if (timeCall != null && timeCall.contains(":")) {
            timeCall = timeCall.replace(":", "");
        }

        if (startTime != null && startTime.contains(":")) {
            startTime = startTime.replace(":", "");
        }

        if (endTime != null && endTime.contains(":")) {
            endTime = endTime.replace(":", "");
        }

        log.debug("Time Call: " + timeCall + " Start Time: " + startTime + " End Time: " + endTime);

        bill.setBillingNo(Integer.parseInt(billingid));
        bill.setCreatedate(new Date());
        bill.setBillingstatus(billingAccountStatus);
        bill.setDemographicNo(Integer.parseInt(bean.getPatientNo()));
        bill.setAppointmentNo(Integer.parseInt(bean.getApptNo()));
        bill.setClaimcode("C02");
        bill.setDatacenter(dataCenterId);
        bill.setPayeeNo(bean.getBillingGroupNo());
        bill.setPractitionerNo(bean.getBillingPracNo());
        bill.setPhn(bean.getPatientPHN());


        bill.setNameVerify(bean.getPatientFirstName(), bean.getPatientLastName());
        bill.setDependentNum(bean.getDependent());
        bill.setBillingUnit(billingUnit); //"" + billItem.getUnit());
        bill.setClarificationCode(bean.getVisitLocation().substring(0, 2));

        String anatomicalArea = "00";
        bill.setAnatomicalArea(anatomicalArea);
        bill.setAfterHour(bean.getAfterHours());
        String newProgram = "00";
        bill.setNewProgram(newProgram);
        bill.setBillingCode(serviceCode); //billItem.getServiceCode());
        bill.setBillAmount(billedAmount);
        bill.setPaymentMode(paymentMode);
        bill.setServiceDate(convertDate8Char(bean.getServiceDate())); //aka: xml_appointment_date
        bill.setServiceToDay(bean.getService_to_date());
        bill.setSubmissionCode(bean.getSubmissionCode());
        bill.setExtendedSubmissionCode(" ");
        bill.setDxCode1(bean.getDx1());
        bill.setDxCode2(bean.getDx2());
        bill.setDxCode3(bean.getDx3());
        bill.setDxExpansion(" ");

        bill.setServiceLocation(bean.getVisitType().substring(0, 1));
        bill.setReferralFlag1(bean.getReferType1());
        bill.setReferralNo1(bean.getReferral1());
        bill.setReferralFlag2(bean.getReferType2());
        bill.setReferralNo2(bean.getReferral2());

        bill.setTimeCall(timeCall);
        bill.setServiceStartTime(startTime);
        bill.setServiceEndTime(endTime);

        bill.setBirthDate(convertDate8Char(bean.getPatientDoB()));
        bill.setOfficeNumber("");
        bill.setCorrespondenceCode(bean.getCorrespondenceCode());
        bill.setClaimComment(bean.getShortClaimNote());
        bill.setMvaClaimCode(bean.getMva_claim_code());
        bill.setIcbcClaimNo(bean.getIcbc_claim_no());
        bill.setFacilityNo(bean.getFacilityNum());
        bill.setFacilitySubNo(bean.getFacilitySubNum());
        bill.setPaymentMethod(Integer.parseInt(bean.getPaymentType()));

        if (bean.getPatientHCType() != null && !bean.getPatientHCType().isEmpty() && !bean.getBillRegion().trim().equals(bean.getPatientHCType().trim())) {

            bill.setOinInsurerCode(bean.getPatientHCType());
            bill.setOinRegistrationNo(bean.getPatientPHN());
            bill.setOinBirthdate(convertDate8Char(bean.getPatientDoB()));
            bill.setOinFirstName(bean.getPatientFirstName());
            bill.setOinSecondName(" ");
            bill.setOinSurname(bean.getPatientLastName());
            bill.setOinSexCode(bean.getPatientSex());
            bill.setOinAddress(bean.getPatientAddress1());
            bill.setOinAddress2(bean.getPatientAddress2());
            bill.setOinAddress3("");
            bill.setOinAddress4("");
            bill.setOinPostalcode(bean.getPatientPostal());

            bill.setPhn("0000000000");
            bill.setNameVerify("0000");
            bill.setDependentNum("00");
            bill.setBirthDate("00000000");

        }
        log.debug("Bill " + bill.getBillingCode() + " " + bill.getBillAmount());
        return bill;
    }

    private String submit;
    private String[] billingIds;

    public String getSubmit() {
        return submit;
    }

    @StrutsParameter
    public void setSubmit(String submit) {
        this.submit = submit;
    }

    public String[] getBillingIds() {
        return billingIds;
    }

    @StrutsParameter
    public void setBillingIds(String[] billingIds) {
        this.billingIds = billingIds;
    }
}
