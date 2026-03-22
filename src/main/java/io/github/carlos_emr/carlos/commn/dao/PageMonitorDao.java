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

import io.github.carlos_emr.carlos.commn.model.PageMonitor;

/**
 * DAO interface for page monitoring operations.
 *
 * @since 2001
 */

public interface PageMonitorDao extends AbstractDao<PageMonitor> {
    /**
     * Find By Page.
     *
     * @param pageName String the pageName
     * @param pageId String the pageId
     * @return List<PageMonitor>
     */
    List<PageMonitor> findByPage(String pageName, String pageId);

    /**
     * Find By Page Name.
     *
     * @param pageName String the pageName
     * @return List<PageMonitor>
     */
    List<PageMonitor> findByPageName(String pageName);

    /**
     * Update Page.
     *
     * @param pageName String the pageName
     * @param pageId String the pageId
     */
    void updatePage(String pageName, String pageId);

    /**
     * Remove Page Name Keep Page Id For Provider.
     *
     * @param pageName String the pageName
     * @param excludePageId String the excludePageId
     * @param providerNo String the providerNo
     */
    void removePageNameKeepPageIdForProvider(String pageName, String excludePageId, String providerNo);

    /**
     * Cancel Page Id For Provider.
     *
     * @param pageName String the pageName
     * @param cancelPageId String the cancelPageId
     * @param providerNo String the providerNo
     */
    void cancelPageIdForProvider(String pageName, String cancelPageId, String providerNo);
}
