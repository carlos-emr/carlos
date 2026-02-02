//CHECKSTYLE:OFF
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

    public boolean hasWriteAccess(String objectName, String roleNames) {
        return hasWriteAccess(objectName, roleNames, false);
    }

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

    public static boolean hasPrivilege(String objectName, String roleName) {
        ArrayList<Object> v = OscarRoleObjectPrivilege.getPrivilegePropAsArrayList(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (ArrayList<String>) v.get(1));
    }
}
