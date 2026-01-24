//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.PMmodule.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Expression;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.PMmodule.model.ClientReferral;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for ClientReferral entity operations.
 * <p>
 * This DAO handles all database operations related to client referrals in the PMmodule,
 * including retrieval, persistence, and searching of referral records. It provides methods
 * to query referrals by client, program, facility, and status.
 * </p>
 * <p>
 * Migrated from HibernateDaoSupport to direct SessionFactory injection for Spring 6 compatibility.
 * </p>
 *
 * @since 2005-11-01
 */
public class ClientReferralDAOImpl implements ClientReferralDAO {

    private Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     *
     * @return Session the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all client referrals from the database.
     *
     * @return List&lt;ClientReferral&gt; list of all client referrals
     */
    public List<ClientReferral> getReferrals() {
        Query<ClientReferral> query = getSession().createQuery("from ClientReferral", ClientReferral.class);
        List<ClientReferral> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getReferrals: # of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves all referrals for a specific client.
     *
     * @param clientId Long the client identifier
     * @return List&lt;ClientReferral&gt; list of referrals for the client
     * @throws IllegalArgumentException if clientId is null or less than or equal to 0
     */
    public List<ClientReferral> getReferrals(Long clientId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId";
        Query<ClientReferral> query = getSession().createQuery(sSQL, ClientReferral.class);
        query.setParameter("clientId", clientId);
        List<ClientReferral> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getReferrals: clientId=" + clientId + ",# of results=" + results.size());
        }

        // [ 1842692 ] RFQ Feature - temp change for pmm referral history report
        results = displayResult(results);
        // end of change

        return results;
    }

    /**
     * Retrieves referrals for a specific client filtered by facility.
     * <p>
     * Returns referrals where the facility ID matches directly or where the referral's
     * program belongs to the specified facility.
     * </p>
     *
     * @param clientId Long the client identifier
     * @param facilityId Integer the facility identifier
     * @return List&lt;ClientReferral&gt; list of referrals for the client at the facility
     * @throws IllegalArgumentException if clientId is null/invalid or facilityId is null/negative
     */
    public List<ClientReferral> getReferralsByFacility(Long clientId, Integer facilityId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (facilityId == null || facilityId.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId " +
                " and ( (cr.FacilityId=:facilityId1) or (cr.ProgramId in (select s.id from Program s where s.facilityId=:facilityId2 or s.facilityId is null)))";
        Query<ClientReferral> query = getSession().createQuery(sSQL, ClientReferral.class);
        query.setParameter("clientId", clientId);
        query.setParameter("facilityId1", facilityId);
        query.setParameter("facilityId2", facilityId);
        List<ClientReferral> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getReferralsByFacility: clientId=" + clientId + ",# of results=" + results.size());
        }
        results = displayResult(results);
        return results;
    }

    /**
     * Enriches referral results with referring program/agency information for reporting.
     * <p>
     * This is a temporary implementation for RFQ Feature #1842692. The suggestion is to add
     * a dedicated field to the client_referral table for referring program/agency information.
     * </p>
     *
     * @param lResult List&lt;ClientReferral&gt; list of referrals to enrich
     * @return List&lt;ClientReferral&gt; enriched list with completion notes and external program flags
     */
    public List<ClientReferral> displayResult(List<ClientReferral> lResult) {
        List<ClientReferral> ret = new ArrayList<ClientReferral>();

        for (ClientReferral element : lResult) {
            ClientReferral cr = element;

            ClientReferral result = null;

            String sSQL = "from ClientReferral r where r.ClientId = :clientId and r.Id < :id order by r.Id desc";
            Query<ClientReferral> query = getSession().createQuery(sSQL, ClientReferral.class);
            query.setParameter("clientId", cr.getClientId());
            query.setParameter("id", cr.getId());
            List<ClientReferral> results = query.list();

            // temp - completionNotes/Referring program/agency, notes/External
            String completionNotes = "";
            String notes = "";
            if (!results.isEmpty()) {
                result = results.get(0);
                completionNotes = result.getProgramName();
                notes = isExternalProgram(Integer.parseInt(result.getProgramId().toString())) ? "Yes" : "No";
            } else {
                // get program from table admission
                List<Admission> lr = getAdmissions(Integer.parseInt(cr.getClientId().toString()));
                Admission admission = lr.get(lr.size() - 1);
                completionNotes = admission.getProgramName();
                notes = isExternalProgram(Integer.parseInt(admission.getProgramId().toString())) ? "Yes" : "No";
            }

            // set the values for added report fields
            cr.setCompletionNotes(completionNotes);
            cr.setNotes(notes);

            ret.add(cr);
        }

        return ret;
    }

    /**
     * Checks if a program is marked as external type.
     *
     * @param programId Integer the program identifier
     * @return boolean true if the program is external, false otherwise
     * @throws IllegalArgumentException if programId is null or less than or equal to 0
     */
    private boolean isExternalProgram(Integer programId) {
        boolean result = false;

        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Program p WHERE p.id = :programId AND p.type = 'external'";
        Query<Program> query = getSession().createQuery(queryStr, Program.class);
        query.setParameter("programId", programId);
        List<Program> rs = query.list();

        if (!rs.isEmpty()) {
            result = true;
        }

        if (log.isDebugEnabled()) {
            log.debug("isCommunityProgram: id=" + programId + " : " + result);
        }

        return result;
    }

