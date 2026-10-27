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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.PMmodule.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.model.CaisiAccessType;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class RoleCache {

    static SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
    static DefaultRoleAccessDAO defaultRoleAccessDAO =
            (DefaultRoleAccessDAO) SpringUtils.getBean(DefaultRoleAccessDAO.class);
    static CaisiAccessTypeDao accessTypeDao =
            (CaisiAccessTypeDao) SpringUtils.getBean(CaisiAccessTypeDao.class);

    private record Snapshot(
            Map<Long, Secrole> roles,
            Map<String, Integer> accessTypes,
            Map<Long, Set<Long>> defaultAccess) {}

    private static final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    /** A failed or held reload must preserve the previous complete policy, never publish partial maps. */
    public static synchronized void reload() {
        Map<String, Integer> accessTypes = new HashMap<>();
        for (CaisiAccessType at : accessTypeDao.findAll())
            accessTypes.put(at.getName(), at.getId());
        Map<Long, Secrole> roles = new HashMap<>();
        for (Secrole role : secroleDao.getRoles()) roles.put(role.getId(), role);
        Map<Long, Set<Long>> defaults = new HashMap<>();
        for (DefaultRoleAccess access : defaultRoleAccessDAO.findAll()) {
            defaults.computeIfAbsent(access.getAccessTypeId(), ignored -> new HashSet<>())
                    .add(access.getRoleId());
        }
        Map<Long, Set<Long>> frozenDefaults = new HashMap<>();
        defaults.forEach((type, selected) -> frozenDefaults.put(type, Set.copyOf(selected)));
        snapshot.set(
                new Snapshot(
                        Map.copyOf(roles), Map.copyOf(accessTypes), Map.copyOf(frozenDefaults)));
    }

    public static boolean hasAccess(String accessTypeName, long roleId) {
        Snapshot current = requiredSnapshot();
        Integer accessTypeId = current.accessTypes().get(accessTypeName);
        if (accessTypeId == null) {
            MiscUtils.getLogger().warn("Access Type not found:" + accessTypeName);
            return false;
        }
        Set<Long> roles = current.defaultAccess().get(accessTypeId.longValue());
        return roles != null && roles.contains(roleId);
    }

    public static Secrole getRole(Long id) {
        return requiredSnapshot().roles().get(id);
    }

    public static synchronized void setRole(Long roleId, Secrole role) {
        Snapshot current = requiredSnapshot();
        Map<Long, Secrole> roles = new HashMap<>(current.roles());
        roles.put(roleId, role);
        snapshot.set(
                new Snapshot(Map.copyOf(roles), current.accessTypes(), current.defaultAccess()));
    }

    private static Snapshot requiredSnapshot() {
        Snapshot current = snapshot.get();
        if (current == null) throw new IllegalStateException("ROLE_CACHE_NOT_INITIALIZED");
        return current;
    }
}
