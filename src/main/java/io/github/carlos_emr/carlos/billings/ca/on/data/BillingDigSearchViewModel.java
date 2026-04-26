/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billingDigSearch.jsp}, the diagnostic-code
 * (ICD-9) search popup.
 *
 * <p>Captures the search-result rows + auto-select state. Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingDigSearchDataAssembler#assemble}.
 * The legacy JSP scriptlet ran a complex code-vs-text dispatcher;
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingDigSearchDataAssembler}
 * encapsulates that decision and produces a flat row list here.</p>
 *
 * @since 2026-04-26
 */
public final class BillingDigSearchViewModel {

    private final List<DxRow> rows;
    private final boolean noMatch;
    private final boolean autoSelect;
    private final String autoSelectCode;
    private final String autoSelectDesc;

    /** One row in the diagnostic-code search results. {@code description}
     *  is already trimmed (legacy JSP did this inline). */
    public record DxRow(String code, String description) {}

    private BillingDigSearchViewModel(Builder b) {
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.noMatch = b.noMatch;
        this.autoSelect = b.autoSelect;
        this.autoSelectCode = b.autoSelectCode == null ? "" : b.autoSelectCode;
        this.autoSelectDesc = b.autoSelectDesc == null ? "" : b.autoSelectDesc;
    }

    public static Builder builder() { return new Builder(); }

    public List<DxRow> getRows() { return rows; }
    public boolean isNoMatch() { return noMatch; }
    public boolean isAutoSelect() { return autoSelect; }
    public String getAutoSelectCode() { return autoSelectCode; }
    public String getAutoSelectDesc() { return autoSelectDesc; }

    public static final class Builder {
        private List<DxRow> rows;
        private boolean noMatch;
        private boolean autoSelect;
        private String autoSelectCode;
        private String autoSelectDesc;

        public Builder rows(List<DxRow> v) { this.rows = v == null ? null : List.copyOf(v); return this; }
        public Builder noMatch(boolean v) { this.noMatch = v; return this; }
        public Builder autoSelect(boolean v) { this.autoSelect = v; return this; }
        public Builder autoSelectCode(String v) { this.autoSelectCode = v; return this; }
        public Builder autoSelectDesc(String v) { this.autoSelectDesc = v; return this; }

        public BillingDigSearchViewModel build() { return new BillingDigSearchViewModel(this); }
    }
}
