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
        content = nullToEmpty(content);
        billingNo = nullToEmpty(billingNo);
        hin = nullToEmpty(hin);
        dob = nullToEmpty(dob);
        visitType = nullToEmpty(visitType);
        visitDate = nullToEmpty(visitDate);
        status = nullToEmpty(status);
        clinicRefCode = nullToEmpty(clinicRefCode);
        providerNo = nullToEmpty(providerNo);
        billingDate = nullToEmpty(billingDate);
        updateDate = nullToEmpty(updateDate);
        total = nullToEmpty(total);
        demoName = nullToEmpty(demoName);
        demoAddress = nullToEmpty(demoAddress);
        demoProvince = nullToEmpty(demoProvince);
        demoCity = nullToEmpty(demoCity);
        demoPostal = nullToEmpty(demoPostal);
        demoSex = nullToEmpty(demoSex);
        referralDoctor = nullToEmpty(referralDoctor);
        referralDoctorOhip = nullToEmpty(referralDoctorOhip);
        hcType = nullToEmpty(hcType);
        manualReviewLabel = nullToEmpty(manualReviewLabel);
        referralCheckedLabel = nullToEmpty(referralCheckedLabel);
        rosterStatus = nullToEmpty(rosterStatus);
        diagCode = nullToEmpty(diagCode);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
