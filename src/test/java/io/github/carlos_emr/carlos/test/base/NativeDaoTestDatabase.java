/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.test.base;

import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;

/** Real native-query execution over a test-owned datasource, without the full application context. */
public final class NativeDaoTestDatabase implements AutoCloseable {
    private final SessionFactory factory;
    private final EntityManager manager;

    public NativeDaoTestDatabase(DataSource source) {
        Configuration configuration = new Configuration().addAnnotatedClass(EncounterForm.class)
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.show_sql", "false");
        configuration.getProperties().put("hibernate.connection.datasource", source);
        factory = configuration.buildSessionFactory();
        manager = factory.createEntityManager();
    }

    public <T> T dao(Class<T> implementation) {
        try {
            T dao = implementation.getDeclaredConstructor().newInstance();
            ReflectionTestUtils.setField(dao, "entityManager", manager);
            if (dao instanceof AbstractDaoImpl<?>) ReflectionTestUtils.setField(dao, "entityManagerFactory", factory);
            return dao;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public void transaction(Runnable operation) {
        manager.getTransaction().begin();
        try {
            operation.run();
            manager.getTransaction().commit();
        } catch (RuntimeException e) {
            if (manager.getTransaction().isActive()) manager.getTransaction().rollback();
            throw e;
        }
    }

    @Override
    public void close() {
        manager.close();
        factory.close();
    }
}
