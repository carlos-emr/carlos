/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ProviderData;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.ConversionUtils;

@Repository
public class ProviderDataDaoImpl extends AbstractDaoImpl<ProviderData> implements ProviderDataDao {

    private static final String ACTIVE_WHERE_CLAUSE = " p.status = '1'";

    public ProviderDataDaoImpl() {
        super(ProviderData.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ProviderData findByOhipNumber(String ohipNumber) {
        Query query;
        List<ProviderData> results;
        String sqlCommand = "SELECT x FROM ProviderData x WHERE x.ohipNo=?1";

        query = this.entityManager.createQuery(sqlCommand);
        query.setParameter(1, ohipNumber);

        results = query.getResultList();
        if (results.size() > 0) {
            return results.get(0);
        }
        // If we get here, there were no results
        return null;
    }

    @Override
    public ProviderData findByProviderNo(String providerNo) {

        String sqlCommand = "select x from ProviderData x where x.id=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderData> results = query.getResultList();

        if (results.size() > 0) return results.get(0);
        return null;
    }

    @Override
    public List<ProviderData> findByProviderNo(String providerNo, String status, int limit, int offset) {

        String sqlCommand = "From ProviderData p where p.id like ?1";

        if (status != null)
            sqlCommand += " and p.status = ?2 ";

        Query query = entityManager.createQuery(sqlCommand);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        query.setParameter(1, providerNo + "%");
        if (status != null)
            query.setParameter(2, status);

        @SuppressWarnings("unchecked")
        List<ProviderData> results = query.getResultList();

        return results;
    }

    @Override
    public List<ProviderData> findByProviderName(String searchStr, String status, int limit, int offset) {

        String queryString = "From ProviderData p where p.lastName like ?1 ";


        String[] name = searchStr.split(",");
        if (name.length == 2)
            queryString += " and p.firstName like ?2 ";

        if (status != null)
            queryString += " and p.status = ?3 ";

        Query query = entityManager.createQuery(queryString);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        query.setParameter(1, name[0].trim() + "%");
        if (name.length == 2)
            query.setParameter(2, name[1].trim() + "%");
        if (status != null)
            query.setParameter(3, status);

        List list = query.getResultList();
        return list;
    }

    @Override
    public List<ProviderData> findAllOrderByLastName() {

        String sqlCommand = "select x from ProviderData x order by x.lastName";

        Query query = entityManager.createQuery(sqlCommand);

        @SuppressWarnings("unchecked")
        List<ProviderData> results = query.getResultList();

        return results;
    }

    @Override
    public List<ProviderData> findByProviderSite(String providerNo) {

        String queryStr = "select * from provider p inner join providersite s on s.provider_no = p.provider_no "
                + " where s.site_id in (select site_id from providersite where provider_no=?1)";


        Query query = entityManager.createNativeQuery(queryStr, modelClass);
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderData> proList = query.getResultList();

        return proList;
    }

    @Override
    public List<Object[]> findProviderSecUserRoles(String lastName, String firstName) {

        String queryStr = "select u.id, u.role_name, p.provider_no, p.first_name, p.last_name from provider p LEFT JOIN secUserRole u ON  p.provider_no=u.provider_no "
                + " where p.last_name like ?1 and p.first_name like ?2 and p.status='1' order by p.first_name, p.last_name, u.role_name";

        Query query = entityManager.createNativeQuery(queryStr);
        query.setParameter(1, lastName);
        query.setParameter(2, firstName);

        @SuppressWarnings("unchecked")
        List<Object[]> proList = query.getResultList();

        return proList;
    }

    @Override
    public List<ProviderData> findByProviderTeam(String providerNo) {

        String queryStr = "select * from provider p  " +
                "where team in (select team from provider where team is not null and team <> '' and provider_no=?1)";

        Query query = entityManager.createNativeQuery(queryStr, modelClass);
        query.setParameter(1, providerNo);

        @SuppressWarnings("unchecked")
        List<ProviderData> proList = query.getResultList();

        return proList;
    }

    @Override
    public List<ProviderData> findAllBilling(String active) {
        Query query = createQuery("p", "p.ohipNo is not null and p.ohipNo != '' and p.status = ?1 order by p.lastName");
        query.setParameter(1, active);
        return query.getResultList();
    }


    /**
     * Finds all providers for the specified type and insurance no, ordered by last name.
     *
     * @param providerType Provider type (doctor, nurse, etc.)
     * @param insuranceNo  Provider's insurance number
     * @return Returns all matching providers
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderData> findByTypeAndOhip(String providerType, String insuranceNo) {
        Query query = createQuery("p", "p.providerType = ?1 and p.ohipNo like ?2 order by p.lastName");
        query.setParameter(1, providerType);
        query.setParameter(2, insuranceNo);
        return query.getResultList();
    }

    /**
     * Finds all providers with the specified providers type
     *
     * @param providerType Provider type to be found
     * @return Returns all the active matching providers.
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderData> findByType(String providerType) {
        Query query = createQuery("p", "p.providerType = ?1 and p.status = '1' order by p.lastName, p.firstName");
        query.setParameter(1, providerType);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderData> findByName(String firstName, String lastName, boolean onlyActive) {
        StringBuilder buf = createQueryString("p", "");
        boolean isAppended = false;
        List<Object> params = new ArrayList<>();
        int paramIndex = 1;
        if (firstName != null && !firstName.trim().equals("")) {
            buf.append("WHERE p.firstName like ?" + paramIndex++);
            params.add(firstName + "%");
            isAppended = true;
        }

        if (lastName != null && !lastName.trim().equals("")) {
            if (isAppended) {
                buf.append(" AND");
            } else {
                buf.append(" WHERE ");
            }
            buf.append(" p.lastName like ?" + paramIndex++);
            params.add(lastName + "%");
            isAppended = true;
        }

        if (onlyActive) {
            if (isAppended) {
                buf.append(" AND");
            } else {
                buf.append(" WHERE ");
            }
            buf.append(ACTIVE_WHERE_CLAUSE);
        }

        buf.append(" ORDER BY p.lastName, p.firstName");

        Query query = entityManager.createQuery(buf.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderData> findAll() {
        Query query = entityManager.createQuery("FROM " + modelClass.getName());
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProviderData> findAll(boolean inactive) {
        if (inactive) return findAll();
        Query query = createQuery("p", ACTIVE_WHERE_CLAUSE);
        return query.getResultList();
    }

    @Override
    public Integer getLastId() {
        Query query = entityManager.createQuery("SELECT p.id FROM ProviderData p ORDER BY CAST(p.id AS integer) ASC");
        query.setMaxResults(1);
        String result = (String) query.getSingleResult();
        if (result == null)
            return 0;
        return ConversionUtils.fromIntString(result);
    }
}
