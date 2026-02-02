//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.PMmodule.utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.map.MultiValueMap;
import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.model.CaisiAccessType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;

public class RoleCache {

    static SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
    static DefaultRoleAccessDAO defaultRoleAccessDAO = (DefaultRoleAccessDAO) SpringUtils.getBean(DefaultRoleAccessDAO.class);
    static CaisiAccessTypeDao accessTypeDao = (CaisiAccessTypeDao) SpringUtils.getBean(CaisiAccessTypeDao.class);

    static Map<Long, Secrole> roleMap = new HashMap<Long, Secrole>();
    static Map<String, Integer> accessTypeMap = new HashMap<String, Integer>();
    static MultiValueMap defaultRoleAccessMap = new MultiValueMap();

    public static void reload() {
        accessTypeMap.clear();
        for (CaisiAccessType at : accessTypeDao.findAll()) {
            accessTypeMap.put(at.getName(), at.getId());
        }
        roleMap.clear();
        for (Secrole role : secroleDao.getRoles()) {
            setRole(role.getId(), role);
        }
        defaultRoleAccessMap.clear();
        for (DefaultRoleAccess dra : defaultRoleAccessDAO.findAll()) {
            long roleId = dra.getRoleId();
            long accessTypeId = dra.getAccessTypeId();
            defaultRoleAccessMap.put(accessTypeId, roleId);
        }
    }

    public static boolean hasAccess(String accessTypeName, long roleId) {
        Integer accessTypeId = accessTypeMap.get(accessTypeName);
        if (accessTypeId == null) {
            MiscUtils.getLogger().warn("Access Type not found:" + accessTypeName);
            return false;
        }
        Collection<Long> roles = defaultRoleAccessMap.getCollection(accessTypeId.longValue());
        if (roles != null && roles.contains(roleId))
            return true;
        return false;
    }

    public static Secrole getRole(Long id) {
        return roleMap.get(id);
    }

    public static void setRole(Long roleId, Secrole role) {
        roleMap.put(roleId, role);
    }
}
