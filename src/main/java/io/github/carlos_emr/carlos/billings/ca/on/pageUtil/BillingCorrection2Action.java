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
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.service.BillingONService;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


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
    // Dual-constructor DI: every dependency is final + injected. The no-arg
    // constructor below resolves them from Spring (the production path Struts
    // calls). Tests use the package-private constructor with mocks. This
    // confines SpringUtils.getBean to a single point and keeps the class
    // body free of static service-locator calls.
    private final SecurityInfoManager securityInfoManager;
    private final BillingONPaymentDao bPaymentDao;
    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingONExtDao billExtDao;
    private final BillingPaymentTypeDao billingPaymentTypeDao;
    private final BillingONService billingONService;
    private final BillingONRepoDao billRepoDao;
    private final ProviderDao providerDao;
    private final BillingServiceDao billingServiceDao;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Production constructor used by Struts2's Spring object factory.
     * Resolves every dependency via {@link SpringUtils#getBean} so this is
     * the only place service-locator calls live in the class.
     */
    public BillingCorrection2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             SpringUtils.getBean(BillingONPaymentDao.class),
             SpringUtils.getBean(BillingONCHeader1Dao.class),
             SpringUtils.getBean(BillingONExtDao.class),
             SpringUtils.getBean(BillingPaymentTypeDao.class),
             SpringUtils.getBean(BillingONService.class),
             SpringUtils.getBean(BillingONRepoDao.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(BillingServiceDao.class));
    }

    /**
     * Test-friendly constructor that bypasses SpringUtils — call directly
     * with mocks. Package-private to discourage external use; production
     * code should always go through the no-arg constructor.
     */
    BillingCorrection2Action(SecurityInfoManager securityInfoManager,
                             BillingONPaymentDao bPaymentDao,
                             BillingONCHeader1Dao bCh1Dao,
                             BillingONExtDao billExtDao,
                             BillingPaymentTypeDao billingPaymentTypeDao,
                             BillingONService billingONService,
                             BillingONRepoDao billRepoDao,
                             ProviderDao providerDao,
                             BillingServiceDao billingServiceDao) {
        this.securityInfoManager = securityInfoManager;
        this.bPaymentDao = bPaymentDao;
        this.bCh1Dao = bCh1Dao;
        this.billExtDao = billExtDao;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
        this.billingONService = billingONService;
        this.billRepoDao = billRepoDao;
        this.providerDao = providerDao;
        this.billingServiceDao = billingServiceDao;
    }

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
        // Matches the pattern in ViewBillingON2Action / ViewBillingONReview2Action /
        // ViewBillingONStatus2Action / ViewBillingShortcutPg12Action.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        // Build user-context + bill-record view model up front and stash on
        // the request so every result that forwards to billingONCorrection.jsp
        // (success, closeReload, adminReload) sees a populated correctionModel.
        // The mutation state machine in updateInvoice / add3rdPartyPayment
        // remains unchanged. The action delegates DAO orchestration to
        // BillingONCorrectionDataAssembler to keep the layering symmetric
        // with the four other refactored ON billing pages.
        request.setAttribute("correctionModel",
                new BillingONCorrectionDataAssembler().assemble(loggedInInfo, request));

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
            MiscUtils.getLogger().error("3rd party payment: invalid billing_no '{}'",
                    LogSanitizer.sanitize(invoiceNo));
            throw new BillingValidationException(
                    "3rd party payment rejected: invalid billing_no ["
                    + LogSanitizer.sanitizeForDisplay(invoiceNo) + "]", nfe);
        }
        BillingONCHeader1 bCh1 = bCh1Dao.find(invoiceId);

        if (bCh1 == null) {
            MiscUtils.getLogger().error("3rd party payment: billing_no '{}' not found",
                    LogSanitizer.sanitize(invoiceNo));
            throw new BillingValidationException(
                    "3rd party payment rejected: bill not found ["
                    + LogSanitizer.sanitizeForDisplay(invoiceNo) + "]");
        }

        //Validate pay amount
        // amtPaid may be null (param missing) or empty; both throw distinct
        // exceptions from new BigDecimal(...) — NPE for null, NumberFormatException
        // for empty/non-numeric. Without the explicit null/empty check the NPE
        // path produced an uncaught 500 instead of the validation page.
        String amtPaid = request.getParameter("amtPaid");
        if (amtPaid == null || amtPaid.isEmpty()) {
            MiscUtils.getLogger().error(
                    "3rd party payment: amtPaid is missing for bill {}", invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: amount is missing");
        }
        BigDecimal paidAmt;
        try {
            paidAmt = new BigDecimal(amtPaid);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error(
                    "3rd party payment: amtPaid '{}' is not a valid number for bill {}",
                    LogSanitizer.sanitize(amtPaid), invoiceId, e);
            throw new BillingValidationException(
                    "3rd party payment rejected: amount ["
                    + LogSanitizer.sanitizeForDisplay(amtPaid) + "] is not a valid number", e);
        }

        //Validate pay Method
        String payMethod = request.getParameter("payMethod");
        BillingPaymentType paymentType = billingPaymentTypeDao.find(payMethod);
        if (paymentType == null) {
            MiscUtils.getLogger().error(
                    "3rd party payment: payMethod '{}' not in billing_payment_type for bill {}",
                    LogSanitizer.sanitize(payMethod), invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-method ["
                    + LogSanitizer.sanitizeForDisplay(payMethod) + "] is not configured");
        }

        //Validate pay type
        String payType = request.getParameter("payType");
        if (payType == null
                || (!payType.equals("P") /* Payment */
                        && !payType.equals("R") /* Refund */)) {
            MiscUtils.getLogger().error(
                    "3rd party payment: payType '{}' invalid for bill {} (must be P or R)",
                    LogSanitizer.sanitize(payType), invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-type ["
                    + LogSanitizer.sanitizeForDisplay(payType) + "] must be P (payment) or R (refund)");
        }

        //Add new payment amount to third party bill
        bPaymentDao.createPayment(bCh1, request.getLocale(), payType, paidAmt, payMethod, providerNo);

        return SUCCESS;
    }

    public String updateInvoice() {

        // The action funnels every request that isn't "add3rdPartyPayment"
        // through updateInvoice(), including the GET-load path that opens
        // the correction page in the first place. The GET path posts no
        // `xml_billing_no` (it uses `billing_no` only); we render the JSP
        // through the dedicated `loadOnly` result so the result name
        // accurately reflects the path. `closeReload` (used below after
        // a successful save) is reserved for its actual semantic — a
        // post-save reload — to keep the result vocabulary honest. A
        // *non-null but unparseable* value indicates form tampering or
        // browser auto-fill regression and must throw, otherwise the
        // user gets a silent-success page instead of a rejection.
        String rawBillingNo = request.getParameter("xml_billing_no");
        if (rawBillingNo == null || rawBillingNo.isEmpty()) {
            return "loadOnly";
        }
        Integer billingNo;
        try {
            billingNo = Integer.parseInt(rawBillingNo);
        } catch (NumberFormatException e) {
            // Use sanitize() for the log line (defends against log-injection
            // via control chars in the input) but sanitizeForDisplay() for
            // the user-facing exception message (preserves printable Unicode
            // / quotes so the operator can read what they typed).
            MiscUtils.getLogger().error("updateInvoice: invalid xml_billing_no '{}'",
                    LogSanitizer.sanitize(rawBillingNo), e);
            throw new BillingValidationException(
                    "Bill change rejected: invalid bill identifier ("
                    + LogSanitizer.sanitizeForDisplay(rawBillingNo) + ")", e);
        }
        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);

        if (bCh1 == null) {
            MiscUtils.getLogger().error("updateInvoice: bill {} not found",
                    LogSanitizer.sanitize(rawBillingNo));
            throw new BillingValidationException(
                    "Bill change rejected: bill ("
                    + LogSanitizer.sanitizeForDisplay(rawBillingNo) + ") not found");
        }

        if (!updateBillingONCHeader1(bCh1, request))
            return "failure";

        if (!bCh1.getBillingItems().isEmpty()) {
            updateBillingItems(bCh1, request);
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

            // Parse input dates BEFORE writing the audit-trail row. The
            // legacy order wrote the BillingONRepo "before" snapshot first,
            // so a ParseException then orphaned an audit row that pointed
            // at a state-change that never happened. Parsing first means a
            // BVE on bad input leaves no trace beyond the validation log.
            Date billingDate;
            try {
                billingDate = DateUtils.parseDate(request.getParameter("xml_appointment_date"), locale);
            } catch (java.text.ParseException e) {
                String rawAppt = request.getParameter("xml_appointment_date");
                MiscUtils.getLogger().error("Invalid billing date: {}", LogSanitizer.sanitize(rawAppt), e);
                throw new BillingValidationException(
                        "Bill correction rejected: appointment date ["
                        + LogSanitizer.sanitizeForDisplay(rawAppt)
                        + "] is not in a recognised format", e);
            }

            Date visitDate;
            try {
                visitDate = DateUtils.parseDate(request.getParameter("xml_vdate"), locale);
            } catch (java.text.ParseException e) {
                // Throw rather than silently leave visitDate null: the
                // downstream `if (visitDate != null) bCh1.setAdmissionDate(visitDate)`
                // guard would otherwise save the bill with the OLD admission
                // date — exactly the "looked-saved-but-not" pattern this
                // refactor exists to eliminate.
                String rawVisit = request.getParameter("xml_vdate");
                MiscUtils.getLogger().error("Invalid visit date: {}", LogSanitizer.sanitize(rawVisit), e);
                throw new BillingValidationException(
                        "Bill correction rejected: visit date ["
                        + LogSanitizer.sanitizeForDisplay(rawVisit)
                        + "] is not in a recognised format", e);
            }

            //Add Existing state of Invoice to Billing Repository
            billRepoDao.createBillingONCHeader1Entry(bCh1, locale);

            String manualReview = "";

            if (request.getParameter("m_review") != null) {
                manualReview = "Y";
            }

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
            // visitDate is guaranteed non-null here — the parse-or-throw
            // BVE above ensures we never reach this assignment with a stale
            // admission_date. Removing the null-guard closes the silent
            // "saved-but-admission_date-untouched" path.
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
                // the read at the top of this method and now. Surface as
                // BVE so the operator gets the actionable validation page
                // (refresh + retry) instead of the generic failure.jsp stub.
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount owing on bill {} is negative ({}); cannot return payment to third party. Likely concurrent payment write.",
                        bCh1.getId(), reversedFunds);
                throw new BillingValidationException(
                        "Bill " + bCh1.getId()
                        + " could not be updated — a concurrent payment landed while you were editing. "
                        + "Refresh the bill and retry.");
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
                // Same race-on-concurrent-write surface as the refund branch
                // above — throw BVE for actionable user guidance.
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount-to-settle on bill {} already negative ({}); no additional third party payment required. Likely concurrent payment write.",
                        bCh1.getId(), amtOutstanding);
                throw new BillingValidationException(
                        "Bill " + bCh1.getId()
                        + " could not be settled — a concurrent payment landed while you were editing. "
                        + "Refresh the bill and retry.");
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
            MiscUtils.getLogger().error(
                    "Bill item save: unparseable xml_appointment_date={}; aborting",
                    LogSanitizer.sanitize(serviceDateStr), e);
            throw new BillingValidationException(
                    "Bill item save aborted: unparseable xml_appointment_date ["
                    + LogSanitizer.sanitizeForDisplay(serviceDateStr) + "]", e);
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
                    BillingService bService = billingServiceDao.searchBillingCode(serviceCodeId, "ON", serviceDate);

                    if (bService == null) {
                        bService = billingServiceDao.searchPrivateBillingCode(serviceCodeId, serviceDate);
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
            // unintended bCh1Dao.merge(). Chain `e` so the original parse
            // failure surfaces in stack traces / Sentry breadcrumbs.
            throw new BillingValidationException(
                    "Bill " + bCh1.getId()
                            + " has unparseable admission/billing date; refusing to compare or persist", e);
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

}
