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
package io.github.carlos_emr.carlos.admin.web;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Shared parsing and facility-access checks for admin security record flows.
 *
 * @since 2026-05-31
 */
public class SecurityRecordAccessGuard {

    private final SecurityDao securityDao;
    private final ProviderSiteDao providerSiteDao;

    public SecurityRecordAccessGuard() {
        this(SpringUtils.getBean(SecurityDao.class), SpringUtils.getBean(ProviderSiteDao.class));
    }

    SecurityRecordAccessGuard(SecurityDao securityDao, ProviderSiteDao providerSiteDao) {
        this.securityDao = securityDao;
        this.providerSiteDao = providerSiteDao;
    }

    /**
     * Parses a security record id from request input.
     *
     * @param securityIdParam request parameter value; leading and trailing whitespace is ignored
     * @return parsed id, or {@code null} when the value is null, blank, or non-numeric
     */
    public Integer parseSecurityId(String securityIdParam) {
        if (securityIdParam == null || securityIdParam.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.valueOf(securityIdParam.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Loads a security record by id.
     *
     * @param securityId security record id
     * @return matching record, or {@code null} when the id is null or the record does not exist
     */
    public Security findSecurity(Integer securityId) {
        if (securityId == null) {
            return null;
        }

        return securityDao.find(securityId);
    }

    /**
     * Checks whether the security record belongs to the current facility.
     *
     * @param loggedInInfo current logged-in context
     * @param security security record to validate
     * @return {@code true} when the security record is non-null and its provider belongs to the current facility
     */
    public boolean hasCurrentFacilityAccess(LoggedInInfo loggedInInfo, Security security) {
        return security != null && hasCurrentFacilityAccess(loggedInInfo, security.getProviderNo());
    }

    /**
     * Checks whether a provider belongs to the current facility via provider-site mappings.
     *
     * @param loggedInInfo current logged-in context
     * @param providerNo provider number to validate
     * @return {@code true} when the provider is mapped to the current facility; otherwise {@code false}, including for null or blank input
     */
    public boolean hasCurrentFacilityAccess(LoggedInInfo loggedInInfo, String providerNo) {
        if (loggedInInfo == null
                || loggedInInfo.getCurrentFacility() == null
                || loggedInInfo.getCurrentFacility().getId() == null
                || providerNo == null
                || providerNo.trim().isEmpty()) {
            return false;
        }

        Integer currentFacilityId = loggedInInfo.getCurrentFacility().getId();
        List<ProviderSite> providerSites = providerSiteDao.findByProviderNo(providerNo);
        if (providerSites == null) {
            return false;
        }

        return providerSites.stream()
                .map(ProviderSite::getId)
                .filter(id -> id != null)
                .anyMatch(id -> currentFacilityId.intValue() == id.getSiteId());
    }
}
