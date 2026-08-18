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
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

/**
 * DAO for the secUserRole table.  Uses {@link Secuserrole} (the canonical JPA entity
 * with auto-increment PK) internally and converts to/from the {@link SecUserRole} DTO
 * that the interface exposes.
 */
@Transactional
public class SecUserRoleDaoImpl extends AbstractJpaDao implements SecUserRoleDao {

    private static Logger log = MiscUtils.getLogger();

    /** Convert a {@code Secuserrole} JPA entity to the DTO used by callers. */
    private static SecUserRole toDTO(Secuserrole s) {
        if (s == null) return null;
        SecUserRole r = new SecUserRole(s.getRoleName(), s.getProviderNo());
        r.setActive(s.getActiveyn() != null && s.getActiveyn() == 1);
        r.setOrgCd(s.getOrgcd());
        r.setLastUpdateDate(s.getLastUpdateDate());
        return r;
    }

    private static List<SecUserRole> toDTOList(List<Secuserrole> entities) {
        List<SecUserRole> out = new ArrayList<>(entities.size());
        for (Secuserrole e : entities) out.add(toDTO(e));
        return out;
    }

    @Override
    public List<SecUserRole> getUserRoles(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Secuserrole s where s.providerNo = ?1";
        @SuppressWarnings("unchecked")
        List<Secuserrole> entities = (List<Secuserrole>) JpqlQueryHelper.find(entityManager(), sSQL, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getUserRoles: providerNo=" + providerNo + ",# of results=" + entities.size());
        }

        return toDTOList(entities);
    }

    @Override
    public List<SecUserRole> getSecUserRolesByRoleName(String roleName) {
        String sSQL = "from Secuserrole s where s.roleName = ?1";
        @SuppressWarnings("unchecked")
        List<Secuserrole> entities = (List<Secuserrole>) JpqlQueryHelper.find(entityManager(), sSQL, roleName);
        return toDTOList(entities);
    }

    @Override
    public List<SecUserRole> findByRoleNameAndProviderNo(String roleName, String providerNo) {
        String sSQL = "from Secuserrole s where s.roleName = ?1 and s.providerNo=?2";
        @SuppressWarnings("unchecked")
        List<Secuserrole> entities = (List<Secuserrole>) JpqlQueryHelper.find(entityManager(), sSQL, roleName, providerNo);
        return toDTOList(entities);
    }

    @Override
    public boolean hasAdminRole(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        // An inactive admin assignment (activeyn = 0 or legacy NULL) must not grant admin access.
        String sSQL = "from Secuserrole s where s.providerNo = ?1 and s.roleName = 'admin' and s.activeyn = 1";
        @SuppressWarnings("unchecked")
        List<Secuserrole> entities = (List<Secuserrole>) JpqlQueryHelper.find(entityManager(), sSQL, providerNo);
        boolean result = !entities.isEmpty();

        if (log.isDebugEnabled()) {
            log.debug("hasAdminRole: providerNo=" + providerNo + ",result=" + result);
        }

        return result;
    }

    @Override
    public SecUserRole find(Long id) {
        if (id == null || id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("secUserRole id must fit the integer primary key range");
        }
        return toDTO(entityManager().find(Secuserrole.class, id.intValue()));
    }

    @Override
    public void save(SecUserRole sur) {
        sur.setLastUpdateDate(new Date());

        String hql = "from Secuserrole s where s.roleName = ?1 and s.providerNo = ?2";
        @SuppressWarnings("unchecked")
        List<Secuserrole> existing = (List<Secuserrole>) JpqlQueryHelper.find(
                entityManager(), hql, sur.getRoleName(), sur.getProviderNo());

        Secuserrole entity = existing.isEmpty() ? new Secuserrole() : existing.get(0);
        entity.setProviderNo(sur.getProviderNo());
        entity.setRoleName(sur.getRoleName());
        entity.setActiveyn(sur.getActive() ? 1 : 0);
        entity.setOrgcd(sur.getOrgCd());
        entity.setLastUpdateDate(sur.getLastUpdateDate());

        if (existing.isEmpty()) {
            entityManager().persist(entity);
        }
    }

    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String sSQL = "select p.providerNo From Secuserrole p WHERE p.lastUpdateDate > ?1";
        @SuppressWarnings("unchecked")
        List<String> records = (List<String>) JpqlQueryHelper.find(entityManager(), sSQL, date);
        return records;
    }

}
