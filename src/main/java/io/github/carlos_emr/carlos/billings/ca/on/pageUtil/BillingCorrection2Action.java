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

package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONCorrectionViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingRAImpl;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.commn.service.BillingONService;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.HashSet;
import java.util.Set;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * @author mweston4
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class BillingCorrection2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private BillingONPaymentDao bPaymentDao = (BillingONPaymentDao) SpringUtils.getBean(BillingONPaymentDao.class);
    private BillingONCHeader1Dao bCh1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    private BillingONExtDao billExtDao = (BillingONExtDao) SpringUtils.getBean(BillingONExtDao.class);
    private final BillingPaymentTypeDao billingPaymentTypeDao = SpringUtils.getBean(BillingPaymentTypeDao.class);

    /*
     * TODO(transactional): wrap updateInvoice() (which calls updateBillingONCHeader1
     * + updateBillingItems + bCh1Dao.merge) in a transaction so a partial-save
     * failure rolls back the in-memory mutations on bCh1. Today, if merge()
     * throws after updateBillingONCHeader1 mutated header fields, the bCh1
     * may be detached/dirty for the next request in the same Hibernate
     * session — a clinical-data-integrity concern. Adding @Transactional to
     * this Struts-instantiated action does not take effect without registering
     * the action as a Spring bean (Struts+Spring objectFactory autowires
     * dependencies but does not apply AOP proxies). The clean fix is a
     * TransactionTemplate around the merge sequence; deferred to a focused
     * follow-up because correctness here requires a Hibernate session-state
     * audit beyond this round's scope. The throw-on-invalid-input fixes
     * (BillingValidationException) close the most acute exposure: input
     * validation now fails before any mutation begins.
     */
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, so
        // null-checking here keeps the log signal clean for real privilege denials.
        // Matches the pattern in BillingONView2Action / ViewBillingONReview2Action /
        // ViewBillingONStatus2Action / BillingShortcutPg1View2Action.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Build user-context view model up front and stash on the request so
        // every result that forwards to billingONCorrection.jsp (success,
        // closeReload, adminReload) sees a populated correctionModel. The
        // bill-record state machine in updateInvoice / add3rdPartyPayment
        // remains unchanged.
        request.setAttribute("correctionModel", buildModel(loggedInInfo));

        if ("add3rdPartyPayment".equals(request.getParameter("method"))) {
            return add3rdPartyPayment();
        }
        return updateInvoice();
    }

    public String add3rdPartyPayment() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Validate billing_no first — every other validation below assumes
        // a real BillingONCHeader1 row. Throwing on bad input surfaces a
        // specific "submission rejected" page (via the package-level
        // BillingValidationException mapping) instead of the legacy
        // "closeReload" silent-success — operator no longer believes the
        // payment posted when it didn't.
        String invoiceNo = request.getParameter("billing_no");
        Integer invoiceId;
        try {
            invoiceId = Integer.valueOf(invoiceNo);
        } catch (NumberFormatException nfe) {
            String sanitized = LogSanitizer.sanitize(invoiceNo);
            MiscUtils.getLogger().error("3rd party payment: invalid billing_no '{}'", sanitized);
            throw new BillingValidationException(
                    "3rd party payment rejected: invalid billing_no [" + sanitized + "]", nfe);
        }
        BillingONCHeader1 bCh1 = bCh1Dao.find(invoiceId);

        if (bCh1 == null) {
            String sanitized = LogSanitizer.sanitize(invoiceNo);
            MiscUtils.getLogger().error("3rd party payment: billing_no '{}' not found", sanitized);
            throw new BillingValidationException(
                    "3rd party payment rejected: bill not found [" + sanitized + "]");
        }

        //Validate pay amount
        BigDecimal paidAmt;
        try {
            String amtPaid = request.getParameter("amtPaid");
            paidAmt = new BigDecimal(amtPaid);
        } catch (NumberFormatException e) {
            String sanitized = LogSanitizer.sanitize(request.getParameter("amtPaid"));
            MiscUtils.getLogger().error(
                    "3rd party payment: amtPaid '{}' is not a valid number for bill {}",
                    sanitized, invoiceId, e);
            throw new BillingValidationException(
                    "3rd party payment rejected: amount [" + sanitized + "] is not a valid number", e);
        }

        //Validate pay Method
        String payMethod = request.getParameter("payMethod");
        BillingPaymentType paymentType = billingPaymentTypeDao.find(payMethod);
        if (paymentType == null) {
            String sanitized = LogSanitizer.sanitize(payMethod);
            MiscUtils.getLogger().error(
                    "3rd party payment: payMethod '{}' not in billing_payment_type for bill {}",
                    sanitized, invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-method [" + sanitized + "] is not configured");
        }

        //Validate pay type
        String payType = request.getParameter("payType");
        if (payType == null
                || (!payType.equals("P") /* Payment */
                        && !payType.equals("R") /* Refund */)) {
            String sanitized = LogSanitizer.sanitize(payType);
            MiscUtils.getLogger().error(
                    "3rd party payment: payType '{}' invalid for bill {} (must be P or R)",
                    sanitized, invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-type [" + sanitized + "] must be P (payment) or R (refund)");
        }

        //Add new payment amount to third party bill
        bPaymentDao.createPayment(bCh1, request.getLocale(), payType, paidAmt, payMethod, providerNo);

        return SUCCESS;
    }

    public String updateInvoice() {

        // The action funnels every request that isn't "add3rdPartyPayment"
        // through updateInvoice(), including the GET-load path that opens
        // the correction page in the first place. The GET path posts no
        // `xml_billing_no` (it uses `billing_no` only); legacy behavior was
        // to short-circuit through "closeReload" which renders the JSP.
        // Preserve that load path here by treating a null/missing
        // `xml_billing_no` as the load case. A *non-null but unparseable*
        // value indicates form tampering / browser auto-fill regression
        // and must throw — that's the silent-success failure mode round-3
        // closed.
        String rawBillingNo = request.getParameter("xml_billing_no");
        if (rawBillingNo == null || rawBillingNo.isEmpty()) {
            return "closeReload";
        }
        Integer billingNo;
        try {
            billingNo = Integer.parseInt(rawBillingNo);
        } catch (NumberFormatException e) {
            String sanitized = LogSanitizer.sanitize(rawBillingNo);
            MiscUtils.getLogger().error("updateInvoice: invalid xml_billing_no '{}'", sanitized, e);
            throw new BillingValidationException(
                    String.join("", "Bill change rejected: invalid bill identifier (", sanitized, ")"), e);
        }
        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);

        if (bCh1 == null) {
            String sanitized = LogSanitizer.sanitize(rawBillingNo);
            MiscUtils.getLogger().error("updateInvoice: bill {} not found", sanitized);
            throw new BillingValidationException(
                    String.join("", "Bill change rejected: bill (", sanitized, ") not found"));
        }

        if (!updateBillingONCHeader1(bCh1, request))
            return "failure";

        if (!bCh1.getBillingItems().isEmpty()) {
            updateBillingItems(bCh1, request);
            BillingONService billingONService = (BillingONService) SpringUtils.getBean(BillingONService.class);
            if (!billingONService.updateTotal(bCh1))
                return "failure";
        }

        bCh1Dao.merge(bCh1);

        String newStatus = request.getParameter("status").substring(0, 1);
        String oldStatus = bCh1.getStatus();

        //Add payment audit if bill has just been settled.
        if (newStatus.equals(BillingONCHeader1.SETTLED) && !oldStatus.equals(newStatus)) {
            BillingONPayment billPayment = new BillingONPayment();
            billPayment.setBillingOnCheader1(bCh1);
            billPayment.setPaymentDate(new java.util.Date());
            bPaymentDao.persist(billPayment);
        }

        //Update Bill To if changed.
        if (request.getParameter("billTo") != null) {
            BillingONExt billExt = billExtDao.getBillTo(bCh1);
            if (billExt != null) {
                billExt.setValue(request.getParameter("billTo"));

                billExtDao.merge(billExt);
            } else {
                billExt = new BillingONExt();
                billExt.setBillingNo(bCh1.getId());
                billExt.setDateTime(new Date());
                billExt.setDemographicNo(bCh1.getDemographicNo());
                billExt.setKeyVal("billTo");
                billExt.setPaymentId(Integer.valueOf(0));
                billExt.setStatus('1');
                billExt.setValue(request.getParameter("billTo"));

                billExtDao.persist(billExt);
            }
        }

        //Update Due Date if changed.
        if (request.getParameter("invoiceDueDate") != null) {

            BillingONExt billExt = billExtDao.getDueDate(bCh1);

            if (billExt != null) {
                billExt.setValue(request.getParameter("invoiceDueDate"));
                billExtDao.merge(billExt);
            } else {
                billExt = new BillingONExt();
                billExt.setBillingNo(bCh1.getId());
                billExt.setDateTime(new Date());
                billExt.setDemographicNo(bCh1.getDemographicNo());
                billExt.setKeyVal("dueDate");
                billExt.setPaymentId(Integer.valueOf(0));
                billExt.setStatus('1');
                billExt.setValue(request.getParameter("invoiceDueDate"));

                billExtDao.persist(billExt);
            }
        }

        //Update Use Bill To for Reprint if changed                    
        BillingONExt billExt = billExtDao.getUseBillTo(bCh1);
        if (billExt != null) {
            if (request.getParameter("overrideUseDemoContact") != null) {
                billExt.setValue(request.getParameter("overrideUseDemoContact"));
                billExt.setStatus('1');
                billExtDao.merge(billExt);
            } else {
                billExt.setStatus('0');
                billExtDao.merge(billExt);
            }
        } else if (request.getParameter("overrideUseDemoContact") != null) {
            billExt = new BillingONExt();
            billExt.setBillingNo(bCh1.getId());
            billExt.setDateTime(new Date());
            billExt.setDemographicNo(bCh1.getDemographicNo());
            billExt.setKeyVal("useBillTo");
            billExt.setPaymentId(Integer.valueOf(0));
            billExt.setValue(request.getParameter("overrideUseDemoContact"));
            billExt.setStatus('1');

            billExtDao.persist(billExt);
        }


        if (request.getParameter("submit").equals("Save&Correct Another")) {
            return "closeReload";
        } else if (request.getParameter("adminSubmit") != null) {
            return "adminReload";
        } else {
            return "submitClose";
        }


    }

    private boolean updateBillingONCHeader1(BillingONCHeader1 bCh1, HttpServletRequest request) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        Locale locale = request.getLocale();

        String status = request.getParameter("status").substring(0, 1);

        boolean statusChangedToSettled = status.equals("S") && !bCh1.getStatus().equals(status);

        String payProgram = "";

        if (status.equals("N"))
            payProgram = "NOT";
        else
            payProgram = request.getParameter("payProgram");

        boolean nowMohPayProgram = ("HCP".equals(payProgram) || "RMB".equals(payProgram) || "WCB".equals(payProgram));
        boolean was3rdPartyPayProgram = request.getParameter("oldStatus").equals("thirdParty");

        if (hasInvoiceChanged(bCh1, request)) {

            //Add Existing state of Invoice to Billing Repository
            BillingONRepoDao billRepoDao = (BillingONRepoDao) SpringUtils.getBean(BillingONRepoDao.class);
            billRepoDao.createBillingONCHeader1Entry(bCh1, locale);

            Date billingDate = null;
            try {
                billingDate = DateUtils.parseDate(request.getParameter("xml_appointment_date"), locale);
            } catch (java.text.ParseException e) {
                MiscUtils.getLogger().error("Invalid billing date:{}", LogSanitizer.sanitize(request.getParameter("xml_appointment_date")), e);
                return false;
            }

            Date visitDate = null;
            try {
                visitDate = DateUtils.parseDate(request.getParameter("xml_vdate"), locale);
            } catch (java.text.ParseException e) {
                MiscUtils.getLogger().warn("Could not parse visit date: {}", LogSanitizer.sanitize(request.getParameter("xml_vdate")), e);
            }

            String manualReview = "";

            if (request.getParameter("m_review") != null) {
                manualReview = "Y";
            }

            ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
            Provider provider = providerDao.getProvider(bCh1.getProviderNo());

            bCh1.setStatus(status);

            if (!(was3rdPartyPayProgram || nowMohPayProgram)) {
                /*
                 * from Ministry of Health Pay Program  to 3rd Party Pay Program
                 * so default payee to the first pay method in the list
                 */
                List<BillingPaymentType> paymentTypes = billingPaymentTypeDao.findAll();
                if (paymentTypes != null) {
                    bCh1.setPayee(String.valueOf(paymentTypes.get(0).getId()));
                }
            } else if (was3rdPartyPayProgram && nowMohPayProgram) {
                /*
                 * from 3rd Party Pay Program to Ministry of Health Pay Program
                 * so default payee to "P"
                 */
                bCh1.setPayee(BillingDataHlp.CLAIMHEADER1_PAYEE);
            }

            bCh1.setPayProgram(payProgram);
            bCh1.setRefNum(request.getParameter("rdohip"));
            bCh1.setVisitType(request.getParameter("visittype"));
            bCh1.setFaciltyNum(request.getParameter("clinic_ref_code"));
            bCh1.setManReview(manualReview);
            bCh1.setBillingDate(billingDate);
            if (visitDate != null)
                bCh1.setAdmissionDate(visitDate);
            bCh1.setProviderNo(request.getParameter("provider_no"));
            bCh1.setComment(request.getParameter("comment"));
            bCh1.setProviderOhipNo(provider.getOhipNo());
            bCh1.setProviderRmaNo(provider.getRmaNo());
            bCh1.setCreator(providerNo);
            bCh1.setClinic(request.getParameter("site"));
            bCh1.setProvince(request.getParameter("hc_type"));
            bCh1.setLocation(request.getParameter("xml_slicode"));

            if (!provider.getProviderNo().equals(request.getParameter("provider_no"))) {
                Provider newProvider = providerDao.getProvider(request.getParameter("provider_no"));
                if (newProvider != null) {
                    bCh1.setProviderOhipNo(newProvider.getOhipNo());
                } else {
                    MiscUtils.getLogger().warn("null providers! can't do the update ({})", LogSanitizer.sanitize(request.getParameter("provider_no"))); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
                }
            }
        }

        if (was3rdPartyPayProgram && nowMohPayProgram) {
            /*
             * If status has been changed from 3rd Party Pay Program to Ministry of Health Pay Program,
             * AND there has been 3rd party payments already received, refund an amount equal to the
             * total amount paid by the 3rd party.
             */
            List<BillingONPayment> paymentRecords = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1);
            BigDecimal payments = BillingONPaymentDao.calculatePaymentTotal(paymentRecords);
            BigDecimal refunds = BillingONPaymentDao.calculateRefundTotal(paymentRecords);
            BigDecimal reversedFunds = payments.subtract(refunds);

            int doReverse = reversedFunds.compareTo(new BigDecimal("0.00"));

            if (doReverse < 0) {
                // Race-on-concurrent-write: another payment landed between
                // the read at the top of this method and now. Surface the
                // bill id so ops can correlate with the parallel write.
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount owing on bill {} is negative ({}); cannot return payment to third party. Likely concurrent payment write.",
                        bCh1.getId(), reversedFunds);
                return false;
            }

            if (doReverse > 0) {
                bPaymentDao.createPayment(bCh1, locale, BillingONPayment.REFUND, reversedFunds, "", providerNo);
            }
        } else if (statusChangedToSettled && !nowMohPayProgram) {
            /*
             * If the invoice has just been settled for a 3rd party invoice,
             * Then any amount outstanding is now paid in full.
             */
            List<BillingONPayment> paymentRecords = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1);

            BigDecimal totalOwing = bCh1.getTotal();
            BigDecimal totalPaid = BillingONPaymentDao.calculatePaymentTotal(paymentRecords);
            BigDecimal totalRefund = BillingONPaymentDao.calculateRefundTotal(paymentRecords);
            BigDecimal amtOutstanding = totalOwing.subtract(totalPaid).add(totalRefund);

            int doSettlePayment = amtOutstanding.compareTo(new BigDecimal("0.00"));

            if (doSettlePayment < 0) {
                // Same race-on-concurrent-write surface as above.
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount-to-settle on bill {} already negative ({}); no additional third party payment required. Likely concurrent payment write.",
                        bCh1.getId(), amtOutstanding);
                return false;
            }

            if (doSettlePayment > 0) {
                bPaymentDao.createPayment(bCh1, locale, BillingONPayment.PAYMENT, amtOutstanding, "", providerNo);
            }
        }

        return true;
    }

    private void updateBillingItems(BillingONCHeader1 bCh1, HttpServletRequest request) {

        String dx = request.getParameter("xml_diagnostic_detail");

        if (dx.length() > 2) {
            dx = dx.substring(0, 3);
        }

        String serviceDateStr = request.getParameter("xml_appointment_date");

        // ServiceDate must be valid: it feeds into bService.getTerminationDate().before(serviceDate)
        // (line ~501) and bServiceDao.searchBillingCode(... serviceDate) (line ~493),
        // both of which NPE or behave undefined on null. The previous code
        // logged the failure but continued with serviceDate=null, which
        // poisoned the entire flow while the operator saw a successful submission.
        Date serviceDate;
        try {
            serviceDate = DateUtils.parseDate(serviceDateStr, request.getLocale());
        } catch (java.text.ParseException e) {
            String sanitizedDate = LogSanitizer.sanitize(serviceDateStr);
            MiscUtils.getLogger().error(
                    "Bill item save: unparseable xml_appointment_date={}; aborting",
                    sanitizedDate, e);
            throw new BillingValidationException(
                    "Bill item save aborted: unparseable xml_appointment_date [" + sanitizedDate + "]", e);
        }

        /*
         * Create list of billing items in current state
         */
        List<BillingONItem> bItemsCurrent = new ArrayList<BillingONItem>();

        for (int i = 0; i < BillingDataHlp.FIELD_MAX_SERVICE_NUM; i++) {
            String serviceCodeId = request.getParameter("servicecode" + i);
            if ((serviceCodeId != null) && (serviceCodeId.length() > 0)) { // == 5

                String itemStatus = "O";
                if (request.getParameter("itemStatus" + i) != null)
                    itemStatus = "S";

                //Determine Unit
                String unit = request.getParameter("billingunit" + i);
                MiscUtils.getLogger().info("({}) Unit Amount:{}", LogSanitizer.sanitize(serviceCodeId), LogSanitizer.sanitize(unit)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
                if (!unit.matches("\\d+")) {
                    // Clinical billing-amount bug: previously the operator
                    // believed they entered a different unit count while the
                    // bill silently rounded to 1. Log so the rewrite is
                    // visible — the form data is preserved for forensics.
                    MiscUtils.getLogger().warn(
                            "Bill item {}: non-numeric unit '{}' rewritten to '1'; preserving submission",
                            LogSanitizer.sanitize(serviceCodeId), LogSanitizer.sanitize(unit));
                    unit = "1";
                }
                BigDecimal unitAmt = new BigDecimal(unit);

                //Determine fee
                String fee = request.getParameter("billingamount" + i);
                if (fee == null || fee.isEmpty() || fee.trim().isEmpty()) {
                    BillingServiceDao bServiceDao = (BillingServiceDao) SpringUtils.getBean(BillingServiceDao.class);
                    BillingService bService = bServiceDao.searchBillingCode(serviceCodeId, "ON", serviceDate);

                    if (bService == null) {
                        bService = bServiceDao.searchPrivateBillingCode(serviceCodeId, serviceDate);
                    }
                    if (bService != null) {

                        if (bService.getTerminationDate().before(serviceDate)) {
                            fee = "defunct";
                        } else {
                            fee = bService.getValue();
                            BigDecimal feeAmt = new BigDecimal(fee);
                            feeAmt = feeAmt.multiply(unitAmt).setScale(2, BigDecimal.ROUND_HALF_UP);
                            fee = feeAmt.toPlainString();
                        }
                    }
                }

                BillingONItem bItem = new BillingONItem();
                bItem.setServiceCode(serviceCodeId);
                bItem.setServiceCount(unit);
                bItem.setFee(fee);
                bItem.setServiceDate(serviceDate);
                bItem.setDx(dx);
                bItem.setStatus(itemStatus);
                bItem.setCh1Id(bCh1.getId());
                bItem.setTranscId(bCh1.getTranscId());
                bItem.setRecId(BillingDataHlp.ITEM_REORDIDENTIFICATION);
                bItemsCurrent.add(bItem);
            }
        }

        List<BillingONItem> bItemsExisting = bCh1.getBillingItems();

        for (BillingONItem bItemCurrent : bItemsCurrent) {

            if (bItemsExisting.contains(bItemCurrent)) {

                // Update an existing billing items  that is now modified, not deleted.               

                int index = bItemsExisting.indexOf(bItemCurrent);
                BillingONItem bItemExisting = bItemsExisting.get(index);

                boolean statusChanged = false;
                if ((!bItemExisting.getStatus().equals("S") && bItemCurrent.getStatus().equals("S"))
                        || (bItemExisting.getStatus().equals("S") && !bItemCurrent.getStatus().equals("S"))) {
                    statusChanged = true;
                }

                String fee = bItemCurrent.getFee();
                String unit = bItemCurrent.getServiceCount();

                if (!bItemExisting.getServiceCount().equals(unit)
                        || !bItemExisting.getFee().equals(fee)
                        || !bItemExisting.getDx().equals(dx)
                        || (bItemExisting.getServiceDate().compareTo(serviceDate) != 0)
                        || statusChanged) {

                    BillingONRepoDao billRepoDao = (BillingONRepoDao) SpringUtils.getBean(BillingONRepoDao.class);
                    billRepoDao.createBillingONItemEntry(bItemExisting, request.getLocale());
                }


                if (!fee.equals("defunct") && !bItemExisting.getServiceCount().equals(unit)) {
                    BigDecimal feeAmt = new BigDecimal(fee);
                    BigDecimal unitAmt = new BigDecimal(unit);
                    feeAmt = feeAmt.multiply(unitAmt).setScale(2, BigDecimal.ROUND_HALF_UP);
                    fee = feeAmt.toPlainString();
                }

                bItemExisting.setServiceCount(unit);
                bItemExisting.setFee(fee);
                bItemExisting.setServiceDate(bItemCurrent.getServiceDate());
                bItemExisting.setDx(dx);
                bItemExisting.setStatus(bItemCurrent.getStatus());
            } else {
                // This is a new billing item that isn't already persisted.                
                bCh1.getBillingItems().add(bItemCurrent);
            }
        }

        // Update status on existing billing items now removed
        for (BillingONItem bItemExisting : bItemsExisting) {

            if (!bItemsCurrent.contains(bItemExisting)) {
                bItemExisting.setStatus("D");
            }
        }
    }

    private boolean hasInvoiceChanged(BillingONCHeader1 bCh1, HttpServletRequest request) {

        boolean isChanged = false;

        Locale locale = request.getLocale();

        // Use a sentinel that can never match a user-submitted value AND that
        // is distinguishable from legitimate "no change" output. The legacy
        // code used "Invalid Date" which collides with itself across both
        // fields and creates a phantom "user changed something" signal when
        // a parse failure leaves both at the sentinel — that triggers a
        // bCh1Dao.merge(...) below and writes an audit row for a change
        // that didn't happen.
        String admissionDateStr;
        String billingDateStr;

        try {
            admissionDateStr = DateUtils.formatDate(bCh1.getAdmissionDate(), locale);
            billingDateStr = DateUtils.formatDate(bCh1.getBillingDate(), locale);
        } catch (java.text.ParseException e) {
            MiscUtils.getLogger().error(
                    "Bill {} has unparseable admission/billing date; aborting change-detection to avoid phantom audit entry",
                    bCh1.getId(), e);
            // Fail fast back to the caller so the bill is left untouched and
            // the operator sees the validation-error path instead of a silent
            // phantom audit-row. Without this, the legacy "Invalid Date"
            // sentinel would mismatch every user input below and trigger an
            // unintended bCh1Dao.merge().
            throw new BillingValidationException(
                    "Bill " + bCh1.getId()
                            + " has unparseable admission/billing date; refusing to compare or persist");
        }

        String manualReview = request.getParameter("m_review");
        if (manualReview != null)
            manualReview = "Y";
        else
            manualReview = "";

        if (!bCh1.getStatus().equals(request.getParameter("status").substring(0, 1))
                || !bCh1.getPayProgram().equals(request.getParameter("payProgram"))
                || !bCh1.getRefNum().equals(request.getParameter("rdohip"))
                || !bCh1.getVisitType().equals(request.getParameter("visittype"))
                || !admissionDateStr.equals(request.getParameter("xml_vdate"))
                || !bCh1.getFaciltyNum().equals(request.getParameter("clinic_ref_code"))
                || !bCh1.getManReview().equals(manualReview)
                || !billingDateStr.equals(request.getParameter("xml_appointment_date"))
                || !bCh1.getComment().equals(request.getParameter("comment"))
                || !bCh1.getProviderNo().equals(request.getParameter("provider_no"))
                || !bCh1.getLocation().equals(request.getParameter("xml_slicode"))
                || !StringUtils.nullSafeEquals(bCh1.getClinic(), request.getParameter("site"))
                || !bCh1.getProvince().equals(request.getParameter("hc_type"))) {

            isChanged = true;
        }

        return isChanged;
    }

    /**
     * Builds the user-context view model (provider record, site/team-access
     * flags, multisite list) that the JSP top scriptlet currently constructs
     * inline via 5 DAO lookups. Bill-record-specific data stays in the
     * existing state-machine path; this only captures the pieces driven by
     * the logged-in user.
     */
    // Package-private for direct unit-testing without reflection.
    // BillingCorrection2ActionBuildModelUnitTest depends on this visibility.
    BillingONCorrectionViewModel buildModel(LoggedInInfo loggedInInfo) {
        String providerNo = loggedInInfo != null && loggedInInfo.getLoggedInProviderNo() != null
                ? loggedInInfo.getLoggedInProviderNo()
                : "";

        ProviderDao providerDaoLocal = SpringUtils.getBean(ProviderDao.class);
        Provider userProvider = providerNo.isEmpty() ? null : providerDaoLocal.getProvider(providerNo);

        boolean siteAccessPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);
        boolean teamAccessPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_team_access_privacy", "r", null);
        boolean teamBillingOnly = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_team_billing_only", "r", null);

        Set<String> providerAccessList = new HashSet<>();
        if (siteAccessPrivacy) {
            ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
            for (ProviderSite ps : providerSiteDao.findByProviderNo(providerNo)) {
                providerAccessList.add(ps.getId().getProviderNo());
            }
        }
        if (teamAccessPrivacy && userProvider != null) {
            for (Provider p : providerDaoLocal.getBillableProvidersOnTeam(userProvider)) {
                providerAccessList.add(p.getProviderNo());
            }
        }

        boolean multisites = IsPropertiesOn.isMultisitesEnable();
        List<String> mgrSites = new ArrayList<>();
        if (multisites) {
            SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
            for (Site s : siteDao.getActiveSitesByProviderNo(providerNo)) {
                mgrSites.add(s.getName());
            }
        }

        BillingONCorrectionViewModel.Builder builder = BillingONCorrectionViewModel.builder()
                .userProviderNo(providerNo)
                .userFirstName(userProvider != null ? userProvider.getFirstName() : "")
                .userLastName(userProvider != null ? userProvider.getLastName() : "")
                .siteAccessPrivacy(siteAccessPrivacy)
                .teamAccessPrivacy(teamAccessPrivacy)
                .teamBillingOnly(teamBillingOnly)
                .multisites(multisites)
                .providerAccessList(providerAccessList)
                .mgrSites(mgrSites);

        loadBillRecord(loggedInInfo, builder, providerAccessList, mgrSites,
                siteAccessPrivacy, teamAccessPrivacy, multisites);

        return builder.build();
    }

    /**
     * Loads the bill record (BillingONCHeader1) referenced by the {@code billing_no}
     * (or fallback {@code claim_no}) request param and populates the bill-record
     * fields on the view model. Mirrors what {@code billingONCorrection.jsp}
     * lines 399-526 used to do inside the JSP body.
     *
     * <p>If neither {@code billing_no} nor {@code claim_no} is present, the model
     * stays empty (corresponds to the legacy "form not loaded" path). If the
     * billing_no is present but doesn't resolve to a bill, the model surfaces
     * {@code billNoErr=true} which the JSP renders as "Invoice number does
     * not exist!".</p>
     *
     * <p>Multisite/team access is enforced here: a provider who lacks access
     * to the bill's clinic site or to the bill provider's team gets
     * {@code multiSiteProvider=false} and the patient fields stay empty (the
     * JSP shows an "access denied" alert).</p>
     */
    private void loadBillRecord(LoggedInInfo loggedInInfo,
                                BillingONCorrectionViewModel.Builder b,
                                Set<String> providerAccessList,
                                List<String> mgrSites,
                                boolean siteAccessPrivacy,
                                boolean teamAccessPrivacy,
                                boolean multisites) {
        String billNoParam = request.getParameter("billing_no");
        String claimNoParam = request.getParameter("claim_no");
        if (claimNoParam != null && claimNoParam.equals("null")) {
            claimNoParam = null;
        }

        String billNo = billNoParam == null ? "" : billNoParam.trim();
        String claimNo = claimNoParam == null ? "" : claimNoParam.trim();

        // claim_no fallback: resolve billing_no via RaDetailDao if billing_no missing
        if (billNo.isEmpty() && !claimNo.isEmpty()) {
            RaDetailDao raDetailDao = SpringUtils.getBean(RaDetailDao.class);
            List<RaDetail> raDetails = raDetailDao.getRaDetailByClaimNo(claimNo);
            if (!raDetails.isEmpty()) {
                billNo = String.valueOf(raDetails.get(0).getBillingNo());
            }
        }

        b.billingNo(billNo).claimNo(claimNo);

        if (billNo.isEmpty()) {
            return;
        }

        Integer billingNo;
        try {
            billingNo = Integer.parseInt(billNo);
        } catch (NumberFormatException nfe) {
            b.billNoErr(true);
            return;
        }

        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);
        if (bCh1 == null) {
            b.billNoErr(true);
            return;
        }

        b.billLoaded(true);

        String clinicSite = bCh1.getClinic() == null ? "" : bCh1.getClinic();
        b.clinicSite(clinicSite);

        // Multisite / team-billing access guard
        boolean multiSiteProvider = true;
        if ((siteAccessPrivacy || teamAccessPrivacy) && !providerAccessList.contains(bCh1.getProviderNo())) {
            multiSiteProvider = false;
        }
        if (multisites && !mgrSites.contains(clinicSite)) {
            multiSiteProvider = false;
        }
        b.multiSiteProvider(multiSiteProvider);
        b.manReview(bCh1.getManReview() == null ? "" : bCh1.getManReview());

        if (!multiSiteProvider) {
            // Access denied — leave patient fields empty; JSP shows alert.
            return;
        }

        Locale locale = request.getLocale();
        b.createTimestamp(DateUtils.formatDateTime(bCh1.getTimestamp(), locale));
        b.demoNo(bCh1.getDemographicNo() == null ? "" : bCh1.getDemographicNo().toString())
                .demoName(bCh1.getDemographicName() == null ? "" : bCh1.getDemographicName())
                .demoDob(bCh1.getDob() == null ? "" : bCh1.getDob());

        String demoSex = "";
        if (bCh1.getSex() != null) {
            demoSex = bCh1.getSex().equals("1") ? "M" : "F";
        }
        b.demoSex(demoSex);

        String hin = "";
        String demoRosterStatus = "";
        if (bCh1.getDemographicNo() != null) {
            try {
                Demographic sdemo = new DemographicData().getDemographic(loggedInInfo, bCh1.getDemographicNo().toString());
                if (sdemo != null) {
                    hin = (sdemo.getHin() == null ? "" : sdemo.getHin())
                            + (sdemo.getVer() == null ? "" : sdemo.getVer());
                    String dobYy = sdemo.getYearOfBirth() == null ? "" : sdemo.getYearOfBirth();
                    String dobMm = sdemo.getMonthOfBirth() == null ? "" : sdemo.getMonthOfBirth();
                    String dobDd = sdemo.getDateOfBirth() == null ? "" : sdemo.getDateOfBirth();
                    b.demoDob(dobYy + dobMm + dobDd);
                    if (sdemo.getSex() != null) {
                        b.demoSex(sdemo.getSex());
                    }
                    demoRosterStatus = sdemo.getRosterStatus() == null ? "" : sdemo.getRosterStatus();
                }
            } catch (RuntimeException e) {
                // Empty patient context renders without a banner today; surface
                // a flag so the JSP shows ops "demographic load failed" rather
                // than letting the operator act on the (empty) context as if
                // it were authoritative. Logged at ERROR because a failed
                // demographic load is data-integrity, not a transient fetch.
                b.demoLoadError(true);
                MiscUtils.getLogger().error(
                        "Demographic load failed for bill {} demoNo={}; rendering correction page with empty patient context",
                        billNo, bCh1.getDemographicNo(), e);
            }
        }
        b.hin(hin).demoRosterStatus(demoRosterStatus);

        b.billLocationNo(bCh1.getFaciltyNum() == null ? "" : bCh1.getFaciltyNum());
        b.billDate(DateUtils.formatDate(bCh1.getBillingDate(), locale));
        b.billProvider(bCh1.getProviderNo() == null ? "" : bCh1.getProviderNo());
        b.billStatus(bCh1.getStatus() == null ? "" : bCh1.getStatus());
        b.payProgram(bCh1.getPayProgram() == null ? "" : bCh1.getPayProgram());
        b.billTotal(bCh1.getTotal() == null ? "" : bCh1.getTotal().toPlainString());

        try {
            b.visitDate(DateUtils.formatDate(bCh1.getAdmissionDate(), locale));
        } catch (java.text.ParseException e) {
            b.visitDate("");
        }
        b.visitType(bCh1.getVisitType() == null ? "" : bCh1.getVisitType());
        b.sliCode(bCh1.getLocation() == null ? "" : bCh1.getLocation());
        b.hcType(bCh1.getProvince() == null ? "" : bCh1.getProvince());
        b.hcSex(bCh1.getSex() == null ? "" : bCh1.getSex());

        // Referral doctor from ohip number
        String rDoctorOhip = bCh1.getRefNum() == null ? "" : bCh1.getRefNum();
        b.referralDoctorOhip(rDoctorOhip);
        if (!rDoctorOhip.isEmpty()) {
            ProfessionalSpecialistDao psDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
            List<ProfessionalSpecialist> specialists = psDao.findByReferralNo(rDoctorOhip);
            if (specialists != null && !specialists.isEmpty()) {
                ProfessionalSpecialist sp = specialists.get(0);
                b.referralDoctor((sp.getLastName() == null ? "" : sp.getLastName())
                        + ", " + (sp.getFirstName() == null ? "" : sp.getFirstName()));
            }
        }

        b.comment(bCh1.getComment() == null ? "" : bCh1.getComment());

        // OHIP RA claim number — primary correlation key for ministry
        // remittance. Surface a flag so the JSP shows "RA lookup unavailable"
        // rather than silently rendering an empty claimNo. Log at ERROR so
        // a JdbcBillingRAImpl regression is visible to ops.
        try {
            JdbcBillingRAImpl raObj = new JdbcBillingRAImpl();
            String raClaim = raObj.getRAClaimNo4BillingNo(billNo);
            if (raClaim != null) {
                b.claimNo(raClaim);
            }
        } catch (RuntimeException e) {
            b.raLookupError(true);
            MiscUtils.getLogger().error(
                    "RA claim lookup failed for bill {}; correction page will show empty claimNo",
                    billNo, e);
        }
    }
}
