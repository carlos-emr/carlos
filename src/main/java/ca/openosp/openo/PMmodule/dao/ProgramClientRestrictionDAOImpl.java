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

import java.util.Collection;
import java.util.List;

import ca.openosp.openo.PMmodule.model.ProgramClientRestriction;
import ca.openosp.openo.commn.dao.DemographicDao;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;

/**
 * Data Access Object implementation for managing ProgramClientRestriction entities.
 * <p>
 * This DAO provides methods to create, read, update, and query program client restrictions,
 * which control access permissions for clients within specific programs.
 * </p>
 * <p>
 * Uses Hibernate SessionFactory for database operations. Each restriction links a client
 * (demographic) to a program with specified access controls and provider information.
 * </p>
 *
 * @see ProgramClientRestriction
 * @see ProgramClientRestrictionDAO
 */
public class ProgramClientRestrictionDAOImpl implements ProgramClientRestrictionDAO {
    
    @Autowired
    private SessionFactory sessionFactory;
    
    private DemographicDao demographicDao;
    private ProgramDao programDao;
    private ProviderDao providerDao;
    
    /**
     * Sets the SessionFactory for this DAO.
     * Used by Spring for dependency injection.
     *
     * @param sessionFactory the SessionFactory to set
     */
    @Autowired
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
    
    /**
     * Gets the current Hibernate session from the SessionFactory.
     *
     * @return the current Hibernate Session
     */
    private Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    public Collection<ProgramClientRestriction> find(int programId, int demographicNo) {

        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.programId = :programId and pcr.demographicNo = :demographicNo order by pcr.startDate";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        query.setParameter("demographicNo", demographicNo);
        @SuppressWarnings("unchecked")
        List<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public void save(ProgramClientRestriction restriction) {
        getSession().saveOrUpdate(restriction);
    }

    public ProgramClientRestriction find(int restrictionId) {
        return setRelationships(getSession().get(ProgramClientRestriction.class, restrictionId));
    }

    public Collection<ProgramClientRestriction> findForProgram(int programId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.programId = :programId order by pcr.demographicNo";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        @SuppressWarnings("unchecked")
        Collection<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findDisabledForProgram(int programId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = false and pcr.programId = :programId order by pcr.demographicNo";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        @SuppressWarnings("unchecked")
        Collection<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findForClient(int demographicNo) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.demographicNo = :demographicNo order by pcr.programId";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("demographicNo", demographicNo);
        @SuppressWarnings("unchecked")
        Collection<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findForClient(int demographicNo, int facilityId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.demographicNo = :demographicNo" +
        " and pcr.programId in (select s.id from Program s where s.facilityId = :facilityId or s.facilityId is null) order by pcr.programId";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("demographicNo", demographicNo);
        query.setParameter("facilityId", facilityId);
        @SuppressWarnings("unchecked")
        Collection<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findDisabledForClient(int demographicNo) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = false and pcr.demographicNo = :demographicNo order by pcr.programId";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("demographicNo", demographicNo);
        @SuppressWarnings("unchecked")
        Collection<ProgramClientRestriction> pcrs = query.list();
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    private ProgramClientRestriction setRelationships(ProgramClientRestriction pcr) {
        pcr.setClient(demographicDao.getDemographic("" + pcr.getDemographicNo()));
        pcr.setProgram(programDao.getProgram(pcr.getProgramId()));
        pcr.setProvider(providerDao.getProvider(pcr.getProviderNo()));

        return pcr;
    }

    @Required
    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    @Required
    public void setProgramDao(ProgramDao programDao) {
        this.programDao = programDao;
    }

    @Required
    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

}
