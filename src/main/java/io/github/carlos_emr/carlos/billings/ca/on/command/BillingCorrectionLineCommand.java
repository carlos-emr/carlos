/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.command;

/**
 * One submitted service-code row from the ON billing correction validation form.
 */
public record BillingCorrectionLineCommand(String serviceCode,
                                           String billingUnit,
                                           String billingAmount) {
    public BillingCorrectionLineCommand {
        serviceCode = nullToEmpty(serviceCode);
        billingUnit = nullToEmpty(billingUnit);
        billingAmount = nullToEmpty(billingAmount);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
