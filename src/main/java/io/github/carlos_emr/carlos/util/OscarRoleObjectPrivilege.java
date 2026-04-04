/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.jsp.PageContext;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class OscarRoleObjectPrivilege {

    private static PageContext pageContext;
    private static String rights = "r";

    /**
     * Ordered privilege hierarchy: read &lt; update &lt; write.
     * Delete ("d") is intentionally absent — it is an independent privilege that
     * does not imply any of r/u/w and is matched by exact equality instead.
     */
    private static final String PRIVILEGE_HIERARCHY = "ruw";

    public static Vector<Object> getPrivilegeProp(String objName) {
        Properties prop = new Properties();
        Vector<String> roleInObj = new Vector<String>();
        ArrayList<String> priority = new ArrayList<String>();

        String[] objectNames = getVecObjectName(objName);
        SecObjPrivilegeDao dao = SpringUtils.getBean(SecObjPrivilegeDao.class);
        for (SecObjPrivilege s : dao.findByObjectNames(Arrays.asList(objectNames))) {
            prop.setProperty(s.getId().getRoleUserGroup(), s.getPrivilege());
            roleInObj.add(s.getId().getRoleUserGroup());
            priority.add("" + s.getPriority());
        }

        Vector<Object> ret = new Vector<Object>();
        ret.add(prop);
        ret.add(roleInObj);
        ret.add(priority);
        return ret;
    }

    public static ArrayList<Object> getPrivilegePropAsArrayList(String objName) {
        ArrayList<Object> ret = new ArrayList<Object>();
        Properties prop = new Properties();

        SecObjPrivilegeDao dao = (SecObjPrivilegeDao) SpringUtils.getBean(SecObjPrivilegeDao.class);
        String[] objectNames = getVecObjectName(objName);
        ArrayList<String> objects = new ArrayList<String>();

        for (String t : objectNames) {
            objects.add(t);
        }

        List<SecObjPrivilege> privileges = dao.findByObjectNames(objects);

        ArrayList<String> roleInObj = new ArrayList<String>();
        for (SecObjPrivilege sop : privileges) {
            prop.setProperty(sop.getId().getRoleUserGroup(), sop.getPrivilege());
            roleInObj.add(sop.getId().getRoleUserGroup());
        }
        ret.add(prop);
        ret.add(roleInObj);

        return ret;
    }

    /**
     * returns the providers roles as properties object
     */
    private static Properties getVecRole(String roleName) {
        Properties prop = new Properties();
        String[] temp = roleName.split("\\,");
        for (int i = 0; i < temp.length; i++) {
            prop.setProperty(temp[i], "1");
        }
        return prop;
    }

    private static String[] getVecObjectName(String objectName) {
        String[] temp = objectName.split("\\,");
        return temp;
    }

    private static ArrayList<String> getPrivilege(String privilege) {
        ArrayList<String> vec = new ArrayList<String>();
        if (privilege != null) {
            String[] temp = privilege.split("\\|");
            for (int i = 0; i < temp.length; i++) {
                temp[i] = StringUtils.trimToNull(temp[i]);
                if (temp[i] == null) continue;
                vec.add(temp[i]);
            }
        }

        return vec;
    }


    public static boolean checkPrivilege(String roleName, Properties propPrivilege, List<String> roleInObj) {
        return checkPrivilege(roleName, propPrivilege, roleInObj, rights);
    }

    public static boolean checkPrivilege(String roleName, Properties propPrivilege, List<String> roleInObj, String rightCustom) {
        return checkPrivilege(roleName, propPrivilege, roleInObj, null, rightCustom);
    }

    public static boolean checkPrivilege(String roleName, Properties propPrivilege, List<String> roleInObj, List<String> priority, String rightCustom) {
        boolean ret = false;
        Properties propRoleName = getVecRole(roleName);
        for (int i = 0; i < roleInObj.size(); i++) {
            if (!propRoleName.containsKey(roleInObj.get(i))) continue;

            String singleRoleName = roleInObj.get(i);
            String strPrivilege = propPrivilege.getProperty(singleRoleName);
            List<String> vecPrivilName = getPrivilege(strPrivilege);

            boolean[] check = {false, false};
            for (int j = 0; j < vecPrivilName.size(); j++) {
                check = checkRights(vecPrivilName.get(j), rightCustom);

                if (check[0]) { // get the rights, stop comparing
                    return true;
                }
                if (check[1]) { // get the only rights, stop and return the result
                    return check[0];
                }
            }
            if (priority != null && priority.get(i) != null) {
                // Since higher priority goes first in the list, if priority>0 we can skip the rest
                if (!priority.get(i).trim().equals("") && !priority.get(i).trim().equals("0")) break;
            }
        }
        return ret;
    }

    private static boolean[] checkRights(String privilege, String rights1) {
        boolean[] ret = {false, false}; // (gotRights, break/continue)
        String rightsLower = rights1.toLowerCase(Locale.ROOT);
        String privilegeLower = privilege.toLowerCase(Locale.ROOT);
        if ("o".equals(rightsLower)) {
            // NORIGHTS check: only an explicit "o" privilege matches, so that "r", "u", "w"
            // privileges do not incorrectly trigger account locking.
            ret[0] = "o".equals(privilegeLower);
        } else if ("x".equals(privilegeLower)) {
            // Full access matches any non-NORIGHTS check.
            ret[0] = true;
        } else {
            // Hierarchy r < u < w (see PRIVILEGE_HIERARCHY).
            // indexOf returns -1 for anything not in the hierarchy (e.g. "d" or "o"),
            // which causes the condition to be false and falls through to exact match.
            int privLevel = PRIVILEGE_HIERARCHY.indexOf(privilegeLower);
            int requiredLevel = PRIVILEGE_HIERARCHY.indexOf(rightsLower);
            if (privLevel >= 0 && requiredLevel >= 0) {
                // Both are in the r/u/w hierarchy: higher level implies lower ones.
                ret[0] = privLevel >= requiredLevel;
            } else {
                // Not in the r/u/w hierarchy (e.g. "d" delete): exact match only.
                ret[0] = privilegeLower.equals(rightsLower);
            }
        }
        return ret;
    }

    public ApplicationContext getAppContext() {
        return WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext());
    }
}
