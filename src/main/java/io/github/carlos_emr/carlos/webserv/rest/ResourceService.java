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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.ResourceBundle;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao;
import io.github.carlos_emr.carlos.commn.dao.AppUserDao;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.prevention.PreventionDS;


@Path("/resources")
@Consumes(MediaType.APPLICATION_JSON)
/**
 * REST service for system resource and configuration operations.
 *
 * @since 2012-08-13
 */
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

        String preventionPath = CarlosProperties.getInstance().getProperty("PREVENTION_FILE");
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

        String fileName = CarlosProperties.getInstance().getProperty("odb_formulary_file");
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