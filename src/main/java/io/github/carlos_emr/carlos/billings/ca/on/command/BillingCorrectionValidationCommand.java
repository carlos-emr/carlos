/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.command;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed input for preparing an ON billing correction review.
 */
public record BillingCorrectionValidationCommand(String diagnosticDetail,
                                                 String referralDoctor,
                                                 String rosterStatus,
                                                 boolean manualReview,
                                                 String referralDoctorOhip,
                                                 boolean referralChecked,
                                                 String hcType,
                                                 String hcSex,
                                                 String specialty,
                                                 Map<String, String> xmlParameters,
                                                 List<BillingCorrectionLineCommand> serviceLines,
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
                                                 String demoName,
                                                 String demoAddress,
                                                 String demoProvince,
                                                 String demoCity,
                                                 String demoPostal,
                                                 String demoSex) {
    public BillingCorrectionValidationCommand {
        diagnosticDetail = nullToEmpty(diagnosticDetail);
        referralDoctor = nullToEmpty(referralDoctor);
        rosterStatus = nullToEmpty(rosterStatus);
        referralDoctorOhip = nullToEmpty(referralDoctorOhip);
        hcType = nullToEmpty(hcType);
        hcSex = nullToEmpty(hcSex);
        specialty = nullToEmpty(specialty);
        xmlParameters = xmlParameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(xmlParameters));
        serviceLines = serviceLines == null ? List.of() : List.copyOf(serviceLines);
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
        demoName = nullToEmpty(demoName);
        demoAddress = nullToEmpty(demoAddress);
        demoProvince = nullToEmpty(demoProvince);
        demoCity = nullToEmpty(demoCity);
        demoPostal = nullToEmpty(demoPostal);
        demoSex = nullToEmpty(demoSex);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
