//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.managers;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.exception.PatientDirectiveException;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.openosp.openo.util.OscarRoleObjectPrivilege;

import ca.openosp.openo.daos.security.SecobjprivilegeDao;
import ca.openosp.openo.daos.security.SecuserroleDao;
import ca.openosp.openo.model.security.Secobjprivilege;
import ca.openosp.openo.model.security.Secuserrole;

import javax.servlet.http.HttpSession;

@Service
public class SecurityInfoManagerImpl implements SecurityInfoManager {

    @Autowired
    private SecuserroleDao secUserRoleDao;

    @Autowired
    private SecobjprivilegeDao secobjprivilegeDao;

    @Override
    public List<Secuserrole> getRoles(LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
            return Collections.emptyList();
        }

        return secUserRoleDao.findByProviderNo(loggedInInfo.getLoggedInProviderNo());
    }

    @Override
    public List<Secobjprivilege> getSecurityObjects(LoggedInInfo loggedInInfo) {

        List<String> roleNames = new ArrayList<>();
        for (Secuserrole role : getRoles(loggedInInfo)) {
            roleNames.add(role.getRoleName());
        }
        roleNames.add(loggedInInfo.getLoggedInProviderNo());

        return secobjprivilegeDao.getByRoles(roleNames);
    }

    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
        return hasPrivilege(loggedInInfo, objectName, privilege, String.valueOf(demographicNo));
    }

    /**
     * Checks to see if this provider has the privilege to the sec object being
     * requested.
     * <p>
     * The way it's coded now
     * <p>
     * get all the roles associated with the logged in providers, including the
     * roleName=providerNo.
     * find the privileges using the roles list.
     * <p>
     * Loop through all the rights, if we find one that can evaluate to true , we
     * exit..else we keep checking
     * <p>
     * if r then an entry with r | u |w | x is required
     * if u then an entry with u | w | x is required
     * if w then an entry with w | x is required
     * if d then an entry with d | x is required
     * <p>
     * Privileges priority is taken care of by
     * OscarRoleObjectPrivilege.checkPrivilege()
     * <p>
     * If patient-specific privileges are present, it takes priority over the
     * general privileges.
     * For checking non-patient-specific object privileges, call with
     * demographicNo==null.
     */
    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo) {
        try {
            List<String> roleNameLs = new ArrayList<>();
            for (Secuserrole role : getRoles(loggedInInfo)) {
                roleNameLs.add(role.getRoleName());
            }
            roleNameLs.add(loggedInInfo.getLoggedInProviderNo());
            String roleNames = StringUtils.join(roleNameLs, ",");

            boolean noMatchingRoleToSpecificPatient = true;
            List<Object> v = null;
            if (demographicNo != null) {
                v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName + "$" + demographicNo);
                List<String> roleInObj = (List<String>) v.get(1);

                for (String objRole : roleInObj) {
                    if (roleNames.toLowerCase().contains(objRole.toLowerCase().trim())) {
                        noMatchingRoleToSpecificPatient = false;
                        break;
                    }
                }
            }

            if (noMatchingRoleToSpecificPatient) {
                v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
            }

            if (!noMatchingRoleToSpecificPatient && OscarRoleObjectPrivilege.checkPrivilege(roleNames,
                    (Properties) v.get(0), (List<String>) v.get(1), (List<String>) v.get(2), NORIGHTS)) {
                HttpSession returnSession = loggedInInfo.getSession();
                returnSession.setAttribute("accountLocked", true);
                loggedInInfo.setSession(returnSession);
            } else if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0),
                    (List<String>) v.get(1), (List<String>) v.get(2), "x")) {
                return true;
            } else if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0),
                    (List<String>) v.get(1), (List<String>) v.get(2), WRITE)) {
                return ((READ + UPDATE + WRITE).contains(privilege));
            } else if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0),
                    (List<String>) v.get(1), (List<String>) v.get(2), UPDATE)) {
                return ((READ + UPDATE).contains(privilege));
            } else if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0),
                    (List<String>) v.get(1), (List<String>) v.get(2), READ)) {
                return (READ.equals(privilege));
            } else if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0),
                    (List<String>) v.get(1), (List<String>) v.get(2), DELETE)) {
                return (DELETE.equals(privilege));
            }

        } catch (PatientDirectiveException ex) {
            throw (ex);
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error checking privileges", ex);
        }

        return false;
    }

    @Override
    public boolean isAllowedAccessToPatientRecord(LoggedInInfo loggedInInfo, Integer demographicNo) {

        List<String> roleNameLs = new ArrayList<>();
        for (Secuserrole role : getRoles(loggedInInfo)) {
            roleNameLs.add(role.getRoleName());
        }
        roleNameLs.add(loggedInInfo.getLoggedInProviderNo());
        String roleNames = StringUtils.join(roleNameLs, ",");

        List<Object> v = OscarRoleObjectPrivilege.getPrivilegeProp("_demographic$" + demographicNo);
        if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0), (List<String>) v.get(1),
                (List<String>) v.get(2), "o")) {
            return false;
        }

        v = OscarRoleObjectPrivilege.getPrivilegeProp("_eChart$" + demographicNo);
        if (OscarRoleObjectPrivilege.checkPrivilege(roleNames, (Properties) v.get(0), (List<String>) v.get(1),
                (List<String>) v.get(2), "o")) {
            return false;
        }

        return true;
    }
}
