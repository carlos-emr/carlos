/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services.security;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;

import io.github.carlos_emr.carlos.daos.security.UserAccessDao;
import io.github.carlos_emr.carlos.model.security.UserAccessValue;
import io.github.carlos_emr.carlos.services.LookupManager;

/**
 * Implementation of the {@link UserAccessManager} interface that builds
 * a user's security context from database-stored access control entries.
 *
 * <p>Queries the {@link UserAccessDao} to retrieve function-level and
 * organization-level permissions, then assembles them into a
 * {@link SecurityManager} instance for the authenticated provider.</p>
 *
 * @see UserAccessManager
 * @see SecurityManager
 * @see io.github.carlos_emr.carlos.daos.security.UserAccessDao
 * @since 2026-03-17
 */
public class UserAccessManagerImpl implements UserAccessManager {
    private UserAccessDao _dao = null;

    /** {@inheritDoc} */
    @Override
    public SecurityManager getUserSecurityManager(String providerNo, Integer shelterId, LookupManager lkManager) {
        // _list is ordered by Function, privilege (desc) and the org
        SecurityManager secManager = new SecurityManager();

        Hashtable functionList = new Hashtable();
        List list = _dao.GetUserAccessList(providerNo, shelterId);
        if (list.size() > 0) {
            int startIdx = 0;
            List orgList = getAccessListForFunction(list, startIdx);
            UserAccessValue uav = (UserAccessValue) list.get(startIdx);
            functionList.put(uav.getFunctionCd(), orgList);

            while (orgList != null && startIdx + orgList.size() < list.size()) {
                startIdx += orgList.size();
                orgList = getAccessListForFunction(list, startIdx);

                uav = (UserAccessValue) list.get(startIdx);
                functionList.put(uav.getFunctionCd(), orgList);
            }
        }
        secManager.setUserFunctionAccessList(functionList);
        List orgs = _dao.GetUserOrgAccessList(providerNo, shelterId);
        String orgRoot = CarlosProperties.getInstance().getProperty("ORGROOT");
        if (orgs.size() > 0 && orgRoot != null && orgRoot.equals(orgs.get(0))) {
            orgs.clear();
        }
        secManager.setUserOrgAccessList(orgs);
        return secManager;
    }

    /** {@inheritDoc} */
    @Override
    public List getAccessListForFunction(List list, int startIdx) {
        if (startIdx >= list.size()) return null;
        List orgList = new ArrayList();
        UserAccessValue uofv = (UserAccessValue) list.get(startIdx);
        String functionCd = uofv.getFunctionCd();
        orgList.add(uofv);
        startIdx++;
        while (startIdx < list.size()) {
            uofv = (UserAccessValue) list.get(startIdx);
            if (uofv.getFunctionCd().equals(functionCd)) {
                orgList.add(uofv);
                startIdx++;
            } else {
                break;
            }
        }
        return orgList;
    }

    /** {@inheritDoc} */
    @Override
    public void setUserAccessDao(UserAccessDao dao) {
        _dao = dao;
    }
}
