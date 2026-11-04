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

import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real draft timestamp precision and deletion after another persistence context replaces a row.
 * @since 2026-10-24
 */
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
class CaseManagementTmpSaveSnapshotIntegrationTest {
    private static final Date CAPTURED = Date.from(Instant.parse("2026-10-24T15:30:45Z"));
    private SessionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Configuration().addAnnotatedClass(CaseManagementTmpSave.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:draft-snapshot-" + UUID.randomUUID())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) factory.close();
    }

    @Test
    void shouldRetainTimeOfDay_whenDraftTimestampIsReadInAnotherPersistenceContext() {
        Integer id = seed("captured draft", 111, CAPTURED);

        try (var reader = factory.createEntityManager()) {
            assertThat(reader.find(CaseManagementTmpSave.class, id).getUpdateDate().getTime())
                    .isEqualTo(CAPTURED.getTime());
        }
    }

    @Test
    void shouldRemoveOnlyCapturedIdentity_andRetainOtherManagedDrafts() {
        Integer capturedId = seed("captured draft", 111, CAPTURED);
        Integer newerId = seed("newer draft", 111, new Date(CAPTURED.getTime() + 1000));
        Integer otherId = seed("other program draft", 222, CAPTURED);

        try (var em = factory.createEntityManager()) {
            var captured = em.find(CaseManagementTmpSave.class, capturedId);
            var newer = em.find(CaseManagementTmpSave.class, newerId);
            var other = em.find(CaseManagementTmpSave.class, otherId);
            em.getTransaction().begin();
            assertThat(dao(em).removeCaptured(capturedId)).isEqualTo(1);
            assertThat(em.contains(captured)).isFalse();
            assertThat(em.contains(newer)).isTrue();
            assertThat(em.contains(other)).isTrue();
            assertThat(dao(em).removeCaptured(capturedId)).isZero();
            em.flush();
            em.getTransaction().commit();
        }
        try (var reader = factory.createEntityManager()) {
            assertThat(reader.find(CaseManagementTmpSave.class, capturedId)).isNull();
            assertThat(reader.find(CaseManagementTmpSave.class, newerId).getNote()).isEqualTo("newer draft");
            assertThat(reader.find(CaseManagementTmpSave.class, otherId).getNote()).isEqualTo("other program draft");
        }
    }

    @Test
    void shouldPreserveConcurrentReplacement_whenOriginalRowIsStillManagedAndDirty() {
        Integer capturedId = seed("captured draft", 111, CAPTURED);
        Integer otherId = seed("other program draft", 222, CAPTURED);
        Integer replacementId;
        try (var original = factory.createEntityManager(); var replacement = factory.createEntityManager()) {
            var stale = original.find(CaseManagementTmpSave.class, capturedId);
            var other = original.find(CaseManagementTmpSave.class, otherId);
            replacement.getTransaction().begin();
            assertThat(dao(replacement).removeCaptured(capturedId)).isEqualTo(1);
            var newer = draft("newer autosaved text", 111, new Date(CAPTURED.getTime() + 1000));
            replacement.persist(newer);
            replacement.getTransaction().commit();
            replacementId = newer.getId();
            assertThat(replacementId).isNotEqualTo(capturedId);
            assertThat(stale.getNote()).isEqualTo("captured draft");
            stale.setNote("stale local mutation must never be flushed");

            original.getTransaction().begin();
            assertThat(dao(original).removeCaptured(capturedId)).isZero();
            assertThat(original.contains(stale)).isFalse();
            assertThat(original.contains(other)).isTrue();
            original.flush();
            original.getTransaction().commit();
        }
        try (var reader = factory.createEntityManager()) {
            var saved = dao(reader).find("1001", 101, 111);
            assertThat(saved.getId()).isEqualTo(replacementId);
            assertThat(saved.getNote()).isEqualTo("newer autosaved text");
            assertThat(reader.find(CaseManagementTmpSave.class, otherId).getNote()).isEqualTo("other program draft");
        }
    }

    private Integer seed(String text, int program, Date updated) {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var draft = draft(text, program, updated);
            em.persist(draft);
            em.getTransaction().commit();
            return draft.getId();
        }
    }

    private CaseManagementTmpSave draft(String text, int program, Date updated) {
        var draft = new CaseManagementTmpSave();
        draft.setProviderNo("1001");
        draft.setDemographicNo(101);
        draft.setProgramId(program);
        draft.setNoteId(7);
        draft.setNote(text);
        draft.setUpdateDate(updated);
        return draft;
    }

    private CaseManagementTmpSaveDaoImpl dao(EntityManager em) {
        var dao = new CaseManagementTmpSaveDaoImpl();
        dao.entityManager = em;
        return dao;
    }
}
