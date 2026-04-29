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
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.AddEditServiceCodeViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Assembles {@link AddEditServiceCodeViewModel} for
 * {@code addEditServiceCode.jsp}, the service-code admin page.
 * Owns the 4 inline {@code SpringUtils.getBean} lookups the JSP body
 * used to perform (BillingServiceDao, BillingPercLimitDao, CSSStylesDAO
 * — twice) plus the persist/merge mutations the legacy scriptlet ran
 * mid-render.
 *
 * <p>This is mutation-on-render (the JSP-era code persisted/merged
 * BillingService and BillingPercLimit rows mid-page); the action layer
 * now invokes this assembler before forwarding to the JSP.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class AddEditServiceCodeViewModelAssembler {

    private static final int SERVICE_CODE_LEN = 5;
    private static final String MSG_SAME_DATE_EXISTS =
            "The selected Service Code has an entry for this Issue Date. <br> Select new issue date, or use 'Save' to update the existing entry.";
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
     * Build the admin-page view model and, when applicable, persist
     * the user's add/edit submission.
     */
    public AddEditServiceCodeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        State state = new State();
        String submitFrm = request.getParameter("submitFrm");

        if ("Save".equals(submitFrm) || (submitFrm != null && "Add Service Code".equalsIgnoreCase(submitFrm))) {
            handleSaveOrAdd(request, state);
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

    private void handleSaveOrAdd(HttpServletRequest request, State state) {
        String valuePara = request.getParameter("value");
        if (request.getParameter("percentage") != null
                && request.getParameter("percentage").length() > 0
                && (valuePara == null || valuePara.trim().isEmpty())) {
            valuePara = ".00";
        }
        String action = request.getParameter("action");
        if (action == null) {
            state.message = MSG_SEARCH_FIRST;
            state.alert = "error";
            return;
        }
        if (action.startsWith("edit")) {
            handleEditSubmit(request, state, valuePara);
        } else if (action.startsWith("add")) {
            handleAddSubmit(request, state, valuePara);
        } else {
            state.message = MSG_SEARCH_FIRST;
            state.alert = "error";
        }
    }

    private void handleEditSubmit(HttpServletRequest request, State state, String valuePara) {
        String serviceCode = request.getParameter("service_code");
        String billingserviceNo = request.getParameter("billingservice_no");
        String action = request.getParameter("action");
        if (!serviceCode.equals(action.substring("edit".length()))) {
            state.message = "You can not save the service code - " + SafeEncode.forHtml(serviceCode)
                    + ". Please search the service code first.";
            state.alert = "error";
            state.action = "search";
            state.prop.setProperty("service_code", serviceCode);
            return;
        }

        BillingService bs = null;
        if (billingserviceNo != null) {
            try {
                bs = billingServiceDao.find(Integer.parseInt(billingserviceNo));
            } catch (NumberFormatException nfe) {
                bs = null;
            }
        } else {
            List<BillingService> bsList = billingServiceDao.findByServiceCode(serviceCode);
            if (!bsList.isEmpty()) {
                bs = bsList.get(0);
            }
        }
        if (bs == null) {
            state.message = SafeEncode.forHtml(serviceCode) + " is not updated. Action failed! Try edit it again.";
            state.alert = "error";
            state.action = "edit" + serviceCode;
            populatePropFromForm(state.prop, request, serviceCode);
            return;
        }

        bs.setDescription(request.getParameter("description"));
        bs.setValue(valuePara);
        bs.setPercentage(request.getParameter("percentage"));
        bs.setBillingserviceDate(MyDateFormat.getSysDate(request.getParameter("billingservice_date")));
        bs.setSliFlag("true".equals(request.getParameter("sliFlag")));
        bs.setTerminationDate(MyDateFormat.getSysDate(request.getParameter("termination_date")));
        applyDisplayStyle(bs, request.getParameter("servicecode_style"));

        upsertPercLimit(request, serviceCode);

        billingServiceDao.merge(bs);
        state.message = SafeEncode.forHtml(serviceCode)
                + " is updated.<br>Type in a service code and search first to see if it is available.";
        state.alert = "success";
        state.action = "search";
        state.prop.setProperty("service_code", serviceCode);
    }

    private void handleAddSubmit(HttpServletRequest request, State state, String valuePara) {
        String serviceCode = request.getParameter("service_code");
        String action = request.getParameter("action");
        if (!serviceCode.equals(action.substring("add".length()))) {
            state.message = "You can not save the service code - " + SafeEncode.forHtml(serviceCode)
                    + ". Please search the service code first.";
            state.alert = "error";
            state.action = "search";
            state.prop.setProperty("service_code", serviceCode);
            return;
        }

        BillingService bs = new BillingService();
        bs.setServiceCompositecode("");
        bs.setServiceCode(serviceCode);
        bs.setDescription(request.getParameter("description"));
        bs.setValue(valuePara);
        bs.setPercentage(request.getParameter("percentage"));
        bs.setBillingserviceDate(MyDateFormat.getSysDate(request.getParameter("billingservice_date")));
        bs.setSpecialty("");
        bs.setRegion("ON");
        bs.setAnaesthesia("00");
        bs.setTerminationDate(MyDateFormat.getSysDate(request.getParameter("termination_date")));
        bs.setSliFlag("true".equals(request.getParameter("sliFlag")));
        applyDisplayStyle(bs, request.getParameter("servicecode_style"));
        bs.setGstFlag(false);

        if (hasPercWithLimits(request)) {
            BillingPercLimit bpl = new BillingPercLimit();
            bpl.setService_code(serviceCode);
            bpl.setMin(request.getParameter("min"));
            bpl.setMax(request.getParameter("max"));
            bpl.setEffective_date(MyDateFormat.getSysDate(request.getParameter("billingservice_date")));
            billingPercLimitDao.persist(bpl);
        }

        @SuppressWarnings("rawtypes")
        List scadList = billingServiceDao.findByServiceCodeAndDate(bs.getServiceCode(), bs.getBillingserviceDate());
        if (scadList != null && !scadList.isEmpty()) {
            state.message = MSG_SAME_DATE_EXISTS;
            state.alert = "error";
            state.prop.setProperty("service_code", serviceCode);
            state.prop.setProperty("description", StringUtils.noNull(bs.getDescription()));
            state.prop.setProperty("value", StringUtils.noNull(bs.getValue()));
            state.prop.setProperty("percentage", StringUtils.noNull(bs.getPercentage()));
            state.prop.setProperty("billingservice_date",
                    StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getBillingserviceDate())));
            state.prop.setProperty("sliFlag", StringUtils.noNull(String.valueOf(bs.getSliFlag())));
            state.prop.setProperty("termination_date",
                    StringUtils.noNull(MyDateFormat.getMyStandardDate(bs.getTerminationDate())));
            state.action = "edit" + serviceCode;
            state.action2 = "add" + serviceCode;
            return;
        }

        billingServiceDao.persist(bs);
        state.message = SafeEncode.forHtml(serviceCode)
                + " is added.<br>Type in a service code and search first to see if it is available.";
        state.alert = "success";
        state.action = "search";
        state.prop.setProperty("service_code", serviceCode);
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

    private void upsertPercLimit(HttpServletRequest request, String serviceCode) {
        if (!hasPercWithLimits(request)) {
            return;
        }
        List<BillingPercLimit> percLimits = billingPercLimitDao.findByServiceCode(serviceCode);
        if (percLimits.isEmpty()) {
            BillingPercLimit pl = new BillingPercLimit();
            pl.setService_code(serviceCode);
            pl.setMin(request.getParameter("min"));
            pl.setMax(request.getParameter("max"));
            pl.setEffective_date(MyDateFormat.getSysDate(request.getParameter("billingservice_date")));
            billingPercLimitDao.persist(pl);
            return;
        }
        BillingPercLimit pl = billingPercLimitDao.findByServiceCodeAndEffectiveDate(serviceCode,
                MyDateFormat.getSysDate(request.getParameter("billingservice_date")));
        if (pl != null) {
            pl.setMin(request.getParameter("min"));
            pl.setMax(request.getParameter("max"));
            billingPercLimitDao.merge(pl);
        }
    }

    private static boolean hasPercWithLimits(HttpServletRequest request) {
        String percentage = request.getParameter("percentage");
        String min = request.getParameter("min");
        String max = request.getParameter("max");
        return percentage != null && percentage.length() > 1
                && min != null && min.length() > 1
                && max != null && max.length() > 1;
    }

    private static void applyDisplayStyle(BillingService bs, String servicecodeStyle) {
        if (servicecodeStyle == null || servicecodeStyle.startsWith("-1")) {
            bs.setDisplayStyle(null);
            return;
        }
        String[] tmp = servicecodeStyle.split(",");
        try {
            bs.setDisplayStyle(Integer.parseInt(tmp[0]));
        } catch (NumberFormatException nfe) {
            bs.setDisplayStyle(null);
        }
    }

    private static void populatePropFromForm(Properties prop, HttpServletRequest request, String serviceCode) {
        prop.setProperty("service_code", serviceCode);
        for (String key : new String[]{"description", "value", "percentage", "billingservice_date", "sliFlag"}) {
            String v = request.getParameter(key);
            if (v != null) {
                prop.setProperty(key, v);
            }
        }
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
