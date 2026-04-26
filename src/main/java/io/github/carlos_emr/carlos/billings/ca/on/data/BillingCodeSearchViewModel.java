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
 * Immutable view model for {@code billingCodeSearch.jsp} (the
 * "Service Code Search" popup) and {@code billingResearchCodeSearch.jsp}
 * (the "Research/ICHPPC Code Search" popup) — the two pages share an
 * identical row shape (code + description + pre-selected flag) so they
 * use the same view model.
 *
 * <p>Populated by the respective code-search assemblers and exposed to
 * the JSPs as request attribute {@code codeSearchModel}.</p>
 *
 * @since 2026-04-26
 */
public final class BillingCodeSearchViewModel {

    private final List<CodeRow> rows;
    private final boolean noMatch;
    private final boolean autoSelect;
    private final String autoSelectCode;

    /** One row in the code-search results table. {@code preChecked}
     *  reflects whether the row's code matches one of the previously
     *  selected codes (name/name1/name2 request params). */
    public record CodeRow(String code, String description, boolean preChecked) {}

    private BillingCodeSearchViewModel(Builder b) {
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.noMatch = b.noMatch;
        this.autoSelect = b.autoSelect;
        this.autoSelectCode = b.autoSelectCode == null ? "" : b.autoSelectCode;
    }

    public static Builder builder() { return new Builder(); }

    public List<CodeRow> getRows() { return rows; }
    public boolean isNoMatch() { return noMatch; }
    public boolean isAutoSelect() { return autoSelect; }
    public String getAutoSelectCode() { return autoSelectCode; }

    public static final class Builder {
        private List<CodeRow> rows;
        private boolean noMatch;
        private boolean autoSelect;
        private String autoSelectCode;

        public Builder rows(List<CodeRow> v) { this.rows = v == null ? null : List.copyOf(v); return this; }
        public Builder noMatch(boolean v) { this.noMatch = v; return this; }
        public Builder autoSelect(boolean v) { this.autoSelect = v; return this; }
        public Builder autoSelectCode(String v) { this.autoSelectCode = v; return this; }

        public BillingCodeSearchViewModel build() { return new BillingCodeSearchViewModel(this); }
    }
}
