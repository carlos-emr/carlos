/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
/**
 * Read-side queries that back the RA report screens: provider list for an
 * RA number, per-provider RA summary rows ({@code OBbilling_no} +
 * {@code CObilling_no}), and the no-error vs error-coded bill split used
 * by the operator's correction queue. {@code updateBillingStatus} is the
 * one write operation, used during interactive settlement.
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingRaReportService {
    private static final Logger _logger = MiscUtils.getLogger();
    private final BillingOnRaService dbObj;

    BillingRaReportService(BillingOnRaService dbObj) {
        this.dbObj = dbObj;
    }

    // ret - ArrayList = || ||
    /**
     * Returns provider list from r a report.
     *
     * @param raNo String
     * @return List
     */
    public List getProviderListFromRAReport(String raNo) {
        List ret = dbObj.getProviderListFromRAReport(raNo);
        return ret;
    }

    /**

     * Returns r a error report.

     *

     * @param raNo String

     * @param providerOhipNo String

     * @param notErrorCode String[]

     * @return List<Properties>

     */

    public List<Properties> getRAErrorReport(String raNo, String providerOhipNo, String[] notErrorCode) {
        List<Properties> ret = dbObj.getRAErrorReport(raNo, providerOhipNo, notErrorCode);
        return ret;
    }

    /**

     * Returns r a billing no code.

     *

     * @param raNo String

     * @param codes String

     * @return List<String>

     */

    public List<String> getRABillingNo4Code(String raNo, String codes) {
        List<String> ret = dbObj.getRABillingNo4Code(raNo, codes);
        return ret;
    }

    /**

     * Returns r a summary.

     *

     * @param raNo String

     * @param providerOhipNo String

     * @param OBbilling_no List

     * @param CObilling_no List

     * @return List

     */

    public List getRASummary(String raNo, String providerOhipNo, List OBbilling_no, List CObilling_no) {
        return getRASummary(raNo, providerOhipNo, OBbilling_no, CObilling_no, null);
    }

    /**

     * Returns r a summary.

     *

     * @param raNo String

     * @param providerOhipNo String

     * @param OBbilling_no List

     * @param CObilling_no List

     * @param map Map

     * @return List

     */

    public List getRASummary(String raNo, String providerOhipNo, List OBbilling_no, List CObilling_no, Map map) {
        List rett = new ArrayList();
        List ret = dbObj.getRASummary(raNo, providerOhipNo);
        BigDecimal BigCTotal = BillingMoney.zero();
        BigDecimal BigPTotal = BillingMoney.zero();
        BigDecimal BigOBTotal = BillingMoney.zero();
        BigDecimal BigCOTotal = BillingMoney.zero();
        BigDecimal BigHTotal = BillingMoney.zero();
        BigDecimal BigLocalHTotal = BillingMoney.zero();
        BigDecimal BigTotal = BillingMoney.zero();
        BigDecimal BigOTotal = BillingMoney.zero();
        BigDecimal BigLTotal = BillingMoney.zero();
        // Count of rows whose amountPay was flagged amountUnreadable upstream
        // (BillingOnRaService.getRASummary). Excluded from running totals AND
        // surfaced via map["xml_partial_count"] so
        // OnRaSummaryTotalsService.mergeTotals refuses to overwrite the
        // persisted RaHeader.content snapshot when partial — adding their
        // zero-coalesced amountPay would silently understate the
        // operator's reconciliation.
        int unreadableRowCount = 0;
        // Billing No Provider Patient HIN Service Date Service Code Invoiced :
        // new BigDecimal(0)
        // Paid Clinic Pay Hospital Pay OB Error
        for (int j = 0; j < ret.size(); j++) {
            Properties prop = (Properties) ret.get(j);
            // BillingOnRaService.getRASummary appends this marker when its
            // outer catch fires mid-iteration. Skip the marker as a row but
            // bump unreadableRowCount so the partial-totals contract trips.
            if ("true".equals(prop.getProperty(BillingOnRaService.LOAD_FAILURE_MARKER))) {
                unreadableRowCount++;
                continue;
            }
            String servicedate = prop.getProperty("servicedate");
            servicedate = servicedate.length() == 8 ? (servicedate.substring(0, 4) + "-" + servicedate.substring(4, 6)
                    + "-" + servicedate.substring(6)) : servicedate;
            prop.setProperty("servicedate", servicedate);

            String explain = prop.getProperty("explain");
            String amountsubmit = prop.getProperty("amountsubmit");
            String amountpay = prop.getProperty("amountpay");
            String location = prop.getProperty("location");
            String localServiceDate = prop.getProperty("localServiceDate");

            String demo_hin = prop.getProperty("demo_hin");
            String account = prop.getProperty("account");
            String clinicPay = "";
            String hospitalPay = "";
            String obPay = "";

            boolean rowAmountUnreadable = "true".equals(prop.getProperty("amountUnreadable"));
            if (rowAmountUnreadable) {
                unreadableRowCount++;
            }

            BigDecimal bdCFee = BillingMoney.amount(amountsubmit);
            BigCTotal = BigCTotal.add(bdCFee);

            // Skip the row from the per-amountpay totals when upstream flagged
            // it unreadable. The display row still renders (it shows up in
            // rett with the badge); only the running totals exclude it so the
            // grand total isn't silently low by N x $0.00.
            BigDecimal bdPFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
            BigPTotal = BigPTotal.add(bdPFee);
            String COflag = "0";
            String OBflag = "0";

            // set flag
            for (int i = 0; i < OBbilling_no.size(); i++) {
                String sqlRAOB = (String) OBbilling_no.get(i);
                if (sqlRAOB.compareTo(account) == 0) {
                    OBflag = "1";
                    break;
                }
            }
            for (int i = 0; i < CObilling_no.size(); i++) {
                String sqlRACO = (String) CObilling_no.get(i);
                if (sqlRACO.compareTo(account) == 0) {
                    COflag = "1";
                    break;
                }
            }

            // Same exclusion rationale as bdPFee above — every per-bucket
            // accumulator below skips an unreadable row's amountpay so the
            // bucketed totals don't silently undercount by zero-coalesce.
            if (OBflag.equals("1")) {
                BigDecimal bdOBFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
                BigOBTotal = BigOBTotal.add(bdOBFee);
                obPay = amountpay;
            } else {
                obPay = "N/A";
                // amountOB = "N/A";
            }

            if (COflag.equals("1")) {
                BigDecimal bdCOFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
                BigCOTotal = BigCOTotal.add(bdCOFee);
            } else {
                // amountCO = "N/A";
            }

            if (explain.compareTo("") == 0 || explain == null) {
                explain = "**";
                prop.setProperty("explain", explain);
            }

            if (location.compareTo("02") == 0) {
                BigDecimal bdHFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
                BigHTotal = BigHTotal.add(bdHFee);
                clinicPay = "N/A";
                hospitalPay = "N/A";

                // is local for hospital
                if (demo_hin.length() > 1 && servicedate.equals(localServiceDate)) {
                    BigLocalHTotal = BigLocalHTotal.add(bdHFee);
                    hospitalPay = amountpay;
                }
            } else {
                if (location.compareTo("00") == 0 && demo_hin.length() > 1 && servicedate.equals(localServiceDate)) {
                    BigDecimal bdFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
                    BigTotal = BigTotal.add(bdFee);
                    clinicPay = amountpay;
                    hospitalPay = "N/A";
                } else {
                    BigDecimal bdOFee = rowAmountUnreadable ? BigDecimal.ZERO : BillingMoney.amount(amountpay);
                    BigOTotal = BigOTotal.add(bdOFee);
                    clinicPay = "N/A";
                    hospitalPay = "N/A";
                }
            }
            prop.setProperty("clinicPay", clinicPay);
            prop.setProperty("hospitalPay", hospitalPay);
            prop.setProperty("obPay", obPay);
            rett.add(prop);
        }

        BigLTotal = BigLTotal.add(BigTotal);
        BigLTotal = BigLTotal.add(BigLocalHTotal);
        Properties prop = new Properties();
        prop.setProperty("servicecode", "Total");
        prop.setProperty("amountsubmit", BigCTotal.toString());
        prop.setProperty("amountpay", BigPTotal.toString());
        prop.setProperty("clinicPay", BigTotal.toString());
        prop.setProperty("hospitalPay", BigHTotal.toString());
        prop.setProperty("obPay", BigOBTotal.toString());
        rett.add(prop);

        if (map != null) {
            map.put("xml_local", BigLTotal);
            map.put("xml_total", BigPTotal);
            map.put("xml_other_total", BigOTotal);
            map.put("xml_ob_total", BigOBTotal);
            map.put("xml_co_total", BigCOTotal);
            // Surface the count so the assembler / view model / persistor
            // can refuse to overwrite RaHeader.content when partial.
            map.put("xml_partial_count", unreadableRowCount);
        }
        ///end


        return rett;
    }

    /**

     * Returns r a no error bill.

     *

     * @param raNo String

     * @param providerOhipNo String

     * @param noErrorCodes String

     * @param errorCodes String

     * @return List

     */

    public List getRANoErrorBill(String raNo, String providerOhipNo, String noErrorCodes, String errorCodes) {
        List ret = new ArrayList();
        List errorBill = dbObj.getRAError35(raNo, providerOhipNo, errorCodes); // !=i2,
        // ...
        List noErrorBill = dbObj.getRAError35(raNo, providerOhipNo, noErrorCodes); // =
        // I2,
        // ...
        for (int i = 0; i < noErrorBill.size(); i++) {
            String errorAccount = (String) noErrorBill.get(i);
            if (!errorBill.contains(errorAccount)) {
                ret.add(errorAccount);
            }
        }
        return ret;
    }

    /**

     * Updates billing status.

     *

     * @param id String

     * @param status String

     * @return boolean

     */

    public boolean updateBillingStatus(String id, String status) {
        boolean ret = dbObj.updateBillingStatus(id, status);
        return ret;
    }
}
