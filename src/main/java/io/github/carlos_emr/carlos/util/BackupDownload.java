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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class BackupDownload extends GenericDownload {

    @SuppressWarnings("unchecked")
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(true);

        // check the rights - sanitize filename to prevent XSS and path traversal
        String rawFilename = req.getParameter("filename");
        String filename = (rawFilename == null) ? "null" : MiscUtils.sanitizeFileName(rawFilename);
        String dir = (String) session.getAttribute("backupfilepath") == null ? "/home/mysql/" : (String) session.getAttribute("backupfilepath");

        boolean adminPrivs = false;

        String roleName = (String) req.getSession().getAttribute("userrole") + "," + (String) req.getSession().getAttribute("user");
        Object[] v = getPrivilegeProp("_admin.backup,_admin");
        if (checkPrivilege(roleName, (Properties) v[0], (List<String>) v[1])) {
            adminPrivs = true;
        }

        boolean bDownload = false;
        if (filename != null && adminPrivs) {
            bDownload = true;
        }
        download(bDownload, res, dir, filename, null);
    }

    //TODO: Refactor this out of the sec tag.
    private String rights = "r";

    private Object[] getPrivilegeProp(String objName) {
        String[] objectNames = getVecObjectName(objName);

        SecObjPrivilegeDao dao = SpringUtils.getBean(SecObjPrivilegeDao.class);
        List<SecObjPrivilege> priviledges = dao.findByObjectNames(Arrays.asList(objectNames));

        Properties prop = new Properties();
        List<String> roleInObj = new ArrayList<String>();
        for (SecObjPrivilege p : priviledges) {
            prop.setProperty(p.getId().getRoleUserGroup(), p.getPrivilege());
            roleInObj.add(p.getId().getRoleUserGroup());
        }

        return new Object[]{prop, roleInObj};
    }

    private Properties getVecRole(String roleName) {
        Properties prop = new Properties();
        String[] temp = roleName.split("\\,");
        for (int i = 0; i < temp.length; i++) {
            prop.setProperty(temp[i], "1");
        }
        return prop;
    }

    private String[] getVecObjectName(String objectName) {
        String[] temp = objectName.split("\\,");
        return temp;
    }

    private List<String> getVecPrivilege(String privilege) {
        List<String> vec = new ArrayList<String>();
        String[] temp = privilege.split("\\|");
        for (int i = 0; i < temp.length; i++) {
            if ("".equals(temp[i])) continue;
            vec.add(temp[i]);
        }
        return vec;
    }

    private boolean checkPrivilege(String roleName, Properties propPrivilege, List<String> roleInObj) {
        boolean ret = false;
        Properties propRoleName = getVecRole(roleName);
        for (int i = 0; i < roleInObj.size(); i++) {
            if (!propRoleName.containsKey(roleInObj.get(i))) continue;

            String singleRoleName = roleInObj.get(i);
            String strPrivilege = propPrivilege.getProperty(singleRoleName, "");
            List<String> vecPrivilName = getVecPrivilege(strPrivilege);

            boolean[] check = {false, false};
            for (int j = 0; j < vecPrivilName.size(); j++) {
                check = checkRights(vecPrivilName.get(j), rights);

                if (check[0]) { // get the rights, stop comparing
                    return true;
                }
                if (check[1]) { // get the only rights, stop and return the result
                    return check[0];
                }
            }
        }
        return ret;
    }

    private boolean[] checkRights(String privilege, String rights1) {
        boolean[] ret = {false, false}; // (gotRights, break/continue)

        if ("x".equals(privilege)) {
            ret[0] = true;
        } else if (privilege.compareTo(rights1.toLowerCase()) >= 0) {
            ret[0] = true;
        }
        return ret;
    }
}
