/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Read-only adapter for the inpatient admission date prefill on Ontario billing.
 *
 * @since 2026-05-01
 */
@Service
public class BillingAdmissionDateLoader {

    private final DemographicManager demographicManager;

    public BillingAdmissionDateLoader(DemographicManager demographicManager) {
        this.demographicManager = demographicManager;
    }

    public String getAdmissionDate(LoggedInInfo loggedInInfo, String demographicNo) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        return demographic == null ? "" : ConversionUtils.toDateString(demographic.getDateJoined());
    }
}
