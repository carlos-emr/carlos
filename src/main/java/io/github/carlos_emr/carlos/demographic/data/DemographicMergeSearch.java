/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.data;

import java.util.Set;

/** Validated search state for the merge-record page; patient terms belong in POST bodies. */
public record DemographicMergeSearch(String mode, String keyword, String orderBy,
                                     int offset, int limit, boolean merged) {
    private static final Set<String> MODES = Set.of(
            "search_name", "search_dob", "search_phone", "search_hin", "search_address");
    private static final Set<String> ORDERS = Set.of(
            "last_name", "first_name", "demographic_no", "sex", "age", "date_of_birth", "roster_status");

    /** Applies defaults and refuses unsupported options before any database query. */
    public DemographicMergeSearch {
        mode = mode == null ? "search_name" : mode;
        orderBy = orderBy == null || orderBy.isEmpty() ? "last_name" : orderBy;
        if (!MODES.contains(mode) || !ORDERS.contains(orderBy)
                || offset < 0 || limit < 1 || limit > 500 || offset > Integer.MAX_VALUE - limit) {
            throw new IllegalArgumentException("Invalid patient search options");
        }
    }
}
