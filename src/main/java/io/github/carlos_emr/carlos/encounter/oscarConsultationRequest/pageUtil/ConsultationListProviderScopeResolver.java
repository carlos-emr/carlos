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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Resolves which most responsible providers (MRPs) a user may filter the Consultations list by.
 *
 * <p>Mirrors the site/team access privacy rules {@code ViewConsultationRequests.jsp} applies to
 * the list rows, so the Provider dropdown and the server-side {@code filterProviderNo} check agree
 * with the rows the page will actually show:</p>
 * <ul>
 *   <li>{@code _team_access_privacy}: only providers on the user's team
 *       ({@link ProviderDataDao#findByProviderTeam(String)}). Enforced with or without multisite.</li>
 *   <li>{@code _site_access_privacy}: only providers sharing a site with the user
 *       ({@link ProviderDataDao#findByProviderSite(String)}), and only when multisite mode is on:
 *       without it there are no site assignments and the restriction would hide everything (the
 *       Flyway seed grants this object to the admin role on every install).</li>
 *   <li>When both apply, team wins, as it does in the JSP.</li>
 * </ul>
 *
 * <p>The row-level filter in the JSP stays in place; this class exists so a crafted
 * {@code filterProviderNo} outside the user's scope is ignored on the server rather than only
 * hidden from the dropdown.</p>
 *
 * @since 2026-09-30
 */
public class ConsultationListProviderScopeResolver {

    private final SecurityInfoManager securityInfoManager;
    private final ProviderDataDao providerDataDao;
    private final BooleanSupplier multisitesEnabled;

    /**
     * @param securityInfoManager SecurityInfoManager used for the privacy security objects
     * @param providerDataDao     ProviderDataDao used to list the providers in scope
     * @param multisitesEnabled   BooleanSupplier reporting whether multisite mode is on
     */
    public ConsultationListProviderScopeResolver(SecurityInfoManager securityInfoManager,
                                                 ProviderDataDao providerDataDao,
                                                 BooleanSupplier multisitesEnabled) {
        this.securityInfoManager = securityInfoManager;
        this.providerDataDao = providerDataDao;
        this.multisitesEnabled = multisitesEnabled;
    }

    /**
     * @param loggedInInfo LoggedInInfo the current user
     * @return the provider numbers the user is restricted to, or an empty Optional when neither
     *         privacy rule applies (every provider is in scope)
     */
    public Optional<Set<String>> resolveAllowedProviderNos(LoggedInInfo loggedInInfo) {
        boolean restrictToTeam = securityInfoManager.hasPrivilege(loggedInInfo, "_team_access_privacy", "r", null);
        boolean restrictToSite = !restrictToTeam && multisitesEnabled.getAsBoolean()
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);
        if (!restrictToTeam && !restrictToSite) {
            return Optional.empty();
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        List<ProviderData> inScope = restrictToTeam
                ? providerDataDao.findByProviderTeam(providerNo)
                : providerDataDao.findByProviderSite(providerNo);
        Set<String> allowed = new LinkedHashSet<>();
        if (inScope != null) {
            for (ProviderData providerData : inScope) {
                if (providerData != null && providerData.getId() != null) {
                    allowed.add(providerData.getId());
                }
            }
        }
        return Optional.of(Collections.unmodifiableSet(allowed));
    }

    /**
     * Drops a requested MRP filter the user is not allowed to use.
     *
     * @param requestedProviderNo String the submitted {@code filterProviderNo}; may be null
     * @param allowedProviderNos  the result of {@link #resolveAllowedProviderNos(LoggedInInfo)}
     * @return the trimmed provider number, or null when it is blank or out of scope
     */
    public static String sanitizeFilterProviderNo(String requestedProviderNo, Optional<Set<String>> allowedProviderNos) {
        if (requestedProviderNo == null || requestedProviderNo.isBlank()) {
            return null;
        }
        String trimmed = requestedProviderNo.trim();
        if (allowedProviderNos.isPresent() && !allowedProviderNos.get().contains(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * Keeps only the dropdown options inside the user's scope.
     *
     * @param options            List of every MRP with consults
     * @param allowedProviderNos the result of {@link #resolveAllowedProviderNos(LoggedInInfo)}
     * @return the options the user may pick, in their original order
     */
    public static List<ConsultationMrpOptionDto> restrictOptions(List<ConsultationMrpOptionDto> options,
                                                                 Optional<Set<String>> allowedProviderNos) {
        if (options == null) {
            return Collections.emptyList();
        }
        if (allowedProviderNos.isEmpty()) {
            return options;
        }
        List<ConsultationMrpOptionDto> restricted = new ArrayList<>();
        for (ConsultationMrpOptionDto option : options) {
            if (allowedProviderNos.get().contains(option.providerNo())) {
                restricted.add(option);
            }
        }
        return restricted;
    }
}
