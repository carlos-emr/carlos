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
import java.util.LinkedHashSet;
import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import static io.github.carlos_emr.carlos.billings.ca.on.support.BillingDomIdTokens.sanitize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
public class BillingOnFormServiceGridComposer {

    /**
     * Whitelist for {@code displayStyle} values rendered into a service-grid
     * {@code <td style="...">} attribute. Only {@code property:value;...}
     * shape is allowed. Implemented as a linear scan so malformed CSS cannot
     * trigger regex backtracking.
     */
    static boolean isSafeInlineStyle(String style) {
        if (style == null || style.isBlank()) {
            return false;
        }
        int i = 0;
        int len = style.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(style.charAt(i))) {
                i++;
            }
            if (i >= len) {
                return true;
            }
            int propertyStart = i;
            if (i >= len || !isAsciiLetter(style.charAt(i))) {
                return false;
            }
            i++;
            while (i < len) {
                char c = style.charAt(i);
                if (!isAsciiLetter(c) && c != '-') {
                    break;
                }
                i++;
            }
            if (i == propertyStart) {
                return false;
            }
            while (i < len && Character.isWhitespace(style.charAt(i))) {
                i++;
            }
            if (i >= len || style.charAt(i) != ':') {
                return false;
            }
            i++;
            while (i < len && Character.isWhitespace(style.charAt(i))) {
                i++;
            }
            int valueStart = i;
            while (i < len && style.charAt(i) != ';') {
                char c = style.charAt(i);
                if (c == '"' || c == '\\') {
                    return false;
                }
                i++;
            }
            if (i == valueStart || style.substring(valueStart, i).isBlank()) {
                return false;
            }
            if (i < len && style.charAt(i) == ';') {
                i++;
            }
        }
        return true;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final BillingServiceDao billingServiceDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final CSSStylesDAO cssStylesDAO;
    private final CodeFilterManager codeFilterManager;
    private final CtlBillingTypeDao ctlBillingTypeDao;
    private final DiagnosticCodeDao diagnosticCodeDao;

    public BillingOnFormServiceGridComposer(CtlBillingServiceDao ctlBillingServiceDao,
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    void compose(BillingOnFormViewModel.Builder b,
                 String ctlBillForm,
                 java.util.Date filterDate,
                 java.util.Date billRefDate,
                 Demographic demo) {
        LinkedHashMap<String, List<BillingOnFormViewModel.ServiceCodeEntry>> serviceCodesMap
                = new LinkedHashMap<>();
        List<String> serviceTypeCodes = new ArrayList<>();
        LinkedHashMap<String, String> serviceTitleMap = new LinkedHashMap<>();
        LinkedHashSet<String> premiumCodes = new LinkedHashSet<>();
        String resolvedBillFormName = "";

        // One DAO roundtrip for the service-type rows; reused below for the
        // billing-form menu so we don't issue an identical findServiceTypesByStatus
        // a second time during the same render.
        List<io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceTypeRow> serviceTypeRows =
                ctlBillingServiceDao.findServiceTypesByStatus("A");
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceTypeRow typeRow : serviceTypeRows) {
            // Skip rows where the code column is empty — would render id="" in
            // the DOM and billForm= in click-through URLs.
            if (typeRow.serviceType().isEmpty()) {
                MiscUtils.getLogger().warn(
                        "ctl_billservice service-type row has empty code column; skipping");
                continue;
            }
            // Sanitize the code at ingest so the same string flows through
            // every downstream surface (HTML element ids, EL ${st}, scriptlet
            // <%=st%>, JS args).
            String ctlcode = sanitize(typeRow.serviceType());
            String ctlcodename = typeRow.serviceTypeName();

            if (ctlcode.equals(ctlBillForm)) {
                resolvedBillFormName = ctlcodename;
            }
            serviceTypeCodes.add(ctlcode);

            for (String groupName : new String[] { "Group1", "Group2", "Group3" }) {
                List<BillingOnFormViewModel.ServiceCodeEntry> groupEntries = new ArrayList<>();
                for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceCodeMagicRow row :
                        billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(ctlcode, groupName, billRefDate)) {
                    if (!codeFilterManager.isCodeValid(row.serviceCode(), null, false, filterDate, demo)) {
                        continue;
                    }
                    String displayStyle = "";
                    if (row.displayStyle() != null) {
                        // TODO(perf): one DB roundtrip per service code (~240
                        // for an 8-service-type x 3-group install). Pre-existing
                        // scriptlet pattern; future refactor should batch via
                        // cssStylesDao.findAll() into a HashMap<String, CssStyle>
                        // outside the loop.
                        CssStyle cssStyle = cssStylesDAO.find(row.displayStyle());
                        if (cssStyle != null && cssStyle.getStyle() != null) {
                            // Allow only the simple "property:value;property:value;"
                            // shape so a malformed DB row can't break out of the
                            // attribute (CSS injection).
                            if (isSafeInlineStyle(cssStyle.getStyle())) {
                                displayStyle = cssStyle.getStyle();
                            } else {
                                MiscUtils.getLogger().warn(
                                        "Dropped malformed inline CSS for service code {}: {}",
                                        row.serviceCode(), cssStyle.getStyle());
                            }
                        }
                    }
                    groupEntries.add(new BillingOnFormViewModel.ServiceCodeEntry(
                            row.serviceCode(),
                            row.description().isEmpty() ? "N/A" : row.description(),
                            row.value(),
                            row.percentage(),
                            row.serviceType(),
                            row.serviceGroupName(),
                            displayStyle,
                            row.sliFlag()));
                    serviceTitleMap.put(
                            groupName.toLowerCase() + "_" + ctlcode,
                            row.serviceGroupName());
                }
                if (!groupEntries.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (BillingOnFormViewModel.ServiceCodeEntry e : groupEntries) {
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
        List<BillingOnFormViewModel.BillingFormMenuEntry> billingForms = new ArrayList<>();
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceTypeRow typeRow : serviceTypeRows) {
            if (typeRow.serviceType().isEmpty()) {
                continue; // already logged in the grid loop above
            }
            String menuCode = sanitize(typeRow.serviceType());
            String menuName = typeRow.serviceTypeName();
            String menuBillType = "";
            for (CtlBillingType t : ctlBillingTypeDao.findByServiceType(menuCode)) {
                menuBillType = t.getBillType();
            }
            billingForms.add(new BillingOnFormViewModel.BillingFormMenuEntry(
                    menuCode, menuName, nullToEmpty(menuBillType)));
        }
        b.billingForms(billingForms);

        // Dx codes grouped by service type (for Layer2 search panels).
        LinkedHashMap<String, List<BillingOnFormViewModel.DxCodeEntry>> dxByType =
                new LinkedHashMap<>();
        for (String st : serviceTypeCodes) {
            List<BillingOnFormViewModel.DxCodeEntry> entries = new ArrayList<>();
            for (io.github.carlos_emr.carlos.commn.dao.projection.DiagnosticCodeRow row :
                    diagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType(st)) {
                entries.add(new BillingOnFormViewModel.DxCodeEntry(
                        st,
                        row.diagnosticCode(),
                        row.description()));
            }
            dxByType.put(st, entries);
        }
        b.dxCodesByServiceType(dxByType);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
