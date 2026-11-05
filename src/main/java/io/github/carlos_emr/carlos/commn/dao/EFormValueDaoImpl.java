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
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.EFormValue;
import org.springframework.stereotype.Repository;

@Repository
public class EFormValueDaoImpl extends AbstractDaoImpl<EFormValue> implements EFormValueDao {

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ClinicalDataRow> findDatabaseApValues(io.github.carlos_emr.carlos.report.data.ParameterizedSql sql) {
        io.github.carlos_emr.carlos.eform.EFormSqlSafety.validateLegacySqlSafety(sql.getSql());
        // AP templates expose JDBC text (including physical timestamp scale), not Java temporal objects.
        // Hibernate owns the connection and transaction; only detached scalar rows leave this DAO.
        return entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement(sql.getSql())) {
                for (int i = 0; i < sql.getParams().size(); i++) statement.setObject(i + 1, sql.getParams().get(i));
                try (var rows = statement.executeQuery()) {
                    var result = new java.util.ArrayList<ClinicalDataRow>();
                    var metadata = rows.getMetaData();
                    while (rows.next()) {
                        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
                        for (int i = 1; i <= metadata.getColumnCount(); i++) {
                            String label = metadata.getColumnLabel(i);
                            if (!values.containsKey(label)) values.put(label, rows.getString(i));
                        }
                        result.add(new ClinicalDataRow(values));
                    }
                    return result;
                }
            }
        });
    }

    public EFormValueDaoImpl() {
        super(EFormValue.class);
    }

    public List<EFormValue> findByDemographicId(Integer demographicId) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.demographicId=?1");
        query.setParameter(1, demographicId);

        @SuppressWarnings("unchecked")
        List<EFormValue> results = query.getResultList();

        return (results);
    }

    public List<EFormValue> findByApptNo(int apptNo) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.varName='appt_no' and x.varValue=?1");
        query.setParameter(1, String.valueOf(apptNo));

        @SuppressWarnings("unchecked")
        List<EFormValue> results = query.getResultList();

        return (results);
    }

    public List<EFormValue> findByFormDataId(int fdid) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.formDataId=?1");
        query.setParameter(1, fdid);

        @SuppressWarnings("unchecked")
        List<EFormValue> results = query.getResultList();

        return (results);
    }

    public EFormValue findByFormDataIdAndKey(int fdid, String varName) {
        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.formDataId=?1 and x.varName=?2");
        query.setParameter(1, fdid);
        query.setParameter(2, varName);

        @SuppressWarnings("unchecked")
        List<EFormValue> results = query.getResultList();

        if (results == null || results.isEmpty()) return null;
        return (results.get(0));
    }

    public List<EFormValue> findByFormDataIdList(List<Integer> fdids) {
        if (fdids == null || fdids.isEmpty()) return new ArrayList<EFormValue>();

        Query query = entityManager.createQuery("select x from " + modelClass.getSimpleName() + " x where x.formDataId in (?1)");
        query.setParameter(1, fdids);

        @SuppressWarnings("unchecked")
        List<EFormValue> results = query.getResultList();

        return (results);
    }

    public List<String> findAllVarNamesForEForm(Integer fid) {
        Query query = entityManager.createQuery("select DISTINCT(x.varName) from " + modelClass.getSimpleName() + " x where x.formId in (?1)");
        query.setParameter(1, fid);

        @SuppressWarnings("unchecked")
        List<String> results = query.getResultList();

        return (results);
    }
}
