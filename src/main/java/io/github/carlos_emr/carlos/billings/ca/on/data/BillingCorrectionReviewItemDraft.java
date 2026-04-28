/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

/**
 * Prepared correction line item shared by the review page and submit command.
 */
public record BillingCorrectionReviewItemDraft(String serviceCode,
                                               String description,
                                               String quantity,
                                               String storedFee,
                                               String percentage,
                                               String diagCode) {
    public BillingCorrectionReviewItemDraft {
        serviceCode = nullToEmpty(serviceCode);
        description = nullToEmpty(description);
        quantity = nullToEmpty(quantity);
        storedFee = nullToEmpty(storedFee);
        percentage = nullToEmpty(percentage);
        diagCode = nullToEmpty(diagCode);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
