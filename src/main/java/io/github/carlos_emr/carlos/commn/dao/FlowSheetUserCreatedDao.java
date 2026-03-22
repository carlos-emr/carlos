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
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FlowSheetUserCreated;

/**
 * DAO interface for clinical flowsheet operations.
 *
 * @since 2001
 */

public interface FlowSheetUserCreatedDao extends AbstractDao<FlowSheetUserCreated> {

    /**
     * Find Active No Template.
     * @return List<FlowSheetUserCreated>
     */
    List<FlowSheetUserCreated> findActiveNoTemplate();

    /**
     * Get All User Created Flow Sheets.
     * @return List<FlowSheetUserCreated>
     */
    List<FlowSheetUserCreated> getAllUserCreatedFlowSheets();

    /**
     * Find Active By Scope.
     *
     * @param scope String the scope
     * @return List<FlowSheetUserCreated>
     */
    List<FlowSheetUserCreated> findActiveByScope(String scope);

    /**
     * Find By Patient Scope.
     *
     * @param template String the template
     * @param demographicNo Integer the demographicNo
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByPatientScope(String template, Integer demographicNo);

    /**
     * Find By Provider Scope.
     *
     * @param template String the template
     * @param providerNo String the providerNo
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByProviderScope(String template, String providerNo);

    /**
     * Find By Clinic Scope.
     *
     * @param template String the template
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByClinicScope(String template);

    /**
     * Find By Patient Scope Name.
     *
     * @param name String the name
     * @param demographicNo Integer the demographicNo
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByPatientScopeName(String name, Integer demographicNo);

    /**
     * Find By Provider Scope Name.
     *
     * @param name String the name
     * @param providerNo String the providerNo
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByProviderScopeName(String name, String providerNo);

    /**
     * Find By Clinic Scope Name.
     *
     * @param name String the name
     * @return FlowSheetUserCreated
     */
    FlowSheetUserCreated findByClinicScopeName(String name);
}
