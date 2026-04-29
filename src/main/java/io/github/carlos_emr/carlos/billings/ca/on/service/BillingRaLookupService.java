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

/**
 * @author jay
 */
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

    public ArrayList<HashMap<String, String>> getRADataIntern(String billingNo, String service_date, String ohip_no) {
        ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
        for (RaDetail ra : dao.findByBillingNoServiceDateAndProviderNo(ConversionUtils.fromIntString(billingNo), service_date, ohip_no)) {
            list.add(getAsMap(ra));
        }
        return list;
    }

    public String getErrorCodes(ArrayList<HashMap<String, String>> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            sb.append(h.get("error_code"));
            sb.append(" ");
        }
        return sb.toString();
    }

    public String getAmountPaid(ArrayList<HashMap<String, String>> a) {
        BigDecimal total = BillingMoney.zero();
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            BigDecimal valueToAdd = BillingMoney.zero();
            try {
                valueToAdd = BillingMoney.amount(h.get("amountpay"));
            } catch (Exception badValueException) {
                MiscUtils.getLogger().debug(" Error calculating value for " + h.get("billing_no"));
                MiscUtils.getLogger().error("Error", badValueException);
            }
            total = total.add(valueToAdd);
        }
        return BillingMoney.format(total);
    }

    public String getAmountPaid(ArrayList<HashMap<String, String>> a, String billingNo, String serviceCode) {
        BigDecimal total = BillingMoney.zero();
        for (int i = 0; i < a.size(); i++) {
            HashMap<String, String> h = a.get(i);
            if (!(h.get("billing_no").equals(billingNo)) || !(h.get("service_code").equals(serviceCode))) {
                continue;
            }

            BigDecimal valueToAdd = BillingMoney.zero();
            try {
                valueToAdd = BillingMoney.amount(h.get("amountpay"));
            } catch (Exception badValueException) {
                MiscUtils.getLogger().debug(" Error calculating value for " + h.get("billing_no"));
                MiscUtils.getLogger().error("Error", badValueException);
            }
            total = total.add(valueToAdd);
        }
        return BillingMoney.format(total);
    }

    public boolean isErrorCode(String billingNo, String errorCode) {
        List<RaDetail> ras = dao.findByBillingNoAndErrorCode(ConversionUtils.fromIntString(billingNo), errorCode);
        return !ras.isEmpty();
    }
}
