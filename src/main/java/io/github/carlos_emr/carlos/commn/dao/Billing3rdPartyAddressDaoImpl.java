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

import java.util.Set;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.ON.model.Billing3rdPartyAddress;
import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class Billing3rdPartyAddressDaoImpl extends AbstractDaoImpl<Billing3rdPartyAddress> implements Billing3rdPartyAddressDao {

    /** Allowlisted column names for the WHERE clause search mode. */
    private static final Set<String> VALID_SEARCH_MODES = Set.of(
        "search_name", "company_name", "attention", "address", "city", "province",
        "postcode", "telephone", "fax"
    );

    /** Allowlisted column names for the ORDER BY clause. */
    private static final Set<String> VALID_ORDER_BY = Set.of(
        "id", "company_name", "attention", "address", "city", "province",
        "postcode", "telephone", "fax"
    );

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

    @NativeSql("billing_on_3rdPartyAddress")
    public List<Billing3rdPartyAddress> findAddresses(String searchModeParam, String orderByParam, String keyword, String limit1, String limit2) {
        String search_mode = searchModeParam == null ? "search_name" : searchModeParam;
        // Validate search_mode against allowlist
        if (!VALID_SEARCH_MODES.contains(search_mode)) {
            search_mode = "search_name";
        }

        String orderBy = orderByParam == null ? "company_name" : orderByParam;
        // Validate orderBy against allowlist
        if (!VALID_ORDER_BY.contains(orderBy)) {
            orderBy = "company_name";
        }

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
            where = search_mode + " like :searchMode";
            params.put("searchMode", keyword + "%");
        }

        // Parse limit values to int to prevent SQL injection
        int intLimit1;
        int intLimit2;
        try {
            intLimit1 = limit1 != null ? Integer.parseInt(limit1) : 0;
        } catch (NumberFormatException e) {
            intLimit1 = 0;
        }
        try {
            intLimit2 = limit2 != null ? Integer.parseInt(limit2) : 20;
        } catch (NumberFormatException e) {
            intLimit2 = 20;
        }
        // Ensure non-negative values
        if (intLimit1 < 0) intLimit1 = 0;
        if (intLimit2 < 1) intLimit2 = 20;

        String sql = "select * from billing_on_3rdPartyAddress where " + where + " order by " + orderBy + " limit "
                + intLimit1 + "," + intLimit2;

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
