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
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.model.security.Secobjectname;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecObjectNameDao} Hibernate migration validation.
 *
 * <p>These tests validate that the {@code saveOrUpdate} method in
 * {@code SecObjectNameDaoImpl} correctly persists and updates
 * {@link Secobjectname} entities via HibernateTemplate after Hibernate migration.
 * The entity uses an assigned ID strategy (the {@code objectname} field is the
 * primary key), mapped to the {@code secObjectName} table.</p>
 *
 * @since 2026-02-09
 * @see SecObjectNameDao
 * @see Secobjectname
 */
@DisplayName("SecObjectNameDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecObjectNameDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private SecObjectNameDao secObjectNameDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String SELECT_SQL = "SELECT objectName, description, orgapplicable FROM secObjectName WHERE objectName = ?1";

    @Test
    @Tag("create")
    @DisplayName("should persist Secobjectname when new instance provided")
    void shouldPersistSecobjectname_whenNewInstanceProvided() {
        // Given
        Secobjectname secObj = new Secobjectname("_testObject1", "Test security object", 1);

        // When
        secObjectNameDao.saveOrUpdate(secObj);
        entityManager.flush();

        // Then - verify via native SQL to ensure data reached the database
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject1")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject1");
        assertThat(row[1]).isEqualTo("Test security object");
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }

    @Test
    @Tag("update")
    @DisplayName("should update Secobjectname when existing instance modified")
    void shouldUpdateSecobjectname_whenExistingInstanceModified() {
        // Given - save an initial entity
        Secobjectname secObj = new Secobjectname("_testObject2", "Original description", 0);
        secObjectNameDao.saveOrUpdate(secObj);
        entityManager.flush();

        // When - modify and saveOrUpdate again
        secObj.setDescription("Updated description");
        secObj.setOrgapplicable(1);
        secObjectNameDao.saveOrUpdate(secObj);
        entityManager.flush();

        // Then - verify the updated values via native SQL
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject2")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject2");
        assertThat(row[1]).isEqualTo("Updated description");
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }

    @Test
    @Tag("create")
    @DisplayName("should persist with minimal fields when only objectname provided")
    void shouldPersistWithMinimalFields_whenOnlyObjectnameProvided() {
        // Given - use the minimal constructor (objectname only)
        Secobjectname secObj = new Secobjectname("_testObject3");

        // When
        secObjectNameDao.saveOrUpdate(secObj);
        entityManager.flush();

        // Then - verify persisted with null optional fields
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject3")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject3");
        assertThat(row[1]).isNull();
        assertThat(row[2]).isNull();
    }

    @Test
    @Tag("create")
    @DisplayName("should persist with all fields when full constructor used")
    void shouldPersistWithAllFields_whenFullConstructorUsed() {
        // Given - use the full constructor
        Secobjectname secObj = new Secobjectname("_testObject4", "Full constructor test", 1);

        // When
        secObjectNameDao.saveOrUpdate(secObj);
        entityManager.flush();

        // Then - verify all fields persisted correctly
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject4")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject4");
        assertThat(row[1]).isEqualTo("Full constructor test");
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }
}
