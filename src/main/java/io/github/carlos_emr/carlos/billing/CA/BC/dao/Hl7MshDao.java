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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link Hl7Msh} entities.
 * Provides persistence operations for HL7 MSH (Message Header) segments
 * in the BC PathNet lab integration. Includes complex queries that construct
 * {@link PathNetLabResults} value objects from joined HL7 segment data.
 *
 * @since 2026-03-17
 */
@Repository
@SuppressWarnings("unchecked")
public class Hl7MshDao extends AbstractDaoImpl<Hl7Msh> {

    /**
     * Constructs a new {@code Hl7MshDao} with the {@link Hl7Msh} entity class.
     */
    public Hl7MshDao() {
        super(Hl7Msh.class);
    }

    /**
     * Finds PathNet lab results filtered by patient name, health insurance number, status,
     * provider number, and lab type. Uses a constructor expression to build
     * {@link PathNetLabResults} instances directly from query results.
     *
     * @param patientName String patient name pattern (supports LIKE wildcards)
     * @param patientHealthNumber String patient HIN pattern (supports LIKE wildcards)
     * @param status String provider lab routing status pattern (supports LIKE wildcards)
     * @param providerNo String provider number pattern (supports LIKE wildcards)
     * @param labType String the lab type identifier
     * @return List of {@link PathNetLabResults} matching the criteria
     */
    public List<PathNetLabResults> findPathnetResultsDataByPatientNameHinStatusAndProvider(String patientName, String patientHealthNumber, String status, String providerNo, String labType) {
		/*
		 * Below query use a constructor expression (SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults(Hl7Msh, Hl7Pid, Hl7Orc, Hl7Obr, ProviderLabRoutingModel, String))
		 * and TypedQuery<PathNetLabResults> to directly create instances of PathNetLabResults from the database results
		 * */
        String sql = "SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults( msh, pid, orc, obr, providerLabRouting, MIN(obr.resultStatus) )" +
                "FROM Hl7Msh msh, Hl7Pid pid, Hl7Orc orc, Hl7Obr obr, ProviderLabRoutingModel providerLabRouting " +
                "WHERE providerLabRouting.labNo = pid.messageId " +
                "AND pid.messageId = msh.messageId " +
                "AND pid.id = orc.pidId " +
                "AND pid.id = obr.pidId  " +
                "AND providerLabRouting.status like :status " +
                "AND providerLabRouting.providerNo like :providerNo " +
                "AND providerLabRouting.labType = :labType " +
                "AND pid.patientName like :patientName " +
                "AND pid.externalId like :patientHealthNumber " +
                "GROUP BY pid.id";

		TypedQuery<PathNetLabResults> query = entityManager.createQuery(sql, PathNetLabResults.class);
        query.setParameter("status", status);
        query.setParameter("providerNo", providerNo);
        query.setParameter("labType", labType);
        query.setParameter("patientName", patientName);
        query.setParameter("patientHealthNumber", patientHealthNumber);
        return query.getResultList();
    }

    /**
     * Finds PathNet lab results for a specific lab number. Uses a constructor expression
     * to build {@link PathNetLabResults} instances.
     *
     * @param labNo Integer the lab number (message ID) to look up
     * @return List of {@link PathNetLabResults} for the given lab number
     */
    public List<PathNetLabResults> findPathnetResultsByLabNo(Integer labNo) {
		/*
		 * Below query use a constructor expression (SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults(Hl7Msh, Hl7Pid, Hl7Orc, Hl7Obr, ProviderLabRoutingModel, String))
		 * and TypedQuery<PathNetLabResults> to directly create instances of PathNetLabResults from the database results
		 * */
        String sql = "SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults( msh, pid, orc, obr, providerLabRouting, MIN(obr.resultStatus) )" +
                "FROM Hl7Msh msh, Hl7Pid pid, Hl7Orc orc, Hl7Obr obr, ProviderLabRoutingModel providerLabRouting " +
                "WHERE providerLabRouting.labNo = pid.messageId " +
                "AND pid.messageId = msh.messageId " +
                "AND pid.id = orc.pidId " +
                "AND pid.id = obr.pidId  " +
                "AND pid.messageId= :labNo " +
                "GROUP BY pid.id";

		TypedQuery<PathNetLabResults> query = entityManager.createQuery(sql, PathNetLabResults.class);
        query.setParameter("labNo", labNo);
        return query.getResultList();
    }

	/**
	 * Finds PathNet lab results for a specific demographic (patient) and lab type.
	 * Uses patient lab routing to join with HL7 segments.
	 *
	 * @param demographicNo Integer the demographic (patient) number
	 * @param labType String the lab type identifier (e.g., "BCP")
	 * @return List of {@link PathNetLabResults} for the given demographic and lab type
	 */
	public List<PathNetLabResults> findPathnetResultsDeomgraphicNo(Integer demographicNo, String labType) {
		/*
		 * Below query use a constructor expression (SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults(Hl7Msh, Hl7Pid, Hl7Orc, Hl7Obr, PatientLabRouting, String))
		 * and TypedQuery<PathNetLabResults> to directly create instances of PathNetLabResults from the database results
		 * */
	    String sql =  "SELECT new io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults( msh, pid, orc, obr, patientLabRouting, MIN(obr.resultStatus) )" +
                "FROM Hl7Msh msh, Hl7Pid pid, Hl7Orc orc, Hl7Obr obr, PatientLabRouting patientLabRouting " +
                "WHERE patientLabRouting.labNo = pid.id " +
                "AND pid.id = orc.pidId " +
                "AND pid.id = obr.pidId " +
                "AND msh.messageId = pid.id " +
                "AND patientLabRouting.labType = :labType " +
                "AND patientLabRouting.demographicNo = :demographicNo " +
                "GROUP BY pid.id";

		TypedQuery<PathNetLabResults> query = entityManager.createQuery(sql, PathNetLabResults.class);
        query.setParameter("demographicNo", demographicNo);
        query.setParameter("labType", labType);
        return query.getResultList();
    }

    /**
     * Retrieves IDs of lab results for a demographic that have been updated since the given date.
     * Checks both the MSH date/time and the patient lab routing modification date.
     *
     * @param demographicNo Integer the demographic (patient) number
     * @param updateDate Date the cutoff date for finding updated results
     * @return List of Integer MSH IDs for results updated since the given date
     */
    public List<Integer> getLabResultsSince(Integer demographicNo, Date updateDate) {
        String query = "select m.id from Hl7Msh m, PatientLabRouting p WHERE m.id = p.labNo and p.labType='BCP' and p.demographicNo = ?1 and (m.dateTime > ?2 or p.dateModified > ?3) ";
        Query q = entityManager.createQuery(query);

        q.setParameter(1, demographicNo);
        q.setParameter(2, updateDate);
        q.setParameter(3, updateDate);

        return q.getResultList();
    }

}
