/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.command;

/**
 * One reviewed line item posted into the ON billing correction submit action.
 */
public record BillingCorrectionSubmitItemCommand(String serviceCode,
                                                 String description,
                                                 String serviceValue,
                                                 String diagCode,
                                                 String quantity) {
    public BillingCorrectionSubmitItemCommand {
        serviceCode = nullToEmpty(serviceCode);
        description = nullToEmpty(description);
        serviceValue = nullToEmpty(serviceValue);
        diagCode = nullToEmpty(diagCode);
        quantity = nullToEmpty(quantity);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
