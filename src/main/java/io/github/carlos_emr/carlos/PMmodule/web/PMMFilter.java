/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.web;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.service.AgencyManager;
import io.github.carlos_emr.carlos.PMmodule.service.OscarSecurityManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.sec.UnauthenticatedRejectionResolver;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 *
 */
public class PMMFilter implements Filter {

    private static Logger logger = MiscUtils.getLogger();

    private AgencyManager agencyManager;
    private OscarSecurityManager oscarSecurityManager;
    private ProviderManager providerManager;
    private FilterConfig config;

    private void setProviderManager() {
        WebApplicationContext wac = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());

        agencyManager = (AgencyManager) wac.getBean(AgencyManager.class);
        oscarSecurityManager = (OscarSecurityManager) wac.getBean(OscarSecurityManager.class);
        providerManager = (ProviderManager) wac.getBean(ProviderManager.class);
    }

    public void init(FilterConfig config) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
        this.config = config;
    }

    /**
     * Applies the PMmodule session gate, then populates the PMmodule session context.
     *
     * <p><strong>Fails closed.</strong> A missing or blank {@code user} session attribute is
     * rejected through {@link UnauthenticatedRejectionResolver} and the request does <em>not</em>
     * continue down the filter chain — {@code chain.doFilter} is never called on that path, and no
     * PMmodule manager is looked up, so an unauthenticated request reaches neither the downstream
     * handlers nor {@code ProviderManager.getProgramDomain}. Blankness is judged by
     * {@link String#isBlank()} so Unicode whitespace is rejected too.</p>
     *
     * <p><strong>This is defence in depth, not the authentication gate.</strong>
     * {@code LoginFilter} is the canonical gate and is the check that actually runs for PMmodule
     * Struts actions: {@code web.xml} maps {@code struts2-execute} ahead of this filter, and Struts
     * terminates the chain once it executes an action, so routes such as
     * {@code /PMmodule/ProgramManager} never arrive here. This filter still runs for PMmodule
     * requests that fall through Struts without matching an action. Keep the blank-user contract
     * here consistent with {@code LoginFilter}; the two must not drift apart.</p>
     *
     * <p>Once authentication is confirmed the filter caches {@code program_domain} and
     * {@code pmm_admin} on the session and the local {@code Agency} in application scope, then
     * delegates to the rest of the chain.</p>
     *
     * @param baseRequest  the request; must be an {@link HttpServletRequest} (mapped to
     *                     {@code /PMmodule/*} only)
     * @param baseResponse the response; must be an {@link HttpServletResponse}
     * @param chain        the remaining chain, invoked only for authenticated requests
     * @throws IOException      if writing the rejection response or continuing the chain fails
     * @throws ServletException if a downstream filter or servlet fails
     */
    public void doFilter(ServletRequest baseRequest, ServletResponse baseResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) baseRequest;
        HttpServletResponse response = (HttpServletResponse) baseResponse;
        HttpSession session = request.getSession();

        String oscarUser = (String) session.getAttribute("user");
        // isBlank(), not trim().isEmpty(): trim() only strips code points <= U+0020, so a session
        // user of Unicode whitespace (for example U+2003) would otherwise reach getProgramDomain().
        if (oscarUser == null || oscarUser.isBlank()) {
            logger.warn("Unauthenticated access attempt to PMmodule blocked: method={}, uri={}, remote={}",
                    LogSafe.sanitize(request.getMethod()),
                    LogSafe.sanitizeUri(request.getRequestURI()),
                    LogSafe.sanitize(request.getRemoteAddr()));
            UnauthenticatedRejectionResolver.rejectUnauthenticatedRequest(request, response);
            return;
        }

        setProviderManager();

        session.setAttribute("program_domain", providerManager.getProgramDomain(oscarUser)); // nosemgrep: tainted-session-from-http-request -- DAO-sourced program domain list for authenticated provider

        if (session.getAttribute("pmm_admin") == null) {
            logger.debug("setting session variable: pmm_admin");
            session.setAttribute("pmm_admin", Boolean.valueOf(oscarSecurityManager.hasAdminRole(oscarUser))); // nosemgrep: tainted-session-from-http-request -- boolean derived from DAO-sourced role check on authenticated provider
        }
		
/* If the providers didn't have the role 'admin', he can still have the access to the administration links(eg. Add Program) on PMM.
 * Each link should be separately configurable under the role rights object screen.
 *

		if (request.getRequestURI().indexOf("ProgramManager") != -1 && ((String) session.getAttribute("userrole")).indexOf("admin") == -1) {
			RequestDispatcher rd = baseRequest.getRequestDispatcher("/commons/auth.jsp");
			rd.forward(baseRequest, baseResponse);
			return;
		}
*/
        // set local agency
        if (request.getSession().getServletContext().getAttribute("agency") == null) {
            Agency agency = agencyManager.getLocalAgency();
            request.getSession().getServletContext().setAttribute("agency", agency); // nosemgrep: tainted-session-from-http-request -- DAO-loaded local agency entity stored in application scope
            Agency.setLocalAgency(agency);
        }


        chain.doFilter(baseRequest, baseResponse);
    }

    public void destroy() {
    }

}
