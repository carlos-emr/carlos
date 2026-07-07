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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.AddEditServiceCodeViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Assembles {@link AddEditServiceCodeViewModel} for
 * {@code addEditServiceCode.jsp}, the service-code admin page.
 * Owns the read-side lookups the JSP body used to perform
 * (BillingServiceDao, BillingPercLimitDao, CSSStylesDAO — twice).
 *
 * <p>The action layer invokes {@link ServiceCodePersister} for add/edit
 * submissions before calling this assembler. This class only projects the
 * already-computed state into the read-only view model.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class AddEditServiceCodeViewModelAssembler {

    private static final int SERVICE_CODE_LEN = 5;
    private static final String MSG_EDIT_PROMPT =
            "You can edit the service code by clicking 'Save' or add a new entry for this code by clicking 'Add Service Code'";
    private static final String MSG_SEARCH_FIRST =
            "You can not save the service code. Please search the service code first.";

    private final BillingServiceDao billingServiceDao;
    private final BillingPercLimitDao billingPercLimitDao;
    private final CSSStylesDAO cssStylesDao;

    public AddEditServiceCodeViewModelAssembler(BillingServiceDao billingServiceDao,
                                     BillingPercLimitDao billingPercLimitDao,
                                     CSSStylesDAO cssStylesDao) {
        this.billingServiceDao = billingServiceDao;
        this.billingPercLimitDao = billingPercLimitDao;
        this.cssStylesDao = cssStylesDao;
    }

    /**
     * Build the admin-page view model from read-only lookups.
     */
    public AddEditServiceCodeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        return assemble(request, loggedInInfo, null);
    }

    /**
     * Build the admin-page view model, optionally projecting the mutation result
     * already produced by {@link ServiceCodePersister}.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public AddEditServiceCodeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo,
                                                ServiceCodePersister.AddEditServiceCodeResult mutationResult) {
        State state = new State();
        String submitFrm = request.getParameter("submitFrm");

        if (mutationResult != null) {
            applyMutationResult(state, mutationResult);
        } else if ("Save".equals(submitFrm) || (submitFrm != null && "Add Service Code".equalsIgnoreCase(submitFrm))) {
            state.message = MSG_SEARCH_FIRST;
            state.alert = "error";
        } else if ("Search".equals(submitFrm)) {
            handleSearch(request, state);
        } else {
            String action = request.getParameter("action");
            if (action != null && action.startsWith("single")) {
                handleSingleEdit(request, state);
            }
        }

        return AddEditServiceCodeViewModel.builder()
                .alert(state.alert)
                .message(state.message)
                .action(state.action)
                .action2(state.action2)
                .prop(state.prop)
                .codes(state.codes)
                .cssStyles(state.cssStyles)
                .build();
    }

    private void applyMutationResult(State state, ServiceCodePersister.AddEditServiceCodeResult result) {
        state.alert = result.alert();
        state.message = result.message();
        state.action = result.action();
        state.action2 = result.action2();
        state.prop = result.prop();
        state.codes = new LinkedHashMap<>(result.codes());
    }

    private void handleSearch(HttpServletRequest request, State state) {
        String serviceCode = request.getParameter("service_code");
        if (serviceCode == null || serviceCode.length() != SERVICE_CODE_LEN) {
            state.message = "Please type in a right service code.";
            state.alert = "warning";
            return;
        }
        List<BillingService> bsList = billingServiceDao.findByServiceCode(serviceCode);
        int count = 0;
        for (BillingService bs : bsList) {
            count++;
            state.codes.put(MyDateFormat.getMyStandardDate(bs.getBillingserviceDate()), bs.getId().toString());
            if (count == 1) {
                populatePropFromBillingService(state.prop, bs, serviceCode);
                state.message = MSG_EDIT_PROMPT;
                state.action = "edit" + serviceCode;
                state.action2 = "add" + serviceCode;
                BillingPercLimit bpl = billingPercLimitDao.findByServiceCodeAndEffectiveDate(serviceCode,
                        MyDateFormat.getSysDate(state.prop.getProperty("billingservice_date")));
                if (bpl != null) {
                    state.prop.setProperty("min", bpl.getMin());
                    state.prop.setProperty("max", bpl.getMax());
                }
            }
        }
        state.cssStyles = loadCssStyles();
        if (count == 0) {
            state.prop.setProperty("service_code", serviceCode);
            state.message = "It is a NEW service code. You can add it.";
            state.alert = "success";
            state.action = "add" + serviceCode;
        }
    }

    private void handleSingleEdit(HttpServletRequest request, State state) {
        String serviceCode = request.getParameter("service_code");
        String billingserviceNo = request.getParameter("billingservice_no");
        if (serviceCode == null || billingserviceNo == null) {
            return;
        }
        int serviceNo;
        try {
            serviceNo = Integer.parseInt(billingserviceNo);
        } catch (NumberFormatException nfe) {
            MiscUtils.getLogger().warn(
                    "Add/Edit service-code edit requested with invalid billingservice_no [{}]; rendering search state",
                    io.github.carlos_emr.carlos.utility.LogSafe.sanitize(billingserviceNo), nfe);
            return;
        }
        BillingService bs = billingServiceDao.find(serviceNo);
        if (bs != null && bs.getId() != null && serviceNo == bs.getId().intValue()) {
            state.codes.put(MyDateFormat.getMyStandardDate(bs.getBillingserviceDate()), bs.getId().toString());
            populatePropFromBillingService(state.prop, bs, serviceCode);
            state.message = MSG_EDIT_PROMPT;
            state.action = "edit" + serviceCode;
            state.action2 = "add" + serviceCode;
            BillingPercLimit bpl = billingPercLimitDao.findByServiceCodeAndEffectiveDate(serviceCode,
                    MyDateFormat.getSysDate(state.prop.getProperty("billingservice_date")));
            if (bpl != null) {
                state.prop.setProperty("min", bpl.getMin());
                state.prop.setProperty("max", bpl.getMax());
            }
        }
        state.cssStyles = loadCssStyles();
    }

    private static void populatePropFromBillingService(Properties prop, BillingService bs, String serviceCode) {
        prop.setProperty("service_code", serviceCode);
        prop.setProperty("description", StringUtils.noNull(bs.getDescription()));
        prop.setProperty("value", StringUtils.noNull(bs.getValue()));
        prop.setProperty("percentage", StringUtils.noNull(bs.getPercentage()));
        prop.setProperty("billingservice_date",
                StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getBillingserviceDate())));
        prop.setProperty("sliFlag", StringUtils.noNull(String.valueOf(bs.getSliFlag())));
        prop.setProperty("termination_date",
                StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getTerminationDate())));
        if (bs.getDisplayStyle() != null) {
            prop.setProperty("displaystyle", StringUtils.noNull(bs.getDisplayStyle().toString()));
        }
    }

    private List<AddEditServiceCodeViewModel.CssStyleEntry> loadCssStyles() {
        List<AddEditServiceCodeViewModel.CssStyleEntry> out = new ArrayList<>();
        for (CssStyle s : cssStylesDao.findAll()) {
            out.add(new AddEditServiceCodeViewModel.CssStyleEntry(
                    s.getId() == null ? "" : s.getId().toString(),
                    s.getName() == null ? "" : s.getName(),
                    s.getStyle() == null ? "" : s.getStyle()));
        }
        return out;
    }

    /** Working state collected as the assembler runs the state machine. */
    private static class State {
        String alert = "info";
        String message = "Type in a service code and search first to see if it is available.";
        String action = "search";
        String action2 = "";
        Properties prop = new Properties();
        Map<String, String> codes = new LinkedHashMap<>();
        List<AddEditServiceCodeViewModel.CssStyleEntry> cssStyles = new ArrayList<>();
    }
}
