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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRASummaryViewModel;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRAPrep;

/**
 * Builds the {@link OnGenRASummaryViewModel} for {@code onGenRASummary.jsp} and
 * runs the RA-header content audit merge. Hoists the inline scriptlet logic
 * the JSP body used to perform: provider list lookup, OB/CO billing-no
 * lookups, the {@link BillingRAPrep#getRASummary} aggregation, and the
 * {@link RaHeaderDao#merge} write that updated the RA header's content XML
 * with the recalculated totals.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
public class OnGenRASummaryDataAssembler {

    private static final String OB_CODES =
            "'P006A','P020A','P022A','P028A','P023A','P007A','P009A','P011A','P008B','P018B','E502A','C989A','E409A','E410A','E411A','H001A'";
    private static final String COLPOSCOPY_CODES =
            "'A004A','A005A','Z731A','Z666A','Z730A','Z720A'";

    private final RaHeaderDao raHeaderDao;
    private final BillingRAPrep prep;

    public OnGenRASummaryDataAssembler(RaHeaderDao raHeaderDao, BillingRAPrep prep) {
        this.raHeaderDao = raHeaderDao;
        this.prep = prep;
    }

    /**
     * Build the view model for the requested RA report. Invokes the same
     * {@link BillingRAPrep#getRASummary} aggregation the JSP body used to
     * perform, and merges the recalculated totals back into the
     * {@link RaHeader#getContent} XML so the audit trail stays current.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OnGenRASummaryViewModel assemble(String raNoParam, String proNoParam) {
        OnGenRASummaryViewModel.Builder builder = OnGenRASummaryViewModel.builder()
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
        builder.localTotal(localTotal).payTotal(payTotal).otherTotal(otherTotal)
                .obTotal(obTotal).coTotal(coTotal);

        mergeRaHeaderTotals(raNoParam, localTotal, payTotal, otherTotal, obTotal, coTotal);

        return builder.build();
    }

    @SuppressWarnings("rawtypes")
    private static List<OnGenRASummaryViewModel.ProviderOption> loadProviderOptions(
            BillingRAPrep prep, String raNo) {
        List raw = prep.getProviderListFromRAReport(raNo);
        List<OnGenRASummaryViewModel.ProviderOption> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            Properties prop = (Properties) o;
            out.add(new OnGenRASummaryViewModel.ProviderOption(
                    prop.getProperty("providerohip_no", ""),
                    prop.getProperty("last_name", ""),
                    prop.getProperty("first_name", "")));
        }
        return out;
    }

    private void mergeRaHeaderTotals(String raNoStr, BigDecimal localTotal, BigDecimal payTotal,
                                      BigDecimal otherTotal, BigDecimal obTotal,
                                      BigDecimal coTotal) {
        int raNo;
        try {
            raNo = Integer.parseInt(raNoStr);
        } catch (NumberFormatException e) {
            return;
        }
        RaHeader header = raHeaderDao.find(raNo);
        if (header == null) return;

        String content = header.getContent();
        String transaction = SxmlMisc.getXmlContent(content,
                "<xml_transaction>", "</xml_transaction>");
        String balanceFwd = SxmlMisc.getXmlContent(content,
                "<xml_balancefwd>", "</xml_balancefwd>");

        StringBuilder rebuilt = new StringBuilder();
        rebuilt.append("<xml_transaction>").append(nullSafe(transaction)).append("</xml_transaction>");
        rebuilt.append("<xml_balancefwd>").append(nullSafe(balanceFwd)).append("</xml_balancefwd>");
        rebuilt.append("<xml_local>").append(localTotal).append("</xml_local>");
        rebuilt.append("<xml_total>").append(payTotal).append("</xml_total>");
        rebuilt.append("<xml_other_total>").append(otherTotal).append("</xml_other_total>");
        rebuilt.append("<xml_ob_total>").append(obTotal).append("</xml_ob_total>");
        rebuilt.append("<xml_co_total>").append(coTotal).append("</xml_co_total>");

        header.setContent(rebuilt.toString());
        raHeaderDao.merge(header);
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
