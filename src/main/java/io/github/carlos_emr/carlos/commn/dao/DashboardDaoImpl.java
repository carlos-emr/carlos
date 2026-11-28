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
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardDaoImpl extends AbstractDaoImpl<Dashboard> implements DashboardDao {

    public DashboardDaoImpl() {
        super(Dashboard.class);
    }

    @Override
    public List<Dashboard> getActiveDashboards() {
        return getDashboardsByStatus(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Dashboard> getDashboardsByStatus(boolean status) {
        Query query = entityManager.createQuery("SELECT x FROM Dashboard x WHERE x.active = :status "
                + "OR (:status = false AND x.active IS NULL)");
        query.setParameter("status", status);
        List<Dashboard> result = query.getResultList();
        return result;
    }

    /**
     * This is a safe operation because the database is not expected to grow
     * large enough to cause performance issues.
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Dashboard> getDashboards() {
        Query query = entityManager.createQuery("SELECT x FROM Dashboard x");
        List<Dashboard> result = query.getResultList();
        return result;
    }

    @Override
    public boolean updateMetadata(int id, String name, String description, Date edited) {
        return updateMetadata(id, name, description, edited, null);
    }

    @Override
    public boolean updateMetadata(int id, String name, String description, Date edited, String revision) {
        Dashboard dashboard = findForAdministrationUpdate(id);
        if (dashboard == null || dashboard.isLocked()) {
            return false;
        }
        if (Objects.equals(dashboard.getName(), name) && Objects.equals(dashboard.getDescription(), description)) {
            return true;
        }
        if (!matchesRevision(dashboard, revision)) {
            return false;
        }
        dashboard.setName(name);
        dashboard.setDescription(description);
        dashboard.setEdited(nextEditTime(dashboard.getEdited(), edited));
        entityManager.flush();
        return true;
    }

    @Override
    public boolean updateActive(int id, boolean active, Date edited) {
        return updateActive(id, active, edited, null);
    }

    @Override
    public boolean updateActive(int id, boolean active, Date edited, String revision) {
        Dashboard dashboard = findForAdministrationUpdate(id);
        if (dashboard == null || dashboard.isLocked()) {
            return false;
        }
        if (dashboard.isActive() == active) {
            return true;
        }
        if (!matchesRevision(dashboard, revision)) {
            return false;
        }
        dashboard.setActive(active);
        dashboard.setEdited(nextEditTime(dashboard.getEdited(), edited));
        entityManager.flush();
        return true;
    }

    private Dashboard findForAdministrationUpdate(int id) {
        entityManager.flush();
        Dashboard dashboard = entityManager.find(Dashboard.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (dashboard != null) {
            // The caller can already hold this row in its persistence context.
            entityManager.refresh(dashboard, LockModeType.PESSIMISTIC_WRITE);
        }
        return dashboard;
    }

    private static boolean matchesRevision(Dashboard dashboard, String revision) {
        return revision == null || dashboard.getAdministrationRevision().equals(revision);
    }

    private static Date nextEditTime(Date previous, Date requested) {
        // The release schema stores edited to whole seconds. Advance on a real change so
        // rapid updates still get different revisions, even when they restore prior values.
        long requestedSecond = Math.floorDiv(requested.getTime(), 1000L);
        long nextSecond = previous == null ? requestedSecond
                : Math.max(requestedSecond, Math.floorDiv(previous.getTime(), 1000L) + 1L);
        return new Date(nextSecond * 1000L);
    }
}
