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

import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientRestriction;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

/**
 * DAO implementation for managing {@link ProgramClientRestriction} records.
 *
 * <p>Provides methods to find, save, and manage restrictions that control
 * client access to specific programs. Restrictions may be scoped by program,
 * client (demographic), or facility, and can be enabled or disabled.</p>
 *
 * <p>Each retrieved restriction is hydrated with its related {@code client},
 * {@code program}, and {@code provider} objects via {@code setRelationships()}.</p>
 *
 * @since 2005-05-28
 * @see ProgramClientRestrictionDAO
 * @see ProgramClientRestriction
 */
@Transactional
public class ProgramClientRestrictionDAOImpl extends AbstractHibernateDao implements ProgramClientRestrictionDAO {
    private DemographicDao demographicDao;
    private ProgramDao programDao;
    private ProviderDao providerDao;

    public Collection<ProgramClientRestriction> find(int programId, int demographicNo) {

        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.programId = ?1 and pcr.demographicNo = ?2 order by pcr.startDate";
        List<ProgramClientRestriction> pcrs = (List<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, programId, demographicNo);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public void save(ProgramClientRestriction restriction) {
        if (restriction.getId() == null || restriction.getId() == 0) {
            currentSession().persist(restriction);
        } else {
            currentSession().merge(restriction);
        }
    }

    public ProgramClientRestriction find(int restrictionId) {
        return setRelationships(currentSession().get(ProgramClientRestriction.class, restrictionId));
    }

    public Collection<ProgramClientRestriction> findForProgram(int programId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.programId = ?1 order by pcr.demographicNo";
        Collection<ProgramClientRestriction> pcrs = (Collection<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, programId);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findDisabledForProgram(int programId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = false and pcr.programId = ?1 order by pcr.demographicNo";
        Collection<ProgramClientRestriction> pcrs = (Collection<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, programId);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findForClient(int demographicNo) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.demographicNo = ?1 order by pcr.programId";
        Collection<ProgramClientRestriction> pcrs = (Collection<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, demographicNo);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findForClient(int demographicNo, int facilityId) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = true and pcr.demographicNo = ?1 and pcr.programId in (select s.id from Program s where s.facilityId = ?2 or s.facilityId is null) order by pcr.programId";
        Collection<ProgramClientRestriction> pcrs = (Collection<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, Integer.valueOf(demographicNo), facilityId);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    public Collection<ProgramClientRestriction> findDisabledForClient(int demographicNo) {
        String sSQL = "from ProgramClientRestriction pcr where pcr.enabled = false and pcr.demographicNo = ?1 order by pcr.programId";
        Collection<ProgramClientRestriction> pcrs = (Collection<ProgramClientRestriction>) HqlQueryHelper.find(currentSession(), sSQL, demographicNo);
        for (ProgramClientRestriction pcr : pcrs) {
            setRelationships(pcr);
        }
        return pcrs;
    }

    private ProgramClientRestriction setRelationships(ProgramClientRestriction pcr) {
        if (pcr == null) return null;
        pcr.setClient(demographicDao.getDemographic("" + pcr.getDemographicNo()));
        pcr.setProgram(programDao.getProgram(pcr.getProgramId()));
        pcr.setProvider(providerDao.getProvider(pcr.getProviderNo()));

        return pcr;
    }

    @Autowired
    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    @Autowired
    public void setProgramDao(ProgramDao programDao) {
        this.programDao = programDao;
    }

    @Autowired
    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

}
