/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.command;

import java.util.List;

/**
 * Typed payload for committing a reviewed ON billing correction.
 */
public record BillingCorrectionSubmitCommand(String billingNo,
                                             String content,
                                             String total,
                                             String hin,
                                             String dob,
                                             String visitType,
                                             String visitDate,
                                             String status,
                                             String clinicRefCode,
                                             String providerNo,
                                             String billingDate,
                                             List<BillingCorrectionSubmitItemCommand> items) {
    public BillingCorrectionSubmitCommand {
        billingNo = nullToEmpty(billingNo);
        content = nullToEmpty(content);
        total = nullToEmpty(total);
        hin = nullToEmpty(hin);
        dob = nullToEmpty(dob);
        visitType = nullToEmpty(visitType);
        visitDate = nullToEmpty(visitDate);
        status = nullToEmpty(status);
        clinicRefCode = nullToEmpty(clinicRefCode);
        providerNo = nullToEmpty(providerNo);
        billingDate = nullToEmpty(billingDate);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
