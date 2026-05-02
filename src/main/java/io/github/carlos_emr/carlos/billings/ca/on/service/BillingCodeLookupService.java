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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * CRUD-style lookup helper around Ontario billing service codes.
 *
 * <p>This service still supports the legacy search/edit screens, so several
 * methods intentionally return map/list structures that match the older JSP
 * and JavaScript callers rather than newer typed view models.</p>
 */
public class BillingCodeLookupService {

    private final BillingServiceDao billingServiceDao;

    public BillingCodeLookupService(BillingServiceDao billingServiceDao) {
        this.billingServiceDao = Objects.requireNonNull(billingServiceDao, "billingServiceDao");
    }

    /** Search service-code rows by code or description for the legacy maintenance UI. */
    public List<HashMap<String, String>> search(String str) {
        ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();

        for (BillingService bs : billingServiceDao.findByServiceCodeOrDescription(str)) {
            list.add(toCodeDataMap(bs));
        }
        return list;
    }

    /** Flatten one {@link BillingService} row into the map shape expected by older JSP/JavaScript callers. */
    public HashMap<String, String> toCodeDataMap(BillingService bs) {
        HashMap<String, String> h = new HashMap<String, String>();
        if (bs == null) {
            MiscUtils.getLogger().warn("Expected a billing service, but got null");
            return h;
        }

        h.put("service_compositecode", c(bs.getServiceCompositecode()));
        h.put("service_code", c(bs.getServiceCode()));
        h.put("description", c(bs.getDescription()));
        h.put("value", c(bs.getValue()));
        h.put("percentage", c(bs.getPercentage()));
        h.put("billingservice_date", c(ConversionUtils.toDateString(bs.getBillingserviceDate())));
        h.put("specialty", c(bs.getSpecialty()));
        h.put("region", c(bs.getRegion()));
        h.put("anaesthesia", c(bs.getAnaesthesia()));
        return h;
    }

    String c(String str) {
        return (str == null) ? "" : str;
    }

    /** Load the most recent billing-code row for a service code and return it in legacy map form. */
    public HashMap<String, String> searchBillingCode(String str) {
        HashMap<String, String> h = null;

        List<BillingService> bss = billingServiceDao.findMostRecentByServiceCode(str);

        for (BillingService bs : bss) {
            h = toCodeDataMap(bs);
        }

        if (h != null) {
            h.put("count", "" + bss.size());
        }

        return h;
    }

    /** Count how many stored revisions exist for one service code. */
    public int searchNumBillingCode(String str) {
        return billingServiceDao.findMostRecentByServiceCode(str).size();
    }

    /** Update description and/or value on a billing-service row identified by primary key. */
    public boolean editBillingCodeDesc(String desc, String val, String codeId) {
        BillingService bs = billingServiceDao.find(codeId);
        if (bs == null) {
            MiscUtils.getLogger().warn("Unable to find billing service for id " + codeId);
            return false;
        }

        if (desc != null) {
            bs.setDescription(desc);
        }

        if (val != null) {
            bs.setValue(val);
        }

        billingServiceDao.merge(bs);
        return true;
    }

    /** Convenience wrapper for value-only edits. */
    public boolean editBillingCode(String val, String codeId) {
        return editBillingCodeDesc(null, val, codeId);
    }

    /** Update all stored revisions for one service code on the given effective date. */
    public boolean editBillingCodeByServiceCode(String val, String codeId, String date) {
        for (BillingService bs : billingServiceDao.findByServiceCodeAndDate(codeId, ConversionUtils.fromDateString(date))) {
            bs.setValue(val);
            billingServiceDao.merge(bs);
        }
        return true;
    }

    /** Insert a new billing-service row using the legacy maintenance-screen parameter contract. */
    public boolean insertBillingCode(String value, String code, String date, String description, String termDate) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        BillingService bs = new BillingService();
        bs.setServiceCompositecode("");
        bs.setServiceCode(code);
        bs.setDescription(description);
        bs.setValue(value);
        bs.setPercentage("");
        bs.setBillingserviceDate(formatter.parse(date));
        bs.setSpecialty("");
        bs.setRegion("ON");
        bs.setAnaesthesia("00");
        bs.setTerminationDate(formatter.parse(termDate));
        bs.setGstFlag(false);
        bs.setSliFlag(false);
        billingServiceDao.persist(bs);

        return true;
    }
}
