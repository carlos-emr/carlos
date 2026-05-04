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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFavouriteViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link BillingOnFavouriteViewModel} for
 * {@code billingONfavourite.jsp}, the Add/Edit favourite-service-code admin
 * form. Owns the read-side Search branch and dropdown population the legacy
 * JSP performed inline. Add/Edit/Delete writes are handled by the action and
 * passed in as a typed mutation result before rendering.
 *
 * <p>HTML fragments emitted in the legacy {@code msg} contained {@code
 * <font color='red'>...</font>} markup; those have been preserved as-is
 * so the rendered text matches byte-for-byte. The JSP outputs the message
 * with {@code escapeXml="false"} to honour that intentional inline markup.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnFavouriteViewModelAssembler {

    private static final String SUFFIX_TYPE_TO_SEARCH =
            "Type in a name and search first to see if it is available.";

    private final BillingOnLookupService lookupService;

    /** Test-friendly constructor — takes the lookup service mock directly. */
    public BillingOnFavouriteViewModelAssembler(BillingOnLookupService lookupService) {
        this.lookupService = lookupService;
    }

    /** Build the view model for both GET (no submit) and POST (form-mutating) paths. */
    public BillingOnFavouriteViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        return assemble(request, loggedInInfo, null);
    }

    /** Build the view model after an optional favourite-list mutation. */
    public BillingOnFavouriteViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo,
                                                BillingOnLookupService.FavouriteMutationResult mutationResult) {
        Map<String, String> formFields = new HashMap<>();
        String msg = SUFFIX_TYPE_TO_SEARCH;
        String action = "search";

        String submit = request.getParameter("submit");
        if (mutationResult != null) {
            msg = mutationResult.message();
            action = mutationResult.action();
            formFields.putAll(mutationResult.formFields());
        } else if ("Search".equals(submit)) {
            FormResult r = handleSearch(request, lookupService, formFields);
            msg = r.msg;
            action = r.action;
        }

        // Dropdown of all existing favourite names.
        List<BillingOnFavouriteViewModel.FavouriteName> nameList = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List sL = lookupService.getBillingFavouriteList();
        // Legacy JSP iterated i=i+2 — every other entry is the display name.
        for (int i = 0; i < sL.size(); i = i + 2) {
            Object v = sL.get(i);
            nameList.add(new BillingOnFavouriteViewModel.FavouriteName(v == null ? "" : v.toString()));
        }

        return BillingOnFavouriteViewModel.builder()
                .message(msg)
                .action(action)
                .names(nameList)
                .formFields(formFields)
                .serviceFieldCount(BillingOnConstants.FIELD_SERVICE_NUM)
                .build();
    }

    private FormResult handleSearch(HttpServletRequest request, BillingOnLookupService lookupService,
                                    Map<String, String> formFields) {
        if (request.getParameter("name") == null) {
            return new FormResult("Please type in a right name.", "search");
        }
        String name = request.getParameter("name");
        @SuppressWarnings("rawtypes")
        List ni = lookupService.getBillingFavouriteOne(name);
        if (ni != null && ni.size() > 0) {
            formFields.put("name", (String) ni.get(0));
            String list1 = (String) ni.get(1);
            String[] temp = list1 == null ? new String[0] : list1.split("\\|");
            int n = 0;
            for (int i = 0; i < temp.length; i++) {
                if (temp[i].length() == 5) {
                    formFields.put("serviceCode" + n, temp[i]);
                    if (i + 1 < temp.length) formFields.put("serviceUnit" + n, temp[i + 1]);
                    if (i + 2 < temp.length) formFields.put("serviceAt" + n, temp[i + 2]);
                    i = i + 2;
                    n++;
                } else if (temp[i].length() == 3) {
                    if (!formFields.containsKey("dx")) {
                        formFields.put("dx", temp[i]);
                    } else if (!formFields.containsKey("dx1")) {
                        formFields.put("dx1", temp[i]);
                    } else if (!formFields.containsKey("dx2")) {
                        formFields.put("dx2", temp[i]);
                    }
                }
            }
            return new FormResult("You can edit the name.", "edit" + name);
        }
        formFields.put("name", name);
        return new FormResult("It is a NEW name. You can add it.", "add" + name);
    }

    private record FormResult(String msg, String action) { }
}
