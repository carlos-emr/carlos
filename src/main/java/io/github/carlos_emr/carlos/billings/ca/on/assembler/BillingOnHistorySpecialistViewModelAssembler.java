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
import java.util.Calendar;
import java.util.List;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnHistorySpecialistViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link BillingOnHistorySpecialistViewModel} for
 * {@code billingONHistorySpec.jsp}, the service-code-filtered billing
 * history popup.
 *
 * <p>Owns the date-range computation, the request-parameter echoes, and
 * the {@link BillingOnClaimLoader#getBillingHist} call. Mirrors the
 * service-code substring filter the legacy JSP applied client-side.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnHistorySpecialistViewModelAssembler {

    private final BillingOnClaimLoader dbObj;

    /** Production constructor — Struts no-arg shape. */
    public BillingOnHistorySpecialistViewModelAssembler(BillingOnClaimLoader dbObj) {
        this.dbObj = dbObj;
    }

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
    public BillingOnHistorySpecialistViewModel assemble(String demographicNo,
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

        List<BillingOnHistorySpecialistViewModel.HistoryRow> rows = new ArrayList<>();
        int nItems = 0;
        boolean partial = false;
        try {
            @SuppressWarnings("rawtypes")
            List aL = dbObj.getBillingHist(safeDemoNo, 10000000, 0, pDateRange);
            for (int i = 0; i + 1 < aL.size(); i = i + 2) {
                BillingClaimHeaderDto obj = (BillingClaimHeaderDto) aL.get(i);
                BillingClaimItemDto itObj = (BillingClaimItemDto) aL.get(i + 1);
                String strServiceCode = itObj.getService_code();
                if (!safeServiceCode.isEmpty()) {
                    if (strServiceCode == null || strServiceCode.indexOf(safeServiceCode) < 0) {
                        continue;
                    }
                }
                rows.add(new BillingOnHistorySpecialistViewModel.HistoryRow(
                        String.valueOf(obj.getId()),
                        nullToEmpty(obj.getBilling_date()),
                        BillingOnConstants.propBillingType.getProperty(
                                nullToEmpty(obj.getStatus()), ""),
                        nullToEmpty(strServiceCode),
                        nullToEmpty(itObj.getDx()),
                        nullToEmpty(obj.getTotal())));
                nItems++;
            }
        } catch (RuntimeException e) {
            // Mid-iteration failure leaves rows incomplete; raise the partial
            // flag so the JSP can render a "data may be incomplete" banner
            // instead of presenting a normal-looking row count that operators
            // would interpret as the full history. Narrowed to RuntimeException
            // because nothing in the loop body throws checked exceptions.
            partial = true;
            MiscUtils.getLogger().error(
                    "Error loading billing history (spec) for demographic_no={}",
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(safeDemoNo), e);
        }

        return BillingOnHistorySpecialistViewModel.builder()
                .demographicNo(safeDemoNo)
                .demoName(safeDemoName)
                .day(safeDay)
                .serviceCodeFilter(safeServiceCode)
                .todayStr(strToday)
                .startDayStr(strStartDay)
                .rows(rows)
                .itemCount(nItems)
                .partial(partial)
                .build();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
