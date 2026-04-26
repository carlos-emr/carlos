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
import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONHistorySpecViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingReviewImpl;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link BillingONHistorySpecViewModel} for
 * {@code billingONHistorySpec.jsp}, the service-code-filtered billing
 * history popup.
 *
 * <p>Owns the date-range computation, the request-parameter echoes, and
 * the {@link JdbcBillingReviewImpl#getBillingHist} call. Mirrors the
 * service-code substring filter the legacy JSP applied client-side.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONHistorySpecDataAssembler {

    /**
     * Build the history-spec view model.
     *
     * @param demographicNo the demographic_no request parameter
     * @param demoName      the demo_name request parameter (display only)
     * @param dayParam      the "day" request parameter (string-of-int days
     *                      back from today; defaults to "0")
     * @param serviceCode   the serviceCode filter (substring match;
     *                      empty means no filter)
     * @return populated view model
     */
    public BillingONHistorySpecViewModel assemble(String demographicNo,
                                                  String demoName,
                                                  String dayParam,
                                                  String serviceCode) {
        String safeDemoNo = demographicNo == null ? "" : demographicNo;
        String safeDemoName = demoName == null ? "" : demoName;
        String safeDay = (dayParam == null || dayParam.isEmpty()) ? "0" : dayParam;
        String safeServiceCode = serviceCode == null ? "" : serviceCode;

        Calendar calendar = Calendar.getInstance();
        String strToday = calendar.get(Calendar.YEAR) + "-"
                + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DATE);

        int daysInt;
        try {
            daysInt = Integer.parseInt(safeDay);
        } catch (NumberFormatException e) {
            daysInt = 0;
        }
        calendar.add(Calendar.DATE, daysInt * (-1));
        String strStartDay = calendar.get(Calendar.YEAR) + "-"
                + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DATE);

        DateRange pDateRange = new DateRange(
                MyDateFormat.getSysDate(strStartDay),
                MyDateFormat.getSysDate(strToday));

        List<BillingONHistorySpecViewModel.HistoryRow> rows = new ArrayList<>();
        int nItems = 0;
        try {
            JdbcBillingReviewImpl dbObj = new JdbcBillingReviewImpl();
            @SuppressWarnings("rawtypes")
            List aL = dbObj.getBillingHist(safeDemoNo, 10000000, 0, pDateRange);
            for (int i = 0; i + 1 < aL.size(); i = i + 2) {
                BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(i);
                BillingItemData itObj = (BillingItemData) aL.get(i + 1);
                String strServiceCode = itObj.getService_code();
                if (!safeServiceCode.isEmpty()) {
                    if (strServiceCode == null || strServiceCode.indexOf(safeServiceCode) < 0) {
                        continue;
                    }
                }
                rows.add(new BillingONHistorySpecViewModel.HistoryRow(
                        String.valueOf(obj.getId()),
                        nullToEmpty(obj.getBilling_date()),
                        BillingDataHlp.propBillingType.getProperty(
                                nullToEmpty(obj.getStatus()), ""),
                        nullToEmpty(strServiceCode),
                        nullToEmpty(itObj.getDx()),
                        nullToEmpty(obj.getTotal())));
                nItems++;
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Error loading billing history (spec) for demographic_no=" + safeDemoNo, e);
        }

        return BillingONHistorySpecViewModel.builder()
                .demographicNo(safeDemoNo)
                .demoName(safeDemoName)
                .day(safeDay)
                .serviceCodeFilter(safeServiceCode)
                .todayStr(strToday)
                .startDayStr(strStartDay)
                .rows(rows)
                .itemCount(nItems)
                .build();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
