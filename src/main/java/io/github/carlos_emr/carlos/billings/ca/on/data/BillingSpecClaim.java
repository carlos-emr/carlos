/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.List;

/**
 * Typed BillingSpec claim payload: one header and its item rows.
 */
public record BillingSpecClaim(BillingClaimHeader1Data header,
                               List<BillingItemData> items) {
    public BillingSpecClaim {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
