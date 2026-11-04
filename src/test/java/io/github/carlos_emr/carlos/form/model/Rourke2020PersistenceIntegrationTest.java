/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.model;

import java.util.Properties;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.carlos_emr.carlos.commn.dao.forms.FormBooleanValueDao;
import io.github.carlos_emr.carlos.commn.dao.forms.Rourke2020Dao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("dao")
class Rourke2020PersistenceIntegrationTest {
    @Test
    void shouldBatchEveryCheckboxAndPreserveNotes_inOneTransaction() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(FormRourke2020.class).addAnnotatedClass(FormBooleanValue.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:rourke2020;NON_KEYWORDS=VALUE")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.generate_statistics", "true");
        try (SessionFactory factory = configuration.buildSessionFactory();
             Session session = factory.openSession();
             MockedStatic<SpringUtils> spring = Mockito.mockStatic(SpringUtils.class)) {
            Rourke2020Dao dao = new Rourke2020Dao();
            FormBooleanValueDao booleans = new FormBooleanValueDao();
            ReflectionTestUtils.setField(dao, "entityManager", session);
            ReflectionTestUtils.setField(booleans, "entityManager", session);
            spring.when(() -> SpringUtils.getBean(FormBooleanValueDao.class)).thenReturn(booleans);
            Properties values = new Properties();
            values.setProperty("provider_no", "999998");
            values.setProperty("demographic_no", "1");
            values.setProperty("formCreated", "14/10/2026");
            values.setProperty("p3_immunization", "Page III persisted note");
            values.setProperty("p4_immunization", "Page IV persisted note");
            for (String field : FormRourke2020Constants.BOOLEAN_FIELD_NAMES) {
                for (String suffix : FormRourke2020Constants.BOOLEAN_FIELD_SUFFIXES) {
                    values.setProperty(field + suffix, "on");
                }
            }
            FormRourke2020 form = new FormRourke2020(values);
            form.createBooleanValueMapFromFormProperties(values);
            int count = form.getBooleanValueMap().size();
            assertThat(count).isGreaterThan(1000);
            session.beginTransaction();
            session.setJdbcBatchSize(7);
            dao.saveEntity(form);
            assertThat(session.getJdbcBatchSize()).isEqualTo(7);
            session.getTransaction().commit();
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThan(count / 10L);
            session.clear();
            FormRourke2020 stored = dao.find(form.getId().intValue());
            assertThat(stored.getP3Immunization()).isEqualTo("Page III persisted note");
            assertThat(stored.getP4Immunization()).isEqualTo("Page IV persisted note");
            assertThat(stored.getBooleanValueMap()).hasSize(count);
            assertThat(stored.getBooleanValueMap().values()).allMatch(value -> value.getValue());

            // A later failure rolls the whole save back, including already flushed batches.
            session.beginTransaction();
            FormRourke2020 rolledBack = new FormRourke2020(values);
            rolledBack.createBooleanValueMapFromFormProperties(values);
            dao.saveEntity(rolledBack);
            session.getTransaction().rollback();
            session.clear();
            assertThat(session.find(FormRourke2020.class, rolledBack.getId())).isNull();
            assertThat(session.createQuery("select count(v) from FormBooleanValue v where v.id.formId = :id", Long.class)
                    .setParameter("id", rolledBack.getId()).getSingleResult()).isZero();
        }
    }
}
