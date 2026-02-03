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

package io.github.carlos_emr.carlos.commn.model.inbox;

import io.github.carlos_emr.carlos.commn.model.Provider;

/**
 * An abstract class to collect and pass inbox query parameters
 * Extending classes should translate provided parameters to the valid formats used by the inbox repository
 */
public class OscarInboxQueryParameters extends InboxQueryParameters {

    public OscarInboxQueryParameters(Provider loggedInProvider) {
        super(loggedInProvider);
    }

    @Override
    public InboxQueryParameters whereProviderNumber(String providerNumber) {
        if ("-1".equals(providerNumber)) {
            providerNumber = "";
        }
        return super.whereProviderNumber(providerNumber);
    }

    @Override
    public void setStartDate(String startDate) {
        if (startDate == null) {
            startDate = "";
        }
        super.setStartDate(startDate);
    }

    @Override
    public void setEndDate(String endDate) {
        if (endDate == null) {
            endDate = "";
        }
        super.setEndDate(endDate);
    }

    @Override
    public void setStatus(String status) {
        if ("".equals(status)) {
            status = "L";
        }
        super.setStatus(status);
    }

    @Override
    public void setAbnormalStatus(String abnormalStatus) {
        switch (abnormalStatus) {
            case "all":
                abnormalStatus = "L";
                break;
            case "normalOnly":
                abnormalStatus = "N";
                break;
            case "abnormalOnly":
                abnormalStatus = "A";
                break;
            default:
                break;
        }
        super.setAbnormalStatus(abnormalStatus);
    }

    @Override
    public void setMatchedStatus(MatchedStatus matchedStatus) {
        super.setMatchedStatus(matchedStatus);
    }

    @Override
    public void setSortBy(String sortBy) {
        super.setSortBy(sortBy);
    }

    @Override
    public void setSortOrder(String sortOrder) {
        super.setSortOrder(sortOrder);
    }

    @Override
    public void setPage(Integer page) {
        super.setPage(page);
    }

    @Override
    public void setResultsPerPage(Integer resultsPerPage) {
        super.setResultsPerPage(resultsPerPage);
    }

    @Override
    public void setShowDocuments(Boolean showDocuments) {
        super.setShowDocuments(showDocuments);
    }

    @Override
    public void setShowLabs(Boolean showLabs) {
        super.setShowLabs(showLabs);
    }

    @Override
    public void setShowHrm(Boolean showHrm) {
        super.setShowHrm(showHrm);
    }

    @Override
    public void setGetCounts(Boolean getCounts) {
        super.setGetCounts(getCounts);
    }

    @Override
    public void setGetDemographicCounts(Boolean getDemographicCounts) {
        super.setGetDemographicCounts(getDemographicCounts);
    }
}
