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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.util.DisplayInvoiceLogo2Action;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnThirdPartyInvoiceViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnInvoiceTotalsService;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyRecordService;

/**
 * Assembles {@link BillingOnThirdPartyInvoiceViewModel} for {@code billingON3rdInv.jsp},
 * the third-party invoice print/preview. Owns the 9 inline
 * {@code SpringUtils.getBean} lookups the JSP used to perform across its
 * scriptlet body, exposing structured records (line items with descriptions
 * resolved, pre-rendered bill-to / remit-to text blocks, computed totals)
 * instead of raw entities.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingOnThirdPartyInvoiceViewModelAssembler {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingONExtDao bExtDao;
    private final BillingServiceDao billingServiceDao;
    private final ClinicDAO clinicDao;
    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final SiteDao siteDao;
    private final BillingThirdPartyRecordService thirdPartyRecordService;

    public BillingOnThirdPartyInvoiceViewModelAssembler(BillingONCHeader1Dao bCh1Dao,
                                 BillingONExtDao bExtDao,
                                 BillingONPaymentDao bPaymentDao,
                                 BillingServiceDao billingServiceDao,
                                 ClinicDAO clinicDao,
                                 DemographicDao demographicDao,
                                 ProviderDao providerDao,
                                 SiteDao siteDao,
                                 BillingOnInvoiceTotalsService totalsService,
                                 BillingThirdPartyRecordService thirdPartyRecordService) {
        this.bCh1Dao = bCh1Dao;
        this.bExtDao = bExtDao;
        this.billingServiceDao = billingServiceDao;
        this.clinicDao = clinicDao;
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.siteDao = siteDao;
        this.thirdPartyRecordService = thirdPartyRecordService;
    }

    /**
     * Build the invoice view model for the given request.
     *
     * @param request in-flight request — provides the {@code billingNo}
     *                parameter and locale for date formatting
     * @return populated view model. {@link BillingOnThirdPartyInvoiceViewModel#isInvoiceLoaded()}
     *         is false when {@code billingNo} is missing or doesn't resolve;
     *         the JSP renders a stub header + "N/A" placeholders in that case.
     */
    public BillingOnThirdPartyInvoiceViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        Locale locale = request.getLocale();
        String invoiceNoStr = request.getParameter("billingNo");
        Integer invoiceNo = null;
        try {
            invoiceNo = Integer.parseInt(invoiceNoStr);
        } catch (NumberFormatException | NullPointerException e) {
            invoiceNoStr = "";
            MiscUtils.getLogger().warn("Invalid Invoice No.");
        }

        Properties propClinic = thirdPartyRecordService.getLocalClinicAddr();
        Properties prop3rdPart = thirdPartyRecordService.get3rdPartBillProp(invoiceNoStr);
        Properties prop3rdPayMethod = thirdPartyRecordService.get3rdPayMethod();

        CarlosProperties oscarProp = CarlosProperties.getInstance();
        boolean isMultisite = oscarProp.getBooleanProperty("multisites", "on");

        BillingOnThirdPartyInvoiceViewModel.Builder b = BillingOnThirdPartyInvoiceViewModel.builder()
                .invoiceNoStr(invoiceNoStr)
                .multisiteEnabled(isMultisite)
                .printDate(DateUtils.sumDate("yyyy-MM-dd HH:mm", "0"));

        BillingONCHeader1 bCh1 = invoiceNo == null ? null : bCh1Dao.find(invoiceNo);
        if (bCh1 == null) {
            populateClinicHeader(b, isMultisite, null, propClinic);
            b.invoiceLoaded(false);
            return b.build();
        }

        b.invoiceLoaded(true);

        populateClinicHeader(b, isMultisite, bCh1.getClinic(), propClinic);
        populatePatientHeader(b, bCh1);

        b.billingDateStr(DateUtils.formatDate(bCh1.getBillingDate(), locale))
                .invoiceRefNum(nullToEmpty(bCh1.getRefNum()))
                .invoiceComment(nullToEmpty(bCh1.getComment()));

        // Provider + payee
        Provider provider = providerDao.getProvider(bCh1.getProviderNo());
        String providerFormattedName = provider == null ? "" : provider.getFormattedName();
        String payee = oscarProp.getProperty("PAYEE", "").trim();
        b.providerFormattedName(providerFormattedName);
        b.payeeName(payee.isEmpty() ? providerFormattedName : payee);

        // Bill-to / remit-to
        Clinic clinic = clinicDao.getClinic();
        Demographic demo = bCh1.getDemographicNo() == null ? null
                : demographicDao.getDemographic(bCh1.getDemographicNo().toString());
        BillToBlocks blocks = composeBillToAndRemitTo(bCh1, demo, clinic, oscarProp, locale);
        b.billTo(blocks.billTo).remitTo(blocks.remitTo);
        b.patientHin(demo == null ? "" : nullToEmpty(demo.getHin()));

        // Due date
        if (oscarProp.hasProperty("invoice_due_date")) {
            b.dueDateEnabled(true);
            BillingONExt dueDateExt = bExtDao.getDueDate(bCh1);
            if (dueDateExt != null) {
                b.dueDateStr(nullToEmpty(dueDateExt.getValue()));
            } else {
                int numDays = Integer.parseInt(oscarProp.getProperty("invoice_due_date", "0"));
                b.dueDateStr(DateUtils.sumDate(bCh1.getBillingDate(), numDays, locale));
            }
        }

        // Payment method label
        List<BillingONExt> payMethodList = bExtDao.findByBillingNoAndKey(bCh1.getId(), "payMethod");
        if (!payMethodList.isEmpty() && !"".equals(payMethodList.get(0).getValue())) {
            b.paymentMethodLabel(nullToEmpty(bExtDao.getPayMethodDesc(payMethodList.get(0))));
        } else {
            // Fall back to the human-readable label keyed off the prop3rdPart
            // payMethod field — same lookup the legacy JSP performed.
            b.paymentMethodLabel(nullToEmpty(
                    prop3rdPayMethod.getProperty(prop3rdPart.getProperty("payMethod", ""), "")));
        }

        // Line items + service descriptions
        List<BillingONItem> billingItems = bCh1Dao.findActiveItems(bCh1.getId());
        List<BillingOnThirdPartyInvoiceViewModel.InvoiceItem> itemRows = new ArrayList<>();
        for (BillingONItem item : billingItems) {
            BillingService bs;
            if (item.getServiceCode() != null && item.getServiceCode().startsWith("_")) {
                bs = billingServiceDao.searchPrivateBillingCode(item.getServiceCode(), item.getServiceDate());
            } else {
                bs = billingServiceDao.searchBillingCode(item.getServiceCode(), "ON", item.getServiceDate());
            }
            String desc = bs == null ? "N/A" : nullToEmpty(bs.getDescription());
            itemRows.add(new BillingOnThirdPartyInvoiceViewModel.InvoiceItem(
                    String.valueOf(item.getId()),
                    desc,
                    nullToEmpty(item.getServiceCode()),
                    nullToEmpty(item.getServiceCount()),
                    nullToEmpty(item.getDx()),
                    nullToEmpty(item.getFee())));
        }
        b.invoiceItems(itemRows);

        // Totals — payment / discount / refund / credit come from the
        // 3rdPart Properties (an XML blob in BillingONExt); BalanceOwing is
        // re-computed from the same inputs so the displayed Balance line
        // matches the math the legacy JSP did inline (line 435).
        BigDecimal total = bCh1.getTotal() == null ? ZERO : bCh1.getTotal().setScale(2, RoundingMode.HALF_UP);
        UnreadableTracker tracker = new UnreadableTracker();
        BigDecimal payment = parseScale(prop3rdPart.getProperty("payment", "0.00"), tracker);
        BigDecimal discount = parseScale(prop3rdPart.getProperty("discount", "0.00"), tracker);
        BigDecimal refund = parseScale(prop3rdPart.getProperty("refund", "0.00"), tracker);
        BigDecimal credit = parseScale(prop3rdPart.getProperty("credit", "0.00"), tracker);
        BigDecimal balance = total.subtract(payment).subtract(discount).add(credit);

        b.totalAmount(total.toPlainString())
                .paymentAmount(payment.toPlainString())
                .discountAmount(discount.toPlainString())
                .creditAmount(credit.toPlainString())
                .refundAmount(refund.toPlainString())
                .balanceAmount(balance.toPlainString())
                .amountsUnreadable(tracker.isUnreadable());

        // Logo: only used when not in multisite mode.
        if (!isMultisite) {
            String filePath = DisplayInvoiceLogo2Action.getLogoImgAbsPath();
            b.clinicLogoImgExists(filePath != null && !filePath.isEmpty());
        }

        return b.build();
    }

    private void populateClinicHeader(BillingOnThirdPartyInvoiceViewModel.Builder b,
                                      boolean isMultisite,
                                      String siteName,
                                      Properties propClinic) {
        if (isMultisite && siteName != null && !siteName.isEmpty()) {
            Site site = siteDao.findByName(siteName);
            if (site != null) {
                boolean hasLogo = site.getSiteLogoId() != null && site.getSiteLogoId() > 0;
                b.siteLogoAvailable(hasLogo)
                        .siteLogoId(hasLogo ? site.getSiteLogoId() : null)
                        .siteName(nullToEmpty(site.getName()))
                        .siteAddress(nullToEmpty(site.getAddress()))
                        .siteCity(nullToEmpty(site.getCity()))
                        .siteProvince(nullToEmpty(site.getProvince()))
                        .sitePostal(nullToEmpty(site.getPostal()))
                        .sitePhone(nullToEmpty(site.getPhone()));
                return;
            }
        }
        // Non-multisite path or missing site — fall back to clinic_*
        // properties pre-loaded by BillingThirdPartyRecordService.getLocalClinicAddr.
        b.clinicName(propClinic.getProperty("clinic_name", ""))
                .clinicAddress(propClinic.getProperty("clinic_address", ""))
                .clinicCity(propClinic.getProperty("clinic_city", ""))
                .clinicProvince(propClinic.getProperty("clinic_province", ""))
                .clinicPostal(propClinic.getProperty("clinic_postal", ""))
                .clinicPhone(propClinic.getProperty("clinic_phone", ""));
    }

    private void populatePatientHeader(BillingOnThirdPartyInvoiceViewModel.Builder b, BillingONCHeader1 bCh1) {
        String sex = bCh1.getSex();
        b.patientName(nullToEmpty(bCh1.getDemographicName()))
                .patientDemoNo(bCh1.getDemographicNo() == null ? "" : bCh1.getDemographicNo().toString())
                .patientGender("1".equals(sex) ? "Male" : "Female")
                .patientDob(nullToEmpty(bCh1.getDob()));
    }

    private static class BillToBlocks {
        final String billTo;
        final String remitTo;
        BillToBlocks(String billTo, String remitTo) { this.billTo = billTo; this.remitTo = remitTo; }
    }

    private BillToBlocks composeBillToAndRemitTo(BillingONCHeader1 bCh1, Demographic demo,
                                                 Clinic clinic, CarlosProperties props,
                                                 Locale locale) {
        BillingONExt billToExt = bExtDao.getBillTo(bCh1);
        String useDemoClinicInfoOnInvoice = props.getProperty("useDemoClinicInfoOnInvoice", "");

        if (!useDemoClinicInfoOnInvoice.isEmpty() && useDemoClinicInfoOnInvoice.equals("true")) {
            BillingONExt useBillToExt = bExtDao.getUseBillTo(bCh1);
            String billTo;
            // Stored 3rd-party Bill-To wins.
            if (billToExt != null && billToExt.getValue() != null && !billToExt.getValue().isEmpty()) {
                billTo = billToExt.getValue();
            } else if ((billToExt == null || billToExt.getValue() == null || billToExt.getValue().isEmpty())
                    && useBillToExt != null && "on".equals(useBillToExt.getValue())) {
                // User explicitly chose to leave Bill-To blank.
                billTo = "";
            } else if (demo != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(nullToEmpty(demo.getFirstName())).append(" ")
                        .append(nullToEmpty(demo.getLastName())).append("\n")
                        .append(nullToEmpty(demo.getAddress())).append("\n")
                        .append(nullToEmpty(demo.getCity())).append(",")
                        .append(nullToEmpty(demo.getProvince())).append("\n")
                        .append(nullToEmpty(demo.getPostal())).append("\n\n")
                        .append("\n\n\n\n\n")
                        .append(LocaleUtils.getMessage(locale, "billing.billing3rdInv.chartNo"))
                        .append(": ")
                        .append(nullToEmpty(demo.getChartNo()));
                billTo = sb.toString();
            } else {
                billTo = "";
            }

            String clinicBillingPhone = props.getProperty("clinic_billing_phone", "");
            if (clinicBillingPhone.isEmpty()) {
                clinicBillingPhone = clinic == null ? "" : nullToEmpty(clinic.getClinicDelimPhone());
            }
            StringBuilder sb = new StringBuilder();
            if (clinic != null) {
                sb.append(nullToEmpty(clinic.getClinicName())).append("\n")
                        .append(nullToEmpty(clinic.getClinicAddress())).append("\n")
                        .append(nullToEmpty(clinic.getClinicCity())).append(",")
                        .append(nullToEmpty(clinic.getClinicProvince())).append("\n")
                        .append(nullToEmpty(clinic.getClinicPostal())).append("\n")
                        .append("Ph:").append(clinicBillingPhone).append("\n");
            }
            return new BillToBlocks(billTo, sb.toString());
        }

        String billTo = (billToExt == null || billToExt.getValue() == null) ? "" : billToExt.getValue();
        BillingONExt remitToExt = bExtDao.getRemitTo(bCh1);
        String remitTo = (remitToExt == null || remitToExt.getValue() == null) ? "" : remitToExt.getValue();
        return new BillToBlocks(billTo, remitTo);
    }

    private static BigDecimal parseScale(String s) {
        return parseScale(s, null);
    }

    /**
     * Variant that records a malformed-amount via the supplied tracker so
     * the caller can render an "AMOUNT UNREADABLE — DO NOT REMIT" banner.
     * A silent zero-coalesce would print a balance line below the malformed
     * amount, hiding the corruption from the operator.
     */
    private static BigDecimal parseScale(String s, UnreadableTracker tracker) {
        if (s == null || s.isEmpty()) return ZERO;
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("Could not parse 3rd-party invoice amount '{}'", s);
            if (tracker != null) tracker.markUnreadable();
            return ZERO;
        }
    }

    /** Mutable single-flag holder used to surface the per-row malformed-amount
     *  signal up to the assembler's view-model build phase. */
    private static final class UnreadableTracker {
        private boolean unreadable;
        void markUnreadable() { this.unreadable = true; }
        boolean isUnreadable() { return unreadable; }
    }

    /**
     * Today's date helper kept as a method so this assembler doesn't read
     * {@code new Date()} at instantiation time — tests can sub via the
     * package-private constructor without freezing the clock at a moment.
     */
    @SuppressWarnings("unused")
    private static Date now() { return new Date(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
