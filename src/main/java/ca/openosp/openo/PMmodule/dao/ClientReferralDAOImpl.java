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
import org.hibernate.criterion.Expression;
import ca.openosp.openo.PMmodule.model.ClientReferral;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import org.hibernate.Query;

/**
 * Data Access Object implementation for ClientReferral entities.
 * Handles database operations for client referrals in the PMmodule system.
 * 
 * <p>This implementation uses direct SessionFactory injection instead of the deprecated
 * HibernateDaoSupport to prepare for Spring 6 and Jakarta EE migration.</p>
 * 
 * @since OpenO v1.0
 */
public class ClientReferralDAOImpl implements ClientReferralDAO {

    private Logger log = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     * 
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all client referrals from the database.
     * 
     * @return list of all ClientReferral entities
     */
    public List<ClientReferral> getReferrals() {
        @SuppressWarnings("unchecked")
        List<ClientReferral> results = (List<ClientReferral>) getSession()
            .createQuery("from ClientReferral")
            .list();

        if (log.isDebugEnabled()) {
            log.debug("getReferrals: # of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves all referrals for a specific client.
     * 
     * @param clientId the client ID to search for
     * @return list of ClientReferral entities for the specified client
     * @throws IllegalArgumentException if clientId is null or <= 0
     */
    @SuppressWarnings("unchecked")
    public List<ClientReferral> getReferrals(Long clientId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }
        
        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId";
        List<ClientReferral> results = (List<ClientReferral>) getSession()
            .createQuery(sSQL)
            .setParameter("clientId", clientId)
            .list();

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
     * 
     * @param clientId the client ID to search for
     * @param facilityId the facility ID to filter by
     * @return list of ClientReferral entities matching the criteria
     * @throws IllegalArgumentException if clientId is null or <= 0, or facilityId is null or < 0
     */
    @SuppressWarnings("unchecked")
    public List<ClientReferral> getReferralsByFacility(Long clientId, Integer facilityId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (facilityId == null || facilityId.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId " +
                " and ( (cr.FacilityId=:facilityId1) or (cr.ProgramId in (select s.id from Program s where s.facilityId=:facilityId2 or s.facilityId is null)))";
        
        List<ClientReferral> results = (List<ClientReferral>) getSession()
            .createQuery(sSQL)
            .setParameter("clientId", clientId)
            .setParameter("facilityId1", facilityId)
            .setParameter("facilityId2", facilityId)
            .list();

        if (log.isDebugEnabled()) {
            log.debug("getReferralsByFacility: clientId=" + clientId + ",# of results=" + results.size());
        }
        results = displayResult(results);
        return results;
    }

    /**
     * Processes and enriches referral results with additional program information.
     * This is a temporary solution for the referral history report.
     * 
     * @param lResult the list of referrals to process
     * @return processed list with additional completion notes and external program flags
     */
    public List<ClientReferral> displayResult(List<ClientReferral> lResult) {
        List<ClientReferral> ret = new ArrayList<ClientReferral>();
        //ProgramDao pd = new ProgramDao();
        //AdmissionDao ad = new AdmissionDao();

        for (ClientReferral element : lResult) {
            ClientReferral cr = element;

            ClientReferral result = null;

            String sSQL = "from ClientReferral r where r.ClientId = :clientId and r.Id < :id order by r.Id desc";
            @SuppressWarnings("unchecked")
            List<ClientReferral> results = (List<ClientReferral>) getSession()
                .createQuery(sSQL)
                .setParameter("clientId", cr.getClientId())
                .setParameter("id", cr.getId())
                .list();

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
     * Checks if a program is an external program.
     * 
     * @param programId the program ID to check
     * @return true if the program is external, false otherwise
     * @throws IllegalArgumentException if programId is null or <= 0
     */
    private boolean isExternalProgram(Integer programId) {
        boolean result = false;

        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Program p WHERE p.id = :programId AND p.type = 'external'";
        @SuppressWarnings("unchecked")
        List<Program> rs = (List<Program>) getSession()
            .createQuery(queryStr)
            .setParameter("programId", programId)
            .list();

        if (!rs.isEmpty()) {
            result = true;
        }

        if (log.isDebugEnabled()) {
            log.debug("isCommunityProgram: id=" + programId + " : " + result);
        }

        return result;
    }

    /**
     * Retrieves all admissions for a demographic/client ordered by admission date.
     * 
     * @param demographicNo the demographic number to search for
     * @return list of Admission entities ordered by admission date descending
     * @throws IllegalArgumentException if demographicNo is null or <= 0
     */
    private List<Admission> getAdmissions(Integer demographicNo) {
        if (demographicNo == null || demographicNo <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Admission a WHERE a.clientId=:clientId ORDER BY a.admissionDate DESC";
        @SuppressWarnings("unchecked")
        List<Admission> rs = (List<Admission>) getSession()
            .createQuery(queryStr)
            .setParameter("clientId", demographicNo)
            .list();
        return rs;
    }
    // end of change

    /**
     * Retrieves active referrals for a client, optionally filtered by facility.
     * Active referrals include those with status: ACTIVE, PENDING, or UNKNOWN.
     * 
     * @param clientId the client ID to search for
     * @param facilityId the facility ID to filter by (can be null)
     * @return list of active ClientReferral entities
     * @throws IllegalArgumentException if clientId is null or <= 0
     */
    @SuppressWarnings("unchecked")
    public List<ClientReferral> getActiveReferrals(Long clientId, Integer facilityId) {
        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List<ClientReferral> results;
        if (facilityId == null) {
            String resultQuery = "from ClientReferral cr where cr.ClientId = :clientId and (cr.Status = :status1 or cr.Status = :status2 or cr.Status = :status3)";
            results = (List<ClientReferral>) getSession()
                .createQuery(resultQuery)
                .setParameter("clientId", clientId)
                .setParameter("status1", ClientReferral.STATUS_ACTIVE)
                .setParameter("status2", ClientReferral.STATUS_PENDING)
                .setParameter("status3", ClientReferral.STATUS_UNKNOWN)
                .list();
        } else {
            String sSQL = "from ClientReferral cr where cr.ClientId = :clientId and (cr.Status = :status1 or cr.Status = :status2 or cr.Status = :status3)" +
                    " and ( (cr.FacilityId=:facilityId1) or (cr.ProgramId in (select s.id from Program s where s.facilityId=:facilityId2)))";
            results = (List<ClientReferral>) getSession()
                .createQuery(sSQL)
                .setParameter("clientId", clientId)
                .setParameter("status1", ClientReferral.STATUS_ACTIVE)
                .setParameter("status2", ClientReferral.STATUS_PENDING)
                .setParameter("status3", ClientReferral.STATUS_UNKNOWN)
                .setParameter("facilityId1", facilityId)
                .setParameter("facilityId2", facilityId)
                .list();
        }

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferrals: clientId=" + clientId + ",# of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves active referrals for a specific client and program.
     * 
     * @param clientId the client ID to search for
     * @param programId the program ID to filter by
     * @return list of active ClientReferral entities matching the criteria, ordered by referral date descending
     * @throws IllegalArgumentException if clientId or programId is null or <= 0
     */
    @SuppressWarnings("unchecked")
    public List<ClientReferral> getActiveReferralsByClientAndProgram(Long clientId, Long programId) {
        if (clientId == null || clientId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List<ClientReferral> results;

        String sSQL = "from ClientReferral cr where cr.ClientId = :clientId and cr.ProgramId=:programId and (cr.Status = :status1 or cr.Status = :status2) order by cr.ReferralDate DESC";
        results = (List<ClientReferral>) getSession()
            .createQuery(sSQL)
            .setParameter("clientId", clientId)
            .setParameter("programId", programId)
            .setParameter("status1", ClientReferral.STATUS_ACTIVE)
            .setParameter("status2", ClientReferral.STATUS_CURRENT)
            .list();

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferralsByClientAndProgram: clientId=" + clientId + "programId " + programId + ", # of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves a specific client referral by ID.
     * 
     * @param id the referral ID to retrieve
     * @return the ClientReferral entity, or null if not found
     * @throws IllegalArgumentException if id is null or <= 0
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
     * Saves or updates a client referral entity.
     * 
     * @param referral the ClientReferral entity to save or update
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
     * Searches for client referrals based on criteria in the provided referral object.
     * 
     * @param referral the ClientReferral object containing search criteria
     * @return list of ClientReferral entities matching the search criteria
     */
    @SuppressWarnings("unchecked")
    public List<ClientReferral> search(ClientReferral referral) {
        Criteria criteria = getSession().createCriteria(ClientReferral.class);

        if (referral != null && referral.getProgramId().longValue() > 0) {
            criteria.add(Expression.eq("ProgramId", referral.getProgramId()));
        }

        return criteria.list();
    }

    /**
     * Retrieves all client referrals for a specific program.
     * 
     * @param programId the program ID to search for
     * @return list of ClientReferral entities for the specified program
     */
    public List<ClientReferral> getClientReferralsByProgram(int programId) {
        @SuppressWarnings("unchecked")
        List<ClientReferral> results = (List<ClientReferral>) getSession()
            .createQuery("from ClientReferral cr where cr.ProgramId = :programId")
            .setParameter("programId", Long.valueOf(programId))
            .list();

        return results;
    }

}
