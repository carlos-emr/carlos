/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
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
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.web.BillingCorrection2Action;

/**
 * Business-logic service for the Ontario billing correction workflow,
 * extracted from the legacy action path now represented by
 * {@link BillingCorrection2Action} so the action remains a thin Struts gate.
 *
 * <p>Two public entry points map 1:1 to the two HTTP-level operations the
 * legacy action exposed:</p>
 * <ul>
 *   <li>{@link #updateInvoice(LoggedInInfo, HttpServletRequest)} — mutate
 *       a {@link BillingONCHeader1} and its items based on the form post,
 *       writing audit-trail snapshots to {@code billing_on_repo} before
 *       each header / item mutation.</li>
 *   <li>{@link #addThirdPartyPayment(LoggedInInfo, HttpServletRequest)} —
 *       record a single payment / refund against an existing bill.</li>
 * </ul>
 *
 * <p>Both throw {@link BillingValidationException} on input-validation
 * failures (the {@code BillingValidationException} → {@code
 * billingValidationError.jsp} path is wired via the package-level Struts
 * {@code <global-exception-mappings>}). Both return a Struts result string
 * that the calling action returns directly to the framework.</p>
 *
 * <p>This class deliberately does NOT enforce security — gates do that
 * in the action layer where {@link io.github.carlos_emr.carlos.managers
 * .SecurityInfoManager} is available. The service trusts that callers
 * have already verified privilege.</p>
 *
 * <p><strong>Transactional boundary:</strong> the class is annotated
 * {@code @Transactional} (see below), so {@link #updateInvoice} and the
 * {@link #addThirdPartyPayment} flow run inside a Spring-managed
 * transaction. {@link BillingValidationException} thrown from the parse
 * step rolls back any in-flight mutations cleanly. Manual
 * instantiation via {@code new} bypasses the proxy; always inject this
 * service through Spring.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingCorrectionService {

    private final BillingONPaymentDao bPaymentDao;
    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingONExtDao billExtDao;
    private final BillingPaymentTypeDao billingPaymentTypeDao;
    private final BillingONRepoDao billRepoDao;
    private final ProviderDao providerDao;
    private final BillingServiceDao billingServiceDao;

    /**
     * Constructor-injection ctor used by Spring. Public so Struts2's
     * {@code SpringObjectFactory} (and {@code SpringUtils.getBean})
     * can resolve and instantiate the bean.
     */
    public BillingCorrectionService(BillingONPaymentDao bPaymentDao,
                             BillingONCHeader1Dao bCh1Dao,
                             BillingONExtDao billExtDao,
                             BillingPaymentTypeDao billingPaymentTypeDao,
                             BillingONRepoDao billRepoDao,
                             ProviderDao providerDao,
                             BillingServiceDao billingServiceDao) {
        this.bPaymentDao = bPaymentDao;
        this.bCh1Dao = bCh1Dao;
        this.billExtDao = billExtDao;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
        this.billRepoDao = billRepoDao;
        this.providerDao = providerDao;
        this.billingServiceDao = billingServiceDao;
    }

    /**
     * Records a single 3rd-party payment or refund against an existing
     * bill. Validates {@code billing_no}, {@code amtPaid}, {@code payMethod},
     * and {@code payType} request parameters; throws
     * {@link BillingValidationException} on any rejection.
     *
     * @return Struts result string ({@code "success"})
     */
    public String addThirdPartyPayment(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Validate billing_no first — every other validation below assumes
        // a real BillingONCHeader1 row.
        String invoiceNo = request.getParameter("billing_no");
        Integer invoiceId;
        try {
            invoiceId = Integer.valueOf(invoiceNo);
        } catch (NumberFormatException nfe) {
            MiscUtils.getLogger().error("3rd party payment: invalid billing_no '{}'", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(invoiceNo));
            throw new BillingValidationException(
                    "3rd party payment rejected: invalid billing_no ["
                    + LogSafe.sanitizeForDisplay(invoiceNo) + "]", nfe);
        }
        BillingONCHeader1 bCh1 = bCh1Dao.findForUpdate(invoiceId);
        if (bCh1 == null) {
            MiscUtils.getLogger().error("3rd party payment: billing_no '{}' not found", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(invoiceNo));
            throw new BillingValidationException(
                    "3rd party payment rejected: bill not found ["
                    + LogSafe.sanitizeForDisplay(invoiceNo) + "]");
        }

        // Validate pay amount. amtPaid may be null (param missing) or empty;
        // both throw distinct exceptions from new BigDecimal(...) — NPE for
        // null, NumberFormatException for empty/non-numeric. Without the
        // explicit null/empty check the NPE path produced an uncaught 500.
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
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "3rd party payment: amtPaid '{}' is not a valid number for bill {}",
                    LogSafe.sanitize(amtPaid), invoiceId, e);
            throw new BillingValidationException(
                    "3rd party payment rejected: amount ["
                    + LogSafe.sanitizeForDisplay(amtPaid) + "] is not a valid number", e);
        }

        // Validate pay method
        String payMethod = request.getParameter("payMethod");
        BillingPaymentType paymentType = billingPaymentTypeDao.find(payMethod);
        if (paymentType == null) {
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "3rd party payment: payMethod '{}' not in billing_payment_type for bill {}",
                    LogSafe.sanitize(payMethod), invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-method ["
                    + LogSafe.sanitizeForDisplay(payMethod) + "] is not configured");
        }

        // Validate pay type
        String payType = request.getParameter("payType");
        if (payType == null
                || (!payType.equals("P") /* Payment */
                        && !payType.equals("R") /* Refund */)) {
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "3rd party payment: payType '{}' invalid for bill {} (must be P or R)",
                    LogSafe.sanitize(payType), invoiceId);
            throw new BillingValidationException(
                    "3rd party payment rejected: pay-type ["
                    + LogSafe.sanitizeForDisplay(payType) + "] must be P (payment) or R (refund)");
        }

        createPaymentAndMergeHeader(bCh1, request.getLocale(), payType, paidAmt, payMethod, providerNo);
        return "success";
    }

    /**
     * Mutates an invoice header and its items based on the form post.
     * Returns:
     * <ul>
     *   <li>{@code "loadOnly"} — GET-load (no {@code xml_billing_no} posted),
     *       caller renders the form view</li>
     *   <li>{@code "submitClose"} — successful save, default close behaviour</li>
     *   <li>{@code "closeReload"} — Save&amp;Correct Another submitted</li>
     *   <li>{@code "adminReload"} — admin-flow reload</li>
     *   <li>throws {@link BillingValidationException} on header-update or
     *       item-fee-parse failure — Spring's {@code @Transactional} proxy
     *       rolls back any dirty-flushed mutations the method made before
     *       the throw, leaving the persistent state untouched. Returning a
     *       failure string would commit those partial mutations.</li>
     * </ul>
     */
    public String updateInvoice(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        String rawBillingNo = request.getParameter("xml_billing_no");
        if (rawBillingNo == null || rawBillingNo.isEmpty()) {
            return "loadOnly";
        }
        Integer billingNo;
        try {
            billingNo = Integer.parseInt(rawBillingNo);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().error("updateInvoice: invalid xml_billing_no '{}'", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(rawBillingNo), e);
            throw new BillingValidationException(
                    "Bill change rejected: invalid bill identifier ("
                    + LogSafe.sanitizeForDisplay(rawBillingNo) + ")", e);
        }
        // findWithItems eagerly fetches the items collection — applyCorrection
        // walks them below to apply edits and recompute the total. The class
        // is @Transactional, so the items are also reachable lazily; the
        // eager fetch keeps this read path independent of fetch-mode drift.
        BillingONCHeader1 bCh1 = bCh1Dao.findWithItems(billingNo);
        if (bCh1 == null) {
            MiscUtils.getLogger().error("updateInvoice: bill {} not found", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(rawBillingNo));
            throw new BillingValidationException(
                    "Bill change rejected: bill ("
                    + LogSafe.sanitizeForDisplay(rawBillingNo) + ") not found");
        }

        if (!updateBillingONCHeader1(bCh1, loggedInInfo, request)) {
            // Throw rather than return "failure": the class is @Transactional
            // and Spring only rolls back on exceptions. A return string would
            // commit any dirty-flushed item mutations made before this point.
            throw newHeaderUpdateRejected(rawBillingNo);
        }

        if (!bCh1.getBillingItems().isEmpty()) {
            updateBillingItems(bCh1, request);
            // Recompute the header total from its own active items; refuse
            // the save if any fee fails to parse. The arithmetic lives on
            // the entity (rich-domain query) since it reads only the
            // entity's own state.
            java.util.Optional<java.math.BigDecimal> newTotal = bCh1.recomputeTotalFromItems();
            if (newTotal.isEmpty()) {
                // Same rationale as the header-update throw above: returning
                // "failure" here would commit the dirty-flushed item edits
                // (delete flags, fee changes, new items added) while leaving
                // the header total stale — an inconsistent persistent state.
                throw newItemFeeUnparseable();
            }
            java.math.BigDecimal current = bCh1.getTotal();
            if (current == null || current.compareTo(newTotal.get()) != 0) {
                bCh1.setTotal(newTotal.get());
            }
        }

        bCh1Dao.merge(bCh1);

        String newStatus = requireParam(request, "status").substring(0, 1);
        String oldStatus = bCh1.getStatus();

        // Add payment audit if bill has just been settled.
        if (newStatus.equals(BillingONCHeader1.SETTLED) && !oldStatus.equals(newStatus)) {
            BillingONPayment billPayment = new BillingONPayment();
            billPayment.setBillingOnCheader1(bCh1);
            billPayment.setPaymentDate(new java.util.Date());
            bPaymentDao.persist(billPayment);
        }

        // Update Bill To if changed.
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

        // Update Due Date if changed.
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

        // Update Use Bill To for Reprint if changed
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

    /**
     * Applies header-level changes to bCh1 (status, payProgram, dates,
     * provider, etc.) and writes an audit-trail snapshot to BillingONRepo
     * before mutating. Returns {@code false} if the helper detects a
     * non-fatal situation that should route the caller to the legacy
     * {@code "failure"} result; throws {@link BillingValidationException}
     * for input-validation failures.
     */
    private boolean updateBillingONCHeader1(BillingONCHeader1 bCh1, LoggedInInfo loggedInInfo, HttpServletRequest request) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        Locale locale = request.getLocale();

        String status = requireParam(request, "status").substring(0, 1);
        boolean statusChangedToSettled = status.equals("S") && !bCh1.getStatus().equals(status);

        String payProgram = "";
        if (status.equals("N"))
            payProgram = "NOT";
        else
            payProgram = request.getParameter("payProgram");

        boolean nowMohPayProgram = ("HCP".equals(payProgram) || "RMB".equals(payProgram) || "WCB".equals(payProgram));
        boolean was3rdPartyPayProgram = request.getParameter("oldStatus").equals("thirdParty");

        if (hasInvoiceChanged(bCh1, request)) {
            // Parse input dates BEFORE writing the audit-trail row so a BVE
            // on bad input leaves no orphan repo entry.
            Date billingDate;
            try {
                billingDate = DateUtils.parseDate(request.getParameter("xml_appointment_date"), locale);
            } catch (java.text.ParseException e) {
                String rawAppt = request.getParameter("xml_appointment_date");
                MiscUtils.getLogger().error("Invalid billing date: {}", LogSafe.sanitize(rawAppt), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                throw new BillingValidationException(
                        "Bill correction rejected: appointment date ["
                        + LogSafe.sanitizeForDisplay(rawAppt)
                        + "] is not in a recognised format", e);
            }

            Date visitDate;
            try {
                visitDate = DateUtils.parseDate(request.getParameter("xml_vdate"), locale);
            } catch (java.text.ParseException e) {
                String rawVisit = request.getParameter("xml_vdate");
                MiscUtils.getLogger().error("Invalid visit date: {}", LogSafe.sanitize(rawVisit), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                throw new BillingValidationException(
                        "Bill correction rejected: visit date ["
                        + LogSafe.sanitizeForDisplay(rawVisit)
                        + "] is not in a recognised format", e);
            }

            // Audit-trail snapshot of the BEFORE state.
            billRepoDao.createBillingONCHeader1Entry(bCh1, locale);

            String manualReview = "";
            if (request.getParameter("m_review") != null) {
                manualReview = "Y";
            }

            Provider provider = providerDao.getProvider(bCh1.getProviderNo());

            bCh1.setStatusStrict(status);

            if (!(was3rdPartyPayProgram || nowMohPayProgram)) {
                // From Ministry-of-Health → 3rd-party: default payee to first.
                List<BillingPaymentType> paymentTypes = billingPaymentTypeDao.findAll();
                if (paymentTypes != null) {
                    bCh1.setPayee(String.valueOf(paymentTypes.get(0).getId()));
                }
            } else if (was3rdPartyPayProgram && nowMohPayProgram) {
                // From 3rd-party → MOH: default payee to "P".
                bCh1.setPayee(BillingOnConstants.CLAIMHEADER1_PAYEE);
            }

            bCh1.setPayProgram(payProgram);
            bCh1.setRefNum(request.getParameter("rdohip"));
            bCh1.setVisitType(request.getParameter("visittype"));
            bCh1.setFaciltyNum(request.getParameter("clinic_ref_code"));
            bCh1.setManReview(manualReview);
            bCh1.setBillingDate(billingDate);
            // visitDate may be null when xml_vdate was blank (outpatient bills
            // legitimately have no admission date). DateUtils.parseDate("")
            // returns null rather than throwing, so the parse-or-throw branch
            // above does not fire for empty input. The setter accepts null
            // and stores it as the empty admission_date sentinel.
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
                    throw new BillingValidationException(
                            "Billing correction rejected: provider not found [provider_no="
                                    + LogSafe.sanitize(request.getParameter("provider_no")) + "]");
                }
            }
        }

        if (was3rdPartyPayProgram && nowMohPayProgram) {
            // Status changed 3rd-party → MOH AND there are 3rd-party payments
            // already received → refund the difference.
            List<BillingONPayment> paymentRecords = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1);
            BigDecimal payments = BillingONPaymentDao.calculatePaymentTotal(paymentRecords);
            BigDecimal refunds = BillingONPaymentDao.calculateRefundTotal(paymentRecords);
            BigDecimal reversedFunds = payments.subtract(refunds);
            int doReverse = reversedFunds.compareTo(new BigDecimal("0.00"));

            if (doReverse < 0) {
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount owing on bill {} is negative ({}); cannot return payment to third party. Likely concurrent payment write.",
                        bCh1.getId(), reversedFunds);
                throw new BillingValidationException(
                        "Bill " + bCh1.getId()
                        + " could not be updated — a concurrent payment landed while you were editing. "
                        + "Refresh the bill and retry.");
            }

            if (doReverse > 0) {
                createPaymentAndMergeHeader(bCh1, locale, BillingONPayment.REFUND, reversedFunds, "", providerNo);
            }
        } else if (statusChangedToSettled && !nowMohPayProgram) {
            // Invoice just settled for a 3rd-party invoice → any outstanding
            // amount is now paid in full.
            List<BillingONPayment> paymentRecords = bPaymentDao.find3rdPartyPayRecordsByBill(bCh1);
            BigDecimal totalOwing = bCh1.getTotal();
            BigDecimal totalPaid = BillingONPaymentDao.calculatePaymentTotal(paymentRecords);
            BigDecimal totalRefund = BillingONPaymentDao.calculateRefundTotal(paymentRecords);
            BigDecimal amtOutstanding = totalOwing.subtract(totalPaid).add(totalRefund);
            int doSettlePayment = amtOutstanding.compareTo(new BigDecimal("0.00"));

            if (doSettlePayment < 0) {
                MiscUtils.getLogger().warn(
                        "updateBillingONCHeader1: amount-to-settle on bill {} already negative ({}); no additional third party payment required. Likely concurrent payment write.",
                        bCh1.getId(), amtOutstanding);
                throw new BillingValidationException(
                        "Bill " + bCh1.getId()
                        + " could not be settled — a concurrent payment landed while you were editing. "
                        + "Refresh the bill and retry.");
            }

            if (doSettlePayment > 0) {
                createPaymentAndMergeHeader(bCh1, locale, BillingONPayment.PAYMENT, amtOutstanding, "", providerNo);
            }
        }

        return true;
    }

    private void createPaymentAndMergeHeader(BillingONCHeader1 bCh1, Locale locale, String payType,
            BigDecimal paidAmt, String payMethod, String providerNo) {
        bPaymentDao.createPayment(bCh1, locale, payType, paidAmt, payMethod, providerNo);
        bCh1Dao.merge(bCh1);
    }

    /**
     * Diff each billing item in the form post against the persisted ones,
     * audit-trail the originals, mutate / add / soft-delete as needed.
     */
    private void updateBillingItems(BillingONCHeader1 bCh1, HttpServletRequest request) {
        String dx = request.getParameter("xml_diagnostic_detail");
        if (dx.length() > 2) {
            dx = dx.substring(0, 3);
        }

        String serviceDateStr = request.getParameter("xml_appointment_date");
        // ServiceDate must be valid: it feeds into bService.getTerminationDate().before(serviceDate)
        // and bServiceDao.searchBillingCode(... serviceDate), both NPE on null.
        Date serviceDate;
        try {
            serviceDate = DateUtils.parseDate(serviceDateStr, request.getLocale());
        } catch (java.text.ParseException e) {
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "Bill item save: unparseable xml_appointment_date={}; aborting",
                    LogSafe.sanitize(serviceDateStr), e);
            throw new BillingValidationException(
                    "Bill item save aborted: unparseable xml_appointment_date ["
                    + LogSafe.sanitizeForDisplay(serviceDateStr) + "]", e);
        }

        // Build the current state from the form post.
        List<BillingONItem> bItemsCurrent = new ArrayList<BillingONItem>();
        for (int i = 0; i < BillingOnConstants.FIELD_MAX_SERVICE_NUM; i++) {
            String serviceCodeId = request.getParameter("servicecode" + i);
            if ((serviceCodeId != null) && (serviceCodeId.length() > 0)) {
                String itemStatus = "O";
                if (request.getParameter("itemStatus" + i) != null)
                    itemStatus = "S";

                String unit = request.getParameter("billingunit" + i);
                MiscUtils.getLogger().info("({}) Unit Amount:{}",
                        LogSafe.sanitize(serviceCodeId), LogSafe.sanitize(unit)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                if (!unit.matches("\\d+")) {
                    MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            "Bill item {}: non-numeric unit '{}' rewritten to '1'; preserving submission",
                            LogSafe.sanitize(serviceCodeId), LogSafe.sanitize(unit));
                    unit = "1";
                }
                BigDecimal unitAmt = new BigDecimal(unit);

                String fee = request.getParameter("billingamount" + i);
                if (fee == null || fee.isEmpty() || fee.trim().isEmpty()) {
                    BillingService bService = billingServiceDao.searchBillingCode(serviceCodeId, "ON", serviceDate);
                    if (bService == null) {
                        bService = billingServiceDao.searchPrivateBillingCode(serviceCodeId, serviceDate);
                    }
                    if (bService != null) {
                        if (bService.getTerminationDate().before(serviceDate)) {
                            fee = BillingONItem.DEFUNCT_FEE;
                        } else {
                            fee = bService.getValue();
                            BigDecimal feeAmt = new BigDecimal(fee);
                            feeAmt = feeAmt.multiply(unitAmt).setScale(2, RoundingMode.HALF_UP);
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
                bItem.setStatusStrict(itemStatus);
                bItem.setCh1Id(bCh1.getId());
                bItem.setTranscId(bCh1.getTranscId());
                bItem.setRecId(BillingOnConstants.ITEM_REORDIDENTIFICATION);
                bItemsCurrent.add(bItem);
            }
        }

        // Snapshot the existing items into a fresh list. The getter returns
        // Collections.unmodifiableList(billingItems) over the live JPA-managed
        // collection; the loop below calls bCh1.addBillingItem(...) which
        // mutates the same backing list, which would CME the unmodifiable
        // view's iterator (or the contains/indexOf checks once the list grows).
        List<BillingONItem> bItemsExisting = new ArrayList<>(bCh1.getBillingItems());
        for (BillingONItem bItemCurrent : bItemsCurrent) {
            if (bItemsExisting.contains(bItemCurrent)) {
                // Update an existing billing item that is now modified.
                int index = bItemsExisting.indexOf(bItemCurrent);
                BillingONItem bItemExisting = bItemsExisting.get(index);

                // Status crossed the SETTLED boundary in either direction.
                boolean statusChanged =
                        (!BillingONItem.SETTLED.equals(bItemExisting.getStatus()) && BillingONItem.SETTLED.equals(bItemCurrent.getStatus()))
                        || (BillingONItem.SETTLED.equals(bItemExisting.getStatus()) && !BillingONItem.SETTLED.equals(bItemCurrent.getStatus()));

                String fee = bItemCurrent.getFee();
                String unit = bItemCurrent.getServiceCount();

                if (!bItemExisting.getServiceCount().equals(unit)
                        || !bItemExisting.getFee().equals(fee)
                        || !bItemExisting.getDx().equals(dx)
                        || (bItemExisting.getServiceDate().compareTo(serviceDate) != 0)
                        || statusChanged) {
                    billRepoDao.createBillingONItemEntry(bItemExisting, request.getLocale());
                }

                if (!BillingONItem.DEFUNCT_FEE.equals(fee) && !bItemExisting.getServiceCount().equals(unit)) {
                    BigDecimal feeAmt = new BigDecimal(fee);
                    BigDecimal unitAmt = new BigDecimal(unit);
                    feeAmt = feeAmt.multiply(unitAmt).setScale(2, RoundingMode.HALF_UP);
                    fee = feeAmt.toPlainString();
                }

                bItemExisting.setServiceCount(unit);
                bItemExisting.setFee(fee);
                bItemExisting.setServiceDate(bItemCurrent.getServiceDate());
                bItemExisting.setDx(dx);
                bItemExisting.setStatusStrict(bItemCurrent.getStatus());
            } else {
                // New billing item — append.
                bCh1.addBillingItem(bItemCurrent);
            }
        }

        // Soft-delete every existing item that the operator's edited list
        // no longer contains — these are the rows the user removed in the
        // form. markDeleted() flips status, leaving the row in place for
        // audit so a recovery rerun can resurrect it.
        for (BillingONItem bItemExisting : bItemsExisting) {
            if (!bItemsCurrent.contains(bItemExisting)) {
                bItemExisting.markDeleted();
            }
        }
    }

    /**
     * Header-change detection. Compares the persisted bCh1 against the form
     * post field by field. Throws {@link BillingValidationException} if the
     * stored admission/billing date itself is unparseable (data corruption
     * — the operator needs to know rather than getting a phantom audit
     * entry from sentinel-string mismatches).
     */
    private boolean hasInvoiceChanged(BillingONCHeader1 bCh1, HttpServletRequest request) {
        Locale locale = request.getLocale();

        String admissionDateStr;
        String billingDateStr;
        try {
            admissionDateStr = DateUtils.formatDate(bCh1.getAdmissionDate(), locale);
            billingDateStr = DateUtils.formatDate(bCh1.getBillingDate(), locale);
        } catch (java.text.ParseException e) {
            MiscUtils.getLogger().error(
                    "Bill {} has unparseable admission/billing date; aborting change-detection to avoid phantom audit entry",
                    bCh1.getId(), e);
            throw new BillingValidationException(
                    "Bill " + bCh1.getId()
                            + " has unparseable admission/billing date; refusing to compare or persist", e);
        }

        String manualReview = request.getParameter("m_review") != null ? "Y" : "";
        String status = requireParam(request, "status").substring(0, 1);

        return !bCh1.getStatus().equals(status)
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
                || !bCh1.getProvince().equals(request.getParameter("hc_type"));
    }

    private static BillingValidationException newHeaderUpdateRejected(String rawBillingNo) {
        // Log with sanitized id for audit; user message is plain to avoid
        // round-tripping unsanitized data through the rendered error page.
        MiscUtils.getLogger().error("updateInvoice header rejected for bill {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                LogSafe.sanitize(rawBillingNo));
        return new BillingValidationException(
                "Bill change rejected: header update failed; please refresh and retry.");
    }

    private static String requireParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isEmpty()) {
            throw new BillingValidationException("Bill correction rejected: missing required field [" + name + "]");
        }
        return value;
    }

    private static BillingValidationException newItemFeeUnparseable() {
        return new BillingValidationException(
                "Bill change rejected: one or more item fees could not be parsed; refresh and retry.");
    }
}
