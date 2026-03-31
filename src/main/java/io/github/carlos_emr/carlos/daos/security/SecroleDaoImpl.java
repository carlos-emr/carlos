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

package io.github.carlos_emr.carlos.daos.security;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.model.security.Secrole;

@Transactional
public class SecroleDaoImpl extends AbstractHibernateDao implements SecroleDao {

    private Logger logger = MiscUtils.getLogger();

    @Override
    public List<Secrole> getRoles() {
        @SuppressWarnings("unchecked")
        List<Secrole> results = (List<Secrole>) HqlQueryHelper.find(currentSession(), "from Secrole r order by roleName");

        logger.debug("getRoles: # of results=" + results.size());

        return results;
    }

    @Override
    public Secrole getRole(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Secrole result = currentSession().find(Secrole.class, Long.valueOf(id));

        logger.debug("getRole: id=" + id + ",found=" + (result != null));

        return result;
    }

    @Override
    public Secrole getRoleByName(String roleName) {
        Secrole result = null;
        if (roleName == null || roleName.length() <= 0) {
            throw new IllegalArgumentException();
        }

        List lst = HqlQueryHelper.find(currentSession(), "from Secrole r where r.roleName = ?1", roleName);
        if (lst != null && lst.size() > 0)
            result = (Secrole) lst.get(0);

        logger.debug("getRoleByName: roleName=" + roleName + ",found=" + (result != null));

        return result;
    }

    @Override
    public List getDefaultRoles() {
        return HqlQueryHelper.find(currentSession(), "from Secrole r where r.userDefined=0");
    }

    @Override
    public void save(Secrole secrole) {
        if (secrole == null) {
            throw new IllegalArgumentException();
        }

        if (secrole.getId() == null) {
            currentSession().persist(secrole);
        } else {
            currentSession().merge(secrole);
        }

    }

}
