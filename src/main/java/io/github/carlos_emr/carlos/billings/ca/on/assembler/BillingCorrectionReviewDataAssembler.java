/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.BillingBean;
import io.github.carlos_emr.BillingDataBean;
import io.github.carlos_emr.BillingItemBean;
import io.github.carlos_emr.BillingPatientDataBean;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCorrectionReviewViewModel;

/**
 * Assembles {@link BillingCorrectionReviewViewModel} for
 * {@code billing/CA/ON/billingCorrectionReview.jsp}, the read-only
 * "correction review" preview that shows pending changes before they are
 * POSTed to {@code BillingCorrectionSubmit2Action}.
 *
 * <p>Reads the same three session-scoped beans the legacy JSP scriptlet
 * pulled directly: {@code billing} ({@link BillingBean}), {@code billingDataBean}
 * ({@link BillingDataBean}), {@code billingPatientDataBean}
 * ({@link BillingPatientDataBean}). Returns an empty/stub model when any of
 * the three is missing &mdash; the legacy code wrapped its render in a
 * try/catch on {@code ArrayIndexOutOfBoundsException} to silently swallow
 * that case; this class makes the empty-state explicit via
 * {@link BillingCorrectionReviewViewModel#isDataLoaded()}.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingCorrectionReviewDataAssembler {

    /**
     * Build the review view model from the three session-scoped beans
     * populated by {@code billingCorrectionValid.jsp} on the Save flow.
     *
     * @param billing the session-scoped {@link BillingBean} (line items)
     * @param billingDataBean the session-scoped {@link BillingDataBean}
     *                        (header fields + raw {@code content} XML blob)
     * @param patient the session-scoped {@link BillingPatientDataBean}
     *                (demographic projection)
     * @return populated view model; {@link BillingCorrectionReviewViewModel#isDataLoaded()}
     *         is {@code false} when any input is null
     */
    public BillingCorrectionReviewViewModel assemble(BillingBean billing,
                                                     BillingDataBean billingDataBean,
                                                     BillingPatientDataBean patient) {
        BillingCorrectionReviewViewModel.Builder b = BillingCorrectionReviewViewModel.builder();
        if (billing == null || billingDataBean == null || patient == null) {
            return b.build();
        }

        b.dataLoaded(true);

        // Patient demographic block.
        b.demoName(patient.getDemoname())
                .demoAddress(patient.getAddress())
                .demoCity(patient.getCity())
                .demoProvince(patient.getProvince())
                .demoPostal(patient.getPostal())
                .demoSex(patient.getSex())
                .demoDob(billingDataBean.getDob())
                .hin(billingDataBean.getHin());

        // Header fields.
        b.billingNo(billingDataBean.getBilling_no())
                .billingType(billingDataBean.getStatus())
                .billingDate(billingDataBean.getBilling_date())
                .visitLocation(billingDataBean.getClinic_ref_code())
                .billingPhysicianNo(billingDataBean.getProviderNo())
                .visitType(billingDataBean.getVisittype())
                .visitDate(billingDataBean.getVisitdate())
                .updateDate(billingDataBean.getUpdate_date());

        // Fields that the legacy scriptlet extracted out of the freeform
        // {@code content} XML blob using SxmlMisc#getXmlContent.
        String content = billingDataBean.getContent();
        b.referralDoctor(SxmlMisc.getXmlContent(content, "<rd>", "</rd>"))
                .referralDoctorOhip(SxmlMisc.getXmlContent(content, "<rdohip>", "</rdohip>"))
                .hcType(SxmlMisc.getXmlContent(content, "<hctype>", "</hctype>"))
                .rosterStatus(SxmlMisc.getXmlContent(content, "<xml_roster>", "</xml_roster>"));

        // The original scriptlet rendered "Yes" when the inner XML value was
        // literally "checked"; otherwise "N/A". Mirrored exactly.
        b.manualReviewLabel(toYesOrNA(SxmlMisc.getXmlContent(content, "<mreview>", "</mreview>")))
                .referralCheckedLabel(toYesOrNA(SxmlMisc.getXmlContent(content, "<xml_referral>", "</xml_referral>")));

        // Line items + last-seen diag code (legacy scriptlet's _p0_17 was the
        // last billing item's diag_code, which is what the diag-code panel
        // shows below the table).
        List<BillingCorrectionReviewViewModel.Item> items = new ArrayList<>();
        String lastDiag = "";
        if (billing.getBillingItems() != null) {
            for (BillingItemBean bi : billing.getBillingItems()) {
                lastDiag = nullToEmpty(bi.getDiag_code());
                items.add(new BillingCorrectionReviewViewModel.Item(
                        bi.getService_code(),
                        bi.getDesc(),
                        bi.getQuantity(),
                        formatCents(bi.getService_value()),
                        lastDiag));
            }
        }
        b.billingItems(items).diagCode(lastDiag);

        // Total: stored as cents (no decimal); legacy scriptlet split off the
        // last two characters as the cents portion. Mirrored here.
        b.formattedTotal(formatCents(billingDataBean.getTotal()));

        return b.build();
    }

    /**
     * Format a "no-decimal cents" amount like {@code "2375"} as
     * {@code "23.75"}. Returns the input unchanged when too short to split
     * (matching the lenient legacy substring math at render time, but
     * without throwing on short inputs).
     */
    static String formatCents(String stored) {
        if (stored == null || stored.length() < 3) {
            return nullToEmpty(stored);
        }
        return stored.substring(0, stored.length() - 2) + "." + stored.substring(stored.length() - 2);
    }

    private static String toYesOrNA(String xmlVal) {
        return "checked".equals(xmlVal) ? "Yes" : "N/A";
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
