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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingMultisiteContext;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnCorrectionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONErrorCodeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONErrorCode;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyRecordService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Composer that owns the 11 inline DAO/service lookups
 * {@code billingONCorrection.jsp} used to perform via
 * {@link io.github.carlos_emr.carlos.utility.SpringUtils#getBean} in scriptlet
 * blocks at lines 132-1095. The composer assembles structured records onto
 * {@link BillingOnCorrectionViewModel.Builder} so the JSP only has to iterate
 * pre-resolved DTOs.
 *
 * <p>Lookups consolidated here:</p>
 * <ul>
 *   <li>{@link BillingServiceDao} — service-code descriptions per bill item</li>
 *   <li>active items filtered from the loaded {@code bCh1}'s billingItems collection</li>
 *   <li>{@link BillingONEAReportDao#getBillingErrorList} +
 *       {@link RaDetailDao#getBillingExplanatoryList} +
 *       {@link BillingONErrorCodeDao#find} — combined RA error-code report</li>
 *   <li>{@link BillingONPaymentDao#find3rdPartyPaymentsByBillingNo} —
 *       third-party payment totals (payment / credit / discount / refund)</li>
 *   <li>{@link BillingONExtDao#getDueDate} / {@link BillingONExtDao#getUseBillTo}
 *       — invoice due date + use-demo-contact flags</li>
 *   <li>{@link ClinicLocationDao#findByClinicNo} — visit-location dropdown</li>
 *   <li>{@link ClinicNbrDao#findAll} — clinic-nbr dropdown (when RMA enabled)</li>
 *   <li>{@link SecurityInfoManager#hasPrivilege _billing w} — Save-button gate</li>
 * </ul>
 *
 * <p>Composer is instantiated by
 * {@link BillingOnCorrectionViewModelAssembler}; nothing returned — mutates the
 * supplied builder. Mirrors the structure already in place for
 * {@link BillingOnFormSiteContextComposer} on the main billing form.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnCorrectionRenderComposer {

    private final SecurityInfoManager securityInfoManager;
    private final BillingServiceDao billingServiceDao;
    private final BillingONExtDao bExtDao;
    private final BillingONPaymentDao billingONPaymentDao;
    private final BillingONEAReportDao billingONEAReportDao;
    private final BillingONErrorCodeDao billingONErrorCodeDao;
    private final RaDetailDao raDetailDao;
    private final ClinicLocationDao clinicLocationDao;
    private final ClinicNbrDao clinicNbrDao;
    private final BillingThirdPartyRecordService thirdPartyRecordService;

    public BillingOnCorrectionRenderComposer(SecurityInfoManager securityInfoManager,
                                             BillingServiceDao billingServiceDao,
                                             BillingONExtDao bExtDao,
                                             BillingONPaymentDao billingONPaymentDao,
                                             BillingONEAReportDao billingONEAReportDao,
                                             BillingONErrorCodeDao billingONErrorCodeDao,
                                             RaDetailDao raDetailDao,
                                             ClinicLocationDao clinicLocationDao,
                                             ClinicNbrDao clinicNbrDao,
                                             BillingThirdPartyRecordService thirdPartyRecordService) {
        this.securityInfoManager = securityInfoManager;
        this.billingServiceDao = billingServiceDao;
        this.bExtDao = bExtDao;
        this.billingONPaymentDao = billingONPaymentDao;
        this.billingONEAReportDao = billingONEAReportDao;
        this.billingONErrorCodeDao = billingONErrorCodeDao;
        this.raDetailDao = raDetailDao;
        this.clinicLocationDao = clinicLocationDao;
        this.clinicNbrDao = clinicNbrDao;
        this.thirdPartyRecordService = thirdPartyRecordService;
    }

    /**
     * Populate the render-context fields on the builder.
     *
     * @param b builder being assembled by {@link BillingOnCorrectionViewModelAssembler#assemble}
     * @param request the in-flight request — needed only for {@code request.getLocale()}
     * @param loggedInInfo session principal — needed for the Save-button privilege gate
     * @param bCh1 already-loaded bill record (or {@code null} when no bill is requested)
     * @param billNo same value the assembler set as {@code billingNo} on the builder
     * @param multiSiteProvider whether the access guard passed for this user/bill —
     *                          third-party totals must remain blank when access is denied
     * @param payProgram the bill's pay program, used to derive {@code thirdParty}
     */
    void compose(BillingOnCorrectionViewModel.Builder b,
                 HttpServletRequest request,
                 LoggedInInfo loggedInInfo,
                 BillingONCHeader1 bCh1,
                 String billNo,
                 boolean multiSiteProvider,
                 String payProgram) {
        boolean rmaEnabled = CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true");
        String clinicNo = CarlosProperties.getInstance().getProperty("clinic_no", "").trim();

        b.rmaEnabled(rmaEnabled).clinicNo(clinicNo);

        boolean canEditBilling = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null);
        b.canEditBilling(canEditBilling);

        b.clinicLocations(loadClinicLocations());
        if (rmaEnabled) {
            b.clinicNbrs(loadClinicNbrs());
        }

        // Third-party flag derived the same way the JSP did inline at line 491.
        // Scope: when no bill is loaded (billNo empty) the legacy code also
        // entered the first-party branch; preserved here.
        boolean thirdParty = bCh1 != null
                && !"HCP".equals(payProgram)
                && !"RMB".equals(payProgram)
                && !"WCB".equals(payProgram)
                && billNo != null && !billNo.isEmpty();
        b.thirdParty(thirdParty);

        composePaidAndPayer(b, bCh1, billNo, multiSiteProvider, thirdParty);
        composeDueDateAndUseDemoContact(b, request, bCh1, thirdParty);

        if (bCh1 != null) {
            b.billItems(loadBillItems(bCh1, multiSiteProvider));
        }

        b.errorReportEntries(loadErrorReportEntries(bCh1));
    }

    private List<BillingOnCorrectionViewModel.ClinicLocationEntry> loadClinicLocations() {
        List<BillingOnCorrectionViewModel.ClinicLocationEntry> out = new ArrayList<>();
        for (ClinicLocation cl : clinicLocationDao.findByClinicNo(1)) {
            out.add(new BillingOnCorrectionViewModel.ClinicLocationEntry(
                    nullToEmpty(cl.getClinicLocationNo()),
                    nullToEmpty(cl.getClinicLocationName())));
        }
        return out;
    }

    private List<BillingMultisiteContext.ClinicNbrEntry> loadClinicNbrs() {
        List<BillingMultisiteContext.ClinicNbrEntry> out = new ArrayList<>();
        for (ClinicNbr c : clinicNbrDao.findAll()) {
            String value = nullToEmpty(c.getNbrValue());
            String label = String.format("%s | %s", value, nullToEmpty(c.getNbrString()));
            out.add(new BillingMultisiteContext.ClinicNbrEntry(value, label));
        }
        return out;
    }

    private void composePaidAndPayer(BillingOnCorrectionViewModel.Builder b,
                                     BillingONCHeader1 bCh1,
                                     String billNo,
                                     boolean multiSiteProvider,
                                     boolean thirdParty) {
        String today = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");

        if (!thirdParty) {
            Properties tProp = null;
            if (billNo != null && !billNo.isEmpty()) {
                tProp = thirdPartyRecordService.get3rdPartBillPropInactive(billNo.trim());
            }
            if (tProp == null || tProp.isEmpty()) {
                b.paymentBlock(new BillingOnCorrectionViewModel.PaymentBlock(
                        true, false, "0.00", "0.00", today, "0.00", "", false));
                b.payer("");
            } else {
                String payment = tProp.getProperty("payment", "0.00");
                String refund = tProp.getProperty("refund", "");
                b.paymentBlock(new BillingOnCorrectionViewModel.PaymentBlock(
                        true, false, payment, payment, today, refund, "", false));
                String payer = tProp.getProperty("billTo");
                b.payer(payer == null ? "" : payer);
            }
            return;
        }

        Properties tProp = thirdPartyRecordService.get3rdPartBillProp(billNo.trim());
        String payer = tProp.getProperty("billTo");
        b.payer(payer == null ? "" : payer);

        if (!multiSiteProvider) {
            b.paymentBlock(BillingOnCorrectionViewModel.PaymentBlock.EMPTY);
            return;
        }

        BigDecimal payment = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;

        Integer billingNo = Integer.valueOf(billNo.trim());
        for (BillingONPayment bop : billingONPaymentDao.find3rdPartyPaymentsByBillingNo(billingNo)) {
            credit = credit.add(bop.getTotal_credit());
            discount = discount.add(bop.getTotal_discount());
            payment = payment.add(bop.getTotal_payment());
            refund = refund.add(bop.getTotal_refund());
        }

        BigDecimal total = bCh1.getTotal() == null ? BigDecimal.ZERO : bCh1.getTotal();
        BigDecimal balance = total.subtract(payment).subtract(discount).add(credit);
        payment = payment.subtract(credit);

        // Currency.US matches the legacy formatter — invoices render in
        // CAD-as-USD format throughout the ON billing module.
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        // refund is computed for parity with the legacy code; the third-party
        // display block doesn't surface it inline (it appears in the payments-list popup).
        if (refund.signum() != 0) {
            // no-op — preserved for legacy parity; refund tally surfaced via payments list link
        }
        b.paymentBlock(new BillingOnCorrectionViewModel.PaymentBlock(
                false, true, formatCurrency(currency, payment), "", "", "",
                formatCurrency(currency, balance), true));
    }

    private static String formatCurrency(NumberFormat currency, BigDecimal amount) {
        return (amount.signum() < 0 ? "-" : "") + currency.format(amount);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private void composeDueDateAndUseDemoContact(BillingOnCorrectionViewModel.Builder b,
                                                 HttpServletRequest request,
                                                 BillingONCHeader1 bCh1,
                                                 boolean thirdParty) {
        boolean dueDateAvailable = thirdParty
                && bCh1 != null
                && CarlosProperties.getInstance().hasProperty("invoice_due_date");
        b.dueDateAvailable(dueDateAvailable);

        if (dueDateAvailable) {
            BillingONExt bExtDueDate = bExtDao.getDueDate(bCh1);
            String dueDateStr;
            if (bExtDueDate == null) {
                int numDaysTilDue = Integer.parseInt(
                        CarlosProperties.getInstance().getProperty("invoice_due_date", "0"));
                dueDateStr = DateUtils.sumDate(bCh1.getBillingDate(), numDaysTilDue, request.getLocale());
            } else {
                dueDateStr = nullToEmpty(bExtDueDate.getValue());
            }
            b.dueDateString(dueDateStr);
        }

        String useDemoClinicInfoOnInvoice = CarlosProperties.getInstance()
                .getProperty("useDemoClinicInfoOnInvoice", "");
        boolean useDemoContactAvailable = bCh1 != null
                && !useDemoClinicInfoOnInvoice.isEmpty()
                && useDemoClinicInfoOnInvoice.equals("true");
        b.useDemoContactAvailable(useDemoContactAvailable);

        if (useDemoContactAvailable) {
            BillingONExt bExtUseBillTo = bExtDao.getUseBillTo(bCh1);
            boolean checked = bExtUseBillTo != null
                    && bExtUseBillTo.getValue() != null
                    && bExtUseBillTo.getValue().equalsIgnoreCase("on");
            b.useDemoContactChecked(checked);
        }
    }

    private List<BillingOnCorrectionViewModel.BillItemEntry> loadBillItems(BillingONCHeader1 bCh1,
                                                                          boolean multiSiteProvider) {
        // Multi-site denial: the JSP previously skipped per-row rendering with
        // `if (!isMultiSiteProvider) continue;` inside the row loop. Mirrored
        // here as a complete-list short-circuit since the model is consumed
        // directly in the view.
        if (!multiSiteProvider) {
            return List.of();
        }
        // The header is already loaded; filter its items in-memory rather
        // than round-tripping through the DAO. Same status check the
        // legacy service used.
        List<BillingONItem> items = bCh1.getBillingItems().stream()
                .filter(BillingONItem::isActive)
                .toList();
        List<BillingOnCorrectionViewModel.BillItemEntry> out = new ArrayList<>();
        for (BillingONItem item : items) {
            BillingService bService;
            if (item.getServiceCode() != null && item.getServiceCode().startsWith("_")) {
                bService = billingServiceDao.searchPrivateBillingCode(item.getServiceCode(), item.getServiceDate());
            } else {
                bService = billingServiceDao.searchBillingCode(item.getServiceCode(), "ON", item.getServiceDate());
            }
            String desc = bService == null ? "N/A" : nullToEmpty(bService.getDescription());
            String status = BillingONItem.SETTLED.equals(item.getStatus()) ? "checked" : "";
            out.add(new BillingOnCorrectionViewModel.BillItemEntry(
                    nullToEmpty(item.getServiceCode()),
                    desc,
                    nullToEmpty(item.getFee()),
                    nullToEmpty(item.getDx()),
                    nullToEmpty(item.getServiceCount()),
                    status));
        }
        return out;
    }

    private List<BillingOnCorrectionViewModel.ErrorReportEntry> loadErrorReportEntries(BillingONCHeader1 bCh1) {
        if (bCh1 == null) {
            return List.of();
        }
        Integer billingNo = bCh1.getId();
        List<String> codes = new ArrayList<>(raDetailDao.getBillingExplanatoryList(billingNo));
        codes.addAll(billingONEAReportDao.getBillingErrorList(billingNo));

        List<BillingOnCorrectionViewModel.ErrorReportEntry> out = new ArrayList<>();
        for (String code : codes) {
            if (code == null || code.isEmpty()) {
                continue;
            }
            BillingONErrorCode ec = billingONErrorCodeDao.find(code);
            String desc = ec == null || ec.getDescription() == null ? "Unknown" : ec.getDescription();
            out.add(new BillingOnCorrectionViewModel.ErrorReportEntry(code, desc));
        }
        return out;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
