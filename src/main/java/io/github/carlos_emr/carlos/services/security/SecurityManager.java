/**
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services.security;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;

import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;

/**
 * Legacy utility manager for Role-Based Access Control (RBAC) privilege checks.
 * This class validates if a given set of roles has read/write/delete privileges
 * for a specific security object (e.g., "_admin.billing", "_encounter").
 * <p>
 * Checks are performed against the {@link Secobjprivilege} entity mappings.
 * Note: Newer code should generally prefer {@code SecurityInfoManager.hasPrivilege()} 
 * using the {@code LoggedInInfo} context.
 */
public class SecurityManager {
    public static final String ACCESS_NONE = "o";
    public static final String ACCESS_READ = "r";
    public static final String ACCESS_UPDATE = "u";
    public static final String ACCESS_WRITE = "w";
    public static final String ACCESS_ALL = "x";
    Hashtable _userFunctionAccessList;
    List _userOrgAccessList;    /* list of all orgs the user has at least read only rights */

    public void setUserFunctionAccessList(Hashtable functionAccessList) {
        _userFunctionAccessList = functionAccessList;
    }

    public List getUserOrgAccessList() {
        return _userOrgAccessList;
    }

    public void setUserOrgAccessList(List orgAccessList) {
        _userOrgAccessList = orgAccessList;
    }

    /**
     * Checks if the provided roles have read ('r') or all ('x') access to the specified object.
     * 
     * @param objectName The name of the secured object/module.
     * @param roleNames A comma-separated list of role names held by the user.
     * @return True if read access is granted, or if no explicit restrictions exist for the object.
     */
    public boolean hasReadAccess(String objectName, String roleNames) {
        boolean result = false;

        SecobjprivilegeDao secobjprivilegeDao = (SecobjprivilegeDao) SpringUtils.getBean(SecobjprivilegeDao.class);

        List<String> rl = new ArrayList<String>();
        for (String tmp : roleNames.split(",")) {
            rl.add(tmp);
        }
        List<Secobjprivilege> priv = secobjprivilegeDao.getByObjectNameAndRoles(objectName, rl);

        if (priv.size() == 0) {
            return true;
        }
        for (Secobjprivilege p : priv) {
            if (p.getPrivilege_code().indexOf("r") != -1)
                result = true;
            if (p.getPrivilege_code().indexOf("x") != -1)
                result = true;
        }
        return result;
    }

    /**
     * Checks if the provided roles have write ('w') or all ('x') access to the specified object.
     * Defaults to NOT requiring an explicit privilege mapping (i.e., returns true if no mapping exists).
     * 
     * @param objectName The name of the secured object.
     * @param roleNames Comma-separated list of roles.
     * @return True if write access is granted or no mapping exists.
     */
    public boolean hasWriteAccess(String objectName, String roleNames) {
        return hasWriteAccess(objectName, roleNames, false);
    }

    /**
     * Checks if the provided roles have write access, optionally enforcing that an explicit
     * permission mapping MUST exist in the database.
     * 
     * @param objectName The secured object name.
     * @param roleNames Comma-separated list of roles.
     * @param required If true, access is denied when no explicit mapping exists in the DB.
     * @return True if write access is granted according to the constraints.
     */
    public boolean hasWriteAccess(String objectName, String roleNames, boolean required) {
        boolean result = false;

        SecobjprivilegeDao secobjprivilegeDao = (SecobjprivilegeDao) SpringUtils.getBean(SecobjprivilegeDao.class);

        List<String> rl = new ArrayList<String>();
        for (String tmp : roleNames.split(",")) {
            rl.add(tmp);
        }
        List<Secobjprivilege> priv = secobjprivilegeDao.getByObjectNameAndRoles(objectName, rl);

        if (!required && priv.size() == 0) {
            return true;
        }
        if (required && priv.size() == 0) {
            return false;
        }
        for (Secobjprivilege p : priv) {
            if (p.getPrivilege_code().indexOf("w") != -1)
                result = true;
            if (p.getPrivilege_code().indexOf("x") != -1)
                result = true;
        }
        return result;
    }

    /**
     * Checks if the provided roles have delete ('d') or all ('x') access to the specified object.
     * 
     * @param objectName The secured object name.
     * @param roleNames Comma-separated list of roles.
     * @return True if delete access is granted, or if no explicit restrictions exist for the object.
     */
    public boolean hasDeleteAccess(String objectName, String roleNames) {
        boolean result = false;

        SecobjprivilegeDao secobjprivilegeDao = (SecobjprivilegeDao) SpringUtils.getBean(SecobjprivilegeDao.class);

        List<String> rl = new ArrayList<String>();
        for (String tmp : roleNames.split(",")) {
            rl.add(tmp);
        }
        List<Secobjprivilege> priv = secobjprivilegeDao.getByObjectNameAndRoles(objectName, rl);

        if (priv.size() == 0) {
            return true;
        }
        for (Secobjprivilege p : priv) {
            if (p.getPrivilege_code().indexOf("d") != -1)
                result = true;
            if (p.getPrivilege_code().indexOf("x") != -1)
                result = true;
        }
        return result;
    }

    /**
     * Checks legacy property-file based privilege configurations.
     * 
     * @param objectName The object name.
     * @param roleName The role to check.
     * @return True if privileged.
     */
    public static boolean hasPrivilege(String objectName, String roleName) {
        ArrayList<Object> v = OscarRoleObjectPrivilege.getPrivilegePropAsArrayList(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (ArrayList<String>) v.get(1));
    }
}
