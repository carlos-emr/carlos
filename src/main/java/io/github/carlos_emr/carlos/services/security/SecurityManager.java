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
 * Manages per-user security context including function-level access control
 * and organization-scoped permissions in the CARLOS EMR system.
 *
 * <p>This class maintains a user's resolved privilege set, mapping security
 * object names to access levels (none, read, update, write, all). It evaluates
 * privilege checks against the {@link SecobjprivilegeDao} to determine whether
 * the current user has the required permission for a given EMR function.</p>
 *
 * <p>Access level constants define the privilege hierarchy:</p>
 * <ul>
 *   <li>{@code "o"} - No access</li>
 *   <li>{@code "r"} - Read access</li>
 *   <li>{@code "u"} - Update access</li>
 *   <li>{@code "w"} - Write (create) access</li>
 *   <li>{@code "x"} - Full access (includes all operations)</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao
 * @see io.github.carlos_emr.carlos.model.security.Secobjprivilege
 * @since 2026-03-17
 */
public class SecurityManager {
    /** Access level: no access. */
    public static final String ACCESS_NONE = "o";
    /** Access level: read-only access. */
    public static final String ACCESS_READ = "r";
    /** Access level: update access. */
    public static final String ACCESS_UPDATE = "u";
    /** Access level: write (create) access. */
    public static final String ACCESS_WRITE = "w";
    /** Access level: full access (all operations). */
    public static final String ACCESS_ALL = "x";
    Hashtable _userFunctionAccessList;
    List _userOrgAccessList;    /* list of all orgs the user has at least read only rights */

    /**
     * Sets the function-level access list for the current user.
     *
     * @param functionAccessList Hashtable mapping function codes to their access entries
     */
    public void setUserFunctionAccessList(Hashtable functionAccessList) {
        _userFunctionAccessList = functionAccessList;
    }

    /**
     * Returns the list of organizations the user has at least read access to.
     *
     * @return List of organization identifiers accessible by the user
     */
    public List getUserOrgAccessList() {
        return _userOrgAccessList;
    }

    /**
     * Sets the list of organizations accessible by the current user.
     *
     * @param orgAccessList List of organization identifiers
     */
    public void setUserOrgAccessList(List orgAccessList) {
        _userOrgAccessList = orgAccessList;
    }


    /**
     * Checks whether any of the specified roles grant read access to the named object.
     * Returns true if no privilege entries exist for the object (open by default).
     *
     * @param objectName String the security object name to check
     * @param roleNames String comma-separated role names to evaluate
     * @return boolean true if read or full access is granted
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
     * Checks whether any of the specified roles grant write access to the named object.
     * Delegates to {@link #hasWriteAccess(String, String, boolean)} with required=false.
     *
     * @param objectName String the security object name to check
     * @param roleNames String comma-separated role names to evaluate
     * @return boolean true if write or full access is granted
     */
    public boolean hasWriteAccess(String objectName, String roleNames) {
        return hasWriteAccess(objectName, roleNames, false);
    }

    /**
     * Checks whether any of the specified roles grant write access to the named object.
     * When required is false and no privilege entries exist, access is granted by default.
     * When required is true and no privilege entries exist, access is denied.
     *
     * @param objectName String the security object name to check
     * @param roleNames String comma-separated role names to evaluate
     * @param required boolean if true, privilege entries must exist for access to be granted
     * @return boolean true if write or full access is granted
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
     * Checks whether any of the specified roles grant delete access to the named object.
     * Returns true if no privilege entries exist for the object (open by default).
     *
     * @param objectName String the security object name to check
     * @param roleNames String comma-separated role names to evaluate
     * @return boolean true if delete or full access is granted
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
     * Static convenience method that checks whether a role has any privilege
     * on the named object using the {@link OscarRoleObjectPrivilege} utility.
     *
     * @param objectName String the security object name to check
     * @param roleName String the role name to evaluate
     * @return boolean true if the role has the required privilege
     */
    public static boolean hasPrivilege(String objectName, String roleName) {
        ArrayList<Object> v = OscarRoleObjectPrivilege.getPrivilegePropAsArrayList(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (ArrayList<String>) v.get(1));
    }
}
