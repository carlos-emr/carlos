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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AppDefinition;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppDefinitionTo1;

/**
 * Service interface for managing third-party application definitions integrated
 * with the CARLOS EMR system.
 *
 * <p>Application definitions describe external applications that interact with
 * the EMR through OAuth or other integration mechanisms, including their
 * consent requirements and configuration metadata.</p>
 *
 * @see AppManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.AppDefinition
 * @since 2026-03-17
 */
public interface AppManager {

    /**
     * Retrieves all registered application definitions as transfer objects.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @return List of AppDefinitionTo1 transfer objects
     */
    public List<AppDefinitionTo1> getAppDefinitions(LoggedInInfo loggedInInfo);

    /**
     * Saves a new application definition.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appDef AppDefinition the application definition to save
     * @return AppDefinition the persisted application definition
     */
    public AppDefinition saveAppDefinition(LoggedInInfo loggedInInfo, AppDefinition appDef);

    /**
     * Updates an existing application definition.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appDef AppDefinition the application definition to update
     * @return AppDefinition the updated application definition
     */
    public AppDefinition updateAppDefinition(LoggedInInfo loggedInInfo, AppDefinition appDef);

    /**
     * Retrieves an application definition by its name.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appName String the unique application name
     * @return AppDefinition the application definition, or null if not found
     */
    public AppDefinition getAppDefinition(LoggedInInfo loggedInInfo, String appName);

    /**
     * Checks whether an application definition exists with the given name.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appName String the application name to check
     * @return boolean true if the application definition exists
     */
    public boolean hasAppDefinition(LoggedInInfo loggedInInfo, String appName);

    /**
     * Retrieves the consent identifier associated with an application definition.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appName String the application name
     * @return Integer the consent ID, or null if not found
     */
    public Integer getAppDefinitionConsentId(LoggedInInfo loggedInInfo, String appName);

}