    /**
     * Retrieves admissions for a specific demographic/client ordered by admission date.
     *
     * @param demographicNo Integer the demographic identifier
     * @return List&lt;Admission&gt; list of admissions ordered by admission date descending
     * @throws IllegalArgumentException if demographicNo is null or less than or equal to 0
     */
    private List<Admission> getAdmissions(Integer demographicNo) {
        if (demographicNo == null || demographicNo <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Admission a WHERE a.clientId=:clientId ORDER BY a.admissionDate DESC";
        Query<Admission> query = getSession().createQuery(queryStr, Admission.class);
        query.setParameter("clientId", demographicNo);
        return query.list();
    }
    // end of change

    /**
     * Retrieves active referrals for a client, optionally filtered by facility.
     * <p>
     * Active referrals include those with status ACTIVE, PENDING, or UNKNOWN.
     * </p>
     *
     * @param clientId Long the client identifier
     * @param facilityId Integer the facility identifier (optional, can be null)
     * @return List&lt;ClientReferral&gt; list of active referrals for the client
     * @throws IllegalArgumentException if clientId is null or invalid
     */
    public List<ClientReferral> getActiveReferrals(Long clientId, Integer facilityId) {
        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List<ClientReferral> results;
        if (facilityId == null) {
            String resultQuery = "from ClientReferral cr where cr.ClientId = :clientId and (cr.Status = :status1 or cr.Status = :status2 or cr.Status = :status3)";
            Query<ClientReferral> query = getSession().createQuery(resultQuery, ClientReferral.class);
            query.setParameter("clientId", clientId);
            query.setParameter("status1", ClientReferral.STATUS_ACTIVE);
            query.setParameter("status2", ClientReferral.STATUS_PENDING);
            query.setParameter("status3", ClientReferral.STATUS_UNKNOWN);
            results = query.list();
        } else {
            String sSQL = "from ClientReferral cr where cr.ClientId = :clientId and (cr.Status = :status1 or cr.Status = :status2 or cr.Status = :status3)" +
                    " and ( (cr.FacilityId=:facilityId1) or (cr.ProgramId in (select s.id from Program s where s.facilityId=:facilityId2)))";
            Query<ClientReferral> query = getSession().createQuery(sSQL, ClientReferral.class);
            query.setParameter("clientId", clientId);
            query.setParameter("status1", ClientReferral.STATUS_ACTIVE);
            query.setParameter("status2", ClientReferral.STATUS_PENDING);
            query.setParameter("status3", ClientReferral.STATUS_UNKNOWN);
            query.setParameter("facilityId1", facilityId);
            query.setParameter("facilityId2", facilityId);
            results = query.list();
        }

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferrals: clientId=" + clientId + ",# of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves active referrals for a specific client and program combination.
     * <p>
     * Returns referrals with ACTIVE or CURRENT status, ordered by referral date descending.
     * </p>
     *
     * @param clientId Long the client identifier
     * @param programId Long the program identifier
     * @return List&lt;ClientReferral&gt; list of active referrals ordered by referral date descending
     * @throws IllegalArgumentException if clientId or programId is null or invalid
     */
    public List<ClientReferral> getActiveReferralsByClientAndProgram(Long clientId, Long programId) {
        if (clientId == null || clientId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId and cr.ProgramId=:programId and (cr.Status = :status1 or cr.Status = :status2) order by cr.ReferralDate DESC";
        Query<ClientReferral> query = getSession().createQuery(sSQL, ClientReferral.class);
        query.setParameter("clientId", clientId);
        query.setParameter("programId", programId);
        query.setParameter("status1", ClientReferral.STATUS_ACTIVE);
        query.setParameter("status2", ClientReferral.STATUS_CURRENT);
        List<ClientReferral> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferralsByClientAndProgram: clientId=" + clientId + "programId " + programId + ", # of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves a single client referral by its identifier.
     *
     * @param id Long the referral identifier
     * @return ClientReferral the referral object, or null if not found
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    public ClientReferral getClientReferral(Long id) {
        if (id == null || id.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ClientReferral result = getSession().get(ClientReferral.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getClientReferral: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Persists or updates a client referral entity.
     *
     * @param referral ClientReferral the referral object to save or update
     * @throws IllegalArgumentException if referral is null
     */
    public void saveClientReferral(ClientReferral referral) {
        if (referral == null) {
            throw new IllegalArgumentException();
        }

        getSession().saveOrUpdate(referral);

        if (log.isDebugEnabled()) {
            log.debug("saveClientReferral: id=" + referral.getId());
        }

    }

    /**
     * Searches for client referrals based on criteria from a referral object.
     * <p>
     * Currently supports filtering by program ID when provided.
     * </p>
     *
     * @param referral ClientReferral the referral object containing search criteria
     * @return List&lt;ClientReferral&gt; list of matching referrals
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public List<ClientReferral> search(ClientReferral referral) {
        Session session = getSession();
        Criteria criteria = session.createCriteria(ClientReferral.class);

        if (referral != null && referral.getProgramId().longValue() > 0) {
            criteria.add(Expression.eq("ProgramId", referral.getProgramId()));
        }

        return criteria.list();
    }

    /**
     * Retrieves all client referrals associated with a specific program.
     *
     * @param programId int the program identifier
     * @return List&lt;ClientReferral&gt; list of referrals for the program
     */
    public List<ClientReferral> getClientReferralsByProgram(int programId) {
        String hql = "from ClientReferral cr where cr.ProgramId = :programId";
        Query<ClientReferral> query = getSession().createQuery(hql, ClientReferral.class);
        query.setParameter("programId", Long.valueOf(programId));
        return query.list();
    }

}
