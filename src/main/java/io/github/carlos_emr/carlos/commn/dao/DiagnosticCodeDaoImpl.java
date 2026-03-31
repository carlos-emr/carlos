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

import java.util.Collections;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class DiagnosticCodeDaoImpl extends AbstractDaoImpl<DiagnosticCode> implements DiagnosticCodeDao {

    public DiagnosticCodeDaoImpl() {
        super(DiagnosticCode.class);
    }


    public List<DiagnosticCode> findByDiagnosticCode(String diagnosticCode) {
        String sql = "select x from DiagnosticCode x where x.diagnosticCode=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, diagnosticCode);

        List<DiagnosticCode> results = query.getResultList();
        return results;
    }

    public List<DiagnosticCode> findByDiagnosticCodeAndRegion(String diagnosticCode, String region) {
        String sql = "select x from DiagnosticCode x where x.diagnosticCode=?1 and x.region=?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, diagnosticCode);
        query.setParameter(2, region);

        List<DiagnosticCode> results = query.getResultList();
        return results;
    }

    public List<DiagnosticCode> search(String searchString) {
        String sql = "select x from DiagnosticCode x where x.status like 'A' and x.diagnosticCode like ?1 or x.description like ?2 order by x.diagnosticCode";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + searchString + "%");
        query.setParameter(2, "%" + searchString + "%");

        List<DiagnosticCode> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

    public List<DiagnosticCode> newSearch(String a, String b, String c, String d, String e, String f) {
        String sql = "select x from DiagnosticCode x where x.diagnosticCode like ?1 or x.diagnosticCode like ?2 or x.diagnosticCode like ?3 or x.description like ?4 or x.description like ?5 or x.description like ?6";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, a);
        query.setParameter(2, b);
        query.setParameter(3, c);
        query.setParameter(4, d);
        query.setParameter(5, e);
        query.setParameter(6, f);


        List<DiagnosticCode> results = query.getResultList();
        return results;
    }

    public List<DiagnosticCode> searchCode(String code) {
        String sql = "select x from DiagnosticCode x where x.diagnosticCode like ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, code);


        List<DiagnosticCode> results = query.getResultList();
        return results;
    }

    public List<DiagnosticCode> searchText(String description) {
        String sql = "select x from DiagnosticCode x where x.description like ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, "%" + description + "%");

        List<DiagnosticCode> results = query.getResultList();
        return results;
    }

    public List<DiagnosticCode> getByDxCode(String dxCode) {
        Query query = entityManager.createQuery("select bdx from DiagnosticCode bdx where bdx.diagnosticCode = ?1");
        query.setParameter(1, dxCode);

        List<DiagnosticCode> results = query.getResultList();
        return results;
    }


    /**
     * Retrieves a list of DiagnosticCode based on the specified bill region and service type.
     */
    public List<DiagnosticCode> findByRegionAndType(String billRegion, String serviceType) {
        Query query = entityManager.createQuery("SELECT d, c FROM DiagnosticCode d, CtlDiagCode c " +
                "WHERE d.diagnosticCode = c.diagnosticCode " +
                "AND d.region = ?1" +
                "AND c.serviceType = ?2");
        query.setParameter(1, billRegion);
        query.setParameter(2, serviceType);
        return query.getResultList();
    }

    public List<Object[]> findDiagnosictsAndCtlDiagCodesByServiceType(String serviceType) {
        String sql = "SELECT d, c FROM DiagnosticCode d, CtlDiagCode c " +
                "WHERE c.diagnosticCode = d.diagnosticCode " +
                "AND c.serviceType = ?1" +
                "ORDER BY d.description";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, serviceType);
        return query.getResultList();
    }

    @Override
    public DiagnosticCode findByCode(String code) {
        List<DiagnosticCode> diagnosticCodeList = getByDxCode(code);
        if (diagnosticCodeList != null && diagnosticCodeList.size() > 0) {
            return diagnosticCodeList.get(0);
        }
        return null;
    }

    @Override
    public AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem) {
        Query query = entityManager.createQuery("FROM DiagnosticCode d WHERE d.diagnosticCode like ?1");

        query.setParameter(1, codingSystem);
        query.setMaxResults(1);

        return getSingleResultOrNull(query);
    }
}
