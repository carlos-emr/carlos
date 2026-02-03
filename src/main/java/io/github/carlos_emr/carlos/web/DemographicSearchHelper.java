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


package io.github.carlos_emr.carlos.web;

import java.net.MalformedURLException;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.caisi_integrator.CaisiIntegratorManager;
import io.github.carlos_emr.carlos.caisi_integrator.ws.DemographicWs;
import io.github.carlos_emr.carlos.caisi_integrator.ws.MatchingDemographicParameters;
import io.github.carlos_emr.carlos.caisi_integrator.ws.MatchingDemographicTransferScore;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public final class DemographicSearchHelper {

    public static List<MatchingDemographicTransferScore> getIntegratedSearchResults(LoggedInInfo loggedInInfo, MatchingDemographicParameters matchingDemographicParameters) throws MalformedURLException {
        if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) return (null);

        DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());

        List<MatchingDemographicTransferScore> integratedMatches = demographicWs.getMatchingDemographics(matchingDemographicParameters);
        return (integratedMatches);
    }
}
