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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.service.AgencyManager;
import io.github.carlos_emr.carlos.PMmodule.service.OscarSecurityManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
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

    public void doFilter(ServletRequest baseRequest, ServletResponse baseResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) baseRequest;
        HttpSession session = request.getSession();

        setProviderManager();

        String oscarUser = (String) session.getAttribute("user");
        if (oscarUser == null || oscarUser.length() == 0) {
            logger.info("Not logged in!");
            chain.doFilter(baseRequest, baseResponse);

            return;
        }

        session.setAttribute("program_domain", providerManager.getProgramDomain(oscarUser));

        if (session.getAttribute("pmm_admin") == null) {
            logger.debug("setting session variable: pmm_admin");
            session.setAttribute("pmm_admin", Boolean.valueOf(oscarSecurityManager.hasAdminRole(oscarUser)));
        }
		
        // set local agency
        if (request.getSession().getServletContext().getAttribute("agency") == null) {
            Agency agency = agencyManager.getLocalAgency();
            request.getSession().getServletContext().setAttribute("agency", agency);
            Agency.setLocalAgency(agency);
        }


        chain.doFilter(baseRequest, baseResponse);
    }

    public void destroy() {
    }

}
