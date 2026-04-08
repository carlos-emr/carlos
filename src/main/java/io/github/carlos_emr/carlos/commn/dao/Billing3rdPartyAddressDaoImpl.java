/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.ON.model.Billing3rdPartyAddress;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class Billing3rdPartyAddressDaoImpl extends AbstractDaoImpl<Billing3rdPartyAddress> implements Billing3rdPartyAddressDao {

    public Billing3rdPartyAddressDaoImpl() {
        super(Billing3rdPartyAddress.class);
    }

    public List<Billing3rdPartyAddress> findAll() {
        Query q = entityManager.createQuery("select b from Billing3rdPartyAddress b");
        List<Billing3rdPartyAddress> results = q.getResultList();
        return results;
    }

    public List<Billing3rdPartyAddress> findByCompanyName(String companyName) {
        Query q = entityManager.createQuery("select b from Billing3rdPartyAddress b where b.companyName = ?1");
        q.setParameter(1, companyName);
        List<Billing3rdPartyAddress> results = q.getResultList();
        return results;
    }

    /**
     * Allowed values for the {@code search_mode} request parameter.
     * The special value {@code "search_name"} uses a compound name-based filter;
     * other values map directly to a column name in the native query.
     */
    private static final java.util.Set<String> ALLOWED_SEARCH_MODES = java.util.Set.of(
            "search_name", "postcode", "telephone");

    /** Allowed column names for the ORDER BY clause. */
    private static final java.util.Set<String> ALLOWED_ORDER_BY = java.util.Set.of(
            "company_name", "postcode", "telephone", "address", "city", "province");

    @NativeSql("billing_on_3rdPartyAddress")
    public List<Billing3rdPartyAddress> findAddresses(String searchModeParam, String orderByParam, String keyword, String limit1, String limit2) {
        String search_mode = searchModeParam == null ? "search_name" : searchModeParam;
        String orderBy = orderByParam == null ? "company_name" : orderByParam;

        if (!ALLOWED_SEARCH_MODES.contains(search_mode)) {
            throw new IllegalArgumentException("Invalid search mode: " + search_mode
                    + ". Allowed: " + ALLOWED_SEARCH_MODES);
        }
        if (!ALLOWED_ORDER_BY.contains(orderBy)) {
            throw new IllegalArgumentException("Invalid order-by column: " + orderBy
                    + ". Allowed: " + ALLOWED_ORDER_BY);
        }

        int intLimit1;
        int intLimit2;
        try {
            intLimit1 = limit1 != null ? Integer.parseInt(limit1) : 0;
            intLimit2 = limit2 != null ? Integer.parseInt(limit2) : 20;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid limit parameter: limit1=" + limit1
                    + ", limit2=" + limit2, e);
        }
        // Clamp to safe bounds: offset must be non-negative, page size must be 1–200
        if (intLimit1 < 0) intLimit1 = 0;
        if (intLimit2 < 1 || intLimit2 > 200) intLimit2 = 20;

        String where = "";
        Map<String, Object> params = new HashMap<String, Object>();
        if ("search_name".equals(search_mode)) {
            if (keyword == null) {
                keyword = "";
            }

            String[] temp = keyword.split("\\,\\p{Space}*");
            if (temp.length > 1) {
                where = "company_name like :compName0 and company_name like :compName1";
                params.put("compName0", temp[0] + "%");
                params.put("compName1", temp[1] + "%");
            } else {
                where = "company_name like :compName0";
                params.put("compName0", temp[0] + "%");
            }
        } else {
            if (keyword == null) {
                keyword = "";
            }
            where = search_mode + " like :searchMode";
            params.put("searchMode", keyword + "%");
        }

        String sql = "select * from billing_on_3rdPartyAddress where " + where + " order by " + orderBy
                + " limit " + intLimit1 + "," + intLimit2;

        try {
            Query q = entityManager.createNativeQuery(sql, modelClass);
            for (Entry<String, Object> o : params.entrySet()) {
                q.setParameter(o.getKey(), o.getValue());
            }
            return q.getResultList();
        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
            return new ArrayList<Billing3rdPartyAddress>();
        }
    }
}
