/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao.forms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.NativeSql;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@SuppressWarnings("unchecked")
public class FormsDao {

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager = null;

    /**
     * Returns:
     * <p>
     * ID int
     * formCreated date
     * patientName string
     */
    @NativeSql("formLabReq07")
    public List<Object[]> findIdFormCreatedAndPatientNameFromFormLabReq07() {
        String sql = "SELECT ID, formCreated, patientName FROM formLabReq07";
        Query query = entityManager.createNativeQuery(sql);
        return query.getResultList();
    }

    @NativeSql("formLabReq10")
    public List<Object[]> findIdFormCreatedAndPatientNameFromFormLabReq10() {
        String sql = "SELECT ID, formCreated, patientName FROM formLabReq10";
        Query query = entityManager.createNativeQuery(sql);
        return query.getResultList();
    }

    @NativeSql("formLabReq07")
    public List<Object[]> findIdFormCreatedAndPatientNameFromFormLabReq07(String demographicNo) {
        if (demographicNo == null) {
            return findIdFormCreatedAndPatientNameFromFormLabReq07();
        }
        String sql = "SELECT ID, formCreated, patientName FROM formLabReq07 where demographic_no = :demoNo";
        Query query = entityManager.createNativeQuery(sql);
        try {
            query.setParameter("demoNo", Integer.parseInt(demographicNo));
        } catch (NumberFormatException e) {
            return new ArrayList<>();
        }
        return query.getResultList();
    }

    @NativeSql("formLabReq10")
    public List<Object[]> findIdFormCreatedAndPatientNameFromFormLabReq10(String demographicNo) {
        if (demographicNo == null) {
            return findIdFormCreatedAndPatientNameFromFormLabReq10();
        }
        String sql = "SELECT ID, formCreated, patientName FROM formLabReq10 where demographic_no = :demoNo";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("demoNo", Integer.parseInt(demographicNo));
        return query.getResultList();
    }

    @NativeSql("formLabReq07")
    public List<Object> findFormCreatedFromFormLabReq07ById(Integer linkReqId) {
        String sql = "SELECT formCreated FROM formLabReq07 WHERE ID = :linkReqId";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("linkReqId", linkReqId);
        return query.getResultList();
    }

    @NativeSql("formLabReq10")
    public List<Object> findFormCreatedFromFormLabReq10ById(Integer linkReqId) {
        String sql = "SELECT formCreated FROM formLabReq10 WHERE ID = :linkReqId";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("linkReqId", linkReqId);
        return query.getResultList();
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @NativeSql("formBCAR")
    public List<Object[]> selectBcFormAr(String beginEdd, String endEdd, int limit, int offset) {
        String sql = "select demographic_no, c_EDD, c_surname,c_givenName, pg1_ageAtEDD, pg1_dateOfBirth, pg1_langPref, c_phn, pg1_gravida, pg1_term, c_phone, c_phyMid, ar2_doula, ar2_doulaNo, provider_no from formBCAR where c_EDD >= ? and c_EDD <= ? order by c_EDD desc, ID desc";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, beginEdd);
        query.setParameter(2, endEdd);
        query.setMaxResults(limit);
        query.setFirstResult(offset);

        return query.getResultList();
    }

    @NativeSql("formBCAR2007")
    public List<Object[]> selectBcFormAr2007(String beginEdd, String endEdd, int limit, int offset) {
        String sql = "select demographic_no, c_EDD, c_surname,c_givenName, pg1_ageAtEDD, pg1_dateOfBirth, pg1_langPref, c_phn, pg1_gravida, pg1_term, c_phone, ar2_doula, ar2_doulaNo, provider_no from formBCAR2007 where c_EDD >= ? and c_EDD <= ? order by c_EDD desc, ID desc";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, beginEdd);
        query.setParameter(2, endEdd);
        query.setMaxResults(limit);
        query.setFirstResult(offset);

        return query.getResultList();
    }

    /**
     * Executes a parameterized native SQL query with named parameters.
     * Parameters should be passed as alternating name-value pairs.
     *
     * @param sql The SQL query with named parameters (e.g., :paramName)
     * @param params Alternating parameter names and values (name1, value1, name2, value2, ...)
     * @return List of Object arrays containing the query results
     * @throws IllegalArgumentException if params array has odd length
     */
    public List<Object[]> runParameterizedNativeQuery(String sql, Object... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be provided in name-value pairs");
        }

        // nosemgrep: jpa-sqli — this utility method binds named parameters below; callers provide parameterized SQL
        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < params.length; i += 2) {
            String paramName = (String) params[i];
            Object paramValue = params[i + 1];
            query.setParameter(paramName, paramValue);
        }

        return query.getResultList();
    }

    /**
     * Executes a parameterized native SQL query with named parameters provided as a Map.
     * This method provides protection against SQL injection by properly binding parameters.
     *
     * @param sql The SQL query with named parameters (e.g., :paramName)
     * @param params Map of parameter names to values
     * @return List of Object arrays containing the query results
     */
    @SuppressWarnings("rawtypes")
    public List<Object[]> runParameterizedNativeQuery(String sql, Map<String, Object> params) {
        // nosemgrep: jpa-sqli — this utility method binds named parameters below; callers provide parameterized SQL
        Query query = entityManager.createNativeQuery(sql);

        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        List resultList = query.getResultList();
        if (resultList == null) {
            return new ArrayList<Object[]>();
        }

        // get first meaningful element
        Object firstNonNullElement = null;
        for (int i = 0; i < resultList.size(); i++) {
            Object o = resultList.get(i);
            if (o != null) {
                firstNonNullElement = o;
                break;
            }
        }

        // contains arrays, so it's safe to return the original result set
        if (firstNonNullElement != null && firstNonNullElement.getClass().isArray()) {
            return resultList;
        }

        // at this point we ended up having a list of single element and not an array, so
        // wrap it up properly in the array values. This might happen when we select
        // a single value, for example "SELECT d.id FROM demographic d"
        List<Object[]> wrappedResult = new ArrayList<Object[]>(resultList.size());
        for (Object o : resultList) {
            wrappedResult.add(new Object[]{o});
        }
        return wrappedResult;
    }
}
