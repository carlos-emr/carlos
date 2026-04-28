/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
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
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDxCodeDataAssembler#assemble}.
 * The legacy JSP scriptlet ran a complex code-vs-text dispatcher;
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingDxCodeDataAssembler}
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
    private final String name2;
    private final String targetFormIdx;
    private final String targetElement;
    private final boolean name2ParseError;

    /** One row in the diagnostic-code search results. {@code description}
     *  is already trimmed (legacy JSP did this inline). */
    public record DxRow(String code, String description) {}

    private BillingDigSearchViewModel(Builder b) {
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.noMatch = b.noMatch;
        this.autoSelect = b.autoSelect;
        this.autoSelectCode = b.autoSelectCode == null ? "" : b.autoSelectCode;
        this.autoSelectDesc = b.autoSelectDesc == null ? "" : b.autoSelectDesc;
        this.name2 = b.name2 == null ? "" : b.name2;
        this.targetFormIdx = b.targetFormIdx == null ? "" : b.targetFormIdx;
        this.targetElement = b.targetElement == null ? "" : b.targetElement;
        this.name2ParseError = b.name2ParseError;
    }

    public static Builder builder() { return new Builder(); }

    public List<DxRow> getRows() { return rows; }
    public boolean isNoMatch() { return noMatch; }
    public boolean isAutoSelect() { return autoSelect; }
    public String getAutoSelectCode() { return autoSelectCode; }
    public String getAutoSelectDesc() { return autoSelectDesc; }

    /**
     * Raw {@code name2} request parameter, echoed verbatim into the hidden
     * input that the search-form re-submits. Empty string when absent.
     *
     * @return raw value (never null)
     */
    public String getName2() { return name2; }

    /**
     * Form index parsed out of the legacy
     * {@code document.forms[N].elements['x'].value} {@code name2} pattern.
     * Empty when {@code name2} did not match the pattern.
     */
    public String getTargetFormIdx() { return targetFormIdx; }

    /**
     * Element name parsed out of the legacy
     * {@code document.forms[N].elements['x'].value} {@code name2} pattern.
     * Empty when {@code name2} did not match the pattern.
     */
    public String getTargetElement() { return targetElement; }

    /**
     * @return {@code true} when the assembler successfully parsed
     *         {@link #getName2()} into ({@link #getTargetFormIdx()},
     *         {@link #getTargetElement()}).
     */
    public boolean isHasTargetElement() { return !targetElement.isEmpty(); }

    /**
     * @return {@code true} when {@code name2} was supplied non-empty but
     *         did NOT match the expected pattern. The JSP shows a JS alert
     *         and skips the targeted-write branch when this is true.
     */
    public boolean isName2ParseError() { return name2ParseError; }

    /**
     * @return {@code true} when the JSP should render the hidden
     *         {@code name2} echo input — i.e., either the parse succeeded
     *         (so the popup form needs to round-trip the value) OR a
     *         parse error occurred (so the user can see the error state).
     *         Mirrors the legacy
     *         {@code if (targetElement != null || name2ParseError)} guard.
     */
    public boolean isShowName2Echo() { return isHasTargetElement() || name2ParseError; }

    public static final class Builder {
        private List<DxRow> rows;
        private boolean noMatch;
        private boolean autoSelect;
        private String autoSelectCode;
        private String autoSelectDesc;
        private String name2;
        private String targetFormIdx;
        private String targetElement;
        private boolean name2ParseError;

        public Builder rows(List<DxRow> v) { this.rows = v == null ? null : List.copyOf(v); return this; }
        public Builder noMatch(boolean v) { this.noMatch = v; return this; }
        public Builder autoSelect(boolean v) { this.autoSelect = v; return this; }
        public Builder autoSelectCode(String v) { this.autoSelectCode = v; return this; }
        public Builder autoSelectDesc(String v) { this.autoSelectDesc = v; return this; }
        public Builder name2(String v) { this.name2 = v; return this; }
        public Builder targetFormIdx(String v) { this.targetFormIdx = v; return this; }
        public Builder targetElement(String v) { this.targetElement = v; return this; }
        public Builder name2ParseError(boolean v) { this.name2ParseError = v; return this; }

        public BillingDigSearchViewModel build() { return new BillingDigSearchViewModel(this); }
    }
}
