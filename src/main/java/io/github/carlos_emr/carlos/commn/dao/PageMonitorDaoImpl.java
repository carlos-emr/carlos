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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.PageMonitor;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link PageMonitorDao} for page monitoring data access.
 *
 * @since 2001
 */

public class PageMonitorDaoImpl extends AbstractDaoImpl<PageMonitor> implements PageMonitorDao {

    /** Constructs this DAO for the {@link PageMonitor} entity class. */

    public PageMonitorDaoImpl() {
        super(PageMonitor.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<PageMonitor> findByPage(String pageName, String pageId) {
        Query query = entityManager.createQuery("SELECT e FROM PageMonitor e WHERE e.pageName=?1 and e.pageId=?2 order by e.updateDate desc");
        query.setParameter(1, pageName);
        query.setParameter(2, pageId);
        @SuppressWarnings("unchecked")
        List<PageMonitor> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<PageMonitor> findByPageName(String pageName) {
        Query query = entityManager.createQuery("SELECT e FROM PageMonitor e WHERE e.pageName=?1 order by e.updateDate desc");
        query.setParameter(1, pageName);
        @SuppressWarnings("unchecked")
        List<PageMonitor> results = query.getResultList();
        return results;
    }

    /** {@inheritDoc} */

    @Override
    public void updatePage(String pageName, String pageId) {
        Query query = entityManager.createQuery("SELECT e FROM PageMonitor e WHERE e.pageName=?1 and e.pageId=?2 order by e.updateDate desc");
        query.setParameter(1, pageName);
        query.setParameter(2, pageId);
        @SuppressWarnings("unchecked")
        List<PageMonitor> results = query.getResultList();
        for (PageMonitor result : results) {
            Date now = new Date();
            Calendar c = Calendar.getInstance();
            c.setTime(result.getUpdateDate());
            c.add(Calendar.SECOND, result.getTimeout());
            if (c.getTime().before(now)) {
                this.remove(result.getId());
            }
        }
    }

    /** {@inheritDoc} */

    @Override
    public void removePageNameKeepPageIdForProvider(String pageName, String excludePageId, String providerNo) {
        Query query = entityManager.createQuery("SELECT e FROM PageMonitor e WHERE e.pageName=?1 and e.pageId!=?2 and e.providerNo=?3");
        query.setParameter(1, pageName);
        query.setParameter(2, excludePageId);
        query.setParameter(3, providerNo);
        @SuppressWarnings("unchecked")
        List<PageMonitor> results = query.getResultList();
        for (PageMonitor result : results) {
            this.remove(result.getId());
        }
    }

    /** {@inheritDoc} */

    @Override
    public void cancelPageIdForProvider(String pageName, String cancelPageId, String providerNo) {
        Query query = entityManager.createQuery("SELECT e FROM PageMonitor e WHERE e.pageName=?1 and e.pageId=?2 and  e.providerNo=?3");
        query.setParameter(1, pageName);
        query.setParameter(2, cancelPageId);
        query.setParameter(3, providerNo);
        @SuppressWarnings("unchecked")
        List<PageMonitor> results = query.getResultList();
        for (PageMonitor result : results) {
            this.remove(result.getId());
        }
    }
}
