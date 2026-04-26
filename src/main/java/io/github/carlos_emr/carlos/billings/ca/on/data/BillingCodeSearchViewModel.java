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
    private final String nameFSafe;
    private final String nameFRaw;

    /** One row in the code-search results table. {@code preChecked}
     *  reflects whether the row's code matches one of the previously
     *  selected codes (name/name1/name2 request params). */
    public record CodeRow(String code, String description, boolean preChecked) {}

    private BillingCodeSearchViewModel(Builder b) {
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.noMatch = b.noMatch;
        this.autoSelect = b.autoSelect;
        this.autoSelectCode = b.autoSelectCode == null ? "" : b.autoSelectCode;
        this.nameFSafe = b.nameFSafe == null ? "" : b.nameFSafe;
        this.nameFRaw = b.nameFRaw == null ? "" : b.nameFRaw;
    }

    public static Builder builder() { return new Builder(); }

    public List<CodeRow> getRows() { return rows; }
    public boolean isNoMatch() { return noMatch; }
    public boolean isAutoSelect() { return autoSelect; }
    public String getAutoSelectCode() { return autoSelectCode; }

    /**
     * The {@code nameF} request parameter validated against
     * {@code [a-zA-Z_][a-zA-Z0-9_.]*}. Empty string when the param is
     * missing, malformed, or null. JSP uses this to splice a JS identifier
     * path directly into {@code self.opener.<name> = ...}.
     *
     * @return validated identifier or empty string (never null)
     */
    public String getNameFSafe() { return nameFSafe; }

    /**
     * Raw {@code nameF} request parameter, echoed verbatim into the hidden
     * form input that round-trips the value back to the search action on
     * resubmit. Empty string when absent.
     */
    public String getNameFRaw() { return nameFRaw; }

    /**
     * @return {@code true} when {@link #getNameFSafe()} is non-empty (JS
     *         identifier-path form is safe to splice).
     */
    public boolean isHasNameF() { return !nameFSafe.isEmpty(); }

    /**
     * @return {@code true} when {@link #getNameFRaw()} was supplied (any
     *         non-null), regardless of whether it passed validation. The
     *         JSP renders the hidden round-trip input under this gate.
     */
    public boolean isHasNameFRaw() { return !nameFRaw.isEmpty(); }

    public static final class Builder {
        private List<CodeRow> rows;
        private boolean noMatch;
        private boolean autoSelect;
        private String autoSelectCode;
        private String nameFSafe;
        private String nameFRaw;

        public Builder rows(List<CodeRow> v) { this.rows = v == null ? null : List.copyOf(v); return this; }
        public Builder noMatch(boolean v) { this.noMatch = v; return this; }
        public Builder autoSelect(boolean v) { this.autoSelect = v; return this; }
        public Builder autoSelectCode(String v) { this.autoSelectCode = v; return this; }
        public Builder nameFSafe(String v) { this.nameFSafe = v; return this; }
        public Builder nameFRaw(String v) { this.nameFRaw = v; return this; }

        public BillingCodeSearchViewModel build() { return new BillingCodeSearchViewModel(this); }
    }
}
