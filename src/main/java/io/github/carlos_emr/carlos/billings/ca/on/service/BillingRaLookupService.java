/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

@Service
public class BillingRaLookupService {

    private final RaDetailDao dao;

    public BillingRaLookupService(RaDetailDao dao) {
        this.dao = dao;
    }

    // select * from radetail limit 100,10;
    // radetail_no | raheader_no | providerohip_no | billing_no | service_code |
    // service_count | hin | amountclaim | amountpay | service_date | error_code
    // | billtype |
    /**
     * Returns r a data.
     *
     * @param billingNo String
     * @return ArrayList<HashMap<String, String>>
     */
    public ArrayList<HashMap<String, String>> getRAData(String billingNo) {
        ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
        for (RaDetail ra : dao.findByBillingNo(ConversionUtils.fromIntString(billingNo))) {
            list.add(getAsMap(ra));
        }
        return list;
    }

    private HashMap<String, String> getAsMap(RaDetail ra) {
        HashMap<String, String> h = new HashMap<String, String>();
        h.put("radetail_no", ra.getId().toString());
        h.put("raheader_no", "" + ra.getRaHeaderNo());
        h.put("providerohip_no", ra.getProviderOhipNo());
        h.put("billing_no", "" + ra.getBillingNo());
        h.put("service_code", ra.getServiceCode());
        h.put("service_count", ra.getServiceCount());
        h.put("hin", ra.getHin());
        h.put("amountclaim", ra.getAmountClaim());
        h.put("amountpay", ra.getAmountPay());
        h.put("service_date", ra.getServiceDate());
        h.put("error_code", ra.getErrorCode());
        h.put("billtype", ra.getBillType());
        return h;
    }

    /**

     * Returns r a data intern.

     *

     * @param billingNo String

     * @param service_date String

     * @param ohip_no String

     * @return ArrayList<HashMap<String, String>>

     */

    public ArrayList<HashMap<String, String>> getRADataIntern(String billingNo, String service_date, String ohip_no) {
        ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
        for (RaDetail ra : dao.findByBillingNoServiceDateAndProviderNo(ConversionUtils.fromIntString(billingNo), service_date, ohip_no)) {
            list.add(getAsMap(ra));
        }
        return list;
    }

    /**

     * Returns error codes.

     *

     * @param a ArrayList<HashMap<String, String>>

     * @return String

     */

    public String getErrorCodes(ArrayList<HashMap<String, String>> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            sb.append(h.get("error_code"));
            sb.append(" ");
        }
        return sb.toString();
    }

    /**

     * Returns amount paid.

     *

     * @param a ArrayList<HashMap<String, String>>

     * @return String

     */

    public String getAmountPaid(ArrayList<HashMap<String, String>> a) {
        return getAmountPaidWithCount(a).formattedTotal();
    }

    /**
     * Same as {@link #getAmountPaid(ArrayList)} but returns the formatted
     * total alongside a count of rows whose {@code amountpay} couldn't be
     * parsed and was zero-coalesced. Callers that aggregate this method's
     * output into a grand total (e.g. {@code BillingOnStatusViewModelAssembler})
     * can propagate {@code unreadableCount} into their own
     * {@code unreadableTotalRowCount} so the JSP banner reflects the true
     * "rows excluded" count instead of silently understating the total.
     */
    public AmountPaidResult getAmountPaidWithCount(ArrayList<HashMap<String, String>> a) {
        BigDecimal total = BillingMoney.zero();
        int unreadable = 0;
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            BigDecimal valueToAdd = BillingMoney.zero();
            try {
                valueToAdd = BillingMoney.amount(h.get("amountpay"));
            } catch (Exception badValueException) {
                // Coalescing to zero understates the displayed total — log
                // with billing_no + raw amountpay so ops can reconcile the
                // pay-stub vs DB rather than puzzling at a generic "Error".
                unreadable++;
                MiscUtils.getLogger().error(
                        "Failed to parse amountpay {} for billing_no {}; coalescing to zero (total will understate)",
                        io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(h.get("amountpay")),
                        io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(h.get("billing_no")),
                        badValueException);
            }
            total = total.add(valueToAdd);
        }
        return new AmountPaidResult(BillingMoney.format(total), unreadable);
    }

    /**
     * Tuple result for {@link #getAmountPaidWithCount}: the same formatted
     * String the legacy callers consume, plus a count of zero-coalesced rows.
     */
    public record AmountPaidResult(String formattedTotal, int unreadableCount) { }

    /**

     * Returns amount paid.

     *

     * @param a ArrayList<HashMap<String, String>>

     * @param billingNo String

     * @param serviceCode String

     * @return String

     */

    public String getAmountPaid(ArrayList<HashMap<String, String>> a, String billingNo, String serviceCode) {
        return getAmountPaidWithCount(a, billingNo, serviceCode).formattedTotal();
    }

    /** Counted variant of {@link #getAmountPaid(ArrayList, String, String)}. */
    public AmountPaidResult getAmountPaidWithCount(ArrayList<HashMap<String, String>> a,
                                                   String billingNo, String serviceCode) {
        BigDecimal total = BillingMoney.zero();
        int unreadable = 0;
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            if (!(h.get("billing_no").equals(billingNo)) || !(h.get("service_code").equals(serviceCode))) {
                continue;
            }

            BigDecimal valueToAdd = BillingMoney.zero();
            try {
                valueToAdd = BillingMoney.amount(h.get("amountpay"));
            } catch (Exception badValueException) {
                unreadable++;
                MiscUtils.getLogger().error(
                        "Failed to parse amountpay {} for billing_no {} service_code {}; coalescing to zero",
                        io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(h.get("amountpay")),
                        io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(h.get("billing_no")),
                        io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(serviceCode),
                        badValueException);
            }
            total = total.add(valueToAdd);
        }
        return new AmountPaidResult(BillingMoney.format(total), unreadable);
    }

    /**

     * Returns error code as a boolean.

     *

     * @param billingNo String

     * @param errorCode String

     * @return boolean

     */

    public boolean isErrorCode(String billingNo, String errorCode) {
        List<RaDetail> ras = dao.findByBillingNoAndErrorCode(ConversionUtils.fromIntString(billingNo), errorCode);
        return !ras.isEmpty();
    }
}
