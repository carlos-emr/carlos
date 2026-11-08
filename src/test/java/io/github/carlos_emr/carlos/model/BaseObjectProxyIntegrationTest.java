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
package io.github.carlos_emr.carlos.model;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual Hibernate method interceptor, including after the session closes. */
@Tag("integration")
@Tag("security")
class BaseObjectProxyIntegrationTest {
    private static SessionFactory factory;

    @BeforeAll
    static void createOwnedDatabase() {
        factory = new Configuration()
                .addAnnotatedClass(Issue.class)
                .addAnnotatedClass(ClientImage.class)
                .addAnnotatedClass(CaseManagementCPP.class)
                .addAnnotatedClass(FieldDefValue.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:diagnostic_proxy_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.generate_statistics", "true")
                .buildSessionFactory();
    }

    @AfterAll
    static void closeOwnedDatabase() {
        if (factory != null) factory.close();
    }

    static Stream<Class<? extends BaseObject>> persistentTypes() {
        return Stream.of(Issue.class, ClientImage.class, CaseManagementCPP.class, FieldDefValue.class);
    }

    @ParameterizedTest
    @MethodSource("persistentTypes")
    void shouldCompareDetachedProxies_withoutLoadingPatientFields(Class<? extends BaseObject> type) throws Exception {
        BaseObject entity = newEntity(type);
        Object id = persist(entity);
        BaseObject proxy = detachedReference(type, id);
        BaseObject anotherProxy = detachedReference(type, id);
        long queries = factory.getStatistics().getPrepareStatementCount();

        assertThat(entity.equals(proxy)).isTrue();
        assertThat(proxy.equals(entity)).isTrue();
        assertThat(proxy.equals(proxy)).isTrue();
        assertThat(proxy.equals(anotherProxy)).isTrue();
        assertThat(proxy.equals(null)).isFalse();
        assertThat(proxy.equals(newEntity(type))).isFalse();
        assertThat(proxy.equals(new BaseObject())).isFalse();
        assertThat(proxy.hashCode()).isEqualTo(entity.hashCode()).isEqualTo(anotherProxy.hashCode());
        HashSet<BaseObject> values = new HashSet<>();
        values.add(entity);
        assertThat(values.contains(proxy)).isTrue();
        assertThat(values.remove(proxy)).isTrue();
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        assertThat(Hibernate.isInitialized(anotherProxy)).isFalse();
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(queries);
    }

    @ParameterizedTest
    @MethodSource("persistentTypes")
    void shouldKeepAttachedProxiesUninitialized_whenComparingAndHashing(Class<? extends BaseObject> type) throws Exception {
        BaseObject entity = newEntity(type);
        Object id = persist(entity);
        BaseObject other = newEntity(type);
        Object otherId = persist(other);
        try (var session = factory.openSession()) {
            BaseObject proxy = session.getReference(type, id);
            BaseObject different = session.getReference(type, otherId);
            long queries = factory.getStatistics().getPrepareStatementCount();
            assertThat(proxy.equals(entity)).isTrue();
            assertThat(entity.equals(proxy)).isTrue();
            assertThat(proxy.equals(different)).isFalse();
            assertThat(different.equals(proxy)).isFalse();
            assertThat(proxy.hashCode()).isEqualTo(entity.hashCode());
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            assertThat(Hibernate.isInitialized(different)).isFalse();
            assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(queries);
            Hibernate.initialize(proxy);
            assertThat(proxy.equals(entity)).isTrue();
            assertThat(entity.equals(proxy)).isTrue();
            assertThat(proxy.hashCode()).isEqualTo(entity.hashCode());
        }
    }

    @ParameterizedTest
    @MethodSource("persistentTypes")
    void shouldRenderDetachedProxyDiagnostics_withoutLoadingPatientFields(Class<? extends BaseObject> type) throws Exception {
        BaseObject entity = newEntity(type);
        BaseObject proxy = detachedReference(type, persist(entity));
        long queries = factory.getStatistics().getPrepareStatementCount();
        assertThat(proxy.toString()).isEqualTo(type.getSimpleName() + "[protected]");
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(queries);
    }

    @ParameterizedTest
    @MethodSource("persistentTypes")
    void shouldRetainSetMembership_whenPersistingAndReloading(Class<? extends BaseObject> type) throws Exception {
        BaseObject entity = newEntity(type);
        HashSet<BaseObject> values = new HashSet<>();
        values.add(entity);
        Object id = persist(entity);
        try (var session = factory.openSession()) {
            BaseObject reloaded = session.find(type, id);
            assertThat(reloaded).isNotSameAs(entity);
            assertThat(values.contains(entity)).isTrue();
            assertThat(values.contains(reloaded)).isTrue();
            assertThat(values.remove(reloaded)).isTrue();
            assertThat(values).isEmpty();
        }
    }

    private static BaseObject newEntity(Class<? extends BaseObject> type) throws Exception {
        BaseObject entity = type.getDeclaredConstructor().newInstance();
        if (entity instanceof Issue issue) issue.setDescription("Synthetic private diagnosis");
        if (entity instanceof CaseManagementCPP cpp) cpp.setSocialHistory("Synthetic private history");
        if (entity instanceof ClientImage image) image.setImage_data(new byte[] {1, 2, 3});
        if (entity instanceof FieldDefValue field) {
            field.setTableId("synthetic");
            field.setFieldName(UUID.randomUUID().toString());
            field.setVal("Synthetic private value");
        }
        return entity;
    }

    private static Object persist(BaseObject entity) {
        try (var session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                session.persist(entity);
                tx.commit();
                return session.getIdentifier(entity);
            } catch (RuntimeException failure) {
                if (tx.isActive()) tx.rollback();
                throw failure;
            }
        }
    }

    private static BaseObject detachedReference(Class<? extends BaseObject> type, Object id) {
        try (var session = factory.openSession()) {
            BaseObject proxy = session.getReference(type, id);
            assertThat(Hibernate.isInitialized(proxy)).isFalse();
            return proxy;
        }
    }
}
