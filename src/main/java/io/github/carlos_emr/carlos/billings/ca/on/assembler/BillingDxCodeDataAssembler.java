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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchAjaxViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigUpdateViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONDxDescViewModel;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Shared assembler for the four dx-code (ICD-9 diagnostic code) JSPs:
 * {@code billingDigSearch.jsp}, {@code billingDigSearchAjax.jsp},
 * {@code billingDigUpdate.jsp}, {@code billingON_dx_desc.jsp}.
 * All four hit {@link DiagnosticCodeDao}; this assembler owns the four
 * inline {@code SpringUtils.getBean(DiagnosticCodeDao)} lookups the JSPs
 * used to perform.
 *
 * <p>Each page picks up its specific result via a dedicated assemble
 * method ({@link #assembleSearch}, {@link #assembleAjax},
 * {@link #assembleUpdate}, {@link #assembleDescription}); the row shapes
 * are page-specific so each gets its own view model.</p>
 *
 * @since 2026-04-26
 */
public final class BillingDxCodeDataAssembler {

    private static final int MAX_AJAX_SUGGESTIONS = 20;
    private static final int MIN_AJAX_TERM_LENGTH = 2;
    private static final int DESC_TRUNCATE_LEN = 32;
    private static final String EN_DASH = "–";

    /**
     * Pattern matching the legacy
     * {@code document.forms[N].elements['x'].value} JS-path string the
     * billing-form callers pass via the {@code name2} parameter. Captures
     * the form index and element name. Element names may contain dots
     * (e.g. {@code pref.default_dx_code}).
     */
    private static final Pattern NAME2_PATTERN = Pattern.compile(
            "^document\\.forms\\[(\\d+)\\]\\.elements\\['([a-zA-Z0-9_.]+)'\\]\\.value$");

    private final DiagnosticCodeDao diagnosticCodeDao;

    public BillingDxCodeDataAssembler() {
        this(SpringUtils.getBean(DiagnosticCodeDao.class));
    }

    BillingDxCodeDataAssembler(DiagnosticCodeDao diagnosticCodeDao) {
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    /**
     * Search dispatch for {@code billingDigSearch.jsp}. The legacy JSP's
     * scriptlet split the input into numeric and text portions to choose
     * between {@link DiagnosticCodeDao#searchCode} and
     * {@link DiagnosticCodeDao#searchText}; preserved here.
     *
     * @param coderange numeric-prefix dropdown value (0-9)
     * @param codedesc free-text description input
     */
    public BillingDigSearchViewModel assembleSearch(String coderange, String codedesc) {
        return assembleSearch(coderange, codedesc, null);
    }

    /**
     * Search dispatch with optional {@code name2} callback path. Parses
     * the legacy {@code document.forms[N].elements['x'].value} pattern so
     * the JSP can render targeted-write JS via EL instead of an inline
     * scriptlet regex.
     *
     * @param coderange numeric-prefix dropdown value (0-9)
     * @param codedesc free-text description input
     * @param name2 raw JS path the billing-form caller wants the popup to
     *              write back to; null/empty when not provided
     */
    public BillingDigSearchViewModel assembleSearch(String coderange, String codedesc, String name2) {
        String input = decideInput(coderange, codedesc);
        SearchClassification c = classify(input);

        Map<String, String> deduped = new LinkedHashMap<>();
        switch (c.searchType) {
            case "N" -> {
                List<DiagnosticCode> results = "search_diagnostic_code".equals(c.search)
                        ? diagnosticCodeDao.searchCode(c.codeName + "%")
                        : diagnosticCodeDao.searchText(c.codeName + "%");
                addDistinct(deduped, results);
            }
            case "BOTH" -> {
                addDistinct(deduped, diagnosticCodeDao.searchText(c.codeName + "%"));
                addDistinct(deduped, diagnosticCodeDao.searchCode(c.codeName2 + "%"));
            }
            default -> {
                // No search performed (empty input fallback).
            }
        }

        List<BillingDigSearchViewModel.DxRow> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : deduped.entrySet()) {
            rows.add(new BillingDigSearchViewModel.DxRow(e.getKey(), e.getValue()));
        }
        BillingDigSearchViewModel.Builder b = BillingDigSearchViewModel.builder().rows(rows);
        if (rows.isEmpty()) {
            b.noMatch(true);
        } else if (rows.size() == 1) {
            b.autoSelect(true)
                    .autoSelectCode(rows.get(0).code())
                    .autoSelectDesc(rows.get(0).description());
        }
        applyName2(b, name2);
        return b.build();
    }

    /**
     * Parse {@code name2} into form-index + element-name and stamp the
     * builder. Mirrors the legacy {@code billingDigSearch.jsp} scriptlet
     * exactly, including the truncated-warning log on parse failure.
     */
    private static void applyName2(BillingDigSearchViewModel.Builder b, String name2) {
        if (name2 == null) {
            return;
        }
        b.name2(name2);
        Matcher m = NAME2_PATTERN.matcher(name2);
        if (m.matches()) {
            b.targetFormIdx(m.group(1));
            b.targetElement(m.group(2));
        } else if (!name2.isEmpty()) {
            String truncated = name2.length() > 120 ? name2.substring(0, 120) + "..." : name2;
            MiscUtils.getLogger().warn(
                    "billingDigSearch.jsp: 'name2' did not match expected JS path format: {} (length={})",
                    truncated, name2.length());
            b.name2ParseError(true);
        }
    }

    /**
     * Autocomplete suggestions for {@code billingDigSearchAjax.jsp}.
     * The legacy JSP dispatched on {@code Character.isDigit(term.charAt(0))} —
     * digit-prefix terms hit code search, otherwise description search.
     */
    public BillingDigSearchAjaxViewModel assembleAjax(String term) {
        if (term == null) term = "";
        term = term.trim();
        if (term.length() < MIN_AJAX_TERM_LENGTH) {
            return BillingDigSearchAjaxViewModel.builder().build();
        }
        List<DiagnosticCode> results;
        if (Character.isDigit(term.charAt(0))) {
            results = diagnosticCodeDao.searchCode(term + "%");
        } else {
            results = diagnosticCodeDao.searchText(term);
        }
        if (results == null) {
            return BillingDigSearchAjaxViewModel.builder().build();
        }

        List<BillingDigSearchAjaxViewModel.Suggestion> out = new ArrayList<>();
        int limit = Math.min(results.size(), MAX_AJAX_SUGGESTIONS);
        for (int i = 0; i < limit; i++) {
            DiagnosticCode dc = results.get(i);
            String code = dc.getDiagnosticCode() == null ? "" : dc.getDiagnosticCode();
            String desc = dc.getDescription() == null ? "" : dc.getDescription();
            out.add(new BillingDigSearchAjaxViewModel.Suggestion(
                    code, code + " " + EN_DASH + " " + desc, code, desc));
        }
        return BillingDigSearchAjaxViewModel.builder().suggestions(out).build();
    }

    /**
     * Description-edit mutation for {@code billingDigUpdate.jsp}.
     * Cleaves the last 3 characters off the {@code update} submit-button
     * value to derive the dx code (legacy JSP behavior — ICD-9 codes are
     * 3-digit prefixes), then merges the new description.
     */
    public BillingDigUpdateViewModel assembleUpdate(String submitValue, String newDescription) {
        if (submitValue == null || submitValue.length() < 3) {
            return BillingDigUpdateViewModel.builder().error(true).build();
        }
        String code = submitValue.substring(submitValue.length() - 3);
        try {
            for (DiagnosticCode dcode : diagnosticCodeDao.findByDiagnosticCode(code)) {
                dcode.setDescription(newDescription);
                diagnosticCodeDao.merge(dcode);
            }
            return BillingDigUpdateViewModel.builder().build();
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().error("Diagnostic code update failed for {}", code, ex);
            return BillingDigUpdateViewModel.builder().error(true).build();
        }
    }

    /**
     * Single-row description lookup for {@code billingON_dx_desc.jsp}.
     * Truncates to {@value #DESC_TRUNCATE_LEN} chars + "..." when longer.
     */
    public BillingONDxDescViewModel assembleDescription(String diagnosticCode) {
        if (diagnosticCode == null || diagnosticCode.isEmpty()) {
            return BillingONDxDescViewModel.builder().build();
        }
        String description = "";
        for (DiagnosticCode result : diagnosticCodeDao.findByDiagnosticCode(diagnosticCode)) {
            if (result.getDescription() != null) {
                description = result.getDescription().trim();
            }
        }
        // Legacy JSP truncated only when length > 32.
        String rendered = description;
        if (!rendered.isEmpty() && rendered.length() > DESC_TRUNCATE_LEN) {
            rendered = rendered.substring(0, DESC_TRUNCATE_LEN) + "...";
        }
        return BillingONDxDescViewModel.builder().description(rendered).build();
    }

    /**
     * Pick which of the two inputs the user actually used:
     * {@code codedesc} (free-text) wins over {@code coderange} (numeric
     * dropdown). Mirrors the legacy JSP's branch.
     */
    private static String decideInput(String coderange, String codedesc) {
        if (codedesc == null) {
            return coderange == null ? "" : coderange;
        }
        if (codedesc.isEmpty()) {
            return coderange == null ? "" : coderange;
        }
        return codedesc;
    }

    private static SearchClassification classify(String input) {
        SearchClassification c = new SearchClassification();
        if (input == null) {
            return c;
        }
        String numCode = "";
        String textCode = "";
        for (int i = 0; i < input.length(); i++) {
            String ch = input.substring(i, i + 1);
            int h = ch.hashCode();
            if (h >= 48 && h <= 58) {
                numCode += ch;
            } else {
                textCode += ch;
            }
        }
        if (numCode.isEmpty()) {
            if (textCode.isEmpty()) {
                // Both empty — return empty classification (no search).
                c.searchType = "";
            } else {
                c.codeName = "%" + textCode;
                c.search = "search_diagnostic_text";
                c.searchType = "N";
            }
        } else {
            if (textCode.isEmpty()) {
                c.codeName = numCode;
                c.search = "search_diagnostic_code";
                c.searchType = "N";
            } else {
                c.codeName = "%" + textCode;
                c.codeName2 = numCode;
                c.searchType = "BOTH";
            }
        }
        return c;
    }

    private static void addDistinct(Map<String, String> deduped, List<DiagnosticCode> results) {
        if (results == null) return;
        for (DiagnosticCode r : results) {
            String code = r.getDiagnosticCode() == null ? "" : r.getDiagnosticCode();
            if (code.isEmpty()) continue;
            String desc = r.getDescription() == null ? "" : r.getDescription().trim();
            deduped.putIfAbsent(code, desc);
        }
    }

    /** Working state for the search-dispatch decision. */
    private static class SearchClassification {
        String codeName = "";
        String codeName2 = "";
        String search = "";
        String searchType = "";
    }
}
