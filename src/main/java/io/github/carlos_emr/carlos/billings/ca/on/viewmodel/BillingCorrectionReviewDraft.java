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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.util.List;

/**
 * Prepared ON billing correction data before it is rendered for review.
 */
public record BillingCorrectionReviewDraft(boolean dataLoaded,
                                           String content,
                                           String billingNo,
                                           String hin,
                                           String dob,
                                           String visitType,
                                           String visitDate,
                                           String status,
                                           String clinicRefCode,
                                           String providerNo,
                                           String billingDate,
                                           String updateDate,
                                           String total,
                                           String demoName,
                                           String demoAddress,
                                           String demoProvince,
                                           String demoCity,
                                           String demoPostal,
                                           String demoSex,
                                           String referralDoctor,
                                           String referralDoctorOhip,
                                           String hcType,
                                           String manualReviewLabel,
                                           String referralCheckedLabel,
                                           String rosterStatus,
                                           String diagCode,
                                           List<BillingCorrectionReviewItemDraft> items) {
    public BillingCorrectionReviewDraft {
        content = BillingViewStrings.nullToEmpty(content);
        billingNo = BillingViewStrings.nullToEmpty(billingNo);
        hin = BillingViewStrings.nullToEmpty(hin);
        dob = BillingViewStrings.nullToEmpty(dob);
        visitType = BillingViewStrings.nullToEmpty(visitType);
        visitDate = BillingViewStrings.nullToEmpty(visitDate);
        status = BillingViewStrings.nullToEmpty(status);
        clinicRefCode = BillingViewStrings.nullToEmpty(clinicRefCode);
        providerNo = BillingViewStrings.nullToEmpty(providerNo);
        billingDate = BillingViewStrings.nullToEmpty(billingDate);
        updateDate = BillingViewStrings.nullToEmpty(updateDate);
        total = BillingViewStrings.nullToEmpty(total);
        demoName = BillingViewStrings.nullToEmpty(demoName);
        demoAddress = BillingViewStrings.nullToEmpty(demoAddress);
        demoProvince = BillingViewStrings.nullToEmpty(demoProvince);
        demoCity = BillingViewStrings.nullToEmpty(demoCity);
        demoPostal = BillingViewStrings.nullToEmpty(demoPostal);
        demoSex = BillingViewStrings.nullToEmpty(demoSex);
        referralDoctor = BillingViewStrings.nullToEmpty(referralDoctor);
        referralDoctorOhip = BillingViewStrings.nullToEmpty(referralDoctorOhip);
        hcType = BillingViewStrings.nullToEmpty(hcType);
        manualReviewLabel = BillingViewStrings.nullToEmpty(manualReviewLabel);
        referralCheckedLabel = BillingViewStrings.nullToEmpty(referralCheckedLabel);
        rosterStatus = BillingViewStrings.nullToEmpty(rosterStatus);
        diagCode = BillingViewStrings.nullToEmpty(diagCode);
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public String toString() {
        // Validation and error paths log the draft object for troubleshooting;
        // keep the operational shape visible without writing PHI into logs.
        return "BillingCorrectionReviewDraft["
                + "dataLoaded=" + dataLoaded + ", "
                + "content=" + content + ", "
                + "billingNo=" + billingNo + ", "
                + "hin=<redacted>, "
                + "dob=<redacted>, "
                + "visitType=" + visitType + ", "
                + "visitDate=" + visitDate + ", "
                + "status=" + status + ", "
                + "clinicRefCode=" + clinicRefCode + ", "
                + "providerNo=" + providerNo + ", "
                + "billingDate=" + billingDate + ", "
                + "updateDate=" + updateDate + ", "
                + "total=" + total + ", "
                + "demoName=<redacted>, "
                + "demoAddress=<redacted>, "
                + "demoProvince=<redacted>, "
                + "demoCity=<redacted>, "
                + "demoPostal=<redacted>, "
                + "demoSex=<redacted>, "
                + "referralDoctor=<redacted>, "
                + "referralDoctorOhip=<redacted>, "
                + "hcType=" + hcType + ", "
                + "manualReviewLabel=" + manualReviewLabel + ", "
                + "referralCheckedLabel=" + referralCheckedLabel + ", "
                + "rosterStatus=" + rosterStatus + ", "
                + "diagCode=" + diagCode + ", "
                + "items=" + items + "]";
    }
}
