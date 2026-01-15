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

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.QueueCache;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;

public class ProgramProviderDAOImpl extends HibernateDaoSupport implements ProgramProviderDAO {

    private Logger log = MiscUtils.getLogger();

    private static QueueCache<String, List<ProgramProvider>> programProviderByProviderProgramIdCache = new QueueCache<String, List<ProgramProvider>>(
            4, 100, DateUtils.MILLIS_PER_HOUR, null);

    private static String makeCacheKey(String providerNo, Long programId) {
        return (providerNo + ':' + programId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProgramProvider> getProgramProviderByProviderProgramId(String providerNo, Long programId) {
        String cacheKey = makeCacheKey(providerNo, programId);

        List<ProgramProvider> results = programProviderByProviderProgramIdCache.get(cacheKey);

        if (results == null) {
            String q = "select pp from ProgramProvider pp where pp.ProgramId=?0 and pp.ProviderNo=?1";
            results = (List<ProgramProvider>) getHibernateTemplate().find(q, new Object[]{programId, providerNo});
            if (results != null)
                programProviderByProviderProgramIdCache.put(cacheKey, results);
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProgramProvider> getAllProgramProviders() {
        return (List<ProgramProvider>) getHibernateTemplate().find("FROM ProgramProvider");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProgramProvider> getProgramProviderByProviderNo(String providerNo) {
        String q = "select pp from ProgramProvider pp where pp.ProviderNo=?0";
        return (List<ProgramProvider>) getHibernateTemplate().find(q, providerNo);
    }

    @Override
    public List<ProgramProvider> getProgramProviders(Long programId) {
        if (programId == null || programId.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        @SuppressWarnings("unchecked")
        List<ProgramProvider> results = (List<ProgramProvider>) this.getHibernateTemplate()
                .find("from ProgramProvider pp where pp.ProgramId = ?0", programId);

        if (log.isDebugEnabled()) {
            log.debug("getProgramProviders: programId=" + programId + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public List<ProgramProvider> getProgramProvidersByProvider(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ProgramProvider pp where pp.ProviderNo = ?0";
        List<ProgramProvider> results = (List<ProgramProvider>) this.getHibernateTemplate().find(sSQL, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvidersByProvider: providerNo=" + providerNo + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public List getProgramProvidersByProviderAndFacility(String providerNo, Integer facilityId) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = "from ProgramProvider pp where pp.ProviderNo = ?0 and pp.ProgramId in " +
                "(select s.id from Program s where s.facilityId=?1 or s.facilityId is null)";
        List results = getHibernateTemplate().find(queryStr, new Object[]{providerNo, facilityId});

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvidersByProviderAndFacility: providerNo=" + providerNo + ",# of results="
                    + results.size());
        }
        return results;
    }

    @Override
    public ProgramProvider getProgramProvider(Long id) {
        if (id == null || id.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        ProgramProvider result = this.getHibernateTemplate().get(ProgramProvider.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvider: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    public ProgramProvider getProgramProvider(String providerNo, Long programId, Long roleId) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (roleId == null || roleId.intValue() < 0) {
            throw new IllegalArgumentException();
        }


        ProgramProvider result = null;
        String queryStr = "from ProgramProvider pp where pp.ProviderNo = ?0 and pp.ProgramId = ?1 and pp.RoleId = ?2";
        List results = this.getHibernateTemplate().find(queryStr, new Object[] { providerNo, programId, roleId });

        if (!results.isEmpty()) {
            result = (ProgramProvider) results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvider: providerNo=" + providerNo + ",programId=" + programId + ",roleId=" + roleId + ",found="
                    + (result != null));
        }

        return result;

    }

        @Override
    public ProgramProvider getProgramProvider(String providerNo, Long programId) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramProvider result = null;
        String sSQL = "from ProgramProvider pp where pp.ProviderNo = ?0 and pp.ProgramId = ?1";
        List results = this.getHibernateTemplate().find(sSQL, new Object[]{providerNo, programId});
        if (!results.isEmpty()) {
            result = (ProgramProvider) results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvider: providerNo=" + providerNo + ",programId=" + programId + ",found="
                    + (result != null));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = false)
    public void saveProgramProvider(ProgramProvider pp) {
        if (pp == null) {
            throw new IllegalArgumentException();
        }

        programProviderByProviderProgramIdCache.remove(makeCacheKey(pp.getProviderNo(), pp.getProgramId()));
        getHibernateTemplate().saveOrUpdate(pp);

        if (log.isDebugEnabled()) {
            log.debug("saveProgramProvider: id=" + pp.getId());
        }

    }

    @Override
    public void deleteProgramProvider(Long id) {
        if (id == null || id.intValue() < 0) {
            throw new IllegalArgumentException();
        }

        ProgramProvider pp = getProgramProvider(id);
        if (pp != null) {
            programProviderByProviderProgramIdCache.remove(makeCacheKey(pp.getProviderNo(), pp.getProgramId()));
            getHibernateTemplate().delete(pp);
        }

        if (log.isDebugEnabled()) {
            log.debug("deleteProgramProvider id=" + id);
        }
    }

    @Override
    public void deleteProgramProviderByProgramId(Long programId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        List o = getProgramProviders(programId);
        if (o != null) {
            Iterator it = o.iterator();
            while (it.hasNext()) {
                ProgramProvider pp = (ProgramProvider) it.next();
                programProviderByProviderProgramIdCache.remove(makeCacheKey(pp.getProviderNo(), pp.getProgramId()));
                getHibernateTemplate().delete(pp);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("deleteProgramProvider programId=" + programId);
        }
    }

    @Override
    public List<ProgramProvider> getProgramProvidersInTeam(Integer programId, Integer teamId) {
        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException();
        }
        Long pId = programId.longValue();

        String sSQL = "select pp from ProgramProvider pp left join pp.teams as team where pp.ProgramId = ?0 and team.id = ?1";
        List<ProgramProvider> results = (List<ProgramProvider>) this.getHibernateTemplate().find(sSQL, new Object[]{pId, teamId});

        if (log.isDebugEnabled()) {
            log.debug("getProgramProvidersInTeam: programId=" + programId + ",teamId=" + teamId + ",# of results="
                    + results.size());
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ProgramProvider> getProgramDomain(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ProgramProvider pp where pp.ProviderNo = ?0";
        List results = this.getHibernateTemplate().find(sSQL, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProgramDomain: providerNo=" + providerNo + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public List<ProgramProvider> getActiveProgramDomain(String providerNo) {
        if (providerNo == null || Long.valueOf(providerNo) == null) {
            throw new IllegalArgumentException();
        }

        String sSQL = "select pp from ProgramProvider pp, Program p where pp.ProgramId=p.id and p.programStatus='active' and pp.ProviderNo = ?0";
        List results = this.getHibernateTemplate().find(sSQL, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getProgramDomain: providerNo=" + providerNo + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public List<ProgramProvider> getProgramDomainByFacility(String providerNo, Integer facilityId) {
        if (providerNo == null || Long.valueOf(providerNo) == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = "from ProgramProvider pp where pp.ProviderNo = ?0 and pp.ProgramId in " +
                "(select s.id from Program s where s.facilityId=?1 or s.facilityId is null)";
        List results = getHibernateTemplate().find(queryStr, new Object[]{providerNo, facilityId});

        if (log.isDebugEnabled()) {
            log.debug("getProgramDomainByFacility: providerNo=" + providerNo + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public boolean isThisProgramInProgramDomain(String providerNo, Integer programId) {
        if (providerNo == null || Long.valueOf(providerNo) == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = "from ProgramProvider pp where pp.ProviderNo = ?0 and pp.ProgramId = ?1";
        List results = getHibernateTemplate().find(queryStr,
                new Object[]{providerNo, Long.valueOf(programId.longValue())});
        if (results != null && results.size() > 0) {
            return true;
        } else {
            return false;
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Facility> getFacilitiesInProgramDomain(String providerNo) {
        if (providerNo == null || Long.valueOf(providerNo) == null) {
            throw new IllegalArgumentException();
        }
        String sSQL = "select distinct f from Facility f, Program p, ProgramProvider pp " +
        "where pp.ProgramId = p.id and f.id = p.facilityId and pp.ProviderNo = ?0";
        List results = this.getHibernateTemplate().find(sSQL, providerNo);

        return results;
    }

    @Transactional
    @Override
    public void updateProviderRole(ProgramProvider pp, Long roleId) {
        pp.setRoleId(roleId);
        getHibernateTemplate().update(pp);
    }
}
