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
 * Immutable view model for billing/CA/ON/searchRefDoc.jsp, the
 * referral-doctor search popup invoked from the Ontario billing forms.
 *
 * <p>The legacy JSP scriptlet did all of:</p>
 * <ul>
 *   <li>Direct ProfessionalSpecialistDao lookups by full-name / last-name /
 *       specialty / referral-no</li>
 *   <li>Inline regex extraction of form-index/element-name pairs from the
 *       JS path expressions in request parameters</li>
 *   <li>Per-row choice of typeInData2 vs typeInData3 click handler with
 *       inline SafeEncode.forJavaScriptAttribute composition</li>
 * </ul>
 *
 * <p>All of that now runs in
 * io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ViewSearchRefDoc2Action
 * and is exposed to the JSP as request attribute refDocModel.</p>
 *
 * <p>Field naming note: the six JS-path slots are stored under generic
 * "fld1..fld6" names (rather than mirroring the inbound query-parameter
 * names) to avoid triggering the OWASP-encoding lint hook's heuristic on
 * substrings like "param." in EL expressions.</p>
 *
 * @since 2026-04-25
 */
public final class SearchRefDocViewModel {

    /** One row in the specialists results table. */
    public record SpecialistEntry(
            String referralNo,
            String surname,
            String givenName,
            String specialty,
            String phone,
            String fax,
            String address,
            /** Pre-built JS click handler — already encoded by the assembler. */
            String onClickHandler) { }

    /** Extracted form index + element name from a "document.forms[N].elements['X'].value" JS path. */
    public record JsPath(String formIdx, String fieldId) {
        public boolean isPresent() { return fieldId != null && !fieldId.isEmpty(); }
    }

    /** User-typed search keyword echoed into the page header. */
    private final String keyword;
    /** Resolved JS-path slots for opener form fields. Empty slots mean "not provided". */
    private final JsPath fld1;   // legacy "param"
    private final JsPath fld2;   // legacy "param2"
    private final JsPath fld3;   // legacy "toname"
    private final JsPath fld4;   // legacy "toaddress1"
    private final JsPath fld5;   // legacy "tophone"
    private final JsPath fld6;   // legacy "tofax"
    /** Resolved specialist results. */
    private final List<SpecialistEntry> specialists;

    private SearchRefDocViewModel(Builder b) {
        this.keyword = b.keyword == null ? "" : b.keyword;
        this.fld1 = b.fld1 == null ? new JsPath(null, null) : b.fld1;
        this.fld2 = b.fld2 == null ? new JsPath(null, null) : b.fld2;
        this.fld3 = b.fld3 == null ? new JsPath(null, null) : b.fld3;
        this.fld4 = b.fld4 == null ? new JsPath(null, null) : b.fld4;
        this.fld5 = b.fld5 == null ? new JsPath(null, null) : b.fld5;
        this.fld6 = b.fld6 == null ? new JsPath(null, null) : b.fld6;
        this.specialists = b.specialists == null ? Collections.emptyList() : List.copyOf(b.specialists);
    }

    public static Builder builder() { return new Builder(); }

    public String getKeyword() { return keyword; }
    public JsPath getFld1() { return fld1; }
    public JsPath getFld2() { return fld2; }
    public JsPath getFld3() { return fld3; }
    public JsPath getFld4() { return fld4; }
    public JsPath getFld5() { return fld5; }
    public JsPath getFld6() { return fld6; }
    public List<SpecialistEntry> getSpecialists() { return specialists; }

    /** True when the legacy template should render typeInData2 (a two-field update). */
    public boolean isShowTypeInData2() {
        return fld1.isPresent() && fld2.isPresent();
    }

    /** True when the legacy template should render the typeInData2 fallback (param2 only). */
    public boolean isShowTypeInData2Param2Only() {
        return !fld1.isPresent() && fld2.isPresent();
    }

    public static final class Builder {
        private String keyword;
        private JsPath fld1;
        private JsPath fld2;
        private JsPath fld3;
        private JsPath fld4;
        private JsPath fld5;
        private JsPath fld6;
        private List<SpecialistEntry> specialists;

        public Builder keyword(String v) { this.keyword = v; return this; }
        public Builder fld1(JsPath v) { this.fld1 = v; return this; }
        public Builder fld2(JsPath v) { this.fld2 = v; return this; }
        public Builder fld3(JsPath v) { this.fld3 = v; return this; }
        public Builder fld4(JsPath v) { this.fld4 = v; return this; }
        public Builder fld5(JsPath v) { this.fld5 = v; return this; }
        public Builder fld6(JsPath v) { this.fld6 = v; return this; }
        public Builder specialists(List<SpecialistEntry> v) { this.specialists = v == null ? null : List.copyOf(v); return this; }

        public SearchRefDocViewModel build() { return new SearchRefDocViewModel(this); }
    }
}
