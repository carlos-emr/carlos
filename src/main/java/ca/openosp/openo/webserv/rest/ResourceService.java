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
package ca.openosp.openo.webserv.rest;

import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.logging.log4j.Logger;

import ca.openosp.openo.commn.dao.AppDefinitionDao;
import ca.openosp.openo.commn.dao.AppUserDao;
import ca.openosp.openo.commn.dao.ResourceStorageDao;
import ca.openosp.openo.commn.model.ResourceStorage;
import ca.openosp.openo.managers.AppManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.OscarProperties;
import ca.openosp.openo.prevention.PreventionDS;


@Path("/resources")
@Consumes(MediaType.APPLICATION_JSON)
public class ResourceService extends AbstractServiceImpl {
    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private AppDefinitionDao appDefinitionDao;

    @Autowired
    AppManager appManager;

    @Autowired
    private AppUserDao appUserDao;

    @Autowired
    private ResourceStorageDao resourceStorageDao;

    @Autowired
    private PreventionDS preventionDS;


    @GET
    @Path("/currentPreventionRulesVersion")
    @Produces("application/json")
    public String getCurrentPreventionRulesVersion() {
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_admin", "r", null) && !securityInfoManager.hasPrivilege(getLoggedInInfo(), "_report", "w", null)) {
            throw new RuntimeException("Access Denied");
        }
        ResourceBundle bundle = getResourceBundle();

        String preventionPath = OscarProperties.getInstance().getProperty("PREVENTION_FILE");
        if (preventionPath != null) {
            return bundle.getString("prevention.currentrules.propertyfile");
        } else {
            ResourceStorage resourceStorage = resourceStorageDao.findActive(ResourceStorage.PREVENTION_RULES);
            if (resourceStorage != null) {
                return bundle.getString("prevention.currentrules.resourceStorage") + " " + resourceStorage.getResourceName();
            }
        }
        return bundle.getString("prevention.currentrules.default");
    }



    @GET
    @Path("/currentLuCodesVersion")
    @Produces("application/json")
    public String getCurrentLuCodesVersion() {
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_admin", "r", null) && !securityInfoManager.hasPrivilege(getLoggedInInfo(), "_report", "w", null)) {
            throw new RuntimeException("Access Denied");
        }
        ResourceBundle bundle = getResourceBundle();

        String fileName = OscarProperties.getInstance().getProperty("odb_formulary_file");
        if (fileName != null && !fileName.isEmpty()) {
            return bundle.getString("lucodes.currentrules.propertyfile");
        } else {
            ResourceStorage resourceStorage = resourceStorageDao.findActive(ResourceStorage.LU_CODES);
            if (resourceStorage != null) {
                return bundle.getString("lucodes.currentrules.resourceStorage") + " " + resourceStorage.getResourceName();
            }
        }
        return bundle.getString("lucodes.currentrules.default");
    }
}