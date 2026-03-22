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

package io.github.carlos_emr.carlos.casemgmt.common;

import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Base JSP tag class for case management tags. Provides Spring application context
 * access and convenience methods for retrieving the {@link CaseManagementManager} bean.
 *
 * <p>Subclasses should extend this tag to gain access to the Spring-managed
 * case management services within JSP custom tags.</p>
 *
 * @since 2026-03-17
 */
public class BasicTag extends TagSupport {

    /**
     * Retrieves the Spring application context from the servlet context.
     *
     * @return ApplicationContext the Spring web application context
     */
    public ApplicationContext getAppContext() {
        ApplicationContext cont = WebApplicationContextUtils.getWebApplicationContext(
                pageContext.getServletContext());
        return cont;
    }

    /**
     * Retrieves the {@link CaseManagementManager} Spring bean from the application context.
     *
     * @return CaseManagementManager the case management service manager
     */
    public CaseManagementManager getCaseManagementManager() {

        CaseManagementManager bpm = (CaseManagementManager) getAppContext()
                .getBean(CaseManagementManager.class);
        return bpm;
    }
}
