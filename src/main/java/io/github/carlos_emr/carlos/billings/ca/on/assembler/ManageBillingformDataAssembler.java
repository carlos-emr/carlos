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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformViewModel;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles a {@link ManageBillingformViewModel} for {@code manageBillingform.jsp}
 * and its three included tab fragments.
 *
 * <p>The assembler reads the same request parameters the legacy scriptlets
 * read ({@code billingform}, {@code reportAction}) and replicates the inline
 * DAO lookups against {@link CtlBillingServiceDao}, {@link CtlDiagCodeDao}
 * and {@link CtlBillingServicePremiumDao}. Each tab populates its own subset
 * of the view model — unselected tabs leave their lists empty, mirroring the
 * legacy "include only one fragment" pattern.</p>
 *
 * <p>Pure read: privilege gating is performed by
 * {@code ManageBillingform2Action}; this assembler runs after the gate.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class ManageBillingformDataAssembler {

    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final CtlDiagCodeDao ctlDiagCodeDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;

    public ManageBillingformDataAssembler(CtlBillingServiceDao ctlBillingServiceDao,
                                    CtlDiagCodeDao ctlDiagCodeDao,
                                    CtlBillingServicePremiumDao ctlBillingServicePremiumDao) {
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.ctlDiagCodeDao = ctlDiagCodeDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
    }

    /**
     * Build the view model for the active request.
     *
     * @param request live request — supplies {@code billingform},
     *                {@code reportAction}, and {@code errorMessage}
     * @return populated view model
     */
    public ManageBillingformViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        ManageBillingformViewModel.Builder b = ManageBillingformViewModel.builder();

        String defaultView = CarlosProperties.getInstance().getProperty("default_view", "");
        String clinicView = request.getParameter("billingform") == null
                ? defaultView : request.getParameter("billingform");
        String reportAction = request.getParameter("reportAction") == null
                ? "" : request.getParameter("reportAction");
        b.clinicView(clinicView).reportAction(reportAction);

        // Service-type drop-down options (rendered in every tab).
        List<ManageBillingformViewModel.ServiceTypeOption> serviceTypes = new ArrayList<>();
        List<Object[]> billingServices = ctlBillingServiceDao.findServiceTypes();
        if (billingServices != null) {
            for (Object[] row : billingServices) {
                String code = row[0] == null ? "" : String.valueOf(row[0]);
                String name = row[1] == null ? "" : String.valueOf(row[1]);
                serviceTypes.add(new ManageBillingformViewModel.ServiceTypeOption(code, name));
            }
        }
        b.serviceTypes(serviceTypes);

        // dx-code tab: 45-slot grid.
        if ("dxcode".equals(reportAction)) {
            b.dxCodes(buildDxCodes(clinicView));
        }

        // premium tab: 10 add slots + paginated delete grid.
        if ("***".equals(clinicView)) {
            b.premiumAddSlots(rangeInclusive(1, 10));
            b.premiumDeleteRows(buildPremiumDeleteRows());
        }

        // service-code tab: 3 groups of 20 slots each.
        if ("servicecode".equals(reportAction)) {
            ServiceGroupResult sg = buildServiceGroups(clinicView);
            b.serviceGroups(sg.groups);
            b.currentServiceTypeName(sg.lastSeenServiceTypeName);
        }

        // Unique service-types table for the manageBillingform_add.jspf
        // right-hand panel. Mirrors the legacy
        // ctlBillingServiceDao.getUniqueServiceTypes() / "failed!!!" branch.
        List<Object[]> uniqueRowsRaw = ctlBillingServiceDao.getUniqueServiceTypes();
        boolean uniqueLoaded = uniqueRowsRaw != null;
        List<ManageBillingformViewModel.UniqueServiceTypeRow> uniqueRows = new ArrayList<>();
        if (uniqueLoaded) {
            for (Object[] row : uniqueRowsRaw) {
                String typeId = row[0] == null ? "" : String.valueOf(row[0]);
                String typeName = row[1] == null ? "" : String.valueOf(row[1]);
                uniqueRows.add(new ManageBillingformViewModel.UniqueServiceTypeRow(typeId, typeName));
            }
        }
        b.uniqueServiceTypes(uniqueRows).uniqueServiceTypesLoaded(uniqueLoaded);

        // Echo the request parameters that the JSPs need to round-trip via
        // hidden inputs / display values, keeping the body free of scriptlets.
        Map<String, String> echoes = new LinkedHashMap<>();
        putEcho(echoes, request, "billingform");
        putEcho(echoes, request, "errorMessage");
        b.requestParamEchoes(echoes);

        return b.build();
    }

    private List<ManageBillingformViewModel.DxCodeSlot> buildDxCodes(String serviceType) {
        List<ManageBillingformViewModel.DxCodeSlot> slots = new ArrayList<>(45);
        String[] codes = new String[45];
        for (int i = 0; i < 45; i++) codes[i] = "";
        if (serviceType != null && !serviceType.isEmpty()) {
            List<CtlDiagCode> cdcs = ctlDiagCodeDao.findByServiceType(serviceType);
            if (cdcs != null) {
                int idx = 0;
                for (CtlDiagCode cdc : cdcs) {
                    if (idx >= 45) break;
                    codes[idx] = nullToEmpty(cdc.getDiagnosticCode());
                    idx++;
                }
            }
        }
        for (int i = 0; i < 45; i++) {
            slots.add(new ManageBillingformViewModel.DxCodeSlot(i, codes[i]));
        }
        return slots;
    }

    private List<ManageBillingformViewModel.PremiumDeleteRow> buildPremiumDeleteRows() {
        List<CtlBillingServicePremium> active = ctlBillingServicePremiumDao.findByStatus("A");
        if (active == null) active = List.of();
        int rCount = active.size();

        String[] codes = new String[rCount];
        String[] descs = new String[rCount];
        for (int j = 0; j < rCount; j++) {
            codes[j] = "";
            descs[j] = "";
        }

        List<Object[]> results = ctlBillingServicePremiumDao.search_ctlpremium("A");
        if (results != null) {
            int idx = 0;
            for (Object[] r : results) {
                if (idx >= rCount) break;
                codes[idx] = r[0] == null ? "" : (String) r[0];
                descs[idx] = r[1] == null ? "" : (String) r[1];
                idx++;
            }
        }

        // Legacy: while (tCount < rCount-3) { tCount += 3 } — chunks of 3.
        // This produces floor((rCount-1) / 3) iterations when rCount-3 > 0.
        // Translating into chunked rows: only print the chunks where i+2 < rCount.
        List<ManageBillingformViewModel.PremiumDeleteRow> rows = new ArrayList<>();
        int tCount = 0;
        while (tCount < rCount - 3) {
            int t1 = tCount;
            int t2 = tCount + 1;
            int t3 = tCount + 2;
            tCount += 3;
            rows.add(new ManageBillingformViewModel.PremiumDeleteRow(
                    codes[t1], descs[t1],
                    codes[t2], descs[t2],
                    codes[t3], descs[t3]));
        }
        return rows;
    }

    private static final class ServiceGroupResult {
        final List<ManageBillingformViewModel.ServiceGroup> groups;
        final String lastSeenServiceTypeName;
        ServiceGroupResult(List<ManageBillingformViewModel.ServiceGroup> groups, String name) {
            this.groups = groups;
            this.lastSeenServiceTypeName = name;
        }
    }

    private ServiceGroupResult buildServiceGroups(String serviceType) {
        List<ManageBillingformViewModel.ServiceGroup> groups = new ArrayList<>(3);
        String lastTypeName = "";
        for (int i = 1; i < 4; i++) {
            String[] codes = new String[20];
            String[] orders = new String[20];
            for (int j = 0; j < 20; j++) {
                codes[j] = "";
                orders[j] = "";
            }
            String groupName = "";
            if (serviceType != null && !serviceType.isEmpty()) {
                List<CtlBillingService> results =
                        ctlBillingServiceDao.findByServiceGroupAndServiceTypeId("Group" + i, serviceType);
                if (results != null) {
                    int idx = 0;
                    for (CtlBillingService r : results) {
                        if (idx >= 20) break;
                        if (r.getServiceTypeName() != null) {
                            lastTypeName = r.getServiceTypeName();
                        }
                        codes[idx] = nullToEmpty(r.getServiceCode());
                        orders[idx] = String.valueOf(r.getServiceOrder());
                        if (r.getServiceGroupName() != null) {
                            groupName = r.getServiceGroupName();
                        }
                        idx++;
                    }
                }
            }
            List<ManageBillingformViewModel.ServiceSlot> slots = new ArrayList<>(20);
            for (int k = 0; k < 20; k++) {
                slots.add(new ManageBillingformViewModel.ServiceSlot(k, codes[k], orders[k]));
            }
            groups.add(new ManageBillingformViewModel.ServiceGroup(i, groupName, slots));
        }
        return new ServiceGroupResult(groups, lastTypeName);
    }

    private static List<Integer> rangeInclusive(int from, int to) {
        List<Integer> out = new ArrayList<>(to - from + 1);
        for (int i = from; i <= to; i++) out.add(i);
        return out;
    }

    private static void putEcho(Map<String, String> echoes, HttpServletRequest request, String name) {
        String v = request.getParameter(name);
        echoes.put(name, v == null ? "" : v);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
