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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaSummaryViewModel;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaReportService;

/**
 * Builds the {@link OnRaSummaryViewModel} for {@code onGenRASummary.jsp}.
 * Hoists the inline scriptlet logic the JSP body used to perform: provider
 * list lookup, OB/CO billing-no lookups, and the
 * {@link BillingRaReportService#getRASummary} aggregation.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class OnRaSummaryViewModelAssembler {

    private static final String OB_CODES =
            "'P006A','P020A','P022A','P028A','P023A','P007A','P009A','P011A','P008B','P018B','E502A','C989A','E409A','E410A','E411A','H001A'";
    private static final String COLPOSCOPY_CODES =
            "'A004A','A005A','Z731A','Z666A','Z730A','Z720A'";

    private final BillingRaReportService prep;

    public OnRaSummaryViewModelAssembler(BillingRaReportService prep) {
        this.prep = prep;
    }

    /**
     * Build the view model for the requested RA report. Invokes the same
     * {@link BillingRaReportService#getRASummary} aggregation the JSP body used to
     * perform. The action persists the recalculated totals via a service after
     * assembly so this class remains read-only.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OnRaSummaryViewModel assemble(String raNoParam, String proNoParam) {
        OnRaSummaryViewModel.Builder builder = OnRaSummaryViewModel.builder()
                .multisitesEnabled(IsPropertiesOn.isMultisitesEnable());

        if (raNoParam == null || raNoParam.isEmpty()) {
            return builder.build();
        }

        String selectedProvider = (proNoParam == null || proNoParam.isEmpty()) ? "all" : proNoParam;
        builder.raNo(raNoParam).selectedProviderOhip(selectedProvider);

        builder.providerOptions(loadProviderOptions(prep, raNoParam));

        List<String> obBillingNos = prep.getRABillingNo4Code(raNoParam, OB_CODES);
        List<String> coBillingNos = prep.getRABillingNo4Code(raNoParam, COLPOSCOPY_CODES);
        Map totalsAccumulator = new HashMap();
        List<Properties> rows = (List<Properties>) prep.getRASummary(raNoParam, selectedProvider,
                obBillingNos, coBillingNos, totalsAccumulator);
        builder.summaryRows(rows);

        BigDecimal localTotal = (BigDecimal) totalsAccumulator.getOrDefault("xml_local", BigDecimal.ZERO);
        BigDecimal payTotal = (BigDecimal) totalsAccumulator.getOrDefault("xml_total", BigDecimal.ZERO);
        BigDecimal otherTotal = (BigDecimal) totalsAccumulator.getOrDefault("xml_other_total", BigDecimal.ZERO);
        BigDecimal obTotal = (BigDecimal) totalsAccumulator.getOrDefault("xml_ob_total", BigDecimal.ZERO);
        BigDecimal coTotal = (BigDecimal) totalsAccumulator.getOrDefault("xml_co_total", BigDecimal.ZERO);
        Object partialCountRaw = totalsAccumulator.getOrDefault("xml_partial_count", 0);
        int partialCount = partialCountRaw instanceof Integer ? (Integer) partialCountRaw : 0;
        builder.localTotal(localTotal).payTotal(payTotal).otherTotal(otherTotal)
                .obTotal(obTotal).coTotal(coTotal).unreadableRowCount(partialCount);

        return builder.build();
    }

    @SuppressWarnings("rawtypes")
    private static List<OnRaSummaryViewModel.ProviderOption> loadProviderOptions(
            BillingRaReportService prep, String raNo) {
        List raw = prep.getProviderListFromRAReport(raNo);
        List<OnRaSummaryViewModel.ProviderOption> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            Properties prop = (Properties) o;
            out.add(new OnRaSummaryViewModel.ProviderOption(
                    prop.getProperty("providerohip_no", ""),
                    prop.getProperty("last_name", ""),
                    prop.getProperty("first_name", "")));
        }
        return out;
    }

}
