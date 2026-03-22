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

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.LabTestResults;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
/**
 * JPA implementation of {@link LabTestResultsDao} for laboratory data access.
 *
 * @since 2001
 */

public class LabTestResultsDaoImpl extends AbstractDaoImpl<LabTestResults> implements LabTestResultsDao {

    /** Constructs this DAO for the {@link LabTestResults} entity class. */

    public LabTestResultsDaoImpl() {
        super(LabTestResults.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<LabTestResults> findByTitleAndLabInfoId(Integer labId) {
        Query query = createQuery("r", "r.title IS NOT NULL AND r.title <> '' and r.labPatientPhysicianInfoId = :labId");
        query.setParameter("labId", labId);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<LabTestResults> findByLabInfoId(Integer labId) {
        Query query = createQuery("r", "r.labPatientPhysicianInfoId = :labId");
        query.setParameter("labId", labId);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<LabTestResults> findByAbnAndLabInfoId(String abn, Integer labId) {
        Query query = createQuery("r", "r.abn = :abn and r.labPatientPhysicianInfoId = :labId");
        query.setParameter("labId", labId);
        query.setParameter("abn", abn);
        return query.getResultList();
    }

    /**
     * Finds unique test names for the specified patient and lab type
     *
     * @param demoNo  Demographic id of the patient
     * @param labType Type of the lab to find results for
     * @return Returns a list of triples containing lab type, test title and test name.
     */
    @Override
    public List<Object[]> findUniqueTestNames(Integer demoNo, String labType) {
        String jpql = "SELECT DISTINCT p.labType, ltr.title, ltr.testName " +
                "FROM " +
                "PatientLabRouting p, " +
                "LabTestResults ltr, " +
                "LabPatientPhysicianInfo lpp " +
                "WHERE p.labType = :labType " +
                "AND p.demographicNo = :demoNo " +
                "AND p.labNo = ltr.labPatientPhysicianInfoId " +
                "AND ltr.labPatientPhysicianInfoId = lpp.id " +
                "AND ltr.testName IS NOT NULL " +
                "AND ltr.testName <> '' " +
                "ORDER BY ltr.title";

        Query query = entityManager.createQuery(jpql);
        query.setParameter("labType", labType);
        query.setParameter("demoNo", demoNo);
        return query.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<LabTestResults> findByAbnAndPhysicianId(String abn, Integer lppii) {
        Query q = createQuery("ltr", "ltr.abn = :abn and ltr.labPatientPhysicianInfoId = :lppii");
        q.setParameter("abn", abn);
        q.setParameter("lppii", lppii);
        return q.getResultList();
    }

    /** {@inheritDoc} */

    @Override
    public List<LabTestResults> findByLabPatientPhysicialInfoId(Integer labid) {
        Query query = createQuery("r", "r.labPatientPhysicianInfoId = :labid");
        query.setParameter("labid", labid);
        return query.getResultList();
    }
}
