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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCodeSearchViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.IchppccodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Ichppccode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Shared assembler for the two code-search popups —
 * {@code billingCodeSearch.jsp} (BillingService DAO) and
 * {@code billingResearchCodeSearch.jsp} (Ichppccode DAO). Each page picks
 * up its specific {@link Mode} via the dedicated assemble method; the
 * row-shape and auto-select behavior are identical between them, so the
 * view model is shared.
 *
 * <p>Eliminates the inline {@code SpringUtils.getBean} lookups both JSPs
 * used to perform.</p>
 *
 * @since 2026-04-26
 */
public final class BillingCodeSearchDataAssembler {

    /** Which DAO to query — pick via {@link #assembleService} or
     *  {@link #assembleResearch}. */
    public enum Mode { SERVICE_CODE, RESEARCH_CODE }

    private final BillingServiceDao billingServiceDao;
    private final IchppccodeDao ichppccodeDao;

    public BillingCodeSearchDataAssembler() {
        this(SpringUtils.getBean(BillingServiceDao.class),
             SpringUtils.getBean(IchppccodeDao.class));
    }

    BillingCodeSearchDataAssembler(BillingServiceDao billingServiceDao, IchppccodeDao ichppccodeDao) {
        this.billingServiceDao = billingServiceDao;
        this.ichppccodeDao = ichppccodeDao;
    }

    /**
     * Search OHIP service codes (BillingServiceDao). The legacy JSP built
     * its query parameters with two side-by-side conventions:
     * codeName uses {@code "%"}-suffix and desc uses
     * {@code "%X%"}-wrap when the user provided a value; both default to
     * a single space when blank. Same shape preserved here.
     *
     * @param name name search input #1 (raw user-typed value)
     * @param name1 search input #2
     * @param name2 search input #3
     * @return populated view model
     */
    public BillingCodeSearchViewModel assembleService(String name, String name1, String name2) {
        return assembleService(name, name1, name2, null);
    }

    /**
     * Same as {@link #assembleService(String, String, String)} but also
     * captures the {@code nameF} request parameter (the JS callback path
     * the legacy JSP read inline) into the view model so the JSP can render
     * pure EL.
     */
    public BillingCodeSearchViewModel assembleService(String name, String name1, String name2, String nameF) {
        QueryParams q = buildServiceQueryParams(name, name1, name2);
        Set<String> preSelected = collectPreSelected(name, name1, name2);

        List<BillingCodeSearchViewModel.CodeRow> rows = new ArrayList<>();
        for (BillingService bs : billingServiceDao.search_service_code(
                q.code, q.code1, q.code2, q.desc, q.desc1, q.desc2)) {
            String code = bs.getServiceCode() == null ? "" : bs.getServiceCode();
            String desc = bs.getDescription() == null ? "" : bs.getDescription();
            rows.add(new BillingCodeSearchViewModel.CodeRow(code, desc, preSelected.contains(code)));
        }
        return finalize(rows, nameF);
    }

    /**
     * Validate the {@code nameF} request parameter against the legacy
     * {@code [a-zA-Z_][a-zA-Z0-9_.]*} JS-identifier-path pattern. Returns
     * the input when it matches, empty string otherwise. Never returns null.
     */
    private static String validateNameF(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.matches("[a-zA-Z_][a-zA-Z0-9_.]*") ? raw : "";
    }

    /**
     * Search ICHPPC research codes ({@link IchppccodeDao}). Same query
     * shape as {@link #assembleService} but the desc params don't get
     * the surrounding wildcards (parity with legacy JSP).
     */
    public BillingCodeSearchViewModel assembleResearch(String name, String name1, String name2) {
        QueryParams q = buildResearchQueryParams(name, name1, name2);
        // Research search legacy JSP didn't pre-select rows; preserve.
        List<BillingCodeSearchViewModel.CodeRow> rows = new ArrayList<>();
        for (Ichppccode i : ichppccodeDao.search_research_code(
                q.code, q.code1, q.code2, q.desc, q.desc1, q.desc2)) {
            String code = i.getId() == null ? "" : i.getId();
            String desc = i.getDescription() == null ? "" : i.getDescription();
            rows.add(new BillingCodeSearchViewModel.CodeRow(code, desc, false));
        }
        return finalize(rows, null);
    }

    private BillingCodeSearchViewModel finalize(List<BillingCodeSearchViewModel.CodeRow> rows, String nameF) {
        BillingCodeSearchViewModel.Builder b = BillingCodeSearchViewModel.builder().rows(rows);
        if (rows.isEmpty()) {
            b.noMatch(true);
        } else if (rows.size() == 1) {
            // Auto-select single result. Mirrors the legacy JSP's
            // <script>CodeAttach('<%=Dcode%>');</script> emission when
            // intCount == 1.
            b.autoSelect(true).autoSelectCode(rows.get(0).code());
        }
        if (nameF != null) {
            b.nameFRaw(nameF);
            b.nameFSafe(validateNameF(nameF));
        }
        return b.build();
    }

    private static QueryParams buildServiceQueryParams(String name, String name1, String name2) {
        QueryParams q = new QueryParams();
        if (isEmpty(name)) {
            q.code = " ";
            q.desc = " ";
        } else {
            q.code = name + "%";
            q.desc = "%" + q.code + "%";
        }
        if (isEmpty(name1)) {
            q.code1 = " ";
            q.desc1 = " ";
        } else {
            q.code1 = name1 + "%";
            q.desc1 = "%" + q.code1 + "%";
        }
        if (isEmpty(name2)) {
            q.code2 = " ";
            q.desc2 = " ";
        } else {
            q.code2 = name2 + "%";
            q.desc2 = "%" + q.code2 + "%";
        }
        return q;
    }

    private static QueryParams buildResearchQueryParams(String name, String name1, String name2) {
        QueryParams q = new QueryParams();
        if (isEmpty(name)) { q.code = " "; q.desc = " "; }
        else { q.code = name + "%"; q.desc = q.code + "%"; }
        if (isEmpty(name1)) { q.code1 = " "; q.desc1 = " "; }
        else { q.code1 = name1 + "%"; q.desc1 = q.code1 + "%"; }
        if (isEmpty(name2)) { q.code2 = " "; q.desc2 = " "; }
        else { q.code2 = name2 + "%"; q.desc2 = q.code2 + "%"; }
        return q;
    }

    private static Set<String> collectPreSelected(String... raw) {
        Set<String> out = new HashSet<>();
        for (String r : raw) {
            if (!isEmpty(r)) {
                out.add(r);
            }
        }
        return out;
    }

    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }

    private static class QueryParams {
        String code = " ";
        String code1 = " ";
        String code2 = " ";
        String desc = " ";
        String desc1 = " ";
        String desc2 = " ";
    }
}
