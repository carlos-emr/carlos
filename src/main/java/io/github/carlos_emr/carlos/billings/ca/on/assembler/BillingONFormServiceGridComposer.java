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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import static io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFormDataAssembler.sanitizeIdToken;

/**
 * Composer for the service-code grid + adjacent menu / dx-codes structures.
 * This is the largest single block in the legacy assembler (~120 lines)
 * and the one with the most concentrated DAO orchestration:
 *
 * <ul>
 *   <li>3 service-type groups × N service types: a {@link
 *       BillingServiceDao#findBillingServiceAndCtlBillingServiceByMagic}
 *       call per (group, service-type) pair</li>
 *   <li>Per-row {@link CSSStylesDAO#find} (TODO(perf): batch via
 *       {@code findAll} into a HashMap)</li>
 *   <li>Per-group premium-code lookup via
 *       {@link CtlBillingServicePremiumDao#findByServceCodes}</li>
 *   <li>Billing-form menu: one entry per service type, with its bill type
 *       (TODO(perf): N+1 — needs a batch
 *       {@code findByServiceTypes(Collection)} on the DAO)</li>
 *   <li>Dx codes grouped by service type for the Layer2 search panels</li>
 * </ul>
 *
 * <p>The composer reuses {@code serviceTypeRows} captured once from
 * {@link CtlBillingServiceDao#findServiceTypesByStatus} for both the grid
 * and the menu, instead of issuing two identical roundtrips.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingONFormServiceGridComposer {

    /**
     * Whitelist for {@code displayStyle} values rendered into a service-grid
     * {@code <td style="...">} attribute. Only {@code property:value;...}
     * shape allowed — drops malformed DB rows that could break out of the
     * attribute (CSS injection).
     */
    private static final Pattern SAFE_INLINE_STYLE = Pattern.compile(
            "^(?:[A-Za-z-]+\\s*:\\s*[^;\"\\\\]+;?\\s*)+$");

    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final BillingServiceDao billingServiceDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final CSSStylesDAO cssStylesDAO;
    private final CodeFilterManager codeFilterManager;
    private final CtlBillingTypeDao ctlBillingTypeDao;
    private final DiagnosticCodeDao diagnosticCodeDao;

    public BillingONFormServiceGridComposer(CtlBillingServiceDao ctlBillingServiceDao,
                                     BillingServiceDao billingServiceDao,
                                     CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                                     CSSStylesDAO cssStylesDAO,
                                     CodeFilterManager codeFilterManager,
                                     CtlBillingTypeDao ctlBillingTypeDao,
                                     DiagnosticCodeDao diagnosticCodeDao) {
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.billingServiceDao = billingServiceDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
        this.cssStylesDAO = cssStylesDAO;
        this.codeFilterManager = codeFilterManager;
        this.ctlBillingTypeDao = ctlBillingTypeDao;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    void compose(BillingONFormViewModel.Builder b,
                 String ctlBillForm,
                 java.util.Date filterDate,
                 java.util.Date billRefDate,
                 Demographic demo) {
        LinkedHashMap<String, List<BillingONFormViewModel.ServiceCodeEntry>> serviceCodesMap
                = new LinkedHashMap<>();
        List<String> serviceTypeCodes = new ArrayList<>();
        LinkedHashMap<String, String> serviceTitleMap = new LinkedHashMap<>();
        LinkedHashSet<String> premiumCodes = new LinkedHashSet<>();
        String resolvedBillFormName = "";

        // One DAO roundtrip for the service-type rows; reused below for the
        // billing-form menu so we don't issue an identical findServiceTypesByStatus
        // a second time during the same render.
        @SuppressWarnings("unchecked")
        List<Object[]> serviceTypeRows = (List<Object[]>) ctlBillingServiceDao.findServiceTypesByStatus("A");
        for (Object[] typeRow : serviceTypeRows) {
            // Skip rows where the code column is null — String.valueOf((Object)null)
            // returns the literal 4-character string "null", which would render
            // id="null" in the DOM and billForm=null in click-through URLs.
            if (typeRow == null || typeRow[1] == null) {
                MiscUtils.getLogger().warn(
                        "ctl_billservice service-type row has null code column; skipping");
                continue;
            }
            // Sanitize the code at ingest so the same string flows through
            // every downstream surface (HTML element ids, EL ${st}, scriptlet
            // <%=st%>, JS args).
            String ctlcode = sanitizeIdToken(String.valueOf(typeRow[1]));
            String ctlcodename = typeRow[0] == null ? "" : String.valueOf(typeRow[0]);

            if (ctlcode.equals(ctlBillForm)) {
                resolvedBillFormName = ctlcodename;
            }
            serviceTypeCodes.add(ctlcode);

            for (String groupName : new String[] { "Group1", "Group2", "Group3" }) {
                List<BillingONFormViewModel.ServiceCodeEntry> groupEntries = new ArrayList<>();
                for (Object[] o : billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(ctlcode, groupName, billRefDate)) {
                    BillingService svc = (BillingService) o[0];
                    CtlBillingService ctl = (CtlBillingService) o[1];
                    if (!codeFilterManager.isCodeValid(svc.getServiceCode(), null, false, filterDate, demo)) {
                        continue;
                    }
                    String displayStyle = "";
                    if (svc.getDisplayStyle() != null) {
                        // TODO(perf): one DB roundtrip per service code (~240
                        // for an 8-service-type x 3-group install). Pre-existing
                        // scriptlet pattern; future refactor should batch via
                        // cssStylesDao.findAll() into a HashMap<String, CssStyle>
                        // outside the loop.
                        CssStyle cssStyle = cssStylesDAO.find(svc.getDisplayStyle());
                        if (cssStyle != null && cssStyle.getStyle() != null) {
                            // Allow only the simple "property:value;property:value;"
                            // shape so a malformed DB row can't break out of the
                            // attribute (CSS injection).
                            if (SAFE_INLINE_STYLE.matcher(cssStyle.getStyle()).matches()) {
                                displayStyle = cssStyle.getStyle();
                            } else {
                                MiscUtils.getLogger().warn(
                                        "Dropped malformed inline CSS for service code {}: {}",
                                        svc.getServiceCode(), cssStyle.getStyle());
                            }
                        }
                    }
                    groupEntries.add(new BillingONFormViewModel.ServiceCodeEntry(
                            nullToEmpty(svc.getServiceCode()),
                            svc.getDescription() == null ? "N/A" : svc.getDescription(),
                            nullToEmpty(svc.getValue()),
                            nullToEmpty(svc.getPercentage()),
                            nullToEmpty(ctl.getServiceType()),
                            nullToEmpty(ctl.getServiceGroupName()),
                            displayStyle,
                            svc.getSliFlag()));
                    serviceTitleMap.put(
                            groupName.toLowerCase() + "_" + ctlcode,
                            ctl.getServiceGroupName());
                }
                if (!groupEntries.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (BillingONFormViewModel.ServiceCodeEntry e : groupEntries) {
                        codes.add(e.serviceCode());
                    }
                    for (CtlBillingServicePremium p : ctlBillingServicePremiumDao.findByServceCodes(codes)) {
                        premiumCodes.add(p.getServiceCode());
                    }
                }
                String mapKey = groupName.toLowerCase() + "_" + ctlcode;
                serviceCodesMap.put(mapKey, groupEntries);
            }
        }

        b.billingServiceCodesMap(serviceCodesMap)
                .listServiceType(serviceTypeCodes)
                .titleMap(serviceTitleMap)
                .premiumCodes(premiumCodes)
                .defaultBillFormName(resolvedBillFormName);

        // Default bill type for the selected service type.
        String resolvedBillType = "";
        for (CtlBillingType t : ctlBillingTypeDao.findByServiceType(ctlBillForm)) {
            resolvedBillType = t.getBillType();
        }
        b.defaultBillType(nullToEmpty(resolvedBillType));

        // Billing-form menu: one entry per service type, with its billType
        // (for Layer1 anchors + _billingForms JS array). Reuses the
        // serviceTypeRows captured above instead of re-querying the DAO.
        // TODO(perf): the inner ctlBillingTypeDao.findByServiceType call is N+1.
        List<BillingONFormViewModel.BillingFormMenuEntry> billingForms = new ArrayList<>();
        for (Object[] typeRow : serviceTypeRows) {
            if (typeRow == null || typeRow[1] == null) {
                continue; // already logged in the grid loop above
            }
            String menuCode = sanitizeIdToken(String.valueOf(typeRow[1]));
            String menuName = typeRow[0] == null ? "" : String.valueOf(typeRow[0]);
            String menuBillType = "";
            for (CtlBillingType t : ctlBillingTypeDao.findByServiceType(menuCode)) {
                menuBillType = t.getBillType();
            }
            billingForms.add(new BillingONFormViewModel.BillingFormMenuEntry(
                    menuCode, menuName, nullToEmpty(menuBillType)));
        }
        b.billingForms(billingForms);

        // Dx codes grouped by service type (for Layer2 search panels).
        LinkedHashMap<String, List<BillingONFormViewModel.DxCodeEntry>> dxByType =
                new LinkedHashMap<>();
        for (String st : serviceTypeCodes) {
            List<BillingONFormViewModel.DxCodeEntry> entries = new ArrayList<>();
            for (Object[] o : diagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType(st)) {
                DiagnosticCode dx = (DiagnosticCode) o[0];
                entries.add(new BillingONFormViewModel.DxCodeEntry(
                        st,
                        nullToEmpty(dx.getDiagnosticCode()),
                        nullToEmpty(dx.getDescription())));
            }
            dxByType.put(st, entries);
        }
        b.dxCodesByServiceType(dxByType);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
