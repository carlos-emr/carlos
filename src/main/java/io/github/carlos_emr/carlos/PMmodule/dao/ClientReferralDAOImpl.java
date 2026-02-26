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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Expression;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

public class ClientReferralDAOImpl extends HibernateDaoSupport implements ClientReferralDAO {

    private Logger log = MiscUtils.getLogger();
    public SessionFactory sessionFactory;

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    public List<ClientReferral> getReferrals() {
        @SuppressWarnings("unchecked")
        List<ClientReferral> results = (List<ClientReferral>) this.getHibernateTemplate().find("from ClientReferral");

        if (log.isDebugEnabled()) {
            log.debug("getReferrals: # of results=" + results.size());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    public List<ClientReferral> getReferrals(Long clientId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }
        
        String sSQL = "from ClientReferral cr where cr.ClientId = ?0";
        List<ClientReferral> results = (List<ClientReferral>) this.getHibernateTemplate().find(sSQL, clientId);

        if (log.isDebugEnabled()) {
            log.debug("getReferrals: clientId=" + clientId + ",# of results=" + results.size());
        }

        // [ 1842692 ] RFQ Feature - temp change for pmm referral history report
        results = displayResult(results);
        // end of change

        return results;
    }

    @SuppressWarnings("unchecked")
    public List<ClientReferral> getReferralsByFacility(Long clientId, Integer facilityId) {

        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (facilityId == null || facilityId.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ClientReferral cr where cr.ClientId = ?0 " +
                " and ( (cr.FacilityId=?1) or (cr.ProgramId in (select s.id from Program s where s.facilityId=?2 or s.facilityId is null)))";
        Object[] param = new Object[]{
            clientId,
            facilityId,
            facilityId
        };
        List<ClientReferral> results = (List<ClientReferral>) this.getHibernateTemplate().find(sSQL, param);

        if (log.isDebugEnabled()) {
            log.debug("getReferralsByFacility: clientId=" + clientId + ",# of results=" + results.size());
        }
        results = displayResult(results);
        return results;
    }

    // [ 1842692 ] RFQ Feature - temp change for pmm referral history report
    // - suggestion: to add a new field to the table client_referral (Referring program/agency)
    public List<ClientReferral> displayResult(List<ClientReferral> lResult) {
        List<ClientReferral> ret = new ArrayList<ClientReferral>();

        for (ClientReferral element : lResult) {
            ClientReferral cr = element;

            ClientReferral result = null;

            String sSQL = "from ClientReferral r where r.ClientId = ?0 and r.Id < ?1 order by r.Id desc";
            Object[] param = new Object[]{cr.getClientId(), cr.getId()};
            @SuppressWarnings("unchecked")
            List<ClientReferral> results = (List<ClientReferral>) this.getHibernateTemplate().find(sSQL, param);

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
                if (!lr.isEmpty()) {
                    Admission admission = lr.get(lr.size() - 1);
                    completionNotes = admission.getProgramName();
                    notes = isExternalProgram(Integer.parseInt(admission.getProgramId().toString())) ? "Yes" : "No";
                }
            }

            // set the values for added report fields
            cr.setCompletionNotes(completionNotes);
            cr.setNotes(notes);

            ret.add(cr);
        }

        return ret;
    }

    private boolean isExternalProgram(Integer programId) {
        boolean result = false;

        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Program p WHERE p.id = ?0 AND p.type = 'external'";
        @SuppressWarnings("unchecked")
        List<Program> rs = (List<Program>) getHibernateTemplate().find(queryStr, programId);

        if (!rs.isEmpty()) {
            result = true;
        }

        if (log.isDebugEnabled()) {
            log.debug("isCommunityProgram: id=" + programId + " : " + result);
        }

        return result;
    }

    private List<Admission> getAdmissions(Integer demographicNo) {
        if (demographicNo == null || demographicNo <= 0) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM Admission a WHERE a.clientId=?0 ORDER BY a.admissionDate DESC";
        @SuppressWarnings("unchecked")
        List<Admission> rs = (List<Admission>) getHibernateTemplate().find(queryStr, new Object[]{demographicNo});
        return rs;
    }
    // end of change

    @SuppressWarnings("unchecked")
    public List<ClientReferral> getActiveReferrals(Long clientId, Integer facilityId) {
        if (clientId == null || clientId.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List<ClientReferral> results;
        if (facilityId == null) {
            String resultQuery = "from ClientReferral cr where cr.ClientId = ?0 and (cr.Status = ?1 or cr.Status = ?2 or cr.Status = ?3)";
            Object[] param = new Object[]{
                clientId,
                ClientReferral.STATUS_ACTIVE,
                ClientReferral.STATUS_PENDING,
                ClientReferral.STATUS_UNKNOWN
            };
            results = (List<ClientReferral>) this.getHibernateTemplate().find(resultQuery, param);
        } else {
            String sSQL = "from ClientReferral cr where cr.ClientId = ?0 and (cr.Status = ?1 or cr.Status = ?2 or cr.Status = ?3) and ((cr.FacilityId=?4) or (cr.ProgramId in (select s.id from Program s where s.facilityId=?5)))";
            Object params[] = new Object[] {
                clientId,
                ClientReferral.STATUS_ACTIVE,
                ClientReferral.STATUS_PENDING,
                ClientReferral.STATUS_UNKNOWN,
                facilityId,
                facilityId
            };
            results = (List<ClientReferral>) getHibernateTemplate().find(sSQL, params);
        }

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferrals: clientId=" + clientId + ",# of results=" + results.size());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    public List<ClientReferral> getActiveReferralsByClientAndProgram(Long clientId, Long programId) {
        if (clientId == null || clientId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List<ClientReferral> results;

        String sSQL = "from ClientReferral cr where cr.ClientId = ?0 and cr.ProgramId=?1 and (cr.Status = ?2 or cr.Status = ?3) order by cr.ReferralDate DESC";
        Object params[] = new Object[] {
            clientId,
            programId,
            ClientReferral.STATUS_ACTIVE,
            ClientReferral.STATUS_CURRENT
        };
        results = (List<ClientReferral>) getHibernateTemplate().find(sSQL, params);

        if (log.isDebugEnabled()) {
            log.debug("getActiveReferralsByClientAndProgram: clientId=" + clientId + "programId " + programId + ", # of results=" + results.size());
        }

        return results;
    }

    public ClientReferral getClientReferral(Long id) {
        if (id == null || id.longValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ClientReferral result = this.getHibernateTemplate().get(ClientReferral.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getClientReferral: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    public void saveClientReferral(ClientReferral referral) {
        if (referral == null) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().saveOrUpdate(referral);

        if (log.isDebugEnabled()) {
            log.debug("saveClientReferral: id=" + referral.getId());
        }

    }

    @SuppressWarnings("unchecked")
    public List<ClientReferral> search(ClientReferral referral) {
        Session session = getSessionFactory().getCurrentSession();
        try {
            Criteria criteria = session.createCriteria(ClientReferral.class);

            if (referral != null && referral.getProgramId().longValue() > 0) {
                criteria.add(Expression.eq("ProgramId", referral.getProgramId()));
            }

            return criteria.list();
        } finally {
            //this.releaseSession(session);
            session.close();
        }
    }

    public List<ClientReferral> getClientReferralsByProgram(int programId) {
        @SuppressWarnings("unchecked")
        List<ClientReferral> results = (List<ClientReferral>) this.getHibernateTemplate().find("from ClientReferral cr where cr.ProgramId = ?0", Long.valueOf(programId));

        return results;
    }

}
