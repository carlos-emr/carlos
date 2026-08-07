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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeSearchAjaxViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

/**
 * Assembles {@link BillingCodeSearchAjaxViewModel} for
 * {@code billingCodeSearchAjax.jsp}, the jQuery-UI Autocomplete endpoint.
 * Owns the inline {@code SpringUtils.getBean(BillingServiceDao)} lookup
 * the JSP used to perform.
 *
 * <p>Behavior preserved from the legacy JSP: returns up to 20
 * deduplicated suggestions, code-prefix matches first, description
 * matches second. The em-dash separator ("CODE – DESCRIPTION") is
 * preserved in the {@code label} field.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingCodeSearchAjaxViewModelAssembler {

    private static final int MAX_SUGGESTIONS = 20;
    private static final int MIN_TERM_LENGTH = 2;
    private static final String EN_DASH = "–";

    private final BillingServiceDao billingServiceDao;

    public BillingCodeSearchAjaxViewModelAssembler(BillingServiceDao billingServiceDao) {
        this.billingServiceDao = billingServiceDao;
    }

    /**
     * Build the suggestions list for the given autocomplete term.
     *
     * @param term raw query string from the autocomplete widget; trimmed
     *             internally; must be at least {@value #MIN_TERM_LENGTH}
     *             characters or an empty list is returned (matches legacy
     *             behavior — saves a DAO round-trip on single-character
     *             keystrokes)
     * @return populated view model
     */
    public BillingCodeSearchAjaxViewModel assemble(String term) {
        if (term == null) {
            term = "";
        }
        term = term.trim();
        if (term.length() < MIN_TERM_LENGTH) {
            return BillingCodeSearchAjaxViewModel.builder().build();
        }

        Date searchDate = new Date();
        LinkedHashMap<String, BillingService> merged = new LinkedHashMap<>();

        // Code-prefix search: A00 → A001A, A002A, ... (term uppercased).
        List<BillingService> codeResults = billingServiceDao.search(term.toUpperCase() + "%", "ON", searchDate);
        if (codeResults != null) {
            for (BillingService bs : codeResults) {
                String key = bs.getServiceCode() == null ? "" : bs.getServiceCode();
                if (!key.isEmpty()) {
                    merged.put(key, bs);
                }
            }
        }

        // Description-contains search: term as substring of description.
        List<BillingService> descResults = billingServiceDao.search("%" + term + "%", "ON", searchDate);
        if (descResults != null) {
            for (BillingService bs : descResults) {
                String key = bs.getServiceCode() == null ? "" : bs.getServiceCode();
                if (!key.isEmpty()) {
                    merged.putIfAbsent(key, bs);
                }
            }
        }

        List<BillingCodeSearchAjaxViewModel.Suggestion> out = new ArrayList<>();
        int limit = Math.min(merged.size(), MAX_SUGGESTIONS);
        int i = 0;
        for (BillingService bs : merged.values()) {
            if (i++ >= limit) break;
            String code = bs.getServiceCode() == null ? "" : bs.getServiceCode();
            String desc = bs.getDescription() == null ? "" : bs.getDescription();
            out.add(new BillingCodeSearchAjaxViewModel.Suggestion(
                    code, code + " " + EN_DASH + " " + desc, code, desc));
        }
        return BillingCodeSearchAjaxViewModel.builder().suggestions(out).build();
    }
}
