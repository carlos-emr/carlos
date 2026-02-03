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

package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao;
import io.github.carlos_emr.carlos.commn.dao.AppUserDao;
import io.github.carlos_emr.carlos.commn.model.AppDefinition;
import io.github.carlos_emr.carlos.commn.model.AppUser;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppDefinitionTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class AppManagerImpl implements AppManager {
    protected Logger logger = MiscUtils.getLogger();

    @Autowired
    private AppDefinitionDao appDefinitionDao;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;


    @Override
    public List<AppDefinitionTo1> getAppDefinitions(LoggedInInfo loggedInInfo) {
        List<AppDefinition> appList = appDefinitionDao.findAll();
        List<AppDefinitionTo1> returningAppList = new ArrayList<AppDefinitionTo1>(appList.size());
        for (AppDefinition app : appList) {
            AppDefinitionTo1 appTo = new AppDefinitionTo1();
            appTo.setId(app.getId());
            appTo.setAdded(app.getAdded());
            appTo.setAppType(app.getAppType());
            appTo.setName(app.getName());
            appTo.setActive(app.getActive());
            appTo.setAddedBy(app.getAddedBy());
            AppUser appuser = appUserDao.findForProvider(app.getId(), loggedInInfo.getLoggedInProviderNo());
            if (appuser != null) {
                appTo.setAuthenticated(true);
            } else {
                appTo.setAuthenticated(false);
            }
            returningAppList.add(appTo);
        }

        //--- log action ---
        if (returningAppList != null && returningAppList.size() > 0) {
            String resultIds = AppDefinition.getIdsAsStringList(appList);
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.getAppDefinitions", "ids returned=" + resultIds);
        }

        return returningAppList;
    }

    @Override
    public AppDefinition saveAppDefinition(LoggedInInfo loggedInInfo, AppDefinition appDef) {
        //Can user create new AppDefinitions?
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appDefinition", "w", null)) {
            throw new RuntimeException("Access Denied");
        }

        appDefinitionDao.persist(appDef);

        //--- log action ---
        if (appDef != null) {
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.saveAppDefinition", "id=" + appDef.getId());
        }
        return appDef;
    }

    @Override
    public AppDefinition updateAppDefinition(LoggedInInfo loggedInInfo, AppDefinition appDef) {
        //Can user create new AppDefinitions?
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appDefinition", "w", null)) {
            throw new RuntimeException("Access Denied");
        }

        appDefinitionDao.merge(appDef);

        //--- log action ---
        if (appDef != null) {
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.updateAppDefinition", "id=" + appDef.getId());
        }
        return appDef;
    }

    @Override
    public AppDefinition getAppDefinition(LoggedInInfo loggedInInfo, String appName) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appDefinition", "r", null)) {
            throw new RuntimeException("Access Denied");
        }
        AppDefinition appDef = appDefinitionDao.findByName(appName);

        //--- log action ---
        if (appDef != null) {
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.getAppDefinition", "id=" + appDef.getId());
        }
        return appDef;
    }

    @Override
    public boolean hasAppDefinition(LoggedInInfo loggedInInfo, String appName) {
        AppDefinition appDef = appDefinitionDao.findByName(appName);

        //--- log action ---
        if (appDef != null) {
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.hasAppDefinition", "id=" + appDef.getId());
            return true;
        }

        LogAction.addLogSynchronous(loggedInInfo, "AppManager.hasAppDefinition", "Not found:" + appName);
        return false;
    }


    @Override
    public Integer getAppDefinitionConsentId(LoggedInInfo loggedInInfo, String appName) {
        AppDefinition appDef = appDefinitionDao.findByName(appName);
        Integer retval = null;
        //--- log action ---
        if (appDef != null) {
            retval = appDef.getConsentTypeId();
            LogAction.addLogSynchronous(loggedInInfo, "AppManager.getAppDefinitionConsentId", "id=" + retval);
        }
        return retval;
    }


}
 