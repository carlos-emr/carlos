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
package io.github.carlos_emr.carlos.commn.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MyGroupMembershipService} against the H2 {@code mygroup} table:
 * the parameters a group list form really posts, CSRFGuard token included, remove exactly the
 * ticked memberships (issue #4515).
 *
 * @since 2026-10-15
 */
@DisplayName("MyGroupMembershipService Integration Tests")
@Tag("integration")
@Tag("service")
@Tag("delete")
@Transactional
class MyGroupMembershipServiceIntegrationTest extends CarlosTestBase {

    @Autowired
    private MyGroupDao myGroupDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private MyGroupMembershipService service;

    @BeforeEach
    void setUp() {
        service = new MyGroupMembershipService(myGroupDao);
    }

    private void member(String groupNo, String providerNo) {
        MyGroup row = new MyGroup();
        row.setId(new MyGroupPrimaryKey(groupNo, providerNo));
        row.setLastName("Last" + providerNo);
        row.setFirstName("First" + providerNo);
        myGroupDao.persist(row);
    }

    private List<String> providersIn(String groupNo) {
        return myGroupDao.getGroupByGroupNo(groupNo).stream()
                .map(row -> row.getId().getProviderNo())
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("should delete only the ticked members when the form also posts its CSRF token")
    void shouldDeleteOnlyTickedMembers_whenFormPostsCsrfToken() {
        member("GRPA", "101");
        member("GRPA", "102");
        member("GRPA", "103");
        member("GRPB", "101");
        entityManager.flush();

        // What admindisplaymygroup.jsp posts with two boxes ticked, in browser order.
        Map<String, String[]> form = new LinkedHashMap<>();
        form.put("GRPA101", new String[] {"GRPA"});
        form.put("GRPB101", new String[] {"GRPB"});
        form.put("submit", new String[] {"Delete"});
        form.put("CSRF-TOKEN", new String[] {"Q1W2-E3R4-T5Y6-U7I8-O9P0-A1S2-D3F4-G5H6"});

        int deleted = service.deleteSelectedMembers(form);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(providersIn("GRPA")).containsExactly("102", "103");
        assertThat(providersIn("GRPB")).isEmpty();
    }

    @Test
    @DisplayName("should delete nothing and report zero when the ticked row is already gone")
    void shouldReportZero_whenTickedRowIsAlreadyGone() {
        member("GRPC", "201");
        entityManager.flush();

        Map<String, String[]> form = new LinkedHashMap<>();
        form.put("GRPC202", new String[] {"GRPC"});
        form.put("CSRF-TOKEN", new String[] {"Q1W2-E3R4-T5Y6-U7I8-O9P0-A1S2-D3F4-G5H6"});

        assertThat(service.deleteSelectedMembers(form)).isZero();
        entityManager.flush();
        entityManager.clear();
        assertThat(providersIn("GRPC")).containsExactly("201");
    }
}
