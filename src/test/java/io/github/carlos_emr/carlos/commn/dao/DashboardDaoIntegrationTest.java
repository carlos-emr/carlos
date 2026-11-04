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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

@Tag("integration")
@Tag("dao")
@Transactional
@DisplayName("Dashboard partial updates preserve unrelated changes")
class DashboardDaoIntegrationTest extends CarlosTestBase {
    @Autowired private DashboardDao dao;
    @PersistenceContext private EntityManager entityManager;

    private Dashboard createDashboard(boolean locked) {
        Dashboard dashboard = new Dashboard();
        dashboard.setName("FAKE dashboard");
        dashboard.setDescription("Original description");
        dashboard.setCreator("999998");
        dashboard.setActive(true);
        dashboard.setLocked(locked);
        dashboard.setEdited(new Date());
        dao.persist(dashboard);
        entityManager.flush();
        entityManager.clear();
        return dashboard;
    }

    @Test
    @DisplayName("should preserve an inactivation that happened after the edit form loaded")
    void shouldPreserveStatus_whenStaleMetadataFormSaved() {
        Dashboard original = createDashboard(false);
        int id = original.getId();
        Dashboard stale = dao.find(id);
        entityManager.detach(stale);
        assertThat(dao.updateActive(id, false, new Date())).isTrue();
        assertThat(dao.updateMetadata(id, "Renamed", "Edited description", new Date())).isTrue();
        entityManager.clear();
        Dashboard stored = dao.find(id);
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getName()).isEqualTo("Renamed");
        assertThat(stored.getDescription()).isEqualTo("Edited description");
        assertThat(stored.getCreator()).isEqualTo("999998");
        assertThat(stored.isLocked()).isFalse();
    }

    @Test
    @DisplayName("should preserve a metadata edit that happened before a status update")
    void shouldPreserveMetadata_whenStatusFormSaved() {
        int id = createDashboard(false).getId();
        assertThat(dao.updateMetadata(id, "Renamed", "Edited description", new Date())).isTrue();
        assertThat(dao.updateActive(id, false, new Date())).isTrue();
        entityManager.clear();
        Dashboard stored = dao.find(id);
        assertThat(stored.getName()).isEqualTo("Renamed");
        assertThat(stored.getDescription()).isEqualTo("Edited description");
        assertThat(stored.isActive()).isFalse();
    }

    @Test
    @DisplayName("should reject metadata and status changes when a dashboard is locked or missing")
    void shouldRefuseUpdates_whenLockedOrMissing() {
        int id = createDashboard(true).getId();
        assertThat(dao.updateMetadata(id, "Renamed", "Edited", new Date())).isFalse();
        assertThat(dao.updateActive(id, false, new Date())).isFalse();
        assertThat(dao.updateMetadata(Integer.MAX_VALUE, "Renamed", "Edited", new Date())).isFalse();
        assertThat(dao.updateActive(Integer.MAX_VALUE, false, new Date())).isFalse();
        entityManager.clear();
        Dashboard stored = dao.find(id);
        assertThat(stored.getName()).isEqualTo("FAKE dashboard");
        assertThat(stored.isActive()).isTrue();
        assertThat(stored.isLocked()).isTrue();
    }
}
