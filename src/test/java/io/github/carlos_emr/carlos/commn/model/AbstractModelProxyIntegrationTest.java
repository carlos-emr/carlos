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
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link AbstractModel} equality and hashing through a real Hibernate session factory
 * (H2), including actual lazy proxies attached to a session and detached from it (#5303).
 *
 * <p>A hand-written subclass cannot show proxy behavior: only Hibernate's own method
 * interceptor decides whether a call loads the target, so every proxy case here goes through
 * {@code Session.getReference}. The test owns its database and shares nothing with the Spring
 * test context.</p>
 */
@Tag("integration")
@Tag("dao")
class AbstractModelProxyIntegrationTest {
    private static SessionFactory factory;

    @BeforeAll
    static void createOwnedDatabase() {
        factory = new Configuration()
                .addAnnotatedClass(Department.class)
                .addAnnotatedClass(MeasurementCSSLocation.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:abstract_model_proxy_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.generate_statistics", "true")
                .buildSessionFactory();
    }

    @AfterAll
    static void closeOwnedDatabase() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("should keep set membership when the entity is persisted after being added")
    void shouldKeepSetMembership_whenPersistedAfterAdd() {
        Department department = newDepartment();
        Set<Department> set = new HashSet<>();
        set.add(department);
        assertThat(department.getId()).isNull();
        assertThat(set.contains(department)).isTrue();

        Integer id = persist(department);

        assertThat(department.getId()).isEqualTo(id);
        assertThat(set.contains(department)).isTrue();
        try (var session = factory.openSession()) {
            Department reloaded = session.find(Department.class, id);
            assertThat(reloaded).isNotSameAs(department);
            assertThat(reloaded.equals(department)).isTrue();
            assertThat(set.contains(reloaded)).isTrue();
            assertThat(set.remove(reloaded)).isTrue();
        }
        assertThat(set).isEmpty();
    }

    @Test
    @DisplayName("should compare unsaved entities without throwing")
    void shouldCompareUnsavedEntities_withoutThrowing() {
        Department first = newDepartment();
        Department second = newDepartment();

        assertThat(first.equals(first)).isTrue();
        assertThat(first.equals(second)).isFalse();
        assertThat(first.equals(null)).isFalse();
        assertThat(first.equals(persisted())).isFalse();
    }

    @Test
    @DisplayName("should compare managed and detached entities by identifier")
    void shouldCompareManagedAndDetached_byIdentifier() {
        Integer id = persist(newDepartment());
        Integer otherId = persist(newDepartment());
        try (var session = factory.openSession()) {
            Department managed = session.find(Department.class, id);
            Department sameManaged = session.find(Department.class, id);
            Department other = session.find(Department.class, otherId);
            assertThat(managed).isSameAs(sameManaged);
            assertThat(managed.equals(other)).isFalse();
            session.detach(managed);
            Department refetched = session.find(Department.class, id);
            assertThat(refetched).isNotSameAs(managed);
            assertThat(managed.equals(refetched)).isTrue();
            assertThat(refetched.equals(managed)).isTrue();
            assertThat(managed.hashCode()).isEqualTo(refetched.hashCode());
        }
    }

    @Test
    @DisplayName("should compare against an attached uninitialized proxy without loading it")
    void shouldNotLoadAttachedProxy_whenUsedAsArgument() {
        Integer id = persist(newDepartment());
        Integer otherId = persist(newDepartment());
        try (var session = factory.openSession()) {
            Department entity = session.find(Department.class, id);
            Department proxy = session.getReference(Department.class, id);
            Department differentProxy = session.getReference(Department.class, otherId);
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            long statements = factory.getStatistics().getPrepareStatementCount();

            assertThat(entity.equals(proxy)).isTrue();
            assertThat(entity.equals(differentProxy)).isFalse();
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            assertThat(Hibernate.isInitialized(differentProxy)).isFalse();
            assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(statements);
        }
    }

    @Test
    @DisplayName("should compare against a detached uninitialized proxy without loading it")
    void shouldNotLoadDetachedProxy_whenUsedAsArgument() {
        Integer id = persist(newDepartment());
        Department detachedProxy = detachedReference(Department.class, id);
        Department differentProxy = detachedReference(Department.class, persist(newDepartment()));
        Department entity = load(id);
        long statements = factory.getStatistics().getPrepareStatementCount();

        assertThat(entity.equals(detachedProxy)).isTrue();
        assertThat(entity.equals(differentProxy)).isFalse();
        assertThat(Hibernate.isInitialized(detachedProxy)).isFalse();
        assertThat(Hibernate.isInitialized(differentProxy)).isFalse();
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(statements);
    }

    @Test
    @DisplayName("should not equal a proxy of another entity type with the same identifier")
    void shouldNotEqual_forProxyOfOtherEntityWithSameIdentifier() {
        Integer id = persist(newDepartment());
        Department department = load(id);
        // A reference needs no row: it is the same identifier under a different entity type.
        MeasurementCSSLocation locationProxy = detachedReference(MeasurementCSSLocation.class, id);

        assertThat(department.equals(locationProxy)).isFalse();
        assertThat(Hibernate.isInitialized(locationProxy)).isFalse();
    }

    @Test
    @DisplayName("should treat an attached proxy receiver as its entity")
    void shouldEqualEntity_whenReceiverIsAttachedProxy() {
        Integer id = persist(newDepartment());
        Integer otherId = persist(newDepartment());
        try (var session = factory.openSession()) {
            Department entity = session.find(Department.class, id);
            Department proxy = session.getReference(Department.class, id);
            Department different = session.getReference(Department.class, otherId);

            assertThat(proxy.equals(entity)).isTrue();
            assertThat(entity.equals(proxy)).isTrue();
            assertThat(proxy.equals(different)).isFalse();
            assertThat(proxy.hashCode()).isEqualTo(entity.hashCode());
            Set<Department> set = new HashSet<>(Set.of(entity));
            assertThat(set.contains(proxy)).isTrue();
            assertThat(set.remove(proxy)).isTrue();
        }
    }

    @Test
    @DisplayName("should keep an initialized proxy equal to its entity after the session closes")
    void shouldEqualEntity_whenInitializedProxyIsDetached() {
        Integer id = persist(newDepartment());
        Department proxy;
        try (var session = factory.openSession()) {
            proxy = session.getReference(Department.class, id);
            Hibernate.initialize(proxy);
        }
        Department entity = load(id);

        assertThat(Hibernate.isInitialized(proxy)).isTrue();
        assertThat(proxy.equals(entity)).isTrue();
        assertThat(entity.equals(proxy)).isTrue();
        assertThat(proxy.hashCode()).isEqualTo(entity.hashCode());
        assertThat(new HashSet<>(Set.of(entity)).contains(proxy)).isTrue();
    }

    private static Department newDepartment() {
        Department department = new Department();
        department.setName("Synthetic department " + UUID.randomUUID());
        return department;
    }

    private static Department persisted() {
        Department department = newDepartment();
        persist(department);
        return department;
    }

    private static Integer persist(Department entity) {
        try (var session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                session.persist(entity);
                tx.commit();
                return entity.getId();
            } catch (RuntimeException failure) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw failure;
            }
        }
    }

    private static Department load(Integer id) {
        try (var session = factory.openSession()) {
            return session.find(Department.class, id);
        }
    }

    private static <E> E detachedReference(Class<E> type, Integer id) {
        try (var session = factory.openSession()) {
            E proxy = session.getReference(type, id);
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            return proxy;
        }
    }
}
