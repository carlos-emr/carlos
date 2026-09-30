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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Resolves the Consultant and Provider (MRP) filters of the Consultations list (issue #3976) into
 * the request attributes {@code ViewConsultationRequests.jsp} renders from.
 *
 * <p>Both actions that forward to that page ({@code encounter/ViewConsultation} and the schedule
 * banner's {@code encounter/IncomingConsultation}) call {@link #publish}, so the Provider dropdown
 * is populated however the page is reached. The JSP never reads the raw parameters:</p>
 * <ul>
 *   <li>an unknown or malformed {@code consultantId} is dropped rather than applied;</li>
 *   <li>a {@code filterProviderNo} outside the user's site/team access privacy scope (see
 *       {@link ConsultationListProviderScopeResolver}), or not among the dropdown options at all, is
 *       dropped, and the dropdown only lists in-scope providers.</li>
 * </ul>
 * <p>Callers must have checked {@code _con} read access first.</p>
 *
 * @since 2026-09-30
 */
public class ConsultationListFilterResolver {

    /** Request attribute: the applied consultant specId (Integer), or absent. */
    public static final String ATTR_CONSULTANT_ID = "consultantId";
    /** Request attribute: "Last, First" of the applied consultant, for re-filling the search box. */
    public static final String ATTR_CONSULTANT_LABEL = "consultantLabel";
    /** Request attribute: the applied, in-scope MRP provider number, or absent. */
    public static final String ATTR_FILTER_PROVIDER_NO = "filterProviderNo";
    /** Request attribute: List of {@link ConsultationMrpOptionDto} for the Provider dropdown. */
    public static final String ATTR_MRP_OPTIONS = "consultMrpOptions";

    private final ConsultationRequestDao consultationRequestDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final ConsultationListProviderScopeResolver providerScopeResolver;

    /**
     * @param consultationRequestDao    ConsultationRequestDao for the MRP dropdown options
     * @param professionalSpecialistDao ProfessionalSpecialistDao for the selected consultant's label
     * @param providerScopeResolver     ConsultationListProviderScopeResolver for access privacy
     */
    public ConsultationListFilterResolver(ConsultationRequestDao consultationRequestDao,
                                          ProfessionalSpecialistDao professionalSpecialistDao,
                                          ConsultationListProviderScopeResolver providerScopeResolver) {
        this.consultationRequestDao = consultationRequestDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.providerScopeResolver = providerScopeResolver;
    }

    /**
     * Builds a resolver from the Spring context, for the legacy no-arg Struts action constructors.
     *
     * @return ConsultationListFilterResolver wired to the application's DAOs and security manager
     */
    public static ConsultationListFilterResolver fromSpringContext() {
        return new ConsultationListFilterResolver(
                SpringUtils.getBean(ConsultationRequestDao.class),
                SpringUtils.getBean(ProfessionalSpecialistDao.class),
                new ConsultationListProviderScopeResolver(
                        SpringUtils.getBean(SecurityInfoManager.class),
                        SpringUtils.getBean(ProviderDataDao.class),
                        IsPropertiesOn::isMultisitesEnable));
    }

    /**
     * Publishes the resolved filters and the in-scope Provider dropdown options as request attributes.
     *
     * @param request             HttpServletRequest to publish the attributes on
     * @param loggedInInfo        LoggedInInfo the current user (already authorized for {@code _con r})
     * @param rawConsultantId     String the submitted {@code consultantId}; may be null or malformed
     * @param rawFilterProviderNo String the submitted {@code filterProviderNo}; may be null or out of scope
     */
    public void publish(HttpServletRequest request, LoggedInInfo loggedInInfo,
                        String rawConsultantId, String rawFilterProviderNo) {
        resolveConsultantFilter(request, rawConsultantId);
        resolveProviderFilter(request, loggedInInfo, rawFilterProviderNo);
    }

    private void resolveConsultantFilter(HttpServletRequest request, String rawConsultantId) {
        Integer specialistId = parsePositiveInt(rawConsultantId);
        if (specialistId == null) {
            return;
        }
        ProfessionalSpecialist specialist = professionalSpecialistDao.find(specialistId);
        if (specialist == null) {
            return;
        }
        request.setAttribute(ATTR_CONSULTANT_ID, specialistId);
        request.setAttribute(ATTR_CONSULTANT_LABEL,
                EctViewConsultationRequestsUtil.formatSpecialistName(specialist.getLastName(), specialist.getFirstName()));
    }

    private void resolveProviderFilter(HttpServletRequest request, LoggedInInfo loggedInInfo, String rawFilterProviderNo) {
        Optional<Set<String>> allowed = providerScopeResolver.resolveAllowedProviderNos(loggedInInfo);
        List<ConsultationMrpOptionDto> options = ConsultationListProviderScopeResolver.restrictOptions(
                consultationRequestDao.findDistinctConsultMrps(), allowed);
        request.setAttribute(ATTR_MRP_OPTIONS, options);

        // Apply only a provider the dropdown itself offers: in scope for this user AND the MRP of at
        // least one patient with a consult. An unknown or out-of-scope value is ignored rather than
        // silently turning the list into an empty (or badge-labelled but meaningless) result.
        String applied = ConsultationListProviderScopeResolver.sanitizeFilterProviderNo(rawFilterProviderNo, allowed);
        if (applied != null && options.stream().anyMatch(option -> applied.equals(option.providerNo()))) {
            request.setAttribute(ATTR_FILTER_PROVIDER_NO, applied);
        }
    }

    static Integer parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
