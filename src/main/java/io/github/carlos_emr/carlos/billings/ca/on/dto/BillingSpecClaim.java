/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import java.util.List;

/**
 * Typed BillingSpec claim payload: one header and its item rows.
 */
public record BillingSpecClaim(BillingClaimHeaderDto header,
                               List<BillingClaimItemDto> items) {
    public BillingSpecClaim {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
